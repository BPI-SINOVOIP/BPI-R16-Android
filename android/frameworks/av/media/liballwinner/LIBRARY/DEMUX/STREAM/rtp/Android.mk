LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LIB_ROOT=$(LOCAL_PATH)/../../..
include $(LIB_ROOT)/config.mk
 
DEMUX_PATH = $(LIB_ROOT)/DEMUX

include $(DEMUX_PATH)/STREAM/config.mk

LOCAL_SRC_FILES = \
		$(notdir $(wildcard $(LOCAL_PATH)/*.c))	\
		$(addprefix codec/,$(notdir $(wildcard $(LOCAL_PATH)/codec/*.c)))

LOCAL_C_INCLUDES:= \
    $(DEMUX_PATH)/BASE/include \
    $(DEMUX_PATH)/STREAM/include \
	$(LIB_ROOT)

LOCAL_CFLAGS += $(CDX_CFLAGS)

LOCAL_MODULE_TAGS := optional
LOCAL_PRELINK_MODULE := false

LOCAL_MODULE:= libcdx_rtp_stream

ifeq ($(TARGET_ARCH),arm)
    LOCAL_CFLAGS += -Wno-psabi
endif

include $(BUILD_STATIC_LIBRARY)

