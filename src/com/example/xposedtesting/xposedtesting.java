package com.example.xposedtesting;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.URI;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

public class xposedtesting implements IXposedHookInitPackageResources, IXposedHookLoadPackage {

    Loggable logger;

    public xposedtesting() {
        this.logger = new Loggable(false);
    }

    public void handleInitPackageResources(InitPackageResourcesParam resparam) throws Throwable {
        if (resparam.packageName.equals("de.robv.android.xposed.installer")) {
            //resparam.res.setReplacement("de.robv.android.xposed.installer", "string", "welcome", "APPLEBLOOM");
        }
    }

    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        switch (lpparam.packageName) {
            case "com.nianticproject.ingress":
                //prepareApp(lpparam, new Ingress());
                Ingress I = new Ingress();
                I.prepare(lpparam);
                break;
            case "com.instagram.android":
//                logger.log("Unpinning INSTAGRAM");
//                findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
//                    @Override
//                    protected void afterHookedMethod(MethodHookParam param) {
//                        DefaultAbstractApp.unpinSsl(lpparam);
//                    }
//                });
//                logger.log("Unpinned INSTAGRAM");
                break;
            case "ru.yandex.market":
                logger.log("hooking MARKET");
                findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        final Class<?> net = findClass("ru.yandex.market.net.ContentVersionHelper", lpparam.classLoader);
                        hookAllMethods(net, "generateContentVersion", new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                final String[] array = (String[])param.args[0];
                                final StringBuilder sb = new StringBuilder();
                                final int length = array.length;
                                String s = null;
                                for (int i = 0; i < length; ++i) {
                                    s = array[i];
                                    sb.append(s).append('\t');
                                }
                                final String string = sb.toString();

                                logger.log("Hooked generateContentVersion: " + string);
                            }
                        });

                    }
                });
                logger.log("hooked MARKET");
            default:
                break;
        }
    }

    private void prepareApp(final XC_LoadPackage.LoadPackageParam lpparam, final DefaultAbstractApp hookerClass) {
        logger.log("Loaded " + lpparam.packageName);
        findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                logger.log("Preparing " + hookerClass.getClass().toString());
                hookerClass.prepare(lpparam);
                logger.log("Prepared " + hookerClass.getClass().toString());
            }
        });

    }
}
