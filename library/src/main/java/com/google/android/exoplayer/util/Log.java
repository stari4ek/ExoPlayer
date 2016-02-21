/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Added by Michael Sotnikov
 */
package com.google.android.exoplayer.util;


/**
 * We'd like to route all logging
 */
public class Log {

  public interface Logger {
    int v(String tag, String msg);
    int v(String tag, String msg, Throwable tr);
    int d(String tag, String msg);
    int d(String tag, String msg, Throwable tr);
    int i(String tag, String msg);
    int i(String tag, String msg, Throwable tr);
    int w(String tag, String msg);
    int w(String tag, String msg, Throwable tr);
    int w(String tag, Throwable tr);
    int e(String tag, String msg);
    int e(String tag, String msg, Throwable tr);
    int println(int priority, String tag, String msg);
  }

  final static class LogcatLogger implements Logger {
    public int v(String tag, String msg) {
      return android.util.Log.v(tag, msg);
    }
    public int v(String tag, String msg, Throwable tr) {
      return android.util.Log.v(tag, msg, tr);
    }
    public int d(String tag, String msg) {
      return android.util.Log.d(tag, msg);
    }
    public int d(String tag, String msg, Throwable tr) {
      return android.util.Log.d(tag, msg, tr);
    }
    public int i(String tag, String msg) {
      return android.util.Log.i(tag, msg);
    }
    public int i(String tag, String msg, Throwable tr) {
      return android.util.Log.i(tag, msg, tr);
    }
    public int w(String tag, String msg) {
      return android.util.Log.w(tag, msg);
    }
    public int w(String tag, String msg, Throwable tr) {
      return android.util.Log.w(tag, msg, tr);
    }
    public int w(String tag, Throwable tr) {
      return android.util.Log.w(tag, tr);
    }
    public int e(String tag, String msg) {
      return android.util.Log.e(tag, msg);
    }
    public int e(String tag, String msg, Throwable tr) {
      return android.util.Log.e(tag, msg, tr);
    }
    public int println(int priority, String tag, String msg) {
      return android.util.Log.println(priority, tag, msg);
    }
  }

  // route to standard logcat by default
  private static Logger sLogger = new LogcatLogger();


  private Log() {
  }

  public static Logger setLogger(Logger newLogger) {
    Assertions.checkNotNull(newLogger);
    Logger oldLogger = sLogger;
    sLogger = newLogger;
    return oldLogger;
  }


  public static int v(String tag, String msg) {
    return sLogger.v(tag, msg);
  }
  public static int v(String tag, String msg, Throwable tr) {
    return sLogger.v(tag, msg, tr);
  }
  public static int d(String tag, String msg) {
    return sLogger.d(tag, msg);
  }
  public static int d(String tag, String msg, Throwable tr) {
    return sLogger.d(tag, msg, tr);
  }
  public static int i(String tag, String msg) {
    return sLogger.i(tag, msg);
  }
  public static int i(String tag, String msg, Throwable tr) {
    return sLogger.i(tag, msg, tr);
  }
  public static int w(String tag, String msg) {
    return sLogger.w(tag, msg);
  }
  public static int w(String tag, String msg, Throwable tr) {
    return sLogger.w(tag, msg, tr);
  }
  public static int w(String tag, Throwable tr) {
    return sLogger.w(tag, tr);
  }
  public static int e(String tag, String msg) {
    return sLogger.e(tag, msg);
  }
  public static int e(String tag, String msg, Throwable tr) {
    return sLogger.e(tag, msg, tr);
  }
  public static int println(int priority, String tag, String msg) {
    return sLogger.println(priority, tag, msg);
  }
}
