/*******************************************************************************
 *
 *  Copyright (C) 2013 Broadcom Corporation
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
package com.android.bluetooth.gatt;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Helper class used to manage advertisement package filters.
 * @hide
 */
/*package*/ class AdvFilterQueue {
    public static final int TYPE_DEVICE_ADDRESS = 0;
    public static final int TYPE_SERVICE_DATA = 1;
    public static final int TYPE_SERVICE_UUID = 2;
    public static final int TYPE_SOLICIT_UUID = 3;
    public static final int TYPE_LOCAL_NAME = 4;
    public static final int TYPE_MANUFACTURER_DATA = 5;

    class Entry {
        public String address;
        public byte addr_type;
        public byte type;
        public UUID uuid;
        public String name;
        public int company;
        public byte[] data;
    }

    private List<Entry> mEntries = new ArrayList<Entry>();

    void addDeviceAddress(String address, byte type) {
        Entry entry = new Entry();
        entry.type = TYPE_DEVICE_ADDRESS;
        entry.address = address;
        entry.addr_type = type;
        mEntries.add(entry);
    }

    void addServiceChanged() {
        Entry entry = new Entry();
        entry.type = TYPE_SERVICE_DATA;
        mEntries.add(entry);
    }

    void addUuid(UUID uuid) {
        Entry entry = new Entry();
        entry.type = TYPE_SERVICE_UUID;
        entry.uuid = uuid;
        mEntries.add(entry);
    }

    void addSolicitUuid(UUID uuid) {
        Entry entry = new Entry();
        entry.type = TYPE_SOLICIT_UUID;
        entry.uuid = uuid;
        mEntries.add(entry);
    }

    void addName(String name) {
        Entry entry = new Entry();
        entry.type = TYPE_LOCAL_NAME;
        entry.name = name;
        mEntries.add(entry);
    }

    void addManufacturerData(int company, byte[] data) {
        Entry entry = new Entry();
        entry.type = TYPE_MANUFACTURER_DATA;
        entry.company = company;
        entry.data = data;
        mEntries.add(entry);
    }

    Entry pop() {
        Entry entry = mEntries.get(0);
        mEntries.remove(0);
        return entry;
    }

    boolean isEmpty() {
        return mEntries.isEmpty();
    }

    void clearUuids() {
        for(Iterator <Entry> it = mEntries.iterator(); it.hasNext();) {
            Entry entry = it.next();
            if (entry.type == TYPE_SERVICE_UUID) it.remove();
        }
    }

    void clear() {
        mEntries.clear();
    }
}
