package com.aw.parrot;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class DlnaCtrl {
	private final String TAG = "llh>>DlnaCtrl";
	private static Context mContext;
	private static DlnaCtrl mInstance;
	private DlnaCtrl(Context context){
		Log.d(TAG, "DlnaCtrl structure");
		mContext = context;
	}
	public static DlnaCtrl getInstance(Context context){
		if(mInstance == null){
			mInstance = new DlnaCtrl(context);
		}
		return mInstance;
	}
	public void closeDlnaService(){
		Log.d(TAG, "closeDlnaService");
		Intent stopIntent = new Intent();
		stopIntent.setAction("com.softwinner.fireair.receiver.MediaControlService");
		mContext.stopService(stopIntent);
	}
	public void openDlnaService(){
		Log.d(TAG, "openDlnaService");
		Intent startIntent = new Intent();
		startIntent.setAction("com.softwinner.fireair.receiver.MediaControlService");
		mContext.startService(startIntent);
	}
}
