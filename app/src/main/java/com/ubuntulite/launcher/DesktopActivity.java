package com.ubuntulite.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

public final class DesktopActivity extends Activity {
    private static final int BG = Color.rgb(24, 17, 31);
    private static final int FG = Color.rgb(249, 246, 252);
    private static final int MUTED = Color.rgb(193, 182, 202);
    private static final int ORANGE = Color.rgb(233, 84, 32);

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final StringBuilder logBuffer = new StringBuilder();
    private FrameLayout root;
    private WebView desktopView;
    private LinearLayout bootPanel;
    private TextView bootTitle, bootStatus, bootLog;
    private ProgressBar bootProgress;
    private ScrollView logScroll;
    private Button installButton, retryButton, keyboardButton;
    private Process desktopProcess;
    private Process nativeX11Process;
    private File rootfs, prootTmp, x11Tmp;
    private volatile boolean desktopReady;
    private volatile boolean shuttingDown;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        rootfs = new File(getFilesDir(), "ubuntu-rootfs");
        prootTmp = new File(getFilesDir(), "proot-tmp");
        x11Tmp = new File(getFilesDir(), "x11-shared-tmp");
        prootTmp.mkdirs();
        x11Tmp.mkdirs();
        x11Tmp.setReadable(true, true);
        x11Tmp.setWritable(true, true);
        x11Tmp.setExecutable(true, true);
        prootTmp.setReadable(true, true);
        prootTmp.setWritable(true, true);
        prootTmp.setExecutable(true, true);
        buildScreen();

        showBoot("Preparing your Ubuntu desktop", "First launch prepares the desktop already included in the app. Please keep the app open.");
        appendLog("Ubuntu Desktop is bundled inside this APK; first launch only extracts it into app storage.");
        startDesktopSession();
    }

    private int dp(float n) {
        return (int)(n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private TextView label(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private void buildScreen() {
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        bootPanel = vertical();
        bootPanel.setPadding(dp(24), dp(22), dp(24), dp(20));
        bootPanel.setBackgroundColor(BG);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        TextView mark = label("●", 34, ORANGE);
        mark.setTypeface(Typeface.DEFAULT_BOLD);
        brand.addView(mark, new LinearLayout.LayoutParams(dp(42), dp(47)));
        TextView word = label("Ubuntu", 28, FG);
        word.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        brand.addView(word, new LinearLayout.LayoutParams(-1, dp(47)));
        bootPanel.addView(brand);

        bootTitle = label("Ubuntu Desktop", 26, FG);
        bootTitle.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.topMargin = dp(26);
        bootPanel.addView(bootTitle, titleLp);

        bootStatus = label("Ubuntu 22.04 LTS · ARM64 · XFCE", 14, MUTED);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(9);
        bootPanel.addView(bootStatus, statusLp);

        bootProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bootProgress.setIndeterminate(true);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(-1, dp(6));
        progressLp.topMargin = dp(18);
        bootPanel.addView(bootProgress, progressLp);

        installButton = new Button(this);
        installButton.setText("Start Ubuntu Desktop");
        installButton.setAllCaps(false);
        installButton.setTextSize(16);
        installButton.setOnClickListener(v -> startDesktopSession());
        LinearLayout.LayoutParams buttonLp = new LinearLayout.LayoutParams(-1, dp(54));
        buttonLp.topMargin = dp(22);
        bootPanel.addView(installButton, buttonLp);

        retryButton = new Button(this);
        retryButton.setText("Restart Ubuntu Desktop");
        retryButton.setAllCaps(false);
        retryButton.setVisibility(View.GONE);
        retryButton.setOnClickListener(v -> startDesktopSession());
        LinearLayout.LayoutParams retryLp = new LinearLayout.LayoutParams(-1, dp(48));
        retryLp.topMargin = dp(8);
        bootPanel.addView(retryButton, retryLp);

        TextView details = label("Native high-resolution X11 · Firefox · Python/Git tools · Touch-ready XFCE", 13, MUTED);
        details.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams detailsLp = new LinearLayout.LayoutParams(-1, -2);
        detailsLp.topMargin = dp(14);
        bootPanel.addView(details, detailsLp);

        logScroll = new ScrollView(this);
        logScroll.setBackground(rounded(Color.rgb(7, 5, 10), 12));
        bootLog = label("", 11, Color.rgb(215, 209, 220));
        bootLog.setTypeface(Typeface.MONOSPACE);
        bootLog.setGravity(Gravity.TOP);
        bootLog.setPadding(dp(11), dp(10), dp(11), dp(10));
        logScroll.addView(bootLog);
        LinearLayout.LayoutParams logsLp = new LinearLayout.LayoutParams(-1, 0, 1);
        logsLp.topMargin = dp(16);
        logScroll.setVisibility(View.GONE);
        bootPanel.addView(logScroll, logsLp);

        root.addView(bootPanel, new FrameLayout.LayoutParams(-1, -1));

        setContentView(root);
    }

    private void showInstallScreen() {
        bootPanel.setVisibility(View.VISIBLE);
        installButton.setVisibility(View.VISIBLE);
        installButton.setEnabled(true);
        installButton.setText("Start Ubuntu Desktop");
        retryButton.setVisibility(View.GONE);
        bootTitle.setText("Ubuntu Desktop");
        bootStatus.setText("Ubuntu 22.04 LTS · XFCE · Dark mountain theme");
        appendLog("Ubuntu desktop is preinstalled in the APK. No first-run package download is required.");
        appendLog("First start unpacks the local image and may take several minutes; leave several GB of free storage.");
    }

    private void updateBootProgress(String status) {
        runOnUiThread(() -> {
            bootPanel.setVisibility(View.VISIBLE);
            bootTitle.setText("Starting Ubuntu desktop");
            bootStatus.setText(status);
            bootProgress.setVisibility(View.VISIBLE);
        });
    }

    private void showBoot(String title, String status) {
        runOnUiThread(() -> {
            bootPanel.setVisibility(View.VISIBLE);
            bootTitle.setText(title);
            bootStatus.setText(status);
            if (title.toLowerCase(java.util.Locale.ROOT).contains("error")) {
                bootProgress.setVisibility(View.GONE);
                logScroll.setVisibility(View.VISIBLE);
                retryButton.setVisibility(View.VISIBLE);
            }
        });
    }

    private void installDesktop() {
        // The complete Ubuntu desktop is bundled in the APK; no package bootstrap is needed.
        startDesktopSession();
    }

    private void startDesktopSession() {
        if (desktopProcess != null && desktopProcess.isAlive()) {
            worker.execute(this::waitForNativeDesktop);
            return;
        }
        desktopReady = false;
        runOnUiThread(() -> {
            bootPanel.setVisibility(View.VISIBLE);
            installButton.setVisibility(View.GONE);
            retryButton.setVisibility(View.GONE);
            bootTitle.setText("Starting Ubuntu desktop");
            bootStatus.setText("Step 1/3: Preparing the included Ubuntu files…");
        });
        worker.execute(() -> {
            try {
                updateBootProgress("Step 1/3: Preparing Ubuntu files…");
                installRootfsIfNeeded();
                updateBootProgress("Step 2/3: Configuring the desktop…");
                configureDns();
                copyAssetToRootfs("ubuntu-desktop-start.sh", "root/ubuntu-desktop-start.sh");
                updateBootProgress("Step 3/4: Starting native high-resolution X11 renderer…");
                Process nativeServer = launchNativeX11Server();
                nativeX11Process = nativeServer;
                Thread nativeReader = new Thread(() -> readLog(nativeServer), "ubuntu-native-x11-log");
                nativeReader.setDaemon(true);
                nativeReader.start();
                if (!waitForNativeProcessStart(nativeServer)) {
                    throw new IllegalStateException("Native X11 server exited during startup. Check the diagnostic log.");
                }
                updateBootProgress("Step 4/4: Opening native display and starting XFCE…");
                runOnUiThread(this::openNativeX11Activity);
                Thread.sleep(1200);
                Process p = launchScript("root/ubuntu-desktop-start.sh");
                desktopProcess = p;
                Thread reader = new Thread(() -> readLog(p), "ubuntu-desktop-log");
                reader.setDaemon(true);
                reader.start();
                waitForNativeDesktop();
            } catch (Exception e) {
                appendLog("DESKTOP START FAILED: " + e.getMessage());
                runOnUiThread(() -> {
                    bootTitle.setText("Ubuntu desktop did not start");
                    bootStatus.setText("Startup error: " + (e.getMessage() == null ? "Unknown error" : e.getMessage()) + "\nSee diagnostic details below, then retry.");
                    bootProgress.setVisibility(View.GONE);
                    logScroll.setVisibility(View.VISIBLE);
                    retryButton.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private int runFiniteScript(String guestScript) throws Exception {
        Process p = launchScript(guestScript);
        readLog(p);
        int code = p.waitFor();
        if (code != 0) appendLog("Setup process exited with code " + code);
        return code;
    }

    private Process launchScript(String guestScript) throws Exception {
        File proot = new File(getApplicationInfo().nativeLibraryDir, "libproot.so");
        File loader = new File(getApplicationInfo().nativeLibraryDir, "libproot-loader.so");
        if (!proot.canExecute() || !loader.isFile()) throw new IllegalStateException("Bundled ARM64 PRoot runtime not found.");
        List<String> args = new ArrayList<>();
        args.add(proot.getAbsolutePath());
        String[] rest = {
            "-r", rootfs.getAbsolutePath(), "-w", "/root", "-0",
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            "-b", x11Tmp.getAbsolutePath() + ":/tmp",
            "/usr/bin/env", "-i",
            "HOME=/root", "TMPDIR=/tmp", "TEMP=/tmp", "TMP=/tmp",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color", "LANG=C.UTF-8", "DEBIAN_FRONTEND=noninteractive",
            "/bin/bash", "/" + guestScript
        };
        java.util.Collections.addAll(args, rest);
        ProcessBuilder pb = new ProcessBuilder(args).redirectErrorStream(true);
        pb.environment().put("PROOT_LOADER", loader.getAbsolutePath());
        // Termux's packaged PRoot defaults to Termux's own private tmp path.
        // Override it with a writable path owned by this APK.
        pb.environment().put("PROOT_TMP_DIR", prootTmp.getAbsolutePath());
        pb.environment().put("TMPDIR", prootTmp.getAbsolutePath());
        pb.environment().put("LD_LIBRARY_PATH", getApplicationInfo().nativeLibraryDir);
        pb.environment().put("PROOT_NO_SECCOMP", "1");
        return pb.start();
    }

    private Process launchNativeX11Server() throws Exception {
        File x11Library = new File(getApplicationInfo().nativeLibraryDir, "libXlorie.so");
        if (!x11Library.isFile()) throw new IllegalStateException("Native X11 library libXlorie.so is missing from the APK.");
        List<String> command = new ArrayList<>();
        command.add("/system/bin/app_process");
        command.add("/");
        command.add("--nice-name=ubuntu-lite-x11");
        command.add("com.termux.x11.CmdEntryPoint");
        command.add(":0");
        command.add("-legacy-drawing");
        command.add("-dpi");
        command.add("180");
        command.add("-ac");
        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
        pb.environment().put("CLASSPATH", getApplicationInfo().sourceDir);
        pb.environment().put("UBUNTU_LITE_NATIVE_LIBRARY_DIR", getApplicationInfo().nativeLibraryDir);
        pb.environment().put("LD_LIBRARY_PATH", getApplicationInfo().nativeLibraryDir);
        pb.environment().put("TMPDIR", x11Tmp.getAbsolutePath());
        pb.environment().put("HOME", getFilesDir().getAbsolutePath());
        return pb.start();
    }

    private boolean waitForNativeProcessStart(Process process) throws InterruptedException {
        for (int i = 0; i < 12; i++) {
            if (!process.isAlive()) return false;
            Thread.sleep(250);
        }
        return process.isAlive();
    }

    private void openNativeX11Activity() {
        try {
            Intent intent = new Intent();
            intent.setClassName(getPackageName(), "com.termux.x11.MainActivity");
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        } catch (Exception e) {
            appendLog("Could not open native X11 renderer: " + e.getMessage());
        }
    }

    private void waitForNativeDesktop() {
        long deadline = System.currentTimeMillis() + 120000L;
        long nextProgressUpdate = 0L;
        while (!shuttingDown && System.currentTimeMillis() < deadline) {
            if (desktopReady) {
                runOnUiThread(() -> { bootPanel.setVisibility(View.GONE); bootProgress.setVisibility(View.GONE); });
                appendLog("Native X11 desktop ready; no VNC/noVNC bridge is used.");
                return;
            }
            Process p = desktopProcess;
            if (p == null || !p.isAlive()) {
                appendLog("XFCE/PRoot exited before native desktop startup completed.");
                showNativeStartupError("Ubuntu desktop failed to start", "XFCE exited before the native display was ready. Diagnostic output is shown below.");
                closeNativeDisplayActivity();
                return;
            }
            Process x = nativeX11Process;
            if (x == null || !x.isAlive()) {
                appendLog("Native X11 server exited before the desktop became ready.");
                showNativeStartupError("Native display failed", "The Android X11 renderer stopped. Diagnostic output is shown below.");
                return;
            }
            if (System.currentTimeMillis() >= nextProgressUpdate) {
                long seconds = (System.currentTimeMillis() - (deadline - 120000L)) / 1000L;
                updateBootProgress("Starting native Linux desktop… " + seconds + " seconds");
                nextProgressUpdate = System.currentTimeMillis() + 5000L;
            }
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
        if (!shuttingDown && !desktopReady) {
            showNativeStartupError("Native display timed out", "XFCE did not report ready within 120 seconds. Diagnostic output is shown below.");
            closeNativeDisplayActivity();
        }
    }

    private void showNativeStartupError(String title, String message) {
        runOnUiThread(() -> {
            bootPanel.setVisibility(View.VISIBLE);
            bootTitle.setText(title);
            bootStatus.setText(message);
            bootProgress.setVisibility(View.GONE);
            logScroll.setVisibility(View.VISIBLE);
            installButton.setVisibility(View.GONE);
            retryButton.setText("Restart Ubuntu Desktop");
            retryButton.setVisibility(View.VISIBLE);
            try { startActivity(new Intent(this, DesktopActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)); }
            catch (Exception ignored) {}
        });
    }

    private void closeNativeDisplayActivity() {
        try { sendBroadcast(new Intent("com.termux.x11.ACTION_STOP").setPackage(getPackageName())); }
        catch (Exception ignored) {}
    }

    private void copyAssetToRootfs(String asset, String relativePath) throws Exception {
        File dest = new File(rootfs, relativePath);
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Cannot create " + parent);
        }
        try (InputStream in = getAssets().open(asset); FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[32768]; int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
        dest.setReadable(true, false);
        dest.setWritable(true, false);
        dest.setExecutable(true, false);
    }

    private void installRootfsIfNeeded() throws Exception {
        File preinstalled = new File(rootfs, "root/.ubuntu-preinstalled-desktop-v1");
        if (preinstalled.isFile() && new File(rootfs, "bin/bash").exists()
                && new File(rootfs, "usr/sbin/synaptic").exists()) {
            appendLog("Using complete preinstalled Ubuntu desktop already unpacked on this device.");
            return;
        }
        appendLog("Unpacking the preinstalled Ubuntu desktop from this APK. No network package download is required…");
        delete(rootfs);
        if (!rootfs.mkdirs()) throw new IllegalStateException("Cannot create Ubuntu root filesystem.");
        Path base = rootfs.toPath().toAbsolutePath().normalize();
        List<TarArchiveEntry> links = new ArrayList<>();
        List<Path> linkPaths = new ArrayList<>();
        try (InputStream raw = getAssets().open("ubuntu-rootfs.dat");
             GZIPInputStream gz = new GZIPInputStream(raw);
             TarArchiveInputStream tar = new TarArchiveInputStream(gz)) {
            TarArchiveEntry entry; byte[] buf = new byte[65536];
            int extractedEntries = 0;
            while ((entry = tar.getNextTarEntry()) != null) {
                extractedEntries++;
                if (extractedEntries % 150 == 0) updateBootProgress("Step 1/3: Unpacking Ubuntu files (" + extractedEntries + " entries)…");
                String name = entry.getName().replaceFirst("^\\./", "").replaceAll("/+$", "");
                if (name.isEmpty()) continue;
                Path target = base.resolve(name).normalize();
                if (!target.startsWith(base)) continue;
                if (entry.isSymbolicLink() || entry.isLink()) {
                    links.add(entry); linkPaths.add(target); continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    target.toFile().setReadable(true, false);
                    target.toFile().setWritable(true, true);
                    target.toFile().setExecutable(true, true);
                } else if (entry.isFile()) {
                    Files.createDirectories(target.getParent());
                    try (FileOutputStream out = new FileOutputStream(target.toFile())) {
                        int n; while ((n = tar.read(buf)) != -1) out.write(buf, 0, n);
                    }
                    // Every extracted inode belongs to the Android app UID. In this
                    // private rootfs, owner-writable files allow dpkg to replace files
                    // and create backups while PRoot emulates guest root.
                    target.toFile().setReadable(true, false);
                    target.toFile().setWritable(true, true);
                    if ((entry.getMode() & 0111) != 0) target.toFile().setExecutable(true, true);
                }
            }
        }
        for (int i = 0; i < links.size(); i++) {
            TarArchiveEntry entry = links.get(i);
            Path target = linkPaths.get(i);
            try {
                Files.createDirectories(target.getParent());
                if (entry.isSymbolicLink()) {
                    Files.createSymbolicLink(target, java.nio.file.Paths.get(entry.getLinkName()));
                } else {
                    Path src = base.resolve(entry.getLinkName()).normalize();
                    if (src.startsWith(base) && Files.exists(src)) Files.createLink(target, src);
                }
            } catch (Exception ignored) {}
        }
        if (!new File(rootfs, "root/.ubuntu-preinstalled-desktop-v1").isFile()
                || !new File(rootfs, "usr/sbin/synaptic").isFile()) {
            throw new IllegalStateException("The bundled image is not a complete preinstalled Ubuntu desktop. This APK must be rebuilt.");
        }
        appendLog("Complete preinstalled Ubuntu desktop extracted locally.");
    }

    private void configureDns() throws Exception {
        List<String> servers = new ArrayList<>();
        try {
            ConnectivityManager cm = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            Network active = cm == null ? null : cm.getActiveNetwork();
            LinkProperties lp = active == null ? null : cm.getLinkProperties(active);
            if (lp != null) {
                for (InetAddress address : lp.getDnsServers()) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress() && !address.isLinkLocalAddress()) {
                        String v = address.getHostAddress();
                        if (!servers.contains(v)) servers.add(v);
                    }
                }
            }
        } catch (Exception ignored) {}
        if (!servers.contains("1.1.1.1")) servers.add("1.1.1.1");
        if (!servers.contains("8.8.8.8")) servers.add("8.8.8.8");
        File etc = new File(rootfs, "etc");
        if (!etc.exists() && !etc.mkdirs()) throw new IllegalStateException("Cannot create Ubuntu /etc");
        File resolv = new File(etc, "resolv.conf");
        if (Files.isSymbolicLink(resolv.toPath()) || resolv.exists()) Files.deleteIfExists(resolv.toPath());
        try (FileWriter fw = new FileWriter(resolv, false)) {
            fw.write("# DNS provided by Android network with public fallbacks\n");
            for (String dns : servers) fw.write("nameserver " + dns + "\n");
            fw.write("options timeout:3 attempts:3\n");
        }
        appendLog("Wrote DNS settings (" + servers.size() + " resolvers).");
    }

    private void readLog(Process p) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if ("UBUNTU_DESKTOP_DISPLAY_READY".equals(line.trim())) desktopReady = true;
                appendLog(line);
            }
        } catch (Exception e) {
            appendLog("Log reader: " + e.getMessage());
        }
    }

    private void appendLog(String line) {
        runOnUiThread(() -> {
            logBuffer.append(line == null ? "" : line).append('\n');
            if (logBuffer.length() > 14000) logBuffer.delete(0, logBuffer.length() - 10000);
            if (bootLog != null) bootLog.setText(logBuffer.toString());
            if (logScroll != null && logScroll.getVisibility() == View.VISIBLE) {
                logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
            }
        });
    }

    private void delete(File f) {
        if (!f.exists()) return;
        if (f.isDirectory() && !Files.isSymbolicLink(f.toPath())) {
            File[] children = f.listFiles();
            if (children != null) for (File child : children) delete(child);
        }
        try { Files.deleteIfExists(f.toPath()); } catch (Exception ignored) {}
    }

    private void stopDesktop() {
        shuttingDown = true;
        try { if (desktopProcess != null) desktopProcess.destroy(); } catch (Exception ignored) {}
        try { if (nativeX11Process != null) nativeX11Process.destroy(); } catch (Exception ignored) {}
        desktopProcess = null;
        nativeX11Process = null;
        desktopReady = false;
        closeNativeDisplayActivity();
        showInstallScreen();
        shuttingDown = false;
    }

    @Override public void onBackPressed() {
        if (bootPanel.getVisibility() == View.GONE) {
            new AlertDialog.Builder(this)
                .setTitle("Ubuntu Desktop")
                .setMessage("Stop the Ubuntu desktop session?")
                .setNegativeButton("Keep running", (d, w) -> d.dismiss())
                .setPositiveButton("Stop Ubuntu", (d, w) -> stopDesktop())
                .show();
        } else {
            super.onBackPressed();
        }
    }

    @Override protected void onDestroy() {
        shuttingDown = true;
        try { if (desktopProcess != null) desktopProcess.destroy(); } catch (Exception ignored) {}
        try { if (nativeX11Process != null) nativeX11Process.destroy(); } catch (Exception ignored) {}
        if (desktopView != null) desktopView.destroy();
        worker.shutdownNow();
        super.onDestroy();
    }
}
