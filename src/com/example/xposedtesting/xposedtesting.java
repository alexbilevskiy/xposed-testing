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

import java.lang.reflect.Method;

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
                prepareApp(lpparam, new Ingress());
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
