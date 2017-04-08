/*
 * Copyright (C) 2008 The Android Open Source Project
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

#define LOG_TAG "BatteryService"

#include "JNIHelp.h"
#include "jni.h"
#include <utils/Log.h>
#include <utils/misc.h>

#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <stdlib.h>
#include <errno.h>
#include <unistd.h>
#include <dirent.h>
#include <linux/ioctl.h>
#include <hardware/power.h>
#include <hardware_legacy/power.h>
#include <cutils/android_reboot.h>

namespace android {

#define POWER_SUPPLY_PATH "/sys/class/power_supply"
#define POWER_CHARGE_STATE_PATH "/sys/class/power_supply/battery/chgen"


static void android_server_BatteryService_update(JNIEnv* env, jobject obj)
{
}

static void android_server_BatteryService_setChargeState(JNIEnv* env,jobject obj,jboolean state){
    int fd = open(POWER_CHARGE_STATE_PATH,O_WRONLY);
    if(fd>0){
        if (state==JNI_FALSE)
        {
            ALOGD("setChargeState false %d",state);
            write(fd,"0",1);
        }
        else if (state==JNI_TRUE)
        {
            ALOGD("setChargeState true %d",state);
            write(fd,"1",1);
        }
        else
        {
            ALOGD("error setChargeState %d",state);
        }
        close(fd);
    }
}
static void android_server_BatteryService_shutDownNotFromPMS(){
    ALOGD("android_server_BatteryService_shutDownNotFromPMS");
    acquire_wake_lock(PARTIAL_WAKE_LOCK,"battery");
    //android_reboot(ANDROID_RB_POWEROFF, 0, 0);
}
static JNINativeMethod sMethods[] = {
     /* name, signature, funcPtr */
	{"native_update", "()V", (void*)android_server_BatteryService_update},
    {"native_shutdown","()V",(void*)android_server_BatteryService_shutDownNotFromPMS}, 
    {"native_setChargeState","(Z)V",(void*)android_server_BatteryService_setChargeState},
};

int register_android_server_BatteryService(JNIEnv* env)
{
    
    return jniRegisterNativeMethods(env, "com/android/server/BatteryService", sMethods, NELEM(sMethods));
}

} /* namespace android */
