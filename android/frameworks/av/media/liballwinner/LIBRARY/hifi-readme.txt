192k_16bit  192k_24bit patch �漰�ļ�:
һ��git��֧:
1.android4.4\device\softwinner\polaris-common
2.android4.4\frameworks\av
3.android4.4\hardware\libhardware_legacy
4.android4.4\system\core

����ϵͳhifi-192k-16bit���÷���:
frameworks\av\media\liballwinner\LIBRARY\config.mk
��Ҫ��AUDIO_HIFI_OUTPUT_VERSION_ANDROID_4_4 : ���ó�2;

ע:192k,24bit���������͵Ĳ���ʱ��������������������ڶ����ɿ����õ����;
#0: normal ��ͨ���ֲ���ģʽ;  1:hifi ֧��192k.24bit,�������Գ���xrun; 2:hifi but 16 bit�ȶ�����;

��192k,24bit˵��:���Ҫ֧��192k��24bit,����ֹxrun,��Ҫ����cpu�ĺ���Ϊ4.����Ϊ���Ƶ��;
r16:
����4����:
cd /sys/kernel/autohotplug
echo 4 > lock
�鿴�Ƿ��������ĸ���:
cd /sys/devices/system/cpu
cat online                         
0-3

�������Ƶ��:
cd /sys/devices/system/cpu/cpu0/cpufreq/
echo performance > scaling_governor
cat scaling_governor

=============================================
�ָ����غ�:
cd /sys/kernel/autohotplug
echo 0 > lock

�ָ���̬��Ƶ��
cd /sys/devices/system/cpu/cpu0/cpufreq/
echo interactive > scaling_governor

�ġ���������ע��:
1.(96k��192k)��hifiģʽ��������������Ȼ��normalģʽ;
  normalģʽ���ڵ����������;
  hifiģʽ���ڵ�Ӳ������:

 ����ǵ�����codec, ��Ҫ����Ӳ������,��Ҫ������չalsa��mixer�ӿ��ṩ��audio_hw������������ڿ���;
 
 