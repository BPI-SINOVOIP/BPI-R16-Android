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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.bluetooth.*;
import android.widget.Toast;



import com.broadcom.bt.hfdevice.BluetoothHfDevice;


public class BRCMHfDeviceBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "BRCMHfDeviceBroadcastReceiver";

    private static boolean isCallDisconnected = true;
    private String mDeviceName;
    private String mDeviceAddress;
    SharedPreferences.Editor editor;
    NotificationManager mNotificationManager;

    @Override
    public void onReceive(Context content, Intent intent) {
        Log.d(TAG,"onReceive()"+ intent.getAction());
        String action = intent.getAction();

        Intent in;
        int state;

        Bundle bundle = intent.getExtras();
        SharedPreferences sharedPref;
        mNotificationManager =
            (NotificationManager)content.getSystemService(content.NOTIFICATION_SERVICE);
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

        if (device!= null) {
            mDeviceName = device.getName();
            mDeviceAddress = device.getAddress();
        }
        state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE,-1);

        if (action.equals(BluetoothHfDevice.ACTION_CONNECTION_STATE_CHANGED)) {
            switch(state) {
                case BluetoothHfDevice.STATE_CONNECTED :
                {
                    Log.d(TAG,"onReceive: Hf device connection ..");
                    int localFeatures =
                            intent.getIntExtra(BluetoothHfDevice.EXTRA_LOCAL_FEATURES, 0);
                    int peerFeatures =
                            intent.getIntExtra(BluetoothHfDevice.EXTRA_PEER_FEATURES, 0);
                    boolean isHspConnection =
                        BluetoothHfDevice.getPeerFeatures(peerFeatures).isHSPConnection();
                    sharedPref = PreferenceManager.getDefaultSharedPreferences(content);
                    editor = sharedPref.edit();
                    editor.putBoolean(BRCMHfDeviceConstants.HF_DEVICE_CONNECTED, true);
                    editor.putString(BRCMHfDeviceConstants.HF_DEVICE_NAME,mDeviceName);
                    editor.putString(BRCMHfDeviceConstants.HF_DEVICE_ADDRESS,mDeviceAddress);
                    editor.putBoolean(
                        BRCMHfDeviceConstants.HF_DEVICE_IS_HSP_CONNECTION,isHspConnection);
                    editor.commit();
                    showNotification(R.drawable.stat_sys_device_connected, content);
                }   break;

                case BluetoothHfDevice.STATE_DISCONNECTED :
                {
                    Log.d(TAG,"onReceive: Hf device disconnected ..");
                    mNotificationManager.cancel(BRCMHfDeviceConstants.HF_NOTIFICATION_ID);
                    sharedPref = PreferenceManager.getDefaultSharedPreferences(content);
                    editor = sharedPref.edit();
                    editor.putBoolean(BRCMHfDeviceConstants.HF_DEVICE_CONNECTED, false);
                    editor.putString(BRCMHfDeviceConstants.HF_DEVICE_NAME,null);
                    editor.putString(BRCMHfDeviceConstants.HF_DEVICE_ADDRESS,null);
                    editor.putInt(BRCMHfDeviceConstants.HF_DEVICE_WBS_STATE, 0);
                    editor.commit();
                } break;
            }

        }else if (action.equals(BluetoothHfDevice.ACTION_CALL_STATE_CHANGED)) {
            switch(state) {
                case BluetoothHfDevice.CALL_SETUP_STATE_IDLE:
                {
                    isCallDisconnected = false;
                    showNotification(R.drawable.stat_sys_device_connected, content);
                } break;
                case BluetoothHfDevice.CALL_SETUP_STATE_INCOMING:
                {
                    Log.d(TAG,"onReceive: Incoming call received..");
                    isCallDisconnected = true;
                    in = new Intent();
                    in.setClass(content,BRCMHfDeviceConnectedActivity.class);
                    in.putExtra(BRCMHfDeviceConstants.HF_DEVICE_NAME, mDeviceName);
                    in.putExtra(BRCMHfDeviceConstants.HF_DEVICE_ADDRESS,mDeviceAddress);
                    in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    Log.d(TAG,"onReceive: Broadcast Reciever started an activity..");
                    content.startActivity(in);
                } break;
                default :
            }
        }else if (action.equals(BluetoothHfDevice.ACTION_WBS_STATE_CHANGED)) {
            Log.d(TAG,"WBS config:"+state);
            String toastString;
            sharedPref = PreferenceManager.getDefaultSharedPreferences(content);
            editor = sharedPref.edit();
            editor.putInt(BRCMHfDeviceConstants.HF_DEVICE_WBS_STATE, state);
            editor.commit();

            if (0 == state) {
                toastString = "WBS codec:"+ "Disabled";
            } else {
                toastString = "WBS codec:"+ "Enabled";
            }



            Toast.makeText(content,
                toastString,
                Toast.LENGTH_LONG).show();
        }else if (action.equals(BluetoothHfDevice.ACTION_RING_EVENT)) {
            Log.d(TAG,"onReceive: RING event ..");
            in = new Intent();
            in.setClass(content,BRCMHfDeviceConnectedActivity.class);
            in.putExtra(BRCMHfDeviceConstants.HF_DEVICE_NAME, mDeviceName);
            in.putExtra(BRCMHfDeviceConstants.HF_DEVICE_ADDRESS,mDeviceAddress);
            in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Log.d(TAG,"onReceive: Broadcast Reciever started an activity..");
            content.startActivity(in);
        }else if (action.equals(BluetoothHfDevice.ACTION_AUDIO_STATE_CHANGED)) {
            int audioState = intent.getIntExtra(BluetoothProfile.EXTRA_STATE,-1);
            Log.d(TAG,"onReceive: ACTION_AUDIO_STATE_CHANGED event audioState= " +audioState);

            if (audioState != BluetoothHfDevice.STATE_AUDIO_CONNECTED)
                return;

            sharedPref = PreferenceManager.getDefaultSharedPreferences(content);
            if (sharedPref.getBoolean(BRCMHfDeviceConstants.HF_DEVICE_IS_HSP_CONNECTION, false)) {
                in = new Intent();
                in.setClass(content,BRCMHfDeviceConnectedActivity.class);
                in.putExtra(BRCMHfDeviceConstants.HF_DEVICE_NAME, mDeviceName);
                in.putExtra(BRCMHfDeviceConstants.HF_DEVICE_ADDRESS,mDeviceAddress);
                in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                Log.d(TAG,"onReceive: Broadcast Reciever started an activity..");
                content.startActivity(in);
            }

        }




    }

   /**
     * This function is called to send notification to NotificationManager
     */
    private void showNotification(int icon, Context context) {
        Log.d(TAG,"showNotification: notification sent.." + mDeviceAddress);
        Notification notification = new Notification(icon, "", System.currentTimeMillis());
        Intent notificationIntent = new Intent(context, BRCMHfDeviceConnectedActivity.class);
        notificationIntent.putExtra(BRCMHfDeviceConstants.HF_DEVICE_NAME, mDeviceName);
        notificationIntent.putExtra(BRCMHfDeviceConstants.HF_DEVICE_ADDRESS,mDeviceAddress);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        notification.setLatestEventInfo(context, "Connected to",mDeviceName, contentIntent);
        mNotificationManager.notify(BRCMHfDeviceConstants.HF_NOTIFICATION_ID, notification);
    }
}
