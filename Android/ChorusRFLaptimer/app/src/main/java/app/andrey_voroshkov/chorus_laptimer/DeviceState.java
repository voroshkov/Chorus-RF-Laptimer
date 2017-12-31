package app.andrey_voroshkov.chorus_laptimer;

/**
 * Created by Andrey_Voroshkov on 1/21/2017.
 */

public class DeviceState {
    public int channel;
    public int band;
    public int threshold;
    public String pilotName;
    public boolean isCalibrated;
    public int calibrationTime;
    public int calibrationValue;
    public int currentRSSI;
    public boolean isEnabled;
    public RssiRingBuffer historicalRSSI;

    DeviceState(int rssiBufferLength) {
        isCalibrated = false;
        isEnabled = true;
        historicalRSSI = new RssiRingBuffer(rssiBufferLength);
    }
}

