package com.quectel.qcarapi.stream;

import android.view.Surface;
import com.quectel.qcarapi.osd.QCarOsd;
import com.quectel.qcarapi.util.QCarError;
import java.nio.ByteBuffer;

/**
 * qcarcam(Qualcomm AIS) 카메라 접근 (JNI 바인딩용 재구현).
 *
 * 네이티브 라이브러리 libmmqcar_qcar_jni.so 가 이 클래스의 native 메서드/필드에 바인딩되므로
 * 패키지·클래스명·필드명(csiNum, mNativeContext)·메서드 시그니처를 원본과 정확히 일치시킨다.
 * (프레임 획득 = getPreviewFrameInfo, 검증된 호출: cameraOpen(csi,inputNum,fmt) →
 *  채널별 setPreviewStreamSize/ColorFormat/startPreviewStream → getPreviewFrameInfo)
 */
public class QCarCamera {
    public static int MAIN_STREAM_OSD_TYPE = 0;
    public static int MERGE_STREAM_OSD_TYPE = 1;
    public static int SUB_STREAM_OSD_TYPE = 2;
    public static int YUV420_NV12 = 1;
    public static int YUV420_NV21 = 0;
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

    private native void native_setup();
    private native void native_release();
    private native int cameraOpen(int i, int i2, int i3);

    public native int cameraClose();
    public native int cameraForceCloseHw();
    public native int detectCamInputStatus(boolean[] zArr, int i);

    public native int getPreviewStreamColorFormat(int i);
    public native FrameInfo getPreviewFrameInfo(int i, ByteBuffer byteBuffer);
    public native FrameInfo getSubFrameInfo(int i, ByteBuffer byteBuffer);
    public native FrameInfo getVideoFrameInfo(int i, ByteBuffer byteBuffer);
    public native int getVideoColorFormat(int i);
    public native FrameInfo getMergeStreamFrameInfo(ByteBuffer byteBuffer);
    public native int getMergeStreamColorFormat();

    public native int setCarOsd(long j, int i);
    public native int setErrorCB(Object obj);
    public native int setFps(int i, int i2);
    public native int setFpsLogDebug(int i, int i2);
    public native int setMergeStreamColorFormat(int i);
    public native int setMergeStreamMirror(int i, boolean z);
    public native int setMergeStreamSize(int i, int i2);
    public native int setPreviewMirror(int i, boolean z);
    public native int setPreviewStreamColorFormat(int i, int i2);
    public native int setPreviewStreamMirror(int i, boolean z);
    public native int setPreviewStreamSize(int i, int i2, int i3);
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

    public int cameraOpen(int i, int i2) {
        return cameraOpen(this.csiNum, i, i2);
    }

    public int getCsiNum() { return this.csiNum; }
    public QCarOsd getMainOsd() { return this.mainOsd; }
    public QCarOsd getSubOsd() { return this.subOsd; }

    public void registerOnErrorCB(QCarError.OnErrorCB cb) {
        onErrorCB = cb;
        if (cb != null) setErrorCB(cb);
    }

    public void setMainOsd(QCarOsd qCarOsd) {
        this.mainOsd = qCarOsd;
        setCarOsd(qCarOsd.getNativeContext(), MAIN_STREAM_OSD_TYPE);
    }

    public void release() {
        native_release();
    }
}
