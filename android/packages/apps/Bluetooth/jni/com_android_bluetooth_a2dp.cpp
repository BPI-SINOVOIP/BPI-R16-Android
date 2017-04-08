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

#define LOG_TAG "BluetoothA2dpServiceJni"

#define LOG_NDEBUG 1

#define DBG_A2DP_JNI FALSE

#include "com_android_bluetooth.h"
#include "hardware/bt_av.h"
#include "hardware/bt_rc.h"
#include "hardware/bt_avk.h"

#include "utils/Log.h"
#include "android_runtime/AndroidRuntime.h"

#include <string.h>


#define CHECK_CALLBACK_ENV                                                      \
   if (!checkCallbackThread()) {                                                \
       ALOGE("Callback: '%s' is not called on the correct thread", __FUNCTION__);\
       return;                                                                  \
   }

#define A2DP_SOURCE_SERVICE_UUID    0x110a
#define A2DP_SINK_SERVICE_UUID      0x110b


namespace android {
static jmethodID method_onConnectionStateChanged;
static jmethodID method_onAudioStateChanged;
//BRCM Enhancements ++
static jmethodID method_onCPTypeCallback;
//BRCM Enhancements --


static const btav_interface_t  *sBluetoothA2dpSourceInterface = NULL;
static const btavk_interface_t *sBluetoothA2dpSinkInterface   = NULL;


static jobject mCallbacksObj = NULL;
static JNIEnv *sCallbackEnv = NULL;

static jmethodID method_getA2dpProfileServiceUuid;

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


static void bta2dp_connection_state_callback(jint state,
    bt_bdaddr_t* bd_addr) {
    jbyteArray addr;

    ALOGI("%s", __FUNCTION__);

    CHECK_CALLBACK_ENV

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
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onConnectionStateChanged, (jint) state,
        addr);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    sCallbackEnv->DeleteLocalRef(addr);
}

static void bta2dp_source_connection_state_callback(btav_connection_state_t state, bt_bdaddr_t* bd_addr) {
    bta2dp_connection_state_callback((jint)state, bd_addr);
}

static void bta2dp_sink_connection_state_callback(btavk_connection_state_t state, bt_bdaddr_t* bd_addr) {
    bta2dp_connection_state_callback((jint)state, bd_addr);
}

static void bta2dp_audio_state_callback(jint state, bt_bdaddr_t* bd_addr) {
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
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAudioStateChanged, state,
                                 addr);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    sCallbackEnv->DeleteLocalRef(addr);
}

//BRCM Enhancements ++
static void bta2dp_cp_type_callback(uint8_t cp_enabled, btav_cp_type_t type) {
    jbyteArray addr;

    ALOGI("%s", __FUNCTION__);

    CHECK_CALLBACK_ENV

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onCPTypeCallback,
                                 (jboolean) cp_enabled, (jint) type );
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

static void bta2dp_source_audio_state_callback(btav_audio_state_t state, bt_bdaddr_t* bd_addr) {
    bta2dp_audio_state_callback((jint)state, bd_addr);
}

static void bta2dp_sink_audio_state_callback(btavk_audio_state_t state, bt_bdaddr_t* bd_addr) {
    bta2dp_audio_state_callback((jint)state, bd_addr);
}

// AUTOMOTIVE FIXME -- add content protection ?
static btav_callbacks_t sBluetoothA2dpSourceCallbacks = {
    sizeof(sBluetoothA2dpSourceCallbacks),
    bta2dp_source_connection_state_callback,
    bta2dp_source_audio_state_callback,
    bta2dp_cp_type_callback
};

static btavk_callbacks_t sBluetoothA2dpSinkCallbacks = {
    sizeof(sBluetoothA2dpSinkCallbacks),
    bta2dp_sink_connection_state_callback,
    bta2dp_sink_audio_state_callback,

};

static void classInitNative(JNIEnv* env, jclass clazz) {
    int err;
    const bt_interface_t* btInf;
    bt_status_t status;

    ALOGI("%s", __FUNCTION__);

    method_getA2dpProfileServiceUuid =

        env->GetMethodID(clazz, "getA2dpProfileServiceUuid", "()I");

    method_onConnectionStateChanged =
        env->GetMethodID(clazz, "onConnectionStateChanged", "(I[B)V");

    method_onAudioStateChanged =
        env->GetMethodID(clazz, "onAudioStateChanged", "(I[B)V");

    //BRCM Enhancements ++
    method_onCPTypeCallback =
        env->GetMethodID(clazz, "onCPTypeCallback", "(ZI)V");
    //BRCM Enhancements --
    /*
    if ( (btInf = getBluetoothInterface()) == NULL) {
        ALOGE("Bluetooth module is not loaded");
        return;
    }

    if ( (sBluetoothA2dpInterface = (btav_interface_t *)
          btInf->get_profile_interface(BT_PROFILE_ADVANCED_AUDIO_ID)) == NULL) {
        ALOGE("Failed to get Bluetooth A2DP Interface");
        return;
    }
    */

    // TODO(BT) do this only once or
    //          Do we need to do this every time the BT reenables?
    /*
    if ( (status = sBluetoothA2dpInterface->init(&sBluetoothA2dpCallbacks)) != BT_STATUS_SUCCESS) {
        ALOGE("Failed to initialize Bluetooth A2DP, status: %d", status);
        sBluetoothA2dpInterface = NULL;
        return;
    }*/

    ALOGI("%s: succeeds", __FUNCTION__);
}

static void initNative(JNIEnv *env, jobject object) {
    const bt_interface_t* btInf = NULL;
    bt_status_t status;
    jint a2dpRole;

    if ( (btInf = getBluetoothInterface()) == NULL) {
        ALOGE("Bluetooth module is not loaded");
        return;
    }

    if (sBluetoothA2dpSourceInterface != NULL) {
         ALOGW("Cleaning up A2DP Source Interface before initializing...");
         sBluetoothA2dpSourceInterface->cleanup();
         sBluetoothA2dpSourceInterface = NULL;
    }

    if (sBluetoothA2dpSinkInterface != NULL) {
         ALOGW("Cleaning up A2DP Sink Interface before initializing...");
         sBluetoothA2dpSinkInterface->cleanup();
         sBluetoothA2dpSinkInterface = NULL;
    }

    if (mCallbacksObj != NULL) {
         ALOGW("Cleaning up A2DP callback object");
         env->DeleteGlobalRef(mCallbacksObj);
         mCallbacksObj = NULL;
    }



    a2dpRole = (jint) env->CallIntMethod(object, method_getA2dpProfileServiceUuid);
    ALOGI( "Init a2dp role 0x%x" ,a2dpRole);

    if ( a2dpRole == A2DP_SOURCE_SERVICE_UUID ) {
        if ((sBluetoothA2dpSourceInterface = (btav_interface_t *)
          btInf->get_profile_interface(BT_PROFILE_ADVANCED_AUDIO_ID))!= NULL){
        ALOGD("Attempting to acquire Bluetooth A2DP Source Interface");
        status = sBluetoothA2dpSourceInterface->init(&sBluetoothA2dpSourceCallbacks);
            if (status == BT_STATUS_SUCCESS) {
                mCallbacksObj = env->NewGlobalRef(object);
            }
            else {
                ALOGE("Failed to initialize Bluetooth A2DP source, status: %d", status);
                sBluetoothA2dpSourceInterface = NULL;
            }
        }
        else {
            ALOGW("Bluetooth A2DP Source Interface Unavailable");
        }
    }else if (a2dpRole == A2DP_SINK_SERVICE_UUID ) {
            if ( (sBluetoothA2dpSinkInterface = (btavk_interface_t *)
              btInf->get_profile_interface(BT_PROFILE_ADVANCED_AUDIO_SINK_ID)) != NULL){
            ALOGD("Attempting to acquire Bluetooth A2DP Sink Interface");
            status = sBluetoothA2dpSinkInterface->init(&sBluetoothA2dpSinkCallbacks);

                if (status == BT_STATUS_SUCCESS) {
                    mCallbacksObj = env->NewGlobalRef(object);
                } else {
                    ALOGE("Failed to initialize Bluetooth A2DP sink, status: %d", status);
                    sBluetoothA2dpSinkInterface = NULL;
                }
            }
            else {
                ALOGW("Bluetooth A2DP Sink Interface Unavailable");
            }
    }
    return;
}

static void cleanupNative(JNIEnv *env, jobject object) {
    const bt_interface_t* btInf;
    bt_status_t status;

    ALOGI("%s", __FUNCTION__);

    if ( (btInf = getBluetoothInterface()) == NULL) {
        ALOGE("Bluetooth module is not loaded");
        return;
    }

    if (sBluetoothA2dpSourceInterface != NULL) {
        sBluetoothA2dpSourceInterface->cleanup();
        sBluetoothA2dpSourceInterface = NULL;
    }

    if (sBluetoothA2dpSinkInterface != NULL) {
        sBluetoothA2dpSinkInterface->cleanup();
        sBluetoothA2dpSinkInterface = NULL;
    }

    if (mCallbacksObj != NULL) {
        env->DeleteGlobalRef(mCallbacksObj);
        mCallbacksObj = NULL;
    }

}

static jboolean connectA2dpNative(JNIEnv *env, jobject object, jbyteArray address) {
    jbyte *addr;
    bt_status_t status;
    jint a2dpRole = 0;

    ALOGI("%s: sBluetoothA2dpSourceInterface: %p sBluetoothA2dpSinkInterface: %p", __FUNCTION__,
          sBluetoothA2dpSourceInterface, sBluetoothA2dpSinkInterface);
    if (!(sBluetoothA2dpSourceInterface || sBluetoothA2dpSinkInterface)) return JNI_FALSE;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        ALOGI("%s: addr null",__FUNCTION__);
        return JNI_FALSE;
    }

    a2dpRole = (jint) env->CallIntMethod(object, method_getA2dpProfileServiceUuid);
    ALOGI( "connectA2dpNative a2dp role 0x%x" ,a2dpRole);
    if ((sBluetoothA2dpSourceInterface)&& (a2dpRole == A2DP_SOURCE_SERVICE_UUID )) {
        ALOGI( "Attempting to connect a2dp source ");
        if ((status = sBluetoothA2dpSourceInterface->connect((bt_bdaddr_t *)addr)) !=
                                        BT_STATUS_SUCCESS) {
            ALOGE("Failed A2dp Source connection, status: %d", status);
    }
    }

    else if ((sBluetoothA2dpSinkInterface) && (a2dpRole == A2DP_SINK_SERVICE_UUID )) {
        ALOGI( "Attempting to connect a2dp sink ");
        if ((status = sBluetoothA2dpSinkInterface->connect((bt_bdaddr_t *)addr)) !=
                                        BT_STATUS_SUCCESS) {
            ALOGE("Failed A2dp Sink connection, status: %d", status);
        }
    }

    env->ReleaseByteArrayElements(address, addr, 0);
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean disconnectA2dpNative(JNIEnv *env, jobject object, jbyteArray address) {
    jbyte *addr;
    bt_status_t status;
    jint a2dpRole = 0;

    if (!(sBluetoothA2dpSourceInterface || sBluetoothA2dpSinkInterface)) return JNI_FALSE;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        ALOGI("%s: addr null",__FUNCTION__);
        return JNI_FALSE;
    }

    a2dpRole = (jint) env->CallIntMethod(object, method_getA2dpProfileServiceUuid);
    ALOGI( "disconnectA2dpNative a2dp role 0x%x" ,a2dpRole);
    if ((sBluetoothA2dpSourceInterface)&& (a2dpRole == A2DP_SOURCE_SERVICE_UUID )) {
        ALOGI( "Attempting to disconnect a2dp source ");
        if ((status = sBluetoothA2dpSourceInterface->disconnect((bt_bdaddr_t *)addr)) !=
                                            BT_STATUS_SUCCESS) {
            ALOGE("Failed A2dp Source disconnection, status: %d", status);
        }
    }

    else if ((sBluetoothA2dpSinkInterface) && (a2dpRole == A2DP_SINK_SERVICE_UUID )) {
        ALOGI( "Attempting to disconnect a2dp sink ");
        if ((status = sBluetoothA2dpSinkInterface->disconnect((bt_bdaddr_t *)addr)) !=
                                            BT_STATUS_SUCCESS) {
            ALOGE("Failed A2dp Sink disconnection, status: %d", status);
        }
    }

    env->ReleaseByteArrayElements(address, addr, 0);
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

//BRCM Enhancements ++
static jboolean configureCPNative(JNIEnv *env, jobject object, int cp_type) {
    bt_status_t status;

    if (!sBluetoothA2dpSourceInterface) return JNI_FALSE;

    if((status = sBluetoothA2dpSourceInterface->configure_cp((btav_cp_type_t)cp_type))
                                              != BT_STATUS_SUCCESS) {
         ALOGE("Failed to Configure CP Type, status: %d", status);
    }
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}
//BRCM Enhancements --
static JNINativeMethod sMethods[] = {
    {"classInitNative", "()V", (void *) classInitNative},
    {"initNative", "()V", (void *) initNative},
    {"cleanupNative", "()V", (void *) cleanupNative},
    {"connectA2dpNative", "([B)Z", (void *) connectA2dpNative},
    {"disconnectA2dpNative", "([B)Z", (void *) disconnectA2dpNative},
    //BRCM Enhancements ++
    {"configureCPNative", "(I)Z", (void*) configureCPNative},
    //BRCM Enhancements --
};

int register_com_android_bluetooth_a2dp(JNIEnv* env)
{
    return jniRegisterNativeMethods(env, "com/android/bluetooth/a2dp/A2dpStateMachine",
                                    sMethods, NELEM(sMethods));
}

}
