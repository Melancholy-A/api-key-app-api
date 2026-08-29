package com.codex.apikeychat;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConnectionDiagnosticsTest {
    @Test
    public void successfulCheckReportsNormalizedUrlAndSelectedModel() {
        ConnectionDiagnostics.Result result = ConnectionDiagnostics.success(
                "beeapi.dev",
                "https://api.openai.com/v1",
                184,
                Arrays.asList("gpt-5", "gpt-4o"),
                "gpt-5"
        );

        assertEquals("https://beeapi.dev/v1", result.normalizedBaseUrl);
        assertEquals(200, result.httpStatus);
        assertEquals(184, result.elapsedMs);
        assertEquals(2, result.modelCount);
        assertTrue(result.selectedModelListed);
        assertTrue(result.summary.contains("HTTP 200"));
        assertTrue(result.summary.contains("当前模型已在列表中"));
    }

    @Test
    public void countryRestrictionExplainsTheProviderSideFailure() {
        ConnectionDiagnostics.Result result = ConnectionDiagnostics.failure(
                "https://provider.example/v1",
                "https://api.openai.com/v1",
                403,
                91,
                "HTTP 403: Country, region, or territory not supported"
        );

        assertTrue(result.summary.contains("地区"));
        assertTrue(result.summary.contains("服务商"));
        assertTrue(result.summary.contains("HTTP 403"));
        assertFalse(result.retryable);
    }

    @Test
    public void tlsFailureIsExplainedAndCanBeRetried() {
        ConnectionDiagnostics.Result result = ConnectionDiagnostics.failure(
                "https://provider.example/v1",
                "https://api.openai.com/v1",
                -1,
                250,
                "HTTPS/TLS 连接失败。原始错误: TLSVI_ALERT_INTERNAL_ERROR"
        );

        assertTrue(result.summary.contains("TLS"));
        assertTrue(result.summary.contains("证书"));
        assertTrue(result.retryable);
    }

    @Test
    public void selectedModelMissingIsReportedWithoutFailingTheConnection() {
        ConnectionDiagnostics.Result result = ConnectionDiagnostics.success(
                "https://provider.example/v1",
                "https://api.openai.com/v1",
                40,
                Collections.singletonList("gpt-4o"),
                "gpt-5"
        );

        assertFalse(result.selectedModelListed);
        assertTrue(result.summary.contains("当前模型未出现在列表中"));
    }

    @Test
    public void failureOutputNeverEchoesBearerCredentials() {
        String fakeSecret = "sk-" + "diagnostic-secret-value";
        ConnectionDiagnostics.Result result = ConnectionDiagnostics.failure(
                "https://provider.example/v1",
                "https://api.openai.com/v1",
                400,
                23,
                "HTTP 400: Authorization: Bearer " + fakeSecret
        );

        assertFalse(result.summary.contains(fakeSecret));
        assertTrue(result.summary.contains("[已隐藏]"));
    }
}
