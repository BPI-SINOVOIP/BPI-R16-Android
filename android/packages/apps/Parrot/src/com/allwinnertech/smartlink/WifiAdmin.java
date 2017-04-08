package com.allwinnertech.smartlink;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.io.Serializable;
import java.util.List;

/**
 * Created by A on 2015/9/10.
 */
public class WifiAdmin {
    // 定义WifiManager对象
    private WifiManager mWifiManager;
    // 定义WifiInfo对象
    private WifiInfo mWifiInfo;
    // 扫描出的网络连接列表
    private List<ScanResult> mWifiList;
    // 网络连接列表
    private List<WifiConfiguration> mWifiConfiguration;
    // 定义一个WifiLock
    WifiManager.WifiLock mWifiLock;


    // 构造器
    public WifiAdmin(Context context) {
        // 取得WifiManager对象
        mWifiManager = (WifiManager) context
                .getSystemService(Context.WIFI_SERVICE);
        // 取得WifiInfo对象
        mWifiInfo = mWifiManager.getConnectionInfo();
    }

    // 打开WIFI
    public void openWifi() {
        if (!mWifiManager.isWifiEnabled()) {
            System.out.println("setWifiEnabled(true)");
                    mWifiManager.setWifiEnabled(true);
        }
    }

    // 关闭WIFI
    public void closeWifi() {
        if (mWifiManager.isWifiEnabled()) {
            mWifiManager.setWifiEnabled(false);
        }
    }

    // 检查当前WIFI状态
    public int checkState() {
        return mWifiManager.getWifiState();
    }

    // 锁定WifiLock
    public void acquireWifiLock() {
        mWifiLock.acquire();
    }

    // 解锁WifiLock
    public void releaseWifiLock() {
        // 判断时候锁定
        if (mWifiLock.isHeld()) {
            mWifiLock.release();
        }
    }

    // 创建一个WifiLock
    public void creatWifiLock() {
        mWifiLock = mWifiManager.createWifiLock("Test");
    }

    // 得到配置好的网络
    public List<WifiConfiguration> getConfiguration() {
        return mWifiConfiguration;
    }

    // 指定配置好的网络进行连接
    public void connectConfiguration(int index) {
        // 索引大于配置好的网络索引返回
        if (index > mWifiConfiguration.size()) {
            return;
        }
        // 连接配置好的指定ID的网络
        mWifiManager.enableNetwork(mWifiConfiguration.get(index).networkId,
                true);
    }

    public void startScan() {
        mWifiManager.startScan();
        // 得到扫描结果
        mWifiList = mWifiManager.getScanResults();
        // 得到配置好的网络连接
        mWifiConfiguration = mWifiManager.getConfiguredNetworks();
    }

    // 得到网络列表
    public List<ScanResult> getWifiList() {
        return mWifiList;
    }

    // 查看扫描结果
    public StringBuilder lookUpScan() {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < mWifiList.size(); i++) {
            stringBuilder
                    .append("Index_" + new Integer(i + 1).toString() + ":");
            // 将ScanResult信息转换成一个字符串包
            // 其中把包括：BSSID、SSID、capabilities、frequency、level
            stringBuilder.append((mWifiList.get(i)).toString());
            stringBuilder.append("/n");
        }
        return stringBuilder;
    }

    // 得到MAC地址
    public String getMacAddress() {
        mWifiInfo = mWifiManager.getConnectionInfo();
        return (mWifiInfo == null) ? "NULL" : mWifiInfo.getMacAddress();
    }

    // 得到接入点的BSSID
    public String getBSSID() {
        mWifiInfo = mWifiManager.getConnectionInfo();
        return (mWifiInfo == null) ? "NULL" : mWifiInfo.getBSSID();
    }

    // 得到IP地址
    public int getIPAddress() {
        mWifiInfo = mWifiManager.getConnectionInfo();
        return (mWifiInfo == null) ? 0 : mWifiInfo.getIpAddress();
    }

    // 得到连接的ID
    public int getNetworkId() {
        mWifiInfo = mWifiManager.getConnectionInfo();
        return (mWifiInfo == null) ? 0 : mWifiInfo.getNetworkId();
    }

    // 得到WifiInfo的所有信息包
    public String getWifiInfo() {
        mWifiInfo = mWifiManager.getConnectionInfo();
        return (mWifiInfo == null) ? "NULL" : mWifiInfo.toString();
    }

    // 得到当前连接的supplicant state
    public SupplicantState getSupplicantState(){
        mWifiInfo = mWifiManager.getConnectionInfo();
        return (mWifiInfo == null) ? SupplicantState.UNINITIALIZED : mWifiInfo.getSupplicantState();

    }

    // 添加一个网络并连接
    public boolean addNetwork(WifiConfiguration wcg) {
        int wcgID = mWifiManager.addNetwork(wcg);
        mWifiManager.saveConfiguration();
        return mWifiManager.enableNetwork(wcgID, true);

    }

    // 断开指定ID的网络
    public void disconnectWifi(int netId) {
        mWifiManager.disableNetwork(netId);
        mWifiManager.disconnect();
    }

    public void resetWifi(){
        if (!mWifiManager.isWifiEnabled()) {
            System.out.println("Current Network close");
            openWifi();
            int count = 0;
            while(!mWifiManager.isWifiEnabled()){
                try {
                    System.out.println("wifi starting: " + count);
                    Thread.sleep(500,0);
                    if(count++ > 20) break;
                } catch (InterruptedException e) {
                    //e.printStackTrace();
                }
            }
        }
        mWifiManager.disconnect();
        /*
        //获取当前的连接的wifi信息
        int id = getNetworkId();
        System.out.println("Current Network id: " + id);
        //断开当前的连接
        if(id > 0)
            disconnectWifi(id);
        */
    }
    public boolean checkConnectionStateTimeout(int timeout/*s*/){
        int trys = timeout * 2;
        while(getSupplicantState() != SupplicantState.COMPLETED){
            try {
                Thread.sleep(500,0);
                if(trys-- == 0) return false;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //Log.d(TAG,"info String:　" + mWifiAdmin.getWifiInfo());
        }
        return true;
    }
//然后是一个实际应用方法，只验证过没有密码的情况：

    public WifiConfiguration CreateWifiInfo(String SSID, String Password, int Type)
    {
        WifiConfiguration config = new WifiConfiguration();
        config.allowedAuthAlgorithms.clear();
        config.allowedGroupCiphers.clear();
        config.allowedKeyManagement.clear();
        config.allowedPairwiseCiphers.clear();
        config.allowedProtocols.clear();
        config.SSID = "\""+SSID+"\"";


        WifiConfiguration tempConfig = this.IsExsits(SSID);
        if(tempConfig != null) {
            System.out.println(SSID +" exsits!!");
            mWifiManager.removeNetwork(tempConfig.networkId);
        }

        if(Type == WifiConfiguration.KeyMgmt.NONE) //WIFICIPHER_NOPASS
        {
            config.wepKeys[0] = "";
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            config.wepTxKeyIndex = 0;
        }
        if(Type == 2) //WIFICIPHER_WEP
        {
            config.hiddenSSID = true;
            config.wepKeys[0]= "\""+Password+"\"";
            config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
            config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            config.wepTxKeyIndex = 0;

        }
        if(Type == 3) //WIFICIPHER_WPA
        {
            config.preSharedKey = "\""+Password+"\"";
            config.hiddenSSID = true;
            //config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            //config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            //config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            //config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            //config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            //config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
            config.status = WifiConfiguration.Status.ENABLED;
            config.priority = getMaxPriority() + 1;
        }
        return config;
    }

    private WifiConfiguration IsExsits(String SSID)
    {
        List<WifiConfiguration> existingConfigs = mWifiManager.getConfiguredNetworks();
        if(existingConfigs == null){
            System.out.println("the configure networks is null");
            return null;
        }
        for (WifiConfiguration existingConfig : existingConfigs)
        {
            if (existingConfig.SSID.equals("\""+SSID+"\""))
            {
                return existingConfig;
            }
        }
        return null;
    }
    private int getMaxPriority()
    {
        int priority = 0;
        List<WifiConfiguration> existingConfigs = mWifiManager.getConfiguredNetworks();
        if(existingConfigs == null){
            System.out.println("the configure networks is null");
            return 0;
        }
        for (WifiConfiguration existingConfig : existingConfigs)
        {
            if (priority < existingConfig.priority)
            {
                priority = existingConfig.priority;
            }
        }
        return priority;
    }
}
