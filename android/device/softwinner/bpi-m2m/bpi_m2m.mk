$(call inherit-product, device/softwinner/polaris-common/polaris-common.mk)
$(call inherit-product, frameworks/native/build/tablet-7in-hdpi-1024-dalvik-heap.mk)
#$(call inherit-product, device/softwinner/polaris-common/rild/polaris_3gdongle.mk)

# google pinyin
PRODUCT_PACKAGES += GooglePinyin

# init.rc, kernel
PRODUCT_COPY_FILES += \
	device/softwinner/bpi-m2m/kernel:kernel \
	device/softwinner/bpi-m2m/modules/modules/nand.ko:root/nand.ko \
	device/softwinner/bpi-m2m/init.sun8i.rc:root/init.sun8i.rc \
	device/softwinner/bpi-m2m/ueventd.sun8i.rc:root/ueventd.sun8i.rc \
	device/softwinner/bpi-m2m/initlogo.rle:root/initlogo.rle  \
	device/softwinner/bpi-m2m/media/bootanimation.zip:system/media/bootanimation.zip \
	device/softwinner/bpi-m2m/media/boot.wav:system/media/boot.wav \
	device/softwinner/bpi-m2m/media/initlogo.bmp:system/media/initlogo.bmp \
	device/softwinner/bpi-m2m/fstab.sun8i:root/fstab.sun8i \
	device/softwinner/bpi-m2m/init.recovery.sun8i.rc:root/init.recovery.sun8i.rc

# wifi features
PRODUCT_COPY_FILES += \
    frameworks/native/data/etc/android.hardware.wifi.xml:system/etc/permissions/android.hardware.wifi.xml \
    frameworks/native/data/etc/android.hardware.wifi.direct.xml:system/etc/permissions/android.hardware.wifi.direct.xml \
    frameworks/native/data/etc/android.hardware.bluetooth.xml:system/etc/permissions/android.hardware.bluetooth.xml \
    frameworks/native/data/etc/android.hardware.bluetooth_le.xml:system/etc/permissions/android.hardware.bluetooth_le.xml

#key and tp config file
PRODUCT_COPY_FILES += \
	device/softwinner/bpi-m2m/configs/sunxi-keyboard.kl:system/usr/keylayout/sunxi-keyboard.kl \
	device/softwinner/bpi-m2m/configs/tp.idc:system/usr/idc/tp.idc \
	device/softwinner/bpi-m2m/configs/gsensor.cfg:system/usr/gsensor.cfg

# BPI-M2_Magic
#copy touch and keyboard driver to recovery randisk
PRODUCT_COPY_FILES += \
    device/softwinner/bpi-m2m/modules/modules/disp.ko:obj/disp.ko \
    device/softwinner/bpi-m2m/modules/modules/sunxi-keyboard.ko:obj/sunxi-keyboard.ko \
    device/softwinner/bpi-m2m/modules/modules/lcd.ko:obj/lcd.ko \
    device/softwinner/bpi-m2m/modules/modules/gt82x.ko:obj/gt82x.ko \
    device/softwinner/bpi-m2m/modules/modules/gt818_ts.ko:obj/gt818_ts.ko \
    device/softwinner/bpi-m2m/modules/modules/sw-device.ko:obj/sw-device.ko \
    device/softwinner/bpi-m2m/modules/modules/gt9xxnew_ts.ko:obj/gt9xxnew_ts.ko 

# ap6181/6210/6330 sdio wifi fw and nvram
#$(call inherit-product-if-exists, hardware/broadcom/wlan/firmware/ap6181/device-bcm.mk)
$(call inherit-product-if-exists, hardware/broadcom/wlan/firmware/ap6212/device-bcm.mk)
#$(call inherit-product-if-exists, hardware/broadcom/wlan/firmware/ap6330/device-bcm.mk)

PRODUCT_PACKAGES += bt_vendor.conf \
  libbt-client-api \
  com.broadcom.bt \
  com.broadcom.bt.xml \
  com.dsi.ant.antradio_library \
  com.dsi.ant.antradio_library.xml \
  AntHalService \
  ANTRadioService

#smarklink  airkiss server
PRODUCT_PACKAGES += smartlinkd setup

# BPI-M2_Magic
#Parrot apk
#PRODUCT_PACKAGES += Parrot

#rtl8723bs bt fw and config
#PRODUCT_COPY_FILES += \
# device/softwinner/polaris-common/hardware/realtek/bluetooth/rtl8723bs/firmware/rtl8723b_fw:system/etc/firmware/rtlbt/rtlbt_fw \
# device/softwinner/polaris-common/hardware/realtek/bluetooth/rtl8723bs/firmware/rtl8723b_config:system/etc/firmware/rtlbt/rtlbt_config

#esp8089 wifi firmware
#$(call inherit-product-if-exists, hardware/espressif/wlan/firmware/esp8089/device-esp.mk)

#vold config
PRODUCT_COPY_FILES += \
	device/softwinner/bpi-m2m/recovery.fstab:recovery.fstab 
# camera
PRODUCT_COPY_FILES += \
	device/softwinner/bpi-m2m/configs/camera.cfg:system/etc/camera.cfg \
	device/softwinner/bpi-m2m/configs/media_profiles.xml:system/etc/media_profiles.xml \
	frameworks/native/data/etc/android.hardware.camera.xml:system/etc/permissions/android.hardware.camera.xml \
	frameworks/native/data/etc/android.hardware.camera.front.xml:system/etc/permissions/android.hardware.camera.front.xml \
	frameworks/native/data/etc/android.hardware.usb.accessory.xml:system/etc/permissions/android.hardware.usb.accessory.xml \
	frameworks/native/data/etc/android.hardware.camera.autofocus.xml:system/etc/permissions/android.hardware.camera.autofocus.xml

# BPI-M2_Magic
PRODUCT_PROPERTY_OVERRIDES += \
   	persist.sys.timezone=Asia/Taipei \
	persist.sys.language=EN \
	persist.sys.country=US


#rtl8189es cob set macaddr
#PRODUCT_PACKAGES +=  setmacaddr

PRODUCT_PACKAGES += Bluetooth

#0: always; others: seconds
PRODUCT_PROPERTY_OVERRIDES += \
  	debug.bt.discoverable_time=0
#PRODUCT_PROPERTY_OVERRIDES += \
#	ro.product.8723b_bt.used=true

#GPS Feature
PRODUCT_PACKAGES +=  gps.polaris
BOARD_USES_GPS_TYPE := simulator
PRODUCT_COPY_FILES += frameworks/native/data/etc/android.hardware.location.xml:system/etc/permissions/android.hardware.location.xml

# evb logger
PRODUCT_COPY_FILES += \
       device/softwinner/bpi-m2m/tools/logger.sh:system/bin/logger.sh
       
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
	
#PRODUCT_COPY_FILES += \
#	device/softwinner/polaris-common/rild/init.3gdongle.rc:root/init.sunxi.3gdongle.rc

# 3G Data Card usb modeswitch File
PRODUCT_COPY_FILES += \
	$(call find-copy-subdir-files,*,device/softwinner/polaris-common/rild/usb_modeswitch.d,system/etc/usb_modeswitch.d)
	
#FirAirReceiver support
PRODUCT_PACKAGES += FireairReceiver \
		    android.softwinner.framework.jar \
		    android.softwinner.framework.xml \
		    SoftWinnerService.jar \
		    libsoftwinner_servers \
		    libjni_fireair \
		    librtsp \
		    libupdatesoftwinner
	
PRODUCT_PROPERTY_OVERRIDES += \
		    ro.sw.embeded.telephony = false

# BPI-M2_Magic
PRODUCT_PROPERTY_OVERRIDES += \
	persist.sys.usb.config=mass_storage,adb \
	ro.udisk.lable=BPI-M2M \
	ro.font.scale=1.0 \
	ro.hwa.force=false \
	rw.logger=0 \
	ro.sys.bootfast=true \
	debug.hwc.showfps=0 \
	debug.hwui.render_dirty_regions=false

# BPI-M2_Magic
PRODUCT_PROPERTY_OVERRIDES += \
       ro.sf.lcd_density=320 \
	ro.product.firmware=v1.0rc7


$(call inherit-product-if-exists, device/softwinner/bpi-m2m/modules/modules.mk)

DEVICE_PACKAGE_OVERLAYS := device/softwinner/bpi-m2m/overlay
PRODUCT_CHARACTERISTICS := tablet

# Overrides
PRODUCT_AAPT_CONFIG := xlarge hdpi xhdpi large
PRODUCT_AAPT_PREF_CONFIG := xhdpi

PRODUCT_BRAND  := Allwinner-Tablet
PRODUCT_NAME   := bpi_m2m
PRODUCT_DEVICE := bpi-m2m
PRODUCT_MODEL  := BPI-M2-Magic

include device/softwinner/polaris-common/prebuild/google/products/gms_minimal.mk
