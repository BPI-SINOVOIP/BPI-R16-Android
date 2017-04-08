/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2013 Broadcom Corporation
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

#define LOG_TAG "BluetoothAvrcpServiceJni"

#define LOG_NDEBUG 0

#include "com_android_bluetooth.h"
#include "hardware/bt_rc.h"
#include "utils/Log.h"
#include "android_runtime/AndroidRuntime.h"

#include <string.h>

#define CHECK_CALLBACK_ENV                                                      \
   if (!checkCallbackThread()) {                                                \
       ALOGE("Callback: '%s' is not called on the correct thread", __FUNCTION__);\
       return;                                                                  \
   }

namespace android {
static jmethodID method_getRcFeatures;
static jmethodID method_getPlayStatus;
static jmethodID method_getElementAttr;
static jmethodID method_registerNotification;
static jmethodID method_volumeChangeCallback;
static jmethodID method_handlePassthroughCmd;
//BRCM AVRCP 1.4 Enhancements ++
static jmethodID method_getFolderItemsCallback;
static jmethodID method_setAddrPlayerCallback;
//BRCM AVRCP 1.4 Enhancements --

//BRCM RC Enhancements
static jmethodID method_onRCConnectionStateChanged;

//BRCM PlayerSettings ++
// Repeat/Shuffle
static jmethodID method_getRepeatMode;
static jmethodID method_getShuffleMode;
static jmethodID method_setRepeatMode;
static jmethodID method_setShuffleMode;

static jfieldID  field_AppSettingsArray;
static jfieldID  field_RepeatAttrValsArray;
static jfieldID  field_RepeatAttrTextArray;
static jfieldID  field_ShuffleAttrValsArray;
static jfieldID  field_ShuffleAttrTextArray;

static jobject mAvrcpCallbacksObj = NULL;
static jclass mAvrcpClass = NULL;
//BRCM PlayerSettings --

static const btrc_interface_t *sBluetoothAvrcpInterface = NULL;
static jobject mCallbacksObj = NULL;
static JNIEnv *sCallbackEnv = NULL;

static bool checkCallbackThread() {
    // Always fetch the latest callbackEnv from AdapterService.
    // Caching this could cause this sCallbackEnv to go out-of-sync
    // with the AdapterService's ENV if an ASSOCIATE/DISASSOCIATE event
    // is received
    sCallbackEnv = getCallbackEnv();

    JNIEnv* env = AndroidRuntime::getJNIEnv();
    if (sCallbackEnv != env || sCallbackEnv == NULL) return false;
    return true;
}

//BRCM PlayerSettings ++
static int is_supported_def_player_setting_attribute(uint8_t attr) {
    checkCallbackThread();
    jbyteArray array = static_cast<jbyteArray>(sCallbackEnv->GetStaticObjectField(mAvrcpClass,
        field_AppSettingsArray));
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    jbyte* attr_array = sCallbackEnv->GetByteArrayElements(array, 0);
    int num_attr = sCallbackEnv->GetArrayLength(array);
    for(int i=0; i< num_attr; i++) {
        if(attr_array[i] == attr) {
            sCallbackEnv->ReleaseByteArrayElements(array, attr_array, 0);
            sCallbackEnv->DeleteLocalRef(array);
            return true;
        }
    }
    sCallbackEnv->ReleaseByteArrayElements(array, attr_array, 0);
    sCallbackEnv->DeleteLocalRef(array);
    return false;
}

static void btavrcp_remote_features_callback(bt_bdaddr_t* bd_addr, btrc_remote_features_t features) {
    ALOGI("%s", __FUNCTION__);
    jbyteArray addr;

    if (!checkCallbackThread()) {
        ALOGE("Callback: '%s' is not called on the correct thread", __FUNCTION__);
        return;
    }
    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (!addr) {
        ALOGE("Fail to new jbyteArray bd addr for connection state");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }

    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_getRcFeatures, addr, (jint)features);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    sCallbackEnv->DeleteLocalRef(addr);
}

static bool validate_attr_value(btrc_player_attr_t attr, uint8_t val) {
    if(attr != BTRC_PLAYER_ATTR_REPEAT && attr != BTRC_PLAYER_ATTR_SHUFFLE) {
        ALOGW("%s Unsupported attribute : 0x%x", __FUNCTION__, attr);
        return false;
    }
    if(attr == BTRC_PLAYER_ATTR_REPEAT) {
        if(val == BTRC_PLAYER_VAL_OFF_REPEAT || val == BTRC_PLAYER_VAL_SINGLE_REPEAT ||
          val == BTRC_PLAYER_VAL_ALL_REPEAT) {
            return true;
        }
        ALOGW("%s Invalid REPEAT value. Not setting it", __FUNCTION__);
        return false;
    }
    if(attr == BTRC_PLAYER_ATTR_SHUFFLE) {
        if(val == BTRC_PLAYER_VAL_OFF_SHUFFLE || val == BTRC_PLAYER_VAL_ALL_SHUFFLE) {
               return true;
           }
           ALOGW("%s Invalid SHUFFLE value. Not setting it", __FUNCTION__);
           return false;
    }
    return false;
}

#define GET_CSTR_FROM_JOBJECT_ARRAY(obj_array, idx, text)                       \
    jstring str = (jstring)sCallbackEnv->GetObjectArrayElement(obj_array, idx); \
    const char* attrChars = sCallbackEnv->GetStringUTFChars(str, NULL);         \
    if(attrChars)                                                               \
    {                                                                           \
        strcpy((char*)text, attrChars);                                         \
    } else {                                                                    \
        text[0] = 0;                                                            \
    }                                                                           \
    sCallbackEnv->ReleaseStringUTFChars(str, attrChars);                        \
    sCallbackEnv->DeleteLocalRef(str);                                          \

//BRCM PlayerSettings --

//BRCM RC Enhancements
#define GET_CSTR_FROM_JAVA(pJavaObject, cstr, jmethod, ...)                         \
        checkCallbackThread();                                                      \
        do {                                                                        \
            jstring jstr = (jstring) sCallbackEnv->CallObjectMethod(pJavaObject,    \
                jmethod, ## __VA_ARGS__);                                           \
              checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);       \
            const char* utf = (char *) sCallbackEnv->GetStringUTFChars(jstr, 0);    \
            cstr[0] = 0;                                                            \
            if (utf)                                                                \
            {                                                                       \
                size_t tmpSize;                                                     \
                if(sizeof(cstr) == sizeof(char *))                                  \
                    strcpy((char *)cstr, utf);                                      \
                else                                                                \
                    strncpy((char *)cstr, utf, sizeof(cstr));                       \
                tmpSize = strlen((char *)cstr);                                     \
                if(tmpSize < BTRC_MAX_ATTR_STR_LEN)                                 \
                    cstr[tmpSize+1] = '\0';                                         \
                else                                                                \
                    cstr[BTRC_MAX_ATTR_STR_LEN-1] = '\0';                           \
                sCallbackEnv->ReleaseStringUTFChars(jstr, utf);                     \
                sCallbackEnv->DeleteLocalRef(jstr);                                 \
            }                                                                       \
        } while(0)



/** Callback for play status request */
static void btavrcp_get_play_status_callback() {
    ALOGI("%s", __FUNCTION__);

    CHECK_CALLBACK_ENV

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_getPlayStatus);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

//BRCM PlayerSettings ++
/* AVRCP callbacks */
/** Callback for RC Connection state changed */
static void btavrcp_connection_state_callback(bt_bdaddr_t *bd_addr,
    btrc_connection_state_t state) {
    jbyteArray addr;

    ALOGI("%s", __FUNCTION__);

    CHECK_CALLBACK_ENV
    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (!addr) {
            ALOGE("Fail to new jbyteArray bd addr for connection state");
            checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
            return;
    }

    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onRCConnectionStateChanged, (jint) state,
        addr);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    sCallbackEnv->DeleteLocalRef(addr);
}
static void btavrcp_list_player_app_attr_callback() {
    ALOGI("%s", __FUNCTION__);
    bt_status_t status;
    btrc_player_attr_t attrs[BTRC_MAX_APP_SETTINGS];
    int num_attr=0;

    CHECK_CALLBACK_ENV
    memset(attrs, 0, sizeof(btrc_player_attr_t)*BTRC_MAX_APP_SETTINGS);
    jbyteArray array = static_cast<jbyteArray>(sCallbackEnv->GetStaticObjectField(mAvrcpClass,
        field_AppSettingsArray));

    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    jbyte* attr_array = sCallbackEnv->GetByteArrayElements(array, 0);
    num_attr = sCallbackEnv->GetArrayLength(array);
    for(int i=0; i< num_attr; i++) {
        attrs[i] = static_cast<btrc_player_attr_t>(attr_array[i]);
    }
    sCallbackEnv->ReleaseByteArrayElements(array, attr_array, 0);
    sCallbackEnv->DeleteLocalRef(array);

    if (!sBluetoothAvrcpInterface) {
        ALOGE("sBluetoothAvrcpInterface is null. Hence return");
        return ;
    }
    if ((status = sBluetoothAvrcpInterface->list_player_app_attr_rsp(num_attr, attrs))
        != BT_STATUS_SUCCESS) {
        ALOGE("%s Failed to send  list play app attr response, status: %d",
            __FUNCTION__, status);
    }
}


/** Callback for list player application attributes (Shuffle, Repeat,...) */
void btavrcp_list_player_app_values_callback(btrc_player_attr_t attr_id) {
    ALOGI("%s attr_id:%d", __FUNCTION__, attr_id);
    uint8_t vals[BTRC_MAX_APP_ATTR_SIZE];
    //uint8_t num_val = 0;
    int num_val = 0;
    int i;
    bt_status_t status;
    memset(vals, 0, sizeof(uint8_t)*BTRC_MAX_APP_ATTR_SIZE);

    CHECK_CALLBACK_ENV

    if (is_supported_def_player_setting_attribute(attr_id)) {
        jintArray array = static_cast<jintArray>(sCallbackEnv->GetStaticObjectField(mAvrcpClass,
            (attr_id == BTRC_PLAYER_ATTR_REPEAT)?field_RepeatAttrValsArray:
            field_ShuffleAttrValsArray));
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        jint* attr_array = sCallbackEnv->GetIntArrayElements(array, 0);
        num_val = sCallbackEnv->GetArrayLength(array);
        for(int j=0; j<num_val;j++) {
            vals[j] = static_cast<uint8_t>(attr_array[j]);
        }
        sCallbackEnv->ReleaseIntArrayElements(array, attr_array, 0);
        sCallbackEnv->DeleteLocalRef(array);
        attr_array = NULL;
    } else {
        ALOGW("%s Unhandled attr_id: %x", __FUNCTION__, attr_id);
        num_val = 0;
    }

    if (!sBluetoothAvrcpInterface) {
        ALOGE("sBluetoothAvrcpInterface is null. Hence return");
        return ;
    }
    if ((status = sBluetoothAvrcpInterface->list_player_app_value_rsp(/*(uint8_t)*/num_val, vals))
        != BT_STATUS_SUCCESS) {
        ALOGE("%s Failed to send list play app value response, status: %d",
            __FUNCTION__, status);
    }

}


/** Callback for getting the current player application settings value
**  num_attr: specifies the number of attribute ids contained in p_attrs
*/
void btavrcp_get_player_app_value_callback(uint8_t num_attr, btrc_player_attr_t *p_attrs) {
    ALOGI("%s", __FUNCTION__);
    bt_status_t status;
    int i;
    uint8_t num_val = 0;
    btrc_player_settings_t vals;
    memset(&vals, 0, sizeof(btrc_player_settings_t));

    CHECK_CALLBACK_ENV

    for (i=0;i<num_attr;i++)
    {
       switch (p_attrs[i])
       {
          case BTRC_PLAYER_ATTR_REPEAT:
            {
                uint8_t mode = sCallbackEnv->CallIntMethod(mAvrcpCallbacksObj,
                    method_getRepeatMode);
                checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
                vals.attr_ids[num_val] = BTRC_PLAYER_ATTR_REPEAT;
                vals.attr_values[num_val] = mode;
                num_val++;
            }
            break;
          case BTRC_PLAYER_ATTR_SHUFFLE:
            {
                uint8_t mode = sCallbackEnv->CallIntMethod(mAvrcpCallbacksObj,
                    method_getShuffleMode);
                checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
                vals.attr_ids[num_val]= BTRC_PLAYER_ATTR_SHUFFLE;
                vals.attr_values[num_val] = mode;
                num_val++;
            }
            break;
        default:
            ALOGW("%s Unhandled attr id : 0x%x",
                __FUNCTION__, p_attrs[i]);
            break;
       }
    }
    vals.num_attr = num_val;
    if (num_val == 0) {
        /* This means there isnt even a single supported setting response */
        //get_cur_app_val_rsp->rsp.status = BTLIF_AVRC_STS_BAD_PARAM;
        ALOGW("%s Value count is 0 in response", __FUNCTION__);
    }

    if (!sBluetoothAvrcpInterface) {
        ALOGE("sBluetoothAvrcpInterface is null. Hence return");
        return ;
    }
    if ((status = sBluetoothAvrcpInterface->get_player_app_value_rsp(
            &vals)) != BT_STATUS_SUCCESS) {
        ALOGE("%s Failed to send current play app value response, status: %d",
            __FUNCTION__, status);
    }
}

/** Callback for getting the player application settings attributes' text
**  num_attr: specifies the number of attribute ids contained in p_attrs
*/
void btavrcp_get_player_app_attrs_text_callback(uint8_t num_attr,
    btrc_player_attr_t *p_attrs) {
    ALOGI("%s", __FUNCTION__);
    //uint8_t l_num_attr = 0;
    int l_num_attr = 0;
    bt_status_t status;
    int i;
    btrc_player_setting_text_t rsp_attrs[BTRC_MAX_APP_SETTINGS];
    memset(rsp_attrs, 0, sizeof(btrc_player_setting_text_t)*
        BTRC_MAX_APP_SETTINGS);
    CHECK_CALLBACK_ENV

    for(i=0; i<num_attr; i++) {
        /* check if the get_app_attr_txt_req.attrs is one of the supported attr_ids */
        if (is_supported_def_player_setting_attribute(p_attrs[i])) {
            jobjectArray array = static_cast<jobjectArray>(sCallbackEnv->GetStaticObjectField(
                mAvrcpClass, (p_attrs[i] == BTRC_PLAYER_ATTR_REPEAT)?field_RepeatAttrTextArray:
                field_ShuffleAttrTextArray));
            checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
            GET_CSTR_FROM_JOBJECT_ARRAY(array, 0, rsp_attrs[l_num_attr].text)
            if (rsp_attrs[l_num_attr].text[0] != 0) {
                rsp_attrs[l_num_attr].id = p_attrs[i];
                l_num_attr++;
            }
            sCallbackEnv->DeleteLocalRef(array);
        } else {
            ALOGW("%s Unhandled attr : %x", __FUNCTION__, p_attrs[i]);
            continue;
        }
    }
    if (l_num_attr == 0) {
        ALOGW("%s Attribute count is 0 in response", __FUNCTION__);
        //get_app_attr_txt_rsp->rsp.status = BTLIF_AVRC_STS_BAD_PARAM;
    }

    if (!sBluetoothAvrcpInterface) {
        ALOGE("sBluetoothAvrcpInterface is null. Hence return");
        return ;
    }
    if ((status = sBluetoothAvrcpInterface->get_player_app_attr_text_rsp(l_num_attr, rsp_attrs))
        != BT_STATUS_SUCCESS) {
        ALOGE("%s Failed to send current play attr response, status: %d", __FUNCTION__, status);
    }
}

/** Callback for getting the player application settings values' text
**  num_attr: specifies the number of value ids contained in p_vals
*/
void btavrcp_get_player_app_values_text_callback(uint8_t attr_id,
    uint8_t num_val, uint8_t *p_vals) {
    ALOGI("%s", __FUNCTION__);
    uint8_t /*num_attr = 0,*/tmp_id = 0;
    int num_attr = 0;
    int i, j;
    bt_status_t status;
    btrc_player_setting_text_t rsp_vals[BTRC_MAX_APP_SETTINGS];
    memset(rsp_vals, 0, sizeof(btrc_player_setting_text_t)*BTRC_MAX_APP_SETTINGS);

    CHECK_CALLBACK_ENV

    /* check if the get_app_attr_txt_req.attrs is one of the supported attr_ids */
    if (is_supported_def_player_setting_attribute(attr_id)) {
        jintArray array_ids = static_cast<jintArray>(sCallbackEnv->GetStaticObjectField(
            mAvrcpClass, (attr_id == BTRC_PLAYER_ATTR_REPEAT)?field_RepeatAttrValsArray:
            field_ShuffleAttrValsArray));
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        jobjectArray array_vals = static_cast<jobjectArray>(sCallbackEnv->GetStaticObjectField(
            mAvrcpClass, (attr_id == BTRC_PLAYER_ATTR_REPEAT)?field_RepeatAttrTextArray:
            field_ShuffleAttrTextArray));
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        jint* attr_id_array = sCallbackEnv->GetIntArrayElements(array_ids, 0);
        int attr_len = sCallbackEnv->GetArrayLength(array_ids);
        for(j=0; j<attr_len; j++) {
            tmp_id = static_cast<uint8_t>(attr_id_array[j]);
            if(p_vals[j] == tmp_id) {
                GET_CSTR_FROM_JOBJECT_ARRAY(array_vals, j+1, rsp_vals[num_attr].text)
                if(rsp_vals[num_attr].text[0] != 0)
                {
                    rsp_vals[num_attr].id = tmp_id;
                    num_attr++;
                }
            }
        }
        if(attr_id_array != NULL) {
            sCallbackEnv->ReleaseIntArrayElements(array_ids, attr_id_array, 0);
            attr_id_array = NULL;
        }
        sCallbackEnv->DeleteLocalRef(array_ids);
        sCallbackEnv->DeleteLocalRef(array_vals);
    }  else {
        ALOGW("%s Unhandled attr ID : %x", __FUNCTION__, attr_id);
        num_attr = 0;
    }

    if (num_attr == 0) {
        /* This means there isnt even a single supported setting response */
        //get_app_val_txt_rsp->rsp.status = BTLIF_AVRC_STS_BAD_PARAM;
        ALOGW("%s Attribute count is 0 in response", __FUNCTION__);
    }

    if (!sBluetoothAvrcpInterface) {
        ALOGE("sBluetoothAvrcpInterface is null. Hence return");
        return ;
    }
    if ((status = sBluetoothAvrcpInterface->get_player_app_value_text_rsp(num_attr, rsp_vals))
        != BT_STATUS_SUCCESS) {
        ALOGE("%s Failed to send current play values response, status: %d", __FUNCTION__, status);
    }
}


/** Callback for setting the player application settings values */
void btavrcp_set_player_app_value_callback(btrc_player_settings_t *p_vals) {
    ALOGI("%s", __FUNCTION__);
    bt_status_t status;
    btrc_status_t rsp_status = BTRC_STS_BAD_PARAM;
    uint8_t num_attr = 0;
    int i, j;
    for(i=0; i< p_vals->num_attr; i++) {
        switch(p_vals->attr_ids[i]) {
            case BTRC_PLAYER_ATTR_REPEAT:
                CHECK_CALLBACK_ENV
                if(validate_attr_value(BTRC_PLAYER_ATTR_REPEAT, p_vals->attr_values[i])) {
                    sCallbackEnv->CallVoidMethod(mAvrcpCallbacksObj, method_setRepeatMode,
                        (jint)p_vals->attr_values[i]);
                    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
                    ALOGI("%s: BTRC_PLAYER_ATTR_REPEAT - Called setRepeatMode()",
                         __FUNCTION__);
                    rsp_status = BTRC_STS_NO_ERROR;
                } else {
                    rsp_status = BTRC_STS_NOT_FOUND;
                }
                break;
            case BTRC_PLAYER_ATTR_SHUFFLE:
                CHECK_CALLBACK_ENV
                if(validate_attr_value(BTRC_PLAYER_ATTR_SHUFFLE, p_vals->attr_values[i])) {
                    sCallbackEnv->CallVoidMethod(mAvrcpCallbacksObj, method_setShuffleMode,
                        (jint)p_vals->attr_values[i]);
                    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
                    ALOGI("%s: BTRC_PLAYER_ATTR_SHUFFLE - Called setShuffleMode()", __FUNCTION__);
                    rsp_status = BTRC_STS_NO_ERROR;
                } else {
                    rsp_status = BTRC_STS_NOT_FOUND;
                }
                break;
            default:
                ALOGE("%s:  Unhandled player attribute 0x%x", __FUNCTION__, p_vals->attr_ids[i]);
                rsp_status = BTRC_STS_BAD_PARAM;
                break;
        }
    }
    if (!sBluetoothAvrcpInterface) {
        ALOGE("sBluetoothAvrcpInterface is null. Hence return");
        return ;
    }
    if ((status = sBluetoothAvrcpInterface->set_player_app_value_rsp(rsp_status))
        != BT_STATUS_SUCCESS) {
        ALOGE("%s Failed to send set attr response, status: %d", __FUNCTION__, status);
    }
}
//BRCM PlayerSettings --

static void btavrcp_get_element_attr_callback(uint8_t num_attr, btrc_media_attr_t *p_attrs) {
    jintArray attrs;

    ALOGI("%s", __FUNCTION__);

    CHECK_CALLBACK_ENV
    attrs = (jintArray)sCallbackEnv->NewIntArray(num_attr);
    if (!attrs) {
        ALOGE("Fail to new jintArray for attrs");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    sCallbackEnv->SetIntArrayRegion(attrs, 0, num_attr, (jint *)p_attrs);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_getElementAttr, (jbyte)num_attr, attrs);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    sCallbackEnv->DeleteLocalRef(attrs);
}

static void btavrcp_register_notification_callback(btrc_event_id_t event_id, uint32_t param) {
    ALOGI("%s", __FUNCTION__);
    bt_status_t status;
    btrc_register_notification_t rsp_param;
    CHECK_CALLBACK_ENV

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_registerNotification,
                                 (jint)event_id, (jint)param);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}
//Callback Interface
static void btavrcp_volume_change_callback(uint8_t volume, uint8_t ctype) {
    ALOGI("%s", __FUNCTION__);

    if (!checkCallbackThread()) {
        ALOGE("Callback: '%s' is not called on the correct thread", __FUNCTION__);
        return;
    }

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_volumeChangeCallback, (jint)volume,
                                                                             (jint)ctype);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

static void btavrcp_passthrough_command_callback(int id, int pressed) {
    ALOGI("%s", __FUNCTION__);

    if (!checkCallbackThread()) {
        ALOGE("Callback: '%s' is not called on the correct thread", __FUNCTION__);
        return;
    }

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_handlePassthroughCmd, (jint)id,
                                                                             (jint)pressed);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

//BRCM AVRCP 1.4 Enhancements ++
static void btavrcp_get_folder_items_callback(uint8_t scope, uint32_t start_item,
            uint32_t end_item,uint8_t num_attr,btrc_element_attr_val_t *p_attr_ids) {

    jintArray attr_ids;
    jobjectArray strAttrTextArray;
    jclass stringClass;

    ALOGI("%s", __FUNCTION__);
    if (!checkCallbackThread()) {
        ALOGE("Callback: '%s' is not called on the correct thread", __FUNCTION__);
        return;
    }


    attr_ids = (jintArray)sCallbackEnv->NewIntArray(num_attr);
    if (!attr_ids) {
        ALOGE("Fail to new jintArray for attrs");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }

    stringClass = sCallbackEnv->FindClass("java/lang/String");
    strAttrTextArray=sCallbackEnv->NewObjectArray(num_attr, stringClass, NULL);
    if (!strAttrTextArray) {
        if (attr_ids != NULL) sCallbackEnv->DeleteLocalRef(attr_ids);
        if (stringClass != NULL) sCallbackEnv->DeleteLocalRef(stringClass);
        ALOGE("Fail to new jobjectArray for attr strings");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }

    if (stringClass != NULL) sCallbackEnv->DeleteLocalRef(stringClass);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_getFolderItemsCallback, (jbyte) scope,
                  (jint) start_item, (jint) end_item, (jbyte) num_attr, attr_ids, strAttrTextArray);

Exit:
    if (attr_ids != NULL) sCallbackEnv->DeleteLocalRef(attr_ids);
    if (strAttrTextArray !=NULL) sCallbackEnv->DeleteLocalRef(strAttrTextArray);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

static void btavrcp_set_addressed_player_callback(uint16_t player_id) {
    ALOGI("%s", __FUNCTION__);

    if (!checkCallbackThread()) {
        ALOGE("Callback: '%s' is not called on the correct thread", __FUNCTION__);
    return;
    }

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_setAddrPlayerCallback, (jint) player_id);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}
//BRCM AVRCP 1.4 Enhancements --

static btrc_callbacks_t sBluetoothAvrcpCallbacks = {
    sizeof(sBluetoothAvrcpCallbacks),
    btavrcp_connection_state_callback,
    btavrcp_remote_features_callback,
    btavrcp_get_play_status_callback,
    //BRCM PlayerSettings ++
    btavrcp_list_player_app_attr_callback,
    btavrcp_list_player_app_values_callback,
    btavrcp_get_player_app_value_callback,
    btavrcp_get_player_app_attrs_text_callback,
    btavrcp_get_player_app_values_text_callback,
    btavrcp_set_player_app_value_callback,
    //BRCM PlayerSettings --
    btavrcp_get_element_attr_callback,
    btavrcp_register_notification_callback,
    btavrcp_volume_change_callback,
    btavrcp_passthrough_command_callback,
//BRCM AVRCP 1.4 Enhancements ++
    btavrcp_get_folder_items_callback,
    btavrcp_set_addressed_player_callback
//BRCM AVRCP 1.4 Enhancements --
};

static void classInitNative(JNIEnv* env, jclass clazz) {
    method_getRcFeatures =
        env->GetMethodID(clazz, "getRcFeatures", "([BI)V");
    method_getPlayStatus =
        env->GetMethodID(clazz, "getPlayStatus", "()V");

    method_getElementAttr =
        env->GetMethodID(clazz, "getElementAttr", "(B[I)V");

    method_registerNotification =
        env->GetMethodID(clazz, "registerNotification", "(II)V");

    //BRCM PlayerSettings ++
    // Repeat/Shuffle
    method_getRepeatMode     = env->GetMethodID(clazz, "getRepeatMode", "()I");
    method_getShuffleMode    = env->GetMethodID(clazz, "getShuffleMode", "()I");
    method_setRepeatMode     = env->GetMethodID(clazz, "setRepeatMode", "(I)V");
    method_setShuffleMode    = env->GetMethodID(clazz, "setShuffleMode", "(I)V");
    method_volumeChangeCallback =
        env->GetMethodID(clazz, "volumeChangeCallback", "(II)V");

    method_handlePassthroughCmd =
        env->GetMethodID(clazz, "handlePassthroughCmd", "(II)V");

    jclass cls_avrcp = env->FindClass("com/android/bluetooth/a2dp/Avrcp");
    if(cls_avrcp == NULL) {
        ALOGW("%s Class Avrcp not found from JNI", __FUNCTION__);
    } else {
        field_AppSettingsArray = env->GetStaticFieldID(cls_avrcp,
            "AVRC_SUPPORTED_APP_SETTINGS", "[B");
        field_RepeatAttrValsArray = env->GetStaticFieldID(cls_avrcp,
            "SUPPORTED_REPEAT_ATTR_VAL", "[I");
        field_RepeatAttrTextArray = env->GetStaticFieldID(cls_avrcp, "SUPPORTED_REPEAT_ATTR_TXT",
            "[Ljava/lang/String;");
        field_ShuffleAttrValsArray = env->GetStaticFieldID(cls_avrcp,
            "SUPPORTED_SHUFFLE_ATTR_VAL", "[I");
        field_ShuffleAttrTextArray = env->GetStaticFieldID(cls_avrcp, "SUPPORTED_SHUFFLE_ATTR_TXT",
            "[Ljava/lang/String;");
    }
    //BRCM PlayerSettings --
    //BRCM RC Enhancement
    method_onRCConnectionStateChanged = env->GetMethodID(clazz, "onRCConnectionStateChanged",
            "(I[B)V");
    /*method_startTimer        = env->GetMethodID(clazz,"startTimer", "(I)V");
    method_stopTimer         = env->GetMethodID(clazz,"stopTimer", "()V");*/

//BRCM AVRCP 1.4 Enhancements ++
    method_getFolderItemsCallback =
        env->GetMethodID(clazz, "getFolderItemsCallback", "(BIIB[B[Ljava/lang/String;)V");

    method_setAddrPlayerCallback =
        env->GetMethodID(clazz, "setAddrPlayerCallback", "(I)V");
//BRCM AVRCP 1.4 Enhancements --

    ALOGI("%s: succeeds", __FUNCTION__);
}

static void initNative(JNIEnv *env, jobject object) {
    const bt_interface_t* btInf;
    bt_status_t status;
    //BRCM PlayerSettings ++
    jclass cls_avrcp;
    //BRCM PlayerSettings --

    if ( (btInf = getBluetoothInterface()) == NULL) {
        ALOGE("Bluetooth module is not loaded");
        return;
    }

    if (sBluetoothAvrcpInterface !=NULL) {
         ALOGW("Cleaning up Avrcp Interface before initializing...");
         sBluetoothAvrcpInterface->cleanup();
         sBluetoothAvrcpInterface = NULL;
    }

    if (mCallbacksObj != NULL) {
         ALOGW("Cleaning up Avrcp callback object");
         env->DeleteGlobalRef(mCallbacksObj);
         mCallbacksObj = NULL;
    }

    if ( (sBluetoothAvrcpInterface = (btrc_interface_t *)
          btInf->get_profile_interface(BT_PROFILE_AV_RC_ID)) == NULL) {
        ALOGE("Failed to get Bluetooth Avrcp Interface");
        return;
    }

    if ( (status = sBluetoothAvrcpInterface->init(&sBluetoothAvrcpCallbacks)) !=
         BT_STATUS_SUCCESS) {
        ALOGE("Failed to initialize Bluetooth Avrcp, status: %d", status);
        sBluetoothAvrcpInterface = NULL;
        return;
    }

    mCallbacksObj = env->NewGlobalRef(object);
    //BRCM PlayerSettings ++
    mAvrcpCallbacksObj = env->NewGlobalRef(object);
    cls_avrcp = env->FindClass("com/android/bluetooth/a2dp/Avrcp");
    if(cls_avrcp == NULL) {
        ALOGW("%s Class AVRCP not found from JNI", __FUNCTION__);
    } else {
        mAvrcpClass = (jclass)env->NewGlobalRef(cls_avrcp);
    }
    //BRCM PlayerSettings --

}

static void cleanupNative(JNIEnv *env, jobject object) {
    const bt_interface_t* btInf;

    if ( (btInf = getBluetoothInterface()) == NULL) {
        ALOGE("Bluetooth module is not loaded");
        return;
    }

    if (sBluetoothAvrcpInterface !=NULL) {
        sBluetoothAvrcpInterface->cleanup();
        sBluetoothAvrcpInterface = NULL;
    }

    if (mCallbacksObj != NULL) {
        env->DeleteGlobalRef(mCallbacksObj);
        mCallbacksObj = NULL;
    }
    //BRCM PlayerSettings ++
    if (mAvrcpCallbacksObj != NULL) {
        env->DeleteGlobalRef(mAvrcpCallbacksObj);
        mAvrcpCallbacksObj = NULL;
    }

    if(mAvrcpClass != NULL) {
        env->DeleteGlobalRef(mAvrcpClass);
        mAvrcpClass = NULL;
    }
    //BRCM PlayerSettings --
}

//BRCM RC Enhancements
static jboolean disconnectRcNative(JNIEnv *env, jobject object, jbyteArray address) {
    jbyte *addr;
    bt_status_t status;

    if (!sBluetoothAvrcpInterface) return JNI_FALSE;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return JNI_FALSE;
    }

    if((status = sBluetoothAvrcpInterface->rc_disconnect((bt_bdaddr_t *)addr)) !=
        BT_STATUS_SUCCESS) {
        ALOGE("Failed RC disconnection, status: %d", status);
    }
    env->ReleaseByteArrayElements(address, addr, 0);
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}


static jboolean getPlayStatusRspNative(JNIEnv *env, jobject object, jint playStatus,
                                       jint songLen, jint songPos) {
    bt_status_t status;

    ALOGI("%s: sBluetoothAvrcpInterface: %p", __FUNCTION__, sBluetoothAvrcpInterface);
    if (!sBluetoothAvrcpInterface) return JNI_FALSE;

    if ((status = sBluetoothAvrcpInterface->get_play_status_rsp((btrc_play_status_t)playStatus,
                                            songLen, songPos)) != BT_STATUS_SUCCESS) {
        ALOGE("Failed get_play_status_rsp, status: %d", status);
    }

    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

  static jboolean getElementAttrRspNative(JNIEnv *env, jobject object, jbyte numAttr,
                                          jintArray attrIds, jobjectArray textArray) {
    jint *attr;
    bt_status_t status;
    jstring text;
    int i;
    btrc_element_attr_val_t *pAttrs = NULL;
    const char* textStr;

    if (!sBluetoothAvrcpInterface) return JNI_FALSE;

    if (numAttr > BTRC_MAX_ELEM_ATTR_SIZE) {
        ALOGE("get_element_attr_rsp: number of attributes exceed maximum");
        return JNI_FALSE;
    }

    pAttrs = new btrc_element_attr_val_t[numAttr];
    if (!pAttrs) {
        ALOGE("get_element_attr_rsp: not have enough memeory");
        return JNI_FALSE;
    }

    attr = env->GetIntArrayElements(attrIds, NULL);
    if (!attr) {
        delete[] pAttrs;
        jniThrowIOException(env, EINVAL);
        return JNI_FALSE;
    }

    for (i = 0; i < numAttr; ++i) {
        text = (jstring) env->GetObjectArrayElement(textArray, i);
        textStr = env->GetStringUTFChars(text, NULL);
        if (!textStr) {
            ALOGE("get_element_attr_rsp: GetStringUTFChars return NULL");
            env->DeleteLocalRef(text);
            break;
        }

        pAttrs[i].attr_id = attr[i];
        if (strlen(textStr) >= BTRC_MAX_ATTR_STR_LEN) {
            ALOGE("get_element_attr_rsp: string length exceed maximum");
            strncpy((char *)pAttrs[i].text, textStr, BTRC_MAX_ATTR_STR_LEN-1);
            pAttrs[i].text[BTRC_MAX_ATTR_STR_LEN-1] = 0;
        } else {
            strcpy((char *)pAttrs[i].text, textStr);
        }
        env->ReleaseStringUTFChars(text, textStr);
        env->DeleteLocalRef(text);
    }

    if (i < numAttr) {
        delete[] pAttrs;
        env->ReleaseIntArrayElements(attrIds, attr, 0);
        return JNI_FALSE;
    }

    if ((status = sBluetoothAvrcpInterface->get_element_attr_rsp(numAttr, pAttrs)) !=
        BT_STATUS_SUCCESS) {
        ALOGE("Failed get_element_attr_rsp, status: %d", status);
    }

    delete[] pAttrs;
    env->ReleaseIntArrayElements(attrIds, attr, 0);
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean registerNotificationRspPlayStatusNative(JNIEnv *env, jobject object,
                                                        jint type, jint playStatus) {
    bt_status_t status;
    btrc_register_notification_t param;

    ALOGI("%s: sBluetoothAvrcpInterface: %p", __FUNCTION__, sBluetoothAvrcpInterface);
    if (!sBluetoothAvrcpInterface) return JNI_FALSE;

    param.play_status = (btrc_play_status_t)playStatus;
    if ((status = sBluetoothAvrcpInterface->register_notification_rsp(BTRC_EVT_PLAY_STATUS_CHANGED,
                  (btrc_notification_type_t)type, &param)) != BT_STATUS_SUCCESS) {
        ALOGE("Failed register_notification_rsp play status, status: %d", status);
    }

    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean registerNotificationRspTrackChangeNative(JNIEnv *env, jobject object,
                                                         jint type, jbyteArray track) {
    bt_status_t status;
    btrc_register_notification_t param;
    jbyte *trk;
    int i;

    ALOGI("%s: sBluetoothAvrcpInterface: %p", __FUNCTION__, sBluetoothAvrcpInterface);
    if (!sBluetoothAvrcpInterface) return JNI_FALSE;

    trk = env->GetByteArrayElements(track, NULL);
    if (!trk) {
        jniThrowIOException(env, EINVAL);
        return JNI_FALSE;
    }

    for (i = 0; i < BTRC_UID_SIZE; ++i) {
      param.track[i] = trk[i];
    }

    if ((status = sBluetoothAvrcpInterface->register_notification_rsp(BTRC_EVT_TRACK_CHANGE,
                  (btrc_notification_type_t)type, &param)) != BT_STATUS_SUCCESS) {
        ALOGE("Failed register_notification_rsp track change, status: %d", status);
    }

    env->ReleaseByteArrayElements(track, trk, 0);
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean registerNotificationRspPlayPosNative(JNIEnv *env, jobject object,
                                                        jint type, jint playPos) {
    bt_status_t status;
    btrc_register_notification_t param;

    ALOGI("%s: sBluetoothAvrcpInterface: %p", __FUNCTION__, sBluetoothAvrcpInterface);
    if (!sBluetoothAvrcpInterface) return JNI_FALSE;

    param.song_pos = (uint32_t)playPos;
    if ((status = sBluetoothAvrcpInterface->register_notification_rsp(BTRC_EVT_PLAY_POS_CHANGED,
                  (btrc_notification_type_t)type, &param)) != BT_STATUS_SUCCESS) {
        ALOGE("Failed register_notification_rsp play position, status: %d", status);
    }

    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

//BRCM AVRCP 1.4 Enhancements ++
static jboolean registerNotificationRspAddrPlayerChangedNative(JNIEnv *env, jobject object,
                                                        jint type, jint Player_id, jint uid_counter) {
    bt_status_t status;
    btrc_register_notification_t param;

    ALOGI("%s: sBluetoothAvrcpInterface: %p", __FUNCTION__, sBluetoothAvrcpInterface);
    if (!sBluetoothAvrcpInterface) return JNI_FALSE;

    param.addr_player_changed.player_id = (uint16_t)Player_id;
    param.addr_player_changed.uid_counter = (uint16_t)uid_counter;

    if ((status = sBluetoothAvrcpInterface->register_notification_rsp(BTRC_EVT_ADDR_PLAYER_CHANGE,
                  (btrc_notification_type_t)type, &param)) != BT_STATUS_SUCCESS) {
        ALOGE("Failed register_notification_rsp address player changed status, status: %d", status);
    }

    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean registerNotificationRspAvalPlayerChangedNative(JNIEnv *env, jobject object,
                                                        jint type) {
    bt_status_t status;
    btrc_register_notification_t param;

    ALOGI("%s: sBluetoothAvrcpInterface: %p", __FUNCTION__, sBluetoothAvrcpInterface);
    if (!sBluetoothAvrcpInterface) return JNI_FALSE;

    if ((status = sBluetoothAvrcpInterface->register_notification_rsp(BTRC_EVT_AVAL_PLAYER_CHANGE,
                  (btrc_notification_type_t)type, &param)) != BT_STATUS_SUCCESS) {
        ALOGE("Failed register_notification_rsp available player changed status, status: %d",
            status);
    }

    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}
//BRCM AVRCP 1.4 Enhancements --

static jboolean setVolumeNative(JNIEnv *env, jobject object, jint volume) {
    bt_status_t status;

    //TODO: delete test code
    ALOGI("%s: jint: %d, uint8_t: %u", __FUNCTION__, volume, (uint8_t) volume);

    ALOGI("%s: sBluetoothAvrcpInterface: %p", __FUNCTION__, sBluetoothAvrcpInterface);
    if (!sBluetoothAvrcpInterface) return JNI_FALSE;

    if ((status = sBluetoothAvrcpInterface->set_volume((uint8_t)volume)) != BT_STATUS_SUCCESS) {
        ALOGE("Failed set_volume, status: %d", status);
    }

    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

//BRCM PlayerSettings ++
static jboolean registerNotificationRspAppSettingsChagedNative(JNIEnv *env, jobject object,
                                                            jint type, jint repeat, jint shuffle){
    bt_status_t status;
    btrc_register_notification_t param;
    ALOGI("%s: sBluetoothAvrcpInterface: %p", __FUNCTION__, sBluetoothAvrcpInterface);
    if (!sBluetoothAvrcpInterface) return JNI_FALSE;

    memset(&param, 0, sizeof(btrc_player_settings_t));
    param.player_setting.num_attr = 2;
    param.player_setting.attr_ids[0] = BTRC_PLAYER_ATTR_REPEAT;
    param.player_setting.attr_values[0] = repeat;
    param.player_setting.attr_ids[1] = BTRC_PLAYER_ATTR_SHUFFLE;
    param.player_setting.attr_values[1] = shuffle;
    ALOGD("%s: Repeat: %d , Shuffle: %d" , __FUNCTION__,
         param.player_setting.attr_values[0],
         param.player_setting.attr_values[1]);

        if ((status = sBluetoothAvrcpInterface->register_notification_rsp(
            BTRC_EVT_APP_SETTINGS_CHANGED,
                  (btrc_notification_type_t)type, &param)) != BT_STATUS_SUCCESS) {
        ALOGE("Failed register_notification_rsp App Settings, status: %d", status);
    }
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

//BRCM PlayerSettings --

//BRCM AVRCP 1.4 Enhancements ++
static jboolean getFolderItemsMediaRspNative(JNIEnv *env, jobject object, jint rsp_status,
                                            jint uid_counter,jbyte item_type,jint num_items,
                                            jbyteArray PlayerTypes,jintArray PlayerSubTypes,
                                            jbyteArray PlayStatusValues,
                                            jshortArray FeatureBitMaskValues,jobjectArray textArray) {
    bt_status_t status;
    btrc_folder_items_t *p_items=NULL;
    int OutIndex=0, InnCnt=0, j =0;
    jbyte* p_playerTypes=NULL,*p_PlayStatusValues=NULL;
    jshort *p_FeatBitMaskValues=NULL;
    jint *p_playerSubTypes=NULL;
    jstring text;
    const char *textStr;

    ALOGI("%s: sBluetoothAvrcpInterface: %p", __FUNCTION__, sBluetoothAvrcpInterface);

    p_playerTypes = env->GetByteArrayElements(PlayerTypes, NULL);
    if (!p_playerTypes) {
        jniThrowIOException(env, EINVAL);
        return JNI_FALSE;
    }

    p_playerSubTypes = env->GetIntArrayElements(PlayerSubTypes, NULL);
    if (!p_playerSubTypes) {
        env->ReleaseByteArrayElements(PlayerTypes, p_playerTypes, 0);
        jniThrowIOException(env, EINVAL);
        return JNI_FALSE;
    }

    p_PlayStatusValues = env->GetByteArrayElements(PlayStatusValues, NULL);
    if (!p_PlayStatusValues) {
        env->ReleaseByteArrayElements(PlayerTypes, p_playerTypes, 0);
        env->ReleaseIntArrayElements(PlayerSubTypes, p_playerSubTypes, 0);
        jniThrowIOException(env, EINVAL);
        return JNI_FALSE;
    }

    p_FeatBitMaskValues = env->GetShortArrayElements(FeatureBitMaskValues, NULL);
    if (!p_FeatBitMaskValues) {
         env->ReleaseByteArrayElements(PlayerTypes, p_playerTypes, 0);
         env->ReleaseIntArrayElements(PlayerSubTypes, p_playerSubTypes, 0);
         env->ReleaseByteArrayElements(PlayStatusValues, p_PlayStatusValues, 0);
         jniThrowIOException(env, EINVAL);
         return JNI_FALSE;
    }

    p_items = new btrc_folder_items_t[num_items];
    if (!p_items) {
        env->ReleaseByteArrayElements(PlayerTypes, p_playerTypes, 0);
        env->ReleaseIntArrayElements(PlayerSubTypes, p_playerSubTypes, 0);
        env->ReleaseByteArrayElements(PlayStatusValues, p_PlayStatusValues, 0);
        env->ReleaseShortArrayElements(FeatureBitMaskValues, p_FeatBitMaskValues, 0);
        ALOGE("%s: not have enough memory", __FUNCTION__);
        return JNI_FALSE;
    }

    p_items->item_type=(uint8_t)item_type;

    for (OutIndex=0; OutIndex < num_items; ++OutIndex) {
        p_items[OutIndex].player_item.player_id = (uint16_t)(OutIndex+1);
        p_items[OutIndex].player_item.player_type = p_playerTypes[OutIndex];
        p_items[OutIndex].player_item.player_subtype = p_playerSubTypes[OutIndex];
        p_items[OutIndex].player_item.play_status = p_PlayStatusValues[OutIndex];
        p_items[OutIndex].player_item.char_set_id = 0x006A;
        text = (jstring) env->GetObjectArrayElement(textArray, OutIndex);
        textStr = env->GetStringUTFChars(text, NULL);
        if (!textStr) {
            ALOGE("%s: GetStringUTFChars return NULL",__FUNCTION__);
            env->DeleteLocalRef(text);
            break;
        }

        if (strlen(textStr) >= BTRC_MAX_ATTR_STR_LEN) {
            ALOGE("%s: string length exceed maximum", __FUNCTION__);
            strncpy((char *)p_items[OutIndex].player_item.text, textStr, BTRC_MAX_ATTR_STR_LEN-1);
            p_items[OutIndex].player_item.text[BTRC_MAX_ATTR_STR_LEN-1] = 0;
        } else {
            strcpy((char *)p_items[OutIndex].player_item.text, textStr);
            ALOGI("%s: Player name: %s, Length: %d, Original:%s",
                __FUNCTION__,p_items[OutIndex].player_item.text,
                strlen((char*)p_items[OutIndex].player_item.text),textStr);
        }
        env->ReleaseStringUTFChars(text, textStr);
        env->DeleteLocalRef(text);

        // Feature bit mask is 128-bit value each
        for (InnCnt=0; InnCnt < 16; InnCnt ++) {
            p_items[OutIndex].player_item.feature_bit_mask[InnCnt]
                = (uint8_t)p_FeatBitMaskValues[InnCnt];
//          ALOGI("%s: FeaturebitMaskValue: %x,%x", __FUNCTION__,
//              p_items[OutIndex].player_item.feature_bit_mask[InnCnt],p_FeatBitMaskValues[InnCnt]);
        }
   }

    if (OutIndex < num_items) {
        delete[] p_items;
        env->ReleaseByteArrayElements(PlayerTypes, p_playerTypes, 0);
        env->ReleaseIntArrayElements(PlayerSubTypes, p_playerSubTypes, 0);
        env->ReleaseByteArrayElements(PlayStatusValues, p_PlayStatusValues, 0);
        env->ReleaseShortArrayElements(FeatureBitMaskValues, p_FeatBitMaskValues, 0);
        return JNI_FALSE;
    }

    ALOGI("%s: Item Type: %d", __FUNCTION__,p_items->item_type);
    if ((status = sBluetoothAvrcpInterface->get_folder_items_list_rsp((btrc_status_t)rsp_status,
                  uid_counter,num_items,p_items)) != BT_STATUS_SUCCESS) {
            ALOGE("Failed get_folder_items_list_rsp, status: %d", status);
    }

    delete[] p_items;
    env->ReleaseByteArrayElements(PlayerTypes, p_playerTypes, 0);
    env->ReleaseIntArrayElements(PlayerSubTypes, p_playerSubTypes, 0);
    env->ReleaseByteArrayElements(PlayStatusValues, p_PlayStatusValues, 0);
    env->ReleaseShortArrayElements(FeatureBitMaskValues, p_FeatBitMaskValues, 0);

    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean setAddressedPlayerRspNative(JNIEnv *env, jobject object, jint rsp_status) {
    bt_status_t status;

    ALOGI("%s: sBluetoothAvrcpInterface: %p", __FUNCTION__, sBluetoothAvrcpInterface);
    if (!sBluetoothAvrcpInterface) return JNI_FALSE;

    if ((status = sBluetoothAvrcpInterface->set_addressed_player_rsp((btrc_status_t)rsp_status))
        != BT_STATUS_SUCCESS) {
          ALOGE("Failed set_addressed_player_rsp, status: %d", status);
    }
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}
//BRCM AVRCP 1.4 Enhancements --

static JNINativeMethod sMethods[] = {
    {"classInitNative", "()V", (void *) classInitNative},
    {"initNative", "()V", (void *) initNative},
    {"cleanupNative", "()V", (void *) cleanupNative},
    {"getPlayStatusRspNative", "(III)Z", (void *) getPlayStatusRspNative},
    {"getElementAttrRspNative", "(B[I[Ljava/lang/String;)Z", (void *) getElementAttrRspNative},
    {"registerNotificationRspPlayStatusNative", "(II)Z",
     (void *) registerNotificationRspPlayStatusNative},
    {"registerNotificationRspTrackChangeNative", "(I[B)Z",
     (void *) registerNotificationRspTrackChangeNative},
    {"registerNotificationRspPlayPosNative", "(II)Z",
     (void *) registerNotificationRspPlayPosNative},
    {"setVolumeNative", "(I)Z",
     (void *) setVolumeNative},
    //BRCM PlayerSettings ++
    {"registerNotificationRspAppSettingsChagedNative","(III)Z",
      (void *)registerNotificationRspAppSettingsChagedNative},
    //BRCM PlayerSettings --
    //BRCM RC Enhancements
     {"disconnectRcNative", "([B)Z", (void *) disconnectRcNative},
    //BRCM AVRCP 1.4 Enhancements ++
     {"getFolderItemsMediaRspNative", "(IIBI[B[I[B[S[Ljava/lang/String;)Z",
     (void *) getFolderItemsMediaRspNative},
     {"setAddressedPlayerRspNative", "(I)Z",
     (void *) setAddressedPlayerRspNative},
     {"registerNotificationRspAddrPlayerChangedNative", "(III)Z",
     (void *) registerNotificationRspAddrPlayerChangedNative},
     {"registerNotificationRspAvalPlayerChangedNative", "(I)Z",
     (void *) registerNotificationRspAvalPlayerChangedNative},
    //BRCM AVRCP 1.4 Enhancements --
};

int register_com_android_bluetooth_avrcp(JNIEnv* env) {
    return jniRegisterNativeMethods(env, "com/android/bluetooth/a2dp/Avrcp",
                                    sMethods, NELEM(sMethods));
}

}
