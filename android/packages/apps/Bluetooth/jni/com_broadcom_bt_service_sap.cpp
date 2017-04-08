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


#define LOG_TAG "BluetoothSAPServiceJni"

#define LOG_NDEBUG 0

#define CHECK_CALLBACK_ENV                                                      \
   if (!checkCallbackThread()) {                                                \
       error("Callback: '%s' is not called on the correct thread", __FUNCTION__);\
       return;                                                                  \
   }

#include "com_android_bluetooth.h"
#include "hardware/bt_sc.h"
#include "utils/Log.h"
#include "android_runtime/AndroidRuntime.h"

#include <string.h>

#define info(fmt, ...)  ALOGI ("%s(L%d): " fmt,__FUNCTION__, __LINE__,  ## __VA_ARGS__)
#define debug(fmt, ...) ALOGD ("%s(L%d): " fmt,__FUNCTION__, __LINE__,  ## __VA_ARGS__)
#define warn(fmt, ...) ALOGW ("## WARNING : %s(L%d): " fmt "##",__FUNCTION__, __LINE__, ## __VA_ARGS__)
#define error(fmt, ...) ALOGE ("## ERROR : %s(L%d): " fmt "##",__FUNCTION__, __LINE__, ## __VA_ARGS__)
#define asrt(s) if(!(s)) ALOGE ("## %s(L%d): ASSERT %s failed! ##",__FUNCTION__, __LINE__, #s)

#define BROADCOM_BLUETOOTH_SAP_SERVICE_CLASS_PATH  "com/broadcom/bt/service/sap/SapService"


namespace android {

static jmethodID method_onConnectStateChanged;

static const btsc_interface_t *sSapIf = NULL;
static jobject mCallbacksObj = NULL;
static JNIEnv *sCallbackEnv = NULL;

static bool checkCallbackThread() {
    sCallbackEnv = getCallbackEnv();

    JNIEnv* env = AndroidRuntime::getJNIEnv();
    if (sCallbackEnv != env || sCallbackEnv == NULL) return false;
    return true;
}

static void connection_state_callback(const bt_bdaddr_t *bd_addr,  btsc_connection_state_t state){
    jbyteArray addr;
    debug("state:%d" , state);

    CHECK_CALLBACK_ENV
    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (!addr) {
        error("Fail to new jbyteArray bd addr for SAP conn state");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte *) bd_addr);

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onConnectStateChanged, addr,
                                 (jint) state);

    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    sCallbackEnv->DeleteLocalRef(addr);
}

static void client_request_callback  (btsc_client_req_type_t req_type,
                                        btsc_client_req_t *req_param)

{
    debug("client_request_callback: Req type %d" , req_type);
    /* Todo handle the client Request here...*/
}

static btsc_callbacks_t sBluetoothSAPCallbacks = {
    sizeof(sBluetoothSAPCallbacks),
    connection_state_callback,
    client_request_callback
};

// Define native functions

static void classInitNative(JNIEnv* env, jclass clazz) {
    int err;
    bt_status_t status;

    method_onConnectStateChanged = env->GetMethodID(clazz, "onConnectStateChanged",
                                                    "([BI)V");
    info("succeeds");
}
static const bt_interface_t* btIf;

static void initializeNative(JNIEnv *env, jobject object) {
    debug("sap");
    if(btIf)
        return;

    if ( (btIf = getBluetoothInterface()) == NULL) {
        error("Bluetooth module is not loaded");
        return;
    }

    if (sSapIf !=NULL) {
         warn("Cleaning up Bluetooth SAP Interface before initializing...");
         sSapIf->cleanup();
         sSapIf = NULL;
    }

    if (mCallbacksObj != NULL) {
         warn("Cleaning up Bluetooth SAP callback object");
         env->DeleteGlobalRef(mCallbacksObj);
         mCallbacksObj = NULL;
    }

    if ( (sSapIf = (btsc_interface_t *)
          btIf->get_profile_interface(BT_PROFILE_SAP_ID)) == NULL) {
        error("Failed to get Bluetooth SAP Interface");
        return;
    }

    bt_status_t status;
    if ( (status = sSapIf->init(&sBluetoothSAPCallbacks)) != BT_STATUS_SUCCESS) {
        error("Failed to initialize Bluetooth SAP, status: %d", status);
        sSapIf = NULL;
        return;
    }

    mCallbacksObj = env->NewGlobalRef(object);
}

static void cleanupNative(JNIEnv *env, jobject object) {
    bt_status_t status;
    if (!btIf) return;

    if (sSapIf !=NULL) {
        warn("Cleaning up Bluetooth SAP Interface...");
        sSapIf->cleanup();
        sSapIf = NULL;
    }

    if (mCallbacksObj != NULL) {
        warn("Cleaning up Bluetooth SAP callback object");
        env->DeleteGlobalRef(mCallbacksObj);
        mCallbacksObj = NULL;
    }
    btIf = NULL;
}

static jboolean disconnectSapNative(JNIEnv *env, jobject object, jint disc_type ) {
    bt_status_t status;
    jboolean ret = JNI_TRUE;
    if (!sSapIf) return JNI_FALSE;

    if ( (status = sSapIf->disconnect((btsc_disconnection_type_t) disc_type)) !=
         BT_STATUS_SUCCESS) {
        error("Failed disconnect SAP client, status: %d", status);
        ret = JNI_FALSE;
    }
    return ret;
}

static jboolean simCardResponse (btsc_sim_resp_type_t  resp_type,btsc_sim_resp_t *resp_param)
{
    //Call into SAP HAL Layer to send out the SIM card response to client.
    bt_status_t status;
    jboolean ret = JNI_TRUE;
    if (!sSapIf) return JNI_FALSE;

    debug("simCardResponse : Req type %d" , resp_type);

    sSapIf->sim_card_response(resp_type, resp_param);

    return ret;
}

static JNINativeMethod sMethods[] = {
    {"classInitNative", "()V", (void *) classInitNative},
    {"initializeNative", "()V", (void *) initializeNative},
    {"cleanupNative", "()V", (void *) cleanupNative},
    {"disconnectSapNative", "(I)Z", (void *) disconnectSapNative},
    // TBD cleanup
};

int register_com_broadcom_bt_service_sap(JNIEnv* env)
{
    return jniRegisterNativeMethods(env,BROADCOM_BLUETOOTH_SAP_SERVICE_CLASS_PATH,
                                    sMethods, NELEM(sMethods));
}

}
