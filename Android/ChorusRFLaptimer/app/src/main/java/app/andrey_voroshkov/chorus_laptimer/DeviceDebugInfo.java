package app.andrey_voroshkov.chorus_laptimer;

public class DeviceDebugInfo {
    boolean dbg_hasLeftDeviceArea;
    boolean dbg_isMinLapExpired;
    int dbg_dynamicThreshold;
    int dbg_maxRssi;
    String dbg_proximity;

    DeviceDebugInfo() {
        dbg_proximity = "";
        dbg_hasLeftDeviceArea = false;
        dbg_isMinLapExpired = false;
        dbg_dynamicThreshold = 0;
        dbg_maxRssi = 0;
    }
}
