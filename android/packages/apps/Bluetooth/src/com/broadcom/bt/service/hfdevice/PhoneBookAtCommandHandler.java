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

package com.broadcom.bt.service.hfdevice;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import com.android.bluetooth.R;

import com.android.internal.telephony.GsmAlphabet;
import android.util.Log;

import com.broadcom.bt.hfdevice.BluetoothHfDevice;
import com.broadcom.bt.hfdevice.BluetoothPhoneBookInfo;



/**
 * Helper for managing phonebook presentation over AT commands
 * @hide
 */
public class PhoneBookAtCommandHandler {
    private static final String TAG = "PhoneBookAtCommandHandler";
    private static final boolean DBG = true;

    private static final int STATE_IDLE = 0;
    private static final int STATE_WAITING_FOR_CSCS_TEST_RESP = 1;
    private static final int STATE_WAITING_FOR_CSCS_SELECT_RESP = 2;
    private static final int STATE_WAITING_FOR_CPBS_TEST_RESP = 3;
    private static final int STATE_WAITING_FOR_CPBS_SELECT_RESP = 4;
    private static final int STATE_WAITING_FOR_CPBS_READ_RESP = 5;
    private static final int STATE_WAITING_FOR_CPBR_SELECT_RESP = 6;
    private static final int STATE_IGNORE_FURTHER_CPBR_SELECT_RESP  = 7;


    private static final String UTF8 = "UTF-8"; // default
    private static final String GSM = "GSM";

    private static final int PHONEBOOK_DOWNLOAD_SIZE = 5;


    private static int mState;


    private HfDeviceService mService;

    private HfDeviceStateMachine mHfStateMachine;
    private String mPhoneMemType;
    private String mCharacterSet;

    private int mCpbrStartIndex, mCpbrEndIndex,mDowloadCount;
    private int mCpbrTotalLocation, mCpbrUsedLocation;
    private int mMaxReadLimitCount;

    //Not used for app can be removed
    private int mCpbrMaxNumberLen, mCpbrMaxTextLen;

    private List<BluetoothPhoneBookInfo> mPhoneBookInfoList = new ArrayList<BluetoothPhoneBookInfo>();


    public PhoneBookAtCommandHandler() {
    }

    public boolean startDownload (HfDeviceService service, HfDeviceStateMachine hfDeviceSM,
                                        String phoneMemType, int maxReadLimitCount) {

        if (isBusy())
            return false;

        mService = service;
        mHfStateMachine = hfDeviceSM;

        // Set default values
        mCharacterSet = "UTF-8";
        mPhoneMemType = phoneMemType;
        mMaxReadLimitCount = maxReadLimitCount;
        mCpbrStartIndex = 0;
        mCpbrEndIndex = 0;
        mCpbrMaxNumberLen = 0;
        mCpbrMaxTextLen = 0;
        mDowloadCount = 0;
        mCpbrTotalLocation = 0;
        mCpbrUsedLocation = 0;


        mPhoneBookInfoList.clear();

        mHfStateMachine.configPbDownloadMode(HfDeviceHalConstants.PB_DOWNLOAD_MODE_ENABLE);

        return sendCSCSTestCommand();
    }

    public synchronized void handleStatusEvent (int status) {

        if (!isBusy())
            return;

        switch (mState) {
            case STATE_IDLE:
                break;
            case STATE_WAITING_FOR_CSCS_TEST_RESP:
                if (status == BluetoothHfDevice.NO_ERROR) {
                    sendCSCSSelectCommand(mCharacterSet);
                } else {
                    Log.e(TAG,"Error in STATE_WAITING_FOR_CSCS_TEST_RESP  status = "+status);
                    mService.broadcastonPhoneBookReadEvent(status, null);
                    cleanup();
                }
                break;
            case STATE_WAITING_FOR_CSCS_SELECT_RESP:
                if (status == BluetoothHfDevice.NO_ERROR) {
                    sendCPBSTestCommand();
                } else {
                    Log.e(TAG,"Error in STATE_WAITING_FOR_CSCS_TEST_RESP  status = "+status);
                    mService.broadcastonPhoneBookReadEvent(status, null);
                    cleanup();
                }
                break;
            case STATE_WAITING_FOR_CPBS_TEST_RESP:
                if (status == BluetoothHfDevice.NO_ERROR) {
                    sendCPBSSelectCommand(mPhoneMemType);
                } else {
                    Log.e(TAG,"Error in STATE_WAITING_FOR_CSCS_TEST_RESP  status = "+status);
                    mService.broadcastonPhoneBookReadEvent(status, null);
                    cleanup();
                }
                break;
            case STATE_WAITING_FOR_CPBS_SELECT_RESP:
                if (status == BluetoothHfDevice.NO_ERROR) {
                    sendCPBSReadCommand();
                } else {
                    Log.e(TAG,"Error in STATE_WAITING_FOR_CSCS_TEST_RESP  status = "+status);
                    mService.broadcastonPhoneBookReadEvent(status, null);
                    cleanup();
                }
                break;
            case STATE_WAITING_FOR_CPBS_READ_RESP:
                if (status == BluetoothHfDevice.NO_ERROR) {
                    sendCPBRSelectCommand(mCpbrStartIndex, mCpbrEndIndex);
                } else {
                    Log.e(TAG,"Error in STATE_WAITING_FOR_CPBS_READ_RESP  status = "+status);
                    mService.broadcastonPhoneBookReadEvent(status, null);
                    cleanup();
                }
                   break;
            case STATE_IGNORE_FURTHER_CPBR_SELECT_RESP:
                //fall through
            case STATE_WAITING_FOR_CPBR_SELECT_RESP:

                if (status == BluetoothHfDevice.NO_ERROR) {
                    int statusForApp;

                    if ((mDowloadCount == mMaxReadLimitCount)
                        || ((mCpbrStartIndex + PHONEBOOK_DOWNLOAD_SIZE) > mCpbrTotalLocation))
                        statusForApp = BluetoothHfDevice.PHONEBOOK_READ_COMPLETED;
                    else
                        statusForApp = BluetoothHfDevice.PHONEBOOK_READ_PROGRESS_UPDATE;
                    // Create a copy and send to callback
                    List<BluetoothPhoneBookInfo> clonePhoneBookInfoList =
                            new ArrayList<BluetoothPhoneBookInfo>();
                    for(int i = 0; i < mPhoneBookInfoList.size(); i++) {
                        BluetoothPhoneBookInfo clone = new BluetoothPhoneBookInfo(mPhoneBookInfoList.get(i));
                        clonePhoneBookInfoList.add(clone);
                    }
                    mPhoneBookInfoList.clear();
                    mService.broadcastonPhoneBookReadEvent(
                           statusForApp,
                           clonePhoneBookInfoList);

                    if(statusForApp == BluetoothHfDevice.PHONEBOOK_READ_PROGRESS_UPDATE) {
                        mCpbrStartIndex += PHONEBOOK_DOWNLOAD_SIZE;
                        updateCpbrEndIndex();

                        Log.d(TAG, "PHONEBOOK_READ_PROGRESS_UPDATE mCpbrStartIndex = "+ mCpbrStartIndex +
                                        "mCpbrEndIndex ="+mCpbrEndIndex+
                                        "mMaxReadLimitCount= "+ mMaxReadLimitCount);

                        sendCPBRSelectCommand(mCpbrStartIndex, mCpbrEndIndex);
                    } else {
                        cleanup();
                    }

                } else {
                     Log.e(TAG,"Error in STATE_WAITING_FOR_CPBR_SELECT_RESP  status = "+status);
                     mService.broadcastonPhoneBookReadEvent(status, mPhoneBookInfoList);
                     cleanup();
                }

                break;
            default:
                break;

        }
    }

    public void handleCSCSCommandRsp (String rspString) {

        if (!isBusy())
            return;

        if (rspString.contains(mCharacterSet)) {
            // Wait for Status event
        } else if (rspString.contains(mCharacterSet)){
            mCharacterSet = "GSM";
            Log.d(TAG,"GSM Character set will be selected for PB download "+rspString);
        } else {
            //Character set not supported
            Log.e(TAG,"Character set not supported charset = "+rspString);
            mService.broadcastonPhoneBookReadEvent(BluetoothHfDevice.ERROR_NO_SERVICE, null);
            cleanup();
        }
    }

    public void handleCPBSCommandRsp (String rspString) {
        if (!isBusy())
            return;

        if (STATE_WAITING_FOR_CPBS_SELECT_RESP == mState) {
            if (rspString.contains(mPhoneMemType)) {
                // Wait for Status event
            } else {
                // phoneMemType not supported by AG
                Log.e(TAG,"Pnone memory not supported AG memory types = "+rspString);
                mService.broadcastonPhoneBookReadEvent(BluetoothHfDevice.ERROR_AG_FAILURE, null);
                cleanup();
            }
        } else if (STATE_WAITING_FOR_CPBS_READ_RESP == mState) {
            StringTokenizer stringTokenizer = new StringTokenizer(rspString,",");
            int i = 0;
            String tokenString;

            Log.d(TAG, rspString);

            while(stringTokenizer.hasMoreTokens()) {

                tokenString = stringTokenizer.nextToken();
                if (null != tokenString)
                {
                    tokenString = tokenString.trim();
                    if (i == 0) {
                        //already we have the type
                    } else if ( i == 1) {
                        mCpbrUsedLocation= Integer.parseInt(tokenString.trim());
                    } else if (i == 2) {
                        mCpbrTotalLocation= Integer.parseInt(tokenString.trim());
                    }
                }
                i = i + 1;
            }

            Log.d(TAG, "mCpbrUsedLocation = "+ mCpbrUsedLocation +
                            "mCpbrTotalLocation ="+mCpbrTotalLocation);

            if ((-1 == mMaxReadLimitCount) || (mMaxReadLimitCount > mCpbrUsedLocation))
                mMaxReadLimitCount = mCpbrUsedLocation;

            mCpbrStartIndex = 1;
            updateCpbrEndIndex();

            Log.d(TAG, "mCpbrStartIndex = "+ mCpbrStartIndex +
                            "mCpbrEndIndex ="+mCpbrEndIndex+
                            "mMaxReadLimitCount= "+ mMaxReadLimitCount);

        }

    }

    //To be called only for mDowloadCount < mMaxReadLimitCount 
    private  void  updateCpbrEndIndex() {
        Log.d(TAG,"mCpbrEndIndex"+mCpbrEndIndex+"mDowloadCount"+mDowloadCount
            +"mCpbrTotalLocation"+mCpbrTotalLocation);

        mCpbrEndIndex += PHONEBOOK_DOWNLOAD_SIZE;

        if (mCpbrEndIndex > mCpbrTotalLocation)
            mCpbrEndIndex = mCpbrTotalLocation ;

    }

    public void handleCPBRCommandRsp (String rspString) {
        if (!isBusy())
            return;

         if (STATE_WAITING_FOR_CPBR_SELECT_RESP == mState){

            BluetoothPhoneBookInfo phoneBookInfo = new BluetoothPhoneBookInfo(rspString, mCharacterSet);
            mPhoneBookInfoList.add(phoneBookInfo);
            mDowloadCount++;
            Log.d(TAG, "mDowloadCount=" +mDowloadCount);

            if (mDowloadCount == mMaxReadLimitCount) {
                mState = STATE_IGNORE_FURTHER_CPBR_SELECT_RESP;
                Log.d(TAG,
                    "Swtiched to  STATE_IGNORE_FURTHER_CPBR_SELECT_RESP as DL limit reached");
            }

        } else if (STATE_IGNORE_FURTHER_CPBR_SELECT_RESP == mState) {
            Log.d(TAG,
                "In STATE_IGNORE_FURTHER_CPBR_SELECT_RESP  response ignored as DL limit reached");
        }


    }

    public boolean sendCSCSTestCommand () {
        boolean status = false;

        status = mHfStateMachine.sendVndATCmdNative("+CSCS=?");
        if (status) {
            mState = STATE_WAITING_FOR_CSCS_TEST_RESP;
        }
        return status;
    }

    public void sendCSCSSelectCommand (String characterSet) {
        boolean status = false;

        status = mHfStateMachine.sendVndATCmdNative("+CSCS=\"" + characterSet + "\"");
        if (status) {
            mState = STATE_WAITING_FOR_CSCS_SELECT_RESP;
        } else {
            mService.broadcastonPhoneBookReadEvent(BluetoothHfDevice.ERROR_AG_FAILURE, null);
            cleanup();
        }
    }

    public void sendCPBSTestCommand () {
        boolean status = false;

        status = mHfStateMachine.sendVndATCmdNative("+CPBS=?");
        if (status) {
            mState = STATE_WAITING_FOR_CPBS_TEST_RESP;
        } else {
            mService.broadcastonPhoneBookReadEvent(BluetoothHfDevice.ERROR_AG_FAILURE, null);
            cleanup();
        }
    }

    public void sendCPBSSelectCommand (String memType) {

        boolean status = false;

        status = mHfStateMachine.sendVndATCmdNative("+CPBS=\"" + memType + "\"");
        if (status) {
            mState = STATE_WAITING_FOR_CPBS_SELECT_RESP;
        } else {
            mService.broadcastonPhoneBookReadEvent(BluetoothHfDevice.ERROR_AG_FAILURE, null);
            cleanup();
        }
    }

    public void sendCPBSReadCommand () {

        boolean status = false;

        status = mHfStateMachine.sendVndATCmdNative("+CPBS?");
        if (status) {
            mState = STATE_WAITING_FOR_CPBS_READ_RESP;
        } else {
            mService.broadcastonPhoneBookReadEvent(BluetoothHfDevice.ERROR_AG_FAILURE, null);
            cleanup();
        }

    }


    public void sendCPBRSelectCommand (int startIntex, int endIndex) {

        boolean status = false;

        status = mHfStateMachine.sendVndATCmdNative("+CPBR=" + startIntex + "," + endIndex);
        if (status) {
            mState = STATE_WAITING_FOR_CPBR_SELECT_RESP;
        } else {
            mService.broadcastonPhoneBookReadEvent(BluetoothHfDevice.ERROR_AG_FAILURE, null);
            cleanup();
        }
    }


    public void cleanup() {
        mState = STATE_IDLE;

        if (null != mHfStateMachine)
            mHfStateMachine.configPbDownloadMode(HfDeviceHalConstants.PB_DOWNLOAD_MODE_DISABLE);

        mService = null;
        mHfStateMachine = null;

        // Set default values
        mCharacterSet = "UTF-8";
        mPhoneMemType = null;
        mMaxReadLimitCount = 0;
        mCpbrStartIndex = 0;
        mCpbrEndIndex = 0;
        mCpbrMaxNumberLen = 0;
        mCpbrMaxTextLen = 0;
        mDowloadCount = 0;
        mCpbrTotalLocation = 0;
        mCpbrUsedLocation = 0;

        mPhoneBookInfoList.clear();

    }

    public static boolean isBusy () {
            return STATE_IDLE != mState;
    }


    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}

