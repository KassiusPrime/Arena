package dev.nexus.arenaagent;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/**
 * Unofficial mobile client for the Arena website.
 * All login and conversation data stays on the website / Android WebView storage.
 * The application does not handle or store passwords or authentication tokens itself.
 */
public final class MainActivity extends Activity {
    private static final String HOME = "https://arena.ai/";
    private static final String AGENT = "https://arena.ai/agent";
    private static final String HISTORY = "https://arena.ai/history/search";
    private static final int PICK_FILES = 6401;
    private static final int DARK = Color.rgb(17, 17, 19);
    private static final int LIGHT = Color.rgb(248, 249, 252);
    private static final int MUTED = Color.rgb(159, 163, 172);

    private FrameLayout root, content, drawer, scrim;
    private TextView floatingMenu;
    private ProgressBar progress;
    private WebView webView, historyWebView;
    private LinearLayout errorLayer;
    private ValueCallback<Uri[]> pendingUpload;
    private Dialog popupDialog;
    private WebView popupWebView;
    private boolean drawerOpen = false;
    private float gestureStartX, gestureStartY;
    private boolean trackingEdge, trackingDrawer;
    private int drawerWidth;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(DARK);
        getWindow().setNavigationBarColor(DARK);
        // No native action bar or custom top toolbar; website gets the whole viewport.
        buildUI();
        createMainWebView();
        // Always start at the public landing page. Do not restore the invalid old /agent/{uuid}.
        webView.loadUrl(HOME);
    }

    private int dp(float size) { return (int) (getResources().getDisplayMetrics().density * size + 0.5f); }

    private GradientDrawable shape(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private void buildUI() {
        root = new FrameLayout(this);
        root.setBackgroundColor(DARK);
        content = new FrameLayout(this);
        content.setBackgroundColor(Color.WHITE);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams bar = new FrameLayout.LayoutParams(-1, dp(2), Gravity.TOP);
        root.addView(progress, bar);

        floatingMenu = text("☰", 23, Color.WHITE, false);
        floatingMenu.setGravity(Gravity.CENTER);
        floatingMenu.setContentDescription("Abrir conversas");
        floatingMenu.setBackground(shape(Color.rgb(37, 39, 44), 15));
        floatingMenu.setElevation(dp(4));
        FrameLayout.LayoutParams menu = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP | Gravity.LEFT);
        menu.leftMargin = dp(10);
        menu.topMargin = dp(8);
        root.addView(floatingMenu, menu);
        floatingMenu.setOnClickListener(v -> showDrawer());

        scrim = new FrameLayout(this);
        scrim.setBackgroundColor(0x88000000);
        scrim.setVisibility(View.GONE);
        scrim.setAlpha(0f);
        scrim.setOnClickListener(v -> hideDrawer());
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        drawerWidth = Math.min(dp(360), (int) (getResources().getDisplayMetrics().widthPixels * 0.86));
        drawer = new FrameLayout(this);
        drawer.setBackground(shape(DARK, 0));
        drawer.setElevation(dp(16));
        drawer.setVisibility(View.GONE);
        FrameLayout.LayoutParams side = new FrameLayout.LayoutParams(drawerWidth, -1, Gravity.LEFT);
        root.addView(drawer, side);
        makeDrawerContents();

        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                root.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }
        setContentView(root);
    }

    private TextView drawerAction(String label, String desc, Runnable action) {
        TextView t = text(label, 16, LIGHT, false);
        t.setContentDescription(desc);
        t.setPadding(dp(16), 0, dp(10), 0);
        t.setBackground(shape(0xFF25262A, 13));
        t.setOnClickListener(v -> action.run());
        return t;
    }

    private void makeDrawerContents() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(14), dp(12), dp(14), dp(15));
        drawer.addView(column, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        column.addView(header, new LinearLayout.LayoutParams(-1, dp(56)));

        ImageView logo = new ImageView(this);
        logo.setImageResource(dev.nexus.arenaagent.R.drawable.arena_pillar);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setContentDescription("Logotipo Arena: coluna branca em fundo preto");
        header.addView(logo, new LinearLayout.LayoutParams(dp(45), dp(45)));

        LinearLayout headline = new LinearLayout(this);
        headline.setOrientation(LinearLayout.VERTICAL);
        headline.setPadding(dp(9), 0, 0, 0);
        header.addView(headline, new LinearLayout.LayoutParams(0, -2, 1));
        headline.addView(text("Arena LLM", 19, LIGHT, true));
        headline.addView(text("Suas conversas", 12, MUTED, false));

        TextView close = text("×", 30, LIGHT, false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Fechar conversas");
        header.addView(close, new LinearLayout.LayoutParams(dp(44), dp(44)));
        close.setOnClickListener(v -> hideDrawer());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams aParams = new LinearLayout.LayoutParams(-1, -2);
        aParams.topMargin = dp(14);
        column.addView(actions, aParams);

        TextView newChat = drawerAction("＋   Novo chat", "Começar conversa no Arena", () -> navigate(HOME));
        actions.addView(newChat, new LinearLayout.LayoutParams(-1, dp(46)));
        TextView agent = drawerAction("◇   Modo Agente", "Abrir modo Agente", () -> navigate(AGENT));
        LinearLayout.LayoutParams item = new LinearLayout.LayoutParams(-1, dp(46));
        item.topMargin = dp(8);
        actions.addView(agent, item);

        LinearLayout historyHeader = new LinearLayout(this);
        historyHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams histParams = new LinearLayout.LayoutParams(-1, dp(50));
        histParams.topMargin = dp(10);
        column.addView(historyHeader, histParams);
        TextView chats = text("Histórico de chats", 15, LIGHT, true);
        chats.setPadding(dp(4), 0, 0, 0);
        historyHeader.addView(chats, new LinearLayout.LayoutParams(0, -1, 1));
        TextView refresh = text("↻", 24, MUTED, false);
        refresh.setGravity(Gravity.CENTER);
        refresh.setContentDescription("Atualizar histórico");
        historyHeader.addView(refresh, new LinearLayout.LayoutParams(dp(44), dp(44)));
        refresh.setOnClickListener(v -> {
            if (historyWebView != null) historyWebView.reload();
        });

        FrameLayout historyHolder = new FrameLayout(this);
        historyHolder.setBackground(shape(0xFF202125, 12));
        historyHolder.setClipToOutline(true);
        column.addView(historyHolder, new LinearLayout.LayoutParams(-1, 0, 1));

        historyWebView = new WebView(this);
        historyWebView.setBackgroundColor(0xFF202125);
        applyWebSettings(historyWebView, false);
        historyHolder.addView(historyWebView, new FrameLayout.LayoutParams(-1, -1));
        historyWebView.addJavascriptInterface(new HistoryNavigation(), "NativeHistory");
        historyWebView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                Uri uri = request.getUrl();
                if (isArenaUrl(uri) && !isHistoryUrl(uri)) {
                    runOnUiThread(() -> navigate(uri.toString()));
                    return true;
                }
                if (isArenaUrl(uri)) return false;
                openExternal(uri);
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                if (isHistoryUrl(Uri.parse(url))) {
                    // The site's own authenticated search/history UI is rendered here;
                    // native bridge only transfers a tapped chat link to the main WebView.
                    view.evaluateJavascript("(function(){if(window.__arenaDrawerHook)return;window.__arenaDrawerHook=true;" +
                            "document.addEventListener('click',function(e){var a=e.target.closest&&e.target.closest('a[href]');" +
                            "if(!a)return;try{var u=new URL(a.href,location.href);if(u.origin===location.origin&&" +
                            "u.pathname!='/history/search'&&u.pathname!='/'){e.preventDefault();" +
                            "NativeHistory.open(u.href);}}catch(_){}},true);" +
                            "var old=history.pushState;history.pushState=function(){old.apply(this,arguments);" +
                            "if(location.pathname!='/history/search')NativeHistory.open(location.href);};})();", null);
                }
            }
        });

        TextView footer = text("ⓘ  Login e histórico pertencem ao Arena", 11, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams foot = new LinearLayout.LayoutParams(-1, dp(42));
        column.addView(footer, foot);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);
        column.addView(bottom, new LinearLayout.LayoutParams(-1, dp(46)));
        TextView browser = text("Navegador", 13, LIGHT, false);
        browser.setGravity(Gravity.CENTER);
        bottom.addView(browser, new LinearLayout.LayoutParams(0, -1, 1));
        browser.setOnClickListener(v -> { hideDrawer(); openBrowser(); });
        TextView signout = text("Limpar sessão", 13, MUTED, false);
        signout.setGravity(Gravity.CENTER);
        bottom.addView(signout, new LinearLayout.LayoutParams(0, -1, 1));
        signout.setOnClickListener(v -> confirmClearSession());
    }

    private final class HistoryNavigation {
        @JavascriptInterface public void open(String url) {
            Uri u = Uri.parse(url);
            if (isArenaUrl(u) && !isHistoryUrl(u)) runOnUiThread(() -> navigate(url));
        }
    }

    private static boolean isArenaHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals("arena.ai") || h.endsWith(".arena.ai") ||
               h.equals("lmarena.ai") || h.endsWith(".lmarena.ai");
    }

    private static boolean isArenaUrl(Uri uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme()) && isArenaHost(uri.getHost());
    }

    private static boolean isHistoryUrl(Uri uri) {
        return isArenaUrl(uri) && "/history/search".equals(uri.getPath());
    }

    // OAuth popups and redirections need to remain in-app to complete the Arena session.
    // Google may still reject embedded browsers server-side (Google OAuth policy).
    private static boolean isTrustedAuthHost(String host) {
        if (host == null) return false;
        String h = host.toLowerCase(Locale.ROOT);
        return h.equals("accounts.google.com") || h.equals("github.com") ||
            h.equals("appleid.apple.com") || h.equals("login.microsoftonline.com") ||
            h.equals("login.live.com") || h.equals("discord.com") ||
            h.equals("huggingface.co") || h.equals("auth0.com") || h.endsWith(".auth0.com");
    }

    private void applyWebSettings(WebView w, boolean popups) {
        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setSupportZoom(false);
        s.setJavaScriptCanOpenWindowsAutomatically(popups);
        s.setSupportMultipleWindows(popups);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(popups); // user-selected documents via Android picker
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSafeBrowsingEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true);
    }

    private void createMainWebView() {
        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        content.addView(webView, 0, new FrameLayout.LayoutParams(-1, -1));
        applyWebSettings(webView, true);
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                if (!req.isForMainFrame()) return false;
                Uri uri = req.getUrl();
                if ("https".equalsIgnoreCase(uri.getScheme()) &&
                        (isArenaHost(uri.getHost()) || isTrustedAuthHost(uri.getHost()))) return false;
                openExternal(uri);
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                hideError();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError e) {
                if (request.isForMainFrame()) showError("Não foi possível carregar o Arena. Verifique a conexão.");
            }
            @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail d) {
                content.removeView(view);
                view.destroy();
                webView = null;
                createMainWebView();
                showError("A página foi reiniciada. Reabra o Arena.");
                return true;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int pct) {
                progress.setProgress(pct);
                progress.setVisibility(pct >= 100 ? View.GONE : View.VISIBLE);
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams p) {
                return chooseFiles(callback, p);
            }
            @Override public boolean onCreateWindow(WebView view, boolean isDialog, boolean userGesture, Message result) {
                openAuthPopup(result);
                return true;
            }
            @Override public void onCloseWindow(WebView window) {
                closeAuthPopup();
            }
        });
        webView.setDownloadListener((url, agent, disposition, mime, size) -> download(url, agent, disposition, mime));
    }

    private void openAuthPopup(Message result) {
        closeAuthPopup();
        popupDialog = new Dialog(this, android.R.style.Theme_Material_Light_NoActionBar);
        FrameLayout holder = new FrameLayout(this);
        popupWebView = new WebView(this);
        popupWebView.setBackgroundColor(Color.WHITE);
        applyWebSettings(popupWebView, true);
        holder.addView(popupWebView, new FrameLayout.LayoutParams(-1, -1));
        TextView close = text("×", 28, LIGHT, false);
        close.setBackground(shape(DARK, 12));
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Fechar janela de autenticação");
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.TOP | Gravity.RIGHT);
        cp.topMargin = dp(12); cp.rightMargin = dp(12);
        holder.addView(close, cp);
        close.setOnClickListener(v -> closeAuthPopup());
        popupWebView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                if (!req.isForMainFrame()) return false;
                Uri url = req.getUrl();
                if ("https".equalsIgnoreCase(url.getScheme()) &&
                    (isArenaHost(url.getHost()) || isTrustedAuthHost(url.getHost()))) return false;
                openExternal(url);
                return true;
            }
            @Override public void onPageFinished(WebView view, String url) {
                CookieManager.getInstance().flush();
                // Many auth providers return to arena.ai in this popup; refresh the main
                // site afterwards to pick up the newly persisted session cookies.
                if (isArenaUrl(Uri.parse(url)) && !url.equals(HOME)) {
                    view.postDelayed(() -> {
                        if (popupDialog != null && webView != null) {
                            CookieManager.getInstance().flush();
                            webView.reload();
                        }
                    }, 500);
                }
            }
        });
        popupWebView.setWebChromeClient(new WebChromeClient() {
            @Override public void onCloseWindow(WebView w) { closeAuthPopup(); }
        });
        popupDialog.setContentView(holder);
        popupDialog.setOnDismissListener(d -> {
            if (popupWebView != null) { popupWebView.destroy(); popupWebView = null; }
            popupDialog = null;
            if (webView != null) webView.reload();
        });
        popupDialog.show();
        if (popupDialog.getWindow() != null) popupDialog.getWindow().setLayout(-1, -1);
        WebView.WebViewTransport transport = (WebView.WebViewTransport) result.obj;
        transport.setWebView(popupWebView);
        result.sendToTarget();
    }

    private void closeAuthPopup() {
        if (popupDialog != null) popupDialog.dismiss();
    }

    private boolean chooseFiles(ValueCallback<Uri[]> callback, WebChromeClient.FileChooserParams p) {
        if (pendingUpload != null) pendingUpload.onReceiveValue(null);
        pendingUpload = callback;
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            String[] accepted = p.getAcceptTypes();
            if (accepted != null && accepted.length == 1 && accepted[0] != null && accepted[0].contains("/")) {
                intent.setType(accepted[0]);
            } else if (accepted != null && accepted.length > 1) intent.putExtra(Intent.EXTRA_MIME_TYPES, accepted);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, p.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE);
            startActivityForResult(intent, PICK_FILES);
        } catch (ActivityNotFoundException e) {
            pendingUpload = null;
            callback.onReceiveValue(null);
            Toast.makeText(this, "Seletor de arquivos indisponível", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_FILES || pendingUpload == null) return;
        Uri[] uris = null;
        if (result == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                uris = new Uri[n];
                for (int i=0; i<n; i++) uris[i] = data.getClipData().getItemAt(i).getUri();
            } else if (data.getData() != null) uris = new Uri[]{data.getData()};
        }
        pendingUpload.onReceiveValue(uris);
        pendingUpload = null;
    }

    private void download(String url, String agent, String disposition, String mime) {
        if (url == null || !url.startsWith("https://")) {
            Toast.makeText(this, "Para esse formato, use o navegador externo", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            r.setMimeType(mime);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) r.addRequestHeader("Cookie", cookie);
            if (agent != null) r.addRequestHeader("User-Agent", agent);
            String filename = URLUtil.guessFileName(url, disposition, mime);
            r.setTitle(filename);
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
            Toast.makeText(this, "Download iniciado", Toast.LENGTH_SHORT).show();
        } catch (Exception e) { Toast.makeText(this, "Falha ao baixar. Abra no navegador.", Toast.LENGTH_LONG).show(); }
    }

    private void navigate(String url) {
        Uri uri = Uri.parse(url);
        if (!isArenaUrl(uri)) return;
        hideDrawer();
        hideError();
        if (webView != null) webView.loadUrl(url);
    }

    private void showDrawer() {
        if (drawerOpen) return;
        drawerOpen = true;
        scrim.setVisibility(View.VISIBLE);
        drawer.setVisibility(View.VISIBLE);
        drawer.setTranslationX(-drawerWidth);
        scrim.animate().alpha(1f).setDuration(200).start();
        drawer.animate().translationX(0).setDuration(220).start();
        if (historyWebView != null) historyWebView.loadUrl(HISTORY);
    }

    private void hideDrawer() {
        if (!drawerOpen) return;
        drawerOpen = false;
        drawer.animate().translationX(-drawerWidth).setDuration(210).withEndAction(() -> {
            if (!drawerOpen) drawer.setVisibility(View.GONE);
        }).start();
        scrim.animate().alpha(0f).setDuration(210).withEndAction(() -> {
            if (!drawerOpen) scrim.setVisibility(View.GONE);
        }).start();
    }

    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        switch(e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                gestureStartX = e.getRawX(); gestureStartY = e.getRawY();
                trackingEdge = !drawerOpen && gestureStartX <= dp(34);
                trackingDrawer = drawerOpen && gestureStartX <= drawerWidth;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getRawX() - gestureStartX;
                float dy = e.getRawY() - gestureStartY;
                if (Math.abs(dx) > dp(76) && Math.abs(dx) > Math.abs(dy) * 1.35f) {
                    if (trackingEdge && dx > 0) {
                        trackingEdge = false;
                        MotionEvent cancel = MotionEvent.obtain(e); cancel.setAction(MotionEvent.ACTION_CANCEL);
                        super.dispatchTouchEvent(cancel); cancel.recycle();
                        showDrawer(); return true;
                    }
                    if (trackingDrawer && dx < 0) {
                        trackingDrawer = false;
                        MotionEvent cancel = MotionEvent.obtain(e); cancel.setAction(MotionEvent.ACTION_CANCEL);
                        super.dispatchTouchEvent(cancel); cancel.recycle();
                        hideDrawer(); return true;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                trackingEdge = false; trackingDrawer = false;
                break;
        }
        return super.dispatchTouchEvent(e);
    }

    private void openExternal(Uri uri) {
        try {
            if (!"https".equalsIgnoreCase(uri.getScheme()) && !"mailto".equalsIgnoreCase(uri.getScheme())) return;
            Intent i = new Intent(Intent.ACTION_VIEW, uri);
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(i);
        } catch (Exception ignored) { Toast.makeText(this, "Não foi possível abrir o link", Toast.LENGTH_SHORT).show(); }
    }

    private void openBrowser() {
        String current = webView != null ? webView.getUrl() : null;
        openExternal(Uri.parse(current != null && current.startsWith("https://") ? current : HOME));
    }

    private void confirmClearSession() {
        new AlertDialog.Builder(this)
            .setTitle("Apagar login do app?")
            .setMessage("Esta operação remove cookies, histórico e dados locais do Arena neste aplicativo. Seus chats na conta Arena não serão excluídos.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Limpar", (d, which) -> {
                WebStorage.getInstance().deleteAllData();
                CookieManager.getInstance().removeAllCookies(ok -> CookieManager.getInstance().flush());
                if (webView != null) { webView.clearCache(true); webView.clearHistory(); webView.loadUrl(HOME); }
                if (historyWebView != null) { historyWebView.clearCache(true); historyWebView.loadUrl(HISTORY); }
                hideDrawer();
            }).show();
    }

    private void showError(String message) {
        hideError();
        errorLayer = new LinearLayout(this);
        errorLayer.setOrientation(LinearLayout.VERTICAL);
        errorLayer.setGravity(Gravity.CENTER);
        errorLayer.setPadding(dp(26), dp(26), dp(26), dp(26));
        errorLayer.setBackgroundColor(DARK);
        TextView msg = text(message, 16, LIGHT, true);
        msg.setGravity(Gravity.CENTER);
        errorLayer.addView(msg, new LinearLayout.LayoutParams(-1, -2));
        Button retry = new Button(this);
        retry.setText("Abrir Arena novamente");
        retry.setOnClickListener(v -> navigate(HOME));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(50));
        rp.topMargin = dp(20);
        errorLayer.addView(retry, rp);
        content.addView(errorLayer, new FrameLayout.LayoutParams(-1, -1));
    }

    private void hideError() {
        if (errorLayer != null) { content.removeView(errorLayer); errorLayer = null; }
    }

    @Override public void onBackPressed() {
        if (drawerOpen) hideDrawer();
        else if (popupDialog != null) closeAuthPopup();
        else if (errorLayer != null) hideError();
        else if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onPause() {
        CookieManager.getInstance().flush();
        if (webView != null) webView.onPause();
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
    }
    @Override protected void onStop() {
        CookieManager.getInstance().flush();
        super.onStop();
    }
    @Override protected void onDestroy() {
        CookieManager.getInstance().flush();
        if (pendingUpload != null) pendingUpload.onReceiveValue(null);
        closeAuthPopup();
        if (historyWebView != null) { historyWebView.destroy(); historyWebView = null; }
        if (webView != null) { content.removeView(webView); webView.destroy(); webView = null; }
        super.onDestroy();
    }
}