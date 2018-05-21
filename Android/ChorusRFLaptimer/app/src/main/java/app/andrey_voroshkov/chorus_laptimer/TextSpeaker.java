package app.andrey_voroshkov.chorus_laptimer;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.support.annotation.StringRes;

import java.text.DecimalFormatSymbols;
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
        SUPPORTED_LOCALES.put("es", new Locale("es"));
        SUPPORTED_LOCALES.put("de", new Locale("de"));
        SUPPORTED_LOCALES.put("it", new Locale("it"));
    }

    private TextToSpeech tts;
    private boolean useEnglishOnly;
    private boolean isInitialized = false;
    private Context originalContext;
    private Context contextInUse;
    private String decimalSeparator = ".";

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
        //UPDATE: rely on the speech engine to correctly pronounce decimals
        String mills = String.format("%03d", msec);
        mills = mills.substring(0, mills.length() - 1);

        String text = "";
        String seconds = s + decimalSeparator + mills + contextInUse.getString(R.string.seconds_short);

        //only add minutes if lap is longer than 1 minute
        if (m > 0) {
            String minutes = contextInUse.getResources().getQuantityString(R.plurals.minutes, m, m);
            text = minutes + " ";
        }

        text += seconds;
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
            DecimalFormatSymbols dfs = new DecimalFormatSymbols(localeToSet);
            decimalSeparator = Character.toString(dfs.getDecimalSeparator());
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
        // applying this unconditionally is necessary to apply prefs before the TTS engine is initialized
        useEnglishOnly = shouldSpeakEnglish;
        if (isInitialized) {
            onInit(TextToSpeech.SUCCESS);
        }
    }
}
