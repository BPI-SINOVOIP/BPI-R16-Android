
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

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

public class HIDLauncher extends Activity {

    private static final String TAG = "HIDLauncher";

    private static final int REQUEST_DISCOVERABLE_BT = 300;
    private static final int REQUEST_ENABLE_BT = 200;
    private static final int REQUEST_SELECT_HID_HOST = 400;

    private boolean isHidHostScreenAttempted;

    private BroadcastReceiver mDiscoverabilityReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG,"OnReceive()");
            String action = intent.getAction();

            if (BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)) {
                int scanMode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, -1);
                if (scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE) {
                    Intent i = new Intent(HIDLauncher.this, HIDDeviceActivity.class);
                    startActivity(i);
                    finish();
                }
            }
        }
    };

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG,"OnCreate()");
        SharedPreferences pref = this.getSharedPreferences(
                HIDConstants.PREF_NAME, Context.MODE_PRIVATE);

        BluetoothAdapter mAdapter = BluetoothAdapter.getDefaultAdapter();
        if (!mAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        else {
            reconnectOrEnableDiscovery();
        }
    }

    @Override
    protected void onPause() {
        unregisterReceiver(mDiscoverabilityReceiver);
        super.onPause();
    }


    @Override
    protected void onResume() {
        registerReceiver(mDiscoverabilityReceiver,new IntentFilter(
            BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));
        super.onResume();
    }


    public void reconnectOrEnableDiscovery() {
        BluetoothAdapter mAdapter = BluetoothAdapter.getDefaultAdapter();

        if (checkHIDHostAvilable() && !isHidHostScreenAttempted)
        {
            Intent i = new Intent(HIDLauncher.this, HIDHostListActivity.class);
            startActivityForResult(i,REQUEST_SELECT_HID_HOST);
        }

        else if(mAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
        {
            setDiscoverable(REQUEST_DISCOVERABLE_BT);
        }
        else
        {
            Intent i = new Intent(HIDLauncher.this, HIDDeviceActivity.class);
            startActivity(i);
            finish();
        }
    }


    private boolean checkHIDHostAvilable() {
        BluetoothDevice hidHost = HIDDevicePreferences.getInstance(getApplicationContext())
            .getCachedHidHost();
        if(hidHost == null)
            return false;
        else
            return true;
    }


    private void setDiscoverable(int requestDiscoverableBt) {
        Log.d(TAG,"Setting device in discoverable mode");
        BluetoothAdapter mAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
                REQUEST_DISCOVERABLE_BT);
            startActivityForResult(discoverableIntent,REQUEST_DISCOVERABLE_BT);
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "requestCode/resultCode = " + requestCode + "/" + resultCode);
        switch (requestCode) {
        case REQUEST_ENABLE_BT:
            onActivityResult_BluetoothEnable(requestCode, resultCode, data);
            break;
        case REQUEST_DISCOVERABLE_BT:
            onActivityResult_Discovery(requestCode, resultCode, data);
            break;
        case REQUEST_SELECT_HID_HOST:
            onActivityResult_HIDHost(requestCode, resultCode, data);
            break;
        }
    }

    private void onActivityResult_HIDHost(int requestCode, int resultCode,
            Intent data) {
        isHidHostScreenAttempted= true;
        if (resultCode == Activity.RESULT_CANCELED) {
            reconnectOrEnableDiscovery();
        } else {
            BluetoothDevice bluetoothDevice = data.getParcelableExtra(HIDConstants.EXTRA_DEVICE);
            Intent i = new Intent(this, HIDDeviceActivity.class);
            i.putExtra(HIDConstants.EXTRA_DEVICE, bluetoothDevice);
            startActivity(i);
            finish();
        }
    }


    private void onActivityResult_BluetoothEnable(int requestCode, int resultCode,
            Intent data) {
        if (resultCode == Activity.RESULT_CANCELED) {
            return;
        }

        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) {
            finish();
            return;
        }
        reconnectOrEnableDiscovery();
    }

    private void onActivityResult_Discovery(int requestCode, int resultCode,
            Intent data) {
        if (resultCode == Activity.RESULT_CANCELED) {
            finish();
        } else {
            Intent i = new Intent(this, HIDDeviceActivity.class);
            startActivity(i);
            finish();
        }
    }
}
