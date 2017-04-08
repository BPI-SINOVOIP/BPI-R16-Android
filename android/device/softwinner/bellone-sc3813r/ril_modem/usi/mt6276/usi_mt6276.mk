# USI 3G usi6276 Configuration Flie
PRODUCT_COPY_FILES += \
	device/softwinner/bellone-sc3813r/ril_modem/usi/mt6276/ppp/chat:system/bin/chat \
	device/softwinner/bellone-sc3813r/ril_modem/usi/mt6276/ppp/call-pppd:system/etc/ppp/call-pppd \
	device/softwinner/bellone-sc3813r/ril_modem/usi/mt6276/ppp/ip-down:system/etc/ppp/ip-down \
	device/softwinner/bellone-sc3813r/ril_modem/usi/mt6276/ppp/ip-up:system/etc/ppp/ip-up \
	device/softwinner/bellone-sc3813r/ril_modem/usi/mt6276/ppp/ip-up-vpn:system/etc/ppp/ip-up-vpn \
	device/softwinner/bellone-sc3813r/ril_modem/usi/mt6276/apns-conf_sdk.xml:system/etc/apns-conf.xml \
	device/softwinner/bellone-sc3813r/ril_modem/usi/mt6276/spn-conf.xml:system/etc/spn-conf.xml \
	device/softwinner/bellone-sc3813r/ril_modem/usi/mt6276/usiuna-ril.so:system/lib/usiuna-ril.so

PRODUCT_COPY_FILES += \
	device/softwinner/bellone-sc3813r/ril_modem/usi/mt6276/init.usi_mt6276.rc:root/init.sun6i.3g.rc
