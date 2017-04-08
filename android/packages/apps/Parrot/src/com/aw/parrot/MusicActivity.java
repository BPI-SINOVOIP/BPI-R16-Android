package com.aw.parrot;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import com.unisound.sim.cmd.base.CmdManager;
import com.unisound.sim.cmd.base.CmdType;
import com.unisound.sim.im.music.IMusicService;
import com.unisound.sim.im.music.Music;
import com.unisound.sim.im.music.MusicPlayMode;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class MusicActivity extends Activity {

	private final String TAG = "llh>>MusicActivity";
	
	private final int CHANGE_TITLE_MSG = 1;
	private final int CHANGE_LIST_MSG = 2;
	private final int CHANGE_PROGRESS_MSG = 3;
	private final int CHANGE_PLAY_BUTTON_MSG = 4;
	
	private ListView mMusicListView;
	private TextView mMusicTitleView;
	private TextView mStartTimeView;
	private TextView mTotalTimeView;
	private SeekBar mSeekBar;
	private Button mPreButton;
	private Button mPlayButton;
	private Button mNextButton;
	private YZSMusicListAdapter mMusicListAdapter;
	
	private Timer mTimer;
	
	private MyMusicListChangeReceiver mMusicListChangeReceiver;
	private MyMusicChangeReceiver mMusicChangeReceiver;
	private MyPlayButtonChangeReceiver mPlayButtonChangeReceiver;
	private boolean mAllMusicListChangeFlag;
	
	private Handler mHandler = new Handler(){
		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
			case CHANGE_TITLE_MSG:
				if(msg.obj != null){
					if (msg.obj instanceof Music) {
	                    Music info = (Music) msg.obj;
	                    
	                    if((info.getTitle() == null) || ((info.getTitle() != null) && ((info.getDuration() == 0)))){
	                    	mMusicTitleView.setText("");
	                        mSeekBar.setProgress(0);
	                        mStartTimeView.setText("");
	                        mTotalTimeView.setText("");
	                    }else{
	                    	mMusicTitleView.setText(info.getTitle());
	                        mSeekBar.setMax((int) info.getDuration());
	                        mStartTimeView.setText("00:00");
	                        mTotalTimeView.setText(Utils.getPlayTime(info.getDuration()));
	                        if(MainService.mMusicService.getCurrMusicList() == null){
	                        	mMusicListAdapter.setSelectItem(0);
	                        	mMusicListAdapter.notifyDataChange((ArrayList<Music>)MainService.getLocalMusicList());
	                        }else{
	                        	mMusicListAdapter.setSelectItem((int)(MainService.mMusicService.getCurrMusicInfo().getItemId()));
	                        	mMusicListAdapter.notifyDataChange((ArrayList<Music>)MainService.mMusicService.getCurrMusicList());
	                        }
	                    }
	                } 
				}else{
					mMusicTitleView.setText("");
                    mSeekBar.setProgress(0);
                    mStartTimeView.setText("");
                    mTotalTimeView.setText("");
				}
				
				break;
			case CHANGE_LIST_MSG:
				if(msg.obj == null){
					mMusicListAdapter.notifyDataChange((ArrayList<Music>) msg.obj);
				}else{
					if(MainService.mMusicService.getCurrMusicList() != null){
						mMusicListAdapter.setSelectItem((int)MainService.mMusicService.getCurrMusicInfo().getItemId());
					}
					mMusicListAdapter.notifyDataChange((ArrayList<Music>) msg.obj);
				}
				break;
			case CHANGE_PROGRESS_MSG:
//				Log.d(TAG, "handleMessage:current play time = "+msg.obj.toString());
				mSeekBar.setProgress((Integer) msg.obj);
				mStartTimeView.setText(Utils.getPlayTime(Long.parseLong(msg.obj.toString())));
				break;
			case CHANGE_PLAY_BUTTON_MSG:
				Log.d(TAG, "CHANGE_PLAY_BUTTON_MSG:mMusicService.getPlaybackStatus() = "+MainService.mMusicService.getPlaybackStatus());
				if(MainService.mMusicService.getPlaybackStatus()==IMusicService.STATE_PLAYING){
					mPlayButton.setText(getString(R.string.pause));
				}else{
					mPlayButton.setText(getString(R.string.play));
				}
				break;
			default:
				break;
			}
		};
	};
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		super.onCreate(savedInstanceState);
		Log.d(TAG, "onCreate");
		setContentView(R.layout.activity_music);
		initView();
	}
	
	@Override
	protected void onStart() {
		// TODO Auto-generated method stub
		Log.d(TAG, "onStart");
		if(MainService.mMusicService.getCurrMusicList() == null){
			updateLocalMusicList();
		}else{
			updateCurMusicList();
		}
		updateStaticCurMusic();
		updatePlayTime();
		registerMusicListChangeReceiver();
		registerMusicChangeReceiver();
		registerPlayButtonChangeReceiver();
		super.onStart();
	}
	
	@Override
	protected void onStop() {
		// TODO Auto-generated method stub
		Log.d(TAG, "onStop");
		unregisterReceiver(mMusicListChangeReceiver);
		unregisterReceiver(mMusicChangeReceiver);
		unregisterReceiver(mPlayButtonChangeReceiver);
		super.onStop();
	}
	
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		Log.d(TAG, "onDestroy");
		super.onDestroy();
	}
	
	private void initView(){
		mMusicListView = (ListView)findViewById(R.id.music_list_id);
		mMusicTitleView = (TextView)findViewById(R.id.music_name_textview_id);
		mStartTimeView = (TextView)findViewById(R.id.start_time_id);
		mTotalTimeView = (TextView)findViewById(R.id.end_time_id);
		mSeekBar = (SeekBar)findViewById(R.id.seekbar_id);
		mPreButton = (Button)findViewById(R.id.pre_bt_id);
		mPlayButton = (Button)findViewById(R.id.play_bt_id);
		mNextButton = (Button)findViewById(R.id.next_bt_id);
		mSeekBar.setOnSeekBarChangeListener(new MySeekBarChangeListener());
		mPreButton.setOnClickListener(new MyClickListener());
		mPlayButton.setOnClickListener(new MyClickListener());
		mNextButton.setOnClickListener(new MyClickListener());
		mMusicListAdapter = new YZSMusicListAdapter(this, null);
		mMusicListView.setAdapter(mMusicListAdapter);
		mMusicListView.setOnItemClickListener(new MyListItemClickListener());
	}
	private void updateLocalMusicList(){
		Log.d(TAG, "updateLocalMusicList");
		List<Music> list = MainService.getLocalMusicList();
		Log.d(TAG, "local list size = "+list.size());
		sendMessage(CHANGE_LIST_MSG, list);
	}
	
	private void updateCurMusicList(){
		Log.d(TAG, "updateCurMusicList");
		List<Music> list = MainService.mMusicService.getCurrMusicList();
		Log.d(TAG, "cur list size = "+list.size());
		sendMessage(CHANGE_LIST_MSG, list);
		if(mAllMusicListChangeFlag){
			mAllMusicListChangeFlag = false;
			sendMessage(CHANGE_TITLE_MSG, null);
		}
	}
	
	private void updateStaticCurMusic(){
		Music music = MainService.mMusicService.getCurrMusicInfo();
		if(music == null){
			Log.d(TAG, "music is null");
		}else{
			Log.d(TAG, "updateStaticCurMusic: index is :"+music.getItemId()+",title:"+music.getTitle()+",artist:"+music.getArtist()
					+",album:"+music.getAlbum()+",total time:"+music.getDuration());
		}
		sendMessage(CHANGE_TITLE_MSG, music);
		sendMessageDelay(CHANGE_PLAY_BUTTON_MSG, null,100);
	}
	
	private void updatePlayTime(){
		if(MainService.mMusicService.getPlaybackStatus() == IMusicService.STATE_PLAYING){
			if(mTimer==null){
				mTimer = new Timer();
			} else {
	            mTimer.cancel();
	            mTimer = new Timer();
	        }
	        mTimer.schedule(new TimerTask() {
				
				@Override
				public void run() {
					// TODO Auto-generated method stub
					Music music = MainService.mMusicService.getCurrMusicInfo();
		            sendMessage(CHANGE_PROGRESS_MSG, (int)(music.getCurPostion()));
				}
			}, 0, 1000);
		}else{
			Music music = MainService.mMusicService.getCurrMusicInfo();
            sendMessage(CHANGE_PROGRESS_MSG, (int)(music.getCurPostion()));
		}
	}
	
	private void registerMusicListChangeReceiver(){
		mMusicListChangeReceiver = new MyMusicListChangeReceiver();
		IntentFilter filter = new IntentFilter();
		filter.addAction(MainService.ACTION_MUSIC_LIST_CHANGE);
		registerReceiver(mMusicListChangeReceiver, filter);
	}
	
	private void registerMusicChangeReceiver(){
		mMusicChangeReceiver = new MyMusicChangeReceiver();
		IntentFilter filter = new IntentFilter();
		filter.addAction(MainService.ACTION_MUSIC_INFO_CHANGE);
		registerReceiver(mMusicChangeReceiver, filter);
	}
	
	private void registerPlayButtonChangeReceiver(){
		mPlayButtonChangeReceiver = new MyPlayButtonChangeReceiver();
		IntentFilter filter = new IntentFilter();
		filter.addAction(MainService.ACTION_PLAY_BUTTON_CHANGE);
		registerReceiver(mPlayButtonChangeReceiver, filter);
	}
	
	private void sendMessage(int what,Object obj){
		Message message = mHandler.obtainMessage(what, obj);
        mHandler.sendMessage(message);
	}
	
	private void sendMessageDelay(int what,Object obj,int delayMs){
		Message message = mHandler.obtainMessage(what, obj);
		mHandler.sendMessageDelayed(message, delayMs);
	}
	
	private class MySeekBarChangeListener implements OnSeekBarChangeListener{

		@Override
		public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2) {
			// TODO Auto-generated method stub
//			Log.d(TAG, "onProgressChanged:progress = "+arg0.getProgress()+",arg1 = "+arg1+",arg2 = "+arg2);
			if(arg2){//arg2 is true means the user seek the seekbar
				Log.d(TAG, "onProgressChanged:progress = "+arg0.getProgress()+",arg1 = "+arg1+",arg2 = "+arg2);
				YZSMusicCtrl.seek(arg1);
			}
		}

		@Override
		public void onStartTrackingTouch(SeekBar arg0) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void onStopTrackingTouch(SeekBar arg0) {
			// TODO Auto-generated method stub
			
		}
		
	}
	
	private class MyClickListener implements OnClickListener{

		@Override
		public void onClick(View arg0) {
			// TODO Auto-generated method stub
			switch (arg0.getId()) {
			case R.id.pre_bt_id:
				Log.d(TAG, "previous button is onClick");
				if((MainService.mMusicService.getPlaybackStatus() == IMusicService.STATE_NONE) || (MainService.mMusicService.getPlaybackStatus() == IMusicService.STATE_STOPPED)){
	            	if(MainService.mMusicService.getCurrMusicList() != null){
	            		int playPreIndex;
	            		if(MainService.mMusicService.getCurrMusicInfo().getItemId()==0){
	            			playPreIndex = MainService.mMusicService.getCurrMusicList().size()-1;
	            		}else{
	            			playPreIndex = (int)MainService.mMusicService.getCurrMusicInfo().getItemId()-1;
	            		}
	            		YZSMusicCtrl.playMusicList(MainService.mMusicService.getCurrMusicList(), playPreIndex, MusicPlayMode.PLAY_LIST);
	            	}else{//only the first time play local music will go here
	            		YZSMusicCtrl.playMusicList(MainService.getLocalMusicList(), MainService.getLocalMusicList().size() - 1, MusicPlayMode.PLAY_LIST);
	            	}
				}else{
					YZSMusicCtrl.previous();
				}
				break;
			case R.id.play_bt_id:
				Log.d(TAG, "play button is onClick");
				Log.d(TAG, "play status :"+MainService.mMusicService.getPlaybackStatus());
				if (MainService.mMusicService.getPlaybackStatus() == IMusicService.STATE_PLAYING) {
					mPlayButton.setText(getString(R.string.play));
	                MainService.mStatusCtrlManager.changeStatusAndCtrPlay(CtrStatusType.YZS_MUSIC_PAUSE);
	            } else if (MainService.mMusicService.getPlaybackStatus() == IMusicService.STATE_PAUSED) {
	                YZSMusicCtrl.play();
	            }else if((MainService.mMusicService.getPlaybackStatus() == IMusicService.STATE_NONE) || (MainService.mMusicService.getPlaybackStatus() == IMusicService.STATE_STOPPED)){
	            	if(MainService.mMusicService.getCurrMusicList() != null){
	            		YZSMusicCtrl.playMusicList(MainService.mMusicService.getCurrMusicList(), (int)(MainService.mMusicService.getCurrMusicInfo().getItemId()), MusicPlayMode.PLAY_LIST);
	            	}else{
	            		YZSMusicCtrl.playMusicList(MainService.getLocalMusicList(), 0, MusicPlayMode.PLAY_LIST);
	            	}
	            }
				break;
			case R.id.next_bt_id:
				Log.d(TAG, "next button is onClick");
				if((MainService.mMusicService.getPlaybackStatus() == IMusicService.STATE_NONE) || (MainService.mMusicService.getPlaybackStatus() == IMusicService.STATE_STOPPED)){
	            	if(MainService.mMusicService.getCurrMusicList() != null){
	            		int playNextIndex;
	            		if(MainService.mMusicService.getCurrMusicInfo().getItemId()==(MainService.mMusicService.getCurrMusicList().size()-1)){
	            			playNextIndex = 0;
	            		}else{
	            			playNextIndex = (int)MainService.mMusicService.getCurrMusicInfo().getItemId()+1;
	            		}
	            		YZSMusicCtrl.playMusicList(MainService.mMusicService.getCurrMusicList(), playNextIndex, MusicPlayMode.PLAY_LIST);
	            	}else{
	            		if(MainService.getLocalMusicList().size()>1){
	            			YZSMusicCtrl.playMusicList(MainService.getLocalMusicList(), 1, MusicPlayMode.PLAY_LIST);
	            		}else{
	            			YZSMusicCtrl.playMusicList(MainService.getLocalMusicList(), 0, MusicPlayMode.PLAY_LIST);
	            		}
	            	}
				}else{
					YZSMusicCtrl.next();
				}
				break;
			default:
				break;
			}
		}
		
	}
	
	private class MyListItemClickListener implements OnItemClickListener{

		@Override
		public void onItemClick(AdapterView<?> arg0, View arg1, int arg2,
				long arg3) {
			// TODO Auto-generated method stub
			if(MainService.mMusicService.getCurrMusicList() != null){
				Log.d(TAG, "onItemClick:index = "+arg2+",play current music");
        		YZSMusicCtrl.playMusicList(MainService.mMusicService.getCurrMusicList(), arg2, MusicPlayMode.PLAY_LIST);
        	}else{
        		Log.d(TAG, "onItemClick:index = "+arg2+",play local music");
        		YZSMusicCtrl.playMusicList(MainService.getLocalMusicList(), arg2, MusicPlayMode.PLAY_LIST);
        	}
		}
		
	}
	
	private class MyMusicListChangeReceiver extends BroadcastReceiver{

		@Override
		public void onReceive(Context arg0, Intent arg1) {
			// TODO Auto-generated method stub
			Log.d(TAG, "onReceive : "+arg1.getAction());
			if(MainService.mMusicService.getCurrMusicList()==null){
				updateLocalMusicList();
			}else{
				if(arg1.getBooleanExtra("all_music_list_changed", false)){
					mAllMusicListChangeFlag = true;
				}else{
					mAllMusicListChangeFlag = false;
				}
				updateCurMusicList();
			}
		}
		
	}
	
	private class MyMusicChangeReceiver extends BroadcastReceiver{

		@Override
		public void onReceive(Context arg0, Intent arg1) {
			// TODO Auto-generated method stub
			Log.d(TAG, "onReceive : "+arg1.getAction());
			updateStaticCurMusic();
			updatePlayTime();
		}
		
	}
	
	private class MyPlayButtonChangeReceiver extends BroadcastReceiver{

		@Override
		public void onReceive(Context arg0, Intent arg1) {
			// TODO Auto-generated method stub
			Log.d(TAG, "onReceive : "+arg1.getAction());
			sendMessageDelay(CHANGE_PLAY_BUTTON_MSG, null,100);
		}
		
	}
	
	
}
