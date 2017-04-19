# BPI-M2M Android 4.4 Source code
----------
###1 Build Android BSP
 $ cd lichee
 
   $ ./build.sh config       

Welcome to mkscript setup progress
All available chips:
   1. sun8iw5p1

Choice: 1


All available platforms:
   1. android
   2. dragonboard
   3. linux
   4. tina
 
Choice: 1


All available kernel:
   1. linux-3.4
 
Choice: 1


All available boards:
   1. bpi-m2m

Choice: 1

   $ ./build.sh 

***********

###2 Build Android 
   $cd ../android

   $source build/envsetup.sh
   
   $lunch    //(bpi_m2m-eng)
   
   $extract-bsp
   
   $make -j8
   
   $pack
   





