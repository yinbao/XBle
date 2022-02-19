package com.xing.xblelibrary.utils;

import android.text.TextUtils;
import android.util.Log;

import com.xing.xblelibrary.BuildConfig;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


/**
 * 打印日志工具
 */
public class XBleL {
    /**
     * 是否开启log日志
     */
    private static boolean isLog = BuildConfig.DEBUG;
    private static String TAG = "XBle";


    /**
     * 日志初始化
     *
     * @param isShowLog 是否打印日志
     */
    public static void init(boolean isShowLog) {
        isLog = isShowLog;

    }

    /**
     * 详细日志
     */
    public static void v(String tag, String msg) {
        if (isLog) {
            logContent(tag,msg, 4);
        }
    }

    public static void v(String msg) {
        v(TAG, msg);
    }

    /**
     * 错误日志
     */
    public static void e(String tag, String msg) {
        if (isLog) {
            logContent(tag,msg, 1);
        }
    }


    public static void e(String msg) {
        e(TAG, msg);
    }

    /**
     * 警告日志
     */
    public static void w(String tag, String msg) {
        if (isLog) {
            logContent(tag,msg, 3);
        }
    }

    public static void w(String msg) {
        w(TAG, msg);
    }

    /**
     * 信息日志
     */
    public static void i(String tag, String msg) {
        if (isLog) {
            logContent(tag,msg, 0);
        }
    }



    public static void i(String msg) {
        i(TAG, msg);
    }

    /**
     * 调试日志
     */
    public static void d(String tag, String msg) {
        if (isLog) {
            logContent(tag,msg, 2);
        }
    }

    public static void d(String msg) {
        d(TAG, msg);
    }



    /**
     * @param tag   tag
     * @param msg   内容
     * @param level 0=i;1=e;2=d;3=w;其他=v;
     */
    private static void logContent(String tag, String msg, int level) {
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        int methodCount = 1;
        int stackOffset = getStackOffset(trace);
        if (methodCount + stackOffset > trace.length) {
            methodCount = trace.length - stackOffset - 1;
        }
        for (int i = methodCount; i > 0; i--) {
            int stackIndex = i + stackOffset;
            if (stackIndex >= trace.length) {
                continue;
            }
            StackTraceElement element = trace[stackIndex];
            if (TextUtils.isEmpty(tag)) {
                tag = getTag(element);
            }
            switch (level) {
                case 0:
                    Log.i(tag, getLogContent(msg, element));
                    break;
                case 1:
                    Log.e(tag, getLogContent(msg, element));
                    break;
                case 2:
                    Log.d(tag, getLogContent(msg, element));
                    break;
                case 3:
                    Log.w(tag, getLogContent(msg, element));
                    break;
                default:
                    Log.v(tag, msg);
                    break;
            }

        }
    }


    private static int getStackOffset(StackTraceElement[] trace) {
        for (int i = 2; i < trace.length; i++) {
            StackTraceElement e = trace[i];
            String name = e.getClassName();
            String l = XBleL.class.getName();
            if (!name.equals(l)) {
                return --i;
            }
        }
        return -1;
    }


    private static String getTag(StackTraceElement element) {
        String tag = "Tag_" + getModuleName(element.getClassName()) + "_" + getSimpleClassName(element.getClassName());
        return tag;
    }


    private static String getLogContent(String msg, StackTraceElement element) {
        StringBuilder builder = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String time = sdf.format(new Date());
        builder.append("\n").append("-------------------------------------------------------\n").append("|").append(time).append("\n").append("|").append(getSimpleClassName(element.getClassName()))
                .append(".").append(element.getMethodName()).append(" ").append(" (").append(element.getFileName()).append(":").append(element.getLineNumber()).append(")").append("\n").append("||==>")
                .append(msg).append("\n").append("-------------------------------------------------------\n");
        return builder.toString();
    }


    private static String getSimpleClassName(String name) {
        if (name == null)
            return "";
        int lastIndex = name.lastIndexOf(".");
        return name.substring(lastIndex + 1);
    }

    private static String getModuleName(String name) {
        if (name == null)
            return "";
        String[] split = name.split("\\.");
        String moduleName = "";
        if (split.length > 2) {
            moduleName = split[2];
        }
        return moduleName;
    }

}
