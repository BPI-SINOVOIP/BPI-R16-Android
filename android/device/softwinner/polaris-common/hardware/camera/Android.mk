
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

# 1.libapperceivepeople
include $(CLEAR_VARS)
LOCAL_MODULE := libapperceivepeople
LOCAL_SRC_FILES := libfacedetection/libapperceivepeople.so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_SUFFIX := .so
LOCAL_MULTILIB := 32
include $(BUILD_PREBUILT)

# 1.libfacedetection
include $(CLEAR_VARS)
LOCAL_MODULE := libfacedetection
LOCAL_SRC_FILES := libfacedetection/libfacedetection.so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_SUFFIX := .so
LOCAL_MULTILIB := 32
include $(BUILD_PREBUILT)

# 1.libsmileeyeblink
include $(CLEAR_VARS)
LOCAL_MODULE := libsmileeyeblink
LOCAL_SRC_FILES := libfacedetection/libsmileeyeblink.so
LOCAL_MODULE_CLASS := SHARED_LIBRARIES
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_SUFFIX := .so
LOCAL_MULTILIB := 32
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)

LOCAL_PREBUILT_LIBS := libcnr.so
#LOCAL_PREBUILT_LIBS := libapperceivepeople.so
#LOCAL_PREBUILT_LIBS := libfacedetection.so
#LOCAL_PREBUILT_LIBS := libsmileeyeblink.so

LOCAL_MODULE_TAGS := optional
include $(BUILD_MULTI_PREBUILT)

include $(CLEAR_VARS)

LOCAL_MODULE_PATH := $(TARGET_OUT_SHARED_LIBRARIES)/hw

LOCAL_SHARED_LIBRARIES:= \
    libbinder \
    libutils \
    libcutils \
    libcamera_client \
    libui \
	
# cedarx libraries
LOCAL_SHARED_LIBRARIES += \
	libfacedetection \
	libMemAdapter    \
	libcnr \
	libvencoder \
	libvdecoder
	
LOCAL_C_INCLUDES += 								\
	frameworks/base/core/jni/android/graphics 		\
	frameworks/native/include/media/openmax			\
	hardware/libhardware/include/hardware			\
	frameworks/native/include						\
	frameworks/av/media/liballwinner/LIBRARY/CODEC/VIDEO/ENCODER/include \
	frameworks/av/media/liballwinner/LIBRARY/CODEC/VIDEO/DECODER/include \
	frameworks/native/include/media/hardware \
	system/core/include/camera \
	$(TARGET_HARDWARE_INCLUDE)

LOCAL_SRC_FILES := \
	HALCameraFactory.cpp \
	PreviewWindow.cpp \
	CallbackNotifier.cpp \
	CCameraConfig.cpp \
	BufferListManager.cpp \
	OSAL_Mutex.c \
	OSAL_Queue.c \
	scaler.c


# choose hal for new driver or old
SUPPORT_NEW_DRIVER := Y

ifeq ($(SUPPORT_NEW_DRIVER),Y)
LOCAL_CFLAGS += -DSUPPORT_NEW_DRIVER
LOCAL_SRC_FILES += \
	CameraHardware2.cpp \
	V4L2CameraDevice2.cpp
else
LOCAL_SRC_FILES += \
	CameraHardware.cpp \
	V4L2CameraDevice.cpp
endif

ifneq ($(filter nuclear%,$(TARGET_DEVICE)),)
LOCAL_CFLAGS += -D__SUN5I__
endif

ifneq ($(filter crane%,$(TARGET_DEVICE)),)
LOCAL_CFLAGS += -D__SUN4I__
endif

ifneq ($(filter polaris%,$(TARGET_DEVICE)),)
LOCAL_CFLAGS += -D__SUN6I__
endif

ifneq ($(filter polaris%,$(TARGET_DEVICE)),)
LOCAL_CFLAGS += -D__SUNXI__
endif


LOCAL_MODULE := camera.$(TARGET_BOARD_PLATFORM)

LOCAL_MODULE_TAGS := optional
include $(BUILD_SHARED_LIBRARY)
