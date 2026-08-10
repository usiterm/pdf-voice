package com.pdfvoice.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.media.AudioAttributes;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.view.KeyEvent;

import androidx.annotation.Nullable;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;

public class PlaybackService extends Service {

    public static final String ACTION_PLAY_PAUSE =
            "com.pdfvoice.app.PLAY_PAUSE";

    public static final String ACTION_NEXT =
            "com.pdfvoice.app.NEXT";

    public static final String ACTION_PREVIOUS =
            "com.pdfvoice.app.PREVIOUS";

    public static final String ACTION_START =
            "com.pdfvoice.app.START";

    public static final String ACTION_STOP =
            "com.pdfvoice.app.STOP";

    public static final String EXTRA_SENTENCE =
            "sentence";

    private static final String CHANNEL_ID = "pdf_voice_playback";
    private static final int NOTIFICATION_ID = 10;

    private TextToSpeech tts;
    private MediaSession mediaSession;

    private final ArrayList<String> sentences = new ArrayList<>();

    private int currentSentence = 0;
    private boolean ready = false;
    private boolean playing = false;

    private SharedPreferences prefs;

    @Override
    public void onCreate() {
        super.onCreate();

        PDFBoxResourceLoader.init(getApplicationContext());

        prefs = getSharedPreferences("pdf_voice", MODE_PRIVATE);

        createNotificationChannel();
        setupMediaSession();
        setupTts();
    }

    private void setupMediaSession() {

        mediaSession = new MediaSession(
                this,
                "PDF Voice"
        );

        mediaSession.setCallback(new MediaSession.Callback() {

            @Override
            public void onPlay() {
                play();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onSkipToNext() {
                nextSentence();
            }

            @Override
            public void onSkipToPrevious() {
                previousSentence();
            }

            @Override
            public boolean onMediaButtonEvent(Intent mediaButtonIntent) {

                KeyEvent event =
                        mediaButtonIntent.getParcelableExtra(
                                Intent.EXTRA_KEY_EVENT
                        );

                if (event != null &&
                    event.getAction() == KeyEvent.ACTION_DOWN &&
                    event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {

                    if (playing) {
                        pause();
                    } else {
                        play();
                    }

                    return true;
                }

                return super.onMediaButtonEvent(mediaButtonIntent);
            }
        });

        mediaSession.setActive(true);
    }

    private void setupTts() {

        tts = new TextToSpeech(this, status -> {

            if (status == TextToSpeech.SUCCESS) {

                int result =
                        tts.setLanguage(Locale.ITALIAN);

                if (result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED) {

                    ready = true;

                    loadCurrentPdf();
                }
            }
        });

        tts.setAudioAttributes(
                new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(
                                AudioAttributes.CONTENT_TYPE_SPEECH
                        )
                        .build()
        );

        tts.setOnUtteranceProgressListener(
                new UtteranceProgressListener() {

                    @Override
                    public void onStart(String id) {

                        try {
                            int index = Integer.parseInt(id);

                            currentSentence = index;

                            savePosition();

                            sendSentenceUpdate(index);

                            updatePlaybackState();
                            updateNotification();

                        } catch (Exception ignored) {
                        }
                    }

                    @Override
                    public void onDone(String id) {

                        try {
                            int index = Integer.parseInt(id);

                            if (index == sentences.size() - 1) {
                                playing = false;
                                updatePlaybackState();
                                updateNotification();
                            }

                        } catch (Exception ignored) {
                        }
                    }

                    @Override
                    public void onError(String id) {
                        playing = false;
                        updatePlaybackState();
                        updateNotification();
                    }
                }
        );
    }

    private void loadCurrentPdf() {

        String uriString =
                prefs.getString("current_pdf_uri", null);

        if (uriString == null) {
            return;
        }

        Uri uri = Uri.parse(uriString);

        new Thread(() -> {

            ArrayList<String> extracted =
                    extractSentences(uri);

            synchronized (sentences) {

                sentences.clear();
                sentences.addAll(extracted);
            }

            currentSentence =
                    prefs.getInt("current_sentence", 0);

        }).start();
    }

    private ArrayList<String> extractSentences(Uri uri) {

        ArrayList<String> result = new ArrayList<>();

        try (
                InputStream input =
                        getContentResolver().openInputStream(uri);

                PDDocument document =
                        PDDocument.load(input)
        ) {

            PDFTextStripper stripper =
                    new PDFTextStripper();

            String text = stripper.getText(document);

            text = text.replaceAll("\\s+", " ").trim();

            if (!text.isEmpty()) {

                String[] parts =
                        text.split("(?<=[.!?])\\s+");

                for (String part : parts) {

                    part = part.trim();

                    if (!part.isEmpty()) {
                        result.add(part);
                    }
                }
            }

        } catch (Exception ignored) {
        }

        return result;
    }

    private void play() {

        if (!ready) {
            return;
        }

        if (sentences.isEmpty()) {
            loadCurrentPdf();
            return;
        }

        playing = true;

        tts.stop();

        for (int i = currentSentence;
             i < sentences.size();
             i++) {

            tts.speak(
                    sentences.get(i),
                    TextToSpeech.QUEUE_ADD,
                    null,
                    String.valueOf(i)
            );
        }

        updatePlaybackState();
        updateNotification();
    }

    private void pause() {

        if (tts != null) {
            tts.stop();
        }

        playing = false;

        updatePlaybackState();
        updateNotification();
    }

    private void nextSentence() {

        if (sentences.isEmpty()) {
            return;
        }

        tts.stop();

        currentSentence =
                Math.min(
                        currentSentence + 1,
                        sentences.size() - 1
                );

        savePosition();

        play();
    }

    private void previousSentence() {

        if (sentences.isEmpty()) {
            return;
        }

        tts.stop();

        currentSentence =
                Math.max(
                        currentSentence - 1,
                        0
                );

        savePosition();

        play();
    }

    private void playSingleSentence() {

        if (!ready || sentences.isEmpty()) {
            return;
        }

        playing = true;

        tts.speak(
                sentences.get(currentSentence),
                TextToSpeech.QUEUE_FLUSH,
                null,
                String.valueOf(currentSentence)
        );

        updatePlaybackState();
        updateNotification();
    }

    private void savePosition() {

        prefs.edit()
                .putInt("current_sentence", currentSentence)
                .apply();
    }

    private void sendSentenceUpdate(int index) {

        Intent intent =
                new Intent("com.pdfvoice.SENTENCE_CHANGED");

        intent.setPackage(getPackageName());

        intent.putExtra(EXTRA_SENTENCE, index);

        sendBroadcast(intent);
    }

    private void updatePlaybackState() {

        int state =
                playing
                        ? PlaybackState.STATE_PLAYING
                        : PlaybackState.STATE_PAUSED;

        long actions =
                PlaybackState.ACTION_PLAY |
                PlaybackState.ACTION_PAUSE |
                PlaybackState.ACTION_PLAY_PAUSE |
                PlaybackState.ACTION_SKIP_TO_NEXT |
                PlaybackState.ACTION_SKIP_TO_PREVIOUS;

        PlaybackState playbackState =
                new PlaybackState.Builder()
                        .setActions(actions)
                        .setState(
                                state,
                                currentSentence,
                                1.0f
                        )
                        .build();

        mediaSession.setPlaybackState(playbackState);
    }

    private void updateNotification() {

        Intent openIntent =
                new Intent(this, MainActivity.class);

        PendingIntent contentIntent =
                PendingIntent.getActivity(
                        this,
                        1,
                        openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT |
                        PendingIntent.FLAG_IMMUTABLE
                );

        Notification.MediaStyle style =
                new Notification.MediaStyle()
                        .setMediaSession(
                                mediaSession.getSessionToken()
                        )
                        .setShowActionsInCompactView(0, 1, 2);

        Notification.Builder builder =
                new Notification.Builder(this, CHANNEL_ID)
                        .setContentTitle("PDF Voice")
                        .setContentText(
                                "Frase " +
                                (currentSentence + 1)
                        )
                        .setSmallIcon(
                                android.R.drawable.ic_media_play
                        )
                        .setContentIntent(contentIntent)
                        .setOngoing(playing)
                        .setStyle(style);

        builder.addAction(
                new Notification.Action.Builder(
                        android.R.drawable.ic_media_previous,
                        "Precedente",
                        actionIntent(ACTION_PREVIOUS)
                ).build()
        );

        builder.addAction(
                new Notification.Action.Builder(
                        playing
                                ? android.R.drawable.ic_media_pause
                                : android.R.drawable.ic_media_play,
                        playing ? "Pausa" : "Riproduci",
                        actionIntent(ACTION_PLAY_PAUSE)
                ).build()
        );

        builder.addAction(
                new Notification.Action.Builder(
                        android.R.drawable.ic_media_next,
                        "Successiva",
                        actionIntent(ACTION_NEXT)
                ).build()
        );

        startForeground(
                NOTIFICATION_ID,
                builder.build()
        );
    }

    private PendingIntent actionIntent(String action) {

        Intent intent =
                new Intent(this, PlaybackService.class);

        intent.setAction(action);

        return PendingIntent.getService(
                this,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                PendingIntent.FLAG_IMMUTABLE
        );
    }

    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "PDF Voice",
                            NotificationManager.IMPORTANCE_LOW
                    );

            channel.setDescription(
                    "Controlli della riproduzione PDF Voice"
            );

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(
            Intent intent,
            int flags,
            int startId) {

        if (intent != null &&
            intent.getAction() != null) {

            String action = intent.getAction();

            switch (action) {

                case ACTION_START:
                    loadCurrentPdf();
                    break;

                case ACTION_PLAY_PAUSE:
                    if (playing) {
                        pause();
                    } else {
                        play();
                    }
                    break;

                case ACTION_NEXT:
                    nextSentence();
                    break;

                case ACTION_PREVIOUS:
                    previousSentence();
                    break;

                case ACTION_STOP:
                    pause();
                    stopForeground(STOP_FOREGROUND_REMOVE);
                    stopSelf();
                    break;
            }
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {

        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }

        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
