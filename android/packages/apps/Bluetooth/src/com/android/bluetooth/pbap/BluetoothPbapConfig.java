/************************************************************************************
 *
 *  Copyright (C) 2009-2012 Broadcom Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ************************************************************************************/
package com.android.bluetooth.pbap;

import com.android.bluetooth.R;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import com.android.vcard.VCardConfig;

public class BluetoothPbapConfig {
    public static final boolean DEBUG = BluetoothPbapService.DEBUG;
    public static final boolean VERBOSE = BluetoothPbapService.VERBOSE;

    public static final int DEFAULT_VCARD_CFG  =
            VCardConfig.FLAG_REFRAIN_RELATION
            | VCardConfig.FLAG_REFRAIN_IMS
            | VCardConfig.FLAG_REFRAIN_SIP_ADDRESS
            | VCardConfig.FLAG_REFRAIN_EVENTS;

    public static final boolean USE_NAME_AND_NUMBER_FILTER=true;

    private static boolean sUseProfileForOwnerVcard=true;
    private static boolean sIncludePhotosInVcard = false;

    public static void init(Context ctx) {
        Resources r = ctx.getResources();
        if (r != null) {
            try {
                sUseProfileForOwnerVcard = r.getBoolean(R.bool.pbap_use_profile_for_owner_vcard);
            } catch(Exception e) {
                Log.e("BluetoothPbapConfig","",e);
            }
            try {
                sIncludePhotosInVcard = r.getBoolean(R.bool.pbap_include_photos_in_vcard);
            } catch(Exception e) {
                Log.e("BluetoothPbapConfig","",e);
            }
        }
    }

    /**
     * If true, owner vcard will be generated from the "Me" profile
     */
    public static boolean useProfileForOwnerVcard() {
        return sUseProfileForOwnerVcard;
    }

    /**
     * If true, include photos in contact information returned to PCE
     * @return
     */
    public static boolean includePhotosInVcard() {
        return sIncludePhotosInVcard;
    }


}
