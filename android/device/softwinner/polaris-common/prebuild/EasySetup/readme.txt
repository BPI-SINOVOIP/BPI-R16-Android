---v1.0  2015.7.29  henrisk
1��easysetupserverΪ����
2��libeasysetup_jni.so ΪӦ�õ�jni�⣬�Ѿ�Ĭ�ϼ��ɵ�sdk��
3��java�µ�Ϊjava�ӿڣ���Ӧ�ù����м���˰����ɵ��ýӿ�

java�ӿ�˵����
	Ŀǰ֧��AP6210��AP6212��Broadcom BCM43362����Cooee Airkiss ����Э�顣
	����ģ���Э����������֧�֡�

���ÿ�ʼ�ӿڣ�
setEasysetupProtol(int protocol)������ӿ�Ϊ����Э�����͡�������BroadcomEasysetup��Protocolsxxx ����

startEasySetup()����ʼ�����������������á�

״̬�ص��ӿڣ�
	public interface EasysetupListener{
		public void onEasysetupStart();                     //������ʼɨ��㲥��Ϣ
		public void onLinktoAPStart();                      //�����˺�����ɹ�����ʼ����·��
		public void onLinktoAPFinished();                   //����·�ɳɹ�
		public void onSendUDPBroadcastStart();              //Airkiss��Ҫ����udp�㲥
		public void onSendUDPBroadcastFinished();           //���͹㲥���
		public void onEasysetupFinished(EasysetupInfo info);//������ɣ��˺������͵�Ӧ��
	}

----------------------------------------------------------------------------------------------------------------



