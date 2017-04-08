LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    CTC_MediaProcessorImpl.cpp \
    IPTVcache.cpp \

IPTV_DEBUG := Y

ifeq ($(IPTV_DEBUG), Y)
LOCAL_C_INCLUDES  := \
    $(LOCAL_PATH)/../../CODEC/VIDEO/DECODER/include \
    $(LOCAL_PATH)/../../CODEC/AUDIO/DECODER/include \
    $(LOCAL_PATH)/../../CODEC/SUBTITLE/DECODER/include \
	$(LOCAL_PATH)/../../PLAYER/include \
	$(LOCAL_PATH)/../demux/ \
	$(LOCAL_PATH)/../include/  \
	$(LOCAL_PATH)/../../   \
	$(LOCAL_PATH)/../../DEMUX/BASE/include      \

else
LOCAL_C_INCLUDES  := \
    $(LOCAL_PATH)/../include/ 
endif        

LOCAL_SHARED_LIBRARIES := \
    libcutils \
    libutils \
    libbinder \
    liblog \
    libui \
    libgui \
    libdl \
    libmedia \
    libplayer \
    libtvdemux 

LOCAL_MODULE := libCTC_MediaProcessor

LOCAL_PRELINK_MODULE := false

LOCAL_CFLAGS := -DMSTAR_MM_PLAYER=1 -g
LOCAL_CFLAGS += -DUSE_ANDROID_OVERLAY
LOCAL_CFLAGS += -DAndroid_4

LOCAL_MODULE_TAGS := optional

include $(BUILD_SHARED_LIBRARY)
