/******************************************************************************
 *
 *  Copyright (C) 2009-2013 Broadcom Corporation
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
 *         CORRESPONDENCE TO DESCRIPTION. YOU ASSUME THE ENTIRE RISK ARISING
 *         OUT OF USE OR PERFORMANCE OF THE SOFTWARE.
 *
 *  3.     TO THE MAXIMUM EXTENT PERMITTED BY LAW, IN NO EVENT SHALL BROADCOM
 *         OR ITS LICENSORS BE LIABLE FOR
 *         (i)   CONSEQUENTIAL, INCIDENTAL, SPECIAL, INDIRECT, OR EXEMPLARY
 *               DAMAGES WHATSOEVER ARISING OUT OF OR IN ANY WAY RELATING TO
 *               YOUR USE OF OR INABILITY TO USE THE SOFTWARE EVEN IF BROADCOM
 *               HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES; OR
 *         (ii)  ANY AMOUNT IN EXCESS OF THE AMOUNT ACTUALLY PAID FOR THE
 *               SOFTWARE ITSELF OR U.S. $1, WHICHEVER IS GREATER. THESE
 *               LIMITATIONS SHALL APPLY NOTWITHSTANDING ANY FAILURE OF
 *               ESSENTIAL PURPOSE OF ANY LIMITED REMEDY.
 *
 *****************************************************************************/

package com.broadcom.bt.pbap.pce;

import java.util.HashMap;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.broadcom.bt.pbap.BluetoothPbapClient;
import com.broadcom.bt.pbap.AppParamValue;

public class PbapClientTestApp extends Application implements OnSharedPreferenceChangeListener {

    private static final String TAG = "PceclientTestApp";

    // Application states for FSM
    // App is initialized, but unaware of remote server
    static final int PCE_STATE_INIT = 0;

    // App is initialized and have discovered remote server
    static final int PCE_STATE_READY = 1;

    // Client is connected to remote pbap server
    static final int PCE_STATE_CONNECTED = 2;

    // Client state is in error, we will disconnect internally
    static final int PCE_STATE_ERROR = 3;

    // Client is disconnected from remote pbap server, can reconnect
    static final int PCE_STATE_DISCONNECTED = 4;

    static final int PCE_STATE_BUSY = 5;

    BluetoothAdapter mBluetoothAdapter;

    private static final String PREFS_KEY_SERVER_ADDRESS = "remote_server_address";

    private static final String PREFS_KEY_SERVER_NAME = "remote_server_name";

    private static final String PREFS_KEY_PCE_STATE = "pce_app_state";

    private static final String PREFS_KEY_PBAP_PATH = "pbap_folder_path";

    private static final String PREFS_KEY_PBAP_AUTH = "pbap_auth";

    SharedPreferences prefs;

    Editor prefsEdit;

    @Override
    public void onCreate() {
        // TODO Auto-generated method stub
        super.onCreate();
        Log.d(TAG, "onCreate");

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
        prefsEdit = prefs.edit();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        //if (key.equals("PREFS_KEY_VCARD_VER")) {
        // pceNotificationServerEnabled =
        // prefs.getBoolean("pceNotificationServerEnabled", false);
        //}
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    public String getRemoteServerAddr() {
        String remoteServerAddr = prefs.getString(PREFS_KEY_SERVER_ADDRESS, null);
        Log.d(TAG, "remoteServerAddr ret: " + remoteServerAddr);
        return remoteServerAddr;
    }

    public void setRemoteServerAddr(String remoteServerAddr) {
        prefsEdit.putString(PREFS_KEY_SERVER_ADDRESS, remoteServerAddr);
        prefsEdit.commit();
    }

    public String getRemoteServerName() {
        String remoteServerName = prefs.getString(PREFS_KEY_SERVER_NAME, null);
        Log.d(TAG, "remoteServerName ret: " + remoteServerName);
        return remoteServerName;
    }

    public void setRemoteServerName(String remoteServerName) {
        prefsEdit.putString(PREFS_KEY_SERVER_NAME, remoteServerName);
        prefsEdit.commit();
    }

    public String getPceFolderPath() {
        String pceFolderPath = prefs.getString(PREFS_KEY_PBAP_PATH, BluetoothPbapClient.PB_PATH);
        Log.d(TAG, "getPceFolderPath ret: " + pceFolderPath);
        return pceFolderPath;
    }

    public void setPceFolderPath(String pceFolderPath) {
        prefsEdit.putString(PREFS_KEY_PBAP_PATH, pceFolderPath);
        prefsEdit.commit();
    }

    public int getApplicationState() {
        int appState = prefs.getInt(PREFS_KEY_PCE_STATE, PCE_STATE_INIT);
        Log.d(TAG, "getApplicationState ret: " + appState);
        return appState;
    }

    public void setApplicationState(int applicationState) {

        prefsEdit.putInt(PREFS_KEY_PCE_STATE, applicationState);
        prefsEdit.commit();

        switch (applicationState) {
            case PCE_STATE_INIT:
                Log.d(TAG, "ApplicationState: " + "Init");
                break;
            case PCE_STATE_CONNECTED:
                Log.d(TAG, "ApplicationState: " + "Connected");
                break;
            case PCE_STATE_DISCONNECTED:
                Log.d(TAG, "ApplicationState: " + "Disconnected");
                break;
            case PCE_STATE_ERROR:
                Log.d(TAG, "ApplicationState: " + "Error");
                break;
                // TBD - unused
            case PCE_STATE_READY:
                Log.d(TAG, "ApplicationState: " + "Ready");
                break;
            case PCE_STATE_BUSY:
                Log.d(TAG, "ApplicationState: " + "Busy");
                break;
            default:
                Log.d(TAG, "ApplicationState: " + "Unknown");
        }
    }
}
