/* Copyright 2009-2013 Broadcom Corporation
 **
 ** This program is the proprietary software of Broadcom Corporation and/or its
 ** licensors, and may only be used, duplicated, modified or distributed
 ** pursuant to the terms and conditions of a separate, written license
 ** agreement executed between you and Broadcom (an "Authorized License").
 ** Except as set forth in an Authorized License, Broadcom grants no license
 ** (express or implied), right to use, or waiver of any kind with respect to
 ** the Software, and Broadcom expressly reserves all rights in and to the
 ** Software and all intellectual property rights therein.
 ** IF YOU HAVE NO AUTHORIZED LICENSE, THEN YOU HAVE NO RIGHT TO USE THIS
 ** SOFTWARE IN ANY WAY, AND SHOULD IMMEDIATELY NOTIFY BROADCOM AND DISCONTINUE
 ** ALL USE OF THE SOFTWARE.
 **
 ** Except as expressly set forth in the Authorized License,
 **
 ** 1.     This program, including its structure, sequence and organization,
 **        constitutes the valuable trade secrets of Broadcom, and you shall
 **        use all reasonable efforts to protect the confidentiality thereof,
 **        and to use this information only in connection with your use of
 **        Broadcom integrated circuit products.
 **
 ** 2.     TO THE MAXIMUM EXTENT PERMITTED BY LAW, THE SOFTWARE IS PROVIDED
 **        "AS IS" AND WITH ALL FAULTS AND BROADCOM MAKES NO PROMISES,
 **        REPRESENTATIONS OR WARRANTIES, EITHER EXPRESS, IMPLIED, STATUTORY,
 **        OR OTHERWISE, WITH RESPECT TO THE SOFTWARE.  BROADCOM SPECIFICALLY
 **        DISCLAIMS ANY AND ALL IMPLIED WARRANTIES OF TITLE, MERCHANTABILITY,
 **        NONINFRINGEMENT, FITNESS FOR A PARTICULAR PURPOSE, LACK OF VIRUSES,
 **        ACCURACY OR COMPLETENESS, QUIET ENJOYMENT, QUIET POSSESSION OR
 **        CORRESPONDENCE TO DESCRIPTION. YOU ASSUME THE ENTIRE RISK ARISING OUT
 **        OF USE OR PERFORMANCE OF THE SOFTWARE.
 **
 ** 3.     TO THE MAXIMUM EXTENT PERMITTED BY LAW, IN NO EVENT SHALL BROADCOM OR
 **        ITS LICENSORS BE LIABLE FOR
 **        (i)   CONSEQUENTIAL, INCIDENTAL, SPECIAL, INDIRECT, OR EXEMPLARY
 **              DAMAGES WHATSOEVER ARISING OUT OF OR IN ANY WAY RELATING TO
 **              YOUR USE OF OR INABILITY TO USE THE SOFTWARE EVEN IF BROADCOM
 **              HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES; OR
 **        (ii)  ANY AMOUNT IN EXCESS OF THE AMOUNT ACTUALLY PAID FOR THE
 **              SOFTWARE ITSELF OR U.S. $1, WHICHEVER IS GREATER. THESE
 **              LIMITATIONS SHALL APPLY NOTWITHSTANDING ANY FAILURE OF
 **              ESSENTIAL PURPOSE OF ANY LIMITED REMEDY.
 */
 package com.broadcom.bt.app.hiddevice;

import java.io.IOException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothDevicePicker;
import android.bluetooth.BluetoothProfile;

import com.broadcom.bt.service.hidd.HidDevice;
import com.broadcom.bt.service.hidd.IBluetoothHidDeviceCallback;
import com.broadcom.bt.service.hidd.SDPRecord;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnDismissListener;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;


public class HIDDeviceActivity extends Activity implements OnCancelListener, OnDismissListener {
    /** Called when the activity is first created. */

    private static final String TAG = "HIDDeviceActivity";

    private HIDViewTouchEventHandler mHIDViewTouchEventHandler;
    private HIDDeviceView mHIDDeviceView;

    private static final boolean DEBUG = true;

    private static final int REQUEST_DISCOVERABLE_BT = 300;

    private static final int GUI_UPDATE_MSG_ON_ENABLE = 0;
    private static final int GUI_UPDATE_MSG_ON_CONNECTED = 1;
    private static final int GUI_UPDATE_MSG_ON_CONNECTION_ERROR = 2;
    private static final int GUI_UPDATE_MSG_ON_DISCONNECTED = 3;
    private static final int GUI_UPDATE_MSG_ON_DISCONNECT_ERROR = 4;
    private static final int GUI_UPDATE_MSG_ON_DISABLE = 5;
    private static final int GUI_UPDATE_MSG_ON_DISABLE_ERROR = 6;
    private static final int GUI_UPDATE_MSG_ON_VIRTUAL_UNPLUG = 7;
    private static final int GUI_UPDATE_MSG_ON_ENABLE_ERROR = 8;
    private static final int GUI_UPDATE_MSG_ON_DISCOVERABLE_TOUT = 9;


    private static final int GUI_UPDATE_MSG_DEVICE_ALREADY_CONNECTED = 99;
    private static final int GUI_UPDATE_MSG_DEVICE_ALREADY_DISCONNECTED=101;
    private static final int GUI_UPDATE_MSG_WAITING_FOR_CONNECTION = 100;

    private static final int DEVICE_CLASS = 0x5c0;

    private HidDevice mHIDDevice;
    private SDPRecord mSDPRecord;
    private BluetoothDevice mHIDHost;
    private BluetoothDevice mLastConnectedHidHost;
    private ProgressDialog mWaitingProgressDialog;
    private AlertDialog mErrorDialog;
    private KeyMapping mKeyMapInstance= KeyMapping.getInstance();

    private boolean mInitAndConnect =false;

    // These callbacks run on the main thread.
    private BluetoothProfile.ServiceListener mBluetoothProfileServiceListener =
                new BluetoothProfile.ServiceListener() {

        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            Log.d(TAG,"Bluetooth service connected");
            mHIDDevice = (HidDevice) proxy;
            mHIDDevice.setCallback(mHIDeviceCallback);
            enable();
            createSdpRecord();
        }

        public void onServiceDisconnected(int profile) {
            Log.d(TAG,"Bluetooth service disconnected");
            mHIDDevice.finish();
            HidDevice.closeProfileProxy(mHIDDevice);
            mHIDDevice = null;
            viewUpdateHandler.sendEmptyMessage(GUI_UPDATE_MSG_ON_DISABLE);
        }
    };

    private final IBluetoothHidDeviceCallback mHIDeviceCallback = new
        IBluetoothHidDeviceCallback.Stub() {

        public void onConnected(BluetoothDevice hidHost, boolean isDeviceInitiated) {
            mHIDState = HIDState.STATE_CONNECTED;
            viewUpdateHandler.removeMessages(GUI_UPDATE_MSG_ON_DISCOVERABLE_TOUT);
            Log.d(TAG, "onConnected(" + hidHost + " , " + isDeviceInitiated + ")");
            mLastConnectedHidHost = hidHost;
            Message msg = Message.obtain();
            msg.what = GUI_UPDATE_MSG_ON_CONNECTED;
            msg.obj = hidHost;
            msg.arg1 = isDeviceInitiated == true ? 1 : 0;
            viewUpdateHandler.sendMessage(msg);

        }

        public void onConnectError(BluetoothDevice hidHost, boolean isDeviceInitiated,
            int errorCode) {

            Log.d(TAG, "onConnectError(" + hidHost + " , " + isDeviceInitiated + ")");
            viewUpdateHandler.removeMessages(GUI_UPDATE_MSG_ON_DISCOVERABLE_TOUT);

            Message msg = Message.obtain();
            msg.what = GUI_UPDATE_MSG_ON_CONNECTION_ERROR;
            msg.arg1 = isDeviceInitiated == true ? 1 : 0;
            msg.arg2 = errorCode;
            msg.obj = (BluetoothDevice) hidHost;
            viewUpdateHandler.sendMessage(msg);

        }

        public void onDisconnected(BluetoothDevice hidHost, boolean isDeviceInitiated) {

            Log.d(TAG, "onDisconnected(" + hidHost + " , " + isDeviceInitiated + ")");
            mHIDState = HIDState.STATE_DISCONNECTED;

            Message msg = Message.obtain();
            msg.what = GUI_UPDATE_MSG_ON_DISCONNECTED;
            msg.arg1 = isDeviceInitiated == true ? 1 : 0;
            msg.obj = (BluetoothDevice) hidHost;

            viewUpdateHandler.sendMessage(msg);
        }

        public void onDisconnectError(BluetoothDevice hidHost, boolean isDeviceInitiated,
            int errorCode) {

            Log.d(TAG, "onDisconnectionError(" + hidHost + " , " + isDeviceInitiated + ")");

            Message msg = Message.obtain();
            msg.what = GUI_UPDATE_MSG_ON_DISCONNECT_ERROR;
            msg.arg1 = isDeviceInitiated == true ? 1 : 0;
            msg.arg2 = errorCode;
            msg.obj = (BluetoothDevice) hidHost;

            viewUpdateHandler.sendMessage(msg);
        }

        public void onReceiveReport(byte[] reportData) {

            Log.d(TAG, "onReceiveReport(" + reportData + ")");

        }

        public void onEnable() {
            mHIDState = HIDState.STATE_WAITING_FOR_HOST;
            Log.d(TAG, "onEnable()");
            Message msg = Message.obtain();
            msg.what = GUI_UPDATE_MSG_ON_ENABLE;
            viewUpdateHandler.sendMessage(msg);
        }

        public void onEnableError(int errCode) {

            Log.d(TAG, "onEnableError(" + errCode + ")");
            viewUpdateHandler.removeMessages(GUI_UPDATE_MSG_ON_DISCOVERABLE_TOUT);
            Message msg = Message.obtain();
            msg.what = GUI_UPDATE_MSG_ON_ENABLE_ERROR;
            viewUpdateHandler.sendMessage(msg);
        }

        public void onDisable() {
            mHIDState = HIDState.STATE_HID_DISABLE;

            Log.d(TAG, "onDisable()");

            Message msg = Message.obtain();
            msg.what = GUI_UPDATE_MSG_ON_DISABLE;
            viewUpdateHandler.sendMessage(msg);

        }

        public void onDisableError(int errCode) {

            Log.d(TAG, "onDisableError(" + errCode + ")");

            Message msg = Message.obtain();
            msg.what = GUI_UPDATE_MSG_ON_DISABLE_ERROR;
            msg.arg2 = errCode;
            viewUpdateHandler.sendMessage(msg);
        }

        public void onVirtualUnplug(BluetoothDevice hidHost, boolean isDeviceInitiated) {

            Log.d(TAG, "onVirtualUnplug(" + isDeviceInitiated + ")");
            Message msg = Message.obtain();
            msg.what = GUI_UPDATE_MSG_ON_VIRTUAL_UNPLUG;
            viewUpdateHandler.sendMessage(msg);
        }

    };

    private BroadcastReceiver mDiscoverabilityReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG,"OnReceive()");
            String action = intent.getAction();

            if (BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)) {
                int scanMode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, -1);
                if(scanMode > 0 && scanMode !=
                    BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                    viewUpdateHandler.removeMessages(GUI_UPDATE_MSG_ON_DISCOVERABLE_TOUT);
                    viewUpdateHandler.sendEmptyMessage(GUI_UPDATE_MSG_ON_DISCOVERABLE_TOUT);
                }
            }
        }
    };

    private int mLastX= 0xffff, mLastY= 0xffff, mCurrentX= 0xffff, mCurrentY = 0xffff;
    private int mScrollLastX= 0xffff, mScrollLastY= 0xffff,
        mScrollCurrentX= 0xffff, mScrollCurrentY = 0xffff;

    enum HIDState {
        UNKNOWN, STATE_HID_INIT, STATE_WAITING_FOR_HOST, STATE_HID_DISABLE, STATE_CONNECTED,
            STATE_DISCONNECTED
    };
    private HIDState mHIDState = HIDState.UNKNOWN;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

        mHIDViewTouchEventHandler = new HIDViewTouchEventHandler();
        /* Create the visual element controller and pass event handler. */
        mHIDDeviceView = new HIDDeviceView(this, mHIDViewTouchEventHandler,    metrics);

        mHIDDeviceView.loadViewBasedOnResolution(metrics.widthPixels, metrics.heightPixels);

        mHIDHost = getIntent().getParcelableExtra(HIDConstants.EXTRA_DEVICE);

        if(mHIDHost==null)
            initHIDConnectionOnly();
        else
            initAndConnectHIDHost();

        /* Set the application to use this custom view system. */
        setContentView(mHIDDeviceView.getView());
        if(BluetoothAdapter.getDefaultAdapter().getScanMode() ==
            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE &&
            BluetoothAdapter.getDefaultAdapter().getDiscoverableTimeout() != 0)
            viewUpdateHandler.sendEmptyMessageDelayed(GUI_UPDATE_MSG_ON_DISCOVERABLE_TOUT,
                REQUEST_DISCOVERABLE_BT*1000);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    protected void onResume() {
        Log.d(TAG, "onResume"+mLastConnectedHidHost);
        registerReceiver(mDiscoverabilityReceiver,new IntentFilter(
            BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));
        super.onResume();
    }

    protected void onPause() {
        Log.d(TAG, "onPasue");
        unregisterReceiver(mDiscoverabilityReceiver);
        super.onPause();
    }

    protected void onDestroy() {
        Log.d(TAG, "onDestroy");

        if (mHIDDevice!=null)
        {
            mHIDDevice.clearCallback();
            mHIDDevice.finish();
            HidDevice.closeProfileProxy(mHIDDevice);
            mHIDDevice = null;
        }
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.d(TAG, "OnConfigurationChanged: Orientation:" + newConfig.orientation+
            ", keyboardHidden:"+newConfig.keyboardHidden);
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        InputMethodManager man = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        /* Create the visual element controller and pass event handler. */
        mHIDDeviceView = new HIDDeviceView(this, mHIDViewTouchEventHandler,    metrics);

        switch (newConfig.orientation) {
            case Configuration.ORIENTATION_LANDSCAPE:
                mHIDDeviceView.loadViewBasedOnResolution(metrics.widthPixels, metrics.heightPixels);
                break;
            case Configuration.ORIENTATION_PORTRAIT:
            default:
                mHIDDeviceView.loadViewBasedOnResolution(metrics.widthPixels, metrics.heightPixels);
                break;
        }
        setContentView(mHIDDeviceView.getView());
        if(newConfig.keyboardHidden == Configuration.KEYBOARDHIDDEN_NO) {
            mHIDViewTouchEventHandler.launchKeyboard();
        }
        super.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        Log.d(TAG, "OnSaveInstanceState");
        // TODO Auto-generated method stub
        super.onSaveInstanceState(outState);
    }

    private void initHIDConnectionOnly() {
        Log.d(TAG, "initHIDConnectionOnly");

        mHIDState = HIDState.STATE_HID_INIT;
        HidDevice.getProfileProxy(this.getApplicationContext(), mBluetoothProfileServiceListener);
        mInitAndConnect =false;
    }

    private void initAndConnectHIDHost() {
        // TODO Auto-generated method stub
        Log.d(TAG, "initAndConnectHIDHost");
        mHIDState = HIDState.STATE_HID_INIT;

        HidDevice.getProfileProxy(this.getApplicationContext(), mBluetoothProfileServiceListener);
        mInitAndConnect =true;
    }


    private void showDialog() {
        Log.d(TAG, "showDialog()");
        if (mWaitingProgressDialog == null || !mWaitingProgressDialog.isShowing()) {
            // Display dialog waiting for remote connection
            showDialog(GUI_UPDATE_MSG_WAITING_FOR_CONNECTION);
        }
    }

    // Display dialog waiting for remote connection
    private void waitForHIDHostConnection() {
        runOnUiThread(new Runnable() {
            public void run() {
                showDialog();
            }
        });
    }

    protected Dialog onCreateDialog(int id) {
        Log.d(TAG,"OnCreateDialog id:" + id);
        mErrorDialog=null;

        switch (id) {
        case GUI_UPDATE_MSG_WAITING_FOR_CONNECTION:
            mWaitingProgressDialog = new ProgressDialog(this);
            mWaitingProgressDialog.setMessage("HIDDevice app is waiting for the connect");
            mWaitingProgressDialog.setIndeterminate(true);
            mWaitingProgressDialog.setCancelable(true);
            mWaitingProgressDialog.setOnCancelListener(this);
            mWaitingProgressDialog.setOnDismissListener(this);
            mWaitingProgressDialog.setCanceledOnTouchOutside(false);
            break;

        case GUI_UPDATE_MSG_ON_CONNECTION_ERROR:
            mErrorDialog= createErrorDialog("HID Device","There is an Error in connection "+
                "establishment with the Hid Host");
            mErrorDialog.setButton2("Exit", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface arg0, int arg1) {
                    finish();
                }
            });
            mErrorDialog.setButton("Enter CONNECTABLE mode", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface arg0, int arg1) {
                    disable();
                }
            });
            break;

        case GUI_UPDATE_MSG_ON_DISCONNECTED:

            mErrorDialog= createErrorDialog("HID Device", "HID device app connection "+
                "disconnected with remote side");
            mErrorDialog.setButton("Exit", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface arg0, int arg1) {
                    disable();
                }
            });
            break;

        case GUI_UPDATE_MSG_ON_ENABLE_ERROR:
            mErrorDialog= createErrorDialog("HID Device","There is an Error while enabling "+
                "HID Device");
            mErrorDialog.setButton("Exit", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface arg0, int arg1) {
                    finish();
                }
            });
            break;

        case GUI_UPDATE_MSG_ON_DISABLE_ERROR:
            mErrorDialog= createErrorDialog("HID Device","There is an Error while disabling "+
                "HID Device");
            mErrorDialog.setButton("Exit", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface arg0, int arg1) {
                    finish();
                }
            });
            break;

        case GUI_UPDATE_MSG_DEVICE_ALREADY_CONNECTED:
            mErrorDialog= createErrorDialog("HID Device", "Hid device already connected");
            mErrorDialog.setButton("Ok", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface arg0, int arg1) {

                }
            });
            break;

        case GUI_UPDATE_MSG_ON_VIRTUAL_UNPLUG:
            mErrorDialog= createErrorDialog("HID Device", "Hid device virtual unplugged");
            mErrorDialog.setButton("Ok", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface arg0, int arg1) {
                }
            });

            break;
        case GUI_UPDATE_MSG_DEVICE_ALREADY_DISCONNECTED:

            mErrorDialog= createErrorDialog("HID Device", "Hid device already disconnected");
            mErrorDialog.setButton("Ok", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface arg0, int arg1) {

                }
            });
            break;

        case GUI_UPDATE_MSG_ON_DISCOVERABLE_TOUT:

            mErrorDialog= createErrorDialog("HID Device", "Discoverability timeout reached.");
            mErrorDialog.setButton("Re-Enable", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface arg0, int arg1) {
                    createSdpRecord();
                    waitForHIDHostConnection();
                }
            });
            mErrorDialog.setButton2("Exit", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface arg0, int arg1) {
                    finish();
                }
            });
            break;

        default:
            mWaitingProgressDialog = null;
            mErrorDialog=null;
            break;
        }

        return (mErrorDialog == null)?mWaitingProgressDialog:mErrorDialog;
    }

    public void dismissDialog() {
        if (mWaitingProgressDialog != null && mWaitingProgressDialog.isShowing()) {
            mWaitingProgressDialog.dismiss();
            mWaitingProgressDialog = null;
        }
        if(mErrorDialog != null && mErrorDialog.isShowing()) {
            mErrorDialog.dismiss();
        }
    }

    public void onDismiss(DialogInterface dialog) {
        Log.e(TAG, "onDismiss() for "+dialog);

    }

    public void onCancel(DialogInterface dialog) {
        Log.e(TAG, "onCancel() for "+dialog);
        disable();
    }

    private AlertDialog createErrorDialog(String title, String message)
    {
        AlertDialog errorDialog = new AlertDialog.Builder(this).create();
        errorDialog.setTitle(title);
        errorDialog.setMessage(message);
        errorDialog.setCancelable(true);
        errorDialog.setOnCancelListener(this);
        errorDialog.setOnDismissListener(this);
        return errorDialog;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {

        Log.d(TAG, "OnKeyDown , KeyCode: " + keyCode + "KeyEvent: " + event);

        Log.d(TAG, "META_SHIFT_ON"+ event.isShiftPressed()+ "META_ALT_ON" + event.isAltPressed() );

        if(mHIDDevice == null)
            return super.onKeyDown(keyCode, event);

        if (true == mKeyMapInstance.isAndroidSpecificKey(keyCode))
            return super.onKeyUp(keyCode, event);;

        try {

            if (event.getMetaState() == KeyEvent.META_SHIFT_ON     && keyCode !=
                KeyEvent.KEYCODE_SHIFT_LEFT) {
                mHIDDevice.sendReport(HidDevice.HID_DEVICE_TYPE_KEYBOARD,
                    mKeyMapInstance.createKeyDownReport(0x02, mKeyMapInstance.keyMap
                    .get(keyCode)));
                Log.d(TAG, "META_SHIFT_ON"+ event.isShiftPressed());
            }
            else if (event.getMetaState() == KeyEvent.META_ALT_ON)
            {
                if (event.getMetaState() == KeyEvent.META_SHIFT_ON && keyCode !=
                    KeyEvent.KEYCODE_ALT_LEFT) {
                    mHIDDevice.sendReport(HidDevice.HID_DEVICE_TYPE_KEYBOARD,
                        mKeyMapInstance.createKeyDownReport(0x01, mKeyMapInstance.keyMap
                        .get(keyCode)));
                    Log.d(TAG,  "META_ALT_ON" + event.isAltPressed() );
                }
            }
            else{
                if (mKeyMapInstance.isHandleAndroidExceptionsReq(keyCode)){
                    mHIDDevice.sendReport(HidDevice.HID_DEVICE_TYPE_KEYBOARD,
                        mKeyMapInstance.createKeyDownReport(0,mKeyMapInstance.keyMap
                        .get(KeyEvent.KEYCODE_SHIFT_LEFT)));
                    mHIDDevice.sendReport(HidDevice.HID_DEVICE_TYPE_KEYBOARD,
                        mKeyMapInstance.createKeyDownReport(0x02, mKeyMapInstance.keyMap
                        .get(keyCode)));
                    Log.d(TAG,"android exception keys");
                }else{
                    mHIDDevice.sendReport(HidDevice.HID_DEVICE_TYPE_KEYBOARD,
                        mKeyMapInstance.createKeyDownReport(0,mKeyMapInstance.keyMap
                        .get(keyCode)));
                    Log.d(TAG,  "0 report" );
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error Unable to send report due to : " + e.toString());
            e.printStackTrace();
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // TODO Auto-generated method stub
        Log.d(TAG, "OnKeyUp , KeyCode: " + keyCode + "KeyEvent: " + event);

        if(mHIDDevice == null)
            return super.onKeyUp(keyCode, event);

        if (true == mKeyMapInstance.isAndroidSpecificKey(keyCode))
            return super.onKeyUp(keyCode, event);;

        try {

            if((event.getMetaState() != KeyEvent.META_SHIFT_ON) /*&&
                    (!mKeyMapInstance.isHandleAndroidExceptionsReq(keyCode))*/){
                mHIDDevice.sendReport(HidDevice.HID_DEVICE_TYPE_KEYBOARD,
                    mKeyMapInstance.createKeyUpReport());
            }


        } catch (Exception e) {
            Log.e(TAG, "Error Unable to send report due to :  "+ e.toString());

            e.printStackTrace();
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU)
        {
            // do your stuff here
            if (Configuration.ORIENTATION_PORTRAIT == getResources().getConfiguration()
                .orientation) {
                mHIDViewTouchEventHandler.launchKeyboard();
                return true;
            } else {
                return true;
            }
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyMultiple(int keyCode, int repeatCount, KeyEvent event) {
        // TODO Auto-generated method stub
        return super.onKeyMultiple(keyCode, repeatCount, event);
    }

    private void createSdpRecord() {
        Log.d(TAG, "creating  SDP record....");
        mSDPRecord = new SDPRecord();
        try {
            mSDPRecord.setResource(this, R.raw.combo_sdp);
            mHIDDevice.setSDPRecord(mSDPRecord);
            mHIDDevice.setDeviceClass(DEVICE_CLASS);
            Intent intent = mHIDDevice.startDiscoverable(REQUEST_DISCOVERABLE_BT);
            if(intent != null && BluetoothAdapter.getDefaultAdapter().getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE && !mInitAndConnect) {
                startActivity(intent);
                viewUpdateHandler.removeMessages(GUI_UPDATE_MSG_ON_DISCOVERABLE_TOUT);
                viewUpdateHandler.sendEmptyMessageDelayed(GUI_UPDATE_MSG_ON_DISCOVERABLE_TOUT,
                    REQUEST_DISCOVERABLE_BT*1000);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error is creating Sdp record:" + e.toString());
        }
    }

    private void enable() {
        try {
            Log.d(TAG, "Enabling HID Device....");
            mHIDDevice.enable(-1);
        } catch (Throwable t) {
            Log.e(TAG, "Unable to enable device", t);
        }

    }

    private void disable() {
        try {
            Log.d(TAG, "Disable HID Device...."+mHIDDevice+", isenabled="+
                (mHIDDevice==null?"NULL":(mHIDDevice.isEnabled()?"true":"false")));
            if(mHIDDevice !=null && mHIDDevice.isEnabled())
                mHIDDevice.disable();
        } catch (Throwable t) {
            Log.e(TAG, "Unable to disable device", t);
        }
    }

    /**
     * Connect with the HID Host
     *
     * @param device
     */
    private void connect(BluetoothDevice device) {
        try {

            Log.d(TAG, "Connecting to HID host " + device);
            viewUpdateHandler.removeMessages(GUI_UPDATE_MSG_ON_DISCOVERABLE_TOUT);
            waitForHIDHostConnection();
            boolean sucess = mHIDDevice.connect(device, 5000);

            if(!sucess)
            {
                showDialog(GUI_UPDATE_MSG_DEVICE_ALREADY_CONNECTED);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Unable to connect to HID host "+device.getName()+"("+device.getAddress()+
                ")", t);
        }
    }


    /**
     * Disconnect with the connected HID host
     *
     */
    private void disconnect() {
        try {
            Log.d(TAG, "disconnecting with HID host ..." );
            boolean sucess=false;

            if(mHIDDevice !=null && mHIDDevice.isConnected())
                sucess = mHIDDevice.disconnect(5000);


            if(!sucess)
            {
                showDialog(GUI_UPDATE_MSG_DEVICE_ALREADY_DISCONNECTED);
            }

        } catch (Throwable t) {
            Log.e(TAG, "Unable to disconnect to HID host ", t);
        }
    }


    /**
     * Internal class used to specify handling of GUI touch events from the
     * HIDDeviceView class.
     */
    protected class HIDViewTouchEventHandler implements
            IHIDViewTouchEventHandler {

        public void handleButtonEvent(int buttonId, int event) {

            if (DEBUG)
                Log.d(TAG, "onClick(): " + buttonId);

            if (HIDConstants.BUTTON_EVENT_UP == event) {
                /* For each button, perform the requested action. */
                switch (buttonId) {
                case HIDConstants.KEYBOARD_CLICK:
                    launchKeyboard();
                    break;
                case HIDConstants.VIRTUAL_UNPLUG_CLICK:
                    Log.d(TAG,"VIRTUAL_UNPLUG_CLICK");
                    try {
                        mHIDDevice.virtualUnplug(mHIDHost);
                    } catch (Exception e) {
                        Log.e(TAG, "Error Unable to virtual unplug  " + e.toString());
                        e.printStackTrace();
                    }
                   break;
                case HIDConstants.LEFT_CLICK:
                    try {
                        mHIDDevice.sendReport(HidDevice.HID_DEVICE_TYPE_MOUSE,
                            mKeyMapInstance.createMouseReport(HIDConstants.LEFT_CLICK,0,0,0));
                        mHIDDevice.sendReport(HidDevice.HID_DEVICE_TYPE_MOUSE,
                            mKeyMapInstance.createMouseReport(0,0,0,0));
                    } catch (Exception e) {
                        Log.e(TAG, "Error Unable to send report due to : " + e.toString());
                        e.printStackTrace();
                    }
                    break;
                case HIDConstants.RIGHT_CLICK:
                    try {
                        mHIDDevice.sendReport(HidDevice.HID_DEVICE_TYPE_MOUSE,
                            mKeyMapInstance.createMouseReport(HIDConstants.RIGHT_CLICK,0,0,0));
                        mHIDDevice.sendReport(HidDevice.HID_DEVICE_TYPE_MOUSE,
                            mKeyMapInstance.createMouseReport(0,0,0,0));
                    } catch (Exception e) {
                        Log.e(TAG, "Error Unable to send report due to : " + e.toString());
                        e.printStackTrace();
                    }
                    break;
                case HIDConstants.MIDDLE_CLICK:

                    Log.d(TAG,"MIDDLE_CLICK");
                    try {
                        mHIDDevice.sendReport(HidDevice.HID_DEVICE_TYPE_MOUSE,
                            mKeyMapInstance.createMouseReport(HIDConstants.MIDDLE_CLICK,0,0,0));
                        mHIDDevice.sendReport(HidDevice.HID_DEVICE_TYPE_MOUSE,
                            mKeyMapInstance.createMouseReport(0,0,0,0));
                    } catch (Exception e) {
                        Log.e(TAG, "Error Unable to send report due to : " + e.toString());
                        e.printStackTrace();
                    }
                    break;

                default:
                    break;
                }
            }
        }

        public void launchKeyboard() {
            InputMethodManager man = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (DEBUG) Log.d(TAG, "launchKeyboard() getCurrentFocus: " +getCurrentFocus());
            man.toggleSoftInputFromWindow(mHIDDeviceView.getKeyBaordView()
                    .getWindowToken(), InputMethodManager.SHOW_FORCED,
                    InputMethodManager.HIDE_IMPLICIT_ONLY);
        }

        public boolean handleTouchEvent(MotionEvent event) {
            return onTouchPadEvent(event);
        }

        private boolean onTouchPadEvent(MotionEvent event) {

            if (DEBUG)
                Log.d(TAG, "onTouchPadEvent(): " + event.getAction());

            switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN:
                Log.d(TAG,"DOWN getX()="+event.getX()+"getY()"+event.getY());
                mLastX = (int)event.getX();
                mLastY = (int)event.getY();

                return true;

            case MotionEvent.ACTION_UP:
                Log.d(TAG,"UP getX()="+event.getX()+"getY()"+event.getY());
                return true;


            case MotionEvent.ACTION_MOVE:

                Log.d(TAG,"MOVE getX()="+event.getX()+"getY()"+event.getY());

                mCurrentX = (int)event.getX();
                mCurrentY = (int)event.getY();

                int dx= mCurrentX-mLastX;
                int dy= mCurrentY-mLastY;


//                dx = (dx>0)? 20: (dx==0)? 0:(-20);
//                dy = (dy>0)? 20: (dy==0)? 0:(-20);

                byte Bdx = (byte)dx;
                byte Bdy = (byte)dy;


                Log.d("Mouse after","Bdx="+ Bdx + " Bdy=" + Bdy);

//                try{
//                Thread.sleep(30);
//                }catch (Exception e) {
//                }
//
                try {
                    mHIDDevice.sendReport(HidDevice.HID_DEVICE_TYPE_MOUSE,
                        mKeyMapInstance.createMouseReport(0, dx,dy ,0));
                } catch (Exception e) {
                    Log.e(TAG, "Error Unable to send report due to : " + e.toString());
                    e.printStackTrace();
                }
                Log.d(TAG, " X: " + mCurrentX+ " Y: " + mCurrentY);
                Log.d(TAG, " DX: " + dx + " YY: " + dy);

                mLastX=mCurrentX;
                mLastY=mCurrentY;

                // Need to send the event from here only
                return true;
            default:
                break;
            }
            return false;
        }

        public boolean handleScrollTouchEvent(MotionEvent event) {





            switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN:
                Log.d(TAG,"scroll pad DOWN getX()="+event.getX()+"getY()"+event.getY());
                mScrollLastX = (int)event.getX();
                mScrollLastY = (int)event.getY();

                return true;

            case MotionEvent.ACTION_UP:
                Log.d(TAG,"Scroll pad UP getX()="+event.getX()+"getY()"+event.getY());
                int deltaY = mScrollLastY - (int)event.getY();
                try {
                    mHIDDevice.sendReport(HidDevice.HID_DEVICE_TYPE_MOUSE,
                        mKeyMapInstance.createMouseReport(0, 0,0 ,deltaY*1));
                } catch (Exception e) {
                    Log.e(TAG, "Error Unable to send report due to : " + e.toString());
                    e.printStackTrace();
                }
                return true;


            case MotionEvent.ACTION_MOVE:

                return true;
            default:
                break;
            }
            return false;
        }
    }

    public boolean listenerStarted = false;

    protected Handler viewUpdateHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {

            Log.d(TAG,"handleMessage : " + msg);

            switch (msg.what) {
                case GUI_UPDATE_MSG_ON_ENABLE:
                    if (mInitAndConnect)
                        connect(mHIDHost);
                    else{
                        /* When BTA Hid Device stack module is enabled, it
                         will be in listening mode only. No need for explicitly
                        setting LISTEN mode */
                        listenerStarted = false;

                        listenerStarted = true;
                        Log.d(TAG, "Listener started...waiting for connection....");
                        waitForHIDHostConnection();
                    }
                    break;
                case GUI_UPDATE_MSG_ON_CONNECTED:
                    listenerStarted = false;
                    mHIDHost = (BluetoothDevice) msg.obj;
                    HIDDevicePreferences.getInstance(getApplicationContext()).setCachedHidHost(
                        (BluetoothDevice) msg.obj);

                    dismissDialog();
                    break;

                case GUI_UPDATE_MSG_ON_CONNECTION_ERROR:
                    dismissDialog();
                    showDialog(GUI_UPDATE_MSG_ON_CONNECTION_ERROR);
                    break;

                case GUI_UPDATE_MSG_ON_DISCONNECTED:
                    dismissDialog();
                    showDialog(GUI_UPDATE_MSG_ON_DISCONNECTED);
                    break;

                case GUI_UPDATE_MSG_ON_DISCONNECT_ERROR:
                    break;

                case GUI_UPDATE_MSG_ON_DISABLE:
                    finish();
                    break;

                case GUI_UPDATE_MSG_ON_DISABLE_ERROR:
                    dismissDialog();
                    showDialog(GUI_UPDATE_MSG_ON_DISABLE_ERROR);
                    break;

                case GUI_UPDATE_MSG_ON_ENABLE_ERROR:
                    dismissDialog();
                    showDialog(GUI_UPDATE_MSG_ON_ENABLE_ERROR);
                    break;

                case GUI_UPDATE_MSG_ON_VIRTUAL_UNPLUG:
                    HIDDevicePreferences.getInstance(getApplicationContext())
                    .clearCachedHidHost();
                    dismissDialog();
                    showDialog(GUI_UPDATE_MSG_ON_VIRTUAL_UNPLUG);
                    break;

                case GUI_UPDATE_MSG_ON_DISCOVERABLE_TOUT:
                    dismissDialog();
                    try {
                        if(mHIDState != HIDState.STATE_CONNECTED)
                            showDialog(GUI_UPDATE_MSG_ON_DISCOVERABLE_TOUT);
                    } catch(Exception ee) {
                        Log.e(TAG, "Error displaying DISCOVERABLE_TIMEOUT dialog", ee);
                    }
                    break;

                default:
                    break;
            }
            super.handleMessage(msg);
        }

    };

    // make all the object null
    public void finish() {
        Log.d(TAG,"finish() mHIDDevice:"+mHIDDevice);

        if (mHIDDevice!=null)
        {
            mHIDDevice.clearCallback();
            mHIDDevice.finish();
            HidDevice.closeProfileProxy(mHIDDevice);
            mHIDDevice = null;
        }
        super.finish();
    }

}
