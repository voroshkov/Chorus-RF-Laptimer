package app.andrey_voroshkov.chorus_laptimer;

import android.graphics.PorterDuff;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import app.andrey_voroshkov.chorus_laptimer.R;

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
        AppState.getInstance().addListener(new IDataListener() {
            @Override
            public void onDataChange(DataAction dataItemName) {
                switch (dataItemName) {
                    case NDevices:
                    case DeviceChannel:
                    case DeviceBand:
                    case DeviceThreshold:
                        updateResults();
                        break;
                    case DeviceRSSI:
                        updateCurrentRSSI();
                        break;
                }
            }
        });
        useNewAdapter();
        return rootView;
    }
    public void useNewAdapter() {
        ListView listView = (ListView)mRootView.findViewById(R.id.lvPilots);
        PilotsRssiListAdapter adapter = new PilotsRssiListAdapter(getContext(), AppState.getInstance().deviceStates);
//        ChannelsListAdapter adapter = new ChannelsListAdapter(getContext(), AppState.getInstance().deviceStates);
        listView.setAdapter(adapter);
    }

    public void updateResults() {
        ListView listView = (ListView)mRootView.findViewById(R.id.lvPilots);
        ((BaseAdapter) listView.getAdapter()).notifyDataSetChanged();
    }

    public void updateCurrentRSSI() {
        ListView mListView = (ListView)mRootView.findViewById(R.id.lvPilots);
        int first = mListView.getFirstVisiblePosition();
        int last = mListView.getLastVisiblePosition();
        int count = AppState.getInstance().deviceStates.size();
        for (int i = 0; i < count; i++) {
            if (i < first || i > last) {
                break;
            } else {
                View convertView = mListView.getChildAt(i);
                ProgressBar bar = (ProgressBar) convertView.findViewById(R.id.rssiBar);
                TextView txt = (TextView) convertView.findViewById(R.id.txtRssi);
                int curRssi = AppState.getInstance().getCurrentRssi(i);
                int rssiThreshold = AppState.getInstance().getRssiThreshold(i);
                bar.setProgress(AppState.convertRssiToProgress(curRssi));
                int colorId = (curRssi > rssiThreshold) ? R.color.colorAccent : R.color.colorPrimary;
                int color = ContextCompat.getColor(getContext(),colorId);
                bar.getProgressDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
                txt.setText(Integer.toString(curRssi));
            }
        }
    }
}
