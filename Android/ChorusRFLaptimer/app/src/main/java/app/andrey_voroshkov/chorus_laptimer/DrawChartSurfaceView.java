package app.andrey_voroshkov.chorus_laptimer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.Log;
import android.util.TypedValue;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Created by Andrey_Voroshkov on 12/2/2017.
 */

public class DrawChartSurfaceView extends SurfaceView implements SurfaceHolder.Callback {

    // convert 1dp to pixels
    private DrawThread drawThread;
    private int mDeviceId;
    private Context mContext;

    public DrawChartSurfaceView(Context context, int deviceId) {
        super(context);
        getHolder().addCallback(this);
        mDeviceId = deviceId;
        mContext = context;
    }

    public boolean isRefreshingData() {
        if (drawThread == null) return false;
        return drawThread.getShouldRefreshData();
    }

    public void pauseRefreshData() {
        if (drawThread == null) return;
        drawThread.setShouldRefreshData(false);
    }

    public void resumeRefreshData() {
        if (drawThread == null) return;
        drawThread.setShouldRefreshData(true);
    }

    public void pauseRedraw() {
        if (drawThread == null) return;
        drawThread.setActive(false);
    }

    public void resumeRedraw() {
        if (drawThread == null) return;
        drawThread.setActive(true);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        drawThread = new DrawThread(holder, mDeviceId);
        drawThread.setActive(true);
        drawThread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    public void remove() {
        killDrawThread();
    }

    private void killDrawThread() {
        boolean retry = true;
        drawThread.exitRun();
        while (retry) {
            try {
                drawThread.join();
                Log.i("draw", "Thread joined after surface destroyed");
                retry = false;
            } catch (InterruptedException e) {
            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        killDrawThread();
    }

    class DrawThread extends Thread {
        private boolean isExiting = false;
        private boolean isRedrawing = false;
        private boolean shouldRefreshData = true;

        final float dpSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, mContext.getResources().getDisplayMetrics());
        private final int GROUP_SIZE = 5; //group by this amount of neighbour values
        private final float CHART_BOTTOM_OFFSET = (float)4.5 * dpSize;
        private final float CHART_TOP_OFFSET = 0 * dpSize;
        private SurfaceHolder surfaceHolder;
        Paint mBarPaint = new Paint();
        Paint mGraphPaint = new Paint();
        Paint mThresholdPaint = new Paint();

        Paint mTextPaint = new Paint();
        private int mDeviceId;
        private int minRSSI = 1000;
        private int maxRSSI = 0;
        private int minDisplayRSSI;
        private int maxDisplayRSSI;
        private int mTextWidth;

        private int mTextHeight;

        public DrawThread(SurfaceHolder surfaceHolder, int deviceId) {
            this.surfaceHolder = surfaceHolder;
            mDeviceId = deviceId;

            minDisplayRSSI = AppState.MIN_RSSI;
            maxDisplayRSSI = AppState.MAX_RSSI;

            mBarPaint.setColor(Color.argb(50, 0, 0, 200));

            mGraphPaint.setColor(Color.argb(255, 200, 0, 0));
            mGraphPaint.setStrokeWidth(1 * dpSize);

            mThresholdPaint.setColor(Color.argb(255, 100, 80, 0));
            mThresholdPaint.setStyle(Paint.Style.STROKE);
            mThresholdPaint.setStrokeWidth(1 * dpSize);
            mThresholdPaint.setPathEffect(new DashPathEffect(new float[] {4 * dpSize, 4 * dpSize}, 0));

            mTextPaint.setColor(Color.argb(255, 0,0,0));
            mTextPaint.setStyle(Paint.Style.FILL);
            mTextPaint.setTextSize(10 * dpSize);

            Rect txtRect = new Rect();
            mTextPaint.getTextBounds("888", 0, 3, txtRect);
            mTextWidth = txtRect.width();
            mTextHeight = txtRect.height();
        }

        private void drawGraph(Canvas canvas, int[] data, int lastRssi, int threshold, boolean isRefreshingData) {
            // clean the canvas
            if (isRefreshingData) {
                canvas.drawRGB(255, 255, 255);
                mTextPaint.setStrokeWidth(1);
                mTextPaint.setTypeface(Typeface.DEFAULT);
            } else {
                canvas.drawRGB(210, 210, 210);
                mTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
            }

            int rssiValuesCount = data.length;

            int height = canvas.getHeight();
            int width = canvas.getWidth();

            minRSSI = minDisplayRSSI - minDisplayRSSI % GROUP_SIZE;
            maxRSSI = maxDisplayRSSI + (GROUP_SIZE - 1) - maxDisplayRSSI % GROUP_SIZE;

            int valuesRange = maxRSSI - minRSSI + 1;


            float xStepForGraph = (float)width/valuesRange;

            //threshold line
            float threshX = (threshold - minRSSI) * xStepForGraph;
            canvas.drawLine(threshX, CHART_TOP_OFFSET, threshX, height, mThresholdPaint);

            //threshold text
            String thresholdTxt = Integer.toString(threshold);
            canvas.drawText(thresholdTxt, threshX - mTextWidth - 10 * dpSize, height - CHART_BOTTOM_OFFSET - 1 * dpSize, mTextPaint);

            if (data.length == 0) return;

            //graph
            int minValue = data[0];
            int maxValue = data[0];
            int aboveThresholdCount = 0;
            float graphAreaHeight = height - CHART_BOTTOM_OFFSET - CHART_TOP_OFFSET;

            float prevX = -1;
            float prevY = -1;
            float yStepForGraph = graphAreaHeight/(rssiValuesCount - 1);
            for(int i=0; i<data.length; i++) {
                minValue = data[i] < minValue ? data[i] : minValue;
                maxValue = data[i] > maxValue ? data[i] : maxValue;
                if (data[i] > threshold) {
                    aboveThresholdCount++;
                }
                int val = data[i] - minRSSI;
                float x = val * xStepForGraph;
                float y = i * yStepForGraph + CHART_TOP_OFFSET;
                if (prevX > 0) {
                    canvas.drawLine(x, y, prevX, prevY, mGraphPaint);
                }
                prevX = x;
                prevY = y;
            }

            //min max text
            String minTxt = Integer.toString(minValue);
            int minValGraph = minValue - minRSSI;
            float minTextX = minValGraph * xStepForGraph - mTextWidth - 10 * dpSize;
            canvas.drawText(minTxt,minTextX - 1 * dpSize,mTextHeight + 2 * dpSize, mTextPaint);

            String maxTxt = Integer.toString(maxValue);
            int maxValGraph = maxValue - minRSSI;
            float maxTextX = maxValGraph * xStepForGraph + 10 * dpSize;
            canvas.drawText(maxTxt, maxTextX, mTextHeight + 2 * dpSize, mTextPaint);

            //threshold percentage text
            String abovePercentsTxt = Integer.toString((100 * aboveThresholdCount) / rssiValuesCount) + "%";
            canvas.drawText(abovePercentsTxt, maxTextX, height/2, mTextPaint);

            //last position bar
            float lastRssiGraph = lastRssi - minRSSI;
            float lastRssiX = lastRssiGraph * xStepForGraph;;
            canvas.drawRect(0, height - CHART_BOTTOM_OFFSET + 1 * dpSize, lastRssiX, height, mGraphPaint);

            //last position text
            String lastValueTxt = Integer.toString(lastRssi);
            int textX = width - mTextWidth;
            canvas.drawText(lastValueTxt, textX - 2 * dpSize, height - CHART_BOTTOM_OFFSET - 1 * dpSize, mTextPaint);

//            //histogram
//
//            int[] hist = new int[valuesRange];
//            for(int i = 0; i < data.length; i++) {
//                int val = data[i];
//                int histIdx = val - minRSSI;
//                if (histIdx >= 0 && histIdx < valuesRange) {
//                    hist[histIdx]++;
//                }
//            }
//
//            int barsCount = valuesRange / GROUP_SIZE;
//            float barWidth = (float)width/barsCount - 2 * dpSize;
//
//            float yStep = graphAreaHeight/rssiValuesCount;
//            for (int i = 0; i < barsCount; i++) {
//                float val = 0;
//                for (int j = 0; j < GROUP_SIZE; j++) {
//                    val += hist[i*GROUP_SIZE + j];
//                }
//                float x = 1 + 2*i + barWidth*i;
//                float barHeight = val * yStep;
//
////                    float y = height - (barHeight + CHART_BOTTOM_OFFSET);
////                    float bottomY = height - CHART_BOTTOM_OFFSET;
//
//                float y = (barHeight + CHART_TOP_OFFSET);
//                float bottomY = 0 + CHART_TOP_OFFSET;
//
//                canvas.drawRect(x, y, barWidth + x, bottomY, mBarPaint);
//            }

            // window borders
            Paint pntUp = new Paint();
            pntUp.setStrokeWidth(1 * dpSize);
            pntUp.setARGB(255, 30,30,30);
            canvas.drawLine(0,0,width, 0, pntUp);
            canvas.drawLine(0,0,0, height, pntUp);

            Paint pntDn = new Paint();
            pntDn.setStrokeWidth(1 * dpSize);
            pntDn.setARGB(255, 200,200,200);
            canvas.drawLine(0,height,width, height, pntDn);
            canvas.drawLine(width,0,width , height, pntDn);
        }


        public void exitRun () {
            this.isExiting = true;
        }

        public void setActive(boolean running) {
            this.isRedrawing = running;
        }

        public void setShouldRefreshData(boolean shouldRefresh) {
            shouldRefreshData = shouldRefresh;
        }

        public boolean getShouldRefreshData() {
            return shouldRefreshData;
        }

        @Override
        public void run() {
            Canvas canvas;
            int[] data = new int[0];
            while (!isExiting) {
                if (isRedrawing) {
                    canvas = null;
                    try {
                        canvas = surfaceHolder.lockCanvas(null);
                        if (canvas == null)
                            continue;
                        RssiRingBuffer rssiBuf = AppState.getInstance().deviceStates.get(mDeviceId).historicalRSSI;
                        int threshold = AppState.getInstance().deviceStates.get(mDeviceId).threshold;
                        // only start drawing when buf is filled with data
                        if (rssiBuf.getItemsCount() == rssiBuf.mSize) {
                            if (shouldRefreshData) {
                                data = rssiBuf.getOrderedData();
                            }
                            int lastReadValue = rssiBuf.lastValue;
                            this.drawGraph(canvas, data, lastReadValue, threshold, shouldRefreshData);
                        }
                    } finally {
                        if (canvas != null) {
                            surfaceHolder.unlockCanvasAndPost(canvas);
                        }
                    }
                    try {
                        sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }



};

