package info.mandarini.busbooking.logging;


import android.util.Log;
import info.mandarini.busbooking.BuildConfig;


public final class AppLogger {

    private AppLogger() {
    }

    // Solo DEBUG
    public static void debug(String tag, String message) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message);
        }
    }

    // Solo DEBUG
    public static void info(String tag, String message) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message);
        }
    }

    // Sempre, anche in RELEASE
    public static void message(String tag, String message) {
        Log.i(tag, message);
    }

    // Solo in DEBUG
    public static void warning(String tag, String message) {
        if (BuildConfig.DEBUG) {
          Log.w(tag, message);
        }
    }

    // Anche in release
    public static void warning(String tag, String message, Throwable error) {
        Log.w(tag, message,error);
    }

    // Sempre, anche in RELEASE
    public static void error(String tag, String message, Throwable error) {
        Log.e(tag, message, error);
    }

    public static void error(String tag, String message) {
        Log.e(tag, message);
    }
}
