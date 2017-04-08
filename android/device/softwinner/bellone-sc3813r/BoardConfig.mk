# BoardConfig.mk
#
# Product-specific compile-time definitions.
#

include device/softwinner/polaris-common/BoardConfigCommon.mk

# bt default config
BOARD_BLUETOOTH_BDROID_BUILDCFG_INCLUDE_DIR := device/softwinner/bellone-sc3813r/bluetooth

#recovery
TARGET_RECOVERY_UI_LIB := librecovery_ui_bellone_sc3813r

TARGET_NO_BOOTLOADER := true
TARGET_NO_RECOVERY := false
TARGET_NO_KERNEL := false

# wifi and bt configuration
# 1. Wifi Configuration
# 1.1 realtek wifi support 
# 1.1  realtek wifi configuration
#BOARD_WIFI_VENDOR := realtek
ifeq ($(BOARD_WIFI_VENDOR), realtek)
    WPA_SUPPLICANT_VERSION := VER_0_8_X
    BOARD_WPA_SUPPLICANT_DRIVER := NL80211
    BOARD_WPA_SUPPLICANT_PRIVATE_LIB := lib_driver_cmd_rtl
    BOARD_HOSTAPD_DRIVER        := NL80211
    BOARD_HOSTAPD_PRIVATE_LIB   := lib_driver_cmd_rtl

    #SW_BOARD_USR_WIFI := rtl8188eu
    #BOARD_WLAN_DEVICE := rtl8188eu

    SW_BOARD_USR_WIFI := rtl8189es
    BOARD_WLAN_DEVICE := rtl8189es
    
    #SW_BOARD_USR_WIFI := rtl8723bs
    #BOARD_WLAN_DEVICE := rtl8723bs
endif

# 1.2 broadcom wifi support
BOARD_WIFI_VENDOR := broadcom
ifeq ($(BOARD_WIFI_VENDOR), broadcom)
    BOARD_WPA_SUPPLICANT_DRIVER := NL80211
    WPA_SUPPLICANT_VERSION      := VER_0_8_X
    BOARD_WPA_SUPPLICANT_PRIVATE_LIB := lib_driver_cmd_bcmdhd
    BOARD_HOSTAPD_DRIVER        := NL80211
    BOARD_HOSTAPD_PRIVATE_LIB   := lib_driver_cmd_bcmdhd
    BOARD_WLAN_DEVICE           := bcmdhd
    WIFI_DRIVER_FW_PATH_PARAM   := "/sys/module/bcmdhd/parameters/firmware_path"

    SW_BOARD_USR_WIFI := AP6212
endif

#1.3 eag wifi config
#BOARD_WIFI_VENDOR := espressif
ifeq ($(BOARD_WIFI_VENDOR), espressif)
    WPA_SUPPLICANT_VERSION := VER_0_8_X
    BOARD_WPA_SUPPLICANT_DRIVER := NL80211
    BOARD_WPA_SUPPLICANT_PRIVATE_LIB := lib_driver_cmd_esp
    BOARD_HOSTAPD_DRIVER        := NL80211
    BOARD_HOSTAPD_PRIVATE_LIB   := lib_driver_cmd_esp

    SW_BOARD_USR_WIFI := esp8089
    BOARD_WLAN_DEVICE := esp8089

endif

# 2. Bluetooth Configuration
# make sure BOARD_HAVE_BLUETOOTH is true for every bt vendor
BOARD_HAVE_BLUETOOTH := true
BOARD_HAVE_BLUETOOTH_BCM := true
SW_BOARD_HAVE_BLUETOOTH_NAME := ap6212
#SW_BOARD_HAVE_BLUETOOTH_NAME := ap6330
#BOARD_HAVE_BLUETOOTH_RTK := true
#BLUETOOTH_HCI_USE_RTK_H5 := true
#SW_BOARD_HAVE_BLUETOOTH_NAME := rtl8723bs

BLUETOOTH_USE_AFBT := true
# boostup code will on command to set CPU roomage and DDR freq
# default enable
TARGET_USE_BOOSTUP_OPZ := true
