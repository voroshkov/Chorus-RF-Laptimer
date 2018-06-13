package app.andrey_voroshkov.chorus_laptimer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import app.andrey_voroshkov.chorus_laptimer.R;

import java.util.ArrayList;

/**
 * Created by Andrey_Voroshkov on 1/29/2017.
 */

public class ChannelsListAdapter extends BaseAdapter {
    private ArrayList<DeviceState> mDeviceStates;
    private Context mContext;

    public ChannelsListAdapter(Context context, ArrayList<DeviceState> deviceStates){
        mDeviceStates = deviceStates;
        mContext = context;
    }

    static class ViewHolder {
        View spaceChannelColor;
        TextView txtChannel;
        TextView txtFreq;
        Button btnDecCh;
        Button btnIncCh;

        TextView txtBand;
        Button btnDecBand;
        Button btnIncBand;

        TextView txtDeviceLabel;

        ProgressBar rssiBar;
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
        ViewHolder viewHolder;
        final String deviceId = String.format("%X", position);

        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(mContext);
            convertView = inflater.inflate(R.layout.channel_group, null);
            viewHolder = new ViewHolder();
            viewHolder.txtDeviceLabel = (TextView) convertView.findViewById(R.id.txtDeviceLabel);
            viewHolder.txtChannel = (TextView) convertView.findViewById(R.id.txtChannel);
            viewHolder.btnDecCh = (Button) convertView.findViewById(R.id.btnDecChannel);
            viewHolder.btnIncCh = (Button) convertView.findViewById(R.id.btnIncChannel);
            viewHolder.txtBand = (TextView) convertView.findViewById(R.id.txtBand);
            viewHolder.btnDecBand = (Button) convertView.findViewById(R.id.btnDecBand);
            viewHolder.btnIncBand = (Button) convertView.findViewById(R.id.btnIncBand);
            viewHolder.rssiBar = (ProgressBar) convertView.findViewById(R.id.rssiBar);
            viewHolder.spaceChannelColor = convertView.findViewById(R.id.channelColorStrip);
            viewHolder.txtFreq = (TextView) convertView.findViewById(R.id.txtFreq);
            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }


        viewHolder.txtDeviceLabel.setText(mContext.getString(R.string.device_number, position+1));
        viewHolder.txtChannel.setText(AppState.getInstance().getChannelText(position));
        viewHolder.txtBand.setText(AppState.getInstance().getBandText(position));
        viewHolder.spaceChannelColor.setBackgroundColor(Utils.getBackgroundColorItem(position));
        viewHolder.rssiBar.setMax(AppState.RSSI_SPAN);
        String mhzString = mContext.getString(R.string.mhz_string);
        viewHolder.txtFreq.setText(AppState.getInstance().getFrequencyText(position) + " " + mhzString);

        viewHolder.btnDecCh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int newChannel = AppState.getInstance().deviceStates.get(position).channel - 1;
                if (newChannel >= 0) {
                    AppState.getInstance().sendBtCommand("R" + deviceId + "C" + String.format("%X", newChannel));
                }
            }
        });

        viewHolder.btnIncCh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int newChannel = AppState.getInstance().deviceStates.get(position).channel + 1;
                if (newChannel <= 7) {
                    AppState.getInstance().sendBtCommand("R" + deviceId + "C" + String.format("%X", newChannel));
                }
            }
        });

        viewHolder.btnDecBand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int newBand = AppState.getInstance().deviceStates.get(position).band - 1;
                if (newBand >= 0) {
                    AppState.getInstance().sendBtCommand("R" + deviceId + "B" + String.format("%X", newBand));
                }
            }
        });

        viewHolder.btnIncBand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int newBand = AppState.getInstance().deviceStates.get(position).band + 1;
                if (newBand <= 6) {
                    AppState.getInstance().sendBtCommand("R" + deviceId + "B" + String.format("%X", newBand));
                }
            }
        });

        return convertView;
    }
}
