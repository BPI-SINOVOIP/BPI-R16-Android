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
    // ����WifiManager����
    private WifiManager mWifiManager;
    // ����WifiInfo����
    private WifiInfo mWifiInfo;
    // ɨ��������������б�
    private List<ScanResult> mWifiList;
    // ���������б�
    private List<WifiConfiguration> mWifiConfiguration;
    // ����һ��WifiLock
    WifiManager.WifiLock mWifiLock;


    // ������
    public WifiAdmin(Context context) {
        // ȡ��WifiManager����
        mWifiManager = (WifiManager) context
                .getSystemService(Context.WIFI_SERVICE);
        // ȡ��WifiInfo����
        mWifiInfo = mWifiManager.getConnectionInfo();
    }

    // ��WIFI
    public void openWifi() {
        if (!mWifiManager.isWifiEnabled()) {
            System.out.println("setWifiEnabled(true)");
                    mWifiManager.setWifiEnabled(true);
        }
    }

    // �ر�WIFI
    public void closeWifi() {
        if (mWifiManager.isWifiEnabled()) {
            mWifiManager.setWifiEnabled(false);
        }
    }

    // ��鵱ǰWIFI״̬
    public int checkState() {
        return mWifiManager.getWifiState();
    }

    // ����WifiLock
    public void acquireWifiLock() {
        mWifiLock.acquire();
    }

    // ����WifiLock
    public void releaseWifiLock() {
        // �ж�ʱ������
        if (mWifiLock.isHeld()) {
            mWifiLock.release();
        }
    }

    // ����һ��WifiLock
    public void creatWifiLock() {
        mWifiLock = mWifiManager.createWifiLock("Test");
    }

    // �õ����úõ�����
    public List<WifiConfiguration> getConfiguration() {
        return mWifiConfiguration;
    }

    // ָ�����úõ������������
    public void connectConfiguration(int index) {
        // �����������úõ�������������
        if (index > mWifiConfiguration.size()) {
            return;
        }
        // �������úõ�ָ��ID������
        mWifiManager.enableNetwork(mWifiConfiguration.get(index).networkId,
                true);
    }

    public void startScan() {
        mWifiManager.startScan();
        // �õ�ɨ����
        mWifiList = mWifiManager.getScanResults();
        // �õ����úõ���������
        mWifiConfiguration = mWifiManager.getConfiguredNetworks();
    }

    // �õ������б�
    public List<ScanResult> getWifiList() {
        return mWifiList;
    }

    // �鿴ɨ����
    public StringBuilder lookUpScan() {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < mWifiList.size(); i++) {
            stringBuilder
                    .append("Index_" + new Integer(i + 1).toString() + ":");
            // ��ScanResult��Ϣת����һ���ַ�����
            // ���аѰ�����BSSID��SSID��capabilities��frequency��level
            stringBuilder.append((mWifiList.get(i)).toString());
            stringBuilder.append("/n");
        }
        return stringBuilder;
    }

    // �õ�MAC��ַ
    public String getMacAddress() {
        mWifiInfo = mWifiManager.getConnectionInfo();
        return (mWifiInfo == null) ? "NULL" : mWifiInfo.getMacAddress();
    }

    // �õ�������BSSID
    public String getBSSID() {
        mWifiInfo = mWifiManager.getConnectionInfo();
        return (mWifiInfo == null) ? "NULL" : mWifiInfo.getBSSID();
    }

    // �õ�IP��ַ
    public int getIPAddress() {
        mWifiInfo = mWifiManager.getConnectionInfo();
        return (mWifiInfo == null) ? 0 : mWifiInfo.getIpAddress();
    }

    // �õ����ӵ�ID
    public int getNetworkId() {
        mWifiInfo = mWifiManager.getConnectionInfo();
        return (mWifiInfo == null) ? 0 : mWifiInfo.getNetworkId();
    }

    // �õ�WifiInfo��������Ϣ��
    public String getWifiInfo() {
        mWifiInfo = mWifiManager.getConnectionInfo();
        return (mWifiInfo == null) ? "NULL" : mWifiInfo.toString();
    }

    // �õ���ǰ���ӵ�supplicant state
    public SupplicantState getSupplicantState(){
        mWifiInfo = mWifiManager.getConnectionInfo();
        return (mWifiInfo == null) ? SupplicantState.UNINITIALIZED : mWifiInfo.getSupplicantState();

    }

    // ���һ�����粢����
    public boolean addNetwork(WifiConfiguration wcg) {
        int wcgID = mWifiManager.addNetwork(wcg);
        mWifiManager.saveConfiguration();
        return mWifiManager.enableNetwork(wcgID, true);

    }

    // �Ͽ�ָ��ID������
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
        //��ȡ��ǰ�����ӵ�wifi��Ϣ
        int id = getNetworkId();
        System.out.println("Current Network id: " + id);
        //�Ͽ���ǰ������
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
            //Log.d(TAG,"info String:��" + mWifiAdmin.getWifiInfo());
        }
        return true;
    }
//Ȼ����һ��ʵ��Ӧ�÷�����ֻ��֤��û������������

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
