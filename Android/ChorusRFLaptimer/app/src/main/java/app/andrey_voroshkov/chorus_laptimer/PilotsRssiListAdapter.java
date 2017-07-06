package app.andrey_voroshkov.chorus_laptimer;

import android.content.Context;
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

import app.andrey_voroshkov.chorus_laptimer.R;

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

    static class ViewHolder {
        EditText edPilotName;
        TextView txtChannelLabel;
        TextView txtThresh;
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
            viewHolder.btnDecThr = (Button) convertView.findViewById(R.id.btnDecThresh);
            viewHolder.btnIncThr = (Button) convertView.findViewById(R.id.btnIncThresh);
            viewHolder.btnSetThr = (Button) convertView.findViewById(R.id.btnCapture);
            viewHolder.rssiBar = (ProgressBar) convertView.findViewById(R.id.rssiBar);
            viewHolder.isPilotEnabled = (CheckBox) convertView.findViewById(R.id.checkIsPilotEnabled);
            viewHolder.innerGroup = (LinearLayout) convertView.findViewById(R.id.innerGroupLayout);
            viewHolder.edPilotTextWatcher = createTextWatcher(position);
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


        DeviceState ds = AppState.getInstance().deviceStates.get(position);
        String ch = AppState.getInstance().getChannelText(position);
        String band = AppState.getInstance().getBandText(position);
        Boolean isEnabled = AppState.getInstance().getIsPilotEnabled(position);

        viewHolder.txtThresh.setText(Integer.toString(ds.threshold));
        viewHolder.txtChannelLabel.setText("Channel: #" + ch + " (" + band + ")");
        viewHolder.isPilotEnabled.setChecked(isEnabled);

        if (ds.threshold == 0) {
            viewHolder.btnSetThr.setText("Set");
        } else {
            viewHolder.btnSetThr.setText("Clear");
        }

        viewHolder.edPilotName.setText(ds.pilotName, TextView.BufferType.EDITABLE);

        viewHolder.rssiBar.setMax(AppState.RSSI_SPAN);

        viewHolder.btnDecThr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppState.getInstance().sendBtCommand("R" + deviceId + "t");
            }
        });

        viewHolder.btnIncThr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppState.getInstance().sendBtCommand("R" + deviceId + "T");
            }
        });

        viewHolder.btnSetThr.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppState.getInstance().sendBtCommand("R" + deviceId + "S");
            }
        });

        return convertView;
    }
}
