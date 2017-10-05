package app.andrey_voroshkov.chorus_laptimer;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.support.annotation.StringRes;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Created by lplett on 3/16/2017.
 */

public class TextSpeaker implements TextToSpeech.OnInitListener {

    private static Map<String, Locale> SUPPORTED_LOCALES = new HashMap<>();

    static {
        SUPPORTED_LOCALES.put("en", Locale.US);
        SUPPORTED_LOCALES.put("ru", new Locale("ru"));
    }

    private TextToSpeech tts;
    private boolean useEnglishOnly;
    private boolean isInitialized = false;
    private Context originalContext;
    private Context contextInUse;

    public TextSpeaker(Context context, boolean useEnglishOnly) {
        tts = new TextToSpeech(context, this);
        originalContext = contextInUse = context;
        this.useEnglishOnly = useEnglishOnly;
    }

    public void speak(String text) {
        if (isInitialized) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                speakPreLollipop(text);
            } else {
                speakNormally(text);
            }
        }
    }

    public void speakMillisecondsInFriendlyTime(int ms) {
        int m = (int) Math.floor(ms / 1000 / 60);
        int s = (int) Math.floor(ms / 1000) - m * 60;
        int msec = ms - (int) Math.floor(ms / 1000) * 1000;

        //speak 2 higher digits of milliseconds separately, or single zero
        String mills = String.format("%03d", msec);
        mills = mills.substring(0, mills.length() - 1);
        if (mills.equals("00")) {
            mills = "0";
        }
        mills = mills.replace("", " ").trim();

        String text;

        //only add minutes if lap is longer than 1 minute
        if (m > 0) {
            text = contextInUse.getString(R.string.race_report_time_mins, m, s, mills);
        } else {
            text = contextInUse.getString(R.string.race_report_time_secs, s, mills);
        }
        speak(text);
    }

    public void speak(@StringRes int stringRes, Object... args) {
        speak(contextInUse.getString(stringRes, args));
    }

    public void speak(@StringRes int stringRes) {
        speak(contextInUse.getString(stringRes));
    }

    public void shutdown() {
        tts.shutdown();
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.ERROR) {
            Locale localeToSet = Locale.US;
            Locale current = Locale.getDefault();
            if (!useEnglishOnly && SUPPORTED_LOCALES.containsKey(current.getLanguage())) {
                localeToSet = current;
            }
            Configuration currentConfig = originalContext.getResources().getConfiguration();
            if (!currentConfig.locale.equals(localeToSet)) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    setContextToUseLegacy(currentConfig, localeToSet);
                } else {
                    setContextToUse(currentConfig, localeToSet);
                }
            }
            tts.setLanguage(localeToSet);
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

    private void setContextToUseLegacy(Configuration configuration, Locale locale) {
        configuration.locale = locale;
        contextInUse.getResources().updateConfiguration(configuration, null);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void setContextToUse(Configuration configuration, Locale locale) {
        configuration.setLocale(locale);
        contextInUse = originalContext.createConfigurationContext(configuration);
    }


    public void useEnglishOnly(boolean shouldSpeakEnglish) {
        if (isInitialized) {
            useEnglishOnly = shouldSpeakEnglish;
            onInit(TextToSpeech.SUCCESS);
        }
    }
}
