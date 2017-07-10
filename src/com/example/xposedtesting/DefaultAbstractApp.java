package com.example.xposedtesting;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Method;

import static de.robv.android.xposed.XposedHelpers.getParameterTypes;

public abstract class DefaultAbstractApp {

    protected Loggable logger;
    XSharedPreferences pref;
    boolean debug;
    static String depthKey = "xtst-depth";

    public DefaultAbstractApp()
    {
        this.pref = new XSharedPreferences("com.example.xposedtesting", "user_settings");
        this.debug = pref.getBoolean("debug", false);
        this.logger = new Loggable(debug);
    }

    public abstract void prepare(final XC_LoadPackage.LoadPackageParam lpparam);

    protected void hookAllMethods(Class<?> clazz) {
        String clazzName = clazz.toString();
        logger.log("[H] Preparing hooks for " + clazzName);
        for (Method m : clazz.getMethods()) {
            String methodName = m.getName();
            Class<?>[] types = getParameterTypes(m);
            Class<?> declaringClass = m.getDeclaringClass();
            if (declaringClass.equals(Object.class)) {

                continue;
            }
            try {
                logger.log("[H] would hook method `" + m.getName() + "` of " + declaringClass.toString());
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String thisClass = param.thisObject.getClass().toString();
                        String methodName = param.method.getName();
                        if (param.args == null) {
                            logger.log("[M] " + thisClass + ", method: `" + methodName + "`, arguments (0)");

                            return;
                        }
                        logger.log("[M] " + thisClass + ", method: `" + methodName + "`, arguments (" + param.args.length + ")");
                    }
                });
            } catch (Throwable e) {
                logger.log("Faied hookig method " + methodName + " for " + clazzName + ": " + e.getMessage());
            }
        }

    }

    protected void printStackTrace() {
        for (StackTraceElement ste : new Throwable().getStackTrace()) {
            logger.log("[TRACE] " + ste);
        }
    }
}
