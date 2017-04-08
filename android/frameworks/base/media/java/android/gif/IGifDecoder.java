package android.gif;

import android.graphics.Bitmap;

public interface IGifDecoder {
    public static final int INVALID_VALUE = 0;

    public void close();
    public int getWidth();
    public int getHeight();
    public int getTotalDuration();
    public int getTotalFrameCount();
    public int getFrameDuration(int frameIndex);
    public Bitmap getFrameBitmap(int frameIndex);
}
