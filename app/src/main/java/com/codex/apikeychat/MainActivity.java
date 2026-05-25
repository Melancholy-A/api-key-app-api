package com.codex.apikeychat;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.DecelerateInterpolator;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int REQUEST_IMAGE = 1001;
    private static final int REQUEST_FILE = 1002;
    private static final int REQUEST_CAMERA = 1003;
    private static final int REQUEST_CROP = 1004;
    private static final int MAX_RENDERED_HISTORY_MESSAGES = 20;
    private static final int MAX_ATTACHMENTS = 6;
    private static final int MAX_GENERATED_OFFICE_CONTEXT = 12;
    private static final int CONTEXT_RECENT_MESSAGE_COUNT = 12;
    private static final int CONTEXT_DIRECT_TRANSCRIPT_CHARS = 16000;
    private static final int CONTEXT_SUMMARY_CHARS = 6000;
    private static final int CONTEXT_RECENT_TRANSCRIPT_CHARS = 12000;
    private static final int CONTEXT_SEARCH_LIMIT = 8;
    private static final long MAX_ATTACHMENT_BYTES = 20L * 1024L * 1024L;
    private static final long MAX_OFFICE_ATTACHMENT_BYTES = 120L * 1024L * 1024L;
    private static final int MAX_IMAGE_UPLOAD_DIMENSION = 1600;
    private static final int MAX_EDIT_IMAGE_DIMENSION = 1600;
    private static final int JPEG_UPLOAD_QUALITY = 85;
    private static final int JPEG_EDIT_QUALITY = 92;
    private static final long UPDATE_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L;
    private static final long EXPAND_ANIMATION_MS = 300L;
    private static final long PAGE_ANIMATION_MS = 300L;
    private static final long STREAM_UI_FLUSH_MS = 90L;
    private static final String UPDATE_PREFS = "app_update";
    private static final String PREF_LAST_UPDATE_CHECK = "last_update_check";
    private static final String UPDATE_APK_NAME = "CodexMobile-debug.apk";
    private static final Pattern DATA_IMAGE_PATTERN = Pattern.compile("data:image/(?:png|jpeg|jpg|webp);base64,[A-Za-z0-9+/=\\r\\n]+");
    private static final Pattern BASE64_IMAGE_PATTERN = Pattern.compile("^[A-Za-z0-9+/=\\r\\n]+$");
    private static final int RAW_IMAGE_BASE64_MIN_CHARS = 4096;
    private static final String META_NODE_ID = "node_id";
    private static final String META_PARENT_ID = "parent_id";
    private static final String META_ACTIVE_CHILD_ID = "active_child_id";
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
    private ScrollView settingsScrollView;
    private LinearLayout settingsPanel;
    private LinearLayout historyPanel;
    private ScrollView historyScrollView;
    private LinearLayout historyList;
    private LinearLayout browserPanel;
    private LinearLayout toolPanel;
    private LinearLayout keyActionRow;
    private TextView statusView;
    private TextView keyStatusView;
    private LinearLayout attachmentsView;
    private TextView browserTitleView;
    private EditText apiKeyInput;
    private EditText baseUrlInput;
    private EditText imageModelInput;
    private EditText searchEndpointInput;
    private EditText searchApiKeyInput;
    private EditText customModelInput;
    private EditText customInstructionsInput;
    private EditText messageInput;
    private Spinner modelSpinner;
    private Spinner apiModeSpinner;
    private Spinner reasoningEffortSpinner;
    private Spinner imageRouteSpinner;
    private Spinner imageSizeSpinner;
    private Spinner searchProviderSpinner;
    private Spinner searchAuthSpinner;
    private Spinner searchCountSpinner;
    private Spinner agentToolsSpinner;
    private Spinner agentImageToolSpinner;
    private Spinner launchModeSpinner;
    private EditText historySearchInput;
    private ArrayAdapter<String> modelAdapter;
    private ArrayList<ChatStore.SessionMeta> historyMetas = new ArrayList<>();
    private Button settingsButton;
    private Button historyButton;
    private Button browserButton;
    private Button browserBackButton;
    private Button browserForwardButton;
    private Button browserRefreshButton;
    private Button browserLayoutButton;
    private Button browserCloseButton;
    private Button saveSettingsButton;
    private Button editKeyButton;
    private Button forgetKeyButton;
    private Button clearSearchKeyButton;
    private Button refreshModelsButton;
    private Button sendButton;
    private Button stopButton;
    private Button imageButton;
    private Button fileButton;
    private Button imageGenButton;
    private Button imageLibraryButton;
    private Button toolsToggleButton;
    private Button updateButton;
    private WebView chatWebView;
    private WebView browserWebView;

    private boolean settingsVisible;
    private boolean historyVisible;
    private boolean keyInputForcedVisible;
    private boolean toolsCollapsed = true;
    private boolean browserFitMode = true;
    private boolean webReady;
    private boolean ttsReady;
    private String lastResponseId = "";
    private String lastModel = "";
    private String lastAssistantText = "";
    private String lastUserPrompt = "";
    private String lastApiMode = "";
    private String revisionTargetText = "";
    private String conversationTranscript = "";
    private String pendingAssistantParentNodeId = "";
    private int pendingBranchParentMessageIndex = -1;
    private int historyRenderStartIndex = 0;
    private int historyRenderGeneration = 0;
    private OpenAiClient.CancelToken activeCancelToken;
    private StreamingUiBuffer activeStreamingUi;
    private PowerManager.WakeLock activeWakeLock;
    private TextToSpeech textToSpeech;
    private Uri pendingCameraUri;
    private Uri pendingCropOutputUri;
    private long activeUpdateDownloadId = -1L;
    private Runnable updateProgressRunnable;
    private final ArrayList<AttachmentItem> attachments = new ArrayList<>();
    private final ArrayList<GeneratedOfficeFile> generatedOfficeFiles = new ArrayList<>();
    private final ArrayList<GeneratedOfficeFile> pendingGeneratedOfficeFiles = new ArrayList<>();
    private final Object generatedOfficeLock = new Object();
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
        OfficeProcessor.initAndroidPoi();
        apiKeyStore = new ApiKeyStore(this);
        chatStore = new ChatStore(this);
        apiKeyStore.saveSearchEnabled(false);
        currentSession = apiKeyStore.loadStartNewOnLaunch()
                ? chatStore.createSession()
                : chatStore.loadCurrentOrCreate();
        restoreSessionState(currentSession);
        configureWindow();
        buildUi();
        initTextToSpeech();
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
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        rebuildUiForConfiguration();
    }

    @Override
    @SuppressLint("WrongConstant")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAMERA) {
            if (resultCode == RESULT_OK && pendingCameraUri != null) {
                showInlineCropEditor(pendingCameraUri);
            } else {
                pendingCameraUri = null;
            }
            return;
        }
        if (requestCode == REQUEST_CROP) {
            if (resultCode == RESULT_OK && pendingCropOutputUri != null) {
                pendingCameraUri = pendingCropOutputUri;
                pendingCropOutputUri = null;
                addAttachment(pendingCameraUri, true);
            } else if (pendingCameraUri != null) {
                pendingCropOutputUri = null;
                showInlineCropEditor(pendingCameraUri);
            }
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
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

    private void initTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (ttsReady && textToSpeech != null) {
                int result = textToSpeech.setLanguage(Locale.CHINESE);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech.setLanguage(Locale.getDefault());
                }
            }
        });
    }

    private void rebuildUiForConfiguration() {
        if (activeCancelToken != null) {
            return;
        }
        String draft = messageInput == null ? "" : messageInput.getText().toString();
        boolean wasSettingsVisible = settingsVisible;
        boolean wasHistoryVisible = historyVisible;
        boolean wasBrowserVisible = browserPanel != null && browserPanel.getVisibility() == View.VISIBLE;
        String browserUrl = browserWebView == null ? "" : browserWebView.getUrl();
        webReady = false;
        pendingWebActions.clear();
        buildUi();
        syncSettingsState(wasSettingsVisible);
        syncHistoryState(wasHistoryVisible);
        refreshAttachmentView();
        syncToolPanelState();
        messageInput.setText(draft);
        messageInput.setSelection(messageInput.getText().length());
        if (wasBrowserVisible && browserUrl != null && (browserUrl.startsWith("http://") || browserUrl.startsWith("https://"))) {
            openUrlInApp(browserUrl);
        }
        setStatus("布局已切换");
    }

    private void buildUi() {
        FrameLayout shell = new FrameLayout(this);
        shell.setBackgroundColor(color(R.color.app_background));
        shell.setClipChildren(false);
        shell.setClipToPadding(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(0, dp(6), 0, 0);
        root.setBackgroundColor(color(R.color.app_background));
        shell.addView(root, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        buildTopBar(root);
        buildHistoryPanel(root);
        buildSettingsPanel(root);
        buildBrowserView(root);
        buildChatView(root);
        buildComposer(root);
        setContentView(shell);
    }

    private void buildTopBar(LinearLayout root) {
        LinearLayout topArea = new LinearLayout(this);
        topArea.setOrientation(LinearLayout.VERTICAL);
        root.addView(topArea, matchWrap());

        LinearLayout topBar = row();
        topBar.setPadding(dp(10), dp(4), dp(10), dp(4));
        topArea.addView(topBar, matchWrap());

        historyButton = quietButton("☰");
        topBar.addView(historyButton, fixedWrapNoMargin(dp(42)));

        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.setGravity(Gravity.CENTER);
        TextView title = text("Codex", 18, R.color.app_text, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        TextView subtitle = text(apiKeyStore.loadAgentToolsEnabled() ? "自动工具模式" : DEFAULT_MODELS[0], 12, R.color.app_muted, Typeface.NORMAL);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        titleBlock.addView(title, matchWrap());
        titleBlock.addView(subtitle, matchWrap());
        topBar.addView(titleBlock, weightWrap(1));

        browserButton = quietButton("+");
        browserButton.setContentDescription("新聊天");
        settingsButton = quietButton("⚙");
        LinearLayout actionRow = row();
        actionRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actionRow.addView(browserButton, fixedWrap(dp(42)));
        actionRow.addView(settingsButton, fixedWrap(dp(42)));
        topBar.addView(actionRow, wrapWrap());
        browserButton.setOnClickListener(v -> startNewSession());
        historyButton.setOnClickListener(v -> {
            historyVisible = !historyVisible;
            syncHistoryState(historyVisible, true);
        });
        settingsButton.setOnClickListener(v -> {
            settingsVisible = !settingsVisible;
            syncSettingsState(settingsVisible, true);
        });

        statusView = text("", 13, R.color.app_muted, Typeface.NORMAL);
        statusView.setPadding(dp(10), dp(6), dp(10), dp(6));
        statusView.setGravity(Gravity.CENTER);
        statusView.setBackground(roundedStroke(color(R.color.app_accent_soft), color(R.color.app_border), dp(18)));
        statusView.setVisibility(View.GONE);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.leftMargin = dp(14);
        statusParams.rightMargin = dp(14);
        statusParams.topMargin = dp(4);
        statusParams.bottomMargin = dp(4);
        root.addView(statusView, statusParams);
    }

    private void buildSettingsPanel(LinearLayout root) {
        settingsScrollView = new ScrollView(this);
        settingsScrollView.setFillViewport(false);
        settingsScrollView.setVisibility(View.GONE);
        settingsScrollView.setBackgroundColor(color(R.color.app_background));

        settingsPanel = new LinearLayout(this);
        settingsPanel.setOrientation(LinearLayout.VERTICAL);
        settingsPanel.setPadding(dp(2), dp(2), dp(2), dp(14));
        settingsPanel.setBackgroundColor(color(R.color.app_background));
        ScrollView.LayoutParams innerParams = new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        );
        settingsScrollView.addView(settingsPanel, innerParams);

        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        );
        panelParams.leftMargin = dp(10);
        panelParams.rightMargin = dp(10);
        panelParams.topMargin = dp(8);
        panelParams.bottomMargin = dp(6);
        root.addView(settingsScrollView, panelParams);

        LinearLayout settingsHeader = row();
        settingsHeader.setPadding(dp(12), dp(10), dp(12), dp(10));
        settingsHeader.setBackground(roundedStroke(color(R.color.app_panel), color(R.color.app_border), dp(18)));
        LinearLayout titleBlock = new LinearLayout(this);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        TextView settingsTitle = text("设置", 18, R.color.app_text, Typeface.BOLD);
        TextView settingsSubtitle = text("连接、模型和工具", 12, R.color.app_muted, Typeface.NORMAL);
        titleBlock.addView(settingsTitle, matchWrap());
        titleBlock.addView(settingsSubtitle, matchWrap());
        settingsHeader.addView(titleBlock, weightWrap(1));
        updateButton = quietButton("更新");
        saveSettingsButton = primaryButton("保存");
        settingsHeader.addView(updateButton, fixedWrap(dp(64)));
        settingsHeader.addView(saveSettingsButton, fixedWrap(dp(72)));
        addPanelField(settingsHeader);

        keyStatusView = text("", 13, R.color.app_muted, Typeface.NORMAL);
        keyStatusView.setPadding(dp(12), dp(2), dp(12), dp(2));
        addPanelField(keyStatusView);

        LinearLayout connectionSection = settingsSection("连接", "API key 与接口地址", false);

        apiKeyInput = edit("OpenAI API key / 第三方 key");
        apiKeyInput.setSingleLine(true);
        apiKeyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        addSettingsField(connectionSection, apiKeyInput);

        baseUrlInput = edit("接口基础地址，例如 https://example.com/v1");
        baseUrlInput.setSingleLine(true);
        addSettingsField(connectionSection, baseUrlInput);

        keyActionRow = row();
        editKeyButton = quietButton("更换 Key");
        forgetKeyButton = quietButton("忘记 Key");
        keyActionRow.addView(editKeyButton, weightWrap(1));
        keyActionRow.addView(forgetKeyButton, weightWrap(1));
        addSettingsField(connectionSection, keyActionRow);

        LinearLayout modelSection = settingsSection("模型", "聊天接口与模型选择", false);

        apiModeSpinner = new Spinner(this);
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>(Arrays.asList(
                "Responses API",
                "Chat Completions"
        )));
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        apiModeSpinner.setAdapter(modeAdapter);
        styleSpinner(apiModeSpinner);
        addSettingsField(modelSection, apiModeSpinner);

        reasoningEffortSpinner = new Spinner(this);
        ArrayAdapter<String> reasoningAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>(Arrays.asList(
                "推理质量：低",
                "推理质量：中",
                "推理质量：高",
                "推理质量：超高"
        )));
        reasoningAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        reasoningEffortSpinner.setAdapter(reasoningAdapter);
        styleSpinner(reasoningEffortSpinner);
        addSettingsField(modelSection, reasoningEffortSpinner);

        LinearLayout modelRow = row();
        modelAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>(Arrays.asList(DEFAULT_MODELS)));
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modelSpinner = new Spinner(this);
        modelSpinner.setAdapter(modelAdapter);
        styleSpinner(modelSpinner);
        modelRow.addView(modelSpinner, weightWrap(1));
        refreshModelsButton = quietButton("刷新");
        modelRow.addView(refreshModelsButton, fixedWrap(dp(64)));
        addSettingsField(modelSection, modelRow);

        customModelInput = edit("自定义模型 ID，可留空");
        customModelInput.setSingleLine(true);
        addSettingsField(modelSection, customModelInput);

        LinearLayout toolsSection = settingsSection("智能体", "自动工具、启动方式和个人指令", false);

        agentToolsSpinner = new Spinner(this);
        ArrayAdapter<String> agentToolsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>(Arrays.asList(
                "自动工具开",
                "自动工具关"
        )));
        agentToolsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        agentToolsSpinner.setAdapter(agentToolsAdapter);
        styleSpinner(agentToolsSpinner);
        addSettingsField(toolsSection, agentToolsSpinner);

        agentImageToolSpinner = new Spinner(this);
        ArrayAdapter<String> agentImageAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>(Arrays.asList(
                "自动生图关",
                "自动生图开"
        )));
        agentImageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        agentImageToolSpinner.setAdapter(agentImageAdapter);
        styleSpinner(agentImageToolSpinner);
        addSettingsField(toolsSection, agentImageToolSpinner);

        launchModeSpinner = new Spinner(this);
        ArrayAdapter<String> launchModeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>(Arrays.asList(
                "启动时继续上次",
                "启动时新聊天"
        )));
        launchModeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        launchModeSpinner.setAdapter(launchModeAdapter);
        styleSpinner(launchModeSpinner);
        addSettingsField(toolsSection, launchModeSpinner);

        customInstructionsInput = edit("个人指令/记忆：例如称呼、回答风格、常用背景，可留空");
        customInstructionsInput.setMinLines(2);
        customInstructionsInput.setMaxLines(5);
        addSettingsField(toolsSection, customInstructionsInput);

        LinearLayout imageSection = settingsSection("生图", "图片模型与尺寸", false);

        imageModelInput = edit("生图模型，例如 image-2");
        imageModelInput.setSingleLine(true);
        addSettingsField(imageSection, imageModelInput);

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
        styleSpinner(imageSizeSpinner);
        addSettingsField(imageSection, imageSizeSpinner);

        imageRouteSpinner = new Spinner(this);
        ArrayAdapter<String> routeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>(Arrays.asList(
                "Responses 工具生图",
                "Images 接口生图"
        )));
        routeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        imageRouteSpinner.setAdapter(routeAdapter);
        styleSpinner(imageRouteSpinner);
        addSettingsField(imageSection, imageRouteSpinner);

        LinearLayout searchSection = settingsSection("搜索", "普通搜摘要，深度再读网页", false);

        searchProviderSpinner = new Spinner(this);
        ArrayAdapter<String> searchProviderAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>(Arrays.asList(
                "博查 Bocha（推荐）",
                "Tavily",
                "Brave Search",
                "自定义接口",
                "本地兜底",
                "关闭应用侧搜索"
        )));
        searchProviderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        searchProviderSpinner.setAdapter(searchProviderAdapter);
        styleSpinner(searchProviderSpinner);
        addSettingsField(searchSection, searchProviderSpinner);

        searchEndpointInput = edit("自定义搜索接口；博查/Tavily/Brave 可留空");
        searchEndpointInput.setSingleLine(true);
        addSettingsField(searchSection, searchEndpointInput);

        searchApiKeyInput = edit("搜索 API key：博查/Tavily/Brave 填这里");
        searchApiKeyInput.setSingleLine(true);
        searchApiKeyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        addSettingsField(searchSection, searchApiKeyInput);

        searchAuthSpinner = new Spinner(this);
        ArrayAdapter<String> searchAuthAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>(Arrays.asList(
                "搜索不鉴权",
                "Authorization: Bearer",
                "X-API-Key",
                "api_key 参数"
        )));
        searchAuthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        searchAuthSpinner.setAdapter(searchAuthAdapter);
        styleSpinner(searchAuthSpinner);
        addSettingsField(searchSection, searchAuthSpinner);

        searchCountSpinner = new Spinner(this);
        ArrayAdapter<String> searchCountAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<>(Arrays.asList(
                "3",
                "5",
                "8",
                "10",
                "20"
        )));
        searchCountAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        searchCountSpinner.setAdapter(searchCountAdapter);
        styleSpinner(searchCountSpinner);
        addSettingsField(searchSection, searchCountSpinner);

        TextView searchNote = text("普通搜索只取摘要；深度搜索才打开少数网页。诊断会显示搜索源、耗时、缓存和来源数；同 query 20 分钟内走缓存。", 12, R.color.app_muted, Typeface.NORMAL);
        searchNote.setPadding(dp(12), dp(2), dp(12), dp(2));
        addSettingsField(searchSection, searchNote);

        clearSearchKeyButton = quietButton("清除搜索 Key");
        addSettingsField(searchSection, clearSearchKeyButton);

        saveSettingsButton.setOnClickListener(v -> saveSettings());
        editKeyButton.setOnClickListener(v -> {
            keyInputForcedVisible = true;
            syncSettingsState(true, true);
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
        searchProviderSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                syncSearchAdvancedFields(true);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                syncSearchAdvancedFields(true);
            }
        });
    }

    private void buildHistoryPanel(LinearLayout root) {
        historyPanel = new LinearLayout(this);
        historyPanel.setOrientation(LinearLayout.VERTICAL);
        historyPanel.setPadding(dp(14), dp(14), dp(14), dp(14));
        historyPanel.setBackground(roundedStroke(color(R.color.app_panel), color(R.color.app_panel), dp(22)));
        LinearLayout.LayoutParams panelParams = matchWrap();
        panelParams.leftMargin = dp(10);
        panelParams.rightMargin = dp(10);
        panelParams.topMargin = dp(8);
        root.addView(historyPanel, panelParams);

        LinearLayout searchRow = row();
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        historySearchInput = edit("搜索聊天内容...");
        historySearchInput.setSingleLine(true);
        historySearchInput.setTextSize(16);
        historySearchInput.setMinHeight(dp(50));
        historySearchInput.setPadding(dp(16), 0, dp(16), 0);
        historySearchInput.setBackground(roundedStroke(color(R.color.app_panel_alt), color(R.color.app_panel_alt), dp(999)));
        searchRow.addView(historySearchInput, weightWrap(1));
        historyPanel.addView(searchRow, matchWrap());

        historyScrollView = new ScrollView(this);
        historyScrollView.setFillViewport(false);
        historyScrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        historyList = new LinearLayout(this);
        historyList.setOrientation(LinearLayout.VERTICAL);
        historyList.setPadding(0, dp(10), 0, dp(6));
        historyScrollView.addView(historyList, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout.LayoutParams scrollParams = matchWrap();
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        scrollParams.height = Math.max(dp(280), Math.min(dp(690), (int) (screenHeight * 0.62f)));
        scrollParams.topMargin = dp(8);
        historyPanel.addView(historyScrollView, scrollParams);
        historySearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshHistoryList();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        syncHistoryState(false);
    }

    private void buildBrowserView(LinearLayout root) {
        browserPanel = new LinearLayout(this);
        browserPanel.setOrientation(LinearLayout.VERTICAL);
        browserPanel.setVisibility(View.GONE);
        browserPanel.setBackgroundColor(color(R.color.app_background));

        LinearLayout browserBar = new LinearLayout(this);
        browserBar.setOrientation(LinearLayout.VERTICAL);
        browserBar.setPadding(0, dp(6), 0, dp(6));
        browserBackButton = quietButton("←");
        browserForwardButton = quietButton("→");
        browserRefreshButton = quietButton(isCompactLayout() ? "刷" : "刷新");
        browserLayoutButton = quietButton(browserFitMode ? "适配" : "原始");
        browserCloseButton = primaryButton(isCompactLayout() ? "x" : "关闭");
        browserTitleView = text("网页", 13, R.color.app_muted, Typeface.NORMAL);
        browserTitleView.setSingleLine(true);
        browserTitleView.setEllipsize(TextUtils.TruncateAt.END);
        if (isCompactLayout()) {
            LinearLayout titleRow = row();
            titleRow.addView(browserTitleView, weightWrap(1));
            titleRow.addView(browserCloseButton, fixedWrap(dp(40)));
            browserBar.addView(titleRow, matchWrap());

            LinearLayout controlRow = row();
            LinearLayout.LayoutParams controlsParams = matchWrap();
            controlsParams.topMargin = dp(6);
            controlRow.addView(browserBackButton, compactControlParams(1));
            controlRow.addView(browserForwardButton, compactControlParams(1));
            controlRow.addView(browserRefreshButton, compactControlParams(1));
            controlRow.addView(browserLayoutButton, compactControlParams(1));
            browserBar.addView(controlRow, controlsParams);
        } else {
            LinearLayout controlRow = row();
            controlRow.addView(browserBackButton, wrapWrap());
            controlRow.addView(browserForwardButton, wrapWrap());
            controlRow.addView(browserRefreshButton, wrapWrap());
            controlRow.addView(browserLayoutButton, wrapWrap());
            controlRow.addView(browserTitleView, weightWrap(1));
            controlRow.addView(browserCloseButton, wrapWrap());
            browserBar.addView(controlRow, matchWrap());
        }
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
        settings.setSupportZoom(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);
        }
        browserWebView.setHorizontalScrollBarEnabled(true);
        browserWebView.setVerticalScrollBarEnabled(true);
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
                applyBrowserPageFit();
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
        browserLayoutButton.setOnClickListener(v -> toggleBrowserFitMode());
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
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
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
        chatWebView.clearCache(true);
        chatWebView.loadUrl("file:///android_asset/chat.html");
        root.addView(chatWebView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));
    }

    private void buildComposer(LinearLayout root) {
        LinearLayout composerTop = row();
        composerTop.setGravity(Gravity.CENTER_VERTICAL);
        attachmentsView = new LinearLayout(this);
        attachmentsView.setOrientation(LinearLayout.VERTICAL);
        attachmentsView.setPadding(0, 0, 0, 0);
        attachmentsView.setVisibility(View.GONE);
        composerTop.addView(attachmentsView, matchWrap());
        root.addView(composerTop, matchWrap());

        toolPanel = new LinearLayout(this);
        toolPanel.setOrientation(LinearLayout.VERTICAL);
        toolPanel.setPadding(dp(10), dp(9), dp(10), dp(9));
        toolPanel.setBackground(roundedStroke(color(R.color.app_panel), color(R.color.app_border), dp(18)));
        LinearLayout.LayoutParams toolParams = matchWrap();
        toolParams.leftMargin = dp(10);
        toolParams.rightMargin = dp(10);
        toolParams.bottomMargin = dp(2);
        root.addView(toolPanel, toolParams);

        LinearLayout toolRow = row();
        imageButton = chipButton("◎");
        imageButton.setContentDescription("照片");
        fileButton = chipButton("▣");
        fileButton.setContentDescription("文件");
        imageGenButton = chipButton("✦");
        imageGenButton.setContentDescription("生图");
        imageLibraryButton = chipButton("▤");
        imageLibraryButton.setContentDescription("图库");
        toolRow.addView(imageButton, weightWrap(1));
        toolRow.addView(fileButton, weightWrap(1));
        toolRow.addView(imageGenButton, weightWrap(1));
        toolRow.addView(imageLibraryButton, weightWrap(1));
        toolPanel.addView(toolRow, matchWrap());

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.BOTTOM);
        inputRow.setPadding(dp(10), dp(6), dp(10), dp(10));
        LinearLayout.LayoutParams inputRowParams = matchWrap();
        root.addView(inputRow, inputRowParams);

        messageInput = edit("给 Codex 发消息");
        messageInput.setMinLines(1);
        messageInput.setMaxLines(5);
        messageInput.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        inputRow.addView(messageInput, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));

        toolsToggleButton = smallIconButton("+");
        sendButton = roundPrimaryButton("↑");
        stopButton = roundQuietButton("■");
        inputRow.addView(toolsToggleButton, fixedWrap(dp(32)));
        inputRow.addView(sendButton, fixedWrap(dp(46)));
        inputRow.addView(stopButton, fixedWrap(dp(46)));
        stopButton.setVisibility(View.GONE);

        imageButton.setOnClickListener(v -> showPhotoOptions());
        fileButton.setOnClickListener(v -> pickFile());
        imageGenButton.setOnClickListener(v -> generateImageFromPrompt());
        imageLibraryButton.setOnClickListener(v -> showImageLibrary());
        toolsToggleButton.setOnClickListener(v -> toggleToolPanel());
        sendButton.setOnClickListener(v -> sendCurrentMessage(false));
        stopButton.setOnClickListener(v -> stopCurrentRequest());
        syncToolPanelState();
    }

    private void syncSettingsState(boolean showPanel) {
        syncSettingsState(showPanel, false);
    }

    private void syncSettingsState(boolean showPanel, boolean animate) {
        settingsVisible = showPanel;
        boolean hasKey = apiKeyStore.hasSavedKey();
        settingsPanel.setVisibility(View.VISIBLE);
        if (browserPanel != null && showPanel) {
            browserPanel.setVisibility(View.GONE);
        }
        settingsButton.setText(showPanel ? "×" : "⚙");
        updateMainPanelVisibility(showPanel, animate);
        baseUrlInput.setText(apiKeyStore.loadBaseUrl());
        imageModelInput.setText(apiKeyStore.loadImageModel());
        setSpinnerToValue(imageSizeSpinner, apiKeyStore.loadImageSize());
        imageRouteSpinner.setSelection(ApiKeyStore.IMAGE_ROUTE_IMAGES_ENDPOINT.equals(apiKeyStore.loadImageRoute()) ? 1 : 0);
        apiModeSpinner.setSelection(ApiKeyStore.MODE_CHAT_COMPLETIONS.equals(apiKeyStore.loadApiMode()) ? 1 : 0);
        reasoningEffortSpinner.setSelection(reasoningEffortPosition(apiKeyStore.loadReasoningEffort()));
        agentToolsSpinner.setSelection(apiKeyStore.loadAgentToolsEnabled() ? 0 : 1);
        agentImageToolSpinner.setSelection(apiKeyStore.loadAgentImageToolEnabled() ? 1 : 0);
        launchModeSpinner.setSelection(apiKeyStore.loadStartNewOnLaunch() ? 1 : 0);
        searchProviderSpinner.setSelection(searchProviderPosition(apiKeyStore.loadSearchProvider()));
        searchEndpointInput.setText(apiKeyStore.loadSearchEndpoint());
        customInstructionsInput.setText(apiKeyStore.loadCustomInstructions());
        searchAuthSpinner.setSelection(searchAuthPosition(apiKeyStore.loadSearchAuthMode()));
        setSpinnerToValue(searchCountSpinner, String.valueOf(apiKeyStore.loadSearchResultCount()));
        searchApiKeyInput.setText("");
        searchApiKeyInput.setHint(apiKeyStore.hasSavedSearchApiKey()
                ? "搜索 API key 已保存，留空不变"
                : "搜索 API key，可留空");
        clearSearchKeyButton.setVisibility(apiKeyStore.hasSavedSearchApiKey() ? View.VISIBLE : View.GONE);
        syncSearchAdvancedFields(animate);

        keyStatusView.setText(hasKey ? "API key 已保存，留空不会覆盖" : "尚未保存 API key");
        boolean showKeyInput = !hasKey || keyInputForcedVisible;
        setExpandedState(apiKeyInput, showKeyInput, animate);
        if (!showKeyInput) {
            apiKeyInput.setText("");
        }
        if (keyActionRow != null) {
            setExpandedState(keyActionRow, hasKey, animate);
        }
        editKeyButton.setVisibility(hasKey && !showKeyInput ? View.VISIBLE : View.GONE);
        forgetKeyButton.setVisibility(hasKey ? View.VISIBLE : View.GONE);
        if (showPanel && settingsScrollView != null) {
            settingsScrollView.post(() -> settingsScrollView.scrollTo(0, 0));
        }
    }

    private void syncSearchAdvancedFields(boolean animate) {
        boolean customSearch = ApiKeyStore.SEARCH_PROVIDER_CUSTOM.equals(currentSearchProvider());
        setExpandedState(searchEndpointInput, customSearch, animate);
        setExpandedState(searchAuthSpinner, customSearch, animate);
        setExpandedState(searchCountSpinner, customSearch, animate);
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
            apiKeyStore.saveReasoningEffort(currentReasoningEffort());
            apiKeyStore.saveImageModel(currentImageModel());
            apiKeyStore.saveImageSize(currentImageSize());
            apiKeyStore.saveImageRoute(currentImageRoute());
            apiKeyStore.saveAgentToolsEnabled(currentAgentToolsEnabled());
            apiKeyStore.saveAgentImageToolEnabled(currentAgentImageToolEnabled());
            apiKeyStore.saveStartNewOnLaunch(currentStartNewOnLaunch());
            apiKeyStore.saveCustomInstructions(currentCustomInstructions());
            apiKeyStore.saveSearchProvider(currentSearchProvider());
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

    private void showPhotoOptions() {
        new AlertDialog.Builder(this)
                .setTitle("添加照片")
                .setItems(new String[]{"拍照", "从相册选择"}, (dialog, which) -> {
                    if (which == 0) {
                        capturePhoto();
                    } else {
                        pickImage();
                    }
                })
                .show();
    }

    private void capturePhoto() {
        Uri outputUri = createGalleryImageUri("codex_photo_" + System.currentTimeMillis() + ".jpg");
        if (outputUri == null) {
            toast("无法创建相册图片文件");
            return;
        }
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, outputUri);
        intent.setClipData(ClipData.newUri(getContentResolver(), "photo", outputUri));
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        pendingCameraUri = outputUri;
        if (!startExternalActivityForResult(intent, REQUEST_CAMERA, "无法打开相机")) {
            pendingCameraUri = null;
        }
    }

    private Uri createGalleryImageUri(String displayName) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, displayName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ApiKeyChat");
            }
            return getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void showCapturedPhotoEditor(Uri uri) {
        if (uri == null) {
            return;
        }
        addAttachment(uri, true);
        pendingCameraUri = null;
        setStatus("照片已加入附件并保存到相册");
    }

    private void cropCapturedPhoto(Uri sourceUri) {
        showInlineCropEditor(sourceUri);
    }

    private void showInlineCropEditor(Uri sourceUri) {
        setStatus("正在打开裁剪...");
        new Thread(() -> {
            try {
                Bitmap bitmap = decodeBitmap(sourceUri, MAX_EDIT_IMAGE_DIMENSION);
                if (bitmap == null) {
                    throw new IOException("无法读取照片");
                }
                runOnUiThread(() -> showCropDialog(sourceUri, bitmap));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    toast("打开裁剪失败: " + e.getMessage());
                    addAttachment(sourceUri, true);
                    pendingCameraUri = null;
                });
            }
        }).start();
    }

    private void showCropDialog(Uri sourceUri, Bitmap bitmap) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        CropImageView cropView = new CropImageView(this, bitmap);
        root.addView(cropView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        Button rotate = cropOverlayButton("↻");
        rotate.setContentDescription("旋转");
        FrameLayout.LayoutParams rotateParams = new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.START | Gravity.TOP);
        rotateParams.leftMargin = dp(22);
        rotateParams.topMargin = dp(54);
        root.addView(rotate, rotateParams);

        LinearLayout bottom = row();
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(dp(16), 0, dp(16), dp(26));
        Button cancel = cropRoundButton("×");
        Button confirm = cropConfirmButton("确认");
        bottom.addView(cancel, new LinearLayout.LayoutParams(dp(58), dp(58)));
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(0, dp(58), 1f);
        confirmParams.leftMargin = dp(18);
        bottom.addView(confirm, confirmParams);
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
        );
        root.addView(bottom, bottomParams);

        rotate.setOnClickListener(v -> cropView.rotate90());
        cancel.setOnClickListener(v -> {
            dialog.dismiss();
            cropView.recycle();
            pendingCameraUri = null;
            setStatus("已取消裁剪");
        });
        confirm.setOnClickListener(v -> {
            confirm.setEnabled(false);
            setStatus("正在保存裁剪照片...");
            new Thread(() -> {
                Bitmap cropped = null;
                try {
                    cropped = cropView.createCroppedBitmap();
                    Uri outputUri = saveBitmapToGallery(cropped, "codex_photo_crop_" + System.currentTimeMillis() + ".jpg");
                    runOnUiThread(() -> {
                        dialog.dismiss();
                        cropView.recycle();
                        pendingCameraUri = null;
                        setStatus("照片已裁剪并保存到相册");
                        addAttachment(outputUri, true);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        confirm.setEnabled(true);
                        toast("裁剪失败: " + e.getMessage());
                    });
                } finally {
                    if (cropped != null && cropped != cropView.currentBitmap()) {
                        cropped.recycle();
                    }
                }
            }).start();
        });

        dialog.setContentView(root);
        dialog.setOnCancelListener(d -> {
            cropView.recycle();
            pendingCameraUri = null;
            setStatus("已取消裁剪");
        });
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.black);
        }
        dialog.show();
        Window shownWindow = dialog.getWindow();
        if (shownWindow != null) {
            shownWindow.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private Uri saveBitmapToGallery(Bitmap bitmap, String displayName) throws IOException {
        if (bitmap == null) {
            throw new IOException("没有可保存的图片");
        }
        Uri outputUri = createGalleryImageUri(displayName);
        if (outputUri == null) {
            throw new IOException("无法创建相册图片");
        }
        try (OutputStream out = getContentResolver().openOutputStream(outputUri)) {
            if (out == null || !bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_EDIT_QUALITY, out)) {
                throw new IOException("无法保存图片");
            }
        }
        return outputUri;
    }

    private boolean startExternalActivityForResult(Intent intent, int requestCode, String failureMessage) {
        grantIntentUriPermissions(intent);
        try {
            startActivityForResult(intent, requestCode);
            return true;
        } catch (Exception e) {
            toast(failureMessage);
            return false;
        }
    }

    private void grantIntentUriPermissions(Intent intent) {
        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        ClipData clipData = intent.getClipData();
        List<Uri> uris = new ArrayList<>();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                if (uri != null) {
                    uris.add(uri);
                }
            }
        }
        Uri data = intent.getData();
        if (data != null) {
            uris.add(data);
        }
        if (uris.isEmpty()) {
            return;
        }
        List<ResolveInfo> activities = getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo info : activities) {
            if (info.activityInfo == null || info.activityInfo.packageName == null) {
                continue;
            }
            for (Uri uri : uris) {
                grantUriPermission(info.activityInfo.packageName, uri, flags);
            }
        }
    }

    private void rotateCapturedPhoto(Uri sourceUri) {
        setStatus("正在旋转照片...");
        new Thread(() -> {
            try {
                Uri rotated = saveRotatedImage(sourceUri, 90);
                runOnUiThread(() -> {
                    pendingCameraUri = rotated;
                    setStatus("照片已旋转并保存到相册");
                    showCapturedPhotoEditor(rotated);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    toast("旋转失败: " + e.getMessage());
                    showCapturedPhotoEditor(sourceUri);
                });
            }
        }).start();
    }

    private Uri saveRotatedImage(Uri sourceUri, float degrees) throws IOException {
        Bitmap source = decodeBitmap(sourceUri, MAX_EDIT_IMAGE_DIMENSION);
        if (source == null) {
            throw new IOException("无法读取照片");
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
        Uri outputUri = createGalleryImageUri("codex_photo_rotate_" + System.currentTimeMillis() + ".jpg");
        if (outputUri == null) {
            throw new IOException("无法创建旋转图片");
        }
        try (OutputStream out = getContentResolver().openOutputStream(outputUri)) {
            if (out == null || !rotated.compress(Bitmap.CompressFormat.JPEG, JPEG_EDIT_QUALITY, out)) {
                throw new IOException("无法保存旋转图片");
            }
        } finally {
            if (rotated != source) {
                rotated.recycle();
            }
            source.recycle();
        }
        return outputUri;
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

    private void showImageLibrary() {
        File dir = new File(getFilesDir(), "generated_images");
        File[] files = dir.listFiles(file -> file.isFile() && (
                file.getName().toLowerCase(Locale.ROOT).endsWith(".png")
                        || file.getName().toLowerCase(Locale.ROOT).endsWith(".jpg")
                        || file.getName().toLowerCase(Locale.ROOT).endsWith(".jpeg")
        ));
        if (files == null || files.length == 0) {
            toast("图片库还是空的");
            return;
        }
        ArrayList<File> imageFiles = new ArrayList<>(Arrays.asList(files));
        Collections.sort(imageFiles, (left, right) -> Long.compare(right.lastModified(), left.lastModified()));

        ScrollView scrollView = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(8), dp(12), dp(8));
        scrollView.addView(list, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        final AlertDialog[] dialogRef = new AlertDialog[1];
        for (File file : imageFiles) {
            LinearLayout row = row();
            row.setPadding(dp(8), dp(8), dp(8), dp(8));
            row.setBackground(roundedStroke(color(R.color.app_panel), color(R.color.app_border), dp(12)));

            ImageView preview = new ImageView(this);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.setAdjustViewBounds(false);
            preview.setImageURI(Uri.fromFile(file));
            row.addView(preview, new LinearLayout.LayoutParams(dp(96), dp(96)));

            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            info.setPadding(dp(12), 0, 0, 0);
            TextView name = text(file.getName(), 14, R.color.app_text, Typeface.BOLD);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            TextView date = text("点击插入到当前聊天", 12, R.color.app_muted, Typeface.NORMAL);
            info.addView(name, matchWrap());
            info.addView(date, matchWrap());
            row.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            row.setOnClickListener(v -> {
                appendImageFromLibrary(file);
                if (dialogRef[0] != null) {
                    dialogRef[0].dismiss();
                }
            });

            LinearLayout.LayoutParams rowParams = matchWrap();
            rowParams.bottomMargin = dp(8);
            list.addView(row, rowParams);
        }

        dialogRef[0] = new AlertDialog.Builder(this)
                .setTitle("图片库")
                .setView(scrollView)
                .setNegativeButton("关闭", null)
                .show();
    }

    private void appendImageFromLibrary(File file) {
        if (file == null || !file.exists()) {
            toast("图片文件不存在");
            return;
        }
        appendMessage("assistant", "![生成图片](" + file.toURI() + ")", "");
        setStatus("已插入图片");
    }

    private void shareCurrentChat() {
        String textValue = buildCurrentChatText();
        if (textValue.trim().isEmpty()) {
            toast("当前聊天还是空的");
            return;
        }
        shareText(textValue);
    }

    private String buildCurrentChatText() {
        if (currentSession == null || currentSession.messages == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        ensureBranchMetadata(currentSession);
        ArrayList<Integer> path = activeMessageIndexes(currentSession);
        for (Integer index : path) {
            if (index == null) {
                continue;
            }
            int i = index;
            JSONObject message = currentSession.messages.optJSONObject(i);
            if (message == null) {
                continue;
            }
            String role = message.optString("role", "assistant");
            String textValue = message.optString("text", "").trim();
            if (textValue.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("user".equals(role) ? "你" : "Codex").append(":\n").append(textValue);
        }
        return builder.toString();
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

        String mime = getContentResolver().getType(uri);
        if (mime == null || mime.isEmpty()) {
            mime = image ? "image/jpeg" : "application/octet-stream";
        }
        boolean officeFile = !image && OfficeProcessor.isOfficeFile(name, mime);
        long maxBytes = officeFile ? MAX_OFFICE_ATTACHMENT_BYTES : MAX_ATTACHMENT_BYTES;
        if (size > maxBytes) {
            toast(officeFile ? "Office 附件超过 120MB" : "附件超过 20MB");
            return;
        }
        attachments.add(new AttachmentItem(uri, name, mime, size, image));
        refreshAttachmentView();
    }

    private void sendCurrentMessage(boolean regenerate) {
        String apiKey = currentApiKey();
        String baseUrl = currentBaseUrl();
        String apiMode = currentApiMode();
        String model = currentModel();
        String reasoningEffort = currentReasoningEffort();
        String prompt = messageInput.getText().toString().trim();
        boolean isRevisionPrompt = prompt.startsWith("修改要求");
        boolean branchReplyOnly = (regenerate || isRevisionPrompt) && !pendingAssistantParentNodeId.isEmpty();
        boolean imageTextFallbackIntent = attachments.isEmpty() && shouldUseTextImageFallback(prompt);
        boolean useAgentTools = currentAgentToolsEnabled() && ApiKeyStore.MODE_RESPONSES.equals(apiMode);
        boolean deepSearchPrompt = isDeepSearchPrompt(prompt);
        boolean useOfficeTools = useAgentTools && likelyNeedsOfficeTool(prompt, attachments);
        boolean useSearch = useAgentTools
                && !ApiKeyStore.SEARCH_PROVIDER_OFF.equals(currentSearchProvider())
                && likelyNeedsFreshSearch(prompt);
        boolean runAgentTools = useAgentTools && (!useSearch || useOfficeTools || deepSearchPrompt);
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
        if (currentSession != null) {
            ensureBranchMetadata(currentSession);
            if (!branchReplyOnly) {
                conversationTranscript = rebuildTranscriptFromMessages(currentSession.messages);
            }
        }
        String searchEndpoint = currentSearchEndpoint();

        ArrayList<AttachmentItem> pendingAttachments = new ArrayList<>(attachments);
        clearPendingGeneratedOfficeFiles();
        if (!branchReplyOnly) {
            lastUserPrompt = prompt;
            appendMessage("user", buildUserBlock(prompt, pendingAttachments, useSearch, runAgentTools), "");
        }
        String searchAuthMode = currentSearchAuthMode();
        String searchApiKey = currentSearchApiKey();
        int searchResultCount = currentSearchResultCount();
        messageInput.setText("");
        setBusy(true);
        setStatus(runAgentTools ? "智能体正在判断工具..." : (useSearch ? "正在联网搜索..." : "正在思考..."));
        setThinking(true);
        final long requestStartedAt = System.currentTimeMillis();

        OpenAiClient.CancelToken token = new OpenAiClient.CancelToken();
        activeCancelToken = token;
        acquireRequestWakeLock();
        new Thread(() -> {
            try {
                ArrayList<SearchClient.SearchResult> searchResults = new ArrayList<>();
                JSONArray searchDiagnostics = new JSONArray();
                JSONArray localSearchSources = new JSONArray();
                String searchFailure = "";
                if (useSearch) {
                    try {
                        SearchClient.SearchOptions preSearchOptions = deepSearchPrompt
                                ? SearchClient.SearchOptions.deep()
                                : SearchClient.SearchOptions.fast();
                        int effectiveSearchCount = deepSearchPrompt
                                ? Math.max(searchResultCount, 20)
                                : searchResultCount;
                        SearchClient.SearchResponse searchResponse = SearchClient.searchDetailed(
                                currentSearchProvider(),
                                searchEndpoint,
                                searchAuthMode,
                                searchApiKey,
                                effectiveSearchCount,
                                prompt,
                                token,
                                preSearchOptions
                        );
                        searchResults.addAll(searchResponse.results);
                        if (searchResults.isEmpty()) {
                            searchFailure = "联网搜索没有返回可用结果，已改为自动工具模式。";
                        } else {
                            localSearchSources = annotateSources(
                                    SearchClient.toJsonArray(searchResults),
                                    "预搜索",
                                    searchResponse.providerLabel
                            );
                            searchDiagnostics.put(searchDiagnostic(
                                    "预搜索",
                                    searchResponse.providerLabel,
                                    searchResults.size(),
                                    searchResponse.elapsedMs,
                                    searchResponse.cached,
                                    deepSearchPrompt ? "deep" : "normal"
                            ));
                        }
                    } catch (Exception searchError) {
                        if (token.isCanceled()) {
                            throw searchError;
                        }
                        searchFailure = "联网搜索失败，已改为自动工具模式: " + searchError.getMessage();
                    }
                }
                runOnUiThread(() -> setStatus(runAgentTools ? "智能体正在执行工具..." : "正在思考..."));
                ArrayList<AttachmentPayload> payloads = buildAttachmentPayloads(pendingAttachments);
                String apiPrompt = buildApiPrompt(apiMode, prompt, searchResults);
                if (imageTextFallbackIntent) {
                    apiPrompt = buildImageTextFallbackPrompt(apiPrompt, prompt);
                }
                String previousResponseId = "";
                StreamingUiBuffer streamUi = new StreamingUiBuffer(requestStartedAt);
                activeStreamingUi = streamUi;
                JSONArray localUiSources = mergeSources(localSearchSources, searchDiagnostics);
                if (localUiSources.length() > 0) {
                    streamUi.onSources(localUiSources);
                }
                int branchParentIndex = pendingBranchParentMessageIndex;
                runOnUiThread(() -> startAssistantStream(runAgentTools ? "智能体正在处理..." : "正在生成回复...", branchParentIndex));
                OpenAiClient.ChatResult result;
                if (runAgentTools) {
                    boolean hasLocalSearchContext = localSearchSources.length() > 0;
                    boolean officeOnlyAgent = useOfficeTools && !useSearch && !deepSearchPrompt;
                    OpenAiClient.ToolConfig primaryToolConfig = agentToolConfig(prompt, false, hasLocalSearchContext, !imageTextFallbackIntent, officeOnlyAgent);
                    try {
                        result = OpenAiClient.sendAgentMessageStreaming(
                                baseUrl,
                                apiKey,
                                model,
                                reasoningEffort,
                                apiPrompt,
                                payloads,
                                previousResponseId,
                                primaryToolConfig,
                                agentToolHandler(baseUrl, apiKey, model, primaryToolConfig.deepSearch, pendingAttachments),
                                streamUi,
                                token
                        );
                    } catch (Exception firstError) {
                        if (token.isCanceled() || !shouldRetryWithLocalTools(firstError, prompt, primaryToolConfig)) {
                            throw firstError;
                        }
                        runOnUiThread(() -> setStatus("托管搜索不可用，正在使用应用侧搜索兜底..."));
                        OpenAiClient.ToolConfig fallbackToolConfig = agentToolConfig(prompt, true, hasLocalSearchContext, !imageTextFallbackIntent, officeOnlyAgent);
                        result = OpenAiClient.sendAgentMessageStreaming(
                                baseUrl,
                                apiKey,
                                model,
                                reasoningEffort,
                                apiPrompt,
                                payloads,
                                previousResponseId,
                                fallbackToolConfig,
                                agentToolHandler(baseUrl, apiKey, model, fallbackToolConfig.deepSearch, pendingAttachments),
                                streamUi,
                                token
                        );
                    }
                } else {
                    result = OpenAiClient.sendMessageStreaming(
                            apiMode,
                            baseUrl,
                            apiKey,
                            model,
                            reasoningEffort,
                            apiPrompt,
                            payloads,
                            previousResponseId,
                            streamUi,
                            token
                    );
                }
                OpenAiClient.ChatResult finalResult = result;
                String assistantText = imageTextFallbackIntent
                        ? normalizeTextOnlyImageFallbackOutput(finalResult.text, prompt)
                        : normalizeAssistantOutput(finalResult.text);
                JSONArray sourceJson = mergeSources(localUiSources, finalResult.sources);
                String finalSearchFailure = searchFailure;
                runOnUiThread(() -> {
                    long elapsedMs = Math.max(0L, System.currentTimeMillis() - requestStartedAt);
                    streamUi.close();
                    setThinking(false);
                    if (token.isCanceled()) {
                        finishStoppedRequest();
                        return;
                    }
                    lastResponseId = finalResult.responseId;
                    lastModel = model;
                    lastApiMode = apiMode;
                    lastAssistantText = assistantText;
                    if (isRevisionPrompt) {
                        revisionTargetText = "";
                    }
                    rememberTurn(prompt, assistantText);
                    attachments.clear();
                    refreshAttachmentView();
                    if (!finalSearchFailure.isEmpty()) {
                        appendMessage("system", finalSearchFailure, "");
                    }
                    int assistantIndex = persistMessage("assistant", assistantText, finalResult.reasoning, sourceJson, elapsedMs);
                    finishAssistantStream(assistantText, finalResult.reasoning, sourceJson, elapsedMs, assistantIndex);
                    boolean branchCreated = branchParentIndex >= 0;
                    if (branchCreated) {
                        renderSessionMessages(currentSession);
                    }
                    setStatus(branchCreated ? "已创建新回答分支，可用 1/2 切回旧分支" : "完成");
                    finishRequest();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setThinking(false);
                    if (token.isCanceled()) {
                        finishStoppedRequest();
                    } else {
                        int branchIndexOnFailure = pendingBranchParentMessageIndex;
                        cancelAssistantStream();
                        if (branchIndexOnFailure >= 0) {
                            renderSessionMessages(currentSession);
                        }
                        appendMessage("system", e.getMessage(), "");
                        setStatus("发送失败");
                        clearPendingBranchReply();
                        finishRequest();
                    }
                });
            }
        }).start();
    }

    private void generateImageFromPrompt() {
        startImageGeneration(messageInput.getText().toString().trim(), null, false);
    }

    private void startImageGeneration(String prompt, String userDisplayText, boolean regenerate) {
        String apiKey = currentApiKey();
        String baseUrl = currentBaseUrl();
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
        String imageModel = currentImageModel();
        String model = ApiKeyStore.IMAGE_ROUTE_RESPONSES_TOOL.equals(route) ? currentModel() : imageModel;
        String size = currentImageSize();
        if (model.isEmpty()) {
            toast(ApiKeyStore.IMAGE_ROUTE_RESPONSES_TOOL.equals(route) ? "请选择聊天模型" : "填写生图模型 ID");
            return;
        }
        lastUserPrompt = userDisplayText == null ? "生成图片：" + prompt : userDisplayText;
        if (!regenerate) {
            appendMessage("user", lastUserPrompt, "");
        }
        messageInput.setText("");
        setBusy(true);
        setStatus("正在生成图片...");
        setThinking(true);
        final long requestStartedAt = System.currentTimeMillis();

        OpenAiClient.CancelToken token = new OpenAiClient.CancelToken();
        activeCancelToken = token;
        acquireRequestWakeLock();
        new Thread(() -> {
            try {
                OpenAiClient.ImageResult result = generateImageWithFallback(baseUrl, apiKey, route, model, imageModel, prompt, size, token);
                runOnUiThread(() -> {
                    long elapsedMs = Math.max(0L, System.currentTimeMillis() - requestStartedAt);
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
                    appendMessage("assistant", lastAssistantText, "", null, elapsedMs);
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
        int branchIndexOnStop = pendingBranchParentMessageIndex;
        cancelAssistantStream();
        if (branchIndexOnStop >= 0) {
            renderSessionMessages(currentSession);
        }
        appendMessage("system", "已停止本次请求", "");
        setStatus("已停止");
        clearPendingBranchReply();
        finishRequest();
    }

    private void finishRequest() {
        if (activeStreamingUi != null) {
            activeStreamingUi.close();
            activeStreamingUi = null;
        }
        activeCancelToken = null;
        releaseRequestWakeLock();
        setBusy(false);
    }

    private ArrayList<AttachmentPayload> buildAttachmentPayloads(List<AttachmentItem> items) throws IOException {
        ArrayList<AttachmentPayload> payloads = new ArrayList<>();
        for (AttachmentItem item : items) {
            if (!item.image && OfficeProcessor.isOfficeFile(item.name, item.mimeType)) {
                payloads.add(buildOfficeAttachmentPayload(item));
                continue;
            }
            byte[] bytes = item.image ? readImageAttachmentBytes(item) : readAttachmentBytes(item);
            String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
            String mime = item.image
                    ? "image/jpeg"
                    : ((item.mimeType == null || item.mimeType.isEmpty())
                    ? "application/octet-stream"
                    : item.mimeType);
            String dataUrl = "data:" + mime + ";base64," + base64;
            payloads.add(new AttachmentPayload(item.name, dataUrl, item.image));
        }
        return payloads;
    }

    private AttachmentPayload buildOfficeAttachmentPayload(AttachmentItem item) throws IOException {
        File temp = copyAttachmentToTempFile(item, MAX_OFFICE_ATTACHMENT_BYTES);
        try {
            String extractedText;
            try {
                OfficeProcessor.ExtractedOffice office = OfficeProcessor.extract(item.name, item.mimeType, temp);
                extractedText = office.text + (office.truncated ? "\n\n（Office 文件内容较长，已提取前半部分。）" : "");
            } catch (Exception e) {
                extractedText = "Office 文件解析失败: " + e.getMessage();
            }
            return new AttachmentPayload(item.name, "", extractedText, false);
        } finally {
            if (!temp.delete()) {
                temp.deleteOnExit();
            }
        }
    }

    private byte[] readImageAttachmentBytes(AttachmentItem item) throws IOException {
        try {
            Bitmap bitmap = decodeBitmap(item.uri, MAX_IMAGE_UPLOAD_DIMENSION);
            if (bitmap == null) {
                return readAttachmentBytes(item);
            }
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_UPLOAD_QUALITY, out)) {
                    return readAttachmentBytes(item);
                }
                byte[] bytes = out.toByteArray();
                if (bytes.length > MAX_ATTACHMENT_BYTES) {
                    throw new IOException("图片压缩后仍超过 20MB: " + item.name);
                }
                return bytes;
            } finally {
                bitmap.recycle();
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception ignored) {
            return readAttachmentBytes(item);
        }
    }

    private Bitmap decodeBitmap(Uri uri, int maxDimension) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("无法读取图片");
            }
            BitmapFactory.decodeStream(in, null, bounds);
        }
        int width = bounds.outWidth;
        int height = bounds.outHeight;
        if (width <= 0 || height <= 0) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateInSampleSize(width, height, maxDimension);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("无法读取图片");
            }
            return scaleBitmapToMaxDimension(BitmapFactory.decodeStream(in, null, options), maxDimension);
        }
    }

    private int calculateInSampleSize(int width, int height, int maxDimension) {
        if (maxDimension <= 0) {
            return 1;
        }
        int sample = 1;
        int longest = Math.max(width, height);
        while (longest / (sample * 2) >= maxDimension) {
            sample *= 2;
        }
        return Math.max(1, sample);
    }

    private Bitmap scaleBitmapToMaxDimension(Bitmap bitmap, int maxDimension) {
        if (bitmap == null || maxDimension <= 0) {
            return bitmap;
        }
        int longest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        if (longest <= maxDimension) {
            return bitmap;
        }
        float scale = maxDimension / (float) longest;
        int targetWidth = Math.max(1, Math.round(bitmap.getWidth() * scale));
        int targetHeight = Math.max(1, Math.round(bitmap.getHeight() * scale));
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
        if (scaled != bitmap) {
            bitmap.recycle();
        }
        return scaled;
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

    private File copyAttachmentToTempFile(AttachmentItem item, long maxBytes) throws IOException {
        File dir = new File(getCacheDir(), "office-input");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建 Office 临时目录");
        }
        String suffix = fileExtension(item.name);
        if (suffix.isEmpty()) {
            suffix = ".office";
        }
        File temp = File.createTempFile("office_", suffix, dir);
        try (InputStream in = getContentResolver().openInputStream(item.uri);
             OutputStream out = new FileOutputStream(temp)) {
            if (in == null) {
                throw new IOException("无法读取附件: " + item.name);
            }
            byte[] buffer = new byte[64 * 1024];
            int read;
            long total = 0;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new IOException("Office 附件超过 120MB: " + item.name);
                }
                out.write(buffer, 0, read);
            }
            return temp;
        } catch (IOException e) {
            if (!temp.delete()) {
                temp.deleteOnExit();
            }
            throw e;
        }
    }

    private String fileExtension(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        String suffix = name.substring(dot).toLowerCase(Locale.ROOT);
        return suffix.length() > 12 ? "" : suffix;
    }

    private boolean isDeepSearchPrompt(String prompt) {
        String value = prompt == null ? "" : prompt.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return false;
        }
        String[] needles = {
                "深度搜索", "深入搜索", "深度查", "详细查", "仔细查", "全面搜索", "逐条核对", "多方核实",
                "打开来源", "打开网页", "核对来源", "引用来源", "查证", "详细搜索",
                "deep search", "deep research", "detailed search", "verify sources", "open sources", "cross-check"
        };
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsUrl(String prompt) {
        String value = prompt == null ? "" : prompt.trim().toLowerCase(Locale.ROOT);
        return value.contains("http://") || value.contains("https://");
    }

    private boolean likelyNeedsFreshSearch(String prompt) {
        String value = prompt == null ? "" : prompt.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return false;
        }
        String[] needles = {
                "最新", "今天", "现在", "新闻", "价格", "官网", "搜索", "查一下", "查询", "查找", "联网",
                "进展", "动态", "发布", "更新", "search", "latest", "today", "news", "price", "recent", "update"
        };
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private boolean likelyNeedsOfficeTool(String prompt, List<AttachmentItem> items) {
        String value = prompt == null ? "" : prompt.trim().toLowerCase(Locale.ROOT);
        boolean hasOfficeAttachment = false;
        if (items != null) {
            for (AttachmentItem item : items) {
                if (item != null && !item.image && OfficeProcessor.isOfficeFile(item.name, item.mimeType)) {
                    hasOfficeAttachment = true;
                    break;
                }
            }
        }
        boolean hasGeneratedOffice = hasGeneratedOfficeContext();
        if (!hasOfficeAttachment && !hasGeneratedOffice && value.isEmpty()) {
            return false;
        }
        String[] officeNeedles = {
                "word", "docx", "文档文件", "word文档", "论文文件", "报告文件", "替换段落", "修改段落",
                "excel", "xlsx", "excel表格", "表格文件", "工作簿", "工作表", "单元格", "追加工作表",
                "ppt", "pptx", "powerpoint", "演示稿", "幻灯片", "替换标题", "替换正文"
        };
        for (String needle : officeNeedles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        if (hasGeneratedOffice) {
            String[] generatedContextNeedles = {
                    "这个文件", "这个文档", "刚才生成", "刚才的", "上一个", "继续改", "继续修改", "里面的"
            };
            for (String needle : generatedContextNeedles) {
                if (value.contains(needle)) {
                    return true;
                }
            }
        }
        if (!hasOfficeAttachment && !hasGeneratedOffice) {
            return false;
        }
        String[] editNeedles = {
                "修改", "改一下", "替换", "润色", "优化", "生成新", "导出", "另存", "副本", "新增", "追加", "填入", "写入"
        };
        for (String needle : editNeedles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasGeneratedOfficeContext() {
        synchronized (generatedOfficeLock) {
            return !generatedOfficeFiles.isEmpty();
        }
    }

    private boolean shouldRetryWithLocalTools(Exception error, String prompt, OpenAiClient.ToolConfig primaryToolConfig) {
        if (primaryToolConfig == null
                || !primaryToolConfig.hostedWebSearch
                || ApiKeyStore.SEARCH_PROVIDER_OFF.equals(currentSearchProvider())
                || !likelyNeedsFreshSearch(prompt)) {
            return false;
        }
        String message = error == null ? "" : String.valueOf(error.getMessage()).toLowerCase(Locale.ROOT);
        return message.contains("web_search")
                || message.contains("tool")
                || message.contains("unsupported")
                || message.contains("not supported")
                || message.contains("country")
                || message.contains("region")
                || message.contains("territory")
                || message.contains("403")
                || message.contains("503");
    }

    private OpenAiClient.ToolConfig agentToolConfig(String prompt, boolean forceLocalFallback, boolean hasLocalSearchContext) {
        return agentToolConfig(prompt, forceLocalFallback, hasLocalSearchContext, true);
    }

    private OpenAiClient.ToolConfig agentToolConfig(String prompt, boolean forceLocalFallback, boolean hasLocalSearchContext, boolean allowImageGeneration) {
        return agentToolConfig(prompt, forceLocalFallback, hasLocalSearchContext, allowImageGeneration, false);
    }

    private OpenAiClient.ToolConfig agentToolConfig(String prompt, boolean forceLocalFallback, boolean hasLocalSearchContext, boolean allowImageGeneration, boolean officeOnlyAgent) {
        boolean deepSearch = isDeepSearchPrompt(prompt);
        boolean hasUrl = containsUrl(prompt);
        String provider = currentSearchProvider();
        boolean appSearchOff = ApiKeyStore.SEARCH_PROVIDER_OFF.equals(provider);
        boolean dedicatedProvider = ApiKeyStore.SEARCH_PROVIDER_BOCHA.equals(provider)
                || ApiKeyStore.SEARCH_PROVIDER_TAVILY.equals(provider)
                || ApiKeyStore.SEARCH_PROVIDER_BRAVE.equals(provider);
        boolean providerReady = !appSearchOff && (!dedicatedProvider || !currentSearchApiKey().isEmpty());
        OpenAiClient.ToolConfig config = new OpenAiClient.ToolConfig();
        config.hostedWebSearch = !forceLocalFallback && !providerReady && !hasLocalSearchContext;
        config.localTools = true;
        config.openUrlTool = forceLocalFallback || deepSearch || hasUrl;
        config.customSearchTool = !hasLocalSearchContext && (!appSearchOff || forceLocalFallback || deepSearch);
        config.contextSearchTool = true;
        config.imageGenerationTool = allowImageGeneration && currentAgentImageToolEnabled();
        config.documentTools = true;
        config.deepSearch = deepSearch;
        config.quickSearchContext = hasLocalSearchContext && !deepSearch;
        config.maxToolRounds = deepSearch ? 4 : (hasLocalSearchContext ? 1 : 2);
        if (officeOnlyAgent) {
            config.hostedWebSearch = false;
            config.openUrlTool = false;
            config.customSearchTool = false;
            config.contextSearchTool = hasGeneratedOfficeContext() || hasLocalSearchContext;
            config.imageGenerationTool = false;
            config.documentTools = true;
            config.deepSearch = false;
            config.quickSearchContext = false;
            config.maxToolRounds = 1;
            config.officeOnly = true;
            config.preferLowReasoning = true;
        }
        return config;
    }

    private OpenAiClient.ToolHandler agentToolHandler(
            String baseUrl,
            String apiKey,
            String model,
            boolean deepSearch,
            List<AttachmentItem> toolAttachments
    ) {
        String searchProvider = currentSearchProvider();
        String searchEndpoint = currentSearchEndpoint();
        String searchAuthMode = currentSearchAuthMode();
        String searchApiKey = currentSearchApiKey();
        int searchResultCount = currentSearchResultCount();
        SearchClient.SearchOptions searchOptions = deepSearch ? SearchClient.SearchOptions.deep() : SearchClient.SearchOptions.fast();
        String imageRoute = currentImageRoute();
        String imageModel = ApiKeyStore.IMAGE_ROUTE_RESPONSES_TOOL.equals(imageRoute) ? model : currentImageModel();
        String imageSize = currentImageSize();
        boolean imageToolEnabled = currentAgentImageToolEnabled();
        return (name, arguments, cancelToken) -> {
            String toolName = name == null ? "" : name;
            if ("search_context".equals(toolName)) {
                String query = arguments.optString("query", "").trim();
                if (query.isEmpty()) {
                    query = lastUserPrompt == null ? "" : lastUserPrompt.trim();
                }
                JSONObject output = buildLocalContextResult(query);
                int currentCount = output.optJSONArray("current_matches") == null ? 0 : output.optJSONArray("current_matches").length();
                int fileCount = output.optJSONArray("generated_files") == null ? 0 : output.optJSONArray("generated_files").length();
                String summary = "本地上下文查询完成: 当前 " + currentCount
                        + " 条，文件 " + fileCount + " 个";
                return new OpenAiClient.ToolResult(output.toString(), summary, new JSONArray());
            }
            if ("custom_search".equals(toolName)) {
                String query = arguments.optString("query", "").trim();
                if (query.isEmpty()) {
                    return new OpenAiClient.ToolResult("custom_search failed: missing query", "custom_search 缺少 query", new JSONArray());
                }
                int effectiveCount = deepSearch ? Math.max(searchResultCount, 20) : searchResultCount;
                SearchClient.SearchResponse response = SearchClient.searchDetailed(
                        searchProvider,
                        searchEndpoint,
                        searchAuthMode,
                        searchApiKey,
                        effectiveCount,
                        query,
                        cancelToken,
                        searchOptions
                );
                List<SearchClient.SearchResult> results = response.results;
                JSONArray resultSources = annotateSources(SearchClient.toJsonArray(results), "custom_search", response.providerLabel);
                JSONArray sources = mergeSources(resultSources, new JSONArray().put(searchDiagnostic(
                        "custom_search",
                        response.providerLabel,
                        results.size(),
                        response.elapsedMs,
                        response.cached,
                        deepSearch ? "deep" : "normal"
                )));
                JSONObject output = new JSONObject();
                output.put("query", query);
                output.put("provider", response.providerLabel);
                output.put("cached", response.cached);
                output.put("search_elapsed_ms", response.elapsedMs);
                output.put("result_count", results.size());
                output.put("mode", deepSearch ? "deep" : "normal");
                output.put("results", resultSources);
                String summary = "搜索完成: " + response.providerLabel
                        + "，" + results.size() + " 条"
                        + "，" + response.elapsedMs + "ms"
                        + (response.cached ? "，缓存" : "");
                return new OpenAiClient.ToolResult(output.toString(), summary, sources);
            }
            if ("open_url".equals(toolName)) {
                String url = arguments.optString("url", "").trim();
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    return new OpenAiClient.ToolResult("open_url failed: URL must start with http:// or https://", "open_url 地址无效", new JSONArray());
                }
                long startedAt = System.currentTimeMillis();
                String summary = SearchClient.fetchPageSummary(url, cancelToken, searchOptions);
                long elapsedMs = Math.max(0L, System.currentTimeMillis() - startedAt);
                JSONObject source = new JSONObject();
                source.put("title", url);
                source.put("snippet", summary);
                source.put("url", url);
                source.put("kind", "source");
                source.put("channel", "open_url");
                source.put("provider", "网页读取");
                JSONArray sources = new JSONArray();
                sources.put(source);
                JSONObject output = new JSONObject();
                output.put("url", url);
                output.put("read_elapsed_ms", elapsedMs);
                output.put("summary", summary);
                return new OpenAiClient.ToolResult(output.toString(), "读取网页完成: " + elapsedMs + "ms", sources);
            }
            if ("generate_image".equals(toolName)) {
                if (!imageToolEnabled) {
                    return new OpenAiClient.ToolResult("generate_image is disabled in app settings.", "自动生图未开启", new JSONArray());
                }
                String imagePrompt = arguments.optString("prompt", "").trim();
                if (imagePrompt.isEmpty()) {
                    return new OpenAiClient.ToolResult("generate_image failed: missing prompt", "generate_image 缺少 prompt", new JSONArray());
                }
                OpenAiClient.ImageResult image = ApiKeyStore.IMAGE_ROUTE_RESPONSES_TOOL.equals(imageRoute)
                        ? OpenAiClient.generateImageViaResponsesTool(baseUrl, apiKey, model, imagePrompt, imageSize, cancelToken)
                        : OpenAiClient.generateImage(baseUrl, apiKey, imageModel, imagePrompt, imageSize, cancelToken);
                String imageSource = persistGeneratedImage(image.imageSource);
                String markdown = "![生成图片](" + imageSource + ")";
                if (!image.revisedPrompt.isEmpty()) {
                    markdown += "\n\n优化后的提示词：" + image.revisedPrompt;
                }
                JSONObject output = new JSONObject();
                output.put("markdown", markdown);
                output.put("image_url", imageSource);
                return new OpenAiClient.ToolResult(output.toString(), "generate_image: 已生成图片", new JSONArray(), markdown);
            }
            if ("edit_document".equals(toolName)) {
                String filename = arguments.optString("filename", "codex-document-edited.docx").trim();
                String title = arguments.optString("title", "文档修改版").trim();
                String markdown = arguments.optString("markdown", "");
                String replacementsJson = arguments.optString("replacements_json", "[]");
                OfficeSource source = resolveOfficeSource(toolAttachments, ".docx", OfficeProcessor.MIME_DOCX);
                try {
                    String saved = saveExportBytes(
                            filename,
                            OfficeProcessor.MIME_DOCX,
                            OfficeProcessor.editDocx(source == null ? null : source.file, title, markdown, replacementsJson),
                            ".docx"
                    );
                    JSONObject output = new JSONObject();
                    output.put("file", saved);
                    output.put("format", "docx");
                    output.put("mode", "safe_copy");
                    output.put("source", source == null ? "" : source.name);
                    return new OpenAiClient.ToolResult(output.toString(), "Word 修改版已保存: " + saved, new JSONArray());
                } finally {
                    releaseOfficeSource(source);
                }
            }
            if ("edit_spreadsheet".equals(toolName)) {
                String filename = arguments.optString("filename", "codex-table-edited.xlsx").trim();
                String operationsJson = arguments.optString("operations_json", "[]");
                String appendSheetName = arguments.optString("append_sheet_name", "");
                String appendSheetCsv = arguments.optString("append_sheet_csv", "");
                OfficeSource source = resolveOfficeSource(toolAttachments, ".xlsx", OfficeProcessor.MIME_XLSX);
                if (source == null) {
                    return new OpenAiClient.ToolResult("edit_spreadsheet failed: missing XLSX source", "需要先上传 Excel 文件，或先在本对话里生成一个 Excel 文件", new JSONArray());
                }
                try {
                    String saved = saveExportBytes(
                            filename,
                            OfficeProcessor.MIME_XLSX,
                            OfficeProcessor.editXlsx(source.file, operationsJson, appendSheetName, appendSheetCsv),
                            ".xlsx"
                    );
                    JSONObject output = new JSONObject();
                    output.put("file", saved);
                    output.put("format", "xlsx");
                    output.put("mode", "safe_copy");
                    output.put("source", source.name);
                    return new OpenAiClient.ToolResult(output.toString(), "Excel 修改版已保存: " + saved, new JSONArray());
                } finally {
                    releaseOfficeSource(source);
                }
            }
            if ("edit_presentation".equals(toolName)) {
                String filename = arguments.optString("filename", "codex-slides-edited.pptx").trim();
                String title = arguments.optString("title", "演示稿修改版").trim();
                String markdown = arguments.optString("markdown", "");
                String replacementsJson = arguments.optString("replacements_json", "[]");
                OfficeSource source = resolveOfficeSource(toolAttachments, ".pptx", OfficeProcessor.MIME_PPTX);
                try {
                    String saved = saveExportBytes(
                            filename,
                            OfficeProcessor.MIME_PPTX,
                            OfficeProcessor.editPptx(source == null ? null : source.file, title, markdown, replacementsJson),
                            ".pptx"
                    );
                    JSONObject output = new JSONObject();
                    output.put("file", saved);
                    output.put("format", "pptx");
                    output.put("mode", "safe_copy");
                    output.put("source", source == null ? "" : source.name);
                    return new OpenAiClient.ToolResult(output.toString(), "PPT 修改版已保存: " + saved, new JSONArray());
                } finally {
                    releaseOfficeSource(source);
                }
            }
            if ("create_spreadsheet".equals(toolName)) {
                String filename = arguments.optString("filename", "codex-table.xlsx").trim();
                String csv = arguments.optString("csv", "").trim();
                if (csv.isEmpty()) {
                    return new OpenAiClient.ToolResult("create_spreadsheet failed: missing csv", "create_spreadsheet 缺少 csv", new JSONArray());
                }
                boolean nativeXlsx = !filename.toLowerCase(Locale.ROOT).endsWith(".csv");
                String saved = nativeXlsx
                        ? saveExportBytes(filename, OfficeProcessor.MIME_XLSX, OfficeProcessor.createXlsxFromCsv(csv), ".xlsx")
                        : saveExportFile(filename, "text/csv", csv);
                JSONObject output = new JSONObject();
                output.put("file", saved);
                output.put("format", nativeXlsx ? "xlsx" : "csv");
                return new OpenAiClient.ToolResult(output.toString(), "表格已保存: " + saved, new JSONArray());
            }
            if ("create_document".equals(toolName)) {
                String filename = arguments.optString("filename", "codex-document.docx").trim();
                String title = arguments.optString("title", "文档").trim();
                String markdown = arguments.optString("markdown", "").trim();
                if (markdown.isEmpty()) {
                    return new OpenAiClient.ToolResult("create_document failed: missing markdown", "create_document 缺少 markdown", new JSONArray());
                }
                String saved = saveExportBytes(filename, OfficeProcessor.MIME_DOCX, OfficeProcessor.createDocx(title, markdown), ".docx");
                JSONObject output = new JSONObject();
                output.put("file", saved);
                output.put("format", "docx");
                return new OpenAiClient.ToolResult(output.toString(), "Word 文档已保存: " + saved, new JSONArray());
            }
            if ("create_presentation".equals(toolName)) {
                String filename = arguments.optString("filename", "codex-slides.pptx").trim();
                String title = arguments.optString("title", "演示稿").trim();
                String markdown = arguments.optString("markdown", "").trim();
                if (markdown.isEmpty()) {
                    return new OpenAiClient.ToolResult("create_presentation failed: missing markdown", "create_presentation 缺少 markdown", new JSONArray());
                }
                boolean nativePptx = !filename.toLowerCase(Locale.ROOT).endsWith(".html");
                String saved = nativePptx
                        ? saveExportBytes(filename, OfficeProcessor.MIME_PPTX, OfficeProcessor.createPptx(title, markdown), ".pptx")
                        : saveExportFile(filename, "text/html", buildPresentationHtml(title.isEmpty() ? "演示稿" : title, markdown));
                JSONObject output = new JSONObject();
                output.put("file", saved);
                output.put("format", nativePptx ? "pptx" : "html");
                return new OpenAiClient.ToolResult(output.toString(), "演示稿已保存: " + saved, new JSONArray());
            }
            return new OpenAiClient.ToolResult("Unknown tool: " + toolName, "未知工具: " + toolName, new JSONArray());
        };
    }

    private JSONArray mergeSources(JSONArray first, JSONArray second) {
        JSONArray merged = new JSONArray();
        appendUniqueSources(merged, first);
        appendUniqueSources(merged, second);
        return merged;
    }

    private JSONArray annotateSources(JSONArray sources, String channel, String provider) {
        JSONArray annotated = new JSONArray();
        if (sources == null) {
            return annotated;
        }
        for (int i = 0; i < sources.length(); i++) {
            JSONObject item = sources.optJSONObject(i);
            if (item == null) {
                continue;
            }
            try {
                JSONObject copy = new JSONObject(item.toString());
                if (copy.optString("kind", "").isEmpty()) {
                    copy.put("kind", "source");
                }
                if (copy.optString("channel", "").isEmpty()) {
                    copy.put("channel", channel == null ? "" : channel);
                }
                if (copy.optString("provider", "").isEmpty()) {
                    copy.put("provider", provider == null ? "" : provider);
                }
                annotated.put(copy);
            } catch (Exception ignored) {
            }
        }
        return annotated;
    }

    private JSONObject searchDiagnostic(String phase, String provider, int resultCount, long elapsedMs, boolean cached, String mode) {
        JSONObject diagnostic = new JSONObject();
        try {
            String providerValue = provider == null || provider.trim().isEmpty() ? "未知搜索源" : provider.trim();
            String modeValue = "deep".equals(mode) ? "深度" : "普通";
            diagnostic.put("kind", "diagnostic");
            diagnostic.put("title", "搜索诊断");
            diagnostic.put("channel", phase == null ? "" : phase);
            diagnostic.put("provider", providerValue);
            diagnostic.put("diagnosticKey", (phase == null ? "" : phase) + "|" + providerValue + "|" + modeValue);
            diagnostic.put("resultCount", Math.max(0, resultCount));
            diagnostic.put("elapsedMs", Math.max(0L, elapsedMs));
            diagnostic.put("cached", cached);
            diagnostic.put("mode", modeValue);
            diagnostic.put("snippet", (phase == null || phase.isEmpty() ? "搜索" : phase)
                    + ": " + providerValue
                    + "，" + Math.max(0, resultCount) + " 条"
                    + "，" + Math.max(0L, elapsedMs) + "ms"
                    + (cached ? "，缓存" : "")
                    + "，" + modeValue + "模式");
        } catch (Exception ignored) {
        }
        return diagnostic;
    }

    private void appendUniqueSources(JSONArray target, JSONArray values) {
        if (target == null || values == null) {
            return;
        }
        for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.optJSONObject(i);
            if (item == null) {
                continue;
            }
            String url = item.optString("url", "");
            String kind = item.optString("kind", "");
            String diagnosticKey = item.optString("diagnosticKey", "");
            boolean exists = false;
            for (int j = 0; j < target.length(); j++) {
                JSONObject old = target.optJSONObject(j);
                if (old == null) {
                    continue;
                }
                if (!url.isEmpty() && url.equals(old.optString("url", ""))) {
                    mergeSourceMetadata(old, item);
                    exists = true;
                    break;
                }
                if ("diagnostic".equals(kind)
                        && !diagnosticKey.isEmpty()
                        && diagnosticKey.equals(old.optString("diagnosticKey", ""))) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                target.put(item);
            }
        }
    }

    private void mergeSourceMetadata(JSONObject target, JSONObject incoming) {
        if (target == null || incoming == null) {
            return;
        }
        mergeSourceField(target, incoming, "channel");
        mergeSourceField(target, incoming, "provider");
        if (target.optString("kind", "").isEmpty() && !incoming.optString("kind", "").isEmpty()) {
            try {
                target.put("kind", incoming.optString("kind", ""));
            } catch (Exception ignored) {
            }
        }
    }

    private void mergeSourceField(JSONObject target, JSONObject incoming, String key) {
        String oldValue = target.optString(key, "");
        String newValue = incoming.optString(key, "");
        if (newValue.isEmpty() || oldValue.contains(newValue)) {
            return;
        }
        try {
            target.put(key, oldValue.isEmpty() ? newValue : oldValue + " / " + newValue);
        } catch (Exception ignored) {
        }
    }

    private String saveExportFile(String requestedName, String mimeType, String content) throws IOException {
        String extension = mimeType != null && mimeType.contains("csv") ? ".csv" : ".html";
        return saveExportBytes(requestedName, mimeType, (content == null ? "" : content).getBytes(StandardCharsets.UTF_8), extension);
    }

    private String saveExportBytes(String requestedName, String mimeType, byte[] bytes, String extension) throws IOException {
        String safeName = safeExportName(requestedName, extension);
        String saved;
        try {
            saved = savePublicExportFile(safeName, mimeType, bytes);
        } catch (IOException ignored) {
            saved = savePrivateExportFile(safeName, bytes);
        }
        rememberGeneratedOfficeFile(safeName, mimeType, bytes, extension, saved);
        return saved;
    }

    private OfficeSource resolveOfficeSource(List<AttachmentItem> items, String extension, String mimeType) throws IOException {
        AttachmentItem attachment = findOfficeAttachment(items, extension, mimeType);
        if (attachment != null) {
            return new OfficeSource(copyAttachmentToTempFile(attachment, MAX_OFFICE_ATTACHMENT_BYTES), attachment.name, true);
        }
        GeneratedOfficeFile generated = findGeneratedOfficeFile(extension, mimeType);
        if (generated == null) {
            return null;
        }
        File file = new File(generated.privatePath);
        if (!file.isFile()) {
            return null;
        }
        return new OfficeSource(file, generated.name, false);
    }

    private AttachmentItem findOfficeAttachment(List<AttachmentItem> items, String extension, String mimeType) {
        if (items == null) {
            return null;
        }
        for (AttachmentItem item : items) {
            if (item == null || item.image) {
                continue;
            }
            String itemExt = fileExtension(item.name);
            String itemMime = item.mimeType == null ? "" : item.mimeType;
            if ((!extension.isEmpty() && extension.equals(itemExt)) || (!mimeType.isEmpty() && mimeType.equals(itemMime))) {
                return item;
            }
        }
        return null;
    }

    private void releaseOfficeSource(OfficeSource source) {
        if (source != null && source.temporary) {
            deleteTempFile(source.file);
        }
    }

    private void rememberGeneratedOfficeFile(String safeName, String mimeType, byte[] bytes, String extension, String displayPath) {
        if (!isEditableOfficeExport(mimeType, extension)) {
            return;
        }
        try {
            File privateCopy = writeUniqueExportFile(generatedOfficeDir(), safeName, bytes);
            GeneratedOfficeFile ref = new GeneratedOfficeFile(
                    privateCopy.getName(),
                    mimeType == null ? "" : mimeType,
                    fileExtension(privateCopy.getName()),
                    displayPath == null ? "" : displayPath,
                    privateCopy.getAbsolutePath(),
                    System.currentTimeMillis()
            );
            synchronized (generatedOfficeLock) {
                generatedOfficeFiles.add(ref);
                pendingGeneratedOfficeFiles.add(ref);
                trimGeneratedOfficeList(generatedOfficeFiles);
                trimGeneratedOfficeList(pendingGeneratedOfficeFiles);
            }
        } catch (IOException ignored) {
        }
    }

    private boolean isEditableOfficeExport(String mimeType, String extension) {
        String mime = mimeType == null ? "" : mimeType.trim();
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        return ".docx".equals(ext)
                || ".xlsx".equals(ext)
                || ".pptx".equals(ext)
                || OfficeProcessor.MIME_DOCX.equals(mime)
                || OfficeProcessor.MIME_XLSX.equals(mime)
                || OfficeProcessor.MIME_PPTX.equals(mime);
    }

    private File generatedOfficeDir() {
        File base = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (base == null) {
            base = getFilesDir();
        }
        String sessionId = currentSession == null ? "current" : currentSession.id;
        return new File(new File(base, "generated-office"), safePathSegment(sessionId));
    }

    private String safePathSegment(String value) {
        String segment = value == null ? "" : value.trim();
        if (segment.isEmpty()) {
            segment = "current";
        }
        return segment.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    private GeneratedOfficeFile findGeneratedOfficeFile(String extension, String mimeType) {
        synchronized (generatedOfficeLock) {
            for (int i = generatedOfficeFiles.size() - 1; i >= 0; i--) {
                GeneratedOfficeFile ref = generatedOfficeFiles.get(i);
                if (ref == null || ref.privatePath.isEmpty() || !new File(ref.privatePath).isFile()) {
                    generatedOfficeFiles.remove(i);
                    continue;
                }
                if (generatedOfficeMatches(ref, extension, mimeType)) {
                    return ref;
                }
            }
        }
        return null;
    }

    private boolean generatedOfficeMatches(GeneratedOfficeFile ref, String extension, String mimeType) {
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        String mime = mimeType == null ? "" : mimeType;
        return (!ext.isEmpty() && ext.equals(ref.extension))
                || (!mime.isEmpty() && mime.equals(ref.mimeType));
    }

    private void trimGeneratedOfficeList(ArrayList<GeneratedOfficeFile> files) {
        while (files.size() > MAX_GENERATED_OFFICE_CONTEXT) {
            files.remove(0);
        }
    }

    private void deleteTempFile(File file) {
        if (file == null) {
            return;
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }

    private String savePublicExportFile(String safeName, String mimeType, byte[] bytes) throws IOException {
        String displayMime = mimeType == null || mimeType.trim().isEmpty() ? "text/plain" : mimeType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
            values.put(MediaStore.Downloads.MIME_TYPE, displayMime);
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Codex/exports");
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                throw new IOException("无法创建公共导出文件");
            }
            try {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) {
                        throw new IOException("无法写入公共导出文件");
                    }
                    out.write(bytes);
                }
                ContentValues done = new ContentValues();
                done.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(uri, done, null, null);
                return "下载/Codex/exports/" + safeName;
            } catch (IOException e) {
                getContentResolver().delete(uri, null, null);
                throw e;
            }
        }
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Codex/exports");
        File file = writeUniqueExportFile(dir, safeName, bytes);
        return file.getAbsolutePath();
    }

    private String savePrivateExportFile(String safeName, byte[] bytes) throws IOException {
        File base = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (base == null) {
            base = getFilesDir();
        }
        File dir = new File(base, "exports");
        File file = writeUniqueExportFile(dir, safeName, bytes);
        return file.getAbsolutePath();
    }

    private File writeUniqueExportFile(File dir, String safeName, byte[] bytes) throws IOException {
        if (dir == null || (!dir.exists() && !dir.mkdirs())) {
            throw new IOException("无法创建导出目录");
        }
        String extension = "";
        int dot = safeName.lastIndexOf('.');
        if (dot >= 0) {
            extension = safeName.substring(dot);
        }
        String base = extension.isEmpty() ? safeName : safeName.substring(0, safeName.length() - extension.length());
        File file = new File(dir, safeName);
        int suffix = 2;
        while (file.exists() && suffix < 1000) {
            file = new File(dir, base + "-" + suffix + extension);
            suffix++;
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes == null ? new byte[0] : bytes);
        }
        return file;
    }

    private String safeExportName(String requestedName, String extension) {
        String value = requestedName == null ? "" : requestedName.trim();
        if (value.isEmpty()) {
            value = "codex-export" + extension;
        }
        value = value.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", "_")
                .replaceAll("\\s+", "_");
        if (!value.toLowerCase(Locale.ROOT).endsWith(extension)) {
            value += extension;
        }
        return value;
    }

    private String buildPresentationHtml(String title, String markdown) {
        String[] slides = (markdown == null ? "" : markdown).split("(?m)^---\\s*$");
        StringBuilder builder = new StringBuilder();
        builder.append("<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">");
        builder.append("<title>").append(escapeHtml(title)).append("</title>");
        builder.append("<style>body{margin:0;background:#f6f7f9;color:#121316;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;}");
        builder.append(".deck{display:grid;gap:18px;padding:18px;}.slide{aspect-ratio:16/9;background:#fff;border:1px solid #dedfe3;border-radius:16px;padding:34px;box-shadow:0 10px 28px rgba(15,23,42,.08);overflow:hidden;}");
        builder.append("h1{font-size:34px;margin:0 0 18px;}h2{font-size:26px;margin:0 0 14px;}p,li{font-size:18px;line-height:1.55;}ul{padding-left:24px;}code{background:#eef0f3;border-radius:6px;padding:2px 5px;}</style></head><body><main class=\"deck\">");
        if (slides.length == 0) {
            slides = new String[]{markdown == null ? "" : markdown};
        }
        for (String slide : slides) {
            builder.append("<section class=\"slide\">").append(markdownToSimpleHtml(slide)).append("</section>");
        }
        builder.append("</main></body></html>");
        return builder.toString();
    }

    private String markdownToSimpleHtml(String markdown) {
        String[] lines = (markdown == null ? "" : markdown.trim()).split("\\r?\\n");
        StringBuilder builder = new StringBuilder();
        boolean inList = false;
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("# ")) {
                if (inList) {
                    builder.append("</ul>");
                    inList = false;
                }
                builder.append("<h1>").append(escapeHtml(line.substring(2).trim())).append("</h1>");
            } else if (line.startsWith("## ")) {
                if (inList) {
                    builder.append("</ul>");
                    inList = false;
                }
                builder.append("<h2>").append(escapeHtml(line.substring(3).trim())).append("</h2>");
            } else if (line.startsWith("- ") || line.startsWith("* ")) {
                if (!inList) {
                    builder.append("<ul>");
                    inList = true;
                }
                builder.append("<li>").append(escapeHtml(line.substring(2).trim())).append("</li>");
            } else {
                if (inList) {
                    builder.append("</ul>");
                    inList = false;
                }
                builder.append("<p>").append(escapeHtml(line)).append("</p>");
            }
        }
        if (inList) {
            builder.append("</ul>");
        }
        return builder.length() == 0 ? "<p></p>" : builder.toString();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private boolean shouldAutoGenerateImage(String prompt) {
        String value = prompt == null ? "" : prompt.trim();
        if (value.isEmpty()) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.contains("image api")
                || lower.contains("图片生成接口")
                || lower.contains("生图接口")
                || lower.contains("生图模型")
                || lower.contains("生图功能")
                || lower.contains("生图按钮")
                || lower.contains("为什么")
                || lower.contains("报错")
                || lower.contains("乱码")
                || lower.contains("分析这张")
                || lower.contains("识别这张")
                || lower.contains("解释这张")) {
            return false;
        }
        if (value.matches(".*(画|绘制|生成|制作|做|设计|创作|出)(一张|张|个|幅)?(图片|图像|照片|插画|海报|头像|壁纸|logo|图标|表情包|封面|场景).*")) {
            return true;
        }
        if (value.matches(".*(图片|图像|照片|插画|海报|头像|壁纸|logo|图标|表情包|封面|场景).*(画|绘制|生成|制作|设计|创作|出图).*")) {
            return true;
        }
        if (value.contains("生图") && !value.matches(".*(怎么|如何|为什么|教程|说明|按钮|接口|模型|失败|报错|乱码).*")) {
            return true;
        }
        return lower.matches(".*(generate|create|draw|make|design)\\s+(an?\\s+)?(image|picture|photo|poster|logo|avatar|wallpaper|illustration).*")
                || lower.matches(".*(image|picture|photo|poster|logo|avatar|wallpaper|illustration).*(generate|create|draw|make|design).*");
    }

    private boolean shouldUseTextImageFallback(String prompt) {
        String value = prompt == null ? "" : prompt.trim();
        if (value.isEmpty()) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        String[] negative = {
                "为什么", "怎么", "如何", "教程", "说明", "接口", "模型", "报错", "失败", "乱码",
                "分析这张", "识别这张", "解释这张", "看这张", "上传的图"
        };
        for (String word : negative) {
            if (value.contains(word)) {
                return false;
            }
        }
        boolean action = value.contains("画")
                || value.contains("绘制")
                || value.contains("生成")
                || value.contains("做一张")
                || value.contains("设计")
                || value.contains("出图")
                || lower.matches(".*\\b(draw|generate|create|make|design)\\b.*");
        boolean target = value.contains("图")
                || value.contains("图片")
                || value.contains("示意图")
                || value.contains("流程图")
                || value.contains("海报")
                || value.contains("插画")
                || value.contains("头像")
                || value.contains("壁纸")
                || value.contains("logo")
                || lower.matches(".*\\b(image|picture|photo|poster|logo|avatar|wallpaper|illustration|diagram)\\b.*");
        return (action && target) || shouldAutoGenerateImage(prompt);
    }

    private String normalizeAssistantOutput(String text) {
        String value = text == null ? "" : text;
        String persisted = persistInlineDataImages(value);
        if (!persisted.equals(value)) {
            return persisted;
        }
        String compact = value.trim().replaceAll("\\s+", "");
        if (looksLikeRawImageBase64(compact)) {
            String imageSource = persistGeneratedImage("data:image/png;base64," + compact);
            return "![生成图片](" + imageSource + ")";
        }
        return value;
    }

    private String normalizeTextOnlyImageFallbackOutput(String text, String prompt) {
        String value = text == null ? "" : text.trim();
        String compact = value.replaceAll("\\s+", "");
        boolean rawImage = looksLikeRawImageBase64(compact)
                || DATA_IMAGE_PATTERN.matcher(value).find()
                || value.matches("(?is).*\"b64_json\"\\s*:\\s*\"[A-Za-z0-9+/=\\r\\n]{512,}\".*")
                || value.matches("(?is).*\"image_url\"\\s*:\\s*\"data:image/[^\"']+\".*");
        if (!rawImage && !value.isEmpty()) {
            return value;
        }
        String topic = prompt == null || prompt.trim().isEmpty() ? "这个需求" : prompt.trim();
        return "我不能在未点击生图按钮时直接生成真实图片。下面先用字符示意图表达你的需求；如果要生成真实图片，请点底部生图按钮。\n\n"
                + "```text\n"
                + "文本示意图\n"
                + "\n"
                + "主题: " + topic.replace("\n", " ") + "\n"
                + "\n"
                + "+-----------------------------+\n"
                + "|            标题/主体         |\n"
                + "|                             |\n"
                + "|     关键元素 A  ->  关键元素 B |\n"
                + "|        说明、关系、方向       |\n"
                + "+-----------------------------+\n"
                + "```\n\n"
                + "可以继续告诉我画面里要有哪些元素、方向和标注，我会把这个字符示意图细化。";
    }

    private String persistInlineDataImages(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        Matcher matcher = DATA_IMAGE_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        int count = 0;
        String onlyReplacement = "";
        String onlySource = "";
        while (matcher.find()) {
            count++;
            String source = matcher.group().replaceAll("\\s+", "");
            String persisted = persistGeneratedImage(source);
            onlySource = source;
            onlyReplacement = persisted;
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(persisted));
        }
        if (count == 0) {
            return text;
        }
        matcher.appendTail(buffer);
        if (count == 1 && text.trim().replaceAll("\\s+", "").equals(onlySource)) {
            return "![生成图片](" + onlyReplacement + ")";
        }
        return buffer.toString();
    }

    private boolean looksLikeRawImageBase64(String value) {
        if (value == null || value.length() < RAW_IMAGE_BASE64_MIN_CHARS || !BASE64_IMAGE_PATTERN.matcher(value).matches()) {
            return false;
        }
        try {
            byte[] bytes = Base64.decode(value, Base64.DEFAULT);
            return looksLikeImageBytes(bytes);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean looksLikeImageBytes(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return false;
        }
        boolean png = (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47;
        boolean jpg = (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8;
        boolean webp = bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46
                && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50;
        return png || jpg || webp;
    }

    private String buildUserBlock(String prompt, List<AttachmentItem> items, boolean useSearch, boolean useAgentTools) {
        StringBuilder builder = new StringBuilder();
        if (!prompt.isEmpty()) {
            builder.append(prompt);
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
        String rawPrompt = prompt == null ? "" : prompt.trim();
        String trimmed = promptWithSearch == null ? "" : promptWithSearch.trim();
        if (rawPrompt.startsWith("修改要求")) {
            String target = revisionTargetText.trim().isEmpty() ? lastAssistantText : revisionTargetText;
            if (!target.trim().isEmpty()) {
                String requirement = rawPrompt.replaceFirst("^修改要求[:：]?\\s*", "").trim();
                String requirementText = requirement.isEmpty() ? rawPrompt : requirement;
                String requirementWithSearch = searchResults == null || searchResults.isEmpty()
                        ? requirementText
                        : buildPromptWithSearchContext(requirementText, searchResults);
                StringBuilder builder = new StringBuilder();
                if (!conversationTranscript.isEmpty()) {
                    builder.append("以下是被修改消息之前的对话上下文，用于保持连续对话。请不要复述上下文。\n\n")
                            .append(conversationTranscript)
                            .append("\n\n");
                }
                builder.append("用户正在对自己之前发送的一条内容补充或修改要求。\n\n");
                builder.append("原内容:\n").append(target).append("\n\n");
                builder.append("修改要求:\n").append(requirementWithSearch).append("\n\n");
                builder.append("请把原内容和修改要求合并理解，直接接着回答修改后的最新需求。");
                return applyCustomInstructions(builder.toString());
            }
        }
        if (!conversationTranscript.isEmpty() && !rawPrompt.startsWith("修改要求")) {
            return applyCustomInstructions("以下是当前对话上下文，用于保持连续对话。请只回答用户最新消息，不要复述上下文。\n\n"
                    + conversationTranscript
                    + "\n\n用户最新消息:\n" + trimmed);
        }
        return applyCustomInstructions(promptWithSearch);
    }

    private String buildImageTextFallbackPrompt(String apiPrompt, String originalPrompt) {
        StringBuilder builder = new StringBuilder();
        builder.append("用户这次没有点击 App 的生图按钮，所以你不能调用真实生图，也不要输出 base64、图片 JSON、工具调用残片或图片链接占位。\n");
        builder.append("如果用户是在要求“画图、生成图片、示意图、设计图”，请改用文字说明和一个 `text` 代码块画字符示意图。");
        builder.append("字符图要尽量清楚，配合简短解释；如果无法画得精确，就明确说这是文本示意，仅供理解。\n\n");
        builder.append("用户原始意图:\n").append(originalPrompt == null ? "" : originalPrompt.trim()).append("\n\n");
        builder.append("请基于下面的完整请求继续回答:\n");
        builder.append(apiPrompt == null ? "" : apiPrompt);
        return builder.toString();
    }

    private String applyCustomInstructions(String prompt) {
        String instructions = currentCustomInstructions();
        if (instructions.isEmpty()) {
            return prompt;
        }
        return "以下是用户在 App 中保存的个人指令/长期偏好，请在不冲突的情况下遵守，不要主动复述。\n\n"
                + instructions
                + "\n\n用户请求:\n"
                + (prompt == null ? "" : prompt);
    }

    private String buildPromptWithSearchContext(String prompt, List<SearchClient.SearchResult> searchResults) {
        if (searchResults == null || searchResults.isEmpty()) {
            return prompt;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("App 已经完成本次联网搜索，下面是实时搜索资料。你必须优先根据这些资料回答用户最新问题。不要说你不能联网、不能实时搜索、不能打开网页；如果资料不足，就说明资料不足并基于已有来源回答。用户要求搜索、检索、查找、在某网站查资料时，直接给本次检索得到的结果清单和简短结论，不要只给搜索方法、关键词建议或操作步骤。标题包含“站内检索入口”的来源只是打开入口，不是具体文献条目。使用资料中的事实时，请在句末标注对应来源编号，例如 [1]。不要编造资料中没有的来源。\n\n");
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

    private JSONObject buildLocalContextResult(String query) {
        JSONObject output = new JSONObject();
        try {
            String value = query == null ? "" : query.trim();
            output.put("query", value);
            output.put("current_session_id", currentSession == null ? "" : currentSession.id);
            output.put("current_session_title", currentSession == null ? "" : currentSession.title);
            output.put("compressed_or_recent_context", limitContext(conversationTranscript, CONTEXT_SUMMARY_CHARS));
            output.put("current_matches", currentContextMatches(value, CONTEXT_SEARCH_LIMIT));
            output.put("generated_files", generatedOfficeContext(value, CONTEXT_SEARCH_LIMIT));
            output.put("note", "这些结果只来自当前对话的当前分支、自动压缩摘要和当前对话已生成 Office 文件记录；不会跨不同聊天记录查询。");
        } catch (Exception ignored) {
        }
        return output;
    }

    private JSONArray currentContextMatches(String query, int limit) {
        JSONArray matches = new JSONArray();
        if (currentSession == null || currentSession.messages == null) {
            return matches;
        }
        ArrayList<Integer> path = activeMessageIndexes(currentSession);
        int safeLimit = Math.max(1, Math.min(20, limit));
        for (int p = path.size() - 1; p >= 0 && matches.length() < safeLimit; p--) {
            int index = path.get(p);
            JSONObject message = currentSession.messages.optJSONObject(index);
            if (message == null) {
                continue;
            }
            String text = message.optString("text", "");
            if (!matchesContextQuery(text, query) && !matchesContextQuery(currentSession.title, query)) {
                continue;
            }
            JSONObject item = new JSONObject();
            try {
                item.put("index", index);
                item.put("role", message.optString("role", ""));
                item.put("text", snippetForContext(text, query, 1200));
                item.put("time", message.optLong("time", 0L));
            } catch (Exception ignored) {
            }
            matches.put(item);
        }
        if (matches.length() == 0) {
            int start = Math.max(0, path.size() - Math.min(6, path.size()));
            for (int p = start; p < path.size() && matches.length() < safeLimit; p++) {
                int index = path.get(p);
                JSONObject message = currentSession.messages.optJSONObject(index);
                if (message == null) {
                    continue;
                }
                JSONObject item = new JSONObject();
                try {
                    item.put("index", index);
                    item.put("role", message.optString("role", ""));
                    item.put("text", snippetForContext(message.optString("text", ""), query, 900));
                    item.put("time", message.optLong("time", 0L));
                    item.put("fallback", true);
                } catch (Exception ignored) {
                }
                matches.put(item);
            }
        }
        return matches;
    }

    private JSONArray generatedOfficeContext(String query, int limit) {
        JSONArray files = new JSONArray();
        int safeLimit = Math.max(1, Math.min(20, limit));
        synchronized (generatedOfficeLock) {
            for (int i = generatedOfficeFiles.size() - 1; i >= 0 && files.length() < safeLimit; i--) {
                GeneratedOfficeFile ref = generatedOfficeFiles.get(i);
                if (ref == null) {
                    continue;
                }
                String haystack = ref.name + " " + ref.extension + " " + ref.displayPath + " " + ref.mimeType;
                if (!matchesContextQuery(haystack, query) && files.length() > 0) {
                    continue;
                }
                JSONObject item = ref.toJson();
                try {
                    item.remove("privatePath");
                    item.put("available_for_edit", !ref.privatePath.isEmpty() && new File(ref.privatePath).isFile());
                } catch (Exception ignored) {
                }
                files.put(item);
            }
        }
        return files;
    }

    private boolean matchesContextQuery(String text, String query) {
        String haystack = text == null ? "" : text.toLowerCase(Locale.ROOT);
        String value = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return true;
        }
        if (haystack.contains(value)) {
            return true;
        }
        ArrayList<String> terms = contextTerms(value);
        if (terms.isEmpty()) {
            return false;
        }
        int matched = 0;
        for (String term : terms) {
            if (haystack.contains(term)) {
                matched++;
            }
        }
        return matched > 0 && matched >= Math.min(2, terms.size());
    }

    private ArrayList<String> contextTerms(String query) {
        ArrayList<String> terms = new ArrayList<>();
        String value = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return terms;
        }
        String[] pieces = value.split("[\\s,，。；;：:、]+");
        for (String piece : pieces) {
            String term = piece == null ? "" : piece.trim();
            if (term.length() < 2 || terms.contains(term)) {
                continue;
            }
            terms.add(term);
            if (terms.size() >= 8) {
                break;
            }
        }
        return terms;
    }

    private String snippetForContext(String text, String query, int maxChars) {
        String value = cleanContextText(text);
        if (value.length() <= maxChars) {
            return value;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int center = needle.isEmpty() ? -1 : lower.indexOf(needle);
        if (center < 0) {
            for (String term : contextTerms(needle)) {
                center = lower.indexOf(term);
                if (center >= 0) {
                    break;
                }
            }
        }
        if (center < 0) {
            return limitContext(value, maxChars);
        }
        int start = Math.max(0, center - maxChars / 3);
        int end = Math.min(value.length(), start + maxChars);
        return (start > 0 ? "..." : "") + value.substring(start, end) + (end < value.length() ? "..." : "");
    }

    private String cleanContextText(String text) {
        return (text == null ? "" : text)
                .replaceAll("data:image/[^\\s)]+", "[内嵌图片]")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String limitContext(String text, int maxChars) {
        String value = cleanContextText(text);
        int safeMax = Math.max(200, maxChars);
        if (value.length() <= safeMax) {
            return value;
        }
        return value.substring(0, safeMax) + "\n...（已压缩截断）";
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
        String value = transcript == null ? "" : transcript;
        int maxChars = 24000;
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, 4000)
                + "\n\n...（中间上下文已本地压缩；需要细节时调用 search_context 查询）...\n\n"
                + value.substring(value.length() - (maxChars - 4600));
    }

    private void startRevision() {
        startRevisionFromText(lastAssistantText);
    }

    private void startRevisionFromText(String targetText) {
        String target = targetText == null ? "" : targetText.trim();
        if (target.isEmpty()) {
            toast("还没有可修改的回复");
            return;
        }
        revisionTargetText = target;
        messageInput.setText("修改要求：\n");
        messageInput.setSelection(messageInput.getText().length());
        messageInput.requestFocus();
        showKeyboard(messageInput);
        setStatus("写下修改要求后发送");
    }

    private void startRevisionFromMessageIndex(int messageIndex, String targetText) {
        String target = targetText == null ? "" : targetText.trim();
        if (target.isEmpty()) {
            toast("无法定位这条消息");
            return;
        }
        JSONObject targetMessage = messageAtIndex(messageIndex);
        if (targetMessage != null && "user".equals(targetMessage.optString("role", ""))) {
            pendingAssistantParentNodeId = nodeIdOf(targetMessage);
            pendingBranchParentMessageIndex = messageIndex;
        }
        MessageContext context = contextUntilMessage(messageIndex);
        conversationTranscript = context.transcript;
        lastAssistantText = context.lastAssistant;
        lastUserPrompt = context.lastUser.isEmpty() ? target : context.lastUser;
        revisionTargetText = target;
        lastResponseId = "";
        saveCurrentSession();
        messageInput.setText("修改要求：\n");
        messageInput.setSelection(messageInput.getText().length());
        messageInput.requestFocus();
        showKeyboard(messageInput);
        setStatus("写下修改要求后发送，会接着这条消息继续回答");
    }

    private MessageContext contextUntilMessage(int messageIndex) {
        MessageContext context = new MessageContext();
        if (currentSession == null || currentSession.messages == null || messageIndex < 0) {
            return context;
        }
        ensureBranchMetadata(currentSession);
        String pendingUser = "";
        StringBuilder builder = new StringBuilder();
        ArrayList<Integer> path = activeMessageIndexes(currentSession);
        for (Integer index : path) {
            if (index == null || index > messageIndex) {
                break;
            }
            JSONObject message = currentSession.messages.optJSONObject(index);
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
                context.lastUser = textValue;
            } else if ("assistant".equals(role)) {
                if (builder.length() > 0) {
                    builder.append("\n\n");
                }
                builder.append("用户: ").append(pendingUser);
                builder.append("\n助手: ").append(textValue);
                context.lastAssistant = textValue;
                pendingUser = "";
            }
        }
        context.transcript = trimTranscript(builder.toString());
        return context;
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
        int lastUserIndex = lastVisibleUserMessageIndex();
        JSONObject userMessage = messageAtIndex(lastUserIndex);
        if (userMessage != null) {
            pendingAssistantParentNodeId = nodeIdOf(userMessage);
            pendingBranchParentMessageIndex = lastUserIndex;
        }
        messageInput.setText(lastUserPrompt);
        sendCurrentMessage(true);
    }

    private int lastVisibleUserMessageIndex() {
        if (currentSession == null || currentSession.messages == null) {
            return -1;
        }
        ArrayList<Integer> path = activeMessageIndexes(currentSession);
        for (int i = path.size() - 1; i >= 0; i--) {
            int index = path.get(i);
            JSONObject message = currentSession.messages.optJSONObject(index);
            if (message != null && "user".equals(message.optString("role", ""))) {
                return index;
            }
        }
        return -1;
    }

    private void regenerateFromMessageIndex(int messageIndex, String promptText) {
        String target = promptText == null ? "" : promptText.trim();
        if (target.isEmpty()) {
            toast("无法定位这条消息");
            return;
        }
        JSONObject targetMessage = messageAtIndex(messageIndex);
        if (targetMessage == null || !"user".equals(targetMessage.optString("role", ""))) {
            toast("只能从用户消息重新生成");
            return;
        }
        pendingAssistantParentNodeId = nodeIdOf(targetMessage);
        pendingBranchParentMessageIndex = messageIndex;
        MessageContext context = contextUntilMessage(messageIndex);
        conversationTranscript = context.transcript;
        lastAssistantText = context.lastAssistant;
        lastUserPrompt = target;
        revisionTargetText = "";
        lastResponseId = "";
        if (!attachments.isEmpty()) {
            attachments.clear();
            refreshAttachmentView();
        }
        messageInput.setText(target);
        sendCurrentMessage(true);
    }

    private void switchAssistantVariant(int userMessageIndex, int direction) {
        JSONObject userMessage = messageAtIndex(userMessageIndex);
        if (userMessage == null || !"user".equals(userMessage.optString("role", ""))) {
            toast("无法定位分支");
            return;
        }
        String userNodeId = nodeIdOf(userMessage);
        ArrayList<Integer> variants = assistantVariantIndexes(userNodeId);
        if (variants.size() <= 1) {
            return;
        }
        int active = indexOfActiveAssistantVariant(userNodeId, variants);
        if (active < 0) {
            active = 0;
        }
        int next = Math.max(0, Math.min(variants.size() - 1, active + (direction < 0 ? -1 : 1)));
        if (next == active) {
            return;
        }
        JSONObject nextMessage = currentSession.messages.optJSONObject(variants.get(next));
        if (nextMessage == null) {
            return;
        }
        setActiveChild(userNodeId, nodeIdOf(nextMessage));
        conversationTranscript = rebuildTranscriptFromMessages(currentSession.messages);
        updateLastTurnFromActivePath();
        saveCurrentSession();
        renderSessionMessages(currentSession);
        setStatus("已切换回答 " + (next + 1) + "/" + variants.size());
    }

    private JSONObject messageAtIndex(int messageIndex) {
        if (currentSession == null || currentSession.messages == null || messageIndex < 0 || messageIndex >= currentSession.messages.length()) {
            return null;
        }
        ensureBranchMetadata(currentSession);
        return currentSession.messages.optJSONObject(messageIndex);
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
        revisionTargetText = "";
        conversationTranscript = "";
        clearPendingBranchReply();
        attachments.clear();
        clearGeneratedOfficeContext();
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
        clearPendingBranchReply();
        ensureRenderableMessages(session);
        if (ensureBranchMetadata(session)) {
            chatStore.save(session);
        }
        lastResponseId = session.responseId == null ? "" : session.responseId;
        lastModel = session.lastModel == null ? "" : session.lastModel;
        lastApiMode = session.apiMode == null ? "" : session.apiMode;
        conversationTranscript = rebuildTranscriptFromMessages(session.messages);
        updateLastTurnFromActivePath();
        restoreGeneratedOfficeFilesFromMessages(session);
    }

    private void clearGeneratedOfficeContext() {
        synchronized (generatedOfficeLock) {
            generatedOfficeFiles.clear();
            pendingGeneratedOfficeFiles.clear();
        }
    }

    private void restoreGeneratedOfficeFilesFromMessages(ChatStore.Session session) {
        synchronized (generatedOfficeLock) {
            generatedOfficeFiles.clear();
            pendingGeneratedOfficeFiles.clear();
            if (session == null || session.messages == null) {
                return;
            }
            for (int i = 0; i < session.messages.length(); i++) {
                JSONObject message = session.messages.optJSONObject(i);
                if (message == null) {
                    continue;
                }
                JSONObject metadata = message.optJSONObject("metadata");
                JSONArray files = metadata == null ? null : metadata.optJSONArray("generated_office_files");
                if (files == null) {
                    continue;
                }
                for (int j = 0; j < files.length(); j++) {
                    GeneratedOfficeFile ref = GeneratedOfficeFile.fromJson(files.optJSONObject(j));
                    if (ref == null || ref.privatePath.isEmpty() || !new File(ref.privatePath).isFile()) {
                        continue;
                    }
                    generatedOfficeFiles.add(ref);
                    trimGeneratedOfficeList(generatedOfficeFiles);
                }
            }
        }
    }

    private void ensureRenderableMessages(ChatStore.Session session) {
        if (session == null) {
            return;
        }
        if (session.messages != null && session.messages.length() > 0) {
            sanitizeSessionImagePayloads(session);
            return;
        }
        JSONArray recovered = messagesFromTranscript(session.transcript);
        if (recovered.length() == 0) {
            addRecoveredMessage(recovered, "user", session.lastUserPrompt);
            addRecoveredMessage(recovered, "assistant", session.lastAssistantText);
        }
        if (recovered.length() > 0) {
            session.messages = recovered;
            if (session.transcript == null || session.transcript.trim().isEmpty()) {
                session.transcript = rebuildTranscriptFromMessages(recovered);
            }
            chatStore.save(session);
        }
        sanitizeSessionImagePayloads(session);
    }

    private void sanitizeSessionImagePayloads(ChatStore.Session session) {
        if (session == null || session.messages == null || session.messages.length() == 0) {
            return;
        }
        boolean changed = false;
        for (int i = 0; i < session.messages.length(); i++) {
            JSONObject message = session.messages.optJSONObject(i);
            if (message == null) {
                continue;
            }
            String textValue = message.optString("text", "");
            String cleanedText = persistInlineDataImages(textValue);
            String reasoningValue = message.optString("reasoning", "");
            String cleanedReasoning = persistInlineDataImages(reasoningValue);
            try {
                if (!cleanedText.equals(textValue)) {
                    message.put("text", cleanedText);
                    changed = true;
                }
                if (!cleanedReasoning.equals(reasoningValue)) {
                    message.put("reasoning", cleanedReasoning);
                    changed = true;
                }
            } catch (Exception ignored) {
            }
        }
        if (changed) {
            chatStore.save(session);
        }
    }

    private JSONArray messagesFromTranscript(String transcript) {
        JSONArray recovered = new JSONArray();
        String textValue = transcript == null
                ? ""
                : transcript.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (textValue.isEmpty()) {
            return recovered;
        }
        int position = 0;
        while (position < textValue.length()) {
            int userStart = textValue.indexOf("用户:", position);
            if (userStart < 0) {
                break;
            }
            int assistantStart = textValue.indexOf("\n助手:", userStart);
            if (assistantStart < 0) {
                addRecoveredMessage(recovered, "user", textValue.substring(userStart + 3).trim());
                break;
            }
            int nextUserStart = textValue.indexOf("\n\n用户:", assistantStart + 1);
            String userText = textValue.substring(userStart + 3, assistantStart).trim();
            String assistantText = nextUserStart < 0
                    ? textValue.substring(assistantStart + 4).trim()
                    : textValue.substring(assistantStart + 4, nextUserStart).trim();
            addRecoveredMessage(recovered, "user", userText);
            addRecoveredMessage(recovered, "assistant", assistantText);
            if (nextUserStart < 0) {
                break;
            }
            position = nextUserStart + 2;
        }
        return recovered;
    }

    private void addRecoveredMessage(JSONArray messages, String role, String text) {
        String value = text == null ? "" : text.trim();
        if (messages == null || value.isEmpty()) {
            return;
        }
        try {
            JSONObject json = new JSONObject();
            json.put("role", role);
            json.put("text", value);
            json.put("reasoning", "");
            json.put("time", System.currentTimeMillis());
            messages.put(json);
        } catch (Exception ignored) {
        }
    }

    private String rebuildTranscriptFromMessages(JSONArray messages) {
        if (messages == null || currentSession == null) {
            return "";
        }
        ArrayList<Integer> path = activeMessageIndexes(currentSession);
        String full = transcriptFromPath(messages, path, 0, path.size());
        if (full.length() <= CONTEXT_DIRECT_TRANSCRIPT_CHARS || path.size() <= CONTEXT_RECENT_MESSAGE_COUNT + 2) {
            return full;
        }
        int recentStart = Math.max(0, path.size() - CONTEXT_RECENT_MESSAGE_COUNT);
        String olderSummary = compressedContextSummary(messages, path, recentStart);
        String recent = transcriptFromPath(messages, path, recentStart, path.size());
        StringBuilder builder = new StringBuilder();
        if (!olderSummary.isEmpty()) {
            builder.append("【自动压缩上下文摘要】\n")
                    .append("下面是当前分支较早对话的本地压缩摘要，用于保持连续性；如需更细节可调用 search_context 查询本地历史。\n")
                    .append(olderSummary);
        }
        if (!recent.isEmpty()) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("【最近完整对话】\n").append(limitContext(recent, CONTEXT_RECENT_TRANSCRIPT_CHARS));
        }
        return builder.toString();
    }

    private String transcriptFromPath(JSONArray messages, ArrayList<Integer> path, int startInclusive, int endExclusive) {
        if (messages == null || path == null || path.isEmpty()) {
            return "";
        }
        int safeStart = Math.max(0, startInclusive);
        int safeEnd = Math.min(path.size(), Math.max(safeStart, endExclusive));
        String pendingUser = "";
        StringBuilder builder = new StringBuilder();
        for (int p = safeStart; p < safeEnd; p++) {
            Integer index = path.get(p);
            if (index == null) {
                continue;
            }
            JSONObject message = messages.optJSONObject(index);
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
        if (!pendingUser.isEmpty()) {
            if (builder.length() > 0) {
                builder.append("\n\n");
            }
            builder.append("用户: ").append(pendingUser);
        }
        return trimTranscript(builder.toString());
    }

    private String compressedContextSummary(JSONArray messages, ArrayList<Integer> path, int endExclusive) {
        if (messages == null || path == null || endExclusive <= 0) {
            return "";
        }
        StringBuilder summary = new StringBuilder();
        String pendingUser = "";
        int turn = 0;
        int safeEnd = Math.min(path.size(), endExclusive);
        for (int p = 0; p < safeEnd; p++) {
            JSONObject message = messages.optJSONObject(path.get(p));
            if (message == null) {
                continue;
            }
            String role = message.optString("role", "");
            String text = cleanContextText(message.optString("text", ""));
            if (text.isEmpty()) {
                continue;
            }
            if ("user".equals(role)) {
                pendingUser = text;
            } else if ("assistant".equals(role)) {
                turn++;
                if (summary.length() > 0) {
                    summary.append("\n");
                }
                summary.append("- 轮次 ").append(turn).append("：用户=")
                        .append(limitContext(pendingUser, 360))
                        .append("；助手=")
                        .append(limitContext(text, 520));
                appendGeneratedFilesSummary(summary, message);
                pendingUser = "";
            } else if ("system".equals(role)) {
                if (summary.length() > 0) {
                    summary.append("\n");
                }
                summary.append("- 系统提示：").append(limitContext(text, 360));
            }
            if (summary.length() >= CONTEXT_SUMMARY_CHARS) {
                summary.append("\n...（更早内容已省略，可通过 search_context 继续查）");
                break;
            }
        }
        return limitContext(summary.toString(), CONTEXT_SUMMARY_CHARS);
    }

    private void appendGeneratedFilesSummary(StringBuilder summary, JSONObject message) {
        JSONObject metadata = message == null ? null : message.optJSONObject("metadata");
        JSONArray files = metadata == null ? null : metadata.optJSONArray("generated_office_files");
        if (files == null || files.length() == 0) {
            return;
        }
        summary.append("；生成文件=");
        for (int i = 0; i < files.length(); i++) {
            JSONObject file = files.optJSONObject(i);
            if (file == null) {
                continue;
            }
            if (i > 0) {
                summary.append(", ");
            }
            summary.append(file.optString("name", "文件"));
            String path = file.optString("displayPath", "");
            if (!path.isEmpty()) {
                summary.append("(").append(path).append(")");
            }
        }
    }

    private void updateLastTurnFromActivePath() {
        lastAssistantText = "";
        lastUserPrompt = "";
        if (currentSession == null || currentSession.messages == null) {
            return;
        }
        ArrayList<Integer> path = activeMessageIndexes(currentSession);
        for (Integer index : path) {
            if (index == null) {
                continue;
            }
            JSONObject message = currentSession.messages.optJSONObject(index);
            if (message == null) {
                continue;
            }
            String role = message.optString("role", "");
            String textValue = message.optString("text", "").trim();
            if (textValue.isEmpty()) {
                continue;
            }
            if ("user".equals(role)) {
                lastUserPrompt = textValue;
            } else if ("assistant".equals(role)) {
                lastAssistantText = textValue;
            }
        }
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
        if (session == null) {
            return;
        }
        ensureRenderableMessages(session);
        if (ensureBranchMetadata(session)) {
            chatStore.save(session);
        }
        JSONArray messages = visibleMessagesJson(session);
        if (messages.length() == 0 && shouldShowEmptyHistoryNotice(session)) {
            messages = new JSONArray();
            addRecoveredMessage(messages, "system", "这条历史记录只有标题，没有保存到消息正文；可以继续发送消息，后续历史会正常显示。");
        }
        int totalMessages = messages.length();
        int startIndex = Math.max(0, totalMessages - MAX_RENDERED_HISTORY_MESSAGES);
        historyRenderStartIndex = startIndex;
        int remainingCount = startIndex;
        JSONArray items = jsonArraySlice(messages, startIndex, totalMessages);
        int generation = ++historyRenderGeneration;
        runChatJs(historyRenderScript("renderMessages", items, startIndex, remainingCount, generation));
    }

    private boolean shouldShowEmptyHistoryNotice(ChatStore.Session session) {
        if (session == null || session.title == null) {
            return false;
        }
        String title = session.title.trim();
        return !title.isEmpty() && !"新聊天".equals(title);
    }

    private void loadEarlierHistoryMessages() {
        if (currentSession == null || currentSession.messages == null) {
            return;
        }
        JSONArray visible = visibleMessagesJson(currentSession);
        if (historyRenderStartIndex <= 0) {
            toast("已经到最早的消息了");
            return;
        }
        int endIndex = historyRenderStartIndex;
        int startIndex = Math.max(0, endIndex - MAX_RENDERED_HISTORY_MESSAGES);
        historyRenderStartIndex = startIndex;
        int remainingCount = startIndex;
        JSONArray items = jsonArraySlice(visible, startIndex, endIndex);
        runChatJs(historyRenderScript("prependMessages", items, startIndex, remainingCount, historyRenderGeneration));
    }

    private String historyRenderScript(String method, JSONArray items, int startIndex, int remainingCount, int generation) {
        String itemJson = js(items == null ? "[]" : items.toString());
        String methodName = "prependMessages".equals(method) ? "prependMessages" : "renderMessages";
        String fallbackNote = remainingCount > 0
                ? "历史较长，顶部按钮可继续加载更早消息。"
                : "";
        return "(function(){"
                + "var method=" + js(methodName) + ";"
                + "var startIndex=" + startIndex + ";"
                + "var remainingCount=" + remainingCount + ";"
                + "var generation=" + generation + ";"
                + "var note=" + js(fallbackNote) + ";"
                + "var items=[];"
                + "try{items=JSON.parse(" + itemJson + ");}catch(parseError){items=[];}"
                + "if(!Array.isArray(items)){items=[];}"
                + "var previous=Number(window.__codexHistoryRenderGeneration||0);"
                + "if(generation&&previous&&generation<previous){return 'STALE_RENDER';}"
                + "if(generation){window.__codexHistoryRenderGeneration=generation;}"
                + "function fmt(ms){ms=Number(ms)||0;if(!ms){return '';}var s=Math.max(1,Math.round(ms/1000));return s>=60?Math.floor(s/60)+'分'+String(s%60).padStart(2,'0')+'秒':s+' 秒';}"
                + "function directRow(role,text,reasoning,elapsedMs){"
                + "var row=document.createElement('section');"
                + "row.className='msg '+(role||'assistant');"
                + "var bubble=document.createElement('article');"
                + "bubble.className='bubble';"
                + "var label=fmt(elapsedMs);"
                + "if((role||'assistant')==='assistant'&&label&&!reasoning){var meta=document.createElement('div');meta.className='thinking-meta';meta.textContent='已思考（用时 '+label+'）';bubble.appendChild(meta);}"
                + "if(reasoning){var details=document.createElement('details');details.className='reasoning';"
                + "var summary=document.createElement('summary');summary.textContent=label?'已思考（用时 '+label+'）':'思考过程';"
                + "var body=document.createElement('div');body.className='reasoning-body';body.textContent=reasoning;"
                + "details.appendChild(summary);details.appendChild(body);bubble.appendChild(details);}"
                + "var content=document.createElement('div');content.className='content';content.textContent=text||'';"
                + "bubble.appendChild(content);row.appendChild(bubble);return row;}"
                + "function directLoader(chat,empty){"
                + "if(!remainingCount){return;}"
                + "if(empty){empty.style.display='none';}"
                + "var section=document.createElement('section');section.className='msg system history-load';"
                + "var button=document.createElement('button');button.type='button';"
                + "button.textContent='加载更早消息（还有 '+remainingCount+' 条）';"
                + "button.onclick=function(){if(window.AndroidBridge&&AndroidBridge.loadEarlierHistory){AndroidBridge.loadEarlierHistory();}};"
                + "section.appendChild(button);"
                + "var anchor=null;"
                + "for(var i=0;i<chat.children.length;i++){if(chat.children[i]!==empty){anchor=chat.children[i];break;}}"
                + "chat.insertBefore(section,anchor);}"
                + "function directRender(){"
                + "var chat=document.getElementById('chat');var empty=document.getElementById('empty');"
                + "if(!chat){return 'NO_CHAT_NODE';}"
                + "if(method==='renderMessages'){"
                + "chat.innerHTML='';"
                + "if(empty){chat.appendChild(empty);empty.style.display=(items.length||remainingCount)?'none':'flex';}"
                + "if(note){chat.appendChild(directRow('system',note,''));}"
                + "for(var i=0;i<items.length;i++){var m=items[i]||{};chat.appendChild(directRow(m.role||'assistant',m.text||'',m.reasoning||'',m.elapsedMs||0));}"
                + "directLoader(chat,empty);setTimeout(function(){window.scrollTo(0,document.body.scrollHeight);},0);"
                + "return 'DIRECT_RENDER';"
                + "}"
                + "if(empty){empty.style.display='none';}"
                + "var anchor=null;"
                + "for(var j=0;j<chat.children.length;j++){if(chat.children[j]!==empty){anchor=chat.children[j];break;}}"
                + "for(var k=0;k<items.length;k++){var n=items[k]||{};chat.insertBefore(directRow(n.role||'assistant',n.text||'',n.reasoning||'',n.elapsedMs||0),anchor);}"
                + "directLoader(chat,empty);return 'DIRECT_PREPEND';"
                + "}"
                + "try{"
                + "var v=window.ChatView;"
                + "if(v&&typeof v[method]==='function'){try{v[method](items,startIndex,remainingCount,generation);return 'OK_'+method;}catch(viewError){}}"
                + "if(v&&method==='renderMessages'&&typeof v.clearMessages==='function'&&typeof v.addMessage==='function'){"
                + "v.clearMessages(startIndex);"
                + (fallbackNote.isEmpty()
                ? ""
                : "v.addMessage('system',note,'',[]);")
                + "for(var a=0;a<items.length;a++){var x=items[a]||{};v.addMessage(x.role||'assistant',x.text||'',x.reasoning||'',x.sources||[],x.elapsedMs||0);}"
                + "return 'FALLBACK_RENDER';}"
                + "return directRender();"
                + "}catch(error){try{return directRender()+':'+error.message;}catch(secondError){return 'ERROR:'+error.message;}}"
                + "})();";
    }

    private JSONArray jsonArraySlice(JSONArray messages, int startIndex, int endIndex) {
        JSONArray items = new JSONArray();
        if (messages == null) {
            return items;
        }
        int safeStart = Math.max(0, startIndex);
        int safeEnd = Math.min(messages.length(), Math.max(safeStart, endIndex));
        for (int i = safeStart; i < safeEnd; i++) {
            JSONObject message = messages.optJSONObject(i);
            if (message == null) {
                continue;
            }
            items.put(message);
        }
        return items;
    }

    private JSONArray visibleMessagesJson(ChatStore.Session session) {
        JSONArray items = new JSONArray();
        if (session == null || session.messages == null) {
            return items;
        }
        ArrayList<Integer> path = activeMessageIndexes(session);
        for (Integer originalIndex : path) {
            if (originalIndex == null) {
                continue;
            }
            JSONObject message = session.messages.optJSONObject(originalIndex);
            if (message == null) {
                continue;
            }
            JSONObject item = new JSONObject();
            try {
                item.put("role", message.optString("role", "assistant"));
                item.put("text", message.optString("text", ""));
                item.put("reasoning", message.optString("reasoning", ""));
                item.put("elapsedMs", message.optLong("elapsedMs", 0L));
                item.put("index", originalIndex);
                JSONArray sources = message.optJSONArray("sources");
                item.put("sources", sources == null ? new JSONArray() : new JSONArray(sources.toString()));
                JSONObject branch = branchNavForMessage(message, originalIndex);
                if (branch != null) {
                    item.put("branch", branch);
                }
                items.put(item);
            } catch (Exception ignored) {
            }
        }
        return items;
    }

    private JSONObject branchNavForMessage(JSONObject message, int messageIndex) {
        if (message == null || !"user".equals(message.optString("role", ""))) {
            return null;
        }
        String nodeId = nodeIdOf(message);
        ArrayList<Integer> variants = assistantVariantIndexes(nodeId);
        if (variants.size() <= 1) {
            return null;
        }
        int active = indexOfActiveAssistantVariant(nodeId, variants);
        if (active < 0) {
            active = 0;
        }
        JSONObject branch = new JSONObject();
        try {
            branch.put("count", variants.size());
            branch.put("position", active + 1);
            branch.put("messageIndex", messageIndex);
            branch.put("canPrev", active > 0);
            branch.put("canNext", active < variants.size() - 1);
            int activeDescendants = 0;
            int otherDescendants = 0;
            for (int i = 0; i < variants.size(); i++) {
                JSONObject variant = currentSession.messages.optJSONObject(variants.get(i));
                int descendants = descendantCount(nodeIdOf(variant));
                if (i == active) {
                    activeDescendants = descendants;
                } else {
                    otherDescendants += descendants;
                }
            }
            branch.put("activeDescendants", activeDescendants);
            branch.put("otherDescendants", otherDescendants);
        } catch (Exception ignored) {
        }
        return branch;
    }

    private int descendantCount(String nodeId) {
        if (currentSession == null || currentSession.messages == null || nodeId == null || nodeId.isEmpty()) {
            return 0;
        }
        HashMap<String, ArrayList<String>> children = new HashMap<>();
        for (int i = 0; i < currentSession.messages.length(); i++) {
            JSONObject message = currentSession.messages.optJSONObject(i);
            if (message == null) {
                continue;
            }
            String parentId = parentIdOf(message);
            String childId = nodeIdOf(message);
            if (parentId.isEmpty() || childId.isEmpty()) {
                continue;
            }
            ArrayList<String> bucket = children.get(parentId);
            if (bucket == null) {
                bucket = new ArrayList<>();
                children.put(parentId, bucket);
            }
            bucket.add(childId);
        }
        int count = 0;
        ArrayList<String> stack = new ArrayList<>();
        ArrayList<String> first = children.get(nodeId);
        if (first != null) {
            stack.addAll(first);
        }
        HashSet<String> seen = new HashSet<>();
        while (!stack.isEmpty()) {
            String current = stack.remove(stack.size() - 1);
            if (current == null || current.isEmpty() || seen.contains(current)) {
                continue;
            }
            seen.add(current);
            count++;
            ArrayList<String> nested = children.get(current);
            if (nested != null) {
                stack.addAll(nested);
            }
        }
        return count;
    }

    private String titleFromPrompt(String text) {
        String value = text == null ? "" : text.replace("\n", " ").trim();
        if (value.isEmpty()) {
            return "新聊天";
        }
        return value.length() > 22 ? value.substring(0, 22) + "..." : value;
    }

    private void syncHistoryState(boolean showPanel) {
        syncHistoryState(showPanel, false);
    }

    private void syncHistoryState(boolean showPanel, boolean animate) {
        historyVisible = showPanel;
        historyButton.setText(showPanel ? "×" : "☰");
        if (showPanel) {
            refreshHistoryList();
        }
        setExpandedState(historyPanel, showPanel, animate);
    }

    private void refreshHistoryList() {
        if (historyList == null) {
            return;
        }
        String query = historySearchInput == null ? "" : historySearchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        historyMetas = new ArrayList<>();
        for (ChatStore.SessionMeta meta : chatStore.listSessions()) {
            String label = meta.label();
            if (query.isEmpty() || label.toLowerCase(Locale.ROOT).contains(query)) {
                historyMetas.add(meta);
            }
        }
        historyList.removeAllViews();
        if (historyMetas.isEmpty()) {
            TextView empty = text(query.isEmpty() ? "暂无历史" : "没有匹配的历史", 14, R.color.app_muted, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(10), dp(34), dp(10), dp(34));
            historyList.addView(empty, matchWrap());
            return;
        }
        String lastGroup = "";
        for (ChatStore.SessionMeta meta : historyMetas) {
            String group = historyGroupLabel(meta.updatedAt);
            if (!group.equals(lastGroup)) {
                addHistorySection(group);
                lastGroup = group;
            }
            addHistoryRow(meta);
        }
    }

    private void loadSelectedSession() {
        if (historyMetas.isEmpty()) {
            toast("没有可打开的历史");
            return;
        }
        openHistorySession(historyMetas.get(0).id);
    }

    private void openHistorySession(String id) {
        ChatStore.Session session = chatStore.load(id);
        if (session == null) {
            toast("历史记录读取失败");
            return;
        }
        currentSession = session;
        chatStore.setCurrentSessionId(session.id);
        restoreSessionState(session);
        historyVisible = false;
        syncHistoryState(false);
        settingsVisible = false;
        if (settingsScrollView != null) {
            settingsScrollView.setVisibility(View.GONE);
        }
        if (browserPanel != null) {
            browserPanel.setVisibility(View.GONE);
        }
        if (chatWebView != null) {
            chatWebView.setVisibility(View.VISIBLE);
        }
        renderSessionMessages(session);
        setStatus("已打开历史: " + session.title);
    }

    private void deleteSelectedSession() {
        if (historyMetas.isEmpty()) {
            toast("没有可删除的历史");
            return;
        }
        confirmDeleteHistory(historyMetas.get(0));
    }

    private void addHistorySection(String label) {
        TextView section = text(label, 13, R.color.app_muted, Typeface.BOLD);
        section.setPadding(dp(10), dp(16), dp(10), dp(6));
        historyList.addView(section, matchWrap());
    }

    private void addHistoryRow(ChatStore.SessionMeta meta) {
        boolean selected = currentSession != null && meta.id.equals(currentSession.id);
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setMinimumHeight(dp(58));
        item.setPadding(dp(14), dp(10), dp(14), dp(10));
        item.setClickable(true);
        item.setBackground(roundedStroke(
                selected ? 0xFFEAF2FF : color(R.color.app_panel),
                selected ? 0xFFEAF2FF : color(R.color.app_panel),
                dp(17)
        ));

        TextView title = text(compactHistoryTitle(meta.title), 16, R.color.app_text, selected ? Typeface.BOLD : Typeface.NORMAL);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        if (selected) {
            title.setTextColor(0xFF2563EB);
        }
        TextView metaView = text(historyMetaText(meta), 11, R.color.app_muted, Typeface.NORMAL);
        metaView.setSingleLine(true);
        metaView.setEllipsize(TextUtils.TruncateAt.END);
        metaView.setPadding(0, dp(2), 0, 0);

        item.addView(title, matchWrap());
        item.addView(metaView, matchWrap());
        item.setOnClickListener(v -> openHistorySession(meta.id));
        item.setOnLongClickListener(v -> {
            confirmDeleteHistory(meta);
            return true;
        });

        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(2);
        params.bottomMargin = dp(2);
        historyList.addView(item, params);
    }

    private void confirmDeleteHistory(ChatStore.SessionMeta meta) {
        if (meta == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("删除这条历史？")
                .setMessage(compactHistoryTitle(meta.title))
                .setPositiveButton("删除", (dialog, which) -> {
                    chatStore.delete(meta.id);
                    if (currentSession != null && meta.id.equals(currentSession.id)) {
                        startNewSession();
                    } else {
                        refreshHistoryList();
                    }
                    setStatus("已删除历史");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String compactHistoryTitle(String title) {
        String value = title == null || title.trim().isEmpty() ? "新聊天" : title.trim().replace("\n", " ");
        return value.length() > 34 ? value.substring(0, 34) + "..." : value;
    }

    private String historyMetaText(ChatStore.SessionMeta meta) {
        return meta.count + " 条 · " + historyGroupLabel(meta.updatedAt);
    }

    private String historyGroupLabel(long updatedAt) {
        long days = Math.max(0L, (startOfDay(System.currentTimeMillis()) - startOfDay(updatedAt)) / (24L * 60L * 60L * 1000L));
        if (days == 0L) {
            return "今天";
        }
        if (days == 1L) {
            return "昨天";
        }
        if (days < 7L) {
            return "7 天内";
        }
        if (days < 30L) {
            return "30 天内";
        }
        return "更早";
    }

    private long startOfDay(long timeMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeMillis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
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

    private String currentReasoningEffort() {
        Object selected = reasoningEffortSpinner == null ? null : reasoningEffortSpinner.getSelectedItem();
        String label = selected == null ? "" : selected.toString();
        if (label.contains("超高")) {
            return ApiKeyStore.REASONING_XHIGH;
        }
        if (label.contains("高")) {
            return ApiKeyStore.REASONING_HIGH;
        }
        if (label.contains("中")) {
            return ApiKeyStore.REASONING_MEDIUM;
        }
        if (selected == null) {
            return apiKeyStore.loadReasoningEffort();
        }
        return ApiKeyStore.REASONING_LOW;
    }

    private int reasoningEffortPosition(String effort) {
        if (ApiKeyStore.REASONING_MEDIUM.equals(effort)) {
            return 1;
        }
        if (ApiKeyStore.REASONING_HIGH.equals(effort)) {
            return 2;
        }
        if (ApiKeyStore.REASONING_XHIGH.equals(effort)) {
            return 3;
        }
        return 0;
    }

    private boolean currentAgentToolsEnabled() {
        Object selected = agentToolsSpinner == null ? null : agentToolsSpinner.getSelectedItem();
        if (selected == null) {
            return apiKeyStore.loadAgentToolsEnabled();
        }
        return !selected.toString().contains("关");
    }

    private boolean currentAgentImageToolEnabled() {
        Object selected = agentImageToolSpinner == null ? null : agentImageToolSpinner.getSelectedItem();
        if (selected == null) {
            return apiKeyStore.loadAgentImageToolEnabled();
        }
        return selected.toString().contains("开");
    }

    private boolean currentStartNewOnLaunch() {
        Object selected = launchModeSpinner == null ? null : launchModeSpinner.getSelectedItem();
        if (selected == null) {
            return apiKeyStore.loadStartNewOnLaunch();
        }
        return selected.toString().contains("新聊天");
    }

    private String currentCustomInstructions() {
        if (customInstructionsInput != null) {
            return customInstructionsInput.getText().toString().trim();
        }
        return apiKeyStore.loadCustomInstructions();
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

    private String currentSearchProvider() {
        Object selected = searchProviderSpinner == null ? null : searchProviderSpinner.getSelectedItem();
        String label = selected == null ? "" : selected.toString();
        if (label.contains("Tavily")) {
            return ApiKeyStore.SEARCH_PROVIDER_TAVILY;
        }
        if (label.contains("Brave")) {
            return ApiKeyStore.SEARCH_PROVIDER_BRAVE;
        }
        if (label.contains("自定义")) {
            return ApiKeyStore.SEARCH_PROVIDER_CUSTOM;
        }
        if (label.contains("本地")) {
            return ApiKeyStore.SEARCH_PROVIDER_LOCAL;
        }
        if (label.contains("关闭")) {
            return ApiKeyStore.SEARCH_PROVIDER_OFF;
        }
        return ApiKeyStore.SEARCH_PROVIDER_BOCHA;
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
            return Math.max(1, Math.min(20, Integer.parseInt(value)));
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

    private int searchProviderPosition(String provider) {
        if (ApiKeyStore.SEARCH_PROVIDER_TAVILY.equals(provider)) {
            return 1;
        }
        if (ApiKeyStore.SEARCH_PROVIDER_BRAVE.equals(provider)) {
            return 2;
        }
        if (ApiKeyStore.SEARCH_PROVIDER_CUSTOM.equals(provider)) {
            return 3;
        }
        if (ApiKeyStore.SEARCH_PROVIDER_LOCAL.equals(provider)) {
            return 4;
        }
        if (ApiKeyStore.SEARCH_PROVIDER_OFF.equals(provider)) {
            return 5;
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
        appendMessage(role, text, reasoning, null, 0L);
    }

    private void appendMessage(String role, String text, String reasoning, JSONArray sources) {
        appendMessage(role, text, reasoning, sources, 0L);
    }

    private void appendMessage(String role, String text, String reasoning, JSONArray sources, long elapsedMs) {
        int messageIndex = persistMessage(role, text, reasoning, sources, elapsedMs);
        appendMessageToWeb(role, text, reasoning, sources, elapsedMs, messageIndex);
    }

    private void appendMessageToWeb(String role, String text, String reasoning) {
        appendMessageToWeb(role, text, reasoning, null, 0L);
    }

    private void appendMessageToWeb(String role, String text, String reasoning, JSONArray sources) {
        appendMessageToWeb(role, text, reasoning, sources, 0L);
    }

    private void appendMessageToWeb(String role, String text, String reasoning, JSONArray sources, long elapsedMs) {
        appendMessageToWeb(role, text, reasoning, sources, elapsedMs, -1);
    }

    private void appendMessageToWeb(String role, String text, String reasoning, JSONArray sources, long elapsedMs, int messageIndex) {
        String sourceJson = sources == null ? "[]" : sources.toString();
        runChatJs("window.ChatView.addMessage(" + js(role) + "," + js(text) + "," + js(reasoning) + "," + sourceJson + "," + Math.max(0L, elapsedMs) + "," + messageIndex + ");");
    }

    private void startAssistantStream(String status) {
        startAssistantStream(status, -1);
    }

    private void startAssistantStream(String status, int afterMessageIndex) {
        setThinking(false);
        runChatJs("window.ChatView.startAssistantStream(" + js(status) + "," + afterMessageIndex + ");");
    }

    private void updateAssistantStream(String text, String reasoning, JSONArray sources, long elapsedMs, String status) {
        String sourceJson = sources == null ? "[]" : sources.toString();
        runChatJs("window.ChatView.updateAssistantStream("
                + js(text)
                + ","
                + js(reasoning)
                + ","
                + sourceJson
                + ","
                + Math.max(0L, elapsedMs)
                + ","
                + js(status)
                + ");");
    }

    private void finishAssistantStream(String text, String reasoning, JSONArray sources, long elapsedMs) {
        finishAssistantStream(text, reasoning, sources, elapsedMs, -1);
    }

    private void finishAssistantStream(String text, String reasoning, JSONArray sources, long elapsedMs, int messageIndex) {
        String sourceJson = sources == null ? "[]" : sources.toString();
        runChatJs("window.ChatView.finishAssistantStream("
                + js(text)
                + ","
                + js(reasoning)
                + ","
                + sourceJson
                + ","
                + Math.max(0L, elapsedMs)
                + ","
                + messageIndex
                + ");");
    }

    private void cancelAssistantStream() {
        runChatJs("window.ChatView.cancelAssistantStream();");
    }

    private int persistMessage(String role, String text, String reasoning) {
        return persistMessage(role, text, reasoning, null, 0L);
    }

    private int persistMessage(String role, String text, String reasoning, JSONArray sources) {
        return persistMessage(role, text, reasoning, sources, 0L);
    }

    private int persistMessage(String role, String text, String reasoning, JSONArray sources, long elapsedMs) {
        if (currentSession == null) {
            currentSession = chatStore.createSession();
        }
        try {
            ensureBranchMetadata(currentSession);
            String cleanText = persistInlineDataImages(text == null ? "" : text);
            String cleanReasoning = persistInlineDataImages(reasoning == null ? "" : reasoning);
            JSONObject json = new JSONObject();
            json.put("role", role);
            json.put("text", cleanText);
            json.put("reasoning", cleanReasoning);
            String nodeId = newNodeId();
            String parentId = parentIdForNewMessage(role);
            JSONObject metadata = new JSONObject();
            metadata.put(META_NODE_ID, nodeId);
            metadata.put(META_PARENT_ID, parentId);
            if (sources != null && sources.length() > 0) {
                json.put("sources", new JSONArray(sources.toString()));
            }
            if (elapsedMs > 0L) {
                json.put("elapsedMs", elapsedMs);
            }
            if ("assistant".equals(role)) {
                JSONArray generatedFiles = drainPendingGeneratedOfficeFiles();
                if (generatedFiles.length() > 0) {
                    metadata.put("generated_office_files", generatedFiles);
                }
            }
            json.put("metadata", metadata);
            json.put("time", System.currentTimeMillis());
            int messageIndex = currentSession.messages.length();
            currentSession.messages.put(json);
            if (!parentId.isEmpty()) {
                setActiveChild(parentId, nodeId);
            }
            if ("user".equals(role)) {
                pendingAssistantParentNodeId = nodeId;
                pendingBranchParentMessageIndex = -1;
            } else if ("assistant".equals(role)) {
                clearPendingBranchReply();
            }
            if ("user".equals(role) && ("新聊天".equals(currentSession.title) || currentSession.title.trim().isEmpty())) {
                currentSession.title = titleFromPrompt(cleanText);
            }
            conversationTranscript = rebuildTranscriptFromMessages(currentSession.messages);
            saveCurrentSession();
            refreshHistoryList();
            return messageIndex;
        } catch (Exception ignored) {
            return -1;
        }
    }

    private String parentIdForNewMessage(String role) {
        if ("assistant".equals(role) && !pendingAssistantParentNodeId.isEmpty()) {
            return pendingAssistantParentNodeId;
        }
        return currentActiveLeafNodeId();
    }

    private String currentActiveLeafNodeId() {
        if (currentSession == null || currentSession.messages == null || currentSession.messages.length() == 0) {
            return "";
        }
        ArrayList<Integer> path = activeMessageIndexes(currentSession);
        if (path.isEmpty()) {
            return "";
        }
        JSONObject message = currentSession.messages.optJSONObject(path.get(path.size() - 1));
        return nodeIdOf(message);
    }

    private void clearPendingBranchReply() {
        pendingAssistantParentNodeId = "";
        pendingBranchParentMessageIndex = -1;
    }

    private String newNodeId() {
        return "n_" + UUID.randomUUID().toString().replace("-", "");
    }

    private JSONObject metadataOf(JSONObject message) {
        if (message == null) {
            return new JSONObject();
        }
        JSONObject metadata = message.optJSONObject("metadata");
        if (metadata == null) {
            metadata = new JSONObject();
            try {
                message.put("metadata", metadata);
            } catch (Exception ignored) {
            }
        }
        return metadata;
    }

    private String nodeIdOf(JSONObject message) {
        return metadataOf(message).optString(META_NODE_ID, "");
    }

    private String parentIdOf(JSONObject message) {
        return metadataOf(message).optString(META_PARENT_ID, "");
    }

    private boolean ensureBranchMetadata(ChatStore.Session session) {
        if (session == null || session.messages == null) {
            return false;
        }
        boolean changed = false;
        for (int i = 0; i < session.messages.length(); i++) {
            JSONObject message = session.messages.optJSONObject(i);
            if (message == null) {
                continue;
            }
            JSONObject metadata = metadataOf(message);
            if (metadata.optString(META_NODE_ID, "").isEmpty()) {
                try {
                    metadata.put(META_NODE_ID, newNodeId());
                    changed = true;
                } catch (Exception ignored) {
                }
            }
        }
        String previousNodeId = "";
        for (int i = 0; i < session.messages.length(); i++) {
            JSONObject message = session.messages.optJSONObject(i);
            if (message == null) {
                continue;
            }
            JSONObject metadata = metadataOf(message);
            if (!metadata.has(META_PARENT_ID)) {
                try {
                    metadata.put(META_PARENT_ID, previousNodeId);
                    changed = true;
                } catch (Exception ignored) {
                }
            }
            previousNodeId = metadata.optString(META_NODE_ID, previousNodeId);
        }

        Map<String, String> firstChildByParent = new HashMap<>();
        for (int i = 0; i < session.messages.length(); i++) {
            JSONObject child = session.messages.optJSONObject(i);
            if (child == null) {
                continue;
            }
            String parentId = parentIdOf(child);
            String childId = nodeIdOf(child);
            if (!parentId.isEmpty() && !childId.isEmpty() && !firstChildByParent.containsKey(parentId)) {
                firstChildByParent.put(parentId, childId);
            }
        }
        for (int i = 0; i < session.messages.length(); i++) {
            JSONObject message = session.messages.optJSONObject(i);
            if (message == null) {
                continue;
            }
            JSONObject metadata = metadataOf(message);
            String nodeId = metadata.optString(META_NODE_ID, "");
            if (!nodeId.isEmpty()
                    && !metadata.has(META_ACTIVE_CHILD_ID)
                    && firstChildByParent.containsKey(nodeId)) {
                try {
                    metadata.put(META_ACTIVE_CHILD_ID, firstChildByParent.get(nodeId));
                    changed = true;
                } catch (Exception ignored) {
                }
            }
        }
        return changed;
    }

    private void setActiveChild(String parentNodeId, String childNodeId) {
        if (currentSession == null || currentSession.messages == null || parentNodeId == null || parentNodeId.isEmpty()) {
            return;
        }
        for (int i = 0; i < currentSession.messages.length(); i++) {
            JSONObject message = currentSession.messages.optJSONObject(i);
            if (message == null || !parentNodeId.equals(nodeIdOf(message))) {
                continue;
            }
            try {
                metadataOf(message).put(META_ACTIVE_CHILD_ID, childNodeId == null ? "" : childNodeId);
            } catch (Exception ignored) {
            }
            return;
        }
    }

    private ArrayList<Integer> activeMessageIndexes(ChatStore.Session session) {
        ArrayList<Integer> path = new ArrayList<>();
        if (session == null || session.messages == null || session.messages.length() == 0) {
            return path;
        }
        HashMap<String, ArrayList<Integer>> children = new HashMap<>();
        ArrayList<Integer> roots = new ArrayList<>();
        for (int i = 0; i < session.messages.length(); i++) {
            JSONObject message = session.messages.optJSONObject(i);
            if (message == null) {
                continue;
            }
            String parentId = parentIdOf(message);
            if (parentId.isEmpty()) {
                roots.add(i);
            } else {
                ArrayList<Integer> bucket = children.get(parentId);
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    children.put(parentId, bucket);
                }
                bucket.add(i);
            }
        }
        if (roots.isEmpty()) {
            roots.add(0);
        }
        int current = roots.get(0);
        Set<String> seen = new HashSet<>();
        while (current >= 0 && current < session.messages.length()) {
            JSONObject message = session.messages.optJSONObject(current);
            if (message == null) {
                break;
            }
            String nodeId = nodeIdOf(message);
            if (nodeId.isEmpty() || seen.contains(nodeId)) {
                break;
            }
            seen.add(nodeId);
            path.add(current);
            ArrayList<Integer> childIndexes = children.get(nodeId);
            if (childIndexes == null || childIndexes.isEmpty()) {
                break;
            }
            String activeChildId = metadataOf(message).optString(META_ACTIVE_CHILD_ID, "");
            int next = -1;
            if (!activeChildId.isEmpty()) {
                for (Integer childIndex : childIndexes) {
                    JSONObject child = session.messages.optJSONObject(childIndex);
                    if (child != null && activeChildId.equals(nodeIdOf(child))) {
                        next = childIndex;
                        break;
                    }
                }
            }
            if (next < 0) {
                next = childIndexes.get(childIndexes.size() - 1);
            }
            current = next;
        }
        return path;
    }

    private ArrayList<Integer> assistantVariantIndexes(String userNodeId) {
        ArrayList<Integer> variants = new ArrayList<>();
        if (currentSession == null || currentSession.messages == null || userNodeId == null || userNodeId.isEmpty()) {
            return variants;
        }
        for (int i = 0; i < currentSession.messages.length(); i++) {
            JSONObject message = currentSession.messages.optJSONObject(i);
            if (message == null) {
                continue;
            }
            if ("assistant".equals(message.optString("role", "")) && userNodeId.equals(parentIdOf(message))) {
                variants.add(i);
            }
        }
        return variants;
    }

    private int indexOfActiveAssistantVariant(String userNodeId, ArrayList<Integer> variants) {
        if (currentSession == null || currentSession.messages == null || variants == null || variants.isEmpty()) {
            return -1;
        }
        JSONObject parent = messageByNodeId(userNodeId);
        String activeChildId = parent == null ? "" : metadataOf(parent).optString(META_ACTIVE_CHILD_ID, "");
        for (int i = 0; i < variants.size(); i++) {
            JSONObject child = currentSession.messages.optJSONObject(variants.get(i));
            if (child != null && activeChildId.equals(nodeIdOf(child))) {
                return i;
            }
        }
        return variants.size() - 1;
    }

    private JSONObject messageByNodeId(String nodeId) {
        if (currentSession == null || currentSession.messages == null || nodeId == null || nodeId.isEmpty()) {
            return null;
        }
        for (int i = 0; i < currentSession.messages.length(); i++) {
            JSONObject message = currentSession.messages.optJSONObject(i);
            if (message != null && nodeId.equals(nodeIdOf(message))) {
                return message;
            }
        }
        return null;
    }

    private JSONArray drainPendingGeneratedOfficeFiles() {
        JSONArray files = new JSONArray();
        synchronized (generatedOfficeLock) {
            for (GeneratedOfficeFile ref : pendingGeneratedOfficeFiles) {
                if (ref != null) {
                    files.put(ref.toJson());
                }
            }
            pendingGeneratedOfficeFiles.clear();
        }
        return files;
    }

    private void clearPendingGeneratedOfficeFiles() {
        synchronized (generatedOfficeLock) {
            pendingGeneratedOfficeFiles.clear();
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
        attachmentsView.removeAllViews();
        if (attachments.isEmpty()) {
            setExpandedState(attachmentsView, false, true);
            return;
        }
        for (int i = 0; i < attachments.size(); i++) {
            attachmentsView.addView(attachmentChip(attachments.get(i), i), attachmentChipParams(i));
        }
        setExpandedState(attachmentsView, true, true);
    }

    private View attachmentChip(AttachmentItem item, int index) {
        LinearLayout chip = row();
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(14), dp(6), dp(6), dp(6));
        chip.setBackground(roundedStroke(color(R.color.app_accent_soft), color(R.color.app_border), dp(999)));

        TextView label = text(item.displayLine(), 12, R.color.app_muted, Typeface.NORMAL);
        label.setSingleLine(false);
        label.setMaxLines(2);
        chip.addView(label, weightWrap(1));

        Button remove = smallAttachmentRemoveButton();
        remove.setContentDescription("删除附件");
        remove.setOnClickListener(v -> removeAttachmentAt(index));
        chip.addView(remove, fixedWrapNoMargin(dp(28)));
        return chip;
    }

    private LinearLayout.LayoutParams attachmentChipParams(int index) {
        LinearLayout.LayoutParams params = matchWrap();
        params.leftMargin = dp(2);
        params.rightMargin = dp(2);
        params.topMargin = index == 0 ? 0 : dp(5);
        return params;
    }

    private Button smallAttachmentRemoveButton() {
        Button button = baseButton("×");
        button.setTextSize(16);
        button.setTextColor(color(R.color.app_muted));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(28));
        button.setPadding(0, 0, 0, dp(2));
        button.setBackground(roundedStroke(color(R.color.app_panel), color(R.color.app_border), dp(999)));
        return button;
    }

    private void removeAttachmentAt(int index) {
        if (index < 0 || index >= attachments.size()) {
            return;
        }
        attachments.remove(index);
        refreshAttachmentView();
        setStatus(attachments.isEmpty() ? "附件已清空" : "已删除附件");
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

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
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

    private OpenAiClient.ImageResult generateImageWithFallback(
            String baseUrl,
            String apiKey,
            String preferredRoute,
            String chatModel,
            String imageModel,
            String prompt,
            String size,
            OpenAiClient.CancelToken token
    ) throws Exception {
        Exception firstError = null;
        if (ApiKeyStore.IMAGE_ROUTE_RESPONSES_TOOL.equals(preferredRoute)) {
            try {
                return OpenAiClient.generateImageViaResponsesTool(baseUrl, apiKey, chatModel, prompt, size, token);
            } catch (Exception e) {
                firstError = e;
                if (token != null && token.isCanceled()) {
                    throw e;
                }
            }
            if (imageModel != null && !imageModel.trim().isEmpty()) {
                try {
                    return OpenAiClient.generateImage(baseUrl, apiKey, imageModel, prompt, size, token);
                } catch (Exception fallbackError) {
                    if (firstError != null) {
                        throw new IOException(firstError.getMessage() + "\n\n已自动改试 Images 接口，也失败了: " + fallbackError.getMessage(), fallbackError);
                    }
                    throw fallbackError;
                }
            }
        } else {
            try {
                return OpenAiClient.generateImage(baseUrl, apiKey, imageModel, prompt, size, token);
            } catch (Exception e) {
                firstError = e;
                if (token != null && token.isCanceled()) {
                    throw e;
                }
            }
            if (chatModel != null && !chatModel.trim().isEmpty()) {
                try {
                    return OpenAiClient.generateImageViaResponsesTool(baseUrl, apiKey, chatModel, prompt, size, token);
                } catch (Exception fallbackError) {
                    if (firstError != null) {
                        throw new IOException(firstError.getMessage() + "\n\n已自动改试 Responses 工具生图，也失败了: " + fallbackError.getMessage(), fallbackError);
                    }
                    throw fallbackError;
                }
            }
        }
        if (firstError != null) {
            throw firstError;
        }
        throw new IOException("没有可用的生图模型");
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
        if (isCompactLayout()) {
            toolsCollapsed = true;
            syncToolPanelState();
        }
        settingsVisible = false;
        if (settingsScrollView != null) {
            settingsScrollView.setVisibility(View.GONE);
        }
        if (settingsButton != null) {
            settingsButton.setText("⚙");
        }
        browserPanel.setVisibility(View.VISIBLE);
        chatWebView.setVisibility(View.GONE);
        browserTitleView.setText(url);
        applyBrowserViewportMode();
        browserWebView.loadUrl(url);
        updateBrowserNavButtons();
        setStatus("正在打开网页...");
    }

    private void closeBrowser() {
        browserPanel.setVisibility(View.GONE);
        chatWebView.setVisibility(settingsVisible ? View.GONE : View.VISIBLE);
        if (settingsVisible && settingsScrollView != null) {
            settingsScrollView.setVisibility(View.VISIBLE);
        }
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

    private void toggleBrowserFitMode() {
        browserFitMode = !browserFitMode;
        applyBrowserViewportMode();
        if (browserLayoutButton != null) {
            browserLayoutButton.setText(browserFitMode ? "适配" : "原始");
        }
        if (browserWebView != null) {
            applyBrowserPageFit();
            browserWebView.reload();
        }
        setStatus(browserFitMode ? "网页已切到竖屏适配" : "网页已切到原始布局");
    }

    private void applyBrowserViewportMode() {
        if (browserWebView == null) {
            return;
        }
        WebSettings settings = browserWebView.getSettings();
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(browserFitMode);
        browserWebView.setInitialScale(browserFitMode ? 0 : 100);
    }

    private void applyBrowserPageFit() {
        if (browserWebView == null || !browserFitMode) {
            return;
        }
        String script = "(function(){"
                + "var m=document.querySelector('meta[name=viewport]');"
                + "if(!m){m=document.createElement('meta');m.name='viewport';document.head.appendChild(m);}"
                + "m.setAttribute('content','width=device-width,initial-scale=1,maximum-scale=5,user-scalable=yes');"
                + "document.documentElement.style.maxWidth='100%';"
                + "document.body.style.maxWidth='100%';"
                + "document.body.style.overflowX='auto';"
                + "})()";
        browserWebView.evaluateJavascript(script, null);
    }

    private void toggleToolPanel() {
        toolsCollapsed = !toolsCollapsed;
        syncToolPanelState(true);
    }

    private void syncToolPanelState() {
        syncToolPanelState(false);
    }

    private void syncToolPanelState(boolean animate) {
        if (toolPanel != null) {
            setExpandedState(toolPanel, !toolsCollapsed, animate);
        }
        if (toolsToggleButton != null) {
            toolsToggleButton.setText(toolsCollapsed ? "+" : "−");
        }
    }

    private void setBusy(boolean busy) {
        sendButton.setVisibility(busy ? View.GONE : View.VISIBLE);
        stopButton.setVisibility(busy ? View.VISIBLE : View.GONE);
        stopButton.setEnabled(busy);
        if (toolsToggleButton != null) {
            toolsToggleButton.setEnabled(!busy);
        }
        if (imageButton != null) {
            imageButton.setEnabled(!busy);
        }
        if (fileButton != null) {
            fileButton.setEnabled(!busy);
        }
        if (imageGenButton != null) {
            imageGenButton.setEnabled(!busy);
        }
        if (imageLibraryButton != null) {
            imageLibraryButton.setEnabled(!busy);
        }
        if (settingsButton != null) {
            settingsButton.setEnabled(!busy);
        }
        if (browserButton != null) {
            browserButton.setEnabled(!busy);
        }
        if (updateButton != null) {
            updateButton.setEnabled(!busy);
        }
    }

    private void setStatus(String textValue) {
        if (statusView == null) {
            return;
        }
        String value = textValue == null ? "" : textValue.trim();
        statusView.setText(value);
        statusView.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void speakText(String textValue) {
        String value = textValue == null ? "" : textValue.trim();
        if (value.isEmpty()) {
            toast("没有可朗读的内容");
            return;
        }
        if (textToSpeech == null || !ttsReady) {
            toast("朗读引擎还没准备好");
            return;
        }
        textToSpeech.stop();
        textToSpeech.speak(value, TextToSpeech.QUEUE_FLUSH, null, "message-" + System.currentTimeMillis());
    }

    private void stopSpeaking() {
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
    }

    private void shareText(String textValue) {
        String value = textValue == null ? "" : textValue.trim();
        if (value.isEmpty()) {
            toast("没有可分享的内容");
            return;
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, value);
        startActivity(Intent.createChooser(share, "分享"));
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

    private void updateMainPanelVisibility(boolean showSettings, boolean animate) {
        boolean browserVisible = browserPanel != null && browserPanel.getVisibility() == View.VISIBLE;
        if (!animate) {
            setPageVisibility(settingsScrollView, showSettings, false);
            setPageVisibility(chatWebView, !showSettings && !browserVisible, false);
            return;
        }
        if (showSettings) {
            setPageVisibility(chatWebView, false, false);
            setPageVisibility(settingsScrollView, true, true);
            return;
        }
        setPageVisibility(settingsScrollView, false, true, () -> {
            boolean browserStillVisible = browserPanel != null && browserPanel.getVisibility() == View.VISIBLE;
            if (!browserStillVisible) {
                setPageVisibility(chatWebView, true, true);
            }
        });
    }

    private void setPageVisibility(View view, boolean visible, boolean animate) {
        setPageVisibility(view, visible, animate, null);
    }

    private void setPageVisibility(View view, boolean visible, boolean animate, Runnable endAction) {
        if (view == null) {
            if (endAction != null) {
                endAction.run();
            }
            return;
        }
        view.animate().cancel();
        if (!animate) {
            view.setAlpha(1f);
            view.setTranslationY(0f);
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (endAction != null) {
                endAction.run();
            }
            return;
        }
        if (visible) {
            view.setVisibility(View.VISIBLE);
            view.setAlpha(0f);
            view.setTranslationY(dp(8));
            view.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(PAGE_ANIMATION_MS)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(endAction)
                    .start();
            return;
        }
        if (view.getVisibility() != View.VISIBLE) {
            if (endAction != null) {
                endAction.run();
            }
            return;
        }
        view.animate()
                .alpha(0f)
                .translationY(dp(8))
                .setDuration(PAGE_ANIMATION_MS)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    view.setVisibility(View.GONE);
                    view.setAlpha(1f);
                    view.setTranslationY(0f);
                    if (endAction != null) {
                        endAction.run();
                    }
                })
                .start();
    }

    private void setExpandedState(View view, boolean expanded, boolean animate) {
        if (view == null) {
            return;
        }
        cancelExpandAnimation(view);
        if (!animate) {
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (params != null) {
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                view.setLayoutParams(params);
            }
            view.setAlpha(1f);
            view.setTranslationY(0f);
            view.setVisibility(expanded ? View.VISIBLE : View.GONE);
            return;
        }
        if (expanded) {
            expandView(view);
        } else {
            collapseView(view);
        }
    }

    private void expandView(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (view.getVisibility() == View.VISIBLE
                && view.getHeight() > 0
                && params != null
                && params.height == ViewGroup.LayoutParams.WRAP_CONTENT) {
            view.setAlpha(1f);
            view.setTranslationY(0f);
            return;
        }
        if (params == null) {
            view.setVisibility(View.VISIBLE);
            return;
        }
        int targetHeight = measuredExpandedHeight(view);
        if (targetHeight <= 0) {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            view.setLayoutParams(params);
            view.setAlpha(1f);
            view.setTranslationY(0f);
            view.setVisibility(View.VISIBLE);
            return;
        }
        int startHeight = view.getVisibility() == View.VISIBLE ? Math.max(0, view.getHeight()) : 0;
        view.setVisibility(View.VISIBLE);
        view.setAlpha(startHeight > 0 ? 1f : 0f);
        view.setTranslationY(startHeight > 0 ? 0f : -dp(4));
        params.height = startHeight;
        view.setLayoutParams(params);
        animateHeight(view, params, startHeight, targetHeight, true);
    }

    private void collapseView(View view) {
        if (view.getVisibility() != View.VISIBLE) {
            return;
        }
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null) {
            view.setVisibility(View.GONE);
            return;
        }
        int startHeight = view.getHeight();
        if (startHeight <= 0) {
            view.setVisibility(View.GONE);
            return;
        }
        params.height = startHeight;
        view.setLayoutParams(params);
        animateHeight(view, params, startHeight, 0, false);
    }

    private void animateHeight(View view, ViewGroup.LayoutParams params, int from, int to, boolean expanding) {
        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(EXPAND_ANIMATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            params.height = (int) animation.getAnimatedValue();
            view.setLayoutParams(params);
            float visible = expanding ? fraction : 1f - fraction;
            view.setAlpha(visible);
            view.setTranslationY((expanding ? 1f - fraction : fraction) * -dp(4));
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (view.getTag() == animation) {
                    view.setTag(null);
                }
                if (cancelled) {
                    return;
                }
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                view.setLayoutParams(params);
                view.setAlpha(1f);
                view.setTranslationY(0f);
                if (!expanding) {
                    view.setVisibility(View.GONE);
                }
            }
        });
        view.setTag(animator);
        animator.start();
    }

    private int measuredExpandedHeight(View view) {
        int parentWidth = 0;
        if (view.getParent() instanceof View) {
            View parent = (View) view.getParent();
            parentWidth = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
        }
        if (parentWidth <= 0) {
            parentWidth = getResources().getDisplayMetrics().widthPixels - dp(32);
        }
        int widthSpec = View.MeasureSpec.makeMeasureSpec(Math.max(1, parentWidth), View.MeasureSpec.AT_MOST);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        view.measure(widthSpec, heightSpec);
        return view.getMeasuredHeight();
    }

    private void cancelExpandAnimation(View view) {
        Object tag = view.getTag();
        if (tag instanceof ValueAnimator) {
            ((ValueAnimator) tag).cancel();
            view.setTag(null);
        }
    }

    private void addPanelField(View view) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(8);
        settingsPanel.addView(view, params);
    }

    private LinearLayout settingsSection(String title, String subtitle, boolean expanded) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(14), dp(12), dp(14), dp(12));
        section.setBackground(roundedStroke(color(R.color.app_panel), color(R.color.app_border), dp(18)));

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(dp(46));
        header.setClickable(true);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView titleView = text(title, 15, R.color.app_text, Typeface.BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        TextView subtitleView = text(subtitle, 12, R.color.app_muted, Typeface.NORMAL);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        copy.addView(titleView, matchWrap());
        copy.addView(subtitleView, matchWrap());

        TextView chevron = text("", 18, R.color.app_text, Typeface.BOLD);
        chevron.setGravity(Gravity.CENTER);
        chevron.setMinHeight(dp(32));
        chevron.setBackground(roundedStroke(color(R.color.app_panel_alt), color(R.color.app_border), dp(999)));
        header.addView(copy, weightWrap(1));
        header.addView(chevron, fixedWrap(dp(34)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(10), dp(8), dp(10), dp(10));
        body.setBackground(roundedStroke(color(R.color.app_panel_alt), color(R.color.app_border), dp(14)));
        body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        updateSectionHeader(chevron, expanded, false);
        header.setOnClickListener(v -> {
            boolean open = body.getVisibility() != View.VISIBLE;
            setExpandedState(body, open, true);
            updateSectionHeader(chevron, open, true);
        });

        section.addView(header, matchWrap());
        LinearLayout.LayoutParams bodyParams = matchWrap();
        bodyParams.topMargin = dp(10);
        section.addView(body, bodyParams);
        addPanelField(section);
        return body;
    }

    private void updateSectionHeader(TextView chevron, boolean expanded, boolean animate) {
        if (!animate) {
            chevron.setText(expanded ? "-" : "+");
            chevron.setAlpha(1f);
            chevron.setScaleX(1f);
            chevron.setScaleY(1f);
            return;
        }
        chevron.animate().cancel();
        chevron.animate()
                .alpha(0f)
                .scaleX(0.82f)
                .scaleY(0.82f)
                .setDuration(95)
                .withEndAction(() -> {
                    chevron.setText(expanded ? "-" : "+");
                    chevron.animate()
                            .alpha(1f)
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(150)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                })
                .start();
    }

    private void addSettingsField(LinearLayout section, View view) {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(7);
        section.addView(view, params);
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

    private boolean isCompactLayout() {
        return getResources().getConfiguration().screenWidthDp < 420;
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

    private void styleSpinner(Spinner spinner) {
        spinner.setPadding(dp(8), 0, dp(8), 0);
        spinner.setBackground(roundedStroke(color(R.color.app_panel), color(R.color.app_border), dp(11)));
        spinner.setMinimumHeight(dp(42));
    }

    private Button primaryButton(String label) {
        Button button = baseButton(label);
        button.setTextColor(color(R.color.app_panel));
        button.setBackground(roundedStroke(color(R.color.app_accent), color(R.color.app_accent), dp(11)));
        return button;
    }

    private Button roundPrimaryButton(String label) {
        Button button = baseButton(label);
        button.setTextSize(19);
        button.setTextColor(color(R.color.app_panel));
        button.setBackground(roundedStroke(color(R.color.app_accent), color(R.color.app_accent), dp(999)));
        button.setMinHeight(dp(42));
        button.setPadding(0, 0, 0, dp(2));
        return button;
    }

    private Button roundQuietButton(String label) {
        Button button = baseButton(label);
        button.setTextSize(15);
        button.setTextColor(color(R.color.app_text));
        button.setBackground(roundedStroke(color(R.color.app_panel_alt), color(R.color.app_border), dp(999)));
        button.setMinHeight(dp(42));
        button.setPadding(0, 0, 0, 0);
        return button;
    }

    private Button quietButton(String label) {
        Button button = baseButton(label);
        button.setTextColor(color(R.color.app_text));
        button.setBackground(roundedStroke(color(R.color.app_panel), color(R.color.app_border), dp(11)));
        return button;
    }

    private Button chipButton(String label) {
        Button button = baseButton(label);
        button.setTextSize(12);
        button.setTextColor(color(R.color.app_text));
        button.setBackground(roundedStroke(color(R.color.app_accent_soft), color(R.color.app_border), dp(999)));
        button.setMinHeight(dp(36));
        button.setPadding(dp(6), 0, dp(6), 0);
        return button;
    }

    private Button cropOverlayButton(String label) {
        Button button = baseButton(label);
        button.setTextSize(30);
        button.setTextColor(0xFFFFFFFF);
        button.setBackground(roundedStroke(0x00000000, 0x00000000, dp(999)));
        button.setPadding(0, 0, 0, dp(2));
        return button;
    }

    private Button cropRoundButton(String label) {
        Button button = baseButton(label);
        button.setTextSize(34);
        button.setTextColor(0xFFFFFFFF);
        button.setBackground(roundedStroke(0xFF202020, 0xFF202020, dp(999)));
        button.setPadding(0, 0, 0, dp(4));
        return button;
    }

    private Button cropConfirmButton(String label) {
        Button button = baseButton(label);
        button.setTextSize(18);
        button.setTextColor(0xFFFFFFFF);
        button.setBackground(roundedStroke(0xFF4F7DF3, 0xFF4F7DF3, dp(999)));
        button.setPadding(0, 0, 0, dp(2));
        return button;
    }

    private Button smallIconButton(String label) {
        Button button = roundQuietButton(label);
        button.setTextSize(17);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(34));
        button.setPadding(0, 0, 0, dp(1));
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

    private LinearLayout.LayoutParams fixedWrap(int width) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                width,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = dp(6);
        return params;
    }

    private LinearLayout.LayoutParams fixedWrapNoMargin(int width) {
        return new LinearLayout.LayoutParams(
                width,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
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

    private LinearLayout.LayoutParams compactControlParams(float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                weight
        );
        params.leftMargin = dp(4);
        params.rightMargin = dp(4);
        return params;
    }

    private LinearLayout.LayoutParams topActionParams() {
        LinearLayout.LayoutParams params = isCompactLayout()
                ? new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1)
                : new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(6);
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
        public void reviseMessage(int index, String text) {
            runOnUiThread(() -> startRevisionFromMessageIndex(index, text));
        }

        @JavascriptInterface
        public void openUrl(String url) {
            runOnUiThread(() -> openUrlInApp(url));
        }

        @JavascriptInterface
        public void speakText(String text) {
            runOnUiThread(() -> MainActivity.this.speakText(text));
        }

        @JavascriptInterface
        public void stopSpeaking() {
            runOnUiThread(() -> MainActivity.this.stopSpeaking());
        }

        @JavascriptInterface
        public void shareText(String text) {
            runOnUiThread(() -> MainActivity.this.shareText(text));
        }

        @JavascriptInterface
        public void regenerateLastResponse() {
            runOnUiThread(() -> regenerateLast());
        }

        @JavascriptInterface
        public void regenerateFromMessage(int index, String text) {
            runOnUiThread(() -> regenerateFromMessageIndex(index, text));
        }

        @JavascriptInterface
        public void switchAssistantVariant(int userIndex, int direction) {
            runOnUiThread(() -> MainActivity.this.switchAssistantVariant(userIndex, direction));
        }

        @JavascriptInterface
        public void loadEarlierHistory() {
            runOnUiThread(() -> loadEarlierHistoryMessages());
        }
    }

    private static class OfficeSource {
        final File file;
        final String name;
        final boolean temporary;

        OfficeSource(File file, String name, boolean temporary) {
            this.file = file;
            this.name = name == null ? "" : name;
            this.temporary = temporary;
        }
    }

    private static class GeneratedOfficeFile {
        final String name;
        final String mimeType;
        final String extension;
        final String displayPath;
        final String privatePath;
        final long time;

        GeneratedOfficeFile(String name, String mimeType, String extension, String displayPath, String privatePath, long time) {
            this.name = name == null ? "" : name;
            this.mimeType = mimeType == null ? "" : mimeType;
            this.extension = extension == null ? "" : extension;
            this.displayPath = displayPath == null ? "" : displayPath;
            this.privatePath = privatePath == null ? "" : privatePath;
            this.time = time;
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("name", name);
                json.put("mimeType", mimeType);
                json.put("extension", extension);
                json.put("displayPath", displayPath);
                json.put("privatePath", privatePath);
                json.put("time", time);
            } catch (Exception ignored) {
            }
            return json;
        }

        static GeneratedOfficeFile fromJson(JSONObject json) {
            if (json == null) {
                return null;
            }
            return new GeneratedOfficeFile(
                    json.optString("name", ""),
                    json.optString("mimeType", ""),
                    json.optString("extension", ""),
                    json.optString("displayPath", ""),
                    json.optString("privatePath", ""),
                    json.optLong("time", 0L)
            );
        }
    }

    private class StreamingUiBuffer implements OpenAiClient.StreamCallback {
        private final long startedAt;
        private final StringBuilder text = new StringBuilder();
        private final StringBuilder reasoning = new StringBuilder();
        private final JSONArray sources = new JSONArray();
        private long lastFlushAt;
        private boolean flushQueued;
        private boolean closed;
        private String status = "";

        StreamingUiBuffer(long startedAt) {
            this.startedAt = startedAt;
        }

        @Override
        public synchronized void onTextDelta(String delta) {
            if (closed) {
                return;
            }
            if (delta == null || delta.isEmpty()) {
                return;
            }
            text.append(delta);
            queueFlush(false);
        }

        @Override
        public synchronized void onReasoningDelta(String delta) {
            if (closed) {
                return;
            }
            if (delta == null || delta.isEmpty()) {
                return;
            }
            reasoning.append(delta);
            queueFlush(false);
        }

        @Override
        public synchronized void onStatus(String value) {
            if (closed) {
                return;
            }
            status = value == null ? "" : value.trim();
            if (!status.isEmpty()) {
                runOnUiThread(() -> setStatus(status));
            }
            queueFlush(true);
        }

        @Override
        public synchronized void onSources(JSONArray values) {
            if (closed) {
                return;
            }
            appendUniqueSources(sources, values);
            queueFlush(true);
        }

        private synchronized void queueFlush(boolean soon) {
            if (closed) {
                return;
            }
            long now = System.currentTimeMillis();
            if (soon || now - lastFlushAt >= STREAM_UI_FLUSH_MS) {
                flushQueued = false;
                lastFlushAt = now;
                flushSnapshot();
                return;
            }
            if (flushQueued) {
                return;
            }
            flushQueued = true;
            long delay = Math.max(24L, STREAM_UI_FLUSH_MS - (now - lastFlushAt));
            statusView.postDelayed(() -> {
                synchronized (StreamingUiBuffer.this) {
                    if (closed) {
                        flushQueued = false;
                        return;
                    }
                    flushQueued = false;
                    lastFlushAt = System.currentTimeMillis();
                }
                flushSnapshot();
            }, delay);
        }

        synchronized void close() {
            closed = true;
            flushQueued = false;
        }

        private void flushSnapshot() {
            String textSnapshot;
            String reasoningSnapshot;
            String statusSnapshot;
            JSONArray sourcesSnapshot;
            synchronized (this) {
                if (closed) {
                    return;
                }
                textSnapshot = text.toString();
                reasoningSnapshot = reasoning.toString();
                statusSnapshot = status;
                try {
                    sourcesSnapshot = new JSONArray(sources.toString());
                } catch (Exception e) {
                    sourcesSnapshot = new JSONArray();
                }
            }
            long elapsed = Math.max(0L, System.currentTimeMillis() - startedAt);
            final JSONArray finalSourcesSnapshot = sourcesSnapshot;
            runOnUiThread(() -> updateAssistantStream(textSnapshot, reasoningSnapshot, finalSourcesSnapshot, elapsed, statusSnapshot));
        }
    }

    private class CropImageView extends View {
        private static final int HANDLE_NONE = 0;
        private static final int HANDLE_MOVE = 1;
        private static final int HANDLE_LEFT = 2;
        private static final int HANDLE_TOP = 3;
        private static final int HANDLE_RIGHT = 4;
        private static final int HANDLE_BOTTOM = 5;
        private static final int HANDLE_TOP_LEFT = 6;
        private static final int HANDLE_TOP_RIGHT = 7;
        private static final int HANDLE_BOTTOM_LEFT = 8;
        private static final int HANDLE_BOTTOM_RIGHT = 9;

        private Bitmap bitmap;
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF imageRect = new RectF();
        private final RectF cropRect = new RectF();
        private final RectF downRect = new RectF();
        private boolean cropInitialized;
        private int activeHandle = HANDLE_NONE;
        private float downX;
        private float downY;

        CropImageView(Context context, Bitmap bitmap) {
            super(context);
            this.bitmap = bitmap;
            setLayerType(View.LAYER_TYPE_HARDWARE, null);
            setBackgroundColor(0xFF000000);
            overlayPaint.setColor(0xA6000000);
            borderPaint.setColor(0xFFFFFFFF);
            borderPaint.setStyle(Paint.Style.STROKE);
            borderPaint.setStrokeWidth(dp(2));
            handlePaint.setColor(0xFFFFFFFF);
            handlePaint.setStyle(Paint.Style.FILL);
        }

        Bitmap currentBitmap() {
            return bitmap;
        }

        void recycle() {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
            bitmap = null;
        }

        void rotate90() {
            if (bitmap == null || bitmap.isRecycled()) {
                return;
            }
            Matrix matrix = new Matrix();
            matrix.postRotate(90f);
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            if (rotated != bitmap) {
                bitmap.recycle();
            }
            bitmap = rotated;
            cropInitialized = false;
            calculateImageRect(getWidth(), getHeight());
            postInvalidateOnAnimation();
        }

        Bitmap createCroppedBitmap() throws IOException {
            if (bitmap == null || bitmap.isRecycled()) {
                throw new IOException("图片已关闭");
            }
            if (imageRect.width() <= 0 || imageRect.height() <= 0 || cropRect.width() <= 0 || cropRect.height() <= 0) {
                throw new IOException("裁剪区域无效");
            }
            float scaleX = bitmap.getWidth() / imageRect.width();
            float scaleY = bitmap.getHeight() / imageRect.height();
            int left = clampInt(Math.round((cropRect.left - imageRect.left) * scaleX), 0, bitmap.getWidth() - 1);
            int top = clampInt(Math.round((cropRect.top - imageRect.top) * scaleY), 0, bitmap.getHeight() - 1);
            int right = clampInt(Math.round((cropRect.right - imageRect.left) * scaleX), left + 1, bitmap.getWidth());
            int bottom = clampInt(Math.round((cropRect.bottom - imageRect.top) * scaleY), top + 1, bitmap.getHeight());
            return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            calculateImageRect(w, h);
        }

        private void calculateImageRect(int width, int height) {
            if (bitmap == null || bitmap.isRecycled() || width <= 0 || height <= 0) {
                return;
            }
            float horizontalMargin = dp(28);
            float top = dp(116);
            float bottom = height - dp(130);
            if (bottom - top < dp(260)) {
                top = dp(72);
                bottom = height - dp(104);
            }
            RectF box = new RectF(horizontalMargin, top, width - horizontalMargin, Math.max(top + dp(180), bottom));
            float bitmapAspect = (float) bitmap.getWidth() / Math.max(1, bitmap.getHeight());
            float boxAspect = box.width() / Math.max(1f, box.height());
            if (bitmapAspect > boxAspect) {
                float targetHeight = box.width() / bitmapAspect;
                float centerY = box.centerY();
                imageRect.set(box.left, centerY - targetHeight / 2f, box.right, centerY + targetHeight / 2f);
            } else {
                float targetWidth = box.height() * bitmapAspect;
                float centerX = box.centerX();
                imageRect.set(centerX - targetWidth / 2f, box.top, centerX + targetWidth / 2f, box.bottom);
            }
            if (!cropInitialized) {
                float insetX = Math.max(dp(18), imageRect.width() * 0.12f);
                float insetY = Math.max(dp(18), imageRect.height() * 0.10f);
                cropRect.set(imageRect.left + insetX, imageRect.top + insetY, imageRect.right - insetX, imageRect.bottom - insetY);
                cropInitialized = true;
            } else {
                clampCropRect();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (bitmap == null || bitmap.isRecycled()) {
                return;
            }
            if (imageRect.isEmpty()) {
                calculateImageRect(getWidth(), getHeight());
            }
            canvas.drawBitmap(bitmap, null, imageRect, bitmapPaint);
            drawOverlay(canvas);
            canvas.drawRect(cropRect, borderPaint);
            drawHandles(canvas);
        }

        private void drawOverlay(Canvas canvas) {
            canvas.drawRect(imageRect.left, imageRect.top, imageRect.right, cropRect.top, overlayPaint);
            canvas.drawRect(imageRect.left, cropRect.bottom, imageRect.right, imageRect.bottom, overlayPaint);
            canvas.drawRect(imageRect.left, cropRect.top, cropRect.left, cropRect.bottom, overlayPaint);
            canvas.drawRect(cropRect.right, cropRect.top, imageRect.right, cropRect.bottom, overlayPaint);
        }

        private void drawHandles(Canvas canvas) {
            float pillLong = dp(42);
            float pillShort = dp(7);
            float radius = dp(4);
            drawHandle(canvas, cropRect.centerX() - pillLong / 2f, cropRect.top - pillShort / 2f, cropRect.centerX() + pillLong / 2f, cropRect.top + pillShort / 2f, radius);
            drawHandle(canvas, cropRect.centerX() - pillLong / 2f, cropRect.bottom - pillShort / 2f, cropRect.centerX() + pillLong / 2f, cropRect.bottom + pillShort / 2f, radius);
            drawHandle(canvas, cropRect.left - pillShort / 2f, cropRect.centerY() - pillLong / 2f, cropRect.left + pillShort / 2f, cropRect.centerY() + pillLong / 2f, radius);
            drawHandle(canvas, cropRect.right - pillShort / 2f, cropRect.centerY() - pillLong / 2f, cropRect.right + pillShort / 2f, cropRect.centerY() + pillLong / 2f, radius);

            float corner = dp(26);
            drawHandle(canvas, cropRect.left - pillShort / 2f, cropRect.top - pillShort / 2f, cropRect.left + corner, cropRect.top + pillShort / 2f, radius);
            drawHandle(canvas, cropRect.left - pillShort / 2f, cropRect.top - pillShort / 2f, cropRect.left + pillShort / 2f, cropRect.top + corner, radius);
            drawHandle(canvas, cropRect.right - corner, cropRect.top - pillShort / 2f, cropRect.right + pillShort / 2f, cropRect.top + pillShort / 2f, radius);
            drawHandle(canvas, cropRect.right - pillShort / 2f, cropRect.top - pillShort / 2f, cropRect.right + pillShort / 2f, cropRect.top + corner, radius);
            drawHandle(canvas, cropRect.left - pillShort / 2f, cropRect.bottom - pillShort / 2f, cropRect.left + corner, cropRect.bottom + pillShort / 2f, radius);
            drawHandle(canvas, cropRect.left - pillShort / 2f, cropRect.bottom - corner, cropRect.left + pillShort / 2f, cropRect.bottom + pillShort / 2f, radius);
            drawHandle(canvas, cropRect.right - corner, cropRect.bottom - pillShort / 2f, cropRect.right + pillShort / 2f, cropRect.bottom + pillShort / 2f, radius);
            drawHandle(canvas, cropRect.right - pillShort / 2f, cropRect.bottom - corner, cropRect.right + pillShort / 2f, cropRect.bottom + pillShort / 2f, radius);
        }

        private void drawHandle(Canvas canvas, float left, float top, float right, float bottom, float radius) {
            canvas.drawRoundRect(new RectF(left, top, right, bottom), radius, radius, handlePaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (bitmap == null || bitmap.isRecycled()) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    activeHandle = detectHandle(event.getX(), event.getY());
                    if (activeHandle == HANDLE_NONE) {
                        return false;
                    }
                    downX = event.getX();
                    downY = event.getY();
                    downRect.set(cropRect);
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    updateCrop(event.getX() - downX, event.getY() - downY);
                    postInvalidateOnAnimation();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    activeHandle = HANDLE_NONE;
                    getParent().requestDisallowInterceptTouchEvent(false);
                    return true;
                default:
                    return true;
            }
        }

        private int detectHandle(float x, float y) {
            float hit = dp(34);
            if (near(x, y, cropRect.left, cropRect.top, hit)) return HANDLE_TOP_LEFT;
            if (near(x, y, cropRect.right, cropRect.top, hit)) return HANDLE_TOP_RIGHT;
            if (near(x, y, cropRect.left, cropRect.bottom, hit)) return HANDLE_BOTTOM_LEFT;
            if (near(x, y, cropRect.right, cropRect.bottom, hit)) return HANDLE_BOTTOM_RIGHT;
            if (Math.abs(x - cropRect.left) <= hit && y >= cropRect.top - hit && y <= cropRect.bottom + hit) return HANDLE_LEFT;
            if (Math.abs(x - cropRect.right) <= hit && y >= cropRect.top - hit && y <= cropRect.bottom + hit) return HANDLE_RIGHT;
            if (Math.abs(y - cropRect.top) <= hit && x >= cropRect.left - hit && x <= cropRect.right + hit) return HANDLE_TOP;
            if (Math.abs(y - cropRect.bottom) <= hit && x >= cropRect.left - hit && x <= cropRect.right + hit) return HANDLE_BOTTOM;
            return cropRect.contains(x, y) ? HANDLE_MOVE : HANDLE_NONE;
        }

        private boolean near(float x, float y, float targetX, float targetY, float distance) {
            return Math.abs(x - targetX) <= distance && Math.abs(y - targetY) <= distance;
        }

        private void updateCrop(float dx, float dy) {
            cropRect.set(downRect);
            switch (activeHandle) {
                case HANDLE_MOVE:
                    cropRect.offset(dx, dy);
                    break;
                case HANDLE_LEFT:
                    cropRect.left += dx;
                    break;
                case HANDLE_TOP:
                    cropRect.top += dy;
                    break;
                case HANDLE_RIGHT:
                    cropRect.right += dx;
                    break;
                case HANDLE_BOTTOM:
                    cropRect.bottom += dy;
                    break;
                case HANDLE_TOP_LEFT:
                    cropRect.left += dx;
                    cropRect.top += dy;
                    break;
                case HANDLE_TOP_RIGHT:
                    cropRect.right += dx;
                    cropRect.top += dy;
                    break;
                case HANDLE_BOTTOM_LEFT:
                    cropRect.left += dx;
                    cropRect.bottom += dy;
                    break;
                case HANDLE_BOTTOM_RIGHT:
                    cropRect.right += dx;
                    cropRect.bottom += dy;
                    break;
                default:
                    break;
            }
            clampCropRect();
        }

        private void clampCropRect() {
            float minSize = dp(90);
            if (activeHandle == HANDLE_MOVE) {
                if (cropRect.left < imageRect.left) cropRect.offset(imageRect.left - cropRect.left, 0);
                if (cropRect.right > imageRect.right) cropRect.offset(imageRect.right - cropRect.right, 0);
                if (cropRect.top < imageRect.top) cropRect.offset(0, imageRect.top - cropRect.top);
                if (cropRect.bottom > imageRect.bottom) cropRect.offset(0, imageRect.bottom - cropRect.bottom);
                return;
            }
            cropRect.left = Math.max(imageRect.left, Math.min(cropRect.left, cropRect.right - minSize));
            cropRect.top = Math.max(imageRect.top, Math.min(cropRect.top, cropRect.bottom - minSize));
            cropRect.right = Math.min(imageRect.right, Math.max(cropRect.right, cropRect.left + minSize));
            cropRect.bottom = Math.min(imageRect.bottom, Math.max(cropRect.bottom, cropRect.top + minSize));
            if (cropRect.left < imageRect.left) cropRect.left = imageRect.left;
            if (cropRect.top < imageRect.top) cropRect.top = imageRect.top;
            if (cropRect.right > imageRect.right) cropRect.right = imageRect.right;
            if (cropRect.bottom > imageRect.bottom) cropRect.bottom = imageRect.bottom;
        }

        private int clampInt(int value, int min, int max) {
            return Math.max(min, Math.min(value, max));
        }
    }

    private static class MessageContext {
        String transcript = "";
        String lastAssistant = "";
        String lastUser = "";
    }
}
