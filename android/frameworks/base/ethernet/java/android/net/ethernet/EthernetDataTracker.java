/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.net.ethernet;

import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo.DetailedState;
import android.net.InterfaceConfiguration;
import android.net.LinkAddress;
import android.net.NetworkUtils;
import android.net.DhcpResults;
import android.net.RouteInfo;
import android.net.ConnectivityManager;
import android.net.INetworkManagementEventObserver;
import android.net.NetworkStateTracker;
import android.net.LinkCapabilities;
import android.net.LinkProperties;
import android.net.LinkQualityInfo;
import android.net.NetworkInfo;
import android.net.SamplingDataTracker;
import android.os.UserHandle;
import android.os.Handler;
import android.os.IBinder;
import android.os.INetworkManagementService;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.util.Log;
import android.util.Slog;
import android.text.TextUtils;
import java.net.InetAddress;
import java.net.Inet4Address;

import com.android.internal.R;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;

/**
 * This class tracks the data connection associated with Ethernet
 * This is a singleton class and an instance will be created by
 * ConnectivityService.
 * @hide
 */
public class EthernetDataTracker implements NetworkStateTracker {
    private static final String NETWORKTYPE = "ETHERNET";
    private static final String TAG = "EthernetDataTracker";
    private static final boolean DBG = true;

    private AtomicBoolean mTeardownRequested = new AtomicBoolean(false);
    private AtomicBoolean mPrivateDnsRouteSet = new AtomicBoolean(false);
    private AtomicInteger mDefaultGatewayAddr = new AtomicInteger(0);
    private AtomicBoolean mDefaultRouteSet = new AtomicBoolean(false);

    private static boolean mLinkUp;
    private LinkProperties mLinkProperties;
    private LinkCapabilities mLinkCapabilities;
    private NetworkInfo mNetworkInfo;
    private InterfaceObserver mInterfaceObserver;
	private EthernetManager mEthManage;
	private INetworkManagementService mNMService;

    private Handler mCsHandler;
    private Context mContext;

    private static EthernetDataTracker sInstance;
    private static String sIfaceMatch = "";
    private static String mIface = "";
    private static boolean mFirstLinkUp;

    private static class InterfaceObserver extends INetworkManagementEventObserver.Stub {
        private EthernetDataTracker mTracker;

        InterfaceObserver(EthernetDataTracker tracker) {
            super();
            mTracker = tracker;
        }

        public void interfaceStatusChanged(String iface, boolean up) {
            //Log.d(TAG, "Interface status changed: " + iface + (up ? "up" : "down"));
        }

        public void interfaceLinkStateChanged(String iface, boolean up) {
			mTracker.interfaceLinkStateChanged(iface, up);
        }

        public void interfaceAdded(String iface) {
            mTracker.interfaceAdded(iface);
        }

        public void interfaceRemoved(String iface) {
            mTracker.interfaceRemoved(iface);
        }

        public void addressUpdated(String address, String iface, int flags, int scope){
            // Ignored.
        }
        
        public void addressRemoved(String address, String iface, int flags, int scope){
            // Ignored.
        }

        public void limitReached(String limitName, String iface) {
            // Ignored.
        }

        public void interfaceClassDataActivityChanged(String label, boolean active) {
            // Ignored.
        }
    }

    private EthernetDataTracker() {
        mNetworkInfo = new NetworkInfo(ConnectivityManager.TYPE_ETHERNET,
													0, NETWORKTYPE, "");
        mLinkProperties = new LinkProperties();
        mLinkCapabilities = new LinkCapabilities();
        mLinkUp = false;
        mFirstLinkUp = false;

        mNetworkInfo.setIsAvailable(false);
        setTeardownRequested(false);
    }

    private void interfaceAdded(String iface) {
		/* add an new interface to EthernetService and check it. */
		if(!mEthManage.addInterfaceToService(iface)) {
		    if(DBG) Slog.d(TAG, "add iface[" + iface + "] to ethernet list failed.");
			return;
		}

        /* protect var */
        synchronized(mIface) {
            if(!mIface.isEmpty()) {
                if(DBG) Slog.d(TAG, "mIface is empty.");
                return;
            }
            if(DBG) Slog.d(TAG, "intefaceAdded [" + iface + "]");
            mIface = iface;
        }
        if(DBG) Slog.d(TAG, "interfaceAdded: connecting to ethrent network.");
		NetworkUtils.enableInterface(mIface);
		reconnect();
        mFirstLinkUp = true;
    }

    private void interfaceRemoved(String iface) {

		mEthManage.removeInterfaceFormService(iface);
        if (!iface.matches(mIface)) {
            if(DBG) Slog.d(TAG, "ignore ["+iface+"] remove");
            return;
        }
        if(DBG) Slog.d(TAG, "interfaceRemoved: tear down: " + iface);
		teardown();
        /* protect var */
		synchronized(mIface){
			mIface = "";
		}
		mFirstLinkUp = true;
    }

	private void interfaceLinkStateChanged(String iface, boolean up) {
		int tmpStatus = 0;
		int i=0;
		if (mIface.matches(iface)) {
			for(i = 0; i < 3; i++) {
				try {
					Thread.sleep(100);
				} catch(Exception e){ }
				tmpStatus = mEthManage.checkLink(iface);
				if(tmpStatus == 0 && mFirstLinkUp == true) {
				    Slog.d(TAG, "link entry exist,but no any value,bring up.");
				    NetworkUtils.enableInterface(iface);
				    mFirstLinkUp = false;
				}
			}
			Slog.d(TAG, "interfaceLinkStateChanged: ["+ iface + "], up [" + up + "]");
			if(tmpStatus == -1) {
			    //checkLink return -1 means no entry
			    Slog.d(TAG, "checkLink return -1 means no entry.");
			    return;
			}
			up = (tmpStatus == 1) ? true : false;
			if(mLinkUp == up)
				return;

			mLinkUp = up;
			if (up) {
				reconnect();
			} else {
			    if(DBG) Slog.d(TAG, "interfaceLinkStateChanged: teardown network."); 
				NetworkUtils.stopDhcp(mIface);
				mLinkProperties.clear();
				mNetworkInfo.setIsAvailable(false);
				mNetworkInfo.setDetailedState(DetailedState.DISCONNECTED, null, null);
				Message msg = mCsHandler.obtainMessage(EVENT_STATE_CHANGED, mNetworkInfo);
				msg.sendToTarget();
				sendStateBroadcast(EthernetManager.EVENT_DISCONNECTED);
			}
		}
	}

    private void runDhcp() {
        Thread dhcpThread = new Thread(new Runnable() {
            public void run() {
                DhcpResults mDhcpResults = new DhcpResults();
                if (!NetworkUtils.runDhcp(mIface, mDhcpResults)) {
                    Log.e(TAG, "DHCP request error:" + NetworkUtils.getDhcpError());
                    mLinkProperties.clear();
                    mNetworkInfo.setIsAvailable(false);
					mNetworkInfo.setDetailedState(DetailedState.DISCONNECTED, null, null);

					Message msg = mCsHandler.obtainMessage(EVENT_STATE_CHANGED, mNetworkInfo);
					msg.sendToTarget();
					sendStateBroadcast(EthernetManager.EVENT_CONFIGURATION_FAILED);
                    return;
                }
                mLinkProperties = mDhcpResults.linkProperties;
                mLinkProperties.setInterfaceName(mIface);

                mNetworkInfo.setIsAvailable(true);
                if(DBG) Slog.d(TAG, "get dhcp succeed.");
                mNetworkInfo.setDetailedState(DetailedState.CONNECTED, null, null);
                Message msg = mCsHandler.obtainMessage(EVENT_STATE_CHANGED, mNetworkInfo);
                msg.sendToTarget();
				sendStateBroadcast(EthernetManager.EVENT_CONFIGURATION_SUCCEEDED);
            }
        }, "ETH_DHCP");
        dhcpThread.start();
    }

  public DhcpResults getIpConfigure(EthernetDevInfo info){

		InetAddress netmask = null;
		int prefixLength = 0;
		DhcpResults mDhcpResults = new DhcpResults();

		if(info == null)
			return null;

		if(info.getNetMask() == null || info.getNetMask().matches("") ){
			netmask = NetworkUtils.numericToInetAddress("0.0.0.0");
		} else {
			netmask = NetworkUtils.numericToInetAddress(info.getNetMask());
		}
    prefixLength = NetworkUtils.netmaskIntToPrefixLength(NetworkUtils.inetAddressToInt((Inet4Address)netmask));	
		
    mDhcpResults.addLinkAddress(info.getIpAddress(), prefixLength);
    mDhcpResults.addDns(info.getDnsAddr());
    
		if(info.getGateWay() != null && !info.getGateWay().matches(""))
			mDhcpResults.addGateway(info.getGateWay());
		else
			mDhcpResults.addGateway("0.0.0.0");

		return mDhcpResults;
	}

  public LinkAddress getLinkAddress(EthernetDevInfo info){

		InetAddress netmask = null;
		int prefixLength = 0;
    InetAddress ipaddr = null;

		if(info == null)
			return null;

		if(info.getNetMask() == null || info.getNetMask().matches("") ){
			netmask = NetworkUtils.numericToInetAddress("0.0.0.0");
		} else {
			netmask = NetworkUtils.numericToInetAddress(info.getNetMask());
		}
    prefixLength = NetworkUtils.netmaskIntToPrefixLength(NetworkUtils.inetAddressToInt((Inet4Address)netmask));	
		ipaddr = NetworkUtils.numericToInetAddress(info.getIpAddress());
		LinkAddress linkAddress = new LinkAddress(ipaddr, prefixLength);

		return linkAddress;
	}

	public void ConnectNetwork(boolean up) {
		if(DBG) Slog.d(TAG, "ConnectNetwork: Up is " + up + ", mLinkUp is " + mLinkUp +
					", On is " + mEthManage.isOn() + ", mIface " + mIface);
		if(!mEthManage.isConfigured()) {
		    if(mIface != null)
		        Log.d(TAG, "no configuration for " + mIface);
			return;
		}
        /* connect */
		if(up && mEthManage.isOn()) {
			EthernetDevInfo ifaceInfo = mEthManage.getSavedConfig();
			if(ifaceInfo == null) {
                Log.e(TAG, "get configuration failed.");
				return;
			}
			synchronized(mIface) {
				if(!mIface.equals(ifaceInfo.getIfName())) {
					if(!mIface.isEmpty()) {
						NetworkUtils.stopDhcp(mIface);
						NetworkUtils.disableInterface(mIface);
					}
					mIface = ifaceInfo.getIfName();
				}
			}
			NetworkUtils.enableInterface(mIface);

			if(mLinkUp == false)
				return;
            
            /* dhcp way */
			if(mEthManage.isDhcp()) {
			    /* make sure iface to 0.0.0.0 */
				try{
					mNMService.clearInterfaceAddresses(mIface);
					NetworkUtils.resetConnections(mIface, 0);
				} catch (RemoteException e) {
					Log.e(TAG, "ERROR: " + e);
				}
				/* stop dhcp if already running */
				if(SystemProperties.get("dhcp." + mIface + ".result").equals("ok")) {
					NetworkUtils.stopDhcp(mIface);
					sendStateBroadcast(EthernetManager.EVENT_CONFIGURATION_FAILED);
				}
				if(DBG) Slog.d(TAG, "connecting and running dhcp.");
				runDhcp();
			} else {
			    /* static ip way */
				NetworkUtils.stopDhcp(mIface);

        DhcpResults mDhcpResults = getIpConfigure(ifaceInfo);
        mLinkProperties = mDhcpResults.linkProperties;
        mLinkProperties.setInterfaceName(mIface);

        LinkAddress linkAddress = getLinkAddress(ifaceInfo);
        InterfaceConfiguration ifcg = new InterfaceConfiguration();
				ifcg.setLinkAddress(linkAddress);
				ifcg.setInterfaceUp();
				try{
					mNMService.setInterfaceConfig(mIface, ifcg);
				} catch (Exception e) {
					Log.e(TAG, "ERROR: " + e);
					sendStateBroadcast(EthernetManager.EVENT_CONFIGURATION_FAILED);
					return;
				}
                if(DBG) Slog.d(TAG, "connecting and confgure static ip address.");
                mNetworkInfo.setIsAvailable(true);
                mNetworkInfo.setDetailedState(DetailedState.CONNECTED, null, null);
                Message msg = mCsHandler.obtainMessage(EVENT_STATE_CHANGED, mNetworkInfo);
                msg.sendToTarget();
				sendStateBroadcast(EthernetManager.EVENT_CONFIGURATION_SUCCEEDED);
			}
		} else if(isTeardownRequested()) {
		     /* disconnect */
		    Slog.d(TAG, "teardown network.");  
			NetworkUtils.stopDhcp(mIface);
			mLinkProperties.clear();
			NetworkUtils.disableInterface(mIface);
			mNetworkInfo.setIsAvailable(false);
			mNetworkInfo.setDetailedState(DetailedState.DISCONNECTED, null, null);
			Message msg = mCsHandler.obtainMessage(EVENT_STATE_CHANGED, mNetworkInfo);
			msg.sendToTarget();
			sendStateBroadcast(EthernetManager.EVENT_DISCONNECTED);
		}
	}

    private void sendStateBroadcast(int event) {
        Intent intent = new Intent(EthernetManager.NETWORK_STATE_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
									| Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra(EthernetManager.EXTRA_NETWORK_INFO, mNetworkInfo);
        intent.putExtra(EthernetManager.EXTRA_LINK_PROPERTIES,
							new LinkProperties (mLinkProperties));
		intent.putExtra(EthernetManager.EXTRA_ETHERNET_STATE, event);
        mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
    }

    public static synchronized EthernetDataTracker getInstance() {
        if (sInstance == null) sInstance = new EthernetDataTracker();
        return sInstance;
    }

    public Object Clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    public void setTeardownRequested(boolean isRequested) {
        mTeardownRequested.set(isRequested);
    }

    public boolean isTeardownRequested() {
        return mTeardownRequested.get();
    }

    /**
     * Begin monitoring connectivity
     */
	public void startMonitoring(Context context, Handler target) {
		mContext = context;
		mCsHandler = target;

		// register for notifications from NetworkManagement Service
		IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
		mNMService = INetworkManagementService.Stub.asInterface(b);

		mEthManage = EthernetManager.getInstance();
		if (mEthManage == null) {
			Slog.e(TAG, "mEthManage is null !");
			Slog.e(TAG, "startMonitoring failed!");
			return;
		} else {
			Slog.d(TAG, "mEthManage is Ready.");
		}

		// enable and try to connect to an ethernet interface that  already exists
		sIfaceMatch = context.getResources().getString( R.string.config_ethernet_iface_regex );
		if(DBG) Slog.d(TAG, "ethernet_iface_regex: [" + sIfaceMatch +"]");

		List<EthernetDevInfo> ethInfos = mEthManage.getDeviceNameList();
		EthernetDevInfo saveInfo = mEthManage.getSavedConfig();
		if(saveInfo != null && ethInfos != null) {
			for (EthernetDevInfo info : ethInfos) {
				if (info.getIfName().matches(saveInfo.getIfName())){
					saveInfo.setIfName(info.getIfName());
					saveInfo.setHwaddr(info.getHwaddr());
					if(DBG) Slog.d(TAG, "startMonitoring: updateDevInfo.");
					mEthManage.updateDevInfo(saveInfo);
				}
			}
		}

        mInterfaceObserver = new InterfaceObserver(this);
		try {
			mNMService.registerObserver(mInterfaceObserver);
		} catch (RemoteException e) {
			Log.e(TAG, "Could not register InterfaceObserver " + e);
		}
		Slog.d(TAG, "startMonitoring succeed.");
	}

    /**
     * Disable connectivity to a network
     * TODO: do away with return value after making MobileDataStateTracker async
     */
    public boolean teardown() {
        mTeardownRequested.set(true);
		ConnectNetwork(false);
        return true;
    }

    /**
     * Re-enable connectivity to a network after a {@link #teardown()}.
     */
    public boolean reconnect() {
        mTeardownRequested.set(false);
		ConnectNetwork(true);
        return true;
    }

    @Override
    public void captivePortalCheckComplete() {
        // not implemented
    }

    /**
     * Turn the wireless radio off for a network.
     * @param turnOn {@code true} to turn the radio on, {@code false}
     */
    public boolean setRadio(boolean turnOn) {
        return true;
    }

    /**
     * @return true - If are we currently tethered with another device.
     */
    public synchronized boolean isAvailable() {
        return mNetworkInfo.isAvailable();
    }

    /**
     * Tells the underlying networking system that the caller wants to
     * begin using the named feature. The interpretation of {@code feature}
     * is completely up to each networking implementation.
     * @param feature the name of the feature to be used
     * @param callingPid the process ID of the process that is issuing this request
     * @param callingUid the user ID of the process that is issuing this request
     * @return an integer value representing the outcome of the request.
     * The interpretation of this value is specific to each networking
     * implementation+feature combination, except that the value {@code -1}
     * always indicates failure.
     * TODO: needs to go away
     */
    public int startUsingNetworkFeature(String feature, int callingPid, int callingUid) {
        return -1;
    }

    /**
     * Tells the underlying networking system that the caller is finished
     * using the named feature. The interpretation of {@code feature}
     * is completely up to each networking implementation.
     * @param feature the name of the feature that is no longer needed.
     * @param callingPid the process ID of the process that is issuing this request
     * @param callingUid the user ID of the process that is issuing this request
     * @return an integer value representing the outcome of the request.
     * The interpretation of this value is specific to each networking
     * implementation+feature combination, except that the value {@code -1}
     * always indicates failure.
     * TODO: needs to go away
     */
    public int stopUsingNetworkFeature(String feature, int callingPid, int callingUid) {
        return -1;
    }

    @Override
    public void setUserDataEnable(boolean enabled) {
        Log.w(TAG, "ignoring setUserDataEnable(" + enabled + ")");
    }

    @Override
    public void setPolicyDataEnable(boolean enabled) {
        Log.w(TAG, "ignoring setPolicyDataEnable(" + enabled + ")");
    }

    /**
     * Check if private DNS route is set for the network
     */
    public boolean isPrivateDnsRouteSet() {
        return mPrivateDnsRouteSet.get();
    }

    /**
     * Set a flag indicating private DNS route is set
     */
    public void privateDnsRouteSet(boolean enabled) {
        mPrivateDnsRouteSet.set(enabled);
    }

    /**
     * Fetch NetworkInfo for the network
     */
    public synchronized NetworkInfo getNetworkInfo() {
        return mNetworkInfo;
    }

    /**
     * Fetch LinkProperties for the network
     */
    public synchronized LinkProperties getLinkProperties() {
        return new LinkProperties(mLinkProperties);
    }

   /**
     * A capability is an Integer/String pair, the capabilities
     * are defined in the class LinkSocket#Key.
     *
     * @return a copy of this connections capabilities, may be empty but never null.
     */
    public LinkCapabilities getLinkCapabilities() {
        return new LinkCapabilities(mLinkCapabilities);
    }

    /**
     * Fetch default gateway address for the network
     */
    public int getDefaultGatewayAddr() {
        return mDefaultGatewayAddr.get();
    }

    /**
     * Check if default route is set
     */
    public boolean isDefaultRouteSet() {
        return mDefaultRouteSet.get();
    }

    /**
     * Set a flag indicating default route is set for the network
     */
    public void defaultRouteSet(boolean enabled) {
        mDefaultRouteSet.set(enabled);
    }

    /**
     * Return the system properties name associated with the tcp buffer sizes
     * for this network.
     */
    public String getTcpBufferSizesPropName() {
        return "net.tcp.buffersize.wifi";
    }

    public void setDependencyMet(boolean met) {
        // not supported on this network
    }
    
    public LinkQualityInfo getLinkQualityInfo(){
        // not supported on this network
        return null;
    }
    
    public void captivePortalCheckCompleted(boolean isCaptive){
        // not supported on this network
    }
    
    @Override
    public void addStackedLink(LinkProperties link) {
        mLinkProperties.addStackedLink(link);
    }

    @Override
    public void removeStackedLink(LinkProperties link) {
        mLinkProperties.removeStackedLink(link);
    }

    @Override
    public void supplyMessenger(Messenger messenger) {
        // not supported on this network
    }

    @Override
    public String getNetworkInterfaceName() {
        // not supported on this network
        return null;
    }

    @Override
    public void startSampling(SamplingDataTracker.SamplingSnapshot s) {
        // not supported on this network
    }

    @Override
    public void stopSampling(SamplingDataTracker.SamplingSnapshot s) {
        // not supported on this network
    }
}
