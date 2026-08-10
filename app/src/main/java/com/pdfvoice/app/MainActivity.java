package com.pdfvoice.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int PICK_PDF = 100;

    private TextToSpeech tts;
    private LinearLayout textContainer;
    private ScrollView scrollView;

    private final ArrayList<TextView> sentenceViews = new ArrayList<>();
    private final ArrayList<String> sentences = new ArrayList<>();

    private int currentSentence = -1;
    private boolean ready = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PDFBoxResourceLoader.init(getApplicationContext());

        buildInterface();
        setupTts();
    }

    private void buildInterface() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);

        TextView title = new TextView(this);
        title.setText("PDF Voice");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);

        Button openButton = new Button(this);
        openButton.setText("📄 APRI PDF");
        openButton.setOnClickListener(v -> openPdf());

        Button playButton = new Button(this);
        playButton.setText("▶ LEGGI");
        playButton.setOnClickListener(v -> {
            if (!sentences.isEmpty()) {
                int start = currentSentence >= 0 ? currentSentence : 0;
                speakFrom(start);
            }
        });

        scrollView = new ScrollView(this);

        textContainer = new LinearLayout(this);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        textContainer.setPadding(4, 20, 4, 40);

        TextView welcome = new TextView(this);
        welcome.setText(
            "Apri un PDF per iniziare.\n\n" +
            "Dopo aver aperto il documento, tocca una frase " +
            "per iniziare la lettura da quel punto."
        );
        welcome.setTextSize(18);
        welcome.setPadding(10, 10, 10, 10);

        textContainer.addView(welcome);
        scrollView.addView(textContainer);

        root.addView(title);
        root.addView(openButton);
        root.addView(playButton);
        root.addView(scrollView,
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1
                ));

        setContentView(root);
    }

    private void setupTts() {

        tts = new TextToSpeech(this, status -> {

            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.ITALIAN);

                if (result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED) {

                    ready = true;
                }
            }
        });

        tts.setOnUtteranceProgressListener(
            new UtteranceProgressListener() {

                @Override
                public void onStart(String utteranceId) {
                    try {
                        final int index =
                            Integer.parseInt(utteranceId);

                        runOnUiThread(() -> highlightSentence(index));

                    } catch (Exception ignored) {
                    }
                }

                @Override
                public void onDone(String utteranceId) {
                }

                @Override
                public void onError(String utteranceId) {
                }
            }
        );
    }

    private void openPdf() {

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/pdf");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        startActivityForResult(intent, PICK_PDF);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_PDF &&
            resultCode == RESULT_OK &&
            data != null &&
            data.getData() != null) {

            Uri uri = data.getData();

            try {
                getContentResolver().takePersistableUriPermission(
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
        loading.setText("Caricamento PDF...");
        loading.setTextSize(18);
        textContainer.addView(loading);

        new Thread(() -> {

            String text = "";

            try (InputStream input =
                     getContentResolver().openInputStream(uri);
                 PDDocument document =
                     PDDocument.load(input)) {

                PDFTextStripper stripper =
                    new PDFTextStripper();

                text = stripper.getText(document);

            } catch (Exception e) {

                final String error =
                    "Errore nella lettura del PDF:\n\n" +
                    e.getMessage();

                runOnUiThread(() -> {
                    textContainer.removeAllViews();

                    TextView errorView = new TextView(this);
                    errorView.setText(error);
                    errorView.setTextSize(18);
                    textContainer.addView(errorView);
                });

                return;
            }

            final ArrayList<String> extracted =
                splitIntoSentences(text);

            runOnUiThread(() -> displaySentences(extracted));

        }).start();
    }

    private ArrayList<String> splitIntoSentences(String text) {

        ArrayList<String> result = new ArrayList<>();

        text = text.replaceAll("\\s+", " ").trim();

        if (text.isEmpty()) {
            return result;
        }

        String[] parts =
            text.split("(?<=[.!?])\\s+");

        for (String part : parts) {

            part = part.trim();

            if (!part.isEmpty()) {
                result.add(part);
            }
        }

        return result;
    }

    private void displaySentences(ArrayList<String> extracted) {

        textContainer.removeAllViews();

        sentences.addAll(extracted);

        for (int i = 0; i < sentences.size(); i++) {

            final int index = i;

            TextView sentence = new TextView(this);

            sentence.setText(sentences.get(i));
            sentence.setTextSize(19);
            sentence.setTextColor(Color.DKGRAY);
            sentence.setPadding(12, 12, 12, 12);

            sentence.setOnClickListener(v -> {

                currentSentence = index;
                speakFrom(index);

            });

            textContainer.addView(sentence);
            sentenceViews.add(sentence);
        }
    }

    private void speakFrom(int index) {

        if (!ready || sentences.isEmpty()) {
            return;
        }

        if (index < 0) {
            index = 0;
        }

        if (index >= sentences.size()) {
            return;
        }

        currentSentence = index;

        tts.stop();

        for (int i = index; i < sentences.size(); i++) {

            Bundle params = new Bundle();

            tts.speak(
                sentences.get(i),
                TextToSpeech.QUEUE_ADD,
                params,
                String.valueOf(i)
            );
        }
    }

    private void highlightSentence(int index) {

        if (index < 0 || index >= sentenceViews.size()) {
            return;
        }

        for (int i = 0; i < sentenceViews.size(); i++) {
            sentenceViews.get(i).setBackgroundColor(Color.TRANSPARENT);
        }

        TextView active = sentenceViews.get(index);

        active.setBackgroundColor(Color.YELLOW);

        currentSentence = index;

        scrollView.post(() ->
            scrollView.smoothScrollTo(
                0,
                Math.max(0, active.getTop() - 200)
            )
        );
    }

    @Override
    protected void onDestroy() {

        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }

        super.onDestroy();
    }
}
