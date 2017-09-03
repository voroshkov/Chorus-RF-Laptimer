package app.andrey_voroshkov.chorus_laptimer;

import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;

/**
 * Created by Andrey_Voroshkov on 1/29/2017.
 */
public class Utils {
    private static Utils ourInstance = new Utils();

    public static Utils getInstance() {
        return ourInstance;
    }

    private Utils() {
    }

//    public static String convertMsToHMS(int ms) {
//        int h = (int)Math.floor(ms/1000/60/60);
//        int m = (int)Math.floor(ms/1000/60)-h*60;
//        int s = (int)Math.floor(ms/1000)-(h*60+m)*60;
//        int msec = ms-(int)Math.floor(ms/1000)*1000;
//        return String.format("%d : %02d : %02d . %03d", h, m, s, msec);
//    }

    public static String convertMsToDisplayTime(int ms) {
        int m = (int)Math.floor(ms/1000/60);
        int s = (int)Math.floor(ms/1000)-m*60;
        int msec = ms-(int)Math.floor(ms/1000)*1000;
        return String.format("%d : %02d . %03d", m, s, msec);
    }

    public static String convertMsToReportTime(int ms) {
        int m = (int)Math.floor(ms/1000/60);
        int s = (int)Math.floor(ms/1000)-m*60;
        int msec = ms-(int)Math.floor(ms/1000)*1000;
        return String.format("%d:%02d.%03d", m, s, msec);
    }

    public static String convertMsToSpeakableTime(int ms) {
        int m = (int)Math.floor(ms/1000/60);
        int s = (int)Math.floor(ms/1000)-m*60;
        int msec = ms-(int)Math.floor(ms/1000)*1000;

        //speak 2 higher digits of milliseconds separately, or single zero
        String mills = String.format("%03d", msec);
        mills = mills.substring(0, mills.length()-1);
        if (mills.equals("00")) {
            mills = "0";
        }
        mills = mills.replace(""," ").trim();

        String seconds = Integer.toString(s);

        String text = seconds + " point " + mills + " seconds";

        //only add minutes if lap is longer than 1 minute
        if (m > 0) {
            text = Integer.toString(m) + " minutes " + text;
        }
        return text;
    }

    public static String btDataChunkParser(String chunk) {
        String result = "";

        if (chunk.length() < 2) {
            return result;
        }
        char dest = chunk.charAt(0);
        char module = chunk.charAt(1);
        int moduleId = -1;
        if (module != '*') {
            moduleId = Integer.parseInt(String.valueOf(module), 16);
        }
        if (dest == 'S') {
            result += "Module: " + ((moduleId >= 0) ? moduleId : "ALL") + "; ";
            char dataType = chunk.charAt(2);
            switch (dataType) {
                case 'C':
                    int channel = Integer.parseInt(chunk.substring(3,4), 16);
                    result += "Channel: " + channel;
                    AppState.getInstance().changeDeviceChannel(moduleId, channel);
                    break;
                case 'R':
                    int race = Integer.parseInt(chunk.substring(3,4));
                    result += "Race: " + ((race != 0) ? "started" : "stopped");
                    AppState.getInstance().changeRaceState(race!=0);
                    break;
                case 'M':
                    int minLapTime = Integer.parseInt(chunk.substring(3,5), 16);
                    result += "Min Lap: " + minLapTime;
                    AppState.getInstance().changeRaceMinLapTime(minLapTime);
                    break;
                case 'T':
                    int threshold = Integer.parseInt(chunk.substring(3,7), 16);
                    result += "Threshold: " + threshold;
                    AppState.getInstance().changeDeviceThreshold(moduleId, threshold);
                    break;
                case 'S':
                    int rssi = Integer.parseInt(chunk.substring(3,7), 16);
                    result += "RSSI: " + rssi;
                    AppState.getInstance().changeDeviceRSSI(moduleId, rssi);
                    break;
                case 'D':
                    int soundState = Integer.parseInt(chunk.substring(3,4));
                    result += "Sound: " + ((soundState != 0) ? "enabled" : "disabled");
                    AppState.getInstance().changeDeviceSoundState(soundState!=0);
                    break;
                case 'F':
                    int shouldSkipFirstLap = Integer.parseInt(chunk.substring(3,4));
                    result += "Skip First Lap: " + ((shouldSkipFirstLap != 0) ? "enabled" : "disabled");
                    AppState.getInstance().changeSkipFirstLap(shouldSkipFirstLap!=0);
                    break;
                case 'L':
                    int lapNo = Integer.parseInt(chunk.substring(3,5), 16);
                    int lapTime = Integer.parseInt(chunk.substring(5,13), 16);
                    result += "Lap #" + lapNo + " : " + lapTime;
                    AppState.getInstance().addLapResult(moduleId, lapNo, lapTime);
                    break;
                case 'B':
                    int band = Integer.parseInt(chunk.substring(3,4), 16);
                    result += "Band #" + band;
                    AppState.getInstance().changeDeviceBand(moduleId, band);
                    break;
                case 'i':
                    int isCalibrated = Integer.parseInt(chunk.substring(3,4), 16);
                    result += "Calibration: " + ((isCalibrated!= 0) ? "done" : "not performed");
                    AppState.getInstance().changeCalibration(moduleId, (isCalibrated!=0));
                    break;
                case 'I':
                    int calibrationTime = Integer.parseInt(chunk.substring(3,11), 16);
                    result += "Calibration time: " + calibrationTime;
                    AppState.getInstance().changeDeviceCalibrationTime(moduleId, calibrationTime);
                    break;
                case 'V':
                    int isMonitorOn = Integer.parseInt(chunk.substring(3,4), 16);
                    result += "RSSI Monitor: " + ((isMonitorOn!= 0) ? "on" : "off");
                    AppState.getInstance().changeRssiMonitorState(isMonitorOn!=0);
                    break;
                case 'X':
                    result += "EndOfSequence. Device# " + moduleId;
                    AppState.getInstance().receivedEndOfSequence(moduleId);
                    break;
                case 'P':
                    int isDeviceConfigured = Integer.parseInt(chunk.substring(3,4), 16);
                    result += "Device is configured: " + ((isDeviceConfigured!= 0) ? "yes" : "no");
                    AppState.getInstance().changeDeviceConfigStatus(moduleId, (isDeviceConfigured!=0));
                    break;
                case 'Y':
                    int voltageReading = Integer.parseInt(chunk.substring(3,7), 16);
                    result += "Voltage(abstract): " + voltageReading;
                    AppState.getInstance().changeVoltage(voltageReading);
                    break;
            }
        } else if (dest == 'R') {

        } else if (dest == 'N') {
            result += "ModulesCount: " + module;
            AppState.getInstance().setNumberOfDevices(moduleId);
        }
        return result;
    }

    public static void enableDisableView(View view, boolean enabled) {
        view.setEnabled(enabled);

        if ( view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;

            for ( int i = 0 ; i < group.getChildCount() ; i++ ) {
                enableDisableView(group.getChildAt(i), enabled);
            }
        }
    }

    public static String getReportPath(){
        return Environment.getExternalStorageDirectory() + File.separator  + "ChorusRFLaptimer Reports" + File.separator;
    }

    public static final int bkColorPalette[] = {
        0xFF4ECDC4, 0xFFC7F464, 0xFFFF6B6B, 0xFFC44D58, 0xFF3E4CB2, 0xFF049652, 0xFF6F8206, 0xFFF7C683, 0xFF6FDB7E, 0xFF556270
    };

    public static int getBackgroundColorItem(int orderNumber) {
        return bkColorPalette[orderNumber % bkColorPalette.length];
    }
}
