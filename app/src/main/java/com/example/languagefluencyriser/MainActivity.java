package com.example.languagefluencyriser;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String PREFS = "fluency_riser_prefs";
    private static final String PREF_SERVER = "server_url";
    private static final String PREF_MODEL = "model";
    private static final String PREF_SERVICE_TYPE = "service_type";
    private static final String PREF_API_KEY = "api_key";
    private static final String EMULATOR_DEFAULT_SERVER = "http://10.0.2.2:11434";
    private static final String PHONE_USB_REVERSE_SERVER = "http://127.0.0.1:11434";
    private static final String DEFAULT_MODEL = "gemma3:12b";
    private static final String EMULATOR_HOST = "10.0.2.2";
    private static final int MAX_MEMORY_CHARS = 5000;
    private static final int MAX_RESPONSES_PER_STEP = 5;
    private static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String CLOSING_STEP = "Supervisor Closing";
    // Output budgets: normal coaching turns are short; the closing step must fit a memory_update JSON.
    private static final int OLLAMA_TOKENS_DEFAULT = 200;
    private static final int OLLAMA_TOKENS_CLOSING = 900;
    private static final int OLLAMA_TOKENS_FEEDBACK = 450; // JSON reply + corrections
    private static final int GEMINI_TOKENS_DEFAULT = 1024;
    private static final int GEMINI_TOKENS_FEEDBACK = 2048;
    private static final int GEMINI_TOKENS_CLOSING = 4096;

    // Premium Dark Theme Color Palette Tokens
    // Same tokens as the redesign mockup. Text colors are chosen for >= 4.5:1 contrast on the ground.
    private static final int COLOR_BG_ROOT = Color.rgb(11, 16, 32);        // #0B1020 ground
    private static final int COLOR_BAR = Color.rgb(15, 20, 38);            // #0F1426 top/bottom bars
    private static final int COLOR_BAR_BORDER = Color.rgb(31, 39, 64);     // #1F2740
    private static final int COLOR_BG_CARD = Color.rgb(21, 27, 46);        // #151B2E surface
    private static final int COLOR_BORDER_CARD = Color.rgb(37, 45, 69);    // #252D45
    private static final int COLOR_CHIP_BORDER = Color.rgb(46, 54, 82);    // #2E3652
    private static final int COLOR_RAISED = Color.rgb(42, 50, 85);         // #2A3255 secondary buttons
    private static final int COLOR_TEXT_PRIMARY = Color.rgb(243, 244, 246);
    private static final int COLOR_TEXT_SECONDARY = Color.rgb(163, 172, 194); // #A3ACC2
    private static final int COLOR_TEXT_FAINT = Color.rgb(138, 147, 171);     // #8A93AB
    private static final int COLOR_ACCENT = Color.rgb(99, 102, 241);       // #6366F1 primary buttons
    private static final int COLOR_ACCENT_TEXT = Color.rgb(165, 180, 252); // #A5B4FC accent on dark
    private static final int COLOR_ACCENT_SOFT = Color.rgb(129, 140, 248); // #818CF8 progress
    private static final int COLOR_PROGRESS_OFF = Color.rgb(42, 49, 80);
    private static final int COLOR_USER_BUBBLE = Color.rgb(59, 63, 153);   // #3B3F99
    private static final int COLOR_AMBER = Color.rgb(251, 191, 36);
    private static final int COLOR_AMBER_BG = Color.rgb(30, 26, 14);
    private static final int COLOR_AMBER_BORDER = Color.rgb(74, 58, 15);
    private static final int COLOR_AMBER_MUTED = Color.rgb(214, 192, 138);
    private static final int COLOR_STRIKE = Color.rgb(253, 186, 116);
    private static final int COLOR_SUCCESS = Color.rgb(110, 231, 183);
    private static final int COLOR_DANGER = Color.rgb(185, 28, 28);
    private static final int COLOR_INPUT_BG = COLOR_BG_CARD;

    private static final String CONCISE_COACH_SYSTEM_PROMPT = 
            "You are a concise English speaking coach inside a mobile app.\n\n"
            + "Rules:\n"
            + "- Keep responses short.\n"
            + "- Do not explain your plan.\n"
            + "- Do not reveal lesson planning.\n"
            + "- Do not write headings like Plan, Priority Focus, Difficulty, Task Order, or First Task.\n"
            + "- Ask only one question at a time.\n"
            + "- Give feedback only after the learner answers.\n"
            + "- Maximum response length: 60 words.\n"
            + "- Use a friendly, supportive tone.\n\n"
            + "For session openings:\n"
            + "Return only:\n"
            + "1. One short warm-up sentence.\n"
            + "2. One speaking prompt.\n\n"
            + "For feedback:\n"
            + "Return only:\n"
            + "1. One correction.\n"
            + "2. One natural version.\n"
            + "3. One next question.";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ArrayList<String> chain = new ArrayList<>();
    private final StringBuilder transcript = new StringBuilder();
    // Full session conversation sent to the model on every request. Touched only on the main thread.
    // Each entry is {"role": "user"|"assistant", "content": "..."}.
    private final ArrayList<JSONObject> conversation = new ArrayList<>();
    private int userResponseCount = 0;

    private EditText serverUrlField;
    private Spinner serviceTypeSpinner;
    private TextView serverUrlLabel;
    private TextView serverUrlHint;
    private TextView apiKeyLabel;
    private EditText apiKeyField;
    private TextView apiKeyHint;
    private EditText modelField;
    private Button testButton;
    private TextView statusView;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech tts;
    private boolean isListening = false;
    private String lastPartial = ""; // last live words heard, used if the final result comes back empty
    private boolean voiceMode = false;
    private static final String PREF_VOICE_MODE = "voice_mode";
    private static final String PREF_FOCUS = "focus_area";
    private static final String PREF_FULL_DAY = "full_day";
    private static final String PREF_PRACTICE_DAYS = "practice_days";
    private static final String PREF_MINUTES_TOTAL = "minutes_total";
    private int chainIndex = -1;
    private boolean busy = false;

    // --- Screens: the tabbed main layout, the full-screen session, and the session summary ---
    private static final int SCREEN_MAIN = 0, SCREEN_SESSION = 1, SCREEN_SUMMARY = 2;
    private int currentScreen = SCREEN_MAIN;
    private LinearLayout mainLayout;
    private View sessionScreen, summaryScreen;
    private FrameLayout tabContentContainer;
    private View[] tabs;
    private LinearLayout[] navItems;
    private int currentTab = 0;

    // Today tab
    private static final String[] FOCUS_KEYS = {"Auto", "Speaking Lab", "Writing Studio", "Input Immersion", "Diagnostic Coach"};
    private static final String[] FOCUS_CHIP_LABELS = {"Auto", "Speaking", "Writing", "Immersion", "Diagnostic"};
    private String selectedFocusArea = "Auto";
    private boolean fullDaySession = false;
    private TextView todayDateView, streakView, focusLabelView, focusNameView, focusDetailView;
    private TextView reviewCardTitle, reviewCardSubtitle;
    private LinearLayout stepPreviewRow;
    private TextView[] focusChips;
    private TextView shortDayOption, fullDayOption;
    private android.widget.Switch voiceSwitch;
    private View startButton;

    // Session screen
    private TextView stepCounterView, stepTitleView, nextLabelView, composerHint;
    private LinearLayout segmentRow, composer;
    private View nextButton, closeButton;
    private ScrollView chatScroll;
    private LinearLayout transcriptContainer;
    private EditText userInput;
    private FrameLayout micButton, sendButton;
    private LineIcon micIcon;
    private ProgressBar progressBar;
    private View lastUserBubble;

    // Voice overlay (inside the session screen)
    private static final int VOICE_SPEAKING = 0, VOICE_LISTENING = 1, VOICE_THINKING = 2, VOICE_IDLE = 3, VOICE_PAUSED = 4;
    private int voiceState = VOICE_IDLE;
    private boolean voicePaused = false;
    private View voiceOverlay;
    private TextView voiceQuestionView, voiceStateView, voiceLiveView, voiceCorrectionView, voicePrimaryLabel;
    private View voiceOrbInner, voicePrimaryButton;
    private LineIcon voiceOrbIcon;

    // Session bookkeeping, shown in the summary
    private boolean sessionActive = false, sessionReadOnly = false, sessionSaved = false;
    private long sessionStartMillis;
    private int sessionAnswers, sessionReviewsRight, sessionReviewsTotal;
    private final ArrayList<JSONObject> sessionNewCards = new ArrayList<>();
    private String sessionClosingNotes = "";

    // Summary screen
    private TextView summarySubtitle, summaryAnswers, summaryCards, summaryReviews, summaryNotes, summaryFocusLine;
    private LinearLayout summaryCardsList;

    // Review tab
    private static final int SPEECH_SESSION = 0, SPEECH_REVIEW = 1;
    private int speechTarget = SPEECH_SESSION;
    private TextView reviewCountsView, reviewDueView;
    private LinearLayout reviewProgressRow, reviewCardHost, breakdownContainer;
    private final ArrayList<JSONObject> reviewQueue = new ArrayList<>();
    private int reviewIndex = 0, reviewRight = 0;
    private boolean reviewRevealed = false, reviewRoundStarted = false;
    private String reviewHeard = "";
    private TextView reviewLiveView;

    // History tab
    private LinearLayout historyContainer;

    @Override
    protected void onPause() {
        // Never keep the mic or voice running while the app is in the background
        if (tts != null) tts.stop();
        if (isListening) stopListening();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        super.onDestroy();
    }

    private void loadHistory() {
        if (historyContainer == null) return;
        historyContainer.removeAllViews();
        
        File dir = getFilesDir();
        File[] logs = dir.listFiles((d, name) -> name.startsWith("session_log_") && name.endsWith(".json"));
        
        if (logs == null || logs.length == 0) {
            LinearLayout emptyPanel = panel();
            TextView emptyText = new TextView(this);
            emptyText.setText("No practice history yet.\nYour completed sessions will appear here.");
            emptyText.setTextColor(COLOR_TEXT_SECONDARY);
            emptyText.setTextSize(14);
            emptyText.setGravity(Gravity.CENTER);
            emptyText.setPadding(0, dp(64), 0, dp(64));
            emptyPanel.addView(emptyText);
            historyContainer.addView(emptyPanel);
            return;
        }
        
        // Sort newest first
        Arrays.sort(logs, (a, b) -> b.getName().compareTo(a.getName()));
        
        for (File log : logs) {
            try {
                JSONObject json = new JSONObject(readText(log));
                String date = json.optString("date", "Unknown Date");
                String model = json.optString("model", "Unknown Model");
                String transcriptStr = json.optString("transcript", "");
                
                LinearLayout logPanel = panel();
                logPanel.setClickable(true);
                logPanel.setFocusable(true);
                
                TextView dateView = new TextView(this);
                String prettyDate = date.replace("T", " ");
                dateView.setText(prettyDate.length() > 16 ? prettyDate.substring(0, 16) : prettyDate);
                dateView.setTextColor(COLOR_TEXT_PRIMARY);
                dateView.setTextSize(16);
                dateView.setTypeface(font(FONT_MEDIUM));
                logPanel.addView(dateView);
                
                TextView modelView = new TextView(this);
                modelView.setText("Model: " + model);
                modelView.setTextColor(COLOR_TEXT_SECONDARY);
                modelView.setTextSize(12);
                modelView.setPadding(0, dp(4), 0, dp(8));
                logPanel.addView(modelView);
                
                TextView previewView = new TextView(this);
                String preview = transcriptStr.replace("\n", " ").trim();
                if (preview.length() > 100) preview = preview.substring(0, 97) + "...";
                previewView.setText(preview);
                previewView.setTextColor(COLOR_TEXT_SECONDARY);
                previewView.setTextSize(14);
                logPanel.addView(previewView);
                
                // Full transcript, expanded in place so the live session on Home is never touched
                TextView fullView = new TextView(this);
                fullView.setText(transcriptStr.trim());
                fullView.setTextColor(COLOR_TEXT_PRIMARY);
                fullView.setTextSize(14);
                fullView.setLineSpacing(dp(2), 1.1f);
                fullView.setTextIsSelectable(true);
                fullView.setPadding(0, dp(12), 0, 0);
                fullView.setVisibility(View.GONE);
                logPanel.addView(fullView);

                logPanel.setOnClickListener(v -> {
                    boolean expand = fullView.getVisibility() != View.VISIBLE;
                    fullView.setVisibility(expand ? View.VISIBLE : View.GONE);
                    previewView.setVisibility(expand ? View.GONE : View.VISIBLE);
                });

                historyContainer.addView(logPanel);
            } catch (Exception exception) {
                LinearLayout errorPanel = panel();
                TextView errorText = new TextView(this);
                errorText.setText("Could not read " + log.getName());
                errorText.setTextColor(COLOR_TEXT_SECONDARY);
                errorText.setTextSize(13);
                errorPanel.addView(errorText);
                historyContainer.addView(errorPanel);
            }
        }
    }

    private View buildHistoryTab() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_BG_ROOT);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(32), dp(20), dp(48));
        scrollView.addView(content);

        content.addView(heading("History", 30));
        
        historyContainer = new LinearLayout(this);
        historyContainer.setOrientation(LinearLayout.VERTICAL);
        content.addView(historyContainer);
        
        return scrollView;
    }

    private View buildSettingsTab() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_BG_ROOT);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(32));
        scrollView.addView(content);

        content.addView(heading("Settings", 30));
        content.addView(sectionHeader("AI connection"));
        LinearLayout configPanel = panel();
        content.addView(configPanel);

        configPanel.addView(label("Service Provider"));
        serviceTypeSpinner = spinner(Arrays.asList("Ollama (Local)", "Google Gemini"));
        configPanel.addView(serviceTypeSpinner);

        serverUrlLabel = label("Ollama Server URL");
        configPanel.addView(serverUrlLabel);
        serverUrlField = field(defaultServerUrl());
        configPanel.addView(serverUrlField);
        serverUrlHint = hintText("USB: adb reverse tcp:11434 tcp:11434. Emulator: 10.0.2.2. Tailscale/LAN: http://<computer-ip>:11434.");
        configPanel.addView(serverUrlHint);

        apiKeyLabel = label("Gemini API Key");
        configPanel.addView(apiKeyLabel);
        apiKeyField = field("");
        apiKeyField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        configPanel.addView(apiKeyField);
        apiKeyHint = hintText("Get a free key from Google AI Studio.");
        configPanel.addView(apiKeyHint);
        
        configPanel.addView(label("AI Model"));
        modelField = field(DEFAULT_MODEL);
        configPanel.addView(modelField);

        LinearLayout configActions = horizontal();
        testButton = secondaryButton("Test Connection");
        configActions.addView(testButton, weightParams());
        configPanel.addView(configActions);

        statusView = new TextView(this);
        statusView.setTextColor(Color.rgb(120, 130, 150));
        statusView.setTextSize(13);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, dp(16), 0, 0);
        content.addView(statusView);

        serviceTypeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                boolean isGemini = position == 1;
                serverUrlLabel.setVisibility(isGemini ? View.GONE : View.VISIBLE);
                serverUrlField.setVisibility(isGemini ? View.GONE : View.VISIBLE);
                serverUrlHint.setVisibility(isGemini ? View.GONE : View.VISIBLE);
                
                apiKeyLabel.setVisibility(isGemini ? View.VISIBLE : View.GONE);
                apiKeyField.setVisibility(isGemini ? View.VISIBLE : View.GONE);
                apiKeyHint.setVisibility(isGemini ? View.VISIBLE : View.GONE);

                if (isGemini) {
                    if (modelField.getText().toString().trim().equals(DEFAULT_MODEL) || modelField.getText().toString().trim().isEmpty()) {
                        modelField.setText("gemini-3.5-flash");
                    }
                } else {
                    if (modelField.getText().toString().trim().equals("gemini-3.5-flash") || modelField.getText().toString().trim().isEmpty()) {
                        modelField.setText(DEFAULT_MODEL);
                    }
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        testButton.setOnClickListener(v -> testConnection());

        return scrollView;
    }

    // =======================================================================================
    // Lifecycle
    // =======================================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ensureMemoryFiles();
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        voiceMode = prefs.getBoolean(PREF_VOICE_MODE, false);
        selectedFocusArea = prefs.getString(PREF_FOCUS, "Auto");
        fullDaySession = prefs.getBoolean(PREF_FULL_DAY, false);
        loadFonts();
        buildUi();
        loadPrefs();
        initTts();
        refreshToday();
    }

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) {
                tts = null;
                setStatus("Text-to-speech is not available on this device.");
            } else {
                tts.setLanguage(Locale.US);
                tts.setSpeechRate(0.95f); // a touch slower than default, for a learner
                tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
                    @Override public void onStart(String utteranceId) {}
                    @Override public void onDone(String utteranceId) {
                        if (UTTERANCE_AUTO_LISTEN.equals(utteranceId)) mainHandler.post(() -> autoListen());
                    }
                    @Override public void onError(String utteranceId) {
                        if (UTTERANCE_AUTO_LISTEN.equals(utteranceId)) mainHandler.post(() -> autoListen());
                    }
                });
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (currentScreen == SCREEN_SESSION) {
            if (sessionReadOnly) showScreen(SCREEN_SUMMARY);
            else requestEndSession();
        } else if (currentScreen == SCREEN_SUMMARY) {
            exitSession();
        } else if (currentTab != 0) {
            switchTab(0);
        } else {
            super.onBackPressed();
        }
    }

    // =======================================================================================
    // Layout shell: tabs + bottom navigation, with full-screen session and summary on top
    // =======================================================================================

    private void buildUi() {
        FrameLayout rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundColor(COLOR_BG_ROOT);

        mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        tabContentContainer = new FrameLayout(this);
        mainLayout.addView(tabContentContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        View navDivider = new View(this);
        navDivider.setBackgroundColor(COLOR_BAR_BORDER);
        mainLayout.addView(navDivider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));

        LinearLayout navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setBackgroundColor(COLOR_BAR);
        navBar.setPadding(dp(8), dp(6), dp(8), dp(6));
        mainLayout.addView(navBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64)));

        String[] labels = {"Today", "Review", "History", "Settings"};
        LineIcon.Type[] icons = {LineIcon.Type.HOME, LineIcon.Type.REVIEW, LineIcon.Type.HISTORY, LineIcon.Type.SETTINGS};
        navItems = new LinearLayout[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setClickable(true);
            item.setFocusable(true);
            item.setContentDescription(labels[i]);
            item.addView(new LineIcon(this, icons[i], COLOR_TEXT_FAINT), new LinearLayout.LayoutParams(dp(24), dp(24)));
            TextView text = new TextView(this);
            text.setText(labels[i]);
            text.setTextSize(12);
            text.setPadding(0, dp(4), 0, 0);
            text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            item.addView(text);
            item.setOnClickListener(v -> switchTab(index));
            navItems[i] = item;
            navBar.addView(item, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        }

        tabs = new View[]{buildTodayTab(), buildReviewTab(), buildHistoryTab(), buildSettingsTab()};
        for (View tab : tabs) tabContentContainer.addView(tab);

        rootFrame.addView(mainLayout);
        sessionScreen = buildSessionScreen();
        sessionScreen.setVisibility(View.GONE);
        rootFrame.addView(sessionScreen);
        summaryScreen = buildSummaryScreen();
        summaryScreen.setVisibility(View.GONE);
        rootFrame.addView(summaryScreen);

        setContentView(rootFrame);
        applySystemInsets(rootFrame);
        switchTab(0);
        initSpeechRecognizer();
    }

    /** Edge-to-edge on Android 11+: pad for status/nav bars and keep the composer above the keyboard. */
    private void applySystemInsets(View root) {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.ime());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return android.view.WindowInsets.CONSUMED;
            });
        }
    }

    private void switchTab(int index) {
        if (isListening && speechTarget == SPEECH_REVIEW && index != 1) cancelListening();
        currentTab = index;
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
            LinearLayout item = navItems[i];
            int color = i == index ? COLOR_ACCENT_TEXT : COLOR_TEXT_FAINT;
            ((LineIcon) item.getChildAt(0)).setColor(color);
            TextView label = (TextView) item.getChildAt(1);
            label.setTextColor(color);
            label.setTypeface(font(i == index ? FONT_SEMIBOLD : FONT_REGULAR));
            item.setSelected(i == index);
        }
        if (index == 0) refreshToday();
        if (index == 1) startReviewRound();
        if (index == 2) loadHistory();
    }

    private void showScreen(int screen) {
        currentScreen = screen;
        mainLayout.setVisibility(screen == SCREEN_MAIN ? View.VISIBLE : View.GONE);
        sessionScreen.setVisibility(screen == SCREEN_SESSION ? View.VISIBLE : View.GONE);
        summaryScreen.setVisibility(screen == SCREEN_SUMMARY ? View.VISIBLE : View.GONE);
        if (screen == SCREEN_SESSION) updateSessionChrome();
    }

    // =======================================================================================
    // Today tab
    // =======================================================================================

    private View buildTodayTab() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = vertical(dp(20));
        content.setPadding(dp(20), dp(24), dp(20), dp(32));
        scroll.addView(content);

        // Header: date + title, streak chip
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.BOTTOM);
        LinearLayout titles = vertical(dp(4));
        todayDateView = text("", 14, COLOR_TEXT_SECONDARY, false);
        titles.addView(todayDateView);
        titles.addView(heading("Today's practice", 30));
        header.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout streak = new LinearLayout(this);
        streak.setGravity(Gravity.CENTER_VERTICAL);
        streak.setPadding(dp(12), dp(8), dp(12), dp(8));
        streak.setBackground(rounded(COLOR_AMBER_BG, dp(999), 0, 0));
        streak.addView(new LineIcon(this, LineIcon.Type.FLAME, COLOR_AMBER), new LinearLayout.LayoutParams(dp(16), dp(16)));
        streakView = text("", 14, COLOR_AMBER, true);
        streakView.setPadding(dp(6), 0, 0, 0);
        streak.addView(streakView);
        header.addView(streak);
        content.addView(header);

        // Focus card with the main call to action
        LinearLayout focusCard = card(dp(24), dp(20));
        focusCard.setOrientation(LinearLayout.VERTICAL);
        focusLabelView = text("", 12, COLOR_ACCENT_TEXT, true);
        letterSpacing(focusLabelView, 0.08f);
        focusCard.addView(focusLabelView);
        focusNameView = heading("", 24);
        focusNameView.setPadding(0, dp(6), 0, dp(4));
        focusCard.addView(focusNameView);
        focusDetailView = text("", 14, COLOR_TEXT_SECONDARY, false);
        focusCard.addView(focusDetailView);
        stepPreviewRow = new LinearLayout(this);
        stepPreviewRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams previewParams = matchWrap();
        previewParams.setMargins(0, dp(16), 0, dp(16));
        focusCard.addView(stepPreviewRow, previewParams);
        startButton = primaryAction("Start session", LineIcon.Type.ARROW_RIGHT, true);
        startButton.setOnClickListener(v -> startChain());
        focusCard.addView(startButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));
        content.addView(focusCard);

        // Focus chips
        LinearLayout focusSection = vertical(dp(10));
        focusSection.addView(text("Change focus", 13, COLOR_TEXT_SECONDARY, true));
        // Chips wrap onto a second line instead of hiding off-screen
        FlowLayout chipRow = new FlowLayout(this, dp(8), dp(8));
        focusChips = new TextView[FOCUS_KEYS.length];
        for (int i = 0; i < FOCUS_KEYS.length; i++) {
            final String key = FOCUS_KEYS[i];
            TextView chip = text(FOCUS_CHIP_LABELS[i], 14, COLOR_TEXT_PRIMARY, false);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(16), 0, dp(16), 0);
            chip.setClickable(true);
            chip.setFocusable(true);
            chip.setOnClickListener(v -> {
                selectedFocusArea = key;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_FOCUS, key).apply();
                refreshToday();
            });
            chipRow.addView(chip, new android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
            focusChips[i] = chip;
        }
        focusSection.addView(chipRow, matchWrap());
        content.addView(focusSection);

        // Options: voice mode + session length
        LinearLayout options = card(dp(20), 0);
        options.setOrientation(LinearLayout.VERTICAL);
        options.setPadding(dp(16), dp(4), dp(16), dp(4));
        LinearLayout voiceRow = optionRow();
        LinearLayout voiceTexts = vertical(dp(2));
        voiceTexts.addView(text("Hands-free voice", 15, COLOR_TEXT_PRIMARY, true));
        voiceTexts.addView(text("Coach speaks, mic opens by itself", 12, COLOR_TEXT_SECONDARY, false));
        voiceRow.addView(voiceTexts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        voiceSwitch = new android.widget.Switch(this);
        voiceSwitch.setContentDescription("Hands-free voice");
        voiceSwitch.setChecked(voiceMode);
        voiceSwitch.setOnCheckedChangeListener((b, checked) -> setVoiceMode(checked));
        voiceRow.addView(voiceSwitch);
        options.addView(voiceRow);
        View divider = new View(this);
        divider.setBackgroundColor(COLOR_BORDER_CARD);
        options.addView(divider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        LinearLayout lengthRow = optionRow();
        lengthRow.addView(text("Session length", 15, COLOR_TEXT_PRIMARY, true),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout segmented = new LinearLayout(this);
        segmented.setPadding(dp(3), dp(3), dp(3), dp(3));
        segmented.setBackground(rounded(COLOR_BG_ROOT, dp(12), 0, 0));
        shortDayOption = segment("Short", false);
        fullDayOption = segment("Full", true);
        segmented.addView(shortDayOption, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)));
        segmented.addView(fullDayOption, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)));
        lengthRow.addView(segmented);
        options.addView(lengthRow);
        content.addView(options);

        // Review reminder
        LinearLayout reviewCard = new LinearLayout(this);
        reviewCard.setOrientation(LinearLayout.HORIZONTAL);
        reviewCard.setGravity(Gravity.CENTER_VERTICAL);
        reviewCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        reviewCard.setBackground(rounded(COLOR_AMBER_BG, dp(20), dp(1), COLOR_AMBER_BORDER));
        reviewCard.setClickable(true);
        reviewCard.setFocusable(true);
        reviewCard.setOnClickListener(v -> switchTab(1));
        FrameLayout reviewIconBox = new FrameLayout(this);
        reviewIconBox.setBackground(rounded(Color.rgb(58, 46, 12), dp(14), 0, 0));
        reviewIconBox.addView(new LineIcon(this, LineIcon.Type.REVIEW, COLOR_AMBER),
                new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER));
        reviewCard.addView(reviewIconBox, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout reviewTexts = vertical(dp(2));
        reviewTexts.setPadding(dp(14), 0, dp(8), 0);
        reviewCardTitle = text("", 15, COLOR_TEXT_PRIMARY, true);
        reviewCardSubtitle = text("", 13, COLOR_AMBER_MUTED, false);
        reviewTexts.addView(reviewCardTitle);
        reviewTexts.addView(reviewCardSubtitle);
        reviewCard.addView(reviewTexts, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        reviewCard.addView(new LineIcon(this, LineIcon.Type.CHEVRON_RIGHT, COLOR_AMBER), new LinearLayout.LayoutParams(dp(20), dp(20)));
        content.addView(reviewCard);

        return scroll;
    }

    private TextView segment(String label, boolean full) {
        TextView option = text(label, 13, COLOR_TEXT_SECONDARY, true);
        option.setGravity(Gravity.CENTER);
        option.setPadding(dp(16), 0, dp(16), 0);
        option.setClickable(true);
        option.setFocusable(true);
        option.setOnClickListener(v -> {
            fullDaySession = full;
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_FULL_DAY, full).apply();
            refreshToday();
        });
        return option;
    }

    private LinearLayout optionRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(60));
        return row;
    }

    /** Refreshes everything on the Today tab from prefs, the deck and the planned chain. */
    private void refreshToday() {
        if (focusNameView == null) return;
        todayDateView.setText(LocalDate.now().format(
                java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.US)));
        int streak = currentStreak();
        streakView.setText(streak == 0 ? "Start a streak" : streak + (streak == 1 ? " day" : " days"));

        boolean auto = "Auto".equals(selectedFocusArea);
        String production = auto ? roleForToday() : selectedFocusArea;
        focusLabelView.setText(auto ? "TODAY'S FOCUS · AUTO" : "TODAY'S FOCUS");
        focusNameView.setText("Recall & Drill".equals(production) ? "Review day" : production);

        List<String> plan = buildChain();
        int due = dueReviewItems(Integer.MAX_VALUE).size();
        int reviewsIncluded = plan.contains("Recall & Drill") ? Math.min(due, MAX_REVIEWS_PER_SESSION) : 0;
        focusDetailView.setText("About " + (plan.size() * 3) + " min"
                + (reviewsIncluded > 0 ? " · " + reviewsIncluded + (reviewsIncluded == 1 ? " review" : " reviews") + " included" : ""));

        stepPreviewRow.removeAllViews();
        for (int i = 0; i < plan.size(); i++) {
            LinearLayout column = vertical(dp(6));
            View bar = new View(this);
            bar.setBackground(rounded(COLOR_ACCENT_SOFT, dp(2), 0, 0));
            column.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4)));
            TextView label = text(stepDisplayName(plan.get(i)), 11, COLOR_TEXT_SECONDARY, false);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            column.addView(label);
            LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            if (i > 0) columnParams.setMargins(dp(6), 0, 0, 0);
            stepPreviewRow.addView(column, columnParams);
        }

        for (int i = 0; i < focusChips.length; i++) {
            boolean selected = FOCUS_KEYS[i].equals(selectedFocusArea);
            focusChips[i].setBackground(rounded(selected ? Color.rgb(35, 42, 82) : Color.TRANSPARENT, dp(999),
                    dp(1), selected ? COLOR_ACCENT_SOFT : COLOR_CHIP_BORDER));
            focusChips[i].setTextColor(selected ? Color.rgb(224, 231, 255) : Color.rgb(209, 213, 219));
            focusChips[i].setSelected(selected);
        }
        styleSegment(shortDayOption, !fullDaySession);
        styleSegment(fullDayOption, fullDaySession);
        if (voiceSwitch.isChecked() != voiceMode) voiceSwitch.setChecked(voiceMode);

        JSONArray deck = loadReviewDeck();
        if (deck.length() == 0) {
            reviewCardTitle.setText("Your review deck is empty");
            reviewCardSubtitle.setText("Corrections from your sessions are saved here");
        } else if (due == 0) {
            reviewCardTitle.setText("All caught up");
            reviewCardSubtitle.setText(deck.length() + " cards in your deck");
        } else {
            reviewCardTitle.setText(due + (due == 1 ? " correction" : " corrections") + " to review");
            reviewCardSubtitle.setText("Quick round, about " + Math.max(1, (due + 1) / 2) + " min");
        }
    }

    private void styleSegment(TextView option, boolean selected) {
        option.setBackground(rounded(selected ? COLOR_RAISED : Color.TRANSPARENT, dp(9), 0, 0));
        option.setTextColor(selected ? COLOR_TEXT_PRIMARY : COLOR_TEXT_SECONDARY);
        option.setSelected(selected);
    }

    // =======================================================================================
    // Session screen: header with progress, chat, fixed composer, voice overlay
    // =======================================================================================

    private View buildSessionScreen() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(COLOR_BG_ROOT);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(COLOR_BAR);
        header.setPadding(dp(16), dp(12), dp(16), dp(14));
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        closeButton = iconButton(LineIcon.Type.CLOSE, "End session", COLOR_BG_CARD, Color.rgb(209, 213, 219), dp(14), 20);
        closeButton.setOnClickListener(v -> {
            if (sessionReadOnly) showScreen(SCREEN_SUMMARY);
            else requestEndSession();
        });
        headerRow.addView(closeButton, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout titles = vertical(dp(2));
        titles.setPadding(dp(12), 0, dp(12), 0);
        stepCounterView = text("", 12, COLOR_TEXT_SECONDARY, false);
        stepTitleView = heading("", 18);
        titles.addView(stepCounterView);
        titles.addView(stepTitleView);
        headerRow.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout next = new LinearLayout(this);
        next.setGravity(Gravity.CENTER_VERTICAL);
        next.setPadding(dp(14), 0, dp(12), 0);
        next.setBackground(rounded(Color.TRANSPARENT, dp(12), dp(1), Color.rgb(58, 66, 112)));
        next.setClickable(true);
        next.setFocusable(true);
        nextLabelView = text("Next", 14, Color.rgb(199, 210, 254), true);
        next.addView(nextLabelView);
        LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(dp(16), dp(16));
        arrowParams.setMargins(dp(6), 0, 0, 0);
        next.addView(new LineIcon(this, LineIcon.Type.ARROW_RIGHT, Color.rgb(199, 210, 254)), arrowParams);
        next.setOnClickListener(v -> nextStep());
        nextButton = next;
        headerRow.addView(next, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)));
        header.addView(headerRow);
        segmentRow = new LinearLayout(this);
        segmentRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams segmentParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4));
        segmentParams.setMargins(0, dp(12), 0, 0);
        header.addView(segmentRow, segmentParams);
        screen.addView(header);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(COLOR_ACCENT_SOFT));
        progressBar.setVisibility(View.INVISIBLE);
        screen.addView(progressBar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3)));

        FrameLayout chatArea = new FrameLayout(this);
        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        transcriptContainer = new LinearLayout(this);
        transcriptContainer.setOrientation(LinearLayout.VERTICAL);
        transcriptContainer.setPadding(dp(16), dp(16), dp(16), dp(16));
        chatScroll.addView(transcriptContainer);
        chatArea.addView(chatScroll);
        voiceOverlay = buildVoiceOverlay();
        voiceOverlay.setVisibility(View.GONE);
        chatArea.addView(voiceOverlay);
        screen.addView(chatArea, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.VERTICAL);
        composer.setBackgroundColor(COLOR_BAR);
        composer.setPadding(dp(12), dp(10), dp(12), dp(12));
        composerHint = text("", 12, COLOR_TEXT_FAINT, false);
        composerHint.setPadding(dp(6), 0, 0, dp(8));
        composer.addView(composerHint);
        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setGravity(Gravity.BOTTOM);
        userInput = new EditText(this);
        userInput.setHint("Type your answer…");
        userInput.setHintTextColor(COLOR_TEXT_FAINT);
        userInput.setTextColor(COLOR_TEXT_PRIMARY);
        userInput.setTextSize(16);
        userInput.setTypeface(font(FONT_REGULAR));
        userInput.setMinHeight(dp(52));
        userInput.setMaxLines(5);
        userInput.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        userInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        userInput.setPadding(dp(16), dp(12), dp(16), dp(12));
        userInput.setBackground(rounded(COLOR_BG_CARD, dp(16), dp(1), COLOR_CHIP_BORDER));
        inputRow.addView(userInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        micButton = iconButton(LineIcon.Type.MIC, "Speak your answer", COLOR_RAISED, Color.rgb(224, 231, 255), dp(16), 22);
        micIcon = (LineIcon) micButton.getChildAt(0);
        micButton.setOnClickListener(v -> toggleMic(SPEECH_SESSION));
        LinearLayout.LayoutParams micParams = new LinearLayout.LayoutParams(dp(52), dp(52));
        micParams.setMargins(dp(8), 0, 0, 0);
        inputRow.addView(micButton, micParams);
        sendButton = iconButton(LineIcon.Type.SEND, "Send", COLOR_ACCENT, Color.WHITE, dp(16), 22);
        sendButton.setOnClickListener(v -> sendCurrentInput());
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(52), dp(52));
        sendParams.setMargins(dp(8), 0, 0, 0);
        inputRow.addView(sendButton, sendParams);
        composer.addView(inputRow);
        screen.addView(composer);
        return screen;
    }

    private View buildVoiceOverlay() {
        LinearLayout overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setBackgroundColor(COLOR_BG_ROOT);
        overlay.setPadding(dp(24), dp(20), dp(24), dp(24));
        overlay.setClickable(true); // block taps reaching the chat underneath

        TextView asked = text("COACH ASKED", 12, COLOR_ACCENT_TEXT, true);
        letterSpacing(asked, 0.08f);
        overlay.addView(asked);
        voiceQuestionView = text("", 20, COLOR_TEXT_PRIMARY, false);
        voiceQuestionView.setLineSpacing(0, 1.25f);
        voiceQuestionView.setMaxLines(7);
        voiceQuestionView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        voiceQuestionView.setPadding(0, dp(10), 0, 0);
        overlay.addView(voiceQuestionView);

        LinearLayout middle = vertical(dp(18));
        middle.setGravity(Gravity.CENTER);
        FrameLayout orb = new FrameLayout(this);
        View outer = new View(this);
        outer.setBackground(circleDrawable(Color.rgb(20, 26, 51), 0, 0));
        orb.addView(outer, new FrameLayout.LayoutParams(dp(200), dp(200), Gravity.CENTER));
        View middleRing = new View(this);
        middleRing.setBackground(circleDrawable(Color.rgb(28, 35, 80), 0, 0));
        orb.addView(middleRing, new FrameLayout.LayoutParams(dp(152), dp(152), Gravity.CENTER));
        voiceOrbInner = new View(this);
        voiceOrbInner.setBackground(circleDrawable(COLOR_ACCENT, 0, 0));
        orb.addView(voiceOrbInner, new FrameLayout.LayoutParams(dp(104), dp(104), Gravity.CENTER));
        voiceOrbIcon = new LineIcon(this, LineIcon.Type.MIC, Color.WHITE);
        orb.addView(voiceOrbIcon, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
        middle.addView(orb, new LinearLayout.LayoutParams(dp(200), dp(200)));
        voiceStateView = text("", 15, Color.rgb(199, 210, 254), true);
        voiceStateView.setGravity(Gravity.CENTER);
        middle.addView(voiceStateView);
        voiceLiveView = text("", 18, Color.rgb(229, 231, 235), false);
        voiceLiveView.setGravity(Gravity.CENTER);
        voiceLiveView.setMaxLines(4);
        voiceLiveView.setEllipsize(android.text.TextUtils.TruncateAt.START);
        middle.addView(voiceLiveView);
        voiceCorrectionView = text("", 14, COLOR_TEXT_PRIMARY, false);
        voiceCorrectionView.setPadding(dp(14), dp(10), dp(14), dp(10));
        voiceCorrectionView.setBackground(rounded(COLOR_AMBER_BG, dp(14), dp(1), COLOR_AMBER_BORDER));
        voiceCorrectionView.setVisibility(View.GONE);
        middle.addView(voiceCorrectionView, matchWrap());
        overlay.addView(middle, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout primary = new LinearLayout(this);
        primary.setGravity(Gravity.CENTER);
        primary.setBackground(rounded(COLOR_TEXT_PRIMARY, dp(16), 0, 0));
        primary.setClickable(true);
        primary.setFocusable(true);
        voicePrimaryLabel = text("Speak", 17, COLOR_BG_ROOT, true);
        primary.addView(voicePrimaryLabel);
        primary.setOnClickListener(v -> onVoicePrimary());
        voicePrimaryButton = primary;
        overlay.addView(primary, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));

        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(10), 0, 0);
        View pause = outlinedAction("Pause", LineIcon.Type.PAUSE);
        pause.setOnClickListener(v -> pauseVoice());
        View type = outlinedAction("Type instead", LineIcon.Type.KEYBOARD);
        type.setOnClickListener(v -> setVoiceMode(false));
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(48), 1);
        row.addView(pause, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, dp(48), 1);
        half2.setMargins(dp(10), 0, 0, 0);
        row.addView(type, half2);
        overlay.addView(row);
        return overlay;
    }

    /** Shows the voice overlay or the chat + composer, depending on mode. */
    private void updateSessionChrome() {
        boolean showVoice = voiceMode && sessionActive && !sessionReadOnly;
        voiceOverlay.setVisibility(showVoice ? View.VISIBLE : View.GONE);
        composer.setVisibility(!showVoice && !sessionReadOnly ? View.VISIBLE : View.GONE);
        nextButton.setVisibility(sessionReadOnly ? View.GONE : View.VISIBLE);
        if (sessionReadOnly) {
            stepCounterView.setText("Session transcript");
            stepTitleView.setText(sessionFocusName());
        }
        updateComposerHint();
    }

    private void buildSegments() {
        segmentRow.removeAllViews();
        for (int i = 0; i < chain.size(); i++) {
            View segment = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
            if (i > 0) params.setMargins(dp(4), 0, 0, 0);
            segmentRow.addView(segment, params);
        }
    }

    private void updateSessionHeader() {
        int total = chain.size();
        int index = Math.max(0, chainIndex);
        stepCounterView.setText("Step " + (index + 1) + " of " + total);
        stepTitleView.setText(stepDisplayName(currentStep()));
        nextLabelView.setText(chainIndex >= total - 1 ? "Finish" : "Next");
        for (int i = 0; i < segmentRow.getChildCount(); i++) {
            segmentRow.getChildAt(i).setBackground(rounded(i <= chainIndex ? COLOR_ACCENT_SOFT : COLOR_PROGRESS_OFF, dp(2), 0, 0));
        }
    }

    private void updateComposerHint() {
        if (composerHint == null) return;
        if (busy) {
            composerHint.setText("Coach is thinking…");
        } else if (userResponseCount >= MAX_RESPONSES_PER_STEP) {
            composerHint.setText("Step complete. Tap " + nextLabelView.getText() + " to continue.");
        } else {
            composerHint.setText("Answer " + (userResponseCount + 1) + " of " + MAX_RESPONSES_PER_STEP + " in this step");
        }
    }

    /** Short, learner-facing names for the internal chain steps. */
    private String stepDisplayName(String step) {
        switch (step) {
            case "Supervisor Opening": return "Warm-up";
            case "Recall & Drill": return "Review";
            case "Speaking Lab": return "Speaking";
            case "Writing Studio": return "Writing";
            case "Input Immersion": return "Immersion";
            case "Diagnostic Coach": return "Diagnostic";
            case "Error Analyst": return "Errors";
            case "Supervisor Closing": return "Wrap-up";
            default: return step;
        }
    }

    private String sessionFocusName() {
        for (String step : chain) {
            if (Arrays.asList(FOCUS_KEYS).contains(step)) return step;
        }
        return chain.contains("Recall & Drill") ? "Review day" : "Practice";
    }

    // =======================================================================================
    // Session flow
    // =======================================================================================

    private List<String> buildChain() {
        String focus = selectedFocusArea;
        ArrayList<String> steps = new ArrayList<>();

        if ("Diagnostic Coach".equals(focus)) {
            steps.add("Diagnostic Coach");
            steps.add("Error Analyst");
            steps.add("Supervisor Closing");
            return steps;
        }

        String production = "Auto".equals(focus) ? roleForToday() : focus;
        steps.add("Supervisor Opening");
        steps.add("Recall & Drill");
        if (!"Recall & Drill".equals(production)) {
            steps.add(production); // review days have no separate production step
        }
        if (fullDaySession && !"Input Immersion".equals(production)) {
            steps.add("Input Immersion");
        }
        steps.add("Error Analyst");
        steps.add("Supervisor Closing");
        return steps;
    }

    private void startChain() {
        savePrefs();
        userResponseCount = 0;
        conversation.clear();
        chain.clear();
        chain.addAll(buildChain());
        chainIndex = -1;
        transcript.setLength(0);
        transcriptContainer.removeAllViews();
        lastUserBubble = null;
        sessionActive = true;
        sessionReadOnly = false;
        sessionSaved = false;
        voicePaused = false;
        sessionStartMillis = System.currentTimeMillis();
        sessionAnswers = 0;
        sessionReviewsRight = 0;
        sessionReviewsTotal = 0;
        sessionNewCards.clear();
        sessionClosingNotes = "";
        voiceQuestionView.setText("");
        voiceLiveView.setText("");
        voiceCorrectionView.setVisibility(View.GONE);
        buildSegments();
        showScreen(SCREEN_SESSION);
        nextStep();
    }

    private void nextStep() {
        if (!sessionActive || busy) return;
        if (chainIndex + 1 >= chain.size()) {
            finishSession();
            return;
        }
        if (tts != null) tts.stop();
        if (isListening) cancelListening();
        chainIndex++;
        userResponseCount = 0; // the response limit applies per step
        String step = chain.get(chainIndex);
        updateSessionHeader();
        reconfigureInputUi(getEffectiveFocusArea());
        updateComposerHint();
        sendOllama(stepPrompt(step), step, false);
    }

    private void reconfigureInputUi(String effective) {
        if ("Writing Studio".equals(effective)) {
            userInput.setMinLines(4);
            userInput.setMaxLines(10);
            userInput.setGravity(Gravity.TOP | Gravity.START);
            userInput.setHint("Write your answer here…");
        } else {
            userInput.setMinLines(1);
            userInput.setMaxLines(5);
            userInput.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            userInput.setHint("Speaking Lab".equals(effective) ? "Tap the mic or type…" : "Type your answer…");
        }
    }

    private String getEffectiveFocusArea() {
        String step = currentStep();
        if (Arrays.asList(FOCUS_KEYS).contains(step) && !"Auto".equals(step)) return step;
        return "Auto".equals(selectedFocusArea) ? roleForToday() : selectedFocusArea;
    }

    private void sendCurrentInput() {
        if (!sessionActive || sessionReadOnly || busy) return;
        String text = userInput.getText().toString().trim();
        if (text.isEmpty()) {
            composerHint.setText("Type or say an answer first.");
            return;
        }
        if (userResponseCount >= MAX_RESPONSES_PER_STEP) {
            // Keep the typed text so it isn't lost; the learner can send it in the next step.
            appendSystem("This step is complete (" + MAX_RESPONSES_PER_STEP + " answers). Tap " + nextLabelView.getText() + " to continue.");
            return;
        }

        String step = currentStep();
        userResponseCount++;
        sessionAnswers++;
        appendTranscript("You", text);
        userInput.setText("");
        if (voiceMode) voiceLiveView.setText("\"" + text + "\"");

        String prompt = "Learner response:\n" + text + "\n\nContinue the current step. Give feedback, then tell me exactly what to do next.";
        if (userResponseCount >= MAX_RESPONSES_PER_STEP) {
            prompt = "Learner response:\n" + text + "\n\nThis is the FINAL interaction for this step. Provide brief feedback, then tell the learner this step is complete and to tap '"
                    + nextLabelView.getText() + "'. Do not ask another question.";
        }
        sendOllama(prompt + feedbackJsonInstructions(step), step, true);
    }

    private void requestEndSession() {
        if (sessionAnswers == 0) {
            stopVoiceOutput();
            exitSession();
            return;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("End this session?")
                .setMessage("Your answers are saved and you'll see a summary.")
                .setPositiveButton("End session", (d, w) -> finishSession())
                .setNegativeButton("Keep practising", null)
                .show();
    }

    /** Saves the session automatically and shows the summary. */
    private void finishSession() {
        stopVoiceOutput();
        if (sessionAnswers == 0) {
            exitSession();
            return;
        }
        int minutes = (int) Math.max(1, Math.round((System.currentTimeMillis() - sessionStartMillis) / 60000.0));
        if (!sessionSaved) {
            saveSessionLog();
            recordPracticeDay(minutes);
            sessionSaved = true;
        }
        populateSummary(minutes);
        sessionReadOnly = true;
        showScreen(SCREEN_SUMMARY);
    }

    private void exitSession() {
        stopVoiceOutput();
        sessionActive = false;
        sessionReadOnly = false;
        showScreen(SCREEN_MAIN);
        switchTab(0);
    }

    private void stopVoiceOutput() {
        if (tts != null) tts.stop();
        if (isListening) cancelListening();
    }

    private void setBusy(boolean value) {
        busy = value;
        setActionEnabled(startButton, !value);
        setActionEnabled(nextButton, !value);
        setActionEnabled(sendButton, !value);
        setActionEnabled(micButton, !value);
        if (testButton != null) testButton.setEnabled(!value);
        if (progressBar != null) progressBar.setVisibility(value ? View.VISIBLE : View.INVISIBLE);
        updateComposerHint();
        if (value && voiceOverlay != null && voiceOverlay.getVisibility() == View.VISIBLE) {
            setVoiceState(VOICE_THINKING, null);
        } else if (!value && voiceState == VOICE_THINKING) {
            setVoiceState(VOICE_IDLE, null); // the request finished; never stay on "thinking"
        }
    }

    /** Shows an error on the voice screen, where the hidden chat can't show it. */
    private void showVoiceError(String message) {
        if (voiceOverlay == null || voiceOverlay.getVisibility() != View.VISIBLE) return;
        setVoiceState(VOICE_IDLE, message + "\nTap Speak to try again.");
    }

    private void setActionEnabled(View view, boolean enabled) {
        if (view == null) return;
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.5f);
    }

    // =======================================================================================
    // Chat transcript
    // =======================================================================================

    private void appendUser(String text) {
        appendTranscript("You", text);
    }

    private void appendTranscript(String speaker, String text) {
        if (transcript.length() == 0) {
            transcriptContainer.removeAllViews();
        }
        String body = text == null ? "" : text.trim();
        transcript.append("\n\n[").append(speaker).append("]\n").append(body);

        boolean isUser = "You".equals(speaker);
        boolean isSystem = "System".equals(speaker);
        boolean isImmersion = "Input Immersion".equals(speaker);

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(isUser ? Gravity.END : isSystem ? Gravity.CENTER_HORIZONTAL : Gravity.START);
        LinearLayout.LayoutParams wrapperParams = matchWrap();
        wrapperParams.setMargins(isUser ? dp(48) : 0, 0, isUser || isSystem ? 0 : dp(32), dp(14));

        if (isSystem) {
            TextView note = text(body, 13, COLOR_TEXT_SECONDARY, false);
            note.setGravity(Gravity.CENTER);
            note.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
            note.setPadding(dp(16), dp(4), dp(16), dp(4));
            wrapper.addView(note);
            transcriptContainer.addView(wrapper, wrapperParams);
            scrollChatToBottom();
            return;
        }

        if (!isUser) {
            TextView who = text("COACH · " + stepDisplayName(speaker).toUpperCase(Locale.US), 11, COLOR_TEXT_FAINT, true);
            letterSpacing(who, 0.08f);
            who.setPadding(dp(4), 0, 0, dp(6));
            wrapper.addView(who);
        }

        TextView bubble = text(body, isImmersion ? 19 : 16, isUser ? Color.WHITE : COLOR_TEXT_PRIMARY, false);
        bubble.setLineSpacing(0, isImmersion ? 1.3f : 1.2f);
        if (isImmersion) bubble.setTypeface(Typeface.SERIF);
        bubble.setTextIsSelectable(true);
        bubble.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable shape = new GradientDrawable();
        float big = dp(20), small = dp(6);
        if (isUser) {
            shape.setColor(COLOR_USER_BUBBLE);
            shape.setCornerRadii(new float[]{big, big, small, small, big, big, big, big});
        } else {
            shape.setColor(COLOR_BG_CARD);
            shape.setStroke(dp(1), COLOR_BORDER_CARD);
            shape.setCornerRadii(new float[]{small, small, big, big, big, big, big, big});
        }
        bubble.setBackground(shape);
        wrapper.addView(bubble);

        if (!isUser && !body.isEmpty()) {
            LinearLayout listen = new LinearLayout(this);
            listen.setGravity(Gravity.CENTER_VERTICAL);
            listen.setPadding(dp(12), 0, dp(14), 0);
            listen.setBackground(rounded(Color.TRANSPARENT, dp(999), dp(1), COLOR_CHIP_BORDER));
            listen.setClickable(true);
            listen.setFocusable(true);
            listen.setContentDescription("Listen");
            listen.addView(new LineIcon(this, LineIcon.Type.SPEAKER, COLOR_TEXT_SECONDARY), new LinearLayout.LayoutParams(dp(16), dp(16)));
            TextView label = text("Listen", 13, COLOR_TEXT_SECONDARY, false);
            label.setPadding(dp(6), 0, 0, 0);
            listen.addView(label);
            listen.setOnClickListener(v -> {
                if (tts != null) speakCoach(body, false);
                else setStatus("Text-to-speech is not ready yet.");
            });
            LinearLayout.LayoutParams listenParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34));
            listenParams.setMargins(0, dp(8), 0, 0);
            wrapper.addView(listen, listenParams);
            if (voiceQuestionView != null) voiceQuestionView.setText(body);
        }

        transcriptContainer.addView(wrapper, wrapperParams);
        if (isUser) lastUserBubble = wrapper;
        scrollChatToBottom();
    }

    private void scrollChatToBottom() {
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    /** Shows the coach's corrections right under the answer they correct, and logs them. */
    private void appendCorrections(JSONArray corrections) {
        LinearLayout card = vertical(dp(6));
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(rounded(COLOR_AMBER_BG, dp(16), dp(1), COLOR_AMBER_BORDER));
        TextView title = text(corrections.length() == 1 ? "CORRECTION · SAVED TO REVIEW" : "CORRECTIONS · SAVED TO REVIEW",
                11, COLOR_AMBER, true);
        letterSpacing(title, 0.06f);
        card.addView(title);

        StringBuilder log = new StringBuilder();
        String firstCorrected = null;
        for (int i = 0; i < corrections.length(); i++) {
            JSONObject c = corrections.optJSONObject(i);
            if (c == null || c.optString("corrected").trim().isEmpty()) continue;
            String original = c.optString("original").trim();
            String corrected = c.optString("corrected").trim();
            String why = c.optString("explanation").trim();
            if (firstCorrected == null) firstCorrected = corrected;
            if (!original.isEmpty()) {
                TextView wrong = text(original, 15, COLOR_STRIKE, false);
                wrong.setPaintFlags(wrong.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                wrong.setContentDescription("You said: " + original);
                if (i > 0) wrong.setPadding(0, dp(8), 0, 0);
                card.addView(wrong);
            }
            TextView right = text(corrected, 15, Color.WHITE, true);
            right.setTextIsSelectable(true);
            card.addView(right);
            if (!why.isEmpty()) card.addView(text(why, 13, COLOR_AMBER_MUTED, false));
            log.append("\n- ").append(original).append(" → ").append(corrected);
            if (!why.isEmpty()) log.append(" (").append(why).append(")");
        }
        if (log.length() == 0) return;
        transcript.append("\n\n[Corrections]").append(log);

        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(dp(48), 0, 0, dp(14));
        int index = lastUserBubble != null ? transcriptContainer.indexOfChild(lastUserBubble) + 1 : -1;
        if (index <= 0 || index > transcriptContainer.getChildCount()) index = transcriptContainer.getChildCount();
        transcriptContainer.addView(card, index, params);

        if (voiceCorrectionView != null && firstCorrected != null) {
            voiceCorrectionView.setText("Correction saved: " + firstCorrected);
            voiceCorrectionView.setVisibility(View.VISIBLE);
        }
    }

    private void refreshDeckStats() {
        refreshToday();
    }

    // =======================================================================================
    // Summary screen
    // =======================================================================================

    private View buildSummaryScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BG_ROOT);
        LinearLayout content = vertical(dp(20));
        content.setPadding(dp(20), dp(40), dp(20), dp(32));
        scroll.addView(content);

        LinearLayout top = vertical(dp(10));
        top.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout check = new FrameLayout(this);
        check.setBackground(circleDrawable(Color.rgb(18, 51, 41), 0, 0));
        check.addView(new LineIcon(this, LineIcon.Type.CHECK, COLOR_SUCCESS), new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.CENTER));
        top.addView(check, new LinearLayout.LayoutParams(dp(64), dp(64)));
        TextView title = heading("Session complete", 28);
        title.setGravity(Gravity.CENTER);
        top.addView(title);
        summarySubtitle = text("", 15, COLOR_TEXT_SECONDARY, false);
        summarySubtitle.setGravity(Gravity.CENTER);
        top.addView(summarySubtitle);
        content.addView(top, matchWrap());

        LinearLayout stats = new LinearLayout(this);
        summaryAnswers = statTile(stats, "answers", COLOR_TEXT_PRIMARY, false);
        summaryCards = statTile(stats, "new cards", COLOR_AMBER, true);
        summaryReviews = statTile(stats, "reviews right", COLOR_SUCCESS, true);
        content.addView(stats, matchWrap());

        LinearLayout notes = card(dp(20), dp(16));
        notes.setOrientation(LinearLayout.VERTICAL);
        TextView notesLabel = text("COACH'S NOTES", 12, COLOR_ACCENT_TEXT, true);
        letterSpacing(notesLabel, 0.08f);
        notes.addView(notesLabel);
        summaryNotes = text("", 16, COLOR_TEXT_PRIMARY, false);
        summaryNotes.setLineSpacing(0, 1.25f);
        summaryNotes.setPadding(0, dp(10), 0, 0);
        notes.addView(summaryNotes);
        summaryFocusLine = text("", 14, COLOR_TEXT_SECONDARY, false);
        summaryFocusLine.setPadding(0, dp(10), 0, 0);
        notes.addView(summaryFocusLine);
        content.addView(notes);

        LinearLayout cardsSection = vertical(dp(10));
        cardsSection.addView(text("Added to your review deck", 13, COLOR_TEXT_SECONDARY, true));
        summaryCardsList = vertical(0);
        summaryCardsList.setPadding(dp(14), dp(4), dp(14), dp(4));
        summaryCardsList.setBackground(rounded(COLOR_AMBER_BG, dp(16), dp(1), COLOR_AMBER_BORDER));
        cardsSection.addView(summaryCardsList, matchWrap());
        content.addView(cardsSection);

        View done = primaryAction("Done", null, false);
        done.setOnClickListener(v -> exitSession());
        content.addView(done, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)));
        TextView transcriptLink = text("View full transcript", 15, Color.rgb(199, 210, 254), true);
        transcriptLink.setGravity(Gravity.CENTER);
        transcriptLink.setMinHeight(dp(44));
        transcriptLink.setClickable(true);
        transcriptLink.setFocusable(true);
        transcriptLink.setOnClickListener(v -> {
            sessionReadOnly = true;
            showScreen(SCREEN_SESSION);
            scrollChatToBottom();
        });
        content.addView(transcriptLink, matchWrap());
        return scroll;
    }

    private TextView statTile(LinearLayout row, String label, int valueColor, boolean margin) {
        LinearLayout tile = card(dp(16), 0);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(12), dp(14), dp(12), dp(14));
        TextView value = heading("0", 26);
        value.setTextColor(valueColor);
        tile.addView(value);
        tile.addView(text(label, 12, COLOR_TEXT_SECONDARY, false));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        if (margin) params.setMargins(dp(10), 0, 0, 0);
        row.addView(tile, params);
        return value;
    }

    private void populateSummary(int minutes) {
        summarySubtitle.setText(sessionFocusName() + " · " + minutes + " min · saved automatically");
        summaryAnswers.setText(String.valueOf(sessionAnswers));
        summaryCards.setText(String.valueOf(sessionNewCards.size()));
        summaryReviews.setText(sessionReviewsTotal == 0 ? "–" : sessionReviewsRight + "/" + sessionReviewsTotal);

        String notes = sessionClosingNotes.trim();
        summaryNotes.setText(notes.isEmpty()
                ? "Finish the Wrap-up step next time to get your coach's notes on this session."
                : notes);

        java.util.LinkedHashMap<String, Integer> categories = new java.util.LinkedHashMap<>();
        for (JSONObject card : sessionNewCards) {
            String category = card.optString("category", "other");
            categories.put(category, categories.getOrDefault(category, 0) + 1);
        }
        String topCategory = null;
        int topCount = 0;
        for (java.util.Map.Entry<String, Integer> entry : categories.entrySet()) {
            if (entry.getValue() > topCount) {
                topCategory = entry.getKey();
                topCount = entry.getValue();
            }
        }
        if (topCategory != null) {
            summaryFocusLine.setText("Most corrections today: " + topCategory + " (" + topCount + ")");
            summaryFocusLine.setVisibility(View.VISIBLE);
        } else {
            summaryFocusLine.setText("No corrections today. Nice work.");
            summaryFocusLine.setVisibility(View.VISIBLE);
        }

        summaryCardsList.removeAllViews();
        if (sessionNewCards.isEmpty()) {
            TextView none = text("No new cards this time.", 14, COLOR_AMBER_MUTED, false);
            none.setPadding(0, dp(10), 0, dp(10));
            summaryCardsList.addView(none);
        }
        for (int i = 0; i < sessionNewCards.size() && i < 8; i++) {
            JSONObject card = sessionNewCards.get(i);
            LinearLayout row = vertical(dp(2));
            row.setPadding(0, dp(10), 0, dp(10));
            row.addView(text(card.optString("corrected"), 15, COLOR_TEXT_PRIMARY, true));
            row.addView(text(card.optString("category", "other") + " · due tomorrow", 13, COLOR_AMBER_MUTED, false));
            summaryCardsList.addView(row);
            if (i < sessionNewCards.size() - 1 && i < 7) {
                View line = new View(this);
                line.setBackgroundColor(Color.rgb(58, 46, 12));
                summaryCardsList.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
            }
        }
    }

    // =======================================================================================
    // Streak and practice time
    // =======================================================================================

    private void recordPracticeDay(int minutes) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        try {
            JSONArray days = new JSONArray(prefs.getString(PREF_PRACTICE_DAYS, "[]"));
            String today = LocalDate.now().toString();
            boolean present = false;
            for (int i = 0; i < days.length(); i++) {
                if (today.equals(days.optString(i))) present = true;
            }
            if (!present) days.put(today);
            while (days.length() > 400) days.remove(0);
            prefs.edit()
                    .putString(PREF_PRACTICE_DAYS, days.toString())
                    .putInt(PREF_MINUTES_TOTAL, prefs.getInt(PREF_MINUTES_TOTAL, 0) + minutes)
                    .apply();
        } catch (Exception ignored) {
            // corrupt pref: start over next time
            prefs.edit().remove(PREF_PRACTICE_DAYS).apply();
        }
    }

    /** Consecutive practice days ending today (or yesterday, so the streak survives until tonight). */
    private int currentStreak() {
        java.util.HashSet<String> days = new java.util.HashSet<>();
        try {
            JSONArray stored = new JSONArray(getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_PRACTICE_DAYS, "[]"));
            for (int i = 0; i < stored.length(); i++) days.add(stored.optString(i));
        } catch (Exception ignored) {
            return 0;
        }
        LocalDate day = LocalDate.now();
        if (!days.contains(day.toString())) day = day.minusDays(1);
        int streak = 0;
        while (days.contains(day.toString())) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }

    // =======================================================================================
    // Review tab: practise due cards any time, without the AI
    // =======================================================================================

    private View buildReviewTab() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = vertical(dp(18));
        content.setPadding(dp(20), dp(24), dp(20), dp(32));
        scroll.addView(content);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.BOTTOM);
        LinearLayout titles = vertical(dp(4));
        reviewCountsView = text("", 14, COLOR_TEXT_SECONDARY, false);
        titles.addView(reviewCountsView);
        titles.addView(heading("Review", 30));
        header.addView(titles, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        reviewDueView = text("", 14, COLOR_AMBER, true);
        header.addView(reviewDueView);
        content.addView(header);

        reviewProgressRow = new LinearLayout(this);
        reviewProgressRow.setBackground(rounded(COLOR_BAR_BORDER, dp(3), 0, 0));
        content.addView(reviewProgressRow, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(6)));

        reviewCardHost = vertical(0);
        content.addView(reviewCardHost, matchWrap());

        LinearLayout breakdown = vertical(dp(12));
        breakdown.addView(text("Where your mistakes come from", 13, COLOR_TEXT_SECONDARY, true));
        breakdownContainer = vertical(dp(10));
        breakdown.addView(breakdownContainer, matchWrap());
        content.addView(breakdown, matchWrap());
        return scroll;
    }

    private void startReviewRound() {
        if (reviewCardHost == null) return;
        // Keep an unfinished round when the learner returns to the tab
        if (!reviewRoundStarted || reviewIndex >= reviewQueue.size()) {
            reviewQueue.clear();
            reviewQueue.addAll(dueReviewItems(30));
            reviewIndex = 0;
            reviewRight = 0;
            reviewRevealed = false;
            reviewHeard = "";
            reviewRoundStarted = !reviewQueue.isEmpty();
        }
        renderReview();
    }

    private void renderReview() {
        JSONArray deck = loadReviewDeck();
        int mastered = 0;
        java.util.HashMap<String, Integer> categories = new java.util.HashMap<>();
        String nextDue = null;
        String today = LocalDate.now().toString();
        for (int i = 0; i < deck.length(); i++) {
            JSONObject item = deck.optJSONObject(i);
            if (item == null) continue;
            if (item.optInt("box", 0) >= 3) mastered++;
            String category = item.optString("category", "other");
            categories.put(category, categories.getOrDefault(category, 0) + 1);
            String due = item.optString("due", today);
            if (due.compareTo(today) > 0 && (nextDue == null || due.compareTo(nextDue) < 0)) nextDue = due;
        }
        reviewCountsView.setText(deck.length() + " cards · " + mastered + " mastered");
        int total = reviewQueue.size();
        reviewDueView.setText(total == 0 ? "" : Math.min(reviewIndex, total) + " of " + total + " done");

        reviewProgressRow.removeAllViews();
        if (total > 0) {
            View fill = new View(this);
            fill.setBackground(rounded(COLOR_AMBER, dp(3), 0, 0));
            reviewProgressRow.addView(fill, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, Math.max(0.001f, reviewIndex)));
            reviewProgressRow.addView(new View(this), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, Math.max(0.001f, total - reviewIndex)));
        }

        reviewCardHost.removeAllViews();
        reviewLiveView = null;
        LinearLayout card = card(dp(24), dp(20));
        card.setOrientation(LinearLayout.VERTICAL);
        reviewCardHost.addView(card, matchWrap());

        if (reviewIndex >= total) {
            String headline;
            String detail;
            if (deck.length() == 0) {
                headline = "No cards yet";
                detail = "When the coach corrects you in a session, the correction becomes a card here.";
            } else if (total > 0) {
                headline = "Round complete: " + reviewRight + " of " + total + " right";
                detail = "Cards you missed come back tomorrow." + (nextDue != null ? " Next review: " + friendlyDate(nextDue) + "." : "");
            } else {
                headline = "All caught up";
                detail = nextDue != null ? "Next review: " + friendlyDate(nextDue) + "." : "Nothing is due right now.";
            }
            card.addView(heading(headline, 22));
            TextView detailView = text(detail, 15, COLOR_TEXT_SECONDARY, false);
            detailView.setPadding(0, dp(8), 0, 0);
            card.addView(detailView);
            reviewRoundStarted = false;
        } else {
            JSONObject item = reviewQueue.get(reviewIndex);
            String original = item.optString("original").trim();
            String corrected = item.optString("corrected").trim();

            TextView pill = text(item.optString("category", "other"), 12, Color.rgb(199, 210, 254), true);
            pill.setPadding(dp(10), dp(4), dp(10), dp(4));
            pill.setBackground(rounded(Color.rgb(35, 42, 82), dp(999), 0, 0));
            card.addView(pill, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView instruction = text(original.isEmpty() ? "Say this naturally" : "Fix this sentence", 13, COLOR_TEXT_SECONDARY, false);
            instruction.setPadding(0, dp(16), 0, dp(6));
            card.addView(instruction);
            TextView prompt = heading(original.isEmpty() ? item.optString("explanation") : "\"" + original + "\"", 22);
            prompt.setTypeface(font(FONT_HEADING_MEDIUM));
            card.addView(prompt);

            reviewLiveView = text(reviewHeard.isEmpty() ? "" : "You said: \"" + reviewHeard + "\"", 15, COLOR_TEXT_PRIMARY, false);
            reviewLiveView.setPadding(0, dp(14), 0, 0);
            reviewLiveView.setVisibility(reviewHeard.isEmpty() && !isListening ? View.GONE : View.VISIBLE);
            card.addView(reviewLiveView);

            if (reviewRevealed) {
                View line = new View(this);
                line.setBackgroundColor(COLOR_CHIP_BORDER);
                LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
                lineParams.setMargins(0, dp(16), 0, dp(14));
                card.addView(line, lineParams);
                card.addView(text("Natural version", 12, COLOR_TEXT_FAINT, false));
                TextView answer = text(corrected, 18, Color.WHITE, true);
                answer.setPadding(0, dp(4), 0, 0);
                card.addView(answer);
                String why = item.optString("explanation").trim();
                if (!why.isEmpty() && !original.isEmpty()) {
                    TextView whyView = text(why, 13, COLOR_TEXT_SECONDARY, false);
                    whyView.setPadding(0, dp(4), 0, 0);
                    card.addView(whyView);
                }
                if (!reviewHeard.isEmpty()) {
                    boolean match = similarity(reviewHeard, corrected) >= 0.85;
                    TextView verdict = text(match ? "That matches the natural version." : "Compare your answer with the natural version.",
                            14, match ? COLOR_SUCCESS : COLOR_STRIKE, true);
                    verdict.setPadding(0, dp(10), 0, 0);
                    card.addView(verdict);
                }
                LinearLayout buttons = twoButtons(card);
                View again = outlinedAction("Again", null);
                again.setOnClickListener(v -> gradeCurrentCard(false));
                View gotIt = primaryAction("I got it", LineIcon.Type.CHECK, false);
                gotIt.setOnClickListener(v -> gradeCurrentCard(true));
                buttons.addView(again, new LinearLayout.LayoutParams(0, dp(52), 1));
                LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(52), 1);
                right.setMargins(dp(10), 0, 0, 0);
                buttons.addView(gotIt, right);
            } else {
                LinearLayout buttons = twoButtons(card);
                View say = primaryAction(isListening ? "Stop" : "Say it", isListening ? LineIcon.Type.STOP : LineIcon.Type.MIC, false);
                say.setOnClickListener(v -> toggleMic(SPEECH_REVIEW));
                View show = outlinedAction("Show answer", null);
                show.setOnClickListener(v -> {
                    if (isListening) cancelListening();
                    reviewRevealed = true;
                    renderReview();
                });
                buttons.addView(say, new LinearLayout.LayoutParams(0, dp(52), 1));
                LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(52), 1);
                right.setMargins(dp(10), 0, 0, 0);
                buttons.addView(show, right);
            }
        }

        // Breakdown bars, largest first
        breakdownContainer.removeAllViews();
        ArrayList<java.util.Map.Entry<String, Integer>> entries = new ArrayList<>(categories.entrySet());
        entries.sort((a, b) -> b.getValue() - a.getValue());
        int max = entries.isEmpty() ? 1 : entries.get(0).getValue();
        if (entries.isEmpty()) {
            breakdownContainer.addView(text("This fills in as you collect corrections.", 14, COLOR_TEXT_FAINT, false));
        }
        for (int i = 0; i < entries.size() && i < 6; i++) {
            java.util.Map.Entry<String, Integer> entry = entries.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = text(capitalize(entry.getKey()), 13, Color.rgb(209, 213, 219), false);
            row.addView(name, new LinearLayout.LayoutParams(dp(104), LinearLayout.LayoutParams.WRAP_CONTENT));
            LinearLayout track = new LinearLayout(this);
            track.setBackground(rounded(COLOR_BAR_BORDER, dp(5), 0, 0));
            View fill = new View(this);
            fill.setBackground(rounded(COLOR_ACCENT_SOFT, dp(5), 0, 0));
            track.addView(fill, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, entry.getValue()));
            track.addView(new View(this), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, Math.max(0.001f, max - entry.getValue())));
            row.addView(track, new LinearLayout.LayoutParams(0, dp(10), 1));
            TextView count = text(String.valueOf(entry.getValue()), 13, COLOR_TEXT_SECONDARY, false);
            count.setGravity(Gravity.END);
            row.addView(count, new LinearLayout.LayoutParams(dp(32), LinearLayout.LayoutParams.WRAP_CONTENT));
            breakdownContainer.addView(row, matchWrap());
        }
    }

    private LinearLayout twoButtons(LinearLayout card) {
        LinearLayout buttons = new LinearLayout(this);
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(18), 0, 0);
        card.addView(buttons, params);
        return buttons;
    }

    private void onReviewHeard(String heard) {
        reviewHeard = heard;
        reviewRevealed = true; // hearing an attempt reveals the answer for comparison
        renderReview();
    }

    private void gradeCurrentCard(boolean correct) {
        if (reviewIndex >= reviewQueue.size()) return;
        String id = reviewQueue.get(reviewIndex).optString("id");
        JSONArray deck = loadReviewDeck();
        JSONObject item = findDeckItem(deck, "id", id);
        if (item != null) {
            gradeItem(item, correct);
            saveReviewDeck(deck);
        }
        if (correct) reviewRight++;
        reviewIndex++;
        reviewRevealed = false;
        reviewHeard = "";
        renderReview();
    }

    private String friendlyDate(String isoDate) {
        try {
            LocalDate date = LocalDate.parse(isoDate);
            long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), date);
            if (days == 1) return "tomorrow";
            if (days > 1 && days < 7) return "in " + days + " days";
            return date.format(java.time.format.DateTimeFormatter.ofPattern("MMM d", Locale.US));
        } catch (Exception exception) {
            return isoDate;
        }
    }

    /** 0..1 similarity between two sentences, ignoring case and punctuation (Levenshtein-based). */
    private double similarity(String a, String b) {
        String x = normalizeForMatch(a);
        String y = normalizeForMatch(b);
        if (x.isEmpty() || y.isEmpty()) return 0;
        int[] prev = new int[y.length() + 1];
        int[] cur = new int[y.length() + 1];
        for (int j = 0; j <= y.length(); j++) prev[j] = j;
        for (int i = 1; i <= x.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= y.length(); j++) {
                int cost = x.charAt(i - 1) == y.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev; prev = cur; cur = t;
        }
        return 1.0 - (double) prev[y.length()] / Math.max(x.length(), y.length());
    }

    private static final String[] NUMBER_WORDS = {"zero", "one", "two", "three", "four", "five", "six",
            "seven", "eight", "nine", "ten", "eleven", "twelve"};

    /** Lower-case, no punctuation, and small numbers as words (speech recognition writes "3" for "three"). */
    private String normalizeForMatch(String value) {
        String s = value.toLowerCase(Locale.US).replaceAll("[^a-z0-9' ]", " ").replaceAll("\\s+", " ").trim();
        StringBuilder out = new StringBuilder();
        for (String word : s.split(" ")) {
            if (word.matches("\\d{1,2}") && Integer.parseInt(word) < NUMBER_WORDS.length) {
                word = NUMBER_WORDS[Integer.parseInt(word)];
            }
            if (out.length() > 0) out.append(' ');
            out.append(word);
        }
        return out.toString();
    }

    private String capitalize(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    // =======================================================================================
    // Voice: TTS, hands-free loop, speech recognition
    // =======================================================================================

    private void speakCoach(String text, boolean thenListen) {
        if (tts == null || text == null) {
            if (thenListen) autoListen();
            return;
        }
        // Strip markdown so the voice doesn't read out asterisks and hashes
        String spoken = text.replaceAll("[*#_`>]", "").replaceAll("\\s+", " ").trim();
        if (spoken.isEmpty()) {
            if (thenListen) autoListen();
            return;
        }
        if (thenListen) setVoiceState(VOICE_SPEAKING, null);
        tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, thenListen ? UTTERANCE_AUTO_LISTEN : UTTERANCE_MANUAL);
    }

    /** Opens the mic if the session is waiting for an answer. Never loops after a failed recognition. */
    private void autoListen() {
        if (!voiceMode || !sessionActive || sessionReadOnly || isListening || isFinishing() || busy) return;
        if (voicePaused) {
            setVoiceState(VOICE_PAUSED, null);
            return;
        }
        if (userResponseCount >= MAX_RESPONSES_PER_STEP) {
            setVoiceState(VOICE_IDLE, "Step complete. Tap " + nextLabelView.getText() + " to continue.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setVoiceState(VOICE_IDLE, "Allow microphone access, then tap Speak.");
            return;
        }
        startListening(SPEECH_SESSION);
    }

    private void setVoiceMode(boolean enabled) {
        voiceMode = enabled;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_VOICE_MODE, enabled).apply();
        if (voiceSwitch != null && voiceSwitch.isChecked() != enabled) voiceSwitch.setChecked(enabled);
        if (enabled) {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 100);
            }
            if (sessionActive && !busy) setVoiceState(VOICE_IDLE, null);
        } else {
            stopVoiceOutput();
        }
        if (currentScreen == SCREEN_SESSION) updateSessionChrome();
    }

    private void setVoiceState(int state, String customLabel) {
        voiceState = state;
        if (voiceStateView == null) return;
        String label;
        String action;
        int orbColor = COLOR_RAISED;
        LineIcon.Type icon = LineIcon.Type.MIC;
        switch (state) {
            case VOICE_SPEAKING:
                label = "Coach is speaking…";
                action = "Skip and answer";
                icon = LineIcon.Type.SPEAKER;
                break;
            case VOICE_LISTENING:
                label = "Listening…";
                action = "I'm done, send it";
                orbColor = COLOR_ACCENT;
                break;
            case VOICE_THINKING:
                label = "Coach is thinking…";
                action = "Thinking…";
                break;
            case VOICE_PAUSED:
                label = "Paused";
                action = "Resume";
                icon = LineIcon.Type.PAUSE;
                break;
            default:
                label = "Tap Speak to answer";
                action = "Speak";
                break;
        }
        voiceStateView.setText(customLabel != null ? customLabel : label);
        voicePrimaryLabel.setText(action);
        setActionEnabled(voicePrimaryButton, state != VOICE_THINKING);
        voiceOrbInner.setBackground(circleDrawable(orbColor, 0, 0));
        voiceOrbIcon.setType(icon);
    }

    private void onVoicePrimary() {
        switch (voiceState) {
            case VOICE_SPEAKING:
                if (tts != null) tts.stop();
                voicePaused = false;
                toggleMic(SPEECH_SESSION);
                break;
            case VOICE_LISTENING:
                stopListening(); // final results arrive and are sent automatically
                break;
            case VOICE_THINKING:
                break;
            default:
                voicePaused = false;
                if (userResponseCount >= MAX_RESPONSES_PER_STEP) {
                    setVoiceState(VOICE_IDLE, "Step complete. Tap " + nextLabelView.getText() + " to continue.");
                } else {
                    toggleMic(SPEECH_SESSION);
                }
                break;
        }
    }

    private void pauseVoice() {
        voicePaused = true;
        stopVoiceOutput();
        setVoiceState(VOICE_PAUSED, null);
    }

    private void initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return;
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                if (speechTarget == SPEECH_SESSION) setVoiceState(VOICE_LISTENING, null);
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int error) {
                isListening = false;
                updateMicUi();
                // Many recognizers show live words, then end with "no match" (or a client error
                // after stopListening). Keep what was already heard instead of discarding it.
                if (!lastPartial.isEmpty()) {
                    handleHeard(lastPartial);
                    return;
                }
                boolean noSpeech = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
                String message = noSpeech ? "Didn't catch that. Tap Speak to try again." : "Microphone error (" + error + "). Tap Speak to try again.";
                if (speechTarget == SPEECH_REVIEW) {
                    renderReview();
                    if (reviewLiveView != null) {
                        reviewLiveView.setText(noSpeech ? "Didn't catch that. Try again or show the answer." : message);
                        reviewLiveView.setVisibility(View.VISIBLE);
                    }
                } else {
                    composerHint.setText(noSpeech ? "Didn't catch that. Tap the mic to try again." : message);
                    setVoiceState(VOICE_IDLE, message);
                }
            }
            @Override public void onResults(Bundle results) {
                isListening = false;
                updateMicUi();
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String heard = (matches != null && !matches.isEmpty() && matches.get(0) != null) ? matches.get(0).trim() : "";
                // Some recognizers return an empty final result after showing partial words.
                if (heard.isEmpty()) heard = lastPartial;
                handleHeard(heard);
            }
            @Override public void onPartialResults(Bundle partialResults) {
                ArrayList<String> partial = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (partial == null || partial.isEmpty() || partial.get(0) == null || partial.get(0).trim().isEmpty()) return;
                String words = partial.get(0);
                lastPartial = words.trim();
                if (speechTarget == SPEECH_REVIEW) {
                    if (reviewLiveView != null) {
                        reviewLiveView.setText("You said: \"" + words + "\"");
                        reviewLiveView.setVisibility(View.VISIBLE);
                    }
                } else {
                    userInput.setText(words);
                    voiceLiveView.setText("\"" + words + "\"");
                }
            }
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    /** Handles a finished recognition: sends it in hands-free mode, or leaves it in the box. */
    private void handleHeard(String heard) {
        lastPartial = "";
        if (speechTarget == SPEECH_REVIEW) {
            if (!heard.isEmpty()) onReviewHeard(heard);
            else renderReview();
            return;
        }
        if (!heard.isEmpty()) userInput.setText(heard);
        if (voiceMode && sessionActive && !heard.isEmpty()) {
            sendCurrentInput(); // hands-free: send as soon as the learner stops talking
        } else {
            setVoiceState(VOICE_IDLE, heard.isEmpty() ? "Didn't catch that. Tap Speak to try again." : null);
            updateComposerHint();
        }
    }

    private void toggleMic(int target) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 100);
            return;
        }
        if (isListening) {
            stopListening();
        } else {
            startListening(target);
        }
    }

    private void startListening(int target) {
        if (speechRecognizer == null) {
            String message = "Speech recognition is not available on this device. Please type instead.";
            if (target == SPEECH_SESSION) {
                composerHint.setText(message);
                setVoiceState(VOICE_IDLE, message);
            } else if (reviewLiveView != null) {
                reviewLiveView.setText(message);
                reviewLiveView.setVisibility(View.VISIBLE);
            }
            return;
        }
        speechTarget = target;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        // The learner practises English, so recognise English even on a Spanish-language phone.
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        // Give a learner time to think mid-sentence (some recognizers ignore these hints)
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000);
        if (tts != null) tts.stop(); // don't let the coach's voice be transcribed
        if (target == SPEECH_SESSION && voiceMode) {
            userInput.setText("");
            voiceLiveView.setText("");
        }
        lastPartial = "";
        isListening = true;
        speechRecognizer.startListening(intent);
        updateMicUi();
        if (target == SPEECH_REVIEW) renderReview();
    }

    /** Stops listening; the final result still arrives (and is sent in voice mode). */
    private void stopListening() {
        isListening = false;
        if (speechRecognizer != null) speechRecognizer.stopListening();
        updateMicUi();
    }

    /** Stops listening and discards what was heard. */
    private void cancelListening() {
        isListening = false;
        lastPartial = "";
        if (speechRecognizer != null) speechRecognizer.cancel();
        updateMicUi();
    }

    private void updateMicUi() {
        if (micButton == null) return;
        boolean sessionMic = isListening && speechTarget == SPEECH_SESSION;
        micButton.setBackground(rounded(sessionMic ? COLOR_DANGER : COLOR_RAISED, dp(16), 0, 0));
        micIcon.setType(sessionMic ? LineIcon.Type.STOP : LineIcon.Type.MIC);
        micButton.setContentDescription(sessionMic ? "Stop recording" : "Speak your answer");
    }

    // =======================================================================================
    // Small view helpers
    // =======================================================================================

    private LinearLayout vertical(int gap) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        if (gap > 0) {
            GradientDrawable spacer = new GradientDrawable();
            spacer.setSize(1, gap);
            spacer.setColor(Color.TRANSPARENT);
            layout.setDividerDrawable(spacer);
            layout.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        }
        return layout;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private TextView text(String value, float sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(font(bold ? FONT_SEMIBOLD : FONT_REGULAR));
        return view;
    }

    /** Space Grotesk headings: bold for screen titles, semibold for card and step titles. */
    private TextView heading(String value, float sp) {
        TextView view = text(value, sp, COLOR_TEXT_PRIMARY, false);
        view.setTypeface(font(sp >= 26 ? FONT_DISPLAY : FONT_HEADING));
        letterSpacing(view, sp >= 26 ? -0.02f : -0.01f);
        return view;
    }

    // Bundled variable fonts (SIL OFL, licenses in assets/fonts): Inter for text, Space Grotesk for headings.
    private static final int FONT_REGULAR = 0, FONT_MEDIUM = 1, FONT_SEMIBOLD = 2,
            FONT_HEADING_MEDIUM = 3, FONT_HEADING = 4, FONT_DISPLAY = 5;
    private final Typeface[] fonts = new Typeface[6];

    private void loadFonts() {
        fonts[FONT_REGULAR] = variableFont("fonts/Inter.ttf", 400, Typeface.create("sans-serif", Typeface.NORMAL));
        fonts[FONT_MEDIUM] = variableFont("fonts/Inter.ttf", 500, font(FONT_MEDIUM));
        fonts[FONT_SEMIBOLD] = variableFont("fonts/Inter.ttf", 600, font(FONT_SEMIBOLD));
        fonts[FONT_HEADING_MEDIUM] = variableFont("fonts/SpaceGrotesk.ttf", 500, fonts[FONT_MEDIUM]);
        fonts[FONT_HEADING] = variableFont("fonts/SpaceGrotesk.ttf", 600, fonts[FONT_SEMIBOLD]);
        fonts[FONT_DISPLAY] = variableFont("fonts/SpaceGrotesk.ttf", 700, fonts[FONT_SEMIBOLD]);
    }

    /** One weight of a variable font from assets; falls back to a system font if it can't load. */
    private Typeface variableFont(String asset, int weight, Typeface fallback) {
        try {
            Typeface typeface = new Typeface.Builder(getAssets(), asset)
                    .setFontVariationSettings("'wght' " + weight)
                    .setWeight(weight)
                    .build();
            return typeface != null ? typeface : fallback;
        } catch (Exception exception) {
            return fallback;
        }
    }

    private Typeface font(int which) {
        Typeface typeface = fonts[which];
        return typeface != null ? typeface : Typeface.DEFAULT;
    }

    private void letterSpacing(TextView view, float em) {
        view.setLetterSpacing(em);
    }

    private GradientDrawable rounded(int fill, int radius, int strokeWidth, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private LinearLayout card(int radius, int padding) {
        LinearLayout card = new LinearLayout(this);
        card.setPadding(padding, padding, padding, padding);
        card.setBackground(rounded(COLOR_BG_CARD, radius, dp(1), COLOR_BORDER_CARD));
        return card;
    }

    /** Filled primary button with optional trailing icon. */
    private View primaryAction(String label, LineIcon.Type icon, boolean trailingIcon) {
        LinearLayout button = new LinearLayout(this);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(COLOR_ACCENT, dp(16), 0, 0));
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription(label);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        if (icon != null && !trailingIcon) {
            iconParams.setMargins(0, 0, dp(8), 0);
            button.addView(new LineIcon(this, icon, Color.WHITE), iconParams);
        }
        button.addView(text(label, 16, Color.WHITE, true));
        if (icon != null && trailingIcon) {
            iconParams.setMargins(dp(10), 0, 0, 0);
            button.addView(new LineIcon(this, icon, Color.WHITE), iconParams);
        }
        return button;
    }

    private View outlinedAction(String label, LineIcon.Type icon) {
        LinearLayout button = new LinearLayout(this);
        button.setGravity(Gravity.CENTER);
        button.setBackground(rounded(Color.TRANSPARENT, dp(14), dp(1), COLOR_CHIP_BORDER));
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription(label);
        if (icon != null) {
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(18), dp(18));
            iconParams.setMargins(0, 0, dp(8), 0);
            button.addView(new LineIcon(this, icon, Color.rgb(209, 213, 219)), iconParams);
        }
        button.addView(text(label, 15, Color.rgb(209, 213, 219), false));
        return button;
    }

    private FrameLayout iconButton(LineIcon.Type icon, String description, int fill, int iconColor, int radius, int iconDp) {
        FrameLayout button = new FrameLayout(this);
        button.setBackground(rounded(fill, radius, 0, 0));
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription(description);
        button.addView(new LineIcon(this, icon, iconColor), new FrameLayout.LayoutParams(dp(iconDp), dp(iconDp), Gravity.CENTER));
        return button;
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(20), dp(20), dp(20));
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(COLOR_BG_CARD);
        drawable.setCornerRadius(dp(16));
        drawable.setStroke(dp(1), COLOR_BORDER_CARD);
        panel.setBackground(drawable);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(20));
        panel.setLayoutParams(params);
        return panel;
    }

    private LinearLayout horizontal() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(4), 0, 0);
        return row;
    }

    private TextView sectionHeader(String text) {
        TextView label = new TextView(this);
        label.setText(text.toUpperCase(Locale.US));
        label.setTextColor(COLOR_TEXT_SECONDARY);
        label.setTextSize(12);
        label.setTypeface(font(FONT_SEMIBOLD));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            label.setLetterSpacing(0.12f);
        }
        label.setPadding(dp(4), dp(32), 0, dp(12));
        return label;
    }

    private TextView label(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(COLOR_TEXT_PRIMARY);
        label.setTextSize(14);
        label.setTypeface(font(FONT_SEMIBOLD));
        label.setPadding(0, dp(8), 0, dp(4));
        return label;
    }

    private TextView hintText(String text) {
        TextView hint = new TextView(this);
        hint.setText(text);
        hint.setTextColor(COLOR_TEXT_SECONDARY);
        hint.setTextSize(12);
        hint.setPadding(0, dp(4), 0, dp(8));
        return hint;
    }

    private EditText field(String hint) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setHint(hint);
        field.setHintTextColor(COLOR_TEXT_SECONDARY);
        field.setTextColor(COLOR_TEXT_PRIMARY);
        field.setTextSize(16);
        field.setTypeface(font(FONT_REGULAR));
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        field.setPadding(dp(12), 0, dp(12), 0);
        field.setBackground(inputBackground());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        field.setLayoutParams(params);
        return field;
    }

    private EditText multiLineField(String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setHintTextColor(COLOR_TEXT_SECONDARY);
        field.setMinLines(4);
        field.setGravity(Gravity.TOP | Gravity.START);
        field.setTextSize(16);
        field.setTypeface(font(FONT_REGULAR));
        field.setTextColor(COLOR_TEXT_PRIMARY);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        field.setPadding(dp(12), dp(12), dp(12), dp(12));
        field.setBackground(inputBackground());
        return field;
    }

    private GradientDrawable inputBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(COLOR_INPUT_BG);
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(1), Color.rgb(51, 65, 85));
        return drawable;
    }

    private Spinner spinner(List<String> values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item,
                values
        ) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(COLOR_TEXT_PRIMARY);
                    ((TextView) v).setTextSize(16);
                }
                return v;
            }
            @Override
            public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                View v = super.getDropDownView(position, convertView, parent);
                v.setBackgroundColor(COLOR_BG_CARD);
                if (v instanceof TextView) {
                    ((TextView) v).setTextColor(COLOR_TEXT_PRIMARY);
                    ((TextView) v).setTextSize(16);
                }
                return v;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        ));
        spinner.setPadding(dp(8), 0, dp(8), 0);
        spinner.setBackground(inputBackground());
        return spinner;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTypeface(font(FONT_SEMIBOLD));
        button.setTextSize(16);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(COLOR_ACCENT);
        drawable.setCornerRadius(dp(12));
        button.setBackground(drawable);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setElevation(dp(4));
            button.setStateListAnimator(null);
        }
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(COLOR_ACCENT);
        button.setTypeface(font(FONT_MEDIUM));
        button.setTextSize(16);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.rgb(22, 28, 45)); // COLOR_BG_CARD
        drawable.setCornerRadius(dp(12));
        drawable.setStroke(dp(1.5f), COLOR_ACCENT);
        button.setBackground(drawable);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setElevation(0);
            button.setStateListAnimator(null);
        }
        return button;
    }

    private LinearLayout.LayoutParams weightParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(48), 1);
        params.setMargins(dp(4), dp(4), dp(4), dp(4));
        return params;
    }

    private LinearLayout.LayoutParams navBarParams() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
    }

    private GradientDrawable circleDrawable(int color, int strokeColor, int strokeWidthDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        if (strokeWidthDp > 0) {
            drawable.setStroke(dp(strokeWidthDp), strokeColor);
        }
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void loadPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String serviceType = prefs.getString(PREF_SERVICE_TYPE, "Ollama");
        int pos = "Gemini".equalsIgnoreCase(serviceType) ? 1 : 0;
        serviceTypeSpinner.setSelection(pos);

        String savedServer = prefs.getString(PREF_SERVER, defaultServerUrl());
        if (isEmulatorOnlyAddressOnPhysicalDevice(savedServer)) {
            savedServer = defaultServerUrl();
        }
        serverUrlField.setText(savedServer);
        apiKeyField.setText(prefs.getString(PREF_API_KEY, ""));
        modelField.setText(prefs.getString(PREF_MODEL, pos == 1 ? "gemini-3.5-flash" : DEFAULT_MODEL));
    }

    private void savePrefs() {
        String service = serviceTypeSpinner.getSelectedItemPosition() == 1 ? "Gemini" : "Ollama";
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(PREF_SERVICE_TYPE, service)
                .putString(PREF_SERVER, serverUrl())
                .putString(PREF_API_KEY, apiKeyField.getText().toString().trim())
                .putString(PREF_MODEL, model())
                .apply();
    }

    private String roleForToday() {
        DayOfWeek day = LocalDate.now().getDayOfWeek();
        switch (day) {
            case TUESDAY:
            case THURSDAY:
                return "Writing Studio";
            case WEDNESDAY:
                return "Input Immersion";
            case SUNDAY:
                return "Recall & Drill";
            case MONDAY:
            case FRIDAY:
            case SATURDAY:
            default:
                return "Speaking Lab";
        }
    }

    /** Asks the model for a structured reply so corrections can be saved to the review deck. */
    private String feedbackJsonInstructions(String step) {
        String reviewPart = step.contains("Recall")
                ? ",\n  \"review_results\": [{\"id\": \"review item id you just quizzed\", \"correct\": true}]"
                : "";
        return "\n\nRespond ONLY with a JSON object, no markdown:\n"
                + "{\n"
                + "  \"message\": \"what you say to the learner: brief feedback, the natural version, then your next question (max 60 words)\",\n"
                + "  \"corrections\": [{\"original\": \"the learner's exact wrong words\", \"corrected\": \"the natural C1 version\", "
                + "\"explanation\": \"why, max 12 words\", \"category\": \"grammar|vocabulary|collocation|word order|register|preposition\"}]"
                + reviewPart + "\n"
                + "}\n"
                + "Only list genuine errors or clearly unnatural phrasing (max 3). Use an empty array if the answer was correct."
                + (reviewPart.isEmpty() ? "" : " In review_results, grade only the review items the learner just answered.");
    }

    private void testConnection() {
        savePrefs();
        boolean isGemini = (serviceTypeSpinner != null && serviceTypeSpinner.getSelectedItemPosition() == 1);
        if (isGemini) {
            testGeminiConnection();
        } else {
            testOllamaConnection();
        }
    }

    private void testOllamaConnection() {
        String urlBase = trimSlash(serverUrl());
        if (urlBase.isEmpty()) {
            setStatus("Invalid URL");
            return;
        }

        setBusy(true);
        setStatus("Testing...");
        executor.execute(() -> {
            try {
                URL url = new URL(urlBase + "/api/tags");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);

                int code = connection.getResponseCode();
                mainHandler.post(() -> {
                    if (code == 200) {
                        setStatus("Connected");
                    } else {
                        setStatus("Failed (HTTP " + code + ")");
                    }
                    setBusy(false);
                });
            } catch (java.net.SocketTimeoutException timeout) {
                mainHandler.post(() -> {
                    setStatus("Timeout");
                    setBusy(false);
                });
            } catch (Exception exception) {
                mainHandler.post(() -> {
                    setStatus("Failed");
                    setBusy(false);
                });
            }
        });
    }

    private void testGeminiConnection() {
        String key = apiKeyField.getText().toString().trim();
        String modelName = model();
        if (key.isEmpty()) {
            setStatus("API Key required");
            return;
        }
        setBusy(true);
        setStatus("Testing Gemini...");
        executor.execute(() -> {
            try {
                URL url = new URL(GEMINI_BASE_URL + modelName + ":generateContent");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("x-goog-api-key", key);

                JSONObject payload = new JSONObject();
                JSONArray parts = new JSONArray().put(new JSONObject().put("text", "Ping"));
                JSONArray contents = new JSONArray().put(new JSONObject().put("parts", parts));
                payload.put("contents", contents);
                payload.put("generationConfig", new JSONObject().put("maxOutputTokens", 1024));

                try (OutputStream output = connection.getOutputStream();
                     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
                    writer.write(payload.toString());
                }

                int code = connection.getResponseCode();
                mainHandler.post(() -> {
                    if (code == 200) {
                        setStatus("Connected to Gemini");
                    } else {
                        setStatus("Failed (HTTP " + code + ")");
                    }
                    setBusy(false);
                });
            } catch (Exception exception) {
                mainHandler.post(() -> {
                    setStatus("Failed: " + exception.getMessage());
                    setBusy(false);
                });
            }
        });
    }

    private void sendOllama(String userPrompt, String step, boolean jsonMode) {
        boolean isGemini = (serviceTypeSpinner != null && serviceTypeSpinner.getSelectedItemPosition() == 1);
        
        if (!isGemini) {
            String setupIssue = ollamaSetupIssue();
            if (setupIssue != null) {
                appendSystem(setupIssue);
                setStatus("Update the Ollama server URL for this device.");
                showVoiceError(setupIssue);
                return;
            }
        }
        
        // Snapshot the conversation on the main thread, with the new user turn appended.
        // The turn is committed to history only after a successful reply, so a failed
        // request can be retried without duplicating it.
        final JSONObject userTurn;
        try {
            userTurn = new JSONObject().put("role", "user").put("content", userPrompt);
        } catch (Exception e) {
            appendSystem("Error: " + e.getMessage());
            return;
        }
        final List<JSONObject> messages = new ArrayList<>(conversation);
        messages.add(userTurn);
        final String systemPrompt = systemPromptForStep(step);
        final boolean closing = CLOSING_STEP.equals(step);
        final int geminiTokens = closing ? GEMINI_TOKENS_CLOSING : jsonMode ? GEMINI_TOKENS_FEEDBACK : GEMINI_TOKENS_DEFAULT;
        final int ollamaTokens = closing ? OLLAMA_TOKENS_CLOSING : jsonMode ? OLLAMA_TOKENS_FEEDBACK : OLLAMA_TOKENS_DEFAULT;

        setBusy(true);
        setStatus(isGemini ? "Contacting Gemini..." : "Contacting Ollama...");
        executor.execute(() -> {
            try {
                String response = isGemini
                        ? postGemini(systemPrompt, messages, geminiTokens, jsonMode)
                        : postChat(systemPrompt, messages, ollamaTokens, jsonMode);
                mainHandler.post(() -> {
                  String shown;
                  try {
                    conversation.add(userTurn);
                    try {
                        conversation.add(new JSONObject().put("role", "assistant").put("content", response));
                    } catch (Exception ignored) {
                        // JSONObject.put only throws for null keys / non-finite numbers.
                    }
                    JSONObject structured = jsonMode ? parseCoachJson(response) : null;
                    if (structured != null) {
                        shown = appendAssistant(step, structured.optString("message", "").trim());
                        JSONArray corrections = structured.optJSONArray("corrections");
                        if (corrections != null && corrections.length() > 0) {
                            appendCorrections(corrections);
                            addToReviewDeck(corrections);
                        }
                        JSONArray reviews = structured.optJSONArray("review_results");
                        if (reviews != null && reviews.length() > 0) {
                            applyReviewResults(reviews);
                        }
                    } else {
                        shown = appendAssistant(step, response); // plain-text step, or the model ignored the JSON format
                    }
                  } catch (Exception displayError) {
                    // Never leave the screen stuck on "thinking" if showing the reply fails
                    setBusy(false);
                    appendSystem("Error showing the coach's reply: " + displayError.getMessage());
                    showVoiceError("Couldn't show the coach's reply.");
                    return;
                  }
                    setStatus("Ready.");
                    setBusy(false);
                    if (voiceMode && sessionActive && !sessionReadOnly) {
                        speakCoach(shown, true); // hands-free: speak, then open the mic when done
                    }
                });
            } catch (Exception exception) {
                mainHandler.post(() -> {
                    String error = isGemini ? exception.getMessage() : ollamaConnectionErrorMessage(exception);
                    appendSystem("Error: " + error);
                    setStatus(isGemini ? "Check API key and network." : ollamaConnectionStatusMessage());
                    setBusy(false);
                    showVoiceError("Couldn't reach the coach: " + error);
                });
            }
        });
    }

    /** Backup model used when the chosen one is overloaded. */
    private static final String GEMINI_FALLBACK_MODEL = "gemini-2.5-flash";

    /** An HTTP error from Gemini, keeping the status code so we can decide whether to retry. */
    private static class GeminiHttpException extends IllegalStateException {
        final int code;
        GeminiHttpException(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    /**
     * Calls Gemini, retrying when Google is temporarily overloaded (500/503/504) and
     * falling back to a second model if the chosen one stays busy or hits its rate limit (429).
     * Runs on the background thread.
     */
    private String postGemini(String systemPrompt, List<JSONObject> history, int maxTokens, boolean jsonMode) throws Exception {
        String chosen = model();
        List<String> models = new ArrayList<>();
        models.add(chosen);
        if (!GEMINI_FALLBACK_MODEL.equals(chosen)) models.add(GEMINI_FALLBACK_MODEL);

        final long[] waitsMillis = {1500, 3000};
        GeminiHttpException last = null;
        for (String modelName : models) {
            for (int attempt = 0; attempt <= waitsMillis.length; attempt++) {
                try {
                    return postGeminiOnce(modelName, systemPrompt, history, maxTokens, jsonMode);
                } catch (GeminiHttpException e) {
                    last = e;
                    boolean overloaded = e.code == 500 || e.code == 503 || e.code == 504;
                    if (e.code == 429) break; // rate limit: retrying the same model won't help soon
                    if (!overloaded) throw e; // bad key, bad model name, etc.
                    if (attempt == waitsMillis.length) break;
                    final String note = "Google is busy, retrying…";
                    mainHandler.post(() -> {
                        setStatus(note);
                        if (voiceState == VOICE_THINKING) setVoiceState(VOICE_THINKING, note);
                    });
                    Thread.sleep(waitsMillis[attempt]);
                }
            }
            if (models.indexOf(modelName) < models.size() - 1) {
                final String note = "Still busy, trying a backup model…";
                mainHandler.post(() -> {
                    setStatus(note);
                    if (voiceState == VOICE_THINKING) setVoiceState(VOICE_THINKING, note);
                });
            }
        }
        throw last;
    }

    private String postGeminiOnce(String modelName, String systemPrompt, List<JSONObject> history, int maxTokens, boolean jsonMode) throws Exception {
        String key = apiKeyField.getText().toString().trim();
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Gemini API Key is empty. Please set it in Settings.");
        }

        URL url = new URL(GEMINI_BASE_URL + modelName + ":generateContent");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(120000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("x-goog-api-key", key);

        JSONObject payload = new JSONObject();
        
        // System instruction
        JSONObject systemInstruction = new JSONObject();
        JSONArray sysParts = new JSONArray().put(new JSONObject().put("text", systemPrompt));
        systemInstruction.put("parts", sysParts);
        payload.put("systemInstruction", systemInstruction);

        // Contents: the whole session so far (Gemini calls the assistant role "model")
        JSONArray contents = new JSONArray();
        for (JSONObject turn : history) {
            String role = "assistant".equals(turn.optString("role")) ? "model" : "user";
            JSONArray parts = new JSONArray().put(new JSONObject().put("text", turn.optString("content")));
            contents.put(new JSONObject().put("role", role).put("parts", parts));
        }
        payload.put("contents", contents);

        // Options
        JSONObject config = new JSONObject();
        config.put("temperature", 0.4);
        config.put("topP", 0.8);
        config.put("maxOutputTokens", maxTokens);
        if (jsonMode) config.put("responseMimeType", "application/json");
        payload.put("generationConfig", config);

        try (OutputStream output = connection.getOutputStream();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
            writer.write(payload.toString());
        }

        int code = connection.getResponseCode();
        InputStream input = code >= 200 && code < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String body = readStream(input);
        if (code < 200 || code >= 300) {
            // Show Google's short explanation (bad key, quota, unknown model) instead of raw JSON
            String reason = body;
            try {
                JSONObject err = new JSONObject(body).optJSONObject("error");
                if (err != null && !err.optString("message").isEmpty()) reason = err.optString("message");
            } catch (Exception ignored) {
                // body wasn't JSON; keep it as-is
            }
            throw new GeminiHttpException(code, "Gemini HTTP " + code + ": " + reason);
        }

        JSONObject json = new JSONObject(body);
        JSONArray candidates = json.optJSONArray("candidates");
        if (candidates != null && candidates.length() > 0) {
            JSONObject candidate = candidates.getJSONObject(0);
            JSONObject content = candidate.optJSONObject("content");
            if (content != null) {
                JSONArray resParts = content.optJSONArray("parts");
                if (resParts != null) {
                    // Newer models can return several parts (including hidden "thought" parts);
                    // join the visible text ones.
                    StringBuilder text = new StringBuilder();
                    for (int i = 0; i < resParts.length(); i++) {
                        JSONObject part = resParts.optJSONObject(i);
                        if (part == null || part.optBoolean("thought", false)) continue;
                        text.append(part.optString("text", ""));
                    }
                    if (text.length() > 0) return text.toString();
                }
            }
            String finish = candidate.optString("finishReason", "");
            if (!finish.isEmpty() && !"STOP".equals(finish)) {
                throw new IllegalStateException("Gemini returned no text (" + finish + ").");
            }
        }
        JSONObject feedback = json.optJSONObject("promptFeedback");
        if (feedback != null && !feedback.optString("blockReason").isEmpty()) {
            throw new IllegalStateException("Gemini blocked the request (" + feedback.optString("blockReason") + ").");
        }
        throw new IllegalStateException("Gemini returned an empty reply.");
    }

    private String postChat(String systemPrompt, List<JSONObject> history, int maxTokens, boolean jsonMode) throws Exception {
        URL url = new URL(trimSlash(serverUrl()) + "/api/chat");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(120000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
        for (JSONObject turn : history) {
            messages.put(new JSONObject()
                    .put("role", turn.optString("role"))
                    .put("content", turn.optString("content")));
        }

        JSONObject payload = new JSONObject();
        payload.put("model", model());
        payload.put("stream", false);
        payload.put("messages", messages);

        JSONObject options = new JSONObject();
        options.put("temperature", 0.4);
        options.put("top_p", 0.8);
        options.put("repeat_penalty", 1.15);
        options.put("num_predict", maxTokens);
        payload.put("options", options);
        if (jsonMode) payload.put("format", "json");

        try (OutputStream output = connection.getOutputStream();
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {
            writer.write(payload.toString());
        }

        int code = connection.getResponseCode();
        InputStream input = code >= 200 && code < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String body = readStream(input);
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code + ": " + body);
        }

        JSONObject json = new JSONObject(body);
        JSONObject message = json.optJSONObject("message");
        if (message == null) {
            return body;
        }
        return message.optString("content", body);
    }

    private String systemPromptForStep(String step) {
        return CONCISE_COACH_SYSTEM_PROMPT + "\n\n"
                + "You are currently coaching a Spanish-speaking learner moving from B2 to C1.\n"
                + "Use English as the default language. Use Spanish only when it prevents confusion.\n"
                + "Current chain step: " + step + "\n\n"
                + roleInstructions(step)
                + "\n\nMemory JSON files:\n" + readMemoryBlock();
    }

    private String roleInstructions(String step) {
        switch (step) {
            case "Supervisor Opening":
                return "Act as Supervisor GPT. Choose today's priorities and start the session immediately.";
            case "Supervisor Closing":
                return "Act as Supervisor GPT closing step. Review the whole conversation above.\n"
                        + "Output exactly two parts:\n"
                        + "1. A short closing message to the learner (max 60 words): one strength, one thing to work on.\n"
                        + "2. Then a single JSON object (no markdown fences, no comments) with this shape:\n"
                        + "{\"memory_update\": {\n"
                        + "  \"learner_profile.json\": {\"current_estimate\": \"B2|B2+|C1\", \"notes\": [\"new short note\"]},\n"
                        + "  \"progress_tracker.json\": {\"scores\": {\"skill\": number}, \"recurring_weaknesses\": [\"new pattern\"], \"last_session\": \"YYYY-MM-DD: one-line summary\"},\n"
                        + "  \"review_queue.json\": {\"items\": [\"phrase or pattern to review next time\"]}\n"
                        + "}}\n"
                        + "Only include NEW array entries; existing ones are kept automatically. "
                        + "Omit any field you have nothing new for. The 60-word limit does not apply to the JSON.";
            case "Recall & Drill":
                return "Act as Recall & Drill GPT. Run retrieval practice. Ask one item at a time.";
            case "Speaking Lab":
                return "Act as Speaking Lab GPT. Create speaking practice under pressure with C1-level reformulations.";
            case "Writing Studio":
                return "Act as Writing Studio GPT. Give a writing task, then provide correction and C1 alternatives.";
            case "Input Immersion":
                return "Act as Input Immersion GPT. Provide reading or listening-style input with comprehension checks.";
            case "Error Analyst":
                return "Act as Error Analyst GPT. Identify recurring patterns and root causes.";
            case "Diagnostic Coach":
                return "Act as Diagnostic Coach GPT. Evaluate CEFR across skills.";
            default:
                return "Run this step as part of the B2 to C1 fluency chain.";
        }
    }

    private static final String PREF_LAST_OPENING = "last_opening_prompt";
    private static final String[] OPENING_PROMPTS = {
            "Tell me about something useful you learned recently.",
            "Describe a small challenge you handled this week.",
            "What is one thing you are looking forward to?",
            "Tell me about a conversation you had recently.",
            "Describe something you watched, read, or listened to recently.",
            "What is one decision you made this week?",
            "Tell me about something that surprised you recently."
    };

    private String stepPrompt(String step) {
        if ("Supervisor Opening".equals(step)) {
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            String lastPrompt = prefs.getString(PREF_LAST_OPENING, "");
            
            List<String> available = new ArrayList<>();
            for (String p : OPENING_PROMPTS) {
                if (!p.equals(lastPrompt)) {
                    available.add(p);
                }
            }
            
            // Fallback if list is empty (shouldn't happen with >1 prompts)
            if (available.isEmpty()) {
                available.addAll(Arrays.asList(OPENING_PROMPTS));
            }
            
            String selected = available.get((int) (Math.random() * available.size()));
            prefs.edit().putString(PREF_LAST_OPENING, selected).apply();
            
            return "Start the Supervisor Opening step for today's session.\n"
                    + "Ask the learner this specific opening question: \"" + selected + "\"\n"
                    + "Keep it short and wait for their response.";
        }
        if ("Recall & Drill".equals(step)) {
            List<JSONObject> due = dueReviewItems(MAX_REVIEWS_PER_SESSION);
            if (due.isEmpty()) {
                return "Start the Recall & Drill step. The learner's review deck has nothing due today, "
                        + "so run a quick retrieval drill on vocabulary or patterns from the memory files "
                        + "(or, if there are none, 3 useful C1 collocations). Ask one item at a time and wait.";
            }
            StringBuilder items = new StringBuilder();
            for (JSONObject item : due) {
                items.append("- id=").append(item.optString("id"))
                        .append(" | learner's earlier mistake: \"").append(item.optString("original"))
                        .append("\" | target: \"").append(item.optString("corrected"))
                        .append("\" | note: ").append(item.optString("explanation")).append('\n');
            }
            return "Start the Recall & Drill step. These are mistakes the learner made in earlier sessions "
                    + "and are due for review:\n" + items
                    + "Quiz them one item at a time, in order. For each, give a short new context or the Spanish meaning "
                    + "and ask the learner to say it naturally. Never show the target before they answer. "
                    + "Start with the first item now.";
        }
        return "Start the " + step + " step for today's session. If you need learner input, ask for it clearly and wait.";
    }

    private String currentStep() {
        if (chainIndex >= 0 && chainIndex < chain.size()) {
            return chain.get(chainIndex);
        }
        return "Free Practice";
    }

    private void appendSystem(String text) {
        appendTranscript("System", text);
    }

    // ---------------------------------------------------------------------------------------
    // Review deck: every correction becomes a card, reviewed with Leitner-style spaced repetition.
    // ---------------------------------------------------------------------------------------
    private static final String REVIEW_DECK_FILE = "review_deck.json";
    /** Days until the next review for each box. A correct answer moves a card up one box. */
    private static final int[] REVIEW_INTERVAL_DAYS = {1, 3, 7, 14, 30, 60};
    private static final int MAX_REVIEWS_PER_SESSION = 4;
    private static final int MAX_DECK_SIZE = 500;

    /** Parses a JSON coach reply, tolerating markdown fences. Returns null if it isn't one. */
    private JSONObject parseCoachJson(String response) {
        int[] span = findJsonObjectContaining(response, "\"message\"");
        if (span == null) return null;
        try {
            JSONObject json = new JSONObject(response.substring(span[0], span[1]));
            return json.optString("message", "").trim().isEmpty() ? null : json;
        } catch (Exception exception) {
            return null;
        }
    }

    private File reviewDeckFile() {
        return new File(getFilesDir(), REVIEW_DECK_FILE);
    }

    private JSONArray loadReviewDeck() {
        try {
            JSONArray items = new JSONObject(readText(reviewDeckFile())).optJSONArray("items");
            return items != null ? items : new JSONArray();
        } catch (Exception exception) {
            return new JSONArray();
        }
    }

    private void saveReviewDeck(JSONArray items) {
        try {
            if (!writeText(reviewDeckFile(), new JSONObject().put("items", items).toString(2))) {
                appendSystem("Could not save your review deck.");
            }
        } catch (Exception exception) {
            appendSystem("Could not save your review deck: " + exception.getMessage());
        }
        refreshDeckStats();
    }

    private void addToReviewDeck(JSONArray corrections) {
        JSONArray deck = loadReviewDeck();
        String tomorrow = LocalDate.now().plusDays(1).toString();
        int added = 0;
        for (int i = 0; i < corrections.length(); i++) {
            JSONObject c = corrections.optJSONObject(i);
            if (c == null) continue;
            String original = c.optString("original").trim();
            String corrected = c.optString("corrected").trim();
            if (corrected.isEmpty() || corrected.equalsIgnoreCase(original)) continue;
            if (findDeckItem(deck, "corrected", corrected) != null) continue; // already in the deck
            try {
                JSONObject card = new JSONObject()
                        .put("id", "r" + System.currentTimeMillis() + "_" + i)
                        .put("original", original)
                        .put("corrected", corrected)
                        .put("explanation", c.optString("explanation").trim())
                        .put("category", c.optString("category", "other").trim().toLowerCase(Locale.US))
                        .put("created", LocalDate.now().toString())
                        .put("box", 0)
                        .put("due", tomorrow)
                        .put("reviews", 0)
                        .put("lapses", 0);
                deck.put(card);
                sessionNewCards.add(card);
                added++;
            } catch (Exception ignored) {
                // put() only fails on null keys
            }
        }
        if (added == 0) return;
        // Keep the deck bounded: drop the oldest cards first
        while (deck.length() > MAX_DECK_SIZE) deck.remove(0);
        saveReviewDeck(deck);
    }

    private JSONObject findDeckItem(JSONArray deck, String field, String value) {
        for (int i = 0; i < deck.length(); i++) {
            JSONObject item = deck.optJSONObject(i);
            if (item != null && item.optString(field).trim().equalsIgnoreCase(value.trim())) return item;
        }
        return null;
    }

    /** Cards due today or earlier, most overdue first. */
    private List<JSONObject> dueReviewItems(int max) {
        JSONArray deck = loadReviewDeck();
        String today = LocalDate.now().toString();
        ArrayList<JSONObject> due = new ArrayList<>();
        for (int i = 0; i < deck.length(); i++) {
            JSONObject item = deck.optJSONObject(i);
            if (item != null && item.optString("due", today).compareTo(today) <= 0) due.add(item);
        }
        due.sort((a, b) -> a.optString("due").compareTo(b.optString("due")));
        return due.size() > max ? new ArrayList<>(due.subList(0, max)) : due;
    }

    private void applyReviewResults(JSONArray results) {
        JSONArray deck = loadReviewDeck();
        int right = 0, wrong = 0;
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.optJSONObject(i);
            if (r == null) continue;
            JSONObject item = findDeckItem(deck, "id", r.optString("id"));
            if (item == null) continue; // model invented or mangled an id
            boolean correct = r.optBoolean("correct", false);
            gradeItem(item, correct);
            if (correct) right++; else wrong++;
        }
        if (right + wrong == 0) return;
        sessionReviewsRight += right;
        sessionReviewsTotal += right + wrong;
        saveReviewDeck(deck);
        appendSystem("Review: " + right + " correct, " + wrong + " to practise again tomorrow.");
    }

    /** Leitner step: correct moves the card up one box (longer interval), wrong resets it to tomorrow. */
    private void gradeItem(JSONObject item, boolean correct) {
        int box = item.optInt("box", 0);
        box = correct ? Math.min(box + 1, REVIEW_INTERVAL_DAYS.length - 1) : 0;
        try {
            item.put("box", box)
                    .put("due", LocalDate.now().plusDays(REVIEW_INTERVAL_DAYS[box]).toString())
                    .put("reviews", item.optInt("reviews", 0) + 1)
                    .put("last_reviewed", LocalDate.now().toString());
            if (!correct) item.put("lapses", item.optInt("lapses", 0) + 1);
        } catch (Exception ignored) {
            // put() only fails on null keys
        }
    }

    /** Shows a coach reply and returns the text actually displayed (closing JSON removed). */
    private String appendAssistant(String step, String text) {
        String cleanText = text;
        if ("Supervisor Closing".equals(step)) {
            cleanText = processMemoryUpdate(text);
            sessionClosingNotes = cleanText;
        }
        appendTranscript(step, cleanText);
        return cleanText;
    }

    // Hands-free voice mode: utterance ids tell the TTS callback whether to open the mic afterwards.
    private static final String UTTERANCE_AUTO_LISTEN = "coach_auto_listen";
    private static final String UTTERANCE_MANUAL = "coach";

    private String processMemoryUpdate(String text) {
        int[] span = findJsonObjectContaining(text, "\"memory_update\"");
        if (span == null) {
            appendSystem("No memory update was received this session, so your learner files are unchanged.");
            return text;
        }
        try {
            JSONObject update = new JSONObject(text.substring(span[0], span[1]));
            applyMemoryUpdate(update.getJSONObject("memory_update"));
        } catch (Exception exception) {
            appendSystem("Memory update failed: " + exception.getMessage());
            return text;
        }
        // Show the learner only the closing message, without the JSON (or any ```json fence around it)
        String before = text.substring(0, span[0]).replaceAll("```(?:json)?\\s*$", "");
        String after = text.substring(span[1]).replaceAll("^\\s*```", "");
        return (before + after).trim();
    }

    /**
     * Returns [start, endExclusive] of the outermost balanced {...} object that contains marker,
     * respecting JSON string literals, or null if none is found or it is truncated.
     */
    private int[] findJsonObjectContaining(String text, String marker) {
        int markerPos = text.indexOf(marker);
        if (markerPos < 0) return null;
        for (int start = text.lastIndexOf('{', markerPos); start >= 0; start = text.lastIndexOf('{', start - 1)) {
            int depth = 0;
            boolean inString = false;
            for (int i = start; i < text.length(); i++) {
                char c = text.charAt(i);
                if (inString) {
                    if (c == '\\') i++;
                    else if (c == '"') inString = false;
                } else if (c == '"') {
                    inString = true;
                } else if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        if (i > markerPos) return new int[]{start, i + 1};
                        break; // this object closes before the marker; try an earlier '{'
                    }
                }
            }
            if (start == 0) break;
        }
        return null;
    }

    // Fields the model may write in each memory file, and the max length of array fields.
    private static final String[][] MEMORY_SCHEMA = {
            {"learner_profile.json", "current_estimate", "immersion_phase", "notes"},
            {"progress_tracker.json", "week", "scores", "recurring_weaknesses", "last_session"},
            {"review_queue.json", "items"},
    };
    private static final int MAX_MEMORY_ARRAY_ITEMS = 40;

    private void applyMemoryUpdate(JSONObject update) {
        for (String[] schema : MEMORY_SCHEMA) {
            String fileName = schema[0];
            JSONObject newData = update.optJSONObject(fileName);
            if (newData == null) continue;
            List<String> allowed = Arrays.asList(schema).subList(1, schema.length);
            try {
                File file = new File(getFilesDir(), fileName);
                JSONObject current = new JSONObject(readText(file));
                int changed = 0;
                java.util.Iterator<String> keys = newData.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (!allowed.contains(key)) continue; // ignore fields outside the schema
                    Object incoming = newData.get(key);
                    Object existing = current.opt(key);
                    if (incoming instanceof JSONArray && existing instanceof JSONArray) {
                        current.put(key, appendUnique((JSONArray) existing, (JSONArray) incoming));
                    } else if (incoming instanceof JSONObject && existing instanceof JSONObject) {
                        JSONObject merged = (JSONObject) existing;
                        java.util.Iterator<String> sub = ((JSONObject) incoming).keys();
                        while (sub.hasNext()) {
                            String k = sub.next();
                            merged.put(k, ((JSONObject) incoming).get(k));
                        }
                    } else {
                        current.put(key, incoming);
                    }
                    changed++;
                }
                if (changed == 0) continue;
                if (writeText(file, current.toString(2))) {
                    appendSystem("Updated " + fileName);
                } else {
                    appendSystem("Could not save " + fileName);
                }
            } catch (Exception exception) {
                appendSystem("Memory update for " + fileName + " failed: " + exception.getMessage());
            }
        }
    }

    /** Appends entries not already present, keeping only the newest MAX_MEMORY_ARRAY_ITEMS. */
    private JSONArray appendUnique(JSONArray existing, JSONArray incoming) {
        ArrayList<Object> items = new ArrayList<>();
        ArrayList<String> seen = new ArrayList<>();
        for (JSONArray source : new JSONArray[]{existing, incoming}) {
            for (int i = 0; i < source.length(); i++) {
                Object item = source.opt(i);
                String key = String.valueOf(item).trim().toLowerCase(Locale.US);
                if (item == null || seen.contains(key)) continue;
                seen.add(key);
                items.add(item);
            }
        }
        int from = Math.max(0, items.size() - MAX_MEMORY_ARRAY_ITEMS);
        return new JSONArray(items.subList(from, items.size()));
    }

    private void setStatus(final String text) {
        if (statusView == null) return;
        mainHandler.post(() -> {
            if (text == null || text.trim().isEmpty()) {
                statusView.setVisibility(View.GONE);
                return;
            }
            statusView.setVisibility(View.VISIBLE);
            String lowercase = text.toLowerCase(Locale.US);
            
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(12));
            
            int textColor;
            int paddingVertical = dp(10);
            int paddingHorizontal = dp(16);
            String prefix = "";
            
            if (lowercase.contains("connect") || lowercase.equals("ready.")) {
                bg.setColor(Color.argb(40, 34, 197, 94)); // semi-transparent green
                bg.setStroke(dp(1), Color.rgb(34, 197, 94));
                textColor = Color.rgb(74, 222, 128); // light green
                prefix = "✓ ";
            } else if (lowercase.contains("fail") || lowercase.contains("error") || lowercase.contains("timeout") || lowercase.contains("invalid") || lowercase.contains("required")) {
                bg.setColor(Color.argb(40, 239, 68, 68)); // semi-transparent red
                bg.setStroke(dp(1), Color.rgb(239, 68, 68));
                textColor = Color.rgb(248, 113, 113); // light red
                prefix = "⚠️ ";
            } else if (lowercase.contains("test") || lowercase.contains("contact") || lowercase.contains("process") || lowercase.contains("listen")) {
                bg.setColor(Color.argb(40, 59, 130, 246)); // semi-transparent blue
                bg.setStroke(dp(1), Color.rgb(59, 130, 246));
                textColor = Color.rgb(96, 165, 250); // light blue
                prefix = "⏳ ";
            } else {
                bg.setColor(Color.argb(40, 148, 163, 184)); // semi-transparent slate
                bg.setStroke(dp(1), COLOR_BORDER_CARD);
                textColor = COLOR_TEXT_SECONDARY;
                prefix = "ℹ️ ";
            }
            
            statusView.setBackground(bg);
            statusView.setTextColor(textColor);
            statusView.setText(prefix + text);
            statusView.setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical);
            
            android.view.ViewGroup.LayoutParams rawParams = statusView.getLayoutParams();
            if (rawParams instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) rawParams;
                params.setMargins(0, dp(16), 0, 0);
                statusView.setLayoutParams(params);
            }
        });
    }

    private String serverUrl() {
        String value = serverUrlField.getText().toString().trim();
        return value.isEmpty() ? defaultServerUrl() : value;
    }

    private String defaultServerUrl() {
        return isProbablyEmulator() ? EMULATOR_DEFAULT_SERVER : PHONE_USB_REVERSE_SERVER;
    }

    private String ollamaSetupIssue() {
        if (isEmulatorOnlyAddressOnPhysicalDevice(serverUrl())) {
            return "10.0.2.2 only works in the Android emulator. On this phone, use http://127.0.0.1:11434 and run adb reverse tcp:11434 tcp:11434 on the computer before testing Ollama.";
        }
        return null;
    }

    private String ollamaConnectionErrorMessage(Exception exception) {
        String detail = exception.getMessage();
        if (detail == null || detail.trim().isEmpty()) {
            detail = exception.getClass().getSimpleName();
        }

        String configuredUrl = serverUrl();
        if (isLocalhostAddressOnPhysicalDevice(configuredUrl)) {
            return "Failed to connect to " + configuredUrl
                    + ". This real phone needs an active USB tunnel: adb reverse tcp:11434 tcp:11434. Detail: "
                    + detail;
        }

        return detail;
    }

    private String ollamaConnectionStatusMessage() {
        if (isLocalhostAddressOnPhysicalDevice(serverUrl())) {
            return "Run adb reverse, then test Ollama again.";
        }
        return "Check Ollama URL, model name, and network access.";
    }

    private boolean isEmulatorOnlyAddressOnPhysicalDevice(String value) {
        try {
            URI uri = new URI(value.contains("://") ? value : "http://" + value);
            String host = uri.getHost();
            return EMULATOR_HOST.equals(host) && !isProbablyEmulator();
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private boolean isLocalhostAddressOnPhysicalDevice(String value) {
        try {
            URI uri = new URI(value.contains("://") ? value : "http://" + value);
            String host = uri.getHost();
            return !isProbablyEmulator()
                    && ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host));
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private boolean isProbablyEmulator() {
        String fingerprint = lower(Build.FINGERPRINT);
        String model = lower(Build.MODEL);
        String manufacturer = lower(Build.MANUFACTURER);
        String brand = lower(Build.BRAND);
        String device = lower(Build.DEVICE);
        String product = lower(Build.PRODUCT);
        String hardware = lower(Build.HARDWARE);

        return fingerprint.startsWith("generic")
                || fingerprint.startsWith("unknown")
                || fingerprint.contains("generic/sdk")
                || fingerprint.contains("vbox")
                || model.contains("google_sdk")
                || model.contains("emulator")
                || model.contains("android sdk built for x86")
                || manufacturer.contains("genymotion")
                || (brand.startsWith("generic") && device.startsWith("generic"))
                || product.contains("google_sdk")
                || product.contains("sdk_gphone")
                || product.contains("emulator")
                || hardware.contains("goldfish")
                || hardware.contains("ranchu");
    }

    private String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }

    private String model() {
        String value = modelField.getText().toString().trim();
        if (value.isEmpty()) {
            boolean isGemini = (serviceTypeSpinner != null && serviceTypeSpinner.getSelectedItemPosition() == 1);
            return isGemini ? "gemini-3.5-flash" : DEFAULT_MODEL;
        }
        return value;
    }

    private String trimSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private void ensureMemoryFiles() {
        createIfMissing("learner_profile.json",
                "{\n"
                        + "  \"target\": \"C1 English fluency\",\n"
                        + "  \"current_estimate\": \"B2\",\n"
                        + "  \"native_language\": \"Spanish\",\n"
                        + "  \"immersion_phase\": 1,\n"
                        + "  \"notes\": []\n"
                        + "}\n");
        createIfMissing("progress_tracker.json",
                "{\n"
                        + "  \"week\": 1,\n"
                        + "  \"scores\": {},\n"
                        + "  \"recurring_weaknesses\": [],\n"
                        + "  \"last_session\": null\n"
                        + "}\n");
        createIfMissing("review_queue.json",
                "{\n"
                        + "  \"items\": []\n"
                        + "}\n");
    }

    private void createIfMissing(String name, String content) {
        File file = new File(getFilesDir(), name);
        if (!file.exists()) {
            writeText(file, content);
        }
    }

    private String readMemoryBlock() {
        StringBuilder builder = new StringBuilder();
        for (String name : Arrays.asList("learner_profile.json", "progress_tracker.json", "review_queue.json")) {
            File file = new File(getFilesDir(), name);
            builder.append("\n--- ").append(name).append(" ---\n");
            builder.append(truncate(readText(file), MAX_MEMORY_CHARS)).append("\n");
        }
        return builder.toString();
    }

    private String truncate(String value, int maxChars) {
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars) + "\n[truncated]";
    }

    /** Saves the transcript for the History tab. Called automatically when a session ends. */
    private void saveSessionLog() {
        try {
            JSONObject payload = new JSONObject();
            String timestamp = java.time.LocalDateTime.now().toString();
            payload.put("date", timestamp);
            payload.put("model", model());
            payload.put("server_url", serverUrl());
            payload.put("chain", new JSONArray(chain));
            payload.put("answers", sessionAnswers);
            payload.put("new_cards", sessionNewCards.size());
            payload.put("transcript", transcript.toString());

            String safeName = "session_log_" + timestamp.replace(":", "-").replace(".", "-") + ".json";
            if (!writeText(new File(getFilesDir(), safeName), payload.toString(2))) {
                setStatus("Could not save this session to History.");
            }
        } catch (Exception exception) {
            setStatus("Could not save this session: " + exception.getMessage());
        }
    }

    private String readText(File file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                java.nio.file.Files.newInputStream(file.toPath()), StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        } catch (Exception exception) {
            return "{}";
        }
    }

    private String readStream(InputStream input) throws Exception {
        if (input == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
            return builder.toString();
        }
    }

    /** Writes the file; returns false (instead of throwing) so callers can report the failure. */
    private boolean writeText(File file, String content) {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                java.nio.file.Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8))) {
            writer.write(content);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

}
