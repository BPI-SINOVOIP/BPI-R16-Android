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

package com.broadcom.bt.app.avrcp;

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

import com.broadcom.bt.avrcp.BluetoothAvrcpController;


public class AvrcpBroadcastReceiver extends BroadcastReceiver {

    private static final String TAG = "AvrcpBroadcastReceiver";

    public static final String AVRCP_TG_NAME = "com.broadcom.bt.app.avrcp.targetname";
    public static final String AVRCP_TG_ADDRESS = "com.broadcom.bt.app.avrcp.targetaddress";
    public static final String AVRCP_CONNECTED = "com.broadcom.bt.app.avrcp.connected";
    public static final int AVRCP_NOTIFICATION_ID = -1000003;   //identifier for the notification

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
        state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);

        if (action.equals(BluetoothAvrcpController.ACTION_CONNECTION_STATE_CHANGED)) {
            switch(state) {
                case BluetoothProfile.STATE_CONNECTED:
                {
                    Log.d(TAG,"onReceive: AVRCP Controller connection ..");
                    sharedPref = PreferenceManager.getDefaultSharedPreferences(content);
                    editor = sharedPref.edit();
                    editor.putBoolean(AVRCP_CONNECTED, true);
                    editor.putString(AVRCP_TG_NAME, mDeviceName);
                    editor.putString(AVRCP_TG_ADDRESS, mDeviceAddress);
                    editor.commit();
                    showNotification(R.drawable.ic_launcher, content);
                }   break;

                case BluetoothProfile.STATE_DISCONNECTED:
                {
                    Log.d(TAG,"onReceive: AVRCP Controller disconnected ..");
                    mNotificationManager.cancel(AVRCP_NOTIFICATION_ID);
                    sharedPref = PreferenceManager.getDefaultSharedPreferences(content);
                    editor = sharedPref.edit();
                    editor.putBoolean(AVRCP_CONNECTED, false);
                    editor.putString(AVRCP_TG_NAME,null);
                    editor.putString(AVRCP_TG_ADDRESS,null);
                    editor.commit();
                }   break;
            }
        }
    }

   /**
     * This function is called to send notification to NotificationManager
     */
    private void showNotification(int icon, Context context) {
        Log.d(TAG,"showNotification: notification sent.." + mDeviceAddress);
        Notification notification = new Notification(icon, "", System.currentTimeMillis());
        Intent notificationIntent = new Intent(context, MediaPlayerActivity.class);
        notificationIntent.putExtra(AVRCP_TG_NAME, mDeviceName);
        notificationIntent.putExtra(AVRCP_TG_ADDRESS, mDeviceAddress);
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent,
                                            PendingIntent.FLAG_CANCEL_CURRENT);
        notification.setLatestEventInfo(context, "Connected to", mDeviceName, contentIntent);
        mNotificationManager.notify(AVRCP_NOTIFICATION_ID, notification);
    }
}
