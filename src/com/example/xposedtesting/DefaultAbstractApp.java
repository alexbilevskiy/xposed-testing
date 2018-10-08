package com.example.xposedtesting;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.net.Socket;
import java.security.KeyStore;
import java.security.SecureRandom;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookConstructor;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.newInstance;
import static de.robv.android.xposed.XposedHelpers.setObjectField;

import java.lang.reflect.Method;

import static de.robv.android.xposed.XposedHelpers.getParameterTypes;

public abstract class DefaultAbstractApp {

    protected Loggable logger;
    XSharedPreferences pref;
    boolean debug;
    static String depthKey = "xtst-depth";

    public DefaultAbstractApp() {
        this.pref = new XSharedPreferences("com.example.xposedtesting", "user_settings");
        this.debug = pref.getBoolean("debug", false);
        this.logger = new Loggable(debug);
    }

    public abstract void prepare(final XC_LoadPackage.LoadPackageParam lpparam);

    protected void hookAllMethods(Class<?> clazz) {
        this.hookAllMethods(clazz, true, false, false);
    }

    protected void hookAllMethods(Class<?> clazz, boolean hookDeclaringClass, boolean hookResult, final boolean objectToString) {
        String clazzName = clazz.toString();
        logger.log("[H] Preparing hooks for " + clazzName);
        for (Method m : clazz.getMethods()) {
            String methodName = m.getName();
            Class<?>[] types = getParameterTypes(m);
            Class<?> declaringClass = m.getDeclaringClass();
            if (declaringClass.equals(Object.class)) {

                continue;
            }
            if (!hookDeclaringClass) {
                declaringClass = clazz;
            }
            try {
                logger.log("[H] would hook constructors of " + declaringClass.toString());
                XposedBridge.hookAllConstructors(clazz, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String thisClass;
                        if (param.thisObject == null) {

                            thisClass = "STATIC " + param.method.getDeclaringClass().toString();
                        } else {
                            thisClass = param.thisObject.getClass().toString();
                        }
                        String methodName = param.method.getName();
                        if (param.args == null) {
                            logger.log("[C] " + thisClass + ", method: `" + methodName + "`, arguments (0)");

                            return;
                        }
                        String params = "[";
                        for (Object arg :
                                param.args) {
                            if (arg == null) {
                                params += " null |";

                                continue;
                            }
                            params += " " + arg.getClass().toString() + " `" + arg.toString() + "` |";
                        }
                        params = params.substring(0, params.length() - 1) + "]";
                        logger.log("[C] " + thisClass + ", method: `" + methodName + "`, arguments (" + param.args.length + ") " + params);
                    }
                });
            } catch (Throwable e) {
                logger.log("Faied hookig constructors for " + clazzName + ": " + e.getMessage());
            }
            if (hookResult) {
                try {
                    logger.log("[H] would hook method `" + m.getName() + "` of " + declaringClass.toString());
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            String thisClass, result;
                            if (param.thisObject == null) {

                                thisClass = "STATIC " + param.method.getDeclaringClass().toString();
                            } else {
                                thisClass = param.thisObject.getClass().toString();
                            }
                            Class resultClass = param.getResult().getClass();
                            if (objectToString == true || resultClass == Float.class || resultClass == Integer.class || resultClass == String.class || resultClass == Boolean.class) {
                                result = "`" + param.getResult().toString() + "`";
                            } else {
                                result = resultClass.toString();
                            }

                            String methodName = param.method.getName();
                            if (param.args == null) {
                                logger.log("[M] " + thisClass + ", method: `" + methodName + "`, arguments (0)" + ", RESULT: " + result);

                                return;
                            }
                            String params = "[";
                            for (Object arg :
                                    param.args) {
                                String value = "unknown";
                                if (arg == null) {
                                    value = "null";
                                } else {
                                    Class argClass = arg.getClass();
                                    if (objectToString == true || argClass == Float.class || argClass == Integer.class || argClass == String.class || argClass == Boolean.class) {
                                        value = "`" + arg.toString() + "`";
                                    } else {
                                        value = argClass.toString();
                                    }
                                }
                                params += " " + value + " |";
                            }
                            params = params.substring(0, params.length() - 1) + "]";
                            logger.log("[M] " + thisClass + ", method: `" + methodName + "`, arguments (" + param.args.length + ") " + params + ", RESULT: " + result);
                        }
                    });
                } catch (Throwable e) {
                    logger.log("Faied hookig method " + methodName + " for " + clazzName + ": " + e.getMessage());
                }
            } else {
                try {
                    logger.log("[H] would hook method `" + m.getName() + "` of " + declaringClass.toString());
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String thisClass;
                            if (param.thisObject == null) {

                                thisClass = "STATIC " + param.method.getDeclaringClass().toString();
                            } else {
                                thisClass = param.thisObject.getClass().toString();
                            }
                            String methodName = param.method.getName();
                            if (param.args == null) {
                                logger.log("[M] " + thisClass + ", method: `" + methodName + "`, arguments (0)");

                                return;
                            }
                            String params = "[";
                            for (Object arg :
                                    param.args) {
                                String value = "unknown";
                                if (arg == null) {
                                    value = "null";
                                } else {
                                    Class argClass = arg.getClass();
                                    if (objectToString == true || argClass == Float.class || argClass == Integer.class || argClass == String.class || argClass == Boolean.class) {
                                        value = "`" + arg.toString() + "`";
                                    } else {
                                        value = argClass.toString();
                                    }
                                }
                                params += " " + value + " |";
                            }
                            params = params.substring(0, params.length() - 1) + "]";
                            logger.log("[M] " + thisClass + ", method: `" + methodName + "`, arguments (" + param.args.length + ") " + params);
                        }
                    });
                } catch (Throwable e) {
                    logger.log("Faied hookig method " + methodName + " for " + clazzName + ": " + e.getMessage());
                }
            }

        }

    }

    protected void printStackTrace() {
        for (StackTraceElement ste : new Throwable().getStackTrace()) {
            logger.log("[TRACE] " + ste);
        }
    }
}

