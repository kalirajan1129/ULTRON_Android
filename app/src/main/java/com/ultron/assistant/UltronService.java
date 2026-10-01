package com.ultron.assistant;

import android.app.*;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Privacy-first ULTRON service.
 *
 * Important: this service stays alive in the foreground, but it DOES NOT keep
 * SpeechRecognizer running. The microphone is opened only after ACTION_WAKE,
 * which is triggered by the triple-volume shortcut from the accessibility service.
 */
public class UltronService extends Service implements RecognitionListener {
    public static final String ACTION_WAKE = "com.ultron.assistant.ACTION_WAKE";
    private static final String CHANNEL = "ultron_voice";
    private static final int NOTIFICATION_ID = 7;
    private static final long COMMAND_TIMEOUT_MS = 12000;

    private SpeechRecognizer sr;
    private Intent recognizerIntent;
    private TextToSpeech tts;
    private OverlayController overlay;
    private final Handler handler = new Handler();
    private boolean listeningForCommand = false;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, buildReadyNotification());

        overlay = new OverlayController(this);
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(new Locale("ta", "IN"));
                if (r < 0) tts.setLanguage(Locale.ENGLISH);
                // Keep this generic so it does not imitate a specific real person's voice.
                tts.setPitch(0.82f);
                tts.setSpeechRate(0.92f);
            }
        });
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_WAKE.equals(intent.getAction())) {
            wakeAndListen();
        }
        return START_STICKY;
    }

    private Notification buildReadyNotification() {
        return new Notification.Builder(this, CHANNEL)
                .setContentTitle("ULTRON ready")
                .setContentText("Mic off • press Volume Up 3× to wake")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build();
    }

    private void wakeAndListen() {
        if (listeningForCommand) return;
        listeningForCommand = true;
        overlay.showListening();
        speak("சொல்லுங்க பாஸ்");

        // Give TTS a moment to finish before opening speech recognition so ULTRON
        // does not transcribe its own prompt.
        handler.postDelayed(this::beginOneShotRecognition, 1050);
        handler.postDelayed(this::finishListening, COMMAND_TIMEOUT_MS);
    }

    private void beginOneShotRecognition() {
        if (!listeningForCommand) return;
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                speak("Voice recognition கிடைக்கவில்லை பாஸ்");
                finishListening();
                return;
            }
            if (sr != null) {
                try { sr.destroy(); } catch (Exception ignored) {}
            }
            sr = SpeechRecognizer.createSpeechRecognizer(this);
            sr.setRecognitionListener(this);

            recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ta-IN");
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            sr.startListening(recognizerIntent);
        } catch (Exception e) {
            finishListening();
        }
    }

    private void handleCommand(String raw) {
        if (!listeningForCommand) return;
        finishListening();
        if (raw == null || raw.trim().isEmpty()) return;
        CommandRouter.handle(this, raw, this::speak);
    }

    private void finishListening() {
        if (!listeningForCommand && sr == null) return;
        listeningForCommand = false;
        handler.removeCallbacksAndMessages(null);
        overlay.hide();
        if (sr != null) {
            try { sr.cancel(); } catch (Exception ignored) {}
            try { sr.destroy(); } catch (Exception ignored) {}
            sr = null;
        }
    }

    private void speak(String s) {
        if (tts != null) tts.speak(s, TextToSpeech.QUEUE_FLUSH, null, "ultron");
    }

    @Override public void onResults(android.os.Bundle results) {
        ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        handleCommand(list != null && !list.isEmpty() ? list.get(0) : "");
    }

    @Override public void onError(int error) { finishListening(); }
    @Override public void onReadyForSpeech(android.os.Bundle params) {}
    @Override public void onBeginningOfSpeech() {}
    @Override public void onRmsChanged(float rmsdB) {}
    @Override public void onBufferReceived(byte[] buffer) {}
    @Override public void onEndOfSpeech() {}
    @Override public void onPartialResults(android.os.Bundle partialResults) {}
    @Override public void onEvent(int eventType, android.os.Bundle params) {}

    @Override public void onDestroy() {
        finishListening();
        try { if (tts != null) tts.shutdown(); } catch (Exception ignored) {}
        overlay.hide();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "ULTRON ready", NotificationManager.IMPORTANCE_LOW));
        }
    }
}
