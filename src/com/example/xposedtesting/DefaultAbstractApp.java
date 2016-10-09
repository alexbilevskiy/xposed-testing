package com.example.xposedtesting;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Created by Alex on 07/09/2016.
 */
public abstract class DefaultAbstractApp {

    public abstract String getName();

    public abstract void prepare(final XC_LoadPackage.LoadPackageParam lpparam);
}
