package app.andrey_voroshkov.chorus_laptimer;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/**
 * Created by lplett on 3/16/2017.
 */

public class TextSpeaker implements TextToSpeech.OnInitListener
{
    private TextToSpeech tts;
    private boolean isInitialized = false;

    public TextSpeaker(Context context) {
        tts = new TextToSpeech(context, this);
    }

    public void speak(String text) {
        if(isInitialized) {
            tts.speak(text, TextToSpeech.QUEUE_ADD, null);
        }
    }

    public void shutdown() {
        tts.shutdown();
    }

    @Override
    public void onInit(int status) {
        if(status != TextToSpeech.ERROR){
            tts.setLanguage(Locale.US);
            isInitialized = true;
        }
    }
}
