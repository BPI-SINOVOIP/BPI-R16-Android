#define LOG_TAG "awStreamListener"
#include "log.h"
#include <CdxLog.h>
#include "awStreamListener.h"
#include <media/stagefright/foundation/AMessage.h>

awStreamListener::awStreamListener(const sp<IStreamSource> &source, ALooper::handler_id id,
									size_t numBuffers, size_t bufferSize)
    : mSource(source),
      mTargetID(id),
      mEOS(false),
	  mSendDataNotification(true),
      mReceiveEOS(false),
      mAvailableSize(0)
{
    mSource->setListener(this);

    mNumBuffers = numBuffers;
    mBufferSize = bufferSize;

    mMemoryDealer = new MemoryDealer(mNumBuffers * mBufferSize);
    for (size_t i = 0; i < mNumBuffers; ++i)
    {
        sp<IMemory> mem = mMemoryDealer->allocate(mBufferSize);
        CDX_CHECK(mem != NULL);

        mBuffers.push(mem);
    }
    mSource->setBuffers(mBuffers);
}

void awStreamListener::start()
{
	logv("awStreamListener::start");
    for (size_t i = 0; i < mNumBuffers; ++i)
    {
    	if(mReceiveEOS) {
    		break;
    	}
        mSource->onBufferAvailable(i);
    }
}

void awStreamListener::stop()
{
	//If not set listener as NULL,
	//BnStreamSource has a ref of this handle,
	//while this handle has a ref of BnStreamSource.
	//This is a dead loop which causes memeroy leak.
	logv("awStreamListener::stop");
	Mutex::Autolock autoLock(mLock);
	mEOS = true;
	mSource->setListener(NULL);
}

void awStreamListener::queueBuffer(size_t index, size_t size)
{
    Mutex::Autolock autoLock(mLock);
    QueueEntry entry;
    entry.mIsCommand = false;
    entry.mIndex = index;
    entry.mSize = size;
    entry.mOffset = 0;

    mAvailableSize += size;

    mQueue.push_back(entry);

//    if (mSendDataNotification) {
//        mSendDataNotification = false;
//
//        if (mTargetID != 0) {
//            (new AMessage(kWhatMoreDataQueued, mTargetID))->post();
//        }
//    }
}
void awStreamListener::clearBuffer() {
	Mutex::Autolock autoLock(mLock);

	logv("queue size %d", mQueue.size());
	QueueEntry *entry = NULL;
    while(mQueue.size() > 0 && !mReceiveEOS) {
    	entry = &*mQueue.begin();
    	CDX_CHECK(entry != NULL);
        mSource->onBufferAvailable(entry->mIndex);
        mQueue.erase(mQueue.begin());
        entry = NULL;
    }
    mAvailableSize = 0;
	CDX_CHECK(mQueue.empty() || mReceiveEOS);
}

void awStreamListener::issueCommand(
        Command cmd, bool synchronous, const sp<AMessage> &extra) {
    CDX_CHECK(!synchronous);
	CEDARX_UNUSE(synchronous);
	
    logv("awStreamListener::issueCommand");
    Mutex::Autolock autoLock(mLock);
    QueueEntry entry;
    entry.mIsCommand = true;
    entry.mCommand = cmd;
    entry.mExtra = extra;

    mQueue.push_back(entry);
    if(entry.mCommand == EOS) {
    	mReceiveEOS = true;
    }

//    if (mSendDataNotification) {
//        mSendDataNotification = false;
//
//        if (mTargetID != 0) {
//            (new AMessage(kWhatMoreDataQueued, mTargetID))->post();
//        }
//    }
}

ssize_t awStreamListener::read(void *data, size_t size, sp<AMessage> *extra)
{
    CDX_CHECK(size > 0u);

    extra->clear();

    Mutex::Autolock autoLock(mLock);

    if (mEOS) {
        return 0;
    }

    if ((mQueue.empty() || mAvailableSize < size) && !mReceiveEOS) {
        mSendDataNotification = true;
        return -EAGAIN;
    }
	if(mAvailableSize < size)
	{
		size = mAvailableSize;
	}

    size_t copiedSize = 0;
    while(copiedSize < size) {
    	if(mQueue.empty()) {
    		logw("Have not gotten enough data, but queue is empty. %u vs %u",
    				mAvailableSize, size);
    		break;
    	}

	    QueueEntry *entry = &*mQueue.begin();

	    if (entry->mIsCommand) {
			logv("awStreamListener mCommand:%d",entry->mCommand);
	        switch (entry->mCommand) {
	            case EOS:
	            {
	                mQueue.erase(mQueue.begin());
	                entry = NULL;
	                mEOS = true;
					return copiedSize;
	            }

	            case DISCONTINUITY:
	            {
	                *extra = entry->mExtra;

	                mQueue.erase(mQueue.begin());
	                entry = NULL;
					logw("DISCONTINUITY");
					continue;
	            }

	            default:
	                CDX_TRESPASS();
	                break;
	        }
	    }

	    size_t copy = entry->mSize;
		if (copy > size - copiedSize)
		{
			copy = size - copiedSize;
	    }

		memcpy((uint8_t *)data + copiedSize,
           (const uint8_t *)mBuffers.editItemAt(entry->mIndex)->pointer()
            + entry->mOffset,
           copy);

	    entry->mOffset += copy;
	    entry->mSize -= copy;
		mAvailableSize 	-= copy;
		copiedSize 		+= copy;
	    if (entry->mSize == 0) {
	        mSource->onBufferAvailable(entry->mIndex);
	        mQueue.erase(mQueue.begin());
	        entry = NULL;
	    }
	}
    CDX_CHECK(copiedSize == size);
    return copiedSize;
}

ssize_t awStreamListener::copy(void *data, size_t size, sp<AMessage> *extra)
{
    CDX_CHECK(size > 0u);

    extra->clear();

    Mutex::Autolock autoLock(mLock);

    if (mEOS) {
        return 0;
    }

    if ((mQueue.empty() || mAvailableSize < size) && !mReceiveEOS) {
        mSendDataNotification = true;
        return -EAGAIN;
    }
	if(mAvailableSize < size)
	{
		size = mAvailableSize;
	}

    size_t copiedSize = 0;
    while(copiedSize < size) {
    	if(mQueue.empty()) {
    		logw("Have not gotten enough data, but queue is empty. %u vs %u",
    				mAvailableSize, size);
    		break;
    	}
		List<QueueEntry>::iterator it = mQueue.begin();
	    QueueEntry *entry = &*it;

	    if (entry->mIsCommand) {
			logv("awStreamListener mCommand:%d",entry->mCommand);
	        switch (entry->mCommand) {
	            case EOS:
	            {
	                mQueue.erase(mQueue.begin());
	                entry = NULL;
	                mEOS = true;
					return copiedSize;
	            }

	            case DISCONTINUITY:
	            {
	                *extra = entry->mExtra;

	                mQueue.erase(mQueue.begin());
	                entry = NULL;
					logw("DISCONTINUITY");
					continue;
	            }

	            default:
	                CDX_TRESPASS();
	                break;
	        }
	    }

	    size_t copy = entry->mSize;
		if (copy > size - copiedSize)
		{
			copy = size - copiedSize;
	    }

		memcpy((uint8_t *)data + copiedSize,
           (const uint8_t *)mBuffers.editItemAt(entry->mIndex)->pointer(),
           copy);

		copiedSize += copy;
		++it;
	}
    CDX_CHECK(copiedSize == size);
    return copiedSize;
}
size_t awStreamListener::getCachedSize()
{
	return mAvailableSize;
}
bool awStreamListener::isReceiveEOS()
{
	return mReceiveEOS;
}
