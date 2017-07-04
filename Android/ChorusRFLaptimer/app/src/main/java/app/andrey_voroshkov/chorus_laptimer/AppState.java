package app.andrey_voroshkov.chorus_laptimer;

import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Message;

import java.util.ArrayList;

import app.akexorcist.bluetotohspp.library.BluetoothSPP;

/**
 * Created by Andrey_Voroshkov on 1/22/2017.
 */
public class AppState {
    public static final byte DELIMITER = '\n';

    public static final int MIN_RSSI = 80;
    public static final int MAX_RSSI = 315;
    public static final int RSSI_SPAN = MAX_RSSI - MIN_RSSI;
    public static final int CALIBRATION_TIME_MS = 10000;
    public static final String bandNames [] = {"Race", "A", "B", "E", "F", "D"};
    public static final int DEFAULT_MIN_LAP_TIME = 5;
    public static final int DEFAULT_LAPS_TO_GO = 3;

    //tone sounds and durations (race start, lap count, etc)
    public static final int TONE_PREPARE = ToneGenerator.TONE_DTMF_1;
    public static final int DURATION_PREPARE = 80;
    public static final int TONE_GO = ToneGenerator.TONE_DTMF_D;
    public static final int DURATION_GO = 600;
    public static final int TONE_LAP = ToneGenerator.TONE_DTMF_S;
    public static final int DURATION_LAP = 400;
    public static final int MIN_TIME_BEFORE_RACE_TO_SPEAK = 5; //seconds, don't speak "Prepare" message if less time is set

    //voltage measuring constants
    public static final int BATTERY_CHECK_INTERVAL = 10000; // 10 seconds
    public static final int BATTERY_WARN_INTERVAL = 60000; // 1 minute
    public static final int BATTERY_WARN_DELAY = 5000; // 5 seconds (increase to prevent false alarms upon connection
    public static final double VOLTAGE_LOW = 3.4;
    public static final double VOLTAGE_HIGH = 4.2;
    public static final double VOLTAGE_DIVIDER_CONSTANT = 11; //(10K + 1K)/1K
    public static final double ARDUINO_VOLTAGE = 5;
    public static final double ARDUINO_ANALOG_COUNTS = 1024;

    private static AppState instance = new AppState();

    public static void Reset_Instance_TEST_ONLY() {
        instance = new AppState();
    }

    public static AppState getInstance() {
        return instance;
    }

    public BluetoothSPP bt;
    public TextSpeaker textSpeaker;
    public SharedPreferences preferences;

    public int numberOfDevices = 0;
    public boolean isDeviceSoundEnabled = false;
    public boolean shouldSpeakLapTimes = true;
    public boolean shouldSpeakMessages = true;
    public boolean shouldSkipFirstLap = true;
    public boolean wereDevicesConfigured = false;
    public boolean isRssiMonitorOn = false;
    public int timeToPrepareForRace = 5; //in seconds
    public RaceState raceState;
    public ArrayList<DeviceState> deviceStates;
    public ArrayList<ArrayList<LapResult>> raceResults;
    public int batteryPercentage = 0;
    public int lastVoltageReading = 0;
    public double batteryVoltage = 0;
    public int batteryAdjustmentConst = 1;
    public boolean isLiPoMonitorEnabled = true;
    public boolean isConnected = false;

    private ArrayList<Boolean> deviceTransmissionStates;
    private ArrayList<IDataListener> mListeners;
    private ToneGenerator mToneGenerator;
    private Handler mBatteryMonitorHandler;
    private Handler mBatteryNotifyHandler;

    private AppState() {
        mListeners = new ArrayList<IDataListener>();
        raceResults = new ArrayList<ArrayList<LapResult>>();
        deviceStates = new ArrayList<DeviceState>();
        deviceTransmissionStates = new ArrayList<Boolean>();
        mToneGenerator = new ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME);

        mBatteryMonitorHandler = new Handler() {
            public void handleMessage(Message msg) {
                AppState app = AppState.getInstance();
                if (app.isConnected && app.isLiPoMonitorEnabled && app.raceState != null && !app.raceState.isStarted) {
                    AppState.getInstance().sendBtCommand("R*Y");
                }
                sendEmptyMessageDelayed(0, BATTERY_CHECK_INTERVAL);
            }
        };

        mBatteryNotifyHandler = new Handler() {
            public void handleMessage(Message msg) {
                AppState app = AppState.getInstance();
                if (app.isConnected && app.isLiPoMonitorEnabled) {
                    if (batteryPercentage <= 10) {
                        speakMessage("Device battery critical");
                    } else if (batteryPercentage <= 20){
                        speakMessage("Device battery low");
                    }
                }
                sendEmptyMessageDelayed(0, BATTERY_WARN_INTERVAL);
            }
        };

        raceState = new RaceState(false, DEFAULT_MIN_LAP_TIME, DEFAULT_LAPS_TO_GO);
    }

    public void addListener(IDataListener listener) {
        mListeners.add(listener);
    } 
    
    private void emitEvent(DataAction eventName) {
        for (IDataListener listener: mListeners) {
            listener.onDataChange(eventName);
        }
    }
    //---------------------------------------------------------------------
    public void actualizeRaceResults() {
        int count = raceResults.size();
        if (count < numberOfDevices) {
            for(int i = count; i < numberOfDevices; i++) {
                raceResults.add(new ArrayList<LapResult>());
            }
        } else if (count > numberOfDevices) {
            for(int i = numberOfDevices; i < count; i++) {
                raceResults.remove(raceResults.size() - 1);
            }
        }
    }

    public void actualizeDeviceStates() {
        int count = deviceStates.size();
        if (count < numberOfDevices) {
            for(int i = count; i < numberOfDevices; i++) {
                DeviceState ds = new DeviceState();
                ds.threshold = 0;
                ds.channel = i;
                ds.pilotName = "Pilot " + Integer.toString(i+1);
                deviceStates.add(ds);
            }
        } else if (count > numberOfDevices) {
            for(int i = numberOfDevices; i < count; i++) {
                deviceStates.remove(deviceStates.size() - 1);
            }
        }
    }

    public void actualizeDeviceTransmissionStates() {
        int count = deviceTransmissionStates.size();
        if (count < numberOfDevices) {
            for(int i = count; i < numberOfDevices; i++) {
                deviceTransmissionStates.add(false);
            }
        } else if (count > numberOfDevices) {
            for(int i = numberOfDevices; i < count; i++) {
                deviceTransmissionStates.remove(deviceStates.size() - 1);
            }
        }
    }

    public void resetRaceResults() {
        raceResults.clear();
        for(int i = 0; i < numberOfDevices; i++) {
            raceResults.add(new ArrayList<LapResult>());
        }
    }

    public int getPilotPositionByBestLap(int deviceId) {
        int count = raceResults.size();
        if (count <= deviceId || !getIsPilotEnabled(deviceId)) return -1;

        int myPosition = getEnabledPilotsCount();

        int myBestLapId = getBestLapId(deviceId);
        for (int i = 0; i < count; i++ ) {
            if (i != deviceId && getIsPilotEnabled(i)) {
                int curDeviceBestLapId = getBestLapId(i);
                if (curDeviceBestLapId == -1) {
                    myPosition--;
                } else if (myBestLapId != -1) {
                    int myBestTime = raceResults.get(deviceId).get(myBestLapId).getMs();
                    int curDeviceBestTime = raceResults.get(i).get(curDeviceBestLapId).getMs();
                    if (myBestTime <= curDeviceBestTime) {
                        myPosition--;
                    }
                }
            }
        }
        return myPosition;
    }

    public int getPilotPositionByTotalTime(int deviceId) {
        int count = raceResults.size();
        if (count <= deviceId || !getIsPilotEnabled(deviceId)) return -1;

        int myPosition = getEnabledPilotsCount();

        int myLapsCount = getValidRaceLapsCount(deviceId);
        int myTotalTime = getTotalRaceTime(deviceId);

        for (int i = 0; i < count; i++ ) {
            if (i != deviceId && getIsPilotEnabled(i)) {
                int curDeviceLapsCount = getValidRaceLapsCount(i);
                int curDeviceTotalTime = getTotalRaceTime(i);
                if (myLapsCount > curDeviceLapsCount) {
                    myPosition--;
                } else if (curDeviceLapsCount == myLapsCount && myTotalTime <= curDeviceTotalTime) {
                    myPosition--;
                }
            }
        }
        return myPosition;
    }

    public int getValidRaceLapsCount(int deviceId) {
        int lapsCount = getLapsCount(deviceId);
        return lapsCount < raceState.lapsToGo ? lapsCount : raceState.lapsToGo;
    }

    public int getTotalRaceTime(int deviceId) {
        if (raceResults.size() <= deviceId) return 0;

        int total = 0;
        int firstLapIndex = shouldSkipFirstLap ? 1 : 0;
        int lapsCount = getValidRaceLapsCount(deviceId);
        int lastLapIndex = shouldSkipFirstLap ? lapsCount : lapsCount - 1;

        if (raceResults.get(deviceId).size() <= lastLapIndex) return 0;

        for (int i = firstLapIndex; i <= lastLapIndex; i++) {
            total += raceResults.get(deviceId).get(i).getMs();
        }
        return total;
    }

    public LapResult getLastLap(int deviceId) {
        if (raceResults.size() <= deviceId) return null;
        ArrayList<LapResult> rr = raceResults.get(deviceId);
        int count = rr.size();
        if (count < 1) {
            return null;
        }
        return rr.get(count-1);
    }

    public int getBestLapId(int deviceId) {
        if (raceResults.size() <= deviceId) {
            return -1;
        }
        ArrayList<LapResult> rr = raceResults.get(deviceId);
        int count = rr.size();
        if (count < 1 || (count < 2 && shouldSkipFirstLap)) {
            return -1;
        }
        int allowedLaps = shouldSkipFirstLap ? raceState.lapsToGo : raceState.lapsToGo - 1;
        int startLap = shouldSkipFirstLap ? 1 : 0;
        int bestId = startLap;
        int lastLap = Math.min(count-1, allowedLaps);

        for (int i = startLap; i <= lastLap; i++) {
            LapResult bestLap = rr.get(bestId);
            LapResult curLap = rr.get(i);
            if (curLap.getMs() < bestLap.getMs()) {
                bestId = i;
            }
        }
        return bestId;
    }

    public int getLapsCount(int deviceId) {
        if (raceResults == null) {
            return 0;
        }
        if (deviceId > raceResults.size()) {
            return 0;
        }
        int size = raceResults.get(deviceId).size();
        int count  = shouldSkipFirstLap ? size - 1 : size;
        return count < 0 ? 0 : count;
    }

    public boolean isJustFinished(int deviceId) {
        int laps = getLapsCount(deviceId);
        return laps == raceState.lapsToGo;
    }

    public boolean getIsFinished(int deviceId) {
        int laps = getLapsCount(deviceId);
        return laps >= raceState.lapsToGo;
    }

    public int getCurrentRssi(int deviceId) {
        if (deviceStates == null) return 0;
        if (deviceStates.size() <= deviceId) return 0;
        return deviceStates.get(deviceId).currentRSSI;
    }

    public int getRssiThreshold(int deviceId) {
        if (deviceStates == null) return 0;
        if (deviceStates.size() <= deviceId) return 0;
        return deviceStates.get(deviceId).threshold;
    }

    public String getBandText(int deviceId) {
        if (deviceStates == null) return "";
        if (deviceStates.size() <= deviceId) return "";
        int band = deviceStates.get(deviceId).band;
        if (band < bandNames.length) {
            return bandNames[band];
        } else {
            return "(#" + band + ")?";
        }
    }

    public String getChannelText(int deviceId) {
        if (deviceStates == null) return "";
        if (deviceStates.size() <= deviceId) return "";
        return Integer.toString(deviceStates.get(deviceId).channel + 1);
    }

    public Boolean getIsPilotEnabled(int deviceId) {
        if (deviceStates == null) return false;
        if (deviceStates.size() <= deviceId) return false;
        return deviceStates.get(deviceId).isEnabled;
    }

    public static int convertRssiToProgress(int rssi) {
        return rssi - MIN_RSSI;
    }

    public void clearRssi() {
        int count = deviceStates.size();
        for(int i = 0; i < count; i++) {
            deviceStates.get(i).currentRSSI = 0;
        }
    }

    public boolean areAllThresholdsSet() {
        for(DeviceState ds: deviceStates) {
            if (ds.threshold < MIN_RSSI && ds.isEnabled) {
                return false;
            }
        }
        return true;
    }

    public boolean areAllEnabledDevicesCalibrated() {
        //no need to calibrate device for single enabled pilot
        if (AppState.getInstance().getEnabledPilotsCount() == 1) {
            return true;
        }
        for (DeviceState ds: deviceStates) {
            if (!ds.isCalibrated && ds.isEnabled) {
                return false;
            }
        }
        return true;
    }

    public int getEnabledPilotsCount() {
        int count = deviceStates.size();
        int result = 0;
        for (int i = 0; i< count; i++) {
            if (deviceStates.get(i).isEnabled) {
                result++;
            }
        }
        return result;
    }
    //---------------------------------------------------------------------
    public void sendBtCommand(String cmd) {
        if (bt == null) return;
        bt.send(cmd + (char)DELIMITER);
    }

    public void onConnected() {
        isConnected = true;
        sendBtCommand("N0");
        wereDevicesConfigured = false;
    }

    public void onDisconnected() {
        isConnected = false;
        clearRssi();
        clearVoltage();
        emitEvent(DataAction.DeviceRSSI);
    }

    //---------------------------------------------------------------------
    public void playTone(int tone, int duration) {
        mToneGenerator.startTone(tone, duration);
    }

    public void speakMessage(String msg) {
        if (shouldSpeakMessages) {
            textSpeaker.speak(msg);
        }
    }
    //---------------------------------------------------------------------
    public void setNumberOfDevices(int n) {
        if (n <= 0) return;

        numberOfDevices = n;
        actualizeDeviceStates();
        actualizeRaceResults();
        actualizeDeviceTransmissionStates();
        emitEvent(DataAction.NDevices);

        resetDeviceTransmissionStates();
        sendBtCommand("R*A");
    }

    public void resetDeviceTransmissionStates() {
        for(int i=0; i<deviceTransmissionStates.size(); i++) {
            deviceTransmissionStates.set(i, false);
        }
    }

    public void changeDeviceChannel(int deviceId, int channel) {
        DeviceState currentState = deviceStates.get(deviceId);
        if (currentState == null) {
            return;
        }
        if (currentState.channel != channel) {
            currentState.channel = channel;
            emitEvent(DataAction.DeviceChannel);
            AppPreferences.save(AppPreferences.DEVICE_CHANNELS);
        }
    }

    public void changeDeviceBand(int deviceId, int band) {
        DeviceState currentState = deviceStates.get(deviceId);
        if (currentState == null) {
            return;
        }
        if (currentState.band != band) {
            currentState.band = band;
            emitEvent(DataAction.DeviceBand);
            AppPreferences.save(AppPreferences.DEVICE_BANDS);
        }
    }

    public void changeDevicePilot(int deviceId, String pilot) {
        DeviceState currentState = deviceStates.get(deviceId);
        if (currentState == null) {
            return;
        }
        if (!currentState.pilotName.equals(pilot)) {
            currentState.pilotName = pilot;
            emitEvent(DataAction.DevicePilot);
            AppPreferences.save(AppPreferences.DEVICE_PILOTS);
        }
    }

    public void updatePilotNamesInEdits() {
        emitEvent(DataAction.SPECIAL_DevicePilot_EditUpdate);
    }

    public void changeDeviceThreshold(int deviceId, int threshold) {
        DeviceState currentState = deviceStates.get(deviceId);
        if (currentState == null) {
            return;
        }
        if (currentState.threshold != threshold) {
            currentState.threshold = threshold;
            emitEvent(DataAction.DeviceThreshold);
        }
    }

    public void changeDeviceRSSI(int deviceId, int rssi) {
        DeviceState currentState = deviceStates.get(deviceId);
        if (currentState == null) {
            return;
        }
        if (currentState.currentRSSI != rssi) {
            currentState.currentRSSI = rssi;
            emitEvent(DataAction.DeviceRSSI);
        }
    }

    public void changeRaceMinLapTime(int minLapTime) {
        if (raceState == null) {
            return;
        }
        if (raceState.minLapTime!= minLapTime) {
            raceState.minLapTime = minLapTime;
            emitEvent(DataAction.RaceMinLap);
            AppPreferences.save(AppPreferences.MIN_LAP_TIME);
        }
    }

    public void changeRaceLaps(int laps) {
        if (raceState == null || laps < 1) {
            return;
        }
        if (raceState.lapsToGo!= laps) {
            raceState.lapsToGo = laps;
            emitEvent(DataAction.RaceLaps);
            AppPreferences.save(AppPreferences.LAPS_TO_GO);
        }
    }

    public void changeTimeToPrepareForRace(int time) {
        if (time < 0) {
            return;
        }
        if (timeToPrepareForRace != time) {
            timeToPrepareForRace = time;
            emitEvent(DataAction.PreparationTime);
            AppPreferences.save(AppPreferences.PREPARATION_TIME);
        }
    }

    public void changeRaceState(boolean isStarted) {
        if (raceState == null) {
            return;
        }
        if (raceState.isStarted!= isStarted) {
            raceState.isStarted = isStarted;
            emitEvent(DataAction.RaceState);
            if (!isStarted && isDevicesInitializationOver()) {
                speakMessage("Race is finished");
            }
        }
    }

    public void changeDeviceSoundState(boolean isSoundEnabled) {
        this.isDeviceSoundEnabled = isSoundEnabled;
        emitEvent(DataAction.SoundEnable);
        AppPreferences.save(AppPreferences.ENABLE_DEVICE_SOUNDS);
    }

    public void changeSkipFirstLap(boolean shouldSkip) {
        if (shouldSkipFirstLap != shouldSkip) {
            shouldSkipFirstLap = shouldSkip;
            emitEvent(DataAction.SkipFirstLap);
            AppPreferences.save(AppPreferences.SKIP_FIRST_LAP);
        }
    }

    public void changeShouldSpeakLapTimes(boolean shouldSpeak) {
        if (shouldSpeakLapTimes != shouldSpeak) {
            shouldSpeakLapTimes = shouldSpeak;
            emitEvent(DataAction.SpeakLapTimes);
            AppPreferences.save(AppPreferences.SPEAK_LAP_TIMES);
        }
    }

    public void changeShouldSpeakMessages(boolean shouldSpeak) {
        if (shouldSpeakMessages != shouldSpeak) {
            shouldSpeakMessages = shouldSpeak;
            emitEvent(DataAction.SpeakMessages);
            AppPreferences.save(AppPreferences.SPEAK_MESSAGES);
        }
    }

    /*
        deviceId is zero-based
        lapNumber is zero-based
     */
    public void addLapResult(int deviceId, int lapNumber, int lapTime) {
        if (deviceId >= numberOfDevices) {
            return;
        }
        //don't track laps from disabled device
        if (!deviceStates.get(deviceId).isEnabled) {
            return;
        }
        actualizeRaceResults();
        ArrayList<LapResult> deviceResults = raceResults.get(deviceId);
        if (deviceResults == null) {
            return;
        }
        int lapsCount = deviceResults.size();
        if (lapNumber >= lapsCount) {
            for (int i = lapsCount; i <= lapNumber; i++) {
                deviceResults.add(new LapResult());
            }
        }
        deviceResults.get(lapNumber).setMs(lapTime);
        emitEvent(DataAction.LapResult);

        if (isDevicesInitializationOver()) {
            playTone(TONE_LAP, DURATION_LAP);
            //speak lap times if initialization is over
            if (shouldSpeakLapTimes) {
                DeviceState currentState = deviceStates.get(deviceId);
                String textToSay = currentState.pilotName;
                if (!this.shouldSkipFirstLap || lapNumber != 0) {
                    if (isJustFinished(deviceId)) {
                        textToSay = textToSay + " finished";
                    } else if (getIsFinished(deviceId)) {
                        textToSay = textToSay + " already finished";
                    }
                    textSpeaker.speak(textToSay + ". Lap " + Integer.toString(lapNumber) + ". " + Utils.convertMsToSpeakableTime(lapTime));
                }
            }
        }
    }

    public void changeCalibration(int deviceId, boolean isCalibrated) {
        if (deviceId >= numberOfDevices) {
            return;
        }
        DeviceState currentState = deviceStates.get(deviceId);
        if (currentState == null) {
            return;
        }
        currentState.isCalibrated = isCalibrated;
        emitEvent(DataAction.DeviceCalibrationStatus);
    }

    public void changeDeviceCalibrationTime(int deviceId, int calibrationTime) {
        if (deviceId >= numberOfDevices) {
            return;
        }
        DeviceState currentState = deviceStates.get(deviceId);
        if (currentState == null) {
            return;
        }
        currentState.calibrationTime = calibrationTime;
        boolean doHaveAllTimes = true;
        for (DeviceState ds: deviceStates) {
            int time = ds.calibrationTime;
            if (time == 0) {
                doHaveAllTimes = false;
                break;
            }
        }
        if (doHaveAllTimes) {
            calculateAndSendCalibrationValues();
        }
    }

    public void calculateAndSendCalibrationValues() {
        int baseTime = CALIBRATION_TIME_MS;
        for (int i = 0; i < numberOfDevices; i++) {
            int time = deviceStates.get(i).calibrationTime;
            int diff = baseTime - time;
            int calibrationValue = 0;
            if (diff != 0) {
                calibrationValue = baseTime/diff;
            }
            deviceStates.get(i).calibrationValue = calibrationValue;
            sendBtCommand("C" + String.format("%X", i) + String.format("%08X", calibrationValue));
        }
        emitEvent(DataAction.DeviceCalibrationValue);
    }

    public void changeRssiMonitorState(boolean isMonitorOn) {
        if (isMonitorOn == isRssiMonitorOn) {
            return;
        }
        isRssiMonitorOn = isMonitorOn;
        if (!isRssiMonitorOn) {
            clearRssi();
            emitEvent(DataAction.DeviceRSSI);
        }
        emitEvent(DataAction.RSSImonitorState);
    }

    //use to determine if all devices reported their state after connection
    public boolean isDevicesInitializationOver() {
        int count = deviceTransmissionStates.size();
        if (count == 0) {
            return false;
        }
        for (int i = 0; i < count; i++) {
            if (!deviceTransmissionStates.get(i)) {
                return false;
            }
        }
        return true;
    }

    public void receivedEndOfSequence(int deviceId) {
        deviceTransmissionStates.set(deviceId, true);

        if (isDevicesInitializationOver()) {
            //run or stop RSSI monitoring after connecction, only after all device states are received
            if (raceState == null) {
                return;
            }
            if (raceState.isStarted && isRssiMonitorOn) {
                sendBtCommand("R*v"); // turn RSSI Monitoring off
            }
            if (!raceState.isStarted && !isRssiMonitorOn) {
                sendBtCommand("R*V"); // turn RSSI Monitoring on
            }
            //also decide to apply preferences after all states are received
            AppPreferences.applyInAppPreferences();

            restartBatteryMonitoringHandlers();

            if (!wereDevicesConfigured) {
                AppPreferences.applyDeviceDependentPreferences();
                wereDevicesConfigured = true;
            }
        }
    }

    public void suspendBatteryMonitoringHandlers() {
        mBatteryMonitorHandler.removeMessages(0);
        mBatteryNotifyHandler.removeMessages(0);
    }

    public void restartBatteryMonitoringHandlers() {
        suspendBatteryMonitoringHandlers(); // this will remove pending messages to preserve messaging intervals
        mBatteryMonitorHandler.sendEmptyMessageDelayed(0, 0);
        mBatteryNotifyHandler.sendEmptyMessageDelayed(0, BATTERY_WARN_DELAY);
    }

    public void changeDeviceConfigStatus(int deviceId, boolean isConfigured) {
        wereDevicesConfigured = wereDevicesConfigured || isConfigured;
    }

    public void changeDeviceEnabled(int deviceId, boolean isEnabled) {
        if (deviceStates == null || deviceId >= deviceStates.size()) {
            return;
        }
        //TODO: update the code so that the below check is not necessary (now it prevents endless loop(!))
        // endless loop(!): click checkbox -> changeDeviceEnabled() -> emitEvent -> updateCheckbox -> changeDeviceEnabled()...
        // probably there are more such places
        if (deviceStates.get(deviceId).isEnabled == isEnabled) {
            return;
        }
        deviceStates.get(deviceId).isEnabled = isEnabled;
        emitEvent(DataAction.PilotEnabledDisabled);
        AppPreferences.save(AppPreferences.DEVICE_ENABLED);
    }

    public void recalculateVoltage() {
        if (!isLiPoMonitorEnabled) {
            return;
        }
        //calc voltage and percentage
        batteryVoltage = (double)lastVoltageReading * VOLTAGE_DIVIDER_CONSTANT * ARDUINO_VOLTAGE * (((double)batteryAdjustmentConst + 1000) / 1000) / ARDUINO_ANALOG_COUNTS;
        int cellsCount = (int)(batteryVoltage/VOLTAGE_LOW);
        double cellVoltage = batteryVoltage/cellsCount;
        int percent = (int)((cellVoltage - VOLTAGE_LOW) * 100 / (VOLTAGE_HIGH - VOLTAGE_LOW));
        percent = (percent > 130) ? 0 : (percent > 100) ? 100 : percent;
        batteryPercentage = percent;
        emitEvent(DataAction.BatteryVoltage);
    }

    public void changeVoltage(int voltageReading) {
        if (!isLiPoMonitorEnabled) {
            return;
        }
        lastVoltageReading = voltageReading;
        recalculateVoltage();
    }

    public void clearVoltage() {
        lastVoltageReading = 0;
        recalculateVoltage();
    }

    public void changeAdjustmentConst(int adjConst) {
        if (!isLiPoMonitorEnabled) {
            return;
        }
        if (adjConst < -100 || adjConst > 100) {
            return;
        }
        if (adjConst == batteryAdjustmentConst) {
            return;
        }
        batteryAdjustmentConst = adjConst;
        emitEvent(DataAction.VoltageAdjustmentConst);
        AppPreferences.save(AppPreferences.LIPO_ADJUSTMENT_CONST);
        recalculateVoltage();
    }

    public void changeEnableLiPoMonitor(boolean isEnabled) {
        if (isLiPoMonitorEnabled != isEnabled) {
            isLiPoMonitorEnabled = isEnabled;
            emitEvent(DataAction.LiPoMonitorEnable);
            AppPreferences.save(AppPreferences.LIPO_MONITOR_ENABLED);
            if (isEnabled) {
                restartBatteryMonitoringHandlers();
            } else {
                suspendBatteryMonitoringHandlers();
            }
        }
    }
}

