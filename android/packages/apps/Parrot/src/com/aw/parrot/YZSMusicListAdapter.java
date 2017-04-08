package com.aw.parrot;

import java.util.ArrayList;
import java.util.zip.Inflater;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import com.unisound.sim.im.music.Music;

public class YZSMusicListAdapter extends BaseAdapter{
	private ArrayList<Music> mList;
    private Context mContext;
    private LayoutInflater mInflater;
    private int mSelectItem = -1;

    public YZSMusicListAdapter(Context context, ArrayList<Music> musicList) {
        mContext = context;
        setmList(musicList);
        mInflater = LayoutInflater.from(context);
    }

    private void setmList(ArrayList<Music> mList) {
        if (mList != null) {
            this.mList = mList;
        } else {
            this.mList = new ArrayList<Music>();
        }
    }

    @Override
    public int getCount() {
        return mList.size();
    }

    @Override
    public Object getItem(int arg0) {
        return mList.get(arg0);
    }

    @Override
    public long getItemId(int arg0) {
        return arg0;
    }

    @Override
    public View getView(int arg0, View arg1, ViewGroup arg2) {
    	ViewHolder viewholder = null;
		if (arg1 == null) {
			viewholder = new ViewHolder();
			arg1 = mInflater.inflate(R.layout.simgle_item_layout, null);
			viewholder.musicTextView = (TextView) arg1
					.findViewById(R.id.music_names_item_id);
			arg1.setTag(viewholder);
		} else {
			viewholder = (ViewHolder) arg1.getTag();
		}

		viewholder.musicTextView.setText(mList.get(arg0).getTitle());

		if (arg0 == mSelectItem) {
			arg1.setBackgroundColor(0xffdd960f);
		} else {
			arg1.setBackgroundColor(Color.TRANSPARENT);
		}

		return arg1;
    }

    public void notifyDataChange(ArrayList<Music> list) {
        setmList(list);
        notifyDataSetChanged();
    }
    
    public void setSelectItem(int selectItem){
    	mSelectItem = selectItem;
    }
    
    private static class ViewHolder{
    	public TextView musicTextView;
    }
}
