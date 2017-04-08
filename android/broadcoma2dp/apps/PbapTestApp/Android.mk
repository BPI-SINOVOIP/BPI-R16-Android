LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := eng

LOCAL_SRC_FILES := \
        $(call all-java-files-under, src)

LOCAL_PACKAGE_NAME := PbapClientTestApp

LOCAL_JAVA_LIBRARIES := com.broadcom.bt

LOCAL_STATIC_JAVA_LIBRARIES := \
    com.android.vcard \

LOCAL_PROGUARD_ENABLED := disabled

include $(BUILD_PACKAGE)

include $(call all-makefiles-under,$(LOCAL_PATH))