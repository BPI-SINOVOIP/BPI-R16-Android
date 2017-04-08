PBAP_CLIENT_SERVICE=com.broadcom.bt.pbap/com.broadcom.bt.pbap.PbapClientTestService
PBAP_CLIENT_ACTION_PREFIX=com.broadcom.bt.pbap
EXTRA_BDADDR=bdaddr

export setenv PBAP_SERVER=$1
export setenv PBAP_CLIENT_SERVICE_API="adb shell am startservice -a $PBAP_CLIENT_ACTION_PREFIX.\$PBAP_CLIENT_ACTION  --es $EXTRA_BDADDR $PBAP_SERVER $PBAP_CLIENT_SERVICE"

echo PBAP_SERVER= $PBAP_SERVER

#PBAP_CLIENT_ACTION="<action>"
#TEST_CMD=`eval "echo $PBAP_CLIENT_SERVICE_API"`
#echo $TEST_CMD
