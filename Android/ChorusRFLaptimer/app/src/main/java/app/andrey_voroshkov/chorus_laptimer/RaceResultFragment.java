package app.andrey_voroshkov.chorus_laptimer;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

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
                    AppState.getInstance().sendBtCommand("R*R1");
                } else {
                    this.sendEmptyMessageDelayed(counter - 1, 1000);
                    //first 3 beeps
                    if (counter < AppState.START_BEEPS_COUNT) {
                        AppState.getInstance().sendBtCommand("T"+ String.format("%01X", counter));
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
                    case RaceIsFinished:
                        triggerCSVReportGeneration();
                        break;
                    case DeviceThreshold:
                    case Disconnect:
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
                pd.setMessage(getString(R.string.calibrate_timers));
                pd.setMax(AppState.CALIBRATION_TIME_MS);
                pd.setIndeterminate(false);
                pd.setCancelable(false);
                pd.setProgressNumberFormat(null);
                pd.show();
                AppState.getInstance().clearOldCalibrationTimes();
                final long msStart = System.currentTimeMillis();
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
                                long msEnd = System.currentTimeMillis();
                                AppState.getInstance().sendBtCommand("R*t");
                                AppState.getInstance().setCalibrationActualTime((int)(msEnd - msStart));
                                pd.dismiss();
                                break;
                        }
                    }
                };
                AppState.getInstance().sendBtCommand("R*t");
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
                    AppState.getInstance().sendBtCommand("R*I0000");

                    Button btnRace = (Button) rootView.findViewById(R.id.btnStartRace);
                    btnRace.setText(R.string.starting_race);
                    AppState.getInstance().sendBtCommand("TP");
                    mIsStartingRace = true;
                    int timeBeforeRace = AppState.getInstance().timeToPrepareForRace;
                    if (timeBeforeRace >= AppState.MIN_TIME_BEFORE_RACE_TO_SPEAK)
                        AppState.getInstance().textSpeaker.speak(R.string.race_announcement_starting, timeBeforeRace - 2);

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
                    AppState.getInstance().sendBtCommand("R*R0");
                    AppState.getInstance().sendBtCommand("R*I0064");
                    return true;
                }  else if (mIsStartingRace) {
                    //TODO: move mIsStartingRace flag into appState, use updateButtons to update button captions
                    mIsStartingRace = false;
                    AppState.getInstance().sendBtCommand("R*R0"); // send end race (workaround for the led gate to switch to no-race mode)
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
            btnRace.setText(R.string.stop_race);
        } else {
            boolean areAllThrSet = AppState.getInstance().areAllThresholdsSet();
            int numberOfPilots = AppState.getInstance().getEnabledPilotsCount();
            if (!areAllCalibrated) {
                btnRace.setEnabled(false);
                btnRace.setText(R.string.start_race);
            } else if (areAllThrSet) {
                if (numberOfPilots == 0) {
                    btnRace.setEnabled(false);
                    btnRace.setText(R.string.start_race_validation_pilots);
                } else {
                    btnRace.setEnabled(true);
                    btnRace.setText(getResources().getQuantityString(R.plurals.pilots_in_race, numberOfPilots, numberOfPilots));
                }
            } else {
                btnRace.setEnabled(false);
                btnRace.setText(R.string.start_race_validation_thresholds);
            }
        }

        if (!AppState.getInstance().isConnected) {
            btnRace.setEnabled(false);
            btnCalibrate.setEnabled(false);
        }
    }

    private void triggerCSVReportGeneration(){
        String fileName = generateCSVReport();
        //if fileName = null, saving of file was not successful (HD space is low)
        if(fileName != null){
            Toast toast = Toast.makeText(getContext(), getString(R.string.report_file_name) + fileName, Toast.LENGTH_SHORT);
            toast.show();
        } else {
            Toast toast = Toast.makeText(getContext(), R.string.report_failure, Toast.LENGTH_SHORT);
            toast.show();
        }
    }

    /**
     * This function will generate the Report string which is to be written in the csv file
     * @return
     */
    private String generateCSVReportString(){
        AppState app = AppState.getInstance();
        ArrayList<ArrayList<LapResult>> raceResults = app.raceResults;

        StringBuilder sb = new StringBuilder();

        sb.append("LAP,PILOT,CHANNEL,FREQUENCY,TIME,RECORDED AT\n");

        int numLaps = app.raceState.lapsToGo;

        //startOfLapCount depends if it should skip First Lap.
        int startOfLapCount = 0;
        boolean shouldSkipFirstLap = app.shouldSkipFirstLap;
        if(shouldSkipFirstLap){
            startOfLapCount = 1;
        }
        //iterate per pilot
        for(int i = 0; i < app.deviceStates.size(); i++){
            ArrayList<LapResult> pilotResults = raceResults.get(i);
            int pilotLaps = pilotResults.size();
            String pilot = app.deviceStates.get(i).pilotName;
            String channel = app.getChannelText(i);
            String band = app.getBandText(i);
            String freq = app.getFrequencyText(i);


            //iterate per lap of each pilot. till allowed number of laps.
            for(int j = startOfLapCount; j < pilotLaps; j++){
                //if shouldSkipFirstLap, lapCount will start from 1
                int lapNumber = shouldSkipFirstLap ? j : j + 1;
                LapResult lapResult = pilotResults.get(j);
                String recordTime = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SZ").format(lapResult.getRecordDate());
                sb.append(lapNumber + "," + pilot + "," + band + channel + "," + freq+ "," + Utils.convertMsToReportTime(lapResult.getMs()) + "," + recordTime + "\n");
            }
        }
        System.out.println(sb.toString());
        return sb.toString();
    }

    /**
     * This function will generate the csv file report
     */
    private String generateCSVReport(){
        String fileName;

        //generate CSVReport String - to be written in csv file
        String report = generateCSVReportString();
        Date today = new Date();

        String path = Utils.getReportPath();

        // Create the folder.
        File folder = new File(path);
        if(!folder.exists()){
            folder.mkdirs();
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        //dateSuffix Format will be like this: 20170214_104304
        String dateSuffix = sdf.format(today);

        // Create the file.
        // File name will look like this: race_20170214_104304
        File file = new File(folder, "race_" + dateSuffix + ".csv");
        try
        {
            file.createNewFile();
            FileOutputStream fOut = new FileOutputStream(file);
            OutputStreamWriter myOutWriter = new OutputStreamWriter(fOut);
            myOutWriter.append(report);
            myOutWriter.close();
            fOut.flush();
            fOut.close();
            //set fileName for toast in RaceResultFragment
            fileName = file.getPath();
        }
        catch (IOException e)
        {
            return null;
        }

        return fileName;
    }

}
