LOCAL_PATH := $(call my-dir)

# the library
# ============================================================
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
	$(call all-subdir-java-files) \
	src/com/broadcom/bt/service/ftp/IBluetoothFTP.aidl \
	src/com/broadcom/bt/service/ftp/IBluetoothFTPCallback.aidl \
	src/com/broadcom/bt/map/IBluetoothMapClient.aidl \
	src/com/broadcom/bt/map/IBluetoothMapClientCallback.aidl \
	src/com/broadcom/bt/service/sap/IBluetoothSap.aidl \
	src/com/broadcom/bt/service/dun/IBluetoothDun.aidl \
	src/com/broadcom/fm/fmreceiver/IFmReceiverService.aidl\
	src/com/broadcom/fm/fmreceiver/IFmReceiverCallback.aidl \
	src/com/broadcom/bt/service/hidd/IBluetoothHidDevice.aidl \
	src/com/broadcom/bt/service/hidd/IBluetoothHidDeviceCallback.aidl \
	src/com/broadcom/bt/avrcp/IBluetoothAvrcpController.aidl \
	src/com/broadcom/bt/avrcp/IBluetoothAvrcpControllerCallback.aidl \
	src/com/broadcom/bt/hfdevice/IBluetoothHfDevice.aidl \
	src/com/broadcom/bt/hfdevice/IBluetoothHfDeviceCallback.aidl


LOCAL_MODULE_TAGS := optional

# This is the target being built.
LOCAL_MODULE:= com.broadcom.bt

LOCAL_JAVA_LIBRARIES += javax.obex

include $(BUILD_JAVA_LIBRARY)

# Install permissions for this shared jar
# ====================================================================
include $(CLEAR_VARS)

LOCAL_MODULE := com.broadcom.bt.xml

LOCAL_MODULE_TAGS := optional

LOCAL_MODULE_CLASS := ETC

LOCAL_MODULE_PATH := $(TARGET_OUT_ETC)/permissions

LOCAL_SRC_FILES := $(LOCAL_MODULE)

include $(BUILD_PREBUILT)

include $(call all-makefiles-under,$(LOCAL_PATH))

