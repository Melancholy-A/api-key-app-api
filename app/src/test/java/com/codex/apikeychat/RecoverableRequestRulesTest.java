package com.codex.apikeychat;

import org.junit.Test;

import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RecoverableRequestRulesTest {
    @Test
    public void transientNetworkAndProviderErrorsCanBeRetried() {
        assertTrue(RecoverableRequestRules.isRetryable("HTTP 503: overloaded", false));
        assertTrue(RecoverableRequestRules.isRetryable("HTTP 524: A timeout occurred", false));
        assertTrue(RecoverableRequestRules.isRetryable("HTTPS/TLS 连接失败", false));
        assertTrue(RecoverableRequestRules.isRetryable("SocketTimeoutException: timeout", false));
    }

    @Test
    public void authenticationAndRegionErrorsCannotBeRetried() {
        assertFalse(RecoverableRequestRules.isRetryable("HTTP 401: invalid API key", false));
        assertFalse(RecoverableRequestRules.isRetryable("HTTP 403: Country not supported", false));
    }

    @Test
    public void canceledRequestsNeverExposeRetry() {
        assertFalse(RecoverableRequestRules.isRetryable("HTTP 503: overloaded", true));
        assertFalse(RecoverableRequestRules.isRetryable("请求已停止", false));
    }

    @Test
    public void dnsFailuresCanBeRetriedEvenWhenTheMessageOnlyContainsTheHost() {
        assertTrue(RecoverableRequestRules.isRetryable(
                new UnknownHostException("provider.example"),
                false
        ));
    }

    @Test
    public void wrappedTimeoutFailuresCanBeRetriedFromTheCauseChain() {
        assertTrue(RecoverableRequestRules.isRetryable(
                new RuntimeException("request failed", new java.net.SocketTimeoutException("provider.example")),
                false
        ));
    }

    @Test
    public void authenticationStatusWinsOverWrappedNetworkCause() {
        assertFalse(RecoverableRequestRules.isRetryable(
                new IOException("HTTP 401: invalid API key", new SocketException("connection reset")),
                false
        ));
    }
}
