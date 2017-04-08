/*******************************************************************************
 *
 *  Copyright (C) 2012 Broadcom Corporation
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
package com.broadcom.bt.service;

import java.util.ArrayList;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.provider.Settings;
import android.util.Log;

import com.android.bluetooth.R;
import com.android.bluetooth.a2dp.A2dpService;
import com.android.bluetooth.btservice.Config;
import com.android.bluetooth.hdp.HealthService;
import com.android.bluetooth.hfp.HeadsetService;
import com.android.bluetooth.hid.HidService;
import com.android.bluetooth.pan.PanService;
import com.android.bluetooth.gatt.GattService;
import com.android.bluetooth.map.BluetoothMapService;
import com.android.bluetooth.btservice.AdapterService;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.broadcom.bt.service.opp.OppService;
import com.broadcom.bt.service.sap.SapService;
import com.broadcom.bt.service.ftp.FTPService;
import com.broadcom.bt.service.dun.DunService;
import com.broadcom.bt.service.hidd.HidDeviceService;
import com.broadcom.bt.service.hfdevice.HfDeviceService;
import com.broadcom.bt.service.avrcp.AvrcpControllerService;
import com.broadcom.bt.service.map.MapClientService;


/**
 * Overrides the Bluedroid standard profile config and adds Broadcom specific
 * profiles
 *
 * @author fredc
 *
 */
public class ProfileConfig extends Config {
    private static final String TAG = "BtSettings.ProfileConfig";
    private static Context mContext;
    /**
     * List of profile services.
     */
    private static final int INDEX_CLASS_NAME=0;
    private static final int INDEX_NAME=1;
    private static final int INDEX_DESCRIPTION=2;
    private static final int INDEX_SUPPORTED=3;
    private static final int INDEX_DEFAULT_START=4;
    private static final int INDEX_CONFIGURABLE=5;
    private static final int INDEX_QUIET_MODE =6;
    private static final int INDEX_CFG_DEVICE_MODE =7;
    private static final int INDEX_DETAILED_FRAGMENT=8;

    // Profile will run in both device and phone mode and is Independent of device mode switching
    // Typically for the profile which is not concerned about "device mode switching"
    // will use CFG_MODE_DUAL
    public static final int CFG_MODE_DUAL = 0;

    // The profile will run only on Device and will be disabled in phone mode
    public static final int CFG_MODE_DEVICE = 1;
    // The profile will run only on Phone and will be disabled in device mode
    public static final int CFG_MODE_PHONE = 2;
    // The same service supports both DEVICE and PHONE so a stop and restart
    // of the same service is required when mode switch happens
    public static final int CFG_MODE_DEVICE_OR_PHONE = 3;


    // Do not include OPP and PBAP, because their services
    // are not managed by AdapterService
    private static final Object[][] PROFILE_SERVICES= {
        //Headset/HFP configuration
        {   HeadsetService.class,
            R.string.profile_hs_hfp,
            R.string.profile_description_hs_hfp,
            R.bool.profile_supported_hs_hfp,
            R.bool.profile_default_start_hs_hfp,
            R.bool.profile_configurable_hs_hfp,
            R.bool.run_in_quiet_mode_hs_hfp,
            R.integer.profile_cfg_run_in_device_mode_hs_hfp,
            null }
        ,
        //A2DP configuration
        {   A2dpService.class,
            R.string.profile_a2dp,
            R.string.profile_description_a2dp,
            R.bool.profile_supported_a2dp,
            R.bool.profile_default_start_a2dp,
            R.bool.profile_configurable_a2dp,
            R.bool.run_in_quiet_mode_a2dp,
            R.integer.profile_cfg_run_in_device_mode_a2dp,
            null }
        ,
        //HID Host configuration
        {   HidService.class,
            R.string.profile_hid,
            R.string.profile_description_hid,
            R.bool.profile_supported_hid,
            R.bool.profile_default_start_hid,
            R.bool.profile_configurable_hid,
            R.bool.run_in_quiet_mode_hid,
            R.integer.profile_cfg_run_in_device_mode_hid,
            null }
        ,
        //Heath Profile configuration
        {   HealthService.class,
            R.string.profile_hdp,
            R.string.profile_description_hdp,
            R.bool.profile_supported_hdp,
            R.bool.profile_default_start_hdp,
            R.bool.profile_configurable_hdp,
            R.bool.run_in_quiet_mode_hdp,
            R.integer.profile_cfg_run_in_device_mode_hdp,
            null }
        ,
        //PAN configuration
        {   PanService.class,
            R.string.profile_pan,
            R.string.profile_description_pan,
            R.bool.profile_supported_pan,
            R.bool.profile_default_start_pan,
            R.bool.profile_configurable_pan,
            R.bool.run_in_quiet_mode_pan,
            R.integer.profile_cfg_run_in_device_mode_pan,
            null }
        ,
        //Gatt configuration
        {   GattService.class,
            R.string.profile_gatt,
            R.string.profile_description_gatt,
            R.bool.profile_supported_gatt,
            R.bool.profile_default_start_gatt,
            R.bool.profile_configurable_gatt,
            R.bool.run_in_quiet_mode_gatt,
            R.integer.profile_cfg_run_in_device_mode_gatt,
            null }
        ,
        //DUN configuration
        {   DunService.class,
            R.string.profile_dun,
            R.string.profile_description_dun,
            R.bool.profile_supported_dun,
            R.bool.profile_default_start_dun,
            R.bool.profile_configurable_dun,
            R.bool.run_in_quiet_mode_dun,
            R.integer.profile_cfg_run_in_device_mode_dun,
            null }

        ,
        //MAP configuration
        {   BluetoothMapService.class,
            R.string.profile_mse,
            R.string.profile_description_mse,
            R.bool.profile_supported_map,
            R.bool.profile_default_start_mse,
            R.bool.profile_configurable_mse,
            R.bool.run_in_quiet_mode_mse,
            R.integer.profile_cfg_run_in_device_mode_mse,
            null
            }
        ,
        //Sap configuration
        {   SapService.class,
            R.string.profile_sap,
            R.string.profile_description_sap,
            R.bool.profile_supported_sap,
            R.bool.profile_default_start_sap,
            R.bool.profile_configurable_sap,
            R.bool.run_in_quiet_mode_sap,
            R.integer.profile_cfg_run_in_device_mode_sap,
            null }
        ,
        //FTP Server configuration
        {   FTPService.class,
            R.string.profile_ftp,
            R.string.profile_description_ftp,
            R.bool.profile_supported_ftp_server,
            R.bool.profile_default_start_ftp_server,
            R.bool.profile_configurable_ftp_server,
            R.bool.run_in_quiet_mode_ftp,
            R.integer.profile_cfg_run_in_device_mode_ftp_server,
            null }
        ,
        //HID-Device configuration
        {   HidDeviceService.class,
            R.string.profile_hidd,
            R.string.profile_description_hidd,
            R.bool.profile_supported_hidd,
            R.bool.profile_default_start_hidd,
            R.bool.profile_configurable_hidd,
            R.bool.run_in_quiet_mode_hidd,
            R.integer.profile_cfg_run_in_device_mode_hidd,
            null }
        ,
        //AVRCP Controller profile configuration
        {   AvrcpControllerService.class,
            R.string.profile_avrcp_ct,
            R.string.profile_description_avrcp_ct,
            R.bool.profile_supported_avrcp_ct,
            R.bool.profile_default_start_avrcp_ct,
            R.bool.profile_configurable_avrcp_ct,
            R.bool.run_in_quiet_mode_avrcp_ct,
            R.integer.profile_cfg_run_in_device_mode_avrcp_ct,
            null }
        ,
         // OPP Service configuration
        {   OppService.class,
            R.string.profile_opp,
            R.string.profile_description_opp,
            R.bool.profile_supported_opp_1_2_service,
            R.bool.profile_default_start_opp_service,
            R.bool.profile_configurable_opp_service,
            R.bool.run_in_quiet_mode_opp,
            R.integer.profile_cfg_run_in_device_mode_opp_1_2,
            null }
        ,
        //HF device profile configuration
        {   HfDeviceService.class,
            R.string.profile_hfdevice,
            R.string.profile_description_hfdevice,
            R.bool.profile_supported_hfdevice,
            R.bool.profile_default_start_hfdevice,
            R.bool.profile_configurable_hfdevice,
            R.bool.run_in_quiet_mode_hfdevice,
            R.integer.profile_cfg_run_in_device_mode_hfdevice,
            null }
        ,
        //MAP Client configuration
        {   MapClientService.class,
            R.string.profile_mce,
            R.string.profile_description_mce,
            R.bool.profile_supported_mce,
            R.bool.profile_default_start_mce,
            R.bool.profile_configurable_mce,
            R.bool.run_in_quiet_mode_mce,
            R.integer.profile_cfg_run_in_device_mode_mce,
            null }
    };

    public static class ProfileCfg {
        public String mName;
        public String mDisplayName;
        public String mDescription;
        public boolean mDefaultStarted;
        public boolean mUserConfigurable;
        public boolean mRunInQuietMode;
        public int mDeviceModeCfg;
        public String mSettingsActivityPkgClassName;
    }

    @SuppressWarnings("rawtypes")
    private static Class[] SUPPORTED_PROFILES = new Class[0];
    private static Class[] QUIET_MODE_PROFILES = new Class[0];
    private static ProfileCfg[] SUPPORTED_PROFILES_CFG = new ProfileCfg[0];
    private static final String SETTINGS_PREFIX = "bt_svcst_";

    private static  boolean mQuietModeSupported = true;

    private static  boolean mDeviceModeCfgSupported = true;

    private static void initSettings() {
        // Initialize Bluetooth profile settins
        if (mContext != null) {
            ContentResolver cr = mContext.getContentResolver();
            boolean settingsExist = Settings.System.getInt(cr, SETTINGS_PREFIX + "init", 0) != 0;
            if (!settingsExist) {
                Log.i(TAG, "*********Initializing Bluetooth Profile Settings*******");
                for (int i = 0; i < SUPPORTED_PROFILES_CFG.length; i++) {
                    ProfileCfg cfg = SUPPORTED_PROFILES_CFG[i];
                    Settings.System.putInt(cr, SETTINGS_PREFIX + cfg.mName, cfg.mDefaultStarted ? 1
                            : 0);
                }
                Settings.System.putInt(cr, SETTINGS_PREFIX + "init", 1);
            }
        }
    }

    private static void checkAndAdjustDeviceModeConfiguration() {
        SharedPreferences settings = PreferenceManager.
            getDefaultSharedPreferences(mContext);
        int deviceMode = settings.getInt("DEVICEMODE",-1);
        Log.d(TAG,"deviceMode from shared preference   " + deviceMode );
        if( deviceMode == -1 )
        {
            Resources r = mContext.getResources();
            boolean isDevicemode = r
                    .getBoolean(com.android.bluetooth.R.bool.phone_mode);
            if( isDevicemode )
                deviceMode = AdapterService.DEFAULT_MODE;
            else
                deviceMode = AdapterService.HEADSET_MODE;
        }
        Log.d(TAG,"checkAndAdjustDeviceModeConfiguration deviceMode" + deviceMode );

        for (int i = 0; i < SUPPORTED_PROFILES_CFG.length; i++) {
            boolean isProfileEnabled = isProfileConfiguredEnabled(SUPPORTED_PROFILES_CFG[i].mName);
            if (SUPPORTED_PROFILES_CFG[i].mDeviceModeCfg == CFG_MODE_DEVICE) {
                //Enable Device mode profile if not enabled
                //and disable Phone mode services if enabled
                if ((AdapterService.HEADSET_MODE == deviceMode) &&
                    !isProfileEnabled)
                    saveProfileSetting(SUPPORTED_PROFILES_CFG[i].mName,true);
                else if ((AdapterService.DEFAULT_MODE == deviceMode) &&
                    isProfileEnabled)
                    saveProfileSetting(SUPPORTED_PROFILES_CFG[i].mName,false);
            } else if (SUPPORTED_PROFILES_CFG[i].mDeviceModeCfg == CFG_MODE_PHONE) {
                //Enable Phone mode profile if not enabled
                //and disable Device mode services if enabled
                if ((AdapterService.DEFAULT_MODE == deviceMode) &&
                    !isProfileEnabled)
                    saveProfileSetting(SUPPORTED_PROFILES_CFG[i].mName,true);
                else if ((AdapterService.HEADSET_MODE == deviceMode) &&
                    isProfileEnabled)
                    saveProfileSetting(SUPPORTED_PROFILES_CFG[i].mName,false);
            } else if (SUPPORTED_PROFILES_CFG[i].mDeviceModeCfg == CFG_MODE_DEVICE_OR_PHONE
                     && !isProfileEnabled) {
                     // For Device and Phone mode service enable if not enabled
                 saveProfileSetting(SUPPORTED_PROFILES_CFG[i].mName,true);
            }
        }

    }

    @SuppressWarnings("rawtypes")
    public static void init(Context ctx) {
        if (ctx == null) {
            return;
        }
        mContext = ctx;
        Resources resources = ctx.getResources();
        if (resources == null) {
            return;
        }
        ArrayList<Class> profiles = new ArrayList<Class>(PROFILE_SERVICES.length);
        ArrayList<ProfileCfg> cfgs = new ArrayList<ProfileCfg>(PROFILE_SERVICES.length);
        for (int i = 0; i < PROFILE_SERVICES.length; i++) {
            boolean supported = resources.getBoolean((Integer)PROFILE_SERVICES[i][INDEX_SUPPORTED]);
            if (supported) {
                Class profile = (Class)PROFILE_SERVICES[i][INDEX_CLASS_NAME];
                Log.d(TAG, "Adding " +profile.getSimpleName());
                profiles.add(profile);

                ProfileCfg cfg = new ProfileCfg();
                cfg.mName = profile.getName();
                // Add display name
                String displayName = null;
                try {
                    displayName = resources.getString((Integer)PROFILE_SERVICES[i][INDEX_NAME]);
                } catch (Throwable t) {
                    Log.w(TAG, "Profile display name not found", t);
                }
                cfg.mDisplayName = (displayName == null ? profile.getName()
                        : displayName);

                // Add description
                String description = null;
                try {
                    description = resources.getString
                            ((Integer)PROFILE_SERVICES[i][INDEX_DESCRIPTION]);
                } catch (Throwable t) {
                    Log.w(TAG, "Profile display name not found", t);
                }
                cfg.mDescription = (description == null ? profile.getName()
                        : description);

                // Add configuration flags
                cfg.mDefaultStarted = resources.getBoolean
                        ((Integer)PROFILE_SERVICES[i][INDEX_DEFAULT_START]);
                cfg.mUserConfigurable = resources
                        .getBoolean((Integer)PROFILE_SERVICES[i][INDEX_CONFIGURABLE]);
                cfg.mRunInQuietMode = resources
                        .getBoolean((Integer)PROFILE_SERVICES[i][INDEX_QUIET_MODE]);
                cfg.mDeviceModeCfg = resources
                        .getInteger((Integer)PROFILE_SERVICES[i][INDEX_CFG_DEVICE_MODE]);
                // Add activity
                cfg.mSettingsActivityPkgClassName =
                        ((String)PROFILE_SERVICES[i][INDEX_DETAILED_FRAGMENT]);
                cfgs.add(cfg);
            }
        }
        int totalProfiles = profiles.size();
        SUPPORTED_PROFILES = new Class[totalProfiles];
        SUPPORTED_PROFILES_CFG = new ProfileCfg[totalProfiles];
        for (int i = 0; i < totalProfiles; i++) {
            SUPPORTED_PROFILES[i] = profiles.get(i);
            SUPPORTED_PROFILES_CFG[i] = cfgs.get(i);
        }
        mQuietModeSupported = resources.getBoolean((Integer)R.bool.supports_quiet_mode);
        mDeviceModeCfgSupported = resources.getBoolean((Integer)R.bool.supports_device_mode_cfg);
        initSettings();
        checkAndAdjustDeviceModeConfiguration();
    }

    private static int findProfileIndex(String className) {
        for (int i = 0; i < SUPPORTED_PROFILES.length; i++) {
            if (className.equals(SUPPORTED_PROFILES[i].getName())) {
                return i;
            }
        }
        return -1;
    }

    @SuppressWarnings("rawtypes")
    public static Class[] getSupportedProfiles() {
        return SUPPORTED_PROFILES;
    }

    public static ProfileCfg[] getSupportedProfileCfgs() {
        return SUPPORTED_PROFILES_CFG;
    }

    public boolean isProfileSupported(String profileName) {
        return findProfileIndex(profileName) >= 0;
    }

    public static boolean isQuietModeProfile(String profileName) {
        if ( mQuietModeSupported== false) {
            // Quiet mode is not supported. Return false by default for all profiles.
            return false;
        }
        for (int i = 0; i < SUPPORTED_PROFILES_CFG.length; i++)
            if (SUPPORTED_PROFILES_CFG[i].mName.equals(profileName) &&
                SUPPORTED_PROFILES_CFG[i].mRunInQuietMode == true)
                return true;
        return false;
    }


    public static boolean isDeviceModeProfile(String profileName) {
        if ( mDeviceModeCfgSupported== false) {
            // Device mode configuration is not supported. Return false by default for all profiles.
            return false;
        }
        for (int i = 0; i < SUPPORTED_PROFILES_CFG.length; i++)
            if (SUPPORTED_PROFILES_CFG[i].mName.equals(profileName) &&
                (SUPPORTED_PROFILES_CFG[i].mDeviceModeCfg == CFG_MODE_DEVICE ||
                SUPPORTED_PROFILES_CFG[i].mDeviceModeCfg == CFG_MODE_DEVICE_OR_PHONE))
                return true;
        return false;
    }

    public static boolean isPhoneModeProfile(String profileName) {
        if ( mDeviceModeCfgSupported== false) {
            // Device mode configuration is not supported. Return false by default for all profiles.
            return false;
        }
        for (int i = 0; i < SUPPORTED_PROFILES_CFG.length; i++)
            if (SUPPORTED_PROFILES_CFG[i].mName.equals(profileName) &&
                (SUPPORTED_PROFILES_CFG[i].mDeviceModeCfg == CFG_MODE_PHONE ||
                SUPPORTED_PROFILES_CFG[i].mDeviceModeCfg == CFG_MODE_DEVICE_OR_PHONE))
                return true;
        return false;
    }


    /**
     * Save the profile state (on or off)
     *
     * @param on
     * @return
     */
    public static void saveProfileSetting(String profileName, boolean enabled) {
        Log.d(TAG,"saveProfileSetting profileName= "+profileName+"enabled"+enabled);
        if (mContext != null) {
            ContentResolver cr = mContext.getContentResolver();
            int profileIndex = findProfileIndex(profileName);
            if (profileIndex < 0) {
                Log.w(TAG, "Profile not supported: " + profileName);
                return;
            }

            Settings.System.putInt(cr, SETTINGS_PREFIX + profileName, enabled ? 1 : 0);
        }
    }

    public static boolean isProfileConfiguredEnabled(String profileName) {
        int profileIndex = findProfileIndex(profileName);
        if (profileIndex < 0) {
            return false;
        }

        if (mContext != null) {
            ContentResolver cr = mContext.getContentResolver();
            try {
                Log.d(TAG, "getProfileSaveSetting: " + profileName);
                return Settings.System.getInt(cr, SETTINGS_PREFIX + profileName) == 1;
            } catch (Throwable t) {
                Log.d(TAG, "Unable to read settings", t);
            }
        }
        // If the profile entry not present,
        // return true assuming by default a profile is always enabled.
        return true;
    }

    /**
     * This function checks if Device mode configuration is supported
     *
     * @return true if Device mode configuration is supported
     */
    public static boolean isDeviceModeConfigurationSupported() {
        return mDeviceModeCfgSupported;
    }

}
