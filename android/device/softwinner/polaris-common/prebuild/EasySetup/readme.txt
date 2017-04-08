---v1.0  2015.7.29  henrisk
1、easysetupserver为服务
2、libeasysetup_jni.so 为应用的jni库，已经默认集成到sdk中
3、java下的为java接口，在应用工程中假如此包即可调用接口

java接口说明：
	目前支持AP6210、AP6212（Broadcom BCM43362）的Cooee Airkiss 物联协议。
	其他模组和协议会持续更新支持。

设置开始接口：
setEasysetupProtol(int protocol)：这个接口为设置协议类型。参数见BroadcomEasysetup的Protocolsxxx 常量

startEasySetup()：开始检测解析，非阻塞调用。

状态回调接口：
	public interface EasysetupListener{
		public void onEasysetupStart();                     //真正开始扫描广播信息
		public void onLinktoAPStart();                      //解析账号密码成功，开始连接路由
		public void onLinktoAPFinished();                   //连接路由成功
		public void onSendUDPBroadcastStart();              //Airkiss需要发送udp广播
		public void onSendUDPBroadcastFinished();           //发送广播完成
		public void onEasysetupFinished(EasysetupInfo info);//整体完成，账号密码送到应用
	}

----------------------------------------------------------------------------------------------------------------



