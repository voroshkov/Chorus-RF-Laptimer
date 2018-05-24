package app.andrey_voroshkov.chorus_laptimer;

import android.content.Context;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class RaceSetupFragment extends Fragment {
    /**
     * The fragment argument representing the section number for this
     * fragment.
     */
    private static final String ARG_SECTION_NUMBER = "section_number";

    private View mRootView;
    private Context mContext;

    public RaceSetupFragment() {

    }

    /**
     * Returns a new instance of this fragment for the given section
     * number.
     */
    public static RaceSetupFragment newInstance(int sectionNumber) {
        RaceSetupFragment fragment = new RaceSetupFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_SECTION_NUMBER, sectionNumber);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.race_setup, container, false);
        mRootView = rootView;
        mContext = getContext();

        updateText(rootView);
        updateSkipFirstLapCheckbox(rootView);
        updateSoundCheckbox(rootView);
        updateSpeakLapTimesCheckbox(rootView);
        updateSpeakMessagesCheckbox(rootView);
        updateSpeakEnglishOnlyCheckbox(rootView);
        updateLiPoMonitorCheckbox(rootView);
        updateBatteryProgressIndicator(rootView);

        AppState.getInstance().addListener(new IDataListener() {
            @Override
            public void onDataChange(DataAction dataItemName) {
                switch (dataItemName) {
                    case RaceMinLap:
                    case RaceLaps:
                    case PreparationTime:
                    case VoltageAdjustmentConst:
                        updateText(rootView);
                        break;
                    case SoundEnable:
                        updateSoundCheckbox(rootView);
                        break;
                    case SkipFirstLap:
                        updateSkipFirstLapCheckbox(rootView);
                        break;
                    case SpeakLapTimes:
                        updateSpeakLapTimesCheckbox(rootView);
                        break;
                    case SpeakMessages:
                        updateSpeakMessagesCheckbox(rootView);
                        break;
                    case SpeakEnglishOnly:
                        updateSpeakEnglishOnlyCheckbox(rootView);
                        break;
                    case BatteryVoltage:
                        updateBatteryProgressIndicator(rootView);
                        updateBatteryVoltageText(rootView);
                        break;
                    case LiPoMonitorEnable:
                        updateLiPoMonitorCheckbox(rootView);
                        break;
                }
            }
        });

        Button btnDecMLT = (Button) rootView.findViewById(R.id.btnDecMinLapTime);
        Button btnIncMLT = (Button) rootView.findViewById(R.id.btnIncMinLapTime);
        Button btnDecLaps = (Button) rootView.findViewById(R.id.btnDecLaps);
        Button btnIncLaps = (Button) rootView.findViewById(R.id.btnIncLaps);
        Button btnDecPrepTime = (Button) rootView.findViewById(R.id.btnDecPreparationTime);
        Button btnIncPrepTime = (Button) rootView.findViewById(R.id.btnIncPreparationTime);
        CheckBox chkSkipFirstLap = (CheckBox) rootView.findViewById(R.id.chkSkipFirstLap);
        CheckBox chkSpeakLapTimes = (CheckBox) rootView.findViewById(R.id.chkSpeakLapTimes);
        CheckBox chkSpeakMessages = (CheckBox) rootView.findViewById(R.id.chkSpeakMessages);
        CheckBox chkSpeakEnglishOnly = (CheckBox) rootView.findViewById(R.id.chkSpeakEnglishOnly);
        CheckBox chkDeviceSoundEnabled = (CheckBox) rootView.findViewById(R.id.chkDeviceSoundEnabled);
        CheckBox chkLiPoMonitor = (CheckBox) rootView.findViewById(R.id.chkLiPoMonitor);
        Button btnDecAdjust = (Button) rootView.findViewById(R.id.btnDecAdjustmentConst);
        Button btnIncAdjust = (Button) rootView.findViewById(R.id.btnIncAdjustmentConst);
        TextView txtVoltage = (TextView) rootView.findViewById(R.id.txtVoltage);
        LinearLayout layoutVoltage = (LinearLayout) rootView.findViewById(R.id.layoutVoltage);

        btnDecAdjust.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int adj = AppState.getInstance().batteryAdjustmentConst;
                AppState.getInstance().changeAdjustmentConst(adj - 1);
            }
        });

        btnIncAdjust.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int adj = AppState.getInstance().batteryAdjustmentConst;
                AppState.getInstance().changeAdjustmentConst(adj + 1);
            }
        });

        btnDecMLT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int mlt = AppState.getInstance().raceState.minLapTime;
                if (mlt > 0) {
                    mlt--;
                }
                AppState.getInstance().sendBtCommand("R*M" + String.format("%02X", mlt));
            }
        });

        btnIncMLT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int mlt = AppState.getInstance().raceState.minLapTime + 1;
                AppState.getInstance().sendBtCommand("R*M" + String.format("%02X", mlt));
            }
        });

        btnDecLaps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int laps = AppState.getInstance().raceState.lapsToGo;
                AppState.getInstance().changeRaceLaps(laps - 1);
            }
        });

        btnIncLaps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int laps = AppState.getInstance().raceState.lapsToGo;
                AppState.getInstance().changeRaceLaps(laps + 1);
            }
        });

        btnDecPrepTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int time = AppState.getInstance().timeToPrepareForRace;
                AppState.getInstance().changeTimeToPrepareForRace(time - 1);
            }
        });

        btnIncPrepTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int time = AppState.getInstance().timeToPrepareForRace;
                AppState.getInstance().changeTimeToPrepareForRace(time + 1);
            }
        });

        chkDeviceSoundEnabled.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean isSoundEnabled = AppState.getInstance().isDeviceSoundEnabled;
                AppState.getInstance().sendBtCommand("R*S" + (isSoundEnabled ? "0" : "1"));
            }
        });

        chkDeviceSoundEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                buttonView.setChecked(AppState.getInstance().isDeviceSoundEnabled);
            }
        });

        chkSkipFirstLap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean shouldSkip = AppState.getInstance().shouldSkipFirstLap;
                AppState.getInstance().sendBtCommand("R*1" + (shouldSkip ? "1" : "0"));
            }
        });

        chkSkipFirstLap.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                buttonView.setChecked(AppState.getInstance().shouldSkipFirstLap);
            }
        });

        chkSpeakLapTimes.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                AppState.getInstance().changeShouldSpeakLapTimes(isChecked);
            }
        });

        chkSpeakMessages.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                AppState.getInstance().changeShouldSpeakMessages(isChecked);
            }
        });

        chkSpeakEnglishOnly.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                AppState.getInstance().changeShouldSpeakEnglishOnly(isChecked);
            }
        });

        chkLiPoMonitor.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                AppState.getInstance().changeEnableLiPoMonitor(isChecked);
            }
        });

        txtVoltage.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                toggleVoltageAdjustmentControls(rootView);
                return false;
            }
        });

        layoutVoltage.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                toggleVoltageAdjustmentControls(rootView);
                return false;
            }
        });

        return rootView;
    }

    private void toggleVoltageAdjustmentControls(View rootView) {
        boolean isEnabled = AppState.getInstance().isLiPoMonitorEnabled;
        LinearLayout layout = (LinearLayout) rootView.findViewById(R.id.adjustmentLayout);
        boolean isVisible = layout.getVisibility() == View.VISIBLE;
        if (!isVisible) {
            if (isEnabled) {
                layout.setVisibility(View.VISIBLE);
            }
        } else {
            layout.setVisibility(View.GONE);
        }
    }

    private void updateText(View rootView) {
        TextView txtMinLaps = (TextView) rootView.findViewById(R.id.txtMinLapTime);
        txtMinLaps.setText(getString(R.string.setup_time, AppState.getInstance().raceState.minLapTime));

        TextView txtLaps = (TextView) rootView.findViewById(R.id.txtLaps);
        txtLaps.setText(Integer.toString(AppState.getInstance().raceState.lapsToGo));

        TextView txtPreparationTime = (TextView) rootView.findViewById(R.id.txtPreparationTime);
        txtPreparationTime.setText(getString(R.string.setup_time, AppState.getInstance().timeToPrepareForRace));

        TextView txtAdjustmentConst = (TextView) rootView.findViewById(R.id.txtAdjustmentConst);
        txtAdjustmentConst.setText(Integer.toString(AppState.getInstance().batteryAdjustmentConst));
    }

    private void updateSkipFirstLapCheckbox(View rootView) {
        CheckBox chkSkipFirstLap = (CheckBox) rootView.findViewById(R.id.chkSkipFirstLap);
        chkSkipFirstLap.setChecked(AppState.getInstance().shouldSkipFirstLap);
    }

    private void updateSoundCheckbox(View rootView) {
        CheckBox chkDeviceSoundEnabled = (CheckBox) rootView.findViewById(R.id.chkDeviceSoundEnabled);
        chkDeviceSoundEnabled.setChecked(AppState.getInstance().isDeviceSoundEnabled);
    }

    private void updateSpeakLapTimesCheckbox(View rootView) {
        CheckBox chkSpeakLapTimes = (CheckBox) rootView.findViewById(R.id.chkSpeakLapTimes);
        chkSpeakLapTimes.setChecked(AppState.getInstance().shouldSpeakLapTimes);
    }

    private void updateSpeakMessagesCheckbox(View rootView) {
        CheckBox chkSpeakMessages = (CheckBox) rootView.findViewById(R.id.chkSpeakMessages);
        chkSpeakMessages.setChecked(AppState.getInstance().shouldSpeakMessages);
    }

    private void updateSpeakEnglishOnlyCheckbox(View rootView) {
        CheckBox chkSpeakEnglishOnly = (CheckBox) rootView.findViewById(R.id.chkSpeakEnglishOnly);
        chkSpeakEnglishOnly.setChecked(AppState.getInstance().shouldSpeakEnglishOnly);
    }

    private void updateLiPoMonitorCheckbox(View rootView) {
        boolean isEnabled = AppState.getInstance().isLiPoMonitorEnabled;
        CheckBox chkLiPoMonitor = (CheckBox) rootView.findViewById(R.id.chkLiPoMonitor);
        chkLiPoMonitor.setChecked(isEnabled);
        LinearLayout layoutVoltage = (LinearLayout) rootView.findViewById(R.id.layoutVoltage);
        layoutVoltage.setVisibility(isEnabled ? View.VISIBLE : View.GONE);
        LinearLayout layoutAdjustment = (LinearLayout) rootView.findViewById(R.id.adjustmentLayout);
        layoutAdjustment.setVisibility(View.GONE);
    }

    private void updateBatteryProgressIndicator(View rootView) {
        ProgressBar bar = (ProgressBar) rootView.findViewById(R.id.batteryCharge);
        int percent = AppState.getInstance().batteryPercentage;
        bar.setProgress(percent);
        int colorId = (percent > 10) ? (percent > 20) ? R.color.colorPrimary : R.color.colorWarn: R.color.colorAccent;
        int color = ContextCompat.getColor(mContext, colorId);
        bar.getProgressDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    private void updateBatteryVoltageText(View rootView) {
        TextView txtVoltage = (TextView) rootView.findViewById(R.id.txtVoltage);
        txtVoltage.setText(String.format("%.2f", AppState.getInstance().batteryVoltage) + "V");

    }
}
