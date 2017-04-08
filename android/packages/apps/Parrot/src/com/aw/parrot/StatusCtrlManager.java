package com.aw.parrot;

import java.util.Timer;
import java.util.TimerTask;

import com.unisound.sim.im.music.IMusicService;
import com.unisound.sim.im.music.MusicPlayMode;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class StatusCtrlManager {
	private final String TAG = "llh>>StatusCtrlManager";
	private CtrStatusType mCurrentStatus;
	private CtrStatusType mPreviousStatus;
	private static Context mContext;
	private static StatusCtrlManager mInstance;
	private static BluetoothCtrl mBluetoothCtrl;
	private static DlnaCtrl mDlnaCtrl;
	public static boolean hasInitStatusFlag;
	
	private StatusCtrlManager(Context context){
		mContext = context;
		hasInitStatusFlag = false;
		mBluetoothCtrl = BluetoothCtrl.getInstance();
		mBluetoothCtrl.openBluetooth();
		mDlnaCtrl = DlnaCtrl.getInstance(context);
	}
	
	public static synchronized StatusCtrlManager getInstance(Context context){
		if(mInstance == null){
			mInstance = new StatusCtrlManager(context);
		}
		return mInstance;
	}
	
	public void initStatus(){
		mCurrentStatus = CtrStatusType.STATUS_INIT;
		mPreviousStatus = CtrStatusType.STATUS_INIT;
		hasInitStatusFlag = true;
	}
	public CtrStatusType getCurStatus(){
		return mCurrentStatus;
	}
	public CtrStatusType getPreviousStatus(){
		return mPreviousStatus;
	}
	public void changeStatusAndCtrPlay(CtrStatusType curStatus){
		mPreviousStatus = mCurrentStatus;
		mCurrentStatus = curStatus;
		Log.d(TAG, "changeStatusAndCtrPlay:mPreviousStatus = "+mPreviousStatus+",mCurrentStatus = "+mCurrentStatus);
		if(mPreviousStatus!=mCurrentStatus){
			switch (mPreviousStatus) {
			case STATUS_INIT:
				if(mCurrentStatus == CtrStatusType.YZS_MUSIC_PLAYING){
					Log.d(TAG, "STATUS_INIT -> YZS_MUSIC_PLAYING:has play,do nothing");
				}else if(mCurrentStatus == CtrStatusType.DLNA_PLAY){
					Log.d(TAG, "STATUS_INIT -> DLNA_PLAY:has play,do nothing");
				}else if(mCurrentStatus == CtrStatusType.BLUETOOTH_CONNECTED){
					Log.d(TAG, "STATUS_INIT -> BLUETOOTH_CONNECTED:close the dlna service");
					mDlnaCtrl.closeDlnaService();
				}
				break;
				
			case YZS_MUSIC_PLAYING:
				if(mCurrentStatus == CtrStatusType.YZS_MUSIC_PAUSE || mCurrentStatus == CtrStatusType.DLNA_PLAY){
					YZSMusicCtrl.pause();
				}else if(mCurrentStatus == CtrStatusType.BLUETOOTH_CONNECTED){
					YZSMusicCtrl.pause();
					mDlnaCtrl.closeDlnaService();
				}else if(mCurrentStatus == CtrStatusType.YZS_MUSIC_STOP){
					YZSMusicCtrl.stop();
				}else if(mCurrentStatus == CtrStatusType.DLNA_STOP){
					mCurrentStatus = CtrStatusType.YZS_MUSIC_PLAYING;
				}else if(mCurrentStatus == CtrStatusType.BLUETOOTH_DISCONNECTED ){//the first change is from BLUETOOTH_CONNECTED to YZS_MUSIC_PLAYING,because when
																					// play yzs music will close bluetooth,and this will cause BLUETOOTH_DISCONNECTED
					mCurrentStatus = CtrStatusType.YZS_MUSIC_PLAYING;
					mDlnaCtrl.openDlnaService();
				}else{
					Log.e(TAG, "It is the err status:mCurrentStatus = "+mCurrentStatus);
				}
				Intent intent = new Intent();
				intent.setAction(MainService.ACTION_PLAY_BUTTON_CHANGE);
				mContext.sendBroadcast(intent);
				break;
				
			case YZS_MUSIC_PAUSE:
				if(mCurrentStatus == CtrStatusType.YZS_MUSIC_PLAYING){
					Log.d(TAG, "YZS_MUSIC_PAUSE -> YZS_MUSIC_PLAYING:has play,do nothing");
				}else if(mCurrentStatus == CtrStatusType.YZS_MUSIC_STOP){
					YZSMusicCtrl.stop();
				}else if(mCurrentStatus == CtrStatusType.DLNA_PLAY ){
					Log.d(TAG, "YZS_MUSIC_PAUSE -> DLNA_PLAY  :has play,do nothing");
				}else if(mCurrentStatus == CtrStatusType.BLUETOOTH_CONNECTED){
					Log.d(TAG, "YZS_MUSIC_PAUSE -> BLUETOOTH_CONNECTED :close the dlna service");
					mDlnaCtrl.closeDlnaService();
				}else if(mCurrentStatus == CtrStatusType.DLNA_STOP){
					mCurrentStatus = CtrStatusType.YZS_MUSIC_PAUSE;
				}else {
					Log.e(TAG, "It is the err status:mCurrentStatus = "+mCurrentStatus);
				}
				break;
				
			case YZS_MUSIC_STOP:
				if(mCurrentStatus == CtrStatusType.YZS_MUSIC_PLAYING){
					if(MainService.mMusicService.getCurrMusicList() != null){
	            		YZSMusicCtrl.playMusicList(MainService.mMusicService.getCurrMusicList(), (int)(MainService.mMusicService.getCurrMusicInfo().getItemId()), MusicPlayMode.PLAY_LIST);
	            	}else{
	            		YZSMusicCtrl.playMusicList(MainService.mMusicService.getLocalMusicList(), 0, MusicPlayMode.PLAY_LIST);
	            	}
				}else if(mCurrentStatus == CtrStatusType.DLNA_PLAY || mCurrentStatus == CtrStatusType.DLNA_STOP){
					Log.d(TAG, "YZS_MUSIC_STOP -> DLNA_PLAY ||  DLNA_STOP:has play or stop ,do nothing");
				}else if(mCurrentStatus == CtrStatusType.BLUETOOTH_CONNECTED){
					Log.d(TAG, "YZS_MUSIC_STOP -> BLUETOOTH_CONNECTED :close the dlna service");
					mDlnaCtrl.closeDlnaService();
				}else if(mCurrentStatus == CtrStatusType.BLUETOOTH_DISCONNECTED){
					mDlnaCtrl.openDlnaService();
				}else{
					Log.e(TAG, "It is the err status:mCurrentStatus = "+mCurrentStatus);
				}
				break;
				
			case DLNA_PLAY:
				if(mCurrentStatus == CtrStatusType.DLNA_STOP || mCurrentStatus == CtrStatusType.DLNA_PAUSE){
					if (MainService.mMusicService.getPlaybackStatus() == IMusicService.STATE_PAUSED) {
		                YZSMusicCtrl.play();
		            }
				}else if(mCurrentStatus == CtrStatusType.YZS_MUSIC_PLAYING ){
					Log.d(TAG, "DLNA_PLAY -> YZS_MUSIC_PLAYING : close dlna service ,then open dlna service, final play yzs music");
					mDlnaCtrl.closeDlnaService();
					Timer dlnaTimer1 = new Timer();
					dlnaTimer1.schedule(new TimerTask() {
						@Override
						public void run() {
							// TODO Auto-generated method stub
							mDlnaCtrl.openDlnaService();//open dlna service after 3 second
						}
					}, 3000);
					if (MainService.mMusicService.getPlaybackStatus() == IMusicService.STATE_PAUSED) {
		                YZSMusicCtrl.play();
		            }else if((MainService.mMusicService.getPlaybackStatus() == IMusicService.STATE_NONE) || (MainService.mMusicService.getPlaybackStatus() == IMusicService.STATE_STOPPED)){
		            	if(MainService.mMusicService.getCurrMusicList() != null){
		            		YZSMusicCtrl.playMusicList(MainService.mMusicService.getCurrMusicList(), (int)(MainService.mMusicService.getCurrMusicInfo().getItemId()), MusicPlayMode.PLAY_LIST);
		            	}else{
		            		YZSMusicCtrl.playMusicList(MainService.mMusicService.getLocalMusicList(), 0, MusicPlayMode.PLAY_LIST);
		            	}
		            }
				}else if(mCurrentStatus == CtrStatusType.BLUETOOTH_CONNECTED){
					Log.d(TAG, "DLNA_PLAY -> BLUETOOTH_CONNECTED : close dlna service");
					mDlnaCtrl.closeDlnaService();
				}else if(mCurrentStatus == CtrStatusType.YZS_MUSIC_STOP){
					mCurrentStatus = CtrStatusType.DLNA_PLAY;
				}else{
					Log.e(TAG, "It is the err status:mCurrentStatus = "+mCurrentStatus);
				}
				break;
				
			case DLNA_PAUSE:
				if(mCurrentStatus == CtrStatusType.YZS_MUSIC_PLAYING || mCurrentStatus == CtrStatusType.DLNA_PLAY 
						|| mCurrentStatus == CtrStatusType.DLNA_STOP || mCurrentStatus == CtrStatusType.YZS_MUSIC_STOP){
					Log.d(TAG, "DLNA_PAUSE -> *** : do nothing,let it auto play or stop");
				}else if(mCurrentStatus == CtrStatusType.BLUETOOTH_CONNECTED ){
					Log.d(TAG, "DLNA_PAUSE -> BLUETOOTH_CONNECTED : close the dlna service");
					mDlnaCtrl.closeDlnaService();
				}else{
					Log.e(TAG, "It is the err status:mCurrentStatus = "+mCurrentStatus);
				}
				break;
				
			case DLNA_STOP:
				if(mCurrentStatus == CtrStatusType.YZS_MUSIC_PLAYING || mCurrentStatus == CtrStatusType.DLNA_PLAY || mCurrentStatus == CtrStatusType.YZS_MUSIC_STOP){
					Log.d(TAG, "DLNA_STOP -> ***_play : do nothing,let it auto play or stop");
				}else if(mCurrentStatus == CtrStatusType.BLUETOOTH_CONNECTED){
					Log.d(TAG, "DLNA_STOP -> BLUETOOTH_CONNECTED : close the dlna service");
					mDlnaCtrl.closeDlnaService();
				}else{
					Log.e(TAG, "It is the err status:mCurrentStatus = "+mCurrentStatus);
				}
				break;
				
			case BLUETOOTH_CONNECTED:
				if(mCurrentStatus == CtrStatusType.YZS_MUSIC_PLAYING ){
					Log.d(TAG, "BLUETOOTH_CONNECTED -> YZS_MUSIC_PLAYING:close bluetooth ,then open bluetooth");
					mBluetoothCtrl.closeBluetooth();
					Timer bluetoothTimer1 = new Timer();
					bluetoothTimer1.schedule(new TimerTask() {
						@Override
						public void run() {
							// TODO Auto-generated method stub
							mBluetoothCtrl.openBluetooth();
						}
					}, 200);
					if (MainService.mMusicService.getPlaybackStatus() == IMusicService.STATE_PAUSED) {
		                YZSMusicCtrl.play();
		            }else if((MainService.mMusicService.getPlaybackStatus() == IMusicService.STATE_NONE) || (MainService.mMusicService.getPlaybackStatus() == IMusicService.STATE_STOPPED)){
		            	if(MainService.mMusicService.getCurrMusicList() != null){
		            		YZSMusicCtrl.playMusicList(MainService.mMusicService.getCurrMusicList(), (int)(MainService.mMusicService.getCurrMusicInfo().getItemId()), MusicPlayMode.PLAY_LIST);
		            	}else{
		            		YZSMusicCtrl.playMusicList(MainService.mMusicService.getLocalMusicList(), 0, MusicPlayMode.PLAY_LIST);
		            	}
		            }
				}else if(mCurrentStatus == CtrStatusType.BLUETOOTH_DISCONNECTED){
					Log.d(TAG, "BLUETOOTH_CONNECTED -> BLUETOOTH_DISCONNECTED : open the dlna service");
					Timer dlnaTimer1 = new Timer();
					dlnaTimer1.schedule(new TimerTask() {
						@Override
						public void run() {
							// TODO Auto-generated method stub
							mDlnaCtrl.openDlnaService();
						}
					}, 3000);
					if (MainService.mMusicService.getPlaybackStatus() == IMusicService.STATE_PAUSED) {
		                YZSMusicCtrl.play();
		            }
				}else if(mCurrentStatus == CtrStatusType.YZS_MUSIC_STOP){
					mCurrentStatus = CtrStatusType.BLUETOOTH_CONNECTED;
				}else{
					Log.e(TAG, "It is the err status:mCurrentStatus = "+mCurrentStatus);
				}
				break;
			case BLUETOOTH_DISCONNECTED:
				if(mCurrentStatus == CtrStatusType.YZS_MUSIC_PLAYING || mCurrentStatus == CtrStatusType.DLNA_PLAY || mCurrentStatus == CtrStatusType.YZS_MUSIC_STOP){
					Log.d(TAG, "BLUETOOTH_DISCONNECTED -> *** : do nothing,let it auto play or stop");
				}else if(mCurrentStatus == CtrStatusType.BLUETOOTH_CONNECTED){
					Log.d(TAG, "BLUETOOTH_DISCONNECTED -> BLUETOOTH_CONNECTED : close the dlna service");
					mDlnaCtrl.closeDlnaService();
				}else{
					Log.e(TAG, "It is the err status:mCurrentStatus = "+mCurrentStatus);
				}
				break;
			default:
				break;
			}
		}
	}
	
}
