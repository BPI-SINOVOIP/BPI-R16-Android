/************************************************************************************
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
 ************************************************************************************/

#define LOG_TAG "BluetoothRccServiceJni"

#define LOG_NDEBUG 0

#define CHECK_CALLBACK_ENV                                                      \
   if (!checkCallbackThread()) {                                                \
       ALOGE("Callback: '%s' is not called on the correct thread", __FUNCTION__);\
       return;                                                                  \
   }

#include "com_android_bluetooth.h"
#include "hardware/bt_rcc.h"
#include "utils/Log.h"
#include "android_runtime/AndroidRuntime.h"

#include <string.h>
#include <ctype.h>
#include <stdio.h>

#include <cutils/log.h>
#define info(fmt, ...)  ALOGI ("%s(L%d): " fmt,__FUNCTION__, __LINE__,  ## __VA_ARGS__)
#define debug(fmt, ...) ALOGD ("%s(L%d): " fmt,__FUNCTION__, __LINE__,  ## __VA_ARGS__)
#define warn(fmt, ...)  ALOGW ("WARNING: %s(L%d): " fmt "##",__FUNCTION__, __LINE__, ## __VA_ARGS__)
#define error(fmt, ...) ALOGE ("ERROR: %s(L%d): " fmt "##",__FUNCTION__, __LINE__, ## __VA_ARGS__)
#define asrt(s) if(!(s)) ALOGE ("%s(L%d): ASSERT %s failed! ##",__FUNCTION__, __LINE__, #s)

#define CMD_FAILED  (jbyte)-1

namespace android {

static jmethodID method_onConnectionStateChanged;
static jmethodID method_onListPlayerAttributeResponse;
static jmethodID method_onListPlayerValuesResponse;
static jmethodID method_onGetPlayerValueResponse;
static jmethodID method_onGetElementAttributeResponse;
static jmethodID method_onGetPlayerAttributesTextResponse;
static jmethodID method_onGetPlayerValuesTextResponse;
static jmethodID method_onPlayStatusResponse;
static jmethodID method_onSetBrowsedPlayerResponse;
static jmethodID method_onChangePathResponse;
static jmethodID method_onGetFolderItemsResponseStart;
static jmethodID method_onPlayerItemResponse;
static jmethodID method_onFolderItemResponse;
static jmethodID method_onElementItemResponse;
static jmethodID method_onGetFolderItemsResponseEnd;
static jmethodID method_onGetItemAttributesResponse;
static jmethodID method_onSearchResponse;
static jmethodID method_onPlayItemResponse;
static jmethodID method_onAddToNowPlayingResponse;
static jmethodID method_onSetAbsoluteVolumeCommand;
static jmethodID method_onSetAbsoluteVolumeResponse;
static jmethodID method_onPlayStatusChangedNotification;
static jmethodID method_onTrackChangedNotification;
static jmethodID method_onTrackReachedEndNotification;
static jmethodID method_onTrackReachedStartNotification;
static jmethodID method_onPlayPositionChangedNotification;
static jmethodID method_onAppSettingsChangedNotification;
static jmethodID method_onAddressedPlayerChangedNotification;
static jmethodID method_onAvailablePlayersChangedNotification;
static jmethodID method_onNowPlayingChangedNotification;
static jmethodID method_onUIDsChangedNotification;
static jmethodID method_onCommandTimeout;

static const btrcc_interface_t *sBluetoothRccInterface = NULL;
static jobject mCallbacksObj = NULL;
static JNIEnv *sCallbackEnv = NULL;

static bool checkCallbackThread() {
    // Always fetch the latest callbackEnv from AdapterService.
    // Caching this could cause this sCallbackEnv to go out-of-sync
    // with the AdapterService's ENV if an ASSOCIATE/DISASSOCIATE event
    // is received
    //if (sCallbackEnv == NULL) {
    sCallbackEnv = getCallbackEnv();
    //}
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    if (sCallbackEnv != env || sCallbackEnv == NULL) return false;
    return true;
}

static void connection_state_callback(
    bt_bdaddr_t* bd_addr,
    bt_status_t status,
    btrcc_connection_state_t connection_state)
{
    jbyteArray addr;
    jboolean success = (status == BT_STATUS_SUCCESS) ? true : false;

    info("Enter...");

    CHECK_CALLBACK_ENV

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (addr) {
        sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
        sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onConnectionStateChanged,
                                     addr, success, (jint)connection_state);

        sCallbackEnv->DeleteLocalRef(addr);
    }
    else {
        error("Fail to new jbyteArray bd addr for connection state");
    }

    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

void list_player_attr_rsp_callback(
    bt_bdaddr_t* bd_addr,
    btrcc_label_t label,
    btrcc_status_t status,
    uint8_t num_attr,
    btrcc_player_attr_t *p_attrs)
{
    jbyteArray addr     = NULL;
    jbyteArray attr_ids = NULL;

    info("Enter...");

    CHECK_CALLBACK_ENV

    /* Create byte array for the returned address */
    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));

    if (addr != NULL) {

        /* Make sure the response was successful.  */
        if (BTRCC_SUCCEEDED(status)) {

            /* if successful, create an array from the attr_ids for java */
            attr_ids = sCallbackEnv->NewByteArray(num_attr);
            if (attr_ids != NULL) {
                sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);

                sCallbackEnv->SetByteArrayRegion(attr_ids, 0, num_attr, (jbyte *)p_attrs);
                sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onListPlayerAttributeResponse,
                                             addr, (jbyte)label, (jint)status, attr_ids);

                sCallbackEnv->DeleteLocalRef(attr_ids);
            }
            else {
                error("Fail to new jbyteArray for attrib ids");
            }
        }
        else {
            error("Bad Status: %d", status);

            sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onListPlayerAttributeResponse,
                                         addr, (jbyte)label, (jint)status, attr_ids);
        }

        sCallbackEnv->DeleteLocalRef(addr);
    }
    else {
        error("Fail to new jbyteArray bd addr for attrib ids");
    }

    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);

}

void list_player_values_rsp_callback(
    bt_bdaddr_t* bd_addr,
    btrcc_label_t label,
    btrcc_status_t status,
    uint8_t num_val,
    btrcc_player_value_t *p_vals)
{
    jbyteArray addr = NULL;
    jbyteArray vals = NULL;

    info("Enter...");

    CHECK_CALLBACK_ENV

    /* Create byte array for the returned address */
    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));

    if (addr != NULL) {

        /* Make sure the response was successful.  */
        if (BTRCC_SUCCEEDED(status)) {

            vals = sCallbackEnv->NewByteArray(num_val);
            if (vals != NULL) {
                sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);

                sCallbackEnv->SetByteArrayRegion(vals, 0, num_val,(jbyte *)p_vals);

                sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onListPlayerValuesResponse,
                                             addr, (jbyte)label, (jint)status, vals);

                sCallbackEnv->DeleteLocalRef(vals);
            }
            else {
                error("Fail to new jbyteArray for list player values");
            }
        }
        else {
            error("Bad Status: %d", status);

            sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onListPlayerValuesResponse,
                                         addr, (jbyte)label, (jint)status, vals);
        }

        sCallbackEnv->DeleteLocalRef(addr);
    }
    else {
        error("Fail to new jbyteArray bd addr for list player values");
    }

    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

void get_player_value_rsp_callback(
    bt_bdaddr_t* bd_addr,
    btrcc_label_t label,
    btrcc_status_t status,
    btrcc_player_settings_t *p_attrs)
{
    jbyteArray addr = NULL;
    jbyteArray attr_ids = NULL;
    jbyteArray vals = NULL;

    info("Enter...");

    CHECK_CALLBACK_ENV

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (addr != NULL) {

        /* Make sure the response was successful.  */
        if (BTRCC_SUCCEEDED(status)) {

            attr_ids = sCallbackEnv->NewByteArray(p_attrs->num_attr);
            if (attr_ids != NULL) {

                vals = sCallbackEnv->NewByteArray(p_attrs->num_attr);
                if (vals != NULL) {
                    sCallbackEnv->SetByteArrayRegion(addr, 0, p_attrs->num_attr,
                                                     (jbyte*) bd_addr);

                    sCallbackEnv->SetByteArrayRegion(attr_ids, 0, p_attrs->num_attr,
                                                     (jbyte *)p_attrs->attr);

                    sCallbackEnv->SetByteArrayRegion(vals, 0, p_attrs->num_attr,
                                                     (jbyte *)p_attrs->value);

                    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onGetPlayerValueResponse,
                                                 addr, (jbyte)label, (jint)status,
                                                 attr_ids,
                                                 vals);

                    sCallbackEnv->DeleteLocalRef(vals);
                }
                else {
                    error("Fail to new jbyteArray for player values");
                }
                sCallbackEnv->DeleteLocalRef(attr_ids);
            }
            else {
                error("Fail to new jbyteArray for player values attr ids");
            }
        }
        else {
            error("Bad Status: %d", status);

            sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onGetPlayerValueResponse,
                                         addr, (jbyte)label, (jint)status,
                                         attr_ids,
                                         vals);
        }

        sCallbackEnv->DeleteLocalRef(addr);
    }
    else {
        error("Fail to new jbyteArray bd addr for player values");
    }

    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

void get_player_text_rsp_callback(
    bool is_attribute,
    btrcc_label_t label,
    btrcc_status_t status,
    bt_bdaddr_t* bd_addr,
    uint8_t num_items,
    btrcc_player_setting_text_t *p_items)
{
    bool succeeded = false;

    /* For each item in the list, create objects to store them */
    jbyteArray attr_ids = NULL;
    jintArray char_sets = NULL;
    jobjectArray val_array = NULL;
    jclass mclass = NULL;

    info("Enter...");

    CHECK_CALLBACK_ENV

    jmethodID method_id = is_attribute ? method_onGetPlayerAttributesTextResponse :
                                         method_onGetPlayerValuesTextResponse;

    jbyteArray addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));

    if (addr != NULL) {
        sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);

        /* Make sure the request succeeded */
        if (BTRCC_FAILED(status)) {
            error("Bad Status: %d", status);

            sCallbackEnv->CallVoidMethod(mCallbacksObj, method_id,
                                         addr, (jbyte)label, (jint)status, attr_ids,
                                         char_sets, val_array);

            goto Exit;
        }

        attr_ids = sCallbackEnv->NewByteArray((jsize)num_items);
    }

    if (attr_ids != NULL) {
        char_sets = sCallbackEnv->NewIntArray((jsize)num_items);
    }

    /* We are going to have an array of byte arrays so use addr to get the class */
    mclass = sCallbackEnv->GetObjectClass(addr);
    if (char_sets != NULL) {
        val_array = sCallbackEnv->NewObjectArray((jsize)num_items, mclass, NULL);
    }

    if (val_array != NULL) {
        jbyte* pId = sCallbackEnv->GetByteArrayElements(attr_ids, NULL);
        jint* pCharset = sCallbackEnv->GetIntArrayElements(char_sets, NULL);
        for (int i=0; i<num_items; i++)
        {
            jbyteArray attr_val = sCallbackEnv->NewByteArray((jsize)p_items[i].string_len);
            if (attr_val == NULL) goto Exit;

            pId[i] = (jbyte)p_items[i].id;
            pCharset[i] = (jint)p_items[i].character_set;

            sCallbackEnv->SetByteArrayRegion(attr_val, 0,
	                                             (jsize)p_items[i].string_len,
	                                             (jbyte *)p_items[i].text);

            sCallbackEnv->SetObjectArrayElement(val_array, i, attr_val);

            sCallbackEnv->DeleteLocalRef(attr_val);
        }
        sCallbackEnv->ReleaseByteArrayElements(attr_ids, pId, 0);
        sCallbackEnv->ReleaseIntArrayElements(char_sets, pCharset, 0);

        succeeded = true;
    }

    if (succeeded) {
        sCallbackEnv->CallVoidMethod(mCallbacksObj, method_id,
                                     addr, (jbyte)label, (jint)status, attr_ids,
                                     char_sets, val_array);
    }

Exit:
    if (addr != NULL)      sCallbackEnv->DeleteLocalRef(addr);
    if (attr_ids != NULL)  sCallbackEnv->DeleteLocalRef(attr_ids);
    if (char_sets != NULL) sCallbackEnv->DeleteLocalRef(char_sets);
    if (val_array != NULL) sCallbackEnv->DeleteLocalRef(val_array);
    if (mclass != NULL)    sCallbackEnv->DeleteLocalRef(mclass);

    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

void get_player_attrs_text_rsp_callback(
    bt_bdaddr_t* bd_addr,
    btrcc_label_t label,
    btrcc_status_t status,
    uint8_t num_attr,
    btrcc_player_setting_text_t *p_attrs)
{
    info("Enter...");

    get_player_text_rsp_callback(true, label, status, bd_addr, num_attr, p_attrs);
}

void get_player_values_text_rsp_callback(
    bt_bdaddr_t* bd_addr,
    btrcc_label_t label,
    btrcc_status_t status,
    uint8_t num_val,
    btrcc_player_setting_text_t *p_values)
{
    get_player_text_rsp_callback(false, label, status, bd_addr, num_val, p_values);
}

void get_element_attr_rsp_callback(
    bt_bdaddr_t* bd_addr,
    btrcc_label_t label,
    btrcc_status_t status,
    uint8_t num_attr,
    btrcc_element_attr_t *p_attrs)
{
    jbyteArray addr = NULL;
    jintArray attr_ids = NULL;
    jintArray char_sets = NULL;
    jobjectArray val_array = NULL;
    jclass mclass = NULL;

    bool succeeded = false;

    info("Enter...");

    CHECK_CALLBACK_ENV

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (addr != NULL) {
        sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);

        /* Make sure the request succeeded */
        if (BTRCC_FAILED(status)) {
            error("Bad Status: %d", status);

            sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onGetElementAttributeResponse,
                                         addr, (jbyte)label, (jint)status, attr_ids,
                                         char_sets, val_array);
            goto Exit;
        }

        attr_ids = sCallbackEnv->NewIntArray((jsize)num_attr);
    }

    if (attr_ids != NULL) {
        char_sets = sCallbackEnv->NewIntArray((jsize)num_attr);
    }

    /* We are going to have an array of byte arrays so use addr to get the class */
    mclass = sCallbackEnv->GetObjectClass(addr);
    if (char_sets != NULL) {
        val_array = sCallbackEnv->NewObjectArray((jsize)num_attr, mclass, NULL);
    }

    if (val_array != NULL) {
        jint* pCharset = sCallbackEnv->GetIntArrayElements(char_sets, 0);
        for (int i=0; i<num_attr; i++)
        {
            jbyteArray attr_val = sCallbackEnv->NewByteArray((jsize)p_attrs[i].attr_str.length);
            if (attr_val == NULL) goto Exit;

            sCallbackEnv->SetIntArrayRegion(attr_ids, i, 1, (jint *)&p_attrs[i].id);
            pCharset[i] = (jint)p_attrs[i].attr_str.character_set;

            sCallbackEnv->SetByteArrayRegion(attr_val, 0,
                                             (jsize)p_attrs[i].attr_str.length,
                                             (jbyte *)p_attrs[i].attr_str.string);
            sCallbackEnv->SetObjectArrayElement(val_array, i, attr_val);

            sCallbackEnv->DeleteLocalRef(attr_val);
        }

        sCallbackEnv->ReleaseIntArrayElements(char_sets, pCharset, 0);

        succeeded = true;
    }

    if (succeeded) {
        sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onGetElementAttributeResponse,
                                     addr, (jbyte)label, (jint)status, attr_ids,
                                     char_sets, val_array);
    }

Exit:
    if (addr != NULL)      sCallbackEnv->DeleteLocalRef(addr);
    if (attr_ids != NULL)  sCallbackEnv->DeleteLocalRef(attr_ids);
    if (char_sets != NULL) sCallbackEnv->DeleteLocalRef(char_sets);
    if (val_array != NULL) sCallbackEnv->DeleteLocalRef(val_array);
    if (mclass != NULL)    sCallbackEnv->DeleteLocalRef(mclass);

    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

void get_play_status_rsp_callback(
    bt_bdaddr_t* bd_addr,
    btrcc_label_t label,
    btrcc_status_t status,
    uint32_t song_len,
    uint32_t song_pos,
    btrcc_play_status_t play_status)
{
    jbyteArray addr;

    info("Enter...");

    CHECK_CALLBACK_ENV

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (addr) {
        sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
        sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onPlayStatusResponse,
                                     addr, (jbyte)label, (jint)status, (jint)song_len,
                                     (jint)song_pos, (jint)play_status);

        sCallbackEnv->DeleteLocalRef(addr);
    }
    else {
        error("Fail to new jbyteArray bd addr for play_status");
    }

    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

#define MAX_BROWSED_PATH 260

void set_browsed_player_rsp_callback(
    bt_bdaddr_t* bd_addr,
    btrcc_label_t label,
    btrcc_status_t status,
    uint32_t num_item,
    uint16_t character_set,
    uint8_t folder_depth,
    btrcc_string_t* p_folder_name)
{
    jbyteArray addr;
    jobjectArray folder_array = NULL;
    jclass mclass = NULL;

    info("Enter...");

    CHECK_CALLBACK_ENV

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (addr) {
        sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);

        /* Make sure the request succeeded */
        if (BTRCC_FAILED(status)) {
            error("Bad Status: %d", status);

            sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onSetBrowsedPlayerResponse,
                                         addr, (jbyte)label, (jint)status, 0, 0, NULL);
            goto Exit;
        }

        mclass = sCallbackEnv->GetObjectClass(addr);
        folder_array = sCallbackEnv->NewObjectArray((jsize)folder_depth, mclass, NULL);
    }

    if (folder_array){
        for (int i=0; i<(int)folder_depth; i++)
        {
            jbyteArray attr_val = sCallbackEnv->NewByteArray((jsize)p_folder_name[i].length);
            if (attr_val == NULL) goto Exit;

            sCallbackEnv->SetByteArrayRegion(attr_val, 0,
                                             (jsize)p_folder_name[i].length,
                                             (jbyte *)p_folder_name[i].string);
            sCallbackEnv->SetObjectArrayElement(folder_array, i, attr_val);

            sCallbackEnv->DeleteLocalRef(attr_val);
        }

        sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onSetBrowsedPlayerResponse,
                                     addr, (jbyte)label, (jint)status, (jint)num_item,
                                     (jint)character_set, folder_array);
    }

Exit:
    if (addr)       sCallbackEnv->DeleteLocalRef(addr);
    if (folder_array) sCallbackEnv->DeleteLocalRef(folder_array);
    if (mclass)     sCallbackEnv->DeleteLocalRef(mclass);

    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

void change_path_rsp_callback(
    bt_bdaddr_t* bd_addr,
    btrcc_label_t label,
    btrcc_status_t status,
    uint32_t num_item)
{
    jbyteArray addr;

    info("Enter...");

    CHECK_CALLBACK_ENV

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (addr) {
        sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
        sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onChangePathResponse,
                                     addr, (jbyte)label, (jint)status, (jint)num_item);

        sCallbackEnv->DeleteLocalRef(addr);
    }

    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

void player_item_rsp_callback(
    jint idx,
    btrcc_browse_item_t* p_item)
{
    jbyteArray disp_name = NULL;
    jbyteArray features = NULL;
    btrcc_media_player_browse_item_t* p_player = &p_item->item.player;

    disp_name = sCallbackEnv->NewByteArray((jsize)p_player->name.length);

    if (disp_name != NULL) {
        sCallbackEnv->SetByteArrayRegion(disp_name, 0, (jsize)p_player->name.length,
                                         (jbyte*)p_player->name.string);

        features = sCallbackEnv->NewByteArray(BTRCC_FEATURE_MASK_SIZE);
    }

    if (features != NULL) {
        sCallbackEnv->SetByteArrayRegion(features, 0, BTRCC_FEATURE_MASK_SIZE,
                                         (jbyte*)p_player->feature_mask);

        sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onPlayerItemResponse,
                                     idx, (jint)p_player->name.character_set, disp_name,
                                     (jint)p_player->player_id, (jbyte)p_player->player_type,
                                     (jint)p_player->player_subtype, (jint)p_player->play_status,
                                     features);
    }

    if (disp_name != NULL) sCallbackEnv->DeleteLocalRef(disp_name);
    if (features != NULL)  sCallbackEnv->DeleteLocalRef(features);
}

void folder_item_rsp_callback(
    jint idx,
    btrcc_browse_item_t* p_item)
{
    jbyteArray disp_name = NULL;
    jbyteArray uid = NULL;
    btrcc_folder_browse_item_t* p_folder = &p_item->item.folder;

    disp_name = sCallbackEnv->NewByteArray((jsize)p_folder->name.length);

    if (disp_name != NULL) {
        sCallbackEnv->SetByteArrayRegion(disp_name, 0, (jsize)p_folder->name.length,
                                         (jbyte*)p_folder->name.string);

        uid = sCallbackEnv->NewByteArray(BTRCC_UID_SIZE);
    }

    if (uid != NULL) {
        sCallbackEnv->SetByteArrayRegion(uid, 0, BTRCC_UID_SIZE, (jbyte*)p_folder->uid);

        sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onFolderItemResponse,
                                     idx, (jint)p_folder->name.character_set, disp_name,
                                     uid, (jbyte)p_folder->type, (jbyte)p_folder->is_playable);
    }

    if (disp_name != NULL) sCallbackEnv->DeleteLocalRef(disp_name);
    if (uid != NULL)       sCallbackEnv->DeleteLocalRef(uid);
}

void element_item_rsp_callback(
    jint idx,
    btrcc_browse_item_t* p_item)
{
    jbyteArray disp_name = NULL;
    jbyteArray uid = NULL;
    jintArray attrs = NULL;
    jintArray charsets = NULL;
    jobjectArray val_array = NULL;
    jclass mclass = NULL;
    btrcc_media_element_browse_item_t* p_elem = &p_item->item.element;

    disp_name = sCallbackEnv->NewByteArray((jsize)p_elem->name.length);

    if (disp_name != NULL) {
        sCallbackEnv->SetByteArrayRegion(disp_name, 0, (jsize)p_elem->name.length,
                                         (jbyte*)p_elem->name.string);

        uid = sCallbackEnv->NewByteArray(BTRCC_UID_SIZE);
    }

    if (uid != NULL) {
        sCallbackEnv->SetByteArrayRegion(uid, 0, BTRCC_UID_SIZE, (jbyte*)p_elem->uid);

        attrs = sCallbackEnv->NewIntArray((jsize)p_elem->attr_count);
    }

    if (attrs != NULL) {
        charsets = sCallbackEnv->NewIntArray((jsize)p_elem->attr_count);
    }

    if (charsets != NULL) {
        /* We are going to have an array of byte arrays so use uid to get the class */
        mclass = sCallbackEnv->GetObjectClass(uid);
        val_array = sCallbackEnv->NewObjectArray((jsize)p_elem->attr_count, mclass, NULL);
    }


    if (val_array != NULL) {
        jint* pCharset = sCallbackEnv->GetIntArrayElements(charsets, 0);
        for (int i = 0; i < p_elem->attr_count; i++)
        {
            btrcc_element_attr_t* p_attr = &p_elem->attr_list[i];
            btrcc_full_string_t* p_str = &p_attr->attr_str;
            jbyteArray attr_val = sCallbackEnv->NewByteArray((jsize)p_str->length);
            if (attr_val != NULL) {

                sCallbackEnv->SetIntArrayRegion(attrs, i, 1, (jint*)&p_attr->id);
                pCharset[i] = (jint)p_str->character_set;

                sCallbackEnv->SetByteArrayRegion(attr_val, 0, (jsize)p_str->length,
                                                 (jbyte*)p_str->string);
                sCallbackEnv->SetObjectArrayElement(val_array, i, attr_val);

                sCallbackEnv->DeleteLocalRef(attr_val);
            }
        }
        sCallbackEnv->ReleaseIntArrayElements(charsets, pCharset, 0);

        sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onElementItemResponse,
                                     idx, (jint)p_elem->name.character_set, disp_name,
                                     uid, (jbyte)p_elem->element_type, attrs,
                                     charsets, val_array);
    }

    if (disp_name != NULL) sCallbackEnv->DeleteLocalRef(disp_name);
    if (uid != NULL)       sCallbackEnv->DeleteLocalRef(uid);
    if (attrs != NULL)     sCallbackEnv->DeleteLocalRef(attrs);
    if (charsets != NULL)  sCallbackEnv->DeleteLocalRef(charsets);
    if (val_array != NULL) sCallbackEnv->DeleteLocalRef(val_array);
    if (mclass != NULL)    sCallbackEnv->DeleteLocalRef(mclass);
}

void get_folder_items_rsp_callback(
    bt_bdaddr_t* bd_addr,
    btrcc_label_t label,
    btrcc_status_t status,
    uint16_t num_item,
    btrcc_browse_item_t* p_items)
{
    jbyteArray addr;

    info("Enter...");

    CHECK_CALLBACK_ENV

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (addr) {
        sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);

        if (BTRCC_SUCCEEDED(status)) {
            sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onGetFolderItemsResponseStart,
                                         (jint)num_item);

            for (jint i = 0; i < num_item; i++) {
                if (p_items[i].item_type == ITEM_TYPE_MEDIA_PLAYER)
                    player_item_rsp_callback(i, &p_items[i]);
                else if (p_items[i].item_type == ITEM_TYPE_FOLDER)
                    folder_item_rsp_callback(i, &p_items[i]);
                else if (p_items[i].item_type == ITEM_TYPE_MEDIA_ELEMENT)
                    element_item_rsp_callback(i, &p_items[i]);
            }
        }

        sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onGetFolderItemsResponseEnd,
                                     addr, (jbyte)label, (jint)status);
        sCallbackEnv->DeleteLocalRef(addr);
    }

    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

void get_item_attrs_rsp_callback(
    bt_bdaddr_t* bd_addr,
    btrcc_label_t label,
    btrcc_status_t status,
    uint8_t num_attr,
    btrcc_element_attr_t *p_attrs)
{
    jbyteArray addr = NULL;
    jintArray attr_ids = NULL;
    jintArray char_sets = NULL;
    jobjectArray val_array = NULL;
    jclass mclass = NULL;

    bool succeeded = false;

    info("Enter...");

    CHECK_CALLBACK_ENV

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (addr != NULL) {
        sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);

        /* Make sure the request succeeded */
        if (BTRCC_FAILED(status)) {
            error("Bad Status: %d", status);

            sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onGetItemAttributesResponse,
                                         addr, (jbyte)label, (jint)status, attr_ids,
                                         char_sets, val_array);
            goto Exit;
        }

        attr_ids = sCallbackEnv->NewIntArray((jsize)num_attr);
    }

    if (attr_ids != NULL) {
        char_sets = sCallbackEnv->NewIntArray((jsize)num_attr);
    }

    /* We are going to have an array of byte arrays so use addr to get the class */
    mclass = sCallbackEnv->GetObjectClass(addr);
    if (char_sets != NULL) {
        val_array = sCallbackEnv->NewObjectArray((jsize)num_attr, mclass, NULL);
    }

    if (val_array != NULL) {
        jint* pCharset = sCallbackEnv->GetIntArrayElements(char_sets, 0);
        for (int i=0; i<num_attr; i++)
        {
            jbyteArray attr_val = sCallbackEnv->NewByteArray((jsize)p_attrs[i].attr_str.length);
            if (attr_val == NULL) goto Exit;

            sCallbackEnv->SetIntArrayRegion(attr_ids, i, 1, (jint *)&p_attrs[i].id);
            pCharset[i] = (jint)p_attrs[i].attr_str.character_set;

            sCallbackEnv->SetByteArrayRegion(attr_val, 0,
                                             (jsize)p_attrs[i].attr_str.length,
                                             (jbyte *)p_attrs[i].attr_str.string);
            sCallbackEnv->SetObjectArrayElement(val_array, i, attr_val);

            sCallbackEnv->DeleteLocalRef(attr_val);
        }

        sCallbackEnv->ReleaseIntArrayElements(char_sets, pCharset, 0);

        succeeded = true;
    }

    if (succeeded) {
        sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onGetItemAttributesResponse,
                                     addr, (jbyte)label, (jint)status, attr_ids,
                                     char_sets, val_array);
    }

Exit:
    if (addr != NULL)      sCallbackEnv->DeleteLocalRef(addr);
    if (attr_ids != NULL)  sCallbackEnv->DeleteLocalRef(attr_ids);
    if (char_sets != NULL) sCallbackEnv->DeleteLocalRef(char_sets);
    if (val_array != NULL) sCallbackEnv->DeleteLocalRef(val_array);
    if (mclass != NULL)    sCallbackEnv->DeleteLocalRef(mclass);

    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

void search_rsp_callback(
    bt_bdaddr_t* bd_addr,
    btrcc_label_t label,
    btrcc_status_t status,
    uint32_t num_item)
{
    jbyteArray addr;

    info("Enter...");

    CHECK_CALLBACK_ENV

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (addr) {
        sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
        sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onSearchResponse,
                                     addr, (jbyte)label, (jint)status, (jint)num_item);

        sCallbackEnv->DeleteLocalRef(addr);
    }

    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

void play_item_rsp_callback(
    bt_bdaddr_t* bd_addr,
    btrcc_label_t label,
    btrcc_status_t status)
{
    jbyteArray addr;

    info("Enter...");

    CHECK_CALLBACK_ENV

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (addr) {
        sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
        sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onPlayItemResponse,
                                     addr, (jbyte)label, (jint)status);

        sCallbackEnv->DeleteLocalRef(addr);
    }

    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

void add_to_now_playing_rsp_callback(
    bt_bdaddr_t* bd_addr,
    btrcc_label_t label,
    btrcc_status_t status)
{
    jbyteArray addr;

    info("Enter...");

    CHECK_CALLBACK_ENV

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (addr) {
        sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
        sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAddToNowPlayingResponse,
                                     addr, (jbyte)label, (jint)status);

        sCallbackEnv->DeleteLocalRef(addr);
    }

    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

void set_absolute_volume_cmd_callback(
    bt_bdaddr_t* bd_addr,
    btrcc_label_t label,
    uint8_t volume)
{
    jbyteArray addr;

    info("Enter...");

    CHECK_CALLBACK_ENV

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (addr) {
        sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
        sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onSetAbsoluteVolumeCommand,
                                     addr, (jbyte)label, (jbyte)volume);

        sCallbackEnv->DeleteLocalRef(addr);
    }

    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

/* ==== Notifications ==== */
void notification_play_status_changed(
    jbyteArray addr,
    btrcc_play_status_t play_status)
{
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onPlayStatusChangedNotification,
                                 addr,(jint)play_status);
}

void notification_track_changed(
    jbyteArray addr,
    btrcc_uid_t track_id)
{
    jbyteArray batrackid = sCallbackEnv->NewByteArray(sizeof(btrcc_uid_t));
    if (batrackid != NULL) {
        sCallbackEnv->SetByteArrayRegion(batrackid, 0, sizeof(btrcc_uid_t), (jbyte*) track_id);
        sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onTrackChangedNotification,
                                     addr, batrackid);
        sCallbackEnv->DeleteLocalRef(batrackid);
    }

}

void notification_track_reached_end(jbyteArray addr)
{
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onTrackReachedEndNotification, addr);
}

void notification_track_reached_start(jbyteArray addr)
{
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onTrackReachedStartNotification, addr);
}

void notification_play_position_changed(jbyteArray addr, uint32_t song_pos)
{
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onPlayPositionChangedNotification,
                                 addr, (jint)song_pos);
}

void notification_app_settings_changed(
    jbyteArray addr,
    btrcc_player_settings_t *p_attrs)
{
    jbyteArray attr_ids = NULL;
    jbyteArray vals = NULL;

    info("Enter...");

    CHECK_CALLBACK_ENV

    attr_ids = sCallbackEnv->NewByteArray(p_attrs->num_attr);
    if (attr_ids != NULL) {

        vals = sCallbackEnv->NewByteArray(p_attrs->num_attr);
        if (vals != NULL) {
            sCallbackEnv->SetByteArrayRegion(attr_ids, 0, (jsize)p_attrs->num_attr, (jbyte *)p_attrs->attr);

            sCallbackEnv->SetByteArrayRegion(vals, 0, (jsize)p_attrs->num_attr, (jbyte *)p_attrs->value);

            sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAppSettingsChangedNotification,
                                         addr, attr_ids, vals);

            sCallbackEnv->DeleteLocalRef(vals);
        }
        else {
            error("Fail to new jbyteArray for player values");
        }
        sCallbackEnv->DeleteLocalRef(attr_ids);
    }
    else {
        error("Fail to new jbyteArray for player values attr ids");
    }
}

void notification_addressed_player_changed(jbyteArray addr, uint16_t player_id)
{
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAddressedPlayerChangedNotification,
                                 addr, (jint)player_id);
}

void notification_available_players_changed(jbyteArray addr)
{
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAvailablePlayersChangedNotification, addr);
}

void notification_now_playing_changed(jbyteArray addr)
{
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onNowPlayingChangedNotification, addr);
}

void notification_uids_changed(jbyteArray addr)
{
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onUIDsChangedNotification, addr);
}

void notification_callback(
    bt_bdaddr_t* bd_addr,
    btrcc_status_t status,
    btrcc_notif_evt_id_t event_id,
    btrcc_notification_t *p_notif)
{
    info("Enter...");

    CHECK_CALLBACK_ENV

    if (BTRCC_FAILED(status)) {
        error("Failure Status: %d",status);
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }

    jbyteArray addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (addr) {
        sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);

        switch(event_id)
        {
            case NOTIF_EVT_PLAY_STATUS_CHANGED:
                 notification_play_status_changed(addr, p_notif->play_status);
                 break;

            case NOTIF_EVT_TRACK_CHANGE:
                notification_track_changed(addr, p_notif->track_id);
                break;

            case NOTIF_EVT_TRACK_REACHED_END:
                notification_track_reached_end(addr);
                break;

            case NOTIF_EVT_TRACK_REACHED_START:
                notification_track_reached_start(addr);
                break;

            case NOTIF_EVT_PLAY_POS_CHANGED:
                notification_play_position_changed(addr, p_notif->song_pos);
                break;

            case NOTIF_EVT_APP_SETTINGS_CHANGED:
                notification_app_settings_changed(addr, &p_notif->player_setting);
                break;

            case NOTIF_EVT_NOW_PLAYING_CHANGED:
                notification_now_playing_changed(addr);
                break;

            case NOTIF_EVT_AVAIL_PLAYERS_CHANGED:
                notification_available_players_changed(addr);
                break;

            case NOTIF_EVT_ADDRESSED_PLAYER_CHANGED:
                notification_addressed_player_changed(addr, p_notif->player_id);
                break;

            case NOTIF_EVT_UIDS_CHANGED:
                notification_uids_changed(addr);
                break;

            default:
                error("unsupported notification: %d", event_id);
                break;
        }

        sCallbackEnv->DeleteLocalRef(addr);
    }
    else {
        error("Fail to new jbyteArray bd addr for notification");
    }

    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

void command_timeout_callback(
    bt_bdaddr_t* bd_addr,
    btrcc_label_t label)
{
    jbyteArray addr;

    info("Enter...");

    CHECK_CALLBACK_ENV

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (addr) {
        sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
        sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onCommandTimeout,
                                     addr, (jbyte)label);

        sCallbackEnv->DeleteLocalRef(addr);
    }
    else {
        error("Fail to create new jbyteArray bd addr for command timeout");
    }

    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);

}


static btrcc_callbacks_t sBluetoothRccCallbacks = {
    sizeof(sBluetoothRccCallbacks),
    connection_state_callback,
    list_player_attr_rsp_callback,
    list_player_values_rsp_callback,
    get_player_value_rsp_callback,
    get_player_attrs_text_rsp_callback,
    get_player_values_text_rsp_callback,
    get_element_attr_rsp_callback,
    get_play_status_rsp_callback,
    set_browsed_player_rsp_callback,
    change_path_rsp_callback,
    get_folder_items_rsp_callback,
    get_item_attrs_rsp_callback,
    search_rsp_callback,
    play_item_rsp_callback,
    add_to_now_playing_rsp_callback,
    set_absolute_volume_cmd_callback,
    notification_callback,
    command_timeout_callback
};

static void classInitNative(JNIEnv* env, jclass clazz)
{
    method_onConnectionStateChanged =
        env->GetMethodID(clazz, "onConnectionStateChanged", "([BZI)V");

    method_onListPlayerAttributeResponse =
        env->GetMethodID(clazz, "onListPlayerAttributeResponse", "([BBI[B)V");

    method_onListPlayerValuesResponse =
        env->GetMethodID(clazz, "onListPlayerValuesResponse", "([BBI[B)V");

    method_onGetPlayerValueResponse =
        env->GetMethodID(clazz, "onGetPlayerValueResponse", "([BBI[B[B)V");

    method_onGetPlayerAttributesTextResponse =
        env->GetMethodID(clazz, "onGetPlayerAttributesTextResponse", "([BBI[B[I[Ljava/lang/Object;)V");

    method_onGetPlayerValuesTextResponse =
        env->GetMethodID(clazz, "onGetPlayerValuesTextResponse", "([BBI[B[I[Ljava/lang/Object;)V");

    method_onGetElementAttributeResponse =
        env->GetMethodID(clazz, "onGetElementAttributeResponse", "([BBI[I[I[Ljava/lang/Object;)V");

    method_onPlayStatusResponse =
        env->GetMethodID(clazz, "onPlayStatusResponse", "([BBIIII)V");

    method_onSetBrowsedPlayerResponse =
        env->GetMethodID(clazz, "onSetBrowsedPlayerResponse", "([BBIII[Ljava/lang/Object;)V");

    method_onChangePathResponse =
        env->GetMethodID(clazz, "onChangePathResponse", "([BBII)V");

    method_onGetFolderItemsResponseStart =
        env->GetMethodID(clazz, "onGetFolderItemsResponseStart", "(I)V");

    method_onPlayerItemResponse =
        env->GetMethodID(clazz, "onPlayerItemResponse", "(II[BIBII[B)V");

    method_onFolderItemResponse =
        env->GetMethodID(clazz, "onFolderItemResponse", "(II[B[BBB)V");

    method_onElementItemResponse =
        env->GetMethodID(clazz, "onElementItemResponse", "(II[B[BB[I[I[Ljava/lang/Object;)V");

    method_onGetFolderItemsResponseEnd =
        env->GetMethodID(clazz, "onGetFolderItemsResponseEnd", "([BBI)V");

    method_onGetItemAttributesResponse =
        env->GetMethodID(clazz, "onGetItemAttributesResponse", "([BBI[I[I[Ljava/lang/Object;)V");

    method_onSearchResponse =
        env->GetMethodID(clazz, "onSearchResponse", "([BBII)V");

    method_onPlayItemResponse =
        env->GetMethodID(clazz, "onPlayItemResponse", "([BBI)V");

    method_onAddToNowPlayingResponse =
        env->GetMethodID(clazz, "onAddToNowPlayingResponse", "([BBI)V");

    method_onSetAbsoluteVolumeCommand =
        env->GetMethodID(clazz, "onSetAbsoluteVolumeCommand", "([BBB)V");

    method_onPlayStatusChangedNotification =
        env->GetMethodID(clazz, "onPlayStatusChangedNotification", "([BI)V");

    method_onTrackChangedNotification =
        env->GetMethodID(clazz, "onTrackChangedNotification", "([B[B)V");

    method_onTrackReachedEndNotification =
        env->GetMethodID(clazz, "onTrackReachedEndNotification", "([B)V");

    method_onTrackReachedStartNotification =
        env->GetMethodID(clazz, "onTrackReachedStartNotification", "([B)V");

    method_onPlayPositionChangedNotification =
        env->GetMethodID(clazz, "onPlayPositionChangedNotification", "([BI)V");

    method_onAppSettingsChangedNotification =
        env->GetMethodID(clazz, "onAppSettingsChangedNotification", "([B[B[B)V");

    method_onAddressedPlayerChangedNotification =
        env->GetMethodID(clazz, "onAddressedPlayerChangedNotification", "([BI)V");

    method_onAvailablePlayersChangedNotification =
        env->GetMethodID(clazz, "onAvailablePlayersChangedNotification", "([B)V");

    method_onNowPlayingChangedNotification =
        env->GetMethodID(clazz, "onNowPlayingChangedNotification", "([B)V");

    method_onUIDsChangedNotification =
        env->GetMethodID(clazz, "onUIDsChangedNotification", "([B)V");

    method_onCommandTimeout =
        env->GetMethodID(clazz, "onCommandTimeout", "([BB)V");

}

static void initializeNative(JNIEnv *env, jobject object)
{
    const bt_interface_t* btInf;
    bt_status_t status;

    if ( (btInf = getBluetoothInterface()) == NULL) {
        ALOGE("Bluetooth module is not loaded");
        return;
    }

    if (sBluetoothRccInterface !=NULL) {
        ALOGW("Cleaning up Bluetooth AVRCP CT Interface before initializing...");
        sBluetoothRccInterface->cleanup();
        sBluetoothRccInterface = NULL;
    }

    if (mCallbacksObj != NULL) {
        ALOGW("Cleaning up Bluetooth AVRCP CT callback object");
        env->DeleteGlobalRef(mCallbacksObj);
        mCallbacksObj = NULL;
    }

    if ( (sBluetoothRccInterface = (btrcc_interface_t *)
          btInf->get_profile_interface(BT_PROFILE_AV_RCC_ID)) == NULL) {
        ALOGE("Failed to get Bluetooth AVRCP CT Interface");
        return;
    }

    if ( (status = sBluetoothRccInterface->init(&sBluetoothRccCallbacks))
                                                            != BT_STATUS_SUCCESS) {
        ALOGE("Failed to initialize AVRCP CT Device, status: %d", status);
        sBluetoothRccInterface = NULL;
        return;
    }

    mCallbacksObj = env->NewGlobalRef(object);
}

static void cleanupNative(JNIEnv *env, jobject object)
{
    const bt_interface_t* btInf;
    bt_status_t status;

    if ( (btInf = getBluetoothInterface()) == NULL) {
        ALOGE("Bluetooth module is not loaded");
        return;
    }

    if (sBluetoothRccInterface !=NULL) {
        ALOGW("Cleaning up Bluetooth AVRCP CT Interface...");
        sBluetoothRccInterface->cleanup();
        sBluetoothRccInterface = NULL;
    }

    if (mCallbacksObj != NULL) {
        ALOGW("Cleaning up Bluetooth AVRCP CT callback object");
        env->DeleteGlobalRef(mCallbacksObj);
        mCallbacksObj = NULL;
    }
}

static jboolean connectNative(JNIEnv *env, jobject object, jbyteArray address)
{
    jbyte *addr;
    bt_status_t status;

    ALOGI("%s: sBluetoothRccInterface: %p", __FUNCTION__, sBluetoothRccInterface);
    if (!sBluetoothRccInterface) return JNI_FALSE;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return JNI_FALSE;
    }

    if ((status = sBluetoothRccInterface->connect((bt_bdaddr_t *)addr))
                                                                != BT_STATUS_SUCCESS) {
        ALOGE("Failed AVRCP CT connection, status: %d", status);
    }
    env->ReleaseByteArrayElements(address, addr, 0);
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean disconnectNative(JNIEnv *env, jobject object, jbyteArray address)
{
    jbyte *addr;
    bt_status_t status;

    if (!sBluetoothRccInterface) return JNI_FALSE;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return JNI_FALSE;
    }

    if ( (status = sBluetoothRccInterface->disconnect((bt_bdaddr_t *)addr))
                                                              != BT_STATUS_SUCCESS) {
        ALOGE("Failed AVRCP CT disconnection, status: %d", status);
    }
    env->ReleaseByteArrayElements(address, addr, 0);
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jint getTargetFeaturesNative(JNIEnv *env, jobject object, jbyteArray address)
{
    jbyte *addr;
    jint features;

    if (!sBluetoothRccInterface) return 0;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return 0;
    }

    features = sBluetoothRccInterface->get_target_features((bt_bdaddr_t *)addr);
    env->ReleaseByteArrayElements(address, addr, 0);
    return features;
}

static jboolean passThroughCommandNative(
    JNIEnv *env, jobject object,
    jbyteArray address,
    jint cmd, jint state,
    jbyte data_len, jbyteArray ba_data)
{
    jbyte *addr = NULL;
    jbyte *pdata = NULL;
    bt_status_t status;

    if (!sBluetoothRccInterface) return JNI_FALSE;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return JNI_FALSE;
    }

    if (data_len > 0) {
        pdata = env->GetByteArrayElements(ba_data, NULL);
        if (!pdata) {
            env->ReleaseByteArrayElements(address, addr, 0);
            jniThrowIOException(env, EINVAL);
            return JNI_FALSE;
        }
    }

    status = sBluetoothRccInterface->pass_through_cmd((bt_bdaddr_t *)addr,
                                                      (btrcc_pass_cmd_t)cmd,
                                                      (btrcc_pass_state_t)state,
                                                      (uint8_t)data_len,
                                                      (uint8_t *)pdata);

    if ( status != BT_STATUS_SUCCESS) {
        ALOGE("Failed AVRCP CT passThroughCmdNative, status: %d", status);
    }

    env->ReleaseByteArrayElements(address, addr, 0);
    if (pdata != NULL) env->ReleaseByteArrayElements(ba_data, pdata, 0);

    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jbyte listPlayerAttributesNative(JNIEnv *env, jobject object, jbyteArray address)
{
    jbyte *addr;
    bt_status_t status;
    jbyte label = CMD_FAILED;

    if (!sBluetoothRccInterface) return CMD_FAILED;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return CMD_FAILED;
    }

    status = sBluetoothRccInterface->list_player_attrs((bt_bdaddr_t *)addr, (btrcc_label_t*)&label);
    if ( status != BT_STATUS_SUCCESS) {
        ALOGE("Failed AVRCP CT list_player_attrs, status: %d", status);
    }

    env->ReleaseByteArrayElements(address, addr, 0);
    return (status == BT_STATUS_SUCCESS) ? label : CMD_FAILED;
}

static jbyte listPlayerValuesNative(JNIEnv *env, jobject object,
    jbyteArray address,
    jbyte attribute)
{
    jbyte *addr;
    bt_status_t status;
    jbyte label = CMD_FAILED;

    if (!sBluetoothRccInterface) return CMD_FAILED;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return CMD_FAILED;
    }

    status = sBluetoothRccInterface->list_player_values((bt_bdaddr_t *)addr,
                                                        (btrcc_player_attr_t)attribute,
                                                        (btrcc_label_t*)&label);
    if ( status != BT_STATUS_SUCCESS) {
        ALOGE("Failed AVRCP CT list_player_values, status: %d", status);
    }

    env->ReleaseByteArrayElements(address, addr, 0);
    return (status == BT_STATUS_SUCCESS) ? label : CMD_FAILED;
}

static jbyte getPlayerValuesNative(JNIEnv *env, jobject object,
    jbyteArray address,
    jbyte num_attributes,
    jbyteArray attributes)
{
    jbyte *addr;
    jbyte *attrs;
    bt_status_t status;
    jbyte label = CMD_FAILED;

    if (!sBluetoothRccInterface) return CMD_FAILED;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return CMD_FAILED;
    }

    attrs = env->GetByteArrayElements(attributes, NULL);
    if (!attrs) {
        env->ReleaseByteArrayElements(address, addr, 0);
        jniThrowIOException(env, EINVAL);
        return CMD_FAILED;
    }

    status = sBluetoothRccInterface->get_player_value((bt_bdaddr_t *)addr,
                                                       (uint8_t)num_attributes,
                                                       (btrcc_player_attr_t *)attrs,
                                                       (btrcc_label_t*)&label);
    if (status != BT_STATUS_SUCCESS) {
        error("Failed AVRCP CT get_player_values, status: %d", status);
    }

    env->ReleaseByteArrayElements(address, addr, 0);
    env->ReleaseByteArrayElements(attributes, attrs, 0);
    return (status == BT_STATUS_SUCCESS) ? label : CMD_FAILED;
}

static jboolean setPlayerValuesNative(JNIEnv *env, jobject object,
    jbyteArray address,
    jbyte num_attributes,
    jbyteArray attributes,
    jbyteArray values)
{
    jbyte *addr;
    jbyte *attrs;
    jbyte *vals;
    bt_status_t status;

    if (!sBluetoothRccInterface) return JNI_FALSE;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return JNI_FALSE;
    }

    attrs = env->GetByteArrayElements(attributes, NULL);
    if (!attrs) {
        env->ReleaseByteArrayElements(address, addr, 0);
        jniThrowIOException(env, EINVAL);
        return JNI_FALSE;
    }

    vals = env->GetByteArrayElements(values, NULL);
    if (!vals) {
        env->ReleaseByteArrayElements(address, addr, 0);
        env->ReleaseByteArrayElements(attributes, attrs, 0);
        jniThrowIOException(env, EINVAL);
        return JNI_FALSE;
    }

    btrcc_player_settings_t player_settings;
    player_settings.num_attr = (uint8_t)num_attributes;
    if (player_settings.num_attr > BTRCC_MAX_APP_SETTINGS)
        player_settings.num_attr = BTRCC_MAX_APP_SETTINGS;
    memcpy(player_settings.attr, attrs, player_settings.num_attr);
    memcpy(player_settings.value, vals, player_settings.num_attr);

    status = sBluetoothRccInterface->set_player_value((bt_bdaddr_t *)addr,
                                                       &player_settings);
    if (status != BT_STATUS_SUCCESS) {
        error("Failed AVRCP CT set_player_values, status: %d", status);
    }

    env->ReleaseByteArrayElements(address, addr, 0);
    env->ReleaseByteArrayElements(attributes, attrs, 0);
    env->ReleaseByteArrayElements(values, vals, 0);
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jbyte getPlayerAttributesTextNative(JNIEnv *env, jobject object,
    jbyteArray address,
    jbyte num_attributes,
    jbyteArray attributes)
{
    jbyte *addr;
    jbyte *attr;
    bt_status_t status;
    jbyte label = CMD_FAILED;

    if (!sBluetoothRccInterface) return CMD_FAILED;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return CMD_FAILED;
    }

    attr = env->GetByteArrayElements(attributes, NULL);
    if (!attr) {
        env->ReleaseByteArrayElements(address, addr, 0);
        jniThrowIOException(env, EINVAL);
        return CMD_FAILED;
    }

    status = sBluetoothRccInterface->get_player_attrs_text((bt_bdaddr_t *)addr,
                                                           (uint8_t)num_attributes,
                                                           (btrcc_player_attr_t*)attr,
                                                           (btrcc_label_t*)&label);
    if (status != BT_STATUS_SUCCESS) {
        error("Failed AVRCP CT get_player_attrs_text, status: %d", status);
    }

    env->ReleaseByteArrayElements(address, addr, 0);
    env->ReleaseByteArrayElements(attributes, attr, 0);
    return (status == BT_STATUS_SUCCESS) ? label : CMD_FAILED;
}

static jbyte getPlayerValuesTextNative(JNIEnv *env, jobject object,
    jbyteArray address,
    jbyte attribute,
    jbyte num_values,
    jbyteArray values)
{
    jbyte *addr;
    jbyte *vals;
    bt_status_t status;
    jbyte label = CMD_FAILED;

    if (!sBluetoothRccInterface) return CMD_FAILED;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return CMD_FAILED;
    }

    vals = env->GetByteArrayElements(values, NULL);
    if (!vals) {
        env->ReleaseByteArrayElements(address, addr, 0);
        jniThrowIOException(env, EINVAL);
        return CMD_FAILED;
    }

    status = sBluetoothRccInterface->get_player_values_text((bt_bdaddr_t *)addr,
                                                           (btrcc_player_attr_t)attribute,
                                                           (uint8_t)num_values,
                                                           (btrcc_player_value_t*)vals,
                                                           (btrcc_label_t*)&label);
    if (status != BT_STATUS_SUCCESS) {
        error("Failed AVRCP CT get_player_attrs_text, status: %d", status);
    }

    env->ReleaseByteArrayElements(address, addr, 0);
    env->ReleaseByteArrayElements(values, vals, 0);
    return (status == BT_STATUS_SUCCESS) ? label : CMD_FAILED;
}

static jbyte getElementAttributesNative(JNIEnv *env, jobject object,
    jbyteArray address,
    jbyteArray element_id,
    jbyte num_attributes,
    jintArray attributes)
{
    jbyte *addr;
    jint *attr;
    jbyte *e_id;
    bt_status_t status;
    jbyte label = CMD_FAILED;

    if (!sBluetoothRccInterface) return CMD_FAILED;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return CMD_FAILED;
    }

    e_id = env->GetByteArrayElements(element_id, NULL);
    if (!e_id) {
        env->ReleaseByteArrayElements(address, addr, 0);
        jniThrowIOException(env, EINVAL);
        return CMD_FAILED;
    }

    /* TODO: e_id should be all zeros. everything else is reserved */

    attr = env->GetIntArrayElements(attributes, NULL);
    if (!attr) {
        env->ReleaseByteArrayElements(address, addr, 0);
        env->ReleaseByteArrayElements(element_id, e_id, 0);
        jniThrowIOException(env, EINVAL);
        return CMD_FAILED;
    }

    status = sBluetoothRccInterface->get_element_attr((bt_bdaddr_t *)addr,
                                                      (uint8_t *)e_id,
                                                      (uint8_t)num_attributes,
                                                      (btrcc_media_attr_t *)attr,
                                                      (btrcc_label_t*)&label);
    if (status != BT_STATUS_SUCCESS) {
        error("Failed AVRCP CT get_player_attrs_text, status: %d", status);
    }

    env->ReleaseByteArrayElements(address, addr, 0);
    env->ReleaseByteArrayElements(element_id, e_id, 0);
    env->ReleaseIntArrayElements(attributes, attr, 0);
    return (status == BT_STATUS_SUCCESS) ? label : CMD_FAILED;
}

static jbyte getPlayStatusNative(JNIEnv *env, jobject object, jbyteArray address)
{
    jbyte *addr;
    bt_status_t status;
    jbyte label = CMD_FAILED;

    ALOGI("%s: sBluetoothRccInterface: %p", __FUNCTION__, sBluetoothRccInterface);
    if (!sBluetoothRccInterface) return CMD_FAILED;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return CMD_FAILED;
    }

    status = sBluetoothRccInterface->get_play_status((bt_bdaddr_t *)addr, (btrcc_label_t*)&label);
    if (status != BT_STATUS_SUCCESS) {
        ALOGE("Failed AVRCP CT get_play_status, status: %d", status);
    }
    env->ReleaseByteArrayElements(address, addr, 0);
    return (status == BT_STATUS_SUCCESS) ? label : CMD_FAILED;
}

static jboolean setAddressedPlayerNative(JNIEnv *env, jobject object,
    jbyteArray address,
    jint player_id)
{
    jbyte *addr;
    bt_status_t status;

    if (!sBluetoothRccInterface) return JNI_FALSE;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return JNI_FALSE;
    }

    status = sBluetoothRccInterface->set_addressed_player((bt_bdaddr_t *)addr,
                                                          (btrcc_player_id_t)player_id);
    if (status != BT_STATUS_SUCCESS) {
        error("Failed AVRCP CT set_addressed_player, status: %d", status);
    }

    env->ReleaseByteArrayElements(address, addr, 0);
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jbyte setBrowsedPlayerNative(JNIEnv *env, jobject object,
    jbyteArray address,
    jint player_id)
{
    jbyte *addr;
    bt_status_t status;
    jbyte label = CMD_FAILED;

    if (!sBluetoothRccInterface) return CMD_FAILED;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return CMD_FAILED;
    }

    status = sBluetoothRccInterface->set_browsed_player((bt_bdaddr_t *)addr,
                                                        (btrcc_player_id_t)player_id,
                                                        (btrcc_label_t*)&label);
    if (status != BT_STATUS_SUCCESS) {
        ALOGE("Failed AVRCP CT set_browsed_player, status: %d", status);
    }
    env->ReleaseByteArrayElements(address, addr, 0);
    return (status == BT_STATUS_SUCCESS) ? label : CMD_FAILED;
}

static jbyte changePathNative(JNIEnv *env, jobject object,
    jbyteArray address,
    jbyte direction,
    jbyteArray folder_uid)
{
    jbyte *addr;
    jbyte *uid;
    bt_status_t status;
    jbyte label = CMD_FAILED;

    if (!sBluetoothRccInterface) return CMD_FAILED;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return CMD_FAILED;
    }

    uid = env->GetByteArrayElements(folder_uid, NULL);
    if (!uid) {
        env->ReleaseByteArrayElements(address, addr, 0);
        jniThrowIOException(env, EINVAL);
        return CMD_FAILED;
    }

    status = sBluetoothRccInterface->change_path((bt_bdaddr_t *)addr,
                                                 (uint8_t)direction,
                                                 (uint8_t *)uid,
                                                 (btrcc_label_t*)&label);
    if (status != BT_STATUS_SUCCESS) {
        error("Failed AVRCP CT change_path, status: %d", status);
    }

    env->ReleaseByteArrayElements(address, addr, 0);
    env->ReleaseByteArrayElements(folder_uid, uid, 0);
    return (status == BT_STATUS_SUCCESS) ? label : CMD_FAILED;
}

static jbyte getFolderItemsNative(JNIEnv *env, jobject object,
    jbyteArray address,
    jbyte scope,
    jint start_item,
    jint end_item,
    jbyte num_attributes,
    jintArray attributes)
{
    jbyte *addr;
    jint *attr;
    bt_status_t status;
    jbyte label = CMD_FAILED;

    if (!sBluetoothRccInterface) return CMD_FAILED;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return CMD_FAILED;
    }

    attr = env->GetIntArrayElements(attributes, NULL);
    if (!attr) {
        env->ReleaseByteArrayElements(address, addr, 0);
        jniThrowIOException(env, EINVAL);
        return CMD_FAILED;
    }

    status = sBluetoothRccInterface->get_folder_items((bt_bdaddr_t *)addr,
                                                      (btrcc_scope_t)scope,
                                                      (uint32_t)start_item,
                                                      (uint32_t)end_item,
                                                      (uint8_t)num_attributes,
                                                      (btrcc_media_attr_t *)attr,
                                                      (btrcc_label_t*)&label);
    if (status != BT_STATUS_SUCCESS) {
        error("Failed AVRCP CT get_folder_items, status: %d", status);
    }

    env->ReleaseByteArrayElements(address, addr, 0);
    env->ReleaseIntArrayElements(attributes, attr, 0);
    return (status == BT_STATUS_SUCCESS) ? label : CMD_FAILED;
}

static jbyte getItemAttributesNative(JNIEnv *env, jobject object,
    jbyteArray address,
    jbyte scope,
    jbyteArray item_uid,
    jbyte num_attributes,
    jintArray attributes)
{
    jbyte *addr;
    jbyte *uid;
    jint *attr;
    bt_status_t status;
    jbyte label = CMD_FAILED;

    if (!sBluetoothRccInterface) return CMD_FAILED;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return CMD_FAILED;
    }

    uid = env->GetByteArrayElements(item_uid, NULL);
    if (!uid) {
        env->ReleaseByteArrayElements(address, addr, 0);
        jniThrowIOException(env, EINVAL);
        return CMD_FAILED;
    }

    attr = env->GetIntArrayElements(attributes, NULL);
    if (!attr) {
        env->ReleaseByteArrayElements(address, addr, 0);
        env->ReleaseByteArrayElements(item_uid, uid, 0);
        jniThrowIOException(env, EINVAL);
        return CMD_FAILED;
    }

    status = sBluetoothRccInterface->get_item_attributes((bt_bdaddr_t *)addr,
                                                         (btrcc_scope_t)scope,
                                                         (uint8_t *)uid,
                                                         (uint8_t)num_attributes,
                                                         (btrcc_media_attr_t *)attr,
                                                         (btrcc_label_t*)&label);
    if (status != BT_STATUS_SUCCESS) {
        error("Failed AVRCP CT get_item_attributes, status: %d", status);
    }

    env->ReleaseByteArrayElements(address, addr, 0);
    env->ReleaseByteArrayElements(item_uid, uid, 0);
    env->ReleaseIntArrayElements(attributes, attr, 0);
    return (status == BT_STATUS_SUCCESS) ? label : CMD_FAILED;
}

static jbyte searchNative(JNIEnv *env, jobject object,
    jbyteArray address,
    jint charset,
    jint length,
    jbyteArray string)
{
    jbyte *addr;
    jbyte *str;
    bt_status_t status;
    jbyte label = CMD_FAILED;

    if (!sBluetoothRccInterface) return CMD_FAILED;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return CMD_FAILED;
    }

    str = env->GetByteArrayElements(string, NULL);
    if (!str) {
        env->ReleaseByteArrayElements(address, addr, 0);
        jniThrowIOException(env, EINVAL);
        return CMD_FAILED;
    }

    btrcc_full_string_t search_string;
    search_string.character_set = (uint16_t)charset;
    search_string.length = (uint16_t)length;
    search_string.string = (uint8_t *)str;

    status = sBluetoothRccInterface->search((bt_bdaddr_t *)addr,
                                            search_string,
                                            (btrcc_label_t*)&label);
    if (status != BT_STATUS_SUCCESS) {
        error("Failed AVRCP CT search, status: %d", status);
    }

    env->ReleaseByteArrayElements(address, addr, 0);
    env->ReleaseByteArrayElements(string, str, 0);
    return (status == BT_STATUS_SUCCESS) ? label : CMD_FAILED;
}

static jbyte playItemNative(JNIEnv *env, jobject object,
    jbyteArray address,
    jbyte scope,
    jbyteArray item_uid)
{
    jbyte *addr;
    jbyte *uid;
    bt_status_t status;
    jbyte label = CMD_FAILED;

    if (!sBluetoothRccInterface) return CMD_FAILED;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return CMD_FAILED;
    }

    uid = env->GetByteArrayElements(item_uid, NULL);
    if (!uid) {
        env->ReleaseByteArrayElements(address, addr, 0);
        jniThrowIOException(env, EINVAL);
        return CMD_FAILED;
    }

    status = sBluetoothRccInterface->play_item((bt_bdaddr_t *)addr,
                                               (btrcc_scope_t)scope,
                                               (uint8_t *)uid,
                                               (btrcc_label_t*)&label);
    if (status != BT_STATUS_SUCCESS) {
        error("Failed AVRCP CT play_item, status: %d", status);
    }

    env->ReleaseByteArrayElements(address, addr, 0);
    env->ReleaseByteArrayElements(item_uid, uid, 0);
    return (status == BT_STATUS_SUCCESS) ? label : CMD_FAILED;
}

static jbyte addToNowPlayingNative(JNIEnv *env, jobject object,
    jbyteArray address,
    jbyte scope,
    jbyteArray item_uid)
{
    jbyte *addr;
    jbyte *uid;
    bt_status_t status;
    jbyte label = CMD_FAILED;

    if (!sBluetoothRccInterface) return CMD_FAILED;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return CMD_FAILED;
    }

    uid = env->GetByteArrayElements(item_uid, NULL);
    if (!uid) {
        env->ReleaseByteArrayElements(address, addr, 0);
        jniThrowIOException(env, EINVAL);
        return CMD_FAILED;
    }

    status = sBluetoothRccInterface->add_to_now_playing((bt_bdaddr_t *)addr,
                                                        (btrcc_scope_t)scope,
                                                        (uint8_t *)uid,
                                                        (btrcc_label_t*)&label);
    if (status != BT_STATUS_SUCCESS) {
        error("Failed AVRCP CT add_to_now_playing, status: %d", status);
    }

    env->ReleaseByteArrayElements(address, addr, 0);
    env->ReleaseByteArrayElements(item_uid, uid, 0);
    return (status == BT_STATUS_SUCCESS) ? label : CMD_FAILED;
}

static jbyte setAbsoluteVolumeResponseNative(JNIEnv *env, jobject object,
    jbyteArray address,
    jbyte volume,
    jbyte label,
    jint cmd_status)
{
    jbyte *addr;
    jbyte *uid;
    bt_status_t status;

    if (!sBluetoothRccInterface) return CMD_FAILED;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return CMD_FAILED;
    }

    sBluetoothRccInterface->send_absolute_volume_rsp((bt_bdaddr_t *)addr,
                                                      (uint8_t)volume,
                                                      (btrcc_label_t)label,
                                                      (uint32_t)cmd_status);

    env->ReleaseByteArrayElements(address, addr, 0);
    return label;
}

static jbyte updateAbsoluteVolumeNative(JNIEnv *env, jobject object,
    jbyte volume)
{
    bt_status_t status;
    jbyte label = CMD_FAILED;

    if (!sBluetoothRccInterface) return CMD_FAILED;

    status = sBluetoothRccInterface->absolute_volume_update((uint8_t)volume);
    if (status != BT_STATUS_SUCCESS) {
        error("Failed AVRCP CT absolute_volume_update, status: %d", status);
    }

    return (jbyte)status;
}


static JNINativeMethod sMethods[] = {
    {"classInitNative", "()V", (void *) classInitNative},
    {"initializeNative", "()V", (void *) initializeNative},
    {"cleanupNative", "()V", (void *) cleanupNative},
    {"connectNative", "([B)Z", (void *) connectNative},
    {"disconnectNative", "([B)Z", (void *) disconnectNative},
    {"getTargetFeaturesNative", "([B)I", (void *) getTargetFeaturesNative},
    {"passThroughCommandNative", "([BIIB[B)Z", (void *) passThroughCommandNative},
    {"listPlayerAttributesNative", "([B)B", (void *) listPlayerAttributesNative},
    {"listPlayerValuesNative", "([BB)B", (void *) listPlayerValuesNative},
    {"getPlayerValuesNative", "([BB[B)B", (void *) getPlayerValuesNative},
    {"setPlayerValuesNative", "([BB[B[B)Z", (void *) setPlayerValuesNative},
    {"getPlayerAttributesTextNative", "([BB[B)B", (void *) getPlayerAttributesTextNative},
    {"getPlayerValuesTextNative", "([BBB[B)B", (void *) getPlayerValuesTextNative},
    {"getElementAttributesNative", "([B[BB[I)B", (void *) getElementAttributesNative},
    {"getPlayStatusNative", "([B)B", (void *) getPlayStatusNative},
    {"setAddressedPlayerNative", "([BI)Z", (void *) setAddressedPlayerNative},
    {"setBrowsedPlayerNative", "([BI)B", (void *) setBrowsedPlayerNative},
    {"changePathNative", "([BB[B)B", (void *) changePathNative},
    {"getFolderItemsNative", "([BBIIB[I)B", (void *) getFolderItemsNative},
    {"getItemAttributesNative", "([BB[BB[I)B", (void *) getItemAttributesNative},
    {"searchNative", "([BII[B)B", (void *) searchNative},
    {"playItemNative", "([BB[B)B", (void *) playItemNative},
    {"addToNowPlayingNative", "([BB[B)B", (void *) addToNowPlayingNative},
    {"setAbsoluteVolumeResponseNative", "([BBBI)B", (void *) setAbsoluteVolumeResponseNative},
    {"updateAbsoluteVolumeNative", "(B)B", (void *) updateAbsoluteVolumeNative},
};

int register_com_broadcom_bluetooth_rcc(JNIEnv* env)
{
    return jniRegisterNativeMethods(env,
            "com/broadcom/bt/service/avrcp/AvrcpControllerService", sMethods, NELEM(sMethods));
}

} /* namespace android */

