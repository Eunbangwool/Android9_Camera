package com.quectel.qcarapi.mediautil;

/* JADX INFO: loaded from: classes.dex */
public class QCarMdvxRead {
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

    public static class FrameInfo {
        public int channel;
        public int frameLen;
        public long framePts;
        public int frameType;
    }

    public static native FrameInfo backwardIFrame(long j, byte[] bArr);

    public static native int close(long j);

    public static native long create();

    public static native FrameInfo forwardIFrame(long j, byte[] bArr);

    public static native int open(long j, String str);

    public static native FrameInfo readFrame(long j, byte[] bArr);

    public static native int seek(long j, int i, long j2);
}
