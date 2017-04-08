/*
 * Copyright (C) 2011 The Android Open Source Project
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

#include <errno.h>
#include <fcntl.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>

#include <unistd.h>

#include <cutils/klog.h>
#include <cutils/log.h>

#define LED_BRIGHTNESS_OFF 0
#define LED_BRIGHTNESS_MAX 255

#define ALPHA_MASK 0xff000000
#define COLOR_MASK 0x00ffffff

#define ARRAY_SIZE(x) (sizeof(x) / sizeof((x)[0]))

#define LOGE(x...) do { KLOG_ERROR("charger", x); } while (0)
#define LOGI(x...) do { KLOG_INFO("charger", x); } while (0)
#define LOGV(x...) do { KLOG_DEBUG("charger", x); } while (0)

static unsigned int leds_exist = 0x00000000;

const char *const LED_DIR = "/sys/class/leds";
const char *const LED_BRIGHTNESS_FILE = "brightness";
const char *const LED_DELAY_ON_FILE = "delay_on";
const char *const LED_DELAY_OFF_FILE = "delay_off";
const char *const LED_TRIGGER_FILE = "trigger";
const char* const led_name[3] = {"red_led","green_led","blue_led"};

enum LED_NAME {
        RED,
        GREEN,
        BLUE,
};

enum LED_STATE {
        OFF,
        ON,
        BLINK,
};

struct sunxi_led_info {
        unsigned int color;
        unsigned int delay_on;
        unsigned int delay_off;
        enum LED_STATE state[3];
};

int porbe_light_leds(void)
{
        char path[PATH_MAX];
        int fd = 123,err;
        enum LED_NAME i = RED;

        if(leds_exist & 0xff000000)
                return leds_exist;
        else
                leds_exist |= 0xff000000;

        LOGI("%s leds_exist=%u\n", __FUNCTION__,leds_exist);
        usleep(100000);
        for(i = RED;i <= BLUE;i++){
                sprintf(path, "%s/%s/%s", LED_DIR,led_name[i],LED_BRIGHTNESS_FILE);
                LOGI("%s path=%s\n", __FUNCTION__,path);
                fd = open(path,O_WRONLY);
                err = -errno;
                if(fd != -1){
                        leds_exist |= 0xff<<(16-8*i);
                        close(fd);
                        fd = -1;
                }
                else{
                        leds_exist |= 0x0<<(16-8*i);
                }
        }
        LOGI("%s leds_exist=%u\n", __FUNCTION__,leds_exist);
        return leds_exist;
}

static int led_sysfs_write(char *buf, const char *path, char *format, ...)
{
        int fd;
        int err;
        int len;
        va_list args;
        struct timespec timeout;
        int ret;
        
        fd = open(path, O_WRONLY);
        err = -errno;
        if (fd < 0) {
                        return err;
        }

        va_start(args, format);
        len = vsprintf(buf, format, args);
        va_end(args);

        if (len < 0)
                return len;

        err = write(fd, buf, len);
        if (err == -1)
                return -errno;

        err = close(fd);
        if (err == -1)
                return -errno;

        return 0;
}

int setLight(int colorARGB, int flashMode, int onMS,
       int offMS, int brightnessMode)
{
        char buf[20];
        int err = 0;
        enum LED_NAME i = RED;
        char path_name[PATH_MAX];
        unsigned int color;
        unsigned int ledcolor[3];
        struct sunxi_led_info leds;

        if (onMS < 0 || offMS < 0)
        	return -EINVAL;

        leds.delay_off = offMS;
        leds.delay_on = onMS;

        if (!colorARGB) {
        	leds.state[RED] = OFF;
        	leds.state[GREEN] = OFF;
        	leds.state[BLUE] = OFF;
        }

        color = colorARGB & COLOR_MASK;
        
        ledcolor[RED] = (color >> 16) & 0x000000ff;
        ledcolor[GREEN] = (color >> 8) & 0x000000ff;
        ledcolor[BLUE] = color & 0x000000ff;

        for(i = RED;i <= BLUE;i++){
                if (ledcolor[i] == 0) {
                	leds.state[i] = OFF;
                }
                else{
                        if (flashMode != 0 && leds.delay_on && leds.delay_off)
                                leds.state[i] = BLINK;
                        else
                                leds.state[i] = ON;
                }
                LOGI("%s set leds state=%d!flashMode=%d color=0x%x offMS=%d onMS=%d\n", __FUNCTION__,leds.state[i],flashMode,colorARGB,offMS,onMS);
        }


        for(i = RED;i <= BLUE;i++){
        if(((leds_exist >> (16-8*i)) & 0x000000ff) == 0)
                continue;
        switch(leds.state[i]) {
                case OFF:
                        sprintf(path_name, "%s/%s/%s", LED_DIR,led_name[i],LED_BRIGHTNESS_FILE);
                        err = led_sysfs_write(buf,path_name, "%d",LED_BRIGHTNESS_OFF);
                        if (err)
                                goto err_write_fail;
                        err = sprintf(path_name, "%s/%s/%s", LED_DIR,led_name[i],LED_TRIGGER_FILE);
                        err = led_sysfs_write(buf, path_name, "%s", "none");
                        if (err)
                                goto err_write_fail;
                        break;
                case BLINK:
                        err = sprintf(path_name, "%s/%s/%s", LED_DIR,led_name[i],LED_TRIGGER_FILE);
                        err = led_sysfs_write(buf, path_name, "%s", "timer");
                        if (err)
                                goto err_write_fail;
                        sprintf(path_name, "%s/%s/%s", LED_DIR,led_name[i],LED_DELAY_ON_FILE);
                        err = led_sysfs_write(buf, path_name, "%u", leds.delay_on);
                        if (err)
                                goto err_write_fail;
                        sprintf(path_name, "%s/%s/%s", LED_DIR,led_name[i],LED_DELAY_OFF_FILE);
                        err = led_sysfs_write(buf, path_name, "%u", leds.delay_off);
                        if (err)
                                goto err_write_fail;
                case ON:
                        sprintf(path_name, "%s/%s/%s", LED_DIR,led_name[i],LED_BRIGHTNESS_FILE);
                        err = led_sysfs_write(buf, path_name, "%d",LED_BRIGHTNESS_MAX);
                        if (err)
                                goto err_write_fail;
                default:
                        break;
                }
        }

err_write_fail:
        return err;
}


