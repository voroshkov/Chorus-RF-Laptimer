package app.andrey_voroshkov.chorus_laptimer;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ExpandableListView;

import app.andrey_voroshkov.chorus_laptimer.R;

import java.util.ArrayList;

/**
 * A placeholder fragment containing a simple view.
 */
public class RaceResultFragment extends Fragment {
    /**
     * The fragment argument representing the section number for this
     * fragment.
     */
    private static final String ARG_SECTION_NUMBER = "section_number";

    private View mRootView;
    private RaceResultsListAdapter mAdapter;
    private boolean mIsStartingRace = false;
    private Handler mRaceStartingHandler;

    public RaceResultFragment() {
        mRaceStartingHandler = new Handler() {
            public void handleMessage(Message msg) {
                int counter = msg.what;
                if (counter == 0) {
                    //last beep
                    AppState.getInstance().playTone(AppState.TONE_GO, AppState.DURATION_GO);
                    AppState.getInstance().sendBtCommand("R*R");
                } else {
                    this.sendEmptyMessageDelayed(counter - 1, 1000);
                    //first 3 beeps
                    if (counter < 4) {
                        AppState.getInstance().playTone(AppState.TONE_PREPARE, AppState.DURATION_PREPARE);
                    }
                }
            }
        };
    }

    /**
     * Returns a new instance of this fragment for the given section
     * number.
     */
    public static RaceResultFragment newInstance(int sectionNumber) {
        RaceResultFragment fragment = new RaceResultFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_SECTION_NUMBER, sectionNumber);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.race_result, container, false);
        mRootView = rootView;

        useNewAdapter();

        AppState.getInstance().addListener(new IDataListener() {
            @Override
            public void onDataChange(DataAction dataItemName) {
                switch (dataItemName) {
                    case RaceState:
                    case NDevices:
                        if (AppState.getInstance().raceState.isStarted) {
                            mIsStartingRace = false;
                            resetRaceResults();
                        }
                        updateButtons(rootView);
                        break;
                    case DeviceThreshold:
                        updateButtons(rootView);
                        break;
                    case DeviceCalibrationStatus:
                        updateButtons(rootView);
                        break;
                    case PilotEnabledDisabled:
                        updateButtons(rootView);
                        updateResults();
                        break;
                    case LapResult:
                    case RaceLaps:
                    case SkipFirstLap:
//                    case DeviceChannel:
//                    case DeviceBand:
                        updateResults();
                        break;
                }
            }
        });

        Button btnRunRace = (Button) rootView.findViewById(R.id.btnStartRace);
        Button btnCalibrate = (Button) rootView.findViewById(R.id.btnCalibrate);

        btnCalibrate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final ProgressDialog pd = new ProgressDialog(getContext());
                pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                pd.setMessage("Calibrating timers...");
                pd.setMax(AppState.CALIBRATION_TIME_MS);
                pd.setIndeterminate(false);
                pd.setCancelable(false);
                pd.setProgressNumberFormat(null);
                pd.show();
                final Handler h = new Handler() {
                    public void handleMessage(Message msg) {
                        switch(msg.what) {
                            case 0:
                                if (pd.getProgress() < pd.getMax()) {
                                    pd.incrementProgressBy(AppState.CALIBRATION_TIME_MS/100);
                                    this.sendEmptyMessageDelayed(0, AppState.CALIBRATION_TIME_MS/100);
                                }
                                break;
                            case 1:
                                AppState.getInstance().sendBtCommand("R*i");
                                pd.dismiss();
                                break;
                        }
                    }
                };
                AppState.getInstance().sendBtCommand("R*I");
                h.sendEmptyMessage(0);
                h.sendEmptyMessageDelayed(1, AppState.CALIBRATION_TIME_MS);
            }
        });

        btnRunRace.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isStarted = AppState.getInstance().raceState.isStarted;
                if (!isStarted && !mIsStartingRace) {
                    //stop rssi monitoring first, then start race
                    AppState.getInstance().sendBtCommand("R*v");

                    Button btnRace = (Button) rootView.findViewById(R.id.btnStartRace);
                    btnRace.setText("Starting...");
                    mIsStartingRace = true;
                    int timeBeforeRace = AppState.getInstance().timeToPrepareForRace;
                    if (timeBeforeRace >= AppState.MIN_TIME_BEFORE_RACE_TO_SPEAK)
                        AppState.getInstance().speakMessage("Starting race in " + Integer.toString(timeBeforeRace - 2) + " seconds");

                    mRaceStartingHandler.sendEmptyMessage(timeBeforeRace);
                }
            }
        });

        // use long press to stop race to make sure it doesn't stop occasionally
        btnRunRace.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                boolean isStarted = AppState.getInstance().raceState.isStarted;
                if (isStarted) {
                    //stop race and start RSSI monitoring
                    AppState.getInstance().sendBtCommand("R*r");
                    AppState.getInstance().sendBtCommand("R*V");
                    return true;
                } else if (mIsStartingRace) {
                    //TODO: move mIsStartingRace flag into appState, use updateButtons to update button captions
                    mIsStartingRace = false;
                    mRaceStartingHandler.removeCallbacksAndMessages(null);
                    updateButtons(rootView);
                    return true;
                } else {
                    return false; //allow short click after long to start race on both short and long clicks
                }
            }
        });
        return rootView;
    }

    public void useNewAdapter() {
        ExpandableListView listView = (ExpandableListView)mRootView.findViewById(R.id.elvResults);
        mAdapter = new RaceResultsListAdapter(getContext(), AppState.getInstance().raceResults);
        listView.setAdapter(mAdapter);
    }

    public void updateResults() {
        mAdapter.notifyDataSetChanged();
    }

    public void resetRaceResults() {
        AppState.getInstance().resetRaceResults();
        useNewAdapter();
    }

    public void updateButtons(View rootView) {
        ArrayList<DeviceState> dsList = AppState.getInstance().deviceStates;

        boolean areAllCalibrated = AppState.getInstance().areAllEnabledDevicesCalibrated();

        Button btnCalibrate = (Button) rootView.findViewById(R.id.btnCalibrate);
        btnCalibrate.setEnabled(!areAllCalibrated);
        btnCalibrate.setVisibility(areAllCalibrated ? View.GONE : View.VISIBLE);

        Button btnRace = (Button) rootView.findViewById(R.id.btnStartRace);
        if (AppState.getInstance().raceState.isStarted) {
            btnRace.setEnabled(true);
            btnRace.setText("Stop Race (Long Press)");
        } else {
            boolean areAllThrSet = AppState.getInstance().areAllThresholdsSet();
            int numberOfPilots = AppState.getInstance().getEnabledPilotsCount();
            if (!areAllCalibrated) {
                btnRace.setEnabled(false);
                btnRace.setText("Start Race");
            } else if (areAllThrSet) {
                if (numberOfPilots == 0) {
                    btnRace.setEnabled(false);
                    btnRace.setText("Enable pilots to start Race");
                } else {
                    btnRace.setEnabled(true);
                    btnRace.setText("Start Race (" + Integer.toString(numberOfPilots) + (numberOfPilots == 1 ? "pilot" : "pilots") +  ")");
                }
            } else {
                btnRace.setEnabled(false);
                btnRace.setText("Set all thresholds before Race");
            }
        }
    }
}
