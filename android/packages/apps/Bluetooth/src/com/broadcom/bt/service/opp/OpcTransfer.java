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

package com.broadcom.bt.service.opp;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothUuid;
import android.os.ParcelUuid;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.Process;
import android.util.Log;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import android.provider.MediaStore;
import android.database.Cursor;
import android.content.ContentResolver;

import android.webkit.MimeTypeMap;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Groups;
import android.provider.MediaStore.MediaColumns;
import java.net.URLDecoder;
import android.content.ContentResolver;
import android.content.res.AssetFileDescriptor;





/**
 * This class run an actual Opp transfer session (from connect target device to
 * disconnect)
 */
public class OpcTransfer implements BluetoothOppBatch.BluetoothOppBatchListener {
    private static final String TAG = "OpcTransfer";

    private static final boolean D = Constants.DEBUG;
    private static final boolean V = Constants.VERBOSE;

    private Context mContext;

    private BluetoothAdapter mAdapter;

    public  BluetoothOppBatch mBatch;

    private BluetoothOppShareInfo mCurrentShare;

    private HandlerThread mHandlerThread;


    private long mTimestamp;

    private OppService mOppService = null;

    private static final int OPC_STATUS_OK =              0; /* Object push succeeded. */
    private static final int OPC_STATUS_FAIL =            1; /* Object push failed. */
    private static final int OPC_STATUS_NOT_FOUND =       2; /* Object not found. */
    private static final int OPC_STATUS_NO_PERMISSION =   3; /* Operation not authorized. */
    private static final int OPC_STATUS_SRV_UNAVAIL =     4; /* Service unavaliable */
    private static final int OPC_STATUS_RSP_FORBIDDEN =   7; /* Operation forbidden */
    private static final int OPC_STATUS_RSP_NOT_ACCEPTABLE =  8; /* Operation not acceptable */


    public OpcTransfer(Context context, PowerManager powerManager,
            BluetoothOppBatch batch) {

        mContext = context;
        mBatch = batch;
        mBatch.registerListern(this);
        mAdapter = BluetoothAdapter.getDefaultAdapter();

    }

    public int getBatchId() {
        return mBatch.mId;
    }

    private void markShareTimeout(BluetoothOppShareInfo share) {
        if(D) Log.d(TAG, "markShareTimeout");
        Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + share.mId);
        ContentValues updateValues = new ContentValues();
        updateValues
                .put(BluetoothShare.USER_CONFIRMATION, BluetoothShare.USER_CONFIRMATION_TIMEOUT);
        mContext.getContentResolver().update(contentUri, updateValues, null, null);
    }

    private void markBatchFailed(int failReason) {
        synchronized (this) {
            try {
                wait(1000);
            } catch (InterruptedException e) {
                if (V) Log.v(TAG, "Interrupted waiting for markBatchFailed");
            }
        }

        if (D) Log.d(TAG, "Mark all ShareInfo in the batch as failed");
        if (mCurrentShare != null) {
            if (V) Log.v(TAG, "Current share has status " + mCurrentShare.mStatus);
            if (BluetoothShare.isStatusError(mCurrentShare.mStatus)) {
                failReason = mCurrentShare.mStatus;
                if(D) Log.d(TAG, "markBatchFailed, fail reason is =" + failReason);
            }
            if (mCurrentShare.mDirection == BluetoothShare.DIRECTION_INBOUND
                    && mCurrentShare.mFilename != null) {
                new File(mCurrentShare.mFilename).delete();
            }
        }

        BluetoothOppShareInfo info = null;
        if (mBatch == null) {
            return;
        }
        info = mBatch.getPendingShare();
        while (info != null) {
            if (info.mStatus < 200) {
                info.mStatus = failReason;
                Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + info.mId);
                ContentValues updateValues = new ContentValues();
                updateValues.put(BluetoothShare.STATUS, info.mStatus);
                /* Update un-processed outbound transfer to show some info */
                if (info.mDirection == BluetoothShare.DIRECTION_OUTBOUND) {
                    BluetoothOppSendFileInfo fileInfo
                            = BluetoothOppUtility.getSendFileInfo(info.mUri);
                    BluetoothOppUtility.closeSendFileInfo(info.mUri);
                    if (fileInfo.mFileName != null) {
                        updateValues.put(BluetoothShare.FILENAME_HINT, fileInfo.mFileName);
                        updateValues.put(BluetoothShare.TOTAL_BYTES, fileInfo.mLength);
                        updateValues.put(BluetoothShare.MIMETYPE, fileInfo.mMimetype);
                    }
                } else { /*for inbound failures delete the file*/
                    if (info.mStatus < 200 && info.mFilename != null) {
                        new File(info.mFilename).delete();
                    }
                }
                mContext.getContentResolver().update(contentUri, updateValues, null, null);
                Constants.sendIntentIfCompleted(mContext, contentUri, info.mStatus);
            }
            info = mBatch.getPendingShare();
        }

    }


    public void intiate() {
        /** check Bluetooth enable status
                    normally it's impossible to reach here if BT is disabled. Just check  for safety
             **/
        if (!mAdapter.isEnabled()) {
            Log.e(TAG, "Can't start transfer when Bluetooth is disabled for " + mBatch.mId);
            markBatchFailed(BluetoothShare.STATUS_UNKNOWN_ERROR);
            mBatch.mStatus = Constants.BATCH_STATUS_FAILED;
            return;
        }

        if (mBatch.mDirection == BluetoothShare.DIRECTION_OUTBOUND) {
                /* for outbound transfer, we do connect first */
            startPush();
        }

    }


    public void intializePushStatus() {

            if(D)Log.d(TAG, "intializePushStatus()");
            mCurrentShare = mBatch.getPendingShare();
            //mOppService.setCurrentShareInfo(mCurrentShare);
            if (V) Log.v(TAG, "Current Share id is =  " + mCurrentShare.mId);
            BluetoothOppSendFileInfo fileInfo =
                                           BluetoothOppUtility.getSendFileInfo(mCurrentShare.mUri);

            if (fileInfo.mFileName == null || fileInfo.mLength == 0) {
                if (V) Log.v(TAG, "BluetoothOppSendFileInfo get invalid file");
                    Constants.updateShareStatus(mContext, mCurrentShare.mId, fileInfo.mStatus);

            } else {
                if (V) {
                    Log.v(TAG, "Generate BluetoothOppSendFileInfo:");
                    Log.v(TAG, "filename  :" + fileInfo.mFileName);
                    Log.v(TAG, "length    :" + fileInfo.mLength);
                    Log.v(TAG, "mimetype  :" + fileInfo.mMimetype);
                }
                mBatch.mStatus = Constants.BATCH_STATUS_RUNNING;
                ContentValues updateValues = new ContentValues();
                Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + mCurrentShare.mId);
                updateValues.put(BluetoothShare.FILENAME_HINT, fileInfo.mFileName);
                updateValues.put(BluetoothShare.TOTAL_BYTES, fileInfo.mLength);
                updateValues.put(BluetoothShare.MIMETYPE, fileInfo.mMimetype);
                updateValues.put(BluetoothShare.STATUS, BluetoothShare.STATUS_RUNNING);
                /**in the existing implementation , before start of Sending file the share is updated*/
                updateValues.put(BluetoothShare.CURRENT_BYTES, 0);
                mContext.getContentResolver().update(contentUri, updateValues, null, null);
            }

    }


  private void processNextShareInfo() {
            mCurrentShare = mBatch.getPendingShare();
            //mOppService.setCurrentShareInfo(mCurrentShare);
            if (V) Log.v(TAG, "Current Share id is =  " + mCurrentShare.mId);
            BluetoothOppSendFileInfo fileInfo =
                                           BluetoothOppUtility.getSendFileInfo(mCurrentShare.mUri);

            if (fileInfo.mFileName == null || fileInfo.mLength == 0) {
                if (V) Log.v(TAG, "BluetoothOppSendFileInfo get invalid file");
                    Constants.updateShareStatus(mContext, mCurrentShare.mId, fileInfo.mStatus);
            } else {
                if (V) {
                    Log.v(TAG, "Generate BluetoothOppSendFileInfo:");
                    Log.v(TAG, "filename  :" + fileInfo.mFileName);
                    Log.v(TAG, "length    :" + fileInfo.mLength);
                    Log.v(TAG, "mimetype  :" + fileInfo.mMimetype);
                }
                ContentValues updateValues = new ContentValues();
                Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + mCurrentShare.mId);
                updateValues.put(BluetoothShare.FILENAME_HINT, fileInfo.mFileName);
                updateValues.put(BluetoothShare.TOTAL_BYTES, fileInfo.mLength);
                updateValues.put(BluetoothShare.MIMETYPE, fileInfo.mMimetype);
                mContext.getContentResolver().update(contentUri, updateValues, null, null);
            }

    }


    public void updateProgressOpc (int transfSize, int tolalSize, int prevState){
        /*work on the mCurrentShare which was updated during intialize push*/
        if(prevState != Constants.OPC_PUSHD){
            ContentValues updateValues;
            updateValues = new ContentValues();
            updateValues.put(BluetoothShare.CURRENT_BYTES, transfSize);
            mCurrentShare.mCurrentPosition = transfSize;
            Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + mCurrentShare.mId);
            mContext.getContentResolver().update(contentUri, updateValues,null, null);
        }else{
            /**only if the previous state is OPC_OBJ means this is a next push of the same batch**/
            ContentValues updateValues;
            updateValues = new ContentValues();
            updateValues.put(BluetoothShare.CURRENT_BYTES, transfSize);
            updateValues.put(BluetoothShare.STATUS, BluetoothShare.STATUS_RUNNING);
            mCurrentShare.mCurrentPosition = transfSize;
            Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + mCurrentShare.mId);
            mContext.getContentResolver().update(contentUri, updateValues,null, null);
        }
    }



    public void handleOpcPushed(int status){
    if(V) Log.d(TAG, "handlePushed()");
    switch (status){

        case OPC_STATUS_OK:
            {
               if(D) Log.d(TAG, "Recieved OPC_STATUS_OK in OPC_OBJ_PSHD_EVT");
              /*Do the following
                            1> Close the ParcelFile descriptor
                            2> Mark the current share status as succes,
                            3> Check if there are pending share in the batch
                            4> if there are pending share in batch continue the next share else mark batch as finished
                         */
                BluetoothOppUtility.closeSendFileInfo(mCurrentShare.mUri);
                Constants.updateShareStatus(mContext,
                                            mCurrentShare.mId,
                                            BluetoothShare.STATUS_SUCCESS);
                /*Status needs to be updated to Succes for cases
                where OPC open, progress and pushed happens  vary fast,
                such a sceanario happens while executing
                PTS testcase OPH_BV_03_I for small vcard size
                */
                mCurrentShare.mStatus = BluetoothShare.STATUS_SUCCESS;
                if (mBatch.getPendingShare() != null) {
                    /* we have additional share to process */
                    if (V) Log.v(TAG, "continue session for info " + mCurrentShare.mId +
                            " from batch " + mBatch.mId);
                    startPush();
                    processNextShareInfo();
                } else {
                    /* for outbound transfer, all shares are processed */
                    if (V) Log.v(TAG, "Batch " + mBatch.mId + " is done");
                    mOppService = OppService.getOppService();
                    /*close the fd*/
                    mOppService.closeOpc();
                  /*bacth status can also be set to finished after recieving  BTA_OPC_OK with BTA_CLOSE_EVT */
                }
            }
         break;

        case OPC_STATUS_FAIL:
            {
                    if(D) Log.d(TAG, "Recieved OPC_STATUS_FAIL in OPC_OBJ_PSHD_EVT");
                    BluetoothOppUtility.closeSendFileInfo(mCurrentShare.mUri);
                    Constants.updateShareStatus(mContext,
                                            mCurrentShare.mId,
                                           BluetoothShare.STATUS_CANCELED);
                    markBatchFailed(BluetoothShare.STATUS_CANCELED);
                    mBatch.mStatus = Constants.BATCH_STATUS_FAILED;
                    tickShareStatus(mCurrentShare);
                    mOppService = OppService.getOppService();
                    mOppService.closeOpc();
            }
        break;

        case OPC_STATUS_NOT_FOUND:
            {
                    if(D) Log.d(TAG, "Recieved OPC_STATUS_NOT_FOUND in OPC_OBJ_PSHD_EVT");
                    BluetoothOppUtility.closeSendFileInfo(mCurrentShare.mUri);
                    Constants.updateShareStatus(mContext, mCurrentShare.mId,
                            BluetoothShare.STATUS_NOT_FOUND);
                    markBatchFailed(BluetoothShare.STATUS_NOT_FOUND);
                    mBatch.mStatus = Constants.BATCH_STATUS_FAILED;
                    tickShareStatus(mCurrentShare);
                    mOppService = OppService.getOppService();
                    mOppService.closeOpc();
            }
        break;

        /*This case is hit when a OPS rejects or doesnt authorize the incoming connection*/
        case OPC_STATUS_NO_PERMISSION:
            {
                if(D) Log.d(TAG, "Recieved OPC_STATUS_NO_PERMISSION in OPC_OBJ_PSHD_EVT");
                /*close the parcel descriptor after complete and remove the sendFileinfo from the Map*/
                BluetoothOppUtility.closeSendFileInfo(mCurrentShare.mUri);
                Constants.updateShareStatus(mContext, mCurrentShare.mId,
                                            BluetoothShare.STATUS_NO_PERMISSION);
                markBatchFailed( BluetoothShare.STATUS_NO_PERMISSION);
                mBatch.mStatus = Constants.BATCH_STATUS_FAILED;
                tickShareStatus(mCurrentShare);
                /*we have to close the OPC connection*/
                mOppService = OppService.getOppService();
                mOppService.closeOpc();

            }
        break;

        case OPC_STATUS_SRV_UNAVAIL:
            {
                if(D) Log.d(TAG, "Recieved OPC_STATUS_SRV_UNAVAIL in OPC_OBJ_PSHD_EVT");
                /*close the parcel descriptor after complete and remove the sendFileinfo from the Map*/
                BluetoothOppUtility.closeSendFileInfo(mCurrentShare.mUri);
                Constants.updateShareStatus(mContext, mCurrentShare.mId,
                                            BluetoothShare.STATUS_SRV_UNAVAILABLE);
                markBatchFailed( BluetoothShare.STATUS_SRV_UNAVAILABLE);
                mBatch.mStatus = Constants.BATCH_STATUS_FAILED;
                tickShareStatus(mCurrentShare);
                /*we have to close the OPC connection*/
                mOppService = OppService.getOppService();
                mOppService.closeOpc();
            }
        break;


        case OPC_STATUS_RSP_FORBIDDEN:
            {

                if(D) Log.d(TAG, "Recieved OPC_STATUS_RSP_FORBIDDEN in OPC_OBJ_PSHD_EVT");
                /*close the parcel descriptor after complete and remove the sendFileinfo from the Map*/
                BluetoothOppUtility.closeSendFileInfo(mCurrentShare.mUri);
                Constants.updateShareStatus(mContext,
                                            mCurrentShare.mId,
                                           BluetoothShare.STATUS_FORBIDDEN);

                markBatchFailed(BluetoothShare.STATUS_FORBIDDEN);
                mBatch.mStatus = Constants.BATCH_STATUS_FAILED;
                tickShareStatus(mCurrentShare);
                /*we have to close the OPC connection*/
                mOppService = OppService.getOppService();
                mOppService.closeOpc();

            }
        break;

        case OPC_STATUS_RSP_NOT_ACCEPTABLE:
        {
                if(D) Log.d(TAG, "Recieved OPC_STATUS_RSP_NOT_ACCEPTABLE in OPC_OBJ_PSHD_EVT");
                BluetoothOppUtility.closeSendFileInfo(mCurrentShare.mUri);
                Constants.updateShareStatus(mContext, mCurrentShare.mId,
                                            BluetoothShare.STATUS_NOT_ACCEPTABLE);
                markBatchFailed( BluetoothShare.STATUS_NOT_ACCEPTABLE);
                mBatch.mStatus = Constants.BATCH_STATUS_FAILED;
                tickShareStatus(mCurrentShare);
                /*we have to close the OPC connection*/
                mOppService = OppService.getOppService();
                mOppService.closeOpc();
        }
        break;

    }

    }


     public void handleOpcClose(int status, int prevState){
        Log.d(TAG, "recieving handleOpcClose for batch " + mBatch.mId);
        Log.d(TAG,"handleOpcClose , Status is " + status  + "PrevState = " + prevState);

        switch(status){

          case OPC_STATUS_OK:
            {
                if(D) Log.d(TAG , "recieved OPC_OK in OPC_CLOSE_EVT");
                /*this indicates one OPC session is completed*/
                mBatch.mStatus = Constants.BATCH_STATUS_FINISHED;
                tickShareStatus(mCurrentShare);
            }
            break;

            case OPC_STATUS_FAIL:
            {
                if(D) Log.d(TAG , "recieved OPC_FAIL in OPC_CLOSE_EVT");
                if(prevState == Constants.OPC_ENABLE){
                    Log.e(TAG, "indicates OPC connection failure");
                    BluetoothOppUtility.closeSendFileInfo(mCurrentShare.mUri);
                    markBatchFailed(BluetoothShare.STATUS_CONNECTION_ERROR);
                    mBatch.mStatus = Constants.BATCH_STATUS_FAILED;
                    tickShareStatus(mCurrentShare);
                }else if(prevState == Constants.OPC_PROG || prevState == Constants.OPC_OPEN){
                    /*we hit this case during ongoing opc , the client cancels the transfer*/
                      if(mCurrentShare != null){
                            BluetoothOppUtility.closeSendFileInfo(mCurrentShare.mUri);
                            mCurrentShare.mStatus = BluetoothShare.STATUS_CANCELED;
                            Constants.updateShareStatus(mContext, mCurrentShare.mId,
                            mCurrentShare.mStatus);
                            mBatch.mStatus = Constants.BATCH_STATUS_FAILED;
                            markBatchFailed(BluetoothShare.STATUS_CANCELED);
                            tickShareStatus(mCurrentShare);
                      }

                }else{
                  Log.e(TAG, "unhandled use case of OPC_STATUS_FAIL with OPC_CLOSE_EVT");
                }

                }
            break;


            case OPC_STATUS_NOT_FOUND :
            {
                if(D) Log.d(TAG , "recieved OPC_NOT_FOUND in OPC_CLOSE_EVT");
                if(prevState != Constants.OPC_PUSHD){
                    BluetoothOppUtility.closeSendFileInfo(mCurrentShare.mUri);
                    Constants.updateShareStatus(mContext, mCurrentShare.mId,
                    BluetoothShare.STATUS_NOT_FOUND);
                    markBatchFailed(BluetoothShare.STATUS_NOT_FOUND);
                    mBatch.mStatus = Constants.BATCH_STATUS_FAILED;
                    tickShareStatus(mCurrentShare);
                }

            }
            break;


            case OPC_STATUS_NO_PERMISSION:
            {
                if(D) Log.d(TAG , "recieved OPC_NO_PERMISSION in OPC_CLOSE_EVT");
                if(prevState != Constants.OPC_PUSHD){
                    BluetoothOppUtility.closeSendFileInfo(mCurrentShare.mUri);
                    Constants.updateShareStatus(mContext, mCurrentShare.mId,
                    BluetoothShare.STATUS_NO_PERMISSION);
                    markBatchFailed(BluetoothShare.STATUS_NO_PERMISSION);
                    mBatch.mStatus = Constants.BATCH_STATUS_FAILED;
                    tickShareStatus(mCurrentShare);
                }

            }
            break;

            case OPC_STATUS_SRV_UNAVAIL :
            {

                if(D) Log.d(TAG , "recieved OPC_SRV_UNAVAIL in OPC_CLOSE_EVT");
                if(prevState != Constants.OPC_PUSHD){

                    BluetoothOppUtility.closeSendFileInfo(mCurrentShare.mUri);
                    Constants.updateShareStatus(mContext, mCurrentShare.mId,
                    BluetoothShare.STATUS_SRV_UNAVAILABLE);
                    markBatchFailed(BluetoothShare.STATUS_SRV_UNAVAILABLE);
                    mBatch.mStatus = Constants.BATCH_STATUS_FAILED;
                    tickShareStatus(mCurrentShare);
                }

            }

            case OPC_STATUS_RSP_FORBIDDEN :
            {

                if(D) Log.d(TAG , "recieved OPC_STATUS_RSP_FORBIDDEN , in OPC_CLOSE_EVT");
                if(prevState != Constants.OPC_PUSHD){
                    BluetoothOppUtility.closeSendFileInfo(mCurrentShare.mUri);
                    Constants.updateShareStatus(mContext, mCurrentShare.mId,
                    BluetoothShare.STATUS_FORBIDDEN);
                    markBatchFailed(BluetoothShare.STATUS_FORBIDDEN);
                    mBatch.mStatus = Constants.BATCH_STATUS_FAILED;
                    tickShareStatus(mCurrentShare);
                }

            }
            break;

            case OPC_STATUS_RSP_NOT_ACCEPTABLE :
            {

                if(D) Log.d(TAG , "recieved OPC_STATUS_RSP_NOT_ACCEPTABLE in OPC_CLOSE_EVT");
                if(prevState != Constants.OPC_PUSHD){
                    BluetoothOppUtility.closeSendFileInfo(mCurrentShare.mUri);
                    Constants.updateShareStatus(mContext, mCurrentShare.mId,
                    BluetoothShare.STATUS_NOT_ACCEPTABLE);
                    markBatchFailed(BluetoothShare.STATUS_NOT_ACCEPTABLE);
                    mBatch.mStatus = Constants.BATCH_STATUS_FAILED;
                    tickShareStatus(mCurrentShare);
                }


            }
            break;

        }

    }



   private BluetoothOppSendFileInfo processShareInfo(BluetoothOppShareInfo mInfo) {
            if (V) Log.v(TAG, "Client thread processShareInfo() " + mInfo.mId);

            BluetoothOppSendFileInfo fileInfo = BluetoothOppUtility.getSendFileInfo(mInfo.mUri);
            if (fileInfo.mFileName == null || fileInfo.mLength == 0) {
                if (V) Log.v(TAG, "BluetoothOppSendFileInfo get invalid file");
                    Constants.updateShareStatus(mContext, mInfo.mId, fileInfo.mStatus);

            } else {
                if (V) {
                    Log.v(TAG, "Generate BluetoothOppSendFileInfo:");
                    Log.v(TAG, "filename  :" + fileInfo.mFileName);
                    Log.v(TAG, "length    :" + fileInfo.mLength);
                    Log.v(TAG, "mimetype  :" + fileInfo.mMimetype);
                }

                ContentValues updateValues = new ContentValues();
                Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + mInfo.mId);

                updateValues.put(BluetoothShare.FILENAME_HINT, fileInfo.mFileName);
                updateValues.put(BluetoothShare.TOTAL_BYTES, fileInfo.mLength);
                updateValues.put(BluetoothShare.MIMETYPE, fileInfo.mMimetype);
                updateValues.put(BluetoothShare.STATUS, BluetoothShare.STATUS_RUNNING);
                /**in the existing implementation , before start of Sending file the share is updated*/
                updateValues.put(BluetoothShare.CURRENT_BYTES, 0);
                mContext.getContentResolver().update(contentUri, updateValues, null, null);
                Log.e(TAG,"Current share after OPC_OPEN is updated by calling OppProvider.update");

            }
            return fileInfo;
        }



    private void startPush() {
        int fd = 0;
        ContentResolver contentResolver = mContext.getContentResolver();
        mOppService = OppService.getOppService();
        mCurrentShare = mBatch.getPendingShare();
        BluetoothDevice remoteBdAddress = mAdapter.getRemoteDevice(mCurrentShare.mDestination);
        /*storing the BD address and its name in shared preference*/
        BluetoothOppPreference.getInstance(mContext).setName(remoteBdAddress,
                                           remoteBdAddress.getName());
        BluetoothOppSendFileInfo fileInfo =
                                          BluetoothOppUtility.getSendFileInfo(mCurrentShare.mUri);
        if((fileInfo.mStatus == BluetoothShare.STATUS_FILE_ERROR) || fileInfo == null){
            Log.e(TAG,"Aborting Transfet due to file system Error");
            Constants.updateShareStatus(mContext,
                                        mCurrentShare.mId,
                                        BluetoothShare.STATUS_FILE_ERROR);
            markBatchFailed(BluetoothShare.STATUS_FILE_ERROR);
            mBatch.mStatus = Constants.BATCH_STATUS_FAILED;
            tickShareStatus(mCurrentShare);
            return;
        }
        try{
        AssetFileDescriptor assetFd =
                                contentResolver.openAssetFileDescriptor(mCurrentShare.mUri, "r");
        /**
         Following are the reasons to detach fd from AssetFileDescriptor
         1> The Fd passed to lower layer is read by bta file system callouts.
         2> During ongoing progress Strict mode errors on Asset file descriptors were thrown.
         3> Strict mode errors causes bad access errors in lower layer.
         4> Bad access errors aborts the ongoing transfers.
         5> bta_fs_co gracefully closes the fd so we can detach fd in java layer.
         **/
        fd = assetFd.getParcelFileDescriptor().detachFd();
        if(V) Log.v(TAG, "Native fd is : " + fd);
        fileInfo.setAssetFileDescriptor(assetFd);
        /*update fd to BluetoothOppSendFileInfo hashtable*/
        BluetoothOppUtility.putSendFileInfo(mCurrentShare.mUri,fileInfo);
        }catch(FileNotFoundException e){
            Constants.updateShareStatus(mContext,
                                        mCurrentShare.mId,
                                        BluetoothShare.STATUS_FILE_ERROR);
            markBatchFailed(BluetoothShare.STATUS_FILE_ERROR);
            mBatch.mStatus = Constants.BATCH_STATUS_FAILED;
            tickShareStatus(mCurrentShare);
            return;
        }
        if(V){
                Log.v(TAG, "MimeType is : "+ fileInfo.mMimetype);
                Log.v(TAG, "fileName is :" + fileInfo.mFileName);
        }
        /*Applying remoteDeviceQuirks , this check is done in legacy implementation for poloroid pogo files*/
        applyRemoteDeviceQuirks(mCurrentShare.mDestination,fileInfo.mFileName);
        try{
                if(D) Log.d(TAG, "calling btif push");
                mOppService.pushObject(remoteBdAddress,fileInfo.mFileName,fd);
        }catch(Throwable e){
                Log.e(TAG ,"Exception Occured : " + e);
        }
    }



    /* update a trivial field of a share to notify Provider the batch status change */
    private void tickShareStatus(BluetoothOppShareInfo share) {
        if (share == null) {
            Log.e(TAG,"tickShareStatus share is null");
            return;
        }
        Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + share.mId);
        ContentValues updateValues = new ContentValues();
        updateValues.put(BluetoothShare.DIRECTION, share.mDirection);
        mContext.getContentResolver().update(contentUri, updateValues, null, null);
    }


   /*
     * Note: For outbound transfer We don't implement this method now. If later
     * we want to support merging a later added share into an existing session,
     * we could implement here For inbounds transfer add share means it's
     * multiple receive in the same session, we should handle it to fill it into
     * mSession
     */
    /**
     * Process when a share is added to current transfer
     */
    public void onShareAdded(int id) {
        BluetoothOppShareInfo info = mBatch.getPendingShare();
        if (info.mDirection == BluetoothShare.DIRECTION_INBOUND) {
            mCurrentShare = mBatch.getPendingShare();
            /* * TODO what if it's not auto confirmed? , this isnot implemented in legacy solution also             */
            if (mCurrentShare != null &&
                    (mCurrentShare.mConfirm == BluetoothShare.USER_CONFIRMATION_AUTO_CONFIRMED ||
                     mCurrentShare.mConfirm ==
                     BluetoothShare.USER_CONFIRMATION_HANDOVER_CONFIRMED)) {
                /* have additional auto confirmed share to process */
                /*send the Access repsonse for the share*/
                if(D) Log.d(TAG,"Succesive share recieved , are auto confirmed send +ve response");
                mOppService = OppService.getOppService();
                byte oper = 1;
                mOppService.OpsAccessRsp(oper,true,mCurrentShare.mFilename);
                if (V) Log.v(TAG, "Transfer continue session for info " + mCurrentShare.mId +
                        " from batch " + mBatch.mId);
                Constants.updateShareStatus(mContext,
                                            mCurrentShare.mId,
                                            BluetoothShare.STATUS_RUNNING);

            }
        }
    }


   /*
    * NOTE We don't implement this method now. Now delete a single share from
    * the batch means the whole batch should be canceled. If later we want to
    * support single cancel, we could implement here For outbound transfer, if
    * the share is currently in transfer, cancel it For inbounds transfer,
    * delete share means the current receiving file should be canceled.
    */
    /**
     * Process when a share is deleted from current transfer
     */
    public void onShareDeleted(int id) {

    }

    /**
     * Process when current transfer is canceled
     */
    public void onBatchCanceled() {
        if (V) Log.v(TAG, "Transfer on Batch canceled");
        mBatch.mStatus = Constants.BATCH_STATUS_FINISHED;
    }

    private void applyRemoteDeviceQuirks(String address, String filename) {
        String newFilename = null;
        if (address == null) {
                return;
         }
        if (address.startsWith("00:04:48")) {
            // Poloroid Pogo
            // Rejects filenames with more than one '.'. Rename to '_'.
            // for example: 'a.b.jpg' -> 'a_b.jpg'
            //              'abc.jpg' NOT CHANGED
            char[] c = filename.toCharArray();
            boolean firstDot = true;
            boolean modified = false;
            for (int i = c.length - 1; i >= 0; i--) {
                if (c[i] == '.') {
                    if (!firstDot) {
                        modified = true;
                        c[i] = '_';
                    }
                    firstDot = false;
                }
            }

            if (modified) {
                newFilename = new String(c);
                Log.i(TAG, "Sending file \"" + filename + "\" as \"" + newFilename +
                        "\" to workaround Poloroid filename quirk");
            }
        }
   }

}
