param(
    [string]$BaseUrl = $env:TEST_BASE_URL,
    [string]$Model = $env:TEST_MODEL,
    [string]$ImageModel = $env:TEST_IMAGE_MODEL,
    [switch]$UseEnvKey,
    [switch]$IncludeExpensive
)

$ErrorActionPreference = "Stop"
$script:TinyPngDataUrl = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMB/6X4f8sAAAAASUVORK5CYII="

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

function Invoke-ProviderGet {
    param([string]$Path)

    $url = "$script:CleanBaseUrl/$Path"
    $params = @{
        Uri = $url
        Method = "GET"
        Headers = @{
            Authorization = "Bearer $($script:ApiKey)"
            Accept = "application/json"
            "User-Agent" = "CodexMobile-ProviderToolTest/1.0"
        }
        TimeoutSec = 60
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

function Test-Models {
    $result = Invoke-ProviderGet -Path "models"
    if ($result.Success) {
        $ids = @()
        try {
            foreach ($item in $result.Json.data) {
                if ($null -ne $item.id) {
                    $ids += [string]$item.id
                }
            }
        } catch {
            $ids = @()
        }
        $detail = "/models is available"
        if ($ids.Count -gt 0) {
            $sample = ($ids | Select-Object -First 8) -join ", "
            $contains = $ids -contains $script:Model
            $detail = "models=$($ids.Count); selected_model_listed=$contains; sample=$sample"
        }
        Write-TestLine "Models list" "OK" $detail
    } else {
        Write-TestLine "Models list" "FAIL" $result.Error
    }
    return $result
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

function Test-ChatStreaming {
    $body = @{
        model = $script:Model
        stream = $true
        messages = @(
            @{
                role = "user"
                content = "Reply with OK only."
            }
        )
    }
    $result = Invoke-ProviderRequest -Path "chat/completions" -Body $body
    if ($result.Success) {
        if ($result.Content -match "data:|\[DONE\]|delta") {
            Write-TestLine "Chat Completions streaming" "OK" "SSE-like stream response detected"
        } else {
            Write-TestLine "Chat Completions streaming" "ACCEPTED" "stream=true was accepted, but response did not look like SSE"
        }
    } else {
        Write-TestLine "Chat Completions streaming" "FAIL" $result.Error
    }
    return $result
}

function Test-ResponsesStreaming {
    $body = @{
        model = $script:Model
        stream = $true
        input = "Reply with OK only."
    }
    $result = Invoke-ProviderRequest -Path "responses" -Body $body
    if ($result.Success) {
        if ($result.Content -match "response\.|data:|\[DONE\]|event:") {
            Write-TestLine "Responses streaming" "OK" "SSE-like Responses stream detected"
        } else {
            Write-TestLine "Responses streaming" "ACCEPTED" "stream=true was accepted, but response did not look like SSE"
        }
    } else {
        Write-TestLine "Responses streaming" "FAIL" $result.Error
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

function Test-ResponsesFunctionTools {
    $body = @{
        model = $script:Model
        input = "Call the get_weather function for Hangzhou. Do not answer directly."
        tools = @(
            @{
                type = "function"
                name = "get_weather"
                description = "Get current weather for a city."
                parameters = @{
                    type = "object"
                    properties = @{
                        city = @{
                            type = "string"
                            description = "City name"
                        }
                    }
                    required = @("city")
                    additionalProperties = $false
                }
            }
        )
        tool_choice = @{
            type = "function"
            name = "get_weather"
        }
    }
    $result = Invoke-ProviderRequest -Path "responses" -Body $body
    if (-not $result.Success) {
        $body.Remove("tool_choice") | Out-Null
        $body.input = "You have a get_weather function available. Call it for Hangzhou instead of answering directly."
        $fallback = Invoke-ProviderRequest -Path "responses" -Body $body
        if ($fallback.Success) {
            $result = $fallback
        }
    }
    if ($result.Success) {
        $hasToolCall = $result.Content -match "function_call|get_weather|tool_call|arguments"
        if ($hasToolCall) {
            Write-TestLine "Responses function tools" "OK" "Function tool-call signal detected"
            return [PSCustomObject]@{ Supported = $true; AcceptedOnly = $false; Result = $result }
        }
        Write-TestLine "Responses function tools" "ACCEPTED" "Function tool parameter was accepted, but no tool call was detected"
        return [PSCustomObject]@{ Supported = $false; AcceptedOnly = $true; Result = $result }
    }
    Write-TestLine "Responses function tools" "FAIL" $result.Error
    return [PSCustomObject]@{ Supported = $false; AcceptedOnly = $false; Result = $result }
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
    if (-not $result.Success) {
        $body.tool_choice = "auto"
        $body.messages = @(
            @{
                role = "user"
                content = "You have a web_search function available. Call it for the OpenAI official website instead of answering directly."
            }
        )
        $fallback = Invoke-ProviderRequest -Path "chat/completions" -Body $body
        if ($fallback.Success) {
            $result = $fallback
        }
    }
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

function Test-ChatJsonMode {
    $body = @{
        model = $script:Model
        response_format = @{
            type = "json_object"
        }
        messages = @(
            @{
                role = "user"
                content = "Return a JSON object with exactly this shape: {""ok"":true}."
            }
        )
    }
    $result = Invoke-ProviderRequest -Path "chat/completions" -Body $body
    if ($result.Success) {
        Write-TestLine "Chat JSON object mode" "OK" "response_format=json_object was accepted"
    } else {
        Write-TestLine "Chat JSON object mode" "FAIL" $result.Error
    }
    return $result
}

function Test-ChatJsonSchema {
    $body = @{
        model = $script:Model
        response_format = @{
            type = "json_schema"
            json_schema = @{
                name = "capability_test"
                strict = $true
                schema = @{
                    type = "object"
                    properties = @{
                        ok = @{
                            type = "boolean"
                        }
                    }
                    required = @("ok")
                    additionalProperties = $false
                }
            }
        }
        messages = @(
            @{
                role = "user"
                content = "Return ok=true."
            }
        )
    }
    $result = Invoke-ProviderRequest -Path "chat/completions" -Body $body
    if ($result.Success) {
        Write-TestLine "Chat JSON schema mode" "OK" "Structured Outputs-style json_schema was accepted"
    } else {
        Write-TestLine "Chat JSON schema mode" "FAIL" $result.Error
    }
    return $result
}

function Test-ResponsesJsonSchema {
    $body = @{
        model = $script:Model
        input = "Return ok=true."
        text = @{
            format = @{
                type = "json_schema"
                name = "capability_test"
                strict = $true
                schema = @{
                    type = "object"
                    properties = @{
                        ok = @{
                            type = "boolean"
                        }
                    }
                    required = @("ok")
                    additionalProperties = $false
                }
            }
        }
    }
    $result = Invoke-ProviderRequest -Path "responses" -Body $body
    if ($result.Success) {
        Write-TestLine "Responses JSON schema mode" "OK" "Responses text.format=json_schema was accepted"
    } else {
        Write-TestLine "Responses JSON schema mode" "FAIL" $result.Error
    }
    return $result
}

function Test-ChatVision {
    $body = @{
        model = $script:Model
        messages = @(
            @{
                role = "user"
                content = @(
                    @{
                        type = "text"
                        text = "This is a 1x1 test image. Reply with OK if you can inspect it."
                    },
                    @{
                        type = "image_url"
                        image_url = @{
                            url = $script:TinyPngDataUrl
                        }
                    }
                )
            }
        )
    }
    $result = Invoke-ProviderRequest -Path "chat/completions" -Body $body
    if ($result.Success) {
        Write-TestLine "Chat vision input" "OK" "image_url content was accepted"
    } else {
        Write-TestLine "Chat vision input" "FAIL" $result.Error
    }
    return $result
}

function Test-ResponsesVision {
    $body = @{
        model = $script:Model
        input = @(
            @{
                role = "user"
                content = @(
                    @{
                        type = "input_text"
                        text = "This is a 1x1 test image. Reply with OK if you can inspect it."
                    },
                    @{
                        type = "input_image"
                        image_url = $script:TinyPngDataUrl
                    }
                )
            }
        )
    }
    $result = Invoke-ProviderRequest -Path "responses" -Body $body
    if ($result.Success) {
        Write-TestLine "Responses vision input" "OK" "input_image content was accepted"
    } else {
        Write-TestLine "Responses vision input" "FAIL" $result.Error
    }
    return $result
}

function Test-ResponsesImageTool {
    if (-not $IncludeExpensive) {
        Write-TestLine "Responses image_generation tool" "SKIP" "Use -IncludeExpensive to test image generation"
        return [PSCustomObject]@{ Success = $false; Skipped = $true }
    }
    $body = @{
        model = $script:Model
        input = "Generate a simple 1x1 style icon: a black square on white background."
        tools = @(
            @{
                type = "image_generation"
                size = "1024x1024"
            }
        )
    }
    $result = Invoke-ProviderRequest -Path "responses" -Body $body
    if ($result.Success) {
        if ($result.Content -match "image_generation_call|b64_json|result") {
            Write-TestLine "Responses image_generation tool" "OK" "image generation signal detected"
        } else {
            Write-TestLine "Responses image_generation tool" "ACCEPTED" "Tool accepted, but no image signal was detected"
        }
    } else {
        Write-TestLine "Responses image_generation tool" "FAIL" $result.Error
    }
    return $result
}

function Test-ImagesEndpoint {
    if (-not $IncludeExpensive) {
        Write-TestLine "Images generations endpoint" "SKIP" "Use -IncludeExpensive to test /images/generations"
        return [PSCustomObject]@{ Success = $false; Skipped = $true }
    }
    $imageModelValue = $ImageModel
    if ($null -eq $imageModelValue -or $imageModelValue.Trim().Length -eq 0) {
        $imageModelValue = "image-2"
    }
    $body = @{
        model = $imageModelValue.Trim()
        prompt = "A minimal black square on a white background."
        size = "1024x1024"
        n = 1
    }
    $result = Invoke-ProviderRequest -Path "images/generations" -Body $body
    if ($result.Success) {
        if ($result.Content -match "b64_json|url") {
            Write-TestLine "Images generations endpoint" "OK" "/images/generations returned image data"
        } else {
            Write-TestLine "Images generations endpoint" "ACCEPTED" "Endpoint accepted request, but no image data signal was detected"
        }
    } else {
        Write-TestLine "Images generations endpoint" "FAIL" $result.Error
    }
    return $result
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
    if ($IncludeExpensive) {
        Write-Host "Expensive tests: enabled"
    } else {
        Write-Host "Expensive tests: skipped"
    }
    Write-Host ""

    $models = Test-Models
    $plainChat = Test-PlainChat
    $responsesBasic = Test-ResponsesBasic
    $chatStreaming = Test-ChatStreaming
    $responsesStreaming = Test-ResponsesStreaming
    $responsesSearch = Test-ResponsesWebSearch
    $responsesTools = Test-ResponsesFunctionTools
    $chatTools = Test-ChatFunctionTools
    $chatJson = Test-ChatJsonMode
    $chatSchema = Test-ChatJsonSchema
    $responsesSchema = Test-ResponsesJsonSchema
    $chatVision = Test-ChatVision
    $responsesVision = Test-ResponsesVision
    $responsesImage = Test-ResponsesImageTool
    $imagesEndpoint = Test-ImagesEndpoint

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
    if ($responsesTools.Supported -or $chatTools.Supported) {
        Write-Host "Agent shell: tool loop is viable. Implement model-requested local tools such as open_url, custom_search, image_generation, and file_summary."
    } elseif ($responsesSearch.Supported) {
        Write-Host "Agent shell: hosted web search is viable, but local non-search tools still need app-side routing."
    }
    if ($chatStreaming.Success -or $responsesStreaming.Success) {
        Write-Host "UI: streaming output can be enabled."
    }
    if ($chatVision.Success -or $responsesVision.Success) {
        Write-Host "Attachments: image input appears usable for vision-capable models."
    }
} finally {
    $script:ApiKey = $null
    [GC]::Collect()
}
