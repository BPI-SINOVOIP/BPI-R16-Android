L_CFLAGS = -DANDROID_ENV
L_CFLAGS += -DSIMPLE_CONFIG_PBC_SUPPORT
#L_CFLAGS += -DPLATFORM_MSTAR             #mStar platform doesn't support system() function.

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := rtw_simple_config
#LOCAL_STATIC_LIBRARIES := libsc
LOCAL_LDFLAGS = $(LOCAL_PATH)/libsc.a
LOCAL_CFLAGS := $(L_CFLAGS)
LOCAL_SRC_FILES := main.c
LOCAL_C_INCLUDES := $(INCLUDES)
LOCAL_MODULE_TAGS := optional
include $(BUILD_EXECUTABLE)



