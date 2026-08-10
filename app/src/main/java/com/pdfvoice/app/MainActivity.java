package com.pdfvoice.app;

import android.app.Activity;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity {

    private TextToSpeech tts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(40, 40, 40, 40);

        TextView title = new TextView(this);
        title.setText("PDF Voice");
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);

        TextView subtitle = new TextView(this);
        subtitle.setText("\nPDF → testo → voce\n\nPrima versione in costruzione");
        subtitle.setTextSize(18);
        subtitle.setGravity(Gravity.CENTER);

        Button testButton = new Button(this);
        testButton.setText("TEST VOCE");

        layout.addView(title);
        layout.addView(subtitle);
        layout.addView(testButton);

        setContentView(layout);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.ITALIAN);
            }
        });

        testButton.setOnClickListener(v -> {
            if (tts != null) {
                tts.speak(
                    "Benvenuto in PDF Voice. La sintesi vocale funziona.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "pdfvoice-test"
                );
            }
        });
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
