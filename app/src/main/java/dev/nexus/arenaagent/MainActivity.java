package dev.nexus.arenaagent;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Browser;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Independent Android wrapper for one Arena Agent session.
 * This is not an official Arena product and does not request user credentials itself.
 */
public final class MainActivity extends Activity {
    private static final String START_URL =
            "https://arena.ai/agent/01a1cb5e-c8db-7f67-8fa1-5220b5404438";
    private static final int FILE_PICKER_REQUEST = 6401;
    private static final int BG = Color.rgb(14, 17, 24);
    private static final int TEXT = Color.rgb(239, 243, 250);

    private FrameLayout root;
    private FrameLayout content;
    private ProgressBar progress;
    private LinearLayout errorLayer;
    private WebView webView;
    private ValueCallback<Uri[]> uploadCallback;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        buildUI();
        createWebView();
        if (state == null || webView.restoreState(state) == null) {
            webView.loadUrl(START_URL);
        }
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView toolbarButton(String title, int width) {
        TextView v = new TextView(this);
        v.setText(title);
        v.setTextColor(TEXT);
        v.setTextSize(13);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(9), 0, dp(9), 0);
        v.setBackgroundColor(BG);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(width), dp(48)));
        return v;
    }

    private void buildUI() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setBackgroundColor(BG);
        page.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(48)));

        TextView title = new TextView(this);
        title.setText("Arena Agent");
        title.setTextColor(TEXT);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextSize(16);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(16), 0, 0, 0);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -1, 1));

        TextView refresh = toolbarButton("↻", 45);
        refresh.setTextSize(23);
        refresh.setContentDescription("Recarregar página");
        refresh.setOnClickListener(v -> {
            hideError();
            if (webView != null) webView.reload();
        });
        toolbar.addView(refresh);

        TextView menu = toolbarButton("⋮", 45);
        menu.setTextSize(25);
        menu.setContentDescription("Opções do aplicativo");
        menu.setOnClickListener(v -> showMenu(menu));
        toolbar.addView(menu);

        content = new FrameLayout(this);
        content.setBackgroundColor(Color.WHITE);
        page.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgress(0);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP);
        root.addView(progress, p);

        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                root.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }
        setContentView(root);
    }

    private void createWebView() {
        webView = new WebView(this);
        content.addView(webView, 0, new FrameLayout.LayoutParams(-1, -1));
        webView.setBackgroundColor(Color.WHITE);
        webView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setSupportZoom(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true); // Android system document picker only
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSafeBrowsingEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                if (!req.isForMainFrame()) return false;
                Uri uri = req.getUrl();
                String scheme = uri.getScheme();
                String host = uri.getHost();
                if ("https".equalsIgnoreCase(scheme) && isArenaHost(host)) return false;
                openExternal(uri);
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                super.onPageFinished(view, url);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError error) {
                if (req.isForMainFrame()) showError("Não foi possível abrir o Arena. Verifique sua conexão ou abra pelo navegador.");
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                content.removeView(view);
                view.destroy();
                webView = null;
                createWebView();
                showError("A página foi reiniciada. Toque em Tentar novamente.");
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (uploadCallback != null) uploadCallback.onReceiveValue(null);
                uploadCallback = callback;
                try {
                    Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    picker.addCategory(Intent.CATEGORY_OPENABLE);
                    picker.setType("*/*");
                    String[] accepted = params.getAcceptTypes();
                    if (accepted != null && accepted.length == 1 && accepted[0].contains("/")) {
                        picker.setType(accepted[0]);
                    } else if (accepted != null && accepted.length > 1) {
                        picker.putExtra(Intent.EXTRA_MIME_TYPES, accepted);
                    }
                    picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                    startActivityForResult(picker, FILE_PICKER_REQUEST);
                } catch (ActivityNotFoundException e) {
                    uploadCallback = null;
                    callback.onReceiveValue(null);
                    Toast.makeText(MainActivity.this, "Seletor de arquivos não disponível", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });
        webView.setDownloadListener((url, agent, disposition, mime, size) -> {
            if (url.startsWith("https://") || url.startsWith("http://")) {
                try {
                    Uri uri = Uri.parse(url);
                    if (!"https".equalsIgnoreCase(uri.getScheme())) {
                        Toast.makeText(this, "Download inseguro bloqueado", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    DownloadManager.Request r = new DownloadManager.Request(uri);
                    r.setMimeType(mime);
                    String cookies = CookieManager.getInstance().getCookie(url);
                    if (cookies != null) r.addRequestHeader("Cookie", cookies);
                    r.addRequestHeader("User-Agent", agent);
                    String name = URLUtil.guessFileName(url, disposition, mime);
                    r.setTitle(name);
                    r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                    DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    dm.enqueue(r);
                    Toast.makeText(this, "Download iniciado", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Não foi possível baixar o arquivo", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "Esse tipo de download precisa do navegador externo", Toast.LENGTH_LONG).show();
                openBrowser();
            }
        });
    }

    private static boolean isArenaHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(java.util.Locale.ROOT);
        return h.equals("arena.ai") || h.endsWith(".arena.ai") || h.equals("lmarena.ai") || h.endsWith(".lmarena.ai");
    }

    private void openExternal(Uri url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, url);
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Nenhum aplicativo encontrado para esse link", Toast.LENGTH_SHORT).show();
        }
    }

    private void openBrowser() {
        String current = webView != null && webView.getUrl() != null ? webView.getUrl() : START_URL;
        openExternal(Uri.parse(current));
    }

    private void showMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "Abrir no navegador");
        popup.getMenu().add(0, 2, 1, "Voltar à sessão inicial");
        popup.getMenu().add(0, 3, 2, "Limpar dados deste aplicativo");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) openBrowser();
            else if (item.getItemId() == 2) {
                hideError();
                webView.loadUrl(START_URL);
            } else if (item.getItemId() == 3) {
                new AlertDialog.Builder(this)
                        .setTitle("Limpar dados?")
                        .setMessage("Isso apaga o login, cookies e armazenamento do Arena somente neste aplicativo.")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Limpar", (d, w) -> {
                            WebStorage.getInstance().deleteAllData();
                            CookieManager.getInstance().removeAllCookies(value -> CookieManager.getInstance().flush());
                            webView.clearCache(true);
                            webView.clearHistory();
                            hideError();
                            webView.loadUrl(START_URL);
                        })
                        .show();
            }
            return true;
        });
        popup.show();
    }

    private void showError(String message) {
        if (errorLayer != null) content.removeView(errorLayer);
        errorLayer = new LinearLayout(this);
        errorLayer.setOrientation(LinearLayout.VERTICAL);
        errorLayer.setGravity(Gravity.CENTER);
        errorLayer.setPadding(dp(28), dp(24), dp(28), dp(24));
        errorLayer.setBackgroundColor(BG);
        TextView t = new TextView(this);
        t.setText(message);
        t.setTextSize(16);
        t.setTextColor(TEXT);
        t.setGravity(Gravity.CENTER);
        errorLayer.addView(t, new LinearLayout.LayoutParams(-1, -2));
        Button retry = new Button(this);
        retry.setText("Tentar novamente");
        retry.setOnClickListener(v -> {
            hideError();
            webView.loadUrl(webView.getUrl() == null ? START_URL : webView.getUrl());
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(54));
        lp.topMargin = dp(20);
        errorLayer.addView(retry, lp);
        Button browser = new Button(this);
        browser.setText("Abrir no navegador");
        browser.setOnClickListener(v -> openBrowser());
        errorLayer.addView(browser, new LinearLayout.LayoutParams(-1, dp(54)));
        content.addView(errorLayer, new FrameLayout.LayoutParams(-1, -1));
        errorLayer.bringToFront();
    }

    private void hideError() {
        if (errorLayer != null) {
            content.removeView(errorLayer);
            errorLayer = null;
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != FILE_PICKER_REQUEST || uploadCallback == null) return;
        Uri[] uris = null;
        if (result == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                uris = new Uri[n];
                for (int i = 0; i < n; i++) uris[i] = data.getClipData().getItemAt(i).getUri();
            } else if (data.getData() != null) {
                uris = new Uri[]{data.getData()};
            }
        }
        uploadCallback.onReceiveValue(uris);
        uploadCallback = null;
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (webView != null) webView.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush();
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }

    @Override
    public void onBackPressed() {
        if (errorLayer != null) {
            hideError();
            return;
        }
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (uploadCallback != null) uploadCallback.onReceiveValue(null);
        if (webView != null) {
            CookieManager.getInstance().flush();
            content.removeView(webView);
            webView.destroy();
        }
        super.onDestroy();
    }
}