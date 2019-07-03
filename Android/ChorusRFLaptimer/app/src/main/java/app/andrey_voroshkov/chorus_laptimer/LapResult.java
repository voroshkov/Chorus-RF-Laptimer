package app.andrey_voroshkov.chorus_laptimer;

import java.util.Date;

/**
 * Created by Andrey_Voroshkov on 1/28/2017.
 */

public class LapResult {
    private int mLapTime;
    private String mDisplayTime;
    private Date mRecordTime;

    LapResult() {
        this(0);
    }

    LapResult(int lapTime) {
        mRecordTime = new Date();
        mLapTime = lapTime;
        mDisplayTime = Utils.convertMsToDisplayTime(lapTime);
    }

    public String getDisplayTime () {
        return mDisplayTime;
    }

    public int getMs () { return mLapTime; }

    public Date getRecordDate() {
        return mRecordTime;
    }

    public String setMs(int ms) {
        if (mRecordTime == null) {
            mRecordTime = new Date();
        }
        mLapTime = ms;
        mDisplayTime = Utils.convertMsToDisplayTime(ms);
        return mDisplayTime;
    }
}
