/* Copyright 2009-2012 Broadcom Corporation
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
package com.broadcom.bt.service.ftp;

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

/**
 * Provides Bluetooth FTP Server profile, as a service in
 * the Bluetooth application.
 * @hide
 */
public class FTPService extends ProfileService implements MediaScannerConnectionClient {
    private static final String TAG = "BluetoothFTPService";
    private static final boolean V = true;

    private static boolean isStateRunning = false;
    private static String ftpRootPath = "/storage/emulated/legacy/";

    MediaScannerConnection mMediaScanner;

    private static final int MESSAGE_SERVER_ACCESS_RESP = 1;
    private static final int MESSAGE_SERVER_AUTHEN_RSP = 2;
    private static final int MESSAGE_ON_CONNECT_STATE_CHANGED = 3;
    private static final int MESSAGE_ON_FTPS_AUTH_REQUESTED = 4;
    private static final int MESSAGE_ON_FTPS_ACCESS_REQUESTED = 5;
    private static final int MESSAGE_ON_GET_COMPLETED = 6;
    private static final int MESSAGE_ON_PUT_COMPLETED = 7;
    private static final int MESSAGE_ON_DEL_COMPLETED = 8;
    private static final int MESSAGE_TRANSFER_IN_PROGRESS = 9;

    // Constants matching Hal header file bt_fts.h
    // btfts_connection_state_t
    private static final int CONN_STATE_CLOSE = 0;
    private static final int CONN_STATE_OPEN = 1;

    /* The list of all registered client callbacks, if callbacks are being used */
    private RemoteCallbackList<IBluetoothFTPCallback> mCallbacks;

    private Map<BluetoothDevice, Integer> mFtpClientDevices;
    private boolean mNativeAvailable;
    private static boolean mFtpServerStopped = false;
    private BluetoothAdapter mAdapter;

    private String filePathForMediaScan = null;
    private Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_ON_CONNECT_STATE_CHANGED:
                {
                    BluetoothDevice device = (BluetoothDevice) msg.obj;
                    int halState = msg.arg1;
                    int newState = (halState == CONN_STATE_OPEN)?BluetoothProfile.STATE_CONNECTED:
                        BluetoothProfile.STATE_DISCONNECTED;
                    Integer prevStateInteger = (mFtpClientDevices.isEmpty())?null:
                        mFtpClientDevices.get(device);
                    int prevState = (prevStateInteger == null)?BluetoothProfile.STATE_DISCONNECTED:
                        prevStateInteger;
                    broadcastConnectionState(device, newState, prevState);
                }
                    break;
                case MESSAGE_ON_FTPS_AUTH_REQUESTED:
                {
                    Bundle data = msg.getData();
                    if(data != null) {
                        byte userid_length = data.getByte("userid_length");
                        String userid = data.getString("userid");
                        boolean required = data.getBoolean("required");
                        processFtpServerAuthen(userid, userid_length, required);
                    }
                }
                    break;
                case MESSAGE_ON_FTPS_ACCESS_REQUESTED:
                {
                    Bundle data = msg.getData();
                    Log.d(TAG, "MESSAGE_ON_FTPS_ACCESS_REQUESTED");
                    if(data != null) {
                        byte opcode = data.getByte("opcode");
                        String filename = data.getString("filename");
                        String remotename = data.getString("remotename");
                        String remoteaddr = data.getString("remoteaddr");
                        int size = data.getInt("size");
                        processFtpServerAccessRequested(filename, size, remotename, opcode,
                            remoteaddr);
                    }
                }
                    break;
                case MESSAGE_ON_GET_COMPLETED:
                {
                    Bundle data = msg.getData();
                    if(data != null) {
                        processFtpServerGetCompleted(data.getString("filepath"),
                            data.getByte("status"));
                    }
                }
                    break;
                case MESSAGE_ON_PUT_COMPLETED:
                {
                    Bundle data = msg.getData();
                    if(data != null) {
                        processFtpServerPutCompleted(data.getString("filepath"),
                            data.getByte("status"));
                    }
                }
                    break;
                case MESSAGE_ON_DEL_COMPLETED:
                {
                    Bundle data = msg.getData();
                    if(data != null) {
                        processFtpServerDelCompleted(data.getString("filepath"),
                            data.getByte("status"));
                    }
                }
                    break;
                case MESSAGE_TRANSFER_IN_PROGRESS:
                    processFtpServerFileTransferInProgress(msg.arg1, msg.arg2);
                    break;
            }
        }
    };

    static {
        classInitNative();
    }

    private native static void classInitNative();

    //Broadcast Listener for receiving ACTION_MEDIA_EJECT and ACTION_MEDIA_UNMOUNTED events
    private class UnmountBroadcastReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            if ( intent.getAction().equals(Intent.ACTION_MEDIA_EJECT) ||
                 intent.getAction().equals(Intent.ACTION_MEDIA_UNMOUNTED) ||
                 intent.getAction().equals(Intent.ACTION_MEDIA_BAD_REMOVAL) ||
                 intent.getAction().equals(Intent.ACTION_MEDIA_REMOVED) ||
                 intent.getAction().equals("broadcom.android.bluetooth.intent.action.MEDIA_EJECT"))
             {
                if(isStateRunning) {
                    Log.d(TAG, "Closing FTP Server during the event : "+intent.getAction());
                    closeFtpServerWithoutCleanup();
                }
            }
        }
    };
    private BroadcastReceiver mUnmountReceiver;

    /**
     * Helper function to determine if access request is enabled OR if
     * FORCE_ACCESS_REQUEST is true
     *
     * @return
     */
    private static boolean needAccessRequest() {
        try {
            return FTPServiceConfig.FORCE_ACCESS_REQUEST
                    || "true".equals(SystemProperties.get("service.brcm.bt.secure_mode", ""));
        } catch (Throwable t) {
            Log.e(TAG, "needAccessRequest()", t);
            return false;
        }
    }

    /**
     * Returns the name of the profile
     */
    public String getName() {
        return "**"+TAG;
    }

    protected IProfileServiceBinder initBinder() {
        return new BluetoothFtpBinder(this);
    }

    protected synchronized boolean start() {
        if (V) Log.v(TAG, "FTP-Server Service start");
        isStateRunning = false;
        mFtpServerStopped = false;
        // Initialize callbacks only if we are using callbacks for events
        if (FTPServiceConfig.USE_CALLBACKS == true && !FTPServiceConfig.USE_BROADCAST_INTENTS
                && !FTPServiceConfig.USE_LEGACY_BROADCAST_INTENTS) {
            mCallbacks = new RemoteCallbackList<IBluetoothFTPCallback>();
        }

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        try {
             mFtpClientDevices= new HashMap<BluetoothDevice, Integer> ();

            IntentFilter iFilter = new IntentFilter(Intent.ACTION_MEDIA_EJECT);
            iFilter.addAction(Intent.ACTION_MEDIA_UNMOUNTED);
            iFilter.addAction(Intent.ACTION_MEDIA_BAD_REMOVAL);
            iFilter.addAction(Intent.ACTION_MEDIA_REMOVED);
            iFilter.addAction("broadcom.android.bluetooth.intent.action.MEDIA_EJECT");
            iFilter.addDataScheme("file");
            iFilter.addDataAuthority("*", null);
            if (mUnmountReceiver != null) {
                try {
                    this.getBaseContext().unregisterReceiver(mUnmountReceiver);
                } catch (Throwable t) {
                    Log.e(TAG, "Unable to unregister unmount broadcast receiver",t);
                }
            }
            mMediaScanner = new MediaScannerConnection(this, this);
            mUnmountReceiver = new UnmountBroadcastReceiver();
            this.getApplicationContext().registerReceiver(mUnmountReceiver, iFilter);

            initFtpNativeDataNative();
            mNativeAvailable=true;
            mFtpServerStopped = false;
        } catch (Exception e) {
            Log.e(TAG, "onCreate failed", e);
            mNativeAvailable=false;
            return false;
        }
        return true;
     }

    /**
     * Called to stop the FTP server
     */
    protected boolean stop() {
        if (V) log("Stopping Bluetooth FTP Service");
        closeFtpServer();
        return true;
    }

    /**
     * Called to cleanup the FTP server
     */
    protected boolean cleanup() {
        Log.d(TAG, "cleanup FTP Service");
        //Cleanup native
        if(!mFtpServerStopped) {
            closeFtpServer();
        }
        if (mNativeAvailable) {
            try {
                cleanupFtpNativeDataNative();
            } catch (Throwable t) {
                Log.e(TAG, "Unable to cleanup ftp service", t);
            }
            mNativeAvailable=false;
        }
        if(mBinder != null)
            mBinder.cleanup();
        mBinder = null;

        if(mUnmountReceiver != null) {
            try {
                this.getApplicationContext().unregisterReceiver(mUnmountReceiver);
            } catch (Throwable t) {
                Log.e(TAG, "Unable to unregister unmount broadcast receiver");
            } finally {
                mUnmountReceiver = null;
            }
        }
        //mHandler.removeCallbacksAndMessages(null);
        Log.d(TAG, "cleanup done");
        return true;
    }

    public boolean isAvailable() {
        return mNativeAvailable && !mFtpServerStopped && super.isAvailable();
    }

    @Override
    protected void finalize() {
        if(V) {
            synchronized (FTPService.class) {
                Log.d(TAG, "FINALIZED. Class= " + this);
            }
        }
        super.finalize();
    }

    public void onMediaScannerConnected() {
        if (V) Log.v(TAG, "MediaScannerConnection onMediaScannerConnected");
        //Refresh any outstanding file path
        if (filePathForMediaScan != null)
         {
            ftpMediaScanFilePath (filePathForMediaScan);
         }
    }

    public void onScanCompleted(String path, Uri uri) {
        if (V) {
            Log.v(TAG, "MediaScannerConnection onScanCompleted");
            Log.v(TAG, "MediaScannerConnection path is " + path);
            Log.v(TAG, "MediaScannerConnection Uri is " + uri);
        }
    }

    private class BluetoothFtpBinder extends IBluetoothFTP.Stub implements IProfileServiceBinder {
        private FTPService mService;

        public BluetoothFtpBinder(FTPService svc) {
            Log.d(TAG, "BluetoothFtpBinder()");
            mService = svc;
        }

        public boolean cleanup()  {
            Log.d(TAG, "BluetoothFtpBinder.cleanup()");
            mService = null;
            return true;
        }

        private FTPService getService() {
            if (mService  != null && mService.isAvailable()) {
                return mService;
            }
            return null;
        }
        public int getConnectionState(BluetoothDevice device) {
            FTPService service = getService();
            if (service == null) return BluetoothProfile.STATE_DISCONNECTED;
            return service.getConnectionState(device);
        }

        public List<BluetoothDevice> getConnectedDevices() {
            return getDevicesMatchingConnectionStates(
                    new int[] {BluetoothProfile.STATE_CONNECTED});
        }

        public List<BluetoothDevice> getDevicesMatchingConnectionStates(
                int[] states) {
            FTPService service = getService();
            if (service == null) return new ArrayList<BluetoothDevice>(0);
            return service.getDevicesMatchingConnectionStates(states);
        }

        public void ftpServerAuthenRsp(String password, String user_id) {
            FTPService service = getService();
            if (service == null) return;
            service.ftpServerAuthenRsp(password, user_id);
        }

        public void ftpServerAccessRsp(byte op_code, boolean access,
            String filename) {
            FTPService service = getService();
            if (service == null) return;
            service.ftpServerAccessRsp(op_code, access, filename);
        }

        public void registerCallback(IBluetoothFTPCallback cb) {
            FTPService service = getService();
            if (service == null) return;
            service.registerCallback(cb);
        }

        public void unregisterCallback(IBluetoothFTPCallback cb) {
            FTPService service = getService();
            if (service == null) return;
            service.unregisterCallback(cb);
        }
    }

    int getConnectionState(BluetoothDevice device) {
        if (mFtpClientDevices.get(device) == null) {
            return BluetoothProfile.STATE_DISCONNECTED;
        }
        return mFtpClientDevices.get(device);
    }

    private BluetoothDevice getDevice(String address) {
        return mAdapter.getRemoteDevice(address);
    }

    List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        enforceCallingOrSelfPermission(ProfileService.BLUETOOTH_PERM, "Need BLUETOOTH permission");
        List<BluetoothDevice> ftpClientDevices = new ArrayList<BluetoothDevice>();

        for (BluetoothDevice device: mFtpClientDevices.keySet()) {
            int inputDeviceState = getConnectionState(device);
            for (int state : states) {
                if (state == inputDeviceState) {
                    ftpClientDevices.add(device);
                    break;
                }
            }
        }
        return ftpClientDevices;
    }

    /**
     * closeFtpServer provides service to close FTP server.
     */
    public synchronized void closeFtpServer() {
        try {
            closeFtpServerNative();
        } catch (Exception e) {
            Log.e(TAG, "closeFtpServerNative failed", e);
        }

        // Disconnect from media scanner, if the media scanner was in use
        if (FTPServiceConfig.USE_MEDIA_SCANNER && mMediaScanner.isConnected()) {
            try {
                mMediaScanner.disconnect();
            } catch (Throwable t) {
                Log.e(TAG, "Unable to disconnect from media scanner");
            }
        }
        if (mUnmountReceiver != null) {
            try {
                this.getApplicationContext().unregisterReceiver(mUnmountReceiver);
                mUnmountReceiver = null;
            } catch (Throwable t) {
                Log.e(TAG, "Unable to unregister unmount broadcast receiver");
            }
        }
        if(mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
        isStateRunning = false;
    }

    /**
     * FTP don't working when SD card remove.
     * closeFtpServer provides service to close FTP server.
     */
    public synchronized void closeFtpServerWithoutCleanup() {
        try {
            closeFtpServerWithoutCleanupNative();
        } catch (Exception e) {
            Log.e(TAG, "closeFtpServerNative failed", e);
        }

        // Disconnect from media scanner, if the media scanner was in use
        if (FTPServiceConfig.USE_MEDIA_SCANNER && mMediaScanner.isConnected()) {
            try {
                mMediaScanner.disconnect();
            } catch (Throwable t) {
                Log.e(TAG, "Unable to disconnect from media scanner");
            }
        }
        isStateRunning = false;
    }

    private native void closeFtpServerWithoutCleanupNative();

    private native void closeFtpServerNative();

    /**
     * ftpServerAuthenRsp is not implemented in current release.
     */
    public synchronized void ftpServerAuthenRsp(String password, String userId) {
        try {
            ftpServerAuthenRspNative(password, userId);
        } catch (Exception e) {
            Log.e(TAG, "ftpServerAuthRspNative failed", e);
        }
    }

    private native void ftpServerAuthenRspNative(String password, String userId);

    /**
     * ftpServerAccessRsp provides service to response to FTP access request.
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
    public synchronized void ftpServerAccessRsp(byte op_code, boolean access, String filename) {
        try {
            ftpServerAccessRspNative(op_code, access, filename);
        } catch (Exception e) {
            Log.e(TAG, "ftpServerAccessRspNative failed", e);
        }
    }

    public void registerCallback(IBluetoothFTPCallback cb) {
        if (cb != null && mCallbacks != null) {
            mCallbacks.register(cb);
        }
    }

    public void unregisterCallback(IBluetoothFTPCallback cb) {
        if (cb != null && mCallbacks != null) {
            mCallbacks.unregister(cb);
        }
    }

    private native void ftpServerAccessRspNative(byte op_code, boolean access, String filename);

    private native void initFtpNativeDataNative();

    private native void cleanupFtpNativeDataNative();

    /**
     * Returns true if the operation is an action command
     */
    private boolean isActionCommand(byte opCode) {
        return opCode >= BluetoothFTP.FTPS_OPER_COPY && opCode <=BluetoothFTP.FTPS_OPER_SET_PERM;
    }

    /**
     * Returns true if the specified action command is supported, according to the FTPServiceConfig
     */
    private boolean isActionCommandSupported(byte opCode) {
        for (int i=0; i < FTPServiceConfig.SUPPORTED_ACTION_COMMANDS.length; i++) {
            if (FTPServiceConfig.SUPPORTED_ACTION_COMMANDS[i] ==opCode) {
                return true;
            }
        }
        return false;
    }

    private void deleteMedia(String filePath) {
        if (filePath ==  null) {
            Log.w(TAG, "deleteMedia(): invalid media file path");
            return;
        }

        String tmpPath = filePath.replace(ftpRootPath,
            Environment.getExternalStorageDirectory().getPath());
        Log.d(TAG, "FTP "+filePath+" --> "+tmpPath);
        ContentResolver r = this.getApplicationContext().getContentResolver();
        Uri externalMediaUri = MediaStore.Files.getContentUri("external");
        if (r == null || externalMediaUri == null) {
            Log.w(TAG,"deleteMedia(): invalid content resolver or media uri ");
            return;
        }

        int deleteCount = 0;
        try {
            deleteCount = r.delete(externalMediaUri,
                MediaStore.Files.FileColumns.DATA+" =? OR "+MediaStore.Files.FileColumns.DATA+
                " =? ", new String[] { tmpPath , filePath});
        } catch (Exception e) {
            Log.e(TAG, "Error while deleting " + tmpPath+" from media store",e);
        }
        if (V) Log.d(TAG, "Deleted " + deleteCount + "entries for media " + tmpPath+
            " from media store");
    }


    // This method does not check for error conditon (newState == prevState)
    private void broadcastConnectionState(BluetoothDevice device, int newState, int prevState) {
        if (mFtpClientDevices.containsKey(device) && prevState == newState) {
            Log.d(TAG, "no state change: " + newState);
            return;
        }
        mFtpClientDevices.put(device, newState);

        Intent intent = new Intent(BluetoothFTP.ACTION_CONNECTION_STATE_CHANGED);
        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, newState);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
        if (V) Log.d(TAG, "Connection state " + device + ": " + prevState + "->" + newState);
    }

    private void onFtpServerEnabled(String rootPath) {
        Log.i(TAG, "onFtpServerEnabled: "+rootPath);
        isStateRunning = true;
        ftpRootPath = rootPath;

        // Broadcast to all clients the new value.
        if(mCallbacks != null) {
            final int N = mCallbacks.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mCallbacks.getBroadcastItem(i).onFtpServerEnabled();
                } catch (Throwable t) {
                    Log.e(TAG, "Error: onFtpServerOpened()", t);
                }
            }
            mCallbacks.finishBroadcast();
        }
    }

    private void onFtpServerDisabled() {
        Log.d(TAG, "onFtpServerDisabled() called.");

        mFtpServerStopped = true;
        // Disconnect media scanner
        if (FTPServiceConfig.USE_MEDIA_SCANNER && mMediaScanner.isConnected()) {
            try {
                mMediaScanner.disconnect();
            } catch (Throwable t) {
                Log.e(TAG, "Unable to disconnect from media scanner", t);
            }
        }

        // Broadcast to all clients the new value.
        if(mCallbacks != null) {
            final int N = mCallbacks.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mCallbacks.getBroadcastItem(i).onFtpServerClosed();
                } catch (Throwable t) {
                    Log.e(TAG, "Error: onFtpServerClosed()", t);
                }
            }
            mCallbacks.finishBroadcast();
        }
        //isStateRunning = false;
    }

    private void onConnectStateChanged(byte[] address, int state) {
        Log.d(TAG, "onConnectStateChanged() address:"+address+", state:"+state);
        Message msg = mHandler.obtainMessage(MESSAGE_ON_CONNECT_STATE_CHANGED);
        msg.obj = getDevice(address);
        msg.arg1 = state;
        if (state == CONN_STATE_OPEN)
        {
            isStateRunning = true;
        }
        mHandler.sendMessage(msg);
    }

    private void onFtpServerAuthen(String user_id, byte userid_length,
        boolean userid_required) {
        Message msg = mHandler.obtainMessage(MESSAGE_ON_FTPS_AUTH_REQUESTED);
        Bundle data = new Bundle();
        data.putByte("userid_length", userid_length);
        data.putString("userid", user_id);
        data.putBoolean("required", userid_required);
        msg.setData(data);
        mHandler.sendMessage(msg);
    }

    /**
     * This function is not implemented in current release.
     */
    private void processFtpServerAuthen(String user_id, byte userid_length,
    boolean userid_required) {
    }

    private void onFtpServerAccessRequested(String fileName, int fileSize,
        String remoteDeviceName, byte opCode, String remoteAddress) {
        if(V) Log.d(TAG, "onFtpServerAccessRequested() remoteAddress:"+remoteAddress);
        Message msg = mHandler.obtainMessage(MESSAGE_ON_FTPS_ACCESS_REQUESTED);
        Bundle data = new Bundle();
        data.putString("filename", fileName);
        data.putInt("size", fileSize);
        data.putString("remotename", remoteDeviceName);
        data.putByte("opcode", opCode);
        data.putString("remoteaddr", remoteAddress);
        msg.setData(data);
        mHandler.sendMessage(msg);
    }

    private void processFtpServerAccessRequested(String fileName, int fileSize,
        String remoteDeviceName, byte opCode, String remoteAddress) {
        if(V) Log.d(TAG, "processFtpServerAccessRequested()");
        if (opCode == BluetoothFTP.FTPS_OPER_PUT) {
        // ensure there's enough free space for the file in SD Card
            String root = Environment.getExternalStorageDirectory().getPath();
            try {
                 // Getting statfs info of the SD Card
                StatFs stat = new StatFs(new File(root).getPath());
                long freeSpace = (long) stat.getAvailableBlocks() * (long) stat.getBlockSize();

                if (fileSize > freeSpace) {
                    Log.e(TAG, "onFtpServerAccessRequested - Not enough free space");
                    // sorry, not enough free space
                    ftpServerAccessRsp(opCode, false, fileName);
                    return;
                }
           } catch(Exception eee) {
               Log.e(TAG, "Error calling statfs() : "+eee.toString());
               ftpServerAccessRsp(opCode, false, fileName);
               return;
          }
        } else if (isActionCommand(opCode)) {
            Log.d(TAG, "Checking if action command " + opCode + " is supported.");
            if (!isActionCommandSupported(opCode)) {
                Log.d(TAG, "Action command not supported. Denying request...");
                ftpServerAccessRsp(opCode, false, fileName);
                return;
            }
        }

        if (needAccessRequest()) {
            /*mContext.sendOrderedBroadcast(BluetoothIntent.createAccessRequest(null,
                BluetoothFTP.SERVICE_NAME, remoteDeviceName, remoteAddress, opCode, -1, fileName,
                fileSize), BluetoothFTP.BLUETOOTH_PERM);*/
                //TODO : Make changes to initiate access request
        } else {
            // automatically grant access
            ftpServerAccessRsp(opCode, true, fileName);
        }
    }

    private void onFtpServerFileTransferInProgress(int fileSize, int bytesTransferred) {
        Message msg = mHandler.obtainMessage(MESSAGE_TRANSFER_IN_PROGRESS);
        msg.arg1 = fileSize;
        msg.arg2 = bytesTransferred;
        mHandler.sendMessage(msg);
    }

    private void processFtpServerFileTransferInProgress(int fileSize, int bytesTransferred) {
        if (V) {
            Log.d(TAG, "Transferring file via FTP " + bytesTransferred + " bytes of " + fileSize);
        }
        // Broadcast to all clients the new value.
        if(mCallbacks != null) {
            final int N = mCallbacks.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mCallbacks.getBroadcastItem(i).onFtpServerFileTransferInProgress(fileSize,
                        bytesTransferred);
                } catch (Throwable t) {
                    Log.e(TAG, "Error: onFtpServerFileTransferInProgress()", t);
                }
            }
            mCallbacks.finishBroadcast();
        }
    }

    private void onFtpServerPutCompleted(String filePath, byte status) {
        if(V) Log.d(TAG, "onFtpServerPutCompleted() filePath:"+filePath);
        Message msg = mHandler.obtainMessage(MESSAGE_ON_PUT_COMPLETED);
        Bundle data = new Bundle();
        data.putString("filepath", filePath);
        data.putByte("status", status);
        msg.setData(data);
        mHandler.sendMessage(msg);
    }

    private void processFtpServerPutCompleted(String filePath, byte status) {
        if (V) Log.d(TAG, "onFtpServerPutCompleted(): " + filePath + " : status = " + status);
        // After put is complete, run media scanner to refresh media apps
        if (BluetoothFTP.FTPS_STATUS_OK == status ) {
             ftpMediaScanFilePath(filePath);
        }

        // Broadcast to all clients the new value.
        if(mCallbacks != null) {
            final int N = mCallbacks.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mCallbacks.getBroadcastItem(i).onFtpServerPutCompleted(filePath, status);
                } catch (Throwable t) {
                    Log.e(TAG, "Error: onFtpServerPutCompleted()", t);
                }
            }
            mCallbacks.finishBroadcast();
        }
    }

    private void onFtpServerGetCompleted(String filePath, byte status) {
        if(V) Log.d(TAG, "onFtpServerGetCompleted() filePath:"+filePath);
        Message msg = mHandler.obtainMessage(MESSAGE_ON_GET_COMPLETED);
        Bundle data = new Bundle();
        data.putString("filepath", filePath);
        data.putByte("status", status);
        msg.setData(data);
        mHandler.sendMessage(msg);
    }

    private void processFtpServerGetCompleted(String filePath, byte status) {
        if(V) Log.d(TAG, "onFtpServerGetCompleted(): " + filePath + " : state = " + status);

        // Broadcast to all clients the new value.
        if(mCallbacks != null) {
            final int N = mCallbacks.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mCallbacks.getBroadcastItem(i).onFtpServerGetCompleted(filePath, status);
                } catch (Throwable t) {
                    Log.e(TAG, "Error: onFtpServerGetCompleted()", t);
                }
            }
            mCallbacks.finishBroadcast();
        }
    }

    private void onFtpServerDelCompleted(String filePath, byte status) {
        if(V) Log.d(TAG, "onFtpServerDelCompleted() filePath:"+filePath);
        Message msg = mHandler.obtainMessage(MESSAGE_ON_DEL_COMPLETED);
        Bundle data = new Bundle();
        data.putString("filepath", filePath);
        data.putByte("status", status);
        msg.setData(data);
        mHandler.sendMessage(msg);
    }

    private void processFtpServerDelCompleted(String filePath, byte status) {
        if(V) Log.d(TAG, "onFtpServerDelCompleted(): " + filePath + " : state = " + status);
        Log.d(TAG, "ftpRootPath:"+ftpRootPath+", SystemPath:"+
            Environment.getExternalStorageDirectory().getPath());
        // After delete is complete, run media scanner to refresh media apps
        if (status == BluetoothFTP.FTPS_STATUS_OK ) {
            deleteMedia(filePath);
        }

        // Broadcast to all clients the new value.
        if(mCallbacks != null) {
            final int N = mCallbacks.beginBroadcast();
            for (int i = 0; i < N; i++) {
                try {
                    mCallbacks.getBroadcastItem(i).onFtpServerDelCompleted(filePath, status);
                } catch (Throwable t) {
                    Log.e(TAG, "Error: onFtpServerDelCompleted()", t);
                }
            }
            mCallbacks.finishBroadcast();
        }
    }


    private void ftpMediaScanFilePath(String filePath) {
        if (FTPServiceConfig.USE_MEDIA_SCANNER) {
            if ( mMediaScanner.isConnected()) {
                try {
                    mMediaScanner.scanFile(filePath, null);
                } catch (Throwable t) {
                    Log.e(TAG, "Unable to invoke MediaScanner.scanFile()", t);
                }
            }
            else {
                // Store the file path and call Media Scanner connect
                // TBD: Should we maintain a array of file path instead of single path to consider
                // PUT/Delete between mediaScanner.Connect call and Connected () handling..?
                try {
                    Log.d(TAG, "calling MediaScanner.connect()");
                    mMediaScanner.connect();
                    filePathForMediaScan = filePath;
                } catch (Throwable t) {
                    Log.e(TAG, "Unable to connect to media scanner", t);
                }
            }
        }
    }

}
