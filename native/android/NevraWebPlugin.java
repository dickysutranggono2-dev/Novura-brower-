package com.nevra.browser;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.os.Message;
import android.view.View;
import android.util.Log;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

// WebView native Nevra: tampil di antara bilah atas dan toolbar bawah (milik UI HTML), dengan dukungan unduhan.
@CapacitorPlugin(name = "NevraWeb")
public class NevraWebPlugin extends Plugin {
    private static WebView sWeb;
    private static NevraWebPlugin sSelf;
    private WebView web;
    private View customView;
    private WebChromeClient.CustomViewCallback customCb;

    static boolean goBackIfPossible() {
        if (sSelf != null && sSelf.customView != null) {
            sSelf.hideFullscreen();
            return true;
        }
        if (sWeb != null && sWeb.getVisibility() == View.VISIBLE && sWeb.canGoBack()) {
            sWeb.goBack();
            return true;
        }
        return false;
    }

    private FrameLayout.LayoutParams lp(int top, int bottom) {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        p.topMargin = top;
        p.bottomMargin = bottom;
        return p;
    }

    private void ui(Runnable r) {
        getActivity().runOnUiThread(r);
    }

    private void state(String ev, WebView v, String u) {
        JSObject o = new JSObject();
        o.put("url", u == null ? "" : u);
        String t = v.getTitle();
        o.put("title", t == null ? "" : t);
        o.put("canGoBack", v.canGoBack());
        o.put("canGoForward", v.canGoForward());
        notifyListeners(ev, o);
    }

    private void showFullscreen(View view, WebChromeClient.CustomViewCallback cb) {
        if (customView != null) {
            cb.onCustomViewHidden();
            return;
        }
        customView = view;
        customCb = cb;
        ViewGroup root = (ViewGroup) getActivity().findViewById(android.R.id.content);
        root.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        getActivity().getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void hideFullscreen() {
        if (customView == null) return;
        ViewGroup root = (ViewGroup) getActivity().findViewById(android.R.id.content);
        root.removeView(customView);
        customView = null;
        if (customCb != null) {
            customCb.onCustomViewHidden();
            customCb = null;
        }
        getActivity().getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    private void ensure() {
        if (web != null) return;
        ViewGroup root = (ViewGroup) getActivity().findViewById(android.R.id.content);
        web = new WebView(getActivity());
        sWeb = web;
        sSelf = this;
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                String u = r.getUrl().toString();
                if (u.startsWith("http://") || u.startsWith("https://")) return false;
                JSObject o = new JSObject();
                o.put("url", u);
                notifyListeners("blocked", o);
                return true;
            }

            @Override
            public void onPageStarted(WebView v, String u, Bitmap f) {
                state("pageStarted", v, u);
            }

            @Override
            public void onPageFinished(WebView v, String u) {
                state("pageFinished", v, u);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView v, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                WebView.HitTestResult hr = v.getHitTestResult();
                int type = hr.getType();
                String extra = hr.getExtra();
                boolean link = isUserGesture && extra != null
                        && (type == WebView.HitTestResult.SRC_ANCHOR_TYPE || type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE)
                        && (extra.startsWith("http://") || extra.startsWith("https://"));
                if (link) {
                    v.loadUrl(extra);
                } else {
                    notifyListeners("popupBlocked", new JSObject());
                }
                return false;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback cb) {
                showFullscreen(view, cb);
            }

            @Override
            public void onHideCustomView() {
                hideFullscreen();
            }

            @Override
            public void onProgressChanged(WebView v, int p) {
                JSObject o = new JSObject();
                o.put("progress", p);
                notifyListeners("progress", o);
            }
        });

        web.setDownloadListener((url, ua, cd, mime, len) -> {
            Log.i("NevraDownload", "DownloadListener: url=" + url + ", mime=" + mime + ", length=" + len);
            JSObject attempt = new JSObject();
            attempt.put("url", url == null ? "" : url);
            attempt.put("mime", mime == null ? "" : mime);
            attempt.put("size", len);
            notifyListeners("downloadAttempt", attempt);
            try {
                if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
                    throw new IllegalArgumentException("jenis tautan unduhan belum didukung ("
                            + (url == null ? "kosong" : url.split(":")[0]) + ")");
                }
                String name = URLUtil.guessFileName(url, cd, mime);
                String ref = web.getUrl();
                long did;
                try {
                    did = enqueueDownload(url, ua, mime, name, ref, true);
                } catch (Exception first) {
                    Log.e("NevraDownload", "Percobaan 1 gagal", first);
                    try {
                        did = enqueueDownload(url, null, mime, name, null, false);
                    } catch (Exception second) {
                        throw new RuntimeException(first.getClass().getSimpleName() + ": " + first.getMessage()
                                + " | percobaan 2 - " + second.getClass().getSimpleName() + ": " + second.getMessage());
                    }
                }
                Log.i("NevraDownload", "enqueue berhasil, id=" + did + ", name=" + name);
                JSObject o = new JSObject();
                o.put("id", did);
                o.put("name", name);
                o.put("url", url);
                o.put("mime", mime == null ? "" : mime);
                o.put("size", len);
                notifyListeners("download", o);
            } catch (Exception e) {
                String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
                Log.e("NevraDownload", "Gagal memulai unduhan", e);
                Toast.makeText(getActivity(), "Unduhan gagal: " + msg, Toast.LENGTH_LONG).show();
                JSObject o = new JSObject();
                o.put("error", msg);
                o.put("url", url == null ? "" : url);
                notifyListeners("downloadError", o);
            }
        });

        root.addView(web, lp(0, 0));
    }

    private long enqueueDownload(String url, String ua, String mime, String name, String ref, boolean publicDir) {
        DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
        if (mime != null && !mime.isEmpty()) r.setMimeType(mime);
        r.setTitle(name);
        r.setDescription("Nevra Browser");
        if (publicDir) {
            String ck = CookieManager.getInstance().getCookie(url);
            if (ck != null) r.addRequestHeader("Cookie", ck);
            if (ua != null) r.addRequestHeader("User-Agent", ua);
            if (ref != null && ref.startsWith("http")) r.addRequestHeader("Referer", ref);
        }
        r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        if (publicDir) {
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
        } else {
            r.setDestinationInExternalFilesDir(getActivity(), Environment.DIRECTORY_DOWNLOADS, name);
        }
        DownloadManager dm = (DownloadManager) getActivity().getSystemService(Context.DOWNLOAD_SERVICE);
        return dm.enqueue(r);
    }

    @PluginMethod
    public void toast(final PluginCall call) {
        final String t = call.getString("text");
        ui(() -> {
            Toast.makeText(getActivity(), t == null ? "" : t, Toast.LENGTH_LONG).show();
            call.resolve();
        });
    }

    @PluginMethod
    public void open(final PluginCall call) {
        final String url = call.getString("url");
        final int top = call.getInt("top", 0);
        final int bottom = call.getInt("bottom", 0);
        ui(() -> {
            ensure();
            web.setLayoutParams(lp(top, bottom));
            web.setVisibility(View.VISIBLE);
            if (url != null && !url.isEmpty()) web.loadUrl(url);
            call.resolve();
        });
    }

    @PluginMethod
    public void show(final PluginCall call) {
        final int top = call.getInt("top", 0);
        final int bottom = call.getInt("bottom", 0);
        ui(() -> {
            if (web != null) {
                web.setLayoutParams(lp(top, bottom));
                web.setVisibility(View.VISIBLE);
            }
            call.resolve();
        });
    }

    @PluginMethod
    public void hide(final PluginCall call) {
        ui(() -> {
            if (web != null) web.setVisibility(View.GONE);
            call.resolve();
        });
    }

    @PluginMethod
    public void back(final PluginCall call) {
        ui(() -> {
            if (web != null && web.canGoBack()) web.goBack();
            call.resolve();
        });
    }

    @PluginMethod
    public void forward(final PluginCall call) {
        ui(() -> {
            if (web != null && web.canGoForward()) web.goForward();
            call.resolve();
        });
    }

    @PluginMethod
    public void reload(final PluginCall call) {
        ui(() -> {
            if (web != null) web.reload();
            call.resolve();
        });
    }

    @PluginMethod
    public void openDownloads(final PluginCall call) {
        try {
            Intent i = new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getActivity().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    private DownloadManager dm() {
        return (DownloadManager) getActivity().getSystemService(Context.DOWNLOAD_SERVICE);
    }

    private long idOf(PluginCall call) {
        Double d = call.getDouble("id");
        return d == null ? -1L : d.longValue();
    }

    @PluginMethod
    public void queryDownloads(final PluginCall call) {
        try {
            JSArray ids = call.getArray("ids");
            JSArray out = new JSArray();
            DownloadManager.Query q = new DownloadManager.Query();
            if (ids != null && ids.length() > 0) {
                long[] arr = new long[ids.length()];
                for (int i = 0; i < ids.length(); i++) arr[i] = ids.getLong(i);
                q.setFilterById(arr);
            }
            Cursor c = dm().query(q);
            if (c != null) {
                try {
                    while (c.moveToNext()) {
                        JSObject o = new JSObject();
                        o.put("id", c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)));
                        o.put("status", c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)));
                        o.put("total", c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)));
                        o.put("done", c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)));
                        String title = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE));
                        o.put("title", title == null ? "" : title);
                        String uri = c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_URI));
                        o.put("uri", uri == null ? "" : uri);
                        o.put("t", c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP)));
                        o.put("reason", c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)));
                        out.put(o);
                    }
                } finally {
                    c.close();
                }
            }
            JSObject res = new JSObject();
            res.put("items", out);
            call.resolve(res);
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    @PluginMethod
    public void openFile(final PluginCall call) {
        try {
            long id = idOf(call);
            Uri uri = dm().getUriForDownloadedFile(id);
            String mime = dm().getMimeTypeForDownloadedFile(id);
            if (uri == null) {
                call.reject("File tidak ditemukan");
                return;
            }
            if ("application/vnd.android.package-archive".equals(mime)) {
                openDownloads(call);
                return;
            }
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, mime == null ? "*/*" : mime);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            getActivity().startActivity(i);
            call.resolve();
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    @PluginMethod
    public void shareFile(final PluginCall call) {
        try {
            long id = idOf(call);
            Uri uri = dm().getUriForDownloadedFile(id);
            String mime = dm().getMimeTypeForDownloadedFile(id);
            if (uri == null) {
                call.reject("File tidak ditemukan");
                return;
            }
            Intent s = new Intent(Intent.ACTION_SEND);
            s.setType(mime == null ? "*/*" : mime);
            s.putExtra(Intent.EXTRA_STREAM, uri);
            s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent ch = Intent.createChooser(s, "Bagikan file");
            ch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getActivity().startActivity(ch);
            call.resolve();
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    @PluginMethod
    public void removeDownload(final PluginCall call) {
        try {
            dm().remove(idOf(call));
            call.resolve();
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }
}
