package com.aw.parrot;

import android.bluetooth.BluetoothAdapter;
import android.util.Log;

public class BluetoothCtrl {
	private final String TAG = "llh>>DlnaCtrl";
	private static BluetoothCtrl mInstance;
	private static BluetoothAdapter mBluetoothAdapter;
	private BluetoothCtrl(){
		Log.d(TAG, "BluetoothCtrl structure");
		if(mBluetoothAdapter == null){
			mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		}
	}
	public static BluetoothCtrl getInstance(){
		if(mInstance == null){
			mInstance = new BluetoothCtrl();
		}
		return mInstance;
	}
	public void closeBluetooth(){
		Log.d(TAG, "closeBluetooth >> enable :"+mBluetoothAdapter.isEnabled());
		if(mBluetoothAdapter!=null){
			mBluetoothAdapter.disable();
		}
	}
	public void openBluetooth(){
		Log.d(TAG, "openBluetooth >> enable :"+mBluetoothAdapter.isEnabled());
		if(mBluetoothAdapter!=null){
			mBluetoothAdapter.enable();
		}
	}
}
