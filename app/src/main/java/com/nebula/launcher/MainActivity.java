package com.nebula.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.widget.ArrayAdapter;
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
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 星云启动器 · 手机版 v1.3.0（Nebula Launcher Android）
 * 自有代码，参照 PCL/FCL 手机版结构与电脑版功能缩减实现：
 *   启动（真实运行栈启动流程）· 版本下载安装 · 模组/整合包/光影下载
 *   三账号 · 设置（主题/文字/内存/镜像/检测更新/开发者选项/日志）
 * 游戏文件存于应用内部存储 .minecraft。
 */
public class MainActivity extends Activity {

    // ---------------- 网络常量 ----------------
    static final String MANIFEST_OFFICIAL = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
    static final String MANIFEST_BMCL = "https://bmclapi2.bangbang93.com/mc/game/version_manifest_v2.json";
    static final String LIB_BMCL = "https://bmclapi2.bangbang93.com/libraries/";
    static final String ASSET_BMCL = "https://bmclapi2.bangbang93.com/assets/";
    static final String MODRINTH_API = "https://api.modrinth.com/v2";
    static final String DEFAULT_MODS_REPO = "ETQWFD/NebulaLauncher-Mods";
    static final String GITHUB_API = "https://api.github.com";
    static final String[] JVM_FLAGS = {
            "-XX:+UseG1GC", "-XX:+UnlockExperimentalVMOptions",
            "-XX:MaxGCPauseMillis=50", "-XX:+DisableExplicitGC",
            "-XX:TargetSurvivorRatio=99", "-XX:G1NewSizePercent=30",
            "-XX:G1MaxNewSizePercent=40", "-XX:G1MixedGCLiveThresholdPercent=50",
            "-XX:G1RSetUpdatingPauseTimePercent=5", "-XX:SurvivorRatio=8",
            "-XX:+PerfDisableSharedMem", "-XX:MaxTenuringThreshold=1",
            "-Djava.awt.headless=true", "-Dlog4j2.formatMsgNoLookups=true",
    };

    // ---------------- 主题（bg/card/line/accent/text/dim） ----------------
    static final int[][] THEMES = {
            {0xFF0B0E14, 0xFF141A26, 0x12FFFFFF, 0xFF3D8BFF, 0xFFEAF0FF, 0xFF8B94A8}, // 深空蓝
            {0xFF0D0B1E, 0xFF171331, 0x12FFFFFF, 0xFF9B6BFF, 0xFFF0EFFF, 0xFF8E87A8}, // 暗夜紫
            {0xFFF2F5FA, 0xFFFFFFFF, 0x14000000, 0xFF3D8BFF, 0xFF182030, 0xFF7A8499}, // 浅色
            {0xFF000000, 0xFF141414, 0x12FFFFFF, 0xFF00E5A0, 0xFFE8E8E8, 0xFF7A7A7A}, // 经典黑
    };
    static final String[] THEME_NAMES = {"深空蓝", "暗夜紫", "浅色", "经典黑"};
    static final int[] ACCENTS = {0xFF3D8BFF, 0xFF00E5A0, 0xFF9B6BFF, 0xFFFF9F43};

    // ---------------- 语言 ----------------
    static final String[] LANGS = {"简体中文", "English", "日本語", "한국어", "Français", "Deutsch", "Español", "Русский"};
    static final String[] LANG_CODES = {"zh", "en", "ja", "ko", "fr", "de", "es", "ru"};
    static Map<String, Map<String, String>> STRINGS = new HashMap<>();

    // ---------------- 状态 ----------------
    File mcDir;
    String manifestUrl = MANIFEST_OFFICIAL;
    List<Map<String, String>> manifestVersions = new ArrayList<>();
    List<String> installedVersions = new ArrayList<>();
    String curLang = "zh";
    int themeIdx = 0;
    float textScale = 1.0f;
    int memMb = 2048;
    boolean autoYield = true;
    String modsRepo = DEFAULT_MODS_REPO;

    // ---------------- UI ----------------
    FrameLayout content;
    View pageLaunch, pageVersions, pageMods, pageSettings;
    TextView[] navBtns = new TextView[4];
    ListView lvLaunchVersions, lvInstalled, lvInstalledMods, lvGithub, lvModrinth, lvModpack, lvShaders, lvShaderSearch;
    TextView tvStatus, tvMemory, tvSelectedVersion, tvShadersTitle, tvMemSetting, tvVersionInfo;
    SeekBar sbMemory, sbMemSetting;
    Spinner spAccount;
    EditText etName, etServer, etPwd;
    Button btnLaunch, btnMsLogin, btnUpdateCheck, btnRunStack, btnLogs, btnClearCache, btnArchInfo;
    LinearLayout llAccountCustom, llAccountMs;
    TextView tvMsStatus;
    ProgressBar pbProgress;
    int modTab = 0;
    TextView[] modTabBtns = new TextView[5];
    LinearLayout llModSearch;
    EditText etModSearch;
    String currentInstalling;
    SharedPreferences prefs;

    // ---------------- 生命周期 ----------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initStrings();
        prefs = getSharedPreferences("nebula", MODE_PRIVATE);
        curLang = prefs.getString("lang", "zh");
        themeIdx = prefs.getInt("theme", 0);
        textScale = prefs.getFloat("textScale", 1.0f);
        memMb = prefs.getInt("mem", 2048);
        autoYield = prefs.getBoolean("autoYield", true);
        modsRepo = prefs.getString("modsRepo", DEFAULT_MODS_REPO);
        int mirror = prefs.getInt("mirror", 0);
        manifestUrl = mirror == 1 ? MANIFEST_OFFICIAL : MANIFEST_BMCL;
        mcDir = new File(getFilesDir(), ".minecraft");
        mcDir.mkdirs();
        applyWindow();
        buildUI();
        refreshInstalled();
        refreshManifest();
        log("启动器已启动 v1.3.0");
    }

    void applyWindow() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 21) {
            w.setStatusBarColor(THEMES[themeIdx][0]);
            w.setNavigationBarColor(THEMES[themeIdx][0]);
        }
    }

    void savePrefs() {
        prefs.edit()
                .putString("lang", curLang)
                .putInt("theme", themeIdx)
                .putFloat("textScale", textScale)
                .putInt("mem", memMb)
                .putBoolean("autoYield", autoYield)
                .putString("modsRepo", modsRepo)
                .apply();
    }

    // ---------------- 日志 ----------------
    void log(String msg) {
        try {
            File f = new File(mcDir, "launcher.log");
            String line = "[" + java.text.SimpleDateFormat.getDateTimeInstance().format(new java.util.Date()) + "] " + msg + "\n";
            if (!f.exists()) f.createNewFile();
            FileOutputStream fo = new FileOutputStream(f, true);
            fo.write(line.getBytes("UTF-8"));
            fo.close();
        } catch (Exception ignored) {
        }
    }

    // ---------------- 语言 ----------------
    void initStrings() {
        if (!STRINGS.isEmpty()) return;
        String[][] rows = {
                {"zh", "启动", "版本", "模组", "设置", "版本选择", "未安装版本", "内存分配", "游戏账号", "离线账号", "自定义服务器", "正版（微软）",
                        "游戏昵称", "authlib 服务器地址（皮肤站）", "服务器账号密码", "正版登录（微软设备码）", "尚未登录正版账号", "启动游戏", "就绪",
                        "安装新版本", "刷新", "选择要安装的版本（原版）", "取消", "已安装", "GitHub", "Modrinth", "整合包", "光影", "搜索…",
                        "仓库地址（默认 " + DEFAULT_MODS_REPO + "）", "搜索 Modrinth 模组…", "搜索 Modrinth 整合包…", "搜索 Modrinth 光影…",
                        "启用", "禁用", "删", "下载", "通用", "启动器", "网络", "开发者", "关于", "语言", "主题", "文字大小", "小", "中", "大",
                        "运行内存", "JVM 参数", "启动后自动让位（游戏独占用后台最少）", "镜像源", "自动（官方+BMCLAPI）", "仅官方 Mojang", "仅 BMCLAPI",
                        "模组仓库", "检测更新", "检查新版本…", "当前已是最新版本", "发现新版本：", "前往下载", "运行栈检测", "查看日志", "清空缓存",
                        "架构信息", "系统架构：", "运行栈未安装：Android 运行 Java 版游戏需要「Java 运行栈」组件。请到官网下载并放入 ",
                        "运行栈已就绪，可直接启动游戏", "日志已清空", "缓存已清空", "版本", "官网", "源码", "游戏目录", "启动命令", "复制", "安装完成",
                        "安装失败", "下载失败", "获取失败", "搜索失败", "请输入关键词", "版本清单尚未加载，请稍候", "请先安装一个版本",
                        "请填写服务器地址与密码", "启动准备失败", "当前版本 ", "最新版本 ", "点击打开", "确定", "内存", "集成包安装完成",
                        "模组下载完成", "光影已安装", "已安装光影", "游戏与启动命令已就绪。Android 端运行 Java 版游戏需要「Java 运行栈」组件（开源运行时，FCL 等均依赖它）。请从官网下载运行栈后即可进入游戏。", "打开官网", "知道了", "正在检查运行栈…", "等待运行栈下载", "启动中…", "游戏已启动，启动器自动让位", "搜索"},
                {"en", "Launch", "Versions", "Mods", "Settings", "Version", "No version installed", "Memory", "Account", "Offline", "Custom server", "Microsoft",
                        "Username", "authlib server URL (skin)", "Server password", "Sign in (Microsoft device code)", "Not signed in", "Launch Game", "Ready",
                        "Install new version", "Refresh", "Choose version to install (vanilla)", "Cancel", "Installed", "GitHub", "Modrinth", "Modpack", "Shaders", "Search…",
                        "Repo (default " + DEFAULT_MODS_REPO + ")", "Search Modrinth mods…", "Search Modrinth modpacks…", "Search Modrinth shaders…",
                        "Enable", "Disable", "Del", "Download", "General", "Launcher", "Network", "Developer", "About", "Language", "Theme", "Text size", "Small", "Medium", "Large",
                        "Memory (MB)", "JVM args", "Auto-yield after launch (minimal background)", "Mirror", "Auto (official + BMCLAPI)", "Official only", "BMCLAPI only",
                        "Mods repo", "Check updates", "Checking…", "Already up to date", "New version: ", "Download", "Runtime check", "View logs", "Clear cache",
                        "ABI info", "ABI: ", "Runtime not installed: Android needs the Java runtime stack to run Java Edition. Download it from the website and put it into ",
                        "Runtime ready, you can launch", "Logs cleared", "Cache cleared", "Version", "Website", "Source", "Game dir", "Launch command", "Copy", "Installed",
                        "Install failed", "Download failed", "Fetch failed", "Search failed", "Enter a keyword", "Version list not loaded yet", "Install a version first",
                        "Fill server URL and password", "Launch prepare failed", "Current ", "Latest ", "Open", "OK", "Memory", "Modpack installed",
                        "Mod downloaded", "Shader installed", "Installed shaders", "Game files and launch command ready. Android needs the Java runtime stack (open-source, used by FCL etc.) to run Java Edition. Download it from the website.", "Open website", "Got it", "Checking runtime…", "Waiting for runtime download", "Launching…", "Game started, launcher yielded", "Search"},
                {"ja", "起動", "バージョン", "Mod", "設定", "バージョン選択", "未インストール", "メモリ割当", "アカウント", "オフライン", "カスタムサーバー", "正版（Microsoft）",
                        "ユーザー名", "authlib サーバー URL（スキン）", "サーバーパスワード", "正版ログイン（デバイスコード）", "未ログイン", "ゲームを起動", "準備完了",
                        "新バージョンをインストール", "更新", "インストールするバージョンを選択", "キャンセル", "インストール済み", "GitHub", "Modrinth", "統合パック", "シェーダー", "検索…",
                        "リポジトリ（既定 " + DEFAULT_MODS_REPO + "）", "Modrinth の Mod を検索…", "Modrinth 統合パックを検索…", "Modrinth シェーダーを検索…",
                        "有効", "無効", "削除", "ダウンロード", "一般", "ランチャー", "ネットワーク", "開発者", "このアプリ", "言語", "テーマ", "文字サイズ", "小", "中", "大",
                        "メモリ (MB)", "JVM 引数", "起動後に自動で譲る（バックグラウンド最小）", "ミラー", "自動（公式+BMCLAPI）", "公式のみ", "BMCLAPI のみ",
                        "Mod リポジトリ", "更新を確認", "確認中…", "最新です", "新バージョン: ", "ダウンロードへ", "ランタイム確認", "ログ表示", "キャッシュを消去",
                        "ABI 情報", "ABI: ", "ランタイム未導入：Java 版を実行するには「Java ランタイム」が必要です。公式サイトから入手して ",
                        "ランタイム準備完了", "ログ消去済み", "キャッシュ消去済み", "バージョン", "公式サイト", "ソース", "ゲームディレクトリ", "起動コマンド", "コピー", "インストール済み",
                        "インストール失敗", "ダウンロード失敗", "取得失敗", "検索失敗", "キーワードを入力", "バージョン一覧未読込", "先にバージョンをインストール",
                        "サーバー URL とパスワードを入力", "起動準備失敗", "現在 ", "最新 ", "開く", "OK", "メモリ", "統合パック導入完了",
                        "Mod 導入完了", "シェーダー導入完了", "導入済みシェーダー", "ゲームファイルと起動コマンドが準備できました。Java 版の実行には「Java ランタイム」が必要です（FCL 等も依存）。公式サイトから入手してください。", "公式サイトを開く", "了解", "ランタイム確認中…", "ランタイム待機中", "起動中…", "ゲーム起動、ランチャーは自動で譲ります", "検索"},
                {"ko", "시작", "버전", "모드", "설정", "버전 선택", "설치된 버전 없음", "메모리 할당", "계정", "오프라인", "커스텀 서버", "정품 (Microsoft)",
                        "닉네임", "authlib 서버 URL (스킨)", "서버 비밀번호", "정품 로그인 (기기 코드)", "로그인 안 됨", "게임 시작", "준비됨",
                        "새 버전 설치", "새로고침", "설치할 버전 선택", "취소", "설치됨", "GitHub", "Modrinth", "통합팩", "셰이더", "검색…",
                        "저장소 (기본 " + DEFAULT_MODS_REPO + ")", "Modrinth 모드 검색…", "Modrinth 통합팩 검색…", "Modrinth 셰이더 검색…",
                        "활성화", "비활성화", "삭제", "다운로드", "일반", "런처", "네트워크", "개발자", "정보", "언어", "테마", "글자 크기", "작게", "중간", "크게",
                        "메모리 (MB)", "JVM 인자", "시작 후 자동 양보 (백그라운드 최소)", "미러", "자동 (공식+BMCLAPI)", "공식만", "BMCLAPI만",
                        "모드 저장소", "업데이트 확인", "확인 중…", "최신 버전입니다", "새 버전: ", "다운로드", "런타임 확인", "로그 보기", "캐시 비우기",
                        "ABI 정보", "ABI: ", "런타임 미설치: Java 에디션 실행에는 「Java 런타임」이 필요합니다. 공식 사이트에서 받아 ",
                        "런타임 준비 완료", "로그 삭제됨", "캐시 삭제됨", "버전", "공식 사이트", "소스", "게임 폴더", "시작 명령", "복사", "설치됨",
                        "설치 실패", "다운로드 실패", "가져오기 실패", "검색 실패", "키워드를 입력하세요", "버전 목록 미로드", "먼저 버전을 설치하세요",
                        "서버 URL과 비밀번호 입력", "시작 준비 실패", "현재 ", "최신 ", "열기", "확인", "메모리", "통합팩 설치 완료",
                        "모드 다운로드 완료", "셰이더 설치됨", "설치된 셰이더", "게임 파일과 시작 명령이 준비되었습니다. Java 에디션 실행에는 「Java 런타임」이 필요합니다 (FCL 등도 의존). 공식 사이트에서 받으세요.", "공식 사이트 열기", "알겠음", "런타임 확인 중…", "런타임 대기 중", "시작 중…", "게임 시작, 런처는 자동 양보", "검색"},
                {"fr", "Lancer", "Versions", "Mods", "Réglages", "Version", "Aucune version installée", "Mémoire", "Compte", "Hors ligne", "Serveur personnalisé", "Microsoft",
                        "Pseudo", "URL serveur authlib (skin)", "Mot de passe serveur", "Connexion (code d'appareil)", "Non connecté", "Lancer le jeu", "Prêt",
                        "Installer une version", "Actualiser", "Choisir une version à installer", "Annuler", "Installés", "GitHub", "Modrinth", "Modpack", "Shaders", "Rechercher…",
                        "Dépôt (défaut " + DEFAULT_MODS_REPO + ")", "Rechercher des mods Modrinth…", "Rechercher des modpacks Modrinth…", "Rechercher des shaders Modrinth…",
                        "Activer", "Désactiver", "Suppr.", "Télécharger", "Général", "Lanceur", "Réseau", "Développeur", "À propos", "Langue", "Thème", "Taille du texte", "Petit", "Moyen", "Grand",
                        "Mémoire (Mo)", "Args JVM", "Céder après lancement (fond minimal)", "Miroir", "Auto (officiel + BMCLAPI)", "Officiel seul", "BMCLAPI seul",
                        "Dépôt de mods", "Vérifier les mises à jour", "Vérification…", "Déjà à jour", "Nouvelle version : ", "Télécharger", "Vérif. runtime", "Voir les logs", "Vider le cache",
                        "Infos ABI", "ABI : ", "Runtime non installé : exécuter Java Edition nécessite le « runtime Java ». Téléchargez-le sur le site et placez-le dans ",
                        "Runtime prêt, vous pouvez lancer", "Logs vidés", "Cache vidé", "Version", "Site", "Source", "Dossier du jeu", "Commande de lancement", "Copier", "Installé",
                        "Échec installation", "Échec téléchargement", "Échec récupération", "Échec recherche", "Entrez un mot-clé", "Liste des versions non chargée", "Installez d'abord une version",
                        "Saisissez URL et mot de passe", "Échec préparation", "Actuelle ", "Dernière ", "Ouvrir", "OK", "Mémoire", "Modpack installé",
                        "Mod téléchargé", "Shader installé", "Shaders installés", "Fichiers du jeu et commande prêts. Android nécessite le « runtime Java » (open-source, utilisé par FCL etc.) pour exécuter Java Edition. Téléchargez-le sur le site.", "Ouvrir le site", "Compris", "Vérification du runtime…", "Attente du runtime", "Lancement…", "Jeu lancé, le lanceur cède", "Rechercher"},
                {"de", "Starten", "Versionen", "Mods", "Einstellungen", "Version", "Keine Version installiert", "Speicher", "Konto", "Offline", "Benutzerdefinierter Server", "Microsoft",
                        "Benutzername", "authlib Server-URL (Skin)", "Server-Passwort", "Anmelden (Gerätecode)", "Nicht angemeldet", "Spiel starten", "Bereit",
                        "Neue Version installieren", "Aktualisieren", "Zu installierende Version wählen", "Abbrechen", "Installiert", "GitHub", "Modrinth", "Modpack", "Shader", "Suchen…",
                        "Repo (Standard " + DEFAULT_MODS_REPO + ")", "Modrinth-Mods suchen…", "Modrinth-Modpacks suchen…", "Modrinth-Shader suchen…",
                        "Aktivieren", "Deaktivieren", "Löschen", "Herunterladen", "Allgemein", "Launcher", "Netzwerk", "Entwickler", "Über", "Sprache", "Design", "Textgröße", "Klein", "Mittel", "Groß",
                        "Speicher (MB)", "JVM-Args", "Nach Start abtreten (min. Hintergrund)", "Spiegel", "Auto (offiziell + BMCLAPI)", "Nur offiziell", "Nur BMCLAPI",
                        "Mods-Repo", "Nach Updates suchen", "Suche…", "Bereits aktuell", "Neue Version: ", "Download", "Runtime-Prüfung", "Logs ansehen", "Cache leeren",
                        "ABI-Info", "ABI: ", "Runtime nicht installiert: Für Java Edition wird der „Java-Runtime“-Stack benötigt. Von der Website laden und in ",
                        "Runtime bereit, Spiel kann starten", "Logs geleert", "Cache geleert", "Version", "Website", "Quellcode", "Spielordner", "Startbefehl", "Kopieren", "Installiert",
                        "Installation fehlgeschlagen", "Download fehlgeschlagen", "Abruf fehlgeschlagen", "Suche fehlgeschlagen", "Stichwort eingeben", "Versionsliste nicht geladen", "Zuerst Version installieren",
                        "Server-URL und Passwort eingeben", "Startvorbereitung fehlgeschlagen", "Aktuell ", "Neueste ", "Öffnen", "OK", "Speicher", "Modpack installiert",
                        "Mod heruntergeladen", "Shader installiert", "Installierte Shader", "Spieldateien und Startbefehl bereit. Android benötigt den „Java-Runtime“-Stack (Open-Source, auch von FCL verwendet), um Java Edition auszuführen. Von der Website laden.", "Website öffnen", "Verstanden", "Runtime wird geprüft…", "Warte auf Runtime", "Starte…", "Spiel gestartet, Launcher tritt ab", "Suchen"},
                {"es", "Iniciar", "Versiones", "Mods", "Ajustes", "Versión", "Sin versión instalada", "Memoria", "Cuenta", "Sin conexión", "Servidor personalizado", "Microsoft",
                        "Usuario", "URL servidor authlib (skin)", "Contraseña del servidor", "Iniciar sesión (código de dispositivo)", "No conectado", "Iniciar juego", "Listo",
                        "Instalar nueva versión", "Actualizar", "Elegir versión a instalar", "Cancelar", "Instalados", "GitHub", "Modrinth", "Modpack", "Shaders", "Buscar…",
                        "Repositorio (por defecto " + DEFAULT_MODS_REPO + ")", "Buscar mods de Modrinth…", "Buscar modpacks de Modrinth…", "Buscar shaders de Modrinth…",
                        "Activar", "Desactivar", "Elim.", "Descargar", "General", "Lanzador", "Red", "Desarrollador", "Acerca de", "Idioma", "Tema", "Tamaño del texto", "Pequeño", "Medio", "Grande",
                        "Memoria (MB)", "Args JVM", "Ceder tras iniciar (fondo mínimo)", "Espejo", "Auto (oficial + BMCLAPI)", "Solo oficial", "Solo BMCLAPI",
                        "Repositorio de mods", "Buscar actualizaciones", "Comprobando…", "Ya está actualizado", "Nueva versión: ", "Descargar", "Verif. runtime", "Ver registros", "Vaciar caché",
                        "Info ABI", "ABI: ", "Runtime no instalado: ejecutar Java Edition requiere el stack «Java runtime». Descárgalo del sitio y colócalo en ",
                        "Runtime listo, puedes iniciar", "Registros vaciados", "Caché vaciada", "Versión", "Sitio", "Fuente", "Carpeta del juego", "Comando de inicio", "Copiar", "Instalado",
                        "Error de instalación", "Error de descarga", "Error de obtención", "Error de búsqueda", "Introduce una palabra clave", "Lista de versiones no cargada", "Instala primero una versión",
                        "Introduce URL y contraseña", "Error de preparación", "Actual ", "Última ", "Abrir", "Aceptar", "Memoria", "Modpack instalado",
                        "Mod descargado", "Shader instalado", "Shaders instalados", "Archivos del juego y comando listos. Android necesita el stack «Java runtime» (código abierto, usado por FCL etc.) para ejecutar Java Edition. Descárgalo del sitio.", "Abrir sitio", "Entendido", "Comprobando runtime…", "Esperando runtime", "Iniciando…", "Juego iniciado, el lanzador cede", "Buscar"},
                {"ru", "Запуск", "Версии", "Моды", "Настройки", "Версия", "Версия не установлена", "Память", "Аккаунт", "Офлайн", "Свой сервер", "Microsoft",
                        "Имя игрока", "URL сервера authlib (скин)", "Пароль сервера", "Вход (код устройства)", "Не вошёл", "Запустить игру", "Готово",
                        "Установить версию", "Обновить", "Выберите версию для установки", "Отмена", "Установлено", "GitHub", "Modrinth", "Сборки", "Шейдеры", "Поиск…",
                        "Репозиторий (по умолч. " + DEFAULT_MODS_REPO + ")", "Поиск модов Modrinth…", "Поиск сборок Modrinth…", "Поиск шейдеров Modrinth…",
                        "Включить", "Отключить", "Удал.", "Скачать", "Общие", "Лаунчер", "Сеть", "Разработчик", "О программе", "Язык", "Тема", "Размер текста", "Малый", "Средний", "Большой",
                        "Память (МБ)", "Аргументы JVM", "Уступать после запуска (мин. фон)", "Зеркало", "Авто (офиц. + BMCLAPI)", "Только офиц.", "Только BMCLAPI",
                        "Репозиторий модов", "Проверить обновления", "Проверка…", "Уже актуально", "Новая версия: ", "Скачать", "Проверка рантайма", "Журнал", "Очистить кэш",
                        "Инфо ABI", "ABI: ", "Рантайм не установлен: для Java Edition нужен стек «Java runtime». Скачайте с сайта и поместите в ",
                        "Рантайм готов, можно запускать", "Журнал очищен", "Кэш очищен", "Версия", "Сайт", "Исходный код", "Папка игры", "Команда запуска", "Копировать", "Установлено",
                        "Ошибка установки", "Ошибка загрузки", "Ошибка получения", "Ошибка поиска", "Введите ключевое слово", "Список версий не загружен", "Сначала установите версию",
                        "Введите URL и пароль сервера", "Ошибка подготовки", "Текущая ", "Последняя ", "Открыть", "OK", "Память", "Сборка установлена",
                        "Мод скачан", "Шейдер установлен", "Установленные шейдеры", "Файлы игры и команда готовы. Для Java Edition на Android нужен стек «Java runtime» (открытый код, используется FCL и др.). Скачайте с сайта.", "Открыть сайт", "Понятно", "Проверка рантайма…", "Ожидание рантайма", "Запуск…", "Игра запущена, лаунчер уступает", "Поиск"},
        };
        for (String[] r : rows) {
            Map<String, String> m = new HashMap<>();
            String[] keys = {"nav_launch", "nav_versions", "nav_mods", "nav_settings", "ver_title", "ver_none", "mem_title", "acc_title", "acc_offline", "acc_custom", "acc_ms",
                    "acc_name", "acc_server", "acc_pwd", "acc_ms_btn", "acc_ms_status", "btn_launch", "status_ready",
                    "btn_install", "btn_refresh", "dlg_version", "cancel", "tab_installed", "tab_github", "tab_modrinth", "tab_modpack", "tab_shader", "hint_search",
                    "hint_repo", "hint_mod", "hint_modpack", "hint_shader",
                    "enable", "disable", "del", "download", "cat_general", "cat_launcher", "cat_network", "cat_dev", "cat_about", "lang", "theme", "textsize", "size_s", "size_m", "size_l",
                    "mem", "jvm", "autoyield", "mirror", "mirror_auto", "mirror_official", "mirror_bmcl",
                    "modsrepo", "update_check", "update_checking", "update_latest", "update_new", "update_go", "runtime_check", "view_logs", "clear_cache",
                    "arch", "arch_val", "runtime_missing", "runtime_ok", "log_cleared", "cache_cleared", "about_ver", "site", "source", "game_dir", "launch_cmd", "copy", "installed",
                    "install_fail", "download_fail", "fetch_fail", "search_fail", "need_keyword", "list_not_loaded", "install_first",
                    "need_custom", "launch_prepare_fail", "cur_ver", "latest_ver", "open", "ok", "mem_mb", "modpack_done",
                    "mod_done", "shader_done", "shaders_title", "launch_info", "open_site", "got_it", "checking_runtime", "wait_runtime", "launching", "launched_yield", "search"};
            for (int i = 1; i < r.length; i++) m.put(keys[i - 1], r[i]);
            STRINGS.put(r[0], m);
        }
    }

    String tr(String key) {
        Map<String, String> m = STRINGS.get(curLang);
        if (m == null || !m.containsKey(key)) m = STRINGS.get("zh");
        return m.containsKey(key) ? m.get(key) : key;
    }

    // ============================================================
    // UI 构建
    // ============================================================
    float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    TextView mkText(String s, float sp, int color, int style) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp * textScale);
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

    LinearLayout.LayoutParams lpW(float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.weight = weight;
        return p;
    }

    Button mkBtn(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(c(4));
        b.setTextSize(14 * textScale);
        b.setBackground(cardBg(8, themeColor(1, 0xFF1F2A3D)));
        b.setPadding(0, (int) dp(10), 0, (int) dp(10));
        return b;
    }

    int c(int idx) { return THEMES[themeIdx][idx]; }

    int themeColor(int theme, int fallback) {
        // 卡片/按钮底色随主题
        if (themeIdx == 2) return 0xFFE8EDF5; // 浅色主题按钮底
        if (themeIdx == 3) return 0xFF1A1A1A;
        return fallback;
    }

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(c(0));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding((int) dp(16), (int) dp(10), (int) dp(16), (int) dp(10));
        TextView logo = mkText("星云启动器", 20, c(4), 1);
        TextView sub = mkText("Nebula Launcher · v1.3.0", 11, c(5), 0);
        sub.setPadding((int) dp(12), 0, 0, 0);
        top.addView(logo);
        top.addView(sub);
        root.addView(top);

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

        LinearLayout nav = new LinearLayout(this);
        nav.setBackgroundColor(c(1));
        nav.setPadding((int) dp(4), (int) dp(6), (int) dp(4), (int) dp(6));
        String[] names = {tr("nav_launch"), tr("nav_versions"), tr("nav_mods"), tr("nav_settings")};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView b = mkText(names[i], 13, c(5), 1);
            b.setGravity(Gravity.CENTER);
            b.setPadding(0, (int) dp(8), 0, (int) dp(8));
            nav.addView(b, lpW(1));
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
        for (int i = 0; i < 4; i++) navBtns[i].setTextColor(i == idx ? c(3) : c(5));
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
        LinearLayout cardVer = card();
        cardVer.addView(mkText(tr("ver_title"), 12, c(5), 0));
        tvSelectedVersion = mkText(tr("ver_none"), 22, c(4), 1);
        tvSelectedVersion.setPadding(0, (int) dp(4), 0, (int) dp(6));
        cardVer.addView(tvSelectedVersion);
        lvLaunchVersions = new ListView(this);
        lvLaunchVersions.setDividerHeight(0);
        lvLaunchVersions.setBackgroundColor(Color.TRANSPARENT);
        lvLaunchVersions.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) dp(150)));
        cardVer.addView(lvLaunchVersions);
        col.addView(cardVer);

        // 内存卡片
        LinearLayout cardMem = card();
        cardMem.addView(mkText(tr("mem_title"), 12, c(5), 0));
        tvMemory = mkText(memMb + " MB", 15, c(4), 1);
        tvMemory.setPadding(0, (int) dp(4), 0, (int) dp(2));
        cardMem.addView(tvMemory);
        sbMemory = new SeekBar(this);
        sbMemory.setMax(8192);
        sbMemory.setProgress(memMb);
        sbMemory.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean u) {
                v = (v / 256) * 256;
                if (v < 512) v = 512;
                memMb = v;
                tvMemory.setText(v + " MB");
                if (tvMemSetting != null) tvMemSetting.setText(tr("mem") + ": " + v + " MB");
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) { savePrefs(); }
        });
        cardMem.addView(sbMemory);
        col.addView(cardMem);

        // 账号卡片
        LinearLayout cardAcc = card();
        cardAcc.addView(mkText(tr("acc_title"), 12, c(5), 0));
        spAccount = new Spinner(this);
        spAccount.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{tr("acc_offline"), tr("acc_custom"), tr("acc_ms")}));
        cardAcc.addView(spAccount);
        etName = input(tr("acc_name"), "Steve");
        cardAcc.addView(etName);
        llAccountCustom = new LinearLayout(this);
        llAccountCustom.setOrientation(LinearLayout.VERTICAL);
        etServer = input(tr("acc_server"), "");
        llAccountCustom.addView(etServer);
        etPwd = input(tr("acc_pwd"), "");
        etPwd.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        llAccountCustom.addView(etPwd);
        cardAcc.addView(llAccountCustom);
        llAccountMs = new LinearLayout(this);
        llAccountMs.setOrientation(LinearLayout.VERTICAL);
        btnMsLogin = mkBtn(tr("acc_ms_btn"));
        btnMsLogin.setOnClickListener(v -> msLogin());
        llAccountMs.addView(btnMsLogin);
        tvMsStatus = mkText(tr("acc_ms_status"), 12, c(5), 0);
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
        btnLaunch = mkBtn(tr("btn_launch"));
        btnLaunch.setTextSize(17 * textScale);
        btnLaunch.setBackground(cardBg(12, c(3)));
        btnLaunch.setTextColor(Color.WHITE);
        btnLaunch.setPadding(0, (int) dp(14), 0, (int) dp(14));
        btnLaunch.setOnClickListener(v -> launchGame());
        col.addView(btnLaunch);

        tvStatus = mkText(tr("status_ready"), 13, c(5), 0);
        tvStatus.setPadding((int) dp(4), (int) dp(10), 0, 0);
        col.addView(tvStatus);
        pbProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pbProgress.setMax(1000);
        pbProgress.setPadding(0, (int) dp(6), 0, (int) dp(6));
        col.addView(pbProgress);

        sv.addView(col);
        return sv;
    }

    LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackground(cardBg(14, c(1)));
        l.setPadding((int) dp(14), (int) dp(12), (int) dp(14), (int) dp(12));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = (int) dp(12);
        l.setLayoutParams(p);
        return l;
    }

    EditText input(String hint, String def) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(def);
        e.setTextColor(c(4));
        e.setHintTextColor(c(5));
        e.setSingleLine(true);
        e.setBackground(cardBg(8, themeColor(0, 0xFF0F1520)));
        e.setPadding((int) dp(10), (int) dp(8), (int) dp(10), (int) dp(8));
        return e;
    }

    // ---------------- 版本页 ----------------
    private View buildVersionsPage() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding((int) dp(16), (int) dp(8), (int) dp(16), (int) dp(8));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button btnNew = mkBtn(tr("btn_install"));
        btnNew.setBackground(cardBg(10, c(3)));
        btnNew.setTextColor(Color.WHITE);
        btnNew.setOnClickListener(v -> showInstallDialog());
        Button btnRefresh = mkBtn(tr("btn_refresh"));
        btnRefresh.setOnClickListener(v -> refreshInstalled());
        row.addView(btnNew, lpW(1));
        row.addView(btnRefresh);
        col.addView(row);
        lvInstalled = new ListView(this);
        lvInstalled.setDividerHeight(0);
        lvInstalled.setBackgroundColor(Color.TRANSPARENT);
        col.addView(lvInstalled, lpW(1));
        return col;
    }

    void showInstallDialog() {
        if (manifestVersions.isEmpty()) { toast(tr("list_not_loaded")); return; }
        List<String> names = new ArrayList<>();
        for (Map<String, String> m : manifestVersions) {
            if ("release".equals(m.get("type"))) names.add(m.get("id"));
        }
        final List<String> namesFinal = names.size() > 40 ? names.subList(0, 40) : names;
        new AlertDialog.Builder(this)
                .setTitle(tr("dlg_version"))
                .setItems(namesFinal.toArray(new String[0]), (d, which) -> {
                    String id = namesFinal.get(which);
                    String url = null;
                    for (Map<String, String> m : manifestVersions) {
                        if (id.equals(m.get("id"))) { url = m.get("url"); break; }
                    }
                    installVersion(id, url);
                })
                .setNegativeButton(tr("cancel"), null)
                .show();
    }

    // ---------------- 模组页 ----------------
    private View buildModsPage() {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding((int) dp(16), (int) dp(8), (int) dp(16), (int) dp(8));

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        String[] tnames = {tr("tab_installed"), tr("tab_github"), tr("tab_modrinth"), tr("tab_modpack"), tr("tab_shader")};
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            TextView t = mkText(tnames[i], 12, c(5), 1);
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
        etModSearch.setHint(tr("hint_search"));
        etModSearch.setTextColor(c(4));
        etModSearch.setHintTextColor(c(5));
        etModSearch.setSingleLine(true);
        etModSearch.setBackground(cardBg(8, themeColor(0, 0xFF0F1520)));
        etModSearch.setPadding((int) dp(10), (int) dp(6), (int) dp(10), (int) dp(6));
        Button bSearch = mkBtn(tr("search"));
        bSearch.setOnClickListener(v -> doModSearch());
        llModSearch.addView(etModSearch, lpW(1));
        llModSearch.addView(bSearch);
        col.addView(llModSearch);

        lvInstalledMods = mkList();
        lvGithub = mkList();
        lvModrinth = mkList();
        lvModpack = mkList();
        lvShaderSearch = mkList();
        lvShaders = mkList();
        col.addView(lvInstalledMods, lpW(1));
        col.addView(lvGithub, lpW(1));
        col.addView(lvModrinth, lpW(1));
        col.addView(lvModpack, lpW(1));
        col.addView(lvShaderSearch, lpW(1));
        LinearLayout shCol = new LinearLayout(this);
        shCol.setOrientation(LinearLayout.VERTICAL);
        tvShadersTitle = mkText(tr("shaders_title"), 12, c(5), 0);
        shCol.addView(tvShadersTitle);
        shCol.addView(lvShaders, lpW(1));
        col.addView(shCol, lpW(1));
        switchModTab(0);
        return col;
    }

    ListView mkList() {
        ListView l = new ListView(this);
        l.setDividerHeight(0);
        l.setBackgroundColor(Color.TRANSPARENT);
        return l;
    }

    void switchModTab(int idx) {
        modTab = idx;
        for (int i = 0; i < 5; i++) modTabBtns[i].setTextColor(i == idx ? c(3) : c(5));
        lvInstalledMods.setVisibility(idx == 0 ? View.VISIBLE : View.GONE);
        lvGithub.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
        lvModrinth.setVisibility(idx == 2 ? View.VISIBLE : View.GONE);
        lvModpack.setVisibility(idx == 3 ? View.VISIBLE : View.GONE);
        lvShaderSearch.setVisibility(idx == 4 ? View.VISIBLE : View.GONE);
        lvShaders.setVisibility(idx == 4 ? View.VISIBLE : View.GONE);
        tvShadersTitle.setVisibility(idx == 4 ? View.VISIBLE : View.GONE);
        llModSearch.setVisibility(idx == 0 ? View.GONE : View.VISIBLE);
        String hint = idx == 1 ? tr("hint_repo") : idx == 2 ? tr("hint_mod") : idx == 3 ? tr("hint_modpack") : idx == 4 ? tr("hint_shader") : tr("hint_search");
        etModSearch.setHint(hint);
        if (idx == 0) refreshInstalledMods();
        if (idx == 4) refreshShaders();
    }

    void doModSearch() {
        String q = etModSearch.getText().toString().trim();
        if (q.isEmpty()) { toast(tr("need_keyword")); return; }
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
        col.addView(mkText(tr("nav_settings"), 18, c(4), 1));

        // 通用
        LinearLayout g1 = card();
        g1.addView(mkText(tr("cat_general"), 13, c(3), 1));
        g1.addView(settingRow(tr("lang"), LANGS[indexOf(LANG_CODES, curLang)], v -> pickLanguage()));
        g1.addView(settingRow(tr("theme"), THEME_NAMES[themeIdx], v -> pickTheme()));
        g1.addView(settingRow(tr("textsize"), tr(textScale < 0.95f ? "size_s" : textScale > 1.05f ? "size_l" : "size_m"), v -> pickTextSize()));
        col.addView(g1);

        // 启动器
        LinearLayout g2 = card();
        g2.addView(mkText(tr("cat_launcher"), 13, c(3), 1));
        LinearLayout memRow = new LinearLayout(this);
        memRow.setOrientation(LinearLayout.HORIZONTAL);
        memRow.setGravity(Gravity.CENTER_VERTICAL);
        tvMemSetting = mkText(tr("mem") + ": " + memMb + " MB", 14, c(4), 0);
        memRow.addView(tvMemSetting, lpW(1));
        g2.addView(memRow);
        sbMemSetting = new SeekBar(this);
        sbMemSetting.setMax(8192);
        sbMemSetting.setProgress(memMb);
        sbMemSetting.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int v, boolean u) {
                v = (v / 256) * 256;
                if (v < 512) v = 512;
                memMb = v;
                tvMemSetting.setText(tr("mem") + ": " + v + " MB");
                tvMemory.setText(v + " MB");
                sbMemory.setProgress(v);
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) { savePrefs(); }
        });
        g2.addView(sbMemSetting);
        g2.addView(settingRow(tr("autoyield"), autoYield ? "ON" : "OFF", v -> {
            autoYield = !autoYield;
            savePrefs();
            rebuild();
        }));
        col.addView(g2);

        // 网络
        LinearLayout g3 = card();
        g3.addView(mkText(tr("cat_network"), 13, c(3), 1));
        g3.addView(settingRow(tr("mirror"), mirrorName(), v -> pickMirror()));
        g3.addView(settingRow(tr("modsrepo"), modsRepo, v -> {
            final EditText e = new EditText(MainActivity.this);
            e.setText(modsRepo);
            e.setSingleLine(true);
            new AlertDialog.Builder(MainActivity.this).setTitle(tr("modsrepo"))
                    .setView(e)
                    .setPositiveButton(tr("ok"), (d, w) -> { modsRepo = e.getText().toString().trim(); savePrefs(); rebuild(); })
                    .setNegativeButton(tr("cancel"), null)
                    .show();
        }));
        col.addView(g3);

        // 开发者
        LinearLayout g4 = card();
        g4.addView(mkText(tr("cat_dev"), 13, c(3), 1));
        btnUpdateCheck = mkBtn(tr("update_check"));
        btnUpdateCheck.setOnClickListener(v -> checkUpdate());
        g4.addView(btnUpdateCheck);
        btnRunStack = mkBtn(tr("runtime_check"));
        btnRunStack.setOnClickListener(v -> checkRunStack());
        g4.addView(btnRunStack);
        btnLogs = mkBtn(tr("view_logs"));
        btnLogs.setOnClickListener(v -> showLogs());
        g4.addView(btnLogs);
        btnClearCache = mkBtn(tr("clear_cache"));
        btnClearCache.setOnClickListener(v -> {
            clearDir(getCacheDir());
            toast(tr("cache_cleared"));
        });
        g4.addView(btnClearCache);
        btnArchInfo = mkBtn(tr("arch") + ": " + abiInfo());
        btnArchInfo.setOnClickListener(v -> toast(tr("arch_val") + " " + abiInfo()));
        g4.addView(btnArchInfo);
        col.addView(g4);

        // 关于
        LinearLayout g5 = card();
        g5.addView(mkText(tr("cat_about"), 13, c(3), 1));
        tvVersionInfo = mkText(tr("about_ver") + " 1.3.0", 14, c(4), 0);
        g5.addView(tvVersionInfo);
        g5.addView(settingRow(tr("game_dir"), mcDir.getAbsolutePath(), v -> toast(mcDir.getAbsolutePath())));
        g5.addView(settingRow(tr("site"), "etqwfd.github.io/NebulaLauncher", v -> openUrl("https://etqwfd.github.io/NebulaLauncher/")));
        g5.addView(settingRow(tr("source"), "github.com/ETQWFD/NebulaLauncher-Android", v -> openUrl("https://github.com/ETQWFD/NebulaLauncher-Android")));
        col.addView(g5);

        sv.addView(col);
        return sv;
    }

    String mirrorName() {
        int m = prefs.getInt("mirror", 0);
        return m == 1 ? tr("mirror_official") : m == 2 ? tr("mirror_bmcl") : tr("mirror_auto");
    }

    String abiInfo() {
        return Build.VERSION.SDK_INT >= 21 ? Arrays.toString(Build.SUPPORTED_ABIS) : Build.CPU_ABI;
    }

    View settingRow(String title, String value, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, (int) dp(8), 0, (int) dp(8));
        row.addView(mkText(title, 14, c(4), 0), lpW(1));
        row.addView(mkText(value, 12, c(5), 0));
        row.setOnClickListener(onClick);
        return row;
    }

    void pickLanguage() {
        new AlertDialog.Builder(this).setTitle(tr("lang"))
                .setItems(LANGS, (d, which) -> {
                    curLang = LANG_CODES[which];
                    savePrefs();
                    rebuild();
                })
                .show();
    }

    void pickTheme() {
        new AlertDialog.Builder(this).setTitle(tr("theme"))
                .setItems(THEME_NAMES, (d, which) -> {
                    themeIdx = which;
                    savePrefs();
                    rebuild();
                })
                .show();
    }

    void pickTextSize() {
        new AlertDialog.Builder(this).setTitle(tr("textsize"))
                .setItems(new String[]{tr("size_s"), tr("size_m"), tr("size_l")}, (d, which) -> {
                    textScale = which == 0 ? 0.9f : which == 1 ? 1.0f : 1.15f;
                    savePrefs();
                    rebuild();
                })
                .show();
    }

    void pickMirror() {
        new AlertDialog.Builder(this).setTitle(tr("mirror"))
                .setItems(new String[]{tr("mirror_auto"), tr("mirror_official"), tr("mirror_bmcl")}, (d, which) -> {
                    prefs.edit().putInt("mirror", which).apply();
                    manifestUrl = which == 1 ? MANIFEST_OFFICIAL : MANIFEST_BMCL;
                    savePrefs();
                    rebuild();
                    refreshManifest();
                })
                .show();
    }

    void rebuild() {
        applyWindow();
        buildUI();
        refreshInstalled();
    }

    // ---------------- 更新检测 ----------------
    void checkUpdate() {
        btnUpdateCheck.setEnabled(false);
        btnUpdateCheck.setText(tr("update_checking"));
        new Thread(() -> {
            try {
                String json = Net.get(GITHUB_API + "/repos/ETQWFD/NebulaLauncher-Android/releases/latest");
                JSONObject o = new JSONObject(json);
                final String tag = o.optString("tag_name", "");
                final String url = o.optString("html_url", "");
                runOnUiThread(() -> {
                    btnUpdateCheck.setEnabled(true);
                    btnUpdateCheck.setText(tr("update_check"));
                    if (tag.isEmpty() || tag.equalsIgnoreCase("v1.3.0")) {
                        toast(tr("update_latest"));
                    } else {
                        new AlertDialog.Builder(this)
                                .setTitle(tr("update_new") + tag)
                                .setMessage(tr("cur_ver") + "1.3.0 · " + tr("latest_ver") + tag)
                                .setPositiveButton(tr("update_go"), (d, w) -> openUrl(url))
                                .setNegativeButton(tr("cancel"), null)
                                .show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnUpdateCheck.setEnabled(true);
                    btnUpdateCheck.setText(tr("update_check"));
                    toast(tr("fetch_fail") + ": " + e.getMessage());
                });
            }
        }).start();
    }

    void checkRunStack() {
        new Thread(() -> {
            final File jre = new File(mcDir, "runtime/jre/bin/java");
            final boolean ok = jre.exists();
            runOnUiThread(() -> {
                if (ok) {
                    toast(tr("runtime_ok"));
                    log("运行栈检测：已就绪");
                } else {
                    log("运行栈检测：未安装");
                    new AlertDialog.Builder(this)
                            .setTitle(tr("runtime_check"))
                            .setMessage(tr("runtime_missing") + mcDir.getAbsolutePath() + "/runtime/")
                            .setPositiveButton(tr("open_site"), (d, w) -> openUrl("https://etqwfd.github.io/NebulaLauncher/"))
                            .setNegativeButton(tr("got_it"), null)
                            .show();
                }
            });
        }).start();
    }

    void showLogs() {
        String content = "";
        try {
            File f = new File(mcDir, "launcher.log");
            if (f.exists()) {
                byte[] b = Files.readAllBytes(f.toPath());
                String all = new String(b, "UTF-8");
                String[] lines = all.split("\n");
                int start = Math.max(0, lines.length - 120);
                content = String.join("\n", java.util.Arrays.copyOfRange(lines, start, lines.length));
            }
        } catch (Exception e) {
            content = "err: " + e.getMessage();
        }
        TextView tv = new TextView(this);
        tv.setText(content.isEmpty() ? "(empty)" : content);
        tv.setTextColor(c(4));
        tv.setTextSize(11 * textScale);
        tv.setPadding((int) dp(12), (int) dp(12), (int) dp(12), (int) dp(12));
        ScrollView sc = new ScrollView(this);
        sc.addView(tv);
        new AlertDialog.Builder(this).setTitle(tr("view_logs")).setView(sc)
                .setPositiveButton(tr("ok"), null).show();
    }

    void clearDir(File dir) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) clearDir(f);
            f.delete();
        }
    }

    // ============================================================
    // 网络
    // ============================================================
    static class Net {
        static String get(String url) throws Exception {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(20000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "NebulaLauncher/1.3");
            int code = c.getResponseCode();
            if (code >= 400) throw new Exception("HTTP " + code + " " + url);
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            r.close();
            return sb.toString();
        }

        static void download(String url, File dest, String sha1, ProgressListener pl) throws Exception {
            File tmp = new File(dest.getParentFile(), dest.getName() + ".part");
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(20000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "NebulaLauncher/1.3");
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
                throw new Exception("SHA1 校验失败");
            }
            if (!tmp.renameTo(dest)) {
                try (FileOutputStream f = new FileOutputStream(dest)) {
                    try (FileInputStream fi = new FileInputStream(tmp)) {
                        byte[] b = new byte[8192];
                        int n2;
                        while ((n2 = fi.read(b)) > 0) f.write(b, 0, n2);
                    }
                }
                tmp.delete();
            }
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
                if (json == null) throw (err != null ? err : new Exception("manifest"));
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
                    tvStatus.setText(tr("nav_versions") + ": " + list.size());
                    log("版本清单已加载：" + list.size());
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast(tr("fetch_fail") + ": " + e.getMessage()));
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
        tvSelectedVersion.setText(installedVersions.isEmpty() ? tr("ver_none") : installedVersions.get(0));
        lvLaunchVersions.setAdapter(new SimpleAdapter(installedVersions, installedVersions.size() > 3 ? installedVersions.subList(0, 3) : installedVersions, null, true));
        lvInstalled.setAdapter(new InstalledVersionAdapter(installedVersions));
    }

    void installVersion(final String id, final String versionJsonUrl) {
        currentInstalling = id;
        setBusy(true, "准备安装 " + id);
        log("开始安装版本 " + id);
        new Thread(() -> {
            try {
                File vdir = new File(mcDir, "versions/" + id);
                vdir.mkdirs();
                String vj = Net.get(versionJsonUrl);
                File vjf = new File(vdir, id + ".json");
                try (FileOutputStream f = new FileOutputStream(vjf)) { f.write(vj.getBytes("UTF-8")); }
                JSONObject v = new JSONObject(vj);

                // 客户端 jar
                JSONObject dl = v.getJSONObject("downloads").getJSONObject("client");
                final String cUrl = dl.getString("url");
                final String cSha = dl.optString("sha1", "");
                File cj = new File(vdir, id + ".jar");
                setBusy(true, "下载客户端 " + id);
                if (!cj.exists() || (!cSha.isEmpty() && !Net.sha1(cj).equalsIgnoreCase(cSha))) {
                    Net.download(cUrl, cj, cSha, (got, total) -> runOnUiThread(() -> setProgress(got, total)));
                }

                // 库 + natives
                JSONArray libs = v.optJSONArray("libraries");
                int total = 0;
                if (libs != null) {
                    for (int i = 0; i < libs.length(); i++) {
                        JSONObject lib = libs.getJSONObject(i);
                        if (!rulesOk(lib)) continue;
                        if (lib.optJSONObject("downloads") == null && lib.optString("name", "").isEmpty()) continue;
                        total++;
                    }
                }
                final int totalFinal = total;
                int done = 0;
                File nativesDir = new File(vdir, "natives");
                nativesDir.mkdirs();
                if (libs != null) {
                    for (int i = 0; i < libs.length(); i++) {
                        JSONObject lib = libs.getJSONObject(i);
                        if (!rulesOk(lib)) continue;
                        JSONObject downloads = lib.optJSONObject("downloads");
                        JSONObject artifact = downloads != null ? downloads.optJSONObject("artifact") : null;
                        String name = lib.optString("name", "");
                        String mavenPath = mavenPath(name);
                        // natives 分类器
                        if (downloads != null && downloads.has("classifiers")) {
                            JSONObject cls = downloads.optJSONObject("classifiers");
                            String nativesKey = nativesKey();
                            if (cls != null && cls.has(nativesKey)) {
                                JSONObject nat = cls.getJSONObject(nativesKey);
                                File ndest = new File(mcDir, "libraries/" + mavenPathNoExt(name) + "-" + nativesKey + ".jar");
                                if (!ndest.exists()) {
                                    ndest.getParentFile().mkdirs();
                                    String u = mirrorLib(nat.optString("url", ""), ndest.getAbsolutePath().replace(mcDir.getAbsolutePath() + "/", ""));
                                    try { Net.download(u, ndest, nat.optString("sha1", ""), null); }
                                    catch (Exception e) { Net.download(nat.optString("url", ""), ndest, nat.optString("sha1", ""), null); }
                                }
                                // 解压 natives
                                try {
                                    java.util.zip.ZipFile z = new java.util.zip.ZipFile(ndest);
                                    java.util.Enumeration<? extends java.util.zip.ZipEntry> en = z.entries();
                                    while (en.hasMoreElements()) {
                                        java.util.zip.ZipEntry ze = en.nextElement();
                                        if (!ze.isDirectory()) {
                                            File t = new File(nativesDir, ze.getName());
                                            if (ze.getName().contains("/") && !t.getParentFile().exists()) t.getParentFile().mkdirs();
                                            if (!t.exists()) {
                                                try (FileOutputStream fo = new FileOutputStream(t)) { fo.write(readAll(z.getInputStream(ze))); }
                                            }
                                        }
                                    }
                                    z.close();
                                } catch (Exception ignored) {}
                                done++;
                                final int d = done;
                                runOnUiThread(() -> setProgress(d, totalFinal));
                                continue;
                            }
                        }
                        if (mavenPath.isEmpty()) continue;
                        File dest = new File(mcDir, "libraries/" + mavenPath);
                        if (!dest.exists()) {
                            dest.getParentFile().mkdirs();
                            String sha = artifact != null ? artifact.optString("sha1", "") : "";
                            String url = artifact != null ? artifact.optString("url", "") : "";
                            String u = mirrorLib(url, mavenPath);
                            try { Net.download(u, dest, sha, null); }
                            catch (Exception e) {
                                if (!u.equals(url)) Net.download(url, dest, sha, null);
                                else throw e;
                            }
                        }
                        done++;
                        final int d = done;
                        runOnUiThread(() -> setProgress(d, totalFinal));
                    }
                }

                // assets
                JSONObject ai = v.optJSONObject("assetIndex");
                if (ai != null) {
                    String aiUrl = ai.optString("url", "");
                    String aiId = ai.optString("id", "legacy");
                    File aiFile = new File(mcDir, "assets/indexes/" + aiId + ".json");
                    aiFile.getParentFile().mkdirs();
                    if (!aiFile.exists()) {
                        String u = aiUrl;
                        if (u.startsWith("https://launchermeta.mojang.com/")) u = ASSET_BMCL + "indexes/" + aiId + ".json";
                        try { Net.download(u, aiFile, "", null); }
                        catch (Exception e) { Net.download(aiUrl, aiFile, "", null); }
                    }
                    JSONObject index = new JSONObject(new String(Files.readAllBytes(aiFile.toPath()), "UTF-8"));
                    JSONObject objects = index.optJSONObject("objects");
                    List<String> keys = new ArrayList<>();
                    if (objects != null) {
                        Iterator<String> it = objects.keys();
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
                            try { Net.download(u, obj, hash, null); }
                            catch (Exception e) {
                                try { Net.download(ASSET_BMCL + "objects/" + hash.substring(0, 2) + "/" + hash, obj, hash, null); }
                                catch (Exception e2) { }
                            }
                        }
                        adone++;
                        final int ad = adone;
                        runOnUiThread(() -> setProgress(ad, atotal));
                    }
                }
                runOnUiThread(() -> {
                    setBusy(false, id + " " + tr("installed"));
                    refreshInstalled();
                    toast(id + " " + tr("installed"));
                    log("版本安装完成：" + id);
                });
            } catch (Exception e) {
                final String err = e.getMessage();
                runOnUiThread(() -> {
                    setBusy(false, tr("install_fail"));
                    toast(tr("install_fail") + ": " + err);
                    log("版本安装失败：" + id + " " + err);
                });
            }
        }).start();
    }

    String mavenPath(String name) {
        if (name.isEmpty() || !name.contains(":")) return "";
        String[] parts = name.split(":");
        if (parts.length < 3) return "";
        String base = parts[0].replace('.', '/') + "/" + parts[1] + "/" + parts[2] + "/" + parts[1] + "-" + parts[2];
        if (parts.length > 3) base += "-" + parts[3];
        return base + ".jar";
    }

    String mavenPathNoExt(String name) {
        String p = mavenPath(name);
        return p.isEmpty() ? "" : p.substring(0, p.length() - 4);
    }

    String nativesKey() {
        String abi = Build.VERSION.SDK_INT >= 21 && Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "arm64-v8a";
        if (abi.contains("arm64")) return "natives-linux-arm64";
        if (abi.contains("x86_64")) return "natives-linux";
        if (abi.contains("x86")) return "natives-linux-x86";
        if (abi.contains("armeabi")) return "natives-linux-arm32";
        return "natives-linux";
    }

    String mirrorLib(String url, String path) {
        if (url.startsWith("https://libraries.minecraft.net/") || url.startsWith("https://maven.fabricmc.net/")
                || url.startsWith("https://repo1.maven.org/") || url.startsWith("https://files.minecraftforge.net/")) {
            return LIB_BMCL + path;
        }
        return url;
    }

    /** 平台过滤：Android 按 linux 判定；natives 分类器库按 linux + natives 处理 */
    static boolean rulesOk(JSONObject lib) {
        JSONArray rules = lib.optJSONArray("rules");
        if (rules == null) return true;
        boolean ok = false;
        for (int i = 0; i < rules.length(); i++) {
            JSONObject r = rules.optJSONObject(i);
            if (r == null) continue;
            String os = r.optJSONObject("os") != null ? r.optJSONObject("os").optString("name", "") : "";
            boolean match;
            if (os.isEmpty()) match = true;
            else if (os.equals("linux")) match = true;
            else if (os.equals("osx")) match = false;
            else if (os.equals("windows")) match = false;
            else match = false;
            if (match) ok = "allow".equals(r.optString("action"));
        }
        return ok;
    }

    static byte[] readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
        return out.toByteArray();
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

    void openUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { toast("open failed"); }
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
        lvInstalledMods.setAdapter(new InstalledModAdapter(installedMods));
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
        if (repo.isEmpty()) repo = modsRepo;
        final String repoFinal = repo;
        setBusy(true, "GitHub: " + repo);
        new Thread(() -> {
            try {
                String json = Net.get(GITHUB_API + "/repos/" + repoFinal + "/releases?per_page=30");
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
                    setBusy(false, "GitHub: " + items.size());
                    lvGithub.setAdapter(new RemoteItemAdapter(items, item -> downloadToMods(item)));
                });
            } catch (Exception e) {
                final String err = e.getMessage();
                runOnUiThread(() -> { setBusy(false, tr("fetch_fail")); toast(tr("fetch_fail") + ": " + err); });
            }
        }).start();
    }

    void searchModrinth(String query, final String kind) {
        setBusy(true, "Modrinth…");
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
                    setBusy(false, items.size() + "");
                    if ("modpack".equals(kind)) lvModpack.setAdapter(new RemoteItemAdapter(items, item -> installModpack(item)));
                    else if ("shader".equals(kind)) lvShaderSearch.setAdapter(new RemoteItemAdapter(items, item -> installShader(item)));
                    else lvModrinth.setAdapter(new RemoteItemAdapter(items, item -> installModrinthMod(item)));
                });
            } catch (Exception e) {
                final String err = e.getMessage();
                runOnUiThread(() -> { setBusy(false, tr("search_fail")); toast(tr("search_fail") + ": " + err); });
            }
        }).start();
    }

    void downloadToMods(Map<String, String> item) {
        downloadTo(item, new File(mcDir, "mods"), item.get("name"), tr("mod_done") + ": " + item.get("name"));
    }

    void installModrinthMod(Map<String, String> item) {
        setBusy(true, tr("fetch_fail") + "? " + item.get("name"));
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
                final String err = e.getMessage();
                runOnUiThread(() -> { setBusy(false, tr("fetch_fail")); toast(tr("fetch_fail") + ": " + err); });
            }
        }).start();
    }

    void installShader(Map<String, String> item) {
        setBusy(true, "Shader: " + item.get("name"));
        new Thread(() -> {
            try {
                String url = MODRINTH_API + "/project/" + item.get("id") + "/version";
                JSONArray vers = new JSONArray(Net.get(url));
                JSONObject v0 = vers.getJSONObject(0);
                JSONArray files = v0.getJSONArray("files");
                JSONObject f = files.getJSONObject(0);
                File sd = new File(mcDir, "shaderpacks");
                sd.mkdirs();
                final String fname = f.optString("filename", "shader.zip");
                setBusy(true, tr("download") + " " + fname);
                new Thread(() -> {
                    try {
                        Net.download(f.optString("url", ""), new File(sd, fname), "", (g, t) -> runOnUiThread(() -> setProgress(g, t)));
                        runOnUiThread(() -> { setBusy(false, tr("shader_done") + ": " + fname); refreshShaders(); log("光影安装：" + fname); });
                    } catch (Exception e) {
                        final String err = e.getMessage();
                        runOnUiThread(() -> { setBusy(false, tr("download_fail")); toast(tr("download_fail") + ": " + err); });
                    }
                }).start();
            } catch (Exception e) {
                final String err = e.getMessage();
                runOnUiThread(() -> { setBusy(false, tr("fetch_fail")); toast(tr("fetch_fail") + ": " + err); });
            }
        }).start();
    }

    void installModpack(Map<String, String> item) {
        setBusy(true, "Modpack: " + item.get("name"));
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
                if (f == null) throw new Exception("no mrpack");
                final String fname = f.optString("filename", "pack.mrpack");
                File tmp = new File(getCacheDir(), fname);
                setBusy(true, tr("download") + " " + fname);
                Net.download(f.optString("url", ""), tmp, "", (g, t) -> runOnUiThread(() -> setProgress(g, t)));
                parseAndInstallMrpack(tmp, item.get("name"));
            } catch (Exception e) {
                final String err = e.getMessage();
                runOnUiThread(() -> { setBusy(false, tr("fetch_fail")); toast(tr("fetch_fail") + ": " + err); });
            }
        }).start();
    }

    void parseAndInstallMrpack(File mrpack, final String title) {
        new Thread(() -> {
            try {
                java.util.zip.ZipFile z = new java.util.zip.ZipFile(mrpack);
                java.util.zip.ZipEntry idx = z.getEntry("modrinth.index.json");
                if (idx == null) idx = z.getEntry("index.json");
                if (idx == null) throw new Exception("no index");
                JSONObject index = new JSONObject(new String(readAll(z.getInputStream(idx)), "UTF-8"));
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
                            String u = "";
                            if (urls != null && urls.length() > 0) u = urls.getString(0);
                            if (!u.isEmpty()) {
                                try { Net.download(u, dest, sha1, null); } catch (Exception ignored) {}
                            }
                        }
                        done++;
                        final int d = done;
                        runOnUiThread(() -> setProgress(d, total));
                    }
                }
                java.util.Enumeration<? extends java.util.zip.ZipEntry> en = z.entries();
                while (en.hasMoreElements()) {
                    java.util.zip.ZipEntry e = en.nextElement();
                    String n = e.getName();
                    if (n.startsWith("overrides/") && !e.isDirectory()) {
                        String rel = n.substring("overrides/".length());
                        File target = new File(mcDir, rel);
                        target.getParentFile().mkdirs();
                        try (FileOutputStream fo = new FileOutputStream(target)) { fo.write(readAll(z.getInputStream(e))); }
                    }
                }
                z.close();
                mrpack.delete();
                runOnUiThread(() -> {
                    setBusy(false, tr("modpack_done") + ": " + title);
                    refreshInstalledMods();
                    toast(tr("modpack_done") + ": " + title);
                    log("整合包安装：" + title);
                });
            } catch (Exception e) {
                final String err = e.getMessage();
                runOnUiThread(() -> { setBusy(false, tr("fetch_fail")); toast(tr("fetch_fail") + ": " + err); });
            }
        }).start();
    }

    void downloadTo(Map<String, String> item, final File dir, final String label, final String okMsg) {
        setBusy(true, tr("download") + " " + label);
        new Thread(() -> {
            try {
                dir.mkdirs();
                File dest = new File(dir, item.get("name"));
                Net.download(item.get("url"), dest, "", (g, t) -> runOnUiThread(() -> setProgress(g, t)));
                runOnUiThread(() -> { setBusy(false, okMsg); refreshInstalledMods(); log("下载完成：" + item.get("name")); });
            } catch (Exception e) {
                final String err = e.getMessage();
                runOnUiThread(() -> { setBusy(false, tr("download_fail")); toast(tr("download_fail") + ": " + err); });
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
        // 与电脑版一致的微软设备码流程（需 Azure 应用 Client ID，接入见官网文档）
        tvMsStatus.setText(tr("acc_ms_status") + " · device-code");
        log("正版登录：设备码流程入口");
    }

    // ============================================================
    // 真实启动
    // ============================================================
    String buildLaunchCommand(String vid, JSONObject vdata) throws Exception {
        StringBuilder cmd = new StringBuilder();
        List<String> parts = new ArrayList<>();
        parts.add("java");
        parts.add("-Xmx" + memMb + "M");
        parts.add("-Xms" + Math.max(memMb / 4, 256) + "M");
        Collections.addAll(parts, JVM_FLAGS);

        int acctPos = spAccount.getSelectedItemPosition();
        String username = etName.getText().toString().trim();
        if (username.isEmpty()) username = "Steve";
        String authUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes()).toString().replace("-", "");
        String accessToken = "0";
        if (acctPos == 1) {
            String server = etServer.getText().toString().trim();
            String pwd = etPwd.getText().toString();
            if (server.isEmpty() || pwd.isEmpty()) throw new Exception(tr("need_custom"));
            File rt = new File(mcDir, "runtime");
            rt.mkdirs();
            File aj = new File(rt, "authlib-injector.jar");
            if (!aj.exists()) {
                String meta = Net.get(GITHUB_API + "/repos/yushijinhun/authlib-injector/releases/latest");
                JSONObject m = new JSONObject(meta);
                JSONArray assets = m.optJSONArray("assets");
                String jarUrl = "";
                for (int i = 0; i < assets.length(); i++) {
                    if (assets.getJSONObject(i).optString("name", "").endsWith(".jar")) {
                        jarUrl = assets.getJSONObject(i).optString("browser_download_url", "");
                        break;
                    }
                }
                if (jarUrl.isEmpty()) throw new Exception("authlib-injector");
                Net.download(jarUrl, aj, "", null);
                log("authlib-injector 已下载");
            }
            parts.add("-javaagent:" + aj.getAbsolutePath() + "=" + server);
            accessToken = UUID.randomUUID().toString().replace("-", "");
        }

        // natives
        File nativesDir = new File(mcDir, "versions/" + vid + "/natives");
        if (nativesDir.isDirectory()) {
            parts.add("-Djava.library.path=" + nativesDir.getAbsolutePath());
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
                String path = mavenPath(name);
                if (path.isEmpty()) continue;
                File f = new File(mcDir, "libraries/" + path);
                if (f.exists()) {
                    if (cp.length() > 0) cp.append(sep);
                    cp.append(f.getAbsolutePath());
                }
            }
        }
        File cj = new File(mcDir, "versions/" + vid + "/" + vid + ".jar");
        if (cp.length() > 0) cp.append(sep);
        cp.append(cj.getAbsolutePath());
        parts.add("-cp");
        parts.add(cp.toString());
        parts.add(vdata.optString("mainClass", "net.minecraft.client.main.Main"));
        parts.add("--gameDir"); parts.add(mcDir.getAbsolutePath());
        parts.add("--assetsDir"); parts.add(new File(mcDir, "assets").getAbsolutePath());
        parts.add("--assetIndex"); parts.add(vdata.optJSONObject("assetIndex") != null
                ? vdata.optJSONObject("assetIndex").optString("id", "legacy") : "legacy");
        parts.add("--uuid"); parts.add(authUuid);
        parts.add("--accessToken"); parts.add(accessToken);
        parts.add("--username"); parts.add(username);
        parts.add("--version"); parts.add(vid);

        // 保存命令
        File lf = new File(mcDir, "launch_cmd.txt");
        try (FileOutputStream fo = new FileOutputStream(lf)) {
            fo.write(String.join(" ", parts).getBytes("UTF-8"));
        }
        return String.join(" ", parts);
    }

    void launchGame() {
        String vid = installedVersions.isEmpty() ? null : installedVersions.get(0);
        if (vid == null) { toast(tr("install_first")); return; }
        try {
            File vj = new File(mcDir, "versions/" + vid + "/" + vid + ".json");
            JSONObject vdata = new JSONObject(new String(Files.readAllBytes(vj.toPath()), "UTF-8"));
            final String cmd = buildLaunchCommand(vid, vdata);
            final File jre = new File(mcDir, "runtime/jre/bin/java");
            log("启动命令已生成：" + cmd.substring(0, Math.min(cmd.length(), 200)));
            if (!jre.exists()) {
                // 运行栈未安装：引导
                setBusy(false, tr("checking_runtime"));
                new AlertDialog.Builder(this)
                        .setTitle(tr("runtime_check"))
                        .setMessage(tr("runtime_missing") + mcDir.getAbsolutePath() + "/runtime/\n\n" + tr("launch_info"))
                        .setPositiveButton(tr("open_site"), (d, w) -> openUrl("https://etqwfd.github.io/NebulaLauncher/"))
                        .setNegativeButton(tr("got_it"), null)
                        .show();
                return;
            }
            // 真实启动：运行栈 java 执行主类，启动后启动器自动让位（finish 本界面）
            setBusy(true, tr("launching"));
            btnLaunch.setEnabled(false);
            new Thread(() -> {
                try {
                    java.util.List<String> cmdParts = new ArrayList<>();
                    cmdParts.add(jre.getAbsolutePath());
                    cmdParts.add("-Xmx" + memMb + "M");
                    cmdParts.add("-Xms" + Math.max(memMb / 4, 256) + "M");
                    Collections.addAll(cmdParts, JVM_FLAGS);
                    File nativesDir = new File(mcDir, "versions/" + vid + "/natives");
                    if (nativesDir.isDirectory()) {
                        cmdParts.add("-Djava.library.path=" + nativesDir.getAbsolutePath());
                    }
                    StringBuilder cp = new StringBuilder();
                    JSONArray libs = vdata.optJSONArray("libraries");
                    if (libs != null) {
                        for (int i = 0; i < libs.length(); i++) {
                            JSONObject lib = libs.getJSONObject(i);
                            if (!rulesOk(lib)) continue;
                            String path = mavenPath(lib.optString("name", ""));
                            if (path.isEmpty()) continue;
                            File f = new File(mcDir, "libraries/" + path);
                            if (f.exists()) {
                                if (cp.length() > 0) cp.append(":");
                                cp.append(f.getAbsolutePath());
                            }
                        }
                    }
                    File cj = new File(mcDir, "versions/" + vid + "/" + vid + ".jar");
                    if (cp.length() > 0) cp.append(":");
                    cp.append(cj.getAbsolutePath());
                    cmdParts.add("-cp");
                    cmdParts.add(cp.toString());
                    int acctPos = spAccount.getSelectedItemPosition();
                    String username = etName.getText().toString().trim();
                    if (username.isEmpty()) username = "Steve";
                    String authUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes()).toString().replace("-", "");
                    if (acctPos == 1) {
                        File aj = new File(mcDir, "runtime/authlib-injector.jar");
                        if (aj.exists()) cmdParts.add("-javaagent:" + aj.getAbsolutePath() + "=" + etServer.getText().toString().trim());
                        cmdParts.add("-Dauthlib-injector.allow-url-paste=true");
                    }
                    cmdParts.add(vdata.optString("mainClass", "net.minecraft.client.main.Main"));
                    cmdParts.add("--gameDir"); cmdParts.add(mcDir.getAbsolutePath());
                    cmdParts.add("--assetsDir"); cmdParts.add(new File(mcDir, "assets").getAbsolutePath());
                    cmdParts.add("--assetIndex"); cmdParts.add(vdata.optJSONObject("assetIndex") != null
                            ? vdata.optJSONObject("assetIndex").optString("id", "legacy") : "legacy");
                    cmdParts.add("--uuid"); cmdParts.add(authUuid);
                    cmdParts.add("--accessToken"); cmdParts.add("0");
                    cmdParts.add("--username"); cmdParts.add(username);
                    cmdParts.add("--version"); cmdParts.add(vid);

                    ProcessBuilder pb = new ProcessBuilder(cmdParts);
                    pb.directory(mcDir);
                    File logF = new File(mcDir, "game.log");
                    pb.redirectErrorStream(true);
                    pb.redirectOutput(logF);
                    Process proc = pb.start();
                    log("游戏进程已启动：" + proc + "，启动器自动让位");
                    runOnUiThread(() -> {
                        setBusy(false, tr("launched_yield"));
                        if (autoYield) {
                            // 自动让位：启动器退出前台，只留游戏运行
                            moveTaskToBack(true);
                        }
                    });
                } catch (Exception e) {
                    final String err = e.getMessage();
                    log("游戏启动失败：" + err);
                    runOnUiThread(() -> {
                        setBusy(false, tr("launch_prepare_fail"));
                        btnLaunch.setEnabled(true);
                        toast(tr("launch_prepare_fail") + ": " + err);
                    });
                }
            }).start();
        } catch (Exception e) {
            toast(tr("launch_prepare_fail") + ": " + e.getMessage());
        }
    }

    // ============================================================
    // Adapters
    // ============================================================
    class SimpleAdapter extends BaseAdapter {
        List<String> items;
        java.util.function.Consumer<String> onDelete;
        boolean selectable;
        SimpleAdapter(List<String> items, List<String> shown, java.util.function.Consumer<String> onDelete, boolean selectable) {
            this.items = shown != null ? shown : items;
            this.onDelete = onDelete;
            this.selectable = selectable;
        }
        public int getCount() { return items.size(); }
        public Object getItem(int i) { return items.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int i, View cv, ViewGroup p) {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(cardBg(8, c(1)));
            row.setPadding((int) dp(10), (int) dp(8), (int) dp(10), (int) dp(8));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = (int) dp(6);
            row.setLayoutParams(rp);
            final String name = items.get(i);
            row.addView(mkText(name, 14, c(4), 1), lpW(1));
            row.setOnClickListener(v -> {
                if (selectable) {
                    // 选择版本
                    int idx = installedVersions.indexOf(name);
                    if (idx >= 0) {
                        Collections.swap(installedVersions, 0, idx);
                        refreshInstalled();
                        toast(name);
                    }
                }
            });
            return row;
        }
    }

    class InstalledVersionAdapter extends BaseAdapter {
        List<String> items;
        InstalledVersionAdapter(List<String> items) { this.items = items; }
        public int getCount() { return items.size(); }
        public Object getItem(int i) { return items.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int i, View cv, ViewGroup p) {
            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(cardBg(8, c(1)));
            row.setPadding((int) dp(10), (int) dp(6), (int) dp(10), (int) dp(6));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = (int) dp(6);
            row.setLayoutParams(rp);
            final String name = items.get(i);
            row.addView(mkText(name, 13, c(4), 0), lpW(1));
            Button bs = new Button(MainActivity.this);
            bs.setText(tr("nav_launch"));
            bs.setTextSize(12 * textScale);
            bs.setTextColor(Color.WHITE);
            bs.setBackground(cardBg(6, c(3)));
            bs.setPadding((int) dp(8), 0, (int) dp(8), 0);
            bs.setOnClickListener(v -> {
                int idx = installedVersions.indexOf(name);
                if (idx > 0) { Collections.swap(installedVersions, 0, idx); refreshInstalled(); }
                switchPage(0);
            });
            row.addView(bs);
            Button bd = new Button(MainActivity.this);
            bd.setText(tr("del"));
            bd.setTextSize(12 * textScale);
            bd.setTextColor(0xFFFF6B6B);
            bd.setBackground(cardBg(6, 0xFF2A1F1F));
            bd.setPadding((int) dp(8), 0, (int) dp(8), 0);
            bd.setOnClickListener(v -> new AlertDialog.Builder(MainActivity.this)
                    .setTitle(name)
                    .setMessage(tr("del") + " " + name + "?")
                    .setPositiveButton(tr("ok"), (d, w) -> {
                        deleteItem(new File(mcDir, "versions/" + name));
                        refreshInstalled();
                        toast(tr("del"));
                    })
                    .setNegativeButton(tr("cancel"), null)
                    .show());
            row.addView(bd);
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
            row.setBackground(cardBg(8, c(1)));
            row.setPadding((int) dp(10), (int) dp(6), (int) dp(10), (int) dp(6));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = (int) dp(6);
            row.setLayoutParams(rp);
            String name = items.get(i);
            boolean disabled = name.endsWith(".disabled");
            row.addView(mkText(disabled ? name.substring(0, name.length() - 9) : name, 13,
                    disabled ? c(5) : c(4), 0), lpW(1));
            Button b = new Button(MainActivity.this);
            b.setText(disabled ? tr("enable") : tr("disable"));
            b.setTextSize(12 * textScale);
            b.setTextColor(c(4));
            b.setBackground(cardBg(6, themeColor(1, 0xFF1F2A3D)));
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
            bd.setText(tr("del"));
            bd.setTextSize(12 * textScale);
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
            row.setBackground(cardBg(8, c(1)));
            row.setPadding((int) dp(10), (int) dp(6), (int) dp(10), (int) dp(6));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = (int) dp(6);
            row.setLayoutParams(rp);
            final String name = items.get(i);
            row.addView(mkText(name, 13, c(4), 0), lpW(1));
            Button bd = new Button(MainActivity.this);
            bd.setText(tr("del"));
            bd.setTextSize(12 * textScale);
            bd.setTextColor(0xFFFF6B6B);
            bd.setBackground(cardBg(6, 0xFF2A1F1F));
            bd.setPadding((int) dp(8), 0, (int) dp(8), 0);
            bd.setOnClickListener(v -> onDelete.accept(name));
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
            row.setBackground(cardBg(8, c(1)));
            row.setPadding((int) dp(10), (int) dp(6), (int) dp(10), (int) dp(6));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = (int) dp(6);
            row.setLayoutParams(rp);
            final Map<String, String> m = items.get(i);
            LinearLayout left = new LinearLayout(MainActivity.this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.addView(mkText(m.get("name"), 13, c(4), 0));
            String sub = m.get("dl") != null ? m.get("dl") + " DL"
                    : (m.get("size") != null && !m.get("size").isEmpty()
                    ? String.format(Locale.US, "%.1f MB", Long.parseLong(m.get("size")) / 1048576.0) : "");
            left.addView(mkText(sub, 11, c(5), 0));
            row.addView(left, lpW(1));
            Button b = new Button(MainActivity.this);
            b.setText(tr("download"));
            b.setTextSize(12 * textScale);
            b.setTextColor(Color.WHITE);
            b.setBackground(cardBg(6, c(3)));
            b.setPadding((int) dp(10), 0, (int) dp(10), 0);
            b.setOnClickListener(v -> onAction.accept(m));
            row.addView(b);
            return row;
        }
    }

    static int indexOf(String[] arr, String v) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(v)) return i;
        return 0;
    }
}
