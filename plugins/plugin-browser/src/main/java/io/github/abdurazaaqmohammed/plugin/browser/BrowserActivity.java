package io.github.abdurazaaqmohammed.plugin.browser;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * In-app browser for MP Manager, delivered as an installable plugin app through
 * the host's external-plugin contract ({@code ACTION_SIDEBAR_OPEN}).
 *
 * <p>It is a real browsing surface, not a viewer: JavaScript, cookies and DOM
 * storage stay on so signed-in sites work, the platform file chooser backs
 * {@code <input type="file">} so forms that upload files can be submitted, and
 * downloads go through the system DownloadManager. Navigation stays inside this
 * activity for http(s); mailto/tel/store links are handed to the system.
 *
 * <p>WebView remote debugging is enabled so a desktop agent can drive the same
 * surface over adb (chrome://inspect) instead of controlling a desktop browser.
 */
public class BrowserActivity extends Activity {

    private static final int REQ_FILE_CHOOSER = 4001;
    private static final String STATE_WEBVIEW = "browser_webview_state";

    private WebView webView;
    private EditText urlBar;
    private ProgressBar progress;
    private ValueCallback<Uri[]> pendingFileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Allow the desktop agent to inspect/drive this in-app browser over adb
        // instead of controlling the browser on the computer.
        WebView.setWebContentsDebuggingEnabled(true);
        applyEdgeToEdge();
        buildUi();
        configureWebView();
        String start = startUrl();
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState);
            if (webView.getUrl() == null && start != null) webView.loadUrl(start);
        } else if (start != null) {
            webView.loadUrl(start);
        }
    }

    private void applyEdgeToEdge() {
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                // Dark browser chrome: keep status icons light.
                controller.setSystemBarsAppearance(0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF121212);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        applyStatusBarInset(root);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        int pad = dp(6);
        bar.setPadding(pad, pad, pad, pad);

        bar.addView(navButton("←", v -> {
            if (webView.canGoBack()) webView.goBack();
        }));
        bar.addView(navButton("→", v -> {
            if (webView.canGoForward()) webView.goForward();
        }));

        urlBar = new EditText(this);
        urlBar.setSingleLine(true);
        urlBar.setHint(R.string.url_hint);
        urlBar.setTextColor(0xFFFFFFFF);
        urlBar.setHintTextColor(0x99FFFFFF);
        urlBar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        urlBar.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlBar.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlBar.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable field = new GradientDrawable();
        field.setColor(0x22FFFFFF);
        field.setCornerRadius(dp(18));
        urlBar.setBackground(field);
        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(
                0, dp(40), 1f);
        urlParams.setMargins(dp(4), 0, dp(4), 0);
        bar.addView(urlBar, urlParams);
        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) {
                navigateFromInput();
                return true;
            }
            return false;
        });

        bar.addView(navButton("⟳", v -> webView.reload()));

        Button go = new Button(this);
        go.setText(R.string.go);
        go.setAllCaps(false);
        go.setTextColor(0xFFFFFFFF);
        go.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        GradientDrawable goBg = new GradientDrawable();
        goBg.setColor(0xFF3B6EA5);
        goBg.setCornerRadius(dp(18));
        go.setBackground(goBg);
        go.setMinWidth(0);
        go.setMinimumWidth(0);
        go.setPadding(dp(14), 0, dp(14), 0);
        go.setOnClickListener(v -> navigateFromInput());
        bar.addView(go, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));

        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        webView = new WebView(this);
        webView.setBackgroundColor(0xFF121212);
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private Button navButton(String label, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setOnClickListener(listener);
        return b;
    }

    private void applyStatusBarInset(View root) {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                top = insets.getInsets(
                        WindowInsets.Type.statusBars() | WindowInsets.Type.displayCutout()).top;
            } else {
                top = insets.getSystemWindowInsetTop();
            }
            v.setPadding(0, top, 0, 0);
            return insets;
        });
        root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUri(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUri(Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
                if (url != null) urlBar.setText(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                if (url != null) urlBar.setText(url);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler,
                                           android.net.http.SslError error) {
                handler.cancel();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (title != null && !title.isEmpty()) setTitle(title);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (pendingFileCallback != null) {
                    pendingFileCallback.onReceiveValue(null);
                }
                pendingFileCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE_CHOOSER);
                    return true;
                } catch (ActivityNotFoundException e) {
                    pendingFileCallback = null;
                    Toast.makeText(BrowserActivity.this, R.string.download_failed,
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimeType, long contentLength) {
                download(url, userAgent, contentDisposition, mimeType);
            }
        });
    }

    private boolean handleUri(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        if (scheme == null) return false;
        switch (scheme) {
            case "http":
            case "https":
            case "file":
            case "content":
            case "about":
            case "data":
                return false;
            default:
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {
                }
                return true;
        }
    }

    private void navigateFromInput() {
        String text = urlBar.getText().toString().trim();
        if (text.isEmpty()) return;
        webView.loadUrl(normalize(text));
        hideKeyboard();
    }

    private String normalize(String text) {
        if (text.startsWith("http://") || text.startsWith("https://")
                || text.startsWith("file://") || text.startsWith("content://")
                || text.startsWith("about:") || text.startsWith("data:")) {
            return text;
        }
        if (text.startsWith("/")) return "file://" + text;
        if (text.contains(" ") || !text.contains(".")) {
            return "https://www.baidu.com/s?wd=" + Uri.encode(text);
        }
        return "https://" + text;
    }

    private String startUrl() {
        Intent intent = getIntent();
        if (intent != null) {
            Uri data = intent.getData();
            if (data != null) {
                String s = data.toString();
                if (!s.isEmpty()) return s;
            }
            String extra = intent.getStringExtra("url");
            if (extra != null && !extra.trim().isEmpty()) return normalize(extra.trim());
        }
        return homePage();
    }

    /** Local start page with the project's fastest links; works offline. */
    private String homePage() {
        String html = "<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<style>body{background:#121212;color:#e8e8e8;font:16px/1.5 sans-serif;margin:0;padding:24px}"
                + "h1{font-size:20px;margin:0 0 4px}p{color:#9aa0a6;margin:0 0 20px}"
                + "a{display:block;padding:14px 16px;margin:10px 0;background:#1e1e1e;border-radius:12px;"
                + "color:#8ab4f8;text-decoration:none;border:1px solid #2c2c2c}"
                + "small{color:#7a7a7a}</style>"
                + "<h1>" + escape(getString(R.string.plugin_label)) + "</h1>"
                + "<p>内置浏览器 · 提交与登录都留在这个页面</p>"
                + "<a href='https://github.com/Yuxiximyxx/MP-Manager'>GitHub 仓库 / 提交 Issue</a>"
                + "<a href='https://github.com/Yuxiximyxx/MP-Manager/releases'>版本发布</a>"
                + "<a href='https://github.com/Yuxiximyxx/MP-Manager/pulls'>Pull requests</a>"
                + "<a href='https://t.me/MP_Manager_Discussion'>Telegram 讨论</a>"
                + "<small>地址栏可直接输入网址、搜索词或 /sdcard 本地路径。</small>";
        return "data:text/html;charset=utf-8," + Uri.encode(html);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void download(String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(mimeType);
            request.addRequestHeader("User-Agent", userAgent);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) request.addRequestHeader("Cookie", cookie);
            String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
            request.setTitle(name);
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (dm != null) dm.enqueue(request);
            Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE_CHOOSER) {
            if (pendingFileCallback != null) {
                pendingFileCallback.onReceiveValue(
                        WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                pendingFileCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) webView.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        if (pendingFileCallback != null) {
            pendingFileCallback.onReceiveValue(null);
            pendingFileCallback = null;
        }
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
