LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_SRC_FILES := ../../../../../device/softwinner/$(basename $(TARGET_DEVICE))/bluetooth/bt_vendor.conf

LOCAL_MODULE := bt_vendor.conf
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_PATH := $(TARGET_OUT)/etc/bluetooth

LOCAL_MODULE_TAGS := optional

include $(BUILD_PREBUILT)

