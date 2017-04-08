LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := eng

LOCAL_SRC_FILES := $(call all-subdir-java-files)

LOCAL_PACKAGE_NAME := HIDDeviceApp

LOCAL_JAVA_LIBRARIES := com.broadcom.bt

LOCAL_CERTIFICATE := platform

include $(BUILD_PACKAGE)

