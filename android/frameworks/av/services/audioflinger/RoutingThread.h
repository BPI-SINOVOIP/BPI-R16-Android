/*
**
** Copyright 2007, The Android Open Source Project
** Copyright (C) 2013 Broadcom Corporation
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#ifndef ROUTINGTHREAD_H
#define ROUTINGTHREAD_H

#include <stdint.h>
#include <sys/types.h>
#include <limits.h>
#include <utils/Atomic.h>
#include <utils/Errors.h>
#include <utils/threads.h>
#include <utils/SortedVector.h>
#include <utils/TypeHelpers.h>
#include <utils/Vector.h>

#include <media/AudioBufferProvider.h>
#include <media/ExtendedAudioBufferProvider.h>

#include <private/media/AudioTrackShared.h>


#ifndef INCLUDING_FROM_AUDIOFLINGER_H
    #error This header file should only be included from AudioFlinger.h
#endif


// unique handle used for input router
#define INPUT_ROUTER_HANDLE 0xb0555eee

class RoutingThread : public Thread {

    public:

    RoutingThread (AudioFlinger *pAudioFlinger,
                       audio_stream_t *inStream,
                       audio_io_handle_t output);

    virtual ~RoutingThread();

        enum {
            NO_MORE_BUFFERS = 0x80000001,
            STOPPED = 1
        };

        enum {
            RT_CMD_NONE,
            RT_CMD_START,
            RT_CMD_STOP
        };

    class Buffer
    {
    public:
        // FIXME use m prefix
        size_t      frameCount;     // number of sample frames corresponding to size;
                                    // on input it is the number of frames available,
                                    // on output is the number of frames actually drained
                                    // (currently ignored, but will make the primary field in future)

        size_t      size;           // input/output in bytes == frameCount * frameSize
                                    // FIXME this is redundant with respect to frameCount,
                                    // and TRANSFER_OBTAIN mode is broken for 8-bit data
                                    // since we don't define the frame format

        union {
            void*       raw;
            short*      i16;        // signed 16-bit
            int8_t*     i8;         // unsigned 8-bit, offset by 0x80
        };
    };

    bool InitializeInternal(void);
    static RoutingThread *Initialize(AudioFlinger *pAudioFlinger,
                                    audio_stream_t *inStream,
                                    audio_io_handle_t output,
                                    audio_io_handle_t *rt_io_handle);

    static status_t checkSuspendFlag(AudioFlinger *pAudioFlinger,
                                           const String8& keyValuePairs);

    bool createOutputTrack(void);

        // Derived class must implement threadLoop(). The thread starts its life
        // here. There are two ways of using the Thread object:
        // 1) loop: if threadLoop() returns true, it will be called again if
        //          requestExit() wasn't called.
        // 2) once: if threadLoop() returns false, the thread will exit upon return.
        virtual bool        threadLoop();
        void threadLoopRun(void);
        void threadLoopExit(void);
        bool isReady(void);
        size_t frameSize() const;
        status_t setParameters(const String8& keyValuePairs);

        status_t startRouting(void);
        void stopRouting(void);

        size_t read(void* buffer, size_t userSize);

        sp<RecordThread::RecordTrack> mInputTrack;
        void*                         mInputBuffers;
        uint8_t                       mInputChannelCount;
        audio_format_t                mInputFormat;
        audio_stream_t *              mInputStream;
        bool                          mInputActive; // protected by mLock

        // OUTPUT (AudioTrack equivalent)
        ssize_t write(const void* buffer, size_t userSize);

        status_t obtainBuffer(Buffer* audioBuffer, const struct timespec *requested,
        struct timespec *elapsed = NULL, size_t *nonContig = NULL);

        void     releaseBuffer(Buffer* audioBuffer);

        audio_io_handle_t             mOutput;
        sp<PlaybackThread>            mOutputThread;
        sp<PlaybackThread::Track>     mOutputTrack;
        bool                          mAwaitBoost;

        // Starting address of buffers in shared memory.  If there is a shared buffer, mBuffers
        // is the value of pointer() for the shared buffer, otherwise mBuffers points
        // immediately after the control block.  This address is for the mapping within client
        // address space.  AudioFlinger::TrackBase::mBuffer is for the server address space.
        void*                         mOutputBuffers;

        bool                          mOutputActive; // protected by mLock
        audio_track_cblk_t*           mOutputCblk;
        sp<AudioTrackClientProxy>     mOutputProxy;
        sp<IMemory>                   mOutputCblkMemory;

        size_t                        mOutputFrameCount;
        size_t                        mOutputFrameSize;    // App-level frame size
        size_t                        mOutputFrameSizeAF;  // AudioFlinger frame size

        uint8_t                       mOutputChannelCount;
        audio_format_t                mOutputFormat;


        // COMMON
        mutable     Mutex             mRtLock;
        Condition                     mRtCond;

        // true if routing thread is created
        bool                          mRoutingThreadRunning;

        // true if routing thread is routing data
        bool                          mRoutingThreadStarted;
        uint8_t                       mRoutingThreadCommandPending;

        mutable Mutex                 mLock;

        uint8_t                       mMuted;

    private:

        AudioFlinger                 *mAudioFlinger;

    };

#endif // ROUTERTHREAD_H
