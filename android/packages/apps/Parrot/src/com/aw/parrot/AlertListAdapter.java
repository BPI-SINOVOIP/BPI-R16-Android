package com.aw.parrot;

import java.util.ArrayList;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import com.unisound.sim.im.alert.AlertData;

public class AlertListAdapter extends BaseAdapter{
	private ArrayList<AlertData> mList;
    private Context mContext;

    public AlertListAdapter(Context context, ArrayList<AlertData> list) {
        mContext = context;
        initData(mList);
    }

    private void initData(ArrayList<AlertData> list) {
        if (list != null) {
            mList = list;
        } else {
            mList = new ArrayList<AlertData>();
        }
    }

    public void notifyDataChange(ArrayList<AlertData> list) {
        initData(list);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return mList.size();
    }

    @Override
    public Object getItem(int position) {
        return mList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView textView = new TextView(mContext);
        textView.setTextSize(25);
        textView.setPadding(0, 10, 0, 10);
        AlertData alertData = mList.get(position);
        textView.setText(alertData.getContent() + "\t\t"
                + Utils.dateToString(alertData.getTriggerTime()));
        if (alertData.getTriggerTime() > System.currentTimeMillis()) {
            textView.setTextColor(mContext.getResources().getColor(R.color.black));
        } else {
            textView.setTextColor(mContext.getResources().getColor(R.color.gray));
        }
        return textView;
    }
}
