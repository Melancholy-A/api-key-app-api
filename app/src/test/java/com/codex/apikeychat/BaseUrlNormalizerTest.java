package com.codex.apikeychat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BaseUrlNormalizerTest {
    @Test
    public void addsV1WhenOnlyDomainIsEntered() {
        assertEquals("https://beeapi.dev/v1", BaseUrlNormalizer.normalize(
                "https://beeapi.dev",
                "https://api.openai.com/v1"
        ));
    }

    @Test
    public void preservesExplicitApiPath() {
        assertEquals("https://beeapi.dev/v1", BaseUrlNormalizer.normalize(
                "https://beeapi.dev/v1/",
                "https://api.openai.com/v1"
        ));
    }

    @Test
    public void stripsKnownEndpointSuffixes() {
        assertEquals("https://beeapi.dev/v1", BaseUrlNormalizer.normalize(
                "https://beeapi.dev/v1/chat/completions",
                "https://api.openai.com/v1"
        ));
    }

    @Test
    public void addsHttpsWhenSchemeIsOmitted() {
        assertEquals("https://beeapi.dev/v1", BaseUrlNormalizer.normalize(
                "beeapi.dev",
                "https://api.openai.com/v1"
        ));
    }
}
