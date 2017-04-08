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

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.IBluetooth;
import android.bluetooth.IBluetoothCallback;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.StatFs;
import android.os.SystemProperties;
import android.provider.MediaStore;
import android.util.Log;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.btservice.ProfileService.IProfileServiceBinder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.content.ContentValues;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Process;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import com.google.android.collect.Lists;


   /**
     * Provides Bluetooth Opp Server profile, as a service in
     * the Bluetooth application.
     * @hide
     */
public class OppService extends ProfileService {

    private static final String TAG = "OppService";

    private static final boolean D = Constants.DEBUG;
    private static final boolean V = Constants.VERBOSE;

    private static boolean isStateRunning = false;
    private long mTimestamp;

    MediaScannerConnection mMediaScanner;

    // Constants matching Hal header file bt_op.h
    private static final int OPP_OPER_PUSH = 1;
    private static final int OPP_OPER_PULL = 2;

    private static final int OPP_STATE_ENABLE = 0;
    private static final int OPP_STATE_OPEN = 1;
    private static final int OPP_STATE_CLOSE = 2;
    private static final int OPP_STATE_DISABLE = 3;

    private static final int MESSAGE_OPC_ENABLE = 1;
    private static final int MESSAGE_OPC_OPEN = 2;
    private static final int MESSAGE_OPC_CLOSE = 3;
    private static final int MESSAGE_OPC_OBJ_PUSHED = 4;
    private static final int MESSAGE_OPC_OBJ_PULLED = 5;
    private static final int MESSAGE_OPC_TRANSFER_IN_PROGRESS = 6;

    private static final int MESSAGE_OPS_ENABLE = 7;
    private static final int MESSAGE_OPS_OPEN = 8;
    private static final int MESSAGE_OPS_CLOSE = 9;
    private static final int MESSAGE_OPS_OBJ = 10;
    private static final int MESSAGE_OPS_ACCESS_REQUESTED = 11;
    private static final int MESSAGE_OPS_TRANSFER_IN_PROGRESS = 12;
    private static final int MEDIA_SCANNED = 13;
    private static final int MEDIA_SCANNED_FAILED = 14;
    private static final int MESSAGE_OPS_DISABLE = 15;

    private static final int MESSAGE_OPS_TIMEOUT = 16;
    private static final int MESSAGE_OPC_TIMEOUT = 17;


    private Map<BluetoothDevice, Integer> mOppClientDevices;
    private boolean mNativeAvailable;
    private static boolean mOppServiceStopped ;
    private BluetoothAdapter mAdapter;

    private String filePathForMediaScan = null;

    /**
     * Array used when extracting strings from content provider
     */
    private CharArrayBuffer mOldChars;

    /**
     * Array used when extracting strings from content provider
     */
    private CharArrayBuffer mNewChars;

    private int mBatchId;
    private boolean userAccepted = false;
    private ArrayList<BluetoothOppShareInfo> mShares;
    private ArrayList<BluetoothOppBatch> mBatchs;

    private OpcTransfer mOpcTransfer;
    private OpsTransfer mOpsTransfer;

    private PowerManager mPowerManager;

    private boolean mOpsObj = false;
    private boolean mOpsClose = true;


   private int mOpcState;
   private int mOpsState;
   private boolean mOpcBatchRunning = false;

    private class BluetoothShareContentObserver extends ContentObserver {

        public BluetoothShareContentObserver() {
            super(new Handler());
        }

        @Override
        public void onChange(boolean selfChange) {
            if (V) Log.v(TAG, "ContentObserver received notification");
           updateFromProvider();

        }
    }

    /** Observer to get notified when the content observer's data changes */
    private BluetoothShareContentObserver mObserver;
    /** Class to handle Notification Manager updates */
    private BluetoothOppNotification mNotifier;

    private boolean mPendingUpdate;
    private boolean mOpsBlocked = true;
    private UpdateThread mUpdateThread;
    private boolean mMediaScanInProgress;
    private boolean mOpsTimeoutMsgSent = false;
    private boolean mOpcTimeoutMsgSent = false;

    private static OppService sOppService = null;
    private static BluetoothOppShareInfo mCurrentShareFileInfo = null;
    private int SESSION_TIMEOUT = 50000;


    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {

                case MESSAGE_OPC_ENABLE:
                    {
                         if(D) Log.d(TAG, "Recieved MESSAGE_OPC_ENABLE");
                         mOpcState = Constants.OPC_ENABLE;
                    }
                    break;

                case MESSAGE_OPC_OPEN :
                    {
                        /**This is equivalent to RFCOMM_CONNECTED **/
                         if(D) Log.d(TAG, "Recieved MESSAGE_OPC_OPEN");
                         mOpcState = Constants.OPC_OPEN;
                         mOpcTransfer.intializePushStatus();
                         //send OPC timeout message
                         mHandler.sendMessageDelayed(mHandler.obtainMessage(MESSAGE_OPC_TIMEOUT)
                            , SESSION_TIMEOUT);
                         mOpcTimeoutMsgSent = true;
                    }
                    break;

                case MESSAGE_OPC_CLOSE :
                    {
                        /*This is equivalent to SHARE COMPLETE */
                        int status = msg.arg1;
                        if(D) Log.d(TAG, "status of MESSAGE_OPC_CLOSE is = " + status);
                        if(mOpcTransfer != null){
                            if(mOpcTimeoutMsgSent){
                            /**
                             remove the message from the queue ,
                            this can happen while waiting for timeout remote
                            message decides to abort instead of accept
                            **/
                            mHandler.removeMessages(MESSAGE_OPC_TIMEOUT);
                            mOpcTimeoutMsgSent = false;
                           }
                           mOpcTransfer.handleOpcClose(status,mOpcState);
                        }
                        /*reset the state of OPC to OPC_ENABLE*/
                        mOpcState = Constants.OPC_ENABLE;
                    }
                    break;


                    case MESSAGE_OPC_OBJ_PUSHED :
                    {
                        int status = msg.arg1;
                        mOpcState = Constants.OPC_PUSHD;
                        if(D) Log.d(TAG, "status of MESSAGE_OPC_OBJ_PUSHED is" + status);
                        mOpcTransfer.handleOpcPushed(status);
                    }
                    break;


                case MESSAGE_OPC_OBJ_PULLED :
                    {
                     /*This feature is currently not implemented*/
                        if(D) Log.d(TAG, " Recieved MESSAGE_OPC_OBJ_PULLED");
                    }
                    break;

               case MESSAGE_OPC_TRANSFER_IN_PROGRESS :
                    {
                         int oper;
                         int totalSize;
                         int transferredSize;
                         if(V) Log.v(TAG, " Recieved MESSAGE_OPC_TRANSFER_IN_PROGRESS");
                         Bundle data = msg.getData();
                             if(data != null) {
                               //remove the timeout message and reset mOpsTimeoutMsgSent
                                if (mHandler != null && mOpcTimeoutMsgSent) {
                                    mHandler.removeMessages(MESSAGE_OPC_TIMEOUT);
                                    mOpcTimeoutMsgSent = false;
                                }
                                oper = data.getInt("operation");
                                totalSize = data.getInt("totalSize");
                                transferredSize = data.getInt("transferedSize");
                                ContentValues updateValues;
                                updateValues = new ContentValues();
                                updateValues.put(BluetoothShare.CURRENT_BYTES, transferredSize);
                                if(V) Log.v(TAG, "Current transferredSize = " + transferredSize
                                           + " total size = " + totalSize);
                                mOpcTransfer.updateProgressOpc(transferredSize,totalSize,mOpcState);
                                mOpcState = Constants.OPC_PROG;
                             }
                    }
                    break;


               case MESSAGE_OPS_ENABLE:
                    {

                         if(D) Log.d(TAG, "Recieved MESSAGE_OPS_ENABLE");
                         mOpsState = Constants.OPS_ENABLE;

                    }
                    break;


               case MESSAGE_OPS_OPEN:
                    {
                         if(D) Log.d(TAG, "Recieved MESSAGE_OPS_OPEN");
                         mOpsObj = false;
                         /*calculate  the time stamp for the batch during OPS Open*/
                         mTimestamp = System.currentTimeMillis();
                         mOpsState = Constants.OPS_OPEN;

                    }
                    break;

               case MESSAGE_OPS_ACCESS_REQUESTED:
                    {
                        Bundle data = msg.getData();
                        if(D) Log.d(TAG, "MESSAGE_OPS_ACCESS_REQUESTED");
                        if(data != null) {
                            String remoteaddr = data.getString("remoteaddr");
                            String remotename = data.getString("remotename");
                            byte opcode    = data.getByte("operation");
                            String filename   = data.getString("filename");
                            String pType      = data.getString("pType");
                            int mimeType      = data.getInt("fmt");
                            int fileSize          = data.getInt("size");
                            processOpsAccessRequested(remoteaddr, remotename,
                                                      opcode,filename,
                                                      pType, mimeType,
                                                      fileSize);
                            mOpsState = Constants.OPS_ACCESS;
                            //send out timeout message
                           mHandler.sendMessageDelayed(mHandler.obtainMessage(MESSAGE_OPS_TIMEOUT)
                            , SESSION_TIMEOUT);
                            mOpsTimeoutMsgSent = true;
                        }
                    }
                    break;


                    case MESSAGE_OPS_CLOSE:
                        {
                           if(D) Log.d(TAG, "Recieved MESSAGE_OPS_CLOSE");
                           if(mOpsTransfer != null){
                              if(mOpsTimeoutMsgSent){
                                /**
                                remove the message from the queue ,
                                this can happen during timeout remote
                                message decides to abort instead of accept
                                **/
                                mHandler.removeMessages(MESSAGE_OPS_TIMEOUT);
                                mOpsTimeoutMsgSent = false;
                              }
                              mOpsTransfer.handleOpsClosed(mOpsObj,mOpsState);
                           }
                           mOpsClose = true;
                           mOpsObj = false;
                           mOpsState = Constants.OPS_ENABLE;
                        }
                    break;


                   case MESSAGE_OPS_OBJ:
                    {
                        if(D) Log.d(TAG, "Recieved MESSAGE_OPS_OBJ");
                        mOpsTransfer.handleOpsObjRecieved();
                        if(mOpsTransfer == null)
                            return;
                        mOpsObj = true;
                        mOpsClose = false;
                        mOpsState = Constants.OPS_OBJ;
                    }
                    break;


                    case MESSAGE_OPS_TRANSFER_IN_PROGRESS:
                    {
                        if(V) Log.v(TAG, "Recieved MESSAGE_OPS_TRANSFER_IN_PROGRESS");
                        int oper;
                        int totalSize;
                        int transferredSize;
                        Bundle data = msg.getData();
                         if((data != null) && (mOpsTransfer != null)) {
                            //remove the timeout message  and reset mOpsTimeoutMsgSent
                            if (mHandler != null && mOpsTimeoutMsgSent) {
                                mHandler.removeMessages(MESSAGE_OPS_TIMEOUT);
                                mOpsTimeoutMsgSent = false;
                            }
                            oper = data.getInt("operation");
                            totalSize = data.getInt("totalSize");
                            transferredSize = data.getInt("transferedSize");
                            mOpsTransfer.updateProgressOps(transferredSize);
                            mOpsState = Constants.OPS_PROG;
                          }
                    }
                    break;



               case MEDIA_SCANNED:
                   {
                   if (D) Log.d(TAG, "Recieved MEDIA_SCANNED");
                   if (V) Log.v(TAG, "Update mInfo.id " + msg.arg1 + " for data uri= "
                               + msg.obj.toString());
                   ContentValues updateValues = new ContentValues();
                   Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + msg.arg1);
                   updateValues.put(Constants.MEDIA_SCANNED, Constants.MEDIA_SCANNED_SCANNED_OK);
                   updateValues.put(BluetoothShare.URI, msg.obj.toString());
                   updateValues.put(BluetoothShare.MIMETYPE, getContentResolver().getType(
                           Uri.parse(msg.obj.toString())));
                   getContentResolver().update(contentUri, updateValues, null, null);
                   synchronized (OppService.this) {
                       mMediaScanInProgress = false;
                   }
                 }
                  break;

               case MEDIA_SCANNED_FAILED:
                 {
                  if(V) Log.v(TAG, "Update mInfo.id " + msg.arg1 + " for MEDIA_SCANNED_FAILED");
                   ContentValues updateValues1 = new ContentValues();
                   Uri contentUri1 = Uri.parse(BluetoothShare.CONTENT_URI + "/" + msg.arg1);
                   updateValues1.put(Constants.MEDIA_SCANNED,
                           Constants.MEDIA_SCANNED_SCANNED_FAILED);
                   getContentResolver().update(contentUri1, updateValues1, null, null);
                   synchronized (OppService.this) {
                       mMediaScanInProgress = false;
                   }
                 }
                 break;

                case MESSAGE_OPS_DISABLE:
                    {
                       if(D) Log.d(TAG, "Recieved MESSAGE_OPS_DISABLE");
                       if(mOpsState != Constants.OPS_ENABLE){
                           if(D) Log.d(TAG, "During Ongoing OPS transfer BT was switched off");
                           mOpsTransfer.handleOpsClosed(mOpsObj,mOpsState);
                       }
                    }
                break;

                case MESSAGE_OPS_TIMEOUT:
                    {
                       if(D) Log.d(TAG, "Recieved MESSAGE_OPS_TIMEOUT");
                       closeOps();
                       mOpsTimeoutMsgSent = false;
                    }
                break;

                case MESSAGE_OPC_TIMEOUT:
                    {
                       if(D) Log.d(TAG, "Recieved MESSAGE_OPC_TIMEOUT");
                       closeOpc();
                       mOpcTimeoutMsgSent = false;
                    }
                break;
            }
        }
    };

    static {
        classInitNative();
    }
    private native static void classInitNative();

    private native void initOppNative();

    private native void cleanupOppNative();

    private native boolean pushObjectNative(byte[] address, String filePath);

    private native boolean pushObjectNative(byte[] address, String filePath, int fd);

    private native boolean pullVcardNative(byte[] address, String filePath);

    private native boolean exchangeVcardNative(byte[] address,
                                                  String srcFilePath, String destFilePath);
    private native void closeOpcNative();

    private native void OpsAccessRspNative(byte op_code, boolean access, String filename);

    private native void closeOpsNative();

    public synchronized void closeOps(){
        Log.v(TAG , "closeOps");
        closeOpsNative();
    }

   /**
     * OpsAccessRsp provides service to response to OPS access request.
     *
     * @param op_code
     *            The operation code of the requested access, such as put, get,
     *            create directory, etc.
     * @param access
     *            Whether to grant access or deny. True to grant and False to
     *            deny.
     * @param filename
     *            The requested filename.
     */
    public synchronized void OpsAccessRsp(byte op_code, boolean access, String filename) {
        try {
            if(D) Log.d(TAG, "OpsAccessRsp , access : " + access);
            OpsAccessRspNative(op_code, access, filename);
        } catch (Exception e) {
            Log.e(TAG, "OpsAccessRspNative failed", e);
        }
    }

    /*
        processOpsAccessRequested , inserts the incoming file request in to the content provider
        it checks if there is enough space available in the file system , creates a path where the file have to be stored
        */
    private void processOpsAccessRequested(String remoteAddr,String remoteName, byte opCode,
                String fileName,String  pType,int mimeType, int fileSize) {


        boolean handover = BluetoothOppManager.getInstance(this).isWhitelisted(remoteAddr);

        if(V){
                Log.v(TAG, "processOpsAccessRequested");
                Log.v(TAG, "remoteAddr is : " + remoteAddr);
                Log.v(TAG, "remoteName is : " + remoteName);
                Log.v(TAG, "fileName is : " + fileName);
                Log.v(TAG, "mimeType = " + mimeType + " fileSize = " +fileSize);
             }

        if (opCode == OPP_OPER_PUSH) {

                    ContentValues values = new ContentValues();
                    values.put(BluetoothShare.FILENAME_HINT, fileName);
                    values.put(BluetoothShare.TOTAL_BYTES, fileSize);
                    values.put(BluetoothShare.DESTINATION, remoteAddr);
                    values.put(BluetoothShare.DIRECTION, BluetoothShare.DIRECTION_INBOUND);
                    values.put(BluetoothShare.TIMESTAMP, mTimestamp);

                    /*This case is hit for multiple incoming shares*/
                     if(mOpsObj && !mOpsClose){
                          if(V) Log.d(TAG, "Handling multiple incoming shares");
                        if(!handover){
                              OppReceiveFileInfo recieveFileInfo =
                              OppReceiveFileInfo.genAbsPathAndMime(fileName,fileSize);
                              if(recieveFileInfo.mStatus == 0){
                              values.put(BluetoothShare._DATA, recieveFileInfo.mAbsFileName);
                              values.put(BluetoothShare.MIMETYPE, recieveFileInfo.mMimeType);
                              values.put(BluetoothShare.USER_CONFIRMATION,
                                         BluetoothShare.USER_CONFIRMATION_AUTO_CONFIRMED);
                              }else{
                               Log.e(TAG,
                                    "fileSystem err occured during multple incoming shares ");
                               OpsAccessRsp((byte)OPP_OPER_PUSH ,
                                             false ,
                                             fileName);
                              }
                            }
                     }

                    if(handover){
                              Log.d(TAG, "The device is whitelisted for NFC Transfer");
                              OppReceiveFileInfo recieveFileInfo =
                              OppReceiveFileInfo.genAbsPathAndMime(fileName,fileSize);
                              if(recieveFileInfo.mStatus == 0){
                              values.put(BluetoothShare._DATA, recieveFileInfo.mAbsFileName);
                              values.put(BluetoothShare.MIMETYPE, recieveFileInfo.mMimeType);
                              values.put(BluetoothShare.USER_CONFIRMATION,
                                         BluetoothShare.USER_CONFIRMATION_HANDOVER_CONFIRMED);
                              OpsAccessRsp((byte)OPP_OPER_PUSH ,
                                           true ,
                                           recieveFileInfo.mAbsFileName);
                              handover = true;
                      }else{
                                Log.e(TAG,"During Nfc handover fileSystem error occured");
                                OpsAccessRsp((byte)OPP_OPER_PUSH ,
                                             false ,
                                             fileName);
                                return;
                           }

                     }
                    Uri contentUri =
                           this.getContentResolver().insert(BluetoothShare.CONTENT_URI, values);

                    /*if more than one file is a part of a incoming batch , we have to auto confirm
                                    with out  calling the incomingFileConfirm activity
                                    Below is the Logic  :
                                    For multiple incoming files , we recieve the stack events as followsadb reb
                            OPS_OPEN>> OPS_ACCESS>> OPS_PROG>>OPS_OBJ>>OPS_ACESS>>OPS_OBj>>OPS_CLOSE
                               so by checking if we get ACESS request after OBJ ,then it means the
                                     incoming file is for the same batch*/
                     if(V){
                         Log.v(TAG, "Current value of mOpsclose = " + mOpsClose);
                         Log.v(TAG, "Current value of mOpsObj = " + mOpsObj);
                     }
                     if(mOpsClose && !mOpsObj && !handover)
                     {
                        if(V) Log.v(TAG, "Calling incomingFileConfirmActivity");
                        Intent in =
                              new Intent(BluetoothShare.INCOMING_FILE_CONFIRMATION_REQUEST_ACTION);
                        in.setClassName(Constants.THIS_PACKAGE_NAME,
                                        BluetoothOppReceiver.class.getName());
                        this.sendBroadcast(in);
                      }

                    int localShareInfoId = Integer.parseInt(contentUri.getPathSegments().get(1));
                    if(V)Log.v(TAG, "insert contentUri: " + contentUri);
                    if(V)Log.v(TAG, "mLocalShareInfoId = " + localShareInfoId);

       } else if (opCode == OPP_OPER_PULL) {

                Log.e(TAG, "Currently we dont support pull");

       }

    }



    public String getName() {
        return "**"+TAG;
    }


    public boolean isAvailable() {
        return mNativeAvailable && !mOppServiceStopped && super.isAvailable();
    }

    @Override
    protected void finalize() {
        if(V) {
            synchronized (OppService.class) {
                Log.d(TAG, "FINALIZED. Class= " + this);
            }
        }
        super.finalize();
    }

    public void onScanCompleted(String path, Uri uri) {
        if (V) {
            Log.v(TAG, "MediaScannerConnection onScanCompleted");
            Log.v(TAG, "MediaScannerConnection path is " + path);
            Log.v(TAG, "MediaScannerConnection Uri is " + uri);
        }
    }


    int getConnectionState(BluetoothDevice device) {
        if (mOppClientDevices.get(device) == null) {
            return BluetoothProfile.STATE_DISCONNECTED;
        }
        return mOppClientDevices.get(device);
    }

    private BluetoothDevice getDevice(String address) {
        return mAdapter.getRemoteDevice(address);
    }


    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        List<BluetoothDevice> OppClientDevices = new ArrayList<BluetoothDevice>();

        for (BluetoothDevice device: mOppClientDevices.keySet()) {
            int inputDeviceState = getConnectionState(device);
            for (int state : states) {
                if (state == inputDeviceState) {
                    OppClientDevices.add(device);
                    break;
                }
            }
        }
        return OppClientDevices;
    }



    protected synchronized boolean start(){

        if(D) Log.d(TAG, "Starting OppService ");
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mShares = Lists.newArrayList();
        mBatchs = Lists.newArrayList();
        mObserver = new BluetoothShareContentObserver();
        getContentResolver().registerContentObserver(BluetoothShare.CONTENT_URI, true, mObserver);
        mBatchId = 1;
        mNotifier = new BluetoothOppNotification(this);
        mNotifier.mNotificationMgr.cancelAll();
        mNotifier.updateNotification();
        final ContentResolver contentResolver = getContentResolver();
        new Thread("trimDatabase") {
            public void run() {
                trimDatabase(contentResolver);
            }
        }.start();

        if (V) BluetoothOppPreference.getInstance(this).dump();

        updateFromProvider();

       try{
            if(D) Log.d(TAG, "Start()");
            initOppNative();
            mNativeAvailable = true;
            mOppServiceStopped = false;
            setOppService(this);
        }catch (Exception e){
            Log.e(TAG,"OppService start failed", e);
            return false;
        }
        return true;
    }


    private void updateFromProvider() {
        synchronized (OppService.this) {
            mPendingUpdate = true;
            if (mUpdateThread == null) {
                mUpdateThread = new UpdateThread();
                mUpdateThread.start();
            }
        }
    }


    private class UpdateThread extends Thread {
        public UpdateThread() {
            super("Bluetooth Share Service");
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            boolean keepService = false;
            for (;;) {
                synchronized (OppService.this) {
                    if (mUpdateThread != this) {
                        throw new IllegalStateException(
                                "multiple UpdateThreads in OppService");
                    }
                    if (V) Log.v(TAG, "pendingUpdate is :  " + mPendingUpdate );
                    if (!mPendingUpdate) {
                        mUpdateThread = null;
                        Log.e(TAG, "UpdateThread is Completed");
                        return;
                    }
                    mPendingUpdate = false;
                }
                Cursor cursor = null;
                try {
                    cursor = getContentResolver().query(BluetoothShare.CONTENT_URI, null, null,
                            null, BluetoothShare._ID);

                    if (cursor == null) {
                        Log.e(TAG, "Error : cursor is null ");
                        return;
                    }

                    cursor.moveToFirst();

                    int arrayPos = 0;

                    keepService = false;
                    boolean isAfterLast = cursor.isAfterLast();
                    int idColumn = cursor.getColumnIndexOrThrow(BluetoothShare._ID);
                    /*
                     * Walk the cursor and the local array to keep them in sync. The
                     * key to the algorithm is that the ids are unique and sorted
                     * both in the cursor and in the array, so that they can be
                     * processed in order in both sources at the same time: at each
                     * step, both sources point to the lowest id that hasn't been
                     * processed from that source, and the algorithm processes the
                     * lowest id from those two possibilities. At each step: -If the
                     * array contains an entry that's not in the cursor, remove the
                     * entry, move to next entry in the array. -If the array
                     * contains an entry that's in the cursor, nothing to do, move
                     * to next cursor row and next array entry. -If the cursor
                     * contains an entry that's not in the array, insert a new entry
                     * in the array, move to next cursor row and next array entry.
                     */

                    while (!isAfterLast || arrayPos < mShares.size()) {
                        if (isAfterLast) {
                            // We're beyond the end of the cursor but there's still
                            // some
                            // stuff in the local array, which can only be junk
                            if (V) Log.v(TAG, "Array update: trimming " +
                                    mShares.get(arrayPos).mId + " @ " + arrayPos);

                            if (shouldScanFile(arrayPos)) {
                                scanFile(null, arrayPos);
                            }
                            deleteShare(arrayPos); // this advances in the array
                        } else {
                            int id = cursor.getInt(idColumn);

                            if (arrayPos == mShares.size()) {
                                insertShare(cursor, arrayPos);
                                if (V) Log.v(TAG, "Array update: inserting " + id + " @ " + arrayPos);
                                if ( shouldScanFile(arrayPos) && (!scanFile(cursor, arrayPos))) {
                                    if(V)Log.v(TAG, "Inbound share scan is succesful");
                                }
                                if ( visibleNotification(arrayPos)) {
                                    if(V)Log.v(TAG, "Notification is visible");
                                }
                                if (needAction(arrayPos)) {
                                    if(V) Log.v(TAG, "Status is not yet completed so requires action");
                                }
                                ++arrayPos;
                                cursor.moveToNext();
                                isAfterLast = cursor.isAfterLast();
                            } else {
                                int arrayId = mShares.get(arrayPos).mId;
                                if (arrayId < id) {
                                    if (V) Log.v(TAG, "Array update: removing " + arrayId + " @ "
                                            + arrayPos);
                                    if ( shouldScanFile(arrayPos)) {
                                        scanFile(null, arrayPos);
                                    }
                                    deleteShare(arrayPos);
                                } else if (arrayId == id) {
                                    // This cursor row already exists in the stored
                                    // array
                                    Log.e(TAG , "ArrayId = id" + arrayId);
                                    updateShare(cursor, arrayPos, userAccepted);
                                    if ( shouldScanFile(arrayPos) && (!scanFile(cursor, arrayPos))) {
                                        if(V) Log.v(TAG, "Inbound share scan is succesful");
                                    }
                                    if ( visibleNotification(arrayPos)) {
                                        if(V) Log.v(TAG, "Notification is visible");
                                    }
                                    if (needAction(arrayPos)) {
                                        if(V) Log.v(TAG, "Status is not yet completed so requires action");
                                    }

                                    ++arrayPos;
                                    cursor.moveToNext();
                                    isAfterLast = cursor.isAfterLast();
                                } else {
                                    // This cursor entry didn't exist in the stored
                                    // array
                                    if (V) Log.v(TAG, "Arr update: appending " + id +
                                            " @ " + arrayPos);
                                    insertShare(cursor, arrayPos);
                                    if ( shouldScanFile(arrayPos) && (!scanFile(cursor, arrayPos))) {
                                        if(V) Log.v(TAG, "Inbound share scan is succesful");
                                    }
                                    if ( visibleNotification(arrayPos)) {
                                        if(V) Log.v(TAG, "Notification is visible");
                                    }
                                    if ( needAction(arrayPos)) {
                                        if(V) Log.v(TAG, "Status is not completed ,requires action");
                                    }
                                    ++arrayPos;
                                    cursor.moveToNext();
                                    isAfterLast = cursor.isAfterLast();
                                }
                            }
                        }
                    }
                    mNotifier.updateNotification();
                } catch (Exception e) {
                    Log.w(TAG, "Exception accessing Bluetoothshare DB");
                    if (V) e.printStackTrace();
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            }
        }
    }


    private void insertShare(Cursor cursor, int arrayPos) {
        String uriString = cursor.getString(cursor.getColumnIndexOrThrow(BluetoothShare.URI));
        Uri uri;
        if (uriString != null) {
            uri = Uri.parse(uriString);
            if(V) Log.v(TAG, "insertShare parsed URI: " + uri);
        } else {
            uri = null;
            Log.e(TAG, "insertShare found null URI at cursor!");
        }
        BluetoothOppShareInfo info = new BluetoothOppShareInfo(
                cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare._ID)),
                uri,
                cursor.getString(cursor.getColumnIndexOrThrow(BluetoothShare.FILENAME_HINT)),
                cursor.getString(cursor.getColumnIndexOrThrow(BluetoothShare._DATA)),
                cursor.getString(cursor.getColumnIndexOrThrow(BluetoothShare.MIMETYPE)),
                cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.DIRECTION)),
                cursor.getString(cursor.getColumnIndexOrThrow(BluetoothShare.DESTINATION)),
                cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.VISIBILITY)),
                cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.USER_CONFIRMATION)),
                cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.STATUS)),
                cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.TOTAL_BYTES)),
                cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.CURRENT_BYTES)),
                cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.TIMESTAMP)),
                cursor.getInt(cursor.getColumnIndexOrThrow(Constants.MEDIA_SCANNED))
                != Constants.MEDIA_SCANNED_NOT_SCANNED);

        if (V) {
            Log.v(TAG, "Service adding new entry");
            Log.v(TAG, "ID      : " + info.mId);
            Log.v(TAG, "URI     : " + info.mUri);
            Log.v(TAG, "HINT    : " + info.mHint);
            Log.v(TAG, "FILENAME: " + info.mFilename);
            Log.v(TAG, "MIMETYPE: " + info.mMimetype);
            Log.v(TAG, "DIRECTION: " + info.mDirection);
            Log.v(TAG, "DESTINAT: " + info.mDestination);
            Log.v(TAG, "VISIBILI: " + info.mVisibility);
            Log.v(TAG, "CONFIRM : " + info.mConfirm);
            Log.v(TAG, "STATUS  : " + info.mStatus);
            Log.v(TAG, "TOTAL   : " + info.mTotalBytes);
            Log.v(TAG, "CURRENT : " + info.mCurrentBytes);
            Log.v(TAG, "TIMESTAMP : " + info.mTimestamp);
            Log.v(TAG, "SCANNED : " + info.mMediaScanned);
            Log.v(TAG, "Ready to start " + info.isReadyToStart());
        }

        mShares.add(arrayPos, info);


        /* Mark the info as failed if it's in invalid status */
        if (info.isObsolete()) {
            Constants.updateShareStatus(this, info.mId, BluetoothShare.STATUS_UNKNOWN_ERROR);
        }

        /*
         * Add info into a batch. The logic is
         * 1) Only add valid and readyToStart info
         * 2) If there is no batch, create a batch and insert this transfer into batch,
         * then run the batch
         * 3) If there is existing batch and timestamp match, insert transfer into batch
         * 4) If there is existing batch and timestamp does not match, create a new batch and
         * put in queue
         */
        if (info.isReadyToStart()) {
            if (info.mDirection == BluetoothShare.DIRECTION_OUTBOUND) {
                /* check if the file exists */
                BluetoothOppSendFileInfo sendFileInfo = BluetoothOppUtility.getSendFileInfo(
                        info.mUri);
                if (sendFileInfo == null) {
                    Log.e(TAG, "Can't send file , sendFileInfo is null for the mId : " + info.mId);
                    Constants.updateShareStatus(this, info.mId, BluetoothShare.STATUS_BAD_REQUEST);
                    BluetoothOppUtility.closeSendFileInfo(info.mUri);
                    return;
                }
            }
            if (mBatchs.size() == 0) {
                Log.e(TAG, "Creating a new batch");
                BluetoothOppBatch newBatch = new BluetoothOppBatch(this, info);
                newBatch.mId = mBatchId;
                mBatchId++;
                mBatchs.add(newBatch);
                if (info.mDirection == BluetoothShare.DIRECTION_OUTBOUND) {
                    if (V) Log.v(TAG, "Service create new Batch " + newBatch.mId
                                + " for OUTBOUND info " + info.mId);
                    if(D) Log.d(TAG, "Intializing OpcTransfer ");
                    mOpcTransfer = new OpcTransfer(this, mPowerManager, newBatch);
                } else if (info.mDirection == BluetoothShare.DIRECTION_INBOUND) {
                    if (V) Log.v(TAG, "Service create new Batch " + newBatch.mId
                                + " for INBOUND info " + info.mId);
                    if(D )Log.d(TAG, "Intializing OpsTransfer ");
                    mOpsTransfer = new OpsTransfer(this, mPowerManager, newBatch);
                }

                if (info.mDirection == BluetoothShare.DIRECTION_OUTBOUND ) {
                    if (V) Log.v(TAG, "Service start transfer new Batch " + newBatch.mId
                                + " for info " + info.mId);
                    mOpcTransfer.intiate();
                    mOpcBatchRunning = true;
                } else if (info.mDirection == BluetoothShare.DIRECTION_INBOUND
                        && mOpsTransfer != null) {
                        if(info == null) Log.d(TAG,"This is a bad case and should not occur");
                    if (V) Log.v(TAG, "Service start server transfer new Batch " + newBatch.mId
                                + " for info " + info.mId);
                    mOpsTransfer.intiate();
                }

            } else {
                int i = findBatchWithTimeStamp(info.mTimestamp);
                if (i != -1) {
                    if (V) Log.v(TAG, "Service add info " + info.mId + " to existing batch "
                                + mBatchs.get(i).mId);
                    mBatchs.get(i).addShare(info);
                } else {
                    // There is ongoing batch
                    if( info.mDirection == BluetoothShare.DIRECTION_INBOUND ){
                        if(V) Log.v(TAG, "Executing an OnGoing Batch for OPS");
                        BluetoothOppBatch newBatch = new BluetoothOppBatch(this, info);
                        newBatch.mId = mBatchId;
                        mBatchId++;
                        mBatchs.add(newBatch);
                        if (V) Log.v(TAG, "Service add OPS Batch " + newBatch.mId + " for info " +
                                info.mId);
                        mOpsTransfer = new OpsTransfer(this, mPowerManager, newBatch);
                        mOpsTransfer.intiate();
                    }else{
                        if(V) Log.v(TAG, "Executing an OnGoing Batch for OPC");
                        BluetoothOppBatch newBatch = new BluetoothOppBatch(this, info);
                        newBatch.mId = mBatchId;
                        mBatchId++;
                        mBatchs.add(newBatch);
                        if(!mOpcBatchRunning){
                            mOpcTransfer = new OpcTransfer(this, mPowerManager, newBatch);
                            if (V) Log.v(TAG, "Service start transfer new Batch " + newBatch.mId
                                + " for info " + info.mId);
                            mOpcTransfer.intiate();
                            mOpcBatchRunning = true;
                        }
                        if (V) Log.v(TAG, "Service add OPC Batch " + newBatch.mId + " for info " +
                                info.mId);
                    }

                }
            }
        }
    }

    private boolean shouldScanFile(int arrayPos) {
        BluetoothOppShareInfo info = mShares.get(arrayPos);
        return BluetoothShare.isStatusSuccess(info.mStatus)
                && info.mDirection == BluetoothShare.DIRECTION_INBOUND && !info.mMediaScanned &&
                info.mConfirm != BluetoothShare.USER_CONFIRMATION_HANDOVER_CONFIRMED;
    }

    private String stringFromCursor(String old, Cursor cursor, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        if (old == null) {
            return cursor.getString(index);
        }
        if (mNewChars == null) {
            mNewChars = new CharArrayBuffer(128);
        }
        cursor.copyStringToBuffer(index, mNewChars);
        int length = mNewChars.sizeCopied;
        if (length != old.length()) {
            return cursor.getString(index);
        }
        if (mOldChars == null || mOldChars.sizeCopied < length) {
            mOldChars = new CharArrayBuffer(length);
        }
        char[] oldArray = mOldChars.data;
        char[] newArray = mNewChars.data;
        old.getChars(0, length, oldArray, 0);
        for (int i = length - 1; i >= 0; --i) {
            if (oldArray[i] != newArray[i]) {
                return new String(newArray, 0, length);
            }
        }
        return old;
    }
    private int findBatchWithTimeStamp(long timestamp) {
        for (int i = mBatchs.size() - 1; i >= 0; i--) {
            if (mBatchs.get(i).mTimestamp == timestamp) {
                return i;
            }
        }
        return -1;
    }


    private void deleteShare(int arrayPos) {
        BluetoothOppShareInfo info = mShares.get(arrayPos);

     /*
         * Delete arrayPos from a batch. The logic is
         * 1) Search existing batch for the info
         * 2) cancel the batch
         * 3) If the batch become empty delete the batch
         */
         /*During progress if user stops the transfer from UI , delete of BluetoothOppProvider is called
                   this will trigger call to deleteShare in OppService
                  close Opc or Ops based on the direction only if OPC or OPS is in open state
                  */
        if(info.mDirection == BluetoothShare.DIRECTION_OUTBOUND){

            if(mOpcState != Constants.OPC_CLOSE){
               if(D) Log.d(TAG, "User cancelled the OPC progress from UI");
               BluetoothOppUtility.closeSendFileInfo(info.mUri);
               closeOpc();
            }

        }

        if(info.mDirection == BluetoothShare.DIRECTION_INBOUND){

            if(mOpsState != Constants.OPS_CLOSE){
                 if(D) Log.d(TAG, "User cancelled the OPS progress from UI");
                 closeOps();
            }

        }

        int i = findBatchWithTimeStamp(info.mTimestamp);
        if (i != -1) {
            BluetoothOppBatch batch = mBatchs.get(i);
            if (batch.hasShare(info)) {
                if (V) Log.v(TAG, "Service cancel batch for share " + info.mId);
                batch.cancelBatch();
            }
            if (batch.isEmpty()) {
                if (V) Log.v(TAG, "Service remove batch  " + batch.mId);
                removeBatch(batch);
            }
        }
        mShares.remove(arrayPos);
    }


    private void updateShare(Cursor cursor, int arrayPos, boolean userAccepted) {
        BluetoothOppShareInfo info = mShares.get(arrayPos);
        int statusColumn = cursor.getColumnIndexOrThrow(BluetoothShare.STATUS);

        info.mId = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare._ID));
        if (info.mUri != null) {
            info.mUri = Uri.parse(stringFromCursor(info.mUri.toString(), cursor,
                    BluetoothShare.URI));
        } else {
            Log.w(TAG, "updateShare() called for ID " + info.mId + " with null URI");
        }
        info.mHint = stringFromCursor(info.mHint, cursor, BluetoothShare.FILENAME_HINT);
        info.mFilename = stringFromCursor(info.mFilename, cursor, BluetoothShare._DATA);
        info.mMimetype = stringFromCursor(info.mMimetype, cursor, BluetoothShare.MIMETYPE);
        info.mDirection = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.DIRECTION));
        info.mDestination = stringFromCursor(info.mDestination, cursor, BluetoothShare.DESTINATION);
        int newVisibility = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.VISIBILITY));

        boolean confirmed = false;
        int newConfirm = cursor.getInt(cursor
                .getColumnIndexOrThrow(BluetoothShare.USER_CONFIRMATION));
        if (info.mVisibility == BluetoothShare.VISIBILITY_VISIBLE
                && newVisibility != BluetoothShare.VISIBILITY_VISIBLE
                && (BluetoothShare.isStatusCompleted(info.mStatus)
                || newConfirm == BluetoothShare.USER_CONFIRMATION_PENDING)) {
            mNotifier.mNotificationMgr.cancel(info.mId);
        }

        info.mVisibility = newVisibility;

        if (info.mConfirm == BluetoothShare.USER_CONFIRMATION_PENDING
                && newConfirm == BluetoothShare.USER_CONFIRMATION_CONFIRMED
                && info.mDirection == BluetoothShare.DIRECTION_INBOUND) {
                 confirmed = true;
                    if(V) Log.v(TAG, "user confirmation is obtained send OpsAccessRsp");
                    /*create the file , if succesfull , update name and mime */
                    OppReceiveFileInfo recieveFileInfo =
                    OppReceiveFileInfo.genAbsPathAndMime(info.mHint,info.mTotalBytes);
                    if(recieveFileInfo.mStatus == 0){
                        if(D) Log.d(TAG, "Created filePath : " + recieveFileInfo.mAbsFileName);
                        OpsAccessRsp((byte)OPP_OPER_PUSH, true, recieveFileInfo.mAbsFileName);
                        ContentValues values = new ContentValues();
                        values.put(BluetoothShare._DATA, recieveFileInfo.mAbsFileName);
                        values.put(BluetoothShare.MIMETYPE, recieveFileInfo.mMimeType);
                        Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + info.mId);
                        getContentResolver().update(contentUri, values,null, null);
                     }else{
                            Log.e(TAG,"fileSystem error while creating file");
                            OpsAccessRsp((byte)OPP_OPER_PUSH, false, info.mHint);
                        if(mOpsTransfer != null){
                            mOpsTransfer.handleFileSysError(info);
                         }
                     }
          }


        /*confirmation denied is valid only for Inbound Transfers  */
        if( info.mDirection == BluetoothShare.DIRECTION_INBOUND
            && info.mConfirm == BluetoothShare.USER_CONFIRMATION_PENDING
            && newConfirm == BluetoothShare.USER_CONFIRMATION_DENIED){
                Log.d(TAG, "User denied the access request, sending negative OpsAccessRsp");
                OpsAccessRsp((byte)OPP_OPER_PUSH ,false , info.mHint);
                if(mOpsTransfer != null){
                    mOpsTransfer.handleOpsDenied(info);
                }
        }
        info.mConfirm = cursor.getInt(cursor
                .getColumnIndexOrThrow(BluetoothShare.USER_CONFIRMATION));
        int newStatus = cursor.getInt(statusColumn);
        if (!BluetoothShare.isStatusCompleted(info.mStatus)
                && BluetoothShare.isStatusCompleted(newStatus)) {
            mNotifier.mNotificationMgr.cancel(info.mId);
        }

        info.mStatus = newStatus;
        info.mTotalBytes = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.TOTAL_BYTES));
        info.mCurrentBytes = cursor.getInt(cursor
                .getColumnIndexOrThrow(BluetoothShare.CURRENT_BYTES));
        info.mTimestamp = cursor.getInt(cursor.getColumnIndexOrThrow(BluetoothShare.TIMESTAMP));
        info.mMediaScanned = (cursor.getInt(cursor.getColumnIndexOrThrow(Constants.MEDIA_SCANNED))
                             != Constants.MEDIA_SCANNED_NOT_SCANNED);

        if (confirmed) {
            if (V) Log.v(TAG, "Service handle info " + info.mId + " confirmed");
            /* Inbounds transfer get user confirmation, so we start it */
            int i = findBatchWithTimeStamp(info.mTimestamp);
            if (i != -1) {
                BluetoothOppBatch batch = mBatchs.get(i);
                if (mOpsTransfer != null && batch.mId == mOpsTransfer.getBatchId()) {
                    if(V)Log.v(TAG, "multple incoming is handled by processOpsAccessRequested");
                }
            }
        }
        int i = findBatchWithTimeStamp(info.mTimestamp);
        if (i != -1) {
            BluetoothOppBatch batch = mBatchs.get(i);
            if (batch.mStatus == Constants.BATCH_STATUS_FINISHED
                    || batch.mStatus == Constants.BATCH_STATUS_FAILED) {
                if (V) Log.v(TAG, "Batch " + batch.mId + " is finished");
                if (batch.mDirection == BluetoothShare.DIRECTION_OUTBOUND) {
                    if (mOpcTransfer== null) {
                        Log.e(TAG, "Unexpected error! mTransfer is null");
                    } else if (batch.mId == mOpcTransfer.getBatchId()) {
                       if(D) Log.d(TAG, "All OpcTransfer batch is completed");
                    } else {
                        Log.e(TAG, "Unexpected error! batch id " + batch.mId
                                + " doesn't match mTransfer id " + mOpcTransfer.getBatchId());
                    }
                    mOpcTransfer = null;
                } else {/*currently it is processing an INBOUND*/
                    if (mOpsTransfer == null) {
                        Log.e(TAG, "Unexpected error! mOpsTransfer is null");
                    } else if (batch.mId == mOpsTransfer.getBatchId()) {
                        if(D) Log.d(TAG, "All OpsTransfer batch is completed");
                    } else {
                        Log.e(TAG, "Unexpected error! batch id " + batch.mId
                                + " doesn't match mOpsTransfer id "
                                + mOpsTransfer.getBatchId());
                    }
                    mOpsTransfer = null;
                }
                removeBatch(batch);
            }
        }
    }


    private void removeBatch(BluetoothOppBatch batch) {
        if (V) Log.v(TAG, "Remove batch " + batch.mId);
        if(batch.mDirection == BluetoothShare.DIRECTION_OUTBOUND){
            mOpcBatchRunning = false;
        }
        mBatchs.remove(batch);
        BluetoothOppBatch nextBatch;
        if (mBatchs.size() > 0) {
            for (int i = 0; i < mBatchs.size(); i++) {
                // we have a running batch
                nextBatch = mBatchs.get(i);
                if (nextBatch.mStatus == Constants.BATCH_STATUS_RUNNING) {
                    if (nextBatch.mDirection == BluetoothShare.DIRECTION_OUTBOUND){
                        mOpcBatchRunning = true;
                    }
                    return;
                } else {
                    // just finish a transfer, start pending outbound transfer
                    if (nextBatch.mDirection == BluetoothShare.DIRECTION_OUTBOUND) {
                        if (V) Log.v(TAG, "Start pending outbound batch " + nextBatch.mId);
                        mOpcTransfer = new OpcTransfer(this,mPowerManager,nextBatch);
                        mOpcTransfer.intiate();
                        mOpcBatchRunning = true;
                        return;
                    } else if (nextBatch.mDirection == BluetoothShare.DIRECTION_INBOUND
                            && mOpsTransfer != null) {
                        // have to support pending inbound transfer
                        // if an outbound transfer and incoming socket happens together
                        // check how to handle this case
                        if (V) Log.v(TAG, "Start pending inbound batch " + nextBatch.mId);
                        mOpsTransfer = new OpsTransfer(this, mPowerManager, nextBatch);
                        mOpsTransfer.intiate();
                        if (nextBatch.getPendingShare().mConfirm ==
                                BluetoothShare.USER_CONFIRMATION_CONFIRMED) {
                            Log.e(TAG, "Next batch Pending share is confirmed");
                        }
                        return;
                    }
                }
            }
        }
    }

    private boolean needAction(int arrayPos) {
        BluetoothOppShareInfo info = mShares.get(arrayPos);
        if (BluetoothShare.isStatusCompleted(info.mStatus)) {
            return false;
        }
        return true;
    }

    private boolean visibleNotification(int arrayPos) {
        BluetoothOppShareInfo info = mShares.get(arrayPos);
        return info.hasCompletionNotification();
    }

    private boolean scanFile(Cursor cursor, int arrayPos) {
        BluetoothOppShareInfo info = mShares.get(arrayPos);
        synchronized (OppService.this) {
            if (D) Log.d(TAG, "Scanning file " + info.mFilename);
            if (!mMediaScanInProgress) {
                mMediaScanInProgress = true;
                new MediaScannerNotifier(this, info, mHandler);
                return true;
            } else {
                return false;
            }
        }
    }


    private static class MediaScannerNotifier implements MediaScannerConnectionClient {

        private MediaScannerConnection mConnection;

        private BluetoothOppShareInfo mInfo;

        private Context mContext;

        private Handler mCallback;

        public MediaScannerNotifier(Context context, BluetoothOppShareInfo info,
                                       Handler handler) {
            mContext = context;
            mInfo = info;
            mCallback = handler;
            mConnection = new MediaScannerConnection(mContext, this);
            if (V) Log.v(TAG, "Connecting to MediaScannerConnection ");
            mConnection.connect();
        }

        public void onMediaScannerConnected() {
            if (V) Log.v(TAG, "MediaScannerConnection onMediaScannerConnected");
            mConnection.scanFile(mInfo.mFilename, mInfo.mMimetype);
        }

        public void onScanCompleted(String path, Uri uri) {
            try {
                if (V) {
                    Log.v(TAG, "MediaScannerConnection onScanCompleted");
                    Log.v(TAG, "MediaScannerConnection path is " + path);
                    Log.v(TAG, "MediaScannerConnection Uri is " + uri);
                }
                if (uri != null) {
                    Message msg = Message.obtain();
                    msg.setTarget(mCallback);
                    msg.what = MEDIA_SCANNED;
                    msg.arg1 = mInfo.mId;
                    msg.obj = uri;
                    msg.sendToTarget();
                } else {
                    Message msg = Message.obtain();
                    msg.setTarget(mCallback);
                    msg.what = MEDIA_SCANNED_FAILED;
                    msg.arg1 = mInfo.mId;
                    msg.sendToTarget();
                }
            } catch (Exception ex) {
                Log.v(TAG, "!!!MediaScannerConnection exception: " + ex);
            } finally {
                if (V) Log.v(TAG, "MediaScannerConnection disconnect");
                mConnection.disconnect();
            }
        }
    }



// Run in a background thread at boot.
    private static void trimDatabase(ContentResolver contentResolver) {

        final String INVISIBLE = BluetoothShare.VISIBILITY + "=" + BluetoothShare.VISIBILITY_HIDDEN;
        // remove the invisible/complete/outbound shares
        final String WHERE_INVISIBLE_COMPLETE_OUTBOUND = BluetoothShare.DIRECTION + "="
                + BluetoothShare.DIRECTION_OUTBOUND + " AND " + BluetoothShare.STATUS + ">="
                + BluetoothShare.STATUS_SUCCESS + " AND " + INVISIBLE;
        int delNum = contentResolver.delete(BluetoothShare.CONTENT_URI,
                WHERE_INVISIBLE_COMPLETE_OUTBOUND, null);
        if (V) Log.v(TAG, "Deleted complete outbound shares, number =  " + delNum);

        // remove the invisible/finished/inbound/failed shares
        final String WHERE_INVISIBLE_COMPLETE_INBOUND_FAILED = BluetoothShare.DIRECTION + "="
                + BluetoothShare.DIRECTION_INBOUND + " AND " + BluetoothShare.STATUS + ">"
                + BluetoothShare.STATUS_SUCCESS + " AND " + INVISIBLE;
        delNum = contentResolver.delete(BluetoothShare.CONTENT_URI,
                WHERE_INVISIBLE_COMPLETE_INBOUND_FAILED, null);
        if (V) Log.v(TAG, "Deleted complete inbound failed shares, number = " + delNum);

        // Only keep the inbound and successful shares for LiverFolder use
        // Keep the latest 1000 to easy db query
        final String WHERE_INBOUND_SUCCESS = BluetoothShare.DIRECTION + "="
                + BluetoothShare.DIRECTION_INBOUND + " AND " + BluetoothShare.STATUS + "="
                + BluetoothShare.STATUS_SUCCESS + " AND " + INVISIBLE;
        Cursor cursor = null;
        try {
            cursor = contentResolver.query(BluetoothShare.CONTENT_URI, new String[] {
                BluetoothShare._ID
            }, WHERE_INBOUND_SUCCESS, null, BluetoothShare._ID); // sort by id

            if (cursor == null) {
                return;
            }

            int recordNum = cursor.getCount();
            if (recordNum > Constants.MAX_RECORDS_IN_DATABASE) {
                int numToDelete = recordNum - Constants.MAX_RECORDS_IN_DATABASE;

                if (cursor.moveToPosition(numToDelete)) {
                    int columnId = cursor.getColumnIndexOrThrow(BluetoothShare._ID);
                    long id = cursor.getLong(columnId);
                    delNum = contentResolver.delete(BluetoothShare.CONTENT_URI, BluetoothShare._ID
                            + " < " + id, null);
                    if (V)
                        Log.v(TAG, "Deleted old inbound success share: " + delNum);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Exception accessing Bluetoothshare DB");
            if (V) e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }



    protected synchronized boolean stop() {
        Log.d(TAG,"Stopping Bluetooth OppService");
        mOppServiceStopped = true;
        /**  Since initBinder returns null, clean up  will be called from Stop()       */
        cleanup();
        return true;
    }


    protected IProfileServiceBinder initBinder() {
         Log.v(TAG, "Opp initBinder");
         return null;
     }



    private void onOpcEnable(){
        Log.v(TAG, "onOpcEnable() is called ");
        Message msg = mHandler.obtainMessage(MESSAGE_OPC_ENABLE);
        isStateRunning = true;
        mHandler.sendMessage(msg);
    }


    private void onOpcOpen(){
        Log.d(TAG, "onOpcOpen() is called ");
        Message msg = mHandler.obtainMessage(MESSAGE_OPC_OPEN);
        mHandler.sendMessage(msg);
    }


        private void onOpcStateChange(byte[]address , int conState , int state){

        Log.d(TAG, "onOpcStateChange()") ;
        if(conState == OPP_STATE_ENABLE){
            Message msg = mHandler.obtainMessage(MESSAGE_OPC_ENABLE);
            isStateRunning = true;
            mHandler.sendMessage(msg);
        }
        else if(conState == OPP_STATE_OPEN){
            Message msg = mHandler.obtainMessage(MESSAGE_OPC_OPEN);
            msg.obj = getDevice(address);
            msg.arg1 = state;
            mHandler.sendMessage(msg);
        }else{
            /*from lower layer we either send OPC_OPEN or OPC_CLOSE*/
            Message msg = mHandler.obtainMessage(MESSAGE_OPC_CLOSE);
            msg.obj = getDevice(address);
            msg.arg1 = state;
            mHandler.sendMessage(msg);
        }

    }

    private void onOpcClose(byte[]address , int state){
        Log.d(TAG, "onOpcClose() address:"+address+", state:"+state);
        Message msg = mHandler.obtainMessage(MESSAGE_OPC_CLOSE);
        msg.obj = getDevice(address);
        msg.arg1 = state;
        mHandler.sendMessage(msg);
    }

    private void onOpcProgress(byte[] address, int operation,int totalSize, int currentSize){
        if(V) Log.v(TAG, "onOpcProgress() address:"+address);
        Message msg = mHandler.obtainMessage(MESSAGE_OPC_TRANSFER_IN_PROGRESS);

        Bundle data = new Bundle();
        data.putByteArray("BdAddress" , address);
        data.putInt("operation", operation);
        data.putInt("totalSize", totalSize);
        data.putInt("transferedSize",currentSize);
        msg.setData(data);
        mHandler.sendMessage(msg);

    }


    private void onOpcObjectPushed(int status){
        Log.d(TAG, "onOpcObjectPushed() :"+status);
        Message msg = mHandler.obtainMessage(MESSAGE_OPC_OBJ_PUSHED);
        msg.arg1 = status;
        mHandler.sendMessage(msg);

    }

    private void onOpcObjectReceived(int status){
        Log.d(TAG, "onOpcObjectReceived() :"+status);
        Message msg = mHandler.obtainMessage(MESSAGE_OPS_OBJ);
        msg.arg1 = status;
        mHandler.sendMessage(msg);
    }

    private void onOpsStateChange (int state){

        Log.d(TAG, "onOpsStateChange() :"+state);

        if(state == OPP_STATE_ENABLE){
            Message msg = mHandler.obtainMessage(MESSAGE_OPS_ENABLE);
            msg.arg1 = state;
            mHandler.sendMessage(msg);

        }else if(state == OPP_STATE_OPEN){

            Message msg = mHandler.obtainMessage(MESSAGE_OPS_OPEN);
            msg.arg1 = state;
            mHandler.sendMessage(msg);

        }else if(state == OPP_STATE_CLOSE){

            Message msg = mHandler.obtainMessage(MESSAGE_OPS_CLOSE);
            msg.arg1 = state;
            mHandler.sendMessage(msg);

        }else{
            Message msg = mHandler.obtainMessage(MESSAGE_OPS_DISABLE);
            msg.arg1 = state;
            mHandler.sendMessage(msg);
        }

    }


    private void onOpsProgress (byte[] address, int operation,int totalSize, int currentSize){

        if(V) Log.v(TAG, "onOpsProgress()");
        Message msg = mHandler.obtainMessage(MESSAGE_OPS_TRANSFER_IN_PROGRESS);
        Bundle data = new Bundle();
        data.putByteArray("BdAddress" , address);
        data.putInt("operation", operation);
        data.putInt("totalSize", totalSize);
        data.putInt("transferedSize",currentSize);
        msg.setData(data);
        mHandler.sendMessage(msg);
    }

    private void onOpsAccessRequest (String remoteAddress, String remoteDeviceName ,
                                           byte operation ,String fileName,
                                           String pType ,int fmt ,int fileSize){

        if(V) Log.d(TAG, "onOpsAccessRequest() remoteAddress:"+remoteAddress);
        Log.d(TAG, "onOpsAccessRequest");
        Message msg = mHandler.obtainMessage(MESSAGE_OPS_ACCESS_REQUESTED);
        Bundle data = new Bundle();
        data.putString("remoteaddr", remoteAddress);
        data.putString("remotename", remoteDeviceName);
        data.putByte("operation", operation);
        data.putString("filename", fileName);
        data.putString("pType", pType);
        data.putInt("fmt", fmt);
        data.putInt("size", fileSize);
        msg.setData(data);
        mHandler.sendMessage(msg);

    }

    private void onOpsObjectReceived (int mimeType, String fileName){

        if(D) Log.d(TAG, "onOpsObjectReceived() mimeType :" + mimeType + ",fileName = " + fileName);
        Message msg = mHandler.obtainMessage(MESSAGE_OPS_OBJ);
        Bundle data = new Bundle();
        data.putInt("mimeType", mimeType);
        data.putString("fileName", fileName);
        msg.setData(data);
        mHandler.sendMessage(msg);
    }


    private byte[] getByteAddress(BluetoothDevice device) {
        return Utils.getBytesFromAddress(device.getAddress());
    }

    public synchronized boolean pushObject(BluetoothDevice remoteDevice , String filePath)
                            throws RemoteException{
        try{
            if(D) Log.d(TAG, "pushObject (" + remoteDevice.getAddress() + "," + filePath + ")");
            pushObjectNative(getByteAddress(remoteDevice), filePath);
        }catch (Throwable t){
            Log.e(TAG , "Error pushObject object" , t);
        }
        return true;

    }

    public synchronized boolean pushObject(BluetoothDevice remoteDevice ,
                                           String filePath,int fd) throws RemoteException{
        try{
            Log.d(TAG, "pushObject (" + remoteDevice.getAddress() + "," +filePath +" ," + fd + ")");
            pushObjectNative(getByteAddress(remoteDevice), filePath, fd);
        }catch (Throwable t){
            Log.e(TAG , "Error pushObject object" , t);
        }
        return true;
    }

    public synchronized boolean pullVcard(BluetoothDevice remoteDevice , String filePath)
        throws RemoteException{

        try{
            if(V) Log.v(TAG, "pullVcard (" + remoteDevice.getAddress()+" ," + filePath + ")");
            pullVcardNative(getByteAddress(remoteDevice), filePath);
        }catch (Throwable t){
            Log.e(TAG , "Error pullVcard object" , t);
        }

        return true;

    }


    public synchronized boolean exchangeVcard( BluetoothDevice remoteDevice, String srcFilePath,
                                                String destFilePath)throws RemoteException{
        try{
            if(V) Log.v(TAG, "exchangeVcard (" + remoteDevice.getAddress()+" ," + srcFilePath +
                                        " ," + destFilePath + ")");
            exchangeVcardNative(getByteAddress(remoteDevice), srcFilePath ,destFilePath);
        }catch (Throwable t){
            Log.e(TAG , "Error exchangeVcard object" , t);
        }
        return true;
    }


    public synchronized void closeOpc(){
        if(D) Log.d(TAG , "closeOpc");
        closeOpcNative();
    }


    protected boolean cleanup() {
        if(D) Log.d(TAG, "cleanup OPP Service");
        if (mNativeAvailable) {
           try {
               cleanupOppNative();
           } catch (Throwable t) {
               Log.e(TAG, "Unable to cleanup OPP Service", t);
           }
           mNativeAvailable=false;
        }
        if(mObserver != null){
            try{
                 getContentResolver().unregisterContentObserver(mObserver);
            }catch(Throwable t){

            }finally {
                mObserver = null;
            }
        }
        if(mHandler != null) {
            if(D) Log.d(TAG,"remove callbacks and handler");
            mHandler.removeCallbacksAndMessages(null);
        }
        clearOppService();
        if(D) Log.d(TAG, "cleanup done");
        return true;

   }

   public static synchronized OppService getOppService(){
       if (sOppService != null && sOppService.isAvailable()) {
           if(V) Log.v(TAG, "getOppService(): returning " + sOppService);
           return sOppService;
       }

       if ((sOppService == null) || !(sOppService.isAvailable())) {
            Log.e(TAG, "either OppService is null or OppService is not available");
       }
       return null;
   }

   private static synchronized void setOppService(OppService instance) {
       if (instance != null && instance.isAvailable()) {
            if(V)Log.v(TAG, "setOppService(): set to: " + instance);
           sOppService = instance;
       }
       else
       {
            if (sOppService == null) {
               if(D) Log.d(TAG, "setOppService(): service not available");
            } else if (!sOppService.isAvailable()) {
               if(D) Log.d(TAG,"setOppService(): service is cleaning up");
            }
       }
   }
   private static synchronized void clearOppService() {
       sOppService = null;
   }

}




