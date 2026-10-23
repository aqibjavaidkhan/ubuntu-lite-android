package com.ubuntulite.launcher;

import android.app.Activity;
import android.app.AlertDialog;
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
    private File rootfs, prootTmp;
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
        prootTmp.mkdirs();
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

        desktopView = new WebView(this);
        desktopView.setBackgroundColor(Color.BLACK);
        WebSettings s = desktopView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        desktopView.setWebChromeClient(new WebChromeClient());
        desktopView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("http".equals(u.getScheme()) && "127.0.0.1".equals(u.getHost())) return false;
                return true;
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest req,
                                                   android.webkit.WebResourceError error) {
                if (req.isForMainFrame()) {
                    showBoot("Ubuntu display error", error.getDescription().toString());
                }
            }
        });
        root.addView(desktopView, new FrameLayout.LayoutParams(-1, -1));

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

        TextView details = label("Lightweight Ubuntu XFCE desktop. Firefox and LibreOffice are temporarily excluded until startup is stable; install them later through Ubuntu package tools.", 13, MUTED);
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

        // Touch-first floating control for the Android on-screen keyboard.
        keyboardButton = new Button(this);
        keyboardButton.setText("⌨  Keyboard");
        keyboardButton.setAllCaps(false);
        keyboardButton.setTextSize(13);
        keyboardButton.setElevation(dp(8));
        keyboardButton.setBackground(rounded(Color.rgb(37, 31, 45), 24));
        keyboardButton.setTextColor(FG);
        keyboardButton.setVisibility(View.GONE);
        keyboardButton.setOnClickListener(v -> desktopView.evaluateJavascript(
                "(function(){var b=document.getElementById('noVNC_keyboard_button');" +
                "if(b){b.click();return 'toolbar';}" +
                "var e=document.querySelector('textarea,input,[contenteditable=true]');" +
                "if(e){e.focus();return 'focused';}return 'no-keyboard-target';})()", null));
        FrameLayout.LayoutParams keyboardLp = new FrameLayout.LayoutParams(
                dp(132), dp(48), Gravity.BOTTOM | Gravity.END);
        keyboardLp.setMargins(dp(12), dp(12), dp(16), dp(20));
        root.addView(keyboardButton, keyboardLp);
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
            worker.execute(this::waitForDesktopBridge);
            return;
        }
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
                copyAssetToRootfs("ubuntu-desktop-start.sh", "tmp/ubuntu-desktop-start.sh");
                updateBootProgress("Step 3/3: Starting XFCE and connecting the display…");
                Process p = launchScript("tmp/ubuntu-desktop-start.sh");
                desktopProcess = p;
                Thread reader = new Thread(() -> readLog(p), "ubuntu-desktop-log");
                reader.setDaemon(true);
                reader.start();
                waitForDesktopBridge();
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

    private void waitForDesktopBridge() {
        long deadline = System.currentTimeMillis() + 120000L;
        long nextProgressUpdate = 0L;
        while (!shuttingDown && System.currentTimeMillis() < deadline) {
            long elapsedSeconds = (120000L - Math.max(0L, deadline - System.currentTimeMillis())) / 1000L;
            if (System.currentTimeMillis() >= nextProgressUpdate) {
                updateBootProgress("Step 3/3: Waiting for XFCE display… " + elapsedSeconds + " seconds");
                nextProgressUpdate = System.currentTimeMillis() + 5000L;
            }
            Process p = desktopProcess;
            if (p == null || !p.isAlive()) {
                appendLog("Desktop process exited before the embedded display became ready.");
                runOnUiThread(() -> {
                    bootTitle.setText("Ubuntu desktop failed to start");
                    bootStatus.setText("The desktop process stopped before the display was ready. Diagnostic output is shown below.");
                    bootProgress.setVisibility(View.GONE);
                    logScroll.setVisibility(View.VISIBLE);
                    installButton.setVisibility(View.GONE);
                    retryButton.setText("Restart Ubuntu Desktop");
                    retryButton.setVisibility(View.VISIBLE);
                });
                return;
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", 6080), 700);
                runOnUiThread(() -> {
                    desktopView.loadUrl("http://127.0.0.1:6080/vnc.html?autoconnect=1&resize=scale&reconnect=1&reconnect_delay=1000");
                    bootPanel.setVisibility(View.GONE);
                    if (keyboardButton != null) keyboardButton.setVisibility(View.VISIBLE);
                });
                return;
            } catch (Exception ignored) {}
            try { Thread.sleep(700); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
        runOnUiThread(() -> {
            bootTitle.setText("Ubuntu display timed out");
            bootStatus.setText("No desktop display after 120 seconds. Diagnostic output is shown below.");
            bootProgress.setVisibility(View.GONE);
            logScroll.setVisibility(View.VISIBLE);
            retryButton.setVisibility(View.VISIBLE);
        });
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
            while ((line = reader.readLine()) != null) appendLog(line);
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
        desktopProcess = null;
        desktopView.loadUrl("about:blank");
        if (keyboardButton != null) keyboardButton.setVisibility(View.GONE);
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
        if (desktopView != null) desktopView.destroy();
        worker.shutdownNow();
        super.onDestroy();
    }
}
