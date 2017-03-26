package app.andrey_voroshkov.chorus_laptimer;

/**
 * Created by Andrey_Voroshkov on 1/28/2017.
 */

public class LapResult {
    private int mLapTime;
    private String mDisplayTime;

    LapResult() {
        this(0);
    }

    LapResult(int lapTime) {
        mLapTime = lapTime;
        mDisplayTime = Utils.convertMsToDisplayTime(lapTime);
    }

    public String getDisplayTime () {
        return mDisplayTime;
    }

    public int getMs () { return mLapTime; }

    public String setMs(int ms) {
        mLapTime = ms;
        mDisplayTime = Utils.convertMsToDisplayTime(ms);
        return mDisplayTime;
    }
}
