package com.example.xposedtesting;

import de.robv.android.xposed.XposedBridge;

public class Loggable {
    public void log(String msg) {
        XposedBridge.log("xfbt: " + msg);
    }
}
