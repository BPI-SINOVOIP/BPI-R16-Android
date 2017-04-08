
################################################################################
## configurations.
################################################################################

## conpile tool chain for linux makefile.
# arm-linux-gnueabihf- or arm-none-linux-gnueabi- tool chain
OPTION_CC_GNUEABIHF = 1
OPTION_CC_GNUEABI   = 2

## option for os config.
OPTION_OS_ANDROID = 1
OPTION_OS_LINUX   = 2

## option for os version config.
OPTION_OS_VERSION_ANDROID_4_2 = 1
OPTION_OS_VERSION_ANDROID_4_4 = 2
OPTION_OS_VERSION_ANDROID_5_0 = 3

## option for momory driver config.
OPTION_MEMORY_DRIVER_SUNXIMEM = 1
OPTION_MEMORY_DRIVER_ION      = 2


## option for product config.
OPTION_PRODUCT_PAD      = 1
OPTION_PRODUCT_TVBOX    = 2
OPTION_PRODUCT_OTT_CMCC = 3
OPTION_PRODUCT_IPTV     = 4
OPTION_PRODUCT_DVB      = 5

## option for chip config.
OPTION_CHIP_1623 = 1
OPTION_CHIP_1625 = 2
OPTION_CHIP_1633 = 3
OPTION_CHIP_1651 = 4
OPTION_CHIP_1650 = 5
OPTION_CHIP_1661 = 6
OPTION_CHIP_1667 = 7
OPTION_CHIP_1639 = 8
OPTION_CHIP_1673 = 9
OPTION_CHIP_1680 = 10
OPTION_CHIP_1681 = 11
OPTION_CHIP_1689 = 12
OPTION_CHIP_1701 = 13

## option for dram interface.
OPTION_DRAM_INTERFACE_DDR1_16BITS = 1
OPTION_DRAM_INTERFACE_DDR1_32BITS = 2
OPTION_DRAM_INTERFACE_DDR2_16BITS = 3
OPTION_DRAM_INTERFACE_DDR2_32BITS = 4
OPTION_DRAM_INTERFACE_DDR3_16BITS = 5
OPTION_DRAM_INTERFACE_DDR3_32BITS = 6
OPTION_DRAM_INTERFACE_DDR3_64BITS = 7

## option for debug level.
OPTION_LOG_LEVEL_CLOSE   = 0
OPTION_LOG_LEVEL_ERROR   = 1
OPTION_LOG_LEVEL_WARNING = 2
OPTION_LOG_LEVEL_DEFAULT = 3
OPTION_LOG_LEVEL_DETAIL  = 4

## option for cmcc
OPTION_CMCC_NO  = 0
OPTION_CMCC_YES = 1

## option for is_camera_decoder
OPTION_IS_CAMERA_DECODER_NO  = 0
OPTION_IS_CAMERA_DECODER_YES = 1

## option for ve ipc
OPTION_VE_IPC_DISABLE  = 1
OPTION_VE_IPC_ENABLE = 2

## configuration.
CONFIG_CC = $(OPTION_CC_GNUEABI)
CONFIG_OS = $(OPTION_OS_ANDROID)
CONFIG_OS_VERSION = $(OPTION_OS_VERSION_ANDROID_4_4)
CONFIG_MEMORY_DRIVER = $(OPTION_MEMORY_DRIVER_ION)
CONFIG_PRODUCT = $(OPTION_PRODUCT_PAD)
CONFIG_CHIP = $(OPTION_CHIP_1667)
CONFIG_CMCC = $(OPTION_CMCC_NO)
CONFIG_VE_IPC = $(OPTION_VE_IPC_DISABLE)
CONFIG_IS_CAMERA_DECODER = $(OPTION_IS_CAMERA_DECODER_NO)
ifdef TARGET_BUSINESS_PLATFORM  
    ifeq (cmccwasu , $(TARGET_BUSINESS_PLATFORM))
        CONFIG_CMCC = $(OPTION_CMCC_YES)
    endif
endif
##############################define global val#################################

LIB_AW_PATH := $(TOP)/frameworks/av/media/liballwinner
LAW_CFLAGS :=

SW_DEINTERLACE_FLAGS = $(shell test -d $(LIB_AW_PATH)/LIBRARY/PLAYER/sw-deinterlace;echo $$?)
ifeq ($(SW_DEINTERLACE_FLAGS), 0)
USE_SW_DEINTERLACE := yes
LAW_CFLAGS += -DUSE_SW_DEINTERLACE
endif

USE_NEW_DISPLAY := 0

ifeq ($(CONFIG_CHIP),$(OPTION_CHIP_1667))
	ifeq ($(CONFIG_OS_VERSION),$(OPTION_OS_VERSION_ANDROID_5_0))
    USE_NEW_DISPLAY := 1
    endif
endif

# on A83-pad-5.0
ifeq ($(CONFIG_CHIP),$(OPTION_CHIP_1673))
    ifeq ($(CONFIG_PRODUCT),$(OPTION_PRODUCT_PAD))
        ifeq ($(CONFIG_OS_VERSION),$(OPTION_OS_VERSION_ANDROID_5_0))
        USE_NEW_DISPLAY := 1
        endif
    endif
endif

# on A80-pad-5.0
ifeq ($(CONFIG_CHIP),$(OPTION_CHIP_1639))
    ifeq ($(CONFIG_PRODUCT),$(OPTION_PRODUCT_PAD))
        ifeq ($(CONFIG_OS_VERSION),$(OPTION_OS_VERSION_ANDROID_5_0))
        USE_NEW_DISPLAY := 1
        endif
    endif
endif

# on A80-tvbox-5.0
ifeq ($(CONFIG_CHIP),$(OPTION_CHIP_1639))
    ifeq ($(CONFIG_PRODUCT),$(OPTION_PRODUCT_TVBOX))
        ifeq ($(CONFIG_OS_VERSION),$(OPTION_OS_VERSION_ANDROID_5_0))
        USE_NEW_DISPLAY := 1
        endif
    endif
endif

ifeq ($(CONFIG_CHIP),$(OPTION_CHIP_1689))
    ifeq ($(CONFIG_PRODUCT),$(OPTION_PRODUCT_PAD))
        ifeq ($(CONFIG_OS_VERSION),$(OPTION_OS_VERSION_ANDROID_5_0))
        USE_NEW_DISPLAY := 1
        endif
    endif
endif

# on all secure box
ifeq ($(CONFIG_PRODUCT),$(OPTION_PRODUCT_TVBOX))
    ifeq ($(BOARD_WIDEVINE_OEMCRYPTO_LEVEL), 1)
    USE_NEW_DISPLAY := 1
    endif
endif

ifeq ($(BOARD_WIDEVINE_OEMCRYPTO_LEVEL), 1)
LAW_CFLAGS += -DWIDEVINE_OEMCRYPTO_LEVEL=1
else
LAW_CFLAGS += -DWIDEVINE_OEMCRYPTO_LEVEL=3
endif

CMCC := no
ifeq ($(CONFIG_CMCC), $(OPTION_CMCC_YES))
CMCC := yes
endif

#We surpport display subtitle in cedarx on android4.2 and 4.4.
#but the APIs of skia on android5.0 are much more different,
#so it do not work on android5.0

ENABLE_SUBTITLE_DISPLAY_IN_CEDARX := 0

#0: normal  1:hifi 2:hifi but 16 bit
AUDIO_HIFI_OUTPUT_VERSION_ANDROID_4_4 := 0
###################################end define####################################

