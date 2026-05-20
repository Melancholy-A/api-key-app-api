param(
    [string]$BaseUrl = $env:TEST_BASE_URL,
    [string]$Model = $env:TEST_MODEL,
    [switch]$UseEnvKey
)

$ErrorActionPreference = "Stop"

function Read-PlainSecret {
    param([string]$Prompt)

    $secure = Read-Host $Prompt -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    }
}

function Normalize-BaseUrl {
    param([string]$Value)

    $clean = ""
    if ($null -ne $Value) {
        $clean = $Value.Trim()
    }
    if ($clean.Length -eq 0) {
        throw "Base URL cannot be empty"
    }
    while ($clean.EndsWith("/")) {
        $clean = $clean.Substring(0, $clean.Length - 1)
    }
    foreach ($suffix in @("/chat/completions", "/images/generations", "/responses", "/models")) {
        if ($clean.ToLowerInvariant().EndsWith($suffix)) {
            return $clean.Substring(0, $clean.Length - $suffix.Length)
        }
    }
    return $clean
}

function Get-ErrorBody {
    param($ErrorRecord)

    try {
        $response = $ErrorRecord.Exception.Response
        if ($null -eq $response) {
            return ""
        }
        $stream = $response.GetResponseStream()
        if ($null -eq $stream) {
            return ""
        }
        $reader = New-Object System.IO.StreamReader($stream)
        return $reader.ReadToEnd()
    } catch {
        return ""
    }
}

function Get-StatusCode {
    param($ErrorRecord)

    try {
        $status = $ErrorRecord.Exception.Response.StatusCode
        if ($null -eq $status) {
            return 0
        }
        return [int]$status
    } catch {
        return 0
    }
}

function Invoke-ProviderRequest {
    param(
        [string]$Path,
        [hashtable]$Body
    )

    $url = "$script:CleanBaseUrl/$Path"
    $jsonBody = $Body | ConvertTo-Json -Depth 30 -Compress
    $params = @{
        Uri = $url
        Method = "POST"
        Headers = @{
            Authorization = "Bearer $($script:ApiKey)"
            Accept = "application/json"
            "User-Agent" = "CodexMobile-ProviderToolTest/1.0"
        }
        Body = $jsonBody
        ContentType = "application/json"
        TimeoutSec = 90
    }
    if ($PSVersionTable.PSVersion.Major -lt 6) {
        $params.UseBasicParsing = $true
    }

    try {
        $response = Invoke-WebRequest @params
        $content = [string]$response.Content
        $parsed = $null
        try {
            $parsed = $content | ConvertFrom-Json
        } catch {
            $parsed = $null
        }
        return [PSCustomObject]@{
            Success = $true
            StatusCode = [int]$response.StatusCode
            Content = $content
            Json = $parsed
            Error = ""
        }
    } catch {
        $body = Get-ErrorBody $_
        $message = if ($body.Trim().Length -gt 0) { $body } else { $_.Exception.Message }
        return [PSCustomObject]@{
            Success = $false
            StatusCode = Get-StatusCode $_
            Content = ""
            Json = $null
            Error = $message
        }
    }
}

function Shorten {
    param([string]$Value, [int]$Max = 260)

    $source = ""
    if ($null -ne $Value) {
        $source = $Value
    }
    $clean = ($source -replace "\s+", " ").Trim()
    if ($clean.Length -le $Max) {
        return $clean
    }
    return $clean.Substring(0, $Max) + "..."
}

function Write-TestLine {
    param(
        [string]$Name,
        [string]$State,
        [string]$Detail = ""
    )

    $line = "{0,-34} {1}" -f $Name, $State
    Write-Host $line
    if ($Detail.Trim().Length -gt 0) {
        Write-Host ("  " + (Shorten $Detail))
    }
}

function Test-PlainChat {
    $body = @{
        model = $script:Model
        messages = @(
            @{
                role = "user"
                content = "Reply with OK only."
            }
        )
    }
    $result = Invoke-ProviderRequest -Path "chat/completions" -Body $body
    if ($result.Success) {
        Write-TestLine "Chat Completions basic" "OK" "/chat/completions is available"
    } else {
        Write-TestLine "Chat Completions basic" "FAIL" $result.Error
    }
    return $result
}

function Test-ResponsesBasic {
    $body = @{
        model = $script:Model
        input = "Reply with OK only."
    }
    $result = Invoke-ProviderRequest -Path "responses" -Body $body
    if ($result.Success) {
        Write-TestLine "Responses basic" "OK" "/responses is available"
    } else {
        Write-TestLine "Responses basic" "FAIL" $result.Error
    }
    return $result
}

function Test-ResponsesWebSearch {
    foreach ($toolType in @("web_search", "web_search_preview")) {
        $body = @{
            model = $script:Model
            input = "Use the web search tool to search the OpenAI official website. Reply in one sentence and include one source URL."
            tools = @(
                @{
                    type = $toolType
                }
            )
        }
        $result = Invoke-ProviderRequest -Path "responses" -Body $body
        if ($result.Success) {
            $hasSearchSignal = $result.Content -match "web_search_call|url_citation|citations|source_url|sources"
            if ($hasSearchSignal) {
                Write-TestLine "Responses hosted $toolType" "OK" "Found search-call or source signals in the response"
                return [PSCustomObject]@{ Supported = $true; AcceptedOnly = $false; ToolType = $toolType; Result = $result }
            }
            Write-TestLine "Responses hosted $toolType" "ACCEPTED" "The API accepted the tool parameter, but no search-call signal was found"
            return [PSCustomObject]@{ Supported = $false; AcceptedOnly = $true; ToolType = $toolType; Result = $result }
        }
        Write-TestLine "Responses hosted $toolType" "FAIL" $result.Error
    }
    return [PSCustomObject]@{ Supported = $false; AcceptedOnly = $false; ToolType = ""; Result = $null }
}

function Test-ChatFunctionTools {
    $body = @{
        model = $script:Model
        messages = @(
            @{
                role = "user"
                content = "Call the web_search tool to search the OpenAI official website. Do not answer directly."
            }
        )
        tools = @(
            @{
                type = "function"
                "function" = @{
                    name = "web_search"
                    description = "Search the web for current information."
                    parameters = @{
                        type = "object"
                        properties = @{
                            query = @{
                                type = "string"
                                description = "Search query"
                            }
                        }
                        required = @("query")
                    }
                }
            }
        )
        tool_choice = @{
            type = "function"
            "function" = @{
                name = "web_search"
            }
        }
    }
    $result = Invoke-ProviderRequest -Path "chat/completions" -Body $body
    if ($result.Success) {
        $hasToolCall = $result.Content -match '"tool_calls"\s*:\s*\[|"function_call"\s*:'
        if ($hasToolCall) {
            Write-TestLine "Chat Completions function tools" "OK" "The model returned tool_calls/function_call; the app can execute its own search tool"
            return [PSCustomObject]@{ Supported = $true; AcceptedOnly = $false; Result = $result }
        }
        Write-TestLine "Chat Completions function tools" "ACCEPTED" "The API accepted tools, but no tool_calls were found"
        return [PSCustomObject]@{ Supported = $false; AcceptedOnly = $true; Result = $result }
    }
    Write-TestLine "Chat Completions function tools" "FAIL" $result.Error
    return [PSCustomObject]@{ Supported = $false; AcceptedOnly = $false; Result = $result }
}

try {
    $baseUrlValue = ""
    if ($null -ne $BaseUrl) {
        $baseUrlValue = $BaseUrl.Trim()
    }
    if ($baseUrlValue.Length -eq 0) {
        $BaseUrl = Read-Host "Base URL, for example https://remix.codes/v1"
    }
    $modelValue = ""
    if ($null -ne $Model) {
        $modelValue = $Model.Trim()
    }
    if ($modelValue.Length -eq 0) {
        $Model = Read-Host "Model name, for example gpt-4o-mini"
    }
    $script:CleanBaseUrl = Normalize-BaseUrl $BaseUrl
    $script:Model = $Model.Trim()

    if ($script:Model.Length -eq 0) {
        throw "Model cannot be empty"
    }

    $envKeyValue = ""
    if ($null -ne $env:TEST_API_KEY) {
        $envKeyValue = $env:TEST_API_KEY.Trim()
    }
    if ($UseEnvKey -and $envKeyValue.Length -gt 0) {
        $script:ApiKey = $env:TEST_API_KEY.Trim()
    } else {
        $script:ApiKey = Read-PlainSecret "API key (hidden input; not shown or saved)"
    }
    $apiKeyValue = ""
    if ($null -ne $script:ApiKey) {
        $apiKeyValue = $script:ApiKey.Trim()
    }
    if ($apiKeyValue.Length -eq 0) {
        throw "API key cannot be empty"
    }

    Write-Host ""
    Write-Host "Codex Mobile provider tool capability test"
    Write-Host "Base URL: $script:CleanBaseUrl"
    Write-Host "Model:    $script:Model"
    Write-Host "API key:  entered (hidden, not saved)"
    Write-Host ""

    $plainChat = Test-PlainChat
    $responsesBasic = Test-ResponsesBasic
    $responsesSearch = Test-ResponsesWebSearch
    $chatTools = Test-ChatFunctionTools

    Write-Host ""
    Write-Host "Conclusion:"
    if ($responsesSearch.Supported) {
        Write-Host "A. Responses hosted $($responsesSearch.ToolType) is supported. The app can use a Codex-like hosted web-search mode."
    } elseif ($chatTools.Supported) {
        Write-Host "B. Function calling/tools are supported, but hosted search was not detected. The app should execute web_search itself."
    } elseif ($plainChat.Success -or $responsesBasic.Success) {
        Write-Host "C. Basic chat works, but tool capability was not detected. The app needs its own auto-search logic plus a search API."
    } else {
        Write-Host "D. Basic chat also failed. Check Base URL, API key, model name, or provider availability."
    }
} finally {
    $script:ApiKey = $null
    [GC]::Collect()
}
