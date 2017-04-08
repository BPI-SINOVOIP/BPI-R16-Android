/*******************************************************************************
 *
 *  Copyright (C) 2013 Broadcom Corporation
 *
 *  This program is the proprietary software of Broadcom Corporation and/or its
 *  licensors, and may only be used, duplicated, modified or distributed
 *  pursuant to the terms and conditions of a separate, written license
 *  agreement executed between you and Broadcom (an "Authorized License").
 *  Except as set forth in an Authorized License, Broadcom grants no license
 *  (express or implied), right to use, or waiver of any kind with respect to
 *  the Software, and Broadcom expressly reserves all rights in and to the
 *  Software and all intellectual property rights therein.
 *  IF YOU HAVE NO AUTHORIZED LICENSE, THEN YOU HAVE NO RIGHT TO USE THIS
 *  SOFTWARE IN ANY WAY, AND SHOULD IMMEDIATELY NOTIFY BROADCOM AND DISCONTINUE
 *  ALL USE OF THE SOFTWARE.
 *
 *  Except as expressly set forth in the Authorized License,
 *
 *  1.     This program, including its structure, sequence and organization,
 *         constitutes the valuable trade secrets of Broadcom, and you shall
 *         use all reasonable efforts to protect the confidentiality thereof,
 *         and to use this information only in connection with your use of
 *         Broadcom integrated circuit products.
 *
 *  2.     TO THE MAXIMUM EXTENT PERMITTED BY LAW, THE SOFTWARE IS PROVIDED
 *         "AS IS" AND WITH ALL FAULTS AND BROADCOM MAKES NO PROMISES,
 *         REPRESENTATIONS OR WARRANTIES, EITHER EXPRESS, IMPLIED, STATUTORY,
 *         OR OTHERWISE, WITH RESPECT TO THE SOFTWARE.  BROADCOM SPECIFICALLY
 *         DISCLAIMS ANY AND ALL IMPLIED WARRANTIES OF TITLE, MERCHANTABILITY,
 *         NONINFRINGEMENT, FITNESS FOR A PARTICULAR PURPOSE, LACK OF VIRUSES,
 *         ACCURACY OR COMPLETENESS, QUIET ENJOYMENT, QUIET POSSESSION OR
 *         CORRESPONDENCE TO DESCRIPTION. YOU ASSUME THE ENTIRE RISK ARISING OUT
 *         OF USE OR PERFORMANCE OF THE SOFTWARE.
 *
 *  3.     TO THE MAXIMUM EXTENT PERMITTED BY LAW, IN NO EVENT SHALL BROADCOM OR
 *         ITS LICENSORS BE LIABLE FOR
 *         (i)   CONSEQUENTIAL, INCIDENTAL, SPECIAL, INDIRECT, OR EXEMPLARY
 *               DAMAGES WHATSOEVER ARISING OUT OF OR IN ANY WAY RELATING TO
 *               YOUR USE OF OR INABILITY TO USE THE SOFTWARE EVEN IF BROADCOM
 *               HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES; OR
 *         (ii)  ANY AMOUNT IN EXCESS OF THE AMOUNT ACTUALLY PAID FOR THE
 *               SOFTWARE ITSELF OR U.S. $1, WHICHEVER IS GREATER. THESE
 *               LIMITATIONS SHALL APPLY NOTWITHSTANDING ANY FAILURE OF
 *               ESSENTIAL PURPOSE OF ANY LIMITED REMEDY.
 *
 *******************************************************************************/

package com.broadcom.bt.app.hfdevice;

import java.util.ArrayList;
import java.util.List;
import java.io.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Notification;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.Vibrator;
import android.widget.Button;
import android.os.IBinder;
import android.os.SystemClock;
import android.content.Intent;
import android.widget.Toast;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.provider.Settings;
import android.content.Context;
import android.content.IntentFilter;
import android.view.View;
import android.view.MenuInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.media.Ringtone;
import android.view.LayoutInflater;
import android.view.WindowManager.LayoutParams;
import android.view.Window;
import android.view.View.OnClickListener;
import android.content.ComponentName;
import android.content.BroadcastReceiver;
import android.view.KeyEvent;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.content.DialogInterface;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.media.AudioManager;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ArrayAdapter;
import android.os.Environment;


import java.util.Timer;
import java.util.TimerTask;

import com.broadcom.bt.hfdevice.BluetoothHfDevice;
import com.broadcom.bt.hfdevice.BluetoothCallStateInfo;
import com.broadcom.bt.hfdevice.IBluetoothHfDeviceEventHandler;
import android.bluetooth.BluetoothProfile.ServiceListener;
import com.broadcom.bt.hfdevice.BluetoothClccInfo;
import com.broadcom.bt.hfdevice.BluetoothPhoneBookInfo;

/**
 * <li> Connection to a device
 * <li> Handling of incoming call
 * <li> Handling of outgoing call
 */

public class BRCMHfDeviceConnectedActivity extends Activity implements ServiceListener,OnClickListener {

    private static final String TAG = "BRCMHfDeviceConnectedActivity";

    /* Object instant references. */
    private HfDeviceEventHandler mHfDeviceEventHandler;
    private SharedPreferences pref;
    private NotificationManager mNotificationManager;
    private BluetoothHfDevice bluetoothHFDevice;
    private BluetoothDevice bluetoothDevice;
    private String mDeviceName;
    private String mDeviceAddress;
    private String dialedNumber;
    private String incomingCallNumber;
    private String waitingCallNumber;
    private SharedPreferences.Editor editor;
    private int currentSignalStrength;
    private int currentBatteryCharge;
    private boolean isKeyguardDisabled;
    private boolean isRoaming;
    private boolean isVibrating;
    private final String privacyMode = "Privacy Mode";
    private final String handsfreeMode = "Handsfree Mode";
    private final String holdCall = "Hold call";
    private final String unholdCall = "Unhold call";
    private final String multiCallControl = "Call control options";
    private final String dialStr = "Dial";
    private final String disconectStr = "Disconnect";
    private final String endCallStr = "End Call";
    private final String answerStr = "Answer";
    private final String rejectStr = "Reject";
    private final String vrOnStr = "VR On";
    private final String vrOffStr = "VR off";
    private final String wbsOnStr = "WBS On";
    private final String wbsOffStr = "WBS Off";
    private final String inBandOnStr = "InBand On";
    private final String inBandOffStr = "InBand Off";

    private final String sendKeyPress = "Send key press";

    private boolean isPendingClcc = false;

    private final int DEFAULT_SCREEN_TIMEOUT = 30000;
    private int volumeMax = 15;
    private String volumeDialogTitle = "Ringer Volume";
    private boolean volumeFlag = false ;
    private int currentVolume;
    private int callState = -1;
    private int mVrState = 0;

    /*View variables */
    private EditText numberEntry;
    private TextView numberEntryText;
    private Button textButton; //  to disp handsfree, prviate mode strings
    private Button textButton2; // to disp answer , dial strings
    private Button redial;
    private Button endCall; // to disp endcall, reject strings
    private TextView displayState;
    private TextView displayNumber;
    private TextView operatorName;
    private TextView subscriberNumber;
    private TextView wbsStatus;
    private TextView inBandStatus;
    private ImageView batteryStatus;
    private ImageView signalStatus;
    private Button callControl;
    private Button vrControl;// used as Send key press button in HSP connection mode

    /*Alert variables*/
    private PowerManager pm;
    private PowerManager.WakeLock wl;
    private KeyguardManager keyguardMgr;
    private KeyguardManager.KeyguardLock keyguardLock;
    private Vibrator vibrator;
    private AudioManager ag;

    private ListView callListView;
    private ArrayAdapter callItems;

    /* volume dialog */
    private Dialog              mVolumeDialog;
    private SeekBar             mSeekBar;
    private AlertDialog.Builder mVolumeBuilder;
    private Timer               mVolumeTimer;

    /* GUI message codes. */
    public static final int GUI_UPDATE_DEVICE_STATUS = 1;
    public static final int GUI_UPDATE_CALL_STATUS = 2;
    public static final int GUI_UPDATE_DEVICE_INDICATORS = 3;
    public static final int GUI_UPDATE_INCOMING_CALL_NUMBER = 4;
    public static final int GUI_UPDATE_AUDIO_STATE = 5;
    public static final int GUI_UPDATE_VENDOR_AT_RSP = 6;
    public static final int GUI_UPDATE_CLCC_AT_RSP = 7;
    public static final int GUI_UPDATE_VOLUME = 8;
    public static final int GUI_UPDATE_OPERATOR = 9;
    public static final int GUI_UPDATE_SUBSCRIBER = 10;
    public static final int GUI_UPDATE_VR_STATE = 11;
    public static final int GUI_UPDATE_PHONEBOOK_AT_RSP = 12;
    public static final int GUI_UPDATE_WBS_STATE = 13;
    public static final int GUI_UPDATE_RING = 14;
    public static final int GUI_UPDATE_IN_BAND_STATUS = 15;

    private List<BluetoothClccInfo> mCallList;
    private List<BluetoothPhoneBookInfo> mPhoneNumList;
    private int mDownLoadCount;

    private BroadcastReceiver mWbsStateIntentRec = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, action);
            if (action.equals(BluetoothHfDevice.ACTION_WBS_STATE_CHANGED)) {
                 int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE,
                    BluetoothAdapter.ERROR);
                 Log.d(TAG, "ACTION_WBS_STATE_CHANGED: state = " + state);
                 Message msg1 = Message.obtain();
                 msg1.what = GUI_UPDATE_WBS_STATE;
                 msg1.arg1 = state;
                 viewUpdateHandler.sendMessage(msg1);
           }
        }
    };


    /**
     * Called first when the Activity is created
     */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG,"onCreate()");

        BluetoothAdapter adp = BluetoothAdapter.getDefaultAdapter();
        if(true != adp.isEnabled()) {
            Toast.makeText(getApplicationContext(),
                "Please enable BT and Pair/Connect an Ag from Settings App and launch the app",
                Toast.LENGTH_LONG).show();
            finish();
            return;
         }


        pref = PreferenceManager.getDefaultSharedPreferences(this);

        setContentView(R.layout.hfdevice_connect);

        ag  = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);


        Log.d(TAG, "onCreate: ");

        mNotificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        pm = (PowerManager)getSystemService(POWER_SERVICE);
        keyguardMgr = (KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        currentSignalStrength  = -1;
        currentBatteryCharge   = -1;
        isVibrating = false;
        displayState = (TextView)findViewById(R.id.titletext);
        displayNumber = (TextView)findViewById(R.id.displaynumber);
        numberEntryText = (TextView)findViewById(R.id.enternumber);
        numberEntry = (EditText)findViewById(R.id.edittext);
        textButton = (Button)findViewById(R.id.disconnect_button);
        redial = (Button)findViewById(R.id.redial_button);
        textButton2 = (Button)findViewById(R.id.dial_button);
        endCall = (Button)findViewById(R.id.endcall_button);
        batteryStatus = (ImageView)findViewById(R.id.battery);
        signalStatus = (ImageView)findViewById(R.id.signal);
        callListView = (ListView)findViewById(R.id.call_list);
        callControl = (Button)findViewById(R.id.call_control);
        vrControl = (Button)findViewById(R.id.vr_button);
        operatorName = (TextView)findViewById(R.id.operator);
        subscriberNumber = (TextView)findViewById(R.id.subscriberInfo);
        wbsStatus = (TextView)findViewById(R.id.wbsStatus);
        inBandStatus = (TextView)findViewById(R.id.inbandStatus);

        textButton.setOnClickListener(this);
        redial.setOnClickListener(this);
        textButton2.setOnClickListener(this);
        endCall.setOnClickListener(this);
        callControl.setOnClickListener(this);
        vrControl.setOnClickListener(this);

        numberEntry.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                BluetoothCallStateInfo callInfo = bluetoothHFDevice.getCallStateInfo(bluetoothDevice);
                if (!callInfo.isInCall())
                    return;
                char keyChar = 'x';
                try {
                    keyChar = s.charAt(start);
                    String allowedChars = "0123456789*#";
                    if(allowedChars.indexOf(keyChar) != -1) {
                        Log.d(TAG, "DTMF key code char = "+keyChar);
                        bluetoothHFDevice.sendDTMFcode(keyChar);
                    }
                    else {
                        Log.e(TAG, "Invalid character input");
                    }
                } catch (Exception e) {
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // TODO Auto-generated method stub
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                    int after) {
                // TODO Auto-generated method stub
            }
        });

        // set focus on some static item to avoid auto focus on editor
        signalStatus.setFocusableInTouchMode(true);
        signalStatus.requestFocus();

        callItems = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,
                new ArrayList<String>());

        callItems.clear();

        callListView.setAdapter(callItems);

        mVolumeDialog = null;

        /*Request for Proxy Object*/
        if(!BluetoothHfDevice.getProxy(this,this)) {
            Log.e(TAG,"onCreate: service not enabled...");
            showDialog(BRCMHfDeviceConstants.HF_DEVICE_SERVICE_NOT_ENABLED);
        }

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothHfDevice.ACTION_WBS_STATE_CHANGED);
        registerReceiver(mWbsStateIntentRec, intentFilter);

    }

    public void onStart() {
        Log.d(TAG,"onStart()");
        super.onStart();

        BluetoothAdapter adp = BluetoothAdapter.getDefaultAdapter();
        if(true != adp.isEnabled()) {
            Toast.makeText(getApplicationContext(),
                "Please enable BT and Pair/Connect an Ag from Settings App and launch the app",
                Toast.LENGTH_LONG).show();
            finish();
            return;
         }

        pref = PreferenceManager.getDefaultSharedPreferences(this);

        if(bluetoothHFDevice != null)
            updateUi();
    }

    public void onPause() {
        Log.d(TAG,"onPause()");

        if(bluetoothHFDevice != null)
            bluetoothHFDevice.sendBIA(false, false, false, false);

        super.onPause();
    }

    public void onResume() {
        Log.d(TAG,"onResume()");

        if(bluetoothHFDevice != null)
            bluetoothHFDevice.sendBIA(true, true, true, true);

        super.onResume();
    }

    public void onStop() {
        Log.d(TAG,"onStop()");
        super.onStop();
    }

    public void onBackPressed() {
        this.moveTaskToBack(true);
    }

    public void onDestroy() {
        Log.d(TAG,"onDestroy()");
        super.onDestroy();

        unregisterReceiver(mWbsStateIntentRec);

        if(isVibrating && (null != vibrator)) {
             vibrator.cancel();
             isVibrating = false;
         }
        /*Release all  the resources*/
        releaseResources();
        finish();
        Log.d(TAG,"finish()");
    }

    public void onNewIntent (Intent intent) {
        Log.d(TAG,"onNewIntent");
    }

    /**
     * This function is called to send notification to NotificationManager
     */
    private void showNotification(int icon) {
        Log.d(TAG,"showNotification: notification sent.." + mDeviceAddress);
        Notification notification = new Notification(icon, "", System.currentTimeMillis());
        Intent notificationIntent = new Intent(this, BRCMHfDeviceConnectedActivity.class);
        notificationIntent.putExtra(BRCMHfDeviceConstants.HF_DEVICE_NAME, mDeviceName);
        notificationIntent.putExtra(BRCMHfDeviceConstants.HF_DEVICE_ADDRESS,mDeviceAddress);
        //notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);
        notification.setLatestEventInfo(this, "Connected to",mDeviceName, contentIntent);
        mNotificationManager.notify(BRCMHfDeviceConstants.HF_NOTIFICATION_ID, notification);
    }

    /**
     *
     * This function is called when the activity gets destroyed
     *
     */
    private void releaseResources()
    {
        if(bluetoothHFDevice != null && mHfDeviceEventHandler != null) {
            bluetoothHFDevice.unregisterEventHandler();
            bluetoothHFDevice.closeProxy();
            bluetoothHFDevice = null;
            Log.d(TAG,"onStop: un registered event handler..");
        }
    }

    /**
     *
     * This function handles all the button press events
     *
     */
    public void onClick(View v) {
        Log.d(TAG,"onClick()");
        Button b = (Button)v;

        if(b == textButton2) {
            String optionString = textButton2.getText().toString();
            if(optionString.equals(dialStr)) {
                Log.d(TAG,"onClick: on clicked dial button....");
                dialedNumber = numberEntry.getText().toString();
                if(dialedNumber.length() != 0) {
                    if(!bluetoothHFDevice.dial(dialedNumber)) {                    //number dialed
                        Log.e(TAG,"onClick: dialing failed as device got disconnected..");
                        /*Dialog shows that Device is Disconnected*/
                        showDialog(BRCMHfDeviceConstants.HF_DEVICE_NOTCONNECTED_DIALOG_ID);
                    }
                    Log.d(TAG,"onClick: dialed number is:"+dialedNumber);
                    showNotification(R.drawable.stat_sys_audio_state_off);
                    displayState.setText("Dialing..");
                } else {
                    Log.e(TAG,"onClick: dialing failed as number is empty");
                }
            } else {
                Log.d(TAG,"onClick: on clicked answer button");
                if(!bluetoothHFDevice.answer()) {                              //answer call
                    Log.e(TAG,"onClick: answering call failed as device got disconnected..");
                    showDialog(BRCMHfDeviceConstants.HF_DEVICE_NOTCONNECTED_DIALOG_ID);
                }
            }
        }

        else if(b == redial) {
            Log.d(TAG,"onClick: on clicked redial button..");
           if(!bluetoothHFDevice.redial()) {                           //redial
                Log.e(TAG,"onClick: redialing failed as device got disconnected..");
                /*Dialog shows that Device is Disconnected*/
                showDialog(BRCMHfDeviceConstants.HF_DEVICE_NOTCONNECTED_DIALOG_ID);
            }
            displayState.setText("Dialing..");
            showNotification(R.drawable.stat_sys_audio_state_off);
        }
        else if(b == textButton) {
            String check = textButton.getText().toString();
            if(check.equals(handsfreeMode)) {
                Log.d(TAG,"onClick: on clicked Handsfree Mode button");
                if(bluetoothHFDevice.connectAudio()) {
                }
            } else {
                Log.d(TAG,"onClick: onclicked Private Mode button");
                if(bluetoothHFDevice.disconnectAudio()) {
                }
            }
        }
        else if(b == endCall) {                                      //end call
            Log.d(TAG,"onClick: on clicked endcall buttton ");
            BluetoothCallStateInfo callinfo = bluetoothHFDevice.getCallStateInfo(bluetoothDevice);
            // If there is a single call and the call is in held state send hold cmd
            if( callinfo.hasHeldCall() && !callinfo.isInCall()) {
                bluetoothHFDevice.hold(BluetoothHfDevice.HANGUP_HELD);
            } else if(!bluetoothHFDevice.hangup()){
                Log.e(TAG,"onClick: hanging up  failed as device got disconnected..");
                /*Dialog shows that Device is Disconnected*/
                showDialog(BRCMHfDeviceConstants.HF_DEVICE_NOTCONNECTED_DIALOG_ID);
            }
            Log.d(TAG,"onClick: Hanging up the call..");
            displayState.setText("Hanging up");
        } else if(b == callControl) {                                      //end call
            BluetoothCallStateInfo callinfo = bluetoothHFDevice.getCallStateInfo(bluetoothDevice);
            if (1 < callinfo.getNumOfCalls()) {
                showDialog(BRCMHfDeviceConstants.HF_DEVICE_MULTI_CALL_CONTROL_DIALOG_ID);
            } else if (callinfo.hasHeldCall() || callinfo.isInCall()){
                String check = callControl.getText().toString();
                if(check.equals(holdCall)) {
                    Log.d(TAG,"onClick: on clicked hold button");
                    bluetoothHFDevice.hold(BluetoothHfDevice.SWAP_CALLS);   //hold the call
                   callControl.setText(unholdCall);
                } else if(check.equals(unholdCall)) {
                    Log.d(TAG,"onClick: on clicked Unhold button");
                    bluetoothHFDevice.hold(BluetoothHfDevice.SWAP_CALLS);   //unhold the call
                    callControl.setText(holdCall);
                }
            }
        }
        else if(b == vrControl) {

            String check = vrControl.getText().toString();
            Log.d(TAG,"onClick: on clicked vrControl button.."+check);

            if(check.equals(vrOnStr)) {
                Log.d(TAG,"onClick: on clicked VR ON button");
                bluetoothHFDevice.startVoiceRecognition(bluetoothDevice);
            } else if(check.equals(vrOffStr)) {
                Log.d(TAG,"onClick: on clicked VR Off  button");
                bluetoothHFDevice.stopVoiceRecognition(bluetoothDevice);
            } else if (check.equals(sendKeyPress)) {
                Log.d(TAG,"onClick: on clicked sendKeyPress  button");
                bluetoothHFDevice.sendKeyPressedEvent();
            }

        }


    }

    /**
     *
     * This function is used to inflate menu from xml
     *
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.layout.hfdevice_atcmd_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.atcmd_menu:
            {
                showDialog(BRCMHfDeviceConstants.HF_DEVICE_AT_CMD_ENTRY_DIALOG_ID);
            }return true;
            case R.id.volume_menu:
            {
                if(volumeFlag == false)
                    showVolumeDialog(ag.getStreamVolume(AudioManager.STREAM_RING), false);
                else
                    showVolumeDialog(currentVolume, false);

            }return true;
            case R.id.phonebook_menu:
            {
                mMemType = BluetoothHfDevice.PHONE_MEM_TYPE_PHONEBOOK;
                showReadCountDialog();
            }return true;

            case R.id.missedcallmenu:
            {
                mMemType = BluetoothHfDevice.PHONE_MEM_TYPE_MISSED;
                showReadCountDialog();
            }return true;

            case R.id.dialedcall_menu:
            {
                mMemType = BluetoothHfDevice.PHONE_MEM_TYPE_LAST_DIALED;
                showReadCountDialog();
            }return true;

            case R.id.receivedcall_menu:
            {
                mMemType = BluetoothHfDevice.PHONE_MEM_TYPE_RECEIVED;
                showReadCountDialog();
            }return true;

            case R.id.sim_menu:
            {
                mMemType = BluetoothHfDevice.PHONE_MEM_TYPE_SIM;
                showReadCountDialog();
            }return true;

            default:
            {
                return super.onOptionsItemSelected(item);
            }
        }
    }

    private String mMemType = null;
    private void downloadPhonebook (String memType , int maxReadLimitCount) {

        try {

            FileOutputStream fOut = openFileOutput(mMemType+".txt",
                                Context.MODE_PRIVATE);

            fOut.close();

        } catch (Exception e) {
            Toast.makeText(BRCMHfDeviceConnectedActivity.this, e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }

       Log.d (TAG,"createNewFile="+mMemType);

        bluetoothHFDevice.readPhoneBookList(memType, maxReadLimitCount);
        mDownLoadCount = 0;
    }

    private void showReadCountDialog (){

        final AlertDialog.Builder alert = new AlertDialog.Builder(this);
        final EditText input = new EditText(this);
        alert.setView(input);
        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String value = input.getText().toString().trim();
                // Check if input is empty string
                if(!value.equals("")) {
                    // Check if input is a number
                    try {
                        downloadPhonebook(mMemType, Integer.parseInt(value));
                        Log.d(TAG,"maxReadLimitCount"+value);
                    }
                    catch(NumberFormatException e) {
                        Log.e(TAG, "Entered value must be an Integer");
                    }
                }
                else {
                    Log.e(TAG, "Entered value cannot be null");
                }
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                //downloadPhonebook(mMemType, -1);
                dialog.cancel();
            }
        });
        alert.show();

    }
    private synchronized void cancelVolumeDialog ()
    {
        if (mVolumeDialog != null)
        {
            Log.d(TAG, "Cancelling previous volume change dialog");
            mVolumeDialog.dismiss();
            if (mVolumeTimer != null)
                mVolumeTimer.cancel();
            mVolumeDialog = null;
            mVolumeTimer = null;
        }
    }
    private void showVolumeDialog(int volume, boolean autoClose) {
        Log.d(TAG,"Current Volume is "+volume);
         volumeFlag=true;

        /* cancel volume dialog, if already open */
        cancelVolumeDialog();

        mVolumeDialog = new Dialog(BRCMHfDeviceConnectedActivity.this);
        mVolumeBuilder = new AlertDialog.Builder(BRCMHfDeviceConnectedActivity.this);
        mVolumeBuilder.setTitle(volumeDialogTitle);
        mSeekBar=new SeekBar(BRCMHfDeviceConnectedActivity.this);
        mSeekBar.setMax(volumeMax);
        mSeekBar.setProgress(volume);

        mSeekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener (){
            public void onProgressChanged(SeekBar seekBar, int progress,
                               boolean fromUser) {
                ag.setStreamVolume(AudioManager.STREAM_RING,progress, AudioManager.FLAG_PLAY_SOUND);
                currentVolume=progress;

                if(!bluetoothHFDevice.setVolume(BluetoothHfDevice.VOLUME_TYPE_SPK, currentVolume)) {
                    Log.e(TAG,
                        "showVolumeDialog.onClick: command failed ");
                    showDialog(BRCMHfDeviceConstants.HF_DEVICE_VOLUME_CHANGE_FAILED_DIALOG_ID);
                }
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });



        mVolumeBuilder.setOnKeyListener(new DialogInterface.OnKeyListener()
        {
            public boolean onKey (DialogInterface dialog, int keyCode, KeyEvent event)
            {
                if ((mVolumeDialog == dialog) && (keyCode == KeyEvent.KEYCODE_BACK))
                {
                    Log.d(TAG,"Cancelling timer on back key pressed");
                    if (mVolumeTimer != null)
                        mVolumeTimer.cancel();
                    dialog.dismiss();
                    return true;
                }
                return false;
            }
        });

        mVolumeBuilder.setView(mSeekBar);
        mVolumeDialog = mVolumeBuilder.create();
        mVolumeDialog.show();

        if (autoClose)
        {
            mVolumeTimer = new Timer();
            mVolumeTimer.schedule(new TimerTask() {
                public void run() {
                    cancelVolumeDialog();
                }
            }, 2000);
        }

    }


    public AlertDialog callOptionsAlert = null;
    private AlertDialog.Builder callOptionsBuilder = null;

    protected Dialog onCreateDialog(int id) {

        Log.d(TAG,"onCreateDialog()");
        AlertDialog.Builder builder = null;

        switch(id) {
            case BRCMHfDeviceConstants.HF_DEVICE_NOTCONNECTED_DIALOG_ID :
            {
                Log.e(TAG,"onCreateDialog: Device Not Connected....");
                builder = new AlertDialog.Builder(this);
                builder.setTitle("Not Connected...");
                builder.setMessage("Press ok to continue");

                builder.setNegativeButton("Ok", new DialogInterface.OnClickListener() {
                       public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                            Message msg = Message.obtain();
                            msg.what = GUI_UPDATE_DEVICE_STATUS;
                            msg.arg1 = BluetoothHfDevice.STATE_DISCONNECTED;
                            msg.obj = bluetoothDevice;
                            viewUpdateHandler.sendMessage(msg);
                       }
                   });
            }   break;

            case BRCMHfDeviceConstants.HF_DEVICE_VOLUME_CHANGE_FAILED_DIALOG_ID :
            {
                Log.e(TAG,"onCreateDialog: set volume failed....");
                builder = new AlertDialog.Builder(this);
                builder.setTitle("Set volume failed...");
                builder.setMessage("Operation allowed only when audio is connected");

                builder.setNegativeButton("Ok", new DialogInterface.OnClickListener() {
                       public void onClick(DialogInterface dialog, int id) {
                            dialog.cancel();
                       }
                   });
            }   break;

            case BRCMHfDeviceConstants.HF_DEVICE_SERVICE_NOT_ENABLED:
            {
                builder = new AlertDialog.Builder(this);
                builder.setTitle("Service Not Enabled..");
                Log.e(TAG,"onCreateDialog: Service not enabled..");
                builder.setMessage("Press ok to exit");
                builder.setNegativeButton("ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        Message msg = Message.obtain();
                        msg.what = GUI_UPDATE_DEVICE_STATUS;
                        msg.arg1 = BluetoothHfDevice.STATE_DISCONNECTED;
                        msg.obj = bluetoothDevice;
                        viewUpdateHandler.sendMessage(msg);
                    }
                });
            }   break;
            case BRCMHfDeviceConstants.HF_DEVICE_CALL_WAITING_DIALOG_ID:
            {
                final CharSequence[] items = {"Reject waiting call",
                                                "Accept waiting release active",
                                                "Accept waiting hold active"};

                callOptionsBuilder = new AlertDialog.Builder(this);
                callOptionsBuilder.setTitle("Call waiting:"+waitingCallNumber);
                callOptionsBuilder.setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        Log.d(TAG,"clicked"+item);
                        switch(item){
                            //TODO: wait of call status change and handle error
                            case 0:
                            {
                                bluetoothHFDevice.hold(BluetoothHfDevice.HANGUP_HELD);
                                break;
                            }
                            case 1:
                            {
                                bluetoothHFDevice.hold(BluetoothHfDevice.HANGUP_ACTIVE_ACCEPT_HELD);
                                displayNumber.setText(waitingCallNumber);
                                break;
                            }
                            case 2:
                            {
                                bluetoothHFDevice.hold(BluetoothHfDevice.SWAP_CALLS);
                                displayNumber.setText(waitingCallNumber);
                                break;
                            }
                        }
                        callOptionsAlert.dismiss();
                    }
                });
                callOptionsAlert = callOptionsBuilder.create();
                callOptionsAlert.show();
                break;
            }
        case BRCMHfDeviceConstants.HF_DEVICE_MULTI_CALL_CONTROL_DIALOG_ID:
        {
            final CharSequence[] items = {"End all held calls",
                                            "End all active calls",
                                            "Swap calls",
                                            "Join calls"};

            callOptionsBuilder = new AlertDialog.Builder(this);
            callOptionsBuilder.setItems(items, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int item) {
                    Log.d(TAG,"clicked"+item);
                    switch(item){
                        //TODO: wait of call status change and handle error
                        case 0:
                        {
                            bluetoothHFDevice.hold(BluetoothHfDevice.HANGUP_HELD);
                            break;
                        }
                        case 1:
                        {
                            bluetoothHFDevice.hold(BluetoothHfDevice.HANGUP_ACTIVE_ACCEPT_HELD);
                            break;
                        }
                        case 2:
                        {
                            bluetoothHFDevice.hold(BluetoothHfDevice.SWAP_CALLS);
                            break;
                        }
                        case 3:
                        {
                            bluetoothHFDevice.hold(BluetoothHfDevice.CONFERENCE);
                            break;
                        }
                    }
                    callOptionsAlert.dismiss();
                }
            });
            callOptionsAlert = callOptionsBuilder.create();
            callOptionsAlert.show();
            break;
        }

            case BRCMHfDeviceConstants.HF_DEVICE_AT_CMD_ENTRY_DIALOG_ID:
            {
                LayoutInflater factory = LayoutInflater.from(this);
                View textEntryView = factory.inflate(R.layout.hfdevice_atcmd_dialog, null);
                builder = new AlertDialog.Builder(this);
                builder.setTitle("AT Command Input");
                Log.d(TAG,"onCreateDialog: AT Command input..");
                builder.setView(textEntryView);
                Spinner spinner = (Spinner) textEntryView.findViewById(R.id.spinner);
                final EditText cmdInputEditText = (EditText)textEntryView.
                                    findViewById(R.id.atcommand_input);
                ArrayAdapter<CharSequence> adapter =
                    ArrayAdapter.createFromResource(BRCMHfDeviceConnectedActivity.this,
                    R.array.vendor_cmds, android.R.layout.simple_spinner_item);
                Log.d(TAG,"onCreateDialog: Spinner object = "+adapter);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(adapter);

                class MyOnItemSelectedListener implements OnItemSelectedListener {
                    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                        String s = parent.getItemAtPosition(pos).toString();
                        Log.d(TAG,"MyOnItemSelectedListener.onItemSelected: selected item is "+s);
                        cmdInputEditText.setText(s.subSequence(0, s.length()),TextView.BufferType.EDITABLE);
                    }

                    public void onNothingSelected(AdapterView parent) {}
                }

                spinner.setOnItemSelectedListener(new MyOnItemSelectedListener());

                builder.setPositiveButton("ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        String cmd = cmdInputEditText.getText().toString();
                        cmd = cmd.trim();
                        if (cmd.contains("+VGM=")) {
                            String volumeStr = cmd.substring(cmd.indexOf("=")+1,
                                cmd.length());
                            int volume = 0;
                            if(null != volumeStr)
                                volume = Integer.parseInt(volumeStr);
                            Log.d(TAG,"Set volume Mic = "+volume);
                            if(!bluetoothHFDevice.setVolume(
                                BluetoothHfDevice.VOLUME_TYPE_MIC, volume)) {
                                Log.e(TAG,"SetVolume(VOLUME_TYPE_MIC) failed");
                            }
                            dialog.cancel();
                        } else if (cmd.contains("+VGS=")) {
                            String volumeStr = cmd.substring(cmd.indexOf("=")+1,
                                cmd.length());
                            int volume = 0;
                            if(null != volumeStr)
                                volume = Integer.parseInt(volumeStr);
                            Log.d(TAG,"Set volume Speaker = "+volume);
                            if(!bluetoothHFDevice.setVolume(
                                BluetoothHfDevice.VOLUME_TYPE_SPK, volume)) {
                                Log.e(TAG,"SetVolume(VOLUME_TYPE_SPK) failed");
                            }
                            dialog.cancel();
                        } else {
                            if(!bluetoothHFDevice.sendVendorCmd(cmd)) {
                                Log.e(TAG,
                                    "OnClickListener.onClick: sendVendorCmd() command failed");
                            }
                            Log.d(TAG,"DialogInterface.OnClickListener.onClick: Entered command.."+cmd);
                            dialog.cancel();
                        }
                    }
                });

                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
            }   break;
        }

        if(builder != null) {
            return builder.create();
        }

        return null;
    }

    /**
     *
     * Hanlder handles all the GUI events
     *
     */
    protected Handler viewUpdateHandler = new Handler() {
        public void handleMessage(Message msg) {

            Log.d(TAG,"handleMessage()");

            switch(msg.what) {
                case GUI_UPDATE_DEVICE_STATUS:
                {
                    Log.d(TAG,"Handler.handleMessage: updating device status");
                    if((null!= msg.obj) && (null != bluetoothDevice)
                        && bluetoothDevice.equals(msg.obj)) {
                        switch(msg.arg1) {
                            case BluetoothHfDevice.STATE_CONNECTING://update device state to connecting
                            {
                                displayState.setText("Connecting to "+mDeviceName);
                            }   break;
                            case BluetoothHfDevice.STATE_CONNECTED: //update device state to connected
                            {
                                /*Edit the shared preference as it is connected*/
                                Log.d(TAG,"Handler.handleMessage: connected to a device named "+
                                    bluetoothDevice.getName() + " address: " + mDeviceAddress +
                                    " bluetoothDevice.getAddress(): " + bluetoothDevice.getAddress());
                                editor = pref.edit();
                                editor.putBoolean(BRCMHfDeviceConstants.HF_DEVICE_CONNECTED, true);
                                editor.putString(BRCMHfDeviceConstants.HF_DEVICE_NAME,mDeviceName);
                                editor.putString(BRCMHfDeviceConstants.HF_DEVICE_ADDRESS,mDeviceAddress);
                                editor.commit();
                                showNotification(R.drawable.stat_sys_device_connected);
                                displayState.setText("Connected to "+mDeviceName);

                                Toast.makeText(BRCMHfDeviceConnectedActivity.this,
                                    "Device connected." ,
                                    Toast.LENGTH_LONG).show();
                            }   break;

                            case BluetoothHfDevice.STATE_DISCONNECTED://update device state to disconnected
                            {
                                Log.d(TAG,"Handler.handleMessage: Device disconnected..");
                                /*Edit the sharedpreference as the device is disconnected*/
                                editor = pref.edit();
                                editor.putBoolean(BRCMHfDeviceConstants.HF_DEVICE_CONNECTED, false);
                                editor.putString(BRCMHfDeviceConstants.HF_DEVICE_NAME,null);
                                editor.putString(BRCMHfDeviceConstants.HF_DEVICE_ADDRESS,null);
                                editor.commit();
                                Log.d(TAG,"Handler.handleMessage: Notification cancelled..");
                                /*Cancel Notification*/
                                mNotificationManager.
                                        cancel(BRCMHfDeviceConstants.HF_NOTIFICATION_ID);

                                displayState.setText("Disconnected..");
                                releaseResources();
                                finish();
                            }    break;

                        }
                    } else {
                        Log.e(TAG,"Handler.handleMessage: Mis-matched device..");
                    }
                } break;
                case GUI_UPDATE_CALL_STATUS:
                {
                    BluetoothCallStateInfo info = (BluetoothCallStateInfo)msg.obj;
                    updateViewWithCallStatus(info);
                    break;
                }
                case GUI_UPDATE_DEVICE_INDICATORS:    //Update status of Battery,Signal etc..
                {
                   updateIndicators((int[])msg.obj);

                }   break;

                case GUI_UPDATE_AUDIO_STATE:
                {
                        updateViewAudioState(msg.arg1);
                }   break;

                case GUI_UPDATE_VENDOR_AT_RSP:
                {
                    Log.d(TAG,"Handler.handleMessage: showing vendor at command response");
                    int status = msg.arg1;
                    String toastMsg;
                    toastMsg = "AT vendor rsp. status=" + status;
                    if (msg.obj != null)
                        toastMsg += " rsp=" + msg.obj.toString();
                    Toast.makeText(BRCMHfDeviceConnectedActivity.this,
                        toastMsg.subSequence(0,toastMsg.length()),Toast.LENGTH_LONG).show();
                } break;

                case GUI_UPDATE_CLCC_AT_RSP:
                {
                    synchronized (this) {
                        Log.d(TAG,"Handler.handleMessage: CLCC ui update");
                            updateUiWithCLCC( mCallList);
                    }
                } break;

                case GUI_UPDATE_VOLUME:
                {
                    Log.d(TAG,"Handler.handleMessage: showing volume changed");
                    int type = msg.arg1;
                    int volume = msg.arg2;
                    if (type == BluetoothHfDevice.VOLUME_TYPE_SPK) {
                        showVolumeDialog(volume, false);
                    }
                } break;
                case GUI_UPDATE_OPERATOR:
                {
                    String opName = (String) msg.obj;
                    if (null != opName)
                        operatorName.setText(opName);
                }   break;
                case GUI_UPDATE_SUBSCRIBER:
                {
                    String subcriberNum = (String) msg.obj;
                    if (null != subcriberNum)
                        subscriberNumber.setText(subcriberNum);
                }   break;
                case GUI_UPDATE_VR_STATE:
                {
                    {
                        updateViewVrState(msg.arg2);
                    }
                }   break;
                case GUI_UPDATE_WBS_STATE:
                {
                    {
                        updateViewWbsState(msg.arg1);
                    }
                }   break;
                case GUI_UPDATE_RING:
                {

                    vrControl.setText(sendKeyPress);
                    vrControl.setVisibility(View.VISIBLE);

                    if(!pm.isScreenOn()) {
                        Log.d(TAG,"Handler.handleMessage: Wake screen up"+pm.isScreenOn());
                        wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK|
                            PowerManager.ACQUIRE_CAUSES_WAKEUP,TAG);
                        wl.acquire(DEFAULT_SCREEN_TIMEOUT);
                    }
                    if(keyguardLock == null) {
                        keyguardLock = keyguardMgr.newKeyguardLock(TAG);
                        keyguardLock.disableKeyguard();
                        Log.d(TAG,"Handler.handleMessage: disabled the key guard..");
                    }

                    vibrator.vibrate(500);

                }   break;

                case GUI_UPDATE_IN_BAND_STATUS:
                {
                    updateViewInBandState(msg.arg1);
                } break;

                case GUI_UPDATE_PHONEBOOK_AT_RSP:
                {
                    synchronized (this) {
                        int status  = msg.arg1;
                        List<BluetoothPhoneBookInfo> phoneNumList = (List<BluetoothPhoneBookInfo>)msg.obj;
                        Log.d(TAG,"ASSERT PhoneNumList");
                        if (null != phoneNumList)
                            Log.d(TAG,"phoneNumList.size"+phoneNumList.size());

                        if (BluetoothHfDevice.PHONEBOOK_READ_COMPLETED == status) {

                            if (null != phoneNumList) {
                                updateDownloadFileContent(phoneNumList);
                                mDownLoadCount = mDownLoadCount + phoneNumList.size();
                                Log.d(TAG,"mDownLoadCount"+mDownLoadCount);
                            }

                            Toast.makeText(BRCMHfDeviceConnectedActivity.this,
                                "Dowload complete.Total count = "+ mDownLoadCount,
                                Toast.LENGTH_LONG).show();

                        } else if (BluetoothHfDevice.PHONEBOOK_READ_PROGRESS_UPDATE == status) {
                            if (null != phoneNumList) {
                                updateDownloadFileContent(phoneNumList);
                                mDownLoadCount = mDownLoadCount + phoneNumList.size();
                                Log.d(TAG,"mDownLoadCount"+mDownLoadCount);
                            }

                            Toast.makeText(BRCMHfDeviceConnectedActivity.this,
                                "Dowload progress.Dowload count = "+ mDownLoadCount,
                                Toast.LENGTH_LONG).show();

                        }

                    }

                }   break;
                default:
                {

                } break;
            }
        }
    };


    private void updateDownloadFileContent(List<BluetoothPhoneBookInfo> phoneNumList) {

        try {

            FileOutputStream fOut = openFileOutput(mMemType+".txt",
                                Context.MODE_APPEND);
            OutputStreamWriter myOutWriter =
                                    new OutputStreamWriter(fOut);

            String eol = System.getProperty("line.separator");

            for (int i = 0; i < phoneNumList.size(); i++) {
                BluetoothPhoneBookInfo tempPB = phoneNumList.get(i);
                Log.d(TAG,tempPB.getSummary());
                String contactDetails = ""+tempPB.getIndex()+","+
                                            tempPB.getContactNumber()+","+
                                            tempPB.getContactNumType()+","+
                                            tempPB.getContactInfo()+ eol;

                 myOutWriter.append(contactDetails);
            }
            myOutWriter.close();
            fOut.close();

        } catch (Exception e) {
            Toast.makeText(BRCMHfDeviceConnectedActivity.this, e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }

    }

    /**
     *
     * This function is used to update the indicators
     */

    private void updateIndicators(int[] indicators) {

        Log.d(TAG, "Service ="+indicators[0]+",Roam ="+indicators[1]+
            ",Signal ="+indicators[2]+",Batt chgv ="+indicators[3]);

        if(indicators[BluetoothHfDevice.INDICATOR_TYPE_SERVICE] !=
            BluetoothHfDevice.SERVICE_NOT_AVAILABLE) {

            if ((indicators[BluetoothHfDevice.INDICATOR_TYPE_ROAM] ==
                BluetoothHfDevice.SERVICE_TYPE_ROAMING)) {
                isRoaming = true;
            } else {
                isRoaming = false;
            }


            currentSignalStrength =
                indicators[BluetoothHfDevice.INDICATOR_TYPE_SIGNAL];
            if (!isRoaming) {
                // The image offset param indicates image index
                // referrred from stat_signal.xml
                // "0" offset sets the non- roaming images(1-5 index)
                updateSignalStatus(currentSignalStrength, 0);
            } else {
                // The image offset param indicates image index
                // referrred from stat_signal.xml
                // "5" offset sets the roaming images(6-10 index)
                updateSignalStatus(currentSignalStrength, 5);
            }

        } else {
            Log.d(TAG,"updateIndicators: no active service..");
            // "-1" offset sets the no signal image index (0 index)
            updateSignalStatus(indicators[BluetoothHfDevice.INDICATOR_TYPE_SIGNAL], -1);
        }

        if(currentBatteryCharge != indicators[BluetoothHfDevice.INDICATOR_TYPE_BATTERY]) {
            currentBatteryCharge = indicators[BluetoothHfDevice.INDICATOR_TYPE_BATTERY];
            updateBatteryStatus(currentBatteryCharge);
        }

    }


    /**
     *
     * This function is used to update the battery status
     */
    private void updateBatteryStatus(int status)
    {
        Log.d(TAG,"updateBatteryStatus: Updating signal status");
        switch(status) {
            case 0:
            {
                batteryStatus.setImageLevel(0);
            }    break;
            case 1:
            {
                batteryStatus.setImageLevel(1);
            }   break;

            case 2:
            {
                batteryStatus.setImageLevel(2);
            }  break;

            case 3:
            {
                batteryStatus.setImageLevel(3);
            } break;

            case 4:
            {
                batteryStatus.setImageLevel(4);
            } break;

            case 5:
            {
                batteryStatus.setImageLevel(5);
            } break;
        }
    }

    /**
     *
     * This function is used to update the signal status
     */
    private void updateSignalStatus(int status, int imageOffset)
    {
        Log.d(TAG,"updateSignalStatus: Updating signal status");

        switch(status) {
            case 0:
            {
                signalStatus.setImageLevel(1 + imageOffset);
            } break;

            case 1:
            {
                signalStatus.setImageLevel(2 + imageOffset);
            } break;

            case 2:
            {
                signalStatus.setImageLevel(3 + imageOffset);
            } break;

            case 3:
            {
                signalStatus.setImageLevel(4 + imageOffset);
            } break;

            case 4:
            {
                signalStatus.setImageLevel(4 + imageOffset);
            } break;
            case 5:
            {
                signalStatus.setImageLevel(5 + imageOffset);
            } break;
        }
    }

    /**
     * Internal class used to specify HF callback/callout events from
     * the BLuetoothHFDeviceService subsystem.
     */


    protected class HfDeviceEventHandler implements IBluetoothHfDeviceEventHandler {

        @Override
        public void onConnectionStateChange(int errCode,
                BluetoothDevice remoteDevice, int newState, int prevState) {
            Log.d(TAG,"onHfDeviceStateChange()");
            Message msg = Message.obtain();
            msg.what = GUI_UPDATE_DEVICE_STATUS;
            msg.arg1 = newState;
            if(remoteDevice == null) {
                msg.obj = bluetoothDevice;
            } else {
                msg.obj = remoteDevice;
            }
            viewUpdateHandler.sendMessage(msg);
        }

        @Override
        public void onAudioStateChange(int newState, int prevState) {
            Log.d(TAG,"onHfDeviceAudioStateChange()");
            Message msg = Message.obtain();
            msg.what = GUI_UPDATE_AUDIO_STATE;
            msg.arg1 = newState;
            viewUpdateHandler.sendMessage(msg);

        }

        @Override
        public void onIndicatorsUpdate(int[] indValue) {
            Log.d(TAG,"onHfDeviceIndicatorsUpdate()");
            Message msg = Message.obtain();
            msg.what = GUI_UPDATE_DEVICE_INDICATORS;
            msg.obj = indValue;
            viewUpdateHandler.sendMessage(msg);

        }

        @Override
        public  void  onCallStateChange(int status, int callSetupState, int numActive,
                int numHeld, String number, int addrType) {

            if (BluetoothHfDevice.NO_ERROR == status) {
                Log.d(TAG,"onHfDeviceCallStateChange()"+"callSetupState:"
                    +callSetupState+"numActive:"+numActive +"numHeld:"+numHeld+
                    "isPendingClcc:"+isPendingClcc);

                Message msg = Message.obtain();
                msg.what = GUI_UPDATE_CALL_STATUS;
                msg.obj  = new BluetoothCallStateInfo(numActive, callSetupState, numHeld, number);
                viewUpdateHandler.sendMessage(msg);

                if (!isPendingClcc) {
                    if(bluetoothHFDevice.getCLCC()){
                        isPendingClcc = true;
                        // wait for clcc to update the UI
                        return;
                    } else {
                        Log.e(TAG,"Get Clcc failed");
                    }
                }
            } else {
                Log.e(TAG,"Call status failed:"+status);

                Toast.makeText(BRCMHfDeviceConnectedActivity.this,
                    "Call operation failed." ,
                    Toast.LENGTH_LONG).show();
            }

        }

        public synchronized void  onCLCCRsp(int status, List<BluetoothClccInfo> clcc) {
            Log.e(TAG,"onCallListRsp"+status+clcc.size()+
                "isPendingClcc"+isPendingClcc);

            if (BluetoothHfDevice.NO_ERROR == status) {
                Message msg = Message.obtain();
                msg.what = GUI_UPDATE_CLCC_AT_RSP;
                mCallList = clcc;
                viewUpdateHandler.sendMessage(msg);
                Log.d(TAG,clcc.toString());
            }


            if (isPendingClcc) {
                if(bluetoothHFDevice.getCLCC()){
                    isPendingClcc = false;
                    // wait for clcc to update the UI
                    return;
                } else {
                    Log.e(TAG,"onCallListRsp Get Clcc failed");
                }
            }
        }


        @Override
        public void onVRStateChange(int status, int vrState) {
            // TODO Auto-generated method stub
            Message msg = Message.obtain();
            msg.what = GUI_UPDATE_VR_STATE;
            msg.arg1 = status;
            msg.arg2 = vrState;
            viewUpdateHandler.sendMessage(msg);

        }

        @Override
        public void onVolumeChange(int volType, int volume) {
            // TODO Auto-generated method stub
            Message msg = Message.obtain();
            msg.what = GUI_UPDATE_VOLUME;
            msg.arg1 = volType;
            msg.arg2 = volume;
            viewUpdateHandler.sendMessage(msg);

        }


        @Override
        public void onOperatorSelectionRsp(int status, int mode, String operatorName) {

            if (BluetoothHfDevice.NO_ERROR == status) {
                Log.d(TAG,"onOperatorSelectionRsp()"+operatorName);
                Message msg = Message.obtain();
                msg.what = GUI_UPDATE_OPERATOR;
                msg.arg1 = status;
                msg.arg2 = mode;
                msg.obj = operatorName;
                viewUpdateHandler.sendMessage(msg);
            } else {
                Log.e(TAG,"Error in onOperatorSelectionRsp"+status);
            }
        }

        @Override
        public void onSubscriberInfoRsp(int status, String number ,int addrType) {

            if (BluetoothHfDevice.NO_ERROR == status) {

                Log.d(TAG,"onSubscriberInfoRsp()"+number);

                Message msg = Message.obtain();
                msg.what = GUI_UPDATE_SUBSCRIBER;
                msg.arg1 = status;
                msg.obj = number;
                viewUpdateHandler.sendMessage(msg);
            } else {
                Log.e(TAG,"Error in onSubscriberInfoRsp"+status);
            }
        }

        @Override
        public void onExtendedErrorResult(int errorResultCode) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onVendorAtRsp(int status, String atRsp) {
            // TODO Auto-generated method stub
            Log.d(TAG,"onVendorAtRsp(): status="+ status + " atRsp=" + atRsp);
            Message msg = Message.obtain();
            msg.what = GUI_UPDATE_VENDOR_AT_RSP;
            msg.arg1 = status;
            msg.obj = atRsp;
            viewUpdateHandler.sendMessage(msg);
        }

        @Override
        public synchronized void onPhoneBookReadRsp(int status, List<BluetoothPhoneBookInfo> phoneNumList){
            Log.d(TAG,"onPhoneBookReadRsp"+status);


            Message msg = Message.obtain();
            msg.what = GUI_UPDATE_PHONEBOOK_AT_RSP;
            msg.arg1 = status;
            msg.obj = phoneNumList;
            //mPhoneNumList = phoneNumList;
            viewUpdateHandler.sendMessage(msg);
        }

        @Override
        public void onRingEvent() {
            Message msg = Message.obtain();
            msg.what = GUI_UPDATE_RING;
            viewUpdateHandler.sendMessage(msg);
        }

        @Override
        public void onInBandRingStatusEvent(int inBandRingStatus) {
            // TODO Auto-generated method stub
            Message msg = Message.obtain();
            msg.what = GUI_UPDATE_IN_BAND_STATUS;
            msg.arg1 = inBandRingStatus;
            viewUpdateHandler.sendMessage(msg);
        }

        @Override
        public void onBIAStatus(int status) {
            Log.d(TAG,"onBIAStatus status: "+status);
        }

    }

    private boolean isHSPConnection;
    private void updateUi() {
        if(bluetoothHFDevice.getConnectedDevices().size() == 0 ) {
            // No connected device available exit app
            Toast.makeText(getApplicationContext(),
                "Please Pair/Connect an Ag from Settings App and launch the app",
                Toast.LENGTH_LONG).show();
            finish();
            return;
        } else if(bluetoothHFDevice.getConnectionState(bluetoothDevice) !=
                BluetoothHfDevice.STATE_CONNECTING) {

            bluetoothDevice = bluetoothHFDevice.getConnectedDevices().get(0);
            mDeviceName = bluetoothDevice.getName();
            mDeviceAddress = bluetoothDevice.getAddress();
            displayState.setText("Connected to "+mDeviceName);


            Log.d(TAG,"updateUi: device already connected..");

             Log.d(TAG,"Handler.handleMessage: connected to a device named "+
             bluetoothDevice.getName() + " address: " +
             mDeviceAddress + " bluetoothDevice.getAddress(): " +
             bluetoothDevice.getAddress());

            updateIndicators(bluetoothHFDevice.
                getDeviceIndicators(bluetoothDevice));   // update indicators
            Log.d(TAG,"updateUi: updated indicators..");
            /*Edit the shared preference as it is connected*/
            Log.d(TAG,"updateUi: connected to a device named "+bluetoothDevice.getName());
            editor = pref.edit();
            editor.putBoolean(BRCMHfDeviceConstants.HF_DEVICE_CONNECTED, true);
            editor.putString(BRCMHfDeviceConstants.HF_DEVICE_NAME,mDeviceName);
            editor.putString(BRCMHfDeviceConstants.HF_DEVICE_ADDRESS,mDeviceAddress);
            editor.commit();
            Message msg = Message.obtain();

            bluetoothHFDevice.getPeerFeatures().printLog();
            bluetoothHFDevice.getLocalFeatures().printLog();

            isHSPConnection = bluetoothHFDevice.getPeerFeatures().isHSPConnection();

            if (!isHSPConnection) {
                msg.what = GUI_UPDATE_CALL_STATUS;
                msg.obj = bluetoothHFDevice.getCallStateInfo(bluetoothDevice);
                viewUpdateHandler.sendMessage(msg);

                Log.d(TAG,"updateUi: call state is:"+
                    bluetoothHFDevice.getCallStateInfo(bluetoothDevice).getCallSetupState());


                bluetoothHFDevice.getCLCC();

                bluetoothHFDevice.queryOperatorSelectionInfo();
                bluetoothHFDevice.querySubscriberInfo();

                updateViewVrState(mVrState);

                Message msg1 = Message.obtain();
                msg1.what = GUI_UPDATE_WBS_STATE;
                msg1.arg1 = pref.getInt(BRCMHfDeviceConstants.HF_DEVICE_WBS_STATE,0);
                viewUpdateHandler.sendMessage(msg1);

                Message msg2 = Message.obtain();
                msg2.what = GUI_UPDATE_IN_BAND_STATUS;
                if (bluetoothHFDevice.getPeerFeatures().isInBandToneSupported())
                    msg2.arg1 = BluetoothHfDevice.INBAND_STATE_ON;
                else
                    msg2.arg1 = BluetoothHfDevice.INBAND_STATE_OFF;
                viewUpdateHandler.sendMessage(msg2);

            }else {

                vrControl.setText(sendKeyPress);
                vrControl.setVisibility(View.VISIBLE);

            }

            updateViewAudioState(bluetoothHFDevice.getAudioState(bluetoothDevice));

        }
    }

    @Override
    public void onServiceConnected(int profile, BluetoothProfile proxy) {
        // TODO Auto-generated method stub
        Log.i(TAG,"onServiceConnected()");
        if(bluetoothHFDevice == null) {
            bluetoothHFDevice = (BluetoothHfDevice) proxy;
        }
        if(bluetoothHFDevice != null) {
            mHfDeviceEventHandler = new HfDeviceEventHandler();
            bluetoothHFDevice.registerEventHandler(mHfDeviceEventHandler);
            Log.d(TAG,"onProxyAvailable: registered event handler..");
        }

        updateUi();
    }

    @Override
    public void onServiceDisconnected(int profile) {
        // TODO Auto-generated method stub
        Log.i(TAG,"onServiceDisconnected()");
            bluetoothHFDevice = null;
        Toast.makeText(getApplicationContext(),
            "Hf app closes as HF service is disconnected ",
            Toast.LENGTH_LONG).show();
        if (null != mNotificationManager)
            mNotificationManager.
                cancel(BRCMHfDeviceConstants.HF_NOTIFICATION_ID);
        finish();
    }

    private void updateUiWithCLCC(List<BluetoothClccInfo> callList) {

        Log.d(TAG,"updateUiWithCLCC before updating"+callList.size());
        callItems.clear();

        for(int i = 0; i < callList.size(); i++) {
            BluetoothClccInfo callinfo = callList.get(i);
            callItems.add(getCallStatusMessage(callinfo));
        }
        callItems.notifyDataSetChanged();
        Log.d(TAG,"updateUiWithCLCC before updating"+callList.size());

        /*
        if (hasWaitingCall(callList))
             return;
        if (null != callOptionsAlert)
            Log.d(TAG,"isShowing="+callOptionsAlert.isShowing());
        if (null != callOptionsAlert) {
            callOptionsAlert.dismiss();
            callWaitingAlert = null;
            Log.d(TAG,"callOptionsAlert cancel try");
        }*/

    }


    private boolean hasWaitingCall(List<BluetoothClccInfo> callList) {
        BluetoothClccInfo callinfo = null;
        for(int i = 0; i < callList.size(); i++) {
             callinfo = callList.get(i);
                if (callinfo.getCallState() == BluetoothHfDevice.CALL_SETUP_STATE_WAITING)
                    return true;
        }

        return false;
    }

    private void updateViewOnCallWaiting (String number) {
        long[] pattern = { 200, 500};
        vibrator.vibrate(pattern, 0);
        isVibrating = true;
        waitingCallNumber = number;
        showDialog(BRCMHfDeviceConstants.HF_DEVICE_CALL_WAITING_DIALOG_ID);
    }



    private void updateViewOnIncoming () {
        long[] pattern = { 200, 500};
        vibrator.vibrate(pattern, 0);
        isVibrating = true;

        textButton2.setText(answerStr);
        textButton2.setVisibility(View.VISIBLE);

        redial.setVisibility(View.INVISIBLE);

        numberEntry.setVisibility(View.INVISIBLE);
        numberEntryText.setVisibility(View.INVISIBLE);

        endCall.setText(rejectStr);
        endCall.setVisibility(View.VISIBLE);

        callControl.setText("");
        callControl.setVisibility(View.INVISIBLE);

        textButton.setText("");
        textButton.setVisibility(View.INVISIBLE);

    }

    private String getCallStatusString(int callSetup) {
        String callStatus = "Inactive";

        switch(callSetup) {
            case BluetoothHfDevice.CALL_STATE_ACTIVE:
                callStatus = "Active";
                break;
            case BluetoothHfDevice.CALL_STATE_HELD:
                callStatus = "Held";
                break;
            case BluetoothHfDevice.CALL_SETUP_STATE_DIALING:
                callStatus = "Dialing";
                break;
            case BluetoothHfDevice.CALL_SETUP_STATE_WAITING:
                callStatus = "Waiting";
                break;
            case BluetoothHfDevice.CALL_SETUP_STATE_ALERTING:
                callStatus = "Alerting";
                break;
            case BluetoothHfDevice.CALL_SETUP_STATE_INCOMING:
                callStatus = "Incoming";
                break;
            default:
                break;
        }

        return callStatus;
    }
    private String getCallStatusMessage(BluetoothClccInfo callList) {
        String multiStatus = "";
        String callStatus = getCallStatusString(callList.getCallState());

        if (BluetoothHfDevice.CALL_MPTY_TYPE_MULTI
            == callList.getCallMultiPartyType())
            multiStatus = "(conference)";

        return callList.getCallNumber()+"("+callStatus+")"+multiStatus;
    }

    private ArrayList<String> getCallList(List<BluetoothClccInfo> callList) {
        ArrayList<String> arrCallList = new ArrayList<String>();

        for(int i = 0; i < callList.size(); i++) {
            BluetoothClccInfo callinfo = callList.get(i);
                arrCallList.add(getCallStatusMessage(callinfo));
        }

        return arrCallList;
    }

    private boolean isPhoneOnHook(BluetoothCallStateInfo callInfo) {
        return ((callInfo.getNumActiveCall() == 0) &&
            (callInfo.getNumHeldCall() == 0) &&
            (callInfo.getCallSetupState() == BluetoothHfDevice.CALL_SETUP_STATE_IDLE));
    }

    private boolean isCallSetupInProgress(BluetoothCallStateInfo callInfo) {
        return (callInfo.getCallSetupState() != BluetoothHfDevice.CALL_SETUP_STATE_IDLE);
    }

    private void updateViewOnActiveCall(BluetoothCallStateInfo callInfo) {
        int numActive = callInfo.getNumActiveCall();
        int numHeld = callInfo.getNumHeldCall();
        int callSetup = callInfo.getCallSetupState();

        displayState.setText("Call Active ");

        textButton2.setText(dialStr);
        textButton2.setVisibility(View.VISIBLE);

        redial.setVisibility(View.VISIBLE);

        numberEntry.setVisibility(View.VISIBLE);
        numberEntryText.setVisibility(View.VISIBLE);

        endCall.setText(endCallStr);
        endCall.setVisibility(View.VISIBLE);


        callControl.setVisibility(View.VISIBLE);

        //When there is only active call
        if ((0 != numActive) && (0 == numHeld)) {
            callControl.setText(holdCall);
        }

        //When there is only held call
        if ((0 == numActive) && (0 != numHeld)) {
            callControl.setText(unholdCall);
            displayState.setText("Held call ");
        }

        //When there is more than one call
        if ((0 != numActive) && (0 != numHeld)) {
            callControl.setText(multiCallControl);
        }

    }

    private void resetCallStateVariable() {

        incomingCallNumber = null;
        waitingCallNumber = null;
        dialedNumber = null;

    }

    private void updateViewOnPhoneHook() {

        textButton2.setText(dialStr);
        textButton2.setVisibility(View.VISIBLE);

        redial.setVisibility(View.VISIBLE);

        numberEntry.setVisibility(View.VISIBLE);
        numberEntryText.setVisibility(View.VISIBLE);

        endCall.setText("");
        endCall.setVisibility(View.INVISIBLE);

        callControl.setText("");
        callControl.setVisibility(View.INVISIBLE);

        displayState.setText("Connected to "+mDeviceName);
        displayNumber.setText("");

        updateViewAudioState(bluetoothHFDevice.getAudioState(bluetoothDevice));
        resetCallStateVariable();


    }

    private void updateViewOnCallProgress(BluetoothCallStateInfo callInfo) {
        int numActive = callInfo.getNumActiveCall();
        int numHeld = callInfo.getNumHeldCall();
        int callSetup = callInfo.getCallSetupState();
        String number = callInfo.getPhoneNumber();
        String callStatus = "";

        if(!pm.isScreenOn()) {
            Log.d(TAG,"Handler.handleMessage: Wake screen up"+pm.isScreenOn());
            wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK|
                PowerManager.ACQUIRE_CAUSES_WAKEUP,TAG);
            wl.acquire(DEFAULT_SCREEN_TIMEOUT);
        }
        if(keyguardLock == null) {
            keyguardLock = keyguardMgr.newKeyguardLock(TAG);
            keyguardLock.disableKeyguard();
            Log.d(TAG,"Handler.handleMessage: disabled the key guard..");
        }

        switch(callSetup) {
            case BluetoothHfDevice.CALL_SETUP_STATE_DIALING:
                callStatus = "Dialing..";
                displayNumber.setText(dialedNumber);
                displayState.setText(callStatus);
                endCall.setText(endCallStr);
                endCall.setVisibility(View.VISIBLE);
                break;
            case BluetoothHfDevice.CALL_SETUP_STATE_WAITING:
                if (callState == BluetoothHfDevice.CALL_SETUP_STATE_WAITING)
                    return;//already in waiting state
                Log.d(TAG,"CALL_SETUP_STATE_WAITING");
                updateViewOnCallWaiting(number);
                break;
            case BluetoothHfDevice.CALL_SETUP_STATE_ALERTING:
                callStatus = "Alerting..";
                displayState.setText(callStatus);
                endCall.setText(endCallStr);
                endCall.setVisibility(View.VISIBLE);
                break;
            case BluetoothHfDevice.CALL_SETUP_STATE_INCOMING:
                if (null == number) {// number not available
                    updateViewOnIncoming();
                }
                else { // when number is availabe
                    displayNumber.setText(number);
                    incomingCallNumber = number;
                }
                callStatus = "Incoming";
                displayState.setText(callStatus);
                break;
            default:
                break;
        }


    }

    private void updateViewWbsState (int wbsState) {
        switch(wbsState) {
             case BluetoothHfDevice.WBS_NONE :
             {
                 wbsStatus.setText(wbsOffStr);
             } break;
             case BluetoothHfDevice.WBS_YES :
             {
                 wbsStatus.setText(wbsOnStr);
             } break;
             default :
                 break;
         }

        wbsStatus.setVisibility(View.VISIBLE);

        pref = PreferenceManager.
           getDefaultSharedPreferences(BRCMHfDeviceConnectedActivity.this);
        pref.edit().putInt(BRCMHfDeviceConstants.HF_DEVICE_WBS_STATE,wbsState);

    }

    private void updateViewInBandState (int inBandRingStatus) {
        switch(inBandRingStatus) {
             case BluetoothHfDevice.INBAND_STATE_OFF:
             {
                 inBandStatus.setText(inBandOffStr);
             } break;
             case BluetoothHfDevice.INBAND_STATE_ON :
             {
                 inBandStatus.setText(inBandOnStr);
             } break;
             default :
                 break;
         }

        inBandStatus.setVisibility(View.VISIBLE);
        String toastString = "";
        if (0 == inBandRingStatus) {
            toastString = "InBand:"+ "Disabled";
        } else {
            toastString = "InBand :"+ "Enabled";
        }

        Toast.makeText(this,
            toastString,
            Toast.LENGTH_LONG).show();

    }

    private void updateViewVrState (int vrState) {
        switch(vrState) {
             case BluetoothHfDevice.VR_STATE_INACTIVE :
             {
                 vrControl.setText(vrOnStr);
             } break;
             case BluetoothHfDevice.VR_STATE_ACTIVE :
             {
                 vrControl.setText(vrOffStr);
             } break;
             default :
                 break;
         }

        mVrState = vrState;
        BluetoothCallStateInfo callInfo = bluetoothHFDevice.getCallStateInfo(bluetoothDevice);
        if ((isCallSetupInProgress(callInfo)) || (0 != callInfo.getNumOfCalls()))
            vrControl.setVisibility(View.INVISIBLE);
        else
            vrControl.setVisibility(View.VISIBLE);

        }
    private void updateViewAudioState (int audioState) {

        Log.d(TAG, "audioState"+audioState);
        switch(audioState) {
             case BluetoothHfDevice.STATE_AUDIO_CONNECTED :
             {
                 textButton.setText(privacyMode);
                 showNotification(R.drawable.stat_sys_audio_state_on);
             } break;
             case BluetoothHfDevice.STATE_AUDIO_DISCONNECTED :
             {
                 textButton.setText(handsfreeMode);
                 showNotification(R.drawable.stat_sys_audio_state_off);
             } break;
             default :
                 break;
         }

        if(((bluetoothHFDevice.getCallStateInfo(bluetoothDevice).getCallSetupState())
               == BluetoothHfDevice.CALL_SETUP_STATE_IDLE))
        {
            textButton.setVisibility(View.VISIBLE);
        }
    }

    private void updateViewWithCallStatus(BluetoothCallStateInfo callInfo) {

        int numActive = callInfo.getNumActiveCall();
        int numHeld = callInfo.getNumHeldCall();
        int callSetup = callInfo.getCallSetupState();

        Log.d(TAG,"TestApp"+"numActive"+numActive+"callSetup"+callSetup+"numHeld"+numHeld);

        updateViewVrState(mVrState);

        //When a call setup is in progress show the status
        if (isCallSetupInProgress(callInfo)) {
            updateViewOnCallProgress(callInfo);
            callState = callSetup;
            return;
        }

        callState = callSetup;
        if (null != callOptionsAlert)
            Log.d(TAG,"isShowing="+callOptionsAlert.isShowing());
        if (null != callOptionsAlert) {
            callOptionsAlert.dismiss();
            callOptionsAlert = null;
            Log.d(TAG,"callOptionsAlert cancel try");
        }


        // Cancell if alerting
        if(isVibrating && (null != vibrator)) {
             vibrator.cancel();
             isVibrating = false;
         }

        //When there is no active calls
        if (isPhoneOnHook(callInfo)) {
            updateViewOnPhoneHook();
            return;
        }

        //When there is  active call
        updateViewOnActiveCall(callInfo);

        //Update the audio state
        updateViewAudioState(bluetoothHFDevice.getAudioState(bluetoothDevice));

    }
}

