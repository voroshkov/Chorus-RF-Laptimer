package app.andrey_voroshkov.chorus_laptimer;

/**
 * Created by Andrey_Voroshkov on 1/28/2017.
 */

public class LapResult {
    private int mLapTime;
    private String mHMS;

    LapResult() {
        this(0);
    }

    LapResult(int lapTime) {
        mLapTime = lapTime;
        mHMS = Utils.convertMsToHMS(lapTime);
    }

    public String getHMS () {
        return mHMS;
    }

    public int getMs () { return mLapTime; }

    public String setMs(int ms) {
        mLapTime = ms;
        mHMS = Utils.convertMsToHMS(ms);
        return mHMS;
    }
}
