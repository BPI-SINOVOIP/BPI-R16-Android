Instructions for installing Pbap Client.

1. Ensure you have a Bluedroid Plus Release 4.2.
2. Unzip the Pbap Client zip file
3. You will see two .apks, PbapClient-debug.apk and PbapClient-release-unsigned.apk.  You only need to install 1 apk, PbapClient-debug.apk.
4. Install the apk:
adb install -r PbapClient-debug.apk
5. Unzip framework_libs_debug.zip
6. Install the javax.obex.jar into the phone's system/framework directory as follows.
a. adb remount
b. adb push system/framework/javax.obex.jar /system/framewaork/javax.obex.ar
c. adb shell rm /data/dalvik-cache/*javax.obex.jar*
d. adb reboot.


Test Pbap Client.

To test, you use the attached script files to do the following
1. Unzip scripts.zip
2. goto scripts/pbc
3. edit testenvpts.sh and set the BD address to the target PBAP server
4. do a "source testenvpts.sh"

Then...

a. To do a connect
./pbc-connect

b. Disconnect
./pbc-disconnect

c. To do a pullvlist, pullpb,pullvcardentry, run the corresponding script.
It will tell you what arguments you need to provide.....
