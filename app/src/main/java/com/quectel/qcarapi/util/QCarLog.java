package com.quectel.qcarapi.util;

import android.util.Log;

/* JADX INFO: loaded from: classes.dex */
public class QCarLog {
    public static int LOG_MODULE_APP = 1024;
    public static int LOG_MODULE_COLLISION = 4;
    public static int LOG_MODULE_RECORDER = 1;
    public static int LOG_MODULE_TAK_PIC = 2;
    public static int moduleLogLevel = 0;
    public static int tagLogLevel = 4;

    public static final void d(int i, String str, Object... objArr) {
        log(i, str, 3, null, objArr);
    }

    public static final void e(int i, String str, Throwable th, Object... objArr) {
        log(i, str, 6, th, objArr);
    }

    public static final void e(int i, String str, Object... objArr) {
        log(i, str, 6, null, objArr);
    }

    public static int getModuleLogLevel() {
        return moduleLogLevel;
    }

    public static int getTagLogLevel() {
        return tagLogLevel;
    }

    public static final void i(int i, String str, Object... objArr) {
        log(i, str, 4, null, objArr);
    }

    public static boolean isLoggerable(int i, String str, int i2) {
        return (i & moduleLogLevel) != 0 || i2 >= tagLogLevel;
    }

    public static final void log(int i, String str, int i2, Throwable th, Object... objArr) {
        String string;
        if (objArr != null && isLoggerable(i, str, i2)) {
            if (objArr.length == 1) {
                string = objArr[0] == null ? null : objArr[0].toString();
            } else {
                StringBuilder sb = new StringBuilder();
                for (Object obj : objArr) {
                    sb.append(obj);
                }
                string = sb.toString();
            }
            if (3 == i2) {
                Log.d(str, string, th);
                return;
            }
            if (6 == i2) {
                Log.e(str, string, th);
            } else if (5 == i2) {
                Log.w(str, string, th);
            } else if (4 == i2) {
                Log.i(str, string, th);
            }
        }
    }

    public static void setModuleLogLevel(int i) {
        moduleLogLevel = i;
    }

    public static void setTagLogLevel(int i) {
        tagLogLevel = i;
    }

    public static final void w(int i, String str, Throwable th, Object... objArr) {
        log(i, str, 5, th, objArr);
    }

    public static final void w(int i, String str, Object... objArr) {
        log(i, str, 5, null, objArr);
    }
}
