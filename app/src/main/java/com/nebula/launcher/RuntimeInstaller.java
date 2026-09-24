package com.nebula.launcher;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 内置运行时安装器：把 APK assets 中内置的 JRE21 / LWJGL3 解压到应用私有目录。
 * APK 体积大 = 真实内置运行时（JVM + 图形栈），不再是空壳。
 */
public class RuntimeInstaller {
    private static final String TAG = "NebulaRuntime";
    private final Context ctx;
    private final File filesDir;

    public RuntimeInstaller(Context ctx) {
        this.ctx = ctx;
        this.filesDir = ctx.getFilesDir();
    }

    /** 判断内置 JRE 是否已安装。 */
    public boolean jreInstalled() {
        return new File(filesDir, "runtime/jre/lib/modules").exists();
    }

    /** 拷贝 assets 资源到目标文件（流式，带进度回调）。 */
    private long copyAsset(String assetPath, File dest, ProgressCallback cb) throws Exception {
        long size = 0;
        try (InputStream in = ctx.getAssets().open(assetPath);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[1 << 16];
            int n;
            long last = 0;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                size += n;
                if (cb != null && size - last > (1 << 20)) { last = size; cb.onProgress(size); }
            }
        }
        return size;
    }

    private void copyTree(String assetDir, File outDir, ProgressCallback cb) throws Exception {
        String[] children = ctx.getAssets().list(assetDir);
        if (children == null) return;
        for (String child : children) {
            String full = assetDir.isEmpty() ? child : assetDir + "/" + child;
            File out = new File(outDir, child);
            if (ctx.getAssets().list(full) != null && ctx.getAssets().list(full).length > 0) {
                out.mkdirs();
                copyTree(full, out, cb);
            } else {
                out.getParentFile().mkdirs();
                copyAsset(full, out, cb);
            }
        }
    }

    /** 获取设备当前 ABI（arm64-v8a / armeabi-v7a / x86 / x86_64）。 */
    public static String currentAbi() {
        String abi = android.os.Build.SUPPORTED_ABIS != null && android.os.Build.SUPPORTED_ABIS.length > 0
                ? android.os.Build.SUPPORTED_ABIS[0] : android.os.Build.CPU_ABI;
        return abi == null ? "arm64-v8a" : abi;
    }

    /** 把 bin-<abi> 合并进 jre 根目录（bin/java + lib/*.so）。 */
    private void mergeBin(String abi, ProgressCallback cb) throws Exception {
        File jre = new File(filesDir, "runtime/jre");
        File binDir = new File(filesDir, "runtime/jre-bin-" + abi);
        if (!binDir.exists() || !jre.exists()) return;
        File srcBin = new File(binDir, "bin");
        File srcLib = new File(binDir, "lib");
        File dstBin = new File(jre, "bin");
        File dstLib = new File(jre, "lib");
        dstBin.mkdirs();
        if (srcBin.isDirectory()) {
            File[] fs = srcBin.listFiles();
            if (fs != null) for (File f : fs) {
                File d = new File(dstBin, f.getName());
                if (!d.exists() || d.length() != f.length()) copyFile(f, d);
            }
        }
        if (srcLib.isDirectory()) {
            File[] fs = srcLib.listFiles();
            if (fs != null) for (File f : fs) {
                File d = new File(dstLib, f.getName());
                if (!d.exists() || d.length() != f.length()) copyFile(f, d);
            }
        }
        // 清理 bin 包
        deleteRecursive(binDir);
        Log.i(TAG, "jre bin merged for " + abi);
    }

    private void copyFile(File src, File dst) throws Exception {
        dst.getParentFile().mkdirs();
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    private void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] cs = f.listFiles();
            if (cs != null) for (File c : cs) deleteRecursive(c);
        }
        f.delete();
    }

    /** 安装内置运行时（JRE + LWJGL jars + gl4es→libGL）。 */
    public boolean install(ProgressCallback cb) throws Exception {
        if (jreInstalled()) return true;
        File jre = new File(filesDir, "runtime/jre");
        jre.mkdirs();
        // universal 根
        copyTree("runtime/jre/universal", jre, cb);
        // bin-<abi>
        String abi = currentAbi();
        File binDir = new File(filesDir, "runtime/jre-bin-" + abi);
        binDir.mkdirs();
        copyTree("runtime/jre/bin-" + abi, binDir, cb);
        mergeBin(abi, cb);
        // LWJGL jars
        File lwjglDir = new File(filesDir, "runtime/lwjgl");
        lwjglDir.mkdirs();
        copyTree("runtime/lwjgl", lwjglDir, cb);
        // gl4es → libGL.so（供 LD_LIBRARY_PATH 使用）
        prepareGl4es();
        return true;
    }

    /** 把 gl4es 复制为 libGL.so，供游戏进程 LD_LIBRARY_PATH 使用。 */
    private void prepareGl4es() throws Exception {
        String abi = currentAbi();
        File dstDir = new File(filesDir, "runtime/gl4es");
        dstDir.mkdirs();
        File libGL = new File(dstDir, "libGL.so");
        if (!libGL.exists()) {
            String so = "runtime/gl4es/" + abi + "/libGL.so";
            try {
                copyAsset(so, libGL, null);
            } catch (Exception e) {
                // 无内置 gl4es 资源时跳过（用系统 EGL 直通）
                Log.w(TAG, "gl4es asset missing: " + so);
            }
        }
    }

    public File jreHome() { return new File(filesDir, "runtime/jre"); }
    public File lwjglDir() { return new File(filesDir, "runtime/lwjgl"); }
    public File gl4esDir() { return new File(filesDir, "runtime/gl4es"); }

    public interface ProgressCallback {
        void onProgress(long bytes);
    }
}
