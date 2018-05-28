package app.andrey_voroshkov.chorus_laptimer;

import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by Andrey_Voroshkov on 3/26/2017.
 */

public class AppPreferences {
    public static final String STRING_ITEMS_DELIMITER = "%%"; //should not contain regex special characters

    public static final String SKIP_FIRST_LAP = "skip_first_lap";
    public static final String SPEAK_LAP_TIMES = "speak_lap_times";
    public static final String SPEAK_MESSAGES = "speak_messages";
    public static final String SPEAK_ENGLISH_ONLY = "speak_english_only";
    public static final String LAPS_TO_GO = "laps_to_go";
    public static final String MIN_LAP_TIME = "min_lap_time";
    public static final String PREPARATION_TIME = "preparation_time";
    public static final String ENABLE_DEVICE_SOUNDS = "enable_device_sounds";
    public static final String DEVICE_BANDS = "device_bands";
    public static final String DEVICE_CHANNELS = "device_channels";
    public static final String DEVICE_PILOTS = "device_pilots";
    public static final String DEVICE_ENABLED = "device_enabled";
    public static final String DEVICE_THRESHOLDS = "device_thresholds";
    public static final String LIPO_MONITOR_ENABLED = "lipo_mon_enabled";
    public static final String LIPO_ADJUSTMENT_CONST = "lipo_adjust_const";


    public String[] mBands;
    public String[] mChannels;
    public String[] mPilots;
    public String[] mDeviceEnabledStatuses;
    public String[] mDeviceThresholds;

    private static AppPreferences instance = new AppPreferences();

    public static AppPreferences getInstance() {
        return instance;
    }

    private AppPreferences() {
        mBands = getArrayFromStringPreference(DEVICE_BANDS);
        mChannels = getArrayFromStringPreference(DEVICE_CHANNELS);
        mPilots = getArrayFromStringPreference(DEVICE_PILOTS);
        mDeviceEnabledStatuses = getArrayFromStringPreference(DEVICE_ENABLED);
        mDeviceThresholds = getArrayFromStringPreference(DEVICE_THRESHOLDS);
    }

    private static String[] getArrayFromStringPreference(String prefName) {
        String value = AppState.getInstance().preferences.getString(prefName, "");
        if (value.equals("")) {
            return new String[0];
        }
        return TextUtils.split(value, STRING_ITEMS_DELIMITER);
    }

    // this function is needed to preserve preferencess of devices that are not currently available in chorus
    // e.g. prefs were saved for 3 devices, then used with 1 device. When you're back with 3 - you'll have old prefs restored
    private static void AppendExtraItemsFromSavedArrayPreference(ArrayList<String> list, String[] savedPreferenceArr) {
        int currentCount = list.size();
        int savedCount = savedPreferenceArr.length;
        if (currentCount < savedCount) {
            list.addAll(Arrays.asList(savedPreferenceArr).subList(currentCount, savedCount));
        }
    }

    public static void save(String preferenceName) {
        AppState app = AppState.getInstance();
        // don't save anything to prefs before device initialization is done
        if (!app.isDevicesInitializationOver()) return;

        SharedPreferences.Editor editor = app.preferences.edit();
        switch (preferenceName) {
            case SKIP_FIRST_LAP:
                editor.putBoolean(SKIP_FIRST_LAP, app.shouldSkipFirstLap);
                break;
            case SPEAK_LAP_TIMES:
                editor.putBoolean(SPEAK_LAP_TIMES, app.shouldSpeakLapTimes);
                break;
            case SPEAK_MESSAGES:
                editor.putBoolean(SPEAK_MESSAGES, app.shouldSpeakMessages);
                break;
            case SPEAK_ENGLISH_ONLY:
                editor.putBoolean(SPEAK_ENGLISH_ONLY, app.shouldSpeakEnglishOnly);
                break;
            case LAPS_TO_GO:
                if (app.raceState == null) break;
                editor.putInt(LAPS_TO_GO, app.raceState.lapsToGo);
                break;
            case MIN_LAP_TIME:
                if (app.raceState == null) break;
                editor.putInt(MIN_LAP_TIME, app.raceState.minLapTime);
                break;
            case PREPARATION_TIME:
                editor.putInt(PREPARATION_TIME, app.timeToPrepareForRace);
                break;
            case ENABLE_DEVICE_SOUNDS:
                editor.putBoolean(ENABLE_DEVICE_SOUNDS, app.isDeviceSoundEnabled);
                break;
            case DEVICE_BANDS:
                if (app.deviceStates == null) break;
                ArrayList<String> bandsList = new ArrayList<>();
                for(int i = 0; i < app.deviceStates.size(); i++) {
                    bandsList.add(Integer.toString(app.deviceStates.get(i).band));
                }
                AppendExtraItemsFromSavedArrayPreference(bandsList, AppPreferences.getInstance().mBands);
                String bands = TextUtils.join(STRING_ITEMS_DELIMITER, bandsList);
                editor.putString(DEVICE_BANDS, bands);
                break;
            case DEVICE_CHANNELS:
                if (app.deviceStates == null) break;
                ArrayList<String> channelsList = new ArrayList<>();
                for(int i = 0; i < app.deviceStates.size(); i++) {
                    channelsList.add(Integer.toString(app.deviceStates.get(i).channel));
                }
                AppendExtraItemsFromSavedArrayPreference(channelsList, AppPreferences.getInstance().mChannels);
                String channels = TextUtils.join(STRING_ITEMS_DELIMITER, channelsList);
                editor.putString(DEVICE_CHANNELS, channels);
                break;
            case DEVICE_PILOTS:
                if (app.deviceStates == null) break;
                ArrayList<String> pilotsList = new ArrayList<>();
                for(int i = 0; i < app.deviceStates.size(); i++) {
                    pilotsList.add(app.deviceStates.get(i).pilotName);
                }
                AppendExtraItemsFromSavedArrayPreference(pilotsList, AppPreferences.getInstance().mPilots);
                String pilots = TextUtils.join(STRING_ITEMS_DELIMITER, pilotsList);
                editor.putString(DEVICE_PILOTS, pilots);
                break;
            case DEVICE_ENABLED:
                if (app.deviceStates == null) break;
                ArrayList<String> statusesList = new ArrayList<>();
                for(int i = 0; i < app.deviceStates.size(); i++) {
                    statusesList.add(Boolean.toString(app.deviceStates.get(i).isEnabled));
                }
                AppendExtraItemsFromSavedArrayPreference(statusesList, AppPreferences.getInstance().mDeviceEnabledStatuses);
                String statuses = TextUtils.join(STRING_ITEMS_DELIMITER, statusesList);
                editor.putString(DEVICE_ENABLED, statuses);
                break;
            case DEVICE_THRESHOLDS:
                if (app.deviceStates == null) break;
                ArrayList<String> thresholdsList = new ArrayList<>();
                for(int i = 0; i < app.deviceStates.size(); i++) {
                    thresholdsList.add(Integer.toString(app.deviceStates.get(i).threshold));
                }
                AppendExtraItemsFromSavedArrayPreference(thresholdsList, AppPreferences.getInstance().mDeviceThresholds);
                String thresholds = TextUtils.join(STRING_ITEMS_DELIMITER, thresholdsList);
                editor.putString(DEVICE_THRESHOLDS, thresholds);
                break;

            case LIPO_MONITOR_ENABLED:
                editor.putBoolean(LIPO_MONITOR_ENABLED, app.isLiPoMonitorEnabled);
                break;
            case LIPO_ADJUSTMENT_CONST:
                editor.putInt(LIPO_ADJUSTMENT_CONST, app.batteryAdjustmentConst);
                break;
        }
        editor.apply();
    }

    public static void applyInAppPreferences() {
        AppState app = AppState.getInstance();

        app.changeShouldSpeakLapTimes(app.preferences.getBoolean(SPEAK_LAP_TIMES, true));
        app.changeShouldSpeakMessages(app.preferences.getBoolean(SPEAK_MESSAGES, true));
        app.changeShouldSpeakEnglishOnly(app.preferences.getBoolean(SPEAK_ENGLISH_ONLY, false));
        app.changeTimeToPrepareForRace(app.preferences.getInt(PREPARATION_TIME, 5));
        app.changeRaceLaps(app.preferences.getInt(LAPS_TO_GO, 5));
        app.changeAdjustmentConst(app.preferences.getInt(LIPO_ADJUSTMENT_CONST, 0));
        app.changeEnableLiPoMonitor(app.preferences.getBoolean(LIPO_MONITOR_ENABLED, true));

        if (app.deviceStates != null) {
            String pilots = app.preferences.getString(DEVICE_PILOTS, "");
            if (!pilots.equals("")) {
                String[] pilotsArray = TextUtils.split(pilots, STRING_ITEMS_DELIMITER);
                int pilotsCount = pilotsArray.length;
                for(int i = 0; i < app.deviceStates.size(); i++) {
                    if (i < pilotsCount) {
                        app.changeDevicePilot(i, pilotsArray[i]);
                    }
                }
                app.updatePilotNamesInEdits(); //because pilot names in UI are not updated via changeDevicePilot()
            }
        }

        if (app.deviceStates != null) {
            String statuses = app.preferences.getString(DEVICE_ENABLED, "");
            if (!statuses.equals("")) {
                String[] statusesArray = TextUtils.split(statuses, STRING_ITEMS_DELIMITER);
                int statusesCount = statusesArray.length;
                for(int i = 0; i < app.deviceStates.size(); i++) {
                    if (i < statusesCount) {
                        app.changeDeviceEnabled(i, Boolean.parseBoolean(statusesArray[i]));
                    }
                }
            }
        }
    }

    public static void applyDeviceDependentPreferences() {
        AppState app = AppState.getInstance();

        boolean prefSkipFirstLap = app.preferences.getBoolean(SKIP_FIRST_LAP, true);
        if (app.shouldSkipFirstLap != prefSkipFirstLap) {
            app.changeSkipFirstLap(prefSkipFirstLap);
            app.sendBtCommand("R*1" + (prefSkipFirstLap ? "0" : "1"));
        }

        boolean prefEnableDeviceSounds = app.preferences.getBoolean(ENABLE_DEVICE_SOUNDS, true);
        if (app.isDeviceSoundEnabled != prefEnableDeviceSounds) {
            app.changeDeviceSoundState(prefEnableDeviceSounds);
            app.sendBtCommand("R*S" + (prefEnableDeviceSounds ? "1" : "0"));
        }

        if (app.raceState != null) {
            int prefMLT = app.preferences.getInt(MIN_LAP_TIME, 3);
            app.changeRaceMinLapTime(prefMLT);
            app.sendBtCommand("R*M" + String.format("%02X", prefMLT));
        }

        if (app.deviceStates != null) {
            String bands = app.preferences.getString(DEVICE_BANDS, "");
            if (!bands.equals("")) {
                String[] bandsArray = TextUtils.split(bands, STRING_ITEMS_DELIMITER);
                int bandsCount = bandsArray.length;
                //count backwards to send commands for last devices first to make sure that
                // first devices don't delay propagation of commands to next devices
                for(int i = app.deviceStates.size() - 1; i >= 0 ; i--) {
                    if (i < bandsCount) {
                        int prefBand = Integer.parseInt(bandsArray[i]);
                        app.changeDeviceBand(i, prefBand);
                        app.sendBtCommand("R" +  String.format("%X", i) + "B" + String.format("%X", prefBand));
                    }
                }
            }

            String channels = app.preferences.getString(DEVICE_CHANNELS, "");
            if (!channels.equals("")) {
                String[] channelsArray = TextUtils.split(channels, STRING_ITEMS_DELIMITER);
                int channelsCount = channelsArray.length;
                //count backwards to send commands for last devices first to make sure that
                // first devices don't delay propagation of commands to next devices
                for(int i = app.deviceStates.size() - 1; i >= 0 ; i--) {
                   if (i < channelsCount) {
                        int prefChannel = Integer.parseInt(channelsArray[i]);
                        app.changeDeviceChannel(i, prefChannel);
                        app.sendBtCommand("R" +  String.format("%X", i) + "C" + String.format("%X", prefChannel));
                    }
                }
            }

            String thresholds = app.preferences.getString(DEVICE_THRESHOLDS, "");
            if (!thresholds.equals("")) {
                String[] thresholdsArray = TextUtils.split(thresholds, STRING_ITEMS_DELIMITER);
                int thresholdsCount = thresholdsArray.length;
                //count backwards to send commands for last devices first to make sure that
                // first devices don't delay propagation of commands to next devices
                for(int i = app.deviceStates.size() - 1; i >= 0 ; i--) {
                    if (i < thresholdsCount) {
                        int prefThreshold = Integer.parseInt(thresholdsArray[i]);
                        app.changeDeviceThreshold(i, prefThreshold);
                        app.sendBtCommand("R" +  String.format("%X", i) + "T" + String.format("%04X", prefThreshold));
                    }
                }
            }

        }
    }

    public static void applyAll() {
        applyInAppPreferences();
        applyDeviceDependentPreferences();
    }
}
