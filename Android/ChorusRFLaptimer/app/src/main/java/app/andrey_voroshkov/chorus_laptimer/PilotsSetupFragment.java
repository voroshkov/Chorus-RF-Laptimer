package app.andrey_voroshkov.chorus_laptimer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;

import java.util.ArrayList;

/**
 * A placeholder fragment containing a simple view.
 */
public class PilotsSetupFragment extends Fragment {
    /**
     * The fragment argument representing the section number for this
     * fragment.
     */
    private static final String ARG_SECTION_NUMBER = "section_number";

    private View mRootView;
    private Context mContext;
    private ArrayList<DrawChartSurfaceView> mDrawChartSurfaceViews = new ArrayList<>();

    public PilotsSetupFragment() {
    }

    /**
     * Returns a new instance of this fragment for the given section
     * number.
     */
    public static PilotsSetupFragment newInstance(int sectionNumber) {
        PilotsSetupFragment fragment = new PilotsSetupFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_SECTION_NUMBER, sectionNumber);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.pilots_setup, container, false);
        mRootView = rootView;
        mContext = getContext();
        AppState.getInstance().addListener(new IDataListener() {
            @Override
            public void onDataChange(DataAction dataItemName) {
                switch (dataItemName) {
                    case NDevices:
                        resetDrawChartSurfaceViews();
                        updateResults();
                        break;
                    case DeviceChannel:
                    case DeviceBand:
                    case DeviceThreshold:
                    case PilotEnabledDisabled:
                        updateResults();
                        break;
                    case DeviceRSSI:
                    case Disconnect:
                        updateCurrentRSSI();
                        break;
                    case SPECIAL_DevicePilot_EditUpdate:
                        updatePilotNames();
                }
            }
        });
        useNewAdapter();
        return rootView;
    }
    public void useNewAdapter() {
        ListView listView = (ListView)mRootView.findViewById(R.id.lvPilots);
        PilotsRssiListAdapter adapter = new PilotsRssiListAdapter(mContext, AppState.getInstance().deviceStates);
        listView.setAdapter(adapter);
    }

    public void updateResults() {
        ListView listView = (ListView)mRootView.findViewById(R.id.lvPilots);
        ((BaseAdapter) listView.getAdapter()).notifyDataSetChanged();
    }

    public void updatePilotNames() {
        ListView mListView = (ListView)mRootView.findViewById(R.id.lvPilots);
        int count = AppState.getInstance().deviceStates.size();
        for (int i = 0; i < count; i++) {
            View convertView = mListView.getChildAt(i);
            if (convertView != null) {
                EditText pilotName = (EditText) convertView.findViewById(R.id.editPilotName);
                String curPilotName = AppState.getInstance().deviceStates.get(i).pilotName;
                pilotName.setText(curPilotName);
            }
        }
    }

    public void resetDrawChartSurfaceViews() {
        mDrawChartSurfaceViews.clear();
        for (int i=0; i<mDrawChartSurfaceViews.size(); i++) {
            mDrawChartSurfaceViews.get(i).remove();
        }
        mDrawChartSurfaceViews.clear();

        for(int i = 0; i < AppState.getInstance().numberOfDevices; i++) {
            mDrawChartSurfaceViews.add(new DrawChartSurfaceView(mContext, i));
        }
    }

    public void updateCurrentRSSI() {
        ListView mListView = (ListView)mRootView.findViewById(R.id.lvPilots);
        int first = mListView.getFirstVisiblePosition();
        int last = mListView.getLastVisiblePosition();
        for (int i = 0; i < mDrawChartSurfaceViews.size(); i++) {
            if (i >= first && i <= last) {
                mDrawChartSurfaceViews.get(i).resumeRedraw();
            } else {
                mDrawChartSurfaceViews.get(i).pauseRedraw();
            }
        }

        for (int i = first; i <= last; i++) {
            View convertView = mListView.getChildAt(i - first);
            if (convertView != null) {
//                ProgressBar bar = (ProgressBar) convertView.findViewById(R.id.rssiBar);
//                TextView txt = (TextView) convertView.findViewById(R.id.txtRssi);
//                int curRssi = AppState.getInstance().getCurrentRssi(i);
//                int rssiThreshold = AppState.getInstance().getRssiThreshold(i);
//                bar.setProgress(AppState.convertRssiToProgress(curRssi));
//                int colorId = (curRssi > rssiThreshold) ? R.color.colorAccent : R.color.colorPrimary;
//                int color = ContextCompat.getColor(mContext, colorId);
//                bar.getProgressDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
//                txt.setText(Integer.toString(curRssi));

                final LinearLayout drawLayout = (LinearLayout)convertView.findViewById(R.id.layoutDrawSurface);
                drawLayout.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        View child = drawLayout.getChildAt(0);
                        DrawChartSurfaceView dv = (DrawChartSurfaceView)child;
                        if (dv.isRefreshingData()) {
                            dv.pauseRefreshData();
                        } else {
                            dv.resumeRefreshData();
                        }
                    }
                });
                if (drawLayout.getChildCount() == 0) {
                    drawLayout.addView(mDrawChartSurfaceViews.get(i));
                }
            }
        }
    }
}
