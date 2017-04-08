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

package com.broadcom.bt.util.bmsg;

import android.util.Log;

/***
 * The BMessageVCard class holds information about sender or recipients
 * of MAP message.  It is used by {@link BMessage} and
 * {@link BMessageEnvelope} class.
 *
 */
public class BMessageVCard extends BMessageBase {
    private static final String TAG = "BMessageVCard";
    private static final boolean ERR_CHECK = BMessageManager.ERR_CHECK;

    private BMessageBase mParent;
    private BMessageVCard mPreviousVCard;

    /**
     * @hide
     */
    BMessageVCard(BMessageBase parent, int nativeRef) {
        mParent = parent;
        setNativeRef(nativeRef);
    }

    /**
     * @hide
     */
    private BMessageVCard(BMessageVCard previousVCard, int nativeRef) {
        this(previousVCard.mParent, nativeRef);
        mPreviousVCard = previousVCard;
    }

    /**
     * Returns the next vcard object.
     *
     * @return next vcard object if one exists, null otherwise.
     */
    public BMessageVCard getNextvCard() {
        if (isNativeCreated()) {
            int childNativeObject = BMessageManager.getBvCardNext(mNativeObjectRef);
            if (childNativeObject > 0) {
                return new BMessageVCard(this, childNativeObject);
            }
        }
        return null;
    }

    /**
     * Add a next vcard
     *
     * @return
     */
    /*
    public BMessageVCard addNextvCard() {
        if (isNativeCreated()) {
            int childNativeObject = BMessageManager.addBvCardNext(mNativeObjectRef);
            if (childNativeObject > 0) {
                return new BMessageVCard(this, childNativeObject);
            }
        }
        return null;
    }*/

    /**
     * Set the vcard version.
     *
     * @param versionId
     *            The identifier for the version Possible values are
     *            {@link BMessageConstants#BTA_MA_VCARD_VERSION_21},
     *            {@link BMessageConstants#BTA_MA_VCARD_VERSION_30}.
     */

    public void setVersion(byte versionId) {
        if (isNativeCreated()) {
            BMessageManager.setBvCardVer(mNativeObjectRef, versionId);
        }
    }

    /**
     * Get the vcard version.
     *
     * @return {@link BMessageConstants#INVALID_VALUE} if version is not set.
     *         Otherwise returns a version id defined in
     *         {@link BMessageConstants}.
     */
    public byte getVersion() {
        return (isNativeCreated() ? BMessageManager.getBvCardVer(mNativeObjectRef)
                : BMessageConstants.INVALID_VALUE);
    }

    /**
     * Add a vcard property.
     *
     * @param propId
     *            The property identifier.Possible values are
     *            {@link BMessageConstants#BTA_MA_VCARD_PROP_N},
     *            {@link BMessageConstants#BTA_MA_VCARD_PROP_FN},
     *            {@link BMessageConstants#BTA_MA_VCARD_PROP_TEL},
     *            {@link BMessageConstants#BTA_MA_VCARD_PROP_EMAIL}.
     *
     * @return BMessageVCardProperty return the added BMessageVCardProperty
     */
    public BMessageVCardProperty addProperty(byte propId, String value, String param) {
        if (!isNativeCreated()) {
            return null;
        }
        if (ERR_CHECK && (propId < BMessageConstants.BTA_MA_VCARD_PROP_N
                || propId > BMessageConstants.BTA_MA_VCARD_PROP_EMAIL)) {
            Log.e(TAG, "Invalid vCard property: " + propId);
            return null;
        }
        int nativePropObj = BMessageManager.addBvCardProp(mNativeObjectRef, propId, value, param);
        if (nativePropObj > 0) {
            return new BMessageVCardProperty(this, nativePropObj);
        }
        return null;
    }

    /**
     * Get vcard property for this BMessageVCard.
     *
     * @param propId property ID of required property.
     * @return BMessageVCardProperty return the added BMessageVCardProperty.
     */
    public BMessageVCardProperty getProperty(byte propId) {
        if (!isNativeCreated()) {
            return null;
        }
        if (ERR_CHECK && (propId < BMessageConstants.BTA_MA_VCARD_PROP_N
                || propId > BMessageConstants.BTA_MA_VCARD_PROP_EMAIL)) {
            Log.e(TAG, "Invalid vCard property: " + propId);
            return null;
        }
        int nativePropObj = BMessageManager.getBvCardProp(mNativeObjectRef, propId);
        if (nativePropObj > 0) {
            return new BMessageVCardProperty(this, nativePropObj);
        }
        return null;
    }

}
