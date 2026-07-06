package com.quectel.qcarapi.mediautil;

/* JADX INFO: loaded from: classes.dex */
public class QCarProtoBbRtp {
    public static int DVR_FRAME_TYPE_AAC = 5;
    public static int DVR_FRAME_TYPE_ADPCM = 100;
    public static int DVR_FRAME_TYPE_BIN = 3;
    public static int DVR_FRAME_TYPE_G711A = 21;
    public static int DVR_FRAME_TYPE_G711U = 22;
    public static int DVR_FRAME_TYPE_G726_16K = 10;
    public static int DVR_FRAME_TYPE_G726_40K = 2;
    public static int DVR_FRAME_TYPE_H264I = 0;
    public static int DVR_FRAME_TYPE_H264P = 1;
    public static int DVR_FRAME_TYPE_H265I = 80;
    public static int DVR_FRAME_TYPE_H265P = 81;
    public static int DVR_FRAME_TYPE_NONE = 127;
    public static int DVR_FRAME_TYPE_PCM = 4;
    public static int DVR_FRAME_TYPE_RGB = 50;
    public static int DVR_FRAME_TYPE_TEXT = 72;
    public static int DVR_FRAME_TYPE_YUV = 40;

    public static native int buildPkg(long j, byte[] bArr, int i, int i2, long j2, byte[] bArr2, int i3);

    public static native long create(String str, int i);

    public static native int release(long j);
}
