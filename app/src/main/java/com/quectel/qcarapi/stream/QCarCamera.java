package com.quectel.qcarapi.stream;

import android.view.Surface;
import com.quectel.qcarapi.osd.QCarOsd;
import com.quectel.qcarapi.util.QCarError;
import java.nio.ByteBuffer;

/* JADX INFO: loaded from: classes.dex */
public class QCarCamera {
    public static int MAIN_STREAM_OSD_TYPE = 0;
    public static int MERGE_STREAM_OSD_TYPE = 1;
    public static int SUB_STREAM_OSD_TYPE = 2;
    public static int YUV420_NV12 = 1;
    public static int YUV420_NV21;
    public static QCarError.OnErrorCB onErrorCB;
    public int csiNum;
    public long mNativeContext;
    public QCarOsd mainOsd = null;
    public QCarOsd mergeOsd = null;
    public QCarOsd subOsd = null;

    public static class FrameInfo {
        public long frameID;
        public long ptsSec;
        public long ptsUsec;
    }

    public QCarCamera(int i) {
        this.csiNum = -1;
        this.csiNum = i;
        native_setup();
    }

    private native int cameraOpen(int i, int i2, int i3);

    public static QCarError.OnErrorCB getOnErrorCB() {
        return onErrorCB;
    }

    private native void native_release();

    private native void native_setup();

    private void setMergeOsd(QCarOsd qCarOsd) {
        long nativeContext;
        int i;
        this.mergeOsd = qCarOsd;
        if (qCarOsd == null) {
            i = MERGE_STREAM_OSD_TYPE;
            nativeContext = 0;
        } else {
            nativeContext = qCarOsd.getNativeContext();
            i = MERGE_STREAM_OSD_TYPE;
        }
        setCarOsd(nativeContext, i);
    }

    public native int cameraClose();

    public native int cameraForceCloseHw();

    public int cameraOpen(int i, int i2) {
        return cameraOpen(this.csiNum, i, i2);
    }

    public native int detectCamInputStatus(boolean[] zArr, int i);

    public int getCsiNum() {
        return this.csiNum;
    }

    public QCarOsd getMainOsd() {
        return this.mainOsd;
    }

    public native int getMergeStreamColorFormat();

    public native FrameInfo getMergeStreamFrameInfo(ByteBuffer byteBuffer);

    public native FrameInfo getPreviewFrameInfo(int i, ByteBuffer byteBuffer);

    public native int getPreviewStreamColorFormat(int i);

    public native FrameInfo getSubFrameInfo(int i, ByteBuffer byteBuffer);

    public QCarOsd getSubOsd() {
        return this.subOsd;
    }

    public native int getVideoColorFormat(int i);

    public native FrameInfo getVideoFrameInfo(int i, ByteBuffer byteBuffer);

    public void registerOnErrorCB(QCarError.OnErrorCB onErrorCB2) {
        onErrorCB = onErrorCB2;
        if (onErrorCB2 != null) {
            setErrorCB(onErrorCB2);
        }
    }

    public void release() {
        native_release();
    }

    public native int setCarOsd(long j, int i);

    public native int setErrorCB(Object obj);

    public native int setFps(int i, int i2);

    public native int setFpsLogDebug(int i, int i2);

    public void setMainOsd(QCarOsd qCarOsd) {
        this.mainOsd = qCarOsd;
        setCarOsd(qCarOsd.getNativeContext(), MAIN_STREAM_OSD_TYPE);
    }

    public native int setMergeStreamColorFormat(int i);

    public native int setMergeStreamMirror(int i, boolean z);

    public native int setMergeStreamSize(int i, int i2);

    public native int setPreviewMirror(int i, boolean z);

    public native int setPreviewStreamColorFormat(int i, int i2);

    public native int setPreviewStreamMirror(int i, boolean z);

    public native int setPreviewStreamSize(int i, int i2, int i3);

    public void setSubOsd(QCarOsd qCarOsd) {
        this.subOsd = qCarOsd;
        setCarOsd(qCarOsd.getNativeContext(), SUB_STREAM_OSD_TYPE);
    }

    public native int setSubStreamMirror(int i, boolean z);

    public native int setSubStreamSize(int i, int i2, int i3);

    public native int setVideoColorFormat(int i, int i2);

    public native int setVideoSize(int i, int i2, int i3);

    public native int setVideoStreamMirror(int i, boolean z);

    public native int startMergeStream();

    public native int startPreview(int i, Surface surface, int i2, int i3, int i4);

    public native int startPreviewStream(int i);

    public native int startSubStream(int i);

    public native int startVideoStream(int i);

    public native int stopMergeStream();

    public native int stopPreview(int i);

    public native int stopPreviewStream(int i);

    public native int stopSubStream(int i);

    public native int stopVideoStream(int i);
}
