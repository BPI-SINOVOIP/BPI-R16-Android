/************************************************************/
typedef enum __CEDAR_AUDIO_EQ           /* 音效类型定义                         */
{
    CEDAR_AUD_EQ_TYPE_NORMAL=0,         /* 自然                                 */
    CEDAR_AUD_EQ_TYPE_DBB,              /* 重低音                               */
    CEDAR_AUD_EQ_TYPE_POP,              /* 流行                                 */
    CEDAR_AUD_EQ_TYPE_ROCK,             /* 摇滚                                 */
    CEDAR_AUD_EQ_TYPE_CLASSIC,          /* 古典                                 */
    CEDAR_AUD_EQ_TYPE_JAZZ,             /* 爵士                                 */
    CEDAR_AUD_EQ_TYPE_VOCAL,            /* 语言                                 */
    CEDAR_AUD_EQ_TYPE_DANCE,            /* 舞曲                                 */
    CEDAR_AUD_EQ_TYPE_SOFT,             /* 柔和                                 */
    CEDAR_AUD_EQ_TYPE_USR_MODE=0xFF,    /* 用户模式                             */

    CEDAR_AUD_EQ_TYPE_

} __cedar_audio_eq_t;
/**********************************************************************************/

1、PostProInfo->UserEQ[0]为音效的切换开关：
PostProInfo->UserEQ[0]：0               nature/自然，不进行eq处理          
			1		low/重低音                         
			2		pop/流行                           
			3		rock/摇滚                          
			4		class/古典                         
			5		jazz/爵士                          
			6		vocal/语言                         
			7		dance/舞曲                         
			8		soft/柔和                          
			0xFF		自定义 
			其它            保留，=0，nature/自然，不进行eq处理
												 
2、只有  PostProInfo->UserEQ[0] = 0xFF, PostProInfo->UserEQ[1]-PostProInfo->UserEQ[10]数据才有效
3、PostProInfo->UserEQ[i]中心频段如下：
i  =			1      2	3	4      5       6	7	8	9	10
			31    62	125    250    500    1000	2000	4000	8000   16000 (hz) 
4、PostProInfo->UserEQ[i]的范围为(-12 <= PostProInfo->UserEQ[i] <= 12) 
5、下面是一些参考参数：
i	=		1	2	3	4	5	6	7	8	9	10
1	low/重低音          	    	    	    	
2	pop/流行        -1      -1      0       3       6       4       2       -1      -2      -2                     
3	rock/摇滚       3       2       -1      -5      -8      -3      -1      2       3       3
4	class/古典      0       6       6       3       0       0       0       0       2       2
5	jazz/爵士       0       0       0       3       3       3       0       2       4       4
6	vocal/语言      -2      0       2       1       0       0       0       0       -2      -5
7	dance/舞曲      -1      4       5       1       -1      -1      0       0       4       4
8	soft/柔和       3       2       1       0       -1      0       1       3       5       6
                    
9    default		-12	 	0		4  		6  		5		3 		3 		3		6 		6							