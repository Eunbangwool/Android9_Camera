package com.quectel.qcarapi.stream;

import com.quectel.qcarapi.cb.IQCarAudioDataCB;
import com.quectel.qcarapi.util.QCarLog;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

/* JADX INFO: loaded from: classes.dex */
public class QCarAudio {
    public static int CHANNEL_BYTE_NUMS_ONE_TIME = 2048;
    public static int QUEC_BYTEORDER_BIGENDIAN = 1;
    public static int QUEC_BYTEORDER_LITTLEENDIAN = 2;
    public static int QUEC_PCMSAMPLEFORMAT_FIXED_16 = 2;
    public static int QUEC_PCMSAMPLEFORMAT_FIXED_32 = 3;
    public static int QUEC_PCMSAMPLEFORMAT_FIXED_8 = 1;
    public static int QUEC_SAMPLINGRATE_16 = 2;
    public static int QUEC_SAMPLINGRATE_24 = 3;
    public static int QUEC_SAMPLINGRATE_32 = 4;
    public static int QUEC_SAMPLINGRATE_44_1 = 5;
    public static int QUEC_SAMPLINGRATE_48 = 6;
    public static int QUEC_SAMPLINGRATE_64 = 7;
    public static int QUEC_SAMPLINGRATE_8 = 1;
    public static int QUEC_SPEAKER_BACK_LEFT = 16;
    public static int QUEC_SPEAKER_BACK_RIGHT = 32;
    public static int QUEC_SPEAKER_FRONT_LEFT = 1;
    public static int QUEC_SPEAKER_FRONT_RIGHT = 2;
    public static final String TAG = "QCarAudio";
    public static boolean mIsAudioMute = false;
    public static QCarAudio qcAudioInstance;
    public Map<Integer, Vector<byte[]>> channelVectorMap;
    public long mNativeContext;
    public int MAX_AUDIO_CACHE_DATA_SIZE = 300;
    public int splitChannel = -1;
    public int splitByteNums = -1;
    public Map<Integer, byte[]> channelBytesMap = null;
    public IQCarAudioDataCB carAudioDataCB = null;
    public int MAX_CHANNEL_NUM = 15;

    public QCarAudio() {
        native_setup();
        this.channelVectorMap = new ConcurrentHashMap();
    }

    public static synchronized QCarAudio getInstance() {
        if (qcAudioInstance == null) {
            qcAudioInstance = new QCarAudio();
        }
        return qcAudioInstance;
    }

    private native void native_release();

    private native void native_setup();

    private native void releaseRecorder();

    public void closeAudioStream(int i) {
        this.channelVectorMap.remove(Integer.valueOf(i));
        Map<Integer, byte[]> map = this.channelBytesMap;
        if (map != null) {
            map.remove(Integer.valueOf(i));
        }
        QCarLog.i(QCarLog.LOG_MODULE_RECORDER, TAG, "remove audio " + i + " channel stream");
    }

    public native int configureAudioParam(int i, int i2, int i3, int i4, int i5);

    public byte[] getAudioBuffer(int i) {
        Vector<byte[]> vector = this.channelVectorMap.get(Integer.valueOf(i));
        if (vector == null || vector.isEmpty()) {
            return null;
        }
        return vector.remove(0);
    }

    public void onAudioStream(byte[] bArr, int i, long j) {
        int i2;
        if (mIsAudioMute) {
            Arrays.fill(bArr, (byte) 0);
        }
        Iterator<Integer> it = this.channelVectorMap.keySet().iterator();
        int i3 = this.splitChannel;
        if (i3 == -1 || (i2 = this.splitByteNums) == -1) {
            while (it.hasNext()) {
                Integer next = it.next();
                Vector<byte[]> vector = this.channelVectorMap.get(next);
                if (vector != null && vector.size() < this.MAX_AUDIO_CACHE_DATA_SIZE) {
                    vector.add(Arrays.copyOf(bArr, bArr.length));
                }
                IQCarAudioDataCB iQCarAudioDataCB = this.carAudioDataCB;
                if (iQCarAudioDataCB != null) {
                    iQCarAudioDataCB.onAudioChannelStream(next.intValue(), bArr, bArr.length);
                }
            }
            return;
        }
        int i4 = i3 * i2;
        if (i2 == 1) {
            int i5 = 0;
            for (int i6 = 0; i6 < i; i6 += i4) {
                for (Integer numValueOf = 0; numValueOf.intValue() < this.splitChannel; numValueOf = Integer.valueOf(numValueOf.intValue() + 1)) {
                    try {
                        if (this.channelBytesMap.get(numValueOf) != null && this.channelBytesMap.get(numValueOf).length > 0) {
                            this.channelBytesMap.get(numValueOf)[i5] = 0;
                            this.channelBytesMap.get(numValueOf)[i5 + 1] = bArr[(numValueOf.intValue() * this.splitByteNums) + i6];
                        }
                    } catch (Exception unused) {
                        QCarLog.i(QCarLog.LOG_MODULE_RECORDER, TAG, "harrison||pBuf exception");
                    }
                }
                i5 += 2;
            }
        } else {
            int i7 = 0;
            for (int i8 = 0; i8 < i; i8 += i4) {
                for (Integer numValueOf2 = 0; numValueOf2.intValue() < this.splitChannel; numValueOf2 = Integer.valueOf(numValueOf2.intValue() + 1)) {
                    if (this.channelBytesMap.get(numValueOf2) != null) {
                        int iIntValue = (numValueOf2.intValue() * this.splitByteNums) + i8;
                        byte[] bArr2 = this.channelBytesMap.get(numValueOf2);
                        int i9 = this.splitByteNums;
                        System.arraycopy(bArr, iIntValue, bArr2, i7 * i9, i9);
                    }
                }
                i7++;
            }
        }
        while (it.hasNext()) {
            Integer next2 = it.next();
            Vector vector2 = this.channelVectorMap.get(next2);
            if (vector2 != null && vector2.size() < this.MAX_AUDIO_CACHE_DATA_SIZE) {
                vector2.add(this.channelBytesMap.get(next2));
            }
            if (this.carAudioDataCB != null && this.channelBytesMap.get(next2) != null) {
                this.carAudioDataCB.onAudioChannelStream(next2.intValue(), this.channelBytesMap.get(next2), (bArr.length * 2) / this.splitChannel);
            }
        }
    }

    public void registerQCarAudioDataCB(IQCarAudioDataCB iQCarAudioDataCB) {
        this.carAudioDataCB = iQCarAudioDataCB;
    }

    public synchronized void release() {
        releaseRecorder();
        native_release();
        qcAudioInstance = null;
    }

    public void setMute(boolean z) {
        mIsAudioMute = z;
    }

    public void setSplitChannelAndByteNums(int i, int i2) {
        QCarLog.i(QCarLog.LOG_MODULE_RECORDER, TAG, " splitChannel = " + i + " splitByteNums = " + i2);
        this.splitChannel = i;
        this.splitByteNums = i2;
        this.channelBytesMap = new ConcurrentHashMap();
    }

    public void startAudioStream(int i) {
        if (i < 0 || i > this.MAX_CHANNEL_NUM) {
            return;
        }
        this.channelVectorMap.put(Integer.valueOf(i), new Vector<>());
        Map<Integer, byte[]> map = this.channelBytesMap;
        if (map != null) {
            map.put(Integer.valueOf(i), new byte[CHANNEL_BYTE_NUMS_ONE_TIME]);
        }
        QCarLog.i(QCarLog.LOG_MODULE_RECORDER, TAG, "start audio " + i + " channel stream");
    }

    public native void startRecorder();

    public native void stopRecorder();
}
