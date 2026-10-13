package com.tietie.calendar;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.Manifest;
import android.content.ContentValues;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.content.FileProvider;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String HOME = "https://" + HOST + "/assets/记录.html";
    private static final int REQUEST_FILE = 11;
    private static final int REQUEST_CAMERA = 12;
    private static final int REQUEST_BACKUP = 13;
    private static final int REQUEST_SAVE_PERMISSION = 14;

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;
    private File cameraFile;
    private File backupFile;
    private String backupName;
    private FileOutputStream backupOutput;
    private byte[] pendingSticker;
    private String pendingStickerName;
    private final Handler widgetHandler=new Handler(Looper.getMainLooper());
    private String widgetRequest;
    private ComponentName widgetProvider;
    private int[] widgetBeforeIds;
    private long widgetRequestedAt;
    private boolean activityResumed;
    private final Runnable widgetCheck=this::checkWidgetRequest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(246, 244, 237));
        getWindow().setNavigationBarColor(Color.rgb(255, 253, 249));
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(246, 244, 237));
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.addJavascriptInterface(new NativeBridge(), "AndroidApp");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return localAsset(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                return !("https".equals(uri.getScheme()) && HOST.equals(uri.getHost())
                        && uri.getPath() != null && uri.getPath().startsWith("/assets/"));
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                if (params.isCaptureEnabled() && wantsImage(params)) {
                    if (openCamera()) return true;
                }
                try {
                    Intent intent = params.createIntent();
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(intent, REQUEST_FILE);
                } catch (Exception error) {
                    fileCallback.onReceiveValue(null);
                    fileCallback = null;
                }
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this).setMessage(message)
                        .setPositiveButton("确定", (dialog, which) -> result.confirm())
                        .setNegativeButton("取消", (dialog, which) -> result.cancel())
                        .setOnCancelListener(dialog -> result.cancel()).show();
                return true;
            }
        });
        webView.loadUrl(HOME);
    }

    private boolean wantsImage(WebChromeClient.FileChooserParams params) {
        for (String type : params.getAcceptTypes()) {
            if (type != null && type.startsWith("image/")) return true;
        }
        return false;
    }

    private boolean openCamera() {
        try {
            File folder = new File(getCacheDir(), "captures");
            if (!folder.exists() && !folder.mkdirs()) return false;
            cameraFile = File.createTempFile("sticker_", ".jpg", folder);
            cameraUri = FileProvider.getUriForFile(this, getPackageName() + ".files", cameraFile);
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            intent.setClipData(ClipData.newUri(getContentResolver(), "贴纸照片", cameraUri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_CAMERA);
            return true;
        } catch (Exception error) {
            if (cameraFile != null) cameraFile.delete();
            cameraFile = null;
            cameraUri = null;
            return false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_BACKUP) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && backupFile != null) {
                try (InputStream in = new java.io.FileInputStream(backupFile);
                     OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if (out == null) throw new IllegalStateException("无法写入备份文件");
                    byte[] buffer = new byte[65536];
                    int count;
                    while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                } catch (Exception error) {
                    new AlertDialog.Builder(this).setMessage("备份保存失败，请重试").setPositiveButton("确定", null).show();
                }
            }
            if (backupFile != null) backupFile.delete();
            backupFile = null;
            return;
        }
        if (fileCallback == null) return;
        Uri[] selected = null;
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_CAMERA && cameraUri != null) {
                selected = new Uri[]{cameraUri};
            } else if (requestCode == REQUEST_FILE) {
                selected = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
            }
        }
        if (requestCode == REQUEST_CAMERA && selected == null && cameraFile != null) cameraFile.delete();
        cameraUri = null;
        cameraFile = null;
        fileCallback.onReceiveValue(selected);
        fileCallback = null;
    }

    private WebResourceResponse localAsset(Uri uri) {
        if (!"https".equals(uri.getScheme()) || !HOST.equals(uri.getHost())) return forbidden();
        List<String> parts = uri.getPathSegments();
        if (parts.size() < 2 || !"assets".equals(parts.get(0))) return forbidden();
        StringBuilder name = new StringBuilder();
        for (int i = 1; i < parts.size(); i++) {
            String part = parts.get(i);
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.contains("\\")) return forbidden();
            if (name.length() > 0) name.append('/');
            name.append(part);
        }
        try {
            InputStream stream = getAssets().open(name.toString());
            String path = name.toString();
            String mime = path.endsWith(".html") ? "text/html"
                    : path.endsWith(".js") ? "text/javascript"
                    : path.endsWith(".wasm") ? "application/wasm"
                    : path.endsWith(".ttf") ? "font/ttf"
                    : path.endsWith(".woff2") ? "font/woff2"
                    : path.endsWith(".json") ? "application/json" : "application/octet-stream";
            return new WebResourceResponse(mime, "UTF-8", stream);
        } catch (Exception error) {
            return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                    java.util.Collections.emptyMap(),
                    new ByteArrayInputStream("Missing app asset".getBytes(StandardCharsets.UTF_8)));
        }
    }

    private WebResourceResponse forbidden() {
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden",
                java.util.Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
    }

    private void saveStickerToGallery(String name, byte[] png) {
        if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            pendingSticker = png;
            pendingStickerName = name;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_SAVE_PERMISSION);
            return;
        }
        Uri uri = null;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            if (Build.VERSION.SDK_INT >= 29) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/贴贴");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
            }
            uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("无法创建图片");
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IllegalStateException("无法写入图片");
                out.write(png);
            }
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues done = new ContentValues();
                done.put(MediaStore.Images.Media.IS_PENDING, 0);
                getContentResolver().update(uri, done, null, null);
            }
            Toast.makeText(this, "贴纸已保存到相册", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            if (uri != null) getContentResolver().delete(uri, null, null);
            Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_SAVE_PERMISSION && pendingSticker != null) {
            byte[] png = pendingSticker;
            String name = pendingStickerName;
            pendingSticker = null;
            pendingStickerName = null;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                saveStickerToGallery(name, png);
            else Toast.makeText(this, "需要相册写入权限", Toast.LENGTH_SHORT).show();
        }
    }

    private void widgetStatus(String state,String message) {
        if(webView!=null)webView.evaluateJavascript("if(typeof widgetAddStatus==='function')widgetAddStatus("+JSONObject.quote(state)+","+JSONObject.quote(message)+")",null);
    }

    private void clearWidgetRequest() {
        widgetHandler.removeCallbacks(widgetCheck);widgetRequest=null;widgetProvider=null;widgetBeforeIds=null;
    }

    private void showWidgetHelp(String reason) {
        clearWidgetRequest();widgetStatus("manual",reason);
        new AlertDialog.Builder(this).setTitle("从桌面添加组件")
            .setMessage(reason+"\n\n1. 长按手机桌面空白处（或双指捏合）。\n2. 打开「组件」「原子组件」或「桌面挂件」。\n3. 找到「贴贴」，选择「今日」或「月历」，拖到桌面空白位置。")
            .setPositiveButton("打开桌面",(dialog,which)->{
                try {startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));}
                catch(Exception error){Toast.makeText(this,"请按手机主页键返回桌面",Toast.LENGTH_LONG).show();}
            }).setNegativeButton("知道了",null).show();
    }

    private void checkWidgetRequest() {
        if(widgetRequest==null||!activityResumed||!hasWindowFocus())return;
        String completed=getSharedPreferences("widget-pinning",MODE_PRIVATE).getString("completed","");
        boolean added=widgetRequest.equals(completed);
        try {
            int[] ids=AppWidgetManager.getInstance(this).getAppWidgetIds(widgetProvider);
            for(int id:ids){boolean old=false;for(int before:widgetBeforeIds)if(before==id)old=true;if(!old)added=true;}
        }catch(Exception ignored){}
        if(added){clearWidgetRequest();widgetStatus("added","组件已添加到桌面。");return;}
        long remaining=8000-(SystemClock.elapsedRealtime()-widgetRequestedAt);
        if(remaining>0){widgetHandler.removeCallbacks(widgetCheck);widgetHandler.postDelayed(widgetCheck,remaining);return;}
        showWidgetHelp("没有确认到组件已添加。如果桌面没有弹出确认窗口，请手动添加。");
    }

    private void requestDesktopWidget(String type) {
        if(widgetRequest!=null){widgetStatus("pending","请先完成当前的桌面添加请求。");return;}
        try {
            AppWidgetManager manager=AppWidgetManager.getInstance(this);
            if(!manager.isRequestPinAppWidgetSupported()) {showWidgetHelp("此手机桌面不支持应用内直接添加组件。");return;}
            widgetProvider=new ComponentName(this,"monthly".equals(type)?MonthlyStickerWidget.class:DailyStickerWidget.class);
            widgetBeforeIds=manager.getAppWidgetIds(widgetProvider);
            widgetRequest=java.util.UUID.randomUUID().toString();widgetRequestedAt=SystemClock.elapsedRealtime();
            Intent result=new Intent(this,WidgetPinReceiver.class).setData(Uri.parse("tietie://widget-pin/"+widgetRequest)).putExtra("request",widgetRequest);
            PendingIntent callback=PendingIntent.getBroadcast(this,0,result,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            // Use the standard resource preview: some launchers reject custom RemoteViews previews.
            boolean requested=manager.requestPinAppWidget(widgetProvider,null,callback);
            if(!requested){showWidgetHelp("手机桌面未接受添加请求，请从桌面组件列表添加。");return;}
            widgetStatus("pending","已向手机桌面发起请求，请确认系统弹窗。未弹出时可点「从桌面添加」。");
            Toast.makeText(this,"请确认桌面的组件添加提示",Toast.LENGTH_SHORT).show();
            widgetHandler.postDelayed(widgetCheck,8000);
        }catch(Exception error){android.util.Log.w("TietieWidget","Pin request failed",error);showWidgetHelp("组件添加请求未完成，请手动添加。");}
    }

    @Override protected void onResume(){super.onResume();activityResumed=true;widgetHandler.post(widgetCheck);}
    @Override protected void onPause(){activityResumed=false;super.onPause();}
    @Override public void onWindowFocusChanged(boolean hasFocus){super.onWindowFocusChanged(hasFocus);if(hasFocus)widgetHandler.post(widgetCheck);}

    private final class NativeBridge {
        @JavascriptInterface
        public boolean syncWidgets(String json) {
            try { StickerWidgets.save(MainActivity.this,json); return true; }
            catch(Exception error) { return false; }
        }

        @JavascriptInterface
        public void addWidget(String type) {
            runOnUiThread(() -> requestDesktopWidget(type));
        }

        @JavascriptInterface
        public void widgetHelp() {
            runOnUiThread(() -> showWidgetHelp("可直接从手机桌面添加「贴贴」组件。"));
        }

        @JavascriptInterface
        public void saveSticker(String name, String dataUrl) {
            try {
                if (dataUrl == null || !dataUrl.startsWith("data:image/png;base64,")) throw new IllegalArgumentException();
                byte[] png = Base64.decode(dataUrl.substring("data:image/png;base64,".length()), Base64.DEFAULT);
                if (png.length == 0 || png.length > 20 * 1024 * 1024) throw new IllegalArgumentException();
                String safeName = name == null ? "贴纸.png" : name.replaceAll("[^\\p{L}\\p{N}_.-]", "_");
                if (!safeName.endsWith(".png")) safeName += ".png";
                final String finalName = safeName;
                runOnUiThread(() -> saveStickerToGallery(finalName, png));
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "贴纸图片无效", Toast.LENGTH_SHORT).show());
            }
        }

        @JavascriptInterface
        public void vibrate() {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        }

        @JavascriptInterface
        public synchronized boolean beginBackup(String name) {
            try {
                if (backupOutput != null) backupOutput.close();
                backupFile = File.createTempFile("sticker_backup_", ".json", getCacheDir());
                backupOutput = new FileOutputStream(backupFile);
                backupName = name.replaceAll("[^\\p{L}\\p{N}_.-]", "_");
                return true;
            } catch (Exception error) {
                backupOutput = null;
                return false;
            }
        }

        @JavascriptInterface
        public synchronized boolean appendBackup(String chunk) {
            if (backupOutput == null) return false;
            try {
                backupOutput.write(chunk.getBytes(StandardCharsets.UTF_8));
                return true;
            } catch (Exception error) {
                return false;
            }
        }

        @JavascriptInterface
        public synchronized boolean finishBackup() {
            if (backupOutput == null) return false;
            try {
                backupOutput.close();
                backupOutput = null;
                runOnUiThread(() -> {
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    intent.putExtra(Intent.EXTRA_TITLE, backupName);
                    startActivityForResult(intent, REQUEST_BACKUP);
                });
                return true;
            } catch (Exception error) {
                backupOutput = null;
                return false;
            }
        }
    }

    @Override
    public void onBackPressed() {
        webView.evaluateJavascript("(function(){if(document.getElementById('overlay').classList.contains('on')){closeSheet();return true}if(TAB!=='calendar'){go('calendar');return true}return false})()",
                value -> { if (!"true".equals(value)) MainActivity.super.onBackPressed(); });
    }

    @Override
    protected void onStop() {
        if (webView != null) webView.evaluateJavascript("if(typeof lockPrivateZones==='function')lockPrivateZones()", null);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        clearWidgetRequest();
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        webView.destroy();
        super.onDestroy();
    }
}
