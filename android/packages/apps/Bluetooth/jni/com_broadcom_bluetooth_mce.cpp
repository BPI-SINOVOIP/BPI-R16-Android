/*******************************************************************************
 *
 *  Copyright (C) 2012-2013 Broadcom Corporation
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

#define LOG_TAG "BtMap.MceJni"

#define LOG_NDEBUG 1

#define CHECK_CALLBACK_ENV                                                      \
   if (!checkCallbackThread() || mCallbacksObj == NULL) {                       \
       ALOGE("Callback: '%s' is not called on the correct thread or callback object is null", \
        __FUNCTION__);\
       return;                                                                  \
   }

#include "com_android_bluetooth.h"
#include "hardware/bt_mce.h"
#include "hardware/bluetooth.h"
#include "utils/Log.h"
#include "android_runtime/AndroidRuntime.h"
#include <string.h>

#define MAX_BDNAME_LENGTH 248
#define MSG_HANDLE_LENGTH 8

#define MESSAGE_FILTER_CLASS "com/broadcom/bt/map/BluetoothMessageListFilter"
#define MSEINFO_CLASS "com/broadcom/bt/map/BluetoothMseInfo"

namespace android
{
static const btmce_interface_t *sBluetoothMceInterface = NULL;
static jobject mCallbacksObj = NULL;
static JNIEnv *sCallbackEnv = NULL;

static jmethodID method_onProfileStateChanged;
static jmethodID method_onConnectionStateChanged;
static jmethodID method_onNotificationServerStateChanged;
static jmethodID method_onNotificationConnectionStateChanged;
static jmethodID method_onFolderPathSet;
static jmethodID method_onGetFolderListResult;
static jmethodID method_onGetMessageListResult;
static jmethodID method_onGetMessageResult;
static jmethodID method_onPushMessageResult;
static jmethodID method_onUpdateInboxResult;
static jmethodID method_onMessageStatusUpdated;
static jmethodID method_onNotificationRegistrationUpdated;
static jmethodID method_onNotification;
static jmethodID method_onGetMseInstancesResult;

//MseInfo object
static jclass cls_MseInfo;
static jmethodID method_MseInfo_Constructor;
static jfieldID field_MseInfo_mName;
static jfieldID field_MseInfo_mServerInstanceId;
static jfieldID field_MseInfo_mMessageTypes;

//MessageListFilter
static jclass cls_MessageListFilter;
static jfieldID field_MessageListFilter_mMsgMask;
static jfieldID field_MessageListFilter_mMaxListCount;
static jfieldID field_MessageListFilter_mListStartOffset;
static jfieldID field_MessageListFilter_mSubjectLength;
static jfieldID field_MessageListFilter_mPeriodBegin;
static jfieldID field_MessageListFilter_mPeriodEnd;
static jfieldID field_MessageListFilter_mReadStatus;
static jfieldID field_MessageListFilter_mRecipient;
static jfieldID field_MessageListFilter_mOriginator;
static jfieldID field_MessageListFilter_mPriorityStatus;

typedef struct
{
    jobject mse_info;
    jstring name;
} jmseinfo_t;




static bool checkCallbackThread()
{
    // Always fetch the latest callbackEnv from AdapterService.
    // Caching this could cause this sCallbackEnv to go out-of-sync
    // with the AdapterService's ENV if an ASSOCIATE/DISASSOCIATE event
    // is received
    sCallbackEnv = getCallbackEnv();
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    if (sCallbackEnv != env || sCallbackEnv == NULL)
        return false;
    return true;
}

static bool btif_copy_jstring(char* str, int maxBytes, jstring jstr,JNIEnv* env)
{
    const char* p_str;
    int len;

    if (str == NULL || jstr == NULL || env == NULL)
    {
        return false;
    }

    memset(str, 0, maxBytes);
    p_str = env->GetStringUTFChars(jstr, NULL);
    len = strnlen(p_str, maxBytes);
    memcpy(str, p_str, len);
    env->ReleaseStringUTFChars(jstr, p_str);
    return true;
}

static bool btif_copy_jbytearray(uint8_t* a, int maxBytes, jbyteArray jbarray,JNIEnv* env)
{

    jbyte *midarray;
    jint jbarraysize;

    bool success=false;
    memset(a, 0, maxBytes);
    jbarraysize = env->GetArrayLength(jbarray);
    if (jbarraysize>0) {
        midarray = env->GetByteArrayElements(jbarray, NULL);
        if (midarray == NULL)
        {
            ALOGE("%s, INVALID byte array", __FUNCTION__);
        }
        else
        {
            memcpy(a, midarray, maxBytes<=jbarraysize? maxBytes:jbarraysize);
            success=true;
        }
        env->ReleaseByteArrayElements(jbarray, midarray, NULL);
    }
    return success;
}

static bool btif_mce_copy_string(char*str, uint8_t* bytes,int maxBytes)
{
    int len;
    if (str==NULL)
    {
        return false;
    }
    memset(str, 0, maxBytes);
    if (bytes==NULL)
    {
        return false;
    }
    len=strnlen((char const*)bytes, maxBytes);
    memcpy(str,bytes,len);
    return true;
}

static void cleanup_jmseinfo(JNIEnv *env, jmseinfo_t* jmseinfo)
{
    if (env != NULL && jmseinfo != NULL)
    {
        if (jmseinfo->name != NULL)
        {
            env->DeleteLocalRef(jmseinfo->name);
        }
        if (jmseinfo->mse_info != NULL)
        {
            env->DeleteLocalRef(jmseinfo->mse_info);
        }
    }
}

/**
 * Create a Java MseInfo object
 */
static void create_jmseinfo(JNIEnv *env, jmseinfo_t* jmseinfo, btmce_instance_id_t instance_id,
        uint8_t message_types, btmce_name_t* name)
{
    if (jmseinfo == NULL)
    {
        ALOGE("%s: internal error: jmseinfo is null!!!",__FUNCTION__);
        return;
    }

    char namestr[MAX_NAME_LENGTH + 1];
    jmseinfo->mse_info = env->NewObject(cls_MseInfo, method_MseInfo_Constructor);
    env->SetIntField(jmseinfo->mse_info, field_MseInfo_mServerInstanceId,(jint) instance_id);
    env->SetIntField(jmseinfo->mse_info, field_MseInfo_mMessageTypes,(jint) message_types);

    //Setup the NAME
    namestr[0]='\0';
    if (name)
    {
        btif_mce_copy_string(namestr,name->name,MAX_NAME_LENGTH);
    }
    jmseinfo->name = sCallbackEnv->NewStringUTF(namestr);
    env->SetObjectField(jmseinfo->mse_info, field_MseInfo_mName,jmseinfo->name);
}

static void set_btmce_msg_filter(btmce_msg_list_filter_t* filter, JNIEnv *env, jobject jfilter)
{
    jstring jstr;
    if (filter == NULL)
    {
        ALOGE("%s: internal error: filter is null!!!",__FUNCTION__);
        return;
    }
    memset(filter,0,sizeof(btmce_msg_list_filter_t));

    filter->subject_length = (uint8_t)
            env->GetByteField(jfilter , field_MessageListFilter_mSubjectLength);
    ALOGD("%s: setting filter subject length to :%d",__FUNCTION__,filter->subject_length);

    filter->message_mask =  (uint8_t)
            env->GetByteField(jfilter , field_MessageListFilter_mMsgMask);
    ALOGD("%s: setting filter message mask to :%d",__FUNCTION__,filter->message_mask);

    filter->is_priority = (uint8_t)
            env->GetByteField(jfilter , field_MessageListFilter_mPriorityStatus);
    ALOGD("%s: setting filter priority to :%d",__FUNCTION__,filter->is_priority);

    filter->read_status = (uint8_t)
            env->GetByteField(jfilter , field_MessageListFilter_mReadStatus);
    ALOGD("%s: setting filter read status to :%d",__FUNCTION__,filter->read_status);

    jstr =  (jstring) env->GetObjectField(jfilter, field_MessageListFilter_mPeriodBegin);
    btif_copy_jstring((char*) filter->period_begin.datetime,MAX_DATETIME_LENGTH,jstr,env);
    ALOGD("%s: setting filter period_begin to :%s",__FUNCTION__,filter->period_begin.datetime);

    jstr =  (jstring) env->GetObjectField(jfilter, field_MessageListFilter_mPeriodEnd);
    btif_copy_jstring((char*) filter->period_end.datetime,MAX_DATETIME_LENGTH,jstr,env);
    ALOGD("%s: setting filter period_end to :%s",__FUNCTION__,filter->period_end.datetime);

    jstr =  (jstring) env->GetObjectField(jfilter, field_MessageListFilter_mOriginator);
    btif_copy_jstring((char*) filter->originator,MAX_MSG_INFO_FIELD_LENGTH,jstr,env);
    ALOGD("%s: setting filter originator to :%s",__FUNCTION__,filter->originator);

    jstr =  (jstring) env->GetObjectField(jfilter, field_MessageListFilter_mRecipient);
    btif_copy_jstring((char*) filter->recipient,MAX_MSG_INFO_FIELD_LENGTH,jstr,env);
    ALOGD("%s: setting filter recipient to :%s",__FUNCTION__,filter->recipient);
}

void profile_state_callback(btmce_profile_state_t state)
{
    CHECK_CALLBACK_ENV
    if (state ==BTMCE_ENABLED)
    {
        sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onProfileStateChanged,
                JNI_TRUE);
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    }
    else if (state == BTMCE_DISABLED)
    {
        sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onProfileStateChanged,
                JNI_FALSE);
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    }
}

void notification_server_state_callback(btmce_ns_state_t state, btmce_session_id_t session_id)
{
    ALOGI("%s: state=%d, session_id=%d", __FUNCTION__,state,session_id);
    CHECK_CALLBACK_ENV
    if (state ==BTMCE_STARTED)
    {
        sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onNotificationServerStateChanged,
                JNI_TRUE, (jint) session_id);
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    }
    else if (state == BTMCE_STOPPED)
    {
        sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onNotificationServerStateChanged,
                JNI_FALSE, (jint) session_id);
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    }
}



void connection_state_callback(btmce_instance_id_t instance_id,
        btmce_session_id_t session_id, bt_bdname_t* bd_name,
        bt_bdaddr_t* bd_addr, btmce_connection_state_t connection_state,
        btmce_io_status_t status)
{
    char bdname[MAX_BDNAME_LENGTH + 1];
    jbyteArray jaddr;
    jstring jbdname;
    ALOGI("%s: instance_id =%d, session_id=%d, connection_state=%d, status=%d",
            __FUNCTION__, instance_id, session_id, connection_state, status);
    if (status != BTMCE_IO_STATUS_OK)
    {
        ALOGE("%s: Error status = %d", __FUNCTION__, status);
        return;
    }

    CHECK_CALLBACK_ENV
    jaddr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (!jaddr)
    {
        ALOGE("Fail to new jbyteArray bd addr for connection state");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    sCallbackEnv->SetByteArrayRegion(jaddr, 0, sizeof(bt_bdaddr_t),
            (jbyte*) bd_addr);

    bdname[0] = '\0';
    if (bd_name != NULL)
    {
        btif_mce_copy_string(bdname,bd_name->name,MAX_BDNAME_LENGTH);

    }
    jbdname = sCallbackEnv->NewStringUTF(bdname);
    if (!jbdname)
    {
        ALOGE("Fail to new jstring for connection state");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onConnectionStateChanged,
                (connection_state == BTMCE_CONNECTED? JNI_TRUE:JNI_FALSE),
                (jint) instance_id, (jint) session_id, jbdname, jaddr);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    if (jaddr)
    {
        sCallbackEnv->DeleteLocalRef(jaddr);
    }
    if (jbdname)
    {
        sCallbackEnv->DeleteLocalRef(jbdname);
    }
}


void notification_connection_state_callback(
        btmce_session_id_t session_id, bt_bdname_t* bd_name,
        bt_bdaddr_t* bd_addr, btmce_connection_state_t connection_state,
        btmce_io_status_t status)
{
    char bdname[MAX_BDNAME_LENGTH + 1];
    jbyteArray jaddr;
    jstring jbdname;
    ALOGI("%s: session_id=%d, connection_state=%d, status=%d",
            __FUNCTION__,  session_id, connection_state, status);
    if (status != BTMCE_IO_STATUS_OK)
    {
        ALOGE("%s: Error status = %d", __FUNCTION__, status);
        return;
    }

    CHECK_CALLBACK_ENV
    jaddr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (!jaddr)
    {
        ALOGE("Fail to new jbyteArray bd addr for connection state");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    sCallbackEnv->SetByteArrayRegion(jaddr, 0, sizeof(bt_bdaddr_t),
            (jbyte*) bd_addr);

    bdname[0] = '\0';
    if (bd_name != NULL)
    {
        btif_mce_copy_string(bdname,bd_name->name,MAX_BDNAME_LENGTH);

    }
    jbdname = sCallbackEnv->NewStringUTF(bdname);
    if (!jbdname)
    {
        ALOGE("Fail to new jstring for connection state");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onNotificationConnectionStateChanged,
                (connection_state == BTMCE_CONNECTED? JNI_TRUE:JNI_FALSE),
               (jint) session_id, jbdname, jaddr);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    if (jaddr)
    {
        sCallbackEnv->DeleteLocalRef(jaddr);
    }
    if (jbdname)
    {
        sCallbackEnv->DeleteLocalRef(jbdname);
    }
}

void set_folder_path_callback(btmce_session_id_t session_id, btmce_op_status_t status)
{
    ALOGI("%s: session_id=%d, status=%d",
               __FUNCTION__, session_id, status);

    CHECK_CALLBACK_ENV
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onFolderPathSet,
                   (jint) session_id, (status == BTMCE_OP_STATUS_OK ?JNI_TRUE:JNI_FALSE));
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

void get_folder_list_callback( btmce_session_id_t session_id,btmce_op_status_t status,
        int list_size, btmce_path_t* file_path)
{
    char filepath[MAX_PATH_LENGTH + 1];
    jstring jfilepath;

    filepath[0]='\0';
    CHECK_CALLBACK_ENV
    if (file_path !=NULL)
    {
        btif_mce_copy_string(filepath,file_path->path,MAX_PATH_LENGTH);
    }
    ALOGI("%s: session_id=%d, status=%d, list_size=%d, filepath=%s",
            __FUNCTION__, session_id, status,list_size,filepath);
    jfilepath = sCallbackEnv->NewStringUTF(filepath);
    if (!jfilepath)
    {
        ALOGE("Fail to new jstring for folder list");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onGetFolderListResult,
                      (jint) session_id, (status == BTMCE_OP_STATUS_OK ?JNI_TRUE:JNI_FALSE),
                      (jint) list_size, jfilepath);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}


void get_message_list_callback( btmce_session_id_t session_id,btmce_op_status_t status,
        int list_size, int has_new_msg, btmce_path_t* file_path)
{
    ALOGI("%s: session_id=%d, status=%d, list_size=%d",
                __FUNCTION__, session_id, status,list_size);
    char filepath[MAX_PATH_LENGTH + 1];
    jstring jfilepath;

    filepath[0]='\0';
    CHECK_CALLBACK_ENV
    if (file_path !=NULL)
    {
        btif_mce_copy_string(filepath,file_path->path,MAX_PATH_LENGTH);
    }
    ALOGI("%s: session_id=%d, status=%d, list_size=%d, filepath=%s",
            __FUNCTION__, session_id, status,list_size,filepath);
    jfilepath = sCallbackEnv->NewStringUTF(filepath);
    if (!jfilepath)
    {
        ALOGE("Fail to new jstring for message list");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onGetMessageListResult,
                      (jint) session_id, (status == BTMCE_OP_STATUS_OK ?JNI_TRUE:JNI_FALSE),
                      (jint) list_size,  has_new_msg==1?JNI_TRUE:JNI_FALSE, jfilepath);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

void get_message_callback( btmce_session_id_t session_id,
                           btmce_op_status_t status,
                           btmce_msg_handle_t message_handle,
                           btmce_path_t* file_path)
{
    char filepath[MAX_PATH_LENGTH + 1];
    jstring jfilepath;
    jbyteArray jmessage_handle;

    ALOGI("%s: session_id=%d, status=%d",__FUNCTION__, session_id, status);

    filepath[0]='\0';
    CHECK_CALLBACK_ENV
    if (file_path !=NULL)
    {
        btif_mce_copy_string(filepath,file_path->path,MAX_PATH_LENGTH);
    }
    jfilepath = sCallbackEnv->NewStringUTF(filepath);
    if (!jfilepath)
    {
        ALOGE("Fail to new jstring for message");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    jmessage_handle = sCallbackEnv->NewByteArray(sizeof(btmce_msg_handle_t));
    if (jmessage_handle == NULL)
    {
        ALOGE("%s: unable to create message id byte array...Aborting...",__FUNCTION__);
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    sCallbackEnv->SetByteArrayRegion(jmessage_handle, 0, sizeof(btmce_msg_handle_t),
                     (jbyte*) message_handle);

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onGetMessageResult,
                         (jint) session_id, (status == BTMCE_OP_STATUS_OK ?JNI_TRUE:JNI_FALSE),
                         jmessage_handle,jfilepath);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    if (jmessage_handle != NULL)
    {
        sCallbackEnv->DeleteLocalRef(jmessage_handle);
    }
    if (jfilepath != NULL)
    {
        sCallbackEnv->DeleteLocalRef(jfilepath);
    }

}

void push_message_callback( btmce_session_id_t session_id,
                            btmce_op_status_t status,
                            btmce_msg_handle_t message_handle)
{
    jbyteArray jmessage_handle;
    ALOGI("%s: session_id=%d, status=%d",__FUNCTION__, session_id, status);
    CHECK_CALLBACK_ENV
    jmessage_handle = sCallbackEnv->NewByteArray(sizeof(btmce_msg_handle_t));
    if (jmessage_handle == NULL)
    {
        ALOGE("%s: unable to create message id byte array...Aborting...",__FUNCTION__);
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    }
    else
    {
        sCallbackEnv->SetByteArrayRegion(jmessage_handle, 0, sizeof(btmce_msg_handle_t),
                  (jbyte*) message_handle);
        sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onPushMessageResult,
                         (jint) session_id,
                         (status == BTMCE_OP_STATUS_OK ?JNI_TRUE:JNI_FALSE),
                         jmessage_handle);
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    }
    if (jmessage_handle != NULL)
    {
        sCallbackEnv->DeleteLocalRef(jmessage_handle);
    }
}


void update_inbox_callback( btmce_session_id_t session_id,
                            btmce_op_status_t status)
{
    ALOGI("%s: session_id=%d, status=%d",__FUNCTION__, session_id, status);
    CHECK_CALLBACK_ENV

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onUpdateInboxResult,
                         (jint) session_id,
                         (status == BTMCE_OP_STATUS_OK ?JNI_TRUE:JNI_FALSE));
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);

}

void message_status_updated_callback( btmce_session_id_t session_id,
                                      btmce_op_status_t status)
{
    ALOGI("%s: session_id=%d, status=%d",__FUNCTION__, session_id, status);
       CHECK_CALLBACK_ENV

       sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onMessageStatusUpdated,
                            (jint) session_id,
                            (status == BTMCE_OP_STATUS_OK ?JNI_TRUE:JNI_FALSE));
       checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

void notification_registration_updated_callback( btmce_session_id_t session_id,
                                                 btmce_op_status_t status)
{
    ALOGI("%s: session_id=%d, status=%d",__FUNCTION__, session_id, status);
    CHECK_CALLBACK_ENV

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onNotificationRegistrationUpdated,
                            (jint) session_id,
                            (status == BTMCE_OP_STATUS_OK ?JNI_TRUE:JNI_FALSE));
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}


void notification_callback( btmce_instance_id_t instance_id,
                            btmce_session_id_t session_id,
                            btmce_path_t* file_path)
{
    char filepath[MAX_PATH_LENGTH + 1];
    jstring jfilepath;
    ALOGI("%s: instance_id=%d, session_id=%d",__FUNCTION__,
            instance_id, session_id);
    CHECK_CALLBACK_ENV
    filepath[0]='\0';
    if (file_path !=NULL)
    {
        btif_mce_copy_string(filepath,file_path->path,MAX_PATH_LENGTH);
    }
    jfilepath = sCallbackEnv->NewStringUTF(filepath);
    if (!jfilepath)
    {
        ALOGE("Fail to new jstring for notification");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onNotification,
                                (jint) instance_id,
                                (jint) session_id,jfilepath);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    if (jfilepath != NULL)
    {
        sCallbackEnv->DeleteLocalRef(jfilepath);
    }
}

void get_mse_instances_callback(uint8_t size,
                                btmce_mse_instance_info_t* mse_instances)
{
    jobjectArray jmseinfo_array ;
    jmseinfo_t *mseinfolist;
    CHECK_CALLBACK_ENV
    mseinfolist = (jmseinfo_t*) malloc(size* sizeof(jmseinfo_t));
    memset(mseinfolist,0,size* sizeof(jmseinfo_t));
    jmseinfo_array=sCallbackEnv->NewObjectArray(size,cls_MseInfo,NULL);
    ALOGI("%s: size=%d",__FUNCTION__,size);
    for (int i=0; i < size;i++)
    {
        ALOGI("%s: MSE INFO #%d: instance_id=%d, message_types=%d",
                __FUNCTION__,i,mse_instances[i].instance_id, mse_instances[i].message_types);
        create_jmseinfo(sCallbackEnv, &mseinfolist[i],mse_instances[i].instance_id,
                mse_instances[i].message_types,&mse_instances[i].display_name);
        sCallbackEnv->SetObjectArrayElement( jmseinfo_array, i, mseinfolist[i].mse_info);
    }

    //Call java method
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onGetMseInstancesResult,jmseinfo_array);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);

    //Cleanup
    for (int i=0; i < size;i++)
    {
        cleanup_jmseinfo(sCallbackEnv, &mseinfolist[i]);
    }
    free (mseinfolist);

    if (jmseinfo_array != NULL)
    {
        sCallbackEnv->DeleteLocalRef(jmseinfo_array);
    }
}

static btmce_callbacks_t sBluetoothMceCallbacks =
{
    sizeof(sBluetoothMceCallbacks),
    profile_state_callback,
    connection_state_callback,
    notification_server_state_callback,
    notification_connection_state_callback,
    set_folder_path_callback,
    get_folder_list_callback,
    get_message_list_callback,
    get_message_callback,
    push_message_callback,
    update_inbox_callback,
    message_status_updated_callback,
    notification_registration_updated_callback,
    notification_callback,
    get_mse_instances_callback
};

// Define native functions
static void classInitNative(JNIEnv* env, jclass clazz)
{
    method_onProfileStateChanged = env->GetMethodID(clazz,
            "onProfileStateChanged", "(Z)V");
    method_onNotificationServerStateChanged= env->GetMethodID(clazz,
            "onNotificationServerStateChanged", "(ZI)V");
    method_onConnectionStateChanged = env->GetMethodID(clazz, "onConnectionStateChanged",
            "(ZIILjava/lang/String;[B)V");
    method_onNotificationConnectionStateChanged = env->GetMethodID(clazz,
            "onNotificationConnectionStateChanged","(ZILjava/lang/String;[B)V");
    method_onFolderPathSet = env->GetMethodID(clazz,
            "onFolderPathSet", "(IZ)V");
    method_onGetFolderListResult = env->GetMethodID(clazz,
            "onGetFolderListResult", "(IZILjava/lang/String;)V");
    method_onGetMessageListResult = env->GetMethodID(clazz,
            "onGetMessageListResult", "(IZIZLjava/lang/String;)V");
    method_onGetMessageResult = env->GetMethodID(clazz,
             "onGetMessageResult", "(IZ[BLjava/lang/String;)V");
    method_onPushMessageResult = env->GetMethodID(clazz,
             "onPushMessageResult", "(IZ[B)V");
    method_onUpdateInboxResult = env->GetMethodID(clazz,
             "onUpdateInboxResult", "(IZ)V");
    method_onNotificationRegistrationUpdated = env->GetMethodID(clazz,
             "onNotificationRegistrationUpdated", "(IZ)V");
    method_onNotification = env->GetMethodID(clazz,
             "onNotification", "(IILjava/lang/String;)V");
    method_onMessageStatusUpdated = env->GetMethodID(clazz,
             "onMessageStatusUpdated", "(IZ)V");
    method_onGetMseInstancesResult = env->GetMethodID(clazz,
             "onGetMseInstancesResult", "([Lcom/broadcom/bt/map/BluetoothMseInfo;)V");

    /* MseInfo class */
    cls_MseInfo = (jclass) env->NewGlobalRef( env->FindClass(MSEINFO_CLASS));
    method_MseInfo_Constructor = env->GetMethodID(cls_MseInfo, "<init>", "()V");
    field_MseInfo_mServerInstanceId = env->GetFieldID(cls_MseInfo, "mServerInstanceId", "I");
    field_MseInfo_mMessageTypes = env->GetFieldID(cls_MseInfo, "mMessageTypes","I");
    field_MseInfo_mName = env->GetFieldID(cls_MseInfo,"mName","Ljava/lang/String;");

    /* MessageListFilter class*/
    cls_MessageListFilter = (jclass) env->NewGlobalRef( env->FindClass(MESSAGE_FILTER_CLASS));
    field_MessageListFilter_mMsgMask =
            env->GetFieldID(cls_MessageListFilter, "mMsgMask", "B");
    field_MessageListFilter_mMaxListCount =
            env->GetFieldID(cls_MessageListFilter, "mMaxListCount","I");
    field_MessageListFilter_mListStartOffset =
            env->GetFieldID(cls_MessageListFilter, "mListStartOffset", "I");
    field_MessageListFilter_mSubjectLength =
            env->GetFieldID(cls_MessageListFilter, "mSubjectLength", "B");
    field_MessageListFilter_mPeriodBegin =
            env->GetFieldID(cls_MessageListFilter, "mPeriodBegin","Ljava/lang/String;");
    field_MessageListFilter_mPeriodEnd =
            env->GetFieldID(cls_MessageListFilter, "mPeriodEnd","Ljava/lang/String;");
    field_MessageListFilter_mReadStatus =
            env->GetFieldID(cls_MessageListFilter, "mReadStatus","B");
    field_MessageListFilter_mRecipient =
            env->GetFieldID(cls_MessageListFilter, "mRecipient","Ljava/lang/String;");
    field_MessageListFilter_mOriginator =
            env->GetFieldID(cls_MessageListFilter, "mOriginator","Ljava/lang/String;");
    field_MessageListFilter_mPriorityStatus =
            env->GetFieldID(cls_MessageListFilter, "mPriorityStatus", "B");

}


static void initializeNative(JNIEnv *env, jobject object)
{
    const bt_interface_t* btInf;
    bt_status_t status;
    ALOGI("%s: called\n", __FUNCTION__);

    if ((btInf = getBluetoothInterface()) == NULL)
    {
        ALOGE("Bluetooth module is not loaded");
        return;
    }

    if (sBluetoothMceInterface != NULL)
    {
        ALOGW("Cleaning up Bluetooth MAP MCE Interface before initializing...");
        sBluetoothMceInterface->cleanup();
        sBluetoothMceInterface = NULL;
    }

    if (mCallbacksObj != NULL)
    {
        ALOGW("Cleaning up Bluetooth MAP MCE callback object");
        env->DeleteGlobalRef(mCallbacksObj);
        mCallbacksObj = NULL;
    }

    if ((sBluetoothMceInterface =
            (btmce_interface_t *) btInf->get_profile_interface(
                    BT_PROFILE_MAPMCE_ID)) == NULL)
    {
        ALOGE("Failed to get Bluetooth MAP MCE Interface");
        return;
    }

    if ((status = sBluetoothMceInterface->init(&sBluetoothMceCallbacks))
            != BT_STATUS_SUCCESS)
    {
        ALOGE("Failed to initialize Bluetooth MAP MCE, status: %d", status);
        sBluetoothMceInterface = NULL;
        return;
    }

    mCallbacksObj = env->NewGlobalRef(object);
}

static void cleanupNative(JNIEnv *env, jobject object)
{
    const bt_interface_t* btInf;
    bt_status_t status;

    if ((btInf = getBluetoothInterface()) == NULL)
    {
        ALOGE("Bluetooth module is not loaded");
        return;
    }

    if (sBluetoothMceInterface != NULL)
    {
        ALOGW("Cleaning up Bluetooth MAP MCE Interface...");
        sBluetoothMceInterface->cleanup();
        sBluetoothMceInterface = NULL;
    }

    if (mCallbacksObj != NULL)
    {
        ALOGW("Cleaning up Bluetooth MAP MCE callback object");
        env->DeleteGlobalRef(mCallbacksObj);
        mCallbacksObj = NULL;
    }

    env->DeleteGlobalRef(mCallbacksObj);
}


static void startNotificationServerNative(JNIEnv *env, jobject object, jstring jname)
{
    btmce_name_t name;

    if (!sBluetoothMceInterface)
    {
        ALOGE("%s: MAP MCE interface not available", __FUNCTION__);
        return;
    }
    if (!btif_copy_jstring((char*)name.name,MAX_NAME_LENGTH,jname,env))
    {
        ALOGE("%s: Invalid server name specified", __FUNCTION__);
        return;
    }
    name.name[MAX_NAME_LENGTH] = 0;
    sBluetoothMceInterface->start_notification_server(&name);

}
static void stopNotificationServerNative(JNIEnv *env, jobject object)
{
    if (!sBluetoothMceInterface)
    {
        ALOGE("%s: MAP MCE interface not available", __FUNCTION__);
        return;
    }
    sBluetoothMceInterface->stop_notification_server();
}

static void connectNative(JNIEnv *env, jobject object, jbyteArray jaddr, jint jinstanceid)
{
    bt_bdaddr_t bd_addr;
    if (!sBluetoothMceInterface)
    {
        ALOGE("%s: MAP MCE interface not available", __FUNCTION__);
        return;
    }
    if (!btif_copy_jbytearray(bd_addr.address,6,jaddr,env))
    {
        ALOGE("%s: Invalid server address specified", __FUNCTION__);
        return;
    }
    sBluetoothMceInterface->connect(&bd_addr, jinstanceid);
}

static void disconnectNative(JNIEnv *env, jobject object, jint jsessionid)
{
    if (!sBluetoothMceInterface)
    {
        ALOGE("%s: MAP MCE interface not available", __FUNCTION__);
        return;
    }
    sBluetoothMceInterface->disconnect(jsessionid);
}

static void abortOperationNative(JNIEnv *env, jobject object, jbyteArray jaddr, jint jinstanceid)
{
    bt_bdaddr_t bd_addr;
    if (!sBluetoothMceInterface)
    {
        ALOGE("%s: MAP MCE interface not available", __FUNCTION__);
        return;
    }
    if (!btif_copy_jbytearray(bd_addr.address,6,jaddr,env))
    {
        ALOGE("%s: Invalid server address specified", __FUNCTION__);
        return;
    }
    sBluetoothMceInterface->abort_request(&bd_addr,jinstanceid);
}

void setFolderPathNative(JNIEnv *env, jobject object, jint jsessionid, jboolean jsettoroot,
        jstring jfolderpath)
{
    btmce_path_t folderpath;
    if (!btif_copy_jstring((char*)folderpath.path,MAX_PATH_LENGTH,jfolderpath,env))
    {
        ALOGE("%s: Invalid folder path name specified", __FUNCTION__);
    }
    if (!sBluetoothMceInterface)
    {
        ALOGE("%s: MAP MCE interface not available", __FUNCTION__);
        return;
    }
    sBluetoothMceInterface->set_folder_path(jsessionid, (jsettoroot==JNI_TRUE? 1:0),folderpath);
}

static void getFolderListNative(JNIEnv *env, jobject object, jint jsessionid, jint jmaxlength,
        jint jstartoffset)
{
    if (!sBluetoothMceInterface)
    {
        ALOGE("%s: MAP MCE interface not available", __FUNCTION__);
        return;
    }
    sBluetoothMceInterface->get_folder_list(jsessionid, jmaxlength, jstartoffset);
}
static void getMessageListNative(JNIEnv *env, jobject object, jint jsessionid, jint jmaxlength,
        jint jstartoffset, jobject jlist_filter, jlong jparam_filter)
{
    btmce_msg_list_filter_t list_filter;
    if (!sBluetoothMceInterface)
    {
        ALOGE("%s: MAP MCE interface not available", __FUNCTION__);
        return;
    }
    memset(&list_filter,0,sizeof(list_filter));
    set_btmce_msg_filter(&list_filter,env,jlist_filter);

    sBluetoothMceInterface->get_message_list(jsessionid, jmaxlength, jstartoffset,
            &list_filter,(long)jparam_filter);
}

static void getMessageNative(JNIEnv *env, jobject object, jint jsessionid,  jbyteArray jmessage_handle,
        jbyte jcharset, jboolean include_attachments)
{
    if (!sBluetoothMceInterface)
    {
        ALOGE("%s: MAP MCE interface not available", __FUNCTION__);
        return;
    }
    btmce_msg_handle_t message_handle;
    btif_copy_jbytearray(message_handle,MSG_HANDLE_LENGTH,jmessage_handle,env);
    sBluetoothMceInterface->get_message((btmce_session_id_t) jsessionid,
             message_handle,(btmce_charset_t) jcharset,
             include_attachments==JNI_TRUE?1:0);
}

static void pushMessageNative(JNIEnv *env, jobject object, jint jsessionid, jstring jfilepath,
        jbyte jcharset, jboolean jis_retry, jboolean jauto_send)
{
    if (!sBluetoothMceInterface)
    {
        ALOGE("%s: MAP MCE interface not available", __FUNCTION__);
        return;
    }
    btmce_path_t filepath;
    btif_copy_jstring((char*) filepath.path,MAX_PATH_LENGTH,jfilepath,env);
    sBluetoothMceInterface->push_message((btmce_session_id_t) jsessionid,&filepath,
            (btmce_charset_t) jcharset,jis_retry==JNI_TRUE?1:0,
                    jauto_send==JNI_TRUE?1:0);
}

static void updateInboxNative(JNIEnv *env, jobject object, jint jsessionid)
{
    if (!sBluetoothMceInterface)
    {
        ALOGE("%s: MAP MCE interface not available", __FUNCTION__);
        return;
    }
    sBluetoothMceInterface->update_inbox((btmce_session_id_t) jsessionid);
}

static void registerForNotificationNative(JNIEnv *env, jobject object, jint jsessionid)
{
    if (!sBluetoothMceInterface)
    {
        ALOGE("%s: MAP MCE interface not available", __FUNCTION__);
        return;
    }
    sBluetoothMceInterface->register_for_notification((btmce_session_id_t) jsessionid);
}

static void unregisterForNotificationNative(JNIEnv *env, jobject object, jint jsessionid)
{
    if (!sBluetoothMceInterface)
    {
        ALOGE("%s: MAP MCE interface not available", __FUNCTION__);
        return;
    }
    sBluetoothMceInterface->unregister_for_notification((btmce_session_id_t) jsessionid);
}

static void setMessageStatusNative(JNIEnv *env, jobject object, jint jsessionid,
        jbyte jstatus_type, jboolean jis_set, jbyteArray jmessage_handle)
{
    if (!sBluetoothMceInterface)
    {
        ALOGE("%s: MAP MCE interface not available", __FUNCTION__);
        return;
    }
    btmce_msg_handle_t message_handle;
    btif_copy_jbytearray(message_handle,MSG_HANDLE_LENGTH,jmessage_handle,env);
    sBluetoothMceInterface->set_message_status((btmce_session_id_t) jsessionid,
            (btmce_msg_status_t) jstatus_type,message_handle,
            jis_set == JNI_TRUE?1:0);
}
static void getMseInstancesNative(JNIEnv *env, jobject object,jbyteArray jaddr)
{
    bt_bdaddr_t bd_addr;
    if (!sBluetoothMceInterface)
    {
        ALOGE("%s: MAP MCE interface not available", __FUNCTION__);
        return;
    }
    if (!btif_copy_jbytearray(bd_addr.address,6,jaddr,env))
    {
        ALOGE("%s: Invalid server address specified", __FUNCTION__);
        return;
    }
    sBluetoothMceInterface->get_mse_instances(&bd_addr);
}

static JNINativeMethod sMethods[] =
        {
        { "classInitNative", "()V", (void *) classInitNative },
        { "initializeNative", "()V", (void *) initializeNative },
        { "cleanupNative", "()V", (void *) cleanupNative },
        { "startNotificationServerNative", "(Ljava/lang/String;)V",
                (void *) startNotificationServerNative},
        { "stopNotificationServerNative", "()V", (void *) stopNotificationServerNative},
        { "connectNative", "([BI)V", (void *) connectNative},
        { "disconnectNative", "(I)V", (void *) disconnectNative},
        { "abortOperationNative", "([BI)V", (void *) abortOperationNative},
        { "setFolderPathNative", "(IZLjava/lang/String;)V", (void *) setFolderPathNative},
        { "getFolderListNative", "(III)V", (void *) getFolderListNative},
        { "getMessageListNative", "(IIILcom/broadcom/bt/map/BluetoothMessageListFilter;J)V",
                (void *)getMessageListNative},
        { "getMessageNative", "(I[BBZ)V", (void *)getMessageNative},
        { "pushMessageNative", "(ILjava/lang/String;BZZ)V", (void *)pushMessageNative},
        { "updateInboxNative", "(I)V", (void *)updateInboxNative},
        { "registerForNotificationNative", "(I)V", (void *)registerForNotificationNative},
        { "unregisterForNotificationNative", "(I)V", (void *)unregisterForNotificationNative},
        { "setMessageStatusNative", "(IBZ[B)V", (void *)setMessageStatusNative},
        { "getMseInstancesNative", "([B)V",(void *) getMseInstancesNative},
        };

int register_com_broadcom_bluetooth_mce(JNIEnv* env)
{
    return jniRegisterNativeMethods(env,
            "com/broadcom/bt/service/map/MapClientService", sMethods, NELEM(sMethods));
}

}
