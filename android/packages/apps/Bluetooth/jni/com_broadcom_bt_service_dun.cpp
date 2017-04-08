/*******************************************************************************
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
 *******************************************************************************/


#define LOG_TAG "BluetoothDunServiceJni"

#define LOG_NDEBUG 0

#define CHECK_CALLBACK_ENV                                                      \
   if (!checkCallbackThread()) {                                                \
       error("Callback: '%s' is not called on the correct thread", __FUNCTION__);\
       return;                                                                  \
   }

#include "com_android_bluetooth.h"
#include "hardware/bt_dun.h"
#include "utils/Log.h"
#include "android_runtime/AndroidRuntime.h"
#include <pthread.h>
#include <sys/prctl.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/wait.h>

#include <cutils/log.h>
#define info(fmt, ...)  ALOGI ("%s(L%d): " fmt,__FUNCTION__, __LINE__,  ## __VA_ARGS__)
#define debug(fmt, ...) ALOGD ("%s(L%d): " fmt,__FUNCTION__, __LINE__,  ## __VA_ARGS__)
#define warn(fmt, ...) ALOGW ("## WARNING : %s(L%d): " fmt "##",__FUNCTION__, __LINE__, ## __VA_ARGS__)
#define error(fmt, ...) ALOGE ("## ERROR : %s(L%d): " fmt "##",__FUNCTION__, __LINE__, ## __VA_ARGS__)
#define asrt(s) if(!(s)) ALOGE ("## %s(L%d): ASSERT %s failed! ##",__FUNCTION__, __LINE__, #s)

#define BROADCOM_BLUETOOTH_DUN_SERVICE_CLASS_PATH  "com/broadcom/bt/service/dun/DunService"


static inline pthread_t create_thread(void *(*start_routine)(void *), void * arg);

namespace android {

static jmethodID method_onConnectStateChanged;
static jmethodID method_onControlStateChanged;

static const btdun_interface_t *sDunIf = NULL;
static jobject mCallbacksObj = NULL;
static JNIEnv *sCallbackEnv = NULL;

static bool checkCallbackThread() {
    sCallbackEnv = getCallbackEnv();

    JNIEnv* env = AndroidRuntime::getJNIEnv();
    if (sCallbackEnv != env || sCallbackEnv == NULL) return false;
    return true;
}

static void control_state_callback(btdun_control_state_t state, bt_status_t error) {
    debug("state:%d, error:%d", state, error);
    CHECK_CALLBACK_ENV
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onControlStateChanged, (jint)state, error);
}
static int rfc_fd = -1;
static int rfc_id = 0;
static int rfc_port_signal;
static int rfc_port_signal_value;
static bt_bdaddr_t curr_dev_addr;
#define TEST_PPP
#ifdef TEST_PPP
/*******************************************************************************
**
** Function         fake_modem
**
** Description     Sets up the modem communication towards remote host to enable PPP startup
**
**
** Returns          void
*******************************************************************************/
#define PPPDCMD "pppd"

#define NOT_READY 0
#define START_PPP 1

#define PPP_FLAGSEQUENCE 0x7e
#define PPP_CONTROL_ESCAPE 0x7d
#define PPP_CONF_REQ 0x1
#define PPP_TERM_REQ 0x5
#define PROT_LCP 0xc021


int fake_modem(const char *p, int len)
{
    debug("in, len:%d", len);
    const char *connect = "\r\nCONNECT\r\n";
    const char *client_server = "CLIENTSERVER\r";
    const char *ok = "\r\nOK\r\n";
    const char *busy = "\r\nBUSY\r\n";
    int done = 0, i;
    if (len <= 0)
        return NOT_READY;

    /* Check if p is a ppp frame, if so start pppd right away */

    for (i = 0; i < len; i++)
    {
        if ((p[i] == PPP_FLAGSEQUENCE))
        {
            if ((len - i) >= 7)
            {
                unsigned short prot;
                unsigned short type;

                /* check for escaped chars */
                if (p[i+2] == PPP_CONTROL_ESCAPE)
                {
                    debug("Found ctrl esc");
                    prot = ((p[i+4]<<8) | (p[i+5]));
                }
                else
                {
                    prot = ((p[i+3]<<8) | (p[i+4]));
                }

                debug("prot : %x", prot);
                if (prot == PROT_LCP)
                {
                    type = (p[i+7]^0x20);

                    if (type == PPP_TERM_REQ)
                    {
                        debug("Found PPP Terminate, restart modem emulator");
                        return NOT_READY;
                    }
                    else if (type == PPP_CONF_REQ)
                    {
                        debug("Found PPP Connect, start pppd");
                        return START_PPP;
                    }
                    else
                    {
                        debug("Unknown PPP LCP type [%d]", type);
                        return START_PPP; /* start pppd anyway */
                    }
                }
                else
                {
                    debug("Unknown PPP prot type: %d", prot);
                    return NOT_READY;
                }
            }
        }
    }

    /* AT commands */

    if (!strncasecmp(p, "ATD", 3)) /* windows standard modem */
    {
        debug("Got ATD");

        /* check if call is active - if so return BUSY to DUN Client */
        //if(btl_cfg_get_call_active_status())
        if(0)
        {
            debug("Call currently active - do not allow DUN connection");
            send(rfc_fd, busy, strlen(busy), 0);
            return NOT_READY;
        }

        /* indicate DCD [DCE] to remote (DTE) before sending connect */
        //bta_dg_ci_control( p_cb->port_handle, BTA_DG_CD, BTA_DG_CD_ON );
        sDunIf->set_modem_state(rfc_id, RFC_CD, RFC_CD_ON);
        send(rfc_fd, connect, strlen(connect), 0);
        debug("Modem connected!");
        return START_PPP;
    }
    else if (!strncasecmp(p, "CLIENT", 6)) /* windows null modem */
    {
        debug("Got CLIENT");
        //tx_data_copy(dp, client_server, strlen(client_server));
        send(rfc_fd, client_server, strlen(client_server), 0);
        debug("Nullmodem connected!");
        return START_PPP;
    }
    else
    {
        debug("Modem emulator replies OK");
        send(rfc_fd, ok, strlen(ok), 0);
    }
    debug("out, NOT_READY, len:%d", len);
    return NOT_READY;
}
static pid_t start_ppp(int fd)
{
    pid_t pid = fork();

    if (pid < 0)
    {
        perror("from fork()");
        return -1;
    }
    else if (pid == 0)
    {
        /* now start actual pppd */
        char btlif_socket[32];
        char ip_addresses[100];
        char *pppd_options[16];
        sprintf(btlif_socket, "%d", fd);

        /* create remote ip address from 172.16.0.xx subnet */
        strcpy(ip_addresses, "172.16.0.1:172.16.0.10");

        int i = 0;

        pppd_options[i++] = "brcm_bt_helper";
        pppd_options[i++] = PPPDCMD;
        pppd_options[i++] = ip_addresses;
        pppd_options[i++] = btlif_socket;
        pppd_options[i++] = NULL;
        int result =  execvp("brcm_bt_helper", pppd_options);
        debug("execvp brcm_bt_helper ret:%d", result);
        if(result < 0)
            error("execvp brcm_bt_helper ret: %d, %s", result, strerror(errno));
    }
    return pid;
}
static void *dun_ppp_rfc_thread(void *arg)
{
    uint8_t buff[1024];
    prctl(PR_SET_NAME, (unsigned long)"dun rfc", 0, 0, 0);
    fd_set read_set;

    /* wait for a connection or socket data, blocking call */
    debug("in");
    while(rfc_fd != -1)
    {
        FD_ZERO(&read_set);
        FD_SET(rfc_fd, &read_set);
        int result = select(rfc_fd+1, &read_set, NULL, NULL, NULL);
        if (result == 0)
        {
            //debug("select timeout");
            continue;
        }
        else if (result < 0 && errno == EINTR)
        {
            debug("select result:%d, errL %s", result, strerror(errno));
            break;
        }
        int ret = recv(rfc_fd, &buff, sizeof(buff), 0);
        debug("received %d bytes from rfc_fd:%d", ret, rfc_fd);
        if(ret <= 0)
        {
            error("recv error,  return:%d", ret);
            close(rfc_fd);
            rfc_fd = -1;
            break;
        }
        else
        {
            if(fake_modem((char const*)buff, ret) == START_PPP)
            {
                debug("starting ppp with rfcomm fd:%d", rfc_fd);
                pid_t pid = start_ppp(rfc_fd);
                int status = 0;
                if(pid >= 0)
                {
                    debug("ppp started, waiting for it exit...");
                    waitpid(pid, &status, 0);
                    debug("ppp exit, close the rfc connection");
                }
                sDunIf->disconnect(&curr_dev_addr);
                if(rfc_fd != -1)
                {
                    int fd = rfc_fd;
                    rfc_fd = -1;
                    close(fd);
                }
                break;
            }
        }
    }
    debug("out");
    return 0;
}

#endif //TEST_PPP



static inline pthread_t create_thread(void *(*start_routine)(void *), void * arg)
{
    pthread_attr_t thread_attr;
    pthread_attr_init(&thread_attr);
    pthread_attr_setdetachstate(&thread_attr, PTHREAD_CREATE_JOINABLE);
    pthread_t thread_id = -1;
    if( pthread_create(&thread_id, &thread_attr, start_routine, arg)!=0 )
    {
        debug("pthread_create : %s", strerror(errno));
        return -1;
    }
    return thread_id;
}
static void connection_state_callback(btdun_connection_state_t state, bt_status_t error, const bt_bdaddr_t *bd_addr,
                                      int param1, int param2) {
    debug("state:%d, param1:%d, param2:%d", state, param1, param2);
    jbyteArray addr;
    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (!addr) {
        error("Fail to new jbyteArray bd addr for dun channel state");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    curr_dev_addr = *bd_addr;
    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte *) bd_addr);
    switch((int)state)
    {
        case BTDUN_STATE_DISCONNECTING:
            debug("BTDUN_STATE_DISCONNECTING");
            break;
        case BTDUN_STATE_DISCONNECTED:
            debug("BTDUN_STATE_DISCONNECTED");
            break;
        case BTDUN_STATE_CONNECTED:
            rfc_id = param1;
            rfc_fd = param2;
            debug("BTDUN_STATE_CONNECTED, rfc_id:%d, rfc_fd:%d", rfc_id, rfc_fd);
            /* on open, force DCE signals CTS and DSR on. this should make BTW/DUN happy */
            sDunIf->set_modem_state(rfc_id, RFC_DTRDSR | RFC_RTSCTS, RFC_DTRDSR_ON | RFC_RTSCTS_ON);
        #ifdef TEST_PPP
            create_thread(dun_ppp_rfc_thread, NULL);
        #endif
            break;
         case BTDUN_STATE_MODEM_CHANGED:
        {
            rfc_port_signal = param1;
            rfc_port_signal_value = param2;
            debug("BTDUN_STATE_MODEM_CHANGED port signal:%x, port_signal_value:%x", param1, param2);
            break;
        }
    }
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onConnectStateChanged, addr, (jint) state,
                                    (jint)error);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    sCallbackEnv->DeleteLocalRef(addr);
}

static btdun_callbacks_t sBluetoothDunCallbacks = {
    sizeof(sBluetoothDunCallbacks),
    control_state_callback,
    connection_state_callback
};

// Define native functions

static void classInitNative(JNIEnv* env, jclass clazz) {
    int err;
    bt_status_t status;

    method_onConnectStateChanged = env->GetMethodID(clazz, "onConnectStateChanged",
                                                    "([BII)V");
    method_onControlStateChanged = env->GetMethodID(clazz, "onControlStateChanged",
                                                    "(II)V");

    info("succeeds");
}
static const bt_interface_t* btIf;

static void initializeNative(JNIEnv *env, jobject object) {
    debug("dun");
    if(btIf)
        return;

    if ( (btIf = getBluetoothInterface()) == NULL) {
        error("Bluetooth module is not loaded");
        return;
    }

    if (sDunIf !=NULL) {
         ALOGW("Cleaning up Bluetooth dun Interface before initializing...");
         sDunIf->cleanup();
         sDunIf = NULL;
    }

    if (mCallbacksObj != NULL) {
         ALOGW("Cleaning up Bluetooth dun callback object");
         env->DeleteGlobalRef(mCallbacksObj);
         mCallbacksObj = NULL;
    }

    if ( (sDunIf = (btdun_interface_t *)
          btIf->get_profile_interface(BT_PROFILE_DUN_ID)) == NULL) {
        error("Failed to get Bluetooth dun Interface");
        return;
    }

    bt_status_t status;
    if ( (status = sDunIf->init(&sBluetoothDunCallbacks)) != BT_STATUS_SUCCESS) {
        error("Failed to initialize Bluetooth dun, status: %d", status);
        sDunIf = NULL;
        return;
    }

    mCallbacksObj = env->NewGlobalRef(object);
}

static void cleanupNative(JNIEnv *env, jobject object) {
    bt_status_t status;
    if (!btIf) return;

    if (sDunIf !=NULL) {
        ALOGW("Cleaning up Bluetooth dun Interface...");
        sDunIf->cleanup();
        sDunIf = NULL;
    }

    if (mCallbacksObj != NULL) {
        ALOGW("Cleaning up Bluetooth dun callback object");
        env->DeleteGlobalRef(mCallbacksObj);
        mCallbacksObj = NULL;
    }
    btIf = NULL;
}

static jboolean enableDunNative(JNIEnv *env, jobject object, jint enable, jint server) {
    bt_status_t status = BT_STATUS_FAIL;
    debug("in");
    jbyte *addr;
    if (sDunIf)
        status = sDunIf->enable(enable, server);
    debug("out");
    return status == BT_STATUS_SUCCESS ? JNI_TRUE : JNI_FALSE;
}

static jboolean connectDunNative(JNIEnv *env, jobject object, jbyteArray address) {
    debug("dun client mode not supported");
    return JNI_FALSE;
}

static jboolean disconnectDunNative(JNIEnv *env, jobject object, jbyteArray address) {
    bt_status_t status;
    jbyte *addr;
    jboolean ret = JNI_TRUE;
    if (!sDunIf) return JNI_FALSE;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        error("Bluetooth device address null");
        return JNI_FALSE;
    }

    if ( (status = sDunIf->disconnect((bt_bdaddr_t *) addr)) !=
         BT_STATUS_SUCCESS) {
        if(rfc_fd != -1) {
            int fd = rfc_fd;
            rfc_fd = -1;
            close(fd);
        }

        error("Failed disconnect dun channel, status: %d", status);
        ret = JNI_FALSE;
    }
    env->ReleaseByteArrayElements(address, addr, 0);

    return ret;
}

static JNINativeMethod sMethods[] = {
    {"classInitNative", "()V", (void *) classInitNative},
    {"initializeNative", "()V", (void *) initializeNative},
    {"cleanupNative", "()V", (void *) cleanupNative},
    {"connectDunNative", "([B)Z", (void *) connectDunNative},
    {"enableDunNative", "(II)Z", (void *) enableDunNative},
    {"disconnectDunNative", "([B)Z", (void *) disconnectDunNative},
    // TBD cleanup
};

int register_com_android_bluetooth_dun(JNIEnv* env)
{
    return jniRegisterNativeMethods(env, BROADCOM_BLUETOOTH_DUN_SERVICE_CLASS_PATH,
                                    sMethods, NELEM(sMethods));
}

}
