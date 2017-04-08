package com.example.a.airkiss;

import com.allwinnertech.smartlink.Smartlink;
import com.allwinnertech.smartlink.SmartlinkInfo;
import com.allwinnertech.smartlink.WifiAdmin;
import com.softwinner.broadcom.easysetup.BroadcomEasysetup;
import com.softwinner.broadcom.easysetup.EasysetupInfo;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


public class MainActivity extends Activity implements Smartlink.AirkissListerner {
    private static final String TAG = "DEMO";
    private final Handler mHandler = new MainHandler();
    //private EasysetupInfo mInfo;
    //public BroadcomEasysetup mBroadcomEasysetup = null;

    private SmartlinkInfo mInfo;
    public Smartlink mBroadcomEasysetup = null;
    private WifiAdmin mWifiAdmin;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e(TAG, "onCreate");
        setContentView(R.layout.activity_main);

        mBroadcomEasysetup = new Smartlink(this);
        mBroadcomEasysetup.setListener(this);

        mWifiAdmin = new WifiAdmin(this);

        Button btn1 = (Button) findViewById(R.id.startA);
        btn1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                // TODO Auto-generated method stub
                onStartAirkiss(arg0);
            }
        });

        Button btn2 = (Button) findViewById(R.id.startC);
        btn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                // TODO Auto-generated method stub
                onStartCooee(arg0);
            }
        });
    }
    public void onStartAirkiss(View v){
        TextView text = (TextView)findViewById(R.id.info);
        text.setText("starting airkiss...");
        mBroadcomEasysetup.setSmartlinkProtol(Smartlink.ProtocolsAirkiss);
        int ret = mBroadcomEasysetup.startSmartLink();
        Log.e(TAG,"startEasySetup return is "+ret);
    }
    public void onStartCooee(View v){
        TextView text = (TextView)findViewById(R.id.info);
        text.setText("starting cooee...");
        //mBroadcomEasysetup.setEasysetupProtol(BroadcomEasysetup.ProtocolsCooee);
        //int ret = mBroadcomEasysetup.startEasySetup();
        //Log.e(TAG, "startEasySetup return is " + ret);
        mWifiAdmin.resetWifi();
    }
    private class MainHandler extends Handler {

        @Override
        public void handleMessage(Message msg) {
            // TODO Auto-generated method stub
            super.handleMessage(msg);
            switch (msg.what) {
                case 100:{
                    TextView text = (TextView)findViewById(R.id.info);
                    String s = "protocol: " + BroadcomEasysetup.Protocol[mInfo.protocol]
                            + "\nssid: " + mInfo.ssid
                            + "\npassword: " + mInfo.password
                            + "\nrandom: 0x" + Integer.toHexString(mInfo.random)
                            + "\nsecurity:" + BroadcomEasysetup.Security[mInfo.wlansecurity]
                            + "\nsender ip: " + mInfo.senderip
                            + "\nsender port: " + mInfo.senderport;
                    text.setText(s);
                    break;
                }
                case 101:{
                    TextView text = (TextView)findViewById(R.id.info);
                    text.setText("Timeout....");
                    break;
                }
                case 102:{
                    TextView text = (TextView)findViewById(R.id.status);
                    text.setText("操作微信Airkiss");
                    break;
                }
                case 103:{
                    TextView text = (TextView)findViewById(R.id.status);
                    text.setText("解析完成，开始连接路由");
                    break;
                }
                case 104:{
                    TextView text = (TextView)findViewById(R.id.status);
                    text.setText("连接路由成功");
                    break;
                }
                case 105:{
                    TextView text = (TextView)findViewById(R.id.status);
                    text.setText("发送udp广播");
                    break;
                }
                case 106:{
                    TextView text = (TextView)findViewById(R.id.status);
                    text.setText("发送udp广播完成，完成连接");
                    break;
                }
            }
        }
    }

    //@Override
    public void onEasysetupFinished(SmartlinkInfo info) {
        if(info.protocol < 0){
            Log.e(TAG,"time out,try again");
            mHandler.sendEmptyMessage(101);
            return;
        }
        if(info.ssid == null){
            Log.e(TAG,"unknow failed,try again");
        }
        mInfo = info;
        mHandler.sendEmptyMessage(100);
    }

    //@Override
    public void onEasysetupStart() {
        Log.e(TAG,"00000000 onEasysetupStart  ");
        mHandler.sendEmptyMessage(102);
    }

    //@Override
    public void onLinktoAPStart() {
        Log.e(TAG,"00000000 onLinktoAPStart  ");
        mHandler.sendEmptyMessage(103);
    }

    @Override
    public void onSmartlinkFinished(SmartlinkInfo info) {
        onEasysetupFinished(info);
    }

    @Override
    public void onLinktoAPFinished() {
        Log.e(TAG,"00000000 onLinktoAPFinished  ");
        mHandler.sendEmptyMessage(104);
    }

    @Override
    public void onFailed(int errno) {
        Log.e(TAG,"smartlink failed: "+errno);
    }

    //@Override
    public void onSendUDPBroadcastStart() {
        Log.e(TAG, "00000000 onSendUDPBroadcastStart  ");
        mHandler.sendEmptyMessage(105);
    }

    @Override
    public void onSendUDPBroadcastFinished() {
        Log.e(TAG,"00000000 onSendUDPBroadcastFinished  ");
        mHandler.sendEmptyMessage(106);
    }
}
