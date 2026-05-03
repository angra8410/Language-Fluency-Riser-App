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

    private EditText serverUrlField;
    private EditText modelField;
    private EditText userInput;
    private ScrollView mainScrollView;
    private LinearLayout transcriptContainer;
    private TextView statusView;
    private FrameLayout tabContentContainer;
    private View homeTab, historyTab, settingsTab;
    private View homeNav, historyNav, settingsNav;
    private int currentTab = 0;
    private TextView currentStepView;
    private Spinner focusSpinner;
    private Spinner dayTypeSpinner;
    private Button startButton;
    private Button nextButton;
    private Button saveButton;
    private Button sendButton;
    private Button testButton;
    private Button micButton;
    private SpeechRecognizer speechRecognizer;
    private ProgressBar progressBar;
    private TextView[] stepViews;
    private static final String[] STEPS = {"Plan", "Instructions", "Interact", "Review", "Done"};
    private int chainIndex = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ensureMemoryFiles();
        buildUi();
        loadPrefs();
        updateStepIndicator(0);
        appendSystem("Welcome! Configure your settings and tap 'Begin Practice' to start.");
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
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
        mainScrollView.setBackgroundColor(Color.rgb(245, 247, 250));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(24), dp(20), dp(40));
        mainScrollView.addView(content);

        // Premium Hero Header
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(4), 0, 0, dp(28));
        
        TextView title = new TextView(this);
        title.setText("Learning Hub");
        title.setTextColor(Color.rgb(26, 32, 44));
        title.setTextSize(30);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        hero.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Elevate your English to C1 mastery");
        subtitle.setTextColor(Color.rgb(113, 128, 150));
        subtitle.setTextSize(16);
        subtitle.setPadding(0, dp(4), 0, 0);
        hero.addView(subtitle);
        content.addView(hero);

        // Progress Section - Cleaner and more focused
        content.addView(sectionHeader("📊 Session Progress"));
        LinearLayout progressPanel = panel();
        content.addView(progressPanel);
        
        LinearLayout stepContainer = horizontal();
        stepContainer.setPadding(0, dp(8), 0, dp(20));
        stepViews = new TextView[STEPS.length];
        for (int i = 0; i < STEPS.length; i++) {
            stepViews[i] = new TextView(this);
            stepViews[i].setText(STEPS[i]);
            stepViews[i].setTextSize(10);
            stepViews[i].setGravity(Gravity.CENTER);
            stepViews[i].setPadding(dp(2), dp(6), dp(2), dp(6));
            stepViews[i].setTypeface(Typeface.DEFAULT_BOLD);
            stepContainer.addView(stepViews[i], weightParams());
        }
        progressPanel.addView(stepContainer);

        currentStepView = new TextView(this);
        currentStepView.setText("READY TO START");
        currentStepView.setTextColor(Color.rgb(63, 81, 181));
        currentStepView.setTextSize(13);
        currentStepView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        currentStepView.setGravity(Gravity.CENTER);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            currentStepView.setLetterSpacing(0.1f);
        }
        progressPanel.addView(currentStepView);

        // Configuration Section
        content.addView(sectionHeader("🎯 Practice Setup"));
        LinearLayout modePanel = panel();
        content.addView(modePanel);
        
        modePanel.addView(label("Focus Area"));
        focusSpinner = spinner(Arrays.asList(
                "Auto",
                "Speaking Lab",
                "Writing Studio",
                "Input Immersion",
                "Diagnostic Coach"
        ));
        modePanel.addView(focusSpinner);
        
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
        LinearLayout inputPanel = panel();
        content.addView(inputPanel);
        userInput = multiLineField("Share your thoughts or complete the task...");
        inputPanel.addView(userInput);

        LinearLayout inputActions = horizontal();
        micButton = secondaryButton("🎤 Record");
        micButton.setVisibility(View.GONE);
        sendButton = button("Submit Response");
        inputActions.addView(micButton, weightParams());
        inputActions.addView(sendButton, weightParams());
        inputPanel.addView(inputActions);

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

        return mainScrollView;
    }

    private View buildHistoryTab() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(32));
        scrollView.addView(content);

        content.addView(sectionHeader("📜 Practice History"));
        
        LinearLayout historyPanel = panel();
        content.addView(historyPanel);
        
        TextView placeholder = new TextView(this);
        placeholder.setText("No practice history yet.\nYour completed sessions will appear here.");
        placeholder.setTextColor(Color.rgb(100, 110, 130));
        placeholder.setTextSize(14);
        placeholder.setGravity(Gravity.CENTER);
        placeholder.setPadding(0, dp(64), 0, dp(64));
        historyPanel.addView(placeholder);

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
        panel.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(dp(12));
        panel.setBackground(drawable);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            panel.setElevation(dp(2));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(16));
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
        label.setTextColor(Color.rgb(113, 128, 150));
        label.setTextSize(11);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            label.setLetterSpacing(0.08f);
        }
        label.setPadding(dp(4), dp(24), 0, dp(12));
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
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextSize(15);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.rgb(63, 81, 181));
        drawable.setCornerRadius(dp(8));
        button.setBackground(drawable);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            button.setElevation(dp(2));
            button.setStateListAnimator(null); // Remove default button shadow on press for custom feel
        }
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(Color.rgb(63, 81, 181));
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextSize(15);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(2), Color.rgb(63, 81, 181));
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
                stepViews[i].setTextColor(Color.WHITE);
                GradientDrawable active = new GradientDrawable();
                active.setColor(Color.rgb(63, 81, 181));
                active.setCornerRadius(dp(100)); // Capsule shape
                stepViews[i].setBackground(active);
            } else {
                stepViews[i].setTextColor(Color.rgb(160, 174, 192));
                stepViews[i].setBackground(null);
            }
        }
    }

    private int dp(int value) {
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
        chain.clear();
        chain.addAll(buildChain());
        chainIndex = -1;
        transcript.setLength(0);
        transcriptContainer.removeAllViews();
        appendSystem("Chain: " + String.join(" -> ", chain));
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
        currentStepView.setText("Current step: " + step);
        
        // Map chain progress to 5-step UI indicator
        if (chainIndex == 0) updateStepIndicator(0); // Plan
        else if (chainIndex == 1) updateStepIndicator(1); // Instructions
        else if (chainIndex < chain.size() - 2) updateStepIndicator(2); // Interact
        else if (chainIndex < chain.size() - 1) updateStepIndicator(3); // Review
        else updateStepIndicator(4); // Done

        updateMicButtonVisibility();
        sendOllama(stepPrompt(step), step);
    }

    private void sendCurrentInput() {
        String text = userInput.getText().toString().trim();
        if (text.isEmpty()) {
            setStatus("Type a response first.");
            return;
        }
        String step = currentStep();
        appendUser(text);
        userInput.setText("");
        updateStepIndicator(2); // Still interacting
        sendOllama("Learner response:\n" + text + "\n\nContinue the current step. Give feedback, then tell me exactly what to do next.", step);
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

    private String stepPrompt(String step) {
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
        appendTranscript(step, text);
    }

    private void appendTranscript(String speaker, String text) {
        if (transcript.length() == 0) {
            transcriptContainer.removeAllViews();
        }
        transcript.append("\n\n[").append(speaker).append("]\n").append(text.trim());
        
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(12), dp(8), dp(12), dp(8));
        
        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(16));
        
        TextView speakerView = new TextView(this);
        speakerView.setText(speaker.toUpperCase(Locale.US));
        speakerView.setTextSize(10);
        speakerView.setTypeface(Typeface.DEFAULT_BOLD);
        
        if ("You".equals(speaker)) {
            drawable.setColor(Color.rgb(232, 240, 254));
            speakerView.setTextColor(Color.rgb(25, 103, 210));
        } else if ("System".equals(speaker)) {
            drawable.setColor(Color.rgb(241, 243, 244));
            speakerView.setTextColor(Color.rgb(95, 99, 104));
        } else {
            drawable.setColor(Color.WHITE);
            drawable.setStroke(dp(1), Color.rgb(218, 220, 224));
            speakerView.setTextColor(Color.rgb(63, 81, 181));
        }
        
        bubble.setBackground(drawable);
        
        TextView contentView = new TextView(this);
        contentView.setText(text.trim());
        contentView.setTextColor(Color.rgb(32, 33, 36));
        contentView.setTextSize(15);
        contentView.setTextIsSelectable(true);
        
        bubble.addView(speakerView);
        bubble.addView(contentView);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(12));
        
        if ("You".equals(speaker)) {
            params.gravity = Gravity.END;
            params.setMargins(dp(48), 0, 0, dp(12));
            bubble.setGravity(Gravity.END);
        } else if ("System".equals(speaker)) {
            params.gravity = Gravity.CENTER;
            params.setMargins(dp(24), 0, dp(24), dp(12));
            bubble.setGravity(Gravity.CENTER);
        } else {
            params.gravity = Gravity.START;
            params.setMargins(0, 0, dp(48), dp(12));
            bubble.setGravity(Gravity.START);
        }
        
        transcriptContainer.addView(bubble, params);
        
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
            payload.put("date", LocalDate.now().toString());
            payload.put("model", model());
            payload.put("server_url", serverUrl());
            payload.put("chain", new JSONArray(chain));
            payload.put("transcript", transcript.toString());

            File file = new File(getFilesDir(), "session_log_" + LocalDate.now() + ".json");
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
                    micButton.setText("🎤 Record Voice");
                }
                @Override public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        userInput.setText(matches.get(0));
                    }
                    setStatus("Ready.");
                    micButton.setText("🎤 Record Voice");
                }
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
        }
    }

    private void toggleMic() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 100);
            return;
        }
        if (micButton.getText().toString().contains("Record")) {
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
        micButton.setText("🛑 Stop Recording");
    }

    private void stopListening() {
        if (speechRecognizer != null) speechRecognizer.stopListening();
        micButton.setText("🎤 Record Voice");
    }

    private void updateMicButtonVisibility() {
        String step = currentStep();
        if ("Speaking Lab".equals(step)) {
            micButton.setVisibility(View.VISIBLE);
        } else {
            micButton.setVisibility(View.GONE);
        }
    }
}
