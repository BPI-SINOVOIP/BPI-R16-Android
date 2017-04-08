/*******************************************************************************
 *
 *  Copyright (C) 2012 Broadcom Corporation
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

package com.broadcom.bt.app.settings;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import com.android.settings.bluetooth.LocalBluetoothProfileEx;
import com.broadcom.bt.service.sap.BluetoothSap;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import com.android.settings.R;

import java.util.List;

/**
 * SapProfile:  Provides access to SapService by calling into BluetoothSAP public APIs
 */
public final class SapProfile implements LocalBluetoothProfileEx {
    private static final String TAG = "SapProfile";
    private static boolean V = true;

    private BluetoothSap mService;
    private boolean mIsProfileReady;

    public static final String NAME = "SAP";

    // Order of this profile in device profiles list
    private static final int ORDINAL = 5;

    // These callbacks run on the main thread.
    private final class SapServiceListener
            implements BluetoothProfile.ServiceListener {

        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (V) Log.d(TAG,"Bluetooth service connected");
            mService = (BluetoothSap) proxy;
            mIsProfileReady=true;
        }

        public void onServiceDisconnected(int profile) {
            if (V) Log.d(TAG,"Bluetooth service disconnected");
            mIsProfileReady=false;
        }
    }

    public boolean isProfileReady() {
        return mIsProfileReady;
    }

    public SapProfile(Context context) {
        BluetoothSap sap = new BluetoothSap(context, new SapServiceListener());
    }

    public boolean isConnectable() {
        return true;
    }

    public boolean isAutoConnectable() {
        return false;
    }

    public boolean connect(BluetoothDevice device) {
   /* . Cannot connect from SAP Server. */
        return false;
    }

    public boolean disconnect(BluetoothDevice device) {
        if (mService == null) return false;
        return mService.disconnect(device);
    }

    public int getConnectionStatus(BluetoothDevice device) {
        if (mService == null) {
            return BluetoothProfile.STATE_DISCONNECTED;
        }
        List<BluetoothDevice> deviceList = mService.getConnectedDevices();

        return !deviceList.isEmpty() && deviceList.get(0).equals(device)
                ? mService.getConnectionState(device)
                : BluetoothProfile.STATE_DISCONNECTED;
    }

    public boolean isPreferred(BluetoothDevice device) {
        int state = getConnectionStatus(device);

        if (state == BluetoothProfile.STATE_CONNECTED)
            return true;

        return false;
    }

    public int getPreferred(BluetoothDevice device) {
       return -1;
    }

    public void setPreferred(BluetoothDevice device, boolean preferred) {
        // ignore: Can't connect from local. So Conn Preferences does not make sense.

    }

    public  String toString() {
        return NAME;
    }

    public int getOrdinal() {
        return ORDINAL;
    }

    public int getNameResource(BluetoothDevice device) {
        return R.string.bluetooth_profile_sap;
    }

    public int getSummaryResourceForDevice(BluetoothDevice device) {
        int state = getConnectionStatus(device);
        switch (state) {
            case BluetoothProfile.STATE_DISCONNECTED:
                return R.string.bluetooth_sap_profile_summary_use_for;

            case BluetoothProfile.STATE_CONNECTED:
                return R.string.bluetooth_sap_profile_summary_connected;

            default:
                return R.string.bluetooth_sap_profile_summary_use_for;
        }
    }

    public int getDrawableResource(BluetoothClass btClass) {
        // TBD: Find the right icon for SAP
        return R.drawable.ic_bt_cellphone;
    }

    protected void finalize() {
        if (V) Log.d(TAG, "finalize()");
        if (mService != null) {
            try {
                mService.close();
                mService = null;
            }catch (Throwable t) {
                Log.w(TAG, "Error cleaning up SAP proxy", t);
            }
        }
    }
}
