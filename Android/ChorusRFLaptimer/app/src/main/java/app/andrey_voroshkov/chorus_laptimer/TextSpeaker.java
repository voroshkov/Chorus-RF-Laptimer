package app.andrey_voroshkov.chorus_laptimer;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/**
 * Created by lplett on 3/16/2017.
 */

public class TextSpeaker implements TextToSpeech.OnInitListener
{
    TextToSpeech tts;

    public TextSpeaker(Context c)
    {
        tts = new TextToSpeech(c, this);
    }

    @Override
    public void onInit(int status) {
        if(status != TextToSpeech.ERROR){
            tts.setLanguage(Locale.US);
        }
    }
}
