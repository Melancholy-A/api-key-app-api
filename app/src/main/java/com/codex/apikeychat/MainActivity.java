package com.codex.apikeychat;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQUEST_IMAGE = 1001;
    private static final int REQUEST_FILE = 1002;
    private static final int MAX_ATTACHMENTS = 6;
    private static final long MAX_ATTACHMENT_BYTES = 20L * 1024L * 1024L;
    private static final long UPDATE_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L;
    private static final String UPDATE_PREFS = "app_update";
    private static final String PREF_LAST_UPDATE_CHECK = "last_update_check";
    private static final String UPDATE_APK_NAME = "CodexMobile-debug.apk";
    private static final String[] DEFAULT_MODELS = {
            "gpt-5.5",
            "gpt-5.4",
            "gpt-5.4-mini",
            "gpt-5.2",
            "gpt-4.1",
            "gpt-4.1-mini",
            "o4-mini"
    };

    private ApiKeyStore apiKeyStore;
    private ChatStore chatStore;
    private ChatStore.Session currentSession;
    private LinearLayout settingsPanel;
    private LinearLayout historyPanel;
    private LinearLayout browserPanel;
    private TextView statusView;
    private TextView keyStatusView;
    private TextView attachmentsView;
    private TextView browserTitleView;
    private EditText apiKeyInput;
    private EditText baseUrlInput;
    private EditText imageModelInput;
    private EditText searchEndpointInput;
    private EditText searchApiKeyInput;
    private EditText customModelInput;
    private EditText messageInput;
    private Spinner modelSpinner;
    private Spinner apiModeSpinner;
    private Spinner imageRouteSpinner;
    private Spinner imageSizeSpinner;
    private Spinner searchAuthSpinner;
    private Spinner searchCountSpinner;
    private Spinner historySpinner;
    private ArrayAdapter<String> modelAdapter;
    private ArrayAdapter<String> historyAdapter;
    private ArrayList<ChatStore.SessionMeta> historyMetas = new ArrayList<>();
    private Button settingsButton;
    private Button historyButton;
    private Button browserButton;
    private Button browserBackButton;
    private Button browserForwardButton;
    private Button browserRefreshButton;
    private Button browserCloseButton;
    private Button loadHistoryButton;
    private Button deleteHistoryButton;
    private Button newHistoryButton;
    private Button saveSettingsButton;
    private Button editKeyButton;
    private Button forgetKeyButton;
    private Button clearSearchKeyButton;
    private Button refreshModelsButton;
    private Button sendButton;
    private Button stopButton;
    private Button imageButton;
    private Button fileButton;
    private Button searchButton;
    private Button imageGenButton;
    private Button reviseButton;
    private Button editLastButton;
    private Button regenerateButton;
    private Button clearButton;
    private Button updateButton;
    private WebView chatWebView;
    private WebView browserWebView;

    private boolean settingsVisible;
    private boolean historyVisible;
    private boolean keyInputForcedVisible;
    private boolean searchEnabled;
    private boolean webReady;
    private String lastResponseId = "";
    private String lastModel = "";
    private String lastAssistantText = "";
    private String lastUserPrompt = "";
    private String lastApiMode = "";
    private String conversationTranscript = "";
    private OpenAiClient.CancelToken activeCancelToken;
    private PowerManager.WakeLock activeWakeLock;
    private long activeUpdateDownloadId = -1L;
    private Runnable updateProgressRunnable;
    private final ArrayList<AttachmentItem> attachments = new ArrayList<>();
    private final ArrayList<Runnable> pendingWebActions = new ArrayList<>();
    private final Handler updateProgressHandler = new Handler(Looper.getMainLooper());
    private final BroadcastReceiver updateDownloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                return;
            }
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
            if (id == activeUpdateDownloadId) {
                installDownloadedUpdate(id);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        apiKeyStore = new ApiKeyStore(this);
        chatStore = new ChatStore(this);
        searchEnabled = apiKeyStore.loadSearchEnabled();
        currentSession = chatStore.loadCurrentOrCreate();
        restoreSessionState(currentSession);
        configureWindow();
        buildUi();
        registerUpdateReceiver();
        syncSettingsState(!apiKeyStore.hasSavedKey());
        setStatus(apiKeyStore.hasSavedKey() ? "准备好了" : "先保存 API key");
        maybeAutoCheckForUpdates();
    }

    @Override
    protected void onDestroy() {
        stopUpdateProgressMonitor();
        try {
            unregisterReceiver(updateDownloadReceiver);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (flags != 0) {
                getContentResolver().takePersistableUriPermission(uri, flags);
            }
        } catch (Exception ignored) {
        }
        addAttachment(uri, requestCode == REQUEST_IMAGE);
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(color(R.color.app_background));
        window.setNavigationBarColor(color(R.color.app_background));
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(8), dp(10), dp(8));
        root.setBackgroundColor(color(R.color.app_background));
        setContentView(root);

        buildTopBar(root);
        buildHistoryPanel(root);
        buildSettingsPanel(root);
        buildBrowserView(root);
        buildChatView(root);
        buildComposer(root);
    }

    private void buildTopBar(LinearLayout root) {
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(topBar, matchWrap());

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("Codex Mobile", 21, R.color.app_text, Typeface.BOLD);
        TextView subtitle = text("GPT-style chat", 12, R.color.app_muted, Typeface.NORMAL);
        titleBlock.addView(title, matchWrap());
        titleBlock.addView(subtitle, matchWrap());
        topBar.addView(titleBlock, weightWrap(1));

        browserButton = quietButton("网页");
        settingsButton = quietButton("设置");
        historyButton = quietButton("历史");
        topBar.addView(browserButton, wrapWrap());
        topBar.addView(historyButton, wrapWrap());
        topBar.addView(settingsButton, wrapWrap());
        browserButton.setOnClickListener(v -> openBrowserFromInput());
        historyButton.setOnClickListener(v -> {
            historyVisible = !historyVisible;
            syncHistoryState(historyVisible);
        });
        settingsButton.setOnClickListener(v -> {
            settingsVisible = !settingsVisible;
            syncSettingsState(settingsVisible);
        });

        statusView = text("", 13, R.color.app_muted, Typeface.NORMAL);
        statusView.setPadding(dp(10), dp(6), dp(10), dp(6));
        statusView.setBackground(roundedStroke(color(R.color.app_accent_soft), color(R.color.app_border), dp(9)));
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = dp(8);
        root.addView(statusView, statusParams);
    }

    private void buildSettingsPanel(LinearLayout root) {
        settingsPanel = new LinearLayout(this);
        settingsPanel.setOrientation(LinearLayout.VERTICAL);
        settingsPanel.setPadding(dp(12), dp(10), dp(12), dp(12));
        settingsPanel.setBackground(roundedStroke(color(R.color.app_panel), color(R.color.app_border), dp(12)));
        LinearLayout.LayoutParams panelParams = matchWrap();
        panelParams.topMargin = dp(8);
        root.addView(settingsPanel, panelParams);

        keyStatusView = text("", 14, R.color.app_text, Typeface.BOLD);
        settingsPanel.addView(keyStatusView, matchWrap());

        apiKeyInput = edit("OpenAI API key / 第三方 key");
        apiKeyInput.setSingleLine(true);
        apiKeyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        addPanelField(apiKeyInput);

        baseUrlInput = edit("接口基础地址，例如 https://example.com/v1");
        baseUrlInput.setSingleLine(true);
        addPanelField(baseUrlInput);

        apiModeSpinner = new Spinner(this);
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>(Arrays.asList(
                "Responses API",
                "Chat Completions"
        )));
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        apiModeSpinner.setAdapter(modeAdapter);
        addPanelField(apiModeSpinner);

        LinearLayout keyButtons = row();
        saveSettingsButton = primaryButton("保存");
        editKeyButton = quietButton("更换 Key");
        forgetKeyButton = quietButton("忘记 Key");
        keyButtons.addView(saveSettingsButton, weightWrap(1));
        keyButtons.addView(editKeyButton, weightWrap(1));
        keyButtons.addView(forgetKeyButton, weightWrap(1));
        addPanelField(keyButtons);

        LinearLayout modelRow = row();
        modelAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>(Arrays.asList(DEFAULT_MODELS)));
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modelSpinner = new Spinner(this);
        modelSpinner.setAdapter(modelAdapter);
        modelRow.addView(modelSpinner, weightWrap(1));
        refreshModelsButton = quietButton("刷新模型");
        modelRow.addView(refreshModelsButton, wrapWrap());
        addPanelField(modelRow);

        customModelInput = edit("自定义模型 ID，可留空");
        customModelInput.setSingleLine(true);
        addPanelField(customModelInput);

        imageModelInput = edit("生图模型，例如 image-2");
        imageModelInput.setSingleLine(true);
        addPanelField(imageModelInput);

        imageSizeSpinner = new Spinner(this);
        ArrayAdapter<String> sizeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>(Arrays.asList(
                "1024x1024",
                "1024x1536",
                "1536x1024",
                "512x512",
                "256x256"
        )));
        sizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        imageSizeSpinner.setAdapter(sizeAdapter);
        addPanelField(imageSizeSpinner);

        imageRouteSpinner = new Spinner(this);
        ArrayAdapter<String> routeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>(Arrays.asList(
                "Responses 工具生图",
                "Images 接口生图"
        )));
        routeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        imageRouteSpinner.setAdapter(routeAdapter);
        addPanelField(imageRouteSpinner);

        TextView searchTitle = text("联网搜索", 14, R.color.app_text, Typeface.BOLD);
        addPanelField(searchTitle);

        searchEndpointInput = edit("搜索接口地址，可留空自动尝试 DuckDuckGo/Bing");
        searchEndpointInput.setSingleLine(true);
        addPanelField(searchEndpointInput);

        searchApiKeyInput = edit("搜索 API key，可留空");
        searchApiKeyInput.setSingleLine(true);
        searchApiKeyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        addPanelField(searchApiKeyInput);

        searchAuthSpinner = new Spinner(this);
        ArrayAdapter<String> searchAuthAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>(Arrays.asList(
                "搜索不鉴权",
                "Authorization: Bearer",
                "X-API-Key",
                "api_key 参数"
        )));
        searchAuthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        searchAuthSpinner.setAdapter(searchAuthAdapter);
        addPanelField(searchAuthSpinner);

        searchCountSpinner = new Spinner(this);
        ArrayAdapter<String> searchCountAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>(Arrays.asList(
                "3",
                "5",
                "8",
                "10"
        )));
        searchCountAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        searchCountSpinner.setAdapter(searchCountAdapter);
        addPanelField(searchCountSpinner);

        clearSearchKeyButton = quietButton("清除搜索 Key");
        addPanelField(clearSearchKeyButton);

        updateButton = quietButton("检查更新");
        addPanelField(updateButton);

        saveSettingsButton.setOnClickListener(v -> saveSettings());
        editKeyButton.setOnClickListener(v -> {
            keyInputForcedVisible = true;
            syncSettingsState(true);
            apiKeyInput.requestFocus();
            showKeyboard(apiKeyInput);
        });
        forgetKeyButton.setOnClickListener(v -> forgetKey());
        refreshModelsButton.setOnClickListener(v -> refreshModels());
        updateButton.setOnClickListener(v -> checkForUpdates(true));
        clearSearchKeyButton.setOnClickListener(v -> {
            apiKeyStore.clearSearchApiKey();
            searchApiKeyInput.setText("");
            syncSettingsState(true);
            setStatus("搜索 Key 已清除");
        });
    }

    private void buildHistoryPanel(LinearLayout root) {
        historyPanel = new LinearLayout(this);
        historyPanel.setOrientation(LinearLayout.VERTICAL);
        historyPanel.setPadding(dp(12), dp(10), dp(12), dp(12));
        historyPanel.setBackground(roundedStroke(color(R.color.app_panel), color(R.color.app_border), dp(12)));
        LinearLayout.LayoutParams panelParams = matchWrap();
        panelParams.topMargin = dp(8);
        root.addView(historyPanel, panelParams);

        TextView title = text("历史聊天", 14, R.color.app_text, Typeface.BOLD);
        historyPanel.addView(title, matchWrap());

        historyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>());
        historyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        historySpinner = new Spinner(this);
        historySpinner.setAdapter(historyAdapter);
        addHistoryField(historySpinner);

        LinearLayout buttons = row();
        loadHistoryButton = primaryButton("打开");
        deleteHistoryButton = quietButton("删除");
        newHistoryButton = quietButton("新聊天");
        buttons.addView(loadHistoryButton, weightWrap(1));
        buttons.addView(deleteHistoryButton, weightWrap(1));
        buttons.addView(newHistoryButton, weightWrap(1));
        addHistoryField(buttons);

        loadHistoryButton.setOnClickListener(v -> loadSelectedSession());
        deleteHistoryButton.setOnClickListener(v -> deleteSelectedSession());
        newHistoryButton.setOnClickListener(v -> startNewSession());
        syncHistoryState(false);
    }

    private void buildBrowserView(LinearLayout root) {
        browserPanel = new LinearLayout(this);
        browserPanel.setOrientation(LinearLayout.VERTICAL);
        browserPanel.setVisibility(View.GONE);
        browserPanel.setBackgroundColor(color(R.color.app_background));

        LinearLayout browserBar = row();
        browserBar.setPadding(0, dp(6), 0, dp(6));
        browserBackButton = quietButton("←");
        browserForwardButton = quietButton("→");
        browserRefreshButton = quietButton("刷新");
        browserCloseButton = primaryButton("关闭");
        browserTitleView = text("网页", 13, R.color.app_muted, Typeface.NORMAL);
        browserTitleView.setSingleLine(true);
        browserBar.addView(browserBackButton, wrapWrap());
        browserBar.addView(browserForwardButton, wrapWrap());
        browserBar.addView(browserRefreshButton, wrapWrap());
        browserBar.addView(browserTitleView, weightWrap(1));
        browserBar.addView(browserCloseButton, wrapWrap());
        browserPanel.addView(browserBar, matchWrap());

        browserWebView = new WebView(this);
        browserWebView.setBackgroundColor(color(R.color.app_panel));
        WebSettings settings = browserWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        browserWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request == null ? null : request.getUrl();
                return handleBrowserNavigation(view, uri == null ? "" : uri.toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleBrowserNavigation(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                browserTitleView.setText(view.getTitle() == null || view.getTitle().isEmpty() ? url : view.getTitle());
                updateBrowserNavButtons();
            }
        });
        browserPanel.addView(browserWebView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        browserBackButton.setOnClickListener(v -> {
            if (browserWebView.canGoBack()) {
                browserWebView.goBack();
            }
            updateBrowserNavButtons();
        });
        browserForwardButton.setOnClickListener(v -> {
            if (browserWebView.canGoForward()) {
                browserWebView.goForward();
            }
            updateBrowserNavButtons();
        });
        browserRefreshButton.setOnClickListener(v -> browserWebView.reload());
        browserCloseButton.setOnClickListener(v -> closeBrowser());

        root.addView(browserPanel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
    }

    private void buildChatView(LinearLayout root) {
        chatWebView = new WebView(this);
        chatWebView.setBackgroundColor(color(R.color.app_background));
        WebSettings settings = chatWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setAllowContentAccess(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        chatWebView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        chatWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                webReady = true;
                for (Runnable action : new ArrayList<>(pendingWebActions)) {
                    action.run();
                }
                pendingWebActions.clear();
                renderSessionMessages(currentSession);
            }
        });
        chatWebView.loadUrl("file:///android_asset/chat.html");
        root.addView(chatWebView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
    }

    private void buildComposer(LinearLayout root) {
        attachmentsView = text("无附件", 12, R.color.app_muted, Typeface.NORMAL);
        attachmentsView.setPadding(dp(2), dp(4), dp(2), dp(4));
        root.addView(attachmentsView, matchWrap());

        LinearLayout toolRow = row();
        imageButton = quietButton("图片");
        fileButton = quietButton("文件");
        searchButton = quietButton("搜索关");
        imageGenButton = quietButton("生图");
        reviseButton = quietButton("修改要求");
        editLastButton = quietButton("编辑");
        regenerateButton = quietButton("重答");
        clearButton = quietButton("新聊天");
        toolRow.addView(imageButton, weightWrap(1));
        toolRow.addView(fileButton, weightWrap(1));
        toolRow.addView(searchButton, weightWrap(1));
        toolRow.addView(imageGenButton, weightWrap(1));
        root.addView(toolRow, matchWrap());

        LinearLayout toolRow2 = row();
        toolRow2.addView(reviseButton, weightWrap(1));
        toolRow2.addView(editLastButton, weightWrap(1));
        toolRow2.addView(regenerateButton, weightWrap(1));
        toolRow2.addView(clearButton, weightWrap(1));
        LinearLayout.LayoutParams row2Params = matchWrap();
        row2Params.topMargin = dp(6);
        root.addView(toolRow2, row2Params);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams inputRowParams = matchWrap();
        inputRowParams.topMargin = dp(8);
        root.addView(inputRow, inputRowParams);

        messageInput = edit("输入消息");
        messageInput.setMinLines(2);
        messageInput.setMaxLines(6);
        messageInput.setGravity(Gravity.TOP | Gravity.START);
        inputRow.addView(messageInput, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));

        sendButton = primaryButton("发送");
        stopButton = quietButton("停止");
        inputRow.addView(sendButton, wrapWrap());
        inputRow.addView(stopButton, wrapWrap());
        stopButton.setVisibility(View.GONE);

        imageButton.setOnClickListener(v -> pickImage());
        fileButton.setOnClickListener(v -> pickFile());
        searchButton.setOnClickListener(v -> toggleSearchMode());
        imageGenButton.setOnClickListener(v -> generateImageFromPrompt());
        reviseButton.setOnClickListener(v -> startRevision());
        editLastButton.setOnClickListener(v -> editLastPrompt());
        regenerateButton.setOnClickListener(v -> regenerateLast());
        clearButton.setOnClickListener(v -> clearChat());
        sendButton.setOnClickListener(v -> sendCurrentMessage(false));
        stopButton.setOnClickListener(v -> stopCurrentRequest());
        updateSearchButtonState();
    }

    private void syncSettingsState(boolean showPanel) {
        settingsVisible = showPanel;
        boolean hasKey = apiKeyStore.hasSavedKey();
        settingsPanel.setVisibility(showPanel ? View.VISIBLE : View.GONE);
        settingsButton.setText(showPanel ? "收起" : "设置");
        baseUrlInput.setText(apiKeyStore.loadBaseUrl());
        imageModelInput.setText(apiKeyStore.loadImageModel());
        setSpinnerToValue(imageSizeSpinner, apiKeyStore.loadImageSize());
        imageRouteSpinner.setSelection(ApiKeyStore.IMAGE_ROUTE_IMAGES_ENDPOINT.equals(apiKeyStore.loadImageRoute()) ? 1 : 0);
        apiModeSpinner.setSelection(ApiKeyStore.MODE_CHAT_COMPLETIONS.equals(apiKeyStore.loadApiMode()) ? 1 : 0);
        searchEndpointInput.setText(apiKeyStore.loadSearchEndpoint());
        searchAuthSpinner.setSelection(searchAuthPosition(apiKeyStore.loadSearchAuthMode()));
        setSpinnerToValue(searchCountSpinner, String.valueOf(apiKeyStore.loadSearchResultCount()));
        searchApiKeyInput.setText("");
        searchApiKeyInput.setHint(apiKeyStore.hasSavedSearchApiKey()
                ? "搜索 API key 已保存，留空不变"
                : "搜索 API key，可留空");
        clearSearchKeyButton.setVisibility(apiKeyStore.hasSavedSearchApiKey() ? View.VISIBLE : View.GONE);

        keyStatusView.setText(hasKey ? "API key 已保存" : "尚未保存 API key");
        boolean showKeyInput = !hasKey || keyInputForcedVisible;
        apiKeyInput.setVisibility(showKeyInput ? View.VISIBLE : View.GONE);
        if (!showKeyInput) {
            apiKeyInput.setText("");
        }
        editKeyButton.setVisibility(hasKey && !showKeyInput ? View.VISIBLE : View.GONE);
        forgetKeyButton.setVisibility(hasKey ? View.VISIBLE : View.GONE);
    }

    private void saveSettings() {
        try {
            String keyText = apiKeyInput.getText().toString().trim();
            boolean hasKey = apiKeyStore.hasSavedKey();
            if (!hasKey && keyText.isEmpty()) {
                toast("先输入 API key");
                return;
            }
            if (!keyText.isEmpty()) {
                apiKeyStore.save(keyText);
            }
            apiKeyStore.saveBaseUrl(baseUrlInput.getText().toString());
            apiKeyStore.saveApiMode(currentApiMode());
            apiKeyStore.saveImageModel(currentImageModel());
            apiKeyStore.saveImageSize(currentImageSize());
            apiKeyStore.saveImageRoute(currentImageRoute());
            apiKeyStore.saveSearchEndpoint(searchEndpointInput.getText().toString());
            apiKeyStore.saveSearchAuthMode(currentSearchAuthMode());
            apiKeyStore.saveSearchResultCount(currentSearchResultCount());
            String searchKeyText = searchApiKeyInput.getText().toString().trim();
            if (!searchKeyText.isEmpty()) {
                apiKeyStore.saveSearchApiKey(searchKeyText);
            }
            keyInputForcedVisible = false;
            apiKeyInput.setText("");
            searchApiKeyInput.setText("");
            syncSettingsState(false);
            setStatus("设置已保存");
        } catch (Exception e) {
            setStatus("保存失败: " + e.getMessage());
        }
    }

    private void forgetKey() {
        apiKeyStore.clearKey();
        lastResponseId = "";
        lastModel = "";
        lastAssistantText = "";
        conversationTranscript = "";
        keyInputForcedVisible = true;
        syncSettingsState(true);
        setStatus("API key 已删除");
    }

    private void refreshModels() {
        String apiKey = currentApiKey();
        if (apiKey.isEmpty()) {
            toast("先保存 API key");
            return;
        }
        refreshModelsButton.setEnabled(false);
        setStatus("正在刷新模型...");
        String baseUrl = currentBaseUrl();
        new Thread(() -> {
            try {
                List<String> models = OpenAiClient.fetchModels(baseUrl, apiKey);
                runOnUiThread(() -> {
                    if (!models.isEmpty()) {
                        modelAdapter.clear();
                        modelAdapter.addAll(models);
                        modelAdapter.notifyDataSetChanged();
                        setStatus("已刷新 " + models.size() + " 个模型");
                    } else {
                        setStatus("没有拿到模型列表，可手动填写模型 ID");
                    }
                    refreshModelsButton.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setStatus("刷新失败: " + e.getMessage());
                    refreshModelsButton.setEnabled(true);
                });
            }
        }).start();
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_IMAGE);
    }

    private void pickFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/pdf",
                "text/plain",
                "text/markdown",
                "text/csv",
                "application/json",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_FILE);
    }

    private void addAttachment(Uri uri, boolean image) {
        if (attachments.size() >= MAX_ATTACHMENTS) {
            toast("最多选择 " + MAX_ATTACHMENTS + " 个附件");
            return;
        }

        String name = "attachment";
        long size = -1;
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex);
                }
                if (sizeIndex >= 0) {
                    size = cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception ignored) {
        }

        if (size > MAX_ATTACHMENT_BYTES) {
            toast("附件超过 20MB");
            return;
        }

        String mime = getContentResolver().getType(uri);
        if (mime == null || mime.isEmpty()) {
            mime = image ? "image/jpeg" : "application/octet-stream";
        }
        attachments.add(new AttachmentItem(uri, name, mime, size, image));
        refreshAttachmentView();
    }

    private void toggleSearchMode() {
        searchEnabled = !searchEnabled;
        apiKeyStore.saveSearchEnabled(searchEnabled);
        updateSearchButtonState();
        setStatus(searchEnabled
                ? (currentSearchEndpoint().isEmpty() ? "已开启联网搜索，每条消息会使用内置搜索源" : "已开启联网搜索，每条消息会先联网搜索")
                : "已关闭联网搜索");
    }

    private void updateSearchButtonState() {
        if (searchButton == null) {
            return;
        }
        searchButton.setText(searchEnabled ? "搜索开" : "搜索关");
        searchButton.setTextColor(color(searchEnabled ? R.color.app_panel : R.color.app_text));
        searchButton.setBackground(searchEnabled
                ? roundedStroke(color(R.color.app_accent), color(R.color.app_accent), dp(11))
                : roundedStroke(color(R.color.app_panel), color(R.color.app_border), dp(11)));
    }

    private void sendCurrentMessage(boolean regenerate) {
        String apiKey = currentApiKey();
        String baseUrl = currentBaseUrl();
        String apiMode = currentApiMode();
        String model = currentModel();
        String prompt = messageInput.getText().toString().trim();
        boolean useSearch = searchEnabled;
        if (apiKey.isEmpty()) {
            toast("先保存 API key");
            syncSettingsState(true);
            return;
        }
        if (model.isEmpty()) {
            toast("请选择或填写模型 ID");
            return;
        }
        if (prompt.isEmpty() && attachments.isEmpty()) {
            toast("输入消息或选择附件");
            return;
        }
        if (useSearch && prompt.isEmpty()) {
            toast("联网搜索需要输入搜索问题");
            return;
        }
        String searchEndpoint = currentSearchEndpoint();

        ArrayList<AttachmentItem> pendingAttachments = new ArrayList<>(attachments);
        if (!regenerate) {
            lastUserPrompt = prompt;
            appendMessage("user", buildUserBlock(prompt, pendingAttachments, useSearch), "");
        }
        String searchAuthMode = currentSearchAuthMode();
        String searchApiKey = currentSearchApiKey();
        int searchResultCount = currentSearchResultCount();
        messageInput.setText("");
        setBusy(true);
        setStatus(useSearch ? "正在联网搜索..." : "正在思考...");
        setThinking(true);

        OpenAiClient.CancelToken token = new OpenAiClient.CancelToken();
        activeCancelToken = token;
        acquireRequestWakeLock();
        new Thread(() -> {
            try {
                ArrayList<SearchClient.SearchResult> searchResults = new ArrayList<>();
                String searchFailure = "";
                if (useSearch) {
                    try {
                        searchResults.addAll(SearchClient.search(
                                searchEndpoint,
                                searchAuthMode,
                                searchApiKey,
                                searchResultCount,
                                prompt,
                                token
                        ));
                        if (searchResults.isEmpty()) {
                            searchFailure = "联网搜索没有返回可用结果，已改为普通聊天。";
                        }
                    } catch (Exception searchError) {
                        if (token.isCanceled()) {
                            throw searchError;
                        }
                        searchFailure = "联网搜索失败，已改为普通聊天: " + searchError.getMessage();
                    }
                }
                runOnUiThread(() -> setStatus("正在思考..."));
                ArrayList<AttachmentPayload> payloads = buildAttachmentPayloads(pendingAttachments);
                String apiPrompt = buildApiPrompt(apiMode, prompt, searchResults);
                String previousResponseId = ApiKeyStore.MODE_RESPONSES.equals(apiMode)
                        && conversationTranscript.isEmpty()
                        && model.equals(lastModel)
                        ? lastResponseId
                        : "";
                OpenAiClient.ChatResult result = OpenAiClient.sendMessage(
                        apiMode,
                        baseUrl,
                        apiKey,
                        model,
                        apiPrompt,
                        payloads,
                        previousResponseId,
                        token
                );
                JSONArray sourceJson = SearchClient.toJsonArray(searchResults);
                String finalSearchFailure = searchFailure;
                runOnUiThread(() -> {
                    setThinking(false);
                    if (token.isCanceled()) {
                        finishStoppedRequest();
                        return;
                    }
                    lastResponseId = result.responseId;
                    lastModel = model;
                    lastApiMode = apiMode;
                    lastAssistantText = result.text;
                    rememberTurn(prompt, result.text);
                    attachments.clear();
                    refreshAttachmentView();
                    if (!finalSearchFailure.isEmpty()) {
                        appendMessage("system", finalSearchFailure, "");
                    }
                    appendMessage("assistant", result.text, result.reasoning, sourceJson);
                    setStatus("完成");
                    finishRequest();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setThinking(false);
                    if (token.isCanceled()) {
                        finishStoppedRequest();
                    } else {
                        appendMessage("system", e.getMessage(), "");
                        setStatus("发送失败");
                        finishRequest();
                    }
                });
            }
        }).start();
    }

    private void generateImageFromPrompt() {
        String apiKey = currentApiKey();
        String baseUrl = currentBaseUrl();
        String prompt = messageInput.getText().toString().trim();
        if (apiKey.isEmpty()) {
            toast("先保存 API key");
            syncSettingsState(true);
            return;
        }
        if (prompt.isEmpty()) {
            toast("输入生图提示词");
            return;
        }

        String route = currentImageRoute();
        String model = ApiKeyStore.IMAGE_ROUTE_RESPONSES_TOOL.equals(route) ? currentModel() : currentImageModel();
        String size = currentImageSize();
        if (model.isEmpty()) {
            toast(ApiKeyStore.IMAGE_ROUTE_RESPONSES_TOOL.equals(route) ? "请选择聊天模型" : "填写生图模型 ID");
            return;
        }
        lastUserPrompt = "生成图片：" + prompt;
        appendMessage("user", lastUserPrompt, "");
        messageInput.setText("");
        setBusy(true);
        setStatus("正在生成图片...");
        setThinking(true);

        OpenAiClient.CancelToken token = new OpenAiClient.CancelToken();
        activeCancelToken = token;
        new Thread(() -> {
            try {
                OpenAiClient.ImageResult result = ApiKeyStore.IMAGE_ROUTE_RESPONSES_TOOL.equals(route)
                        ? OpenAiClient.generateImageViaResponsesTool(
                        baseUrl,
                        apiKey,
                        model,
                        prompt,
                        size,
                        token
                )
                        : OpenAiClient.generateImage(
                        baseUrl,
                        apiKey,
                        model,
                        prompt,
                        size,
                        token
                );
                runOnUiThread(() -> {
                    setThinking(false);
                    if (token.isCanceled()) {
                        finishStoppedRequest();
                        return;
                    }
                    String imageSource = persistGeneratedImage(result.imageSource);
                    StringBuilder markdown = new StringBuilder();
                    markdown.append("![生成图片](").append(imageSource).append(")");
                    if (!result.revisedPrompt.isEmpty()) {
                        markdown.append("\n\n").append("优化后的提示词：").append(result.revisedPrompt);
                    }
                    lastAssistantText = markdown.toString();
                    lastApiMode = "images";
                    rememberTurn(lastUserPrompt, lastAssistantText);
                    appendMessage("assistant", lastAssistantText, "");
                    setStatus("图片已生成");
                    finishRequest();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setThinking(false);
                    if (token.isCanceled()) {
                        finishStoppedRequest();
                    } else {
                        appendMessage("system", imageFailureMessage(route, e.getMessage()), "");
                        setStatus("生图失败");
                        finishRequest();
                    }
                });
            }
        }).start();
    }

    private void stopCurrentRequest() {
        if (activeCancelToken == null) {
            return;
        }
        activeCancelToken.cancel();
        stopButton.setEnabled(false);
        setStatus("正在停止...");
    }

    private void finishStoppedRequest() {
        appendMessage("system", "已停止本次请求", "");
        setStatus("已停止");
        finishRequest();
    }

    private void finishRequest() {
        activeCancelToken = null;
        releaseRequestWakeLock();
        setBusy(false);
    }

    private ArrayList<AttachmentPayload> buildAttachmentPayloads(List<AttachmentItem> items) throws IOException {
        ArrayList<AttachmentPayload> payloads = new ArrayList<>();
        for (AttachmentItem item : items) {
            byte[] bytes = readAttachmentBytes(item);
            String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
            String mime = item.mimeType == null || item.mimeType.isEmpty()
                    ? "application/octet-stream"
                    : item.mimeType;
            String dataUrl = "data:" + mime + ";base64," + base64;
            payloads.add(new AttachmentPayload(item.name, dataUrl, item.image));
        }
        return payloads;
    }

    private byte[] readAttachmentBytes(AttachmentItem item) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(item.uri);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) {
                throw new IOException("无法读取附件: " + item.name);
            }
            byte[] buffer = new byte[8192];
            int read;
            long total = 0;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_ATTACHMENT_BYTES) {
                    throw new IOException("附件超过 20MB: " + item.name);
                }
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private String buildUserBlock(String prompt, List<AttachmentItem> items, boolean useSearch) {
        StringBuilder builder = new StringBuilder();
        if (!prompt.isEmpty()) {
            builder.append(prompt);
        }
        if (useSearch) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("[联网搜索已开启]");
        }
        for (AttachmentItem item : items) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append("[").append(item.displayLine()).append("]");
        }
        return builder.toString();
    }

    private String buildApiPrompt(String apiMode, String prompt) {
        return buildApiPrompt(apiMode, prompt, new ArrayList<>());
    }

    private String buildApiPrompt(String apiMode, String prompt, List<SearchClient.SearchResult> searchResults) {
        String promptWithSearch = buildPromptWithSearchContext(prompt, searchResults);
        String trimmed = promptWithSearch == null ? "" : promptWithSearch.trim();
        if (!lastAssistantText.isEmpty() && trimmed.startsWith("修改要求")) {
            return "上一条回复:\n" + lastAssistantText + "\n\n" + trimmed;
        }
        if (!conversationTranscript.isEmpty() && !trimmed.startsWith("修改要求")) {
            return "以下是当前对话上下文，用于保持连续对话。请只回答用户最新消息，不要复述上下文。\n\n"
                    + conversationTranscript
                    + "\n\n用户最新消息:\n" + trimmed;
        }
        return promptWithSearch;
    }

    private String buildPromptWithSearchContext(String prompt, List<SearchClient.SearchResult> searchResults) {
        if (searchResults == null || searchResults.isEmpty()) {
            return prompt;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("App 已经完成本次联网搜索，下面是实时搜索资料。你必须优先根据这些资料回答用户最新问题。不要说你不能联网、不能实时搜索、不能打开网页；如果资料不足，就说明资料不足并基于已有来源回答。使用资料中的事实时，请在句末标注对应来源编号，例如 [1]。不要编造资料中没有的来源。\n\n");
        builder.append("联网搜索资料:\n");
        for (int i = 0; i < searchResults.size(); i++) {
            SearchClient.SearchResult result = searchResults.get(i);
            builder.append("[").append(i + 1).append("] ");
            builder.append(result.title.isEmpty() ? "未命名来源" : result.title).append("\n");
            if (!result.url.isEmpty()) {
                builder.append("URL: ").append(result.url).append("\n");
            }
            if (!result.publishedAt.isEmpty()) {
                builder.append("时间: ").append(result.publishedAt).append("\n");
            }
            if (!result.snippet.isEmpty()) {
                builder.append("摘要: ").append(result.snippet).append("\n");
            }
            builder.append("\n");
        }
        builder.append("用户问题:\n").append(prompt == null ? "" : prompt.trim());
        return builder.toString();
    }

    private void rememberTurn(String user, String assistant) {
        String userText = user == null ? "" : user.trim();
        String assistantText = assistant == null ? "" : assistant.trim();
        if (userText.isEmpty() && assistantText.isEmpty()) {
            return;
        }
        StringBuilder builder = new StringBuilder(conversationTranscript);
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append("用户: ").append(userText);
        builder.append("\n助手: ").append(assistantText);
        conversationTranscript = trimTranscript(builder.toString());
    }

    private String trimTranscript(String transcript) {
        int maxChars = 16000;
        if (transcript.length() <= maxChars) {
            return transcript;
        }
        return transcript.substring(transcript.length() - maxChars);
    }

    private void startRevision() {
        if (lastResponseId.isEmpty() && lastAssistantText.isEmpty()) {
            toast("还没有可修改的回复");
            return;
        }
        messageInput.setText("修改要求：\n");
        messageInput.setSelection(messageInput.getText().length());
        messageInput.requestFocus();
        showKeyboard(messageInput);
        setStatus("写下修改要求后发送");
    }

    private void editLastPrompt() {
        if (lastUserPrompt.isEmpty()) {
            toast("还没有上一条消息");
            return;
        }
        messageInput.setText(lastUserPrompt);
        messageInput.setSelection(messageInput.getText().length());
        messageInput.requestFocus();
        showKeyboard(messageInput);
    }

    private void regenerateLast() {
        if (lastUserPrompt.isEmpty()) {
            toast("还没有可重新生成的消息");
            return;
        }
        if (!attachments.isEmpty()) {
            attachments.clear();
            refreshAttachmentView();
        }
        messageInput.setText(lastUserPrompt);
        sendCurrentMessage(true);
    }

    private String persistGeneratedImage(String imageSource) {
        if (imageSource == null || !imageSource.startsWith("data:image")) {
            return imageSource == null ? "" : imageSource;
        }
        int comma = imageSource.indexOf(',');
        if (comma < 0) {
            return imageSource;
        }
        try {
            String header = imageSource.substring(0, comma).toLowerCase();
            String ext = header.contains("jpeg") || header.contains("jpg") ? ".jpg" : ".png";
            byte[] bytes = Base64.decode(imageSource.substring(comma + 1), Base64.DEFAULT);
            File dir = new File(getFilesDir(), "generated_images");
            if (!dir.exists() && !dir.mkdirs()) {
                return imageSource;
            }
            File file = new File(dir, "image_" + System.currentTimeMillis() + ext);
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(bytes);
            }
            return file.toURI().toString();
        } catch (Exception ignored) {
            return imageSource;
        }
    }

    private void clearChat() {
        startNewSession();
    }

    private void startNewSession() {
        lastResponseId = "";
        lastModel = "";
        lastAssistantText = "";
        lastUserPrompt = "";
        lastApiMode = "";
        conversationTranscript = "";
        attachments.clear();
        messageInput.setText("");
        refreshAttachmentView();
        currentSession = chatStore.createSession();
        clearWebChat();
        refreshHistoryList();
        historyVisible = false;
        syncHistoryState(false);
        setStatus("新聊天");
    }

    private void restoreSessionState(ChatStore.Session session) {
        if (session == null) {
            return;
        }
        lastResponseId = session.responseId == null ? "" : session.responseId;
        lastModel = session.lastModel == null ? "" : session.lastModel;
        lastApiMode = session.apiMode == null ? "" : session.apiMode;
        lastAssistantText = session.lastAssistantText == null ? "" : session.lastAssistantText;
        lastUserPrompt = session.lastUserPrompt == null ? "" : session.lastUserPrompt;
        conversationTranscript = session.transcript == null ? "" : session.transcript;
        if (conversationTranscript.trim().isEmpty()) {
            conversationTranscript = rebuildTranscriptFromMessages(session.messages);
        }
    }

    private String rebuildTranscriptFromMessages(JSONArray messages) {
        if (messages == null) {
            return "";
        }
        String pendingUser = "";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) {
                continue;
            }
            String role = message.optString("role", "");
            String textValue = message.optString("text", "").trim();
            if (textValue.isEmpty()) {
                continue;
            }
            if ("user".equals(role)) {
                pendingUser = textValue;
            } else if ("assistant".equals(role)) {
                if (builder.length() > 0) {
                    builder.append("\n\n");
                }
                builder.append("用户: ").append(pendingUser);
                builder.append("\n助手: ").append(textValue);
                pendingUser = "";
            }
        }
        return trimTranscript(builder.toString());
    }

    private void saveCurrentSession() {
        if (currentSession == null) {
            return;
        }
        currentSession.responseId = lastResponseId;
        currentSession.lastModel = lastModel;
        currentSession.apiMode = lastApiMode;
        currentSession.lastAssistantText = lastAssistantText;
        currentSession.lastUserPrompt = lastUserPrompt;
        currentSession.transcript = conversationTranscript;
        chatStore.save(currentSession);
    }

    private void renderSessionMessages(ChatStore.Session session) {
        if (session == null || !webReady) {
            return;
        }
        clearWebChat();
        JSONArray messages = session.messages == null ? new JSONArray() : session.messages;
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) {
                continue;
            }
            appendMessageToWeb(
                    message.optString("role", "assistant"),
                    message.optString("text", ""),
                    message.optString("reasoning", ""),
                    message.optJSONArray("sources")
            );
        }
    }

    private String titleFromPrompt(String text) {
        String value = text == null ? "" : text.replace("\n", " ").trim();
        if (value.isEmpty()) {
            return "新聊天";
        }
        return value.length() > 22 ? value.substring(0, 22) + "..." : value;
    }

    private void syncHistoryState(boolean showPanel) {
        historyVisible = showPanel;
        historyPanel.setVisibility(showPanel ? View.VISIBLE : View.GONE);
        historyButton.setText(showPanel ? "收起" : "历史");
        if (showPanel) {
            refreshHistoryList();
        }
    }

    private void refreshHistoryList() {
        if (historyAdapter == null) {
            return;
        }
        historyMetas = new ArrayList<>(chatStore.listSessions());
        historyAdapter.clear();
        for (ChatStore.SessionMeta meta : historyMetas) {
            historyAdapter.add(meta.label());
        }
        historyAdapter.notifyDataSetChanged();
    }

    private void loadSelectedSession() {
        int position = historySpinner.getSelectedItemPosition();
        if (position < 0 || position >= historyMetas.size()) {
            toast("没有可打开的历史");
            return;
        }
        ChatStore.Session session = chatStore.load(historyMetas.get(position).id);
        if (session == null) {
            toast("历史记录读取失败");
            return;
        }
        currentSession = session;
        chatStore.setCurrentSessionId(session.id);
        restoreSessionState(session);
        renderSessionMessages(session);
        historyVisible = false;
        syncHistoryState(false);
        setStatus("已打开历史: " + session.title);
    }

    private void deleteSelectedSession() {
        int position = historySpinner.getSelectedItemPosition();
        if (position < 0 || position >= historyMetas.size()) {
            toast("没有可删除的历史");
            return;
        }
        String id = historyMetas.get(position).id;
        chatStore.delete(id);
        if (currentSession != null && id.equals(currentSession.id)) {
            startNewSession();
        } else {
            refreshHistoryList();
        }
        setStatus("已删除历史");
    }

    private String currentApiKey() {
        String visibleKey = apiKeyInput.getVisibility() == View.VISIBLE
                ? apiKeyInput.getText().toString().trim()
                : "";
        if (!visibleKey.isEmpty()) {
            return visibleKey;
        }
        return apiKeyStore.load();
    }

    private String currentBaseUrl() {
        String visibleValue = baseUrlInput.getText().toString().trim();
        if (!visibleValue.isEmpty()) {
            return normalizeBaseUrl(visibleValue);
        }
        return apiKeyStore.loadBaseUrl();
    }

    private String currentApiMode() {
        Object selected = apiModeSpinner.getSelectedItem();
        if (selected != null && selected.toString().contains("Chat")) {
            return ApiKeyStore.MODE_CHAT_COMPLETIONS;
        }
        return ApiKeyStore.MODE_RESPONSES;
    }

    private String currentModel() {
        String custom = customModelInput.getText().toString().trim();
        if (!custom.isEmpty()) {
            return custom;
        }
        Object selected = modelSpinner.getSelectedItem();
        return selected == null ? "" : selected.toString();
    }

    private String currentImageModel() {
        String model = imageModelInput == null ? "" : imageModelInput.getText().toString().trim();
        return model.isEmpty() ? apiKeyStore.loadImageModel() : model;
    }

    private String currentImageSize() {
        Object selected = imageSizeSpinner == null ? null : imageSizeSpinner.getSelectedItem();
        return selected == null ? apiKeyStore.loadImageSize() : selected.toString();
    }

    private String currentImageRoute() {
        Object selected = imageRouteSpinner == null ? null : imageRouteSpinner.getSelectedItem();
        if (selected != null && selected.toString().contains("Images")) {
            return ApiKeyStore.IMAGE_ROUTE_IMAGES_ENDPOINT;
        }
        return ApiKeyStore.IMAGE_ROUTE_RESPONSES_TOOL;
    }

    private String currentSearchEndpoint() {
        String value = searchEndpointInput == null ? "" : searchEndpointInput.getText().toString().trim();
        return value.isEmpty() ? apiKeyStore.loadSearchEndpoint() : value;
    }

    private String currentSearchApiKey() {
        String visibleValue = searchApiKeyInput == null ? "" : searchApiKeyInput.getText().toString().trim();
        return visibleValue.isEmpty() ? apiKeyStore.loadSearchApiKey() : visibleValue;
    }

    private String currentSearchAuthMode() {
        Object selected = searchAuthSpinner == null ? null : searchAuthSpinner.getSelectedItem();
        String label = selected == null ? "" : selected.toString();
        if (label.contains("Bearer")) {
            return ApiKeyStore.SEARCH_AUTH_BEARER;
        }
        if (label.contains("X-API-Key")) {
            return ApiKeyStore.SEARCH_AUTH_X_API_KEY;
        }
        if (label.contains("api_key")) {
            return ApiKeyStore.SEARCH_AUTH_QUERY_API_KEY;
        }
        return ApiKeyStore.SEARCH_AUTH_NONE;
    }

    private int currentSearchResultCount() {
        Object selected = searchCountSpinner == null ? null : searchCountSpinner.getSelectedItem();
        String value = selected == null ? "" : selected.toString();
        try {
            return Math.max(1, Math.min(10, Integer.parseInt(value)));
        } catch (Exception ignored) {
            return apiKeyStore.loadSearchResultCount();
        }
    }

    private int searchAuthPosition(String authMode) {
        if (ApiKeyStore.SEARCH_AUTH_BEARER.equals(authMode)) {
            return 1;
        }
        if (ApiKeyStore.SEARCH_AUTH_X_API_KEY.equals(authMode)) {
            return 2;
        }
        if (ApiKeyStore.SEARCH_AUTH_QUERY_API_KEY.equals(authMode)) {
            return 3;
        }
        return 0;
    }

    private String imageFailureMessage(String route, String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage;
        if (message.contains("No available channel") || message.contains("image_generation") || message.contains("images/generations")) {
            if (ApiKeyStore.IMAGE_ROUTE_RESPONSES_TOOL.equals(route)) {
                return message + "\n\n当前使用的是 Responses 工具生图。你的第三方接口需要支持 /v1/responses 里的 image_generation 工具；如果不支持，请在设置里切到 Images 接口生图，或让服务商给当前分组开通生图工具通道。";
            }
            return message + "\n\n当前使用的是 Images 接口生图。这个错误通常表示第三方服务商没有给当前分组开通该生图模型通道；可以在设置里改用 Responses 工具生图，或向服务商确认 image-2 / 其它生图模型是否可用。";
        }
        return message;
    }

    private void setSpinnerToValue(Spinner spinner, String value) {
        if (spinner == null || value == null) {
            return;
        }
        for (int i = 0; i < spinner.getCount(); i++) {
            Object item = spinner.getItemAtPosition(i);
            if (item != null && value.equals(item.toString())) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private String normalizeBaseUrl(String value) {
        String baseUrl = value == null || value.trim().isEmpty()
                ? ApiKeyStore.defaultBaseUrl()
                : value.trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String lower = baseUrl.toLowerCase();
        String[] suffixes = {"/chat/completions", "/images/generations", "/responses", "/models"};
        for (String suffix : suffixes) {
            if (lower.endsWith(suffix)) {
                return baseUrl.substring(0, baseUrl.length() - suffix.length());
            }
        }
        return baseUrl;
    }

    private void appendMessage(String role, String text, String reasoning) {
        appendMessage(role, text, reasoning, null);
    }

    private void appendMessage(String role, String text, String reasoning, JSONArray sources) {
        appendMessageToWeb(role, text, reasoning, sources);
        persistMessage(role, text, reasoning, sources);
    }

    private void appendMessageToWeb(String role, String text, String reasoning) {
        appendMessageToWeb(role, text, reasoning, null);
    }

    private void appendMessageToWeb(String role, String text, String reasoning, JSONArray sources) {
        String sourceJson = sources == null ? "[]" : sources.toString();
        runChatJs("window.ChatView.addMessage(" + js(role) + "," + js(text) + "," + js(reasoning) + "," + sourceJson + ");");
    }

    private void persistMessage(String role, String text, String reasoning) {
        persistMessage(role, text, reasoning, null);
    }

    private void persistMessage(String role, String text, String reasoning, JSONArray sources) {
        if (currentSession == null) {
            currentSession = chatStore.createSession();
        }
        try {
            JSONObject json = new JSONObject();
            json.put("role", role);
            json.put("text", text == null ? "" : text);
            json.put("reasoning", reasoning == null ? "" : reasoning);
            if (sources != null && sources.length() > 0) {
                json.put("sources", new JSONArray(sources.toString()));
            }
            json.put("time", System.currentTimeMillis());
            currentSession.messages.put(json);
            if ("user".equals(role) && ("新聊天".equals(currentSession.title) || currentSession.title.trim().isEmpty())) {
                currentSession.title = titleFromPrompt(text);
            }
            saveCurrentSession();
            refreshHistoryList();
        } catch (Exception ignored) {
        }
    }

    private void setThinking(boolean active) {
        runChatJs("window.ChatView.setThinking(" + (active ? "true" : "false") + ");");
    }

    private void clearWebChat() {
        runChatJs("window.ChatView.clearMessages();");
    }

    private void runChatJs(String script) {
        Runnable action = () -> chatWebView.evaluateJavascript(script, null);
        if (webReady) {
            action.run();
        } else {
            pendingWebActions.add(action);
        }
    }

    private String js(String value) {
        return JSONObject.quote(value == null ? "" : value);
    }

    private void refreshAttachmentView() {
        if (attachments.isEmpty()) {
            attachmentsView.setText("无附件");
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (AttachmentItem item : attachments) {
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(item.displayLine());
        }
        attachmentsView.setText(builder.toString());
    }

    private void acquireRequestWakeLock() {
        try {
            releaseRequestWakeLock();
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager == null) {
                return;
            }
            activeWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CodexMobile:NetworkRequest");
            activeWakeLock.setReferenceCounted(false);
            activeWakeLock.acquire(5L * 60L * 1000L);
        } catch (Exception ignored) {
        }
    }

    private void releaseRequestWakeLock() {
        try {
            if (activeWakeLock != null && activeWakeLock.isHeld()) {
                activeWakeLock.release();
            }
        } catch (Exception ignored) {
        } finally {
            activeWakeLock = null;
        }
    }

    private void registerUpdateReceiver() {
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(updateDownloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(updateDownloadReceiver, filter);
        }
    }

    private void maybeAutoCheckForUpdates() {
        long now = System.currentTimeMillis();
        long last = getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE).getLong(PREF_LAST_UPDATE_CHECK, 0L);
        if (now - last < UPDATE_CHECK_INTERVAL_MS) {
            return;
        }
        getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE)
                .edit()
                .putLong(PREF_LAST_UPDATE_CHECK, now)
                .apply();
        statusView.postDelayed(() -> checkForUpdates(false), 1200);
    }

    private void checkForUpdates(boolean manual) {
        if (updateButton != null) {
            updateButton.setEnabled(false);
        }
        if (manual) {
            setStatus("正在检查更新...");
        }
        OpenAiClient.CancelToken token = new OpenAiClient.CancelToken();
        new Thread(() -> {
            try {
                UpdateClient.UpdateInfo info = UpdateClient.fetchLatest(token);
                boolean newer = UpdateClient.isNewer(info, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE);
                runOnUiThread(() -> {
                    if (updateButton != null) {
                        updateButton.setEnabled(true);
                    }
                    if (!info.hasApk()) {
                        if (manual) {
                            setStatus("最新发布没有 APK 附件");
                        }
                        return;
                    }
                    if (newer) {
                        showUpdateDialog(info, true);
                    } else if (manual) {
                        showUpdateDialog(info, false);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (updateButton != null) {
                        updateButton.setEnabled(true);
                    }
                    if (manual) {
                        setStatus("检查更新失败: " + e.getMessage());
                    }
                });
            }
        }).start();
    }

    private void showUpdateDialog(UpdateClient.UpdateInfo info, boolean newer) {
        String title = newer ? "发现新版本 " + info.normalizedVersion() : "当前已是最新版";
        String message = newer
                ? "当前版本 " + BuildConfig.VERSION_NAME + "，可下载并安装最新版 APK。"
                : "当前版本 " + BuildConfig.VERSION_NAME + "。也可以重新下载 GitHub Release 里的最新版 APK。";
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(newer ? "下载更新" : "重新下载", (dialog, which) -> downloadUpdate(info))
                .setNegativeButton("稍后", null)
                .show();
    }

    private void downloadUpdate(UpdateClient.UpdateInfo info) {
        if (info == null || !info.hasApk()) {
            setStatus("没有可下载的 APK");
            return;
        }
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(info.assetUrl));
            request.setTitle("Codex Mobile " + info.normalizedVersion());
            request.setDescription("正在下载更新安装包");
            request.setMimeType("application/vnd.android.package-archive");
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, UPDATE_APK_NAME);
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) {
                setStatus("系统下载服务不可用");
                return;
            }
            activeUpdateDownloadId = manager.enqueue(request);
            setStatus("更新下载中 0%");
            startUpdateProgressMonitor(activeUpdateDownloadId);
        } catch (Exception e) {
            setStatus("下载更新失败: " + e.getMessage());
        }
    }

    private void startUpdateProgressMonitor(long downloadId) {
        stopUpdateProgressMonitor();
        updateProgressRunnable = new Runnable() {
            @Override
            public void run() {
                boolean keepPolling = updateDownloadProgress(downloadId);
                if (keepPolling && updateProgressRunnable != null) {
                    updateProgressHandler.postDelayed(this, 1000);
                }
            }
        };
        updateProgressHandler.post(updateProgressRunnable);
    }

    private void stopUpdateProgressMonitor() {
        if (updateProgressRunnable != null) {
            updateProgressHandler.removeCallbacks(updateProgressRunnable);
            updateProgressRunnable = null;
        }
    }

    private boolean updateDownloadProgress(long downloadId) {
        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager == null) {
            setStatus("系统下载服务不可用");
            activeUpdateDownloadId = -1L;
            stopUpdateProgressMonitor();
            return false;
        }
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor cursor = manager.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) {
                setStatus("更新下载任务已不可用");
                activeUpdateDownloadId = -1L;
                stopUpdateProgressMonitor();
                return false;
            }
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            long downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            long total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                setStatus("更新下载完成，正在打开安装器...");
                stopUpdateProgressMonitor();
                installDownloadedUpdate(downloadId);
                return false;
            }
            if (status == DownloadManager.STATUS_FAILED) {
                int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                setStatus("更新下载失败: " + downloadReasonText(reason));
                activeUpdateDownloadId = -1L;
                stopUpdateProgressMonitor();
                return false;
            }
            String progress = downloadProgressText(downloaded, total);
            if (status == DownloadManager.STATUS_PAUSED) {
                int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                setStatus("更新下载暂停 " + progress + ": " + downloadReasonText(reason));
            } else if (status == DownloadManager.STATUS_PENDING) {
                setStatus("更新等待下载 " + progress);
            } else {
                setStatus("更新下载中 " + progress);
            }
            return true;
        } catch (Exception e) {
            setStatus("读取下载进度失败: " + e.getMessage());
            return true;
        }
    }

    private String downloadProgressText(long downloaded, long total) {
        if (total > 0) {
            int percent = (int) Math.max(0, Math.min(100, downloaded * 100L / total));
            return percent + "% · " + formatBytes(downloaded) + "/" + formatBytes(total);
        }
        return formatBytes(downloaded);
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) {
            return "未知大小";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        }
        return String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
    }

    private String downloadReasonText(int reason) {
        switch (reason) {
            case DownloadManager.PAUSED_WAITING_TO_RETRY:
                return "等待重试";
            case DownloadManager.PAUSED_WAITING_FOR_NETWORK:
                return "等待网络";
            case DownloadManager.PAUSED_QUEUED_FOR_WIFI:
                return "等待 Wi-Fi";
            case DownloadManager.PAUSED_UNKNOWN:
                return "未知暂停原因";
            case DownloadManager.ERROR_CANNOT_RESUME:
                return "无法继续下载";
            case DownloadManager.ERROR_DEVICE_NOT_FOUND:
                return "存储不可用";
            case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
                return "文件已存在";
            case DownloadManager.ERROR_FILE_ERROR:
                return "文件写入失败";
            case DownloadManager.ERROR_HTTP_DATA_ERROR:
                return "HTTP 数据错误";
            case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                return "存储空间不足";
            case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
                return "重定向过多";
            case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
                return "HTTP 状态异常";
            case DownloadManager.ERROR_UNKNOWN:
                return "未知错误";
            default:
                return "代码 " + reason;
        }
    }

    private void installDownloadedUpdate(long downloadId) {
        stopUpdateProgressMonitor();
        activeUpdateDownloadId = -1L;
        try {
            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) {
                return;
            }
            Uri uri = manager.getUriForDownloadedFile(downloadId);
            if (uri == null) {
                setStatus("更新包下载失败或已被清理");
                return;
            }
            openApkInstaller(uri);
        } catch (Exception e) {
            setStatus("打开安装器失败: " + e.getMessage());
        }
    }

    private void openApkInstaller(Uri uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            Intent settingsIntent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivity(settingsIntent);
            setStatus("请允许本应用安装未知应用，然后重新点击检查更新");
            return;
        }
        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(uri, "application/vnd.android.package-archive");
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(install);
        setStatus("已打开系统安装器");
    }

    private void openBrowserFromInput() {
        String value = messageInput == null ? "" : messageInput.getText().toString().trim();
        if (value.isEmpty()) {
            toast("在输入框输入网址，或点击搜索来源链接");
            return;
        }
        String firstLine = value.split("\\s+", 2)[0];
        if (!firstLine.startsWith("http://") && !firstLine.startsWith("https://")) {
            if (firstLine.contains(".") && !firstLine.contains(" ")) {
                firstLine = "https://" + firstLine;
            } else {
                toast("输入 http:// 或 https:// 开头的网址");
                return;
            }
        }
        openUrlInApp(firstLine);
    }

    private void openUrlInApp(String rawUrl) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            toast("只能在 App 内打开网页链接");
            return;
        }
        browserPanel.setVisibility(View.VISIBLE);
        chatWebView.setVisibility(View.GONE);
        browserTitleView.setText(url);
        browserWebView.loadUrl(url);
        updateBrowserNavButtons();
        setStatus("正在打开网页...");
    }

    private void closeBrowser() {
        browserPanel.setVisibility(View.GONE);
        chatWebView.setVisibility(View.VISIBLE);
        setStatus("已关闭网页");
    }

    private boolean handleBrowserNavigation(WebView view, String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return false;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            toast("无法打开链接: " + e.getMessage());
        }
        return true;
    }

    private void updateBrowserNavButtons() {
        if (browserWebView == null) {
            return;
        }
        browserBackButton.setEnabled(browserWebView.canGoBack());
        browserForwardButton.setEnabled(browserWebView.canGoForward());
    }

    private void setBusy(boolean busy) {
        sendButton.setVisibility(busy ? View.GONE : View.VISIBLE);
        stopButton.setVisibility(busy ? View.VISIBLE : View.GONE);
        stopButton.setEnabled(busy);
        imageButton.setEnabled(!busy);
        fileButton.setEnabled(!busy);
        searchButton.setEnabled(!busy);
        imageGenButton.setEnabled(!busy);
        reviseButton.setEnabled(!busy);
        editLastButton.setEnabled(!busy);
        regenerateButton.setEnabled(!busy);
        clearButton.setEnabled(!busy);
        settingsButton.setEnabled(!busy);
        browserButton.setEnabled(!busy);
        if (updateButton != null) {
            updateButton.setEnabled(!busy);
        }
    }

    private void setStatus(String textValue) {
        statusView.setText(textValue);
    }

    private void showKeyboard(View view) {
        view.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 150);
    }

    private void toast(String textValue) {
        Toast.makeText(this, textValue, Toast.LENGTH_SHORT).show();
    }

    private void addPanelField(View view) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(8);
        settingsPanel.addView(view, params);
    }

    private void addHistoryField(View view) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(8);
        historyPanel.addView(view, params);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView text(String value, int sp, int colorRes, int typefaceStyle) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color(colorRes));
        view.setTypeface(Typeface.DEFAULT, typefaceStyle);
        view.setLineSpacing(dp(2), 1.0f);
        return view;
    }

    private EditText edit(String hint) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setTextSize(15);
        editText.setTextColor(color(R.color.app_text));
        editText.setHintTextColor(color(R.color.app_muted));
        editText.setPadding(dp(11), dp(9), dp(11), dp(9));
        editText.setBackground(roundedStroke(color(R.color.app_panel), color(R.color.app_border), dp(11)));
        return editText;
    }

    private Button primaryButton(String label) {
        Button button = baseButton(label);
        button.setTextColor(color(R.color.app_panel));
        button.setBackground(roundedStroke(color(R.color.app_accent), color(R.color.app_accent), dp(11)));
        return button;
    }

    private Button quietButton(String label) {
        Button button = baseButton(label);
        button.setTextColor(color(R.color.app_text));
        button.setBackground(roundedStroke(color(R.color.app_panel), color(R.color.app_border), dp(11)));
        return button;
    }

    private Button baseButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setMinHeight(dp(40));
        button.setMinWidth(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        return button;
    }

    private GradientDrawable roundedStroke(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams wrapWrap() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = dp(8);
        return params;
    }

    private LinearLayout.LayoutParams weightWrap(float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                weight
        );
        params.rightMargin = dp(5);
        return params;
    }

    private int color(int colorRes) {
        return getResources().getColor(colorRes, getTheme());
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    class AndroidBridge {
        @JavascriptInterface
        public void copyText(String text) {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("message", text));
                runOnUiThread(() -> toast("已复制"));
            }
        }

        @JavascriptInterface
        public void openUrl(String url) {
            runOnUiThread(() -> openUrlInApp(url));
        }
    }
}
