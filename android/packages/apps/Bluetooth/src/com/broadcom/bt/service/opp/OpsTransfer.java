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



/**
 * This class run an actual Opp transfer session (from connect target device to
 * disconnect)
 */
public class OpsTransfer implements BluetoothOppBatch.BluetoothOppBatchListener {
    private static final String TAG = "OpsTransfer";

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


    public OpsTransfer(Context context, PowerManager powerManager,
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
                if(D) Log.d(TAG, "inside markBatchFailed, fail reason is =" + failReason);
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

        if (mBatch.mDirection == BluetoothShare.DIRECTION_INBOUND) {
                mBatch.mStatus = Constants.BATCH_STATUS_RUNNING;
                mCurrentShare = mBatch.getPendingShare();
                if(mCurrentShare == null){
                  Log.e(TAG,"This is unexpected error , deal this by closing Ops");
            }
            /**making the current share status as RUNNING for an inboundShare **/
            Constants.updateShareStatus(mContext,
                                        mCurrentShare.mId,
                                        BluetoothShare.STATUS_RUNNING);
         }

    }



  private void processNextShareInfo() {
            mCurrentShare = mBatch.getPendingShare();
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




        public void updateProgressOps (int transferredSize){
        /*work on the mCurrentShare which was updated during intialize push*/
        ContentValues updateValues;
        updateValues = new ContentValues();
        updateValues.put(BluetoothShare.CURRENT_BYTES, transferredSize);
        mCurrentShare.mCurrentPosition = transferredSize;
        Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + mCurrentShare.mId);
        mContext.getContentResolver().update(contentUri, updateValues,null, null);

    }



    public void handleOpsObjRecieved(){
        Constants.updateShareStatus(mContext, mCurrentShare.mId, BluetoothShare.STATUS_SUCCESS);
    }

    public void handleOpsClosed(){
        mBatch.mStatus = Constants.BATCH_STATUS_FINISHED;
        tickShareStatus(mCurrentShare);
    }

    public void handleOpsClosed(boolean opsObj , int prevStatus){
        if(D) Log.d(TAG, "handleOpsClosed");
        if(mCurrentShare == null)
            return;
        if(!opsObj){
            /**
            if we have recieved close evt before obj event that means its a error condition
            the remote user has terminated the conection during progress
            **/
            if(prevStatus == Constants.OPS_ACCESS){
               Uri contentUri1 = Uri.parse(BluetoothShare.CONTENT_URI + "/" + mCurrentShare.mId);
               ContentValues updateValues1 = new ContentValues();
               updateValues1.put(BluetoothShare.USER_CONFIRMATION,
                                BluetoothShare.USER_CONFIRMATION_DENIED);
               updateValues1.put(BluetoothShare.STATUS, BluetoothShare.STATUS_CANCELED);
               mContext.getContentResolver().update(contentUri1, updateValues1, null, null);
               Constants.sendIntentIfCompleted(mContext,
                                               contentUri1,
                                               BluetoothShare.STATUS_CANCELED);
            }else{
                //This happens if we recieve OPS Close before OPS_PROGRESS
                Constants.updateShareStatus(mContext, mCurrentShare.mId,
                                        BluetoothShare.STATUS_CANCELED);
            }
            markBatchFailed(BluetoothShare.STATUS_CANCELED);
            mBatch.mStatus = Constants.BATCH_STATUS_FAILED;
            tickShareStatus(mCurrentShare);
        }else{
            mBatch.mStatus = Constants.BATCH_STATUS_FINISHED;
            tickShareStatus(mCurrentShare);
        }
    }
    /**  remove the notification and send ui timeout broadcast message*/
    public void handleOpsDenied(BluetoothOppShareInfo info){
        if(D) Log.d(TAG, "handleOpsDenied");
        int status = BluetoothShare.STATUS_CANCELED;
        NotificationManager nm = (NotificationManager)mContext
            .getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(info.mId);
        // Send intent to UI for timeout handling
        Intent in = new Intent(BluetoothShare.USER_CONFIRMATION_TIMEOUT_ACTION);
        mContext.sendBroadcast(in);
        markShareTimeout(info);
        Log.d(TAG, "handleOpsDenied: calling updateShareStatus with status cancelled");
        Constants.updateShareStatus(mContext, info.mId, status);

    }

    public void handleFileSysError(BluetoothOppShareInfo info){
        if(D) Log.d(TAG, "file sys error occured during creation of file");
        Constants.updateShareStatus(mContext,info.mId,info.mStatus);
        markBatchFailed(info.mStatus);
        mBatch.mStatus = Constants.BATCH_STATUS_FAILED;
        tickShareStatus(mCurrentShare);
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


}

