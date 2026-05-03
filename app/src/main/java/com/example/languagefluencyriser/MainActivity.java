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
    private static final String EMULATOR_DEFAULT_SERVER = "http://10.0.2.2:11434";
    private static final String PHONE_USB_REVERSE_SERVER = "http://127.0.0.1:11434";
    private static final String DEFAULT_MODEL = "gemma3:12b";
    private static final String EMULATOR_HOST = "10.0.2.2";
    private static final int MAX_MEMORY_CHARS = 5000;
    private static final int MAX_RESPONSES_PER_SESSION = 5;
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
    private int userResponseCount = 0;

    private EditText serverUrlField;
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
    private TextView autoFocusInfo;
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
    private TextView speakingHint;
    private TextView immersionHint;
    private SpeechRecognizer speechRecognizer;
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(243, 246, 250));

        tabContentContainer = new FrameLayout(this);
        root.addView(tabContentContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));

        // Navigation Bar
        LinearLayout navBar = new LinearLayout(this);
        navBar.setOrientation(LinearLayout.HORIZONTAL);
        navBar.setBackgroundColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            navBar.setElevation(dp(8));
        }
        root.addView(navBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(60)));

        homeNav = navItem("Home", "🏠");
        historyNav = navItem("History", "📜");
        settingsNav = navItem("Settings", "⚙️");

        navBar.addView(homeNav, weightParams());
        navBar.addView(historyNav, weightParams());
        navBar.addView(settingsNav, weightParams());

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
        iconView.setTextSize(20);
        item.addView(iconView);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(10);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
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
            emptyText.setTextColor(Color.rgb(113, 128, 150));
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
                dateView.setText(date.replace("T", " ").substring(0, 16));
                dateView.setTextColor(Color.rgb(30, 41, 59));
                dateView.setTextSize(16);
                dateView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
                logPanel.addView(dateView);
                
                TextView modelView = new TextView(this);
                modelView.setText("Model: " + model);
                modelView.setTextColor(Color.rgb(100, 116, 139));
                modelView.setTextSize(12);
                modelView.setPadding(0, dp(4), 0, dp(8));
                logPanel.addView(modelView);
                
                TextView previewView = new TextView(this);
                String preview = transcriptStr.replace("\n", " ").trim();
                if (preview.length() > 100) preview = preview.substring(0, 97) + "...";
                previewView.setText(preview);
                previewView.setTextColor(Color.rgb(71, 85, 105));
                previewView.setTextSize(14);
                logPanel.addView(previewView);
                
                logPanel.setOnClickListener(v -> {
                    // Show full transcript in the main interaction view
                    appendSystem("Viewing history from " + date);
                    transcript.setLength(0);
                    transcriptContainer.removeAllViews();
                    
                    // Re-parse transcript into UI components
                    String raw = transcriptStr;
                    // Standardize separators for parsing
                    if (raw.startsWith("\n\n[")) raw = raw.substring(2);
                    String[] parts = raw.split("\n\n\\[");
                    
                    for (String part : parts) {
                        if (part.isEmpty()) continue;
                        int endBracket = part.indexOf("]");
                        if (endBracket > 0) {
                            String speaker = part.substring(0, endBracket);
                            String text = part.substring(endBracket + 1).trim();
                            appendTranscript(speaker, text);
                        }
                    }
                    
                    switchTab(0);
                    mainScrollView.postDelayed(() -> mainScrollView.smoothScrollTo(0, 0), 100);
                });
                
                historyContainer.addView(logPanel);
            } catch (Exception ignored) {}
        }
    }

    private void updateNavItemStyle(View view, boolean active) {
        LinearLayout layout = (LinearLayout) view;
        int color = active ? Color.rgb(63, 81, 181) : Color.rgb(120, 130, 150);
        ((TextView) layout.getChildAt(0)).setAlpha(active ? 1.0f : 0.6f);
        ((TextView) layout.getChildAt(1)).setTextColor(color);
    }

    private View buildHomeTab() {
        mainScrollView = new ScrollView(this);
        mainScrollView.setFillViewport(true);
        mainScrollView.setBackgroundColor(Color.rgb(248, 250, 253));

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
        title.setTextColor(Color.rgb(30, 41, 59));
        title.setTextSize(34);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        hero.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Mastering English C1 Proficiency");
        subtitle.setTextColor(Color.rgb(100, 116, 139));
        subtitle.setTextSize(16);
        subtitle.setPadding(0, dp(4), 0, 0);
        hero.addView(subtitle);
        content.addView(hero);

        // Progress Section - Cleaner and more focused
        content.addView(sectionHeader("📊 Session Flow"));
        LinearLayout progressPanel = panel();
        progressPanel.setPadding(dp(12), dp(20), dp(12), dp(20));
        content.addView(progressPanel);
        
        LinearLayout stepContainer = horizontal();
        stepContainer.setPadding(0, dp(8), 0, dp(16));
        stepViews = new TextView[STEPS.length];
        String[] stepIcons = {"🚀", "🧠", "🎯", "🛠️", "🏁"};
        for (int i = 0; i < STEPS.length; i++) {
            stepViews[i] = new TextView(this);
            stepViews[i].setText(stepIcons[i] + "\n" + STEPS[i]);
            stepViews[i].setTextSize(9);
            stepViews[i].setGravity(Gravity.CENTER);
            stepViews[i].setPadding(0, dp(10), 0, dp(10));
            stepViews[i].setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            stepViews[i].setLineSpacing(0, 1.2f);
            stepContainer.addView(stepViews[i], weightParams());
        }
        progressPanel.addView(stepContainer);

        currentStepView = new TextView(this);
        currentStepView.setText("READY TO START");
        currentStepView.setTextColor(Color.rgb(113, 128, 150));
        currentStepView.setTextSize(12);
        currentStepView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        currentStepView.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            currentStepView.setLetterSpacing(0.05f);
        }
        progressPanel.addView(currentStepView);

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
        emptyState.setTextColor(Color.rgb(160, 174, 192));
        emptyState.setTextSize(14);
        emptyState.setGravity(Gravity.CENTER);
        emptyState.setPadding(0, dp(40), 0, dp(40));
        transcriptContainer.addView(emptyState);
        transcriptPanel.addView(transcriptContainer);

        // Input Section
        content.addView(sectionHeader("✍️ Your Response"));
        final LinearLayout inputPanel = panel();
        content.addView(inputPanel);
        
        final TextView writingHelper = new TextView(this);
        writingHelper.setText("Write your response below.");
        writingHelper.setTextColor(Color.rgb(63, 81, 181));
        writingHelper.setTextSize(13);
        writingHelper.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        writingHelper.setPadding(dp(4), 0, 0, dp(8));
        writingHelper.setVisibility(View.GONE);
        inputPanel.addView(writingHelper);

        speakingHint = new TextView(this);
        speakingHint.setText("Try to answer in 45 seconds.");
        speakingHint.setTextColor(Color.rgb(231, 76, 60));
        speakingHint.setTextSize(13);
        speakingHint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        speakingHint.setPadding(dp(4), 0, 0, dp(8));
        speakingHint.setVisibility(View.GONE);
        inputPanel.addView(speakingHint);

        immersionHint = new TextView(this);
        immersionHint.setText("Reading practice. Use the 'Listen' button for TTS.");
        immersionHint.setTextColor(Color.rgb(39, 174, 96));
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
        largeMicDrawable.setColor(Color.rgb(231, 76, 60));
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
                
                // Reset all specific UI elements first
                writingHelper.setVisibility(View.GONE);
                speakingHint.setVisibility(View.GONE);
                immersionHint.setVisibility(View.GONE);
                largeMicButton.setVisibility(View.GONE);
                userInput.setMinLines(4);
                userInput.setHint("Share your thoughts or complete the task...");
                inputPanel.setPadding(dp(20), dp(20), dp(20), dp(20));

                if ("Writing Studio".equals(effective)) {
                    writingHelper.setText("📝 Adaptive UI: Optimized for long-form writing.");
                    writingHelper.setVisibility(View.VISIBLE);
                    userInput.setMinLines(10);
                    userInput.setHint("Start writing your essay or report here...");
                    inputPanel.setPadding(dp(20), dp(24), dp(20), dp(24));
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
                
                // Final unified call to sync mic visibility and speaking hints
                updateMicButtonVisibility();
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
        scrollView.setBackgroundColor(Color.rgb(248, 250, 253));

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

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(32));
        scrollView.addView(content);

        content.addView(sectionHeader("⚙️ Connection Settings"));
        LinearLayout configPanel = panel();
        content.addView(configPanel);
        configPanel.addView(label("Ollama Server URL"));
        serverUrlField = field(defaultServerUrl());
        configPanel.addView(serverUrlField);
        configPanel.addView(hintText("USB: adb reverse tcp:11434 tcp:11434. Emulator: 10.0.2.2."));
        
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

        testButton.setOnClickListener(v -> testConnection());

        return scrollView;
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(20), dp(20), dp(20));
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(dp(16));
        panel.setBackground(drawable);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            panel.setElevation(dp(3));
        }
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
        label.setTextColor(Color.rgb(100, 116, 139));
        label.setTextSize(12);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            label.setLetterSpacing(0.12f);
        }
        label.setPadding(dp(4), dp(32), 0, dp(12));
        return label;
    }

    private TextView label(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(Color.rgb(68, 83, 102));
        label.setTextSize(14);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setPadding(0, dp(8), 0, dp(4));
        return label;
    }

    private TextView hintText(String text) {
        TextView hint = new TextView(this);
        hint.setText(text);
        hint.setTextColor(Color.rgb(87, 99, 116));
        hint.setTextSize(12);
        hint.setPadding(0, dp(4), 0, dp(8));
        return hint;
    }

    private EditText field(String hint) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setHint(hint);
        field.setTextColor(Color.rgb(24, 33, 45));
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
        field.setMinLines(4);
        field.setGravity(Gravity.TOP | Gravity.START);
        field.setTextSize(16);
        field.setTextColor(Color.rgb(24, 33, 45));
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        field.setPadding(dp(12), dp(12), dp(12), dp(12));
        field.setBackground(inputBackground());
        return field;
    }

    private GradientDrawable inputBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.rgb(248, 250, 253));
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(1), Color.rgb(209, 218, 229));
        return drawable;
    }

    private Spinner spinner(List<String> values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                values
        );
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
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextSize(16);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.rgb(63, 81, 181));
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
        button.setTextColor(Color.rgb(63, 81, 181));
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextSize(16);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(dp(12));
        drawable.setStroke(dp(1.5f), Color.rgb(226, 232, 240));
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

    private void updateStepIndicator(int stepIndex) {
        if (stepViews == null) return;
        for (int i = 0; i < stepViews.length; i++) {
            if (i == stepIndex) {
                stepViews[i].setTextColor(Color.rgb(63, 81, 181));
                stepViews[i].setAlpha(1.0f);
                GradientDrawable active = new GradientDrawable();
                active.setColor(Color.rgb(232, 240, 254));
                active.setCornerRadius(dp(12));
                stepViews[i].setBackground(active);
            } else {
                stepViews[i].setTextColor(Color.rgb(160, 174, 192));
                stepViews[i].setAlpha(0.7f);
                stepViews[i].setBackground(null);
            }
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void loadPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String savedServer = prefs.getString(PREF_SERVER, defaultServerUrl());
        if (isEmulatorOnlyAddressOnPhysicalDevice(savedServer)) {
            savedServer = defaultServerUrl();
        }
        serverUrlField.setText(savedServer);
        modelField.setText(prefs.getString(PREF_MODEL, DEFAULT_MODEL));
    }

    private void savePrefs() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(PREF_SERVER, serverUrl())
                .putString(PREF_MODEL, model())
                .apply();
    }

    private void startChain() {
        savePrefs();
        userResponseCount = 0;
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
            currentStepView.setText("Current step: complete");
            updateStepIndicator(4); // Done
            updateMicButtonVisibility();
            return;
        }
        chainIndex++;
        String step = chain.get(chainIndex);
        
        int uiIndex = 2; // Default to Drill
        String uiName = "Drill Phase";
        
        if (step.contains("Opening")) { uiIndex = 0; uiName = "Opening"; }
        else if (step.contains("Recall")) { uiIndex = 1; uiName = "Recall Phase"; }
        else if (step.contains("Error Analyst")) { uiIndex = 3; uiName = "Error Analysis"; }
        else if (step.contains("Closing")) { uiIndex = 4; uiName = "Closing Phase"; }
        
        currentStepView.setText(uiName.toUpperCase(Locale.US));
        updateStepIndicator(uiIndex);

        updateMicButtonVisibility();
        sendOllama(stepPrompt(step), step);
    }

    private void sendCurrentInput() {
        String text = userInput.getText().toString().trim();
        if (text.isEmpty()) {
            setStatus("Type a response first.");
            return;
        }

        if (userResponseCount >= MAX_RESPONSES_PER_SESSION) {
            appendSystem("Session limit reached (5 responses). Please click 'Next Step' to wrap up.");
            userInput.setText("");
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
        if (userResponseCount >= MAX_RESPONSES_PER_SESSION) {
            prompt = "Learner response:\n" + text + "\n\nThis is the FINAL interaction. Provide brief feedback, then explicitly tell the learner that the practice session is complete. Do not ask another question.";
        }

        sendOllama(prompt, step);
    }

    private void testConnection() {
        savePrefs();
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

    private void sendOllama(String userPrompt, String step) {
        String setupIssue = ollamaSetupIssue();
        if (setupIssue != null) {
            appendSystem(setupIssue);
            setStatus("Update the Ollama server URL for this device.");
            return;
        }
        setBusy(true);
        setStatus("Contacting Ollama...");
        executor.execute(() -> {
            try {
                String response = postChat(systemPromptForStep(step), userPrompt);
                mainHandler.post(() -> {
                    appendAssistant(step, response);
                    setStatus("Ready.");
                    setBusy(false);
                });
            } catch (Exception exception) {
                mainHandler.post(() -> {
                    appendSystem("Error: " + ollamaConnectionErrorMessage(exception));
                    setStatus(ollamaConnectionStatusMessage());
                    setBusy(false);
                });
            }
        });
    }

    private String postChat(String systemPrompt, String userPrompt) throws Exception {
        URL url = new URL(trimSlash(serverUrl()) + "/api/chat");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(120000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", systemPrompt));
        messages.put(new JSONObject().put("role", "user").put("content", userPrompt));

        JSONObject payload = new JSONObject();
        payload.put("model", model());
        payload.put("stream", false);
        payload.put("messages", messages);

        JSONObject options = new JSONObject();
        options.put("temperature", 0.4);
        options.put("top_p", 0.8);
        options.put("repeat_penalty", 1.15);
        options.put("num_predict", 90);
        payload.put("options", options);

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
                return "Act as Supervisor GPT closing step. Consolidate progress and output a compact memory_update JSON.";
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

    private void appendAssistant(String step, String text) {
        String cleanText = text;
        if ("Supervisor Closing".equals(step)) {
            cleanText = processMemoryUpdate(text);
        }
        appendTranscript(step, cleanText);
    }

    private String processMemoryUpdate(String text) {
        try {
            int start = text.indexOf("{");
            int end = text.lastIndexOf("}");
            if (start != -1 && end != -1 && end > start) {
                String jsonStr = text.substring(start, end + 1);
                JSONObject update = new JSONObject(jsonStr);
                if (update.has("memory_update")) {
                    applyMemoryUpdate(update.getJSONObject("memory_update"));
                    // Return text without the JSON block for a cleaner UI
                    return (text.substring(0, start) + text.substring(end + 1)).trim();
                }
            }
        } catch (Exception exception) {
            appendSystem("Memory update failed: " + exception.getMessage());
        }
        return text;
    }

    private void applyMemoryUpdate(JSONObject update) {
        String[] files = {"learner_profile.json", "progress_tracker.json", "review_queue.json"};
        for (String fileName : files) {
            if (update.has(fileName)) {
                try {
                    File file = new File(getFilesDir(), fileName);
                    JSONObject current = new JSONObject(readText(file));
                    JSONObject newData = update.getJSONObject(fileName);
                    
                    // Merge logic: only allow-listed fields or preserve existing
                    java.util.Iterator<String> keys = newData.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        current.put(key, newData.get(key));
                    }
                    
                    writeText(file, current.toString(2));
                    appendSystem("Updated " + fileName);
                } catch (Exception ignored) {}
            }
        }
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
        if (speaker.contains("Supervisor") || speaker.contains("Analyst") || speaker.contains("Coach")) {
            speakerLabel = "Coach";
        }
        speakerView.setText(speakerLabel.toUpperCase(Locale.US));
        speakerView.setTextSize(10);
        speakerView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            speakerView.setLetterSpacing(0.1f);
        }
        
        if ("You".equals(speaker)) {
            drawable.setColor(Color.rgb(240, 244, 255));
            speakerView.setTextColor(Color.rgb(63, 81, 181));
        } else if ("System".equals(speaker)) {
            drawable.setColor(Color.TRANSPARENT);
            speakerView.setTextColor(Color.rgb(113, 128, 150));
            speakerView.setText("NOTIFICATION");
        } else if ("Input Immersion".equals(speaker)) {
            drawable.setColor(Color.WHITE);
            drawable.setStroke(dp(2), Color.rgb(34, 197, 94));
            speakerView.setTextColor(Color.rgb(21, 128, 61));
            speakerView.setText("📖 READING IMMERSION");
            speakerView.setGravity(Gravity.CENTER);
            card.setPadding(dp(24), dp(24), dp(24), dp(24));
        } else {
            drawable.setColor(Color.WHITE);
            drawable.setStroke(dp(1), Color.rgb(238, 242, 247));
            speakerView.setTextColor(Color.rgb(45, 55, 72));
        }
        
        card.setBackground(drawable);
        if (!"System".equals(speaker) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            card.setElevation(dp(2));
        }
        
        TextView contentView = new TextView(this);
        contentView.setText(text.trim());
        contentView.setTextColor(Color.rgb(26, 32, 44));
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
        
        if ("Input Immersion".equals(speaker)) {
            Button listenButton = secondaryButton("🔊 Listen to Content");
            if (tts == null) {
                listenButton.setEnabled(false);
                listenButton.setAlpha(0.6f);
                listenButton.setText("🔊 TTS Initializing...");
            }
            listenButton.setOnClickListener(v -> {
                if (tts != null) {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "immersion");
                }
            });
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
            btnParams.setMargins(0, dp(24), 0, 0);
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
            contentView.setTextColor(Color.rgb(113, 128, 150));
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

    private void setStatus(String text) {
        statusView.setText(text);
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
        return value.isEmpty() ? DEFAULT_MODEL : value;
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
            writeText(file, payload.toString(2));
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

    private void writeText(File file, String content) {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                java.nio.file.Files.newOutputStream(file.toPath()), StandardCharsets.UTF_8))) {
            writer.write(content);
        } catch (Exception ignored) {
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
                    setStatus("Mic Error: " + error);
                    updateMicButtonsText("🎤 Record Voice");
                }
                @Override public void onResults(Bundle results) {
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
                largeMicButton.setBackgroundColor(Color.rgb(44, 62, 80));
            } else {
                largeMicButton.setText("🎤 Tap to Speak");
                GradientDrawable largeMicDrawable = new GradientDrawable();
                largeMicDrawable.setColor(Color.rgb(231, 76, 60));
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
        if (micButton.getText().toString().contains("Record") || largeMicButton.getText().toString().contains("Speak")) {
            startListening();
        } else {
            stopListening();
        }
    }

    private void startListening() {
        if (speechRecognizer == null) return;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        speechRecognizer.startListening(intent);
        updateMicButtonsText("🛑 Stop Recording");
    }

    private void stopListening() {
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
                drawable.setColor(Color.rgb(232, 240, 254));
                drawable.setStroke(dp(2), Color.rgb(63, 81, 181));
                iconView.setAlpha(1.0f);
                labelView.setTextColor(Color.rgb(63, 81, 181));
            } else {
                drawable.setColor(Color.WHITE);
                drawable.setStroke(dp(1), Color.rgb(226, 232, 240));
                iconView.setAlpha(0.5f);
                labelView.setTextColor(Color.rgb(100, 116, 139));
            }
            card.setBackground(drawable);
        }
    }
}
