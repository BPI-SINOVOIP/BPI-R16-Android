
#ifndef LAYER_CONTROL
#define LAYER_CONTROL

#include "player_i.h"
#include "videoDecComponent.h"
#include "vdecoder.h"

typedef void* LayerCtrl;

const int MESSAGE_ID_LAYER_RETURN_BUFFER = 0x31;

const int LAYER_RESULT_USE_OUTSIDE_BUFFER = 0x2;

LayerCtrl* LayerInit(void* pNativeWindow);

void LayerRelease(LayerCtrl* l, int bKeepPictureOnScreen);

int LayerSetExpectPixelFormat(LayerCtrl* l, enum EPIXELFORMAT ePixelFormat);

enum EPIXELFORMAT LayerGetPixelFormat(LayerCtrl* l);

int LayerSetPictureSize(LayerCtrl* l, int nWidth, int nHeight);

int LayerSetDisplayRegion(LayerCtrl* l, int nLeftOff, int nTopOff, int nDisplayWidth, int nDisplayHeight);

int LayerSetPicture3DMode(LayerCtrl* l, enum EPICTURE3DMODE ePicture3DMode);

enum EPICTURE3DMODE LayerGetPicture3DMode(LayerCtrl* l);

int LayerSetDisplay3DMode(LayerCtrl* l, enum EDISPLAY3DMODE eDisplay3DMode);

enum EDISPLAY3DMODE LayerGetDisplay3DMode(LayerCtrl* l);

int LayerGetRotationAngle(LayerCtrl* l);

int LayerSetCallback(LayerCtrl* l, PlayerCallback callback, void* pUserData);

int LayerDequeueBuffer(LayerCtrl* l, VideoPicture** ppBuf);

int LayerSetBufferTimeStamp(LayerCtrl* l, int64_t nPtsAbs);

int LayerQueueBuffer(LayerCtrl* l, VideoPicture* pBuf, int bValid = 1);

int LayerDequeue3DBuffer(LayerCtrl* l, VideoPicture** ppBuf0, VideoPicture** ppBuf1);

int LayerQueue3DBuffer(LayerCtrl* l, VideoPicture* pBuf0, VideoPicture* pBuf1, int bValid = 1);

int LayerCtrlHideVideo(LayerCtrl* l);

int LayerCtrlShowVideo(LayerCtrl* l);

int LayerCtrlIsVideoShow(LayerCtrl* l);

int LayerCtrlHoldLastPicture(LayerCtrl* l, int bHold);

int LayerSetRenderToHardwareFlag(LayerCtrl* l,int bFlag);

int LayerSetDeinterlaceFlag(LayerCtrl* l,int bFlag);

#endif

