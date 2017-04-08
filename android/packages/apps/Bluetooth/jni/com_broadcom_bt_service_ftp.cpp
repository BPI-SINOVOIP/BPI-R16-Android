/* Copyright 2009-2012 Broadcom Corporation
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
#define LOG_TAG "BluetoothFTPServiceJni"

#define LOG_NDEBUG 0
#define MAX_PATH_LENGTH         512

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
#include "hardware/bt_fts.h"
#include "utils/Log.h"
#include "android_runtime/AndroidRuntime.h"

#include <string.h>

#define info(fmt, ...)  ALOGI ("%s(L%d): " fmt,__FUNCTION__, __LINE__,  ## __VA_ARGS__)
#define debug(fmt, ...) ALOGD ("%s(L%d): " fmt,__FUNCTION__, __LINE__,  ## __VA_ARGS__)
#define warn(fmt, ...) ALOGW ("## WARNING : %s(L%d): " fmt "##",__FUNCTION__, __LINE__, ## __VA_ARGS__)
#define error(fmt, ...) ALOGE ("## ERROR : %s(L%d): " fmt "##",__FUNCTION__, __LINE__, ## __VA_ARGS__)
#define asrt(s) if(!(s)) ALOGE ("## %s(L%d): ASSERT %s failed! ##",__FUNCTION__, __LINE__, #s)
#define BROADCOM_BLUETOOTH_FTP_SERVICE_CLASS_PATH  "com/broadcom/bt/service/ftp/FTPService"

#define BD_ADDR_LEN 6

namespace android {

static JavaVM *jvm;
static jmethodID method_needAccessRequest;
static jmethodID method_onConnectStateChanged;
static jmethodID method_onFtpServerAuthen;
static jmethodID method_onFtpServerAccessRequested;
static jmethodID method_onFtpServerFileTransferInProgress;
static jmethodID method_onFtpServerPutCompleted;
static jmethodID method_onFtpServerGetCompleted;
static jmethodID method_onFtpServerDelCompleted;
static jmethodID method_onFtpServerEnabled;
static jmethodID method_onFtpServerDisabled;

static const btftp_interface_t *sBluetoothFtpInterface = NULL;
static jobject mCallbacksObj = NULL;
static JNIEnv *sCallbackEnv = NULL;

static const bt_interface_t* btIf;
static jboolean needAccess;
static bool isFtpServerCleanedUp = false;
static bool checkCallbackThread() {
    sCallbackEnv = getCallbackEnv();

    JNIEnv* env = AndroidRuntime::getJNIEnv();
    if (sCallbackEnv != env || sCallbackEnv == NULL) return false;
    return true;
}

static void connection_state_callback( bt_bdaddr_t *bd_addr,
    btfts_connection_state_t state) {

    jbyteArray addr;


    debug(" %s state:%d", __FUNCTION__, state);
    CHECK_CALLBACK_ENV

    if(mCallbacksObj == NULL) {
         error("%s sCallbacksObj is null", __FUNCTION__);
         return;
     }
    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (!addr) {
        error("Fail to new jbyteArray bd addr for FTS Connection state callback");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }

    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onConnectStateChanged,
                                 addr, (jint) state);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    sCallbackEnv->DeleteLocalRef(addr);
}

/** Callback for FTP request for Authentication key and realm
 */
static void auth_request_callback( bt_bdaddr_t *bd_addr,
    btfts_auth_t param) {
    debug("user:%s userid_req:%d", param.p_userid, param.userid_required);
}

/** Callback for FTP request for access to put a file
 * param will contain details of the access request callback
 */
static void access_request_callback( bt_bdaddr_t *bd_addr,
    btfts_access_t param) {
    jstring fileName;
    jstring bdName;
    jstring bdAddr;
    int len = sizeof(bt_bdaddr_t);
    char bdAddrStr[20], bdNameStr[258];
    CHECK_CALLBACK_ENV
    fileName = sCallbackEnv->NewStringUTF(param.file_name);
    //bdName = sCallbackEnv->NewStringUTF(param.bd_name.name);
    sprintf(bdNameStr, "%s", param.bd_name.name);
    sprintf(bdAddrStr, "%02X:%02X:%02X:%02X:%02X:%02X",
                param.bd_addr.address[0], param.bd_addr.address[1],
                param.bd_addr.address[2], param.bd_addr.address[3],
                param.bd_addr.address[4], param.bd_addr.address[5]);
    ALOGI("%s: fileName = [%s], bdName = [%s], bdAddr=[%s]",
         __FUNCTION__, param.file_name, bdNameStr, bdAddrStr);
    // TODO : Pass the BD address to onFtpServerAccessRequested()
    bdAddr = sCallbackEnv->NewStringUTF(bdAddrStr);
    bdName = sCallbackEnv->NewStringUTF(bdNameStr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj,
                method_onFtpServerAccessRequested,    fileName, param.size,
                bdName, (jbyte) param.oper, bdAddr);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    sCallbackEnv->DeleteLocalRef(fileName);
    sCallbackEnv->DeleteLocalRef(bdName);
    sCallbackEnv->DeleteLocalRef(bdAddr);
}

/** Callback for FTP progress status.
 *  prog_info will contain details about FTP progress
 */
static void progress_callback( bt_bdaddr_t *bd_addr,
    btfts_progress_info_t prog_info) {
    debug("%d of %d bytes transferred", prog_info.bytes,
        prog_info.total_bytes);
    CHECK_CALLBACK_ENV
    sCallbackEnv->CallVoidMethod(mCallbacksObj,
                method_onFtpServerFileTransferInProgress,
                (jint) prog_info.total_bytes, (jint) prog_info.bytes);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

/** Callback for FTP operation complete
 *  state will have one of the values from  btfts_operation_cmpl_t
 */
static void operation_cmpl_callback( bt_bdaddr_t *bd_addr,
    btfts_operation_cmpl_t param) {
    jstring fileName = NULL;
    CHECK_CALLBACK_ENV

    debug(" oper:%d status:%d", param.event, param.status);
    switch(param.event) {
        case BTFTS_PUT_CMPL_EVT:
            fileName = sCallbackEnv->NewStringUTF(param.file_name);
            sCallbackEnv->CallVoidMethod(mCallbacksObj,
                method_onFtpServerPutCompleted,
                fileName, (jbyte) param.status);
            break;
        case BTFTS_GET_CMPL_EVT:
            fileName = sCallbackEnv->NewStringUTF(param.file_name);
            sCallbackEnv->CallVoidMethod(mCallbacksObj,
                method_onFtpServerGetCompleted,
                fileName, (jbyte) param.status);
            break;
        case BTFTS_DEL_CMPL_EVT:
            fileName = sCallbackEnv->NewStringUTF(param.file_name);
            sCallbackEnv->CallVoidMethod(mCallbacksObj,
                method_onFtpServerDelCompleted,
                fileName, (jbyte) param.status);
            break;
        case BTFTS_ENABLE_CMPL_EVT:
            /* Root path */
            fileName = sCallbackEnv->NewStringUTF(param.file_name);
            sCallbackEnv->CallVoidMethod(mCallbacksObj,
                method_onFtpServerEnabled, fileName);
            break;
        case BTFTS_DISABLE_CMPL_EVT:
            sCallbackEnv->CallVoidMethod(mCallbacksObj,
                method_onFtpServerDisabled);
            break;
        default:
            error(" Unknown event callback : %d", param.event);
        }
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    if(fileName)
        sCallbackEnv->DeleteLocalRef(fileName);
}


static btfts_callbacks_t sBluetoothFtsCallbacks = {
    sizeof(sBluetoothFtsCallbacks),
    connection_state_callback,
    auth_request_callback,
    access_request_callback,
    progress_callback,
    operation_cmpl_callback
};

// Define native functions

static void classInitNative(JNIEnv* env, jclass clazz) {
    ALOGV("%s", __FUNCTION__);
    if (env->GetJavaVM(&jvm) < 0) {
        ALOGE("%s: Java VM not found!", __FUNCTION__);
    }

    method_needAccessRequest = env->GetStaticMethodID(clazz,
                "needAccessRequest", "()Z");
    method_onFtpServerAuthen = env->GetMethodID(clazz,
                "onFtpServerAuthen", "(Ljava/lang/String;BZ)V");
    method_onFtpServerAccessRequested = env->GetMethodID(clazz,
                "onFtpServerAccessRequested",
                "(Ljava/lang/String;ILjava/lang/String;BLjava/lang/String;)V");
    method_onFtpServerFileTransferInProgress = env->GetMethodID(clazz,
                "onFtpServerFileTransferInProgress", "(II)V");
    method_onFtpServerPutCompleted = env->GetMethodID(clazz,
                "onFtpServerPutCompleted", "(Ljava/lang/String;B)V");
    method_onFtpServerGetCompleted = env->GetMethodID(clazz,
                "onFtpServerGetCompleted", "(Ljava/lang/String;B)V");
    method_onFtpServerDelCompleted = env->GetMethodID(clazz,
                "onFtpServerDelCompleted", "(Ljava/lang/String;B)V");
    method_onFtpServerEnabled = env->GetMethodID(clazz,
                "onFtpServerEnabled", "(Ljava/lang/String;)V");
    method_onFtpServerDisabled = env->GetMethodID(clazz,
                "onFtpServerDisabled", "()V");
    method_onConnectStateChanged = env->GetMethodID(clazz,
                "onConnectStateChanged", "([BI)V");

    needAccess = env->CallStaticBooleanMethod(clazz,method_needAccessRequest);
    info("succeeds");
}

static void initFtpNativeDataNative(JNIEnv *env, jobject object) {
    debug("FTP");
    if(btIf)
        return;

    if ( (btIf = getBluetoothInterface()) == NULL) {
        error("Bluetooth module is not loaded");
        return;
    }

    if (sBluetoothFtpInterface !=NULL && !isFtpServerCleanedUp) {
         ALOGW("Cleaning up Bluetooth FTP Server Interface before initializing...");
         sBluetoothFtpInterface->cleanup();
         isFtpServerCleanedUp = true;
         sBluetoothFtpInterface = NULL;
    }

    if (mCallbacksObj != NULL) {
         ALOGW("Cleaning up Bluetooth FTP callback object");
         env->DeleteGlobalRef(mCallbacksObj);
         mCallbacksObj = NULL;
    }

    if ( (sBluetoothFtpInterface = (btftp_interface_t *)
          btIf->get_profile_interface(BT_PROFILE_FTP_ID)) == NULL) {
        error("Failed to get Bluetooth FTP Server Interface");
        return;
    }

    bt_status_t status;
    mCallbacksObj = env->NewGlobalRef(object);
    if ( (status = sBluetoothFtpInterface->init(&sBluetoothFtsCallbacks))
        != BT_STATUS_SUCCESS) {
        error("Failed to initialize Bluetooth FTP, status: %d", status);
        if (mCallbacksObj != NULL) {
            info("Cleaning up Bluetooth FTP callback object");
            env->DeleteGlobalRef(mCallbacksObj);
            mCallbacksObj = NULL;
        }
        sBluetoothFtpInterface = NULL;
        return;
    }
    isFtpServerCleanedUp = false;
    debug("FTP init done");
}

static void cleanupNative(JNIEnv *env, jobject object) {
    bt_status_t status;
    ALOGV("%s", __FUNCTION__);
    if (!btIf) return;

    if (sBluetoothFtpInterface !=NULL) {
        ALOGW("Cleaning up Bluetooth FTP Server Interface...");
        sBluetoothFtpInterface = NULL;
    }

    if (mCallbacksObj != NULL) {
        ALOGW("Cleaning up Bluetooth FTP callback object");
        env->DeleteGlobalRef(mCallbacksObj);
        mCallbacksObj = NULL;
    }
    btIf = NULL;
}

static void closeFtpServerWithoutCleanupNative(JNIEnv *env, jobject object) {
    ALOGV("%s", __FUNCTION__);
    if (!sBluetoothFtpInterface) {
        ALOGE("sBluetoothFtpInterface is null. Hence return");
        return ;
    }
    sBluetoothFtpInterface->closeftpserver();
    isFtpServerCleanedUp = true;
}

static void closeFtpServerNative(JNIEnv* env, jobject object) {
    ALOGV("%s", __FUNCTION__);
    if (!sBluetoothFtpInterface) {
        ALOGE("sBluetoothFtpInterface is null. Hence return");
        return ;
    }
    sBluetoothFtpInterface->cleanup();
    isFtpServerCleanedUp = true;
}

static void ftpServerAuthenRspNative(JNIEnv* env, jobject object,
                                    jstring user_name, jstring password) {
    bt_status_t status;
    if (!sBluetoothFtpInterface) {
        ALOGE("sBluetoothFtpInterface is null. Hence return");
        return ;
    }
    const char *c_username = env->GetStringUTFChars(user_name, NULL);
    const char *c_password = env->GetStringUTFChars(password, NULL);
    if(!(c_username) || !(c_password)) {
        ALOGE("%s username/password is null", __FUNCTION__);
        return;
    }

    if ( (status = sBluetoothFtpInterface->auth_rsp((char*) c_username,
        (char*) c_password)) != BT_STATUS_SUCCESS) {
        ALOGE("Failed authen response, status: %d", status);
    }
    if(c_username) env->ReleaseStringUTFChars(user_name, c_username);
    if(c_password) env->ReleaseStringUTFChars(password, c_password);
}

static void ftpServerAccessRspNative(JNIEnv* env, jobject object,
                                    jbyte oper_code, jboolean access,
                                    jstring filename) {
    bt_status_t status;
    jint op_code;
    if (!sBluetoothFtpInterface) {
        ALOGE("sBluetoothFtpInterface is null. Hence return");
        return ;
    }
    const char *c_filename = env->GetStringUTFChars(filename, NULL);

    if(c_filename == NULL) {
        ALOGE("%s filename is null", __FUNCTION__);
        return;
    }
    op_code = oper_code;

    if ((status = sBluetoothFtpInterface->access_rsp((uint8_t) op_code,
        (uint8_t) access, (char*) c_filename)) !=
             BT_STATUS_SUCCESS) {
        ALOGE("Failed access response, status: %d", status);
    }
    env->ReleaseStringUTFChars(filename, c_filename);
}

static JNINativeMethod sMethods[] = {
    /* name, signature, funcPtr */
    {"ftpServerAuthenRspNative", "(Ljava/lang/String;Ljava/lang/String;)V",
                (void *)ftpServerAuthenRspNative},
    {"ftpServerAccessRspNative", "(BZLjava/lang/String;)V",
                (void*)ftpServerAccessRspNative},
    {"closeFtpServerNative", "()V", (void *)closeFtpServerNative},
    {"closeFtpServerWithoutCleanupNative", "()V", (void *)closeFtpServerWithoutCleanupNative},
    {"classInitNative", "()V", (void *)classInitNative},
    {"initFtpNativeDataNative", "()V", (void *)initFtpNativeDataNative},
    {"cleanupFtpNativeDataNative", "()V", (void *)cleanupNative}
};

int register_com_broadcom_bt_service_ftp_FtpService(JNIEnv *env) {
    return jniRegisterNativeMethods(env,
        BROADCOM_BLUETOOTH_FTP_SERVICE_CLASS_PATH, sMethods,
        sizeof(sMethods) / sizeof(sMethods[0]));
}

}

