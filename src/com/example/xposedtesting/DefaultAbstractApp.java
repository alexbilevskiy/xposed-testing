package com.example.xposedtesting;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import org.apache.http.conn.scheme.HostNameResolver;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.scheme.Scheme;

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
                            if(arg == null) {
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
                            if(arg == null) {
                                value = "null";
                            } else {
                                Class argClass = arg.getClass();
                                if (argClass == Float.class || argClass == Integer.class || argClass == String.class || argClass == boolean.class) {
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

    protected void printStackTrace() {
        for (StackTraceElement ste : new Throwable().getStackTrace()) {
            logger.log("[TRACE] " + ste);
        }
    }

    public static void unpinSsl(final XC_LoadPackage.LoadPackageParam loadPackageParam) {
        // --- Java Secure Socket Extension (JSSE) ---

        //TrustManagerFactory.getTrustManagers >> EmptyTrustManager
        try {
            findAndHookMethod("javax.net.ssl.TrustManagerFactory", loadPackageParam.classLoader, "getTrustManagers", new XC_MethodHook() {

                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {

                    TrustManager[] tms = EmptyTrustManager.getInstance();
                    param.setResult(tms);
                }
            });
        } catch (Error e) {
            XposedBridge.log("Unpinning_error: " + e.getMessage());
        }
        //SSLContext.init >> (null,EmptyTrustManager,null)
        try {
            findAndHookMethod("javax.net.ssl.SSLContext", loadPackageParam.classLoader, "init", KeyManager[].class, TrustManager[].class, SecureRandom.class, new XC_MethodHook() {

                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.args[0] = null;
                    param.args[1] = EmptyTrustManager.getInstance();
                    param.args[2] = null;
                }
            });
        } catch (Error e) {
            XposedBridge.log("Unpinning_error: " + e.getMessage());
        }
        //HttpsURLConnection.setSSLSocketFactory >> new SSLSocketFactory
        try {
            findAndHookMethod("javax.net.ssl.HttpsURLConnection", loadPackageParam.classLoader, "setSSLSocketFactory", javax.net.ssl.SSLSocketFactory.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.args[0] = newInstance(javax.net.ssl.SSLSocketFactory.class);
                }
            });
        } catch (Error e) {
            XposedBridge.log("Unpinning_error: " + e.getMessage());
        }
        // --- APACHE ---

        //SchemeRegistry.register >> new Scheme
        findAndHookMethod("org.apache.http.conn.scheme.SchemeRegistry", loadPackageParam.classLoader, "register", org.apache.http.conn.scheme.Scheme.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Scheme scheme = (Scheme) param.args[0];
                if (scheme.getName() == "https") {
                    param.args[0] = new Scheme("https", SSLSocketFactory.getSocketFactory(), 443);
                }
            }
        });

        //HttpsURLConnection.setDefaultHostnameVerifier >> SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER
        try {
            findAndHookMethod("org.apache.http.conn.ssl.HttpsURLConnection", loadPackageParam.classLoader, "setDefaultHostnameVerifier",
                    HostnameVerifier.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.args[0] = SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;
                        }
                    });
        } catch (Error e) {
            XposedBridge.log("Unpinning_error: " + e.getMessage());
        }
        //HttpsURLConnection.setHostnameVerifier >> SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER
        try {
            findAndHookMethod("org.apache.http.conn.ssl.HttpsURLConnection", loadPackageParam.classLoader, "setHostnameVerifier", HostnameVerifier.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.args[0] = SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;
                        }
                    });
        } catch (Error e) {
            XposedBridge.log("Unpinning_error: " + e.getMessage());
        }

        //SSLSocketFactory.getSocketFactory >> new SSLSocketFactory
        try {
            findAndHookMethod("org.apache.http.conn.ssl.SSLSocketFactory", loadPackageParam.classLoader, "getSocketFactory", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult((SSLSocketFactory) newInstance(SSLSocketFactory.class));
                }
            });
        } catch (Error e) {
            XposedBridge.log("Unpinning_error: " + e.getMessage());
        }

        //SSLSocketFactory(...) >> SSLSocketFactory(...){ new EmptyTrustManager()}
        try {
            Class<?> sslSocketFactory = findClass("org.apache.http.conn.ssl.SSLSocketFactory", loadPackageParam.classLoader);
            findAndHookConstructor(sslSocketFactory, String.class, KeyStore.class, String.class, KeyStore.class,
                    SecureRandom.class, HostNameResolver.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                            String algorithm = (String) param.args[0];
                            KeyStore keystore = (KeyStore) param.args[1];
                            String keystorePassword = (String) param.args[2];
                            SecureRandom random = (SecureRandom) param.args[4];

                            KeyManager[] keymanagers = null;
                            TrustManager[] trustmanagers;

                            if (keystore != null) {
                                keymanagers = (KeyManager[]) callStaticMethod(SSLSocketFactory.class, "createKeyManagers", keystore, keystorePassword);
                            }

                            trustmanagers = new TrustManager[]{new EmptyTrustManager()};

                            setObjectField(param.thisObject, "sslcontext", SSLContext.getInstance(algorithm));
                            callMethod(getObjectField(param.thisObject, "sslcontext"), "init", keymanagers, trustmanagers, random);
                            setObjectField(param.thisObject, "socketfactory", callMethod(getObjectField(param.thisObject, "sslcontext"), "getSocketFactory"));
                        }

                    });

        } catch (Error e) {
            XposedBridge.log("Unpinning_error: " + e.getMessage());
        }
        //SSLSocketFactory.isSecure >> true
        try {
            findAndHookMethod("org.apache.http.conn.ssl.SSLSocketFactory", loadPackageParam.classLoader, "isSecure", Socket.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(true);
                }
            });
        } catch (Error e) {
            XposedBridge.log("Unpinning_error: " + e.getMessage());
        }

        ///OKHTTP
        try {
            findAndHookMethod("okhttp3.CertificatePinner", loadPackageParam.classLoader, "findMatchingPins", String.class, new XC_MethodHook() {
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.args[0] = "";
                }
            });
        } catch (Error e) {
            XposedBridge.log("Unpinning_error: " + e.getMessage());
        }
    }
}

