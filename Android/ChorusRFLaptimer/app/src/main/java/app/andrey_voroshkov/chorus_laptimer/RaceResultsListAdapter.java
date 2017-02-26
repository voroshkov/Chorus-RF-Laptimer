package app.andrey_voroshkov.chorus_laptimer;

import android.content.Context;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import app.andrey_voroshkov.chorus_laptimer.R;

import java.util.ArrayList;

/**
 * Created by Andrey_Voroshkov on 1/27/2017.
 */

public class RaceResultsListAdapter extends BaseExpandableListAdapter {
    private ArrayList<ArrayList<LapResult>> mGroups;
    private Context mContext;

    public RaceResultsListAdapter (Context context, ArrayList<ArrayList<LapResult>> groups){
        mContext = context;
        mGroups = groups;
    }

    @Override
    public int getGroupCount() {
        return mGroups != null ? mGroups.size() : 0;
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        int grpCnt = getGroupCount();
        if (grpCnt == 0 || grpCnt <= groupPosition) {
            return 0;
        }
        return mGroups.get(groupPosition).size();
    }

    @Override
    public Object getGroup(int groupPosition) {
        return mGroups.get(groupPosition);
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return mGroups.get(groupPosition).get(childPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView,
                             ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.race_result_list_group, null);
        }

        TextView textGroup = (TextView) convertView.findViewById(R.id.textGroupHeading);

        int laps = AppState.getInstance().getLapsCount(groupPosition);
        boolean isFinished = AppState.getInstance().getIsFinished(groupPosition);

        textGroup.setText(createGroupViewText(groupPosition, laps, isFinished));
        int colorId = isFinished ? R.color.colorRaceResultGroupFinished : R.color.colorRaceResultGroup;
        int color = ContextCompat.getColor(mContext,colorId);
        textGroup.setTextColor(color);

        TextView textLastLap = (TextView) convertView.findViewById(R.id.textLastLap);
        LapResult last = AppState.getInstance().getLastLap(groupPosition);
        textLastLap.setText(last != null ? last.getHMS() : "-");

        TextView textBestLap = (TextView) convertView.findViewById(R.id.textBestLap);
        int bestLapId = AppState.getInstance().getBestLapId(groupPosition);
        if (bestLapId != -1) {
            LapResult best = AppState.getInstance().raceResults.get(groupPosition).get(bestLapId);
            textBestLap.setText(best.getHMS());
        } else {
            textBestLap.setText("-");
        }

        return convertView;
    }

    private String createGroupViewText (int position, int laps, boolean isFinished) {
        String ch = AppState.getInstance().getChannelText(position);
        String band = AppState.getInstance().getBandText(position);
        DeviceState ds = AppState.getInstance().deviceStates.get(position);
        return ds.pilotName + " (C " + ch + ", " + band + " band) Laps: " + (laps <= 0 ? "-" : laps) + (isFinished ? " FINISHED" : "");
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
                             View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.race_result_list_child, null);

            Button button = (Button)convertView.findViewById(R.id.buttonChild);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Toast tst = Toast.makeText(mContext, "Clicked", Toast.LENGTH_SHORT);
                    tst.show();
                }
            });
        }

        int grpCnt = getGroupCount();
        int chldCnt = getChildrenCount(groupPosition);
        if (grpCnt <= groupPosition || chldCnt <= childPosition) {
            return convertView;
        }

        TextView textChild = (TextView) convertView.findViewById(R.id.textChild);
        textChild.setText("Lap # " + childPosition + ":  " + mGroups.get(groupPosition).get(childPosition).getHMS());

        int bestLapId = AppState.getInstance().getBestLapId(groupPosition);
        int colorId = (childPosition == bestLapId) ? R.color.colorRaceResultChildBest : R.color.colorRaceResultChild;
        int color = ContextCompat.getColor(mContext,colorId);
        textChild.setTextColor(color);

        return convertView;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }
}
