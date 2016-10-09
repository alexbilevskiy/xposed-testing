package com.example.xposedtesting;

import de.robv.android.xposed.XposedBridge;

public class Loggable {

    boolean debug;

    public Loggable(boolean debug)
    {
        this.debug = debug;
    }
    public void log(String msg) {
        XposedBridge.log("xfbt: " + msg);
    }

    public void debugLog(String msg)
    {
        if (!this.debug) {
            return;
        }
        log("[DEBUG] " + msg);
    }
}
