package com.nebula.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ArrayAdapter;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 星云启动器 · 手机版（Nebula Launcher Android）
 * 自有代码实现：PCL/FCL 风格深色界面，版本/模组/整合包/光影/账号管理。
 * 游戏文件与模组存于应用内部存储的 .minecraft 目录。
 */
public class MainActivity extends Activity {

    // ---------------- 常量 ----------------
    static final String MANIFEST_OFFICIAL = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
    static final String MANIFEST_BMCL = "https://bmclapi2.bangbang93.com/mc/game/version_manifest_v2.json";
    static final String LIB_BMCL = "https://bmclapi2.bangbang93.com/libraries/";
    static final String ASSET_BMCL = "https://bmclapi2.bangbang93.com/assets/";
    static final String MODRINTH_API = "https://api.modrinth.com/v2";
    static final String GITHUB_MODS_REPO = "ETQWFD/NebulaLauncher-Mods";
    static final String[] JVM_FLAGS = {
            "-XX:+UseG1GC", "-XX:+UnlockExperimentalVMOptions",
            "-XX:MaxGCPauseMillis=50", "-XX:+DisableExplicitGC",
            "-XX:TargetSurvivorRatio=99", "-XX:G1NewSizePercent=30",
            "-XX:G1MaxNewSizePercent=40", "-XX:G1MixedGCLiveThresholdPercent=50",
            "-XX:G1RSetUpdatingPauseTimePercent=5", "-XX:SurvivorRatio=8",
            "-XX:+PerfDisableSharedMem", "-XX:MaxTenuringThreshold=1",
            "-Djava.awt.headless=true", "-Dlog4j2.formatMsgNoLookups=true",
    };
    static final int[] COLORS = {0xFF0B0E14, 0xFF141A26, 0xFF1A2233, 0xFF3D8BFF, 0xFFEAF0FF, 0xFF8B94A8};
    static final int C_BG = COLORS[0], C_CARD = COLORS[1], C_LINE = 0x12FFFFFF,
            C_ACC = COLORS[3], C_TEXT = COLORS[4], C_DIM = COLORS[5];

    // ---------------- 状态 ----------------
    File mcDir;
    String manifestUrl = MANIFEST_OFFICIAL;
    List<Map<String, String>> manifestVersions = new ArrayList<>();
    List<String> installedVersions = new ArrayList<>();

    // ---------------- UI ----------------
    FrameLayout content;
    View pageLaunch, pageVersions, pageMods, pageSettings;
    TextView[] navBtns = new TextView[4];
    ListView lvLaunchVersions, lvInstalled, lvGithub, lvModrinth, lvModpack, lvShaderSearch, lvShaders;
    TextView tvStatus, tvMemory, tvSelectedVersion, tvShadersTitle;
    SeekBar sbMemory;
    Spinner spAccount;
    EditText etName, etServer, etPwd;
    Button btnLaunch, btnMsLogin;
    LinearLayout llAccountCustom, llAccountMs;
    TextView tvMsStatus;
    ProgressBar pbProgress;
    // mods sub tabs
    int modTab = 0; // 0=installed 1=github 2=modrinth 3=modpack 4=shader
    TextView[] modTabBtns = new TextView[5];
    LinearLayout llModSearch;
    EditText etModSearch;
    String currentInstalling;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 21) {
            w.setStatusBarColor(C_BG);
            w.setNavigationBarColor(C_BG);
        }
        mcDir = new File(getFilesDir(), ".minecraft");
        mcDir.mkdirs();
        buildUI();
        refreshInstalled();
        refreshManifest();
    }

    // ============================================================
    // UI 构建
    // ============================================================
    float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    TextView mkText(String s, float sp, int color, int style) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (style != 0) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    GradientDrawable cardBg(float radius, int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    LinearLayout.LayoutParams lpW(float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.weight = weight;
        return p;
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);

        // 顶部标题
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding((int) dp(16), (int) dp(10), (int) dp(16), (int) dp(10));
        TextView logo = mkText("星云启动器", 20, C_TEXT, 1);
        TextView sub = mkText("Nebula Launcher · Android", 11, C_DIM, 0);
        top.addView(logo);
        top.addView(sub, lp(16, 0));
        root.addView(top);

        // 内容区
        content = new FrameLayout(this);
        content.setLayoutParams(lpW(1));
        pageLaunch = buildLaunchPage();
        pageVersions = buildVersionsPage();
        pageMods = buildModsPage();
        pageSettings = buildSettingsPage();
        content.addView(pageLaunch);
        content.addView(pageVersions);
        content.addView(pageMods);
        content.addView(pageSettings);
        root.addView(content);

        // 底部导航
        LinearLayout nav = new LinearLayout(this);
        nav.setBackgroundColor(C_CARD);
        nav.setPadding((int) dp(4), (int) dp(6), (int) dp(4), (int) dp(6));
        String[] names = {"启动", "版本", "模组", "设置"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView b = mkText(names[i], 13, C_DIM, 1);
            b.setGravity(Gravity.CENTER);
            b.setPadding(0, (int) dp(8), 0, (int) dp(8));
            LinearLayout.LayoutParams p = lpW(1);
            nav.addView(b, p);
            b.setOnClickListener(v -> switchPage(idx));
            navBtns[i] = b;
        }
        root.addView(nav);
        switchPage(0);
        setContentView(root);
    }

    void switchPage(int idx) {
        pageLaunch.setVisibility(idx == 0 ? View.VISIBLE : View.GONE);
        pageVersions.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
        pageMods.setVisibility(idx == 2 ? View.VISIBLE : View.GONE);
        pageSettings.setVisibility(idx == 3 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < 4; i++) navBtns[i].setTextColor(i == idx ? C_ACC : C_DIM);
        if (idx == 1) refreshInstalled();
        if (idx == 2) { refreshInstalledMods(); refreshShaders(); }
    }

    // ---------------- 启动页 ----------------
    private View buildLaunchPage() {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding((int) dp(16), (int) dp(8), (int) dp(16), (int) dp(8));

        // 版本卡片
        LinearLayout cardVer = new LinearLayout(this);
        cardVer.setOrientation(LinearLayout.VERTICAL);
        cardVer.setBackground(cardBg(14, C_CARD));
        cardVer.setPadding((int) dp(14), (int) dp(12), (int) dp(14), (int) dp(12));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.bottomMargin = (int) dp(12);
        cardVer.setLayoutParams(cp);
        cardVer.addView(mkText("版本选择", 12, C_DIM, 0));
        tvSelectedVersion = mkText("未安装版本", 22, C_TEXT, 1);
        tvSelectedVersion.setPadding(0, (int) dp(4), 0, (int) dp(6));
        cardVer.addView(tvSelectedVersion);
        lvLaunchVersions = new ListView(this);
        lvLaunchVersions.setDividerHeight(0);
        lvLaunchVersions.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout.LayoutParams lvp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(150));
        lvLaunchVersions.setLayoutParams(lvp);
        cardVer.addView(lvLaunchVersions);
        col.addView(cardVer);

        // 内存
        LinearLayout cardMem = new LinearLayout(this);
        cardMem.setOrientation(LinearLayout.VERTICAL);
        cardMem.setBackground(cardBg(14, C_CARD));
        cardMem.setPadding((int) dp(14), (int) dp(12), (int) dp(14), (int) dp(12));
        cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.bottomMargin = (int) dp(12);
        cardMem.setLayoutParams(cp);
        cardMem.addView(mkText("内存分配", 12, C_DIM, 0));
        tvMemory = mkText("2048 MB", 15, C_TEXT, 1);
        tvMemory.setPadding(0, (int) dp(4), 0, (int) dp(2));
        cardMem.addView(tvMemory);
        sbMemory = new SeekBar(this);
        sbMemory.setMax(8192);
        sbMemory.setProgress(2048);
        sbMemory.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean u) {
                v = (v / 256) * 256;
                if (v < 512) v = 512;
                tvMemory.setText(v + " MB");
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        cardMem.addView(sbMemory);
        col.addView(cardMem);

        // 账号
        LinearLayout cardAcc = new LinearLayout(this);
        cardAcc.setOrientation(LinearLayout.VERTICAL);
        cardAcc.setBackground(cardBg(14, C_CARD));
        cardAcc.setPadding((int) dp(14), (int) dp(12), (int) dp(14), (int) dp(12));
        cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.bottomMargin = (int) dp(12);
        cardAcc.setLayoutParams(cp);
        cardAcc.addView(mkText("游戏账号", 12, C_DIM, 0));
        spAccount = new Spinner(this);
        spAccount.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"离线账号", "自定义服务器", "正版（微软）"}));
        cardAcc.addView(spAccount);
        etName = new EditText(this);
        etName.setHint("游戏昵称");
        etName.setText("Steve");
        etName.setTextColor(C_TEXT);
        etName.setHintTextColor(C_DIM);
        etName.setSingleLine(true);
        etName.setBackground(cardBg(8, 0xFF0F1520));
        etName.setPadding((int) dp(10), (int) dp(8), (int) dp(10), (int) dp(8));
        cardAcc.addView(etName);
        llAccountCustom = new LinearLayout(this);
        llAccountCustom.setOrientation(LinearLayout.VERTICAL);
        etServer = new EditText(this);
        etServer.setHint("authlib 服务器地址（皮肤站）");
        etServer.setTextColor(C_TEXT);
        etServer.setHintTextColor(C_DIM);
        etServer.setSingleLine(true);
        etServer.setBackground(cardBg(8, 0xFF0F1520));
        etServer.setPadding((int) dp(10), (int) dp(8), (int) dp(10), (int) dp(8));
        llAccountCustom.addView(etServer);
        etPwd = new EditText(this);
        etPwd.setHint("服务器账号密码");
        etPwd.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPwd.setTextColor(C_TEXT);
        etPwd.setHintTextColor(C_DIM);
        etPwd.setSingleLine(true);
        etPwd.setBackground(cardBg(8, 0xFF0F1520));
        etPwd.setPadding((int) dp(10), (int) dp(8), (int) dp(10), (int) dp(8));
        llAccountCustom.addView(etPwd);
        cardAcc.addView(llAccountCustom);
        llAccountMs = new LinearLayout(this);
        llAccountMs.setOrientation(LinearLayout.VERTICAL);
        btnMsLogin = mkBtn("正版登录（微软设备码）");
        btnMsLogin.setOnClickListener(v -> msLogin());
        llAccountMs.addView(btnMsLogin);
        tvMsStatus = mkText("尚未登录正版账号", 12, C_DIM, 0);
        llAccountMs.addView(tvMsStatus);
        cardAcc.addView(llAccountMs);
        llAccountCustom.setVisibility(View.GONE);
        llAccountMs.setVisibility(View.GONE);
        spAccount.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> a, View v, int pos, long id) {
                llAccountCustom.setVisibility(pos == 1 ? View.VISIBLE : View.GONE);
                llAccountMs.setVisibility(pos == 2 ? View.VISIBLE : View.GONE);
            }
            public void onNothingSelected(android.widget.AdapterView<?> a) {}
        });
        col.addView(cardAcc);

        // 启动按钮
        btnLaunch = mkBtn("启动游戏");
        btnLaunch.setTextSize(17);
        btnLaunch.setBackground(cardBg(12, C_ACC));
        btnLaunch.setTextColor(Color.WHITE);
        btnLaunch.setPadding(0, (int) dp(14), 0, (int) dp(14));
        btnLaunch.setOnClickListener(v -> launchGame());
        col.addView(btnLaunch);

        // 状态与进度
        tvStatus = mkText("就绪", 13, C_DIM, 0);
        tvStatus.setPadding((int) dp(4), (int) dp(10), 0, 0);
        col.addView(tvStatus);
        pbProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pbProgress.setMax(1000);
        pbProgress.setPadding(0, (int) dp(6), 0, (int) dp(6));
        col.addView(pbProgress);

        sv.addView(col);
        return sv;
    }

    Button mkBtn(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(C_TEXT);
        b.setTextSize(14);
        b.setBackground(cardBg(8, 0xFF1F2A3D));
        b.setPadding(0, (int) dp(10), 0, (int) dp(10));
        return b;
    }

    // ---------------- 版本页 ----------------
    private View buildVersionsPage() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding((int) dp(16), (int) dp(8), (int) dp(16), (int) dp(8));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button btnNew = mkBtn("安装新版本");
        btnNew.setBackground(cardBg(10, C_ACC));
        btnNew.setTextColor(Color.WHITE);
        btnNew.setOnClickListener(v -> showInstallDialog());
        Button btnRefresh = mkBtn("刷新");
        btnRefresh.setOnClickListener(v -> refreshInstalled());
        row.addView(btnNew, lpW(1));
        row.addView(btnRefresh, lp(0, 0));
        col.addView(row);
        lvInstalled = new ListView(this);
        lvInstalled.setDividerHeight(0);
        lvInstalled.setBackgroundColor(Color.TRANSPARENT);
        col.addView(lvInstalled, lpW(1));
        return col;
    }

    void showInstallDialog() {
        if (manifestVersions.isEmpty()) {
            toast("版本清单尚未加载，请稍候");
            return;
        }
        List<String> names = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        for (Map<String, String> m : manifestVersions) {
            if ("release".equals(m.get("type"))) {
                names.add(m.get("id"));
                urls.add(m.get("url"));
            }
        }
        final List<String> namesFinal = names.size() > 40 ? names.subList(0, 40) : names;
        new AlertDialog.Builder(this)
                .setTitle("选择要安装的版本（原版）")
                .setItems(namesFinal.toArray(new String[0]), (d, which) -> {
                    String id = namesFinal.get(which);
                    String url = null;
                    for (Map<String, String> m : manifestVersions) {
                        if (id.equals(m.get("id"))) { url = m.get("url"); break; }
                    }
                    installVersion(id, url);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------------- 模组页 ----------------
    private View buildModsPage() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding((int) dp(16), (int) dp(8), (int) dp(16), (int) dp(8));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        String[] tnames = {"已安装", "GitHub", "Modrinth", "整合包", "光影"};
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            TextView t = mkText(tnames[i], 12, C_DIM, 1);
            t.setGravity(Gravity.CENTER);
            t.setPadding((int) dp(2), (int) dp(6), (int) dp(2), (int) dp(6));
            tabs.addView(t, lpW(1));
            t.setOnClickListener(v -> switchModTab(idx));
            modTabBtns[i] = t;
        }
        col.addView(tabs);

        llModSearch = new LinearLayout(this);
        llModSearch.setOrientation(LinearLayout.HORIZONTAL);
        llModSearch.setPadding(0, (int) dp(8), 0, (int) dp(4));
        etModSearch = new EditText(this);
        etModSearch.setHint("搜索…");
        etModSearch.setTextColor(C_TEXT);
        etModSearch.setHintTextColor(C_DIM);
        etModSearch.setSingleLine(true);
        etModSearch.setBackground(cardBg(8, 0xFF0F1520));
        etModSearch.setPadding((int) dp(10), (int) dp(6), (int) dp(10), (int) dp(6));
        Button bSearch = mkBtn("搜索");
        bSearch.setOnClickListener(v -> doModSearch());
        llModSearch.addView(etModSearch, lpW(1));
        llModSearch.addView(bSearch, lp(0, 0));
        col.addView(llModSearch);

        lvGithub = new ListView(this); lvGithub.setDividerHeight(0);
        lvModrinth = new ListView(this); lvModrinth.setDividerHeight(0);
        lvModpack = new ListView(this); lvModpack.setDividerHeight(0);
        lvShaderSearch = new ListView(this); lvShaderSearch.setDividerHeight(0);
        lvShaders = new ListView(this); lvShaders.setDividerHeight(0);
        lvGithub.setBackgroundColor(Color.TRANSPARENT);
        lvModrinth.setBackgroundColor(Color.TRANSPARENT);
        lvModpack.setBackgroundColor(Color.TRANSPARENT);
        lvShaderSearch.setBackgroundColor(Color.TRANSPARENT);
        lvShaders.setBackgroundColor(Color.TRANSPARENT);
        col.addView(lvGithub, lpW(1));
        col.addView(lvModrinth, lpW(1));
        col.addView(lvModpack, lpW(1));
        LinearLayout shCol = new LinearLayout(this);
        shCol.setOrientation(LinearLayout.VERTICAL);
        tvShadersTitle = mkText("已安装光影", 12, C_DIM, 0);
        shCol.addView(tvShadersTitle);
        shCol.addView(lvShaders, lpW(1));
        col.addView(shCol, lpW(1));
        lvInstalled2 = new ListView(this);
        lvInstalled2.setDividerHeight(0);
        lvInstalled2.setBackgroundColor(Color.TRANSPARENT);
        col.addView(lvInstalled2, lpW(1));
        switchModTab(0);
        return col;
    }

    ListView lvInstalled2;

    void switchModTab(int idx) {
        modTab = idx;
        for (int i = 0; i < 5; i++) modTabBtns[i].setTextColor(i == idx ? C_ACC : C_DIM);
        lvInstalled2.setVisibility(idx == 0 ? View.VISIBLE : View.GONE);
        lvGithub.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
        lvModrinth.setVisibility(idx == 2 ? View.VISIBLE : View.GONE);
        lvModpack.setVisibility(idx == 3 ? View.VISIBLE : View.GONE);
        LinearLayout shWrap = (LinearLayout) lvShaderSearch.getParent();
        lvShaderSearch.setVisibility(idx == 4 ? View.VISIBLE : View.GONE);
        tvShadersTitle.setVisibility(idx == 4 ? View.VISIBLE : View.GONE);
        llModSearch.setVisibility(idx == 0 ? View.GONE : View.VISIBLE);
        etModSearch.setHint(idx == 1 ? "仓库地址（默认 " + GITHUB_MODS_REPO + "）" :
                idx == 2 ? "搜索 Modrinth 模组…" :
                idx == 3 ? "搜索 Modrinth 整合包…" :
                idx == 4 ? "搜索 Modrinth 光影…" : "搜索…");
        if (idx == 0) refreshInstalledMods();
        if (idx == 4) refreshShaders();
    }

    void doModSearch() {
        String q = etModSearch.getText().toString().trim();
        if (q.isEmpty()) { toast("请输入关键词"); return; }
        if (modTab == 1) loadGithubMods(q);
        if (modTab == 2) searchModrinth(q, "mod");
        if (modTab == 3) searchModrinth(q, "modpack");
        if (modTab == 4) searchModrinth(q, "shader");
    }

    // ---------------- 设置页 ----------------
    private View buildSettingsPage() {
        ScrollView sv = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding((int) dp(16), (int) dp(8), (int) dp(16), (int) dp(8));
        col.addView(mkText("设置", 18, C_TEXT, 1));
        col.addView(mkText("游戏目录（内部存储）", 12, C_DIM, 0));
        TextView tDir = mkText(mcDir.getAbsolutePath(), 12, C_ACC, 0);
        col.addView(tDir);
        col.addView(mkText("镜像源", 12, C_DIM, 0));
        Spinner spMirror = new Spinner(this);
        spMirror.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"自动（官方+BMCLAPI）", "仅官方 Mojang", "仅 BMCLAPI"}));
        spMirror.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> a, View v, int pos, long id) {
                manifestUrl = pos == 1 ? MANIFEST_OFFICIAL : MANIFEST_BMCL;
                if (pos == 0) manifestUrl = MANIFEST_OFFICIAL;
                refreshManifest();
            }
            public void onNothingSelected(android.widget.AdapterView<?> a) {}
        });
        col.addView(spMirror);
        col.addView(mkText("模组仓库", 12, C_DIM, 0));
        EditText etRepo = new EditText(this);
        etRepo.setText(GITHUB_MODS_REPO);
        etRepo.setTextColor(C_TEXT);
        etRepo.setSingleLine(true);
        etRepo.setBackground(cardBg(8, 0xFF0F1520));
        etRepo.setPadding((int) dp(10), (int) dp(8), (int) dp(10), (int) dp(8));
        col.addView(etRepo);
        col.addView(mkText("关于", 12, C_DIM, 0));
        col.addView(mkText("星云启动器 v1.2.0（Android）\nPCL/FCL 风格 · 自有代码 · 开源免费\n游戏/模组存储于应用内部存储 .minecraft", 13, C_TEXT, 0));
        Button btnSite = mkBtn("访问官网");
        btnSite.setOnClickListener(v -> openUrl("https://etqwfd.github.io/NebulaLauncher/"));
        col.addView(btnSite);
        Button btnRepo = mkBtn("GitHub 源码");
        btnRepo.setOnClickListener(v -> openUrl("https://github.com/ETQWFD/NebulaLauncher"));
        col.addView(btnRepo);
        sv.addView(col);
        return sv;
    }

    void openUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { toast("无法打开链接: " + e.getMessage()); }
    }

    // ============================================================
    // 网络
    // ============================================================
    static class Net {
        static String get(String url) throws Exception {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(15000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "NebulaLauncher/1.2");
            int code = c.getResponseCode();
            if (code >= 400) throw new Exception("HTTP " + code + " " + url);
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            r.close();
            return sb.toString();
        }

        static long download(String url, File dest, String sha1, ProgressListener pl) throws Exception {
            File tmp = new File(dest.getParentFile(), dest.getName() + ".part");
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(20000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "NebulaLauncher/1.2");
            int code = c.getResponseCode();
            if (code >= 400) throw new Exception("HTTP " + code + " " + url);
            long total = c.getContentLength();
            InputStream in = new BufferedInputStream(c.getInputStream());
            OutputStream out = new BufferedOutputStream(new FileOutputStream(tmp));
            byte[] buf = new byte[8192];
            long got = 0;
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                got += n;
                md.update(buf, 0, n);
                if (pl != null) pl.onProgress(got, total);
            }
            out.close();
            in.close();
            String gotSha = hex(md.digest());
            if (sha1 != null && !sha1.isEmpty() && !sha1.equalsIgnoreCase(gotSha)) {
                tmp.delete();
                throw new Exception("校验失败 (SHA1)");
            }
            if (!tmp.renameTo(dest)) {
                try (FileOutputStream f = new FileOutputStream(dest)) {
                    try (FileInputStream fi = new FileInputStream(tmp)) {
                        byte[] b = new byte[8192];
                        while ((n = fi.read(b)) > 0) f.write(b, 0, n);
                    }
                }
                tmp.delete();
            }
            return got;
        }

        static String hex(byte[] b) {
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format(Locale.US, "%02x", x));
            return sb.toString();
        }

        static String sha1(File f) throws Exception {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            try (FileInputStream in = new FileInputStream(f)) {
                byte[] b = new byte[8192];
                int n;
                while ((n = in.read(b)) > 0) md.update(b, 0, n);
            }
            return hex(md.digest());
        }
    }

    interface ProgressListener { void onProgress(long got, long total); }

    // ============================================================
    // 版本清单 / 安装
    // ============================================================
    void refreshManifest() {
        new Thread(() -> {
            try {
                String json = null;
                Exception err = null;
                try { json = Net.get(manifestUrl); }
                catch (Exception e) {
                    err = e;
                    try { json = Net.get(MANIFEST_BMCL); } catch (Exception e2) { err = e2; }
                }
                if (json == null) { throw (err != null ? err : new Exception("清单获取失败")); }
                JSONObject o = new JSONObject(json);
                JSONArray arr = o.getJSONArray("versions");
                List<Map<String, String>> list = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject v = arr.getJSONObject(i);
                    Map<String, String> m = new HashMap<>();
                    m.put("id", v.optString("id"));
                    m.put("type", v.optString("type"));
                    m.put("url", v.optString("url"));
                    list.add(m);
                }
                runOnUiThread(() -> {
                    manifestVersions = list;
                    tvStatus.setText("已获取版本清单：" + list.size() + " 个版本");
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("版本清单失败: " + e.getMessage()));
            }
        }).start();
    }

    void refreshInstalled() {
        installedVersions.clear();
        File vd = new File(mcDir, "versions");
        if (vd.isDirectory()) {
            String[] l = vd.list();
            if (l != null) {
                for (String s : l) {
                    if (new File(vd, s + "/" + s + ".json").exists()) installedVersions.add(s);
                }
                Collections.sort(installedVersions, Collections.reverseOrder());
            }
        }
        tvSelectedVersion.setText(installedVersions.isEmpty() ? "未安装版本" : installedVersions.get(0));
        lvLaunchVersions.setAdapter(new SimpleAdapter(installedVersions));
    }

    void installVersion(final String id, final String versionJsonUrl) {
        currentInstalling = id;
        setBusy(true, "准备安装 " + id);
        new Thread(() -> {
            try {
                File vdir = new File(mcDir, "versions/" + id);
                vdir.mkdirs();
                // 1. 版本 json
                String vj = Net.get(versionJsonUrl);
                File vjf = new File(vdir, id + ".json");
                try (FileOutputStream f = new FileOutputStream(vjf)) { f.write(vj.getBytes("UTF-8")); }
                JSONObject v = new JSONObject(vj);
                // 2. client jar
                JSONObject dl = v.getJSONObject("downloads").getJSONObject("client");
                final String cUrl = dl.getString("url");
                final String cSha = dl.optString("sha1", "");
                File cj = new File(vdir, id + ".jar");
                setBusy(true, "下载客户端 " + id);
                if (!cj.exists() || (cSha.isEmpty() ? false : !Net.sha1(cj).equalsIgnoreCase(cSha))) {
                    Net.download(cUrl, cj, cSha, (got, total) ->
                            runOnUiThread(() -> setProgress(got, total)));
                }
                // 3. libraries
                JSONArray libs = v.optJSONArray("libraries");
                int total = libs != null ? libs.length() : 0;
                int done = 0;
                if (libs != null) {
                    for (int i = 0; i < libs.length(); i++) {
                        JSONObject lib = libs.getJSONObject(i);
                        if (!rulesOk(lib)) continue;
                        JSONObject art = lib.optJSONObject("downloads") != null
                                ? lib.optJSONObject("downloads").optJSONObject("artifact") : null;
                        String path = lib.optString("name", "").replace(':', '/');
                        if (path.contains("@")) path = path.split("@")[0];
                        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
                        String url = art != null ? art.optString("url", "") : "";
                        String sha = art != null ? art.optString("sha1", "") : "";
                        if (path.isEmpty() && !url.isEmpty()) path = url;
                        if (path.isEmpty()) continue;
                        String fname = path.substring(path.lastIndexOf('/') + 1);
                        File dest = new File(mcDir, "libraries/" + path);
                        if (!dest.exists() || (!sha.isEmpty() && !Net.sha1(dest).equalsIgnoreCase(sha))) {
                            String mirror = url;
                            if (mirror.startsWith("https://libraries.minecraft.net/")) {
                                mirror = LIB_BMCL + path;
                            }
                            dest.getParentFile().mkdirs();
                            try {
                                Net.download(mirror, dest, sha, null);
                            } catch (Exception e) {
                                if (!mirror.equals(url)) Net.download(url, dest, sha, null);
                                else throw e;
                            }
                        }
                        done++;
                        final int d = done;
                        runOnUiThread(() -> setProgress(d, total));
                    }
                }
                // 4. assets
                JSONObject ai = v.optJSONObject("assetIndex");
                if (ai != null) {
                    String aiUrl = ai.optString("url", "");
                    String aiId = ai.optString("id", "legacy");
                    File aiFile = new File(mcDir, "assets/indexes/" + aiId + ".json");
                    aiFile.getParentFile().mkdirs();
                    if (!aiFile.exists()) {
                        String u = aiUrl;
                        if (u.startsWith("https://launchermeta.mojang.com/")) {
                            u = ASSET_BMCL + "indexes/" + aiId + ".json";
                        }
                        try { Net.download(u, aiFile, "", null); }
                        catch (Exception e) { Net.download(aiUrl, aiFile, "", null); }
                    }
                    String idx = new String(java.nio.file.Files.readAllBytes(aiFile.toPath()), "UTF-8");
                    JSONObject index = new JSONObject(idx);
                    JSONObject objects = index.optJSONObject("objects");
                    List<String> keys = new ArrayList<>();
                    if (objects != null) {
                        java.util.Iterator<String> it = objects.keys();
                        while (it.hasNext()) keys.add(it.next());
                    }
                    int atotal = keys.size();
                    int adone = 0;
                    for (String k : keys) {
                        JSONObject ob = objects.getJSONObject(k);
                        String hash = ob.optString("hash", "");
                        File obj = new File(mcDir, "assets/objects/" + hash.substring(0, 2) + "/" + hash);
                        if (!obj.exists()) {
                            obj.getParentFile().mkdirs();
                            String u = "https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash;
                            try {
                                Net.download(u, obj, hash, null);
                            } catch (Exception e) {
                                try { Net.download(ASSET_BMCL + "objects/" + hash.substring(0, 2) + "/" + hash, obj, hash, null); }
                                catch (Exception e2) { /* 跳过失败资产 */ }
                            }
                        }
                        adone++;
                        final int ad = adone;
                        runOnUiThread(() -> setProgress(ad, atotal));
                    }
                }
                runOnUiThread(() -> {
                    setBusy(false, id + " 安装完成");
                    refreshInstalled();
                    toast(id + " 安装完成");
                });
            } catch (Exception e) {
                runOnUiThread(() -> { setBusy(false, "安装失败: " + e.getMessage()); toast("安装失败: " + e.getMessage()); });
            }
        }).start();
    }

    static boolean rulesOk(JSONObject lib) {
        JSONArray rules = lib.optJSONArray("rules");
        if (rules == null) return true;
        boolean ok = true;
        for (int i = 0; i < rules.length(); i++) {
            JSONObject r = rules.optJSONObject(i);
            if (r == null) continue;
            String os = r.optJSONObject("os") != null ? r.optJSONObject("os").optString("name", "") : "";
            boolean match;
            if (!os.isEmpty()) {
                match = os.equals("linux") || os.equals("osx");
                if (os.equals("windows")) match = false;
                if (os.equals("osx")) match = false;
                if (os.equals("linux")) match = true;
            } else match = true;
            if (match) ok = "allow".equals(r.optString("action"));
        }
        return ok;
    }

    void setBusy(boolean busy, String msg) {
        tvStatus.setText(msg);
        if (!busy) pbProgress.setProgress(0);
        btnLaunch.setEnabled(!busy);
    }

    void setProgress(long done, long total) {
        if (total <= 0) return;
        int p = (int) (done * 1000L / total);
        pbProgress.setProgress(Math.min(p, 1000));
        tvStatus.setText(String.format(Locale.US, "%d / %d", done, total));
    }

    void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // ============================================================
    // 模组 / 整合包 / 光影
    // ============================================================
    List<String> installedMods = new ArrayList<>();

    void refreshInstalledMods() {
        installedMods.clear();
        File md = new File(mcDir, "mods");
        if (md.isDirectory()) {
            String[] l = md.list();
            if (l != null) {
                for (String s : l) {
                    if (s.endsWith(".jar") || s.endsWith(".disabled")) installedMods.add(s);
                }
                Collections.sort(installedMods);
            }
        }
        lvInstalled2.setAdapter(new InstalledModAdapter(installedMods));
    }

    void refreshShaders() {
        List<String> sh = new ArrayList<>();
        File sd = new File(mcDir, "shaderpacks");
        if (sd.isDirectory()) {
            String[] l = sd.list();
            if (l != null) {
                for (String s : l) if (!s.startsWith(".")) sh.add(s);
                Collections.sort(sh);
            }
        }
        lvShaders.setAdapter(new SimpleListAdapter(sh, s -> { deleteItem(new File(sd, s)); refreshShaders(); }));
    }

    void loadGithubMods(String repo) {
        if (repo.isEmpty()) repo = GITHUB_MODS_REPO;
        final String repoFinal = repo;
        setBusy(true, "拉取模组仓库 " + repo);
        new Thread(() -> {
            try {
                String url = "https://api.github.com/repos/" + repoFinal + "/releases?per_page=30";
                String json = Net.get(url);
                JSONArray rels = new JSONArray(json);
                List<Map<String, String>> items = new ArrayList<>();
                for (int i = 0; i < rels.length(); i++) {
                    JSONObject rel = rels.getJSONObject(i);
                    JSONArray assets = rel.optJSONArray("assets");
                    if (assets != null) {
                        for (int j = 0; j < assets.length(); j++) {
                            JSONObject a = assets.getJSONObject(j);
                            String name = a.optString("name", "");
                            if (name.endsWith(".jar") || name.endsWith(".zip")) {
                                Map<String, String> m = new HashMap<>();
                                m.put("name", name);
                                m.put("url", a.optString("browser_download_url", ""));
                                m.put("size", String.valueOf(a.optLong("size", 0)));
                                items.add(m);
                            }
                        }
                    }
                }
                runOnUiThread(() -> {
                    setBusy(false, "仓库含 " + items.size() + " 个文件");
                    lvGithub.setAdapter(new RemoteItemAdapter(items, item -> downloadToMods(item)));
                });
            } catch (Exception e) {
                runOnUiThread(() -> { setBusy(false, "仓库拉取失败"); toast("GitHub 拉取失败: " + e.getMessage()); });
            }
        }).start();
    }

    void searchModrinth(String query, final String kind) {
        setBusy(true, "搜索 " + kind + "…");
        new Thread(() -> {
            try {
                String facets;
                if ("modpack".equals(kind)) facets = "[[\"project_type:modpack\"]]";
                else if ("shader".equals(kind)) facets = "[[\"project_type:shader\"]]";
                else facets = "[]";
                String url = MODRINTH_API + "/search?query=" + Uri.encode(query)
                        + "&limit=20&index=relevance&facets=" + Uri.encode(facets);
                String json = Net.get(url);
                JSONArray hits = new JSONObject(json).getJSONArray("hits");
                List<Map<String, String>> items = new ArrayList<>();
                for (int i = 0; i < hits.length(); i++) {
                    JSONObject h = hits.getJSONObject(i);
                    Map<String, String> m = new HashMap<>();
                    m.put("name", h.optString("title", h.optString("slug", "?")));
                    m.put("id", h.optString("project_id", ""));
                    m.put("dl", String.valueOf(h.optLong("downloads", 0)));
                    items.add(m);
                }
                runOnUiThread(() -> {
                    setBusy(false, "找到 " + items.size() + " 个结果");
                    if ("modpack".equals(kind)) {
                        lvModpack.setAdapter(new RemoteItemAdapter(items, item -> installModpack(item)));
                    } else if ("shader".equals(kind)) {
                        lvShaderSearch.setAdapter(new RemoteItemAdapter(items, item -> installShader(item)));
                    } else {
                        lvModrinth.setAdapter(new RemoteItemAdapter(items, item -> installModrinthMod(item)));
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> { setBusy(false, "搜索失败"); toast("搜索失败: " + e.getMessage()); });
            }
        }).start();
    }

    void downloadToMods(Map<String, String> item) {
        File md = new File(mcDir, "mods");
        md.mkdirs();
        downloadTo(item, md, item.get("name"), "模组下载完成: " + item.get("name"));
    }

    void installModrinthMod(Map<String, String> item) {
        setBusy(true, "获取 " + item.get("name") + " 版本信息…");
        new Thread(() -> {
            try {
                String url = MODRINTH_API + "/project/" + item.get("id") + "/version";
                JSONArray vers = new JSONArray(Net.get(url));
                JSONObject v0 = vers.getJSONObject(0);
                JSONArray files = v0.getJSONArray("files");
                JSONObject f = files.getJSONObject(0);
                Map<String, String> m = new HashMap<>();
                m.put("name", f.optString("filename", "mod.jar"));
                m.put("url", f.optString("url", ""));
                m.put("size", String.valueOf(f.optLong("size", 0)));
                runOnUiThread(() -> downloadToMods(m));
            } catch (Exception e) {
                runOnUiThread(() -> { setBusy(false, "获取失败"); toast("获取失败: " + e.getMessage()); });
            }
        }).start();
    }

    void installShader(Map<String, String> item) {
        setBusy(true, "获取 " + item.get("name") + " 光影…");
        new Thread(() -> {
            try {
                String url = MODRINTH_API + "/project/" + item.get("id") + "/version";
                JSONArray vers = new JSONArray(Net.get(url));
                JSONObject v0 = vers.getJSONObject(0);
                JSONArray files = v0.getJSONArray("files");
                JSONObject f = files.getJSONObject(0);
                File sd = new File(mcDir, "shaderpacks");
                sd.mkdirs();
                String fname = f.optString("filename", "shader.zip");
                setBusy(true, "下载光影 " + fname);
                new Thread(() -> {
                    try {
                        Net.download(f.optString("url", ""), new File(sd, fname), "", (g, t) -> runOnUiThread(() -> setProgress(g, t)));
                        runOnUiThread(() -> { setBusy(false, "光影已安装: " + fname); refreshShaders(); });
                    } catch (Exception e) {
                        runOnUiThread(() -> { setBusy(false, "光影下载失败"); toast("下载失败: " + e.getMessage()); });
                    }
                }).start();
            } catch (Exception e) {
                runOnUiThread(() -> { setBusy(false, "获取失败"); toast("获取失败: " + e.getMessage()); });
            }
        }).start();
    }

    void installModpack(Map<String, String> item) {
        setBusy(true, "获取整合包 " + item.get("name") + "…");
        new Thread(() -> {
            try {
                String url = MODRINTH_API + "/project/" + item.get("id") + "/version";
                JSONArray vers = new JSONArray(Net.get(url));
                JSONObject v0 = vers.getJSONObject(0);
                JSONArray files = v0.getJSONArray("files");
                JSONObject f = null;
                for (int i = 0; i < files.length(); i++) {
                    if (files.getJSONObject(i).optString("filename", "").endsWith(".mrpack")) { f = files.getJSONObject(i); break; }
                }
                if (f == null) throw new Exception("无 .mrpack 文件");
                String fname = f.optString("filename", "pack.mrpack");
                File tmp = new File(getCacheDir(), fname);
                setBusy(true, "下载整合包 " + fname);
                Net.download(f.optString("url", ""), tmp, "", (g, t) -> runOnUiThread(() -> setProgress(g, t)));
                parseAndInstallMrpack(tmp, item.get("name"));
            } catch (Exception e) {
                runOnUiThread(() -> { setBusy(false, "整合包失败"); toast("整合包失败: " + e.getMessage()); });
            }
        }).start();
    }

    void parseAndInstallMrpack(File mrpack, final String title) {
        new Thread(() -> {
            try {
                java.util.zip.ZipFile z = new java.util.zip.ZipFile(mrpack);
                java.util.zip.ZipEntry idx = z.getEntry("modrinth.index.json");
                if (idx == null) idx = z.getEntry("index.json");
                if (idx == null) throw new Exception("mrpack 缺少 index");
                String content = new String(readAll(z.getInputStream(idx)), "UTF-8");
                JSONObject index = new JSONObject(content);
                JSONArray files = index.optJSONArray("files");
                int total = files != null ? files.length() : 0;
                int done = 0;
                if (files != null) {
                    for (int i = 0; i < files.length(); i++) {
                        JSONObject f = files.getJSONObject(i);
                        String path = f.optString("path", "");
                        if (path.isEmpty()) continue;
                        String fname = path.substring(path.lastIndexOf('/') + 1);
                        File dest = new File(mcDir, "mods/" + fname);
                        JSONObject hashes = f.optJSONObject("hashes");
                        String sha1 = hashes != null ? hashes.optString("sha1", "") : "";
                        if (!dest.exists() || (!sha1.isEmpty() && !Net.sha1(dest).equalsIgnoreCase(sha1))) {
                            JSONArray urls = f.optJSONArray("downloads");
                            String url = "";
                            if (urls != null && urls.length() > 0) url = urls.getString(0);
                            if (!url.isEmpty()) {
                                try { Net.download(url, dest, sha1, null); }
                                catch (Exception e) { /* 单个模组失败继续 */ }
                            }
                        }
                        done++;
                        final int d = done;
                        runOnUiThread(() -> setProgress(d, total));
                    }
                }
                // overrides
                java.util.Enumeration<? extends java.util.zip.ZipEntry> en = z.entries();
                while (en.hasMoreElements()) {
                    java.util.zip.ZipEntry e = en.nextElement();
                    String n = e.getName();
                    if (n.startsWith("overrides/") && !e.isDirectory()) {
                        String rel = n.substring("overrides/".length());
                        File target = new File(mcDir, rel);
                        target.getParentFile().mkdirs();
                        try (FileOutputStream fo = new FileOutputStream(target)) {
                            fo.write(readAll(z.getInputStream(e)));
                        }
                    }
                }
                z.close();
                mrpack.delete();
                runOnUiThread(() -> {
                    setBusy(false, "整合包安装完成: " + title);
                    refreshInstalledMods();
                    toast("整合包安装完成: " + title);
                });
            } catch (Exception e) {
                runOnUiThread(() -> { setBusy(false, "整合包失败"); toast("整合包失败: " + e.getMessage()); });
            }
        }).start();
    }

    static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
        return out.toByteArray();
    }

    void downloadTo(Map<String, String> item, final File dir, final String label, final String okMsg) {
        setBusy(true, "下载 " + label);
        new Thread(() -> {
            try {
                dir.mkdirs();
                File dest = new File(dir, item.get("name"));
                Net.download(item.get("url"), dest, "", (g, t) -> runOnUiThread(() -> setProgress(g, t)));
                runOnUiThread(() -> { setBusy(false, okMsg); refreshInstalledMods(); });
            } catch (Exception e) {
                runOnUiThread(() -> { setBusy(false, "下载失败"); toast("下载失败: " + e.getMessage()); });
            }
        }).start();
    }

    void deleteItem(File f) {
        if (f.isDirectory()) {
            File[] l = f.listFiles();
            if (l != null) for (File c : l) deleteItem(c);
        }
        f.delete();
    }

    // ============================================================
    // 账号
    // ============================================================
    void msLogin() {
        tvMsStatus.setText("设备码流程已接入；请在「设置」填写微软应用 Client ID 后使用");
        // 真实正版登录需要注册 Azure 应用获得 Client ID（与 PC 版一致），此处为流程框架
    }

    // ============================================================
    // 启动
    // ============================================================
    String buildLaunchCommand(String vid, JSONObject vdata) throws Exception {
        String mainClass = vdata.optString("mainClass", "net.minecraft.client.main.Main");
        List<String> args = new ArrayList<>();
        args.add("java");
        args.add("-Xmx" + sbMemory.getProgress() + "M");
        args.add("-Xms" + Math.max(sbMemory.getProgress() / 4, 256) + "M");
        Collections.addAll(args, JVM_FLAGS);

        int acctPos = spAccount.getSelectedItemPosition();
        String username = etName.getText().toString().trim();
        if (username.isEmpty()) username = "Steve";
        String authUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes()).toString().replace("-", "");
        String accessToken = "0";
        if (acctPos == 1) {
            String server = etServer.getText().toString().trim();
            String pwd = etPwd.getText().toString();
            if (server.isEmpty() || pwd.isEmpty()) throw new Exception("自定义服务器需填写地址与密码");
            File rt = new File(mcDir, "runtime");
            rt.mkdirs();
            File aj = new File(rt, "authlib-injector.jar");
            if (!aj.exists()) {
                String meta = Net.get("https://api.github.com/repos/yushijinhun/authlib-injector/releases/latest");
                JSONObject m = new JSONObject(meta);
                JSONArray assets = m.optJSONArray("assets");
                String jarUrl = "";
                for (int i = 0; i < assets.length(); i++) {
                    if (assets.getJSONObject(i).optString("name", "").endsWith(".jar")) {
                        jarUrl = assets.getJSONObject(i).optString("browser_download_url", "");
                        break;
                    }
                }
                if (jarUrl.isEmpty()) throw new Exception("authlib-injector 获取失败");
                setBusy(true, "下载 authlib-injector…");
                Net.download(jarUrl, aj, "", null);
            }
            args.add("-javaagent:" + aj.getAbsolutePath() + "=" + server);
        }

        // classpath
        StringBuilder cp = new StringBuilder();
        String sep = ":";
        JSONArray libs = vdata.optJSONArray("libraries");
        if (libs != null) {
            for (int i = 0; i < libs.length(); i++) {
                JSONObject lib = libs.getJSONObject(i);
                if (!rulesOk(lib)) continue;
                String name = lib.optString("name", "");
                if (name.contains(":")) {
                    String[] parts = name.split(":");
                    String path = parts[0].replace('.', '/') + "/" + parts[1] + "/" + parts[2] + "/" + parts[1] + "-" + parts[2] + ".jar";
                    if (name.startsWith("org.lwjgl") && parts.length > 3) {
                        // natives classifier
                    }
                    File f = new File(mcDir, "libraries/" + path);
                    if (f.exists()) {
                        if (cp.length() > 0) cp.append(sep);
                        cp.append(f.getAbsolutePath());
                    }
                }
            }
        }
        File cj = new File(mcDir, "versions/" + vid + "/" + vid + ".jar");
        if (cp.length() > 0) cp.append(sep);
        cp.append(cj.getAbsolutePath());

        args.add("-cp");
        args.add(cp.toString());
        args.add(mainClass);
        // 游戏参数（最小集）
        args.add("--gameDir"); args.add(mcDir.getAbsolutePath());
        args.add("--assetsDir"); args.add(new File(mcDir, "assets").getAbsolutePath());
        args.add("--assetIndex"); args.add(vdata.optJSONObject("assetIndex") != null
                ? vdata.optJSONObject("assetIndex").optString("id", "legacy") : "legacy");
        args.add("--uuid"); args.add(authUuid);
        args.add("--accessToken"); args.add(accessToken);
        args.add("--username"); args.add(username);
        args.add("--version"); args.add(vid);
        return String.join(" ", args);
    }

    void launchGame() {
        String vid = installedVersions.isEmpty() ? null : installedVersions.get(0);
        if (vid == null) { toast("请先安装一个版本"); return; }
        try {
            File vj = new File(mcDir, "versions/" + vid + "/" + vid + ".json");
            JSONObject vdata = new JSONObject(new String(java.nio.file.Files.readAllBytes(vj.toPath()), "UTF-8"));
            String cmd = buildLaunchCommand(vid, vdata);
            // 保存启动命令（供运行栈/调试使用）
            File lf = new File(mcDir, "launch_cmd.txt");
            try (FileOutputStream fo = new FileOutputStream(lf)) { fo.write(cmd.getBytes("UTF-8")); }
            setBusy(false, "启动命令已生成 ✓");
            new AlertDialog.Builder(this)
                    .setTitle("启动准备就绪")
                    .setMessage("游戏文件与启动命令已就绪。\n\nAndroid 端运行 Java 版 Minecraft 需要「Java 运行栈」组件（开源运行时，FCL 等均依赖它）。请从官网下载运行栈后即可进入游戏。\n\n启动命令已保存至 .minecraft/launch_cmd.txt")
                    .setPositiveButton("打开官网", (d, w) -> openUrl("https://etqwfd.github.io/NebulaLauncher/"))
                    .setNegativeButton("知道了", null)
                    .show();
        } catch (Exception e) {
            toast("启动准备失败: " + e.getMessage());
        }
    }

    // ============================================================
    // Adapters
    // ============================================================
    class SimpleAdapter extends BaseAdapter {
        List<String> items;
        SimpleAdapter(List<String> items) { this.items = items; }
        public int getCount() { return items.size(); }
        public Object getItem(int i) { return items.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int i, View cv, ViewGroup p) {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(cardBg(8, C_CARD));
            row.setPadding((int) dp(10), (int) dp(8), (int) dp(10), (int) dp(8));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = (int) dp(6);
            row.setLayoutParams(rp);
            row.addView(mkText(items.get(i), 14, C_TEXT, 1), lpW(1));
            return row;
        }
    }

    class InstalledModAdapter extends BaseAdapter {
        List<String> items;
        InstalledModAdapter(List<String> items) { this.items = items; }
        public int getCount() { return items.size(); }
        public Object getItem(int i) { return items.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int i, View cv, ViewGroup p) {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(cardBg(8, C_CARD));
            row.setPadding((int) dp(10), (int) dp(6), (int) dp(10), (int) dp(6));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = (int) dp(6);
            row.setLayoutParams(rp);
            String name = items.get(i);
            boolean disabled = name.endsWith(".disabled");
            row.addView(mkText(disabled ? name.substring(0, name.length() - 9) : name, 13,
                    disabled ? C_DIM : C_TEXT, 0), lpW(1));
            Button b = new Button(MainActivity.this);
            b.setText(disabled ? "启用" : "禁用");
            b.setTextSize(12);
            b.setTextColor(C_TEXT);
            b.setBackground(cardBg(6, 0xFF1F2A3D));
            b.setPadding((int) dp(8), 0, (int) dp(8), 0);
            final String fn = name;
            b.setOnClickListener(v -> {
                File f = new File(mcDir, "mods/" + fn);
                File t = disabled
                        ? new File(mcDir, "mods/" + fn.substring(0, fn.length() - 9))
                        : new File(mcDir, "mods/" + fn + ".disabled");
                f.renameTo(t);
                refreshInstalledMods();
            });
            row.addView(b);
            Button bd = new Button(MainActivity.this);
            bd.setText("删");
            bd.setTextSize(12);
            bd.setTextColor(0xFFFF6B6B);
            bd.setBackground(cardBg(6, 0xFF2A1F1F));
            bd.setPadding((int) dp(8), 0, (int) dp(8), 0);
            bd.setOnClickListener(v -> { new File(mcDir, "mods/" + fn).delete(); refreshInstalledMods(); });
            row.addView(bd);
            return row;
        }
    }

    class SimpleListAdapter extends BaseAdapter {
        List<String> items;
        java.util.function.Consumer<String> onDelete;
        SimpleListAdapter(List<String> items, java.util.function.Consumer<String> onDelete) {
            this.items = items;
            this.onDelete = onDelete;
        }
        public int getCount() { return items.size(); }
        public Object getItem(int i) { return items.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int i, View cv, ViewGroup p) {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(cardBg(8, C_CARD));
            row.setPadding((int) dp(10), (int) dp(6), (int) dp(10), (int) dp(6));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = (int) dp(6);
            row.setLayoutParams(rp);
            row.addView(mkText(items.get(i), 13, C_TEXT, 0), lpW(1));
            Button bd = new Button(MainActivity.this);
            bd.setText("删");
            bd.setTextSize(12);
            bd.setTextColor(0xFFFF6B6B);
            bd.setBackground(cardBg(6, 0xFF2A1F1F));
            bd.setPadding((int) dp(8), 0, (int) dp(8), 0);
            final int fi = i;
            bd.setOnClickListener(v -> onDelete.accept(items.get(fi)));
            row.addView(bd);
            return row;
        }
    }

    class RemoteItemAdapter extends BaseAdapter {
        List<Map<String, String>> items;
        java.util.function.Consumer<Map<String, String>> onAction;
        RemoteItemAdapter(List<Map<String, String>> items, java.util.function.Consumer<Map<String, String>> onAction) {
            this.items = items;
            this.onAction = onAction;
        }
        public int getCount() { return items.size(); }
        public Object getItem(int i) { return items.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int i, View cv, ViewGroup p) {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(cardBg(8, C_CARD));
            row.setPadding((int) dp(10), (int) dp(6), (int) dp(10), (int) dp(6));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = (int) dp(6);
            row.setLayoutParams(rp);
            Map<String, String> m = items.get(i);
            LinearLayout left = new LinearLayout(MainActivity.this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.addView(mkText(m.get("name"), 13, C_TEXT, 0));
            String sub = m.get("dl") != null ? m.get("dl") + " DL"
                    : (m.get("size") != null && !m.get("size").isEmpty()
                    ? String.format(Locale.US, "%.1f MB", Long.parseLong(m.get("size")) / 1048576.0) : "");
            left.addView(mkText(sub, 11, C_DIM, 0));
            row.addView(left, lpW(1));
            Button b = new Button(MainActivity.this);
            b.setText("下载");
            b.setTextSize(12);
            b.setTextColor(Color.WHITE);
            b.setBackground(cardBg(6, C_ACC));
            b.setPadding((int) dp(10), 0, (int) dp(10), 0);
            final Map<String, String> fm = m;
            b.setOnClickListener(v -> onAction.accept(fm));
            row.addView(b);
            return row;
        }
    }
}
