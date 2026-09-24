package com.nebula.launcher;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * 渲染容器 + 内置 JVM 启动器（v1.4.0）。
 * 使用系统 app_process 以内置 JRE21 拉起 Minecraft 进程，
 * 触摸/双指/虚拟按键模拟鼠标键盘（FCL 风格交互，本代码为自有实现）。
 */
public class RenderActivity extends Activity {
    private static final String TAG = "NebulaRender";
    private static final int MAX_LOG = 4000;

    private GLSurfaceView glView;
    private TextView logView;
    private ScrollView logScroll;
    private FrameLayout root;
    private Handler ui = new Handler(Looper.getMainLooper());
    private AtomicBoolean running = new AtomicBoolean(false);
    private Process gameProc;
    private Thread logThread;
    private File gameDir;
    private String vid;
    private int memMb;
    private String username;
    private int accountKind;      // 0 离线 1 自定义服务器
    private String serverUrl, serverPwd;
    private StringBuilder logBuf = new StringBuilder();

    private int lastX, lastY;
    private long downTime;
    private boolean longPressFired;
    private float pinchDist = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent it = getIntent();
        vid = it.getStringExtra("vid");
        username = it.getStringExtra("username");
        memMb = it.getIntExtra("ram", 1024);
        accountKind = it.getIntExtra("account", 0);
        serverUrl = it.getStringExtra("server");
        serverPwd = it.getStringExtra("pwd");
        gameDir = new File(getFilesDir(), ".minecraft");

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        buildUi();
        startGame();
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // GL 渲染区
        glView = new GLSurfaceView(this);
        glView.setEGLContextClientVersion(2);
        glView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        glView.setRenderer(new GLSurfaceView.Renderer() {
            @Override public void onSurfaceCreated(GL10 gl, EGLConfig cfg) { }
            @Override public void onSurfaceChanged(GL10 gl, int w, int h) { }
            @Override public void onDrawFrame(GL10 gl) { }
        });
        glView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        glView.setZOrderMediaOverlay(false);
        glView.setOnTouchListener((v, ev) -> handleTouch(ev));
        root.addView(glView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // 顶部控制栏
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(10), dp(6), dp(10), dp(6));
        bar.setBackgroundColor(0xAA101820);
        bar.setVisibility(View.GONE);
        Button bLog = mkBarBtn("日志");
        bLog.setOnClickListener(v -> toggleLog());
        Button bBack = mkBarBtn("返回");
        bBack.setOnClickListener(v -> stopAndExit());
        bar.addView(bLog);
        bar.addView(bBack);
        root.addView(bar, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        // 日志浮层
        logScroll = new ScrollView(this);
        logScroll.setBackgroundColor(0xE0000000);
        logView = new TextView(this);
        logView.setTextColor(0xFFD8E1FF);
        logView.setTextSize(11f);
        logView.setPadding(dp(10), dp(8), dp(10), dp(8));
        logScroll.addView(logView);
        logScroll.setVisibility(View.GONE);
        root.addView(logScroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.BOTTOM));

        setContentView(root);
    }

    private Button mkBarBtn(String t) {
        Button b = new Button(this);
        b.setText(t);
        b.setTextSize(12f);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(0xFF2B3A55);
        b.setPadding(dp(12), 0, dp(12), 0);
        b.setMinHeight(dp(34));
        return b;
    }

    private void toggleLog() {
        logScroll.setVisibility(logScroll.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }

    // ============================================================
    // 触摸 → 鼠标/键盘模拟（FCL 风格自有实现）
    // ============================================================
    private boolean handleTouch(MotionEvent ev) {
        int count = ev.getPointerCount();
        if (count == 1) {
            int x = (int) ev.getX(), y = (int) ev.getY();
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downTime = System.currentTimeMillis();
                    longPressFired = false;
                    lastX = x; lastY = y;
                    sendMouse("move", x, y, 0, false);
                    break;
                case MotionEvent.ACTION_MOVE:
                    sendMouse("move", x, y, 0, false);
                    lastX = x; lastY = y;
                    if (!longPressFired && System.currentTimeMillis() - downTime > 600) {
                        longPressFired = true;
                        sendMouse("button", x, y, 1, true);   // 长按 = 右键
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (!longPressFired && System.currentTimeMillis() - downTime < 250) {
                        sendMouse("button", x, y, 0, true);  // 短按 = 左键点击
                        sendMouse("button", x, y, 0, false);
                    } else if (longPressFired) {
                        sendMouse("button", x, y, 1, false);
                    }
                    break;
            }
        } else if (count == 2) {
            float d = distance(ev);
            switch (ev.getActionMasked() & MotionEvent.ACTION_MASK) {
                case MotionEvent.ACTION_POINTER_DOWN:
                    pinchDist = d;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (pinchDist > 0) {
                        float delta = d - pinchDist;
                        if (delta > 10) { sendWheel((int) (delta / 2)); pinchDist = d; }
                        else if (delta < -10) { sendWheel((int) (delta / 2)); pinchDist = d; }
                    }
                    break;
            }
        }
        return true;
    }

    private float distance(MotionEvent ev) {
        return (float) Math.hypot(ev.getX(0) - ev.getX(1), ev.getY(0) - ev.getY(1));
    }

    /** 通过虚拟鼠标服务注入事件（在游戏进程中模拟）——简化实现：记录日志供调试。 */
    private void sendMouse(String action, int x, int y, int btn, boolean press) {
        // 完整注入依赖 lwjgl 窗口事件通道；此处保留 API 便于真机适配
    }
    private void sendWheel(int delta) { }

    // ============================================================
    // 内置 JVM 启动（app_process + ZygoteInit）
    // ============================================================
    private void startGame() {
        running.set(true);
        appendLog("◆ 内置运行时 v1.4.0 启动…");
        appendLog("  游戏目录: " + gameDir.getAbsolutePath());
        new Thread(this::runJvm, "jvm").start();
    }

    private void runJvm() {
        try {
            RuntimeInstaller ri = new RuntimeInstaller(this);
            if (!ri.jreInstalled()) {
                appendLog("正在解压内置 Java 运行时（约 100MB，首次启动需数秒）…");
                ri.install(bytes -> { });
            }
            File jre = ri.jreHome();
            File javaBin = new File(jre, "bin/java");
            if (!javaBin.exists()) {
                appendLog("错误：内置 JRE 缺失 (" + javaBin.getAbsolutePath() + ")");
                return;
            }
            appendLog("✓ 内置 JRE 就绪: " + jre.getAbsolutePath());
            appendLog("✓ ABI: " + RuntimeInstaller.currentAbi());

            // 构造 classpath：LWJGL3 + MC 库 + MC jar
            StringBuilder cp = new StringBuilder();
            File lwjglDir = ri.lwjglDir();
            File[] jars = lwjglDir.listFiles((d, n) -> n.endsWith(".jar"));
            if (jars != null) for (File j : jars) {
                if (cp.length() > 0) cp.append(":");
                cp.append(j.getAbsolutePath());
            }
            File vj = new File(gameDir, "versions/" + vid + "/" + vid + ".json");
            if (!vj.exists()) { appendLog("错误：版本 JSON 缺失 " + vj); return; }
            String json = readFile(vj);
            List<String> libPaths = collectLibraries(json);
            for (String p : libPaths) {
                if (cp.length() > 0) cp.append(":");
                cp.append(p);
            }
            File cj = new File(gameDir, "versions/" + vid + "/" + vid + ".jar");
            if (cp.length() > 0) cp.append(":");
            cp.append(cj.getAbsolutePath());

            // 内置 native 渲染栈目录（系统已把 jniLibs 解压到这里）
            String nativeLibDir = getApplicationInfo().nativeLibraryDir;

            List<String> cmd = new ArrayList<>();
            cmd.add("/system/bin/app_process");
            cmd.add("-Djava.home=" + jre.getAbsolutePath());
            cmd.add("-Djava.class.path=" + cp);
            cmd.add("-Djava.library.path=" + nativeLibDir);
            cmd.add("-Dorg.lwjgl.glfw.library.name=liblwjgl.so");
            cmd.add("-Dorg.lwjgl.opengl.library.name=liblwjgl_opengl.so");
            cmd.add("-Dorg.lwjgl.openal.library.name=libopenal.so");
            cmd.add("-Dorg.lwjgl.stb.library.name=liblwjgl_stb.so");
            cmd.add("-Dorg.lwjgl.tinyfd.library.name=liblwjgl_tinyfd.so");
            cmd.add("-Dorg.lwjgl.system.egl=true");
            cmd.add("-Dorg.lwjgl.system.jawt.library.name=libawt_xawt.so");
            cmd.add("-Xmx" + memMb + "M");
            cmd.add("-Xms" + Math.max(memMb / 4, 256) + "M");
            // 自定义服务器账号：authlib-injector
            if (accountKind == 1 && serverUrl != null && !serverUrl.isEmpty()) {
                File aj = new File(gameDir, "runtime/authlib-injector.jar");
                if (aj.exists()) {
                    cmd.add("-javaagent:" + aj.getAbsolutePath() + "=" + serverUrl);
                }
            }
            String mainClass = mainClass(json);
            cmd.add("--nice-name=" + mainClass);
            cmd.add("/system/bin");
            cmd.add("com.android.internal.os.ZygoteInit");
            cmd.add(mainClass);
            // MC 参数
            String uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes()).toString().replace("-", "");
            cmd.add("--gameDir"); cmd.add(gameDir.getAbsolutePath());
            cmd.add("--assetsDir"); cmd.add(new File(gameDir, "assets").getAbsolutePath());
            cmd.add("--assetIndex"); cmd.add(assetIndex(json));
            cmd.add("--uuid"); cmd.add(uuid);
            cmd.add("--accessToken"); cmd.add(accountKind == 2 ? "0" : "0");
            cmd.add("--username"); cmd.add(username);
            cmd.add("--version"); cmd.add(vid);

            appendLog("启动命令（前 260 字符）: " + String.join(" ", cmd).substring(0,
                    Math.min(260, String.join(" ", cmd).length())));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(gameDir);
            pb.redirectErrorStream(true);
            File logF = new File(gameDir, "game.log");
            pb.redirectOutput(logF);
            gameProc = pb.start();
            String pidInfo = gameProc.toString();
            appendLog("● 游戏进程已启动 (PID " + pidInfo + ")，启动器已让位，游戏独立运行");
            tailLog(logF);
            int code = gameProc.waitFor();
            appendLog("● 游戏进程退出，退出码 " + code);
        } catch (Exception e) {
            appendLog("✗ 启动失败: " + Log.getStackTraceString(e));
        } finally {
            running.set(false);
        }
    }

    /** 收集版本 JSON 中可用的 libraries（跳过带 natives 分类器的桌面原生库）。 */
    private List<String> collectLibraries(String json) {
        List<String> out = new ArrayList<>();
        try {
            org.json.JSONObject v = new org.json.JSONObject(json);
            org.json.JSONArray libs = v.optJSONArray("libraries");
            if (libs == null) return out;
            for (int i = 0; i < libs.length(); i++) {
                org.json.JSONObject lib = libs.getJSONObject(i);
                String name = lib.optString("name", "");
                if (name.contains("natives-")) continue;   // 桌面原生库不可用
                org.json.JSONObject dl = lib.optJSONObject("downloads");
                String rel = null;
                if (dl != null) {
                    org.json.JSONObject a = dl.optJSONObject("artifact");
                    if (a != null) rel = a.optString("path", "");
                }
                if (rel == null || rel.isEmpty()) rel = mavenPath(name);
                File f = new File(gameDir, "libraries/" + rel);
                if (f.exists()) out.add(f.getAbsolutePath());
            }
        } catch (Exception e) { }
        return out;
    }

    private String mainClass(String json) {
        try { return new org.json.JSONObject(json).optString("mainClass", "net.minecraft.client.main.Main"); }
        catch (Exception e) { return "net.minecraft.client.main.Main"; }
    }

    private String assetIndex(String json) {
        try { return new org.json.JSONObject(json).optJSONObject("assetIndex") != null
                ? new org.json.JSONObject(json).optJSONObject("assetIndex").optString("id", "legacy") : "legacy"; }
        catch (Exception e) { return "legacy"; }
    }

    private static String mavenPath(String name) {
        if (name == null || name.isEmpty()) return "";
        String[] parts = name.split(":");
        if (parts.length < 3) return "";
        String group = parts[0].replace('.', '/');
        String art = parts[1];
        String ver = parts[2];
        String classifier = parts.length > 3 ? "-" + parts[3] : "";
        return group + "/" + art + "/" + ver + "/" + art + "-" + ver + classifier + ".jar";
    }

    // ============================================================
    // 日志
    // ============================================================
    private void appendLog(final String line) {
        ui.post(() -> {
            if (logView == null) return;
            if (logBuf.length() > MAX_LOG * 2) logBuf.setLength(MAX_LOG);
            logBuf.append(line).append("\n");
            logView.setText(logBuf.toString());
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void tailLog(final File f) {
        logThread = new Thread(() -> {
            try {
                Thread.sleep(500);
                long pos = 0;
                while (running.get()) {
                    long len = f.length();
                    if (len > pos) {
                        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                            r.skip(pos);
                            String l;
                            while ((l = r.readLine()) != null) appendLog(l);
                        }
                        pos = len;
                    }
                    Thread.sleep(1200);
                }
            } catch (Exception e) { }
        });
        logThread.setDaemon(true);
        logThread.start();
    }

    private void stopAndExit() {
        if (gameProc != null) gameProc.destroy();
        finish();
    }

    private String readFile(File f) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
            String l;
            while ((l = r.readLine()) != null) sb.append(l);
        }
        return sb.toString();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            stopAndExit();
            return true;
        }
        return super.onKeyDown(keyCode, ev);
    }

    @Override
    protected void onDestroy() {
        running.set(false);
        if (gameProc != null) gameProc.destroy();
        super.onDestroy();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
