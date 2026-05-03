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

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ArrayList<String> chain = new ArrayList<>();
    private final StringBuilder transcript = new StringBuilder();

    private EditText serverUrlField;
    private EditText modelField;
    private EditText userInput;
    private TextView transcriptView;
    private TextView statusView;
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
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Color.rgb(243, 246, 250));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(24));
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("Language Fluency Riser");
        title.setTextColor(Color.rgb(21, 35, 52));
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Local C1 English coaching chain powered by Ollama.");
        subtitle.setTextColor(Color.rgb(80, 93, 110));
        subtitle.setTextSize(15);
        subtitle.setPadding(0, dp(4), 0, dp(14));
        root.addView(subtitle);

        TextView settingsLabel = label("SETTINGS");
        settingsLabel.setPadding(0, 0, 0, dp(8));
        root.addView(settingsLabel);

        LinearLayout configPanel = panel();
        root.addView(configPanel);
        configPanel.addView(label("Ollama server"));
        serverUrlField = field(defaultServerUrl());
        configPanel.addView(serverUrlField);
        configPanel.addView(hintText("Phone over USB: adb reverse tcp:11434 tcp:11434. Emulator: http://10.0.2.2:11434."));
        configPanel.addView(label("Model"));
        modelField = field(DEFAULT_MODEL);
        configPanel.addView(modelField);

        LinearLayout configActions = horizontal();
        testButton = button("Test Connection");
        configActions.addView(testButton, weightParams());
        configPanel.addView(configActions);

        TextView sessionLabel = label("SESSION PROGRESS");
        sessionLabel.setPadding(0, dp(12), 0, dp(8));
        root.addView(sessionLabel);

        LinearLayout stepContainer = horizontal();
        stepContainer.setPadding(0, 0, 0, dp(12));
        stepViews = new TextView[STEPS.length];
        for (int i = 0; i < STEPS.length; i++) {
            stepViews[i] = new TextView(this);
            stepViews[i].setText(STEPS[i]);
            stepViews[i].setTextSize(11);
            stepViews[i].setGravity(Gravity.CENTER);
            stepViews[i].setPadding(dp(4), dp(2), dp(4), dp(2));
            stepViews[i].setTypeface(Typeface.DEFAULT_BOLD);
            stepContainer.addView(stepViews[i], weightParams());
        }
        root.addView(stepContainer);

        LinearLayout modePanel = panel();
        root.addView(modePanel);
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

        LinearLayout actionPanel = panel();
        root.addView(actionPanel);
        LinearLayout rowOne = horizontal();
        startButton = button("Begin Practice");
        nextButton = button("Continue to Next Task");
        rowOne.addView(startButton, weightParams());
        rowOne.addView(nextButton, weightParams());
        actionPanel.addView(rowOne);

        LinearLayout rowTwo = horizontal();
        saveButton = button("Save Session");
        rowTwo.addView(saveButton, weightParams());
        actionPanel.addView(rowTwo);

        currentStepView = new TextView(this);
        currentStepView.setText("Waiting to start...");
        currentStepView.setTextColor(Color.rgb(36, 54, 76));
        currentStepView.setTextSize(15);
        currentStepView.setTypeface(Typeface.DEFAULT_BOLD);
        currentStepView.setPadding(0, dp(8), 0, 0);
        actionPanel.addView(currentStepView);

        LinearLayout transcriptPanel = panel();
        root.addView(transcriptPanel);
        transcriptPanel.addView(label("Conversation History"));
        transcriptView = new TextView(this);
        transcriptView.setText("Your practice session will appear here.");
        transcriptView.setTextColor(Color.rgb(140, 150, 160));
        transcriptView.setTextSize(15);
        transcriptView.setLineSpacing(0, 1.12f);
        transcriptView.setTextIsSelectable(true);
        transcriptPanel.addView(transcriptView);

        LinearLayout inputPanel = panel();
        root.addView(inputPanel);
        inputPanel.addView(label("Your Response"));
        userInput = multiLineField("Type your answer here...");
        inputPanel.addView(userInput);

        LinearLayout inputActions = horizontal();
        micButton = button("🎤 Record Voice");
        micButton.setVisibility(View.GONE);
        sendButton = button("Submit Answer");
        inputActions.addView(micButton, weightParams());
        inputActions.addView(sendButton, weightParams());
        inputPanel.addView(inputActions);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar);

        statusView = new TextView(this);
        statusView.setTextColor(Color.rgb(88, 101, 118));
        statusView.setTextSize(13);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, dp(14), 0, 0);
        root.addView(statusView);

        setContentView(scrollView);
        startButton.setOnClickListener(v -> startChain());
        nextButton.setOnClickListener(v -> nextStep());
        saveButton.setOnClickListener(v -> saveSessionLog());
        sendButton.setOnClickListener(v -> sendCurrentInput());
        testButton.setOnClickListener(v -> testConnection());
        micButton.setOnClickListener(v -> toggleMic());
        initSpeechRecognizer();
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(14), dp(14), dp(14));
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(dp(8));
        drawable.setStroke(dp(1), Color.rgb(220, 227, 236));
        panel.setBackground(drawable);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(12));
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

    private TextView label(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(Color.rgb(68, 83, 102));
        label.setTextSize(13);
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
        field.setTextSize(15);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        field.setPadding(dp(10), 0, dp(10), 0);
        field.setBackground(inputBackground());
        field.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46)
        ));
        return field;
    }

    private EditText multiLineField(String hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setMinLines(4);
        field.setGravity(Gravity.TOP | Gravity.START);
        field.setTextSize(15);
        field.setTextColor(Color.rgb(24, 33, 45));
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        field.setPadding(dp(10), dp(10), dp(10), dp(10));
        field.setBackground(inputBackground());
        return field;
    }

    private GradientDrawable inputBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.rgb(250, 252, 255));
        drawable.setCornerRadius(dp(6));
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
        return spinner;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
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
                active.setCornerRadius(dp(4));
                stepViews[i].setBackground(active);
            } else {
                stepViews[i].setTextColor(Color.rgb(120, 130, 140));
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
        transcriptView.setText("");
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
        return "You are a local English fluency coach for a Spanish-speaking learner moving from B2 to C1.\n"
                + "Use the learner memory below, but do not invent hidden facts. Keep answers practical and concise.\n"
                + "Use English as the default language. Use Spanish only when it prevents confusion.\n"
                + "Always adapt difficulty using these rules: recurring weakness means more practice; scores above 85 increase difficulty; scores below 60 simplify; weak fluency means speaking pressure; weak accuracy means controlled correction.\n"
                + "Current chain step: " + step + "\n\n"
                + roleInstructions(step)
                + "\n\nMemory JSON files:\n" + readMemoryBlock();
    }

    private String roleInstructions(String step) {
        switch (step) {
            case "Supervisor Opening":
                return "Act as Supervisor GPT. Inspect the memory, choose today's priorities, order, difficulty, and first task. Output a short plan plus the exact first instruction.";
            case "Supervisor Closing":
                return "Act as Supervisor GPT closing step. Consolidate progress, name the next priority, and output a compact memory_update JSON with learner_profile, progress_tracker, and review_queue suggestions.";
            case "Recall & Drill":
                return "Act as Recall & Drill GPT. Run retrieval practice from the review queue and recurring errors. Ask one item at a time unless the learner asks otherwise.";
            case "Speaking Lab":
                return "Act as Speaking Lab GPT. Create speaking practice under pressure with natural phrasing, interruptions, spontaneity, and C1-level reformulations.";
            case "Writing Studio":
                return "Act as Writing Studio GPT. Give a writing task, then provide correction, C1 alternatives, structure feedback, and one focused rewrite drill.";
            case "Input Immersion":
                return "Act as Input Immersion GPT. Provide reading or listening-style input, comprehension checks, and active-use transfer into the learner's own sentences.";
            case "Error Analyst":
                return "Act as Error Analyst GPT. Identify recurring patterns, root causes, micro-drills, and review queue additions. This step is non-negotiable before closing.";
            case "Diagnostic Coach":
                return "Act as Diagnostic Coach GPT. Evaluate CEFR across speaking, writing, listening, reading, grammar, vocabulary, fluency, and pragmatics. Give scores and recalibration advice.";
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
            transcriptView.setTextColor(Color.rgb(24, 33, 45));
        }
        transcript.append("\n\n[").append(speaker).append("]\n").append(text.trim());
        transcriptView.setText(transcript.toString().trim());
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
