package com.quectel.qcarapi.osd;

/* JADX INFO: loaded from: classes.dex */
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

    public int getCsiNum() {
        return this.csiNum;
    }

    public long getNativeContext() {
        return this.mNativeContext;
    }

    public native int initOsd(byte[] bArr, int i);

    public native void native_setup();

    public void setCsiNum(int i) {
        this.csiNum = i;
    }

    public native int setOsd(int i, byte[] bArr, int i2, int i3, int i4);

    public native int setOsdColor(int i, int i2, int i3);
}
