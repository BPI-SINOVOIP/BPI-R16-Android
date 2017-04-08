# Copyright (C) 2011 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

ifeq ($(SW_BOARD_HAVE_3G), true)

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE := libaudio_ril

LOCAL_MODULE_PATH := $(TARGET_OUT_SHARED_LIBRARIES)

LOCAL_SRC_FILES := audio_ril.c \
				   audio_ril_huawei_mu509.c \
				   audio_ril_oviphone_em55.c \
				   audio_ril_longcheer_wm5608.c

LOCAL_C_INCLUDES += \
	device/softwinner/polaris-common/hardware/audio

LOCAL_SHARED_LIBRARIES += liblog libcutils

ifeq ($(SW_MODEM_PRODUCT),yuga_cwm600)
LOCAL_SHARED_LIBRARIES += libygplugin
else
LOCAL_SRC_FILES += audio_ril_yuga_cwm600.c
endif

LOCAL_MODULE_TAGS := optional

include $(BUILD_SHARED_LIBRARY)

endif
