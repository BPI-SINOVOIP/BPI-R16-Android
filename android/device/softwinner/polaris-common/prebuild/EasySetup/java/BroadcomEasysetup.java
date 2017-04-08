package com.softwinner.broadcom.easysetup;

import android.util.Log;

public class BroadcomEasysetup {

	private static final String TAG = "BoardcomEasysetup";
	
	public static int ProtocolsCooee = 0;
	public static int ProtocolsNeeze = 1;
	public static int ProtocolsAirkiss = 2;
	public static int ProtocolsQQcon = 3;
	
	public static int SecurityNone = 0;
	public static int SecurityWep = 1;
	public static int SecurityWpa = 2;
	public static int SecurityWpa2 = 3;

	public static String[] Security ={"none","WEP","WPA1","WPA2"};
	public static String[] Protocol = {"cooee","neeze","airkiss","qqcon"};
	
	private boolean mIsStarted = false;
	private EasysetupListener mListener = null;
	//private AirkissInfo mAirkissInfo;
	private int mProtocol = -1;
	public interface EasysetupListener{
		public void onEasysetupFinished(EasysetupInfo info);
		public void onEasysetupStart();
		public void onLinktoAPStart();
		public void onLinktoAPFinished();
		public void onSendUDPBroadcastStart();
		public void onSendUDPBroadcastFinished();

	}

	public void setListener(EasysetupListener listener){
		mListener = listener;		
	}
	
	public void setEasysetupProtol(int protocol){
		/*
		if(protocol != ProtocolsAirkiss){
			Log.e(TAG,"Only support Airkiss now!");
			return ;
		}*/
		mProtocol = 1<<protocol;
	}
	
	public int startEasySetup(){
		if(mProtocol == -1){
			Log.e(TAG,"Please set easy setup protocol first");
			return -1;
		}
		//System.loadLibrary("easysetup_jni");
		if(!mIsStarted){
			new EasysetThread().start();
			mIsStarted = true;
		}
		return 1;
	}
	
    static {
        System.loadLibrary("easysetup_jni");
    }
	public void onEasysetupStart()
	{
		new Thread(new Runnable() {
			@Override
			public void run() {
				if(mListener != null){
					mListener.onEasysetupStart();
				}
			}
		}).start();
	}
	public void onLinktoAPStart()
	{
		new Thread(new Runnable() {
			@Override
			public void run() {
				if(mListener != null){
					mListener.onLinktoAPStart();
				}
			}
		}).start();
	}
	public void onLinktoAPFinished()
	{
		new Thread(new Runnable() {
			@Override
			public void run() {
				if(mListener != null){
					mListener.onLinktoAPFinished();
				}
			}
		}).start();
	}
	public void onSendUDPBroadcastStart()
	{
		new Thread(new Runnable() {
			@Override
			public void run() {
				if(mListener != null){
					mListener.onSendUDPBroadcastStart();
				}
			}
		}).start();
	}
	public void onSendUDPBroadcastFinished()
	{
		new Thread(new Runnable() {
			@Override
			public void run() {
				if(mListener != null){
					mListener.onSendUDPBroadcastFinished();
				}
			}
		}).start();
	}

	public native EasysetupInfo startEasySetup(int protocol);

	private class EasysetThread  extends Thread {
		
		public void run(){
			Log.d(TAG,"start EasysetThread... ");
			EasysetupInfo info = startEasySetup(mProtocol);
			mIsStarted = false;
			//info.protocol = info.protocol;//Broadcom set
			Log.e(TAG,"ssid: "+info.ssid);
			Log.e(TAG,"password: "+info.password);
			Log.e(TAG,"random: "+info.random);
			Log.e(TAG,"protocol: "+info.protocol);
			Log.e(TAG,"senderip: "+info.senderip);
			Log.e(TAG,"senderport: "+info.senderport);
			Log.e(TAG,"wlansecurity: "+info.wlansecurity);
			if(mListener != null){
					mListener.onEasysetupFinished(info);
					
			}
		}
	}

}
