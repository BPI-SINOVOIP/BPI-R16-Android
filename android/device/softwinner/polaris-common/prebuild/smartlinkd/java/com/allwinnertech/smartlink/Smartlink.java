package com.allwinnertech.smartlink;

import android.content.Context;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.util.Log;


/**
 * Created by A on 2015/10/19.
 */
public class Smartlink {
    private static final String TAG = "smartlink";

    public static int ProtocolsCooee = 0;
    public static int ProtocolsNeeze = 1;
    public static int ProtocolsAirkiss = 2;
    public static int ProtocolsQQcon = 3;
    public static int ProtocolsFailed = 0xff;

    public static int SecurityNone = 0;
    public static int SecurityWep = 1;
    public static int SecurityWpa = 2;
    public static int SecurityWpa2 = 3;

    public static String[] Security ={"none","WEP","WPA1","WPA2"};
    public static String[] Protocol = {"cooee","neeze","airkiss","qqcon"};

    public static int START_ERROR = 0xff;
    public static int SCAN_ERROR = 0xfe;
    public static int CONNECT_ERROR = 0xfd;

    private boolean mIsStarted = false;

    private SmartlinkListener mBaseListener = null;
    private AirkissListerner mAirkissListerner = null;

    //private AirkissInfo mAirkissInfo;
    private int mProtocol = -1;

    private WifiAdmin mWifiAdmin;

    public interface SmartlinkListener {
        public void onSmartlinkFinished(SmartlinkInfo info);
        public void onLinktoAPFinished();
        public void onFailed(int errno);

    }
    public interface AirkissListerner extends SmartlinkListener {
        public void onSendUDPBroadcastFinished();
    }
    public Smartlink(Context context){
        mWifiAdmin = new WifiAdmin(context);

    }
    public void setListener(SmartlinkListener listener){
        mBaseListener = listener;
    }
    public void setListener(AirkissListerner listener){
        mAirkissListerner = listener;
        setListener((SmartlinkListener)listener);
    }
    public void setSmartlinkProtol(int protocol){
        //mProtocol = 1<<protocol;
        mProtocol = protocol;
    }
    public int startSmartLink(){
        if(mProtocol == -1){
            Log.e(TAG,"Please set smartlink protocol first");
            return -1;
        }

        if(!mIsStarted){
            mIsStarted = true;
            new SmartlinkThread().start();
        }else {
            Log.d(TAG, "Smartlink is already started!!");
        }
        return 0;
    }
    private class SmartlinkThread extends Thread{
        public void run(){
            //close wifi
            //mWifiAdmin.closeWifi();
            mWifiAdmin.resetWifi();
            int ret = startSmartLink(mProtocol);
            if(ret == -1){
                Log.e(TAG,"Start smartlink failed!");
                mIsStarted = false;

                if(mBaseListener != null ){
                    mBaseListener.onFailed(START_ERROR);
                }
            }
        }
    }
    private native int startSmartLink(int protocol);

    //callback form jni
    private void onSmartLinkFinished(int protocol,String ssid, String password, int sercurity, String senderip, int senderport,int random){
        SmartlinkInfo info = new SmartlinkInfo();
        info.protocol = protocol;
        info.ssid = ssid;
        info.password = password;
        info.wlansecurity = sercurity;
        info.senderip = senderip;
        info.senderport = senderport;
        info.random = random;
        onSmartLinkFinished(info);
    }
    //callback form jni
    private void onSmartLinkFinished(SmartlinkInfo info){
        mIsStarted = false;
        Log.d(TAG,"ssid: " + info.ssid);
        Log.d(TAG,"password: "+info.password);
        Log.d(TAG,"random: " + info.random);
        Log.d(TAG,"protocol: " + info.protocol);
        Log.d(TAG, "senderip: " + info.senderip);
        Log.d(TAG, "senderport: " + info.senderport);
        Log.d(TAG, "wlansecurity: " + info.wlansecurity);

        if(mBaseListener != null ){
            if(info.protocol != ProtocolsFailed)
                mBaseListener.onSmartlinkFinished(info);
            else{
                mBaseListener.onFailed(SCAN_ERROR);
                return;
            }
        }

        if(info.protocol != ProtocolsFailed) {
            mWifiAdmin.openWifi();
            mWifiAdmin.creatWifiLock();
            mWifiAdmin.acquireWifiLock();
            WifiConfiguration config = mWifiAdmin.CreateWifiInfo(info.ssid, info.password, info.wlansecurity);
            mWifiAdmin.addNetwork(config);
            mWifiAdmin.releaseWifiLock();

            //connect failed
            if(!mWifiAdmin.checkConnectionStateTimeout(10)){
                if(mBaseListener != null ){
                    mBaseListener.onFailed(CONNECT_ERROR);
                }
                return;
            }

            if (mBaseListener != null) {
                mBaseListener.onLinktoAPFinished();
            }
            if (info.protocol == ProtocolsAirkiss) {
                setAirkissUDPBroadcast(info.random);
            }
        }
    }

    public void setAirkissUDPBroadcast(int random)
    {
        AirkissUdpSend us = new AirkissUdpSend("255.255.255.255",10000,50,random);
        us.setListener(new AirkissUdpSend.AirkissUdpSendListener() {
            @Override
            public void onSendFinished() {
                if(mAirkissListerner != null){
                    mAirkissListerner.onSendUDPBroadcastFinished();
                }
            }
        });
        us.start();
    }

    static {
        System.loadLibrary("smartlink_jni");
    }
}
