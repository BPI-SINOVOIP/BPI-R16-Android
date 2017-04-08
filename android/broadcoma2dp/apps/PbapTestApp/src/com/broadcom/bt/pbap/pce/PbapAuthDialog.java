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
package com.broadcom.bt.pbap.pce;

import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Button;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.text.InputFilter.LengthFilter;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;

import com.broadcom.bt.pbap.BluetoothPbapClient;
import com.broadcom.bt.pbap.BluetoothAttributeMask;
import com.broadcom.bt.pbap.AppParamValue;
import com.broadcom.bt.pbap.IBluetoothPbapClientAuthenticator;
import com.broadcom.bt.pbap.IBluetoothPbapClientEventHandler;

/**
 * PbapActivity shows two dialogues: One for accepting incoming pbap request and
 * the other prompts the user to enter a session key for authentication with a
 * remote Bluetooth device.
 */
public class PbapAuthDialog extends AlertActivity implements DialogInterface.OnClickListener,
        Preference.OnPreferenceChangeListener, TextWatcher {
    private static final String TAG = "PbapAuthDialog";

    static final String ACTION_AUTH_CHAL = "com.broadcom.bt.pbap.client.AUTH_CHAL";
    static final String ACTION_AUTH = "com.broadcom.bt.pbap.client.AUTH";
    static final String ACTION_CANCEL_AUTH = "com.broadcom.bt.pbap.client.CANCEL_AUTH";
    static final String ACTION_AUTH_RESPONSE = "com.broadcom.bt.pbap.client.AUTH_RESPONSE";
    static final String EXTRA_AUTH_TYPE = "auth_type";
    static final String EXTRA_USERID_REQUIRED = "username_reqd";
    static final String EXTRA_DESCRIPTION = "description";
    static final String EXTRA_FULLACCESS = "fullaccess";
    static final String EXTRA_USERNAME = "username";
    static final String EXTRA_SESSION_KEY="sessionkey";

    static final int TYPE_AUTH_CHAL = 1;
    static final int TYPE_AUTH = 2;

    private static final int BLUETOOTH_OBEX_AUTHKEY_MAX_LENGTH = 16;

    //private static final String KEY_USER_TIMEOUT = "user_timeout";

    private int mAuthType;

    private View mView;

    private TextView messageView;

    private EditText mKeyView;

    private EditText mUsernameView;

    private String mSessionKey = "";

    private BluetoothDevice mDevice;
    private String mDescription;
    private boolean mIsUserIdRequired;
    private boolean mIsFullAccess;
    private String mUsername;

    private Button mOkButton;

    private boolean mTimeout = false;

    //private static final int DISMISS_TIMEOUT_DIALOG = 0;

    //private static final int DISMISS_TIMEOUT_DIALOG_VALUE = 2000;

    /*
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

             * if
             * (!BluetoothPbapService.USER_CONFIRM_TIMEOUT_ACTION.equals(intent
             * .getAction())) { return; } onTimeout();
             *
        }
    };
*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent i = getIntent();
        String action = i.getAction();

        if (ACTION_AUTH_CHAL.equals(action)) {
            mAuthType = TYPE_AUTH_CHAL;
        } else if (ACTION_AUTH.equals(action)) {
            mAuthType = TYPE_AUTH;
        } else {
            Log.e(TAG, "Invalid auth action: " + mAuthType + ". Closing dialog...");
            finish();
        }

        mDevice = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        mDescription = i.getStringExtra(EXTRA_DESCRIPTION);
        mIsUserIdRequired = i.getBooleanExtra(EXTRA_USERID_REQUIRED, false);
        mIsFullAccess = i.getBooleanExtra(EXTRA_FULLACCESS, false);
        mUsername = i.getStringExtra(EXTRA_USERNAME);
        Log.d(TAG, "onCreate(): description= " + (mDescription == null ? "" : mDescription)
                + ", isUserIdRequired=" + mIsUserIdRequired + ",isFullAccess=" + mIsFullAccess
                + ", username=" + (mUsername == null ? "" : mUsername));
        // registerReceiver(mReceiver, new IntentFilter(
        // BluetoothPbapService.USER_CONFIRM_TIMEOUT_ACTION));
        showAuthDialog();

    }

    private void showAuthDialog() {
        final AlertController.AlertParams p = mAlertParams;
        p.mIconId = android.R.drawable.ic_dialog_info;
        p.mTitle = getString(R.string.pbap_session_key_dialog_header);
        p.mView = createView(mAuthType);
        p.mPositiveButtonText = getString(android.R.string.ok);
        p.mPositiveButtonListener = this;
        p.mNegativeButtonText = getString(android.R.string.cancel);
        p.mNegativeButtonListener = this;
        setupAlert();
        mOkButton = mAlert.getButton(DialogInterface.BUTTON_POSITIVE);
        mOkButton.setEnabled(false);
    }

    private View createView(final int dialogType) {
        switch (dialogType) {
        case TYPE_AUTH_CHAL:
        case TYPE_AUTH:
            mView = getLayoutInflater().inflate(R.layout.auth, null);
            messageView = (TextView) mView.findViewById(R.id.message);
            if (mIsUserIdRequired || mUsername != null) {
                if (mUsername != null) {
                    messageView.setText(getString(R.string.pbap_session_key_dialog_title,
                            mDevice.getName()));
                } else {
                    messageView
                            .setText(getString(
                                    R.string.pbap_session_key_dialog_title_with_username,
                                    mDevice.getName()));
                }
                mKeyView = (EditText) mView.findViewById(R.id.key);
                mKeyView.addTextChangedListener(this);
                mKeyView.setFilters(new InputFilter[] { new LengthFilter(
                        BLUETOOTH_OBEX_AUTHKEY_MAX_LENGTH) });

                mUsernameView = (EditText) mView.findViewById(R.id.username);
                if (mUsername == null) {
                    mUsernameView.addTextChangedListener(this);
                    mUsernameView.setFilters(new InputFilter[] { new LengthFilter(
                            BLUETOOTH_OBEX_AUTHKEY_MAX_LENGTH) });
                } else {
                    mUsernameView.setEnabled(false);
                    mUsernameView.setText(mUsername);
                    mKeyView.requestFocus();
                }
            } else {
                messageView.setText(getString(R.string.pbap_session_key_dialog_title,
                        mDevice.getName()));
                mKeyView = (EditText) mView.findViewById(R.id.key);
                mKeyView.addTextChangedListener(this);
                mKeyView.setFilters(new InputFilter[] { new LengthFilter(
                        BLUETOOTH_OBEX_AUTHKEY_MAX_LENGTH) });
                // Hide the username field
                View v = (mView.findViewById(R.id.label_username));
                if (v != null) {
                    v.setVisibility(View.GONE);
                }
                v = (mView.findViewById(R.id.username));
                if (v != null) {
                    v.setVisibility(View.GONE);
                }
                v = mView.findViewById(R.id.label_key);
                if (v != null) {
                    v.setVisibility(View.GONE);
                }
            }
            return mView;
        default:
            return null;
        }
    }

    private void onPositive() {
        if (!mTimeout) {
            mKeyView.removeTextChangedListener(this);
            if (mUsernameView != null) {
                mUsernameView.removeTextChangedListener(this);
            }
            Intent i = new Intent(ACTION_AUTH_RESPONSE);
            i.putExtra(EXTRA_AUTH_TYPE, mAuthType);
            i.putExtra(BluetoothDevice.EXTRA_DEVICE,mDevice);
            i.putExtra(EXTRA_USERNAME, mUsername);
            i.putExtra(EXTRA_SESSION_KEY, mSessionKey);
            sendBroadcast(i);
        }
        mTimeout = false;
        finish();
    }

    private void onNegative() {
        // sendIntentToReceiver(ACTION_CANCEL_AUTH, null, null);
        mKeyView.removeTextChangedListener(this);
        if (mUsernameView != null) {
            mUsernameView.removeTextChangedListener(this);
        }
        Intent i = new Intent(ACTION_CANCEL_AUTH);
        i.putExtra(EXTRA_AUTH_TYPE, mAuthType);
        i.putExtra(BluetoothDevice.EXTRA_DEVICE,mDevice);
        sendBroadcast(i);
        finish();
    }

    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
        case DialogInterface.BUTTON_POSITIVE:
            mSessionKey = mKeyView.getText().toString();
            mUsername = (mUsernameView == null ? null : mUsernameView.getText().toString());
            onPositive();
            break;
        case DialogInterface.BUTTON_NEGATIVE:
            onNegative();
            break;
        default:
            break;
        }
    }

    /*
     * private void onTimeout() { mTimeout = true; if (mCurrentDialog ==
     * DIALOG_YES_NO_AUTH) {
     * messageView.setText(getString(R.string.pbap_authentication_timeout_message
     * , BluetoothPbapService.getRemoteDeviceName()));
     * mKeyView.setVisibility(View.GONE); mKeyView.clearFocus();
     * mKeyView.removeTextChangedListener(this); mOkButton.setEnabled(true);
     * mAlert
     * .getButton(DialogInterface.BUTTON_NEGATIVE).setVisibility(View.GONE); }
     *
     * mTimeoutHandler.sendMessageDelayed(mTimeoutHandler.obtainMessage(
     * DISMISS_TIMEOUT_DIALOG), DISMISS_TIMEOUT_DIALOG_VALUE); }
     */
    /*
     * @Override protected void onRestoreInstanceState(Bundle
     * savedInstanceState) { super.onRestoreInstanceState(savedInstanceState);
     * mTimeout = savedInstanceState.getBoolean(KEY_USER_TIMEOUT); if (V)
     * Log.v(TAG, "onRestoreInstanceState() mTimeout: " + mTimeout); if
     * (mTimeout) { onTimeout(); } }
     *
     * @Override protected void onSaveInstanceState(Bundle outState) {
     * super.onSaveInstanceState(outState);
     * outState.putBoolean(KEY_USER_TIMEOUT, mTimeout); }
     */

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // unregisterReceiver(mReceiver);
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return true;
    }

    public void beforeTextChanged(CharSequence s, int start, int before, int after) {
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    public void afterTextChanged(android.text.Editable s) {
        if ((mKeyView != null && mKeyView.length() > 0)
                && (mUsernameView == null || mUsernameView.length() > 0)) {
            if (mOkButton != null) {
                mOkButton.setEnabled(true);
            }
        }
    }
    /*
     * private final Handler mTimeoutHandler = new Handler() {
     *
     * @Override public void handleMessage(Message msg) { switch (msg.what) {
     * case DISMISS_TIMEOUT_DIALOG: Log.v(TAG,
     * "Received DISMISS_TIMEOUT_DIALOG msg."); finish(); break; default: break;
     * } } };
     */
}
