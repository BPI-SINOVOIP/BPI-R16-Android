#ifndef DRC_H_
#define DRC_H_
/*
function :drcinit
init function
input:
  fs:������
return :
   �޷���ֵ
*/
void drcinit(int fs);
/*
function :drcdec
drc function
input:
  xout			:���������׵�ַ��
  samplenum	:����sample������ͨ·samplenum = ���������ܳ���/2;˫����samplenum = ���������ܳ���/4;
  nch       :ͨ����
return :
   �޷���ֵ
*/
void drcdec(short* xout,int samplenum, int nch);

#endif//DRC_H_
