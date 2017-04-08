/* Copyright 2013 Broadcom Corporation
 **
 ** This program is the proprietary software of Broadcom Corporation and/or its
 ** licensors, and may only be used, duplicated, modified or distributed
 ** pursuant to the terms and conditions of a separate, written license
 ** agreement executed between you and Broadcom (an "Authorized License").
 ** Except as set forth in an Authorized License, Broadcom grants no license
 ** (express or implied), right to use, or waiver of any kind with respect to
 ** the Software, and Broadcom expressly reserves all rights in and to the
 ** Software and all intellectual property rights therein.
 ** IF YOU HAVE NO AUTHORIZED LICENSE, THEN YOU HAVE NO RIGHT TO USE THIS
 ** SOFTWARE IN ANY WAY, AND SHOULD IMMEDIATELY NOTIFY BROADCOM AND DISCONTINUE
 ** ALL USE OF THE SOFTWARE.
 **
 ** Except as expressly set forth in the Authorized License,
 **
 ** 1.     This program, including its structure, sequence and organization,
 **        constitutes the valuable trade secrets of Broadcom, and you shall
 **        use all reasonable efforts to protect the confidentiality thereof,
 **        and to use this information only in connection with your use of
 **        Broadcom integrated circuit products.
 **
 ** 2.     TO THE MAXIMUM EXTENT PERMITTED BY LAW, THE SOFTWARE IS PROVIDED
 **        "AS IS" AND WITH ALL FAULTS AND BROADCOM MAKES NO PROMISES,
 **        REPRESENTATIONS OR WARRANTIES, EITHER EXPRESS, IMPLIED, STATUTORY,
 **        OR OTHERWISE, WITH RESPECT TO THE SOFTWARE.  BROADCOM SPECIFICALLY
 **        DISCLAIMS ANY AND ALL IMPLIED WARRANTIES OF TITLE, MERCHANTABILITY,
 **        NONINFRINGEMENT, FITNESS FOR A PARTICULAR PURPOSE, LACK OF VIRUSES,
 **        ACCURACY OR COMPLETENESS, QUIET ENJOYMENT, QUIET POSSESSION OR
 **        CORRESPONDENCE TO DESCRIPTION. YOU ASSUME THE ENTIRE RISK ARISING OUT
 **        OF USE OR PERFORMANCE OF THE SOFTWARE.
 **
 ** 3.     TO THE MAXIMUM EXTENT PERMITTED BY LAW, IN NO EVENT SHALL BROADCOM OR
 **        ITS LICENSORS BE LIABLE FOR
 **        (i)   CONSEQUENTIAL, INCIDENTAL, SPECIAL, INDIRECT, OR EXEMPLARY
 **              DAMAGES WHATSOEVER ARISING OUT OF OR IN ANY WAY RELATING TO
 **              YOUR USE OF OR INABILITY TO USE THE SOFTWARE EVEN IF BROADCOM
 **              HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES; OR
 **        (ii)  ANY AMOUNT IN EXCESS OF THE AMOUNT ACTUALLY PAID FOR THE
 **              SOFTWARE ITSELF OR U.S. $1, WHICHEVER IS GREATER. THESE
 **              LIMITATIONS SHALL APPLY NOTWITHSTANDING ANY FAILURE OF
 **              ESSENTIAL PURPOSE OF ANY LIMITED REMEDY.
 */
#define LOG_TAG "BluetoothHIDDeviceServiceJni"

#define LOG_NDEBUG 0

#define CHECK_CALLBACK_ENV                                                      \
   if (!checkCallbackThread()) {                                                \
       error("Callback:'%s' is not called on the correct thread", __FUNCTION__);\
       return;                                                                  \
   }                                                                            \
   if(mCallbacksObj == NULL) {                                                  \
        error("Callback: mCallbacksObj is null in %s()", __FUNCTION__);         \
        return;                                                                 \
    }

#include "com_android_bluetooth.h"
#include "hardware/bt_hd.h"
#include "utils/Log.h"
#include "android_runtime/AndroidRuntime.h"

#include <string.h>

#define info(fmt, ...)  ALOGI ("%s(L%d): " fmt,__FUNCTION__, __LINE__,  ## __VA_ARGS__)
#define debug(fmt, ...) ALOGD ("%s(L%d): " fmt,__FUNCTION__, __LINE__,  ## __VA_ARGS__)
#define warn(fmt, ...) ALOGW ("## WARNING : %s(L%d): " fmt "##",__FUNCTION__, __LINE__, ## __VA_ARGS__)
#define error(fmt, ...) ALOGE ("## ERROR : %s(L%d): " fmt "##",__FUNCTION__, __LINE__, ## __VA_ARGS__)
#define asrt(s) if(!(s)) ALOGE ("## %s(L%d): ASSERT %s failed! ##",__FUNCTION__, __LINE__, #s)
#define BROADCOM_BLUETOOTH_HIDD_SERVICE_CLASS_PATH  "com/broadcom/bt/service/hidd/HidDeviceService"

#define BD_ADDR_LEN 6

namespace android {

static JavaVM *jvm;
static jmethodID method_onConnectStateChanged;
static jmethodID method_onDeviceStatusChanged;
static jmethodID method_onVirtualUnplug;
static jmethodID method_onReportDataReceived;

static const bthd_interface_t *sBluetoothHidDevInterface = NULL;
static jobject mCallbacksObj = NULL;
static JNIEnv *sCallbackEnv = NULL;
static const bt_interface_t* btIf;

static bool checkCallbackThread() {
    sCallbackEnv = getCallbackEnv();

    JNIEnv* env = AndroidRuntime::getJNIEnv();
    if (sCallbackEnv != env || sCallbackEnv == NULL) return false;
    return true;
}

static void connection_state_callback( bt_bdaddr_t *bd_addr,
    bthd_connection_state_t state) {
    jbyteArray addr;
    debug(" %s state:%d", __FUNCTION__, state);
    CHECK_CALLBACK_ENV

    if(mCallbacksObj == NULL) {
         error("%s sCallbacksObj is null", __FUNCTION__);
         return;
     }
    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (!addr) {
        error("Fail to new jbyteArray bd addr for HIDD Connection state callback");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }

    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onConnectStateChanged,
                                 addr, (jint) state);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    sCallbackEnv->DeleteLocalRef(addr);
}

static void device_status_callback(bthd_device_status_t status) {
    debug(" %s status:%d", __FUNCTION__, status);
    CHECK_CALLBACK_ENV

    if(mCallbacksObj == NULL) {
         error("%s sCallbacksObj is null", __FUNCTION__);
         return;
     }
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onDeviceStatusChanged, (jint) status);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

static void virtual_unplug_callback(bt_bdaddr_t *bd_addr, bthd_status_t hd_status) {
    jbyteArray addr;
    debug(" %s hd_status:%d", __FUNCTION__, hd_status);
    CHECK_CALLBACK_ENV

    if(mCallbacksObj == NULL) {
         error("%s sCallbacksObj is null", __FUNCTION__);
         return;
     }
    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (!addr) {
        error("Fail to new jbyteArray bd addr for HIDD Connection state callback");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }

    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onVirtualUnplug,
                                 addr, (jint) hd_status);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    sCallbackEnv->DeleteLocalRef(addr);
}

static void report_received_callback(bt_bdaddr_t *bd_addr, uint8_t* data) {
    jbyteArray addr, report_data;
    size_t len = 0;
    debug(" %s()", __FUNCTION__);
    CHECK_CALLBACK_ENV

    if(mCallbacksObj == NULL) {
         error("%s sCallbacksObj is null", __FUNCTION__);
         return;
     }
    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (!addr) {
        error("Fail to new jbyteArray bd addr for HIDD Connection state callback");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    /* TODO : Determine length of input report */
    report_data = sCallbackEnv->NewByteArray(len*sizeof(uint8_t));
    if (!report_data) {
        error("Fail to new jbyteArray report_data for HIDD Connection state callback");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }

    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
    sCallbackEnv->SetByteArrayRegion(report_data, 0, (len*sizeof(uint8_t)), (jbyte*) data);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onReportDataReceived,
                                 addr, report_data);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    sCallbackEnv->DeleteLocalRef(addr);
    sCallbackEnv->DeleteLocalRef(report_data);
}

static bthd_callbacks_t sBluetoothHidDevCallbacks = {
    sizeof(sBluetoothHidDevCallbacks),
    connection_state_callback,
    device_status_callback,
    virtual_unplug_callback,
    report_received_callback
};

// Define native functions

static void classInitNative(JNIEnv* env, jclass clazz) {
    int err;

    method_onConnectStateChanged = env->GetMethodID(clazz, "onConnectStateChanged", "([BI)V");
    method_onDeviceStatusChanged = env->GetMethodID(clazz, "onDeviceStatusChanged", "(I)V");
    method_onVirtualUnplug = env->GetMethodID(clazz, "onVirtualUnplug", "([BI)V");
    method_onReportDataReceived = env->GetMethodID(clazz, "onReportDataReceived", "([B[B)V");
    ALOGI("%s: succeeds", __FUNCTION__);
}

static void initHidDeviceNative(JNIEnv *env, jobject object) {
    const bt_interface_t* btInf;
    bt_status_t status;

    if ( (btInf = getBluetoothInterface()) == NULL) {
        ALOGE("Bluetooth module is not loaded");
        return;
    }

    if (sBluetoothHidDevInterface !=NULL) {
        ALOGW("Cleaning up Bluetooth HID Device Interface before initializing...");
        sBluetoothHidDevInterface->cleanup();
        sBluetoothHidDevInterface = NULL;
    }

    if (mCallbacksObj != NULL) {
        ALOGW("Cleaning up Bluetooth HID Device callback object");
        env->DeleteGlobalRef(mCallbacksObj);
        mCallbacksObj = NULL;
    }


    if ( (sBluetoothHidDevInterface = (bthd_interface_t *)
          btInf->get_profile_interface(BT_PROFILE_HIDDEVICE_ID)) == NULL) {
        ALOGE("Failed to get Bluetooth HID Device Interface");
        return;
    }

    if ( (status = sBluetoothHidDevInterface->init(&sBluetoothHidDevCallbacks)) !=
        BT_STATUS_SUCCESS) {
        ALOGE("Failed to initialize Bluetooth HID Device, status: %d", status);
        sBluetoothHidDevInterface = NULL;
        return;
    }

    mCallbacksObj = env->NewGlobalRef(object);
}

static void cleanupHidDeviceNative(JNIEnv *env, jobject object) {
    const bt_interface_t* btInf;
    bt_status_t status;

    if ( (btInf = getBluetoothInterface()) == NULL) {
        ALOGE("Bluetooth module is not loaded");
        return;
    }

    if (sBluetoothHidDevInterface !=NULL) {
        ALOGW("Cleaning up Bluetooth HID Device Interface...");
        sBluetoothHidDevInterface->cleanup();
        sBluetoothHidDevInterface = NULL;
    }

    if (mCallbacksObj != NULL) {
        ALOGW("Cleaning up Bluetooth HIDD callback object");
        env->DeleteGlobalRef(mCallbacksObj);
        mCallbacksObj = NULL;
    }

    env->DeleteGlobalRef(mCallbacksObj);
}

static jboolean enableHidDeviceNative(JNIEnv *env, jobject object) {
    bt_status_t status;
    jboolean ret = JNI_TRUE;
    if (!sBluetoothHidDevInterface) return JNI_FALSE;

    if ((status = sBluetoothHidDevInterface->enable()) != BT_STATUS_SUCCESS) {
        ALOGE("Failed HID Device enable, status: %d", status);
        ret = JNI_FALSE;
    } else {
        ALOGI("HID Device enable, status: %d", status);
    }
    return ret;
}

static jboolean disableHidDeviceNative(JNIEnv *env, jobject object) {
    bt_status_t status;
    jboolean ret = JNI_TRUE;
    if (!sBluetoothHidDevInterface) return JNI_FALSE;

    if ((status = sBluetoothHidDevInterface->disable()) != BT_STATUS_SUCCESS) {
        ALOGE("Failed HID Device disable, status: %d", status);
        ret = JNI_FALSE;
    } else {
        ALOGI("HID Device disable, status: %d", status);
    }
    return ret;
}

static jboolean connectNative(JNIEnv *env, jobject object, jbyteArray address) {
    bt_status_t status;
    jbyte *addr;
    jboolean ret = JNI_TRUE;
    if (!sBluetoothHidDevInterface) return JNI_FALSE;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        ALOGE("Bluetooth device address null");
        return JNI_FALSE;
    }

    if ((status = sBluetoothHidDevInterface->connect((bt_bdaddr_t *) addr)) !=
         BT_STATUS_SUCCESS) {
        ALOGE("Failed HID channel connection, status: %d", status);
        ret = JNI_FALSE;
    } else {
        ALOGI("HID Device connect, status: %d", status);
    }
    env->ReleaseByteArrayElements(address, addr, 0);

    return ret;
}

static jboolean disconnectNative(JNIEnv *env, jobject object, jbyteArray address) {
    bt_status_t status;
    jbyte *addr;
    jboolean ret = JNI_TRUE;
    if (!sBluetoothHidDevInterface) return JNI_FALSE;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        ALOGE("Bluetooth device address null");
        return JNI_FALSE;
    }

    if ( (status = sBluetoothHidDevInterface->disconnect((bt_bdaddr_t *) addr)) !=
         BT_STATUS_SUCCESS) {
        ALOGE("Failed disconnect hid channel, status: %d", status);
        ret = JNI_FALSE;
    }
    env->ReleaseByteArrayElements(address, addr, 0);

    return ret;
}

static jboolean virtualUnplugNative(JNIEnv *env, jobject object, jbyteArray address) {
    bt_status_t status;
    jbyte *addr;
    jboolean ret = JNI_TRUE;
    if (!sBluetoothHidDevInterface) return JNI_FALSE;

    addr = env->GetByteArrayElements(address, NULL);
        if (!addr) {
            ALOGE("Bluetooth device address null");
            return JNI_FALSE;
        }
    if ( (status = sBluetoothHidDevInterface->virtual_unplug((bt_bdaddr_t *) addr)) !=
             BT_STATUS_SUCCESS) {
        ALOGE("Failed virual unplug, status: %d", status);
        ret = JNI_FALSE;
    }
    env->ReleaseByteArrayElements(address, addr, 0);
    return ret;

}


static jboolean sendDataNative(JNIEnv *env, jobject object, jbyteArray address, jint report_type,
    jbyteArray report) {
    ALOGD("%s", __FUNCTION__);
    bt_status_t status;
    jbyte *addr, *report_data;
    jboolean ret = JNI_TRUE;
    uint16_t data_len;
    if (!sBluetoothHidDevInterface) return JNI_FALSE;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        ALOGE("Bluetooth device address null");
        return JNI_FALSE;
    }
    report_data = env->GetByteArrayElements(report, NULL);
    if (!report_data) {
        ALOGE("Record data is null");
        return JNI_FALSE;
    }
    data_len = env->GetArrayLength(report);
    if ( (status = sBluetoothHidDevInterface->send_data((bt_bdaddr_t *) addr,
          (bthd_device_type_t) report_type, (uint8_t *) report_data, data_len))
          != BT_STATUS_SUCCESS) {
        ALOGE("Failed set report, status: %d", status);
        ret = JNI_FALSE;
    }
    env->ReleaseByteArrayElements(address, addr, 0);
    env->ReleaseByteArrayElements(report, report_data, 0);
    return ret;
}

static jboolean setSdpRecordNative(JNIEnv *env, jobject object, jbyteArray sdp_record)
{
    ALOGD("%s", __FUNCTION__);
    bt_status_t status;
    jbyte *sdp_data;
    uint16_t data_len;
    jboolean ret = JNI_TRUE;
    if (!sBluetoothHidDevInterface) return JNI_FALSE;

    sdp_data = env->GetByteArrayElements(sdp_record, NULL);
    if (!sdp_data) {
        ALOGE("SDP Record data is null");
        return JNI_FALSE;
    }
    data_len = env->GetArrayLength(sdp_record);

    if ( (status = sBluetoothHidDevInterface->set_sdp_record((uint8_t *) sdp_data, data_len))
        != BT_STATUS_SUCCESS) {
        ALOGE("Failed set report, status: %d", status);
        ret = JNI_FALSE;
    }
    env->ReleaseByteArrayElements(sdp_record, sdp_data, 0);
    return ret;
}

static jboolean clearSdpRecordNative(JNIEnv *env, jobject object) {
    bt_status_t status;
    jboolean ret = JNI_TRUE;
    jbyte *data = NULL;
    if (!sBluetoothHidDevInterface) return JNI_FALSE;

    if ( (status = sBluetoothHidDevInterface->set_sdp_record((uint8_t*) NULL, 0)) !=
             BT_STATUS_SUCCESS) {
        ALOGE("Failed clear SDP Record, status: %d", status);
        ret = JNI_FALSE;
    }
    return ret;

}

static JNINativeMethod sMethods[] = {
    /* name, signature, funcPtr */
    {"classInitNative",         "()V",      (void *)classInitNative},
    {"initHidDeviceNative",     "()V",      (void *)initHidDeviceNative},
    {"cleanupHidDeviceNative",  "()V",      (void *)cleanupHidDeviceNative},
    {"enableHidDeviceNative",   "()Z",      (void *)enableHidDeviceNative},
    {"disableHidDeviceNative",  "()Z",      (void *)disableHidDeviceNative},
    {"connectNative",           "([B)Z",    (void *)connectNative},
    {"disconnectNative",        "([B)Z",    (void *)disconnectNative},
    {"virtualUnplugNative",     "([B)Z",    (void *)virtualUnplugNative},
    {"sendDataNative",          "([BI[B)Z", (void *)sendDataNative},
    {"setSdpRecordNative",      "([B)Z",    (void *)setSdpRecordNative},
    {"clearSdpRecordNative",    "()Z",      (void *)clearSdpRecordNative}
};

int register_com_broadcom_bt_service_hid_device(JNIEnv *env) {
    return jniRegisterNativeMethods(env, BROADCOM_BLUETOOTH_HIDD_SERVICE_CLASS_PATH, sMethods,
        sizeof(sMethods) / sizeof(sMethods[0]));
}

}
