#!/bin/sh

source send_cmd_pipe.sh
source script_parser.sh

ROOT_DEVICE=/dev/nandd
for parm in $(cat /proc/cmdline); do
    case $parm in
        root=*)
            ROOT_DEVICE=`echo $parm | awk -F\= '{print $2}'`
            ;;
    esac
done

# install nand driver if we boot from sdmmc
nand_activated=`script_fetch "nand" "activated"`
echo "nand activated #$nand_activated"
if [ $nand_activated -eq 1 ]; then
    case $ROOT_DEVICE in
        /dev/mmc*)
      
        nand_module_path=`script_fetch "nand" "module_path"`
        if [ -n "$nand_module_path" ]; then
            insmod "$nand_module_path"
       fi
            ;;
    esac

fi


case $ROOT_DEVICE in
    /dev/nand*)
        echo "nand boot"
        mount /dev/nanda /boot
        SEND_CMD_PIPE_OK_EX $3
        exit 1
        ;;
    /dev/mmc*)
        echo "mmc boot"
        mount /dev/mmcblk0p2 /boot
        ;;
    *)
        echo "default boot type"
        mount /dev/nanda /boot
        ;;
esac

#only in card boot mode,it will run here 
echo "nand test ioctl start"
nandrw "/dev/nanda" 

if [ $? -ne 0 ]; then
    SEND_CMD_PIPE_FAIL_EX $3 "nand ioctl failed"
    exit 1
else
	echo "nand ok"
	SEND_CMD_PIPE_OK_EX $3 
fi
