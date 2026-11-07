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
    private static final int COLOR_BG_ROOT = Color.rgb(10, 14, 23); // Deep space background
    private static final int COLOR_BG_CARD = Color.rgb(22, 28, 45); // Slate/Navy glassmorphic card background
    private static final int COLOR_BORDER_CARD = Color.argb(40, 255, 255, 255); // Muted border
    private static final int COLOR_TEXT_PRIMARY = Color.rgb(243, 244, 246); // Off-white
    private static final int COLOR_TEXT_SECONDARY = Color.rgb(156, 163, 175); // Lavender-grey
    private static final int COLOR_ACCENT = Color.rgb(99, 102, 241); // Tailwind Indigo-500
    private static final int COLOR_INPUT_BG = Color.rgb(15, 23, 42); // Very dark slate for inputs

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
    private EditText userInput;
    private ScrollView mainScrollView;
    private LinearLayout transcriptContainer;
    private LinearLayout historyContainer;
    private TextView statusView;
    private FrameLayout tabContentContainer;
    private View homeTab, historyTab, settingsTab;
    private View homeNav, historyNav, settingsNav;
    private int currentTab = 0;
    private TextView currentStepView;
    private TextView currentStepDescView;
    private TextView autoFocusInfo;
    private TextView deckStatsView;
    private Spinner focusSpinner;
    private String selectedFocusArea = "Auto";
    private LinearLayout[] focusCards;
    private Spinner dayTypeSpinner;
    private Button startButton;
    private Button nextButton;
    private Button saveButton;
    private Button sendButton;
    private Button testButton;
    private Button micButton;
    private Button largeMicButton;
    private TextView writingHelper;
    private TextView speakingHint;
    private TextView immersionHint;
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;
    private TextToSpeech tts;
    private ProgressBar progressBar;
    private TextView[] stepViews;
    private static final String[] STEPS = {"Opening", "Recall", "Drill", "Error Fix", "Closing"};
    private int chainIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ensureMemoryFiles();
        buildUi();
        loadPrefs();
        updateStepIndicator(-1);
        refreshDeckStats();
        initTts();
        appendSystem("Configure your session and tap 'Begin New Session' to start.");
    }

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) {
                appendSystem("TTS Initialization failed.");
                tts = null;
            } else {
                tts.setLanguage(Locale.US);
            }
        });
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

    private void buildUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(COLOR_BG_ROOT);
            getWindow().setNavigationBarColor(Color.rgb(15, 23, 42));
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BG_ROOT);

        tabContentContainer = new FrameLayout(this);
        root.addView(tabContentContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        // Navigation Bar - Dark Glassmorphism style
        LinearLayout navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        
        GradientDrawable navBg = new GradientDrawable();
        navBg.setColor(Color.rgb(15, 23, 42));
        navBg.setStroke(dp(1), Color.rgb(30, 41, 59));
        navBar.setBackground(navBg);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            navBar.setElevation(dp(8));
        }
        root.addView(navBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(60)));

        homeNav = navItem("Home", "🏠");
        historyNav = navItem("History", "📜");
        settingsNav = navItem("Settings", "⚙️");

        navBar.addView(homeNav, navBarParams());
        navBar.addView(historyNav, navBarParams());
        navBar.addView(settingsNav, navBarParams());

        // Tabs
        homeTab = buildHomeTab();
        historyTab = buildHistoryTab();
        settingsTab = buildSettingsTab();

        tabContentContainer.addView(homeTab);
        tabContentContainer.addView(historyTab);
        tabContentContainer.addView(settingsTab);

        setContentView(root);

        homeNav.setOnClickListener(v -> switchTab(0));
        historyNav.setOnClickListener(v -> switchTab(1));
        settingsNav.setOnClickListener(v -> switchTab(2));

        switchTab(0);
        initSpeechRecognizer();
    }

    private View navItem(String label, String icon) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);
        item.setFocusable(true);

        TextView iconView = new TextView(this);
        iconView.setText(icon);
        iconView.setTextSize(22);
        iconView.setGravity(Gravity.CENTER);
        item.addView(iconView);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(12);
        labelView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        labelView.setTextColor(COLOR_TEXT_SECONDARY);
        labelView.setGravity(Gravity.CENTER);
        labelView.setPadding(0, dp(2), 0, 0);
        item.addView(labelView);

        return item;
    }

    private void switchTab(int index) {
        currentTab = index;
        homeTab.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        historyTab.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        settingsTab.setVisibility(index == 2 ? View.VISIBLE : View.GONE);

        updateNavItemStyle(homeNav, index == 0);
        updateNavItemStyle(historyNav, index == 1);
        updateNavItemStyle(settingsNav, index == 2);
        
        if (index == 1) {
            loadHistory();
        }
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
                dateView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
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

    private void updateNavItemStyle(View view, boolean active) {
        LinearLayout layout = (LinearLayout) view;
        int color = active ? COLOR_ACCENT : COLOR_TEXT_SECONDARY;
        ((TextView) layout.getChildAt(0)).setAlpha(active ? 1.0f : 0.4f);
        ((TextView) layout.getChildAt(1)).setTextColor(color);
    }

    private View buildHomeTab() {
        mainScrollView = new ScrollView(this);
        mainScrollView.setFillViewport(true);
        mainScrollView.setBackgroundColor(COLOR_BG_ROOT);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(32), dp(20), dp(48));
        mainScrollView.addView(content);

        // Premium Hero Header
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(4), 0, 0, dp(32));
        
        TextView title = new TextView(this);
        title.setText("Fluency Riser");
        title.setTextColor(COLOR_TEXT_PRIMARY);
        title.setTextSize(34);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        hero.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Mastering English C1 Proficiency");
        subtitle.setTextColor(Color.rgb(129, 140, 248));
        subtitle.setTextSize(16);
        subtitle.setPadding(0, dp(4), 0, 0);
        hero.addView(subtitle);

        deckStatsView = new TextView(this);
        deckStatsView.setTextColor(COLOR_TEXT_SECONDARY);
        deckStatsView.setTextSize(13);
        deckStatsView.setPadding(0, dp(12), 0, 0);
        hero.addView(deckStatsView);
        content.addView(hero);

        // Progress Section - Cleaner and more focused
        content.addView(sectionHeader("📊 Session Flow"));
        LinearLayout progressPanel = panel();
        progressPanel.setPadding(dp(12), dp(20), dp(12), dp(20));
        content.addView(progressPanel);
        
        LinearLayout stepContainer = new LinearLayout(this);
        stepContainer.setOrientation(LinearLayout.HORIZONTAL);
        stepContainer.setGravity(Gravity.CENTER);
        stepContainer.setPadding(0, dp(12), 0, dp(24));
        
        stepViews = new TextView[STEPS.length];
        String[] stepIcons = {"🚀", "🧠", "🎯", "🛠️", "🏁"};
        for (int i = 0; i < STEPS.length; i++) {
            // Circle view
            stepViews[i] = new TextView(this);
            stepViews[i].setText(stepIcons[i]);
            stepViews[i].setTextSize(16);
            stepViews[i].setGravity(Gravity.CENTER);
            
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(dp(36), dp(36));
            stepViews[i].setLayoutParams(itemParams);
            stepContainer.addView(stepViews[i]);
            
            // Add connecting line between step icons, except for the last item
            if (i < STEPS.length - 1) {
                View line = new View(this);
                line.setBackgroundColor(COLOR_BORDER_CARD);
                LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(dp(20), dp(2));
                lineParams.gravity = Gravity.CENTER_VERTICAL;
                line.setLayoutParams(lineParams);
                stepContainer.addView(line);
            }
        }
        progressPanel.addView(stepContainer);

        currentStepView = new TextView(this);
        currentStepView.setText("READY TO START");
        currentStepView.setTextColor(Color.WHITE);
        currentStepView.setTextSize(18);
        currentStepView.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        currentStepView.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            currentStepView.setLetterSpacing(0.02f);
        }
        progressPanel.addView(currentStepView);

        currentStepDescView = new TextView(this);
        currentStepDescView.setText("Configure your practice options below and click 'Begin New Session' to start learning.");
        currentStepDescView.setTextColor(COLOR_TEXT_SECONDARY);
        currentStepDescView.setTextSize(13);
        currentStepDescView.setGravity(Gravity.CENTER);
        currentStepDescView.setPadding(0, dp(6), 0, 0);
        progressPanel.addView(currentStepDescView);

        // Configuration Section
        content.addView(sectionHeader("🎯 Practice Setup"));
        LinearLayout modePanel = panel();
        content.addView(modePanel);
        
        modePanel.addView(label("Focus Area"));
        
        // Hidden spinner to keep existing logic working
        focusSpinner = spinner(Arrays.asList(
                "Auto",
                "Speaking Lab",
                "Writing Studio",
                "Input Immersion",
                "Diagnostic Coach"
        ));
        focusSpinner.setVisibility(View.GONE);
        modePanel.addView(focusSpinner);

        // New Card Grid
        android.widget.GridLayout focusGrid = new android.widget.GridLayout(this);
        focusGrid.setColumnCount(3);
        focusGrid.setRowCount(2);
        focusGrid.setAlignmentMode(android.widget.GridLayout.ALIGN_BOUNDS);
        focusGrid.setUseDefaultMargins(false);
        
        String[] labels = {"Auto", "Speaking Lab", "Writing Studio", "Input Immersion", "Diagnostic Coach"};
        String[] icons = {"★", "🎙️", "📝", "📖", "🩺"};
        focusCards = new LinearLayout[labels.length];
        
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            final String labelText = labels[i];
            
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(8), dp(16), dp(8), dp(16));
            card.setClickable(true);
            card.setFocusable(true);
            
            android.widget.GridLayout.LayoutParams params = new android.widget.GridLayout.LayoutParams();
            params.width = 0;
            params.height = dp(90);
            params.columnSpec = android.widget.GridLayout.spec(i % 3, 1f);
            params.rowSpec = android.widget.GridLayout.spec(i / 3, 1f);
            params.setMargins(dp(4), dp(4), dp(4), dp(4));
            card.setLayoutParams(params);
            
            TextView iconView = new TextView(this);
            iconView.setText(icons[i]);
            iconView.setTextSize(24);
            card.addView(iconView);
            
            TextView labelView = new TextView(this);
            labelView.setText(labelText);
            labelView.setTextSize(11);
            labelView.setGravity(Gravity.CENTER);
            labelView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            labelView.setPadding(0, dp(4), 0, 0);
            card.addView(labelView);
            
            card.setOnClickListener(v -> {
                focusSpinner.setSelection(index);
                updateFocusCardsUi(index);
            });
            
            focusCards[i] = card;
            focusGrid.addView(card);
        }
        
        updateFocusCardsUi(0); // Initialize with 'Auto' selected
        modePanel.addView(focusGrid);
        
        autoFocusInfo = new TextView(this);
        autoFocusInfo.setTextColor(Color.rgb(63, 81, 181));
        autoFocusInfo.setTextSize(12);
        autoFocusInfo.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        autoFocusInfo.setPadding(dp(4), dp(4), 0, dp(8));
        autoFocusInfo.setVisibility(View.GONE);
        modePanel.addView(autoFocusInfo);
        
        modePanel.addView(label("Session Length"));
        dayTypeSpinner = spinner(Arrays.asList("Short day", "Full day"));
        modePanel.addView(dayTypeSpinner);

        // Main Action - Dominant CTA
        LinearLayout startContainer = new LinearLayout(this);
        startContainer.setPadding(0, dp(8), 0, dp(16));
        startButton = button("Begin New Session");
        startContainer.addView(startButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54)));
        content.addView(startContainer);

        // History Section - Cleaner Card
        content.addView(sectionHeader("💬 Interaction History"));
        LinearLayout transcriptPanel = panel();
        transcriptPanel.setPadding(dp(12), dp(20), dp(12), dp(20));
        content.addView(transcriptPanel);
        
        transcriptContainer = new LinearLayout(this);
        transcriptContainer.setOrientation(LinearLayout.VERTICAL);
        
        TextView emptyState = new TextView(this);
        emptyState.setText("Your conversation history will appear here");
        emptyState.setTextColor(COLOR_TEXT_SECONDARY);
        emptyState.setTextSize(14);
        emptyState.setGravity(Gravity.CENTER);
        emptyState.setPadding(0, dp(40), 0, dp(40));
        transcriptContainer.addView(emptyState);
        transcriptPanel.addView(transcriptContainer);

        // Input Section
        content.addView(sectionHeader("✍️ Your Response"));
        final LinearLayout inputPanel = panel();
        content.addView(inputPanel);
        
        writingHelper = new TextView(this);
        writingHelper.setText("Write your response below.");
        writingHelper.setTextColor(COLOR_ACCENT);
        writingHelper.setTextSize(13);
        writingHelper.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        writingHelper.setPadding(dp(4), 0, 0, dp(8));
        writingHelper.setVisibility(View.GONE);
        inputPanel.addView(writingHelper);

        speakingHint = new TextView(this);
        speakingHint.setText("Try to answer in 45 seconds.");
        speakingHint.setTextColor(Color.rgb(239, 68, 68));
        speakingHint.setTextSize(13);
        speakingHint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        speakingHint.setPadding(dp(4), 0, 0, dp(8));
        speakingHint.setVisibility(View.GONE);
        inputPanel.addView(speakingHint);

        immersionHint = new TextView(this);
        immersionHint.setText("Reading practice. Use the 'Listen' button for TTS.");
        immersionHint.setTextColor(Color.rgb(74, 222, 128));
        immersionHint.setTextSize(13);
        immersionHint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        immersionHint.setPadding(dp(4), 0, 0, dp(8));
        immersionHint.setVisibility(View.GONE);
        inputPanel.addView(immersionHint);

        userInput = multiLineField("Share your thoughts or complete the task...");
        inputPanel.addView(userInput);

        largeMicButton = new Button(this);
        largeMicButton.setText("🎤 Tap to Speak");
        largeMicButton.setAllCaps(false);
        largeMicButton.setTextColor(Color.WHITE);
        largeMicButton.setTextSize(18);
        largeMicButton.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        GradientDrawable largeMicDrawable = new GradientDrawable();
        largeMicDrawable.setColor(Color.rgb(239, 68, 68));
        largeMicDrawable.setCornerRadius(dp(16));
        largeMicButton.setBackground(largeMicDrawable);
        largeMicButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams largeMicParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(72));
        largeMicParams.setMargins(0, dp(8), 0, dp(16));
        inputPanel.addView(largeMicButton, largeMicParams);

        LinearLayout inputActions = horizontal();
        micButton = secondaryButton("🎤 Record");
        micButton.setVisibility(View.GONE);
        sendButton = button("Submit Response");
        inputActions.addView(micButton, weightParams());
        inputActions.addView(sendButton, weightParams());
        inputPanel.addView(inputActions);

        // Update Focus Spinner listener to handle Adaptive UI
        focusSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selected = String.valueOf(focusSpinner.getSelectedItem());
                String effective = "Auto".equals(selected) ? roleForToday() : selected;
                
                if ("Auto".equals(selected)) {
                    autoFocusInfo.setText("★ Adaptive: " + effective);
                    autoFocusInfo.setVisibility(View.VISIBLE);
                } else {
                    autoFocusInfo.setVisibility(View.GONE);
                }
                
                reconfigureInputUi(effective);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        
        // Initial manual trigger to apply the adaptive UI for the starting selection (Auto)
        focusSpinner.post(() -> {
            if (focusSpinner.getOnItemSelectedListener() != null) {
                focusSpinner.getOnItemSelectedListener().onItemSelected(focusSpinner, null, 0, 0);
            }
        });

        // Secondary Actions - Tucked away but accessible
        LinearLayout secondaryActions = horizontal();
        secondaryActions.setPadding(0, dp(24), 0, 0);
        nextButton = secondaryButton("Next Step");
        saveButton = secondaryButton("Save Session");
        secondaryActions.addView(nextButton, weightParams());
        secondaryActions.addView(saveButton, weightParams());
        content.addView(secondaryActions);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            progressBar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(Color.rgb(63, 81, 181)));
        }
        content.addView(progressBar);

        startButton.setOnClickListener(v -> startChain());
        nextButton.setOnClickListener(v -> nextStep());
        saveButton.setOnClickListener(v -> saveSessionLog());
        sendButton.setOnClickListener(v -> sendCurrentInput());
        micButton.setOnClickListener(v -> toggleMic());
        largeMicButton.setOnClickListener(v -> toggleMic());

        return mainScrollView;
    }

    private View buildHistoryTab() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(COLOR_BG_ROOT);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(32), dp(20), dp(48));
        scrollView.addView(content);

        content.addView(sectionHeader("📜 Practice History"));
        
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

        content.addView(sectionHeader("⚙️ Connection Settings"));
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
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
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
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
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
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
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
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
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

    private void updateStepIndicator(int stepIndex) {
        if (stepViews == null) return;
        for (int i = 0; i < stepViews.length; i++) {
            if (i == stepIndex) {
                stepViews[i].setAlpha(1.0f);
                stepViews[i].setBackground(circleDrawable(COLOR_ACCENT, Color.WHITE, 2));
            } else if (i < stepIndex) {
                stepViews[i].setAlpha(1.0f);
                stepViews[i].setBackground(circleDrawable(Color.rgb(15, 118, 110), Color.rgb(20, 184, 166), 1));
            } else {
                stepViews[i].setAlpha(0.4f);
                stepViews[i].setBackground(circleDrawable(COLOR_INPUT_BG, COLOR_BORDER_CARD, 1));
            }
        }
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

    private void startChain() {
        savePrefs();
        userResponseCount = 0;
        conversation.clear();
        chain.clear();
        chain.addAll(buildChain());
        chainIndex = -1;
        transcript.setLength(0);
        transcriptContainer.removeAllViews();
        nextStep();
    }

    private List<String> buildChain() {
        String focus = String.valueOf(focusSpinner.getSelectedItem());
        boolean fullDay = "Full day".equals(dayTypeSpinner.getSelectedItem());
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
        steps.add(production);
        if (fullDay && !"Input Immersion".equals(production)) {
            steps.add("Input Immersion");
        }
        steps.add("Error Analyst");
        steps.add("Supervisor Closing");
        return steps;
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

    private void nextStep() {
        if (chain.isEmpty()) {
            startChain();
            return;
        }
        if (chainIndex + 1 >= chain.size()) {
            appendSystem("Chain complete. Save the session so today's transcript is stored.");
            currentStepView.setText("SESSION COMPLETE");
            if (currentStepDescView != null) {
                currentStepDescView.setText("Review your transcript below. You can save your session log to local storage.");
            }
            updateStepIndicator(4); // Done
            updateMicButtonVisibility();
            syncFocusUiWithStep("Closing");
            return;
        }
        chainIndex++;
        userResponseCount = 0; // the response limit applies per step
        String step = chain.get(chainIndex);
        
        int uiIndex = 2; // Default to practice phase
        String uiName = "Practice Phase";
        String uiDesc = "Dive into your customized learning studio.";
        
        if (step.contains("Opening")) { 
            uiIndex = 0; 
            uiName = "1. Opening Phase"; 
            uiDesc = "Warm up your speech with a customized question from the Supervisor.";
        } else if (step.contains("Recall")) { 
            uiIndex = 1; 
            uiName = "2. Recall & Drill"; 
            uiDesc = "Review and drill vocabulary or patterns from previous sessions.";
        } else if (step.contains("Speaking Lab") || step.contains("Writing Studio") || step.contains("Input Immersion") || step.contains("Diagnostic Coach")) {
            uiIndex = 2;
            uiName = "3. Practice Phase (" + step + ")";
            uiDesc = "Focus on your targeted " + step + " exercises.";
        } else if (step.contains("Error Analyst")) { 
            uiIndex = 3; 
            uiName = "4. Error Analysis"; 
            uiDesc = "Review grammatical or pronunciation errors made during this session.";
        } else if (step.contains("Closing")) { 
            uiIndex = 4; 
            uiName = "5. Closing Phase"; 
            uiDesc = "Consolidate today's learnings and save them to long-term memory.";
        }
        
        currentStepView.setText(uiName.toUpperCase(Locale.US));
        if (currentStepDescView != null) {
            currentStepDescView.setText(uiDesc);
        }
        updateStepIndicator(uiIndex);

        // Sync Focus Area UI with the current step if it matches a focus mode
        syncFocusUiWithStep(step);
        
        updateMicButtonVisibility();
        sendOllama(stepPrompt(step), step, false);
    }

    private void syncFocusUiWithStep(String step) {
        List<String> focusModes = Arrays.asList("Auto", "Speaking Lab", "Writing Studio", "Input Immersion", "Diagnostic Coach");
        int matchIndex = -1;
        for (int i = 0; i < focusModes.size(); i++) {
            if (step.equals(focusModes.get(i))) {
                matchIndex = i;
                break;
            }
        }
        
        if (matchIndex != -1) {
            updateFocusCardsUi(matchIndex);
        } else if (step.contains("Opening") || step.contains("Closing") || step.contains("Error Analyst") || step.contains("Recall")) {
            // Return to neutral state for supervisor/recall steps
            updateFocusCardsUi(-1); 
        }
        
        reconfigureInputUi(getEffectiveFocusArea());
    }

    private void reconfigureInputUi(String effective) {
        // Reset all specific UI elements first
        writingHelper.setVisibility(View.GONE);
        speakingHint.setVisibility(View.GONE);
        immersionHint.setVisibility(View.GONE);
        largeMicButton.setVisibility(View.GONE);
        userInput.setMinLines(4);
        userInput.setHint("Share your thoughts or complete the task...");
        
        // Find inputPanel - we need to ensure it's accessible or find it from parent
        View inputPanel = (View) userInput.getParent();
        if (inputPanel != null) {
            inputPanel.setPadding(dp(20), dp(20), dp(20), dp(20));
        }

        if ("Writing Studio".equals(effective)) {
            writingHelper.setText("📝 Adaptive UI: Optimized for long-form writing.");
            writingHelper.setVisibility(View.VISIBLE);
            userInput.setMinLines(10);
            userInput.setHint("Start writing your essay or report here...");
            if (inputPanel != null) inputPanel.setPadding(dp(20), dp(24), dp(20), dp(24));
        } else if ("Speaking Lab".equals(effective)) {
            speakingHint.setText("🎙️ Adaptive UI: Optimized for speaking practice.");
            speakingHint.setVisibility(View.VISIBLE);
            largeMicButton.setVisibility(View.VISIBLE);
            userInput.setHint("Speak or type your response...");
            micButton.setVisibility(View.GONE);
        } else if ("Input Immersion".equals(effective)) {
            immersionHint.setText("📖 Adaptive UI: Optimized for reading & listening.");
            immersionHint.setVisibility(View.VISIBLE);
            userInput.setMinLines(2);
            userInput.setHint("Type a brief reflection or answer...");
        }
        
        updateMicButtonVisibility();
    }

    private void sendCurrentInput() {
        String text = userInput.getText().toString().trim();
        if (text.isEmpty()) {
            setStatus("Type a response first.");
            return;
        }

        if (userResponseCount >= MAX_RESPONSES_PER_STEP) {
            // Keep the typed text so it isn't lost; the learner can send it in the next step.
            appendSystem("This step is complete (" + MAX_RESPONSES_PER_STEP + " responses). Tap 'Next Step' to continue.");
            return;
        }

        String step = currentStep();
        userResponseCount++;
        appendUser(text);
        userInput.setText("");
        
        int uiIndex = 2;
        if (step.contains("Opening")) uiIndex = 0;
        else if (step.contains("Recall")) uiIndex = 1;
        else if (step.contains("Error Analyst")) uiIndex = 3;
        else if (step.contains("Closing")) uiIndex = 4;
        
        updateStepIndicator(uiIndex);

        String prompt = "Learner response:\n" + text + "\n\nContinue the current step. Give feedback, then tell me exactly what to do next.";
        if (userResponseCount >= MAX_RESPONSES_PER_STEP) {
            prompt = "Learner response:\n" + text + "\n\nThis is the FINAL interaction for this step. Provide brief feedback, then tell the learner this step is complete and to tap 'Next Step'. Do not ask another question.";
        }

        sendOllama(prompt + feedbackJsonInstructions(step), step, true);
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
                    conversation.add(userTurn);
                    try {
                        conversation.add(new JSONObject().put("role", "assistant").put("content", response));
                    } catch (Exception ignored) {
                        // JSONObject.put only throws for null keys / non-finite numbers.
                    }
                    JSONObject structured = jsonMode ? parseCoachJson(response) : null;
                    if (structured != null) {
                        appendAssistant(step, structured.optString("message", "").trim());
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
                        appendAssistant(step, response); // plain-text step, or the model ignored the JSON format
                    }
                    setStatus("Ready.");
                    setBusy(false);
                });
            } catch (Exception exception) {
                mainHandler.post(() -> {
                    appendSystem("Error: " + (isGemini ? exception.getMessage() : ollamaConnectionErrorMessage(exception)));
                    setStatus(isGemini ? "Check API key and network." : ollamaConnectionStatusMessage());
                    setBusy(false);
                });
            }
        });
    }

    private String postGemini(String systemPrompt, List<JSONObject> history, int maxTokens, boolean jsonMode) throws Exception {
        String key = apiKeyField.getText().toString().trim();
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Gemini API Key is empty. Please set it in Settings.");
        }

        URL url = new URL(GEMINI_BASE_URL + model() + ":generateContent");
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
            throw new IllegalStateException("HTTP " + code + ": " + body);
        }

        JSONObject json = new JSONObject(body);
        JSONArray candidates = json.optJSONArray("candidates");
        if (candidates != null && candidates.length() > 0) {
            JSONObject candidate = candidates.getJSONObject(0);
            JSONObject content = candidate.optJSONObject("content");
            if (content != null) {
                JSONArray resParts = content.optJSONArray("parts");
                if (resParts != null && resParts.length() > 0) {
                    return resParts.getJSONObject(0).optString("text", body);
                }
            }
        }
        return body;
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

    private void appendUser(String text) {
        appendTranscript("You", text);
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
                deck.put(new JSONObject()
                        .put("id", "r" + System.currentTimeMillis() + "_" + i)
                        .put("original", original)
                        .put("corrected", corrected)
                        .put("explanation", c.optString("explanation").trim())
                        .put("category", c.optString("category", "other").trim().toLowerCase(Locale.US))
                        .put("created", LocalDate.now().toString())
                        .put("box", 0)
                        .put("due", tomorrow)
                        .put("reviews", 0)
                        .put("lapses", 0));
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
            int box = item.optInt("box", 0);
            box = correct ? Math.min(box + 1, REVIEW_INTERVAL_DAYS.length - 1) : 0;
            try {
                item.put("box", box)
                        .put("due", LocalDate.now().plusDays(REVIEW_INTERVAL_DAYS[box]).toString())
                        .put("reviews", item.optInt("reviews", 0) + 1)
                        .put("last_reviewed", LocalDate.now().toString());
                if (!correct) item.put("lapses", item.optInt("lapses", 0) + 1);
            } catch (Exception ignored) {
            }
            if (correct) right++; else wrong++;
        }
        if (right + wrong == 0) return;
        saveReviewDeck(deck);
        appendSystem("Review: " + right + " correct, " + wrong + " to practise again tomorrow.");
    }

    private void refreshDeckStats() {
        if (deckStatsView == null) return;
        JSONArray deck = loadReviewDeck();
        int due = dueReviewItems(Integer.MAX_VALUE).size();
        int mastered = 0;
        for (int i = 0; i < deck.length(); i++) {
            JSONObject item = deck.optJSONObject(i);
            if (item != null && item.optInt("box", 0) >= 3) mastered++; // survived a 14-day interval
        }
        deckStatsView.setText(deck.length() == 0
                ? "📚 Review deck is empty. Your corrections will be saved here."
                : "📚 Review deck: " + deck.length() + " cards · " + due + " due today · " + mastered + " mastered");
    }

    /** Shows the coach's corrections as a compact card and records them in the transcript. */
    private void appendCorrections(JSONArray corrections) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(Color.argb(30, 250, 204, 21));
        bg.setStroke(dp(1), Color.rgb(202, 138, 4));
        card.setBackground(bg);

        TextView title = new TextView(this);
        title.setText("✏️ CORRECTIONS · SAVED TO YOUR REVIEW DECK");
        title.setTextSize(10);
        title.setTextColor(Color.rgb(250, 204, 21));
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        card.addView(title);

        StringBuilder log = new StringBuilder();
        for (int i = 0; i < corrections.length(); i++) {
            JSONObject c = corrections.optJSONObject(i);
            if (c == null || c.optString("corrected").trim().isEmpty()) continue;
            String original = c.optString("original").trim();
            String corrected = c.optString("corrected").trim();
            String why = c.optString("explanation").trim();

            if (!original.isEmpty()) {
                TextView wrong = new TextView(this);
                wrong.setText("✗ " + original);
                wrong.setTextColor(Color.rgb(248, 113, 113));
                wrong.setTextSize(15);
                wrong.setPaintFlags(wrong.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
                wrong.setPadding(0, dp(10), 0, 0);
                card.addView(wrong);
            }
            TextView right = new TextView(this);
            right.setText("✓ " + corrected);
            right.setTextColor(Color.rgb(74, 222, 128));
            right.setTextSize(15);
            right.setTextIsSelectable(true);
            card.addView(right);
            if (!why.isEmpty()) {
                TextView note = new TextView(this);
                note.setText(why);
                note.setTextColor(COLOR_TEXT_SECONDARY);
                note.setTextSize(12);
                card.addView(note);
            }
            log.append("\n- ").append(original).append(" → ").append(corrected);
            if (!why.isEmpty()) log.append(" (").append(why).append(")");
        }
        if (log.length() == 0) return;
        transcript.append("\n\n[Corrections]").append(log);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, dp(32), dp(16));
        transcriptContainer.addView(card, params);
    }

    private void appendAssistant(String step, String text) {
        String cleanText = text;
        if ("Supervisor Closing".equals(step)) {
            cleanText = processMemoryUpdate(text);
        }
        appendTranscript(step, cleanText);
    }

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

    private void appendTranscript(String speaker, String text) {
        if (transcript.length() == 0) {
            transcriptContainer.removeAllViews();
        }
        transcript.append("\n\n[").append(speaker).append("]\n").append(text.trim());
        
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(16));
        
        TextView speakerView = new TextView(this);
        String speakerLabel = speaker;
        if (speaker.contains("Supervisor")) {
            speakerLabel = "👑 Coach (Supervisor)";
        } else if (speaker.contains("Analyst")) {
            speakerLabel = "🔍 Coach (Error Analyst)";
        } else if (speaker.contains("Diagnostic")) {
            speakerLabel = "🩺 Coach (Diagnostic)";
        } else if ("Speaking Lab".equals(speaker)) {
            speakerLabel = "🎙️ Speaking Lab";
        } else if ("Writing Studio".equals(speaker)) {
            speakerLabel = "📝 Writing Studio";
        } else if ("Recall & Drill".equals(speaker)) {
            speakerLabel = "🎯 Recall & Drill";
        } else if ("Input Immersion".equals(speaker)) {
            speakerLabel = "📖 Input Immersion";
        } else if ("You".equals(speaker)) {
            speakerLabel = "👤 You";
        } else if ("System".equals(speaker)) {
            speakerLabel = "🔔 System";
        } else if (speaker.contains("Coach")) {
            speakerLabel = "🎓 Coach";
        }
        speakerView.setText(speakerLabel.toUpperCase(Locale.US));
        speakerView.setTextSize(10);
        speakerView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            speakerView.setLetterSpacing(0.1f);
        }
        
        if ("You".equals(speaker)) {
            drawable.setColor(Color.rgb(30, 41, 59)); // Slate background for user
            drawable.setStroke(dp(1.5f), COLOR_ACCENT);
            speakerView.setTextColor(COLOR_TEXT_PRIMARY);
        } else if ("System".equals(speaker)) {
            drawable.setColor(Color.TRANSPARENT);
            speakerView.setTextColor(COLOR_TEXT_SECONDARY);
            speakerView.setText("NOTIFICATION");
        } else if ("Input Immersion".equals(speaker)) {
            drawable.setColor(COLOR_BG_CARD);
            drawable.setStroke(dp(2), Color.rgb(34, 197, 94));
            speakerView.setTextColor(Color.rgb(34, 197, 94));
            speakerView.setText("📖 READING IMMERSION");
            speakerView.setGravity(Gravity.CENTER);
            card.setPadding(dp(24), dp(24), dp(24), dp(24));
        } else {
            drawable.setColor(COLOR_BG_CARD);
            drawable.setStroke(dp(1), COLOR_BORDER_CARD);
            speakerView.setTextColor(COLOR_TEXT_SECONDARY);
        }
        
        card.setBackground(drawable);
        if (!"System".equals(speaker) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setElevation(dp(2));
        }
        
        TextView contentView = new TextView(this);
        contentView.setText(text.trim());
        contentView.setTextColor(COLOR_TEXT_PRIMARY);
        if ("Input Immersion".equals(speaker)) {
            contentView.setTextSize(20);
            contentView.setTypeface(Typeface.SERIF);
            contentView.setLineSpacing(dp(4), 1.2f);
        } else {
            contentView.setTextSize(16);
            contentView.setLineSpacing(dp(2), 1.15f);
        }
        contentView.setTextIsSelectable(true);
        
        card.addView(speakerView);
        card.addView(contentView);
        
        boolean isImmersion = "Input Immersion".equals(speaker);
        boolean isCoach = !"You".equals(speaker) && !"System".equals(speaker);
        if (isCoach && !text.trim().isEmpty()) {
            // Every coach message can be heard, so the learner also trains listening and pronunciation.
            Button listenButton = secondaryButton(isImmersion ? "🔊 Listen to Content" : "🔊 Listen");
            listenButton.setTextSize(isImmersion ? 16 : 13);
            listenButton.setOnClickListener(v -> {
                if (tts != null) {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "coach");
                } else {
                    setStatus("Text-to-speech is not ready yet.");
                }
            });
            LinearLayout.LayoutParams btnParams = isImmersion
                    ? new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
                    : new LinearLayout.LayoutParams(dp(110), dp(36));
            btnParams.setMargins(0, dp(isImmersion ? 24 : 10), 0, 0);
            card.addView(listenButton, btnParams);
        }
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(16));
        
        if ("You".equals(speaker)) {
            params.setMargins(dp(32), 0, 0, dp(16));
        } else if ("System".equals(speaker)) {
            params.setMargins(dp(16), dp(8), dp(16), dp(24));
            speakerView.setGravity(Gravity.CENTER);
            contentView.setGravity(Gravity.CENTER);
            contentView.setTextSize(13);
            contentView.setTextColor(COLOR_TEXT_SECONDARY);
            contentView.setTypeface(Typeface.create("sans-serif", Typeface.ITALIC));
        } else if ("Input Immersion".equals(speaker)) {
            params.setMargins(0, dp(8), 0, dp(24));
        } else {
            params.setMargins(0, 0, dp(32), dp(16));
        }
        
        transcriptContainer.addView(card, params);
        
        // Auto-scroll
        mainHandler.postDelayed(() -> {
            mainScrollView.smoothScrollTo(0, transcriptContainer.getBottom() + dp(100));
        }, 100);
    }

    private void setBusy(boolean busy) {
        startButton.setEnabled(!busy);
        nextButton.setEnabled(!busy);
        saveButton.setEnabled(!busy);
        sendButton.setEnabled(!busy);
        testButton.setEnabled(!busy);
        if (micButton != null) micButton.setEnabled(!busy);
        if (largeMicButton != null) largeMicButton.setEnabled(!busy);
        if (progressBar != null) progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
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

    private void saveSessionLog() {
        try {
            JSONObject payload = new JSONObject();
            String timestamp = java.time.LocalDateTime.now().toString();
            payload.put("date", timestamp);
            payload.put("model", model());
            payload.put("server_url", serverUrl());
            payload.put("chain", new JSONArray(chain));
            payload.put("transcript", transcript.toString());

            String safeName = "session_log_" + timestamp.replace(":", "-").replace(".", "-") + ".json";
            File file = new File(getFilesDir(), safeName);
            if (!writeText(file, payload.toString(2))) {
                appendSystem("Save failed: could not write " + file.getName());
                return;
            }
            appendSystem("Saved " + file.getName() + " in the app's local storage.");
        } catch (Exception exception) {
            appendSystem("Save failed: " + exception.getMessage());
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

    private void initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { setStatus("Listening..."); }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() { setStatus("Processing voice..."); }
                @Override public void onError(int error) {
                    isListening = false;
                    setStatus(error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                            ? "Didn't catch that. Tap the mic and try again."
                            : "Mic Error: " + error);
                    updateMicButtonsText("🎤 Record Voice");
                }
                @Override public void onResults(Bundle results) {
                    isListening = false;
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        userInput.setText(matches.get(0));
                    }
                    setStatus("Ready.");
                    updateMicButtonsText("🎤 Record Voice");
                }
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
        }
    }

    private void updateMicButtonsText(String text) {
        if (micButton != null) micButton.setText(text);
        if (largeMicButton != null) {
            if (text.contains("Stop")) {
                largeMicButton.setText("🛑 Stop Recording");
                GradientDrawable activeDrawable = new GradientDrawable();
                activeDrawable.setColor(Color.rgb(44, 62, 80));
                activeDrawable.setCornerRadius(dp(16));
                largeMicButton.setBackground(activeDrawable);
            } else {
                largeMicButton.setText("🎤 Tap to Speak");
                GradientDrawable largeMicDrawable = new GradientDrawable();
                largeMicDrawable.setColor(Color.rgb(239, 68, 68));
                largeMicDrawable.setCornerRadius(dp(16));
                largeMicButton.setBackground(largeMicDrawable);
            }
        }
    }

    private void toggleMic() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 100);
            return;
        }
        if (isListening) {
            stopListening();
        } else {
            startListening();
        }
    }

    private void startListening() {
        if (speechRecognizer == null) {
            setStatus("Speech recognition is not available on this device.");
            appendSystem("Speech recognition is not available on this device. Please type your response.");
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        // The learner practises English, so recognise English even on a Spanish-language phone.
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        isListening = true;
        speechRecognizer.startListening(intent);
        updateMicButtonsText("🛑 Stop Recording");
    }

    private void stopListening() {
        isListening = false;
        if (speechRecognizer != null) speechRecognizer.stopListening();
        updateMicButtonsText("🎤 Record Voice");
    }

    private void updateMicButtonVisibility() {
        String step = currentStep();
        String effective = getEffectiveFocusArea();
        
        boolean isSpeakingFocus = "Speaking Lab".equals(effective);
        boolean isSpeakingStep = "Speaking Lab".equals(step);
        
        // Show large mic button if either the focus is Speaking Lab OR it's the specific Speaking Lab step
        boolean showMic = isSpeakingFocus || isSpeakingStep;
        
        largeMicButton.setVisibility(showMic ? View.VISIBLE : View.GONE);
        speakingHint.setVisibility(showMic ? View.VISIBLE : View.GONE);
        micButton.setVisibility(View.GONE); // Always favor large mic in Speaking focus/step
        
        if (showMic) {
            speakingHint.setText(isSpeakingStep ? "🎙️ Adaptive UI: Optimized for speaking practice." : "🎙️ Speaking Focus active.");
        }
    }

    private String getEffectiveFocusArea() {
        String step = currentStep();
        List<String> focusModes = Arrays.asList("Speaking Lab", "Writing Studio", "Input Immersion", "Diagnostic Coach");
        if (focusModes.contains(step)) return step;
        
        if (focusSpinner == null) return roleForToday();
        Object selected = focusSpinner.getSelectedItem();
        String selectedStr = (selected != null) ? String.valueOf(selected) : "Auto";
        return "Auto".equals(selectedStr) ? roleForToday() : selectedStr;
    }

    private void updateFocusCardsUi(int selectedIndex) {
        if (focusCards == null) return;
        for (int i = 0; i < focusCards.length; i++) {
            LinearLayout card = focusCards[i];
            GradientDrawable drawable = new GradientDrawable();
            drawable.setCornerRadius(dp(16));
            
            TextView iconView = (TextView) card.getChildAt(0);
            TextView labelView = (TextView) card.getChildAt(1);
            
            if (i == selectedIndex) {
                drawable.setColor(COLOR_ACCENT);
                drawable.setStroke(dp(2), Color.WHITE);
                iconView.setTextColor(Color.WHITE);
                iconView.setAlpha(1.0f);
                labelView.setTextColor(Color.WHITE);
            } else {
                drawable.setColor(COLOR_BG_CARD);
                drawable.setStroke(dp(1), COLOR_BORDER_CARD);
                iconView.setTextColor(COLOR_TEXT_PRIMARY);
                iconView.setAlpha(0.4f);
                labelView.setTextColor(COLOR_TEXT_SECONDARY);
            }
            card.setBackground(drawable);
        }
    }
}
