package com.aw.parrot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

public class BootReceiver extends BroadcastReceiver{
	private final String TAG = "llh>>BootReceiver";
	@Override
	public void onReceive(Context arg0, Intent arg1) {
		// TODO Auto-generated method stub
		Log.d(TAG, "onReceive()");
//		Intent intent = new Intent(arg0, MainService.class);
//		arg0.startService(intent);
//		Toast.makeText(arg0, "onReceive()>>MainService has started", Toast.LENGTH_LONG).show();
	}
}
