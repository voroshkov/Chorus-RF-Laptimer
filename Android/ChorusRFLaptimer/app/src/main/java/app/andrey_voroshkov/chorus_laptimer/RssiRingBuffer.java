package app.andrey_voroshkov.chorus_laptimer;

/**
 * Created by Andrey_Voroshkov on 11/13/2017.
 */

public class RssiRingBuffer {
    int[] mBuffer;
    int mHead = 0; // write index
    int mSize = 0;
    int mItemsCount = 0;
    boolean mIsDirty = false; // true if new items were added since last getData() call
    int lastValue = 0;


    RssiRingBuffer(int size) {
        if (size <= 0) {
            throw new IndexOutOfBoundsException("Bad buffer size. Should be > 0");
        }
        mBuffer = new int[size];
        mSize = size;
    }

    public void clear() {
        mItemsCount = 0;
        mHead = 0;
        mIsDirty = false;
    }

    public void write(int value) {
        mBuffer[mHead] = value;
        lastValue = value;
        mHead++;
        if (mHead >= mSize) {
            mHead = 0;
        }
        if (mItemsCount < mSize) {
            mItemsCount++;
        }
        mIsDirty = true;
    }

    public int getItemsCount() {
        return mItemsCount;
    }

    public int[] getData() {
        // no need to rearrange for histogram creation
        int[] result = new int[mSize];
        System.arraycopy(mBuffer, 0, result, 0, mSize);
        mIsDirty = false;
        return result;
    }

    public int[] getOrderedData() {
        // oldest is 0th element
        int[] result = new int[mSize];
        mIsDirty = false;
        for(int i=0; i<mItemsCount; i++) {
            int sourceIndex = mHead - 1 - i;
            if (sourceIndex < 0) {
                sourceIndex = mSize + sourceIndex;
            }
            result[mItemsCount - 1 - i] = mBuffer[sourceIndex];
        }
        return result;
    }
}
