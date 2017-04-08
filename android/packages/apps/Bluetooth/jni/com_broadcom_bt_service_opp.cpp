/* Copyright 2009-2013 Broadcom Corporation
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

#include "com_android_bluetooth.h"
#include "hardware/bt_op.h"
#include "utils/Log.h"
#include "android_runtime/AndroidRuntime.h"
#include <string.h>


#define LOG_TAG "BluetoothOPPServiceJni"
#define LOG_NDEBUG 0
#define MAX_PATH_LENGTH         512
#define CHECK_CALLBACK_ENV                                                      \
        if (!checkCallbackThread()) {                                                \
            error("Callback: '%s' is not called on the correct thread", __FUNCTION__);\
            return;                                                                  \
        }

#define info(fmt, ...)  ALOGI ("%s(L%d): " fmt,__FUNCTION__, __LINE__,  ## __VA_ARGS__)
#define debug(fmt, ...) ALOGD ("%s(L%d): " fmt,__FUNCTION__, __LINE__,  ## __VA_ARGS__)

#define warn(fmt, ...)                                                                \
    ALOGW ("## WARNING : %s(L%d): " fmt "##",__FUNCTION__, __LINE__, ## __VA_ARGS__)

#define error(fmt, ...)                                                               \
ALOGE ("## ERROR : %s(L%d): " fmt "##",__FUNCTION__, __LINE__, ## __VA_ARGS__)

#define asrt(s) if(!(s)) ALOGE ("## %s(L%d): ASSERT %s failed! ##",__FUNCTION__, __LINE__, #s)
#define BROADCOM_BLUETOOTH_OPP_SERVICE_CLASS_PATH  "com/broadcom/bt/service/opp/OppService"




#define BD_ADDR_LEN 6

namespace android {

static JavaVM *jvm;

static jmethodID method_onOpcEnable;
static jmethodID method_onOpcOpen;
static jmethodID method_onOpcClose;
static jmethodID method_onOpcProgress;
static jmethodID method_onOpcObjectPushed;
static jmethodID method_onOpcObjectReceived;
static jmethodID method_onOpcConState;


static jmethodID method_onOpsConnStateChange;
static jmethodID method_onOpsProgress;
static jmethodID method_onOpsAccessRequest;
static jmethodID method_onOpsObjectReceived;


static const btop_interface_t *sBluetoothOppInterface = NULL;

static jobject mOppCallbacksObj = NULL;
static JNIEnv *sCallbackEnv = NULL;
static const bt_interface_t* btIf;

static jboolean needAccess;
static bool isOppCleanedUp = false;
static bool isOpsCleanedUp = false;

static bool checkCallbackThread() {
    sCallbackEnv = getCallbackEnv();
    //ALOGE("calling checkCallbackThread sCallbackEnv val is : %d" ,sCallbackEnv);
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    if (sCallbackEnv != env || sCallbackEnv == NULL) return false;
    return true;
}

static jbyteArray marshall_bda(bt_bdaddr_t* bd_addr)
{
    jbyteArray addr;
    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (!addr) {
        ALOGE("Fail to new jbyteArray bd addr");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return NULL;
    }
    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte *) bd_addr);
    return addr;
}


/** Callback for OPP Client connection state. */
static void opc_state_cb ( bt_bdaddr_t* bd_addr, btop_state_t state,
                                    btopc_status_t status)
{

    jbyteArray addr;
    debug(" %s state:%d", __FUNCTION__, state);
    CHECK_CALLBACK_ENV

    if ((addr = marshall_bda(bd_addr)) == NULL)
        return;

    sCallbackEnv->CallVoidMethod(mOppCallbacksObj,
                                 method_onOpcConState,addr,(jint)state,(jint)status);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    sCallbackEnv->DeleteLocalRef(addr);
}



/** Callback for OPP Client progress status. */
static void opc_transfer_progress_cb(bt_bdaddr_t* bd_addr ,
                                            bt_op_filesize_t total,
                                            bt_op_filesize_t current,
                                            btop_operation_t operation)
{
    jbyteArray addr;
    debug("%d of %d bytes transferred", current, total);
    CHECK_CALLBACK_ENV

    if ((addr = marshall_bda(bd_addr)) == NULL) {
        return;
       }

    sCallbackEnv->CallVoidMethod(mOppCallbacksObj,
                method_onOpcProgress,addr,(jint)operation,
                (jint)total,(jint)current);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    sCallbackEnv->DeleteLocalRef(addr);
}

/** Callback for OPP client operation complete
*  state will have one of the values from btop_operation_t
 */
static void opc_transfer_state_cb (bt_bdaddr_t *bd_addr,
                                        bt_op_filepath_t filepath,
                                        btopc_status_t state,
                                        btop_operation_t operation)
{

    jbyteArray addr;
    jstring fileName = NULL;
    debug(" %s state:%d %s", __FUNCTION__, operation ,filepath);
    CHECK_CALLBACK_ENV

    if ((addr = marshall_bda(bd_addr)) == NULL)
        return;

    fileName = sCallbackEnv->NewStringUTF(filepath);

    debug(" oper:%d", operation);

    switch(operation) {
        case BTOP_OPERATION_PULL:
            fileName = sCallbackEnv->NewStringUTF(filepath);
            sCallbackEnv->CallVoidMethod(mOppCallbacksObj,method_onOpcObjectReceived,(jint)state);
        break;

        case BTOP_OPERATION_PUSH:
            fileName = sCallbackEnv->NewStringUTF(filepath);
            sCallbackEnv->CallVoidMethod(mOppCallbacksObj,method_onOpcObjectPushed,(jint)state);
        break;

        default:
            error(" Unknown operation callback : %d", operation);
        }
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        sCallbackEnv->DeleteLocalRef(fileName);
        sCallbackEnv->DeleteLocalRef(addr);
}



static void ops_state_cb(bt_bdaddr_t *bd_addr, btop_state_t conn_state){

    jbyteArray addr;
    debug(" %s state:%d", __FUNCTION__, conn_state);
    CHECK_CALLBACK_ENV

    if ((addr = marshall_bda(bd_addr)) == NULL)
        return;

    sCallbackEnv->CallVoidMethod(mOppCallbacksObj,
                                 method_onOpsConnStateChange,(jint)conn_state);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);

    sCallbackEnv->DeleteLocalRef(addr);

}

static void ops_access_cb( bt_bdaddr_t *bd_addr, btops_access_t param){

    jbyteArray addr;
    jstring bdAddr;
    jstring bdName;
    jstring fileName;
    jstring ptype;
    char bdNameStr[258],bdAddrStr[20];

    debug("%s : is called", __FUNCTION__);

    CHECK_CALLBACK_ENV

    if ((addr = marshall_bda(bd_addr)) == NULL) {
        return;
     }

    fileName = sCallbackEnv->NewStringUTF(param.file_name);
    ptype    = sCallbackEnv->NewStringUTF(param.ptype);

    sprintf(bdNameStr, "%s", param.bd_name.name);
    sprintf(bdAddrStr, "%02X:%02X:%02X:%02X:%02X:%02X",
                param.bd_addr.address[0], param.bd_addr.address[1],
                param.bd_addr.address[2], param.bd_addr.address[3],
                param.bd_addr.address[4], param.bd_addr.address[5]);

    bdName = sCallbackEnv->NewStringUTF(bdNameStr);
    bdAddr = sCallbackEnv->NewStringUTF(bdAddrStr);
    sCallbackEnv->CallVoidMethod(mOppCallbacksObj,
                                 method_onOpsAccessRequest, bdAddr, bdName, (jbyte)param.oper,
                                 fileName, ptype, (jint)param.fmt, (jint)param.size);

    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);

    sCallbackEnv->DeleteLocalRef(fileName);
    sCallbackEnv->DeleteLocalRef(bdName);
    sCallbackEnv->DeleteLocalRef(ptype);
    sCallbackEnv->DeleteLocalRef(addr);
    sCallbackEnv->DeleteLocalRef(bdAddr);
}

static void ops_obj_cb(bt_bdaddr_t* bd_addr, btop_object_t format, char *file_name){


    jbyteArray addr;
    jstring bdName;
    jstring fileName;

    debug("%s ", __FUNCTION__);

    if ((addr = marshall_bda(bd_addr)) == NULL) {
        return;
     }

    fileName = sCallbackEnv->NewStringUTF(file_name);


    sCallbackEnv->CallVoidMethod(mOppCallbacksObj,
                method_onOpsObjectReceived, (jint)format, fileName);

    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);

    sCallbackEnv->DeleteLocalRef(addr);
    sCallbackEnv->DeleteLocalRef(fileName);

}

static void ops_transfer_progress_cb(  bt_bdaddr_t* bd_addr ,
                                            bt_op_filesize_t total,
                                            bt_op_filesize_t current,
                                            btop_operation_t operation)
{
    jbyteArray addr;
    debug("%d of %d bytes transferred", current, total);
    CHECK_CALLBACK_ENV

    if ((addr = marshall_bda(bd_addr)) == NULL) {
        return;
       }

    sCallbackEnv->CallVoidMethod(mOppCallbacksObj,
                method_onOpsProgress, addr, (jint)operation,
                (jint)total,(jint)current);

    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);

    sCallbackEnv->DeleteLocalRef(addr);

}




static btop_callbacks_t sBluetoothOppCallbacks = {
    sizeof(sBluetoothOppCallbacks),
    opc_state_cb,
    opc_transfer_state_cb,
    opc_transfer_progress_cb,
    ops_state_cb,
    ops_access_cb,
    ops_obj_cb,
    ops_transfer_progress_cb
};


static void classInitNative(JNIEnv* env, jclass clazz) {
    ALOGE("%s", __FUNCTION__);
    if (env->GetJavaVM(&jvm) < 0) {
        ALOGE("%s: Java VM not found!", __FUNCTION__);
    }

    method_onOpcConState        = env->GetMethodID(clazz, "onOpcStateChange",      "([BII)V");
    method_onOpcProgress        = env->GetMethodID(clazz, "onOpcProgress",       "([BIII)V");
    method_onOpcObjectPushed    = env->GetMethodID(clazz, "onOpcObjectPushed",       "(I)V");
    method_onOpcObjectReceived  = env->GetMethodID(clazz, "onOpcObjectReceived",     "(I)V");

    method_onOpsConnStateChange = env->GetMethodID(clazz, "onOpsStateChange",    "(I)V");
    method_onOpsProgress        = env->GetMethodID(clazz, "onOpsProgress",        "([BIII)V");
    method_onOpsAccessRequest   = env->GetMethodID(clazz,"onOpsAccessRequest",
                  "(Ljava/lang/String;Ljava/lang/String;BLjava/lang/String;Ljava/lang/String;II)V");
    method_onOpsObjectReceived  =env->GetMethodID(clazz,
                                  "onOpsObjectReceived",   "(ILjava/lang/String;)V");

    info("succeeds");
}


static void initOppNative(JNIEnv *env, jobject object) {
   ALOGW("initOppNative");
    bt_status_t status;
    if(btIf)
        return;

    debug("OPP");

    if ( (btIf = getBluetoothInterface()) == NULL) {
        error("Bluetooth module is not loaded");
        return;
    }

    if (sBluetoothOppInterface !=NULL && !isOppCleanedUp) {
         ALOGW("Cleaning up Bluetooth Opp Client Interfaces before initializing...");
         sBluetoothOppInterface->cleanup();
         isOppCleanedUp = true;
         sBluetoothOppInterface = NULL;
    }


    if (mOppCallbacksObj != NULL) {
         ALOGW("Cleaning up Bluetooth Opp callback object");
         env->DeleteGlobalRef(mOppCallbacksObj);
         mOppCallbacksObj = NULL;
    }

    if ( (sBluetoothOppInterface = (btop_interface_t *)
          btIf->get_profile_interface(BT_PROFILE_OPP_ID)) == NULL) {
        error("Failed to get Bluetooth OPP Interface");
        return;
    }

    mOppCallbacksObj = env->NewGlobalRef(object);
    debug("mOppCallbacksObj %d" , mOppCallbacksObj);

    debug("calling OPP init");
    if ( (status = sBluetoothOppInterface->init(&sBluetoothOppCallbacks))
        != BT_STATUS_SUCCESS) {
        error("Failed to initialize Bluetooth OPP, status: %d", status );
        if (mOppCallbacksObj != NULL) {
            info("Cleaning up Bluetooth OPP callback object");
            env->DeleteGlobalRef(mOppCallbacksObj);
            mOppCallbacksObj = NULL;

        }
        sBluetoothOppInterface = NULL;
        return;
    }

    isOppCleanedUp = false;

    debug("OPC and OPS init Succesful");
    debug("mOppCallbacksObj %d" , mOppCallbacksObj);
}



/*******************************************************************************
**
** Function        pushObjectNative
**
** Description     JNI method
**
*******************************************************************************/
static jboolean pushObjectNative (JNIEnv *env,
                                   jobject object,
                                   jbyteArray peer_bd_address,
                                   jstring file_path_name,
                                   jint fd
                                   )
{
    ALOGV("%s", __FUNCTION__);
    bt_status_t status;
    jbyte *addr;
    const char *file_path;

    if (!sBluetoothOppInterface)
    {
        ALOGV("sBluetoothOppInterface is null");
        return JNI_FALSE;
    }

    addr = env->GetByteArrayElements(peer_bd_address, NULL);
    if (!addr){
        ALOGE("addr is wrong so will return JNI_FALSE");
        jniThrowIOException(env, EINVAL);
        return JNI_FALSE;
    }

    file_path = env->GetStringUTFChars(file_path_name, NULL);
    ALOGV("file_path is %s" , file_path);
    if (file_path == NULL || strlen(file_path) > OPP_MAX_PATH_LEN){

        ALOGE("%s: ERROR: Cannot push object. Invalid file path.",__FUNCTION__);
        return JNI_FALSE;

    }

    if((status = sBluetoothOppInterface->push((bt_bdaddr_t *)addr,(char*)file_path,fd))
        != BT_STATUS_SUCCESS){

        ALOGE("%s: ERROR: object push failed. ",__FUNCTION__);

    }

    env->ReleaseStringUTFChars(file_path_name, file_path);
    env->ReleaseByteArrayElements(peer_bd_address, addr,0);
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;

}



/*******************************************************************************
**
** Function        pullVcardNative
**
** Description     JNI method
**
*******************************************************************************/
static jboolean pullVcardNative (JNIEnv *env,
                                   jobject object,
                                   jbyteArray peer_bd_address,
                                   jstring file_path_name)
{

    ALOGV("%s", __FUNCTION__);
    bt_status_t status;
    jbyte *addr;
    const char *file_path;

    if (!sBluetoothOppInterface) return JNI_FALSE;

    addr = env->GetByteArrayElements(peer_bd_address, NULL);
    if (!addr){
        jniThrowIOException(env, EINVAL);
        return JNI_FALSE;
    }

    file_path = env->GetStringUTFChars(file_path_name, NULL);
    if (file_path == NULL || strlen(file_path) > OPP_MAX_PATH_LEN){

        ALOGE("%s: ERROR: Cannot pull object. Invalid file path.",__FUNCTION__);
        return JNI_FALSE;

    }

    if((status = sBluetoothOppInterface->pull((bt_bdaddr_t *)addr,(char*)file_path))
        != BT_STATUS_SUCCESS){

        ALOGE("%s: ERROR: object pull failed. ",__FUNCTION__);

    }

    env->ReleaseStringUTFChars(file_path_name, file_path);
    env->ReleaseByteArrayElements(peer_bd_address, addr,0);
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;

}

/*******************************************************************************
**
** Function        exchangeVcardNative
**
** Description     JNI method
**
*******************************************************************************/
static jboolean exchangeVcardNative (JNIEnv *env,
                                           jobject object,
                                           jbyteArray peer_bd_address,
                                           jstring src_path,
                                           jstring dest_path)
{
    ALOGV("%s", __FUNCTION__);
    bt_status_t status;
    jbyte *addr;
    const char *src_file_path;
    const char *dst_file_path;

    if (!sBluetoothOppInterface) return JNI_FALSE;

    addr = env->GetByteArrayElements(peer_bd_address, NULL);
    if (!addr){
        jniThrowIOException(env, EINVAL);
        return JNI_FALSE;
    }

    src_file_path = env->GetStringUTFChars(src_path, NULL);
    dst_file_path = env->GetStringUTFChars(dest_path, NULL);

    if (src_file_path == NULL || dst_file_path == NULL ||
        strlen(src_file_path) > OPP_MAX_PATH_LEN || strlen(dst_file_path) > OPP_MAX_PATH_LEN ){

        ALOGE("%s: ERROR: Cannot exchange. Invalid file path.",__FUNCTION__);
        return JNI_FALSE;

    }

    if((status = sBluetoothOppInterface->exchange((bt_bdaddr_t *)addr,
                                           (char*)src_path,(char*)dest_path)) != BT_STATUS_SUCCESS)
    {

        ALOGE("%s: ERROR: object pull failed. ",__FUNCTION__);

    }

    env->ReleaseStringUTFChars(src_path, src_file_path);
    env->ReleaseStringUTFChars(dest_path, dst_file_path);
    env->ReleaseByteArrayElements(peer_bd_address, addr,0);
    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;

}


/*******************************************************************************
**
** Function        closeOpcSessionNative
**
** Description     JNI method
**
*******************************************************************************/
static void closeOpcNative (JNIEnv *env, jobject object)
{
    ALOGV("%s", __FUNCTION__);
    bt_status_t status;
    if (!sBluetoothOppInterface) {
        ALOGE("sBluetoothOppInterface is null. Hence return");
        return ;
    }
    if ((status = sBluetoothOppInterface->closeOpc()) != BT_STATUS_SUCCESS) {
        ALOGE("Failed to stop Opp Client, status: %d", status);
    }
}



/*******************************************************************************
**
** Function        OpsAccessRspNative
**
** Description     JNI method
**
*******************************************************************************/
static void OpsAccessRspNative (JNIEnv* env, jobject object,
                                    jbyte oper_code, jboolean access,
                                    jstring filename)
{
        bt_status_t status;
        jint op_code;
        if (!sBluetoothOppInterface) {
            ALOGE("sBluetoothOppInterface is null. Hence return");
            return ;
        }
        const char *c_filename = env->GetStringUTFChars(filename, NULL);

        if(c_filename == NULL) {
            ALOGE("%s filename is null", __FUNCTION__);
            return;
        }
        op_code = oper_code;

        if ((status = sBluetoothOppInterface->ops_access_resp((uint8_t) op_code,
            (uint8_t) access, (char*) c_filename)) !=
                 BT_STATUS_SUCCESS) {
            ALOGE("Failed access response, status: %d", status);
        }
        env->ReleaseStringUTFChars(filename, c_filename);

}


/*******************************************************************************
**
** Function        closeOpsNative
**
** Description     JNI method
**
*******************************************************************************/
static void closeOpsNative (JNIEnv *env, jobject object)
{
    ALOGV("%s", __FUNCTION__);
    bt_status_t status;
    if (!sBluetoothOppInterface) {
        ALOGE("sBluetoothOppInterface is null. Hence return");
        return ;
    }
    if ((status = sBluetoothOppInterface->closeOps()) != BT_STATUS_SUCCESS) {
        ALOGE("Failed to stop Opp Client, status: %d", status);
    }
}


/*******************************************************************************
**
** Function        cleanupOppNative
**
** Description     JNI method
**
*******************************************************************************/

static void cleanupOppNative(JNIEnv *env, jobject object) {
    bt_status_t status;
    ALOGV("%s", __FUNCTION__);
    if (!btIf) return;

    if (sBluetoothOppInterface !=NULL) {
        ALOGW("Cleaning up Bluetooth Opp Interface...");
        sBluetoothOppInterface->cleanup();
        sBluetoothOppInterface = NULL;
    }


    if (mOppCallbacksObj != NULL) {
        ALOGW("Cleaning up Bluetooth OPP callback object");
        env->DeleteGlobalRef(mOppCallbacksObj);
        mOppCallbacksObj = NULL;
    }
    btIf = NULL;
}


static JNINativeMethod sMethods[] = {

    {"classInitNative",         "()V",                      (void*)classInitNative},
    {"initOppNative",           "()V",                      (void*)initOppNative},
    {"pushObjectNative",        "([BLjava/lang/String;I)Z",  (void*)pushObjectNative},
    {"pullVcardNative",         "([BLjava/lang/String;)Z",  (void*)pullVcardNative},
    {"exchangeVcardNative",
            "([BLjava/lang/String;Ljava/lang/String;)Z",(void*)exchangeVcardNative},
    {"closeOpcNative",          "()V",(void*)closeOpcNative},
    {"cleanupOppNative",        "()V",(void*)cleanupOppNative},
    {"OpsAccessRspNative",      "(BZLjava/lang/String;)V",(void*)OpsAccessRspNative},
    {"closeOpsNative",          "()V",(void*)closeOpsNative}

};

int register_com_broadcom_bt_service_opp(JNIEnv *env) {
    return jniRegisterNativeMethods(env,
        BROADCOM_BLUETOOTH_OPP_SERVICE_CLASS_PATH, sMethods,
        sizeof(sMethods) / sizeof(sMethods[0]));
}

}

