package com.aw.parrot;

import com.broadcom.bt.avrcp.BluetoothAvrcpController;

import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
public class StatusReceiver extends BroadcastReceiver{

	private final String TAG = "llh>>StatusReceiver";
	
	@Override
	public void onReceive(Context arg0, Intent arg1) {
		// TODO Auto-generated method stub
		String action = arg1.getAction();
		Log.d(TAG, "onReceive action : "+action+",Context is :"+arg0.toString());
		if(MainService.mStatusCtrlManager != null){
			if(action.equals(BluetoothAvrcpController.ACTION_CONNECTION_STATE_CHANGED)){
				int state = arg1.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
				Log.d(TAG, "state = "+state);
				switch (state) {
				case BluetoothProfile.STATE_CONNECTED:
					Log.d(TAG, "BluetoothProfile.STATE_CONNECTED");
					MainService.mStatusCtrlManager.changeStatusAndCtrPlay(CtrStatusType.BLUETOOTH_CONNECTED);
					break;
					
				case BluetoothProfile.STATE_DISCONNECTED:
					Log.d(TAG, "BluetoothProfile.STATE_DISCONNECTED");
					MainService.mStatusCtrlManager.changeStatusAndCtrPlay(CtrStatusType.BLUETOOTH_DISCONNECTED);
					break;
				default:
					break;
				}
			}else if(action.equals("action.dlna.play")){
				if(MainService.mStatusCtrlManager.getCurStatus() == CtrStatusType.DLNA_PLAY){
					Log.d(TAG, "is the same status,do nothing");
				}else{
					Log.d(TAG, "is the different status,change the status");
					MainService.mStatusCtrlManager.changeStatusAndCtrPlay(CtrStatusType.DLNA_PLAY);
				}
			}else if(action.equals("action.dlna.stop")){
				if(MainService.mStatusCtrlManager.getCurStatus() == CtrStatusType.DLNA_STOP){
					Log.d(TAG, "is the same status,do nothing");
				}else{
					Log.d(TAG, "is the different status,change the status");
					MainService.mStatusCtrlManager.changeStatusAndCtrPlay(CtrStatusType.DLNA_STOP);
				}
			}else{
				if(MainService.mStatusCtrlManager.getCurStatus() == CtrStatusType.DLNA_PAUSE){
					Log.d(TAG, "is the same status,do nothing");
				}else{
					Log.d(TAG, "is the different status,change the status");
					MainService.mStatusCtrlManager.changeStatusAndCtrPlay(CtrStatusType.DLNA_PAUSE);
				}
			}
		}
	}

}
