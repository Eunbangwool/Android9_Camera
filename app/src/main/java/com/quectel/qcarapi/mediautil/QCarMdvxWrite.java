package com.quectel.qcarapi.mediautil;

/* JADX INFO: loaded from: classes.dex */
public class QCarMdvxWrite {
    public static int ALM_SUBTYPE_GSENSOR_HIT = 3;
    public static int ALM_SUBTYPE_GSENSOR_TILT_ANGLE = 4;
    public static int ALM_SUBTYPE_GSENSOR_X = 0;
    public static int ALM_SUBTYPE_GSENSOR_Y = 1;
    public static int ALM_SUBTYPE_GSENSOR_Z = 2;
    public static int ALM_TYPE_HIT = 19;
    public static int ALM_TYPE_IO_INPUT = 0;
    public static int ALM_TYPE_SPEED = 2;
    public static int ALM_TYPE_TEMPERATURE = 3;
    public static int ALM_TYPE_TILT_ANGLE = 25;
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
    public static int STREAM_TYPE_MAIN = 0;
    public static int STREAM_TYPE_MIRROR = 2;
    public static int STREAM_TYPE_SUBJECT = 1;

    public static class BinAlarmParam {
        public int alarm_type;
        public int enable;
        public byte[] extdata = new byte[128];
        public int reserve;
        public int sub_type;
    }

    public static class BinGpsParam {
        public char cDirectionLatitude;
        public char cDirectionLongitude;
        public char cGpsStatus;
        public char cLatitudeCent;
        public char cLatitudeDegree;
        public char cLongitudeCent;
        public char cLongitudeDegree;
        public char cSpeedUnit;
        public long lLatitudeSec;
        public long lLongitudeSec;
        public byte[] reserve = new byte[4];
        public int usGpsAngle;
        public int usSpeed;
    }

    public static class BinGsensorParam {
        public short unit;
        public short x;
        public short y;
        public short z;
    }

    public static class BinStatusParam {
        public byte AccStatus;
        public short AlarmIn;
        public short BoardTemperature;
        public byte Brake;
        public short HddTemperature;
        public short PowerCarKey;
        public short PowerInput;
        public long Pulse;
        public byte SpeedUnit;
        public byte SpeedValid;
        public byte State;
        public byte TempUnit;
        public byte TempValid;
        public short Temperature;
        public byte Winker;
        public short[] ad = new short[2];
        public short speed;
        public short sys_voltage;
    }

    public static native int close(long j, int i, int i2, int i3);

    public static native long create();

    public static native long getTime(long j);

    public static native int open(long j, String str, int i, String str2, String str3, String str4, int i2, byte[] bArr, byte[] bArr2);

    public static native int writeBinAlarmFrame(long j, BinAlarmParam binAlarmParam);

    public static native int writeBinGpsFrame(long j, BinGpsParam binGpsParam);

    public static native int writeBinGsensorFrame(long j, BinGsensorParam binGsensorParam);

    public static native int writeBinStatusFrame(long j, BinStatusParam binStatusParam);

    public static native int writeFrame(long j, int i, int i2, int i3, long j2, byte[] bArr);

    public static native int writeSync(long j, long j2);
}
