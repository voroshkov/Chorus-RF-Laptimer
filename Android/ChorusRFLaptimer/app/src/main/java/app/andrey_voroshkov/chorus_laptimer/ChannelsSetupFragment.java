package app.andrey_voroshkov.chorus_laptimer;

import android.content.Context;
import android.graphics.PorterDuff;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * A placeholder fragment containing a simple view.
 */
public class ChannelsSetupFragment extends Fragment {
    /**
     * The fragment argument representing the section number for this
     * fragment.
     */
    private static final String ARG_SECTION_NUMBER = "section_number";

    private View mRootView;
    private Context mContext;

    public ChannelsSetupFragment() {
    }

    /**
     * Returns a new instance of this fragment for the given section
     * number.
     */
    public static ChannelsSetupFragment newInstance(int sectionNumber) {
        ChannelsSetupFragment fragment = new ChannelsSetupFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_SECTION_NUMBER, sectionNumber);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.channels_setup, container, false);
        mRootView = rootView;
        mContext = getContext();
        AppState.getInstance().addListener(new IDataListener() {
            @Override
            public void onDataChange(DataAction dataItemName) {
                switch (dataItemName) {
                    case NDevices:
                    case DeviceBand:
                    case DeviceChannel:
                        updateResults();
                        break;
                    case DeviceRSSI:
                    case Disconnect:
                        updateCurrentRSSI();
                        break;
                }
            }
        });
        useNewAdapter();
        return rootView;
    }
    public void useNewAdapter() {
        ListView listView = (ListView)mRootView.findViewById(R.id.lvChannels);
        ChannelsListAdapter adapter = new ChannelsListAdapter(mContext, AppState.getInstance().deviceStates);
        listView.setAdapter(adapter);
    }

    public void updateResults() {
        ListView listView = (ListView)mRootView.findViewById(R.id.lvChannels);
        ((BaseAdapter) listView.getAdapter()).notifyDataSetChanged();
    }

    public void updateCurrentRSSI() {
        ListView mListView = (ListView)mRootView.findViewById(R.id.lvChannels);
        int first = mListView.getFirstVisiblePosition();
        int last = mListView.getLastVisiblePosition();
        for (int i = first; i <= last; i++) {
            View convertView = mListView.getChildAt(i - first);
            if (convertView != null) {
                ProgressBar bar = (ProgressBar) convertView.findViewById(R.id.rssiBar);
                TextView txt = (TextView) convertView.findViewById(R.id.txtRssi);
                int curRssi = AppState.getInstance().getCurrentRssi(i);
                int rssiThreshold = AppState.getInstance().getRssiThreshold(i);
                bar.setProgress(AppState.convertRssiToProgress(curRssi));
                int colorId = (curRssi > rssiThreshold) ? R.color.colorAccent : R.color.colorPrimary;
                int color = ContextCompat.getColor(mContext, colorId);
                bar.getProgressDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
                txt.setText(Integer.toString(curRssi));
            }
        }
    }
}
