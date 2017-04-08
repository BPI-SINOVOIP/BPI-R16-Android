package com.aw.parrot;

import android.app.Application;
import android.util.Log;

public class MainApplication extends Application{
	private final String TAG = "llh>>MainApplication";
	private static MainApplication sInstance;
	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		Log.d(TAG, "onCreate");
		sInstance = this;
		super.onCreate();
	}
	
	public static synchronized MainApplication getInstance() {
        return sInstance;
    }
}
