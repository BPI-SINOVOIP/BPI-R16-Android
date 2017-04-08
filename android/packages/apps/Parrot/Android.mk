LOCAL_PATH:= $(call my-dir)

# the library
# ============================================================

include $(CLEAR_VARS)
LOCAL_MODULE_TAGS := optional
LOCAL_CERTIFICATE := platform

LOCAL_STATIC_JAVA_LIBRARIES := llh_com.broadcom.bt \
						yzs-sim-sdk-allinone-1.2.11-33d68b1
#LOCAL_JNI_SHARED_LIBRARIES := libaec \
#						libasrfix \
#						libawpe \
#						libDClient \
#						liblocSDK5 \
#						libnr \
#						libofflinenlujni \
#						libuscasr \
#						libyzstts \
#						libencryptor \
#						libxiamitag \
#						libxmediaplayer_x \
#						libxmediaplayer						
LOCAL_REQUIRED_MODULES := libaec libasrfix libawpe libDClient liblocSDK5 \
							libnr libofflinenlujni libuscasr libyzstts \
							libencryptor libxiamitag libxmediaplayer_x libxmediaplayer
							
LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_PACKAGE_NAME := Parrot
LOCAL_PROGUARD_ENABLED := disabled
include $(BUILD_PACKAGE)

include $(CLEAR_VARS)  
LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := llh_com.broadcom.bt:libs/com.broadcom.bt.jar \
										yzs-sim-sdk-allinone-1.2.11-33d68b1:libs/yzs-sim-sdk-allinone-1.2.11-33d68b1.jar  
										
LOCAL_PREBUILT_LIBS := libaec:libs/armeabi/libaec.so \
						libasrfix:libs/armeabi/libasrfix.so \
						libawpe:libs/armeabi/libawpe.so \
						libDClient:libs/armeabi/libDClient.so \
						liblocSDK5:libs/armeabi/liblocSDK5.so \
						libnr:libs/armeabi/libnr.so \
						libofflinenlujni:libs/armeabi/libofflinenlujni.so \
						libuscasr:libs/armeabi/libuscasr.so \
						libyzstts:libs/armeabi/libyzstts.so \
						libencryptor:libs/armeabi/libencryptor.so \
						libxiamitag:libs/armeabi/libxiamitag.so \
						libxmediaplayer_x:libs/armeabi/libxmediaplayer_x.so \
						libxmediaplayer:libs/armeabi/libxmediaplayer.so 
include $(BUILD_MULTI_PREBUILT) 

include $(call all-makefiles-under,$(LOCAL_PATH))