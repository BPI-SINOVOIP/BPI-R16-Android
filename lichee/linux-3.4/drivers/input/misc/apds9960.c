/*
 *  apds9960.c - Linux kernel modules for Gesture + RGB + ambient light + proximity sensor
 *
 *  Copyright (C) 2013 Lee Kai Koon <kai-koon.lee@avagotech.com>
 *  Copyright (C) 2013 Avago Technologies
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/init.h>
#include <linux/fs.h>
#include <linux/slab.h>
#include <linux/i2c.h>
#include <linux/mutex.h>
#include <linux/delay.h>
#include <linux/interrupt.h>
#include <linux/irq.h>
#include <linux/input.h>
#include <linux/ioctl.h>
#include <linux/miscdevice.h>
#include <linux/uaccess.h>
#include <linux/gpio.h>
#include <linux/interrupt.h>
#include <linux/time.h>
#include <mach/sys_config.h>
#include "apds9960.h"


/************************************************
Change history

Ver		When			Who		Why
---		----			---		---
1.0.0	19-Aug-2013		KK		Initial draft
1.0.1	26-Aug-2013		KK		Revise gesture algorithm
1.0.2	29-Aug-2013		KK		Change GTHR_IN and GTHR_OUT
1.0.3	03-Sep-2013		KK		Correct divide by zero error in AveragingRawData()
1.0.4	05-Sep-2013		KK		Accept old and latest ID value
1.0.5	17-Sep-2013		KK		Return if sample size is less than or equal to 4 in GestureDataProcessing();
								Correct error in AveragingRawData()
1.0.6	27-Sep-2013		KK		Simplify GestureDataProcessing() and revise Gesture Calibration
								Added Up/Down/Left/Right U-Turn gesture detection
************************************************/


/*
 * Global data
 */
static struct i2c_client *apds9960_i2c_client; /* global i2c_client to support ioctl */
static struct workqueue_struct *apds_workqueue;

static unsigned char apds9960_als_atime_tb[] = { 0xF6, 0xEB, 0xD6 };
static unsigned short apds9960_als_integration_tb[] = {2400,5040, 10080}; // DO NOT use beyond 100.8ms
static unsigned short apds9960_als_res_tb[] = { 10240, 21504, 43008 };
static unsigned char apds9960_als_again_tb[] = { 1, 4, 16, 64 };
static unsigned char apds9960_als_again_bit_tb[] = { 0x00, 0x01, 0x02, 0x03 };

#ifndef PLATFORM_SENSOR_APDS9960
// ensure the coefficients do not exceed 9999 
static int RGB_COE_X[3] = {-1882, 10240, -8173}; // {-1.8816, 10.24, -8.173};
static int RGB_COE_Y[3] = {-2100, 10130, -7708}; // {-2.0998, 10.13, -7.708};
static int RGB_COE_Z[3] = {-1937, 5201, -2435}; // {-1.937, 5.201, -2.435};
#endif

static int RGB_CIE_N1 = 332; // 0.332;
static int RGB_CIE_N2 = 186; // 0.1858;

static int RGB_CIE_CCT1 = 449; // 449.0;
static int RGB_CIE_CCT2 = 3525; // 3525.0;
static int RGB_CIE_CCT3 = 6823; // 6823.3;
static int RGB_CIE_CCT4 = 5520; // 5520.33;

/* Gesture data storage */
static GESTURE_DATA_TYPE gesture_data;

static int gesture_motion=DIR_NONE;
static int gesture_prev_motion=DIR_NONE;
static int fMotionMapped=0;

int gesture_ud_delta=0;
int gesture_lr_delta=0;
int gesture_state=0;

int negative_ud_delta=0;
int positive_ud_delta=0;
int negative_lr_delta=0;
int positive_lr_delta=0;
int ud_delta_positive_negative=0;	// 1 = positive then negative, 2 = negative then positive
int lr_delta_positive_negative=0;

int gesture_circle_state=0;
int gesture_circle_cw_count=0;
int gesture_circle_acw_count=0;

int gesture_ud_count=0;
int gesture_lr_count=0;

int gesture_near_count=0;
int gesture_far_count=0;

int gesture_fundamentals=0;	// 0 = fundamentals, 1 = extra

unsigned int gesture_start_time=0;
unsigned int gesture_end_time=0;
unsigned int gesture_time=0;
int gesture_speed=0;

char gesture_str[DIR_ALL][15] = {"", "Left", "Right", "Up", "Down", 
									"Up-Right", "Right-Down", "Down-Left", "Left-Up", 
									"NEAR", "FAR", "Circle-CW", "Circle-ACW", 
									"Up U-Turn", "Down U-Turn", "Left U-Turn", "Right U-Turn"};

/* Addresses to scan */
static const unsigned short normal_i2c[2] = {0x39,I2C_CLIENT_END};



unsigned int GetTickCount(void);
int FilterGestureRawData(GESTURE_DATA_TYPE *, GESTURE_DATA_TYPE *);
int GestureDataProcessing(void);
int DecodeGesture(int gesture_mode);
void ResetGestureParameters(void); 

/*
 * Management functions
 */

static int apds9960_clear_interrupt(struct i2c_client *client, int command)
{
	int ret;
		
	ret = i2c_smbus_write_byte(client, command);

	return ret;
}

static int apds9960_set_enable(struct i2c_client *client, int enable)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int ret;

	ret = i2c_smbus_write_byte_data(client, APDS9960_ENABLE_REG, enable);

	data->enable = enable;

	return ret;
}

static int apds9960_set_atime(struct i2c_client *client, int atime)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int ret;
	
	ret = i2c_smbus_write_byte_data(client, APDS9960_ATIME_REG, atime);

	data->atime = atime;

	return ret;
}

static int apds9960_set_wtime(struct i2c_client *client, int wtime)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int ret;
	
	ret = i2c_smbus_write_byte_data(client, APDS9960_WTIME_REG, wtime);

	data->wtime = wtime;

	return ret;
}

static int apds9960_set_ailt(struct i2c_client *client, int threshold)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int ret;
	
	ret = i2c_smbus_write_word_data(client, APDS9960_AILTL_REG, threshold);
	
	data->ailt = threshold;

	return ret;
}

static int apds9960_set_aiht(struct i2c_client *client, int threshold)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int ret;
	
	ret = i2c_smbus_write_word_data(client, APDS9960_AIHTL_REG, threshold);
	
	data->aiht = threshold;

	return ret;
}

static int apds9960_set_pilt(struct i2c_client *client, int threshold)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int ret;
	
	ret = i2c_smbus_write_byte_data(client, APDS9960_PITLO_REG, threshold);
	
	data->pilt = threshold;

	return ret;
}

static int apds9960_set_piht(struct i2c_client *client, int threshold)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int ret;
	
	ret = i2c_smbus_write_byte_data(client, APDS9960_PITHI_REG, threshold);
	
	data->piht = threshold;

	return ret;
}

static int apds9960_set_pers(struct i2c_client *client, int pers)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int ret;
	
	ret = i2c_smbus_write_byte_data(client, APDS9960_PERS_REG, pers);

	data->pers = pers;

	return ret;
}

static int apds9960_set_config(struct i2c_client *client, int config)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int ret;
	
	ret = i2c_smbus_write_byte_data(client, APDS9960_CONFIG_REG, config);

	data->config = config;

	return ret;
}

static int apds9960_set_ppulse(struct i2c_client *client, int ppulse)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int ret;
	
	ret = i2c_smbus_write_byte_data(client, APDS9960_PPULSE_REG, ppulse);

	data->ppulse = ppulse;

	return ret;
}

static int apds9960_set_control(struct i2c_client *client, int control)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int ret;
	
	ret = i2c_smbus_write_byte_data(client, APDS9960_CONTROL_REG, control);

	data->control = control;

	return ret;
}

static int apds9960_set_aux(struct i2c_client *client, int aux)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int ret;
	
	ret = i2c_smbus_write_byte_data(client, APDS9960_AUX_REG, aux);

	data->aux = aux;

	return ret;
}

static int apds9960_set_poffset_ur(struct i2c_client *client, int poffset_ur)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int ret;
	
	ret = i2c_smbus_write_byte_data(client, APDS9960_POFFSET_UR_REG, poffset_ur);

	data->poffset_ur = poffset_ur;

	return ret;
}

static int apds9960_set_poffset_dl(struct i2c_client *client, int poffset_dl)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int ret;
	
	ret = i2c_smbus_write_byte_data(client, APDS9960_POFFSET_DL_REG, poffset_dl);

	data->poffset_dl = poffset_dl;

	return ret;
}

/****************** Gesture related registers ************************/
static int apds9960_set_config2(struct i2c_client *client, int config2)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int ret;
	
	ret = i2c_smbus_write_byte_data(client, APDS9960_CONFIG2_REG, config2);

	data->config2= config2;

	return ret;
}

static int apds9960_set_gthr_in(struct i2c_client *client, int gthr_in)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int ret;
	
	ret = i2c_smbus_write_byte_data(client, APDS9960_GTHR_IN_REG, gthr_in);

	data->gthr_in = gthr_in;

	return ret;
}

static int apds9960_set_gthr_out(struct i2c_client *client, int gthr_out)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int ret;
	
	ret = i2c_smbus_write_byte_data(client, APDS9960_GTHR_OUT_REG, gthr_out);

	data->gthr_out = gthr_out;

	return ret;
}

static int apds9960_set_gconf1(struct i2c_client *client, int gconf1)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int ret;
	
	ret = i2c_smbus_write_byte_data(client, APDS9960_GCONF1_REG, gconf1);

	data->gconf1 = gconf1;

	return ret;
}

static int apds9960_set_gconf2(struct i2c_client *client, int gconf2)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int ret;
	
	ret = i2c_smbus_write_byte_data(client, APDS9960_GCONF2_REG, gconf2);

	data->gconf2 = gconf2;

	return ret;
}

static int apds9960_set_goffset_u(struct i2c_client *client, int goffset_u)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int ret;
	
	ret = i2c_smbus_write_byte_data(client, APDS9960_GOFFSET_U_REG, goffset_u);

	data->goffset_u = goffset_u;

	return ret;
}

static int apds9960_set_goffset_d(struct i2c_client *client, int goffset_d)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int ret;
	
	ret = i2c_smbus_write_byte_data(client, APDS9960_GOFFSET_D_REG, goffset_d);

	data->goffset_d = goffset_d;

	return ret;
}

static int apds9960_set_gpulse(struct i2c_client *client, int gpulse)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int ret;
	
	ret = i2c_smbus_write_byte_data(client, APDS9960_GPULSE_REG, gpulse);

	data->gpulse = gpulse;

	return ret;
}

static int apds9960_set_goffset_l(struct i2c_client *client, int goffset_l)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int ret;
	
	ret = i2c_smbus_write_byte_data(client, APDS9960_GOFFSET_L_REG, goffset_l);

	data->goffset_l = goffset_l;

	return ret;
}

static int apds9960_set_goffset_r(struct i2c_client *client, int goffset_r)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int ret;
	
	ret = i2c_smbus_write_byte_data(client, APDS9960_GOFFSET_R_REG, goffset_r);

	data->goffset_r = goffset_r;

	return ret;
}

static int apds9960_set_gconf3(struct i2c_client *client, int gconf3)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int ret;
	
	ret = i2c_smbus_write_byte_data(client, APDS9960_GCONF3_REG, gconf3);

	data->gconf3 = gconf3;

	return ret;
}

static int apds9960_set_gctrl(struct i2c_client *client, int gctrl)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int ret;
	
	ret = i2c_smbus_write_byte_data(client, APDS9960_GCTRL_REG, gctrl);

	data->gctrl = gctrl;

	return ret;
}

/*********************************************************************/

static int LuxCalculation(struct i2c_client *client)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int X1, Y1, Z1;
	int x1, y1, z1;
	int n;
	//unsigned int lux;
	unsigned int cct;
	int sum_XYZ=0;


	//printk("phase 0 :: cdata = %d, rdata = %d, gdata = %d, bdata = %d\n", data->cdata, data->rdata, data->gdata, data->bdata);
	//printk("phase 0.5 :: atime = %d, again = %d\n", apds9960_als_integration_tb[data->als_atime_index], apds9960_als_again_tb[data->als_again_index]);

	X1 = (data->RGB_COE_X[0]*data->rdata) + (data->RGB_COE_X[1]*data->gdata) + (data->RGB_COE_X[2]*data->bdata);
	Y1 = (data->RGB_COE_Y[0]*data->rdata) + (data->RGB_COE_Y[1]*data->gdata) + (data->RGB_COE_Y[2]*data->bdata);
	Z1 = (data->RGB_COE_Z[0]*data->rdata) + (data->RGB_COE_Z[1]*data->gdata) + (data->RGB_COE_Z[2]*data->bdata);
 
	//printk("%s phase 1 :: X1 = %d, Y1 = %d, Z1 = %d\n", __func__, X1, Y1, Z1);
 
	if ( (X1==0) && (Y1==0) && (Z1==0) ) {
		x1 = y1 = z1 = 0;
	}
	else {	
		sum_XYZ = (X1 + Y1 + Z1)/1000;	// scale down
		if (sum_XYZ > 0) {
			if ( ((X1+Y1+Z1)%1000) >= 500) 
				sum_XYZ++;		
		}
		else {
			if ( ((X1+Y1+Z1)%1000) <= -500)
				sum_XYZ--;	
		}

		x1 = X1/sum_XYZ;
		y1 = Y1/sum_XYZ;
		z1 = Z1/sum_XYZ;
	}
 
	//printk("%s phase 2 :: x1 = %d, y1 = %d, z1 = %d, sum = %d\n", __func__, x1, y1, z1, sum_XYZ);

	if (data->cdata > 10) {

		n = ((x1 - RGB_CIE_N1)*1000)/(RGB_CIE_N2 - y1);
		cct = (((RGB_CIE_CCT1*(n*n*n))/1000000000) + 
			   ((RGB_CIE_CCT2*(n*n))/1000000) + 
			   ((RGB_CIE_CCT3*n)/1000) + RGB_CIE_CCT4);
	}
	else {
		n = 0;
		cct = 0;
	}
 
	data->lux = (data->cdata*10080)/(apds9960_als_integration_tb[data->als_atime_index]*apds9960_als_again_tb[data->als_again_index]);

	data->cct = cct;

	//printk("%s phase 3 :: n = %d, cct = %d, lux = %d, data->cct = %d, data->lux = %d\n", __func__, n, cct, lux, data->cct, data->lux);

	if (data->cdata > 0) {
		if( ((data->rdata * 100)/data->cdata) >= 65 ){	// Incandescent 2600K
			data->cct=(data->cct*data->cct_GA2)/1000;
			data->lux=(data->lux*data->lux_GA2)/1000;
		}
		else if( ((data->rdata * 100)/data->cdata) >= 45 ){  // Fluorescent Warm White 2700K
			data->cct=(data->cct*data->cct_GA3)/1000;
			data->lux=(data->lux*data->lux_GA3)/1000;
		}
		else{  // Fluorescent Daylight 6500K
			data->cct=(data->cct*data->cct_GA1)/1000;
 			data->lux=(data->lux*data->lux_GA1)/1000;
		}
	}

 
	//printk("%s phase 4 :: cct = %d, lux = %d, data->cct = %d, data->lux = %d\n", __func__, cct, lux, data->cct, data->lux);

	return 0;
}

static void apds9960_change_ps_threshold(struct i2c_client *client)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	printk("%s\n",__func__);
	data->ps_data =	i2c_smbus_read_byte_data(client, APDS9960_PDATA_REG);

	if ( (data->ps_data > data->piht) && (data->ps_data > data->pilt) ) {
		/* far-to-near detected */
		data->ps_detection = 1;	

		input_report_abs(data->input_dev_ps, ABS_DISTANCE, 0);/* FAR-to-NEAR detection */	
		input_sync(data->input_dev_ps);

		/* setup to detect far now */
		apds9960_set_pilt(client, data->ps_hysteresis_threshold);
		apds9960_set_piht(client, APDS9960_FAR_THRESHOLD_HIGH);

		printk("far-to-near detected\n");
	}
	else if ( (data->ps_data < data->pilt) && (data->ps_data < data->piht) ) {
		/* near-to-far detected */
		data->ps_detection = 0;

		input_report_abs(data->input_dev_ps, ABS_DISTANCE, 20);/* NEAR-to-FAR detection */	
		input_sync(data->input_dev_ps);

		/* setup to detect near now */
		apds9960_set_pilt(client, APDS9960_NEAR_THRESHOLD_LOW);
		apds9960_set_piht(client, data->ps_threshold);

		printk("near-to-far detected\n");
	}
	else if ( (data->pilt == APDS9960_FAR_THRESHOLD_HIGH) && 
			  (data->piht == APDS9960_NEAR_THRESHOLD_LOW) ) {	// force interrupt
		/* special case */
		/* near-to-far detected */
		data->ps_detection = 0;

		input_report_abs(data->input_dev_ps, ABS_DISTANCE, 20);/* NEAR-to-FAR detection */	
		input_sync(data->input_dev_ps);

		/* setup to detect near now */
		apds9960_set_pilt(client, APDS9960_NEAR_THRESHOLD_LOW);
		apds9960_set_piht(client, data->ps_threshold);

		printk("near-to-far detected\n");		
	}
}

static void apds9960_change_als_threshold(struct i2c_client *client)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	unsigned char change_again=0;
	unsigned char control_data=0;
	unsigned char i2c_data[16];
	int status;

	status = i2c_smbus_read_i2c_block_data(client, APDS9960_CDATAL_REG, 8, (unsigned char*)i2c_data);

	if (status < 0) return;
	if (status != 8) return;

	data->cdata = (i2c_data[1]<<8)|i2c_data[0];

	data->rdata = (i2c_data[3]<<8)|i2c_data[2];


	data->gdata = (i2c_data[5]<<8)|i2c_data[4];
	data->bdata = (i2c_data[7]<<8)|i2c_data[6];

	LuxCalculation(client);

	if (data->lux >= 0) {
		data->lux = data->lux<30000 ? data->lux : 30000;
		data->als_prev_lux = data->lux;
	}

	if (data->cct >= 0) {
		data->cct = data->cct<10000 ? data->cct : 10000;
	}

	printk("cct=%d, lux=%d cdata=%d rdata=%d gdata=%d bdata=%d atime=%x again=%d\n", 
		data->cct, data->lux, data->cdata, data->rdata, data->gdata, 
		data->bdata, apds9960_als_atime_tb[data->als_atime_index], apds9960_als_again_tb[data->als_again_index]);
	
	data->als_data = data->cdata;

	data->als_threshold_l = (data->als_data * (100-APDS9960_ALS_THRESHOLD_HSYTERESIS) ) /100;
	data->als_threshold_h = (data->als_data * (100+APDS9960_ALS_THRESHOLD_HSYTERESIS) ) /100;

	if (data->als_threshold_h >= apds9960_als_res_tb[data->als_atime_index]) {
		data->als_threshold_h = apds9960_als_res_tb[data->als_atime_index];
	}

	if (data->als_data >= (apds9960_als_res_tb[data->als_atime_index]*99)/100) {
		// lower AGAIN if possible
		if (data->als_again_index != APDS9960_ALS_GAIN_1X) {
			data->als_again_index--;
			change_again = 1;
		}
		else {
			input_report_abs(data->input_dev_als, ABS_LIGHT, data->lux); // report lux level
			input_report_abs(data->input_dev_als, ABS_CCT, data->cct); // report color temperature cct
			input_sync(data->input_dev_als);
		}
	}
	else if (data->als_data <= (apds9960_als_res_tb[data->als_atime_index]*1)/100) {
		// increase AGAIN if possible
	 	if (data->als_again_index != APDS9960_ALS_GAIN_64X) {
			data->als_again_index++;
			change_again = 1;
		}
		else {
			input_report_abs(data->input_dev_als, ABS_LIGHT, data->lux); // report lux level
			input_report_abs(data->input_dev_als, ABS_CCT, data->cct); // report color temperature cct
			input_sync(data->input_dev_als);
		}
	}
	else {
		input_report_abs(data->input_dev_als, ABS_LIGHT, data->lux); // report lux level
		input_report_abs(data->input_dev_als, ABS_CCT, data->cct); // report color temperature cct
		input_sync(data->input_dev_als);
	}

	if (change_again) {
		control_data = i2c_smbus_read_byte_data(client, APDS9960_CONTROL_REG);
		control_data = control_data & 0xFC;

		control_data = control_data | apds9960_als_again_bit_tb[data->als_again_index];
		i2c_smbus_write_byte_data(client, APDS9960_CONTROL_REG, control_data);
	}
	
	i2c_smbus_write_word_data(client, APDS9960_AILTL_REG, data->als_threshold_l);
	i2c_smbus_write_word_data(client, APDS9960_AIHTL_REG, data->als_threshold_h);
}

static void apds9960_reschedule_work(struct apds9960_data *data,
					  unsigned long delay)
{
	/*
	 * If work is already scheduled then subsequent schedules will not
	 * change the scheduled time that's why we have to cancel it first.
	 */
	__cancel_delayed_work(&data->dwork);
	queue_delayed_work(apds_workqueue, &data->dwork, delay);
}

void ResetGestureParameters()
{
	gesture_data.index = 0;
	gesture_data.total_gestures = 0;

	gesture_ud_delta=0;
	gesture_lr_delta=0;
	gesture_state=0;

	negative_ud_delta=0;
	positive_ud_delta=0;
	negative_lr_delta=0;
	positive_lr_delta=0;
	ud_delta_positive_negative=0;
	lr_delta_positive_negative=0;

	gesture_circle_state = 0;
	gesture_circle_cw_count = 0;
	gesture_circle_acw_count = 0;

	gesture_ud_count=0;
	gesture_lr_count=0;

	gesture_near_count = 0;
	gesture_far_count = 0;

	gesture_prev_motion = DIR_NONE;

	gesture_start_time=0;
	gesture_end_time=0;
	gesture_time=0;
	gesture_speed=0;
}

void SaveToFile(void)
{
	struct file* fd;
	int i;
	char dummyc[100];

	fd = filp_open("/sdcard/gesture.csv", O_CREAT|O_APPEND|O_WRONLY, 0644);
    if (IS_ERR (fd))
    {
    	printk (KERN_ERR "Failed to open file.\n");
    }
    else
    {
		for (i=0; i<gesture_data.total_gestures; i++) {
			sprintf(dummyc, "%d, %d, %d, %d\r\n", gesture_data.u_data[i], gesture_data.d_data[i], gesture_data.l_data[i], gesture_data.r_data[i]);
	    	fd->f_op->write (fd, dummyc, strlen(dummyc), &fd->f_pos);
			fd->f_pos += strlen(dummyc);
		}
    }

	filp_close(fd, NULL);
}

unsigned int GetTickCount(void)
{
	struct timeval tv;

	do_gettimeofday(&tv);

	return ( (tv.tv_sec * 1000) + (tv.tv_usec/1000) ); // return in msec
}

int FilterGestureRawData(GESTURE_DATA_TYPE *gesture_in_data, GESTURE_DATA_TYPE *gesture_out_data)
{
	int i;

	if (gesture_in_data->total_gestures > 32 || gesture_in_data->total_gestures <= 0) return -1;

	gesture_out_data->total_gestures = 0;

	for (i=0; i<gesture_in_data->total_gestures; i++) {

		if ( gesture_in_data->u_data[i] > gesture_in_data->out_threshold &&
			 gesture_in_data->d_data[i] > gesture_in_data->out_threshold &&
			 gesture_in_data->l_data[i] > gesture_in_data->out_threshold &&
			 gesture_in_data->r_data[i] > gesture_in_data->out_threshold ) {
				
			gesture_out_data->u_data[gesture_out_data->total_gestures] = gesture_in_data->u_data[i];
			gesture_out_data->d_data[gesture_out_data->total_gestures] = gesture_in_data->d_data[i];
			gesture_out_data->l_data[gesture_out_data->total_gestures] = gesture_in_data->l_data[i];
			gesture_out_data->r_data[gesture_out_data->total_gestures] = gesture_in_data->r_data[i];

			gesture_out_data->total_gestures++;
		}
	}

	if (gesture_out_data->total_gestures == 0) return -1;

	for (i=1; i<gesture_out_data->total_gestures-1; i++) {

		gesture_out_data->u_data[i] = (gesture_out_data->u_data[i]+(gesture_out_data->u_data[i-1])+gesture_out_data->u_data[i+1])/3;
		gesture_out_data->d_data[i] = (gesture_out_data->d_data[i]+(gesture_out_data->d_data[i-1])+gesture_out_data->d_data[i+1])/3;
		gesture_out_data->l_data[i] = (gesture_out_data->l_data[i]+(gesture_out_data->l_data[i-1])+gesture_out_data->l_data[i+1])/3;
		gesture_out_data->r_data[i] = (gesture_out_data->r_data[i]+(gesture_out_data->r_data[i-1])+gesture_out_data->r_data[i+1])/3;
	}

	return 1;
}

void AveragingRawData(GESTURE_DATA_TYPE *gesture_data)
{
	int i, j;
	int loop;

	loop = (gesture_data->total_gestures - 4);

	for (i=0; i<loop; i++) {
		for (j=0; j<4; j++) {
			gesture_data->u_data[i] += gesture_data->u_data[i+j+1];
			gesture_data->d_data[i] += gesture_data->d_data[i+j+1];
			gesture_data->l_data[i] += gesture_data->l_data[i+j+1];
			gesture_data->r_data[i] += gesture_data->r_data[i+j+1];
		}

		gesture_data->u_data[i] /= 5;
		gesture_data->d_data[i] /= 5;
		gesture_data->l_data[i] /= 5;
		gesture_data->r_data[i] /= 5;
	}

	for (i=loop; i<(gesture_data->total_gestures-1); i++) {
		for (j=0; j<(gesture_data->total_gestures-i-1); j++) {
			gesture_data->u_data[i] += gesture_data->u_data[i+j+1];
			gesture_data->d_data[i] += gesture_data->d_data[i+j+1];
			gesture_data->l_data[i] += gesture_data->l_data[i+j+1];
			gesture_data->r_data[i] += gesture_data->r_data[i+j+1];
		}

		if ((gesture_data->total_gestures-i) > 0)
		{
			gesture_data->u_data[i] /= (gesture_data->total_gestures-i);
			gesture_data->d_data[i] /= (gesture_data->total_gestures-i);
			gesture_data->l_data[i] /= (gesture_data->total_gestures-i);
			gesture_data->r_data[i] /= (gesture_data->total_gestures-i);
		}
	}
}

int GestureZone(int ud, int lr)
{
	if ( (ud<0 && lr>0) || (ud==0 && lr>0) ) {
		return GESTURE_ZONE_1;
	}
	else if (ud>0 && lr>0) {
		return GESTURE_ZONE_2;
	}
	else if ( (ud>0 && lr<0) || (ud>0 && lr==0) ) {
		return GESTURE_ZONE_3;
	}
	else if (ud<0 && lr<0) {
		return GESTURE_ZONE_4;
	}
	else
		return GESTURE_ZONE_UNKNOWN;
}

int DecodeGestureZone(int mapped, int start, int end)
{
	if ( (start==2 && end==1) || (start==3 && end==4) ) {
		if (!mapped) {
			return DIR_UP;
		}
		else {
			return DIR_RIGHT;	// mapped
		}		
	}
	else if ( (start==1 && end==2) || (start==4 && end==3) ) {
		if (!mapped) {
			return DIR_DOWN;
		}
		else {	
			return DIR_LEFT;	// mapped
		}		
	}
	else if ( (start==1 && end==4) || (start==2 && end==3) ) {
		if (!mapped) {
			return DIR_LEFT;
		}
		else {	
			return DIR_UP;	// mapped
		}		
	}
	else if ( (start==4 && end==1) || (start==3 && end==2) ) {
		if (!mapped) {
			return DIR_RIGHT;
		}
		else {	
			return DIR_DOWN;	// mapped
		}		
	}
	else if (start==1 && end==3) {
		if (!mapped) {
			return DIR_DOWN_LEFT;
		}
		else {	
			return DIR_LEFT_UP;	// mapped
		}		
	}
	else if (start==3 && end==1) {
		if (!mapped) {
			return DIR_UP_RIGHT;
		}
		else {	
			return DIR_RIGHT_DOWN;	// mapped
		}		
	}
	else if (start==4 && end==2) {
		if (!mapped) {
			return DIR_RIGHT_DOWN;
		}
		else {	
			return DIR_DOWN_LEFT;	// mapped
		}		
	}
	else if (start==2 && end==4) {
		if (!mapped) {
			return DIR_LEFT_UP;
		}
		else {	
			return DIR_UP_RIGHT;	// mapped
		}		
	}

	return DIR_NONE;
}

int DecodeMappedGesture(int mapped, int motion) 
{
	if (!mapped) {
		return motion;
	}
	else {
		switch (motion)
		{
		case DIR_UP:
			return DIR_RIGHT;
		case DIR_DOWN:
			return DIR_LEFT;
		case DIR_LEFT:
			return DIR_UP;
		case DIR_RIGHT:
			return DIR_DOWN;
		case DIR_LEFT_UP:
			return DIR_UP_RIGHT;
		case DIR_RIGHT_DOWN:
			return DIR_DOWN_LEFT;
		case DIR_UP_RIGHT:
			return DIR_RIGHT_DOWN;
		case DIR_DOWN_LEFT:
			return DIR_LEFT_UP;
		case DIR_UP_U_TURN:
			return DIR_RIGHT_U_TURN;
		case DIR_DOWN_U_TURN:
			return DIR_LEFT_U_TURN;
		case DIR_LEFT_U_TURN:
			return DIR_UP_U_TURN;
		case DIR_RIGHT_U_TURN:
			return DIR_DOWN_U_TURN;
		default:
			return DIR_NONE;
		}
	}
}

int DecodeGesture(int gesture_mode)
{
	/* check timing */

	if ( (gesture_end_time - gesture_start_time) <= 100) {
		gesture_speed = 2; // fast
	}
	else if ((gesture_end_time - gesture_start_time) <= 300) {
		gesture_speed = 1; // medium
	}
	else {
		gesture_speed = 0; // slow
	}

	printk("gesture_state=%d, cw_count=%d, acw_count=%d, ud_count=%d, lr_count=%d, near_count=%d, far_count=%d\n", 
		gesture_state, gesture_circle_cw_count, gesture_circle_acw_count, gesture_ud_count, gesture_lr_count, gesture_near_count, gesture_far_count);

	// special case
	if (gesture_state == CIRCLE_CW_STATE) {

		if ( (gesture_prev_motion !=DIR_FAR) && 
			(gesture_prev_motion !=DIR_NEAR) ) {

			if (gesture_mode == 1) {
				gesture_motion = DIR_CIRCLE_CW;
				return 1;
			}
			else 
			{
				return -1;
			}
		}
		else
			return -1;
	}
	else if (gesture_state == CIRCLE_ACW_STATE) {

		if ( (gesture_prev_motion !=DIR_FAR) && 
			(gesture_prev_motion !=DIR_NEAR) ) {

			if (gesture_mode == 1) {
				gesture_motion = DIR_CIRCLE_ACW;
				return 1;
			}
			else 
			{
				return -1;
			}
		}
		else 
			return -1;
	}
	else if (gesture_state == NEAR_STATE) {
		
		if ( (gesture_prev_motion != DIR_CIRCLE_CW) && 
			(gesture_prev_motion != DIR_CIRCLE_ACW) ) {

			gesture_motion = DIR_NEAR;
			return 1;
		}
		else {
			return -1;
		}
	}
	else if (gesture_state == FAR_STATE) {

		if ( (gesture_prev_motion != DIR_CIRCLE_CW) && 
			(gesture_prev_motion != DIR_CIRCLE_ACW) ) {
		
			gesture_motion = DIR_FAR;
			return 1;
		}
		else {
			return -1;
		}
	}

#if 0
	if (gesture_circle_cw_count>1 && gesture_circle_acw_count>1) {
		gesture_motion = DIR_NONE;
		OutputDebugString("xxx\n");
		return -1;
	}
#endif

	if (gesture_ud_count == -1 &&
		gesture_lr_count == 0 ) {

		if ( (gesture_prev_motion !=DIR_FAR) && 
			(gesture_prev_motion !=DIR_NEAR) && 
			(gesture_prev_motion != DIR_CIRCLE_CW) &&
			(gesture_prev_motion != DIR_CIRCLE_ACW) ) {

			gesture_motion = DecodeMappedGesture(fMotionMapped, DIR_UP); 
		}
	}
	else if ( gesture_ud_count == 1 &&
			  gesture_lr_count == 0 ) {

		if ( (gesture_prev_motion !=DIR_FAR) && 
			(gesture_prev_motion !=DIR_NEAR) && 
			(gesture_prev_motion != DIR_CIRCLE_CW) &&
			(gesture_prev_motion != DIR_CIRCLE_ACW) ) {

			gesture_motion = DecodeMappedGesture(fMotionMapped, DIR_DOWN); 
		}
	}
	else if ( gesture_ud_count == 0 &&
			  gesture_lr_count == 1 ) {

		if ( (gesture_prev_motion !=DIR_FAR) && 
			(gesture_prev_motion !=DIR_NEAR) && 
			(gesture_prev_motion != DIR_CIRCLE_CW) &&
			(gesture_prev_motion != DIR_CIRCLE_ACW) ) {

			gesture_motion = DecodeMappedGesture(fMotionMapped, DIR_RIGHT); 
		}
	}
	else if ( gesture_ud_count == 0 &&
			  gesture_lr_count == -1 ) {

		if ( (gesture_prev_motion !=DIR_FAR) && 
			(gesture_prev_motion !=DIR_NEAR) && 
			(gesture_prev_motion != DIR_CIRCLE_CW) &&
			(gesture_prev_motion != DIR_CIRCLE_ACW) ) {

			gesture_motion = DecodeMappedGesture(fMotionMapped, DIR_LEFT); 
		}
	}
	else if ( gesture_ud_count == -1 &&
			  gesture_lr_count == 1 ) {

		if ( (gesture_prev_motion !=DIR_FAR) && 
			(gesture_prev_motion !=DIR_NEAR) && 
			(gesture_prev_motion != DIR_CIRCLE_CW) &&
			(gesture_prev_motion != DIR_CIRCLE_ACW) ) {

			if (gesture_mode == 1) {
				gesture_motion = DecodeMappedGesture(fMotionMapped, DIR_UP_RIGHT); 
			}
			else {
				if (abs(gesture_ud_delta) > abs(gesture_lr_delta)) {
					gesture_motion = DecodeMappedGesture(fMotionMapped, DIR_UP); 
				}
				else {
					gesture_motion = DecodeMappedGesture(fMotionMapped, DIR_RIGHT); 
				}
			}
		}
	}
	else if ( gesture_ud_count == 1 &&
			  gesture_lr_count == -1 ) {

		if ( (gesture_prev_motion !=DIR_FAR) && 
			(gesture_prev_motion !=DIR_NEAR) && 
			(gesture_prev_motion != DIR_CIRCLE_CW) &&
			(gesture_prev_motion != DIR_CIRCLE_ACW) ) {

			if (gesture_mode == 1) {
				gesture_motion = DecodeMappedGesture(fMotionMapped, DIR_DOWN_LEFT); 
			}
			else {
				if (abs(gesture_ud_delta) > abs(gesture_lr_delta)) {
					gesture_motion = DecodeMappedGesture(fMotionMapped, DIR_DOWN); 
				}
				else {
					gesture_motion = DecodeMappedGesture(fMotionMapped, DIR_LEFT); 
				}
			}
		}
	}
	else if ( gesture_ud_count == -1 &&
			  gesture_lr_count == -1 ) {

		if ( (gesture_prev_motion !=DIR_FAR) && 
			(gesture_prev_motion !=DIR_NEAR) && 
			(gesture_prev_motion != DIR_CIRCLE_CW) &&
			(gesture_prev_motion != DIR_CIRCLE_ACW) ) {

			if (gesture_mode == 1) {
				gesture_motion = DecodeMappedGesture(fMotionMapped, DIR_LEFT_UP); 
			}
			else {
				if (abs(gesture_ud_delta) > abs(gesture_lr_delta)) {
					gesture_motion = DecodeMappedGesture(fMotionMapped, DIR_UP); 
				}
				else {
					gesture_motion = DecodeMappedGesture(fMotionMapped, DIR_LEFT); 
				}
			}
		}
	}
	else if ( gesture_ud_count == 1 &&
			  gesture_lr_count == 1 ) {

		if ( (gesture_prev_motion !=DIR_FAR) && 
			(gesture_prev_motion !=DIR_NEAR) && 
			(gesture_prev_motion != DIR_CIRCLE_CW) &&
			(gesture_prev_motion != DIR_CIRCLE_ACW) ) {

			if (gesture_mode == 1) {
				gesture_motion = DecodeMappedGesture(fMotionMapped, DIR_RIGHT_DOWN); 
			}
			else {
				if (abs(gesture_ud_delta) > abs(gesture_lr_delta)) {
					gesture_motion = DecodeMappedGesture(fMotionMapped, DIR_DOWN); 
				}
				else {
					gesture_motion = DecodeMappedGesture(fMotionMapped, DIR_RIGHT); 
				}
			}
		}
	}
	else {
		if (positive_ud_delta > positive_lr_delta && negative_ud_delta < negative_lr_delta) {

			if (positive_ud_delta >= GESTURE_SENSITIVITY_LEVEL1 && negative_ud_delta <= -GESTURE_SENSITIVITY_LEVEL1) {

				if (ud_delta_positive_negative == 1) {
					gesture_motion = DecodeMappedGesture(fMotionMapped, DIR_DOWN_U_TURN); 

				}
				else if (ud_delta_positive_negative == 2) {
					gesture_motion = DecodeMappedGesture(fMotionMapped, DIR_UP_U_TURN); 

				}
			}
		}
		else if (positive_lr_delta > positive_ud_delta && negative_lr_delta < negative_ud_delta) {
			if (positive_lr_delta >= GESTURE_SENSITIVITY_LEVEL1 && negative_lr_delta <= -GESTURE_SENSITIVITY_LEVEL1) {
				if (lr_delta_positive_negative == 1) {
					gesture_motion = DecodeMappedGesture(fMotionMapped, DIR_RIGHT_U_TURN); 
				}
				else if (lr_delta_positive_negative == 2) {
					gesture_motion = DecodeMappedGesture(fMotionMapped, DIR_LEFT_U_TURN); 
				}

			}
		}
		else {

			printk("???\n");
			return -1;
		}
	}

	return 1;
}

int GestureDataProcessing()
{
	GESTURE_DATA_TYPE gesture_out_data;
	int ud_delta, lr_delta;
	int sensitivity1_threshold=GESTURE_SENSITIVITY_LEVEL1;
	int sensitivity2_threshold=GESTURE_SENSITIVITY_LEVEL2;
	int zone=0;
	int gesture_u_d_ratio_first, gesture_u_d_ratio_last;
	int gesture_l_r_ratio_first, gesture_l_r_ratio_last;

	if (gesture_data.total_gestures <= 4) return -1;

	/************** This is to detect fundamentals gesture ****************************/

	gesture_data.in_threshold = GESTURE_GTHR_IN;
	gesture_data.out_threshold = GESTURE_THRESHOLD_OUT-10;

	FilterGestureRawData(&gesture_data, &gesture_out_data);	// for fundamental 

	if (gesture_out_data.total_gestures == 0) return -1;

	if (gesture_out_data.u_data[0]==0) gesture_out_data.u_data[0] = 1;
	if (gesture_out_data.d_data[0]==0) gesture_out_data.d_data[0] = 1;
	if (gesture_out_data.l_data[0]==0) gesture_out_data.l_data[0] = 1;
	if (gesture_out_data.r_data[0]==0) gesture_out_data.r_data[0] = 1;

	if (gesture_out_data.u_data[gesture_out_data.total_gestures-1]==0) gesture_out_data.u_data[gesture_out_data.total_gestures-1] = 1;
	if (gesture_out_data.d_data[gesture_out_data.total_gestures-1]==0) gesture_out_data.d_data[gesture_out_data.total_gestures-1] = 1;
	if (gesture_out_data.l_data[gesture_out_data.total_gestures-1]==0) gesture_out_data.l_data[gesture_out_data.total_gestures-1] = 1;
	if (gesture_out_data.r_data[gesture_out_data.total_gestures-1]==0) gesture_out_data.r_data[gesture_out_data.total_gestures-1] = 1;

	gesture_u_d_ratio_first = (gesture_out_data.u_data[0] - gesture_out_data.d_data[0])*100/(gesture_out_data.u_data[0]+gesture_out_data.d_data[0]);
	gesture_l_r_ratio_first = (gesture_out_data.l_data[0] - gesture_out_data.r_data[0])*100/(gesture_out_data.l_data[0]+gesture_out_data.r_data[0]);
	gesture_u_d_ratio_last = (gesture_out_data.u_data[gesture_out_data.total_gestures-1] - gesture_out_data.d_data[gesture_out_data.total_gestures-1])*100/(gesture_out_data.u_data[gesture_out_data.total_gestures-1]+gesture_out_data.d_data[gesture_out_data.total_gestures-1]);
	gesture_l_r_ratio_last = (gesture_out_data.l_data[gesture_out_data.total_gestures-1] - gesture_out_data.r_data[gesture_out_data.total_gestures-1])*100/(gesture_out_data.l_data[gesture_out_data.total_gestures-1]+gesture_out_data.r_data[gesture_out_data.total_gestures-1]);

	ud_delta = (gesture_u_d_ratio_last - gesture_u_d_ratio_first);
	lr_delta = (gesture_l_r_ratio_last - gesture_l_r_ratio_first);

	if (ud_delta >= 0) {
		positive_ud_delta += ud_delta;

		if (positive_ud_delta >= sensitivity1_threshold && ud_delta_positive_negative == 0) {
			ud_delta_positive_negative = 1;
		}
	}
	else {
		negative_ud_delta += ud_delta;
		if (negative_ud_delta <= -sensitivity1_threshold && ud_delta_positive_negative == 0) {
			ud_delta_positive_negative = 2;
		}
	}

	if (lr_delta >= 0) {
		positive_lr_delta += lr_delta;
		if (positive_lr_delta >= sensitivity1_threshold && lr_delta_positive_negative == 0) {
			lr_delta_positive_negative = 1;
		}
	}
	else {
		negative_lr_delta += lr_delta;
		if (negative_lr_delta <= -sensitivity1_threshold && lr_delta_positive_negative == 0) {
			lr_delta_positive_negative = 2;
		}
	}

	gesture_ud_delta = ud_delta + gesture_ud_delta;
	gesture_lr_delta = lr_delta + gesture_lr_delta;

	/**************** for Left/Right/Up/Down ****************/
	if (gesture_ud_delta >= sensitivity1_threshold) {
		gesture_ud_count = 1;
	}
	else if (gesture_ud_delta <= -sensitivity1_threshold) {
		gesture_ud_count = -1;
	}
	else {
		gesture_ud_count = 0;
	}

	if (gesture_lr_delta >= sensitivity1_threshold) {
		gesture_lr_count = 1;
	}
	else if (gesture_lr_delta <= -sensitivity1_threshold) {
		gesture_lr_count = -1;
	}
	else
		gesture_lr_count = 0;
	/**************** for Left/Right/Up/Down ****************/

	if (gesture_ud_count == 0 && gesture_lr_count == 0) {

		if (abs(ud_delta)<sensitivity2_threshold && abs(lr_delta)<sensitivity2_threshold) {

			if (ud_delta==0 && lr_delta==0) {
				gesture_near_count++;
			}
			else if (ud_delta!=0 || lr_delta!=0) {
				gesture_far_count++;
			}

			if (gesture_near_count >= 10 && gesture_far_count >= 2) {

				if (ud_delta==0 && lr_delta==0) {
					gesture_state = NEAR_STATE;
				}
				else if (ud_delta!=0 && lr_delta!=0) {
					gesture_state = FAR_STATE;
				}

				return 1;
			}
		}
	}
	else {

		if (abs(ud_delta)<sensitivity2_threshold && abs(lr_delta)<sensitivity2_threshold) {

			if (ud_delta==0 && lr_delta==0) {
				gesture_near_count++;
			}

			if (gesture_near_count >= 10) {
				gesture_ud_count=0;
				gesture_lr_count=0;
				gesture_ud_delta = 0;
				gesture_lr_delta = 0;
			}
		}
	}

#if 1
	printk("ud_delta=%d, lr_delta=%d, total_ud=%d, total_lr=%d, ud_count=%d, lr_count=%d, near_count=%d, far_count=%d, n=%d\n", 
		ud_delta, lr_delta, gesture_ud_delta, gesture_lr_delta, 
		gesture_ud_count, gesture_lr_count, gesture_near_count, gesture_far_count, gesture_out_data.total_gestures);
#endif

	/********************* Circle Gesture *******************************/

	if (gesture_near_count >= 6) {
		
		gesture_circle_state=CIRCLE_NA;
		gesture_circle_cw_count=0;
		gesture_circle_acw_count=0;
		return -1;
	}

	AveragingRawData(&gesture_data);	// for circle gesture

	if (gesture_data.u_data[0] == 0) gesture_data.u_data[0] = 1;
	if (gesture_data.d_data[0] == 0) gesture_data.d_data[0] = 1;
	if (gesture_data.l_data[0] == 0) gesture_data.l_data[0] = 1;
	if (gesture_data.r_data[0] == 0) gesture_data.r_data[0] = 1;
	if (gesture_data.u_data[gesture_data.total_gestures-1] == 0) gesture_data.u_data[gesture_data.total_gestures-1] = 1;
	if (gesture_data.d_data[gesture_data.total_gestures-1] == 0) gesture_data.d_data[gesture_data.total_gestures-1] = 1;
	if (gesture_data.l_data[gesture_data.total_gestures-1] == 0) gesture_data.l_data[gesture_data.total_gestures-1] = 1;
	if (gesture_data.r_data[gesture_data.total_gestures-1] == 0) gesture_data.r_data[gesture_data.total_gestures-1] = 1;

	gesture_u_d_ratio_first = (gesture_data.u_data[0] - gesture_data.d_data[0])*500/(gesture_data.u_data[0]+gesture_data.d_data[0]);
	gesture_l_r_ratio_first = (gesture_data.l_data[0] - gesture_data.r_data[0])*500/(gesture_data.l_data[0]+gesture_data.r_data[0]);
	gesture_u_d_ratio_last = (gesture_data.u_data[gesture_data.total_gestures-1] - gesture_data.d_data[gesture_data.total_gestures-1])*500/(gesture_data.u_data[gesture_data.total_gestures-1]+gesture_data.d_data[gesture_data.total_gestures-1]);
	gesture_l_r_ratio_last = (gesture_data.l_data[gesture_data.total_gestures-1] - gesture_data.r_data[gesture_data.total_gestures-1])*500/(gesture_data.l_data[gesture_data.total_gestures-1]+gesture_data.r_data[gesture_data.total_gestures-1]);

	ud_delta = (gesture_u_d_ratio_last - gesture_u_d_ratio_first);
	lr_delta = (gesture_l_r_ratio_last - gesture_l_r_ratio_first);

#if 1
	printk("(circle) ud_delta=%d, lr_delta=%d, n=%d\n", ud_delta, lr_delta, gesture_data.total_gestures);
#endif

	if (abs(ud_delta) < sensitivity2_threshold && abs(lr_delta) < sensitivity2_threshold) 
		return -1;

	zone = GestureZone(ud_delta, lr_delta);

	if (zone == GESTURE_ZONE_1) {
		if (gesture_circle_state == CIRCLE_NA) {
			gesture_circle_state = CIRCLE_1;
		}
		else if (gesture_circle_state==CIRCLE_4) {
			gesture_circle_state = CIRCLE_1;
			gesture_circle_cw_count++;
		}
		else if (gesture_circle_state==CIRCLE_3) {
			gesture_circle_state = CIRCLE_1;
			if (gesture_circle_cw_count > gesture_circle_acw_count) {
				gesture_circle_cw_count++;
			}
			else if (gesture_circle_cw_count < gesture_circle_acw_count) {
				gesture_circle_acw_count++;
			}
		}
		else if (gesture_circle_state==CIRCLE_2) {
			gesture_circle_state = CIRCLE_1;
			gesture_circle_acw_count++;
		}
	}
	else if (zone == GESTURE_ZONE_2) {
		if (gesture_circle_state == CIRCLE_NA) {
			gesture_circle_state = CIRCLE_2;
		}
		else if (gesture_circle_state==CIRCLE_1) {
			gesture_circle_state = CIRCLE_2;
			gesture_circle_cw_count++;
		}
		else if (gesture_circle_state==CIRCLE_3) {
			gesture_circle_state = CIRCLE_2;
			gesture_circle_acw_count++;
		}
		else if (gesture_circle_state==CIRCLE_4) {
			gesture_circle_state = CIRCLE_2;
			if (gesture_circle_cw_count > gesture_circle_acw_count) {
				gesture_circle_cw_count++;
			}
			else if (gesture_circle_cw_count < gesture_circle_acw_count) {
				gesture_circle_acw_count++;
			}
		}
	}
	else if (zone == GESTURE_ZONE_3) {
		if (gesture_circle_state == CIRCLE_NA) {
			gesture_circle_state = CIRCLE_3;
		}
		else if (gesture_circle_state==CIRCLE_1) {
			gesture_circle_state = CIRCLE_3;
			if (gesture_circle_cw_count > gesture_circle_acw_count) {
				gesture_circle_cw_count++;
			}
			else if (gesture_circle_cw_count < gesture_circle_acw_count) {
				gesture_circle_acw_count++;
			}
		}
		else if (gesture_circle_state==CIRCLE_2) {
			gesture_circle_state = CIRCLE_3;
			gesture_circle_cw_count++;
		}
		else if (gesture_circle_state==CIRCLE_4) {
			gesture_circle_state = CIRCLE_3;
			gesture_circle_acw_count++;
		}
	}
	else if (zone == GESTURE_ZONE_4) {
		if (gesture_circle_state == CIRCLE_NA) {
			gesture_circle_state = CIRCLE_4;
		}
		else if (gesture_circle_state==CIRCLE_1) {
			gesture_circle_state = CIRCLE_4;
			gesture_circle_acw_count++;
		}
		else if (gesture_circle_state==CIRCLE_2) {
			gesture_circle_state = CIRCLE_4;
			if (gesture_circle_cw_count > gesture_circle_acw_count) {
				gesture_circle_cw_count++;
			}
			else if (gesture_circle_cw_count < gesture_circle_acw_count) {
				gesture_circle_acw_count++;
			}
		}
		else if (gesture_circle_state==CIRCLE_3) {
			gesture_circle_state = CIRCLE_4;
			gesture_circle_cw_count++;
		}
	}

	if (gesture_circle_cw_count>=5 || gesture_circle_acw_count>=5) {
		if (gesture_circle_cw_count>=5) {
			gesture_state = CIRCLE_CW_STATE;
			gesture_state = CIRCLE_CW_STATE;
		}
		else {
			gesture_state = CIRCLE_ACW_STATE;
			gesture_state = CIRCLE_ACW_STATE;
		}

		return 1;
	}

	/************** Remove this code if not needed *********************/

	return -1;
}

static void apds9960_gesture_processing(struct i2c_client *client)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int gstatus;
	int gfifo_level;
	int gfifo_read;
	unsigned char gfifo_data[128];
	int i;
	//int fFifoOverflow = 0;
	
	/* need to loop gstatus until fifo is empty */
	gesture_start_time = GetTickCount();

	while (1) {

		mdelay(30);	// is this enough to collect next batch of the fifo data?

		gstatus = i2c_smbus_read_byte_data(client, APDS9960_GSTATUS_REG);

		//printk("%s: gstatus = %d\n", __func__, gstatus);

		if (gstatus < 0) {
			printk("%s (%d): exit 1\n", __func__, gstatus);
			return;
		}

		if ((gstatus & APDS9960_GFIFO_OV) == APDS9960_GFIFO_OV) {
			//fFifoOverflow = 1;
		}
		
		if ((gstatus & APDS9960_GVALID) == APDS9960_GVALID) {
			gfifo_level = i2c_smbus_read_byte_data(client, APDS9960_GFIFO_LVL_REG);	
			printk("%s: gfifo_level = %d\n", __func__, gfifo_level);

			if (gfifo_level > 0) {
				gfifo_read = i2c_smbus_read_i2c_block_data(client, APDS9960_GFIFO0_REG, 
													(gfifo_level*4), (unsigned char*)gfifo_data);
				//printk("%s: gfifo_read = %d\n", __func__, gfifo_read);

				if (gfifo_read >= 4) {

					for (i=0; i<gfifo_read; i+=4) {
						gesture_data.u_data[gesture_data.index] = gfifo_data[i+0];
						gesture_data.d_data[gesture_data.index] = gfifo_data[i+1];
						gesture_data.l_data[gesture_data.index] = gfifo_data[i+2];
						gesture_data.r_data[gesture_data.index] = gfifo_data[i+3];
						gesture_data.index++;
						gesture_data.total_gestures++;
					}

					if (GestureDataProcessing() > 0) {
						if (DecodeGesture(gesture_fundamentals) > 0) {
							// this is to detect far/near/tilt/circle gestures
							printk("==> %s (%s)\n", gesture_str[gesture_motion], gesture_str[gesture_prev_motion]);
							if (gesture_prev_motion != gesture_motion && gesture_motion != DIR_NONE) {

								/* event->value
								 PS_NEAR = 0 
								 DIR_LEFT = 1
								 DIR_RIGHT =2
								 DIR_UP = 3
								 DIR_DOWN = 4
								 DIR_UP_RIGHT = 5
								 DIR_RIGHT_DOWN = 6
								 DIR_DOWN_LEFT = 7
								 DIR_LEFT_UP = 8
								 DIR_NEAR = 9
								 DIR_FAR = 10
								 DIR_CIRCLE_CW =11
								 DIR_CIRCLE_ACW = 12
								 DIR_UP_U_TURN = 13
								 DIR_DOWN_U_TURN = 14
								 DIR_LEFT_U_TURN = 15
								 DIR_RIGHT_U_TURN = 16
								 PS_FAR = 20
								*/

								input_report_abs(data->input_dev_ps, ABS_DISTANCE, gesture_motion); /* GESTURE event */	
								input_sync(data->input_dev_ps);
							}
							
							gesture_prev_motion = gesture_motion;
	
						}
					}

					gesture_data.index = 0;
					gesture_data.total_gestures = 0;

					//mdelay(30);	// is this enough to collect next batch of the fifo data?
				}
			}
		}
		else {

			gesture_end_time = GetTickCount();

			//SaveToFile();
			if (DecodeGesture(gesture_fundamentals) > 0) {	

				printk("+++ %s (%s)\n", gesture_str[gesture_motion], gesture_str[gesture_prev_motion]);
				if (gesture_prev_motion != gesture_motion && gesture_motion != DIR_NONE) {

				/* event->value
					PS_NEAR = 0 
					DIR_LEFT = 1
					DIR_RIGHT =2
					DIR_UP = 3
					DIR_DOWN = 4
					DIR_UP_RIGHT = 5
					DIR_RIGHT_DOWN = 6
					DIR_DOWN_LEFT = 7
					DIR_LEFT_UP = 8
					DIR_NEAR = 9
					DIR_FAR = 10
					DIR_CIRCLE_CW =11
					DIR_CIRCLE_ACW = 12
					DIR_UP_U_TURN = 13
					DIR_DOWN_U_TURN = 14
					DIR_LEFT_U_TURN = 15
					DIR_RIGHT_U_TURN = 16
					PS_FAR = 20
				*/
				
					input_report_abs(data->input_dev_ps, ABS_DISTANCE, gesture_motion); /* GESTURE event */	
					input_sync(data->input_dev_ps);
				}

				gesture_prev_motion = gesture_motion;
			}

			ResetGestureParameters();
			printk("%s: exit 5\n", __func__);
			return;
		}
	}
}

/* ALS polling routine */
static void apds9960_als_polling_work_handler(struct work_struct *work)
{
	printk("[%s]:into %d===========\n",__func__,__LINE__);
	struct apds9960_data *data = container_of(work, struct apds9960_data, als_dwork.work);
	struct i2c_client *client=data->client;
	unsigned char change_again=0;
	unsigned char control_data=0;
	unsigned char i2c_data[10];
	int status;
	if (data->enable_als_sensor != APDS_ENABLE_ALS_NO_INT)
		return;

	status = i2c_smbus_read_i2c_block_data(client, APDS9960_CDATAL_REG, 8, (unsigned char*)i2c_data);

	if (status < 0) return;
	if (status != 8) return;

	data->cdata = (i2c_data[1]<<8)|i2c_data[0];
	data->rdata = (i2c_data[3]<<8)|i2c_data[2];
	data->gdata = (i2c_data[5]<<8)|i2c_data[4];
	data->bdata = (i2c_data[7]<<8)|i2c_data[6];

	LuxCalculation(client);
	
	if (data->lux >= 0) {
		data->lux = data->lux<30000 ? data->lux : 30000;
		data->als_prev_lux = data->lux;
	}

	if (data->cct >= 0) {
		data->cct = data->cct<10000 ? data->cct : 10000;
	}
	
	printk("cct=%d, lux=%d cdata=%d rdata=%d gdata=%d bdata=%d again=%d\n", 
		data->cct, data->lux, data->cdata, data->rdata, data->gdata, 
		data->bdata, apds9960_als_again_tb[data->als_again_index]);

	data->als_data = data->cdata;

	if (data->als_data >= (apds9960_als_res_tb[data->als_atime_index]*99)/100) {
		// lower AGAIN if possible
		if (data->als_again_index != APDS9960_ALS_GAIN_1X) {
			data->als_again_index--;
			change_again = 1;
		}
	}
	else if (data->als_data <= (apds9960_als_res_tb[data->als_atime_index]*1)/100) {
		// increase AGAIN if possible
		if (data->als_again_index != APDS9960_ALS_GAIN_64X) {
			data->als_again_index++;
			change_again = 1;
		}
	}
	else {
		input_report_abs(data->input_dev_als, ABS_LIGHT, data->lux); // report lux level
		input_report_abs(data->input_dev_als, ABS_CCT, data->cct); // report color temperature cct
		input_sync(data->input_dev_als);	
	}

	if (change_again) {
		control_data = i2c_smbus_read_byte_data(client, APDS9960_CONTROL_REG);
		control_data = control_data & 0xFC;
		control_data = control_data | apds9960_als_again_bit_tb[data->als_again_index];
		i2c_smbus_write_byte_data(client, APDS9960_CONTROL_REG, control_data);
	}
	
	queue_delayed_work(apds_workqueue, &data->als_dwork, msecs_to_jiffies(data->als_poll_delay));	// restart timer
}

/* ALS_PS interrupt routine */
static void apds9960_work_handler(struct work_struct *work)
{
	printk("[%s]:%d into==========\n",__func__,__LINE__);
	struct apds9960_data *data = container_of(work, struct apds9960_data, dwork.work);
	struct i2c_client *client=data->client;
	int status;

	status = i2c_smbus_read_byte_data(client, APDS9960_STATUS_REG);

	printk("==>isr : status=%x, enable=%x\n", status, data->enable);

	if ( (status & APDS9960_STATUS_GINT) && 
		 (data->enable & (APDS9960_GESTURE_ENABLE|APDS9960_PWR_ON)) && 
		 (data->gctrl & 0x02) ) {
		/* Gesture is enabled with interrupte */
		apds9960_gesture_processing(client);
		return;
	}
	
	if ( (status & APDS9960_STATUS_PINT) && 
		 ((data->enable & (APDS9960_PS_INT_ENABLE|APDS9960_PS_ENABLE|APDS9960_PWR_ON)) == (APDS9960_PS_INT_ENABLE|APDS9960_PS_ENABLE|APDS9960_PWR_ON)) ) {
		/* PS is interrupted */

		/* check if this is triggered by background ambient noise */
		printk("APDS9960_STATUS_PSAT\n");
		if (status & APDS9960_STATUS_PSAT) {
			printk("PS is triggered by background ambient noise\n");
		}
		else if (status & APDS9960_STATUS_PVALID) {
			apds9960_change_ps_threshold(client);		
		}
	}
	printk("==>isr 2 : status=%x, enable=%x\n", status, data->enable);
	if ( (status & APDS9960_STATUS_AINT) && 
		 ((data->enable & (APDS9960_ALS_INT_ENABLE|APDS9960_ALS_ENABLE|APDS9960_PWR_ON)) == (APDS9960_ALS_INT_ENABLE|APDS9960_ALS_ENABLE|APDS9960_PWR_ON)) ) {
		/* ALS is interrupted */	
		/* check if this is triggered by background ambient noise */
		if (status & APDS9960_STATUS_ASAT) {
			printk("ALS is saturated\n");
		}
		else if (status & APDS9960_STATUS_AVALID) {
			apds9960_change_als_threshold(client);
		}
	}
	//queue_delayed_work(apds_workqueue, &data->dwork, msecs_to_jiffies(data->als_poll_delay));	
	apds9960_clear_interrupt(client, CMD_CLR_ALL_INT);

	printk("exit<== apds9960_work_handler\n");
}

/* assume this is ISR */
static irqreturn_t apds9960_interrupt(int vec, void *info)
{
	printk("[%s] into %d ==>\n",__func__,__LINE__);
	struct i2c_client *client=(struct i2c_client *)info;
	struct apds9960_data *data = i2c_get_clientdata(client);

	printk("==> apds9960_interrupt\n");
	apds9960_reschedule_work(data, 0);	/* ensure PS cycle is completed if ALS interrupt asserts */	
	printk("<== apds9960_interrupt\n");

	return IRQ_HANDLED;
}

/*
 * IOCTL support
 */

static int apds9960_enable_als_sensor(struct i2c_client *client, int val)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
 	
	printk("%s: enable als sensor (%d)\n", __func__, val);
	if ((val != APDS_DISABLE_ALS) && (val != APDS_ENABLE_ALS_WITH_INT) && (val != APDS_ENABLE_ALS_NO_INT)) {
		printk("%s: enable als sensor=%d\n", __func__, val);
		return -1;
	}
	if ((val == APDS_ENABLE_ALS_WITH_INT) || (val == APDS_ENABLE_ALS_NO_INT)) {
		printk("[%s]:val= %d ###########\n", __func__, val);
		// turn on light  sensor
		if (data->enable_als_sensor==APDS_DISABLE_ALS) {
			printk("[%s]:%d ###########\n", __func__, __LINE__);
			data->enable_als_sensor = val;
			apds9960_set_enable(client, 0); /* Power Off */
				
			if (data->enable_als_sensor == APDS_ENABLE_ALS_NO_INT) {
				if (data->enable_ps_sensor) {
					apds9960_set_enable(client, 0x27);	 /* Enable PS with interrupt */
				}
				else {
					apds9960_set_enable(client, 0x03);	 /* no interrupt*/

				}
				/*
				 * If work is already scheduled then subsequent schedules will not
				 * change the scheduled time that's why we have to cancel it first.
				 */
				__cancel_delayed_work(&data->als_dwork);
				flush_delayed_work(&data->als_dwork);
				queue_delayed_work(apds_workqueue, &data->als_dwork, msecs_to_jiffies(data->als_poll_delay));		
			}
			else {	// als with int
				apds9960_set_ailt(client, 0xFFFF);	// force first ALS interrupt in order to get environment reading
				apds9960_set_aiht(client, 0);
				if (data->enable_ps_sensor) {
					apds9960_set_enable(client, 0x37);	 /* Enable both ALS and PS with interrupt */
				}
				else {
					printk("apds9960_set_enable 0x13\n");
					apds9960_set_enable(client, 0x13);	 /* only enable light sensor with interrupt*/
				}
				/*
				 * If work is already scheduled then subsequent schedules will not
				 * change the scheduled time that's why we have to cancel it first.
				 */
				__cancel_delayed_work(&data->als_dwork);
				flush_delayed_work(&data->als_dwork);		
			}		

		}
	}
	else {
		//turn off light sensor
		// what if the p sensor is active?
		data->enable_als_sensor = APDS_DISABLE_ALS;
		if ( (data->enable_ps_sensor == APDS_ENABLE_PS) &&
			 (data->enable_gesture_sensor == APDS_DISABLE_GESTURE) )  {
			apds9960_set_enable(client,0); /* Power Off */
			
			apds9960_set_pilt(client, APDS9960_FAR_THRESHOLD_HIGH);		
			apds9960_set_piht(client, APDS9960_NEAR_THRESHOLD_LOW);

			apds9960_set_enable(client, 0x2D);	 /* only enable prox sensor with interrupt */
		}
		else if ( (data->enable_ps_sensor == APDS_ENABLE_PS) &&
			 	  (data->enable_gesture_sensor == APDS_ENABLE_GESTURE) )  {
			apds9960_set_enable(client, 0x4D);	 /* only enable gesture sensor with ps */			
		}
		else {
			apds9960_set_enable(client, 0);
		}
						
		/*
		 * If work is already scheduled then subsequent schedules will not
		 * change the scheduled time that's why we have to cancel it first.
		 */
		__cancel_delayed_work(&data->als_dwork);
		flush_delayed_work(&data->als_dwork);		
	}
	printk("%s:%d exit=========\n",__func__,__LINE__);
	return 0;
}

static int apds9960_set_als_poll_delay(struct i2c_client *client, unsigned int val)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
	int ret;
	int atime_index=0;
 	
	printk("%s : %d\n", __func__, val);

	if ((val != APDS_ALS_POLL_SLOW) && (val != APDS_ALS_POLL_MEDIUM) && (val != APDS_ALS_POLL_FAST)) {
		printk("%s:invalid value=%d\n", __func__, val);
		return -1;
	}
	
	if (val == APDS_ALS_POLL_FAST) {
		data->als_poll_delay = 50;		// 50ms
		atime_index = APDS9960_ALS_RES_24MS;
	}
	else if (val == APDS_ALS_POLL_MEDIUM) {
		data->als_poll_delay = 200;		// 200ms
		atime_index = APDS9960_ALS_RES_50MS;
	}
	else {	// APDS_ALS_POLL_SLOW
		data->als_poll_delay = 1000;		// 1000ms
		atime_index = APDS9960_ALS_RES_100MS;
	}

	ret = apds9960_set_atime(client, apds9960_als_atime_tb[atime_index]);
	if (ret >= 0) {
		data->als_atime_index = atime_index;
		printk("poll delay %d, atime_index %d\n", data->als_poll_delay, data->als_atime_index);
	}
	else
		return -1;
		
	/*
	 * If work is already scheduled then subsequent schedules will not
	 * change the scheduled time that's why we have to cancel it first.
	 */
	__cancel_delayed_work(&data->als_dwork);
	flush_delayed_work(&data->als_dwork);
	queue_delayed_work(apds_workqueue, &data->als_dwork, msecs_to_jiffies(data->als_poll_delay));
				
	return 0;
}




static int apds9960_enable_ps_sensor(struct i2c_client *client, int val)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
 	
	printk("enable ps senosr (%d)\n", val);
	
	if ((val != APDS_DISABLE_PS) && (val != APDS_ENABLE_PS)) {
		printk("%s:invalid value=%d\n", __func__, val);
		return -1;
	}
	
	if (val == APDS_ENABLE_PS) {	// interrupt mode only
		//turn on p sensor
		printk("%s turn on p sensor data->enable_als_sensor = %d\n",__func__,data->enable_als_sensor);
		if (data->enable_ps_sensor==APDS_DISABLE_PS) {

			data->enable_ps_sensor= APDS_ENABLE_PS;
		
			apds9960_set_enable(client,0); /* Power Off */
		
			apds9960_set_ppulse(client, data->ps_ppulse);

			apds9960_set_control(client, APDS9960_PDRVIE_FOR_PS|APDS9960_PGAIN_FOR_PS|apds9960_als_again_bit_tb[data->als_again_index]);

			apds9960_set_poffset_ur(client, data->ps_poffset_ur);
			apds9960_set_poffset_dl(client, data->ps_poffset_dl);

			// force first interrupt to inform HAL
			apds9960_set_pilt(client, APDS9960_FAR_THRESHOLD_HIGH);		
			apds9960_set_piht(client, APDS9960_NEAR_THRESHOLD_LOW);
			apds9960_set_aux(client, APDS9960_PS_LED_BOOST|0x01);

			// disable gesture if it was enabled previously
			if (data->enable_als_sensor == APDS_DISABLE_ALS) {
				printk("apds9960_set_enable 0x2D\n");
				apds9960_set_wtime(client, 0xF6);
				apds9960_set_enable(client, 0x2D);	 /* only enable PS interrupt */
			}
			else if (data->enable_als_sensor == APDS_ENABLE_ALS_WITH_INT) {
				apds9960_set_enable(client, 0x37);	 /* enable ALS and PS interrupt */
			}
			else { // APDS_ENABLE_ALS_NO_INT
				apds9960_set_enable(client, 0x27);	 /* enable PS interrupt only */
			}
			queue_delayed_work(apds_workqueue, &data->dwork, msecs_to_jiffies(data->als_poll_delay));	
			
		}
	} 
	else {
		//turn off p sensor - can't turn off the entire sensor, the light sensor may be needed by HAL
		data->enable_ps_sensor = APDS_DISABLE_PS;
		if ( (data->enable_als_sensor == APDS_ENABLE_ALS_NO_INT) &&
			 (data->enable_gesture_sensor == APDS_DISABLE_GESTURE) ) {
			apds9960_set_enable(client, 0x03);	 /* no ALS interrupt */
		
			/*
			 * If work is already scheduled then subsequent schedules will not
			 * change the scheduled time that's why we have to cancel it first.
			 */
			 printk("%s queue_delayed_work \n", __func__);
			__cancel_delayed_work(&data->als_dwork);
			flush_delayed_work(&data->als_dwork);
			queue_delayed_work(apds_workqueue, &data->als_dwork, msecs_to_jiffies(data->als_poll_delay));	
			
		}
		else if ( (data->enable_als_sensor == APDS_ENABLE_ALS_WITH_INT) &&
				  (data->enable_gesture_sensor == APDS_DISABLE_GESTURE) ) {
			// reconfigute light sensor setting			
			apds9960_set_enable(client,0); /* Power Off */
			apds9960_set_ailt( client, 0xFFFF);	// Force ALS interrupt
			apds9960_set_aiht( client, 0);
						
			apds9960_set_enable(client, 0x13);	 /* enable ALS interrupt */
		}
		else if ( (data->enable_als_sensor == APDS_DISABLE_ALS) &&
				  (data->enable_gesture_sensor == APDS_DISABLE_GESTURE) ) {
			apds9960_set_enable(client, 0);			
			/*
			 * If work is already scheduled then subsequent schedules will not
			 * change the scheduled time that's why we have to cancel it first.
			 */
			__cancel_delayed_work(&data->als_dwork);
			flush_delayed_work(&data->als_dwork);		
		}
		else if (data->enable_gesture_sensor == APDS_ENABLE_GESTURE) {
			apds9960_set_enable(client, 0x4D);		
			/*

			 * If work is already scheduled then subsequent schedules will not
			 * change the scheduled time that's why we have to cancel it first.
			 */
			__cancel_delayed_work(&data->als_dwork);
			flush_delayed_work(&data->als_dwork);
		}
	}
	
	return 0;
}

static int apds9960_enable_gesture_sensor(struct i2c_client *client, int val)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
 	
	printk("enable gesture senosr (%d)\n", val);
	
	if ((val != APDS_DISABLE_GESTURE) && (val != APDS_ENABLE_GESTURE)) {
		printk("%s:invalid value=%d\n", __func__, val);
		return -1;
	}
	
	if (val == APDS_ENABLE_GESTURE) {	// interrupt mode only

		if (data->enable_gesture_sensor==APDS_DISABLE_GESTURE) {

			data->enable_gesture_sensor= APDS_ENABLE_GESTURE;
		
			ResetGestureParameters();

			apds9960_set_enable(client,0); /* Power Off */

			apds9960_set_wtime(client, 0xFF);

			apds9960_set_ppulse(client, data->gesture_ppulse);

			apds9960_set_control(client, APDS9960_PDRVIE_FOR_GESTURE|APDS9960_PGAIN_FOR_GESTURE|apds9960_als_again_bit_tb[data->als_again_index]);

			apds9960_set_poffset_ur(client, data->gesture_poffset_ur);
			apds9960_set_poffset_dl(client, data->gesture_poffset_dl);

			apds9960_set_aux(client, APDS9960_GESTURE_LED_BOOST|0x01);

			// gesture registers
			apds9960_set_gctrl(client, 0x07);

			if (data->enable_als_sensor == APDS_ENABLE_ALS_NO_INT) {
				// need to turn on p sensor for gesture mode
				apds9960_set_enable(client, APDS9960_PWR_ON|APDS9960_ALS_ENABLE|APDS9960_PS_ENABLE|APDS9960_GESTURE_ENABLE);

				__cancel_delayed_work(&data->als_dwork);
				flush_delayed_work(&data->als_dwork);
				queue_delayed_work(apds_workqueue, &data->als_dwork, msecs_to_jiffies(data->als_poll_delay));		
			}
			else if (data->enable_als_sensor == APDS_ENABLE_ALS_WITH_INT) {
				// need to turn on p sensor for gesture mode
				apds9960_set_enable(client, APDS9960_PWR_ON|APDS9960_ALS_ENABLE|APDS9960_PS_ENABLE|APDS9960_ALS_INT_ENABLE|APDS9960_GESTURE_ENABLE);
			}
			else { // APDS_DISABLE_ALS
				// need to turn on p sensor for gesture mode
				apds9960_set_enable(client, APDS9960_PWR_ON|APDS9960_WAIT_ENABLE|APDS9960_PS_ENABLE|APDS9960_GESTURE_ENABLE);
			}
		}
	} 
	else {
		//turn off gesture sensor - can't turn off the entire sensor, the light/proximity sensor may be needed by HAL
		data->enable_gesture_sensor = APDS_DISABLE_GESTURE;
		if ( (data->enable_als_sensor == APDS_ENABLE_ALS_NO_INT) &&
			 (data->enable_ps_sensor == APDS_DISABLE_PS) ) {
			apds9960_set_enable(client, 0x03);	 /* no ALS interrupt */
		
			/*
			 * If work is already scheduled then subsequent schedules will not
			 * change the scheduled time that's why we have to cancel it first.
			 */
			__cancel_delayed_work(&data->als_dwork);
			flush_delayed_work(&data->als_dwork);
			queue_delayed_work(apds_workqueue, &data->als_dwork, msecs_to_jiffies(data->als_poll_delay));	
			
		}
		else if ( (data->enable_als_sensor == APDS_ENABLE_ALS_WITH_INT) && 
				  (data->enable_ps_sensor == APDS_DISABLE_PS) ){
			// reconfigute light sensor setting			
			apds9960_set_enable(client,0); /* Power Off */
			apds9960_set_ailt( client, 0xFFFF);	// Force ALS interrupt
			apds9960_set_aiht( client, 0);
						
			apds9960_set_enable(client, 0x13);	 /* enable ALS interrupt */
		}
		else if ( (data->enable_als_sensor == APDS_DISABLE_ALS) && 
				  (data->enable_ps_sensor == APDS_DISABLE_PS) ){
			apds9960_set_enable(client, 0);
			
			/*
			 * If work is already scheduled then subsequent schedules will not
			 * change the scheduled time that's why we have to cancel it first.
			 */
			__cancel_delayed_work(&data->als_dwork);
			flush_delayed_work(&data->als_dwork);		
		}
		else if ( (data->enable_als_sensor == APDS_ENABLE_ALS_NO_INT) &&
			 	  (data->enable_ps_sensor == APDS_ENABLE_PS) ) {
			apds9960_set_enable(client, 0x27);	 /* no ALS interrupt */
		
			/*
			 * If work is already scheduled then subsequent schedules will not
			 * change the scheduled time that's why we have to cancel it first.
			 */
			__cancel_delayed_work(&data->als_dwork);
			flush_delayed_work(&data->als_dwork);
			queue_delayed_work(apds_workqueue, &data->als_dwork, msecs_to_jiffies(data->als_poll_delay));	
			
		}
		else if ( (data->enable_als_sensor == APDS_ENABLE_ALS_WITH_INT) && 
				  (data->enable_ps_sensor == APDS_ENABLE_PS) ){
			// reconfigute light sensor setting			
			apds9960_set_enable(client,0); /* Power Off */
			apds9960_set_ailt( client, 0xFFFF);	// Force ALS interrupt
			apds9960_set_aiht( client, 0);
						
			apds9960_set_enable(client, 0x37);	 /* enable ALS interrupt */
		}
		else {	// APDS_DISBLE_ALS & APDS_ENALBE_PS
			apds9960_set_enable(client, 0);
			
			// force first interrupt to inform HAL
			apds9960_set_pilt(client, APDS9960_FAR_THRESHOLD_HIGH);		
			apds9960_set_piht(client, APDS9960_NEAR_THRESHOLD_LOW);
			
			apds9960_set_enable(client, 0x2D);	 /* only enable PS interrupt */
			/*
			 * If work is already scheduled then subsequent schedules will not
			 * change the scheduled time that's why we have to cancel it first.
			 */
			__cancel_delayed_work(&data->als_dwork);
			flush_delayed_work(&data->als_dwork);		
		}
	}
	
	return 0;
}

static int apds9960_calibration(struct i2c_client *client, int val)
{
	struct apds9960_data *data = i2c_get_clientdata(client);
#ifdef PLATFORM_SENSOR_APDS9960
	struct apds9960_platform_data* platform_data = client->dev.platform_data;
#endif	
	int status;
	unsigned char i2c_data[10];
	unsigned int old_lux_GA1, old_cct_GA1;
	int loop=0;
	unsigned int old_poffset_ur, old_poffset_dl, old_wtime, old_config2, old_gctrl;
	unsigned int old_goffset_u, old_goffset_d, old_goffset_l, old_goffset_r;
	unsigned int old_gthr_in=0, old_gthr_out=0;
	unsigned int old_control, old_ppulse;
	unsigned int temp_offset=0, temp_offset_negative=127;
	unsigned int temp_goffset_u=0, temp_goffset_u_negative=127;
	unsigned int temp_goffset_d=0, temp_goffset_d_negative=127;
	unsigned int temp_goffset_l=0, temp_goffset_l_negative=127;
	unsigned int temp_goffset_r=0, temp_goffset_r_negative=127;	
	int gstatus;
	unsigned int gesture_u, gesture_d, gesture_l, gesture_r;
	unsigned int gesture_u_cal_done = 0;
	unsigned int gesture_d_cal_done = 0;
	unsigned int gesture_l_cal_done = 0;
	unsigned int gesture_r_cal_done = 0;
 	
	printk("apds9960_calibration (%d)\n", val);
	
	if ((data->enable_ps_sensor != APDS_DISABLE_PS) ||
		(data->enable_als_sensor != APDS_DISABLE_ALS) ||
		(data->enable_gesture_sensor != APDS_DISABLE_GESTURE)) {

		printk("%s:sensor is in active mode, no calibration\n", __func__);
		return -1;
	}
	
	if (val == APDS_ALS_CALIBRATION) {

		apds9960_set_enable(client,0x03);
		mdelay(100);		
		status = i2c_smbus_read_i2c_block_data(client, APDS9960_CDATAL_REG, 8, (unsigned char*)i2c_data);

		if (status < 0) return status;
		if (status != 8) return -1;

		data->cdata = (i2c_data[1]<<8)|i2c_data[0];
		data->rdata = (i2c_data[3]<<8)|i2c_data[2];
		data->gdata = (i2c_data[5]<<8)|i2c_data[4];
		data->bdata = (i2c_data[7]<<8)|i2c_data[6];

		old_lux_GA1 = data->lux_GA1;
		old_cct_GA1 = data->cct_GA1;
		data->lux_GA1 = 100;
		LuxCalculation(client);

		if ((data->lux >= (APDS9960_ALS_CALIBRATED_LUX*70)/100) && 
			(data->lux <= (APDS9960_ALS_CALIBRATED_LUX*130)/100)) {
			data->lux_GA1 = (APDS9960_ALS_CALIBRATED_LUX*100)/data->lux;
#ifdef PLATFORM_SENSOR_APDS9960
			platform_data->lux_GA1 = data->lux_GA1;
#endif
			return 1;
		}
		else {
			data->lux_GA1 = old_lux_GA1;
			return -2;
		}

		if ((data->cct >= (APDS9960_ALS_CALIBRATED_CCT*90)/100) && 
			(data->cct <= (APDS9960_ALS_CALIBRATED_CCT*110)/100)) {
			data->cct_GA1 = (APDS9960_ALS_CALIBRATED_CCT*100)/data->cct;
#ifdef PLATFORM_SENSOR_APDS9960
			platform_data->cct_GA1 = data->cct_GA1;
#endif
			return 1;
		}
		else {
			data->cct_GA1 = old_cct_GA1;
			return -3;
		}

		printk("ALS cal done : %d lux\n", data->lux);
	}
	else if (val == APDS_PS_CALIBRATION) {

		old_control = data->control;
		old_ppulse = data->ppulse;
		old_poffset_ur = data->poffset_ur;
		old_poffset_dl = data->poffset_dl;
		old_wtime = data->wtime;
		old_config2 = data->config2;

		apds9960_set_wtime(client, 0xF6);
		apds9960_set_ppulse(client, data->ps_ppulse);
		apds9960_set_control(client, APDS9960_PDRVIE_FOR_PS|APDS9960_PGAIN_FOR_PS); 
		apds9960_set_config(client, 0x60);
		apds9960_set_config2(client, 0x26);
		apds9960_set_aux(client, APDS9960_PS_LED_BOOST|0x01);
		apds9960_set_poffset_ur(client, 0x00);
		apds9960_set_poffset_dl(client, 0x00);
		apds9960_set_enable(client, 0x0D);
		
		// POFFSET_UR
		loop = 0;
		temp_offset = 0;
		temp_offset_negative = 127;
		while (loop++ <= 127) {
			mdelay(10);		
			data->ps_data =	i2c_smbus_read_byte_data(client, APDS9960_PDATA_REG);

			if ((data->ps_data <= APDS9960_PS_CALIBRATED_XTALK) && (data->ps_data >= APDS9960_PS_CALIBRATED_XTALK_BASELINE)) {
				printk("cal ur %d (%d) - done\n", temp_offset, data->ps_data);
				break;
			}
			else {
				if (data->ps_data > APDS9960_PS_CALIBRATED_XTALK) {
				// reduce 
					if ((temp_offset >= 0) && (temp_offset <= 127)) {
						temp_offset += 1;
					}
					else {
						temp_offset -= 1;
					}
				}		
				else if (data->ps_data < APDS9960_PS_CALIBRATED_XTALK_BASELINE) {
					// increase
					if ((temp_offset > 0) && (temp_offset <= 127)) {
						temp_offset -= 1;
					}
					else {
						temp_offset_negative += 1;	// start from 128
						temp_offset = temp_offset_negative&0xFF;
					}					
				}
			}

			apds9960_set_poffset_ur(client, temp_offset);
		}

		if (loop >= 128) {
			apds9960_set_wtime(client, old_wtime);

			apds9960_set_ppulse(client, old_ppulse);
			apds9960_set_control(client, old_control); 
			apds9960_set_poffset_ur(client, old_poffset_ur);
			apds9960_set_poffset_dl(client, old_poffset_dl);
			apds9960_set_config2(client, old_config2);
			apds9960_set_enable(client, 0);
			return -4;
		}

#ifdef PLATFORM_SENSOR_APDS9960
		platform_data->ps_poffset_ur = data->poffset_ur;
#endif
		data->ps_poffset_ur = data->poffset_ur;

		apds9960_set_enable(client, 0x00);
		apds9960_set_config2(client, 0x29);
		apds9960_set_enable(client, 0x0D);

		loop = 0;
		temp_offset = 0;
		temp_offset_negative = 127;
		while (loop++ <= 127) {
			mdelay(10);		
			data->ps_data =	i2c_smbus_read_byte_data(client, APDS9960_PDATA_REG);

			if ((data->ps_data <= APDS9960_PS_CALIBRATED_XTALK) && (data->ps_data >= APDS9960_PS_CALIBRATED_XTALK_BASELINE)) {
				printk("cal dl %d (%d) - done\n", temp_offset, data->ps_data);
				break;
			}
			else {
				if (data->ps_data > APDS9960_PS_CALIBRATED_XTALK) {
				// reduce 
					if ((temp_offset >= 0) && (temp_offset <= 127)) {
						temp_offset += 1;
					}
					else {
						temp_offset -= 1;
					}
				}		
				else if (data->ps_data < APDS9960_PS_CALIBRATED_XTALK_BASELINE) {
					// increase
					if ((temp_offset > 0) && (temp_offset <= 127)) {
						temp_offset -= 1;
					}
					else {
						temp_offset_negative += 1;	// start from 128
						temp_offset = temp_offset_negative&0xFF;
					}					
				}
			}

			apds9960_set_poffset_dl(client, temp_offset);
		}
		
		if (loop >= 128) {
			apds9960_set_wtime(client, old_wtime);
			apds9960_set_ppulse(client, old_ppulse);
			apds9960_set_control(client, old_control); 
			apds9960_set_poffset_ur(client, old_poffset_ur);
			apds9960_set_poffset_dl(client, old_poffset_dl);
			apds9960_set_config2(client, old_config2);
			apds9960_set_enable(client, 0);
			printk("loop %d -5\n", loop);
			return -5;
		}

		apds9960_set_ppulse(client, old_ppulse);
		apds9960_set_control(client, old_control); 
		apds9960_set_wtime(client, old_wtime);
		apds9960_set_config2(client, old_config2);

#ifdef PLATFORM_SENSOR_APDS9960
		platform_data->ps_poffset_dl = data->poffset_dl;
#endif
		data->ps_poffset_dl = data->poffset_dl;

		printk("PS cal done : ur=%d dl=%d\n", data->poffset_ur, data->poffset_dl);
	} 
	else if (val == APDS_PS_GESTURE_CALIBRATION) {

		old_control = data->control;
		old_ppulse = data->ppulse;
		old_poffset_ur = data->poffset_ur;
		old_poffset_dl = data->poffset_dl;
		old_wtime = data->wtime;
		old_config2 = data->config2;

		apds9960_set_wtime(client, 0xF6);
		apds9960_set_ppulse(client, data->gesture_ppulse);
		apds9960_set_control(client, APDS9960_PDRVIE_FOR_GESTURE|APDS9960_PGAIN_FOR_GESTURE); 

		apds9960_set_config(client, 0x60);
		apds9960_set_config2(client, 0x26);
		apds9960_set_aux(client, APDS9960_GESTURE_LED_BOOST|0x01);
		apds9960_set_poffset_ur(client, 0x00);
		apds9960_set_poffset_dl(client, 0x00);
		apds9960_set_enable(client, 0x0D);
		
		// POFFSET_UR
		loop = 0;
		temp_offset = 0;
		temp_offset_negative = 127;
		while (loop++ <= 127) {
			mdelay(10);		
			data->ps_data =	i2c_smbus_read_byte_data(client, APDS9960_PDATA_REG);

			if ((data->ps_data <= APDS9960_PS_CALIBRATED_XTALK) && (data->ps_data >= APDS9960_PS_CALIBRATED_XTALK_BASELINE)) {
				printk("cal ur %d (%d) - done\n", temp_offset, data->ps_data);
				break;
			}
			else {
				if (data->ps_data > APDS9960_PS_CALIBRATED_XTALK) {
				// reduce 
					if ((temp_offset >= 0) && (temp_offset <= 127)) {
						temp_offset += 1;
					}
					else {
						temp_offset -= 1;
					}
				}		
				else if (data->ps_data < APDS9960_PS_CALIBRATED_XTALK_BASELINE) {
					// increase
					if ((temp_offset > 0) && (temp_offset <= 127)) {
						temp_offset -= 1;
					}
					else {
						temp_offset_negative += 1;	// start from 128
						temp_offset = temp_offset_negative&0xFF;
					}					
				}
			}

			apds9960_set_poffset_ur(client, temp_offset);
		}

		if (loop >= 128) {
			apds9960_set_wtime(client, old_wtime);

			apds9960_set_ppulse(client, old_ppulse);
			apds9960_set_control(client, old_control); 
			apds9960_set_poffset_ur(client, old_poffset_ur);
			apds9960_set_poffset_dl(client, old_poffset_dl);
			apds9960_set_config2(client, old_config2);
			apds9960_set_enable(client, 0);
			return -4;
		}

#ifdef PLATFORM_SENSOR_APDS9960
		platform_data->gesture_poffset_ur = data->poffset_ur;
#endif
		data->gesture_poffset_ur = data->poffset_ur;

		apds9960_set_enable(client, 0x00);
		apds9960_set_config2(client, 0x29);
		apds9960_set_enable(client, 0x0D);

		loop = 0;
		temp_offset = 0;
		temp_offset_negative = 127;
		while (loop++ <= 127) {
			mdelay(10);		
			data->ps_data =	i2c_smbus_read_byte_data(client, APDS9960_PDATA_REG);

			if ((data->ps_data <= APDS9960_PS_CALIBRATED_XTALK) && (data->ps_data >= APDS9960_PS_CALIBRATED_XTALK_BASELINE)) {
				printk("cal dl %d (%d) - done\n", temp_offset, data->ps_data);
				break;
			}
			else {
				if (data->ps_data > APDS9960_PS_CALIBRATED_XTALK) {
				// reduce 
					if ((temp_offset >= 0) && (temp_offset <= 127)) {
						temp_offset += 1;
					}
					else {
						temp_offset -= 1;
					}
				}		
				else if (data->ps_data < APDS9960_PS_CALIBRATED_XTALK_BASELINE) {
					// increase
					if ((temp_offset > 0) && (temp_offset <= 127)) {
						temp_offset -= 1;
					}
					else {
						temp_offset_negative += 1;	// start from 128
						temp_offset = temp_offset_negative&0xFF;
					}					
				}
			}

			apds9960_set_poffset_dl(client, temp_offset);
		}
		
		if (loop >= 128) {
			apds9960_set_wtime(client, old_wtime);
			apds9960_set_ppulse(client, old_ppulse);
			apds9960_set_control(client, old_control); 
			apds9960_set_poffset_ur(client, old_poffset_ur);
			apds9960_set_poffset_dl(client, old_poffset_dl);
			apds9960_set_config2(client, old_config2);
			apds9960_set_enable(client, 0);
			printk("loop %d -5\n", loop);
			return -5;
		}

		apds9960_set_ppulse(client, old_ppulse);
		apds9960_set_control(client, old_control); 
		apds9960_set_wtime(client, old_wtime);
		apds9960_set_config2(client, old_config2);

#ifdef PLATFORM_SENSOR_APDS9960
		platform_data->gesture_poffset_dl = data->poffset_dl;
#endif
		data->gesture_poffset_dl = data->poffset_dl;

		old_config2 = data->config2;
		old_gctrl = data->gctrl;
		old_goffset_u = data->goffset_u;
		old_goffset_d = data->goffset_d;
		old_goffset_l = data->goffset_l;
		old_goffset_r = data->goffset_r;
		old_gthr_in = data->gthr_in;
		old_gthr_out = data->gthr_out;

		apds9960_set_enable(client, 0x00);
		apds9960_set_config2(client, 0x00);

		apds9960_set_gthr_in(client, 0x00);
		apds9960_set_gthr_out(client, 0x00);

		apds9960_set_goffset_u(client, 0x00);
		apds9960_set_goffset_d(client, 0x00);
		apds9960_set_goffset_l(client, 0x00);
		apds9960_set_goffset_r(client, 0x00);

		apds9960_set_enable(client, 0x41);
		apds9960_set_gctrl(client, 0x05);

		loop = 0;
		temp_goffset_u = 0;
		temp_goffset_d = 0;
		temp_goffset_l = 0;
		temp_goffset_r = 0;
		temp_goffset_u_negative = 127;
		temp_goffset_d_negative = 127;
		temp_goffset_l_negative = 127;
		temp_goffset_r_negative = 127;

		while(loop++ <= 127) {

			mdelay(10);		
			gstatus = i2c_smbus_read_byte_data(client, APDS9960_GSTATUS_REG);

			if (gstatus < 0) {
				printk("gstatus error %d\n", gstatus);
				return -6;
			}
		
			printk("gstatus = %x (%d)\n", gstatus, loop);
			if (gstatus & APDS9960_GVALID) {

				status = i2c_smbus_read_i2c_block_data(client, APDS9960_GFIFO0_REG, 
													4, (unsigned char*)i2c_data);

				gesture_u = i2c_data[0];
				gesture_d = i2c_data[1];
				gesture_l = i2c_data[2];
				gesture_r = i2c_data[3];

				//if (!gesture_u_cal_done) 
				{
					if ((gesture_u <= APDS9960_PS_CALIBRATED_XTALK) && (gesture_u >= APDS9960_PS_CALIBRATED_XTALK_BASELINE)) {
						gesture_u_cal_done = 1;
						printk("cal u %d (%d) - done\n", gesture_u, temp_goffset_u);
					}
					else {
						gesture_u_cal_done = 0;
						if (gesture_u > APDS9960_PS_CALIBRATED_XTALK) {
							// reduce 
							if ((temp_goffset_u >= 0) && (temp_goffset_u <= 127)) {
								temp_goffset_u += 1;	
							}
							else {
								if (temp_goffset_u == 128) {
									temp_goffset_u = 0;
								}
								else {
									temp_goffset_u -= 1;
								}
							}
						}		
						else if (gesture_u < APDS9960_PS_CALIBRATED_XTALK_BASELINE) {
							// increase
							if ((temp_goffset_u > 0) && (temp_goffset_u <= 127)) {
								temp_goffset_u -= 1;
							}
							else {
								temp_goffset_u_negative += 1;	// start from 128
								temp_goffset_u = temp_goffset_u_negative&0xFF;
							}					
						}
						apds9960_set_goffset_u(client, temp_goffset_u);
					}
				}

				//if (!gesture_d_cal_done) 
				{
					if ((gesture_d <= APDS9960_PS_CALIBRATED_XTALK) && (gesture_d >= APDS9960_PS_CALIBRATED_XTALK_BASELINE)) {
						gesture_d_cal_done = 1;
						printk("cal d %d (%d) - done\n", gesture_d, temp_goffset_d);
					}
					else {
						gesture_d_cal_done = 0;
						if (gesture_d > APDS9960_PS_CALIBRATED_XTALK) {
							// reduce 
							if ((temp_goffset_d >= 0) && (temp_goffset_d <= 127)) {
								temp_goffset_d += 1;
							}
							else {
								if (temp_goffset_d == 128) {
									temp_goffset_d = 0;
								}
								else {
									temp_goffset_d -= 1;
								}
							}
						}		
						else if (gesture_d < APDS9960_PS_CALIBRATED_XTALK_BASELINE) {
							// increase
							if ((temp_goffset_d > 0) && (temp_goffset_d <= 127)) {
								temp_goffset_d -= 1;
							}
							else {
								temp_goffset_d_negative += 1;	// start from 128
								temp_goffset_d = temp_goffset_d_negative&0xFF;
							}					
						}
						apds9960_set_goffset_d(client, temp_goffset_d);
					}
				}

				//if (!gesture_l_cal_done) 
				{
					if ((gesture_l <= APDS9960_PS_CALIBRATED_XTALK) && (gesture_l >= APDS9960_PS_CALIBRATED_XTALK_BASELINE)) {
						gesture_l_cal_done = 1;
						printk("cal l %d (%d) - done\n", gesture_l, temp_goffset_l);
					}
					else {
						gesture_l_cal_done = 0;
						if (gesture_l > APDS9960_PS_CALIBRATED_XTALK) {
							// reduce 
							if ((temp_goffset_l >= 0) && (temp_goffset_l <= 127)) {
								temp_goffset_l += 1;
							}
							else {
								if (temp_goffset_l == 128) {
									temp_goffset_l = 0;
								}
								else {
									temp_goffset_l -= 1;
								}
							}
						}		
						else if (gesture_l < APDS9960_PS_CALIBRATED_XTALK_BASELINE) {
							// increase
							if ((temp_goffset_l > 0) && (temp_goffset_l <= 127)) {
								temp_goffset_l -= 1;
							}
							else {
								temp_goffset_l_negative += 1;	// start from 128
								temp_goffset_l = temp_goffset_l_negative&0xFF;
							}					
						}
						apds9960_set_goffset_l(client, temp_goffset_l);
					}
				}

				//if (!gesture_r_cal_done) 
				{
					if ((gesture_r <= APDS9960_PS_CALIBRATED_XTALK) && (gesture_r >= APDS9960_PS_CALIBRATED_XTALK_BASELINE)) {
						gesture_r_cal_done = 1;
						printk("cal r %d (%d) - done\n", gesture_r, temp_goffset_r);
					}
					else {
						gesture_r_cal_done = 0;
						if (gesture_r > APDS9960_PS_CALIBRATED_XTALK) {
							// reduce 
							if ((temp_goffset_r >= 0) && (temp_goffset_r <= 127)) {
								temp_goffset_r += 1;
							}
							else {
								if (temp_goffset_r == 128) {
									temp_goffset_r = 0;
								}
								else {
									temp_goffset_r -= 1;
								}
							}
						}		
						else if (gesture_r < APDS9960_PS_CALIBRATED_XTALK_BASELINE) {
							// increase
							if ((temp_goffset_r > 0) && (temp_goffset_r <= 127)) {
								temp_goffset_r -= 1;
							}
							else {
								temp_goffset_r_negative += 1;	// start from 128
								temp_goffset_r = temp_goffset_r_negative&0xFF;
							}					
						}

						apds9960_set_goffset_r(client, temp_goffset_r);
					}
				}

				apds9960_set_gctrl(client, 0x05);
			}

			if (gesture_u_cal_done && gesture_d_cal_done && gesture_l_cal_done && gesture_r_cal_done) break;
		}

		if (loop >= 128) {

			apds9960_set_config2(client, old_config2);

			apds9960_set_gthr_in(client, old_gthr_in);
			apds9960_set_gthr_out(client, old_gthr_out);

			apds9960_set_goffset_u(client, old_goffset_u);
			apds9960_set_goffset_d(client, old_goffset_d);
			apds9960_set_goffset_l(client, old_goffset_l);
			apds9960_set_goffset_r(client, old_goffset_r);

			apds9960_set_gctrl(client, old_gctrl);
			apds9960_set_enable(client, 0);
			return -5;
		}

		apds9960_set_config2(client, old_config2);
		apds9960_set_gthr_in(client, old_gthr_in);
		apds9960_set_gthr_out(client, old_gthr_out);
		apds9960_set_gctrl(client, old_gctrl);
		apds9960_set_enable(client, 0);

#ifdef PLATFORM_SENSOR_APDS9960
		platform_data->gesture_goffset_u = data->goffset_u;
		platform_data->gesture_goffset_d = data->goffset_d;
		platform_data->gesture_goffset_l = data->goffset_l;
		platform_data->gesture_goffset_r = data->goffset_r;
#endif
		data->gesture_goffset_u = data->goffset_u;
		data->gesture_goffset_d = data->goffset_d;
		data->gesture_goffset_l = data->goffset_l;
		data->gesture_goffset_r = data->goffset_r;

		printk("Gesture cal done %d %d %d %d\n", gesture_u, gesture_d, gesture_l, gesture_r);
	}
	
	return 0;
}


static int apds9960_ps_open(struct inode *inode, struct file *file)

{
//	printk("apds9960_ps_open\n");
	return 0; 
}

static int apds9960_ps_release(struct inode *inode, struct file *file)
{
//	printk("apds9960_ps_release\n");
	return 0;
}

static long apds9960_ps_ioctl(struct file *file, unsigned int cmd, unsigned long arg)
{
    struct apds9960_data *data;
    struct i2c_client *client;
    int enable;
    int ret = -1;

    if (arg == 0) return -1;

    if(apds9960_i2c_client == NULL) {
		printk("apds9960_ps_ioctl error: i2c driver not installed\n");
		return -EFAULT;
	}

    client = apds9960_i2c_client;   
    data = i2c_get_clientdata(apds9960_i2c_client);

    switch (cmd) {
		case APDS_IOCTL_PS_ENABLE:              

			if (copy_from_user(&enable, (void __user *)arg, sizeof(enable))) {
				printk("apds9960_ps_ioctl: copy_from_user failed\n");
				return -EFAULT;
			}

			ret = apds9960_enable_ps_sensor(client, enable);        
			if(ret < 0) {
				return ret;
			}
		break;

     	case APDS_IOCTL_PS_GET_ENABLE:
			if (copy_to_user((void __user *)arg, &data->enable_ps_sensor, sizeof(data->enable_ps_sensor))) {
				printk("apds9960_ps_ioctl: copy_to_user failed\n");
				return -EFAULT;
			}

		break;

        case APDS_IOCTL_PS_GET_PDATA:

			data->ps_data =	i2c_smbus_read_byte_data(client, APDS9960_PDATA_REG);

			if (copy_to_user((void __user *)arg, &data->ps_data, sizeof(data->ps_data))) {
				printk("apds9960_ps_ioctl: copy_to_user failed\n");
				return -EFAULT;
			}
		break;

		case APDS_IOCTL_GESTURE_ENABLE:              

			if (copy_from_user(&enable, (void __user *)arg, sizeof(enable))) {
				printk("apds9960_ps_ioctl: copy_from_user failed\n");
				return -EFAULT;
			}

			ret = apds9960_enable_gesture_sensor(client, enable);        
			if(ret < 0) {
				return ret;
			}
		break;

     	case APDS_IOCTL_GESTURE_GET_ENABLE:
			if (copy_to_user((void __user *)arg, &data->enable_gesture_sensor, sizeof(data->enable_gesture_sensor))) {
				printk("apds9960_ps_ioctl: copy_to_user failed\n");
				return -EFAULT;
			}

		break;

		default:
		break;
    }
	
    return 0;
}

static int apds9960_als_open(struct inode *inode, struct file *file)
{
//	printk("apds9960_als_open\n");
	return 0;
}

static int apds9960_als_release(struct inode *inode, struct file *file)
{
//	printk("apds9960_als_release\n");
	return 0;
}

static long apds9960_als_ioctl(struct file *file, unsigned int cmd, unsigned long arg)
{
	struct apds9960_data *data;
	struct i2c_client *client;
	int enable;
	int ret = -1;
	unsigned int delay;

	if (arg == 0) return -1;

	if(apds9960_i2c_client == NULL){
		printk("apds9960_als_ioctl error: i2c driver not installed\n");
		return -EFAULT;
	}

	client = apds9960_i2c_client;   
	data = i2c_get_clientdata(apds9960_i2c_client);

	switch (cmd) {

		case APDS_IOCTL_ALS_ENABLE:
		
			if (copy_from_user(&enable, (void __user *)arg, sizeof(enable))) {
				printk("apds9960_als_ioctl: copy_from_user failed\n");
				return -EFAULT;
			}

			ret = apds9960_enable_als_sensor(client, enable); 
			if(ret < 0){
				return ret;
			}
		break;

        case APDS_IOCTL_ALS_POLL_DELAY:

			if (data->enable_als_sensor == APDS_ENABLE_ALS_NO_INT) {	
				if (copy_from_user(&delay, (void __user *)arg, sizeof(delay))) {
					printk("apds9960_als_ioctl: copy_to_user failed\n");
					return -EFAULT;
				}
        
				ret = apds9960_set_als_poll_delay (client, delay); 
				if(ret < 0){
					return ret;
				}
			}
			else {
				printk("apds9960_als_ioctl: als is not in polling mode!\n");
				return -EFAULT;
			}
		break;

        case APDS_IOCTL_ALS_GET_ENABLE:
			if (copy_to_user((void __user *)arg, &data->enable_als_sensor, sizeof(data->enable_als_sensor))) {
				printk("apds9960_als_ioctl: copy_to_user failed\n");
				return -EFAULT;
			}
		break;

        case APDS_IOCTL_ALS_GET_CDATA:


			data->als_data = i2c_smbus_read_word_data(client, APDS9960_CDATAL_REG);

            if (copy_to_user((void __user *)arg, &data->als_data, sizeof(data->als_data))) {
				printk("apds9960_ps_ioctl: copy_to_user failed\n");
				return -EFAULT;
			}
		break;

        case APDS_IOCTL_ALS_GET_RDATA:

			data->als_data = i2c_smbus_read_word_data(client, APDS9960_RDATAL_REG);

            if (copy_to_user((void __user *)arg, &data->als_data, sizeof(data->als_data))) {
				printk("apds9960_ps_ioctl: copy_to_user failed\n");
				return -EFAULT;
			}
		break;

        case APDS_IOCTL_ALS_GET_GDATA:

			data->als_data = i2c_smbus_read_word_data(client, APDS9960_GDATAL_REG);

            if (copy_to_user((void __user *)arg, &data->als_data, sizeof(data->als_data))) {
				printk("apds9960_ps_ioctl: copy_to_user failed\n");
				return -EFAULT;
			}
		break;

        case APDS_IOCTL_ALS_GET_BDATA:

			data->als_data = i2c_smbus_read_word_data(client, APDS9960_BDATAL_REG);

            if (copy_to_user((void __user *)arg, &data->als_data, sizeof(data->als_data))) {
				printk("apds9960_ps_ioctl: copy_to_user failed\n");
				return -EFAULT;
			}
		break;

		default:
		break;
	}

	return 0;
}

/*
 * SysFS support
 */

static ssize_t apds9960_show_cdata(struct device *dev,
			struct device_attribute *attr, char *buf)
{
	//struct i2c_client *client = to_i2c_client(dev);
	struct apds9960_data *data=  dev_get_drvdata(dev);
	int cdata;

	cdata = i2c_smbus_read_word_data(data->client, APDS9960_CDATAL_REG);
	
	return sprintf(buf, "%d\n", cdata);
}

static DEVICE_ATTR(cdata, S_IRUGO,
		   apds9960_show_cdata, NULL);

static ssize_t apds9960_show_rdata(struct device *dev,
			struct device_attribute *attr, char *buf)
{
	//struct i2c_client *client = to_i2c_client(dev);
	struct apds9960_data *data=  dev_get_drvdata(dev);
	int rdata;

	rdata = i2c_smbus_read_word_data(data->client, APDS9960_RDATAL_REG);
	
	return sprintf(buf, "%d\n", rdata);
}

static DEVICE_ATTR(rdata, S_IRUGO,
		   apds9960_show_rdata, NULL);

static ssize_t apds9960_show_gdata(struct device *dev,
			struct device_attribute *attr, char *buf)
{
	//struct i2c_client *client = to_i2c_client(dev);
	struct apds9960_data *data=  dev_get_drvdata(dev);
	int gdata;

	gdata = i2c_smbus_read_word_data(data->client, APDS9960_GDATAL_REG);
	
	return sprintf(buf, "%d\n", gdata);
}

static DEVICE_ATTR(gdata, S_IRUGO,
		   apds9960_show_gdata, NULL);

static ssize_t apds9960_show_bdata(struct device *dev,
			struct device_attribute *attr, char *buf)
{
	//struct i2c_client *client = to_i2c_client(dev);
	struct apds9960_data *data=  dev_get_drvdata(dev);
	int bdata;

	bdata = i2c_smbus_read_word_data(data->client, APDS9960_BDATAL_REG);
	
	return sprintf(buf, "%d\n", bdata);
}

static DEVICE_ATTR(bdata, S_IRUGO,
		   apds9960_show_bdata, NULL);

static ssize_t apds9960_show_pdata(struct device *dev,
			struct device_attribute *attr, char *buf)
{
	//struct i2c_client *client = to_i2c_client(dev);
	struct apds9960_data *data=  dev_get_drvdata(dev);
	int pdata;

	pdata = i2c_smbus_read_byte_data(data->client, APDS9960_PDATA_REG);

	return sprintf(buf, "%d\n", pdata);
}

static DEVICE_ATTR(pdata, S_IRUGO,
		   apds9960_show_pdata, NULL);

static ssize_t apds9960_show_proximity_enable(struct device *dev,
				struct device_attribute *attr, char *buf)
{
	//struct i2c_client *client = to_i2c_client(dev);
	struct apds9960_data *data=  dev_get_drvdata(dev);
	
	return sprintf(buf, "%d\n", data->enable_ps_sensor);
}

static ssize_t apds9960_store_proximity_enable(struct device *dev,
				struct device_attribute *attr, const char *buf, size_t count)
{
	//struct i2c_client *client = to_i2c_client(dev);
	struct apds9960_data *data=  dev_get_drvdata(dev);
	unsigned long val = simple_strtoul(buf, NULL, 10);
	
	printk("%s: enable ps senosr (%ld)\n", __func__, val);
	
	if ((val != APDS_DISABLE_PS) && (val != APDS_ENABLE_PS)) {
		printk("**%s:store invalid value=%ld\n", __func__, val);
		return count;
	}

	apds9960_enable_ps_sensor(data->client, val);	
	
	return count;
}

static DEVICE_ATTR(ps_enable, S_IWUGO | S_IRUGO,
		apds9960_show_proximity_enable, apds9960_store_proximity_enable);

static ssize_t apds9960_show_light_enable(struct device *dev,
				struct device_attribute *attr, char *buf)
{
	//struct i2c_client *client = to_i2c_client(dev);
	struct apds9960_data *data=  dev_get_drvdata(dev);
	
	return sprintf(buf, "%d\n", data->enable_als_sensor);
}

static ssize_t apds9960_store_light_enable(struct device *dev,
				struct device_attribute *attr, const char *buf, size_t count)
{
	//struct i2c_client *client = to_i2c_client(dev);
	struct apds9960_data *data=  dev_get_drvdata(dev);
	unsigned long val = simple_strtoul(buf, NULL, 10);
	
	printk("%s: enable als sensor (%ld)\n", __func__, val);
	
	if ((val != APDS_DISABLE_ALS) && (val != APDS_ENABLE_ALS_WITH_INT) && (val != APDS_ENABLE_ALS_NO_INT))
	{
		printk("**%s: store invalid valeu=%ld\n", __func__, val);
		return count;
	}

	apds9960_enable_als_sensor(data->client, val); 
	printk("%s: %d :exit=============\n", __func__, __LINE__);
	
	return count;
}

static DEVICE_ATTR(als_enable, S_IWUGO | S_IRUGO,
		apds9960_show_light_enable, apds9960_store_light_enable);

static ssize_t apds9960_show_gesture_enable(struct device *dev,
				struct device_attribute *attr, char *buf)
{
	//struct i2c_client *client = to_i2c_client(dev);
	struct apds9960_data *data=  dev_get_drvdata(dev);
	
	return sprintf(buf, "%d\n", data->enable_gesture_sensor);
}

static ssize_t apds9960_store_gesture_enable(struct device *dev,
				struct device_attribute *attr, const char *buf, size_t count)
{
	//struct i2c_client *client = to_i2c_client(dev);
	struct apds9960_data *data=  dev_get_drvdata(dev);
	unsigned long val = simple_strtoul(buf, NULL, 10);
	
	printk("%s: enable gesture sensor (%ld)\n", __func__, val);
	
	if ((val != APDS_DISABLE_GESTURE) && (val != APDS_ENABLE_GESTURE))
	{
		printk("**%s: store invalid valeu=%ld\n", __func__, val);
		return count;
	}

	apds9960_enable_gesture_sensor(data->client, val); 
	
	return count;
}

static DEVICE_ATTR(gesture_enable, S_IWUGO | S_IRUGO,
		apds9960_show_gesture_enable, apds9960_store_gesture_enable);

static ssize_t apds9960_show_calibration(struct device *dev,
				struct device_attribute *attr, char *buf)
{
	//struct i2c_client *client = to_i2c_client(dev);
	struct apds9960_data *data=  dev_get_drvdata(dev);
	
	return sprintf(buf, "%d\n", data->enable_gesture_sensor);
}

static ssize_t apds9960_store_calibration(struct device *dev,
				struct device_attribute *attr, const char *buf, size_t count)
{
	//struct i2c_client *client = to_i2c_client(dev);
	struct apds9960_data *data=  dev_get_drvdata(dev);
	unsigned long val = simple_strtoul(buf, NULL, 10);
	
	printk("%s: calibration (%ld)\n", __func__, val);
	
	if ((val != APDS_ALS_CALIBRATION) && (val != APDS_PS_CALIBRATION) && (val != APDS_PS_GESTURE_CALIBRATION))
	{
		printk("**%s: store invalid valeu=%ld\n", __func__, val);
		return count;
	}

	apds9960_calibration(data->client, val); 
	
	return count;
}

static DEVICE_ATTR(calibration, S_IWUGO | S_IRUGO,
		apds9960_show_calibration, apds9960_store_calibration);

static ssize_t apds9960_light_poll_delay_store(struct device *dev,
				struct device_attribute *attr, const char *buf, size_t count)
{
	struct apds9960_data *data = dev_get_drvdata(dev);
	int64_t new_delay;
	int err;

	err = strict_strtoll(buf, 10, &new_delay);
	if (err < 0)
		return err;

	printk("ps new delay = %lldms, old delay = %ldms\n",
		    new_delay,data->als_poll_delay);
	
	apds9960_set_als_poll_delay (data->client, new_delay);
}

static ssize_t apds9960_light_poll_delay_show(struct device *dev,
				struct device_attribute *attr, char *buf)
{
	struct apds9960_data *data = dev_get_drvdata(dev);
	return sprintf(buf, "%ld\n", data->als_poll_delay);
}

static DEVICE_ATTR(ls_poll_delay, S_IWUGO | S_IRUGO,
		apds9960_light_poll_delay_show, apds9960_light_poll_delay_store);

static struct attribute *apds9960_attributes[] = {
	&dev_attr_cdata.attr,
	&dev_attr_rdata.attr,
	&dev_attr_gdata.attr,
	&dev_attr_bdata.attr,
	&dev_attr_pdata.attr,			
	
	NULL
};

static const struct attribute_group apds9960_attr_group = {
	.attrs = apds9960_attributes,
};

static struct attribute *apds9960_light_attributes[] ={
	&dev_attr_ls_poll_delay.attr,
	&dev_attr_als_enable.attr,
	NULL
};

static const struct attribute_group apds9960_light_attr_group = {
	.attrs = apds9960_light_attributes,
};

static struct attribute *apds9960_proximity_attributes[] ={
	&dev_attr_ps_enable.attr,
	&dev_attr_gesture_enable.attr,
	&dev_attr_calibration.attr,
	NULL
};

static const struct attribute_group apds9960_proximity_attr_group = {
	.attrs = apds9960_proximity_attributes,
};


static struct file_operations apds9960_ps_fops = {
	.owner = THIS_MODULE,
	.open = apds9960_ps_open,
	.release = apds9960_ps_release,
	.unlocked_ioctl = apds9960_ps_ioctl,
};

static struct miscdevice apds9960_ps_device = {
	.minor = MISC_DYNAMIC_MINOR,
	.name = "apds_ps_dev",
	.fops = &apds9960_ps_fops,
};

static struct file_operations apds9960_als_fops = {
	.owner = THIS_MODULE,
	.open = apds9960_als_open,
	.release = apds9960_als_release,
	.unlocked_ioctl = apds9960_als_ioctl,
};

static struct miscdevice apds9960_als_device = {
	.minor = MISC_DYNAMIC_MINOR,
	.name = "apds_als_dev",
	.fops = &apds9960_als_fops,
};

/*
 * Initialization function
 */

static int apds9960_init_client(struct i2c_client *client)
{
	printk("%s into#############\n",__func__);
	
	struct apds9960_data *data = i2c_get_clientdata(client);
	int err;
	int id;

	err = apds9960_set_enable(client, 0);

	if (err < 0)
		return err;
	id = i2c_smbus_read_byte_data(client, APDS9960_ID_REG);
	printk("huangle mark id = %d\n", id);
	if (id == 0xAB || id == 0x9C) {
		printk("APDS-9960\n");
	}
	else {
		printk("Not APDS-9960\n");
		return -EIO;
	}

	err = apds9960_set_atime(client, apds9960_als_atime_tb[data->als_atime_index]);	
	if (err < 0) return err;

	err = apds9960_set_wtime(client, 0xF6);	// 27ms Wait time
	if (err < 0) return err;

	err = apds9960_set_ppulse(client, data->ps_ppulse);	
	if (err < 0) return err;

	err = apds9960_set_poffset_ur(client, data->ps_poffset_ur);	
	if (err < 0) return err;

	err = apds9960_set_poffset_dl(client, data->ps_poffset_dl);	
	if (err < 0) return err;

	err = apds9960_set_config(client, 0x60);		// no long wait
	if (err < 0) return err;

	err = apds9960_set_control(client, APDS9960_PDRVIE_FOR_PS|APDS9960_PGAIN_FOR_PS|apds9960_als_again_bit_tb[data->als_again_index]);
	if (err < 0) return err;

	err = apds9960_set_pilt(client, 0);		// init threshold for proximity
	if (err < 0) return err;

	err = apds9960_set_piht(client, data->ps_threshold);
	if (err < 0) return err;

	err = apds9960_set_ailt(client, 0xFFFF);	// force first ALS interrupt to get the environment reading
	if (err < 0) return err;

	err = apds9960_set_aiht(client, 0);
	if (err < 0) return err;

	err = apds9960_set_pers(client, APDS9960_PPERS_2|APDS9960_APERS_2);	// 2 consecutive persistence
	if (err < 0) return err;

	// gesture register
	err = apds9960_set_aux(client, 0x01);
	if (err < 0) return err;

	err = apds9960_set_config2(client, 0);
	if (err < 0) return err;

	err = apds9960_set_gthr_in(client, data->gthr_in);
	if (err < 0) return err;

	err = apds9960_set_gthr_out(client, data->gthr_out);
	if (err < 0) return err;

	err = apds9960_set_gconf1(client, APDS9960_GESTURE_FIFO);
	if (err < 0) return err;

	err = apds9960_set_gconf2(client, APDS9960_GDRIVE|APDS9960_GGAIN|APDS9960_GTIME);
	if (err < 0) return err;

	err = apds9960_set_goffset_u(client, 0);
	if (err < 0) return err;

	err = apds9960_set_goffset_d(client, 0);
	if (err < 0) return err;

	err = apds9960_set_gpulse(client, (APDS9960_GPULSE-1)|APDS9960_GPULSE_LEN);
	if (err < 0) return err;

	err = apds9960_set_goffset_l(client, 0);
	if (err < 0) return err;

	err = apds9960_set_goffset_r(client, 0);
	if (err < 0) return err;

	err = apds9960_set_gconf3(client, 0);
	if (err < 0) return err;

	err = apds9960_set_gctrl(client, 0x04);
	if (err < 0) return err;

	// sensor is in disabled mode but all the configurations are preset
	printk("[%s]:%d exit#############\n",__func__,__LINE__);
	return 0;
}

/*
 * I2C init/probing/exit functions
 */

static struct i2c_driver apds9960_driver;
static int __devinit apds9960_probe(struct i2c_client *client,
				   const struct i2c_device_id *id)
{
	printk("[%s]:%d into============\n",__func__,__LINE__);
	struct i2c_adapter *adapter = to_i2c_adapter(client->dev.parent);
	struct apds9960_data *data;
#ifdef PLATFORM_SENSOR_APDS9960
	struct apds9960_platform_data* platform_data = client->dev.platform_data;
	int irq;
#endif	
	int err = 0;

	if (!i2c_check_functionality(adapter, I2C_FUNC_SMBUS_BYTE)) {
		err = -EIO;
		goto exit;
	}

	data = kzalloc(sizeof(struct apds9960_data), GFP_KERNEL);
	if (!data) {
		err = -ENOMEM;
		goto exit;
	}

	data->client = client;
	apds9960_i2c_client = client;

	i2c_set_clientdata(client, data);

	data->enable = 0;	/* default mode is standard */
	data->ps_detection = 0;	/* default to no detection */
	data->enable_als_sensor = 0;	// default to 0
	data->enable_ps_sensor = 0;	// default to 0
	data->enable_gesture_sensor = 0;	// default to 0

	data->als_poll_delay = 1000;	// default to 1000ms
	data->als_atime_index = APDS9960_ALS_RES_100MS;	// 100ms ATIME
	data->als_again_index = APDS9960_ALS_GAIN_1X;	// 1x AGAIN
	data->als_prev_lux = 0;
	data->suspended = 0;
	data->enable_suspended_value = 0;

#ifdef PLATFORM_SENSOR_APDS9960
	data->gesture_ppulse = platform_data->ppulse_for_gesture;
	data->ps_ppulse = platform_data->ppulse;
	data->ps_threshold = platform_data->ps_int_threshold;
	data->ps_hysteresis_threshold = platform_data->ps_int_hsyteresis_threshold;
	data->ps_poffset_ur = platform_data->poffset_ur;
	data->ps_poffset_dl = platform_data->poffset_dl;
	data->gthr_in = platform->gthr_in;
	data->gthr_out = platform->gthr_out;
	data->gesture_poffset_ur = platform_data->gesture_poffset_ur;
	data->gesture_poffset_dl = platform_data->gesture_poffset_dl;
	data->gesture_goffset_u = platform_data->gesture_goffset_u;
	data->gesture_goffset_d = platform_data->gesture_goffset_d;
	data->gesture_goffset_l = platform_data->gesture_goffset_l;
	data->gesture_goffset_r = platform_data->gesture_goffset_r;

	data->RGB_COE_X[0] = platform->RGB_COE_X[0];
	data->RGB_COE_X[1] = platform->RGB_COE_X[1];
	data->RGB_COE_X[2] = platform->RGB_COE_X[2];

	data->RGB_COE_Y[0] = platform->RGB_COE_Y[0];
	data->RGB_COE_Y[1] = platform->RGB_COE_Y[1];
	data->RGB_COE_Y[2] = platform->RGB_COE_Y[2];

	data->RGB_COE_Z[0] = platform->RGB_COE_Z[0];
	data->RGB_COE_Z[1] = platform->RGB_COE_Z[1];
	data->RGB_COE_Z[2] = platform->RGB_COE_Z[2];

	data->lux_GA1 = platform->lux_GA1;
	data->lux_GA2 = platform->lux_GA2;
	data->lux_GA3 = platform->lux_GA3;
	
	data->cct_GA1 = platform->cct_GA1;
	data->cct_GA2 = platform->cct_GA2;
	data->cct_GA3 = platform->cct_GA3;
#else
	data->ps_threshold = APDS9960_PS_DETECTION_THRESHOLD;
	data->ps_hysteresis_threshold = APDS9960_PS_HSYTERESIS_THRESHOLD;
	data->gesture_ppulse = (APDS9960_PPULSE_FOR_GESTURE-1)|APDS9960_PPULSE_LEN_FOR_GESTURE;
	data->ps_ppulse = (APDS9960_PPULSE_FOR_PS-1)|APDS9960_PPULSE_LEN_FOR_PS;
	data->ps_poffset_ur = 0;
	data->gthr_in = GESTURE_GTHR_IN;
	data->gthr_out = GESTURE_GTHR_OUT;
	data->gesture_poffset_dl = 0;
	data->gesture_poffset_ur = 0;
	data->gesture_goffset_u = 0;
	data->gesture_goffset_d = 0;
	data->gesture_goffset_l = 0;
	data->gesture_goffset_r = 0;

	data->RGB_COE_X[0] = RGB_COE_X[0];
	data->RGB_COE_X[1] = RGB_COE_X[1];
	data->RGB_COE_X[2] = RGB_COE_X[2];

	data->RGB_COE_Y[0] = RGB_COE_Y[0];
	data->RGB_COE_Y[1] = RGB_COE_Y[1];
	data->RGB_COE_Y[2] = RGB_COE_Y[2];

	data->RGB_COE_Z[0] = RGB_COE_Z[0];
	data->RGB_COE_Z[1] = RGB_COE_Z[1];
	data->RGB_COE_Z[2] = RGB_COE_Z[2];

	data->lux_GA1 = APDS9960_LUX_GA1;
	data->lux_GA2 = APDS9960_LUX_GA2;
	data->lux_GA3 = APDS9960_LUX_GA3;
	
	data->cct_GA1 = APDS9960_CCT_GA1;
	data->cct_GA2 = APDS9960_CCT_GA2;
	data->cct_GA3 = APDS9960_CCT_GA3;
#endif

	//mutex_init(&data->update_lock);

#ifdef PLATFORM_SENSOR_APDS9960
	err = gpio_request(platform_data->irq_num, "apds_irq");
	if (err)
    	{
        	printk("Unable to request GPIO.\n");
        	goto exit_kfree;
    	}
    
    	gpio_direction_input(platform_data->irq_num);
    	irq = APDS9960_INT;
    
    	if (irq < 0)
    	{
        	err = irq;
        	printk("Unable to request gpio irq. err=%d\n", err);
        	gpio_free(platform_data->irq_num);
        
        	goto exit_kfree;
    	}
    
    	data->irq = irq;
	if (request_irq(data->irq, apds9960_interrupt, IRQF_TRIGGER_FALLING,
		APDS9960_DRV_NAME, (void *)client)) {
		printk("%s Could not allocate APDS9960_INT !\n", __func__);
	
		goto exit_kfree;
	}

#else
	int irq_num, irq_err;
	irq_num = __gpio_to_irq(sensor_config.int1);
	printk("%s :%d ===========%d\n", __func__,irq_num,__LINE__);
	irq_err = request_irq(irq_num, apds9960_interrupt, IRQF_TRIGGER_FALLING,
			APDS9960_DRV_NAME, (void *)client);
	printk("%s :%d ===========%d\n", __func__,irq_err,__LINE__);
	if (irq_err){
//	if(!sw_gpio_irq_request(sensor_config.int1, TRIG_EDGE_NEGATIVE, (peint_handle)apds9960_interrupt, (void *)client)){
		printk("%s Could not allocate APDS9960_INT !\n", __func__);
	
		goto exit_kfree;
	}
	printk("%s : ===========%d\n", __func__,__LINE__);

#endif

#ifdef LINUX_KERNEL_2_6_X
//	set_irq_wake(client->irq, 1);
#else
	irq_set_irq_wake(client->irq, 1);
#endif

	INIT_DELAYED_WORK(&data->dwork, apds9960_work_handler);
	INIT_DELAYED_WORK(&data->als_dwork, apds9960_als_polling_work_handler); 

	printk("%s interrupt is hooked\n", __func__);
	/* Initialize the APDS9960 chip */
	err = apds9960_init_client(client);
	if (err)
		goto exit_kfree;
	/* Register to Input Device */
	data->input_dev_als = input_allocate_device();
	if (!data->input_dev_als) {
		err = -ENOMEM;
		printk("Failed to allocate input device als\n");
		goto exit_free_irq;
	}
	data->input_dev_ps = input_allocate_device();
	if (!data->input_dev_ps) {
		err = -ENOMEM;
		printk("Failed to allocate input device ps\n");
		goto exit_free_dev_als;
	}
	set_bit(EV_ABS, data->input_dev_als->evbit);
	set_bit(EV_ABS, data->input_dev_ps->evbit);

	input_set_abs_params(data->input_dev_als, ABS_LIGHT, 0, 30000, 0, 0);	// lux
	input_set_abs_params(data->input_dev_als, ABS_CCT, 0, 10000, 0, 0); // color temperature cct
	input_set_abs_params(data->input_dev_ps, ABS_DISTANCE, 0, 20, 0, 0);

	data->input_dev_als->name = "apds9960_light";
	data->input_dev_ps->name = "apds9960_proximity";

	err = input_register_device(data->input_dev_als);
	if (err) {
		err = -ENOMEM;
		printk("Unable to register input device als: %s\n",
		       data->input_dev_als->name);
		goto exit_free_dev_ps;
	}

	err = input_register_device(data->input_dev_ps);
	if (err) {
		err = -ENOMEM;
		printk("Unable to register input device ps: %s\n",
		       data->input_dev_ps->name);
		goto exit_unregister_dev_als;
	}

	/* Register sysfs hooks */
	err = sysfs_create_group(&client->dev.kobj, &apds9960_attr_group);
	if (err)
		goto exit_unregister_dev_ps;
	input_set_drvdata(data->input_dev_als,data);
	err = sysfs_create_group(&data->input_dev_als->dev.kobj, &apds9960_light_attr_group);
	if (err)
		goto exit_unregister_dev_als;
	input_set_drvdata(data->input_dev_ps,data);
	err = sysfs_create_group(&data->input_dev_ps->dev.kobj, &apds9960_proximity_attr_group);
	if (err)
		goto exit_unregister_dev_ps;
	/* Register for sensor ioctl */
    	err = misc_register(&apds9960_ps_device);
	if (err) {
		printk("Unalbe to register ps ioctl: %d", err);
		goto exit_remove_sysfs_group;
	}

    err = misc_register(&apds9960_als_device);
	if (err) {
		printk("Unalbe to register als ioctl: %d", err);
		goto exit_unregister_ps_ioctl;
	}

	printk("%s support ver. %s enabled\n", __func__, DRIVER_VERSION);

	return 0;

exit_unregister_ps_ioctl:
	misc_deregister(&apds9960_ps_device);
exit_remove_sysfs_group:
	sysfs_remove_group(&client->dev.kobj, &apds9960_attr_group);
exit_unregister_dev_ps:
	input_unregister_device(data->input_dev_ps);	
exit_unregister_dev_als:
	input_unregister_device(data->input_dev_als);
exit_free_dev_ps:
exit_free_dev_als:
exit_free_irq:
	free_irq(APDS9960_INT, client);	
exit_kfree:
	kfree(data);
exit:
	return err;
}

static int __devexit apds9960_remove(struct i2c_client *client)
{
	struct apds9960_data *data = i2c_get_clientdata(client);

	__cancel_delayed_work(&data->dwork);
	__cancel_delayed_work(&data->als_dwork);

	/* Power down the device */
	apds9960_set_enable(client, 0);

	misc_deregister(&apds9960_als_device);
	misc_deregister(&apds9960_ps_device);	

	sysfs_remove_group(&client->dev.kobj, &apds9960_attr_group);

	input_unregister_device(data->input_dev_ps);
	input_unregister_device(data->input_dev_als);
	
	free_irq(APDS9960_INT, client);

	kfree(data);
	printk("%s remove ###########\n",__func__);

	return 0;
}

//#define CONFIG_PM

#ifdef CONFIG_PM

static int apds9960_suspend(struct i2c_client *client, pm_message_t mesg)
{
	struct apds9960_data *data = i2c_get_clientdata(client);

	printk("apds9960_suspend\n");
	
	// Do nothing as p-sensor is in active
	if(data->enable_ps_sensor)
		return 0;

	data->suspended = 1;
	data->enable_suspended_value = data->enable;
	
	apds9960_set_enable(client, 0);
	apds9960_clear_interrupt(client, CMD_CLR_ALL_INT);
	
	__cancel_delayed_work(&data->als_dwork);
	flush_delayed_work(&data->als_dwork);		

	__cancel_delayed_work(&data->dwork);
	flush_delayed_work(&data->dwork);

	flush_workqueue(apds_workqueue);

	disable_irq(data->irq);
	
#ifdef LINUX_KERNEL_2_6_X
	set_irq_wake(client->irq, 0);
#else
	irq_set_irq_wake(client->irq, 0);
#endif

	if(NULL != apds_workqueue){
		destroy_workqueue(apds_workqueue);
		printk(KERN_INFO "%s, Destroy workqueue\n",__func__);
		apds_workqueue = NULL;
	}

	return 0;
}

static int apds9960_resume(struct i2c_client *client)
{
	struct apds9960_data *data = i2c_get_clientdata(client);	
	int err = 0;

	// Do nothing as it was not suspended
	printk("apds9960_resume (enable=%d)\n", data->enable_suspended_value);

	if(apds_workqueue == NULL) {
		apds_workqueue = create_workqueue("proximity_als");
		if(NULL == apds_workqueue)
			return -ENOMEM;
	}
	
	if(!data->suspended)
		return 0;	/* if previously not suspended, leave it */

	enable_irq(data->irq);
		 
	mdelay(50);

	err = apds9960_set_enable(client, data->enable_suspended_value);
	
	if(err < 0){
		printk(KERN_INFO "%s, enable set Fail\n",__func__);
		return 0;
	}

	data->suspended = 0;

#ifdef LINUX_KERNEL_2_6_X
	set_irq_wake(client->irq, 1);
#else
	irq_set_irq_wake(client->irq, 1);
#endif

	apds9960_clear_interrupt(client, CMD_CLR_ALL_INT);	/* clear pending interrupt */
	return 0;
}

#else

#define apds9960_suspend	NULL
#define apds9960_resume		NULL

#endif /* CONFIG_PM */

#if 0	/*********************************************/
#ifdef CONFIG_PM

static int apds9960_suspend(struct i2c_client *client, pm_message_t mesg)
{
	struct apds990x_data *data = i2c_get_clientdata(client);
	int enable = 0;

	disable_irq(data->pdata->irq);
	if (data->enable_als_sensor)
		cancel_delayed_work_sync(&data->als_dwork);

	if (data->enable_ps_sensor) {
		enable = 0x27;
		enable_irq_wake(data->pdata->irq);
	}

	if (enable != data->enable)
		apds990x_set_enable(client, enable);

	return 0;
}

static int apds990x_resume(struct i2c_client *client)
{
	struct apds990x_data *data = i2c_get_clientdata(client);
	int enable = 0;

	if (data->enable_als_sensor)
		enable |= 0x03;

	if (data->enable_ps_sensor) {
		enable |= 0x27;
		disable_irq_wake(data->pdata->irq);
	}

	if (enable != 0x27)
		apds990x_set_enable(client, enable);

	if (data->enable_als_sensor)
		schedule_delayed_work(&data->als_dwork, msecs_to_jiffies(data->als_poll_delay));

	enable_irq(data->pdata->irq);

	return 0;
}

#else

#define apds990x_suspend	NULL
#define apds990x_resume		NULL

#endif /* CONFIG_PM */
#endif /****************************/
static int apds9960_detect(struct i2c_client *client, struct i2c_board_info *info)
{
	printk("[%s]:%d into============\n",__func__,__LINE__);
	struct i2c_adapter *adapter = client->adapter;
	int ret=0;
	if (!i2c_check_functionality(adapter, I2C_FUNC_SMBUS_BYTE_DATA))
            return -ENODEV;
	
	if (sensor_config.twi_id == adapter->nr) {
		client->addr = normal_i2c[0];
		strlcpy(info->type, APDS9960_DRV_NAME, I2C_NAME_SIZE);
		return 0;
	}
	return -ENODEV;
}



/**
 * ls_fetch_sysconfig_para - get config info from sysconfig.fex file.
 * return value:  
 *                    = 0; success;
 *                    < 0; err
 */
static int apds9960_fetch_sysconfig_para(void)
{
	int ret = -1;
	int device_used = -1;
	int twi_id = 0;
	int sensor_int = 0;
	char* ldo_str = NULL;
	script_item_u	val;
	script_item_value_type_e  type;	
		
	printk("========%s===================\n", __func__);
	
	type = script_get_item("ls_para", "ls_used", &val);
 
	if (SCIRPT_ITEM_VALUE_TYPE_INT != type) {
		printk("%s: type err  device_used = %d. \n", __func__, val.val);
		goto script_get_err;
	}
	device_used = val.val;
	
	if (1 == device_used) {
		type = script_get_item("ls_para", "ls_twi_id", &val);	
		if (SCIRPT_ITEM_VALUE_TYPE_INT != type) {
			printk("%s: type err twi_id = %d. \n", __func__, val.val);
			goto script_get_err;
		}
		twi_id = val.val;
		
		printk("%s: twi_id is %d. \n", __func__, twi_id);

		type = script_get_item("ls_para", "ls_int", &val);	
		if (SCIRPT_ITEM_VALUE_TYPE_PIO != type) {
			printk("%s: type err twi int1 = %d. \n", __func__, val.gpio.gpio);
			goto script_get_err;
		}

		sensor_int = val.gpio.gpio; //gpio num
		if(0 != gpio_request(sensor_int, NULL))
		{
			printk("gpio sensor_int request failed.\n");
			ret = -1;
			goto script_get_err;
		}
		gpio_direction_input(sensor_int);
		printk("%s: INT gpio is %d. \n", __func__, sensor_int);
		
		ret = 0;
		type = script_get_item("ls_para", "ls_ldo", &val);
		if (SCIRPT_ITEM_VALUE_TYPE_STR != type)
			printk("%s: no ldo for light distance sensor, ignore it\n", __func__);
		else
			ldo_str = val.str;
	} else {
		printk("%s: ls_unused. \n",  __func__);
		ret = -1;
	}
	sensor_config.twi_id = twi_id;
	sensor_config.int1 = sensor_int;
	sensor_config.ldo = ldo_str;

	return ret;

script_get_err:
	pr_notice("=========script_get_err============\n");
	return ret;

}

static const struct i2c_device_id apds9960_id[] = {
	{ "apds9960", 0 },
	{ }
};
MODULE_DEVICE_TABLE(i2c, apds9960_id);

static struct i2c_driver apds9960_driver = {
	.class = I2C_CLASS_HWMON,
	.driver = {
		.name	= APDS9960_DRV_NAME,
		.owner	= THIS_MODULE,
	},
	.suspend = apds9960_suspend,
	.resume	= apds9960_resume,
	.probe	= apds9960_probe,
	.remove	= __devexit_p(apds9960_remove),
	.id_table = apds9960_id,
	.address_list	= normal_i2c,
};

static int __init apds9960_init(void)
{
	printk("[%s]:%d into============\n",__func__,__LINE__);
	apds_workqueue = create_workqueue("proximity_als");
	
	if (!apds_workqueue)
		return -ENOMEM;

	if (apds9960_fetch_sysconfig_para()) {
		printk("%s: err.\n", __func__);
		return -1;
	}
	apds9960_driver.detect = apds9960_detect;
	return i2c_add_driver(&apds9960_driver);
}

static void __exit apds9960_exit(void)
{
	if (apds_workqueue)
		destroy_workqueue(apds_workqueue);

	apds_workqueue = NULL;

	i2c_del_driver(&apds9960_driver);
	printk("###### apds9960_exit ######\n");
}

MODULE_AUTHOR("Lee Kai Koon <kai-koon.lee@avagotech.com>");
MODULE_DESCRIPTION("APDS9960 gesture + RGB + ambient light + proximity sensor driver");
MODULE_LICENSE("GPL");
MODULE_VERSION(DRIVER_VERSION);

module_init(apds9960_init);
module_exit(apds9960_exit);

