192k_16bit  192k_24bit patch 涉及文件:
一、git分支:
1.android4.4\device\softwinner\polaris-common
2.android4.4\frameworks\av
3.android4.4\hardware\libhardware_legacy
4.android4.4\system\core

二、系统hifi-192k-16bit配置方法:
frameworks\av\media\liballwinner\LIBRARY\config.mk
需要将AUDIO_HIFI_OUTPUT_VERSION_ANDROID_4_4 : 配置成2;

注:192k,24bit存在数据送的不及时的情况。所以配置项现在都做成可配置的情况;
#0: normal 普通音乐播放模式;  1:hifi 支持192k.24bit,但概率性出现xrun; 2:hifi but 16 bit稳定运行;

三192k,24bit说明:如果要支持192k、24bit,并防止xrun,需要限制cpu的核数为4.调整为最大频率;
r16:
限制4个核:
cd /sys/kernel/autohotplug
echo 4 > lock
查看是否限制了四个核:
cd /sys/devices/system/cpu
cat online                         
0-3

限制最大频率:
cd /sys/devices/system/cpu/cpu0/cpufreq/
echo performance > scaling_governor
cat scaling_governor

=============================================
恢复开关核:
cd /sys/kernel/autohotplug
echo 0 > lock

恢复动态调频：
cd /sys/devices/system/cpu/cpu0/cpufreq/
echo interactive > scaling_governor

四、调节音量注意:
1.(96k，192k)是hifi模式，其它采样率仍然走normal模式;
  normal模式调节的是软件音量;
  hifi模式调节的硬件音量:

 如果是第三方codec, 需要调节硬件音量,需要自行扩展alsa的mixer接口提供给audio_hw层进行音量调节控制;
 
 