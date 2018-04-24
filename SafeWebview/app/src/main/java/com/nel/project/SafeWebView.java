package com.nel.project;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.webkit.JsPromptResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Description :
 * CreateTime : 2018/4/24 9:18
 *
 * @author ningerlei@danale.com
 * @version <v1.0>
 */

public class SafeWebView extends WebView {

    private static final String VAR_ARG_PREFIX = "arg";

    private static final String MSG_PROMPT_HEADER = "MyApp";

    private static final String KEY_INTERFACE_NAME = "obj";

    private static final String KEY_FUNCTION_NAME = "func";

    private static final String KEY_ARG_ARRAY = "args";

    private static final List<String> mFilterMethods = Arrays.asList(new String[]{
            "getClass",
            "hashCode",
            "notify",
            "notifyAll",
            "equals",
            "toString",
            "wait"
    });

    /**
     * 缓存addJavascriptInterface的注册对象
     */
    private HashMap<String, Object> mJsInterfaceMap = new HashMap<>();

    /**
     * 缓存注入到JavaScript Context中的js脚本
     */
    private String mJsStringCache = null;

    public SafeWebView(Context context) {
        super(context);
        init();
    }

    public SafeWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SafeWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public SafeWebView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        safeSetting();
        removeUnsafeJavascriptImpl();
    }

    /**
     *
     */
    private void safeSetting() {
        getSettings().setSavePassword(false);
        getSettings().setAllowFileAccess(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            getSettings().setAllowFileAccessFromFileURLs(false);
            getSettings().setAllowUniversalAccessFromFileURLs(false);
        }
    }

    private boolean hasHoneycomb() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
    }

    private boolean hasJellyBeanMR1() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1;
    }

    private boolean removeUnsafeJavascriptImpl() {
        if (hasHoneycomb() && !hasJellyBeanMR1()) {
            super.removeJavascriptInterface("searchBoxJavaBridge_");
            super.removeJavascriptInterface("accessibility");
            super.removeJavascriptInterface("accessibilityTraversal");
            return true;
        }
        return false;
    }

    @Override
    public void setWebViewClient(WebViewClient client) {
        if (hasJellyBeanMR1()) {
            super.setWebViewClient(client);
        } else {
            if (client instanceof WebViewClientEx) {
                super.setWebViewClient(client);
            } else if (client == null) {
                super.setWebViewClient(client);
            } else {
                throw new IllegalArgumentException("the client must be a subclass of WebViewClientEx");
            }
        }
    }

    @Override
    public void setWebChromeClient(WebChromeClient client) {
        if (hasJellyBeanMR1()) {
            super.setWebChromeClient(client);
        } else {
            if (client instanceof WebChromeClientEx) {
                super.setWebChromeClient(client);
            } else if (client == null) {
                super.setWebChromeClient(client);
            } else {
                throw new IllegalArgumentException("the client must be a subclass of WebChromeClientEx");
            }
        }
    }

    @SuppressLint("JavascriptInterface")
    @Override
    public void addJavascriptInterface(Object object, String name) {
        if (TextUtils.isEmpty(name)) {
            return;
        }

        if (hasJellyBeanMR1()) {
            super.addJavascriptInterface(object, name);
        } else {
            mJsInterfaceMap.put(name, object);
        }
    }

    public void removeJavaScriptInterface(String interfaceName) {
        if (hasJellyBeanMR1()) {
            super.removeJavascriptInterface(interfaceName);
        } else {
            mJsInterfaceMap.remove(interfaceName);
            mJsStringCache = null;
            injectJavascriptInterfaces();
        }
    }

    private void injectJavascriptInterfaces(WebView webView) {
        if (webView instanceof SafeWebView) {
            injectJavascriptInterfaces();
        }
    }

    /**
     * 注入我们构造的 JS
     */
    private void injectJavascriptInterfaces() {
        if (!TextUtils.isEmpty(mJsStringCache)) {
            loadUrl(mJsStringCache);
            return;
        }

        //TODO
        mJsStringCache = genJavascriptInterfacesString();
        loadUrl(mJsStringCache);
    }

    private String genJavascriptInterfacesString() {
        if (mJsInterfaceMap.size() == 0) {
            return null;
        }

        Iterator<Map.Entry<String, Object>> iterator = mJsInterfaceMap.entrySet().iterator();
        StringBuilder script = new StringBuilder();
        script.append("javascript:(function JsAddJavascriptInterface_() {");

        //遍历带注入java对象，生成相应的js对象
        while (iterator.hasNext()) {
            Map.Entry<String, Object> next = iterator.next();
            String interfaceName = next.getKey();
            Object obj = next.getValue();
            //生成相应的js方法
            createJsMethod(interfaceName, obj, script);
        }
        script.append("})()");
        return script.toString();
    }

    /**
     * 根据待注入的java对象，生成js方法
     *
     * @param interfaceName 对象名
     * @param obj           待注入的java对象
     * @param script        js代码
     */
    private void createJsMethod(String interfaceName, Object obj, StringBuilder script) {
        if (TextUtils.isEmpty(interfaceName) || obj == null || script == null) {
            return;
        }

        Class<?> objClass = obj.getClass();
        script.append("if(typeof(window.").append(interfaceName).append(") != 'undefined') {");
        script.append("} else {");
        script.append("     window.").append(interfaceName).append("= {");

        Method[] methods = objClass.getMethods();

        for (Method method : methods) {
            String methodName = method.getName();
            if (filterMethods(methodName)) {
                continue;
            }

            script.append("         ").append(methodName).append(":function {");
            // 添加方法的参数
            int argCount = method.getParameterTypes().length;
            if (argCount > 0) {
                int maxCount = argCount - 1;
                for (int i = 0; i < maxCount; ++i) {
                    script.append(VAR_ARG_PREFIX).append(i).append(",");
                }
                script.append(VAR_ARG_PREFIX).append(argCount - 1);
            }

            script.append(") {");

            // Add implementation
            if (method.getReturnType() != void.class) {
                script.append("            return ").append("prompt('").append(MSG_PROMPT_HEADER).append("'+");
            } else {
                script.append("            prompt('").append(MSG_PROMPT_HEADER).append("'+");
            }

            // Begin JSON
            script.append("JSON.stringify({");
            script.append(KEY_INTERFACE_NAME).append(":'").append(interfaceName).append("',");
            script.append(KEY_FUNCTION_NAME).append(":'").append(methodName).append("',");
            script.append(KEY_ARG_ARRAY).append(":[");
            //  添加参数到JSON串中
            if (argCount > 0) {
                int max = argCount - 1;
                for (int i = 0; i < max; i++) {
                    script.append(VAR_ARG_PREFIX).append(i).append(",");
                }
                script.append(VAR_ARG_PREFIX).append(max);
            }

            // End JSON
            script.append("]})");
            // End prompt
            script.append(");");
            // End function
            script.append("        }, ");
        }

        // End of obj
        script.append("    };");
        // End of if or else
        script.append("}");
    }

    private boolean filterMethods(String methodName) {
        return mFilterMethods.contains(methodName);
    }

    private boolean invokeJSInterfaceMethod(JsPromptResult result, String interfaceName, String methodName, Object[] args) {
        boolean succeed = false;
        Object obj = mJsInterfaceMap.get(interfaceName);
        if (null == obj) {
            result.cancel();
            return false;
        }

        Class<?>[] parameterTypes = null;
        int count = 0;
        if (args != null) {
            count = args.length;
        }

        if (count > 0) {
            parameterTypes = new Class[count];
            for (int i = 0; i < count; ++i) {
                parameterTypes[i] = getClassFromJsonObject(args[i]);
            }
        }

        try {
            Method method = obj.getClass().getMethod(methodName, parameterTypes);
            Object returnObj = method.invoke(obj, args);
            boolean isVoid = returnObj == null || returnObj.getClass() == Void.class;
            String returnValue = isVoid ? "" : returnObj.toString();
            result.confirm(returnValue);
            succeed = true;

        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

        result.cancel();
        return succeed;
    }

    private Class<?> getClassFromJsonObject(Object obj) {
        Class<?> cls = obj.getClass();

        if (cls == Integer.class) {
            cls = Integer.TYPE;
        } else if (cls == Boolean.class) {
            cls = Boolean.TYPE;
        } else {
            cls = String.class;
        }

        return cls;
    }

    /**
     * 解析JavaScript调用prompt的参数message，提取出对象名、方法名，以及参数列表，再利用反射，调用java对象的方法。
     *
     * @param view
     * @param url
     * @param message      MyApp:{"obj":"jsInterface","func":"onButtonClick","args":["从JS中传递过来的文本！！！"]}
     * @param defaultValue
     * @param result
     * @return
     */
    private boolean handleJsInterface(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
        String prefix = MSG_PROMPT_HEADER;
        if (!message.startsWith(prefix)) {
            return false;
        }

        String jsonStr = message.substring(prefix.length());

        try {
            JSONObject jsonObject = new JSONObject(jsonStr);
            String interfaceName = jsonObject.getString(KEY_INTERFACE_NAME);
            String methodName = jsonObject.getString(KEY_FUNCTION_NAME);
            JSONArray argsArray = jsonObject.getJSONArray(KEY_ARG_ARRAY);

            Object[] args = null;

            if (null != argsArray) {
                int count = argsArray.length();
                if (count > 0) {
                    args = new Object[count];

                    for (int i = 0; i < count; i++) {
                        Object arg = argsArray.get(i);
                        if (!arg.toString().equals("null")) {
                            args[i] = arg;
                        } else {
                            args[i] = null;
                        }
                    }
                }
            }

            if (invokeJSInterfaceMethod(result, interfaceName, methodName, args)) {
                return true;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        result.cancel();
        return false;
    }

    class WebViewClientEx extends WebViewClient {
        @Override
        public void onLoadResource(WebView view, String url) {
            injectJavascriptInterfaces(view);
            super.onLoadResource(view, url);
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            injectJavascriptInterfaces(view);
            super.doUpdateVisitedHistory(view, url, isReload);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            injectJavascriptInterfaces(view);
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            injectJavascriptInterfaces(view);
            super.onPageFinished(view, url);
        }
    }

    class WebChromeClientEx extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            injectJavascriptInterfaces(view);
            super.onProgressChanged(view, newProgress);
        }

        @Override
        public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
            if (view instanceof SafeWebView) {
                if (handleJsInterface(view, url, message, defaultValue, result)) {
                    return true;
                }
            }

            return super.onJsPrompt(view, url, message, defaultValue, result);
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            injectJavascriptInterfaces(view);
        }
    }
}
