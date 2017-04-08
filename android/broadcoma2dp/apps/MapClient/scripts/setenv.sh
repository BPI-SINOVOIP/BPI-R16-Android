export setenv MCE_DEBUG=1
MCE_SERVICE=com.broadcom.bt.map/com.broadcom.bt.map.mce.MapClientTestService
MCE_ACTION_PREFIX=com.broadcom.bt.map.mce.
EXTRA_CLIENT_ID=CLIENT_ID
export setenv MCE_SERVICE_API="adb shell am startservice -a $MCE_ACTION_PREFIX\$MCE_ACTION $MCE_SERVICE"

