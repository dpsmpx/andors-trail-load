package com.gpl.rpg.AndorsTrail.util;

import android.util.Log;

import com.gpl.rpg.AndorsTrail.AndorsTrailApplication;

public final class L {
	private static final String TAG = "AndorsTrail";

	public static void debug(String s) {
		if (AndorsTrailApplication.DEVELOPMENT_DEBUGMESSAGES) {
			print(Log.DEBUG, s, null);
		}
	}

	public static void info(String s) {
		if (AndorsTrailApplication.DEVELOPMENT_DEBUGMESSAGES) {
			print(Log.INFO, s, null);
		}
	}

	public static void warn(String s) {
		if (AndorsTrailApplication.DEVELOPMENT_DEBUGMESSAGES) {
			print(Log.WARN, s, null);
		}
	}

	public static void error(String s) {
		if (AndorsTrailApplication.DEVELOPMENT_DEBUGMESSAGES) {
			print(Log.ERROR, s, null);
		}
	}

	public static void error(String s, Throwable t) {
		if (AndorsTrailApplication.DEVELOPMENT_DEBUGMESSAGES) {
			print(Log.ERROR, s, t);
		}
	}

	public static void log(String s) {
		warn(s);
	}

	private static void print(int priority, String s, Throwable t) {
		try {
			Log.println(priority, TAG, t == null ? s : s + '\n' + Log.getStackTraceString(t));
		} catch (RuntimeException e) {
			// android.util.Log is not available in JVM unit tests.
			System.err.println(TAG + ": " + s);
			if (t != null) t.printStackTrace(System.err);
		}
	}
}
