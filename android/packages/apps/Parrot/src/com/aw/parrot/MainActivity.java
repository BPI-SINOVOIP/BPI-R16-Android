package com.aw.parrot;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.allwinnertech.smartlink.Smartlink;
import com.allwinnertech.smartlink.Smartlink.AirkissListerner;
import com.allwinnertech.smartlink.SmartlinkInfo;
import com.unisound.sim.cmd.base.CmdManager;
import com.unisound.sim.cmd.base.CmdType;
import com.unisound.sim.im.ServiceType;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {

	private final String TAG = "llh>>MainActivity";
	private Button mMusicBt;
	private Button mAlertBt;
	private Button mAirKissBt;
	private final int ENABLE_BUTTON_MSG = 1;
	private final int AIRKISS_CONNECT_SUCCESS_MSG = 3;
	private final int AIRKISS_CONNECT_FAIL_MSG = 4;
	private Smartlink mSmartlink;
	private YzsServiceOkReceiver mYzsServiceOkReceiver;
	private Dialog mInitServiceProgressDialog;
	private SharedPreferences mSharedPreferences;
	private SharedPreferences.Editor mSharedPreferencesEditor;
	private final String FIRST_START = "first_start";
	private Handler mHandler = new Handler(){
		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
			case ENABLE_BUTTON_MSG:
				boolean button_status = (VoiceIMHelper.getInstance().getVoiceIM().getService(ServiceType.MUSIC_SERVICE) != null) ? true:false;
				if(button_status){
					if(mInitServiceProgressDialog!=null){
						mInitServiceProgressDialog.dismiss();
					}
				}
				mMusicBt.setEnabled(button_status);
				mAlertBt.setEnabled(button_status);
				mAirKissBt.setEnabled(button_status);
				if(button_status){
					mMusicBt.setBackgroundResource(R.drawable.music_bt_state_selector);
					mMusicBt.setTextColor(MainActivity.this.getResources().getColor(R.color.black));
					mAlertBt.setBackgroundResource(R.drawable.alert_bt_state_selector);
					mAlertBt.setTextColor(MainActivity.this.getResources().getColor(R.color.black));
					mAirKissBt.setBackgroundResource(R.drawable.airkiss_bt_state_selector);
					mAirKissBt.setTextColor(MainActivity.this.getResources().getColor(R.color.black));
				}
				if(mSharedPreferences.getBoolean(FIRST_START, true)){
					mSharedPreferencesEditor.putBoolean(FIRST_START, false);
					mSharedPreferencesEditor.commit();
					setBluetoothCanBeFound();
				}
				break;
				
			case AIRKISS_CONNECT_SUCCESS_MSG:
				Toast.makeText(MainActivity.this, "联网成功", Toast.LENGTH_LONG).show();
				break;
			case AIRKISS_CONNECT_FAIL_MSG:
				Toast.makeText(MainActivity.this, "联网失败，请重新连接", Toast.LENGTH_LONG).show();
				break;
			default:
				break;
			}
		};
	};
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate");
		setContentView(R.layout.activity_main);
		/*when shutdown the device,the apk will onCreate again,
		 * and this will bring up the tts,so we need to delay 1.2s to avoid the problem*/
		mSharedPreferences = getSharedPreferences("parrot", MODE_PRIVATE);
		mSharedPreferencesEditor = mSharedPreferences.edit();
		mHandler.postDelayed(new Runnable() {
			public void run() {
				registerYzsServiceOkReceiver();
				Intent intent = new Intent(MainActivity.this, MainService.class);
				startService(intent);
				initAllButton();
				initAirkiss();
				boolean service_status = (VoiceIMHelper.getInstance().getVoiceIM().getService(ServiceType.MUSIC_SERVICE) != null) ? true:false;
				if(!service_status){
					showInitServiceProDialog();
				}
				mMusicBt.setEnabled(service_status);
				mAlertBt.setEnabled(service_status);
				mAirKissBt.setEnabled(service_status);
				if(service_status){
					mMusicBt.setBackgroundResource(R.drawable.music_bt_state_selector);
					mMusicBt.setTextColor(MainActivity.this.getResources().getColor(R.color.black));
					mAlertBt.setBackgroundResource(R.drawable.alert_bt_state_selector);
					mAlertBt.setTextColor(MainActivity.this.getResources().getColor(R.color.black));
					mAirKissBt.setBackgroundResource(R.drawable.airkiss_bt_state_selector);
					mAirKissBt.setTextColor(MainActivity.this.getResources().getColor(R.color.black));
				}
			}
		}, 2500);
		Log.d(TAG, "copy begin");
		try {
			copyBigDataToSD("/mnt/sdcard/Music/一生何求.mp3","yishengheqiu.mp3");
			copyBigDataToSD("/mnt/sdcard/Music/给自己的情书.mp3","geizijideqingshu.mp3");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Log.d(TAG, "copy finish");
		
	}
	
	@Override
	protected void onStart() {
		// TODO Auto-generated method stub
		Log.d(TAG, "onStart");
		super.onStart();
	}
	
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		unregisterReceiver(mYzsServiceOkReceiver);
		super.onDestroy();
	}

	private void setBluetoothCanBeFound(){
		Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
		discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
		startActivity(discoverableIntent);
	}
	
	private void copyBigDataToSD(String strOutFileName,String strInFileName) throws IOException{  
        InputStream myInput;  
        OutputStream myOutput = new FileOutputStream(strOutFileName);  
        myInput = this.getAssets().open(strInFileName);  
        byte[] buffer = new byte[1024];  
        int length = myInput.read(buffer);
        while(length > 0)
        {
            myOutput.write(buffer, 0, length); 
            length = myInput.read(buffer);
        }
        myOutput.flush();  
        myInput.close();  
        myOutput.close();        
    }  
	
	private void initAllButton(){
		mMusicBt = (Button)findViewById(R.id.music_bt_id);
		mAlertBt = (Button)findViewById(R.id.alert_bt_id);
		mAirKissBt = (Button)findViewById(R.id.airkiss_bt_id);
		mMusicBt.setOnClickListener(new MyClickListener());
		mAlertBt.setOnClickListener(new MyClickListener());
		mAirKissBt.setOnClickListener(new MyClickListener());
		mAirKissBt.setOnLongClickListener(new MyLongClickListener());
	}
	
	private void initAirkiss(){
		if(mSmartlink == null){
			mSmartlink = new Smartlink(this);
		}
		mSmartlink.setListener(new MyAirkissListerner());
	}
	
	private void startAirkissConnect(){
		mSmartlink.setSmartlinkProtol(Smartlink.ProtocolsAirkiss);
        int ret = mSmartlink.startSmartLink();
        Log.e(TAG,"startAirkissConnect return is "+ret);
	}
	
//	@Override
//	public boolean onCreateOptionsMenu(Menu menu) {
//		// Inflate the menu; this adds items to the action bar if it is present.
//		getMenuInflater().inflate(R.menu.main, menu);
//		Log.d(TAG, "onCreateOptionsMenu");
//		return true;
//	}

//	@Override
//	public boolean onOptionsItemSelected(MenuItem item) {
//		// TODO Auto-generated method stub
//		switch (item.getItemId()) {
//		case R.id.action_start_id:
//			Log.d(TAG, "start service item is selected");
//			Intent start_intent = new Intent(MainActivity.this,MainService.class);
//			startService(start_intent);
//			Toast.makeText(MainActivity.this, "MainService has started", Toast.LENGTH_LONG).show();
//			mHandler.sendEmptyMessageDelayed(ENABLE_BUTTON_MSG, 5000);
//			break;
//		default:
//			break;
//		}
//		return super.onOptionsItemSelected(item);
//	}
	
	@Override
	public void onBackPressed() {
		// TODO Auto-generated method stub
		Log.d(TAG, "onBackPressed,disable the back press");
	}
	
	private void registerYzsServiceOkReceiver(){
		mYzsServiceOkReceiver = new YzsServiceOkReceiver();
		IntentFilter filter = new IntentFilter();
		filter.addAction(MainService.ACTION_YZS_SERVICE_OK);
		registerReceiver(mYzsServiceOkReceiver, filter);
	}
	
	private void showInitServiceProDialog() {
		mInitServiceProgressDialog = new Dialog(this,
				R.style.Theme_Dialog_ListSelect);
		mInitServiceProgressDialog.setContentView(R.layout.init_service_dialog);
		mInitServiceProgressDialog.show();
	}
	
	private boolean isWifiConnected(){
		ConnectivityManager connectManager = (ConnectivityManager)getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo wifiNetworkInfo = connectManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		if(wifiNetworkInfo.isConnected()){
			return true;
		}else{
			return false;
		}
	}
	
	private class MyClickListener implements OnClickListener{

		@Override
		public void onClick(View arg0) {
			// TODO Auto-generated method stub
			Log.d(TAG, "onClick");
			switch (arg0.getId()) {
			case R.id.music_bt_id:
				Log.d(TAG, "music button is onClick");
				Intent music_intent = new Intent(MainActivity.this, MusicActivity.class);
				MainActivity.this.startActivity(music_intent);
				break;
			case R.id.alert_bt_id:
				Log.d(TAG, "alert button is onClick");
				Intent alert_intent = new Intent(MainActivity.this, AlertActivity.class);
				MainActivity.this.startActivity(alert_intent);
				break;
			case R.id.airkiss_bt_id:
				Log.d(TAG, "airkiss button is onClick");
				if(isWifiConnected()){
					Log.d(TAG, "the wifi is connected");
				}else{
					Log.d(TAG, "the wifi is not connected");
					CmdManager.getInstacne().excuteCmd(CmdType.TTS_TEXT_THAN_WAKE_UP, "请长按airkiss按钮联网");
				}
				break;
			default:
				break;
			}
		}
	}
	
	private class MyLongClickListener implements OnLongClickListener{

		@Override
		public boolean onLongClick(View arg0) {
			// TODO Auto-generated method stub
			Log.d(TAG, "airkiss button is onLongClick");
			Toast.makeText(getApplication(), "airkiss bt is long clicked , please connect the wifi", Toast.LENGTH_LONG).show();
			CmdManager.getInstacne().excuteCmd(CmdType.TTS_TEXT_THAN_WAKE_UP, "请在微信上输入wifi名称和密码");
			startAirkissConnect();
			return true;
		}
		
	}
	
	private class MyAirkissListerner implements AirkissListerner{

		@Override
		public void onSmartlinkFinished(SmartlinkInfo info) {
			// TODO Auto-generated method stub
			Log.d(TAG, "onSmartlinkFinished");
		}

		@Override
		public void onLinktoAPFinished() {
			// TODO Auto-generated method stub
			Log.d(TAG, "onLinktoAPFinished:connect successfully");
		}

		@Override
		public void onFailed(int errno) {
			// TODO Auto-generated method stub
			Log.d(TAG, "onFailed : errno:"+errno);
			mHandler.sendEmptyMessage(AIRKISS_CONNECT_FAIL_MSG);
			CmdManager.getInstacne().excuteCmd(CmdType.TTS_TEXT_THAN_WAKE_UP, "联网失败，请重新连接");
		}

		@Override
		public void onSendUDPBroadcastFinished() {
			// TODO Auto-generated method stub
			Log.d(TAG, "onSendUDPBroadcastFinished:configure successfully");
			mHandler.sendEmptyMessage(AIRKISS_CONNECT_SUCCESS_MSG);
			CmdManager.getInstacne().excuteCmd(CmdType.TTS_TEXT_THAN_WAKE_UP, "联网成功");
		}
		
	}
	
	private class YzsServiceOkReceiver extends BroadcastReceiver{

		@Override
		public void onReceive(Context arg0, Intent arg1) {
			// TODO Auto-generated method stub
			Log.d(TAG, "onReceive : "+arg1.getAction());
			mHandler.sendEmptyMessage(ENABLE_BUTTON_MSG);
		}
		
	}
	
}
