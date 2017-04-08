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

package com.android.bluetooth.pbap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

public class BluetoothPbapPhonebookEntryFinder {
    public static final int LIST_ORDER_TYPE_CONTACT_ID = 1;
    public static final int LIST_ORDER_TYPE_NAME = 2;

    public static final int FIND_TYPE_CONTACT_ID_FIRST = 1;
    public static final int FIND_TYPE_NAME_FIRST = 2;

    private static class PhonebookEntryComparator implements
            Comparator<BluetoothPbapPhonebookEntry> {
        public static final int COMPARE_CONTACT_ID = 1;
        public static final int COMPARE_INDEX = 2;
        public static final int COMPARE_NAME = 3;

        private int mCompareType;

        public PhonebookEntryComparator(int compareType) {
            mCompareType = compareType;
        }

        @Override
        public int compare(BluetoothPbapPhonebookEntry object1, BluetoothPbapPhonebookEntry object2) {
            if (object1 == object2) {
                return 0;
            }
            if (object1 == null) {
                return -1;
            } else if (object2 == null) {
                return 1;
            }

            switch (mCompareType) {
                case COMPARE_CONTACT_ID:
                    return (object1.mId == object2.mId ? 0
                            : (object1.mId < object2.mId ? -1 : 1));
                case COMPARE_INDEX:
                    return (object1.mIndex == object2.mIndex ? 0
                            : (object1.mIndex < object2.mIndex ? -1 : 1));

                case COMPARE_NAME:
                default:
                    if (object1.mName == object2.mName) {
                        return 0;
                    } else if (object1.mName == null) {
                        return -1;
                    } else if (object2.mName == null) {
                        return 1;
                    } else {
                        return object1.mName.compareTo(object2.mName);
                    }
            }
        }
    }

    int mFindType;
    int mListOrderType;
    ArrayList<BluetoothPbapPhonebookEntry> mEntries;

    public BluetoothPbapPhonebookEntryFinder(ArrayList<BluetoothPbapPhonebookEntry> entries,
            int listOrderType, int findType) {
        mListOrderType = listOrderType;
        mFindType = findType;
        mEntries = entries;
        if (mFindType == FIND_TYPE_CONTACT_ID_FIRST
                && mListOrderType != LIST_ORDER_TYPE_CONTACT_ID) {
            mEntries = (ArrayList<BluetoothPbapPhonebookEntry>) entries.clone();
            Collections.sort(mEntries, new PhonebookEntryComparator(
                    PhonebookEntryComparator.COMPARE_CONTACT_ID));
        }

    }

    /**
     * Searches a list entries for a match by contactId.
     * If the contact ID is specified, search based on contact ID.
     * Otherwise, search based on name.
     * Assumption: entries are sorted in ascending order based on contactID, not
     * the name
     *
     * @param contactId
     * @param name
     * @param names
     * @return
     */
    public BluetoothPbapPhonebookEntry findPhonebookEntryByContactIdFirst(long contactId,
            String name) {
        // Check if the entries list is sorted by id. If not, create a sort map;
        String compareValue = null;
        BluetoothPbapPhonebookEntry pbEntry = null;
        if (contactId <= 0 && name != null) {
            // Search based on name. We can't assume name is sorted, so do a
            // linear search
            for (int i = 0; i < mEntries.size(); i++) {
                pbEntry = mEntries.get(i);
                compareValue = pbEntry.mName;
                if (compareValue != null) {
                    compareValue = compareValue.trim();
                }

                if (compareValue != null && name.startsWith(compareValue)) {
                    return pbEntry;
                }
            }
        }

        else if (contactId > 0) {
            int len = mEntries.size();
            int startIndex = 0;
            int endIndex = len - 1;
            while (startIndex <= endIndex) {
                int midIndex = (startIndex + endIndex) / 2;
                pbEntry = mEntries.get(midIndex);
                if (contactId == pbEntry.mId) {
                    return pbEntry;
                } else if (contactId > pbEntry.mId) {
                    startIndex = midIndex + 1;
                } else if (contactId < pbEntry.mId) {
                    endIndex = midIndex - 1;
                }
            }
        }
        return null;
    }

    /*
    private static void dumpEntries(ArrayList<PhonebookEntry> entries){
        for (int i=0; i < entries.size();i++) {
            System.out.print("Entry#" + i);
            PhonebookEntry entry = entries.get(i);
            System.out.print(", ");
            dumpEntry(entry);
        }
    }

    private static void dumpEntry(PhonebookEntry entry) {
        if (entry == null) {
            System.out.println("Entry is null");
        return;
        }
        System.out.print ("index=" + entry.mIndex);
        System.out.print (", id=" + entry.mId);
        System.out.println (", name=" + entry.mName);
    }

    public static void main(String[] args) {
        ArrayList<PhonebookEntry> list = new ArrayList<PhonebookEntry>();
        ArrayList<PhonebookEntry> list2 = new ArrayList<PhonebookEntry>();
        for (int i=1; i <= 10;i ++) {
            list.add(new PhonebookEntry(i, 100*i, "Entry " + i ));
            list2.add(new PhonebookEntry(i, 10*100 - 100*i, "Entry " + i ));
        }

        PhonebookEntryFinder finder = new PhonebookEntryFinder(list, PhonebookEntryFinder.LIST_ORDER_TYPE_CONTACT_ID, PhonebookEntryFinder.FIND_TYPE_CONTACT_ID_FIRST);
        PhonebookEntryFinder finder2 = new PhonebookEntryFinder(list2, PhonebookEntryFinder.LIST_ORDER_TYPE_NAME, PhonebookEntryFinder.FIND_TYPE_CONTACT_ID_FIRST);

        System.out.println("Finder 1(original):");
        dumpEntries(list);

        System.out.println("Finder 1:");
        dumpEntries(finder.mEntries);

        System.out.println("Find result");
        dumpEntry(finder.findPhonebookEntryByContactIdFirst(Long.parseLong(args[0]),""));

        System.out.println("Finder 2(original):");
        dumpEntries(list2);
        System.out.println("Finder 2:");
        dumpEntries(finder2.mEntries);

        System.out.println("Find result");
        dumpEntry(finder2.findPhonebookEntryByContactIdFirst(Long.parseLong(args[0]),""));


    }
            */
}
