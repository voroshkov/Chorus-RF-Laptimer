package app.andrey_voroshkov.chorus_laptimer;

public class ConnectionTester {
    public static final int SEND_DELAY_MS = 50;

    static final int MAX_ITEMS = 100;
    int mOutCounter = 0;
    int mInCounter = 0;

    int[] mFailuresArray = new int[MAX_ITEMS];
    long[] mSendTime = new long[MAX_ITEMS];
    long[] mTimeDiffArray = new long[MAX_ITEMS];

    public long minDelay;
    public long maxDelay;
    public long avgDelay;

    ConnectionTester() {
        mInCounter = 0;
        for(int i=0; i< mFailuresArray.length; i++) {
            mFailuresArray[i] = 0;
        }
    }

    public double getFailuresPercentage() {
        int failures = 0;
        for(int i=0; i< mFailuresArray.length; i++) {
            failures += mFailuresArray[i];
        }
        return (double)(failures*100)/MAX_ITEMS;
    }

    public void calcDiffTimes() {
        maxDelay = 0;
        minDelay = 999999999;
        long averageDelaySum = 0;
        for(int i=0; i< mFailuresArray.length; i++) {
            if (mTimeDiffArray[i] > maxDelay) maxDelay = mTimeDiffArray[i];
            if (mTimeDiffArray[i] < minDelay) minDelay = mTimeDiffArray[i];
            averageDelaySum += mTimeDiffArray[i];
        }
        averageDelaySum -= minDelay + maxDelay; // remove min and max from avg
        avgDelay = averageDelaySum/MAX_ITEMS;
    }

    public void receiveNextValue(int value) {
        mTimeDiffArray[value] = System.currentTimeMillis() - mSendTime[value];
        while (mInCounter != value) {
            mInCounter++;
            mInCounter %=MAX_ITEMS;
            if (mInCounter != value) {
                mFailuresArray[mInCounter] = 1;
            } else {
                mFailuresArray[mInCounter] = 0;
            }
        }
    }
    public int getNextValueToSend() {
        mOutCounter++;
        mOutCounter%=MAX_ITEMS;
        mSendTime[mOutCounter] = System.currentTimeMillis();
        return mOutCounter;
    }
}
