/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

#include <linux/kdev_t.h>
#include <fcntl.h>
#include <dirent.h>
#include <sys/stat.h>

#define LOG_TAG "DirectVolume"

#include <cutils/log.h>
#include <sysutils/NetlinkEvent.h>

#include "DirectVolume.h"
#include "VolumeManager.h"
#include "ResponseCode.h"
#include "cryptfs.h"

// #define PARTITION_DEBUG

DirectVolume::DirectVolume(VolumeManager *vm, const fstab_rec* rec, int flags) :
        Volume(vm, rec, flags) {
    mPaths = new PathCollection();
    for (int i = 0; i < MAX_PARTITIONS; i++)
        mPartMinors[i] = -1;
    mPendingPartMap = 0;
    mDiskMajor = -1;
    mDiskMinor = -1;
    mOrigDiskMajor = -1;
    mOrigDiskMinor = -1;
    mDiskNumParts = 0;
    mOemPartMajor = -1;
    mOemPartMinor = -1;
    mPartsEventCnt = 0;
    mIsDecrypted = 0;
    mDiskSharingCount = 0;
    mSavedNrRequests = -1;
    mSavedMaxSectorsKb = -1;
    // set nr_requests to 32 when UMS is active
    mDiskNrRequests = 32;
    // set max_sectors_kb to 64 when UMS is active
    mDiskMaxSectorsKb = 64;

    if (strcmp(rec->mount_point, "auto") != 0) {
        ALOGE("Vold managed volumes must have auto mount point; ignoring %s",
              rec->mount_point);
    }

    char mount[PATH_MAX];

    snprintf(mount, PATH_MAX, "%s/%s", Volume::MEDIA_DIR, rec->label);
    mMountpoint = strdup(mount);
    snprintf(mount, PATH_MAX, "%s/%s", Volume::FUSE_DIR, rec->label);
    mFuseMountpoint = strdup(mount);

    setState(Volume::State_NoMedia);
}

DirectVolume::~DirectVolume() {
    PathCollection::iterator it;

    for (it = mPaths->begin(); it != mPaths->end(); ++it)
        free(*it);
    delete mPaths;
}

int DirectVolume::addPath(const char *path) {
    mPaths->push_back(strdup(path));
    return 0;
}

dev_t DirectVolume::getDiskDevice() {
    return MKDEV(mDiskMajor, mDiskMinor);
}

dev_t DirectVolume::getShareDevice() {
    if (mPartIdx != -1) {
        return MKDEV(mDiskMajor, mPartIdx);
    } else {
        return MKDEV(mDiskMajor, mDiskMinor);
    }
}

void DirectVolume::handleVolumeShared() {
    if(mDiskSharingCount++ == 0){
        struct dirent *de;
        int dfd, fd;
        struct stat stats;
        DIR *dir;
        char path[256];
        FILE *fp;
        dev_t d;

        dir = opendir("/dev/block");
        dfd = dirfd(dir);
        if(dfd < 0){
            SLOGE("Failed to open dir \"/dev/block\" (%s)", strerror(errno));
        }
        d = getDiskDevice();
        while((de = readdir(dir))){
            if(de->d_name[0] == '.')
                continue;
            fd = openat(dfd, de->d_name, O_RDONLY);
            if(fd < 0)
                continue;
            fstat(fd, &stats);
            close(fd);

            if((dev_t)stats.st_rdev == d){
                mSavedNrRequests = -1; // in case we fail
                if(strncmp(de->d_name, "mmcblk0", 7))
                    sprintf(path, "/sys/block/%s/queue/nr_requests", de->d_name);
                else
                    sprintf(path, "/sys/block/mmcblk0/queue/nr_requests");
                if ((fp = fopen(path, "r+"))) {
                    char line[16];
                    if (fgets(line, sizeof(line), fp) && sscanf(line, "%d", &mSavedNrRequests)) {
                        fprintf(fp, "%d\n", mDiskNrRequests);
                    } else {
                        SLOGE("Failed to read nr_requests (%s)", strerror(errno));
                    }
                    fclose(fp);
                } else {
                    SLOGE("Failed to open %s (%s)", path, strerror(errno));
                }

                mSavedMaxSectorsKb = -1; // in case we fail
                if(strncmp(de->d_name, "mmcblk0", 7))
                    sprintf(path, "/sys/block/%s/queue/max_sectors_kb", de->d_name);
                else
                    sprintf(path, "/sys/block/mmcblk0/queue/max_sectors_kb");
                if ((fp = fopen(path, "r+"))) {
                    char line[16];
                    if (fgets(line, sizeof(line), fp) && sscanf(line, "%d", &mSavedMaxSectorsKb)) {
                        fprintf(fp, "%d\n", mDiskMaxSectorsKb);
                    } else {
                        SLOGE("Failed to read max_sectors_kb (%s)", strerror(errno));
                    }
                    fclose(fp);
                } else {
                    SLOGE("Failed to open %s (%s)", path, strerror(errno));
                }
                break;
            }
        }
        closedir(dir);
    }
    setState(Volume::State_Shared);
}

void DirectVolume::handleVolumeUnshared() {
    if(--mDiskSharingCount == 0){
        struct dirent *de;
        int dfd, fd;
        struct stat stats;
        DIR *dir;
        char path[256];
        FILE *fp;
        dev_t d;
        dir = opendir("/dev/block");
        dfd = dirfd(dir);
        if(dfd < 0){
             SLOGE("Failed to open dir \"/dev/block\" (%s)", strerror(errno));
        }

        d = getDiskDevice();
        while((de = readdir(dir))){
            if(de->d_name[0] == '.')
                continue;
            fd = openat(dfd, de->d_name, O_RDONLY);
            if(fd < 0)
                continue;
            fstat(fd, &stats);
            close(fd);

            if(stats.st_rdev == d){
                if(mSavedNrRequests == -1)
                    break;
                if(strncmp(de->d_name, "mmcblk0", 7))
                    sprintf(path, "/sys/block/%s/queue/nr_requests", de->d_name);
                else
                    sprintf(path, "/sys/block/mmcblk0/queue/nr_requests");
                if ((fp = fopen(path, "r+"))) {
                    fprintf(fp, "%d\n", mSavedNrRequests);
                    fclose(fp);
                } else {
                    SLOGE("Failed to open %s (%s)", path, strerror(errno));
                }

                if(mSavedMaxSectorsKb == -1)
                    break;
                if(strncmp(de->d_name, "mmcblk0", 7))
                    sprintf(path, "/sys/block/%s/queue/max_sectors_kb", de->d_name);
                else
                    sprintf(path, "/sys/block/mmcblk0/queue/max_sectors_kb");
                if ((fp = fopen(path, "r+"))) {
                    fprintf(fp, "%d\n", mSavedMaxSectorsKb);
                    fclose(fp);
                } else {
                    SLOGE("Failed to open %s (%s)", path, strerror(errno));
                }
                break;
            }
        }
        closedir(dir);
    }
    setState(Volume::State_Idle);
}

int DirectVolume::handleBlockEvent(NetlinkEvent *evt) {
    const char *dp = evt->findParam("DEVPATH");

    PathCollection::iterator  it;
    for (it = mPaths->begin(); it != mPaths->end(); ++it) {
        if (!strncmp(dp, *it, strlen(*it))) {
            /* We can handle this disk */
            int action = evt->getAction();
            const char *devtype = evt->findParam("DEVTYPE");

            if (action == NetlinkEvent::NlActionAdd) {
                int major = atoi(evt->findParam("MAJOR"));
                int minor = atoi(evt->findParam("MINOR"));
                char nodepath[255];
                mPartsChangeFlag = 0;
                if (major == 179 || major == 259) {
                    if (!strncmp(getLabel(), "sdcard", strlen("sdcard")) && !strncmp(dp, "/devices/platform/sunxi-mmc.", strlen("/devices/platform/sunxi-mmc."))) {
                        mPartsChangeFlag = 1;
                    } else if (!strncmp(getLabel(), "extsd", strlen("extsd")) && !strncmp(dp, "/devices/platform/sunxi-mmc.2", strlen("/devices/platform/sunxi-mmc.2"))) {
                        mPartsChangeFlag = 2;
                        mOemPartMajor = major;
                        mOemPartMinor = minor;
                    }
                }
                snprintf(nodepath,
                         sizeof(nodepath), "/dev/block/vold/%d:%d",
                         major, minor);
                if (createDeviceNode(nodepath, major, minor)) {
                    SLOGE("Error making device node '%s' (%s)", nodepath,
                                                               strerror(errno));
                }
                if (!strcmp(devtype, "disk")) {
                    handleDiskAdded(dp, evt);
                } else {
                    handlePartitionAdded(dp, evt);
                }
                /* Send notification iff disk is ready (ie all partitions found) */
                if (getState() == Volume::State_Idle) {
                    char msg[255];

                    snprintf(msg, sizeof(msg),
                             "Volume %s %s disk inserted (%d:%d)", getLabel(),
                             getFuseMountpoint(), mDiskMajor, mDiskMinor);
                    mVm->getBroadcaster()->sendBroadcast(ResponseCode::VolumeDiskInserted,
                                                         msg, false);
                }
            } else if (action == NetlinkEvent::NlActionRemove) {
                if (!strcmp(devtype, "disk")) {
                    handleDiskRemoved(dp, evt);
                } else {
                    handlePartitionRemoved(dp, evt);
                }
            } else if (action == NetlinkEvent::NlActionChange) {
                if (!strcmp(devtype, "disk")) {
                    handleDiskChanged(dp, evt);
                } else {
                    handlePartitionChanged(dp, evt);
                }
            } else {
                    SLOGW("Ignoring non add/remove/change event");
            }

            return 0;
        }
    }
    errno = ENODEV;
    return -1;
}

void DirectVolume::handleDiskAdded(const char *devpath, NetlinkEvent *evt) {
    mDiskMajor = atoi(evt->findParam("MAJOR"));
    mDiskMinor = atoi(evt->findParam("MINOR"));

    const char *tmp = evt->findParam("NPARTS");
    if (tmp) {
        mDiskNumParts = atoi(tmp);
    } else {
        SLOGW("Kernel block uevent missing 'NPARTS'");
        mDiskNumParts = 1;
    }
    mPartsEventCnt = 0;

    int partmask = 0;
    int i;
    for (i = 1; i <= mDiskNumParts; i++) {
        partmask |= (1 << i);
    }
    mPendingPartMap = partmask;

    if (mDiskNumParts == 0) {
#ifdef PARTITION_DEBUG
        SLOGD("Dv::diskIns - No partitions - good to go son!");
#endif
        setState(Volume::State_Idle);
    } else {
#ifdef PARTITION_DEBUG
        SLOGD("Dv::diskIns - waiting for %d partitions (mask 0x%x)",
             mDiskNumParts, mPendingPartMap);
#endif
        setState(Volume::State_Pending);
    }
}

void DirectVolume::handlePartitionAdded(const char *devpath, NetlinkEvent *evt) {
    int major = atoi(evt->findParam("MAJOR"));
    int minor = atoi(evt->findParam("MINOR"));

    int part_num;

    const char *tmp = evt->findParam("PARTN");
    if (mPartsEventCnt > mDiskNumParts) {
        SLOGW("Partition event is to much, mPartsEventCnt=%d, mDiskNumParts=%d\n", mPartsEventCnt, mDiskNumParts);
        mPartsEventCnt = mDiskNumParts;
    } else {
        mPartsEventCnt++;
    }

    if (tmp) {
        part_num = atoi(tmp);
    } else {
        SLOGW("Kernel block uevent missing 'PARTN'");
        part_num = 1;
    }

    if (part_num > MAX_PARTITIONS || part_num < 1) {
        SLOGE("Invalid 'PARTN' value");
        return;
    }

    if (part_num > mDiskNumParts) {
        mDiskNumParts = part_num;
    }

    if (major != mDiskMajor) {
        SLOGE("Partition '%s' has a different major than its disk!", devpath);
        return;
    }
#ifdef PARTITION_DEBUG
    SLOGD("Dv:partAdd: part_num = %d, minor = %d\n", part_num, minor);
#endif
    if (part_num >= MAX_PARTITIONS) {
        SLOGE("Dv:partAdd: ignoring part_num = %d (max: %d)\n", part_num, MAX_PARTITIONS-1);
    } else {
        mPartMinors[part_num -1] = minor;
    }
    //mPendingPartMap &= ~(1 << part_num);
    mPendingPartMap &= ~(1 << mPartsEventCnt);

    if (!mPendingPartMap) {
#ifdef PARTITION_DEBUG
        SLOGD("Dv:partAdd: Got all partitions - ready to rock!");
#endif
        if (getState() != Volume::State_Formatting) {
            setState(Volume::State_Idle);
            if (mRetryMount == true) {
                mRetryMount = false;
                mountVol();
            }
        }
    } else {
#ifdef PARTITION_DEBUG
        SLOGD("Dv:partAdd: pending mask now = 0x%x", mPendingPartMap);
#endif
    }
}

void DirectVolume::handleDiskChanged(const char *devpath, NetlinkEvent *evt) {
    int major = atoi(evt->findParam("MAJOR"));
    int minor = atoi(evt->findParam("MINOR"));

    if ((major != mDiskMajor) || (minor != mDiskMinor)) {
        return;
    }

    SLOGI("Volume %s disk has changed", getLabel());
    const char *tmp = evt->findParam("NPARTS");
    if (tmp) {
        mDiskNumParts = atoi(tmp);
    } else {
        SLOGW("Kernel block uevent missing 'NPARTS'");
        mDiskNumParts = 1;
    }

    int partmask = 0;
    int i;
    for (i = 1; i <= mDiskNumParts; i++) {
        partmask |= (1 << i);
    }
    mPendingPartMap = partmask;

    if (getState() != Volume::State_Formatting) {
        if (mDiskNumParts == 0) {
            setState(Volume::State_Idle);
        } else {
            setState(Volume::State_Pending);
        }
    }
}

void DirectVolume::handlePartitionChanged(const char *devpath, NetlinkEvent *evt) {
    int major = atoi(evt->findParam("MAJOR"));
    int minor = atoi(evt->findParam("MINOR"));
    SLOGD("Volume %s %s partition %d:%d changed\n", getLabel(), getMountpoint(), major, minor);
}

void DirectVolume::handleDiskRemoved(const char *devpath, NetlinkEvent *evt) {
    int major = atoi(evt->findParam("MAJOR"));
    int minor = atoi(evt->findParam("MINOR"));
    char msg[255];
    bool enabled;

    if (mVm->shareEnabled(getLabel(), "ums", &enabled) == 0 && enabled) {
        mVm->unshareVolume(getLabel(), "ums");
    }

    SLOGD("Volume %s %s disk %d:%d removed\n", getLabel(), getMountpoint(), major, minor);
    snprintf(msg, sizeof(msg), "Volume %s %s disk removed (%d:%d)",
             getLabel(), getFuseMountpoint(), major, minor);
    mVm->getBroadcaster()->sendBroadcast(ResponseCode::VolumeDiskRemoved,
                                             msg, false);
    Volume::unmountVol(true,true);
    setState(Volume::State_NoMedia);
}

void DirectVolume::handlePartitionRemoved(const char *devpath, NetlinkEvent *evt) {
    int major = atoi(evt->findParam("MAJOR"));
    int minor = atoi(evt->findParam("MINOR"));
    char msg[255];
    int state;

    SLOGD("Volume %s %s partition %d:%d removed\n", getLabel(), getMountpoint(), major, minor);

    /*
     * The framework doesn't need to get notified of
     * partition removal unless it's mounted. Otherwise
     * the removal notification will be sent on the Disk
     * itself
     */
    state = getState();
    if (state != Volume::State_Mounted && state != Volume::State_Shared) {
        return;
    }
        
    if ((dev_t) MKDEV(major, minor) == mCurrentlyMountedKdev) {
        /*
         * Yikes, our mounted partition is going away!
         */

        bool providesAsec = (getFlags() & VOL_PROVIDES_ASEC) != 0;
        if (providesAsec && mVm->cleanupAsec(this, true)) {
            SLOGE("Failed to cleanup ASEC - unmount will probably fail!");
        }

        if (!strstr(getLabel(),"usb") && !strstr(getLabel(),"extsd")) {
        snprintf(msg, sizeof(msg), "Volume %s %s bad removal (%d:%d)",
                 getLabel(), getFuseMountpoint(), major, minor);
        mVm->getBroadcaster()->sendBroadcast(ResponseCode::VolumeBadRemoval,
                                             msg, false);
        }

        if (Volume::unmountVol(true, false)) {
            SLOGE("Failed to unmount volume on bad removal (%s)", 
                 strerror(errno));
            // XXX: At this point we're screwed for now
        } else {
            SLOGD("Crisis averted");
        }
    } else if (state == Volume::State_Shared) {
        /* removed during mass storage */
        snprintf(msg, sizeof(msg), "Volume %s bad removal (%d:%d)",
                 getLabel(), major, minor);
        mVm->getBroadcaster()->sendBroadcast(ResponseCode::VolumeBadRemoval,
                                             msg, false);

        if (mVm->unshareVolume(getLabel(), "ums")) {
            SLOGE("Failed to unshare volume on bad removal (%s)",
                strerror(errno));
        } else {
            SLOGD("Crisis averted");
        }
    }
}

/*
 * Called from base to get a list of devicenodes for mounting
 */
int DirectVolume::getDeviceNodes(dev_t *devs, int max) {

    if (mPartIdx == -1) {
    if (!mPartsChangeFlag) {
        // If the disk has no partitions, try the disk itself
        if (!mDiskNumParts) {
            devs[0] = MKDEV(mDiskMajor, mDiskMinor);
            return 1;
        }

        int i;
        for (i = 0; i < mDiskNumParts; i++) {
            if (i == max)
                break;
            devs[i] = MKDEV(mDiskMajor, mPartMinors[i]);
        }
        return mDiskNumParts;
    } else if (mPartsChangeFlag == 1) {
        devs[0] = MKDEV(mDiskMajor, mPartMinors[0]);
        return 1;
    } else if (mPartsChangeFlag == 2) {
        if (mOemPartMajor > 0 && mOemPartMinor > 0) {
            devs[0] = MKDEV(mOemPartMajor, mOemPartMinor);
            return 1;
        }
        return 0;
    }
    }
    devs[0] = MKDEV(mDiskMajor, mPartMinors[mPartIdx -1]);
    return 1;
}

/*
 * Called from base to update device info,
 * e.g. When setting up an dm-crypt mapping for the sd card.
 */
int DirectVolume::updateDeviceInfo(char *new_path, int new_major, int new_minor)
{
    PathCollection::iterator it;

    if (mPartIdx == -1) {
        SLOGE("Can only change device info on a partition\n");
        return -1;
    }

    /*
     * This is to change the sysfs path associated with a partition, in particular,
     * for an internal SD card partition that is encrypted.  Thus, the list is
     * expected to be only 1 entry long.  Check that and bail if not.
     */
    if (mPaths->size() != 1) {
        SLOGE("Cannot change path if there are more than one for a volume\n");
        return -1;
    }

    it = mPaths->begin();
    free(*it); /* Free the string storage */
    mPaths->erase(it); /* Remove it from the list */
    addPath(new_path); /* Put the new path on the list */

    /* Save away original info so we can restore it when doing factory reset.
     * Then, when doing the format, it will format the original device in the
     * clear, otherwise it just formats the encrypted device which is not
     * readable when the device boots unencrypted after the reset.
     */
    mOrigDiskMajor = mDiskMajor;
    mOrigDiskMinor = mDiskMinor;
    mOrigPartIdx = mPartIdx;
    memcpy(mOrigPartMinors, mPartMinors, sizeof(mPartMinors));

    mDiskMajor = new_major;
    mDiskMinor = new_minor;
    /* Ugh, virual block devices don't use minor 0 for whole disk and minor > 0 for
     * partition number.  They don't have partitions, they are just virtual block
     * devices, and minor number 0 is the first dm-crypt device.  Luckily the first
     * dm-crypt device is for the userdata partition, which gets minor number 0, and
     * it is not managed by vold.  So the next device is minor number one, which we
     * will call partition one.
     */
    mPartIdx = new_minor;
    mPartMinors[new_minor-1] = new_minor;

    mIsDecrypted = 1;

    return 0;
}

/*
 * Called from base to revert device info to the way it was before a
 * crypto mapping was created for it.
 */
void DirectVolume::revertDeviceInfo(void)
{
    if (mIsDecrypted) {
        mDiskMajor = mOrigDiskMajor;
        mDiskMinor = mOrigDiskMinor;
        mPartIdx = mOrigPartIdx;
        memcpy(mPartMinors, mOrigPartMinors, sizeof(mPartMinors));

        mIsDecrypted = 0;
    }

    return;
}

/*
 * Called from base to give cryptfs all the info it needs to encrypt eligible volumes
 */
int DirectVolume::getVolInfo(struct volume_info *v)
{
    strcpy(v->label, mLabel);
    strcpy(v->mnt_point, mMountpoint);
    v->flags = getFlags();
    /* Other fields of struct volume_info are filled in by the caller or cryptfs.c */

    return 0;
}
