/************************************************************/
typedef enum __CEDAR_AUDIO_EQ           /* ��Ч���Ͷ���                         */
{
    CEDAR_AUD_EQ_TYPE_NORMAL=0,         /* ��Ȼ                                 */
    CEDAR_AUD_EQ_TYPE_DBB,              /* �ص���                               */
    CEDAR_AUD_EQ_TYPE_POP,              /* ����                                 */
    CEDAR_AUD_EQ_TYPE_ROCK,             /* ҡ��                                 */
    CEDAR_AUD_EQ_TYPE_CLASSIC,          /* �ŵ�                                 */
    CEDAR_AUD_EQ_TYPE_JAZZ,             /* ��ʿ                                 */
    CEDAR_AUD_EQ_TYPE_VOCAL,            /* ����                                 */
    CEDAR_AUD_EQ_TYPE_DANCE,            /* ����                                 */
    CEDAR_AUD_EQ_TYPE_SOFT,             /* ���                                 */
    CEDAR_AUD_EQ_TYPE_USR_MODE=0xFF,    /* �û�ģʽ                             */

    CEDAR_AUD_EQ_TYPE_

} __cedar_audio_eq_t;
/**********************************************************************************/

1��PostProInfo->UserEQ[0]Ϊ��Ч���л����أ�
PostProInfo->UserEQ[0]��0               nature/��Ȼ��������eq����          
			1		low/�ص���                         
			2		pop/����                           
			3		rock/ҡ��                          
			4		class/�ŵ�                         
			5		jazz/��ʿ                          
			6		vocal/����                         
			7		dance/����                         
			8		soft/���                          
			0xFF		�Զ��� 
			����            ������=0��nature/��Ȼ��������eq����
												 
2��ֻ��  PostProInfo->UserEQ[0] = 0xFF, PostProInfo->UserEQ[1]-PostProInfo->UserEQ[10]���ݲ���Ч
3��PostProInfo->UserEQ[i]����Ƶ�����£�
i  =			1      2	3	4      5       6	7	8	9	10
			31    62	125    250    500    1000	2000	4000	8000   16000 (hz) 
4��PostProInfo->UserEQ[i]�ķ�ΧΪ(-12 <= PostProInfo->UserEQ[i] <= 12) 
5��������һЩ�ο�������
i	=		1	2	3	4	5	6	7	8	9	10
1	low/�ص���          	    	    	    	
2	pop/����        -1      -1      0       3       6       4       2       -1      -2      -2                     
3	rock/ҡ��       3       2       -1      -5      -8      -3      -1      2       3       3
4	class/�ŵ�      0       6       6       3       0       0       0       0       2       2
5	jazz/��ʿ       0       0       0       3       3       3       0       2       4       4
6	vocal/����      -2      0       2       1       0       0       0       0       -2      -5
7	dance/����      -1      4       5       1       -1      -1      0       0       4       4
8	soft/���       3       2       1       0       -1      0       1       3       5       6
                    
9    default		-12	 	0		4  		6  		5		3 		3 		3		6 		6							