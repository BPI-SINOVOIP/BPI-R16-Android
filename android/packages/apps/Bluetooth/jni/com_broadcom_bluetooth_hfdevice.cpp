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

#define LOG_TAG "BluetoothHfDeviceServiceJni"

#define LOG_NDEBUG 0

#define CHECK_CALLBACK_ENV                                                      \
   if (!checkCallbackThread()) {                                                \
       ALOGE("Callback: '%s' is not called on the correct thread", __FUNCTION__);\
       return;                                                                  \
   }

#include "com_android_bluetooth.h"
#include "hardware/bt_hfdevice.h"
#include "utils/Log.h"
#include "android_runtime/AndroidRuntime.h"

#include <string.h>
#include <ctype.h>
#include <stdio.h>


namespace android {

static jmethodID method_onConnectionStateChanged;
static jmethodID method_onAudioStateChanged;
static jmethodID method_onDeviceStatusChanged;
static jmethodID method_onCallStatusChanged;
static jmethodID method_onClipEvent;
static jmethodID method_onCcwaEvent;
static jmethodID method_onClccEvent;
static jmethodID method_onVndAtCmdEvent;
static jmethodID method_onVolumeEvent;
static jmethodID method_onCnumEvent;
static jmethodID method_onCopsEvent;
static jmethodID method_onVREvent;
static jmethodID method_onWbsEvent;
static jmethodID method_onRingEvent;
static jmethodID method_onInBandRingStatus;
static jmethodID method_onBIAStatus;


static const bthfdevice_interface_t *sBluetoothHfDeviceInterface = NULL;
static jobject mCallbacksObj = NULL;
static JNIEnv *sCallbackEnv = NULL;

#define AT_STR_MAX_LEN           255


static size_t copyandtrimwhitespace(char *out, size_t len, const char *str)
{
    if(len == 0)
    return 0;

    const char *end;
    size_t out_size;

    // Trim leading space
    while(isspace(*str)) str++;

    if(*str == 0)  // All spaces?
    {
    *out = 0;
    return 1;
    }

    // Trim trailing space
    end = str + strlen(str) - 1;
    while(end > str && isspace(*end)) end--;
    end++;

    // Set output size to minimum of trimmed string length and buffer size minus 1
    out_size = ((end - str) < len-1) ? (end - str) : len-1;

    // Copy trimmed string and add null terminator
    memcpy(out, str, out_size);
    out[out_size] = 0;

    return out_size;
}

#define JNI_CALL_METHOD_WITH_STRING(method_name, string) \
{ \
    jstring  str;\
    char     c_str[AT_STR_MAX_LEN+1];\
    if (string)\
    {\
        memset(c_str, 0, sizeof(c_str));\
        copyandtrimwhitespace(c_str, AT_STR_MAX_LEN, string);\
        str = sCallbackEnv->NewStringUTF(c_str);\
        sCallbackEnv->CallVoidMethod(mCallbacksObj, method_name, str);\
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);\
        sCallbackEnv->DeleteLocalRef(str);\
    }\
    else\
    {\
        sCallbackEnv->CallVoidMethod(mCallbacksObj, method_name, NULL);\
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);\
    }\
}


#define JNI_CALL_METHOD_WITH_INT_STRING(method_name, status, string) \
{ \
    jstring  str;\
    char     c_str[AT_STR_MAX_LEN+1];\
    if (string)\
    {\
        memset(c_str, 0, sizeof(c_str));\
        copyandtrimwhitespace(c_str, AT_STR_MAX_LEN, string);\
        str = sCallbackEnv->NewStringUTF(c_str);\
        sCallbackEnv->CallVoidMethod(mCallbacksObj, method_name, status, str);\
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);\
        sCallbackEnv->DeleteLocalRef(str);\
    }\
    else\
    {\
        sCallbackEnv->CallVoidMethod(mCallbacksObj, method_name, status, NULL);\
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);\
    }\
}


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

static void connection_state_callback(bthfdevice_connection_state_t state, bt_bdaddr_t* bd_addr,
                                          int peer_features,int local_features) {
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
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onConnectionStateChanged,
                                 (jint) state, addr, peer_features, local_features);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    sCallbackEnv->DeleteLocalRef(addr);
}

static void audio_state_callback(bthfdevice_audio_state_t state, bt_bdaddr_t* bd_addr) {
    jbyteArray addr;

    CHECK_CALLBACK_ENV
    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (!addr) {
        ALOGE("Fail to new jbyteArray bd addr for audio state");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }

    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte *) bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAudioStateChanged, (jint) state, addr);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    sCallbackEnv->DeleteLocalRef(addr);
}

static void device_status_ind_callback(bthfdevice_service_t svc,bthfdevice_service_type_t svc_type,
                                           int signal, int batt_chg)
{
    CHECK_CALLBACK_ENV

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onDeviceStatusChanged, (jint) svc,
                                 (jint) svc_type, (jint) signal, (jint) batt_chg);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

static void call_status_ind_callback(int num_active,bthfdevice_call_state_t call_setup,
                                        int num_held)
{
    CHECK_CALLBACK_ENV

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onCallStatusChanged, (jint) num_active,
                                 (jint) call_setup, (jint) num_held);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}


static void clip_callback(char* clip_str)
{
    CHECK_CALLBACK_ENV

    ALOGD("%s", clip_str);

    JNI_CALL_METHOD_WITH_STRING(method_onClipEvent, clip_str);

}

static void ccwa_callback(char* ccwa_str)
{
    CHECK_CALLBACK_ENV

    JNI_CALL_METHOD_WITH_STRING(method_onCcwaEvent, ccwa_str);

}

static void clcc_callback(bthfdevice_error_code_t code, char* clcc_str)
{
    CHECK_CALLBACK_ENV

    JNI_CALL_METHOD_WITH_INT_STRING(method_onClccEvent, code, clcc_str);

}


static void vnd_at_cmd_callback(bthfdevice_error_code_t code,
                                                            char* vnd_at_str)
{
    CHECK_CALLBACK_ENV

    JNI_CALL_METHOD_WITH_INT_STRING(method_onVndAtCmdEvent, code, vnd_at_str);

}

static void volume_callback(bthfdevice_volume_type_t vol_type, int volume)
{
    CHECK_CALLBACK_ENV

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onVolumeEvent, (jint) vol_type, (jint) volume);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}


static void cnum_callback(bthfdevice_error_code_t code, char* cnum_str)
{
    CHECK_CALLBACK_ENV

    JNI_CALL_METHOD_WITH_INT_STRING(method_onCnumEvent, code, cnum_str);

}

static void cops_callback(bthfdevice_error_code_t code, char* cops_str)
{
    CHECK_CALLBACK_ENV

    JNI_CALL_METHOD_WITH_INT_STRING(method_onCopsEvent, code, cops_str);

}



static void vr_state_callback(int status, bthfdevice_vr_state_t vrstate)
{
    CHECK_CALLBACK_ENV

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onVREvent, (jint)status, (jint) vrstate);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

static void wbs_state_callback( bthfdevice_wbs_config_t wbsState)
{
    CHECK_CALLBACK_ENV

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onWbsEvent, (jint) wbsState);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

static void ring_callback()
{
    CHECK_CALLBACK_ENV

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onRingEvent);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

static void inband_ring_setting_callback(bthfdevice_inband_ring_status_t inBandstate)
{
    CHECK_CALLBACK_ENV

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onInBandRingStatus, (jint) inBandstate);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

static void bia_status_callback( int status)
{
    CHECK_CALLBACK_ENV

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onBIAStatus,
                    (jint) status);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}


static bthfdevice_callbacks_t sBluetoothHfDeviceCallbacks = {
    sizeof(sBluetoothHfDeviceCallbacks),
    connection_state_callback,
    audio_state_callback,
    vr_state_callback,
    volume_callback,
    device_status_ind_callback,
    call_status_ind_callback,
    clip_callback,
    ccwa_callback,
    cnum_callback,
    cops_callback,
    clcc_callback,
    vnd_at_cmd_callback,
    wbs_state_callback,
    ring_callback,
    inband_ring_setting_callback,
    bia_status_callback,
};

static void classInitNative(JNIEnv* env, jclass clazz) {

    method_onConnectionStateChanged =
        env->GetMethodID(clazz, "onConnectionStateChanged", "(I[BII)V");
    method_onAudioStateChanged = env->GetMethodID(clazz, "onAudioStateChanged", "(I[B)V");

    method_onDeviceStatusChanged =
        env->GetMethodID(clazz, "onDeviceStatusChanged", "(IIII)V");

    method_onCallStatusChanged =
        env->GetMethodID(clazz, "onCallStatusChanged", "(III)V");

    method_onClipEvent =
        env->GetMethodID(clazz, "onClipEvent", "(Ljava/lang/String;)V");


    method_onCcwaEvent =
        env->GetMethodID(clazz, "onCcwaEvent", "(Ljava/lang/String;)V");

    method_onClccEvent =
        env->GetMethodID(clazz, "onClccEvent", "(ILjava/lang/String;)V");

    method_onVndAtCmdEvent =
        env->GetMethodID(clazz, "onVndAtCmdEvent", "(ILjava/lang/String;)V");

    method_onVolumeEvent =
               env->GetMethodID(clazz, "onVolumeEvent", "(II)V");;

    method_onCnumEvent =
        env->GetMethodID(clazz, "onCnumEvent", "(ILjava/lang/String;)V");

    method_onCopsEvent =
        env->GetMethodID(clazz, "onCopsEvent", "(ILjava/lang/String;)V");

    method_onVREvent =
               env->GetMethodID(clazz, "onVREvent", "(II)V");

    method_onWbsEvent =
               env->GetMethodID(clazz, "onWBSEvent", "(I)V");

    method_onRingEvent =
               env->GetMethodID(clazz, "onRingEvent", "()V");

    method_onInBandRingStatus =
               env->GetMethodID(clazz, "onInBandRingStatusEvent", "(I)V");
    method_onBIAStatus =
               env->GetMethodID(clazz, "onBIAStatus", "(I)V");

    ALOGI("%s: succeeds", __FUNCTION__);
}

static void initializeNative(JNIEnv *env, jobject object) {
    const bt_interface_t* btInf;
    bt_status_t status;

    if ( (btInf = getBluetoothInterface()) == NULL) {
        ALOGE("Bluetooth module is not loaded");
        return;
    }

    if (sBluetoothHfDeviceInterface !=NULL) {
        ALOGW("Cleaning up Bluetooth Handsfree Interface before initializing...");
        sBluetoothHfDeviceInterface->cleanup();
        sBluetoothHfDeviceInterface = NULL;
    }

    if (mCallbacksObj != NULL) {
        ALOGW("Cleaning up Bluetooth Handsfree callback object");
        env->DeleteGlobalRef(mCallbacksObj);
        mCallbacksObj = NULL;
    }

    if ( (sBluetoothHfDeviceInterface = (bthfdevice_interface_t *)
          btInf->get_profile_interface(BT_PROFILE_HF_DEVICE_ID)) == NULL) {
        ALOGE("Failed to get Bluetooth HF Device Interface");
        return;
    }

    if ( (status = sBluetoothHfDeviceInterface->init(&sBluetoothHfDeviceCallbacks))
                                                            != BT_STATUS_SUCCESS) {
        ALOGE("Failed to initialize Bluetooth HF Device, status: %d", status);
        sBluetoothHfDeviceInterface = NULL;
        return;
    }

    mCallbacksObj = env->NewGlobalRef(object);
}

static void cleanupNative(JNIEnv *env, jobject object) {
    const bt_interface_t* btInf;
    bt_status_t status;

    if ( (btInf = getBluetoothInterface()) == NULL) {
        ALOGE("Bluetooth module is not loaded");
        return;
    }

    if (sBluetoothHfDeviceInterface !=NULL) {
        ALOGW("Cleaning up Bluetooth Handsfree Interface...");
        sBluetoothHfDeviceInterface->cleanup();
        sBluetoothHfDeviceInterface = NULL;
    }

    if (mCallbacksObj != NULL) {
        ALOGW("Cleaning up Bluetooth Handsfree callback object");
        env->DeleteGlobalRef(mCallbacksObj);
        mCallbacksObj = NULL;
    }
}

static jboolean connectNative(JNIEnv *env, jobject object, jbyteArray address) {
    jbyte *addr;
    bt_status_t status;

    ALOGI("%s: sBluetoothHfDeviceInterface: %p", __FUNCTION__, sBluetoothHfDeviceInterface);
    if (!sBluetoothHfDeviceInterface) return JNI_FALSE;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return JNI_FALSE;
    }

    if ((status = sBluetoothHfDeviceInterface->connect((bt_bdaddr_t *)addr))
                                                                != BT_STATUS_SUCCESS) {
        ALOGE("Failed HF connection, status: %d", status);
    }
    env->ReleaseByteArrayElements(address, addr, 0);
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean disconnectNative(JNIEnv *env, jobject object, jbyteArray address) {
    jbyte *addr;
    bt_status_t status;

    if (!sBluetoothHfDeviceInterface) return JNI_FALSE;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return JNI_FALSE;
    }

    if ( (status = sBluetoothHfDeviceInterface->disconnect((bt_bdaddr_t *)addr))
                                                              != BT_STATUS_SUCCESS) {
        ALOGE("Failed HF disconnection, status: %d", status);
    }
    env->ReleaseByteArrayElements(address, addr, 0);
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean connectAudioNative(JNIEnv *env, jobject object, jbyteArray address) {
    jbyte *addr;
    bt_status_t status;

    if (!sBluetoothHfDeviceInterface) return JNI_FALSE;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return JNI_FALSE;
    }

    if ( (status = sBluetoothHfDeviceInterface->connect_audio((bt_bdaddr_t *)addr))
                                                              != BT_STATUS_SUCCESS) {
        ALOGE("Failed HF audio connection, status: %d", status);
    }
    env->ReleaseByteArrayElements(address, addr, 0);
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean disconnectAudioNative(JNIEnv *env, jobject object, jbyteArray address) {
    jbyte *addr;
    bt_status_t status;

    if (!sBluetoothHfDeviceInterface) return JNI_FALSE;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return JNI_FALSE;
    }

    if ( (status = sBluetoothHfDeviceInterface->disconnect_audio((bt_bdaddr_t *) addr))
                                                              != BT_STATUS_SUCCESS) {
        ALOGE("Failed HF audio disconnection, status: %d", status);
    }
    env->ReleaseByteArrayElements(address, addr, 0);
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean answerNative(JNIEnv *env, jobject object) {
    bt_status_t status;

    if (!sBluetoothHfDeviceInterface) return JNI_FALSE;

    if ( (status = sBluetoothHfDeviceInterface->answer()) != BT_STATUS_SUCCESS) {
        ALOGE("Failed HF call answer , status: %d", status);
    }
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean hangupNative(JNIEnv *env, jobject object) {
    bt_status_t status;

    if (!sBluetoothHfDeviceInterface) return JNI_FALSE;

    if ( (status = sBluetoothHfDeviceInterface->hangup()) != BT_STATUS_SUCCESS) {
        ALOGE("Failed HF call hangup , status: %d", status);
    }
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean dialNative(JNIEnv* env, jobject object, jstring number)
{
    bt_status_t status;
    char       *c_number = NULL;

    if (!sBluetoothHfDeviceInterface) return JNI_FALSE;

    if (number) {
        c_number = (char *)env->GetStringUTFChars(number, NULL);
    }

    ALOGV("%s: number = %s", __FUNCTION__, c_number);
    if ( (status = sBluetoothHfDeviceInterface->dial(c_number)) != BT_STATUS_SUCCESS) {
         ALOGE("Failed HF call dial , status: %d", status);
    }
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;

}

static jboolean sendClccNative(JNIEnv *env, jobject object) {
    bt_status_t status;

    if (!sBluetoothHfDeviceInterface) return JNI_FALSE;

    if ( (status = sBluetoothHfDeviceInterface->send_clcc_cmd()) != BT_STATUS_SUCCESS) {
        ALOGE("Failed HF call hangup , status: %d", status);
    }
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean holdNative(JNIEnv *env, jobject object, jint hold_type)
{

    bt_status_t status;

    if (!sBluetoothHfDeviceInterface) return JNI_FALSE;

    if ( (status = sBluetoothHfDeviceInterface->hold((bthfdevice_chld_type_t)hold_type)) !=
                BT_STATUS_SUCCESS) {
        ALOGE("Failed HF call answer , status: %d", status);
    }
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;

}

static jboolean sendVndATCmdNative(JNIEnv* env, jobject object, jstring vnd_cmd_string)
{
    bt_status_t status;
    char       *c_vnd_cmd = NULL;

    if (!sBluetoothHfDeviceInterface) return JNI_FALSE;

    if (vnd_cmd_string) {
        c_vnd_cmd = (char *)env->GetStringUTFChars(vnd_cmd_string, NULL);
    }

    ALOGV("%s: number = %s", __FUNCTION__, c_vnd_cmd);
    if ( (status = sBluetoothHfDeviceInterface->send_unknown_at_cmd(c_vnd_cmd))
            != BT_STATUS_SUCCESS) {
         ALOGE("Failed sending vendor ATcommand , status: %d", status);
    }
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;

}

static jboolean setVolumeNative(JNIEnv *env, jobject object, jint vol_type, jint volume)
{

    bt_status_t status;

    if (!sBluetoothHfDeviceInterface) return JNI_FALSE;

    if ( (status = sBluetoothHfDeviceInterface->set_volume
                ((bthfdevice_volume_type_t)vol_type, (int)volume)) !=
                BT_STATUS_SUCCESS) {
        ALOGE("Failed to setVolume , status: %d", status);
    }
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;

}

static jboolean sendCopsNative(JNIEnv *env, jobject object) {
    bt_status_t status;

    if (!sBluetoothHfDeviceInterface) return JNI_FALSE;

    if ( (status = sBluetoothHfDeviceInterface->send_cops_cmd()) != BT_STATUS_SUCCESS) {
        ALOGE("Failed HF call hangup , status: %d", status);
    }
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean sendCnumNative(JNIEnv *env, jobject object) {
    bt_status_t status;

    if (!sBluetoothHfDeviceInterface) return JNI_FALSE;

    if ( (status = sBluetoothHfDeviceInterface->send_cnum_cmd()) != BT_STATUS_SUCCESS) {
        ALOGE("Failed HF call hangup , status: %d", status);
    }
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean sendDTMFNative(JNIEnv *env, jobject object, jchar dtmf_code)
{

    bt_status_t status;

    if (!sBluetoothHfDeviceInterface) return JNI_FALSE;

    ALOGD("sendDTMFNative = %c",(char)dtmf_code);
    if ( (status = sBluetoothHfDeviceInterface->send_dtmf_cmd((char)dtmf_code)) !=
                BT_STATUS_SUCCESS) {
        ALOGE("Failed HF call answer , status: %d", status);
    }
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;

}

static jboolean startVoiceRecognitionNative(JNIEnv *env, jobject object) {
    bt_status_t status;
    if (!sBluetoothHfDeviceInterface) return JNI_FALSE;

    if ( (status = sBluetoothHfDeviceInterface->start_voice_recognition()) != BT_STATUS_SUCCESS) {
        ALOGE("Failed to start voice recognition, status: %d", status);
    }
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean stopVoiceRecognitionNative(JNIEnv *env, jobject object) {
    bt_status_t status;
    if (!sBluetoothHfDeviceInterface) return JNI_FALSE;

    if ( (status = sBluetoothHfDeviceInterface->stop_voice_recognition()) != BT_STATUS_SUCCESS) {
        ALOGE("Failed to stop voice recognition, status: %d", status);
    }
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean configPbDownloadMode(JNIEnv *env, jobject object, jint mode) {
    bt_status_t status;
    if (!sBluetoothHfDeviceInterface) return JNI_FALSE;

    if ( (status =sBluetoothHfDeviceInterface->config_pb_download_mode
        ((bthfdevice_pb_download_mode_t)mode)) != BT_STATUS_SUCCESS) {
        ALOGE("Failed to stop voice recognition, status: %d", status);
    }
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}


static jboolean sendKeyPressEventNative(JNIEnv *env, jobject object) {
    bt_status_t status;

    if (!sBluetoothHfDeviceInterface) return JNI_FALSE;

    if ( (status = sBluetoothHfDeviceInterface->send_key_pressed_event_cmd())
        != BT_STATUS_SUCCESS) {
        ALOGE("Failed HF call hangup , status: %d", status);
    }
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean sendBIANative(JNIEnv *env, jobject object,
                jint enable_roam, jint enable_service, jint enable_signal, jint enable_battery) {
    bt_status_t status;
    if (!sBluetoothHfDeviceInterface) return JNI_FALSE;

    if ( (status =sBluetoothHfDeviceInterface->send_bia(enable_roam, enable_service,
        enable_signal, enable_battery)) != BT_STATUS_SUCCESS) {
        ALOGE("Failed to Activate indicators, status: %d", status);
    }
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static JNINativeMethod sMethods[] = {
    {"classInitNative", "()V", (void *) classInitNative},
    {"initializeNative", "()V", (void *) initializeNative},
    {"cleanupNative", "()V", (void *) cleanupNative},
    {"connectNative", "([B)Z", (void *) connectNative},
    {"disconnectNative", "([B)Z", (void *) disconnectNative},
    {"connectAudioNative", "([B)Z", (void *) connectAudioNative},
    {"disconnectAudioNative", "([B)Z", (void *) disconnectAudioNative},
    {"answerNative", "()Z", (void *) answerNative},
    {"hangupNative", "()Z", (void *) hangupNative},
    {"dialNative", "(Ljava/lang/String;)Z", (void *) dialNative},
    {"sendClccNative", "()Z", (void *) sendClccNative},
    {"holdNative", "(I)Z", (void *) holdNative},
    {"sendVndATCmdNative", "(Ljava/lang/String;)Z", (void *) sendVndATCmdNative},
    {"setVolumeNative", "(II)Z", (void *) setVolumeNative},
    {"sendCopsNative", "()Z", (void *) sendCopsNative},
    {"sendCnumNative", "()Z", (void *) sendCnumNative},
    {"sendDTMFNative", "(C)Z", (void *) sendDTMFNative},
    {"startVoiceRecognitionNative", "()Z", (void *) startVoiceRecognitionNative},
    {"stopVoiceRecognitionNative", "()Z", (void *) stopVoiceRecognitionNative},
    //TODO remove this api as sniff mode is handled stack
    {"configPbDownloadMode", "(I)Z", (void *) configPbDownloadMode},
    {"sendKeyPressEventNative", "()Z", (void *) sendKeyPressEventNative},
    {"sendBIANative", "(IIII)Z", (void *) sendBIANative},
};


int register_com_broadcom_bluetooth_hfdevice(JNIEnv* env)
{
    return jniRegisterNativeMethods(env, "com/broadcom/bt/service/hfdevice/HfDeviceStateMachine",
                                    sMethods, NELEM(sMethods));
}

} /* namespace android */
