package com.dev.ministudio;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

public class BuildTaskManager {

    public interface BuildListener {
        void onLogAppend(String text, int color);
        void onBuildStarted();
        void onBuildFinished(boolean success, String apkPath);
    }

    private final Context context; 
    private final String projectPath;
    private final BuildEnvironmentManager envManager; 
    private final BuildListener listener;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final int COLOR_INFO = Color.parseColor("#4FC3F7");
    private final int COLOR_SUCCESS = Color.parseColor("#81C784");
    private final int COLOR_ERROR = Color.parseColor("#FF8A80");
    private final int COLOR_WARNING = Color.parseColor("#FFB74D");

    public BuildTaskManager(Context context, String projectPath, BuildEnvironmentManager envManager, BuildListener listener) {
        this.context = context;
        this.projectPath = projectPath;
        this.envManager = envManager;
        this.listener = listener;
    }

    public void executeBuild() {
        new Thread(() -> {
            sendProgress("[CLOUD PIPELINE] เริ่มกระบวนการ Build...", COLOR_INFO);
            postUiEvent(BuildListener::onBuildStarted);

            SharedPreferences prefs = context.getSharedPreferences("GitHubPrefs", Context.MODE_PRIVATE);
            String username = prefs.getString("username", "").trim();
            String email = prefs.getString("email", "").trim();
            String token = prefs.getString("token", "").trim();

            if (username.isEmpty() || token.isEmpty()) {
                sendProgress("❌ กรุณากรอก GitHub Username และ Token ก่อน", COLOR_ERROR);
                postUiEvent(l -> l.onBuildFinished(false, null));
                return;
            }

            File projectDir = new File(projectPath);
            String repoName = projectDir.getName();

            createGitHubRepositoryIfNotExists(username, token, repoName);

            sendProgress("🧹 เคลียร์แคชเก่า...", COLOR_INFO);
            deleteDirectory(new File(projectDir, ".git"));
            deleteDirectory(new File(projectDir, "build"));
            deleteDirectory(new File(projectDir, "app/build"));

            sendProgress("⚙️ สร้าง Gradle + Workflow...", COLOR_INFO);
            
            // 🎯 ค่าตั้งต้นเผื่อกรณีอ่านไฟล์ไม่สำเร็จ
            String packageName = "com.example.default";
            String language = "Java"; 
            int minSdk = 21; 

            File appBuildGradle = new File(projectDir, "app/build.gradle");
            if (appBuildGradle.exists()) {
                try {
                    java.nio.file.Path path = appBuildGradle.toPath();
                    String content = new String(java.nio.file.Files.readAllBytes(path), "UTF-8");
                    
                    // 1. ค้นหา applicationId หรือ namespace เพื่อดึง Package Name ที่แท้จริง
                    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("applicationId\\s+'([^']+)'").matcher(content);
                    if (matcher.find()) {
                        packageName = matcher.group(1);
                    } else {
                        java.util.regex.Matcher nsMatcher = java.util.regex.Pattern.compile("namespace\\s+'([^']+)'").matcher(content);
                        if (nsMatcher.find()) {
                            packageName = nsMatcher.group(1);
                        }
                    }

                    // 2. ตรวจสอบภาษาที่ใช้จากปลั๊กอินหรือรูปแบบของ sourceSets ใน build.gradle เดิม
                    if (content.contains("kotlin-android") || content.contains("org.jetbrains.kotlin.android") || content.contains("src/main/kotlin")) {
                        language = "Kotlin";
                    }

                    // 3. ดึงค่า minSdk เดิมจากไฟล์คอมไพล์
                    java.util.regex.Matcher sdkMatcher = java.util.regex.Pattern.compile("minSdk\\s+([0-9]+)").matcher(content);
                    if (sdkMatcher.find()) {
                        minSdk = Integer.parseInt(sdkMatcher.group(1));
                    }

                } catch (Exception e) {
                    sendProgress("⚠️ ไม่สามารถอ่านการตั้งค่าเดิมของแอปได้: " + e.getMessage(), COLOR_WARNING);
                }
            }

            // 🌟 แก้ไขจุดบกพร่อง: ส่งพารามิเตอร์ 5 ตัวแปรครบถ้วนตามโครงสร้างใหม่
            if (envManager != null) {
                envManager.prepareGitHubWorkflow(projectPath, repoName, packageName, language, minSdk);
            }

            createGitIgnore(projectDir);

            Git git = null;
            try {
                sendProgress("📁 Init Git Repository...", COLOR_INFO);
                git = Git.init().setDirectory(projectDir).call();

                StoredConfig config = git.getRepository().getConfig();
                config.setString("user", null, "name", username);
                config.setString("user", null, "email", 
                    email.isEmpty() ? username + "@users.noreply.github.com" : email);
                config.save();

                git.add().addFilepattern(".").call();
                git.commit().setMessage("🚀 Build from MiniStudio - " + 
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).call();

                String remoteUrl = "https://github.com/" + username + "/" + repoName + ".git";
                sendProgress("📤 Pushing to GitHub...", COLOR_INFO);

                PushCommand pushCommand = git.push();
                pushCommand.setRemote(remoteUrl);
                pushCommand.setForce(true);
                pushCommand.setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, token));
                pushCommand.call();

                sendProgress("✅ Push สำเร็จ! กำลัง Build บนคลาวด์...", COLOR_SUCCESS);
                sendProgress("⏳ เริ่มตรวจสอบ Build...", COLOR_INFO);

                uiHandler.post(() -> {
                    listener.onBuildFinished(true, "CLOUD_SUCCESS");
                    startAutoCheckAndDownload(repoName);
                });

            } catch (Exception e) {
                sendProgress("❌ Push ล้มเหลว: " + e.getMessage(), COLOR_ERROR);
                e.printStackTrace();
                uiHandler.post(() -> listener.onBuildFinished(false, null));
            } finally {
                if (git != null) git.close();
            }
        }).start();
    }

    private void createGitHubRepositoryIfNotExists(String username, String token, String repoName) {
        try {
            URL url = new URL("https://api.github.com/repos/" + username + "/" + repoName);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Accept", "application/vnd.github+json");

            if (conn.getResponseCode() == 200) return;

            sendProgress("📦 สร้าง Repository ใหม่...", COLOR_INFO);
            URL createUrl = new URL("https://api.github.com/user/repos");
            HttpURLConnection createConn = (HttpURLConnection) createUrl.openConnection();
            createConn.setRequestMethod("POST");
            createConn.setRequestProperty("Authorization", "Bearer " + token);
            createConn.setRequestProperty("Content-Type", "application/json");
            createConn.setDoOutput(true);

            String json = "{\"name\":\"" + repoName + "\",\"private\":false,\"auto_init\":true}";
            try (OutputStream os = createConn.getOutputStream()) {
                os.write(json.getBytes("UTF-8"));
            }
        } catch (Exception ignored) {}
    }

    private void startAutoCheckAndDownload(final String repoName) {
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable[] checkRunnable = new Runnable[1];

        checkRunnable[0] = new Runnable() {
            @Override
            public void run() {
                sendProgress("🔄 กำลังตรวจสอบ Build ล่าสุด...", COLOR_INFO);

                DownloadTaskManager downloadTask = new DownloadTaskManager(context, repoName, 
                    new DownloadTaskManager.DownloadListener() {

                    @Override
                    public void onDownloadLog(String text, int color) {
                        if (text != null && !text.contains("404")) {
                            sendProgress(text, color);
                        }
                    }

                    @Override
                    public void onDownloadFinished(boolean success, File apkFile) {
                        if (success && apkFile != null && apkFile.exists() && apkFile.length() > 50000) {
                            sendProgress("🎉 Build สำเร็จ! ดาวน์โหลดและพร้อมติดตั้ง", COLOR_SUCCESS);
                        } else {
                            sendProgress("⏳ ยังไม่พร้อม... รออีก 10 วินาที", COLOR_WARNING);
                            handler.postDelayed(checkRunnable[0], 10000);
                        }
                    }
                });

                downloadTask.startFetchAndInstall();
            }
        };

        handler.postDelayed(checkRunnable[0], 8000);
    }

    private void createGitIgnore(File projectDir) {
        try {
            File gitIgnoreFile = new File(projectDir, ".gitignore");
            String content = ".gradle/\nbuild/\napp/build/\n*.iml\nlocal.properties\n";
            try (FileOutputStream fos = new FileOutputStream(gitIgnoreFile)) {
                fos.write(content.getBytes("UTF-8"));
            }
        } catch (Exception ignored) {}
    }

    private void deleteDirectory(File file) {
        if (file.exists()) {
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                if (files != null) {
                    for (File child : files) deleteDirectory(child);
                }
            }
            file.delete();
        }
    }

    private void sendProgress(final String text, final int color) {
        uiHandler.post(() -> listener.onLogAppend(text, color));
    }

    private void postUiEvent(final UiEventAction action) {
        uiHandler.post(() -> action.run(listener));
    }

    private interface UiEventAction {
        void run(BuildListener listener);
    }
}
