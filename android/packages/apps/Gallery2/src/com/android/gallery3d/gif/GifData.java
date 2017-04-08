package com.android.gallery3d.gif;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.InputStream;
import android.graphics.Bitmap;
import android.util.Log;
import android.gif.GifDecoder;

public class GifData {
    private static final String TAG = "SinglePhotoDataAdapter";
    public GifData() {
        gifDecoder = null;
    }
    public void recycle() {
        gifDecoder = null;
    }

    public void info() {
        if (null != gifDecoder) {
            Log.v(TAG,"DataBundle:gifDecoder,getTotalFrameCount()="
                      + gifDecoder.getTotalFrameCount());
        }
    }
    
    public GifDecoderWrapper gifDecoder;
}

