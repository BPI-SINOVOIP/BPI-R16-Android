#include "hx8379c.h"
#include "panels.h"

static void LCD_power_on(u32 sel);
static void LCD_power_off(u32 sel);
static void LCD_bl_open(u32 sel);
static void LCD_bl_close(u32 sel);

static void LCD_panel_init(u32 sel);
static void LCD_panel_exit(u32 sel);

static void LCD_cfg_panel_info(panel_extend_para * info)
{
	u32 i = 0, j=0;
	u32 items;
	u8 lcd_gamma_tbl[][2] =
	{
		//{input value, corrected value}
		{0, 0},
		{15, 15},
		{30, 30},
		{45, 45},
		{60, 60},
		{75, 75},
		{90, 90},
		{105, 105},
		{120, 120},
		{135, 135},
		{150, 150},
		{165, 165},
		{180, 180},
		{195, 195},
		{210, 210},
		{225, 225},
		{240, 240},
		{255, 255},
	};

//	u8 lcd_bright_curve_tbl[][2] =
//	{
//		//{input value, corrected value}
//		{0    ,0  },//0
//		{15   ,3  },//0
//		{30   ,6  },//0
//		{45   ,9  },// 1
//		{60   ,12  },// 2
//		{75   ,16  },// 5
//		{90   ,22  },//9
//		{105   ,28 }, //15
//		{120  ,36 },//23
//		{135  ,44 },//33
//		{150  ,54 },
//		{165  ,67 },
//		{180  ,84 },
//		{195  ,108},
//		{210  ,137},
//		{225 ,171},
//		{240 ,210},
//		{255 ,255},
//	};

	u32 lcd_cmap_tbl[2][3][4] = {
	{
		{LCD_CMAP_G0,LCD_CMAP_B1,LCD_CMAP_G2,LCD_CMAP_B3},
		{LCD_CMAP_B0,LCD_CMAP_R1,LCD_CMAP_B2,LCD_CMAP_R3},
		{LCD_CMAP_R0,LCD_CMAP_G1,LCD_CMAP_R2,LCD_CMAP_G3},
		},
		{
		{LCD_CMAP_B3,LCD_CMAP_G2,LCD_CMAP_B1,LCD_CMAP_G0},
		{LCD_CMAP_R3,LCD_CMAP_B2,LCD_CMAP_R1,LCD_CMAP_B0},
		{LCD_CMAP_G3,LCD_CMAP_R2,LCD_CMAP_G1,LCD_CMAP_R0},
		},
	};

	//memset(info,0,sizeof(panel_extend_para));

	items = sizeof(lcd_gamma_tbl)/2;
	for(i=0; i<items-1; i++) {
		u32 num = lcd_gamma_tbl[i+1][0] - lcd_gamma_tbl[i][0];

		for(j=0; j<num; j++) {
			u32 value = 0;

			value = lcd_gamma_tbl[i][1] + ((lcd_gamma_tbl[i+1][1] - lcd_gamma_tbl[i][1]) * j)/num;
			info->lcd_gamma_tbl[lcd_gamma_tbl[i][0] + j] = (value<<16) + (value<<8) + value;
		}
	}
	info->lcd_gamma_tbl[255] = (lcd_gamma_tbl[items-1][1]<<16) + (lcd_gamma_tbl[items-1][1]<<8) + lcd_gamma_tbl[items-1][1];

//	items = sizeof(lcd_bright_curve_tbl)/2;
//	for(i=0; i<items-1; i++) {
//		u32 num = lcd_bright_curve_tbl[i+1][0] - lcd_bright_curve_tbl[i][0];
//
//		for(j=0; j<num; j++) {
//			u32 value = 0;
//
//			value = lcd_bright_curve_tbl[i][1] + ((lcd_bright_curve_tbl[i+1][1] - lcd_bright_curve_tbl[i][1]) * j)/num;
//			info->lcd_bright_curve_tbl[lcd_bright_curve_tbl[i][0] + j] = value;
//		}
//	}
//	info->lcd_bright_curve_tbl[255] = lcd_bright_curve_tbl[items-1][1];

	memcpy(info->lcd_cmap_tbl, lcd_cmap_tbl, sizeof(lcd_cmap_tbl));

}

static s32 LCD_open_flow(u32 sel)
{	
	printk("[huyanhu]LCD_open_flow!\n");
	LCD_OPEN_FUNC(sel, LCD_power_on, 100);   //open lcd power, and delay 50ms
	LCD_OPEN_FUNC(sel, LCD_panel_init, 200);   //open lcd power, than delay 200ms
	LCD_OPEN_FUNC(sel, sunxi_lcd_tcon_enable, 200);     //open lcd controller, and delay 100ms
	LCD_OPEN_FUNC(sel, LCD_bl_open, 0);     //open lcd backlight, and delay 0ms

	return 0;
}

static s32 LCD_close_flow(u32 sel)
{
	LCD_CLOSE_FUNC(sel, LCD_bl_close, 0);       //close lcd backlight, and delay 0ms
	LCD_CLOSE_FUNC(sel, sunxi_lcd_tcon_disable, 0);         //close lcd controller, and delay 0ms
	LCD_CLOSE_FUNC(sel, LCD_panel_exit,	200);   //open lcd power, than delay 200ms
	LCD_CLOSE_FUNC(sel, LCD_power_off, 500);   //close lcd power, and delay 500ms

	return 0;
}

static void LCD_power_on(u32 sel)
{
	sunxi_lcd_power_enable(sel, 0);//config lcd_power pin to open lcd power0
	sunxi_lcd_pin_cfg(sel, 1);
}

static void LCD_power_off(u32 sel)
{
	sunxi_lcd_pin_cfg(sel, 0);
	sunxi_lcd_power_disable(sel, 0);//config lcd_power pin to close lcd power0
}

static void LCD_bl_open(u32 sel)
{
	sunxi_lcd_pwm_enable(sel);//open pwm module
	sunxi_lcd_backlight_enable(sel);//config lcd_bl_en pin to open lcd backlight
}

static void LCD_bl_close(u32 sel)
{
	sunxi_lcd_backlight_disable(sel);//config lcd_bl_en pin to close lcd backlight
	sunxi_lcd_pwm_disable(sel);//close pwm module
}

#define REGFLAG_DELAY             							0X01
#define REGFLAG_END_OF_TABLE      							0x02   // END OF REGISTERS MARKER

struct lcd_setting_table {
    u8 cmd;
    u32 count;
    u8 para_list[64];
};

static struct lcd_setting_table hx8379c_init_setting[] = {
#if 0	
	{0xB9,	3,	{0xFF,0x83,0x79}},
//	{0xBA,	2,	{0x33,0X83}},
//	{0xB0,	4,	{0X00,0X00,0X7D,0X0C}},
	{0xB1,	16,	{0x44,0x16,0x16,0x31,0x31,0x50,0xD0,0xEE,0x54,0x80,0x38,0x38,0xF8,0x22,0x22,0x22}},
	{0xB2,	9,	{0x82,0xFE,0x0D,0x0A,0x20,0x50,0x11,0x42,0x1D}},
	{0xB4,	10,	{0x02,0x7C,0x02,0x7C,0x02,0x7C,0x22,0x86,0x23,0x86}},
//	{0xBF,	3,	{0X41,0X0E,0X01}},
//	{0xD3,	37,	{0X00,0X06,0X00,0X40,0X07,0X08,0X00,0X32,0X10,0X07,0X00,0X07,0X54,0X15,0X0F,0X05,0X04,0X02,0X12,0X10,0X05,0X07,0X33,0X33,0X0B,0X0B,0X37,0X10,0X07,0X07,0X08,0X00,0X00,0X00,0X0A,0X00,0X01}},
//	{0xD5,	44,	{0X04,0X05,0X06,0X07,0X00,0X01,0X02,0X03,0X20,0X21,0X22,0X23,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X19,0X19,0X18,0X18,0X18,0X18,0X1B,0X1B,0X1A,0X1A,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18}},
//	{0xD6,	44,	{0X03,0X02,0X01,0X00,0X07,0X06,0X05,0X04,0X23,0X22,0X21,0X20,0X18,0X18,0X18,0X18,0X18,0X18,0X58,0X58,0X18,0X18,0X19,0X19,0X18,0X18,0X1B,0X1B,0X1A,0X1A,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18}},
	{0xC7, 	4,	{0x00,0x00,0x00,0xC0}},
	{0xCC, 	1,	{0x02}},
	{0xD2, 	1,	{0x11}},
	{0xD3, 	29,	{0x19,0x19,0x18,0x18,0x1A,0x1A,0x1B,0x1B,0x02,0x03,0x00,0x01,0x06,0x07,0x04,0x05,0x20,0x21,0x22,0x23,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x00,0x00}},
	{0xD5, 	34,	{0x19,0x19,0x18,0x18,0x1A,0x1A,0x1B,0x1B,0x02,0x03,0x00,0x01,0x06,0x07,0x04,0x05,0x20,0x21,0x22,0x23,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x00,0x00}},
	{0xD6, 	32,	{0x18,0x18,0x19,0x19,0x1A,0x1A,0x1B,0x1B,0x03,0x02,0x05,0x04,0x07,0x06,0x01,0x00,0x23,0x22,0x21,0x20,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18}},
	{0xE0, 	42,	{0x00,0x00,0x04,0x0B,0x0D,0x3F,0x1A,0x2D,0x08,0x0C,0x0E,0x18,0x10,0x14,0x17,0x15,0x16,0x09,0x13,0x14,0x18,0x00,0x00,0x04,0x0B,0x0D,0x3F,0x1A,0x2E,0x07,0x0C,0x0F,0x19,0x0F,0x13,0x16,0x14,0x14,0x07,0x12,0x13,0x17}},
	{0xB6, 	2,	{0x48,0x48}},
//	{0x3A,1,{0x70}}, //24bit
	{0x11, 	1,	{}},
	{REGFLAG_DELAY,	150,	{}},
	{0x29, 	1,	{}},
	{REGFLAG_DELAY, 20, {}},
	{REGFLAG_END_OF_TABLE, 0, {}}	
#else //debug only
	{0xB9,	3,	{0xFF,0x83,0x79}},
//	{0xBA,	2,	{0x33,0X83}},
//	{0xB0,	4,	{0X00,0X00,0X7D,0X0C}},
	{0xB1,	20,	{0x44,0x18,0x18,0x31,0x51,0x50,0xD0,0xE4,0xE4,0x80,0x38,0x38,0xf8,0x22,0x22,0x22,0x00,0x80,0x30,0x00}},
	{0xB2,	9,	{0x82,0xFE,0x0d,0x0a,0x00,0x50,0x11,0x42,0x1D}},
	{0xB4,	10,	{0x02,0x55,0x00,0x66,0x00,0x66,0x22,0x77,0x23,0x77}},
//	{0xBF,	3,	{0X41,0X0E,0X01}},
//	{0xD3,	37,	{0X00,0X06,0X00,0X40,0X07,0X08,0X00,0X32,0X10,0X07,0X00,0X07,0X54,0X15,0X0F,0X05,0X04,0X02,0X12,0X10,0X05,0X07,0X33,0X33,0X0B,0X0B,0X37,0X10,0X07,0X07,0X08,0X00,0X00,0X00,0X0A,0X00,0X01}},
//	{0xD5,	44,	{0X04,0X05,0X06,0X07,0X00,0X01,0X02,0X03,0X20,0X21,0X22,0X23,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X19,0X19,0X18,0X18,0X18,0X18,0X1B,0X1B,0X1A,0X1A,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18}},
//	{0xD6,	44,	{0X03,0X02,0X01,0X00,0X07,0X06,0X05,0X04,0X23,0X22,0X21,0X20,0X18,0X18,0X18,0X18,0X18,0X18,0X58,0X58,0X18,0X18,0X19,0X19,0X18,0X18,0X1B,0X1B,0X1A,0X1A,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18,0X18}},
//	{0xC7, 	4,	{0x00,0x00,0x00,0xC0}},
	{0xCC, 	1,	{0x02}},
//	{0xD2, 	1,	{0x11}},
	{0xD3, 	35,	{0x00,0x07,0x00,0x3c,0x01,0x08,0x08,0x32,0x10,0x04,0x00,0x04,0x03,0x70,0x03,0x70,0x00,0x08,0x00,0x08,0x37,0x33,0x06,0x06,0x37,0x06,0x06,0x37,0x0b,0x00,0x00,0x00,0x0a,0x00,0x11}},
	{0xD5, 	32,	{0x19,0x19,0x18,0x18,0x1a,0x1a,0x1b,0x1b,0x02,0x03,0x00,0x01,0x06,0x07,0x04,0x05,0x20,0x21,0x22,0x23,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18}},
	{0xD6, 	32,	{0x18,0x18,0x19,0x19,0x1b,0x1b,0x1a,0x1a,0x03,0x02,0x05,0x04,0x07,0x06,0x01,0x00,0x23,0x22,0x21,0x20,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18}},
	{0xE0, 	42,	{0x00,0x04,0x07,0x0c,0x0d,0x23,0x1d,0x30,0x08,0x0b,0x0c,0x17,0x12,0x15,0x18,0x16,0x16,0x09,0x14,0x15,0x17,0x00,0x04,0x07,0x0c,0x0d,0x23,0x1d,0x30,0x08,0x0b,0x0c,0x17,0x12,0x15,0x18,0x16,0x16,0x09,0x14,0x15,0x17}},
//	{0xB6, 	2,	{0x50,0x50}},
	{0xB6, 	2,	{0x55,0x55}},
//	{0x3A,1,{0x70}}, //24bit
	{0x11, 	0,	{}},
	{REGFLAG_DELAY,	150,	{}},
	{0x29, 	0,	{}},
	{REGFLAG_DELAY, 20, {}},
	{REGFLAG_END_OF_TABLE, 0, {}}
#endif
};

static void LCD_panel_init(u32 sel)
{
	u32 i;
	printk("[huyanhu:]LCD_panel_init!\n");
	
//	__u32 rx_num;
//	__u8 rx_bf0=0,rx_bf1=0,rx_bf2=0;
	
	sunxi_lcd_pin_cfg(sel, 1);
	sunxi_lcd_delay_ms(10);

//	panel_rst(1);	 //add by lyp@20140423
//	sunxi_lcd_delay_ms(50);//add by lyp@20140423
	panel_rst(0);
	sunxi_lcd_delay_ms(20);
	panel_rst(1);
	sunxi_lcd_delay_ms(10);

	dsi_dcs_wr_0para(sel,DSI_DCS_EXIT_SLEEP_MODE);
	sunxi_lcd_delay_ms(120);
	
//	dsi_dcs_rd(0,0xF4,&rx_bf0,&rx_num);
//	printk("+++++[%s:%d]rx_bf0 = %#x, rx_num = %d ###############\n",__func__,__LINE__,rx_bf0,rx_num);
	
//	sunxi_lcd_dsi_clk_enable(sel);
//	sunxi_lcd_dsi_write(sel,DSI_DCS_SET_DISPLAY_ON, 0, 0);
//	sunxi_lcd_delay_ms(200);

	
//	sunxi_lcd_dsi_clk_enable(sel);
//	dsi_dcs_wr_0para(sel,DSI_DCS_SET_DISPLAY_ON);
//	dsi_dcs_wr_1para(sel,DSI_DCS_SET_TEAR_ON,0x00);
//	dsi_dcs_wr_1para(sel,DSI_DCS_SET_PIXEL_FORMAT,0x77);
//	sunxi_lcd_delay_ms(120);
	
	for (i=0;;i++) {
		if(hx8379c_init_setting[i].cmd == 0x02)
			break;
		else if (hx8379c_init_setting[i].cmd == 0x01)
			sunxi_lcd_delay_ms(hx8379c_init_setting[i].count);
		else
			dsi_dcs_wr(0,hx8379c_init_setting[i].cmd,hx8379c_init_setting[i].para_list,hx8379c_init_setting[i].count);
	}
	printk("[huyanhu:]hx8379c_init_setting i = %d end!\n",i);
	
	sunxi_lcd_dsi_clk_enable(sel);
	sunxi_lcd_dsi_write(sel,DSI_DCS_SET_DISPLAY_ON, 0, 0);
	sunxi_lcd_delay_ms(200);


	return;
}

static void LCD_panel_exit(u32 sel)
{
	sunxi_lcd_dsi_clk_disable(sel);
	panel_rst(0);
	return ;
}

//sel: 0:lcd0; 1:lcd1
static s32 LCD_user_defined_func(u32 sel, u32 para1, u32 para2, u32 para3)
{
	return 0;
}

__lcd_panel_t hx8379c_panel = {
	/* panel driver name, must mach the name of lcd_drv_name in sys_config.fex */
	.name = "hx8379c",
	.func = {
		.cfg_panel_info = LCD_cfg_panel_info,
		.cfg_open_flow = LCD_open_flow,
		.cfg_close_flow = LCD_close_flow,
		.lcd_user_defined_func = LCD_user_defined_func,
	},
};
