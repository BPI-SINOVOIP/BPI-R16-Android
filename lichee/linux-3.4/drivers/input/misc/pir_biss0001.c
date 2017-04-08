/*
*	1.	pir -->vout--> cpu(input)
*	sb3122a vout io input to cpu; 
*		default：0v, 
*   	it's changed to 3.0v  when there have People.
*		it's changed to 0v after people quit and delay 2s.
*
*/

#include <linux/kernel.h>
#include <linux/platform_device.h>
#include <linux/err.h>
#include <linux/gpio.h>
#include <linux/module.h>

#include <linux/ktime.h>
#include <linux/miscdevice.h>

#include <mach/hardware.h>
#include <mach/sys_config.h>

#include <linux/delay.h>

#include <linux/input.h>
#include <linux/interrupt.h>
#include <linux/proc_fs.h>
#include <linux/fs.h>
#include <linux/irq.h>

//copy_to_user copy_from_user is defined 的相关头文件
#include <asm/uaccess.h>

struct pir_io_ctl{
	
	struct gpio_config pir_vout;
	
//	struct work_struct pir_work;
	unsigned int irq_hdl;
	spinlock_t lock;
};
static struct pir_io_ctl pir_ctrl1,pir_ctrl2,pir_ctrl3,pir_ctrlled;

static struct delayed_work pir_ctl_work;
static int default_time = 4000;
static int vout1_level = 0,vout2_level = 0,vout3_level = 0;
static int people_state = 0;
static int slag = 0;

static struct proc_dir_entry *proc_entry;
char k_buf[8];
char *after;
int proc_write = 0;

static struct input_dev *pir_input_dev;
//240
#define PIR_TO 0xf0
//242
#define PIR_GO 0xf2

static int pir_state_proc_write(struct file *filp, const char __user *buff, unsigned long count, void *data);
static int pir_state_proc_read( char *page, char **start, off_t off, int count, int *eof, void *data );

static int pir_state_proc_write(struct file *filp, const char __user *buff, unsigned long count, void *data)
{
	int len;
    if (count == 8)  
		len = 7;
	else  
		len = count; 

	copy_from_user(k_buf, buff, len);
	k_buf[len] = '\0';
	proc_write = simple_strtoul(k_buf, &after, 10);
	printk("hero come here is proc_write = %d!\n", proc_write);
	switch(proc_write)
	{
		case 1:	
			__gpio_set_value(pir_ctrlled.pir_vout.gpio, 1);
			mdelay(10);
			break;
		case 2:	
			__gpio_set_value(pir_ctrlled.pir_vout.gpio, 0);
			mdelay(10);
			break;
		case 3:	
			__gpio_set_value(pir_ctrlled.pir_vout.gpio, 1);
			mdelay(10);
			break;
		default:	
			__gpio_set_value(pir_ctrlled.pir_vout.gpio, 0);
			mdelay(10);
			break;	
	}
	
	return len;
}

static int pir_state_proc_read( char *page, char **start, off_t off, int count, int *eof, void *data )
{
	int len;
	
	len = sprintf(page, "%u",k_buf);
	return len;
}

static int proc_pir_state_init(void)
{
	proc_entry = create_proc_entry("pir_state", 0666, NULL);
    if (proc_entry == NULL)
    {
        printk("Couldn't create proc entry!\n");
        return -1;
    }
    else
    {
        printk("Create proc entry success!\n");
        proc_entry->write_proc = pir_state_proc_write;
        proc_entry->read_proc = pir_state_proc_read;
    }
	strcpy(k_buf, "0"); 
    return 0;
}

static irqreturn_t __pir_func_worker1(int errno, void *dev_id)
{
//	printk("hero %s=====\n",__func__);
	disable_irq_nosync(pir_ctrl1.irq_hdl);
	mdelay(10);
	vout1_level = __gpio_get_value(pir_ctrl1.pir_vout.gpio);
	if(vout1_level == 1)
	{
//		strcpy(k_buf, "1");
		printk("hero %d :vout1_level=%d=====\n",__LINE__,vout1_level);
		input_report_key(pir_input_dev, PIR_TO, 1);
		input_sync(pir_input_dev);
		mdelay(10);
		input_report_key(pir_input_dev, PIR_TO, 0);
		input_sync(pir_input_dev);
		mdelay(10);
	}else{
//		strcpy(k_buf, "0");	
		printk("hero %d :vout1_level=%d=====\n",__LINE__,vout1_level);
		input_report_key(pir_input_dev, PIR_GO, 1);
		input_sync(pir_input_dev);
		mdelay(10);
		input_report_key(pir_input_dev, PIR_GO, 0);
		input_sync(pir_input_dev);
		mdelay(10);
	}
	enable_irq(pir_ctrl1.irq_hdl);
//	schedule_delayed_work(&pir_ctl_work, msecs_to_jiffies(200));
	return IRQ_HANDLED;
}

static irqreturn_t __pir_func_worker2(int errno, void *dev_id)
{
	printk("hero %s=====\n",__func__);
	disable_irq_nosync(pir_ctrl2.irq_hdl);
	vout2_level = __gpio_get_value(pir_ctrl2.pir_vout.gpio);
	if(vout2_level == 1)
	{
		__gpio_set_value(pir_ctrlled.pir_vout.gpio, 1);
		mdelay(10);
	}else{
		__gpio_set_value(pir_ctrlled.pir_vout.gpio, 0);
		mdelay(10);
	}
	enable_irq(pir_ctrl2.irq_hdl);
//	schedule_delayed_work(&pir_ctl_work, msecs_to_jiffies(200));
	return IRQ_HANDLED;
}

static irqreturn_t __pir_func_worker3(int errno, void *dev_id)
{
	printk("hero %s=====\n",__func__);
	disable_irq_nosync(pir_ctrl3.irq_hdl);
	vout3_level = __gpio_get_value(pir_ctrl3.pir_vout.gpio);
	
	return IRQ_HANDLED;
}

static int __init pir_biss0001_init(void) {
	int err,pir_used;
    script_item_u	val;
	script_item_value_type_e  type;

	printk("[pir_sb3122a_init]=====into\n");

    type = script_get_item("pir_para", "pir_used", &val);
    if (SCIRPT_ITEM_VALUE_TYPE_INT != type) {
		printk(KERN_ERR "%s script_parser_fetch \"rst_gpio_para\" rst_gpio_used = %d\n",
				__FUNCTION__, val.val);
        err = -1;
		goto exit1;
	}
    pir_used = val.val;
    if(!pir_used) {
		printk("%s gpio is not used in config\n", __FUNCTION__);
		goto exit1;
	}
// ------------------------------------------huyanhu PL04:pir_vout1
    type = script_get_item("pir_para", "pir_vout1", &val);
	if(SCIRPT_ITEM_VALUE_TYPE_PIO != type) {
		printk("gpio ctrl io type err!");
        err = -1;
		goto exit;
	}
	pir_ctrl1.pir_vout = val.gpio;
	if(0 != gpio_request(pir_ctrl1.pir_vout.gpio, NULL)) {
		printk("ERROR: pir_ctl pir_vout Gpio_request is failed\n");
		err = -1;
		goto exit1;
	}
    gpio_direction_input(pir_ctrl1.pir_vout.gpio);
	
	pir_ctrl1.irq_hdl = gpio_to_irq(pir_ctrl1.pir_vout.gpio);
	err = request_irq(pir_ctrl1.irq_hdl, __pir_func_worker1, IRQF_TRIGGER_FALLING|IRQF_TRIGGER_RISING, "pir_ctrl1", &pir_ctrl1);
	if(err){
		free_irq(pir_ctrl1.irq_hdl, &pir_ctrl1);
		err = -1;
		goto exit1;
	}
// ------------------------------------------huyanhu PL03:pir_vout2
    type = script_get_item("pir_para", "pir_vout2", &val);
	if(SCIRPT_ITEM_VALUE_TYPE_PIO != type) {
		printk("gpio ctrl io type err!");
        err = -1;
		goto exit1;
	}
	pir_ctrl2.pir_vout = val.gpio;
	if(0 != gpio_request(pir_ctrl2.pir_vout.gpio, NULL)) {
		printk("ERROR: pir_ctl pir_vout Gpio_request is failed\n");
		err = -1;
		goto exit1;
	}
    gpio_direction_input(pir_ctrl2.pir_vout.gpio);
	
	pir_ctrl2.irq_hdl = gpio_to_irq(pir_ctrl2.pir_vout.gpio);
	err = request_irq(pir_ctrl2.irq_hdl, __pir_func_worker2, IRQF_TRIGGER_FALLING|IRQF_TRIGGER_RISING, "pir_ctrl2", &pir_ctrl2);
	if(err){
		free_irq(pir_ctrl2.irq_hdl, &pir_ctrl2);
		err = -1;
		goto exit1;
	}
// ------------------------------------------huyanhu PL02:pir_vout2
    type = script_get_item("pir_para", "pir_vout3", &val);
	if(SCIRPT_ITEM_VALUE_TYPE_PIO != type) {
		printk("gpio ctrl io type err!");
        err = -1;
		goto exit;
	}
	pir_ctrl3.pir_vout = val.gpio;
	if(0 != gpio_request(pir_ctrl3.pir_vout.gpio, NULL)) {
		printk("ERROR: pir_ctl pir_vout Gpio_request is failed\n");
		err = -1;
		goto exit1;
	}
    gpio_direction_input(pir_ctrl3.pir_vout.gpio);
	
	pir_ctrl3.irq_hdl = gpio_to_irq(pir_ctrl3.pir_vout.gpio);
	err = request_irq(pir_ctrl3.irq_hdl, __pir_func_worker3, IRQF_TRIGGER_FALLING|IRQF_TRIGGER_RISING, "pir_ctrl3", &pir_ctrl3);
	if(err){
		free_irq(pir_ctrl3.irq_hdl, &pir_ctrl3);
		err = -1;
		goto exit1;
	}
// ------------------------------------------huyanhu PD04:pir_leden	
    type = script_get_item("pir_para", "pir_leden", &val);
	if(SCIRPT_ITEM_VALUE_TYPE_PIO != type) {
		printk("gpio ctrl io type err!");
        err = -1;
		goto exit1;
	}
	pir_ctrlled.pir_vout = val.gpio;
	if(0 != gpio_request(pir_ctrlled.pir_vout.gpio, NULL)) {
		printk("ERROR: pir_ctl pir_vout Gpio_request is failed\n");
		err = -1;
		goto exit1;
	}
    gpio_direction_output(pir_ctrlled.pir_vout.gpio, 0);
	
/*	pir_ctrlled.irq_hdl = gpio_to_irq(pir_ctrlled.pir_vout.gpio);
	err = request_irq(pir_ctrlled.irq_hdl, __pir_func_worker, IRQF_TRIGGER_FALLING|IRQF_TRIGGER_RISING, "pir_ctrlled", &pir_ctrlled);
	if(err){
		free_irq(pir_ctrlled.irq_hdl, &pir_ctrlled);
		err = -1;
		goto exit1;
	}*/


#if 0	
	proc_pir_state_init();
#else
	pir_input_dev = input_allocate_device();
	if(!pir_input_dev)
	{
		printk("[pir_input_dev] input allocate device failed.\n");
		err = -ENOMEM;
		return err;
	}
	
	pir_input_dev->name		= "pir_input";
	pir_input_dev->phys		= "pirinput/input0";
	pir_input_dev->id.bustype = BUS_HOST;
	pir_input_dev->id.vendor	= 0x0001;
	pir_input_dev->id.product = 0x0001;
	pir_input_dev->id.version = 0x0100;
	
	pir_input_dev->evbit[0] = BIT_MASK(EV_KEY)|BIT_MASK(EV_REP);
	set_bit(PIR_TO, pir_input_dev->keybit);
	set_bit(PIR_GO, pir_input_dev->keybit);
	err = input_register_device(pir_input_dev);
	if(err)
	{
		printk("[pir_input_dev] input register device failed.\n");
		input_free_device(pir_input_dev);	
		return -1;
	}
#endif
	
//	INIT_DELAYED_WORK(&pir_ctl_work, __pir_func_worker);
//	schedule_delayed_work(&pir_ctl_work, msecs_to_jiffies(default_time));
//	INIT_WORK(a33_ctl.mcu_work, mcu_read);
//	schedule_work(&mcu_data->mcu_work);
	
exit1:
	if(pir_ctrl1.pir_vout.gpio != 0) gpio_free(pir_ctrl1.pir_vout.gpio);
	if(pir_ctrl2.pir_vout.gpio != 0) gpio_free(pir_ctrl2.pir_vout.gpio);
	if(pir_ctrl3.pir_vout.gpio != 0) gpio_free(pir_ctrl3.pir_vout.gpio);
	if(pir_ctrlled.pir_vout.gpio != 0) gpio_free(pir_ctrlled.pir_vout.gpio);
exit:
	return err;
}

static void __exit pir_biss0001_exit(void) {
	printk("[%s]:%d exit==========\n",__func__,__LINE__);

//	cancel_delayed_work_sync(&pir_ctl_work);
	free_irq(pir_ctrl1.irq_hdl, &pir_ctrl1);
	free_irq(pir_ctrl2.irq_hdl, &pir_ctrl2);
	free_irq(pir_ctrl3.irq_hdl, &pir_ctrl3);
	
	input_unregister_device(pir_input_dev);
	input_free_device(pir_input_dev);
	
//	gpio_free(pir_ctl.pir_vout.gpio);
	if(pir_ctrl1.pir_vout.gpio != 0) gpio_free(pir_ctrl1.pir_vout.gpio);
	if(pir_ctrl2.pir_vout.gpio != 0) gpio_free(pir_ctrl2.pir_vout.gpio);
	if(pir_ctrl3.pir_vout.gpio != 0) gpio_free(pir_ctrl3.pir_vout.gpio);
	if(pir_ctrlled.pir_vout.gpio != 0) gpio_free(pir_ctrlled.pir_vout.gpio);
}

module_init(pir_biss0001_init);
module_exit(pir_biss0001_exit);

MODULE_DESCRIPTION("a simple pir driver");
MODULE_AUTHOR("sc");
MODULE_LICENSE("GPL");
