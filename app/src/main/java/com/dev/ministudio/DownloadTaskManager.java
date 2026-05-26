package com.dev.ministudio;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.core.content.FileProvider;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONObject;

public class DownloadTaskManager {

    public interface DownloadListener {
        void onDownloadLog(String text, int color);
        void onDownloadFinished(boolean success, File apkFile);
    }

    private final Context context;
    private final String projectName;
    private final DownloadListener listener;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final int COLOR_INFO = Color.parseColor("#4FC3F7");
    private final int COLOR_SUCCESS = Color.parseColor("#81C784");
    private final int COLOR_ERROR = Color.parseColor("#FF8A80");
    private final int COLOR_WARNING = Color.parseColor("#FFB74D");

    private int lastBytesProgress = -1;

    public DownloadTaskManager(Context context, String projectName, DownloadListener listener) {
        this.context = context;
        this.projectName = projectName;
        this.listener = listener;
    }

    public void startFetchAndInstall() {
        new Thread(() -> {
            try {
                SharedPreferences prefs = context.getSharedPreferences("GitHubPrefs", Context.MODE_PRIVATE);
                String username = prefs.getString("username", "").trim();
                String token = prefs.getString("token", "").trim();

                if (username.isEmpty() || token.isEmpty()) {
                    sendProgress("❌ กรุณาตั้งค่า GitHub Username + Token ก่อน", COLOR_ERROR);
                    uiHandler.post(() -> listener.onDownloadFinished(false, null));
                    return;
                }

                sendProgress("🔍 กำลังดึง Build ล่าสุด...", COLOR_INFO);

                String releasesUrl = "https://api.github.com/repos/" + username + "/" + projectName + "/releases?per_page=5";
                
                HttpURLConnection conn = (HttpURLConnection) new URL(releasesUrl).openConnection();
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setRequestProperty("Cache-Control", "no-cache, no-store");

                if (conn.getResponseCode() != 200) {
                    sendProgress("❌ ไม่พบ Release", COLOR_ERROR);
                    uiHandler.post(() -> listener.onDownloadFinished(false, null));
                    return;
                }

                JSONArray releases = new JSONArray(readStream(conn.getInputStream()));
                if (releases.length() == 0) {
                    sendProgress("⏳ ยังไม่มี Build", COLOR_WARNING);
                    uiHandler.post(() -> listener.onDownloadFinished(false, null));
                    return;
                }

                JSONObject latestRelease = releases.getJSONObject(0);
                JSONArray assets = latestRelease.getJSONArray("assets");

                String downloadUrl = null;
                String apkName = "app-debug.apk";

                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    String name = asset.getString("name");
                    if (name.endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url");
                        apkName = name;
                        break;
                    }
                }

                if (downloadUrl == null) {
                    sendProgress("⏳ พบ Release แต่ยังไม่มี APK", COLOR_WARNING);
                    uiHandler.post(() -> listener.onDownloadFinished(false, null));
                    return;
                }

                sendProgress("📥 กำลังดาวน์โหลด: " + apkName, COLOR_SUCCESS);

                HttpURLConnection dlConn = (HttpURLConnection) new URL(downloadUrl).openConnection();
                dlConn.setRequestProperty("Authorization", "Bearer " + token);
                dlConn.setRequestProperty("Cache-Control", "no-cache");

                File outputDir = new File(context.getExternalFilesDir(null), "build_output");
                outputDir.mkdirs();
                File apkFile = new File(outputDir, apkName);

                try (InputStream in = new BufferedInputStream(dlConn.getInputStream());
                     FileOutputStream fos = new FileOutputStream(apkFile)) {

                    byte[] data = new byte[8192];
                    long total = 0;
                    int count;
                    int fileLength = Math.max(dlConn.getContentLength(), 1);

                    while ((count = in.read(data)) != -1) {
                        total += count;
                        fos.write(data, 0, count);

                        int progress = (int) (total * 100 / fileLength);
                        if (progress % 10 == 0 && progress != lastBytesProgress) {
                            sendProgress("📥 ดาวน์โหลด " + progress + "%", COLOR_INFO);
                            lastBytesProgress = progress;
                        }
                    }
                }

                if (apkFile.exists() && apkFile.length() > 50000) {
                    sendProgress("🎉 ดาวน์โหลดสำเร็จ! กำลังติดตั้ง...", COLOR_SUCCESS);
                    uiHandler.post(() -> listener.onDownloadFinished(true, apkFile));
                    installApk(apkFile);
                } else {
                    sendProgress("⚠️ ไฟล์ APK ยังไม่สมบูรณ์ (ขนาด: " + apkFile.length() + " bytes)", COLOR_WARNING);
                    uiHandler.post(() -> listener.onDownloadFinished(false, null));
                }

            } catch (Exception e) {
                sendProgress("❌ Error: " + e.getMessage(), COLOR_ERROR);
                e.printStackTrace();
                uiHandler.post(() -> listener.onDownloadFinished(false, null));
            }
        }).start();
    }

    private String readStream(InputStream in) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        in.close();
        return sb.toString();
    }

    private void installApk(File file) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri apkUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            sendProgress("❌ ไม่สามารถเปิดหน้าติดตั้ง: " + e.getMessage(), COLOR_ERROR);
        }
    }

    private void sendProgress(final String text, final int color) {
        uiHandler.post(() -> listener.onDownloadLog(text, color));
    }
}
