package app.andrey_voroshkov.chorus_laptimer;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.support.annotation.StringRes;

import java.util.Locale;

/**
 * Created by lplett on 3/16/2017.
 */

public class TextSpeaker implements TextToSpeech.OnInitListener
{
    private TextToSpeech tts;
    private boolean isInitialized = false;
    private Resources resources;

    public TextSpeaker(Context context) {
        tts = new TextToSpeech(context, this);
        resources = context.getResources();
    }

    public void speak(String text) {
        if(isInitialized) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                speakPreLollipop(text);
            } else {
                speakNormally(text);
            }
        }
    }

    public void speak(@StringRes int stringRes) {
        speak(resources.getString(stringRes));
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

    private void speakPreLollipop(String text) {
        tts.speak(text, TextToSpeech.QUEUE_ADD, null);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void speakNormally(String text) {
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, null);
    }
}
