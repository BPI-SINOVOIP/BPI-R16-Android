//#define LOG_NDEBUG 0
#define LOG_TAG "HDMIListerner"
#include <utils/Log.h>


#include <sys/types.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <sys/resource.h>
#include <fcntl.h>
#include <unistd.h>
#include <pthread.h>

#include <cutils/properties.h>
#include <system/graphics.h>
#include <utils/Errors.h>
#include <utils/Mutex.h>
#include <include/HDMIListerner.h>

namespace android {

HDMIListerner::HDMIListerner()
	:mDone(false),
	 mHDMIPlugged(false),
	 mRunning(false),
	 mMiracastConnected(false),
	 mCookie(NULL) {
	ALOGV("HDMIListerner");
}

HDMIListerner::~HDMIListerner() {
	ALOGV("~HDMIListerner");
}

void HDMIListerner::start() {

    pthread_attr_t attr;
    pthread_attr_init(&attr);
    pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);
    pthread_create(&mThread, &attr, ThreadWrapper, this);
    pthread_attr_destroy(&attr);
    mRunning = true;
}

void HDMIListerner::stop() {
	ALOGV("stop, mStarted %d", mRunning);
	if(!mRunning) {
		/**Sometimes, thread may be not started, or
		 * stop called too many times.
		 * TODO:add lock for mStarted.
		 */
		return;
	}

	mDone = true;
    void *dummy;
    pthread_join(mThread, &dummy);
    status_t err = (status_t) dummy;
    mRunning = false;
}

void HDMIListerner::threadFunc() {
    int32_t sw_fd;
//    bool hdmiPlugged;

//	int8_t val;

    setpriority(PRIO_PROCESS, 0, HAL_PRIORITY_URGENT_DISPLAY);
    sw_fd = open("/sys/class/switch/hdmi/state", O_RDONLY);

    if(sw_fd < 0) {
    	ALOGE("failed to open hdmi state file.");
//    	return;
    }
	while(!mDone) {
//		hdmiPlugged = false;
//		if (read(sw_fd, &val, 1) == 1) {
//			if ('1' == val) {
//				hdmiPlugged = true;
//			}
//		}
//		if(hdmiPlugged != mHDMIPlugged) {
//			ALOGI("hdmi state changed from %d to %d",
//					mHDMIPlugged, hdmiPlugged);
//			//notify listener.
//			mHDMIPlugged = !mHDMIPlugged;
//			sendState();
//		}
//
//		lseek(sw_fd, 0, SEEK_SET);
//		usleep(1000 * 1000);

		if(isHDMIStateChanged(sw_fd) || isMiracastStateChanged()) {
			//status changed, notify listener.
			sendState(mHDMIPlugged || mMiracastConnected);
		}
		usleep(1000*1000);
	}
    if(sw_fd >=0 ){
    	close(sw_fd);
    }
}

bool HDMIListerner::isHDMIStateChanged(int32_t fd) {
	if(fd < 0) {
        return false;
	}

    bool hdmiPlugged = false;
    int8_t val;
    if (read(fd, &val, 1) == 1) {
         if ('1' == val) {
             hdmiPlugged = true;
          }
          lseek(fd, 0, SEEK_SET);
          usleep(1000 * 1000);
    	}
    lseek(fd, 0, SEEK_SET);
    if(hdmiPlugged != mHDMIPlugged) {
      ALOGI("hdmi state changed from %d to %d",mHDMIPlugged, hdmiPlugged);
        mHDMIPlugged = !mHDMIPlugged;
        return true;
    }
    return false;
}

bool HDMIListerner::isMiracastStateChanged() {
       bool miracastConnected = false;

       char value[PROPERTY_VALUE_MAX];
       if (property_get("wfd.enable", value, "0")
                       && (!strcmp(value, "1") || !strcasecmp(value, "true"))) {
               miracastConnected = true;
       }
       if(miracastConnected != mMiracastConnected) {
               ALOGI("miracast state changed from %d to %d",
                               mMiracastConnected, miracastConnected);
               mMiracastConnected = !mMiracastConnected;
               return true;
        }
       return false;
 }

void HDMIListerner::sendState(bool state) {
	ALOGV("sendState");
    Mutex::Autolock autoLock(mNotifyLock);
    if (mNotify) {
//    	mNotify(mCookie, mHDMIPlugged);
    	mNotify(mCookie, state);
    }
}

void HDMIListerner::setNotifyCallback(
        void* cookie, hdmi_state_callback_f notifyFunc) {
	ALOGV("setNotifyCallback");
    Mutex::Autolock autoLock(mNotifyLock);
    mCookie = cookie;
    mNotify = notifyFunc;
}

//static
void *HDMIListerner::ThreadWrapper(void *me)
{
    ALOGV("ThreadWrapper: %p", me);
    HDMIListerner *listener = static_cast<HDMIListerner *>(me);
    listener->threadFunc();
    return NULL;
}

}//namespace android
