package android.gif;

import android.gif.IGifDecoder;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import android.util.Log;
import android.graphics.Bitmap;
import android.graphics.Movie;

public class GifDecoder implements IGifDecoder {
    private static final String TAG = "GifDecoder";
    
    private static final int MINIMAL_DURATION = 40; 

    /**
     * Movie object maitained by GifDecoder, it contains raw GIF info
     * like graphic control informations, color table, pixel indexes,
     * called when application is no longer interested in gif info. 
     * It is contains GIF frame bitmap, 8-bits per pixel, 
     * using SkColorTable to specify the colors, which is much
     * memory efficient than ARGB_8888 config. This is why we
     * maintain a Movie object rather than a set of ARGB_8888 Bitmaps.
     */
    private Movie mMovie;
    
    /**
     * Constructor of GifDecoder, which receives InputStream as
     * parameter. Decode an InputStream into Movie object. 
     * If the InputStream is null, no decoding will be performed
     *
     * @param is InputStream representing the file to be decoded.
     */
    public GifDecoder(InputStream is) {
        Log.i(TAG,"GifDecoder(is="+is+")");
        if (is == null)
            return;
                 
        mMovie = Movie.decodeStream(is);
    }

    public GifDecoder(byte[] data, int offset,int length) {
        if (data == null)
            return;
        mMovie = Movie.decodeByteArray(data, offset, length);
    }

    /**
     * Constructor of GifDecoder, which receives file path name as
     * parameter. Decode a file path into Movie object. 
     * If the specified file name is null, no decoding will be performed
     *
     * @param pathName complete path name for the file to be decoded.
     */
    public GifDecoder(String pathName) {
        Log.i(TAG,"GifDecoder(pathName="+pathName+")");
        if (pathName == null)
            return;
        mMovie = Movie.decodeFile(pathName);
    }

    /**
     * Close gif file, release all informations like frame count,
     * graphic control informations, color table, pixel indexes,
     * called when application is no longer interested in gif info. 
     * It will release all the memory mMovie occupies. After close()
     * is call, GifDecoder should no longer been used.
     */
    public synchronized void close(){
        if (mMovie == null)
            return;
        mMovie.closeGif();
        mMovie = null;
    }

    /**
     * Get width of images in gif file. 
     * if member mMovie is null, returns INVALID_VALUE
     *
     * @return The total frame count of gif file,
     *         or INVALID_VALUE if the mMovie is null
     */
    public synchronized int getWidth() {
        if (mMovie == null)
            return INVALID_VALUE;
        return mMovie.width();
    }

    /**
     * Get height of images in gif file. 
     * if member mMovie is null, returns INVALID_VALUE
     *
     * @return The total frame count of gif file,
     *         or INVALID_VALUE if the mMovie is null
     */
    public synchronized int getHeight() {
        if (mMovie == null)
            return INVALID_VALUE;
        return mMovie.height();
    }

    /**
     * Get total duration of gif file. 
     * if member mMovie is null, returns INVALID_VALUE
     *
     * @return The total duration of gif file,
     *         or INVALID_VALUE if the mMovie is null
     */
    public synchronized int getTotalDuration() {
        if (mMovie == null)
            return INVALID_VALUE;
        return mMovie.duration();
    }

    /**
     * Get total frame count of gif file. 
     * if member mMovie is null, returns INVALID_VALUE
     *
     * @return The total frame count of gif file,
     *         or INVALID_VALUE if the mMovie is null
     */
    public synchronized int getTotalFrameCount() {
        if (mMovie == null)
            return INVALID_VALUE;
        return mMovie.gifTotalFrameCount();
    }

    /**
     * Get frame duration specified with frame index of gif file. 
     * if member mMovie is null, returns INVALID_VALUE
     *
     * @param frameIndex index of frame interested.
     * @return The duration of the specified frame,
     *         or INVALID_VALUE if the mMovie is null
     */
    public synchronized int getFrameDuration(int frameIndex) {
        if (mMovie == null)
            return INVALID_VALUE;
        int duration = mMovie.gifFrameDuration(frameIndex);
        if (duration < MINIMAL_DURATION)
            duration = MINIMAL_DURATION;
        return duration;
    }

    /**
     * Get frame bitmap specified with frame index of gif file. 
     * if member mMovie is null, returns null
     *
     * @param frameIndex index of frame interested.
     * @return The decoded bitmap, or null if the mMovie is null
     */
    public synchronized Bitmap getFrameBitmap(int frameIndex) {
        if (mMovie == null)
            return null;
        return mMovie.gifFrameBitmap(frameIndex);
    }
}
