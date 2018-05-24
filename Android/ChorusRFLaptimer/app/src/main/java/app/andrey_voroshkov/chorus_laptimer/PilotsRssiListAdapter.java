package app.andrey_voroshkov.chorus_laptimer;

import android.content.Context;
import android.graphics.PorterDuff;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by Andrey_Voroshkov on 1/29/2017.
 */

public class PilotsRssiListAdapter extends BaseAdapter {
    private ArrayList<DeviceState> mDeviceStates;
    private Context mContext;

    public PilotsRssiListAdapter(Context context, ArrayList<DeviceState> deviceStates){
        mDeviceStates = deviceStates;
        mContext = context;
    }

    public static final int RSSI_LONG_STEP = 30;

    static class ViewHolder {
        View spacePilotColor;
        EditText edPilotName;
        TextView txtChannelLabel;
        TextView txtThresh;
        ProgressBar spinThresholdSetup;
        Button btnDecThr;
        Button btnIncThr;
        Button btnSetThr;
        ProgressBar rssiBar;
        CheckBox isPilotEnabled;
        LinearLayout innerGroup;
        TextWatcher edPilotTextWatcher;
    }

    private TextWatcher createTextWatcher(final int position) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                AppState.getInstance().changeDevicePilot(position, s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
    }
    @Override
    public int getCount() {
        return mDeviceStates != null ? mDeviceStates.size() : 0;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public Object getItem(int position) {
        return mDeviceStates.get(position);
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        final ViewHolder viewHolder;
        final String deviceId = String.format("%X", position);

        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.pilot_rssi_group, null);
            viewHolder = new ViewHolder();
            viewHolder.edPilotName = (EditText) convertView.findViewById(R.id.editPilotName);
            viewHolder.txtChannelLabel = (TextView) convertView.findViewById(R.id.txtChannelLabel);
            viewHolder.txtThresh = (TextView) convertView.findViewById(R.id.txtThreshold);
            viewHolder.spinThresholdSetup = (ProgressBar) convertView.findViewById(R.id.spinThresholdSetup);
            viewHolder.btnDecThr = (Button) convertView.findViewById(R.id.btnDecThresh);
            viewHolder.btnIncThr = (Button) convertView.findViewById(R.id.btnIncThresh);
            viewHolder.btnSetThr = (Button) convertView.findViewById(R.id.btnCapture);
            viewHolder.rssiBar = (ProgressBar) convertView.findViewById(R.id.rssiBar);
            viewHolder.isPilotEnabled = (CheckBox) convertView.findViewById(R.id.checkIsPilotEnabled);
            viewHolder.innerGroup = (LinearLayout) convertView.findViewById(R.id.innerGroupLayout);
            viewHolder.edPilotTextWatcher = createTextWatcher(position);
            viewHolder.spacePilotColor = convertView.findViewById(R.id.pilotColorStrip);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
            //here we have to reset the text watcher to avoid constantly adding new ones
            viewHolder.edPilotName.removeTextChangedListener(viewHolder.edPilotTextWatcher);
            viewHolder.edPilotTextWatcher = createTextWatcher(position);
            convertView.setTag(viewHolder);
        }

        //this listener should be initialized prior to changing text in edPilotName
        viewHolder.edPilotName.addTextChangedListener(viewHolder.edPilotTextWatcher);

        //this listener should be initialized prior to changing the isPilotEnabled checkbox
        viewHolder.isPilotEnabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Utils.enableDisableView(viewHolder.innerGroup, isChecked);
                AppState.getInstance().changeDeviceEnabled(position, isChecked);
            }
        });

        final DeviceState ds = AppState.getInstance().deviceStates.get(position);
        String ch = AppState.getInstance().getChannelText(position);
        String band = AppState.getInstance().getBandText(position);
        Boolean isEnabled = AppState.getInstance().getIsPilotEnabled(position);
        int thresholdSetupState = AppState.getInstance().getThresholdSetupState(position);

        if (thresholdSetupState > 0) {
            //showThresholdSetupProgress;
            viewHolder.spinThresholdSetup.setVisibility(View.VISIBLE);
            viewHolder.txtThresh.setVisibility(View.GONE);
            int colorId = (thresholdSetupState == 1) ?  R.color.colorWarn : R.color.colorPrimary;
            int color = ContextCompat.getColor(mContext, colorId);
            viewHolder.spinThresholdSetup.getIndeterminateDrawable().setColorFilter(color, PorterDuff.Mode.SRC_IN);
        } else {
            //hideThresholdSetupProgress;
            viewHolder.spinThresholdSetup.setVisibility(View.GONE);
            viewHolder.txtThresh.setVisibility(View.VISIBLE);
        }

        String mhzString = mContext.getString(R.string.mhz_string);
        String freq = AppState.getInstance().getFrequencyText(position) + " " + mhzString;

        viewHolder.spacePilotColor.setBackgroundColor(Utils.getBackgroundColorItem(position));

        viewHolder.txtThresh.setText(Integer.toString(ds.threshold));
        viewHolder.txtChannelLabel.setText(mContext.getString(R.string.channel_descriptor, band, ch, freq));
        viewHolder.isPilotEnabled.setChecked(isEnabled);

        if (ds.threshold == 0) {
            viewHolder.btnSetThr.setText(R.string.setup_set_threshold);
        } else {
            viewHolder.btnSetThr.setText(R.string.setup_clear_threshold);
        }

        viewHolder.edPilotName.setText(ds.pilotName, TextView.BufferType.EDITABLE);

        viewHolder.rssiBar.setMax(AppState.RSSI_SPAN);

        viewHolder.btnDecThr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int newThresh = ds.threshold - 1;
                if (newThresh < 0) {
                    newThresh = 0;
                }
                AppState.getInstance().sendBtCommand("R" + deviceId + "T" + String.format("%04X", newThresh));
            }
        });

        viewHolder.btnDecThr.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                int newThresh = ds.threshold - RSSI_LONG_STEP;
                if (newThresh < 0) {
                    newThresh = 0;
                }
                AppState.getInstance().sendBtCommand("R" + deviceId + "T" + String.format("%04X", newThresh));
                return true;
            }
        });

        viewHolder.btnIncThr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int newThresh = ds.threshold + 1;
                AppState.getInstance().sendBtCommand("R" + deviceId + "T" + String.format("%04X", newThresh));
            }
        });

        viewHolder.btnIncThr.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                int newThresh = ds.threshold + RSSI_LONG_STEP;
                if (newThresh > AppState.MAX_RSSI) {
                    newThresh = AppState.MAX_RSSI;
                }
                AppState.getInstance().sendBtCommand("R" + deviceId + "T" + String.format("%04X", newThresh));
                return true;
            }
        });

        viewHolder.btnSetThr.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (ds.threshold == 0) {
                    AppState.getInstance().sendBtCommand("R" + deviceId + "H1");
                } else {
                    AppState.getInstance().sendBtCommand("R" + deviceId + "T0000");
                }
                return false;
            }
        });

        return convertView;
    }
}
