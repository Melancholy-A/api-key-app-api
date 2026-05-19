import Foundation
import UIKit

enum SearchClientError: LocalizedError {
    case invalidEndpoint(String)
    case noResults(String)
    case http(Int, String)

    var errorDescription: String? {
        switch self {
        case .invalidEndpoint(let value):
            return "搜索接口地址无效：\(value)"
        case .noResults(let value):
            return value
        case .http(let code, let value):
            return "搜索 HTTP \(code): \(value)"
        }
    }
}

final class SearchClient {
    func search(query: String, settings: AppSettingsSnapshot) async throws -> [SearchSource] {
        let cleanQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanQuery.isEmpty else { return [] }
        let count = max(1, min(10, settings.searchResultCount))
        let endpoint = settings.searchEndpoint.trimmingCharacters(in: .whitespacesAndNewlines)

        if endpoint.isEmpty || endpoint == "auto" || endpoint == "builtin" {
            return try await searchBuiltIn(query: cleanQuery, count: count)
        }
        if endpoint.lowercased() == "builtin:bing" || endpoint.lowercased() == "bing" {
            return try await searchBing(query: cleanQuery, count: count)
        }
        if endpoint.lowercased() == "builtin:duckduckgo" || endpoint.lowercased() == "duckduckgo" {
            return try await searchDuckDuckGo(query: cleanQuery, count: count)
        }
        return try await searchCustom(endpoint: endpoint, query: cleanQuery, count: count, settings: settings)
    }

    private func searchBuiltIn(query: String, count: Int) async throws -> [SearchSource] {
        var all: [SearchSource] = []
        var errors: [String] = []
        for candidate in buildSearchQueries(query) {
            do {
                all.append(contentsOf: try await searchBing(query: candidate, count: count))
            } catch {
                errors.append("Bing: \(error.localizedDescription)")
            }
            if unique(all).count >= count { break }
            do {
                all.append(contentsOf: try await searchDuckDuckGo(query: candidate, count: count))
            } catch {
                errors.append("DuckDuckGo: \(error.localizedDescription)")
            }
            if unique(all).count >= count { break }
        }
        let result = Array(unique(all).prefix(count))
        if result.isEmpty {
            throw SearchClientError.noResults("内置搜索源没有返回可解析结果。\n\(errors.joined(separator: "\n"))")
        }
        return result
    }

    private func searchBing(query: String, count: Int) async throws -> [SearchSource] {
        let encoded = query.urlQueryEncoded
        let urlString = "https://cn.bing.com/search?q=\(encoded)&count=\(count)"
        let html = try await fetchText(urlString)
        let results = parseBing(html).prefix(count)
        if results.isEmpty {
            throw SearchClientError.noResults("Bing 没有返回可解析结果")
        }
        return Array(results)
    }

    private func searchDuckDuckGo(query: String, count: Int) async throws -> [SearchSource] {
        let encoded = query.urlQueryEncoded
        let urlString = "https://duckduckgo.com/html/?q=\(encoded)"
        let html = try await fetchText(urlString)
        let results = parseDuckDuckGo(html).prefix(count)
        if results.isEmpty {
            throw SearchClientError.noResults("DuckDuckGo 没有返回可解析结果")
        }
        return Array(results)
    }

    private func searchCustom(endpoint: String, query: String, count: Int, settings: AppSettingsSnapshot) async throws -> [SearchSource] {
        let templated = endpoint.contains("{query}") || endpoint.contains("{q}") || endpoint.contains("{count}")
        var request: URLRequest
        if templated || endpoint.contains("?") {
            var urlString = endpoint
            if templated {
                urlString = urlString
                    .replacingOccurrences(of: "{query}", with: query.urlQueryEncoded)
                    .replacingOccurrences(of: "{q}", with: query.urlQueryEncoded)
                    .replacingOccurrences(of: "{count}", with: "\(count)")
            } else {
                urlString = appendQuery(endpoint, name: "q", value: query)
                urlString = appendQuery(urlString, name: "count", value: "\(count)")
            }
            if settings.searchAuthMode == .queryApiKey && !settings.searchApiKey.isEmpty {
                urlString = appendQuery(urlString, name: "api_key", value: settings.searchApiKey)
            }
            guard let url = URL(string: urlString) else { throw SearchClientError.invalidEndpoint(urlString) }
            request = URLRequest(url: url)
            request.httpMethod = "GET"
        } else {
            guard let url = URL(string: endpoint) else { throw SearchClientError.invalidEndpoint(endpoint) }
            request = URLRequest(url: url)
            request.httpMethod = "POST"
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            var body: [String: Any] = [
                "query": query,
                "q": query,
                "count": count,
                "num": count,
                "max_results": count
            ]
            if settings.searchAuthMode == .queryApiKey && !settings.searchApiKey.isEmpty {
                body["api_key"] = settings.searchApiKey
            }
            request.httpBody = try JSONSerialization.data(withJSONObject: body, options: [])
        }
        applyAuth(to: &request, mode: settings.searchAuthMode, apiKey: settings.searchApiKey)
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue("CodexMobile-iOS/1.0", forHTTPHeaderField: "User-Agent")
        request.timeoutInterval = 45

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw SearchClientError.noResults("搜索接口没有 HTTP 响应")
        }
        guard 200..<300 ~= http.statusCode else {
            throw SearchClientError.http(http.statusCode, String(data: data, encoding: .utf8) ?? "")
        }
        let object = try JSONSerialization.jsonObject(with: data, options: [])
        let results = extractResults(from: object).prefix(count)
        if results.isEmpty {
            throw SearchClientError.noResults("搜索接口返回了数据，但没有识别到 results/data/items/organic/webPages.value")
        }
        return Array(results)
    }

    private func fetchText(_ urlString: String) async throws -> String {
        guard let url = URL(string: urlString) else { throw SearchClientError.invalidEndpoint(urlString) }
        var request = URLRequest(url: url)
        request.timeoutInterval = 45
        request.setValue("Mozilla/5.0 CodexMobile-iOS/1.0", forHTTPHeaderField: "User-Agent")
        request.setValue("text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", forHTTPHeaderField: "Accept")
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw SearchClientError.noResults("搜索源没有 HTTP 响应")
        }
        guard 200..<300 ~= http.statusCode else {
            throw SearchClientError.http(http.statusCode, String(data: data, encoding: .utf8) ?? "")
        }
        return String(data: data, encoding: .utf8) ?? String(data: data, encoding: .isoLatin1) ?? ""
    }

    private func parseBing(_ html: String) -> [SearchSource] {
        let pattern = #"<li\s+class="[^"]*b_algo[^"]*"[^>]*>.*?<h2[^>]*>\s*<a[^>]+href="([^"]+)"[^>]*>(.*?)</a>.*?</h2>(.*?)(?=<li\s+class="[^"]*b_algo|</ol>|</main>|$)"#
        return matches(pattern: pattern, in: html).compactMap { groups in
            guard groups.count >= 3 else { return nil }
            let snippet = firstMatch(pattern: #"<p[^>]*>(.*?)</p>"#, in: groups[2]) ?? ""
            return SearchSource(
                title: cleanHTML(groups[1]),
                snippet: cleanHTML(snippet),
                url: groups[0],
                publishedAt: nil
            )
        }
    }

    private func parseDuckDuckGo(_ html: String) -> [SearchSource] {
        let linkPattern = #"<a[^>]+class="[^"]*result__a[^"]*"[^>]+href="([^"]+)"[^>]*>(.*?)</a>"#
        let snippets = matches(pattern: #"<(?:a|div)[^>]+class="[^"]*result__snippet[^"]*"[^>]*>(.*?)</(?:a|div)>"#, in: html).compactMap { $0.first }
        return matches(pattern: linkPattern, in: html).enumerated().compactMap { index, groups in
            guard groups.count >= 2 else { return nil }
            let url = decodeDuckURL(groups[0])
            let snippet = index < snippets.count ? snippets[index] : ""
            return SearchSource(
                title: cleanHTML(groups[1]),
                snippet: cleanHTML(snippet),
                url: url,
                publishedAt: nil
            )
        }
    }

    private func extractResults(from object: Any) -> [SearchSource] {
        if let array = object as? [[String: Any]] {
            return array.compactMap(parseResult)
        }
        guard let dict = object as? [String: Any] else { return [] }
        let possibleArrays: [Any?] = [
            dict["results"],
            dict["data"],
            dict["items"],
            dict["organic"],
            (dict["webPages"] as? [String: Any])?["value"]
        ]
        for value in possibleArrays {
            if let array = value as? [[String: Any]] {
                let parsed = array.compactMap(parseResult)
                if !parsed.isEmpty { return parsed }
            }
        }
        return []
    }

    private func parseResult(_ item: [String: Any]) -> SearchSource? {
        let title = item["title"] as? String ?? item["name"] as? String ?? item["headline"] as? String ?? ""
        let snippet = item["snippet"] as? String ?? item["content"] as? String ?? item["description"] as? String ?? item["summary"] as? String ?? ""
        let url = item["url"] as? String ?? item["link"] as? String ?? item["href"] as? String ?? ""
        guard !url.isEmpty else { return nil }
        return SearchSource(
            title: title.isEmpty ? url : title,
            snippet: snippet,
            url: url,
            publishedAt: item["publishedAt"] as? String ?? item["date"] as? String
        )
    }

    private func applyAuth(to request: inout URLRequest, mode: SearchAuthMode, apiKey: String) {
        guard !apiKey.isEmpty else { return }
        switch mode {
        case .bearer:
            request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        case .xApiKey:
            request.setValue(apiKey, forHTTPHeaderField: "X-API-Key")
        case .none, .queryApiKey:
            break
        }
    }

    private func buildSearchQueries(_ query: String) -> [String] {
        let lower = query.lowercased()
        if lower.contains("万方") || lower.contains("wanfang") {
            return ["site:wanfangdata.com.cn \(query)", query]
        }
        if lower.contains("知网") || lower.contains("cnki") {
            return ["site:cnki.net \(query)", query]
        }
        if lower.contains("维普") || lower.contains("cqvip") {
            return ["site:cqvip.com \(query)", query]
        }
        return [query]
    }

    private func unique(_ values: [SearchSource]) -> [SearchSource] {
        var seen = Set<String>()
        return values.filter { item in
            let key = item.url.lowercased()
            if seen.contains(key) { return false }
            seen.insert(key)
            return true
        }
    }

    private func appendQuery(_ endpoint: String, name: String, value: String) -> String {
        let separator = endpoint.contains("?") ? "&" : "?"
        return endpoint + separator + "\(name)=\(value.urlQueryEncoded)"
    }

    private func decodeDuckURL(_ value: String) -> String {
        guard let components = URLComponents(string: value),
              let uddg = components.queryItems?.first(where: { $0.name == "uddg" })?.value else {
            return htmlDecode(value)
        }
        return uddg
    }

    private func matches(pattern: String, in text: String) -> [[String]] {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive, .dotMatchesLineSeparators]) else {
            return []
        }
        let ns = text as NSString
        let range = NSRange(location: 0, length: ns.length)
        return regex.matches(in: text, options: [], range: range).map { match in
            (1..<match.numberOfRanges).compactMap { index in
                let range = match.range(at: index)
                guard range.location != NSNotFound else { return nil }
                return ns.substring(with: range)
            }
        }
    }

    private func firstMatch(pattern: String, in text: String) -> String? {
        matches(pattern: pattern, in: text).first?.first
    }

    private func cleanHTML(_ value: String) -> String {
        let withoutTags = value.replacingOccurrences(of: #"<[^>]+>"#, with: " ", options: .regularExpression)
        return htmlDecode(withoutTags)
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func htmlDecode(_ value: String) -> String {
        let wrapped = "<span>\(value)</span>"
        guard let data = wrapped.data(using: .utf8),
              let attributed = try? NSAttributedString(
                data: data,
                options: [.documentType: NSAttributedString.DocumentType.html, .characterEncoding: String.Encoding.utf8.rawValue],
                documentAttributes: nil
              ) else {
            return value
                .replacingOccurrences(of: "&amp;", with: "&")
                .replacingOccurrences(of: "&quot;", with: "\"")
                .replacingOccurrences(of: "&#39;", with: "'")
        }
        return attributed.string
    }
}

private extension String {
    var urlQueryEncoded: String {
        addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? self
    }
}
