package com.pdfvoice.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.InputStream;
import java.util.ArrayList;

public class MainActivity extends Activity {

    private static final int PICK_PDF = 100;

    private LinearLayout textContainer;
    private ScrollView scrollView;

    private Button playPauseButton;

    private final ArrayList<TextView> sentenceViews = new ArrayList<>();
    private final ArrayList<String> sentences = new ArrayList<>();

    private int currentSentence = -1;
    private boolean playing = false;

    private final android.content.BroadcastReceiver sentenceReceiver =
            new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            if ("com.pdfvoice.SENTENCE_CHANGED".equals(intent.getAction())) {

                int index = intent.getIntExtra(
                        PlaybackService.EXTRA_SENTENCE,
                        -1
                );

                if (index >= 0) {
                    currentSentence = index;
                    highlightSentence(index);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PDFBoxResourceLoader.init(getApplicationContext());

        buildInterface();

        android.content.IntentFilter filter =
                new android.content.IntentFilter(
                        "com.pdfvoice.SENTENCE_CHANGED"
                );

        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                    sentenceReceiver,
                    filter,
                    android.content.Context.RECEIVER_NOT_EXPORTED
            );
        } else {
            registerReceiver(sentenceReceiver, filter);
        }
    }

    private void buildInterface() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 20, 20, 10);

        TextView title = new TextView(this);
        title.setText("PDF Voice");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 10);

        Button openButton = new Button(this);
        openButton.setText("📄 APRI PDF");
        openButton.setOnClickListener(v -> openPdf());

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);

        Button previousButton = new Button(this);
        previousButton.setText("⏮");

        playPauseButton = new Button(this);
        playPauseButton.setText("▶");

        Button nextButton = new Button(this);
        nextButton.setText("⏭");

        previousButton.setOnClickListener(v -> previousSentence());
        playPauseButton.setOnClickListener(v -> playPause());
        nextButton.setOnClickListener(v -> nextSentence());

        controls.addView(previousButton,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                ));

        controls.addView(playPauseButton,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                ));

        controls.addView(nextButton,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                ));

        scrollView = new ScrollView(this);

        textContainer = new LinearLayout(this);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        textContainer.setPadding(4, 10, 4, 40);

        TextView welcome = new TextView(this);
        welcome.setText(
                "Apri un PDF per iniziare.\n\n" +
                "Puoi toccare una frase per iniziare " +
                "la lettura da quel punto."
        );
        welcome.setTextSize(18);
        welcome.setPadding(10, 10, 10, 10);

        textContainer.addView(welcome);
        scrollView.addView(textContainer);

        root.addView(title);
        root.addView(openButton);
        root.addView(
                controls,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                )
        );

        root.addView(
                scrollView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                )
        );

        setContentView(root);
    }

    private void openPdf() {

        Intent intent =
                new Intent(Intent.ACTION_OPEN_DOCUMENT);

        intent.setType("application/pdf");

        intent.addCategory(
                Intent.CATEGORY_OPENABLE
        );

        startActivityForResult(
                intent,
                PICK_PDF
        );
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == PICK_PDF &&
                resultCode == RESULT_OK &&
                data != null &&
                data.getData() != null) {

            Uri uri = data.getData();

            try {
                getContentResolver()
                        .takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        );
            } catch (Exception ignored) {
            }

            loadPdf(uri);
        }
    }

    private void loadPdf(Uri uri) {

        textContainer.removeAllViews();

        sentenceViews.clear();
        sentences.clear();

        currentSentence = -1;

        TextView loading = new TextView(this);

        loading.setText(
                "Caricamento PDF..."
        );

        loading.setTextSize(18);

        textContainer.addView(
                loading
        );

        new Thread(() -> {

            String text;

            try (
                    InputStream input =
                            getContentResolver()
                                    .openInputStream(uri);

                    PDDocument document =
                            PDDocument.load(input)
            ) {

                PDFTextStripper stripper =
                        new PDFTextStripper();

                text = stripper.getText(
                        document
                );

            } catch (Exception e) {

                final String error =
                        "Errore nella lettura del PDF:\n\n" +
                        e.getMessage();

                runOnUiThread(() -> {

                    textContainer.removeAllViews();

                    TextView errorView =
                            new TextView(this);

                    errorView.setText(error);

                    errorView.setTextSize(18);

                    textContainer.addView(
                            errorView
                    );
                });

                return;
            }

            ArrayList<String> extracted =
                    splitIntoSentences(text);

            runOnUiThread(() ->
                    displaySentences(extracted, uri)
            );

        }).start();
    }

    private ArrayList<String> splitIntoSentences(
            String text) {

        ArrayList<String> result =
                new ArrayList<>();

        text = text
                .replaceAll("\\s+", " ")
                .trim();

        if (text.isEmpty()) {
            return result;
        }

        String[] parts =
                text.split(
                        "(?<=[.!?])\\s+"
                );

        for (String part : parts) {

            part = part.trim();

            if (!part.isEmpty()) {
                result.add(part);
            }
        }

        return result;
    }

    private void displaySentences(
            ArrayList<String> extracted,
            Uri uri) {

        textContainer.removeAllViews();

        sentences.addAll(extracted);

        for (int i = 0;
             i < sentences.size();
             i++) {

            final int index = i;

            TextView sentence =
                    new TextView(this);

            sentence.setText(
                    sentences.get(i)
            );

            sentence.setTextSize(19);

            sentence.setTextColor(
                    Color.DKGRAY
            );

            sentence.setPadding(
                    12,
                    12,
                    12,
                    12
            );

            sentence.setOnClickListener(
                    v -> {

                        currentSentence = index;

                        startServiceAtSentence(
                                index,
                                uri
                        );
                    }
            );

            textContainer.addView(
                    sentence
            );

            sentenceViews.add(
                    sentence
            );
        }

        saveCurrentPdf(uri);
    }

    private void saveCurrentPdf(Uri uri) {

        getSharedPreferences(
                "pdf_voice",
                MODE_PRIVATE
        )
                .edit()
                .putString(
                        "current_pdf_uri",
                        uri.toString()
                )
                .apply();
    }

    private void startServiceAtSentence(
            int index,
            Uri uri) {

        saveCurrentPdf(uri);

        getSharedPreferences(
                "pdf_voice",
                MODE_PRIVATE
        )
                .edit()
                .putInt(
                        "current_sentence",
                        index
                )
                .apply();

        Intent serviceIntent =
                new Intent(
                        this,
                        PlaybackService.class
                );

        serviceIntent.setAction(
                PlaybackService.ACTION_START
        );
        serviceIntent.putExtra(
                PlaybackService.EXTRA_SENTENCE,
                index
        );

        if (android.os.Build.VERSION.SDK_INT >= 26) {

            startForegroundService(
                    serviceIntent
            );

        } else {

            startService(
                    serviceIntent
            );
        }

        currentSentence = index;

        playPauseButton.setText("⏸");
        playing = true;
    }

    private void playPause() {

        Intent intent =
                new Intent(
                        this,
                        PlaybackService.class
                );

        intent.setAction(
                PlaybackService.ACTION_PLAY_PAUSE
        );

        startService(intent);

        playing = !playing;

        playPauseButton.setText(
                playing ? "⏸" : "▶"
        );
    }

    private void nextSentence() {

        Intent intent =
                new Intent(
                        this,
                        PlaybackService.class
                );

        intent.setAction(
                PlaybackService.ACTION_NEXT
        );

        startService(intent);

        playing = true;

        playPauseButton.setText("⏸");
    }

    private void previousSentence() {

        Intent intent =
                new Intent(
                        this,
                        PlaybackService.class
                );

        intent.setAction(
                PlaybackService.ACTION_PREVIOUS
        );

        startService(intent);

        playing = true;

        playPauseButton.setText("⏸");
    }

    private void highlightSentence(
            int index) {

        if (index < 0 ||
                index >= sentenceViews.size()) {

            return;
        }

        for (TextView view :
                sentenceViews) {

            view.setBackgroundColor(
                    Color.TRANSPARENT
            );
        }

        TextView active =
                sentenceViews.get(index);

        active.setBackgroundColor(
                Color.YELLOW
        );

        scrollView.post(() ->
                scrollView.smoothScrollTo(
                        0,
                        Math.max(
                                0,
                                active.getTop() - 200
                        )
                )
        );
    }

    @Override
    protected void onDestroy() {

        try {
            unregisterReceiver(
                    sentenceReceiver
            );
        } catch (Exception ignored) {
        }

        super.onDestroy();
    }
}
