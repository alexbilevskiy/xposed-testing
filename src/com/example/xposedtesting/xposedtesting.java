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
            case ("com.nianticproject.ingress"):
                prepareApp(lpparam, new Ingress());
                break;
            case ("com.mishiranu.dashchan"):
                logger.log("Dashchan: @TODO");
                break;
            case ("com.microsoft.rdc.android"):
                //prepareRdClient(lpparam);
            default:
                break;

        }
    }

    private void prepareRdClient(final XC_LoadPackage.LoadPackageParam lpparam) {
        logger.log("Loaded " + lpparam.packageName);
        findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                final Class<?> b = findClass("com.microsoft.a3rdc.desktop.b", lpparam.classLoader);
                findAndHookMethod(b, "a", Activity.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Activity activity = (Activity) param.args[0];
                            Rect size = new Rect();
                            Rect prevSize = (Rect) param.getResult();
                            activity.getWindowManager().getDefaultDisplay().getRectSize(size);
                            if (activity.getWindowManager().getDefaultDisplay().getRotation() == Surface.ROTATION_0 ||
                                    activity.getWindowManager().getDefaultDisplay().getRotation() == Surface.ROTATION_180) {
                                size.left = 0;
                                size.top = 0;
                                size.right = 720;
                                size.bottom = 1280;
                            } else if (activity.getWindowManager().getDefaultDisplay().getRotation() == Surface.ROTATION_90 ||
                                    activity.getWindowManager().getDefaultDisplay().getRotation() == Surface.ROTATION_270) {
                                size.left = 0;
                                size.top = 0;
                                size.right = 1280;
                                size.bottom = 720;
                            }
                            logger.log("Replace resolution " + prevSize.top + " " + prevSize.left + " " + prevSize.bottom + " " + prevSize.right + " WITH " + size.top + " " + size.left + " " + size.bottom + " " + size.right);
                            param.setResult(size);
                        } catch (Throwable e) {
                            logger.log("RD exception" + e.getMessage());
                        }
                    }
                });
            }
        });

    }


    private void prepareApp(final XC_LoadPackage.LoadPackageParam lpparam, final DefaultAbstractApp hookerClass) {
        logger.log("Loaded " + lpparam.packageName);
        findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                logger.log("Preparing " + hookerClass.getName());
                hookerClass.prepare(lpparam);
                logger.log("Prepared " + hookerClass.getName());
            }
        });

    }
}
