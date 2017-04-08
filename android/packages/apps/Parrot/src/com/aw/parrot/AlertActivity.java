package com.aw.parrot;

import java.util.ArrayList;
import java.util.List;

import com.unisound.sim.im.alert.AlertData;
import com.unisound.sim.im.alert.AlertType;
import com.unisound.sim.im.music.IMusicService;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class AlertActivity extends Activity{
	private final String TAG = "llh>>AlertActivity";
	private final int MENU_ALERT_TASK_UPDATE = 1;
	private final int MENU_ALERT_TASK_DELETE = 2;
	
	private final int CHANGE_TASK_LIST_MSG = 3;
	private final int TYPE_UPDATE_TASK = 4;
	private final int TYPE_ADD_TASK = 5;
	
	private ListView mAlertListView;
	private Button mAddByHandBt;
	private Button mAddBySpeakBt;
	private Button mStopAlertBt;
	
	private Dialog mEditDialog;
	private EditText mTaskContentText;
	private EditText mTaskTimeText;
	private AlertData mAlertData;
	
	private MyAlertListChangeReceiver mAlertListChangeReceiver;
	private AlertListAdapter mAlertListAdapter;
	private List<AlertData> mAlertList;
	private boolean mSelectedTaskIsOvertime;
	
	private Handler mHandler = new Handler(){
		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
			case CHANGE_TASK_LIST_MSG:
				mAlertListAdapter.notifyDataChange((ArrayList<AlertData>) msg.obj);
				break;

			default:
				break;
			}
		};
	};
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
		Log.d(TAG, "onCreate");
		setContentView(R.layout.activity_alert);
		initAllView();
		super.onCreate(savedInstanceState);
	}
	
	@Override
	protected void onStart() {
		// TODO Auto-generated method stub
		Log.d(TAG, "onStart");
		registerAlertListChangeReceiver();
		updateAlertList();
		super.onStart();
	}
	
	@Override
	protected void onStop() {
		// TODO Auto-generated method stub
		Log.d(TAG, "onStop");
		unregisterReceiver(mAlertListChangeReceiver);
		super.onStop();
	}
	
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		Log.d(TAG, "onDestroy");
		super.onDestroy();
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		// TODO Auto-generated method stub
		AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item
                .getMenuInfo();
        AlertData alertData = mAlertList.get(menuInfo.position);
        switch (item.getItemId()) {
        case MENU_ALERT_TASK_UPDATE:
        	showDialogAndEditTask(TYPE_UPDATE_TASK, alertData);
            break;
        case MENU_ALERT_TASK_DELETE:
        	deleteTask(alertData);
            break;
        }
		return super.onContextItemSelected(item);
	}
	
	private void initAllView(){
		mAlertListView = (ListView)findViewById(R.id.alert_list_id);
		mAddByHandBt = (Button)findViewById(R.id.hand_bt_id);
		mAddBySpeakBt = (Button)findViewById(R.id.speak_bt_id);
		mStopAlertBt = (Button)findViewById(R.id.stop_alert_id);
		mAlertListAdapter = new AlertListAdapter(this, null);
		mAlertListView.setAdapter(mAlertListAdapter);
		mAddByHandBt.setOnClickListener(new MyClickListener());
		mAddBySpeakBt.setOnClickListener(new MyClickListener());
		mStopAlertBt.setOnClickListener(new MyClickListener());
		mAlertListView.setOnItemLongClickListener(new OnItemLongClickListener() {
			
			@Override
			public boolean onItemLongClick(AdapterView<?> arg0, View arg1,
					int arg2, long arg3) {
				// TODO Auto-generated method stub
				Log.d(TAG, "onItemLongClick : index = "+arg2);
				AlertData selectedItemData = (AlertData)mAlertListAdapter.getItem(arg2);
				mSelectedTaskIsOvertime = selectedItemData.getTriggerTime() > System.currentTimeMillis() ? false : true;
				return false;
			}
		});
		mAlertListView.setOnCreateContextMenuListener(new OnCreateContextMenuListener() {
			@Override
			public void onCreateContextMenu(ContextMenu arg0, View arg1,
					ContextMenuInfo arg2) {
				// TODO Auto-generated method stub
				arg0.setHeaderIcon(android.R.drawable.ic_menu_edit);
				arg0.setHeaderTitle(getString(R.string.edit_alert));
				arg0.add(Menu.NONE, MENU_ALERT_TASK_DELETE, 0,
						getString(R.string.delete_alert));
				if(!mSelectedTaskIsOvertime){
					arg0.add(Menu.FIRST, MENU_ALERT_TASK_UPDATE, 0,
	                        getString(R.string.update_alert));
				}
			}
		});
	}
	
	private void showDialogAndEditTask(final int editType,AlertData alertData){
		mAlertData= alertData;
		mEditDialog = new Dialog(this);
        View view = LayoutInflater.from(this).inflate(
                R.layout.alert_task_edit_layout, null);
        mEditDialog.setContentView(view);
        mEditDialog.show();
        Button doneButton = (Button)view.findViewById(R.id.done);
        Button cancelButton = (Button)view.findViewById(R.id.cancel);
        mTaskContentText = (EditText)view.findViewById(R.id.task_content);
        mTaskTimeText = (EditText)view.findViewById(R.id.task_time);
        if(alertData !=null){
        	mTaskContentText.setText(alertData.getContent());
        	mTaskTimeText.setText(Utils.dateToString(alertData.getTriggerTime()));
        }
        doneButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				// TODO Auto-generated method stub
				String content = mTaskContentText.getText().toString();
				long triggerTime = Utils.stringToDate(mTaskTimeText.getText().toString());
				Log.d(TAG, "the task is : "+content);
				if(content.equals("")){
					Toast.makeText(AlertActivity.this, "the task is null", Toast.LENGTH_LONG).show();
				}else if(triggerTime < System.currentTimeMillis()){
					Toast.makeText(AlertActivity.this, "the task is overtime", Toast.LENGTH_LONG).show();
				}else{
					if(editType == TYPE_UPDATE_TASK){
						updateTask(content, triggerTime, mAlertData);
					}else{
						addTask(content, triggerTime);
					}
					mEditDialog.dismiss();
				}
			}
		});
        cancelButton.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View arg0) {
				// TODO Auto-generated method stub
				mEditDialog.dismiss();
			}
		});
	}
	
	private void initAddBySpeakDialog(){
		new AlertDialog.Builder(this)
        .setTitle(getString(R.string.wake_up_tip))
        .setMessage(getString(R.string.add_task_tip)).show();
	}
	
	private void addTask(String content , long triggerTime){
		AlertData alertData = new AlertData();
		alertData.setContent(content);
		alertData.setTriggerTime(triggerTime);
		alertData.setType(AlertType.ALARM);
		MainService.mAlertService.add(alertData);
		updateAlertList();
	}
	
	private void deleteTask(AlertData alertData){
		if(alertData == null){
			Toast.makeText(this, "the alertdata is null", Toast.LENGTH_LONG).show();
		}else{
			MainService.mAlertService.delete(alertData.getTaskId());
		}
		updateAlertList();
	}
	
	private void updateTask(String content , long triggerTime ,AlertData alertData){
		if(alertData == null){
			Toast.makeText(this, "the alertdata is null", Toast.LENGTH_LONG).show();
		}else{
			alertData.setContent(content);
			alertData.setTriggerTime(triggerTime);
			MainService.mAlertService.update(alertData);
		}
		updateAlertList();
	}
	
	private void updateAlertList(){
		mAlertList = MainService.mAlertService.getAllAlertData();
		sendMessage(CHANGE_TASK_LIST_MSG, mAlertList);
	}
	
	private void sendMessage(int what,Object obj){
		Message message = mHandler.obtainMessage(what, obj);
        mHandler.sendMessage(message);
	}
	
	private void registerAlertListChangeReceiver(){
		mAlertListChangeReceiver = new MyAlertListChangeReceiver();
		IntentFilter filter = new IntentFilter();
		filter.addAction(MainService.ACTION_ALERT_LIST_CHANGE);
		registerReceiver(mAlertListChangeReceiver, filter);
	}
	
	private class MyClickListener implements android.view.View.OnClickListener{

		@Override
		public void onClick(View arg0) {
			// TODO Auto-generated method stub
			switch (arg0.getId()) {
			case R.id.hand_bt_id:
				showDialogAndEditTask(TYPE_ADD_TASK, null);
				break;
				
			case R.id.speak_bt_id:
				initAddBySpeakDialog();
				break;
				
			case R.id.stop_alert_id:
				Log.d(TAG, "stop the alert button is clicked");
				if(MainService.mAlertService.isAlerting()){
					Toast.makeText(getApplication(), "stop the alert", Toast.LENGTH_LONG).show();
					MainService.mAlertService.stopAlert();
					if(MainService.mStatusCtrlManager.getCurStatus() == CtrStatusType.YZS_MUSIC_PAUSE){
						YZSMusicCtrl.play();
					}
				}else{
					Toast.makeText(getApplication(), "the alert is not alerting", Toast.LENGTH_SHORT).show();
				}
				break;
				
			default:
				break;
			}
		}
	}
	
	private class MyAlertListChangeReceiver extends BroadcastReceiver{

		@Override
		public void onReceive(Context arg0, Intent arg1) {
			// TODO Auto-generated method stub
			Log.d(TAG, "onReceive : "+arg1.getAction());
			updateAlertList();
		}
		
	}
}
