package com.quectel.qcarapi.osd;

/**
 * qcarcam OSD (JNI 바인딩용 재구현). 프리뷰만 하는 경우 인스턴스화하지 않지만,
 * QCarCamera 가 이 타입을 참조하므로 클래스는 존재해야 한다.
 * 필드명(mNativeContext, csiNum)은 네이티브가 GetFieldID 로 접근하므로 정확히 일치시킨다.
 */
public class QCarOsd {
    public int csiNum = -1;
    public long mNativeContext;

    public QCarOsd() {
        native_setup();
    }

    public native int clearOsd(int i);
    public native int deinitOsd();
    public native int disableOsd();
    public native int enableOsd();
    public native int initOsd(byte[] bArr, int i);
    public native void native_setup();
    public native int setOsd(int i, byte[] bArr, int i2, int i3, int i4);
    public native int setOsdColor(int i, int i2, int i3);

    public int getCsiNum() { return this.csiNum; }
    public long getNativeContext() { return this.mNativeContext; }
    public void setCsiNum(int i) { this.csiNum = i; }
}
