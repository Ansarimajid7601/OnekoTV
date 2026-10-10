package cc.oneko.tv;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.JsPromptResult;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.ByteArrayInputStream;

public class MainActivity extends Activity {

    private static final String HOST = "0neko.cc";
    private static final String START_URL = "https://0neko.cc/#/";

    // ---- Switches ----
    private static final boolean FREE_WEBVIEW_IN_BACKGROUND = true; // destroy WebView when app is hidden (saves RAM)
    private static final boolean BLOCK_TRACKERS = true;             // skip analytics/ad hosts
    private static final boolean REDUCE_MOTION = true;              // shorten CSS animations
    private static final boolean ALLOW_SSL_ERRORS = false;          // true = ignore bad/old certificates (INSECURE)

    private static final String[] BLOCKED = {
        "google-analytics.com", "googletagmanager.com", "doubleclick.net",
        "googlesyndication.com", "googleadservices.com", "connect.facebook.net",
        "hotjar.com", "clarity.ms", "scorecardresearch.com"
    };

    private static final String REDUCE_JS =
        "(function(){if(document.getElementById('_lr'))return;var s=document.createElement('style');s.id='_lr';"
        + "s.textContent='*,*:before,*:after{-webkit-animation-duration:.01ms!important;animation-duration:.01ms!important;"
        + "-webkit-animation-iteration-count:1!important;animation-iteration-count:1!important;"
        + "-webkit-transition-duration:.01ms!important;transition-duration:.01ms!important}';"
        + "(document.head||document.documentElement).appendChild(s);})()";

    // Tells the app when a text box is focused, so number keys type digits instead of firing shortcuts.
    private static final String FOCUS_JS =
        "(function(){if(window._ek)return;window._ek=1;function s(e){var t=e.target,n=t&&t.tagName;"
        + "var ed=n=='TEXTAREA'||(n=='INPUT'&&!/^(checkbox|radio|button|submit|range)$/.test(t.type))||(t&&t.isContentEditable);"
        + "prompt('_ed:'+(ed?1:0))}document.addEventListener('focus',s,true);"
        + "document.addEventListener('blur',function(){prompt('_ed:0')},true);})()";

    private static final String DASH_HEAD =
        "<html><head><meta name='viewport' content='width=device-width'><style>"
        + "body{margin:0;padding:3%;background:#111827;color:#e5e7eb;font:26px sans-serif}"
        + "h1{font-size:34px;margin:0 0 16px}"
        + "a{display:inline-block;width:30%;margin:0 1.5% 16px 0;padding:16px;background:#1f2937;color:#fff;"
        + "border-radius:10px;text-decoration:none;vertical-align:top;overflow:hidden;"
        + "-webkit-box-sizing:border-box;box-sizing:border-box}"
        + "b{color:#a78bfa;font-size:34px;margin-right:10px}"
        + "small{display:block;color:#9ca3af;font-size:20px}p{color:#9ca3af;font-size:22px}"
        + "</style></head><body><h1>Dashboard</h1>";

    private FrameLayout root;
    private WebView web;
    private CursorView cursor;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideCursor = new Runnable() {
        @Override public void run() { cursor.setVisibility(View.INVISIBLE); }
    };

    private boolean cursorMode = true;   // false = native arrow-key focus navigation
    private boolean playerKeys = false;  // true while a fullscreen video is up
    private boolean typing = false;      // a text box has focus on the page
    private boolean numLong = false;     // current number key already did its long-press action
    private float cx, cy, density;
    private String current = START_URL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        density = getResources().getDisplayMetrics().density;
        current = prefs().getString("u", START_URL);
        if (Build.VERSION.SDK_INT < 21) CookieSyncManager.createInstance(getApplicationContext());

        root = new FrameLayout(this);
        createWebView();
        int size = (int) (26 * density);
        cursor = new CursorView(this, size);
        root.addView(cursor, new FrameLayout.LayoutParams(size, size));
        setContentView(root);

        cx = W() / 2f;
        cy = H() / 2f;
        cursor.setTranslationX(cx);
        cursor.setTranslationY(cy);
        showCursor();
        web.loadUrl(current);
    }

    // ------------------------------------------------------------ WebView

    @SuppressWarnings("deprecation")
    private void createWebView() {
        web = new WebView(this);
        root.addView(web, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setGeolocationEnabled(false);
        s.setSaveFormData(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setDatabaseEnabled(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT < 18) s.setRenderPriority(WebSettings.RenderPriority.HIGH);
        if (Build.VERSION.SDK_INT >= 17) s.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= 21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        CookieManager.getInstance().setAcceptCookie(true);

        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        if (Build.VERSION.SDK_INT >= 26) {
            web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true);
        }

        web.setWebViewClient(Build.VERSION.SDK_INT >= 26 ? new Client26()
                : Build.VERSION.SDK_INT >= 21 ? new Client21() : new Client());

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) { callback.onCustomViewHidden(); return; }
                customView = view;
                customCallback = callback;
                web.setVisibility(View.GONE);
                root.addView(view);
                cursor.bringToFront();
                playerKeys = true;
                cursor.setVisibility(View.INVISIBLE);
                toast("Left/Right seek, 0-9 jump, OK pause, MENU cursor");
            }

            @Override
            public void onHideCustomView() { exitFullscreen(); }

            @Override
            public boolean onJsPrompt(WebView v, String url, String msg, String def, JsPromptResult r) {
                if (msg != null && msg.startsWith("_ed:")) {
                    typing = msg.endsWith("1");
                    r.cancel();
                    return true;
                }
                return false;
            }
        });
        web.requestFocus();
    }

    // Three client classes so old Dalvik never has to load API 21/26 types.
    private class Client extends WebViewClient {
        @Override public void onPageStarted(WebView v, String url, Bitmap icon) { typing = false; }

        @Override public void doUpdateVisitedHistory(WebView v, String url, boolean isReload) {
            if (url != null && url.startsWith("http")) current = url;
        }

        @Override public void onPageFinished(WebView v, String url) {
            runJs(FOCUS_JS);
            if (REDUCE_MOTION) runJs(REDUCE_JS);
        }

        @Override public WebResourceResponse shouldInterceptRequest(WebView v, String url) {
            return isBlocked(Uri.parse(url).getHost()) ? emptyResponse() : null;
        }

        @Override public void onReceivedSslError(WebView v, SslErrorHandler h, SslError e) {
            if (ALLOW_SSL_ERRORS) h.proceed(); else h.cancel();
        }
    }

    @TargetApi(21)
    private class Client21 extends Client {
        @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
            return isBlocked(r.getUrl().getHost()) ? emptyResponse() : null;
        }
    }

    @TargetApi(26)
    private class Client26 extends Client21 {
        @Override public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
            handler.post(new Runnable() { @Override public void run() { recreateWebView(); } });
            return true;
        }
    }

    private boolean isBlocked(String h) {
        if (!BLOCK_TRACKERS || h == null) return false;
        for (String b : BLOCKED) if (h.equals(b) || h.endsWith("." + b)) return true;
        return false;
    }

    private WebResourceResponse emptyResponse() {
        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
    }

    // evaluateJavascript only exists on Android 4.4+; older versions use a javascript: URL.
    private void runJs(String js) {
        if (web == null) return;
        if (Build.VERSION.SDK_INT >= 19) web.evaluateJavascript(js, null);
        else web.loadUrl("javascript:void(" + js + ")");
    }

    private void recreateWebView() {
        if (web == null) return;
        exitFullscreen();
        root.removeView(web);
        web.destroy();
        createWebView();
        web.loadUrl(current);
    }

    private void exitFullscreen() {
        if (customView == null) return;
        root.removeView(customView);
        customView = null;
        playerKeys = false;
        if (web != null) web.setVisibility(View.VISIBLE);
        if (customCallback != null) {
            WebChromeClient.CustomViewCallback cb = customCallback;
            customCallback = null;
            cb.onCustomViewHidden();
        }
    }

    // ------------------------------------------------------------ remote control

    private static int digit(int k) {
        if (k >= KeyEvent.KEYCODE_0 && k <= KeyEvent.KEYCODE_9) return k - KeyEvent.KEYCODE_0;
        if (k >= KeyEvent.KEYCODE_NUMPAD_0 && k <= KeyEvent.KEYCODE_NUMPAD_9) return k - KeyEvent.KEYCODE_NUMPAD_0;
        return -1;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        final int k = e.getKeyCode();
        final boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        final int rep = e.getRepeatCount();

        if (k == KeyEvent.KEYCODE_MENU) {
            if (down && rep == 0) toggleMode();
            return true;
        }
        if (web == null) return super.dispatchKeyEvent(e);

        switch (k) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                if (down && rep == 0) video("v.paused?v.play():v.pause();");
                return true;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                if (down && rep == 0) video("v.play();");
                return true;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                if (down && rep == 0) video("v.pause();");
                return true;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (down) video("v.currentTime+=10;");
                return true;
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                if (down) video("v.currentTime-=10;");
                return true;
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                if (down) wheel(3f);
                return true;
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                if (down) wheel(-3f);
                return true;
            default:
                break;
        }

        // Number keys: fullscreen video = jump to n*10%; otherwise speed dial
        // (tap 1-9 = open slot, hold 1-9 = save this page there, 0 = dashboard, hold 0 = home).
        int n = digit(k);
        if (n >= 0) {
            if (customView != null) {
                if (playerKeys) {
                    if (down && rep == 0) video("if(v.duration)v.currentTime=v.duration*" + n + "/10;");
                    return true;
                }
            } else if (!typing) {
                if (down) {
                    if (e.isLongPress() && !numLong) { numLong = true; numLongPress(n); }
                } else {
                    if (!numLong) numShortPress(n);
                    numLong = false;
                }
                return true;
            }
        }

        if (customView != null && playerKeys) {
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (down) video("v.currentTime-=10;");
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down) video("v.currentTime+=10;");
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (down && rep == 0) video("v.paused?v.play():v.pause();");
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    return true;
                default:
                    break;
            }
        }

        if (cursorMode) {
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down) moveCursor(k, rep);
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (down && rep == 0) click();
                    return true;
                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    // ------------------------------------------------------------ speed dial + dashboard

    private SharedPreferences prefs() { return getSharedPreferences("s", MODE_PRIVATE); }

    private void numShortPress(int n) {
        if (n == 0) { showDashboard(); return; }
        String u = prefs().getString("k" + n, null);
        if (u == null) toast("Slot " + n + " is empty. Hold " + n + " on a page to save it here.");
        else web.loadUrl(u);
    }

    private void numLongPress(int n) {
        if (n == 0) { web.loadUrl(START_URL); toast("Home"); return; }
        String u = web.getUrl();
        if (u == null) u = current;
        if (u == null || !u.startsWith("http")) { toast("Open a page first, then hold " + n); return; }
        String t = web.getTitle();
        if (t == null || t.trim().isEmpty()) t = Uri.parse(u).getHost();
        prefs().edit().putString("k" + n, u).putString("t" + n, t).apply();
        toast("Saved to slot " + n + ": " + t);
    }

    private void showDashboard() {
        StringBuilder h = new StringBuilder(DASH_HEAD);
        h.append("<a href='").append(START_URL).append("'><b>0</b>Home<small>hold 0</small></a>");
        for (int i = 1; i <= 9; i++) {
            String u = prefs().getString("k" + i, null);
            if (u == null) {
                h.append("<a style='opacity:.4'><b>").append(i).append("</b>empty<small>hold ")
                 .append(i).append(" on a page</small></a>");
            } else {
                String host = Uri.parse(u).getHost();
                h.append("<a href='").append(esc(u)).append("'><b>").append(i).append("</b>")
                 .append(esc(prefs().getString("t" + i, host))).append("<small>")
                 .append(esc(host)).append("</small></a>");
            }
        }
        h.append("<p>Press 1-9 to open. Hold 1-9 for a second on any page to save it to that slot. "
                + "0 = this screen. Hold 0 = home.</p></body></html>");
        web.loadDataWithBaseURL(null, h.toString(), "text/html", "utf-8", null);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("'", "&#39;").replace("\"", "&quot;");
    }

    // ------------------------------------------------------------ cursor

    private void toggleMode() {
        if (customView != null) {
            playerKeys = !playerKeys;
            if (playerKeys) {
                cursor.setVisibility(View.INVISIBLE);
                toast("Player keys: Left/Right seek, OK pause");
            } else {
                showCursor();
                toast("Cursor mode");
            }
        } else {
            cursorMode = !cursorMode;
            if (cursorMode) {
                showCursor();
                toast("Cursor mode");
            } else {
                cursor.setVisibility(View.INVISIBLE);
                toast("Arrow-key focus mode");
            }
        }
    }

    private void moveCursor(int key, int repeat) {
        float step = density * (4f + 0.6f * Math.min(repeat, 20)); // accelerates while held
        float nx = cx, ny = cy;
        if (key == KeyEvent.KEYCODE_DPAD_LEFT) nx -= step;
        else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) nx += step;
        else if (key == KeyEvent.KEYCODE_DPAD_UP) ny -= step;
        else ny += step;

        float w = W(), h = H();
        if (key == KeyEvent.KEYCODE_DPAD_UP && ny < 0 && repeat % 2 == 0) wheel(1f);
        if (key == KeyEvent.KEYCODE_DPAD_DOWN && ny > h - 1 && repeat % 2 == 0) wheel(-1f);

        cx = clamp(nx, 0, w - 1);
        cy = clamp(ny, 0, h - 1);
        cursor.setTranslationX(cx);
        cursor.setTranslationY(cy);
        showCursor();
    }

    private void click() {
        final View t = customView != null ? customView : web;
        if (t == null) return;
        final long down = SystemClock.uptimeMillis();
        sendTouch(t, MotionEvent.ACTION_DOWN, down, down);
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                sendTouch(t, MotionEvent.ACTION_UP, down, SystemClock.uptimeMillis());
            }
        }, 60);
        showCursor();
    }

    private void sendTouch(View t, int action, long downTime, long time) {
        if (t.getWindowToken() == null) return;
        MotionEvent ev = MotionEvent.obtain(downTime, time, action, cx, cy, 0);
        ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        t.dispatchTouchEvent(ev);
        ev.recycle();
    }

    // Positive v = scroll up. Mouse-wheel event on 4.4+, plain scrollBy on older WebViews.
    private void wheel(float v) {
        View t = customView != null ? customView : web;
        if (t == null || t.getWindowToken() == null) return;
        if (Build.VERSION.SDK_INT < 19) {
            if (customView == null) web.scrollBy(0, (int) (-v * 80 * density));
            return;
        }
        MotionEvent.PointerProperties[] pp = { new MotionEvent.PointerProperties() };
        pp[0].id = 0;
        pp[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;
        MotionEvent.PointerCoords[] pc = { new MotionEvent.PointerCoords() };
        pc[0].x = cx;
        pc[0].y = cy;
        pc[0].setAxisValue(MotionEvent.AXIS_VSCROLL, v);
        long now = SystemClock.uptimeMillis();
        MotionEvent ev = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1, pp, pc,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0);
        t.dispatchGenericMotionEvent(ev);
        ev.recycle();
    }

    // Controls the first <video> found (also inside same-origin iframes).
    private void video(String action) {
        runJs("(function(){function f(d){var v=d.querySelector('video');if(v)return v;"
                + "var i=d.querySelectorAll('iframe');for(var j=0;j<i.length;j++){try{v=f(i[j].contentDocument);"
                + "if(v)return v}catch(e){}}}var v=f(document);if(v){" + action + "}})()");
    }

    private void showCursor() {
        cursor.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideCursor);
        handler.postDelayed(hideCursor, 5000);
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }
    private int W() { return root.getWidth() > 0 ? root.getWidth() : getResources().getDisplayMetrics().widthPixels; }
    private int H() { return root.getHeight() > 0 ? root.getHeight() : getResources().getDisplayMetrics().heightPixels; }

    // ------------------------------------------------------------ lifecycle / memory

    @Override
    public void onBackPressed() {
        if (customView != null) exitFullscreen();
        else if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (web == null) {
            createWebView();
            web.loadUrl(current);
        } else {
            web.resumeTimers();
        }
    }

    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }
    @Override protected void onPause()  { super.onPause();  if (web != null) web.onPause(); }

    @Override
    protected void onStop() {
        super.onStop();
        if (current != null && current.contains("//" + HOST)) {
            prefs().edit().putString("u", current).apply();
        }
        if (web == null) return;
        // Keep cookies (logins) across restarts.
        if (Build.VERSION.SDK_INT >= 21) CookieManager.getInstance().flush();
        else CookieSyncManager.getInstance().sync();
        exitFullscreen();
        if (FREE_WEBVIEW_IN_BACKGROUND) {
            root.removeView(web);
            web.destroy();
            web = null;
        } else {
            web.pauseTimers();
        }
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (web != null && level == TRIM_MEMORY_RUNNING_CRITICAL) web.clearCache(false);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }

    // ------------------------------------------------------------ cursor view (no bitmaps)

    private static class CursorView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        CursorView(Context c, int size) {
            super(c);
            fill.setColor(Color.WHITE);
            line.setColor(Color.BLACK);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(Math.max(2f, size / 12f));
            float s = size - 2;
            path.moveTo(1, 1);
            path.lineTo(1, s * 0.8f);
            path.lineTo(s * 0.22f, s * 0.62f);
            path.lineTo(s * 0.4f, s * 0.95f);
            path.lineTo(s * 0.55f, s * 0.87f);
            path.lineTo(s * 0.38f, s * 0.55f);
            path.lineTo(s * 0.66f, s * 0.55f);
            path.close();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawPath(path, fill);
            canvas.drawPath(path, line);
        }
    }
}
