"rtw_simple_config" is a tool for configuring the wireless setting of an device(ex: IPCAM), user can install an app to an IOS/Android device, then the app will send some packets to the air, DUT will learn the wireless setting by sniffing those packets.

	Usage	-- 
        rtw_simple_config       -i<ifname> -c<wpa_cfgfile> -n<dev_name> -p <pincode> -P <priv_cmd>
                                -m <phone_mac> [-d] [-v]

	OPTIONS:
        -i = interface name
        -c = the path of wpa_supplicant config file
        -p = pincode
        -P = private command
        -d = enable debug message
        -n = device name
        -m = filtering MAC, only accept the configuration frame which's from this MAC address
        -v = version

	Example:
        rtw_simple_config -i wlan0 -c ./wpa_conf -n RTKSC_SAMPLE -m 10:bf:48:cc:f1:99 -d
        rtw_simple_config -i wlan0 -c ./wpa_conf -p 14825903 -P iwpriv -n RTKSC_SAMPLE
	rtw_simple_config -i wlan0 -c /data/misc/wifi/wpa_supplicant.conf -n RTKSC_SAMPLE -d