package com.ultron.assistant;

import android.app.*;
import android.content.Intent;
import android.os.IBinder;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.Locale;

public class UltronService extends Service implements RecognitionListener {
    private static final String CHANNEL = "ultron_voice";
    private SpeechRecognizer sr;
    private Intent recognizerIntent;
    private TextToSpeech tts;
    private OverlayController overlay;
    private boolean waitingForCommand = false;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        Notification n = new Notification.Builder(this, CHANNEL)
                .setContentTitle("ULTRON is listening")
                .setContentText("Say: Hey ULTRON")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true).build();
        startForeground(7, n);

        overlay = new OverlayController(this);
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(new Locale("ta", "IN"));
                if (r < 0) tts.setLanguage(Locale.ENGLISH);
            }
        });
        sr = SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ta-IN");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        startListening();
    }

    private void startListening() {
        try { sr.startListening(recognizerIntent); } catch (Exception ignored) {}
    }

    private void processText(String raw) {
        String text = raw == null ? "" : raw.toLowerCase(Locale.ROOT).trim();
        if (!waitingForCommand) {
            if (text.contains("hey ultron") || text.contains("ஹே அல்ட்ரான்") || text.contains("ultron")) {
                waitingForCommand = true;
                overlay.showListening();
                speak("சொல்லுங்க பாஸ்");
                restartSoon(700);
            }
        } else {
            if (!text.isEmpty() && !text.contains("hey ultron") && !text.equals("ultron")) {
                waitingForCommand = false;
                overlay.hide();
                CommandRouter.handle(this, raw, this::speak);
            }
        }
    }

    private void speak(String s) {
        if (tts != null) tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "ultron");
    }

    private void restartSoon(long ms) {
        new android.os.Handler(getMainLooper()).postDelayed(this::startListening, ms);
    }

    @Override public void onResults(android.os.Bundle results) {
        ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list != null && !list.isEmpty()) processText(list.get(0));
        restartSoon(300);
    }
    @Override public void onPartialResults(android.os.Bundle partialResults) {
        ArrayList<String> list = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list != null && !list.isEmpty()) processText(list.get(0));
    }
    @Override public void onError(int error) { restartSoon(800); }
    @Override public void onReadyForSpeech(android.os.Bundle params) {}
    @Override public void onBeginningOfSpeech() {}
    @Override public void onRmsChanged(float rmsdB) {}
    @Override public void onBufferReceived(byte[] buffer) {}
    @Override public void onEndOfSpeech() {}
    @Override public void onEvent(int eventType, android.os.Bundle params) {}

    @Override public void onDestroy() {
        try { sr.destroy(); } catch (Exception ignored) {}
        try { tts.shutdown(); } catch (Exception ignored) {}
        overlay.hide();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }

    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "ULTRON voice", NotificationManager.IMPORTANCE_LOW));
        }
    }
}
