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
        TextView txtChannel;
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
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
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

            convertView.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        viewHolder.txtDeviceLabel.setText("Device #" + (position + 1));
        viewHolder.txtChannel.setText(AppState.getInstance().getChannelText(position));
        viewHolder.txtBand.setText(AppState.getInstance().getBandText(position));

        viewHolder.rssiBar.setMax(AppState.RSSI_SPAN);

        viewHolder.btnDecCh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppState.getInstance().sendBtCommand("R" + deviceId + "c");
            }
        });

        viewHolder.btnIncCh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppState.getInstance().sendBtCommand("R" + deviceId + "C");
            }
        });

        viewHolder.btnDecBand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppState.getInstance().sendBtCommand("R" + deviceId + "b");
            }
        });

        viewHolder.btnIncBand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppState.getInstance().sendBtCommand("R" + deviceId + "B");
            }
        });

        return convertView;
    }
}
