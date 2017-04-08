package com.aw.parrot;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.broadcom.bt.avrcp.BluetoothAvrcpController;
import com.unisound.sim.cmd.base.CmdManager;
import com.unisound.sim.cmd.base.CmdType;
import com.unisound.sim.im.IMListener;
import com.unisound.sim.im.ServiceType;
import com.unisound.sim.im.alert.AlertActivatedListener;
import com.unisound.sim.im.alert.AlertData;
import com.unisound.sim.im.alert.AlertListener;
import com.unisound.sim.im.alert.IAlertService;
import com.unisound.sim.im.music.IMusicService;
import com.unisound.sim.im.music.Music;
import com.unisound.sim.im.music.MusicListener;
import com.unisound.sim.im.music.MusicPlayMode;
import com.unisound.sim.im.nlu.ASRData;
import com.unisound.sim.im.nlu.ICustomNLU;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.Service;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;

public class MainService extends Service{

	private final String TAG = "llh>>MainService";
	public static final String ACTION_MUSIC_LIST_CHANGE = "action.music.list.change";
	public static final String ACTION_MUSIC_INFO_CHANGE = "action.music.info.change";
	public static final String ACTION_PLAY_BUTTON_CHANGE = "action.play.button.change";
	public static final String ACTION_ALERT_LIST_CHANGE = "action.alert.list.change";
	public static final String ACTION_YZS_SERVICE_OK = "action.yzs.service.ok";
	
	public static IMusicService mMusicService;
	public static IAlertService mAlertService;
	
	public static StatusCtrlManager mStatusCtrlManager = null;
	private AudioManager mAudioManager;
	
	private MyExternalStorageReceiver mExternalStorageReceiver;
	private final int UPDATE_LOCAL_MUSIC_LIST_MSG = 0;
	private CatalogList mCatalogList;
	private String mExtStoragePath;
	private List<Music> mExtSdcardMusicList;
	private List<Music> mExtUpanMusicList;
	private static List<Music> mLocalMusicList = new ArrayList<Music>();
	private static Object mLocalMusicListLock = new Object();
	
	private Handler mHandler = new Handler(){
		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
			case UPDATE_LOCAL_MUSIC_LIST_MSG://for handle the local music list
				Log.d(TAG, "update local music list,mExtStoragePath = "+mExtStoragePath);
				if(mMusicService.getCurrMusicList()==null){//the local and cloud music are not playing
					if((msg.obj != null) && (msg.obj instanceof String)){
						String objStr = (String)msg.obj;
						if(objStr.equalsIgnoreCase(LocalStorageStatus.EXTERNAL_STORAGE_IN)){
							YZSMusicCtrl.playMusicList(getLocalMusicList(), 0, MusicPlayMode.PLAY_LIST);
							appendExtMusicListToLocal(mExtStoragePath);
							if(isForeground(getApplication(), "com.aw.parrot.MusicActivity")){
								Intent listIntent = new Intent();
								listIntent.setAction(ACTION_MUSIC_LIST_CHANGE);
								sendBroadcast(listIntent);
							}
						}else if(objStr.equalsIgnoreCase(LocalStorageStatus.EXTERNAL_SDCARD_OUT)){
							//delete the music in local music list which has the absolute path:/mnt/extsd 
							rmPulledOutExtStorageMusic("/mnt/extsd");
							setLocalMusicList();
							YZSMusicCtrl.playMusicList(getLocalMusicList(), 0, MusicPlayMode.PLAY_LIST);
						}else if(objStr.equalsIgnoreCase(LocalStorageStatus.UPAN_OUT)){
							//delete the music in local music list which has the absolute path:/mnt/usbhost1 
							rmPulledOutExtStorageMusic("/mnt/usbhost");
							setLocalMusicList();
							YZSMusicCtrl.playMusicList(getLocalMusicList(), 0, MusicPlayMode.PLAY_LIST);
						}
					}
				}else if(mMusicService.getCurrMusicList()!=null && mMusicService.getCurrMusicInfo().isLocal()){//the local music is playing
					if((msg.obj != null) && (msg.obj instanceof String)){
						String objStr = (String)msg.obj;
						if(objStr.equalsIgnoreCase(LocalStorageStatus.EXTERNAL_STORAGE_IN)){
							appendExtMusicListToLocal(mExtStoragePath);
							if(isForeground(getApplication(), "com.aw.parrot.MusicActivity")){
								Intent listIntent = new Intent();
								listIntent.setAction(ACTION_MUSIC_LIST_CHANGE);
								sendBroadcast(listIntent);
							}
						}else if(objStr.equalsIgnoreCase(LocalStorageStatus.EXTERNAL_SDCARD_OUT)){
							//delete the music in local music list which has the absolute path:/mnt/extsd 
							rmPulledOutExtStorageMusic("/mnt/extsd");
							setLocalMusicList();
							YZSMusicCtrl.playMusicList(getLocalMusicList(), 0, MusicPlayMode.PLAY_LIST);
						}else if(objStr.equalsIgnoreCase(LocalStorageStatus.UPAN_OUT)){
							//delete the music in local music list which has the absolute path:/mnt/usbhost1 
							rmPulledOutExtStorageMusic("/mnt/usbhost");
							setLocalMusicList();
							YZSMusicCtrl.playMusicList(getLocalMusicList(), 0, MusicPlayMode.PLAY_LIST);
						}
					}
				}else if(mMusicService.getCurrMusicList()!=null && (!mMusicService.getCurrMusicInfo().isLocal())){//the local music is not playing
					if((msg.obj != null) && (msg.obj instanceof String)){
						String objStr = (String)msg.obj;
						if(objStr.equalsIgnoreCase(LocalStorageStatus.EXTERNAL_STORAGE_IN)){
							appendExtMusicListToLocal(mExtStoragePath);
							if(isForeground(getApplication(), "com.aw.parrot.MusicActivity")){
								Intent listIntent = new Intent();
								listIntent.setAction(ACTION_MUSIC_LIST_CHANGE);
								sendBroadcast(listIntent);
							}
						}else if(objStr.equalsIgnoreCase(LocalStorageStatus.EXTERNAL_SDCARD_OUT)){
							//delete the music in local music list which has the absolute path:/mnt/extsd 
							rmPulledOutExtStorageMusic("/mnt/extsd");
							setLocalMusicList();
						}else if(objStr.equalsIgnoreCase(LocalStorageStatus.UPAN_OUT)){
							//delete the music in local music list which has the absolute path:/mnt/usbhost1 
							rmPulledOutExtStorageMusic("/mnt/usbhost");
							setLocalMusicList();
						}
					}
				}
				break;

			default:
				break;
			}
		};
	};
	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		Log.d(TAG, "onBind");
		return null;
	}

	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		Log.d(TAG, "onCreate");
		initVoiceIm();
		mStatusCtrlManager = StatusCtrlManager.getInstance(getApplication());
		if(!StatusCtrlManager.hasInitStatusFlag){
			mStatusCtrlManager.initStatus();
		}
		mAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		Log.d(TAG, "getApplication() = "+getApplication().toString());
		super.onCreate();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// TODO Auto-generated method stub
		Log.d(TAG, "onStartCommand");
		return super.onStartCommand(intent, flags, startId);
	}
	
	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		Log.d(TAG, "onDestroy");
		VoiceIMHelper.getInstance().onDestory();
		if(mExternalStorageReceiver!=null){
			unregisterReceiver(mExternalStorageReceiver);
			mExternalStorageReceiver = null;
		}
		super.onDestroy();
	}
	
	private void initVoiceIm(){
		VoiceIMHelper.getInstance().setIMListener(new MyIMListener());
		VoiceIMHelper.getInstance().setCustomNLU(new MyCustomNLU());
		VoiceIMHelper.getInstance().start();
	}
	private void initMusicService(){
		mMusicService = (IMusicService) VoiceIMHelper.getInstance().getVoiceIM().getService(ServiceType.MUSIC_SERVICE);
		if(mMusicService == null){
			Log.e(TAG, "the music service is null");
		}else{
			mMusicService.registMusicListener(new MyMusicListener());
		}
	}
	
	private void initAlertService(){
		mAlertService = (IAlertService)VoiceIMHelper.getInstance().getVoiceIM().getService(ServiceType.ALERT_SERVICE);
		if(mAlertService == null){
			Log.e(TAG, "the alert service is null");
		}else{
			mAlertService.setAlertListener(new MyAlertListener());
			mAlertService.setAlertActivatedListener(new MyAlertActivatedListener());
		}
	}
	
	private void enableMainActivityButton(){
		Intent intent = new Intent();
		intent.setAction(ACTION_YZS_SERVICE_OK);
		sendBroadcast(intent);
	}
	
	private void startMainActivity(){
		if(!isForeground(getApplication(), "com.aw.parrot.MainActivity")){
			Intent startIntent = new Intent(MainService.this, MainActivity.class);
			startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(startIntent);
		}
	}
	
	private boolean isForeground(Context context,String className){
		if(context == null || TextUtils.isEmpty(className)){
			return false;
		}
		ActivityManager am = (ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
		List<RunningTaskInfo> list = am.getRunningTasks(1);
		if(list != null && list.size()>0){
			ComponentName cpn = list.get(0).topActivity;
			if(className.equals(cpn.getClassName())){
				Log.d(TAG, "class name:"+cpn.getClassName());
				return true;
			}
		}
		return false;
	}
	
	private void setLocalMusicList(){
		synchronized (mLocalMusicListLock) {
			mLocalMusicList = mMusicService.getLocalMusicList();
			mLocalMusicListLock.notifyAll();
		}
	}
	
	public static List<Music> getLocalMusicList(){
		synchronized (mLocalMusicListLock) {
			mLocalMusicListLock.notifyAll();
		}
		return mLocalMusicList;
	}
	
	private void appendExtMusicListToLocal(String path){
		synchronized (mLocalMusicListLock) {
			mCatalogList.setFileType(CatalogList.AUDIO_TYPE);
			List<Music> extList = mCatalogList.getExtStorageMusicList(path);
			CmdManager.getInstacne().excuteCmd(CmdType.MUSIC_APPEND_PLAY_LIST, extList);
			mLocalMusicList.addAll(extList);
			mLocalMusicListLock.notifyAll();
		}
	}
	
	private void rmPulledOutExtStorageMusic(String rmPath){
		synchronized (mLocalMusicListLock) {
			for(int i=0;i < mLocalMusicList.size();i++){
				if(mLocalMusicList.get(i).getUrl().startsWith(rmPath)){
					mLocalMusicList.remove(i);
				}
			}
			mLocalMusicListLock.notifyAll();
		}
	}
	
	private void sendMessage(int what,Object obj){
		Message message = mHandler.obtainMessage(what, obj);
        mHandler.sendMessage(message);
	}
	
	private void sendMessageDelay(int what,Object obj,int delayMs){
		Message message = mHandler.obtainMessage(what, obj);
		mHandler.sendMessageDelayed(message, delayMs);
	}
	
	private void registermExternalStorageReceiver(){
		mExternalStorageReceiver = new MyExternalStorageReceiver();
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.setPriority(1000);
        intentFilter.addAction(Intent.ACTION_MEDIA_EJECT);
        intentFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
        intentFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
        intentFilter.addDataScheme("file");
        registerReceiver(mExternalStorageReceiver, intentFilter);
	}
	
	private class MyIMListener implements IMListener{

		private final String TAG = "llh>>MyIMListener";
		
		@Override
		public void onASREnd(boolean arg0) {
			// TODO Auto-generated method stub
			Log.d(TAG, "onASREnd , arg0 = "+arg0+",recognize end , set the music not mute");
			int sys_cur_volume = mAudioManager.getStreamVolume(AudioManager.STREAM_SYSTEM);
			int music_cur_volume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
			int sys_max_volume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_SYSTEM);
			int music_max_volume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
			Log.d(TAG, "sys_cur_volume = "+sys_cur_volume+",music_cur_volume = "+music_cur_volume
					+",sys_max_volume = "+sys_max_volume+",music_max_volume = "+music_max_volume);
			mAudioManager.setStreamMute(AudioManager.STREAM_MUSIC, false);
		}

		@Override
		public void onASRRecognizing() {
			// TODO Auto-generated method stub
			Log.d(TAG, "onASRRecognizing");
		}

		@Override
		public void onASRRecordingStop() {
			// TODO Auto-generated method stub
			Log.d(TAG, "onASRRecordingStop");
		}

		@Override
		public void onASRStart() {
			// TODO Auto-generated method stub
			Log.d(TAG, "onASRStart,that means it is wake up,we should make the music mute");
			int sys_cur_volume = mAudioManager.getStreamVolume(AudioManager.STREAM_SYSTEM);
			int music_cur_volume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
			int sys_max_volume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_SYSTEM);
			int music_max_volume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
			Log.d(TAG, "sys_cur_volume = "+sys_cur_volume+",music_cur_volume = "+music_cur_volume
					+",sys_max_volume = "+sys_max_volume+",music_max_volume = "+music_max_volume);
			mAudioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
		}

		@Override
		public void onASRUpdateVolume(int arg0) {
			// TODO Auto-generated method stub
//			Log.d(TAG, "onASRUpdateVolume , arg0 = "+arg0);
		}

		@Override
		public void onIMInitEnd() {
			// TODO Auto-generated method stub
			Log.d(TAG, "onIMInitEnd");
			initMusicService();
			initAlertService();
			enableMainActivityButton();
			registermExternalStorageReceiver();
			mCatalogList = new CatalogList(MainApplication.getInstance());
			setLocalMusicList();
//			startMainActivity();
		}

		@Override
		public void onTTSPlayEnd() {
			// TODO Auto-generated method stub
			Log.d(TAG, "onTTSPlayEnd ");
		}

		@Override
		public void onTTSPlayStart() {
			// TODO Auto-generated method stub
			Log.d(TAG, "onTTSPlayStart");
		}

		@Override
		public void onWakeup(boolean arg0) {
			// TODO Auto-generated method stub
			Log.d(TAG, "onWakeup , arg0 = "+arg0);
		}
		
	}
	
	private class MyCustomNLU implements ICustomNLU{

		private final String TAG = "llh>>MyCustomNLU";
		
		@Override
		public boolean onNLU(ASRData arg0) {
			// TODO Auto-generated method stub
			String result = arg0.getASRText();
			Log.d(TAG, "recoginzed result = "+result);
			if(result.equals("暂停。") || result.equals("暂停播放。") || result.equals("暂停") || result.equals("暂停播放")){
				if(mStatusCtrlManager.getCurStatus() == CtrStatusType.YZS_MUSIC_PAUSE){
					Log.d(TAG, "is the same status,do nothing");
				}else{
					Log.d(TAG, "is the different status,change the status");
					mStatusCtrlManager.changeStatusAndCtrPlay(CtrStatusType.YZS_MUSIC_PAUSE);
				}
				Intent intent = new Intent();
				intent.setAction(ACTION_PLAY_BUTTON_CHANGE);
				sendBroadcast(intent);
			}else if(result.equals("停止。") || result.equals("停止播放。") || result.equals("停止") || result.equals("停止播放")){
				if(mStatusCtrlManager.getCurStatus() == CtrStatusType.YZS_MUSIC_STOP){
					Log.d(TAG, "is the same status,do nothing");
				}else{
					Log.d(TAG, "is the different status,change the status");
					mStatusCtrlManager.changeStatusAndCtrPlay(CtrStatusType.YZS_MUSIC_STOP);
				}
				Intent intent = new Intent();
				intent.setAction(ACTION_PLAY_BUTTON_CHANGE);
				sendBroadcast(intent);
			}else if(result.equals("播放。") || result.equals("播放音乐。") ){//if the wifi is connected,return the words with "。"
				if(mMusicService.getCurrMusicList()==null){
					Log.d(TAG, "is the first time to use voice control the music play");
					if(getLocalMusicList().size()>0){
						YZSMusicCtrl.playMusicList(getLocalMusicList(), 0, MusicPlayMode.PLAY_LIST);
						CmdManager.getInstacne().excuteCmd(CmdType.ENTER_WAKE_UP);
						return true;
					}
				}
			}else if(result.equals("播放") || result.equals("播放音乐")){//if the wifi is not connected,return the words with no "。"
				if(mStatusCtrlManager.getCurStatus() == CtrStatusType.YZS_MUSIC_PAUSE){
					YZSMusicCtrl.play();
					CmdManager.getInstacne().excuteCmd(CmdType.ENTER_WAKE_UP);
					return true;
				}
			}
			return false;
		}
		
	}
		
	
	private class MyMusicListener implements MusicListener{

		@Override
		public void onMusicEnd(Music arg0) {
			// TODO Auto-generated method stub
			String localMusic = arg0.isLocal()?"is local music":"is not local music";
			Log.d(TAG, "onMusicEnd: title:"+arg0.getTitle()+",artist:"+arg0.getArtist()
					+",album:"+arg0.getAlbum()+"total time:"+arg0.getDuration()+localMusic);
		}

		@Override
		public void onMusicListChanged(List<Music> arg0) {
			// TODO Auto-generated method stub
			Log.d(TAG, "onMusicListChanged: list.size = "+arg0.size());
			for(Iterator<Music> it = arg0.iterator();it.hasNext();){
				Music music = it.next();
				Log.d(TAG, "music title:"+music.getTitle()+",artist:"+music.getArtist()
						+",album:"+music.getAlbum()+"total time:"+music.getDuration()+",url:"+music.getUrl());
			}
			Intent listIntent = new Intent();
			listIntent.setAction(ACTION_MUSIC_LIST_CHANGE);
			if(!arg0.get(0).isLocal()){
				listIntent.putExtra("all_music_list_changed", true);
			}
			sendBroadcast(listIntent);
		}

		@Override
		public void onMusicStart(Music arg0, int arg1) {
			// TODO Auto-generated method stub
			Log.d(TAG, "onMusicStart");
			if(mStatusCtrlManager.getCurStatus() == CtrStatusType.YZS_MUSIC_PLAYING){
				Log.d(TAG, "is the same status,do nothing");
			}else{
				Log.d(TAG, "is the different status,change the status");
				mStatusCtrlManager.changeStatusAndCtrPlay(CtrStatusType.YZS_MUSIC_PLAYING);
			}
			Intent musicIntent = new Intent();
			musicIntent.setAction(ACTION_MUSIC_INFO_CHANGE);
			sendBroadcast(musicIntent);
			String localMusic = arg0.isLocal()?",is local music":",is not local music";
			Log.d(TAG, "onMusicStart: index is :"+arg1+",title:"+arg0.getTitle()+",artist:"+arg0.getArtist()
					+",album:"+arg0.getAlbum()+",total time:"+arg0.getDuration()+localMusic);
		}

		@Override
		public void onMusicPrepare(Music arg0, int arg1) {
			// TODO Auto-generated method stub
			Log.d(TAG, "onMusicPrepare: arg1 = "+arg1);
			Intent musicIntent = new Intent();
			musicIntent.setAction(ACTION_MUSIC_INFO_CHANGE);
			sendBroadcast(musicIntent);
			String localMusic = arg0.isLocal()?",is local music":",is not local music";
			Log.d(TAG, "onMusicPrepare: index is :"+arg1+",title:"+arg0.getTitle()+",artist:"+arg0.getArtist()
					+",album:"+arg0.getAlbum()+",total time:"+arg0.getDuration()+localMusic);
		}
		
	}
	
	private class MyAlertListener implements AlertListener{

		@Override
		public void onDataChanged() {
			// TODO Auto-generated method stub
			Log.d(TAG, "alertdata change");
			Intent alertListIntent = new Intent();
			alertListIntent.setAction(ACTION_ALERT_LIST_CHANGE);
			sendBroadcast(alertListIntent);
		}
		
	}
	
	private class MyAlertActivatedListener implements AlertActivatedListener{

		@Override
		public List<AlertData> onAlertActivated(List<AlertData> arg0) {
			// TODO Auto-generated method stub
			Log.d(TAG, "the alert is begin to alert");
			if(mStatusCtrlManager.getCurStatus() == CtrStatusType.YZS_MUSIC_PLAYING){
				Log.d(TAG, "the yzs music is playing,set the status to paused");
				mStatusCtrlManager.changeStatusAndCtrPlay(CtrStatusType.YZS_MUSIC_PAUSE);
			}
			Intent intent = new Intent();
			intent.setAction(ACTION_PLAY_BUTTON_CHANGE);
			sendBroadcast(intent);
			return arg0;
		}
		
	}
	
	private class MyExternalStorageReceiver extends BroadcastReceiver{

		@Override
		public void onReceive(Context arg0, Intent arg1) {
			// TODO Auto-generated method stub
			String action_str = arg1.getAction();
			Log.d(TAG, "MyExternalStorageReceiver:intent action = "+action_str);
			if(action_str.equals(Intent.ACTION_MEDIA_MOUNTED)){
				mExtStoragePath = arg1.getDataString().substring(7);
				Log.d(TAG, "data string = "+mExtStoragePath);
				sendMessage(UPDATE_LOCAL_MUSIC_LIST_MSG, LocalStorageStatus.EXTERNAL_STORAGE_IN);
			}else if(action_str.equals(Intent.ACTION_MEDIA_EJECT)){
				mExtStoragePath = arg1.getDataString().substring(7);
				Log.d(TAG, "data string = "+mExtStoragePath);
				if(mExtStoragePath.contains("usbhost")){
					sendMessageDelay(UPDATE_LOCAL_MUSIC_LIST_MSG, LocalStorageStatus.UPAN_OUT, 1000);
				}else if(mExtStoragePath.contains("extsd")){
					sendMessageDelay(UPDATE_LOCAL_MUSIC_LIST_MSG, LocalStorageStatus.EXTERNAL_SDCARD_OUT,1000);
				}
			}
		}
		
	}
	
}
