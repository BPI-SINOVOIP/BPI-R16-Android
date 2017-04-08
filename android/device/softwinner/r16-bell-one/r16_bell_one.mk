$(call inherit-product, device/softwinner/polaris-common/polaris-common.mk)
$(call inherit-product, frameworks/native/build/tablet-7in-hdpi-1024-dalvik-heap.mk)
#$(call inherit-product, device/softwinner/polaris-common/rild/polaris_3gdongle.mk)

# google pinyin
PRODUCT_PACKAGES += GooglePinyin

# init.rc, kernel
PRODUCT_COPY_FILES += \
	device/softwinner/r16-bell-one/kernel:kernel \
	device/softwinner/r16-bell-one/modules/modules/nand.ko:root/nand.ko \
	device/softwinner/r16-bell-one/init.sun8i.rc:root/init.sun8i.rc \
	device/softwinner/r16-bell-one/ueventd.sun8i.rc:root/ueventd.sun8i.rc \
	device/softwinner/r16-bell-one/initlogo.rle:root/initlogo.rle  \
	device/softwinner/r16-bell-one/media/bootanimation.zip:system/media/bootanimation.zip \
	device/softwinner/r16-bell-one/media/boot.wav:system/media/boot.wav \
	device/softwinner/r16-bell-one/media/initlogo.bmp:system/media/initlogo.bmp \
	device/softwinner/r16-bell-one/fstab.sun8i:root/fstab.sun8i \
	device/softwinner/r16-bell-one/init.recovery.sun8i.rc:root/init.recovery.sun8i.rc

# wifi features
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.wifi.xml:system/etc/permissions/android.hardware.wifi.xml \
    frameworks/native/data/etc/android.hardware.wifi.direct.xml:system/etc/permissions/android.hardware.wifi.direct.xml \
    frameworks/native/data/etc/android.hardware.bluetooth.xml:system/etc/permissions/android.hardware.bluetooth.xml \
    frameworks/native/data/etc/android.hardware.bluetooth_le.xml:system/etc/permissions/android.hardware.bluetooth_le.xml

#key and tp config file
PRODUCT_COPY_FILES += \
	device/softwinner/r16-bell-one/configs/sunxi-keyboard.kl:system/usr/keylayout/sunxi-keyboard.kl \
	device/softwinner/r16-bell-one/configs/tp.idc:system/usr/idc/tp.idc \
	device/softwinner/r16-bell-one/configs/gsensor.cfg:system/usr/gsensor.cfg

#copy touch and keyboard driver to recovery randisk
PRODUCT_COPY_FILES += \
    device/softwinner/r16-bell-one/modules/modules/disp.ko:obj/disp.ko \
    device/softwinner/r16-bell-one/modules/modules/sunxi-keyboard.ko:obj/sunxi-keyboard.ko \
    device/softwinner/r16-bell-one/modules/modules/lcd.ko:obj/lcd.ko \
    device/softwinner/r16-bell-one/modules/modules/gt82x.ko:obj/gt82x.ko \
    device/softwinner/r16-bell-one/modules/modules/gt818_ts.ko:obj/gt818_ts.ko \
    device/softwinner/r16-bell-one/modules/modules/sw-device.ko:obj/sw-device.ko

# ap6181/6210/6330 sdio wifi fw and nvram
#$(call inherit-product-if-exists, hardware/broadcom/wlan/firmware/ap6181/device-bcm.mk)
$(call inherit-product-if-exists, hardware/broadcom/wlan/firmware/ap6210/device-bcm.mk)
#$(call inherit-product-if-exists, hardware/broadcom/wlan/firmware/ap6330/device-bcm.mk)

#rtl8723bs bt fw and config
$(call inherit-product, hardware/realtek/bluetooth/rtl8723bs/firmware/rtlbtfw_cfg.mk)

#esp8089 wifi firmware
#$(call inherit-product-if-exists, hardware/espressif/wlan/firmware/esp8089/device-esp.mk)

#vold config
PRODUCT_COPY_FILES += \
	device/softwinner/r16-bell-one/recovery.fstab:recovery.fstab 
# camera
PRODUCT_COPY_FILES += \
	device/softwinner/r16-bell-one/configs/camera.cfg:system/etc/camera.cfg \
	device/softwinner/r16-bell-one/configs/media_profiles.xml:system/etc/media_profiles.xml \
	frameworks/native/data/etc/android.hardware.camera.xml:system/etc/permissions/android.hardware.camera.xml \
	frameworks/native/data/etc/android.hardware.camera.front.xml:system/etc/permissions/android.hardware.camera.front.xml \
	frameworks/native/data/etc/android.hardware.usb.accessory.xml:system/etc/permissions/android.hardware.usb.accessory.xml \
	frameworks/native/data/etc/android.hardware.camera.autofocus.xml:system/etc/permissions/android.hardware.camera.autofocus.xml

PRODUCT_PROPERTY_OVERRIDES += \
	persist.sys.timezone=Asia/Shanghai \
	persist.sys.language=zh \
	persist.sys.country=CN

#rtl8189es cob set macaddr
#PRODUCT_PACKAGES +=  setmacaddr

PRODUCT_PACKAGES += Bluetooth

PRODUCT_PROPERTY_OVERRIDES += \
	ro.product.8723b_bt.used=true

#GPS Feature
PRODUCT_PACKAGES +=  gps.polaris
BOARD_USES_GPS_TYPE := simulator
PRODUCT_COPY_FILES += frameworks/native/data/etc/android.hardware.location.xml:system/etc/permissions/android.hardware.location.xml

# evb logger
PRODUCT_COPY_FILES += \
       device/softwinner/r16-bell-one/tools/logger.sh:system/bin/logger.sh
       
# 3G Data Card Packages
PRODUCT_PACKAGES += \
	u3gmonitor \
	chat \
	rild \
	pppd

# 3G Data Card Configuration Flie
PRODUCT_COPY_FILES += \
	device/softwinner/polaris-common/rild/ip-down:system/etc/ppp/ip-down \
	device/softwinner/polaris-common/rild/ip-up:system/etc/ppp/ip-up \
	device/softwinner/polaris-common/rild/3g_dongle.cfg:system/etc/3g_dongle.cfg \
	device/softwinner/polaris-common/rild/usb_modeswitch:system/bin/usb_modeswitch \
	device/softwinner/polaris-common/rild/call-pppd:system/xbin/call-pppd \
	device/softwinner/polaris-common/rild/usb_modeswitch.sh:system/xbin/usb_modeswitch.sh \
	device/softwinner/polaris-common/rild/apns-conf_sdk.xml:system/etc/apns-conf.xml \
	device/softwinner/polaris-common/rild/libsoftwinner-ril.so:system/lib/libsoftwinner-ril.so
	
#wifi tool	
PRODUCT_PACKAGES += \
	libiw \
	iwpriv \
	iwlist \
	iwconfig

#realtek smarklink demo
PRODUCT_PACKAGES += \
	rtw_simple_config

#PRODUCT_COPY_FILES += \
#	device/softwinner/polaris-common/rild/init.3gdongle.rc:root/init.sunxi.3gdongle.rc

# 3G Data Card usb modeswitch File
PRODUCT_COPY_FILES += \
	$(call find-copy-subdir-files,*,device/softwinner/polaris-common/rild/usb_modeswitch.d,system/etc/usb_modeswitch.d)
	
	
	
PRODUCT_PROPERTY_OVERRIDES += \
		    ro.sw.embeded.telephony = false

PRODUCT_PROPERTY_OVERRIDES += \
	persist.sys.usb.config=mass_storage,adb \
	ro.udisk.lable=Polaris \
	ro.font.scale=1.0 \
	ro.hwa.force=false \
	rw.logger=0 \
	ro.sys.bootfast=true \
	debug.hwc.showfps=0 \
	debug.hwui.render_dirty_regions=false

PRODUCT_PROPERTY_OVERRIDES += \
       ro.sf.lcd_density=213 \
	ro.product.firmware=v1.0rc7


$(call inherit-product-if-exists, device/softwinner/r16-bell-one/modules/modules.mk)

DEVICE_PACKAGE_OVERLAYS := device/softwinner/r16-bell-one/overlay
PRODUCT_CHARACTERISTICS := tablet

# Overrides
PRODUCT_AAPT_CONFIG := xlarge hdpi xhdpi large
PRODUCT_AAPT_PREF_CONFIG := xhdpi

PRODUCT_BRAND  := Allwinner-Tablet
PRODUCT_NAME   := r16_bell_one
PRODUCT_DEVICE := r16-bell-one
PRODUCT_MODEL  := Allwinner-Tablet

include device/softwinner/polaris-common/prebuild/google/products/gms_base.mk
