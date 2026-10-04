package com.pdfvoice.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Layout;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
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

    private static final String EXTRA_WORD_OFFSET = "word_offset";

    private LinearLayout textContainer;
    private ScrollView scrollView;

    private Button playPauseButton;

    private final ArrayList<TextView> sentenceViews =
            new ArrayList<>();

    private final ArrayList<String> sentences =
            new ArrayList<>();

    private int currentSentence = -1;

    private boolean playing = false;

    private final android.content.BroadcastReceiver sentenceReceiver =
            new android.content.BroadcastReceiver() {

        @Override
        public void onReceive(
                android.content.Context context,
                Intent intent) {

            if ("com.pdfvoice.SENTENCE_CHANGED".equals(
                    intent.getAction())) {

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

        PDFBoxResourceLoader.init(
                getApplicationContext()
        );

        buildInterface();

        SharedPreferences prefs =
                getSharedPreferences(
                        "pdf_voice",
                        MODE_PRIVATE
                );

        String savedUri =
                prefs.getString(
                        "current_pdf_uri",
                        null
                );

        if (savedUri != null) {

            try {

                Uri uri = Uri.parse(savedUri);

                loadPdf(
                        uri,
                        true
                );

            } catch (Exception ignored) {
            }
        }

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

            registerReceiver(
                    sentenceReceiver,
                    filter
            );
        }
    }

    private void buildInterface() {

        LinearLayout root =
                new LinearLayout(this);

        root.setOrientation(
                LinearLayout.VERTICAL
        );

        root.setPadding(
                20,
                20,
                20,
                10
        );

        TextView title =
                new TextView(this);

        title.setText(
                "PDF Voice"
        );

        title.setTextSize(28);

        title.setGravity(
                Gravity.CENTER
        );

        title.setPadding(
                0,
                0,
                0,
                10
        );

        Button openButton =
                new Button(this);

        openButton.setText(
                "📄 APRI PDF"
        );

        openButton.setOnClickListener(
                v -> openPdf()
        );

        LinearLayout controls =
                new LinearLayout(this);

        controls.setOrientation(
                LinearLayout.HORIZONTAL
        );

        controls.setGravity(
                Gravity.CENTER
        );

        Button previousButton =
                new Button(this);

        previousButton.setText(
                "⏮"
        );

        playPauseButton =
                new Button(this);

        playPauseButton.setText(
                "▶"
        );

        Button nextButton =
                new Button(this);

        nextButton.setText(
                "⏭"
        );

        previousButton.setOnClickListener(
                v -> previousSentence()
        );

        playPauseButton.setOnClickListener(
                v -> playPause()
        );

        nextButton.setOnClickListener(
                v -> nextSentence()
        );

        controls.addView(
                previousButton,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        controls.addView(
                playPauseButton,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        controls.addView(
                nextButton,
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                )
        );

        scrollView =
                new ScrollView(this);

        textContainer =
                new LinearLayout(this);

        textContainer.setOrientation(
                LinearLayout.VERTICAL
        );

        textContainer.setPadding(
                4,
                10,
                4,
                40
        );

        TextView welcome =
                new TextView(this);

        welcome.setText(
                "Apri un PDF per iniziare.\n\n" +
                "Puoi toccare una frase per iniziare " +
                "la lettura da quel punto.\n\n" +
                "Puoi anche tenere premuta una parola " +
                "per iniziare la lettura da lì."
        );

        welcome.setTextSize(18);

        welcome.setPadding(
                10,
                10,
                10,
                10
        );

        textContainer.addView(
                welcome
        );

        scrollView.addView(
                textContainer
        );

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
                new Intent(
                        Intent.ACTION_OPEN_DOCUMENT
                );

        intent.setType(
                "application/pdf"
        );

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

            Uri uri =
                    data.getData();

            try {

                getContentResolver()
                        .takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        );

            } catch (Exception ignored) {
            }

            getSharedPreferences(
                    "pdf_voice",
                    MODE_PRIVATE
            )
                    .edit()
                    .putInt(
                            "current_sentence",
                            0
                    )
                    .apply();

            loadPdf(
                    uri,
                    false
            );
        }
    }

    private void loadPdf(
            Uri uri,
            boolean restorePosition) {

        textContainer.removeAllViews();

        sentenceViews.clear();

        sentences.clear();

        currentSentence = -1;

        TextView loading =
                new TextView(this);

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

                text =
                        stripper.getText(
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

                    errorView.setText(
                            error
                    );

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
                    displaySentences(
                            extracted,
                            uri,
                            restorePosition
                    )
            );

        }).start();
    }

    private ArrayList<String> splitIntoSentences(
            String text) {

        ArrayList<String> result =
                new ArrayList<>();

        text = text
                .replaceAll(
                        "\\s+",
                        " "
                )
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
            Uri uri,
            boolean restorePosition) {

        textContainer.removeAllViews();

        sentences.addAll(
                extracted
        );

        SharedPreferences prefs =
                getSharedPreferences(
                        "pdf_voice",
                        MODE_PRIVATE
                );

        int savedSentence =
                prefs.getInt(
                        "current_sentence",
                        0
                );

        if (savedSentence < 0 ||
                savedSentence >= sentences.size()) {

            savedSentence = 0;
        }

        final int restoredSentence =
                savedSentence;

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

            GestureDetector gestureDetector =
                    new GestureDetector(
                            this,
                            new GestureDetector.SimpleOnGestureListener() {

                        @Override
                        public boolean onDown(
                                MotionEvent e) {

                            return true;
                        }

                        @Override
                        public boolean onSingleTapConfirmed(
                                MotionEvent e) {

                            startServiceAtSentence(
                                    index
                            );

                            return true;
                        }

                        @Override
                        public void onLongPress(
                                MotionEvent e) {

                            startServiceAtWord(
                                    sentence,
                                    index,
                                    e.getX(),
                                    e.getY(),
                                    uri
                            );
                        }
                    }
            );

            sentence.setOnTouchListener(
                    (v, event) ->
                            gestureDetector.onTouchEvent(event)
            );

            sentenceViews.add(
                    sentence
            );

            textContainer.addView(
                    sentence
            );
        }

        if (restorePosition &&
                !sentences.isEmpty()) {

            currentSentence =
                    restoredSentence;

            textContainer.post(
                    () -> highlightSentence(
                            restoredSentence
                    )
            );
        } else if (!sentences.isEmpty()) {

            currentSentence = 0;

            highlightSentence(0);
        }

        saveCurrentPdf(uri);
    }

    private void startServiceAtSentence(
            int index) {

        if (index < 0 ||
                index >= sentences.size()) {

            return;
        }

        currentSentence = index;

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

        highlightSentence(index);

        Intent intent =
                new Intent(
                        this,
                        PlaybackService.class
                );

        intent.setAction(
                PlaybackService.ACTION_START
        );

        intent.putExtra(
                PlaybackService.EXTRA_SENTENCE,
                index
        );
