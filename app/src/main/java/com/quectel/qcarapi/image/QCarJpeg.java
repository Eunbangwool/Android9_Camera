package com.quectel.qcarapi.image;

/* JADX INFO: loaded from: classes.dex */
public class QCarJpeg {
    public static int QUEC_BITSTREAM = 5;
    public static int QUEC_H1V1 = 3;
    public static int QUEC_H1V2 = 2;
    public static int QUEC_H2V1 = 1;
    public static int QUEC_H2V2 = 0;
    public static int QUEC_IYUV = 2;
    public static int QUEC_MONOCHROME = 4;
    public static int QUEC_YCBCR_SP = 1;
    public static int QUEC_YCRCB_SP = 0;
    public static int QUEC_YUV2 = 3;

    public static native int jpegEncoderToFile(byte[] bArr, int i, int i2, int i3, int i4, byte[] bArr2, int i5, int i6);
}
