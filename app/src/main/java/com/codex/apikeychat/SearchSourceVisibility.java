package com.codex.apikeychat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SearchSourceVisibility {
    private static final Pattern SOURCE_CITATION_PATTERN = Pattern.compile("(?<!!)\\[(\\d{1,2})\\]");

    private SearchSourceVisibility() {
    }

    static boolean answerUsesSourceCitations(String answer) {
        return !citedSourceNumbers(answer).isEmpty();
    }

    static JSONArray visibleSourcesForAnswer(String answer, JSONArray sources) {
        JSONArray visible = new JSONArray();
        Set<Integer> citedNumbers = citedSourceNumbers(answer);
        if (citedNumbers.isEmpty() || sources == null || sources.length() == 0) {
            return visible;
        }

        JSONArray diagnostics = new JSONArray();
        int sourceNumber = 0;
        for (int i = 0; i < sources.length(); i++) {
            JSONObject item = sources.optJSONObject(i);
            if (item == null) {
                continue;
            }
            if (isDiagnostic(item)) {
                diagnostics.put(copy(item));
                continue;
            }
            sourceNumber++;
            if (citedNumbers.contains(sourceNumber)) {
                visible.put(copy(item));
            }
        }

        if (visible.length() > 0) {
            for (int i = 0; i < diagnostics.length(); i++) {
                JSONObject diagnostic = diagnostics.optJSONObject(i);
                if (diagnostic != null) {
                    visible.put(diagnostic);
                }
            }
        }
        return visible;
    }

    private static Set<Integer> citedSourceNumbers(String answer) {
        LinkedHashSet<Integer> numbers = new LinkedHashSet<>();
        if (answer == null || answer.trim().isEmpty()) {
            return numbers;
        }
        Matcher matcher = SOURCE_CITATION_PATTERN.matcher(answer);
        while (matcher.find()) {
            try {
                int number = Integer.parseInt(matcher.group(1));
                if (number > 0) {
                    numbers.add(number);
                }
            } catch (Exception ignored) {
            }
        }
        return numbers;
    }

    private static boolean isDiagnostic(JSONObject item) {
        return "diagnostic".equals(item.optString("kind", ""));
    }

    private static JSONObject copy(JSONObject item) {
        try {
            return new JSONObject(item.toString());
        } catch (Exception ignored) {
            return item;
        }
    }
}
