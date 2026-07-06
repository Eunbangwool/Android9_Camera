package com.quectel.qcarapi.util;

/**
 * qcarcam 오류 콜백 인터페이스 (JNI 바인딩용 재구현).
 * 네이티브 라이브러리(libmmqcar_qcar_jni.so)가 이 인터페이스/메서드 시그니처로 콜백하므로
 * 패키지·클래스·메서드 시그니처가 원본과 정확히 일치해야 한다.
 */
public class QCarError {
    public interface OnErrorCB {
        void onError(int i, int i2, byte[] bArr, int i3, int i4);
    }
}
