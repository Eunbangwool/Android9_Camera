package com.quectel.qcarapi.image;

/* JADX INFO: loaded from: classes.dex */
public class QCarLibYuv {
    public static native int nv12ConvertYV12(byte[] bArr, int i, int i2, byte[] bArr2);

    public static native int yuv420Roi(byte[] bArr, int i, int i2, int i3, byte[] bArr2, int i4, int i5, int i6, int i7);

    public static native int yuv420Scale(byte[] bArr, int i, int i2, int i3, byte[] bArr2, int i4, int i5);
}
