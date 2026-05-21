package com.codex.apikeychat;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

class ApiKeyStore {
    private static final String PREFS = "secure_api_key";
    private static final String PREF_KEY = "encrypted_key";
    private static final String PREF_BASE_URL = "base_url";
    private static final String PREF_API_MODE = "api_mode";
    private static final String PREF_IMAGE_MODEL = "image_model";
    private static final String PREF_IMAGE_MODEL_MIGRATED = "image_model_migrated_image2";
    private static final String PREF_IMAGE_ROUTE = "image_route";
    private static final String PREF_IMAGE_SIZE = "image_size";
    private static final String PREF_SEARCH_ENDPOINT = "search_endpoint";
    private static final String PREF_SEARCH_API_KEY = "encrypted_search_api_key";
    private static final String PREF_SEARCH_AUTH_MODE = "search_auth_mode";
    private static final String PREF_SEARCH_RESULT_COUNT = "search_result_count";
    private static final String PREF_SEARCH_ENABLED = "search_enabled";
    private static final String PREF_AGENT_TOOLS_ENABLED = "agent_tools_enabled";
    private static final String PREF_AGENT_IMAGE_TOOL_ENABLED = "agent_image_tool_enabled";
    private static final String PREF_CUSTOM_INSTRUCTIONS = "custom_instructions";
    private static final String PREF_START_NEW_ON_LAUNCH = "start_new_on_launch";
    private static final String DEFAULT_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_IMAGE_MODEL = "image-2";
    private static final String LEGACY_IMAGE_MODEL = "gpt-image-1.5";
    private static final String DEFAULT_IMAGE_SIZE = "1024x1024";
    private static final int DEFAULT_SEARCH_RESULT_COUNT = 5;
    static final String MODE_RESPONSES = "responses";
    static final String MODE_CHAT_COMPLETIONS = "chat_completions";
    static final String IMAGE_ROUTE_RESPONSES_TOOL = "responses_tool";
    static final String IMAGE_ROUTE_IMAGES_ENDPOINT = "images_endpoint";
    static final String SEARCH_AUTH_NONE = "none";
    static final String SEARCH_AUTH_BEARER = "bearer";
    static final String SEARCH_AUTH_X_API_KEY = "x_api_key";
    static final String SEARCH_AUTH_QUERY_API_KEY = "query_api_key";
    private static final String KEY_ALIAS = "api_key_chat_openai_key";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SharedPreferences prefs;

    ApiKeyStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    void save(String apiKey) throws Exception {
        saveEncrypted(PREF_KEY, apiKey);
    }

    String load() {
        return loadEncrypted(PREF_KEY);
    }

    boolean hasSavedKey() {
        return !load().isEmpty();
    }

    void clearKey() {
        prefs.edit().remove(PREF_KEY).apply();
    }

    void saveSearchApiKey(String apiKey) throws Exception {
        saveEncrypted(PREF_SEARCH_API_KEY, apiKey);
    }

    String loadSearchApiKey() {
        return loadEncrypted(PREF_SEARCH_API_KEY);
    }

    boolean hasSavedSearchApiKey() {
        return !loadSearchApiKey().isEmpty();
    }

    void clearSearchApiKey() {
        prefs.edit().remove(PREF_SEARCH_API_KEY).apply();
    }

    private void saveEncrypted(String prefKey, String plainText) throws Exception {
        if (plainText == null || plainText.trim().isEmpty()) {
            prefs.edit().remove(prefKey).apply();
            return;
        }
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] encrypted = cipher.doFinal(plainText.trim().getBytes(StandardCharsets.UTF_8));
        String iv = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP);
        String value = iv + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP);
        prefs.edit().putString(prefKey, value).apply();
    }

    private String loadEncrypted(String prefKey) {
        String value = prefs.getString(prefKey, "");
        if (value == null || value.isEmpty() || !value.contains(":")) {
            return "";
        }
        try {
            String[] parts = value.split(":", 2);
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    void saveBaseUrl(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        prefs.edit().putString(PREF_BASE_URL, normalized).apply();
    }

    String loadBaseUrl() {
        return normalizeBaseUrl(prefs.getString(PREF_BASE_URL, DEFAULT_BASE_URL));
    }

    void saveApiMode(String apiMode) {
        String value = MODE_CHAT_COMPLETIONS.equals(apiMode) ? MODE_CHAT_COMPLETIONS : MODE_RESPONSES;
        prefs.edit().putString(PREF_API_MODE, value).apply();
    }

    String loadApiMode() {
        String value = prefs.getString(PREF_API_MODE, MODE_RESPONSES);
        return MODE_CHAT_COMPLETIONS.equals(value) ? MODE_CHAT_COMPLETIONS : MODE_RESPONSES;
    }

    void saveImageModel(String model) {
        String value = model == null || model.trim().isEmpty() ? DEFAULT_IMAGE_MODEL : model.trim();
        prefs.edit()
                .putString(PREF_IMAGE_MODEL, value)
                .putBoolean(PREF_IMAGE_MODEL_MIGRATED, true)
                .apply();
    }

    String loadImageModel() {
        String value = prefs.getString(PREF_IMAGE_MODEL, DEFAULT_IMAGE_MODEL);
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_IMAGE_MODEL;
        }
        String trimmed = value.trim();
        if (LEGACY_IMAGE_MODEL.equals(trimmed) && !prefs.getBoolean(PREF_IMAGE_MODEL_MIGRATED, false)) {
            prefs.edit()
                    .putString(PREF_IMAGE_MODEL, DEFAULT_IMAGE_MODEL)
                    .putBoolean(PREF_IMAGE_MODEL_MIGRATED, true)
                    .apply();
            return DEFAULT_IMAGE_MODEL;
        }
        return trimmed;
    }

    void saveImageRoute(String route) {
        String value = IMAGE_ROUTE_RESPONSES_TOOL.equals(route)
                ? IMAGE_ROUTE_RESPONSES_TOOL
                : IMAGE_ROUTE_IMAGES_ENDPOINT;
        prefs.edit().putString(PREF_IMAGE_ROUTE, value).apply();
    }

    String loadImageRoute() {
        String value = prefs.getString(PREF_IMAGE_ROUTE, IMAGE_ROUTE_IMAGES_ENDPOINT);
        return IMAGE_ROUTE_RESPONSES_TOOL.equals(value) ? IMAGE_ROUTE_RESPONSES_TOOL : IMAGE_ROUTE_IMAGES_ENDPOINT;
    }

    void saveImageSize(String size) {
        String value = size == null || size.trim().isEmpty() ? DEFAULT_IMAGE_SIZE : size.trim();
        prefs.edit().putString(PREF_IMAGE_SIZE, value).apply();
    }

    String loadImageSize() {
        String value = prefs.getString(PREF_IMAGE_SIZE, DEFAULT_IMAGE_SIZE);
        return value == null || value.trim().isEmpty() ? DEFAULT_IMAGE_SIZE : value.trim();
    }

    void saveSearchEndpoint(String endpoint) {
        String value = endpoint == null ? "" : endpoint.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        prefs.edit().putString(PREF_SEARCH_ENDPOINT, value).apply();
    }

    String loadSearchEndpoint() {
        String value = prefs.getString(PREF_SEARCH_ENDPOINT, "");
        return value == null ? "" : value.trim();
    }

    void saveSearchAuthMode(String authMode) {
        String value = normalizeSearchAuthMode(authMode);
        prefs.edit().putString(PREF_SEARCH_AUTH_MODE, value).apply();
    }

    String loadSearchAuthMode() {
        return normalizeSearchAuthMode(prefs.getString(PREF_SEARCH_AUTH_MODE, SEARCH_AUTH_NONE));
    }

    void saveSearchResultCount(int count) {
        int value = Math.max(1, Math.min(10, count));
        prefs.edit().putInt(PREF_SEARCH_RESULT_COUNT, value).apply();
    }

    int loadSearchResultCount() {
        return Math.max(1, Math.min(10, prefs.getInt(PREF_SEARCH_RESULT_COUNT, DEFAULT_SEARCH_RESULT_COUNT)));
    }

    void saveSearchEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_SEARCH_ENABLED, enabled).apply();
    }

    boolean loadSearchEnabled() {
        return prefs.getBoolean(PREF_SEARCH_ENABLED, false);
    }

    void saveAgentToolsEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_AGENT_TOOLS_ENABLED, enabled).apply();
    }

    boolean loadAgentToolsEnabled() {
        return prefs.getBoolean(PREF_AGENT_TOOLS_ENABLED, true);
    }

    void saveAgentImageToolEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_AGENT_IMAGE_TOOL_ENABLED, enabled).apply();
    }

    boolean loadAgentImageToolEnabled() {
        return prefs.getBoolean(PREF_AGENT_IMAGE_TOOL_ENABLED, false);
    }

    void saveCustomInstructions(String value) {
        prefs.edit().putString(PREF_CUSTOM_INSTRUCTIONS, value == null ? "" : value.trim()).apply();
    }

    String loadCustomInstructions() {
        String value = prefs.getString(PREF_CUSTOM_INSTRUCTIONS, "");
        return value == null ? "" : value.trim();
    }

    void saveStartNewOnLaunch(boolean enabled) {
        prefs.edit().putBoolean(PREF_START_NEW_ON_LAUNCH, enabled).apply();
    }

    boolean loadStartNewOnLaunch() {
        return prefs.getBoolean(PREF_START_NEW_ON_LAUNCH, false);
    }

    static String defaultBaseUrl() {
        return DEFAULT_BASE_URL;
    }

    private static String normalizeSearchAuthMode(String authMode) {
        if (SEARCH_AUTH_BEARER.equals(authMode)) {
            return SEARCH_AUTH_BEARER;
        }
        if (SEARCH_AUTH_X_API_KEY.equals(authMode)) {
            return SEARCH_AUTH_X_API_KEY;
        }
        if (SEARCH_AUTH_QUERY_API_KEY.equals(authMode)) {
            return SEARCH_AUTH_QUERY_API_KEY;
        }
        return SEARCH_AUTH_NONE;
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return DEFAULT_BASE_URL;
        }
        String value = baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        value = stripEndpointSuffix(value);
        return value.isEmpty() ? DEFAULT_BASE_URL : value;
    }

    private static String stripEndpointSuffix(String value) {
        String lower = value.toLowerCase();
        String[] suffixes = {"/chat/completions", "/images/generations", "/responses", "/models"};
        for (String suffix : suffixes) {
            if (lower.endsWith(suffix)) {
                return value.substring(0, value.length() - suffix.length());
            }
        }
        return value;
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }

        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
        );
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build();
        keyGenerator.init(spec);
        return keyGenerator.generateKey();
    }
}
