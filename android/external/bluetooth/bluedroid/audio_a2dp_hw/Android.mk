LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

ifeq ($(BLUETOOTH_USE_AFBT),false)
LOCAL_SRC_FILES:= \
	audio_a2dp_hw.c

LOCAL_C_INCLUDES+= .

LOCAL_SHARED_LIBRARIES := \
	libcutils liblog

LOCAL_SHARED_LIBRARIES += \
	libpower
else
LOCAL_SRC_FILES := audio.a2dp.default.so
endif  # BLUETOOTH_USE_AFBT == false
LOCAL_MODULE := audio.a2dp.default
LOCAL_MODULE_PATH := $(TARGET_OUT_SHARED_LIBRARIES)/hw

LOCAL_MODULE_TAGS := optional
ifeq ($(BLUETOOTH_USE_AFBT),false)
include $(BUILD_SHARED_LIBRARY)
else
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_SUFFIX := .so
include $(BUILD_PREBUILT)
endif  # BLUETOOTH_USE_AFBT == false
