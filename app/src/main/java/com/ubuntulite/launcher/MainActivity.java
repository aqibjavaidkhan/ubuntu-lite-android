package com.ubuntulite.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.Editable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(27, 19, 35);
    private static final int BG_DARK = Color.rgb(15, 12, 20);
    private static final int CARD = Color.rgb(47, 37, 57);
    private static final int CARD_LIGHT = Color.rgb(65, 52, 77);
    private static final int FG = Color.rgb(250, 247, 252);
    private static final int MUTED = Color.rgb(194, 183, 202);
    private static final int ORANGE = Color.rgb(233, 84, 32);
    private static final int GREEN = Color.rgb(126, 231, 135);

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ExecutorService storeWorker = Executors.newSingleThreadExecutor();
    private final Object rootfsLock = new Object();
    private FrameLayout root;
    private LinearLayout homePage, drawerPage, terminalPage, quickPanel;
    private TextView terminalOutput, terminalState, clockText;
    private EditText commandInput, appSearch, storeSearch;
    private Button terminalStart, runButton;
    private Process shell;
    private BufferedWriter writer;
    private File rootfs;
    private volatile boolean busy;
    private float touchDownX, touchDownY;
    private boolean quickShowing = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        applyImmersiveUi();
        getWindow().setStatusBarColor(BG_DARK);
        getWindow().setNavigationBarColor(BG_DARK);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        rootfs = new File(getFilesDir(), "ubuntu-rootfs");
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        homePage = buildHome();
        drawerPage = buildDrawer();
        terminalPage = buildTerminal();
        root.addView(homePage, match());
        setContentView(root);
        showHome();
        root.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                touchDownX = event.getX();
                touchDownY = event.getY();
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                float dy = event.getY() - touchDownY;
                float dx = event.getX() - touchDownX;
                if (touchDownY < dp(70) && dy > dp(55)) { showQuickPanel(); return true; }
                if (dy < -dp(80) && !quickShowing && terminalPage.getParent() == null) { showDrawer(); return true; }
                if (dy > dp(90) && quickShowing) { hideQuickPanel(); return true; }
                if (Math.abs(dx) > dp(100) && Math.abs(dx) > Math.abs(dy)) {
                    if (dx < 0 && terminalPage.getParent() == null) showDrawer();
                    else if (dx > 0 && drawerPage.getParent() != null) showHome();
                    return true;
                }
            }
            return true;
        });
    }

    private void applyImmersiveUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.statusBars()
                        | android.view.WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveUi();
    }

    private boolean hasPhoneStorageAccess() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
    }
    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private GradientDrawable shape(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radiusDp)); return d;
    }
    private TextView text(String s, float size, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color); t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }
    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l;
    }
    private TextView pill(String label) {
        TextView t = text(label, 12, FG); t.setGravity(Gravity.CENTER); t.setBackground(shape(CARD_LIGHT, 24));
        t.setPadding(dp(11), dp(7), dp(11), dp(7)); return t;
    }
    private String nowTime() { return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()); }
    private int batteryPercent() {
        Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (i == null) return 100;
        int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 100);
        int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        return scale > 0 ? (level * 100 / scale) : 100;
    }

    private LinearLayout buildHome() {
        LinearLayout page = column();
        page.setPadding(dp(18), dp(8), dp(18), dp(7));
        page.setBackgroundColor(BG);

        LinearLayout top = row();
        TextView logo = text("●", 23, ORANGE); logo.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(logo, new LinearLayout.LayoutParams(dp(27), dp(38)));
        TextView brand = text("ubuntu", 19, FG); brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        top.addView(brand, new LinearLayout.LayoutParams(0, dp(38), 1));
        clockText = text(nowTime() + "  ◔", 13, MUTED); clockText.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        top.addView(clockText);
        page.addView(top);

        LinearLayout hero = column();
        hero.setPadding(dp(3), dp(30), dp(3), dp(20));
        hero.addView(text("Welcome back", 15, MUTED));
        TextView title = text("Ubuntu Touch", 31, FG);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        hero.addView(title);
        hero.addView(text("A Linux-inspired mobile experience", 13, MUTED));
        LinearLayout badges = row();
        TextView host = pill("●  Android host");
        TextView battery = pill("▰  " + batteryPercent() + "%");
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(-2, -2); badgeLp.rightMargin = dp(7);
        badges.addView(host, badgeLp); badges.addView(battery);
        LinearLayout.LayoutParams badgeWrap = new LinearLayout.LayoutParams(-1, -2); badgeWrap.topMargin = dp(13);
        hero.addView(badges, badgeWrap);
        page.addView(hero);

        LinearLayout sec = row();
        sec.addView(text("Favourites", 17, FG), new LinearLayout.LayoutParams(0, dp(35), 1));
        TextView all = pill("All apps  ↗"); all.setOnClickListener(v -> showDrawer()); sec.addView(all);
        page.addView(sec);

        GridLayout fav = new GridLayout(this); fav.setColumnCount(3); fav.setUseDefaultMargins(false);
        addTile(fav, "▣", "Terminal", ORANGE, () -> openTerminal());
        addTile(fav, "🌐", "Browser", Color.rgb(55, 124, 218), () -> launchUrl("https://ubuntu.com"));
        addTile(fav, "▤", "Files", Color.rgb(215, 153, 52), () -> openFiles());
        addTile(fav, "⚙", "Settings", Color.rgb(104, 101, 219), () -> launchSettings());
        addTile(fav, "▦", "Gallery", Color.rgb(181, 79, 140), () -> openGallery());
        addTile(fav, "⌗", "Calculator", Color.rgb(43, 151, 122), () -> openCalculator());
        addTile(fav, "▧", "App Store", Color.rgb(52, 145, 113), () -> showAppStore());
        addTile(fav, "▣", "Linux Desktop", Color.rgb(233, 84, 32), () -> openDesktop());
        page.addView(fav, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout dock = row(); dock.setGravity(Gravity.CENTER); dock.setPadding(0, dp(7), 0, dp(5));
        addDockIcon(dock, "▤", "Files", () -> openFiles());
        addDockIcon(dock, "🌐", "Web", () -> launchUrl("https://ubuntu.com"));
        addDockIcon(dock, "●", "Apps", () -> showDrawer());
        addDockIcon(dock, "▣", "Terminal", () -> openTerminal());
        addDockIcon(dock, "⚙", "Settings", () -> launchSettings());
        page.addView(dock);
        TextView gesture = text("Swipe down for quick settings  ·  swipe up for apps", 10, MUTED);
        gesture.setGravity(Gravity.CENTER); page.addView(gesture);
        return page;
    }

    private void addTile(GridLayout grid, String icon, String name, int color, Runnable action) {
        LinearLayout tile = column(); tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(3), dp(8), dp(3), dp(7)); tile.setBackground(shape(CARD, 18));
        TextView glyph = text(icon, 28, FG); glyph.setGravity(Gravity.CENTER); glyph.setBackground(shape(color, 18));
        tile.addView(glyph, new LinearLayout.LayoutParams(dp(51), dp(51)));
        TextView label = text(name, 12, FG); label.setGravity(Gravity.CENTER); label.setPadding(0, dp(6), 0, 0);
        tile.addView(label); tile.setContentDescription(name);
        tile.setOnClickListener(v -> action.run());
        GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
        gp.width = 0; gp.height = dp(106); gp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        gp.setMargins(dp(4), dp(4), dp(4), dp(4));
        grid.addView(tile, gp);
    }

    private void addDockIcon(LinearLayout dock, String icon, String label, Runnable action) {
        LinearLayout item = column(); item.setGravity(Gravity.CENTER);
        item.setPadding(dp(6), dp(4), dp(6), dp(2));
        TextView glyph = text(icon, 22, FG); glyph.setGravity(Gravity.CENTER); glyph.setBackground(shape(CARD_LIGHT, 15));
        item.addView(glyph, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView caption = text(label, 9, MUTED); caption.setGravity(Gravity.CENTER); item.addView(caption);
        item.setOnClickListener(v -> action.run()); dock.addView(item);
    }

    private LinearLayout buildDrawer() {
        LinearLayout page = column(); page.setPadding(dp(18), dp(8), dp(18), dp(8)); page.setBackgroundColor(BG);
        LinearLayout head = row();
        head.addView(text("Applications", 27, FG), new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView close = pill("✕"); close.setOnClickListener(v -> showHome()); head.addView(close);
        page.addView(head);
        appSearch = new EditText(this); appSearch.setSingleLine(true); appSearch.setTextColor(FG); appSearch.setHintTextColor(MUTED);
        appSearch.setHint("Search apps"); appSearch.setBackground(shape(CARD, 15));
        appSearch.setPadding(dp(13), dp(5), dp(13), dp(5)); page.addView(appSearch, new LinearLayout.LayoutParams(-1, dp(45)));
        ScrollView sc = new ScrollView(this); GridLayout grid = new GridLayout(this); grid.setColumnCount(3); grid.setUseDefaultMargins(false);
        addTile(grid, "▣", "Terminal", ORANGE, () -> openTerminal());
        addTile(grid, "🌐", "Browser", Color.rgb(55,124,218), () -> launchUrl("https://ubuntu.com"));
        addTile(grid, "▤", "Files", Color.rgb(215,153,52), () -> openFiles());
        addTile(grid, "⚙", "Settings", Color.rgb(104,101,219), () -> launchSettings());
        addTile(grid, "▦", "Gallery", Color.rgb(181,79,140), () -> openGallery());
        addTile(grid, "⌗", "Calculator", Color.rgb(43,151,122), () -> openCalculator());
        addTile(grid, "▧", "App Store", Color.rgb(52,145,113), () -> showAppStore());
        addTile(grid, "▣", "Linux Desktop", Color.rgb(233,84,32), () -> openDesktop());
        addTile(grid, "♫", "Music", Color.rgb(206,85,99), () -> launchUrl("https://music.youtube.com"));
        addTile(grid, "◷", "Clock", Color.rgb(50,147,185), () -> openClock());
        addTile(grid, "?", "About", Color.rgb(104,112,127), () -> showAbout());
        sc.addView(grid); page.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView foot = text("Ubuntu Touch-inspired shell · swipe right to go home", 10, MUTED);
        foot.setGravity(Gravity.CENTER); page.addView(foot);
        appSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s.toString().toLowerCase(Locale.ROOT);
                for (int i=0; i<grid.getChildCount(); i++) {
                    View child = grid.getChildAt(i);
                    String label = child.getContentDescription() == null ? "" : child.getContentDescription().toString().toLowerCase(Locale.ROOT);
                    child.setVisibility(label.contains(q) ? View.VISIBLE : View.GONE);
                }
            }
            public void afterTextChanged(Editable s) {}
        });
        return page;
    }

    private void showAppStore() {
        hideQuickPanel();
        LinearLayout page = column();
        page.setPadding(dp(16), dp(10), dp(16), dp(12));
        page.setBackgroundColor(BG);
        LinearLayout header = row();
        TextView back = pill("‹ Home");
        back.setOnClickListener(v -> showHome());
        header.addView(back);
        TextView heading = text("  App Store", 24, FG);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.addView(heading, new LinearLayout.LayoutParams(0, dp(48), 1));
        page.addView(header);
        page.addView(text("Install real Ubuntu repository packages", 12, MUTED));

        storeSearch = new EditText(this);
        storeSearch.setSingleLine(true);
        storeSearch.setTextColor(FG);
        storeSearch.setHintTextColor(MUTED);
        storeSearch.setHint("Search featured apps");
        storeSearch.setBackground(shape(CARD, 14));
        storeSearch.setPadding(dp(12), dp(5), dp(12), dp(5));
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, dp(46));
        searchLp.topMargin = dp(12);
        page.addView(storeSearch, searchLp);

        ScrollView scroll = new ScrollView(this);
        LinearLayout packages = column();
        packages.setPadding(0, dp(7), 0, dp(8));
        scroll.addView(packages);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        addPackageCard(packages, "Git", "Version control tools", "git");
        addPackageCard(packages, "Python tools", "Python 3 and pip", "python3-pip");
        addPackageCard(packages, "HTop", "Interactive process monitor", "htop");
        addPackageCard(packages, "Nano", "Terminal text editor", "nano");
        addPackageCard(packages, "Curl", "Transfer data from URLs", "curl");
        addPackageCard(packages, "VLC", "Media player and codecs", "vlc");
        addPackageCard(packages, "GIMP", "Image editing application", "gimp");
        addPackageCard(packages, "Neofetch", "System information in terminal", "neofetch");

        storeSearch.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim().toLowerCase(Locale.ROOT);
                for (int i = 0; i < packages.getChildCount(); i++) {
                    View card = packages.getChildAt(i);
                    String value = card.getContentDescription() == null ? "" :
                            card.getContentDescription().toString().toLowerCase(Locale.ROOT);
                    card.setVisibility(value.contains(query) ? View.VISIBLE : View.GONE);
                }
            }
            public void afterTextChanged(Editable s) {}
        });
        setPage(page);
    }

    private void addPackageCard(LinearLayout list, String name, String description, String packageName) {
        LinearLayout card = column();
        card.setPadding(dp(13), dp(10), dp(13), dp(10));
        card.setBackground(shape(CARD, 15));
        card.setContentDescription((name + " " + description + " " + packageName).toLowerCase(Locale.ROOT));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, -2);
        cardLp.setMargins(0, dp(5), 0, dp(5));
        list.addView(card, cardLp);
        TextView title = text(name, 16, FG);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        card.addView(title);
        TextView details = text(description + " · " + packageName, 12, MUTED);
        details.setPadding(0, dp(2), 0, dp(5));
        card.addView(details);
        LinearLayout actionRow = row();
        TextView status = text("Ubuntu repository package", 11, MUTED);
        actionRow.addView(status, new LinearLayout.LayoutParams(0, dp(38), 1));
        Button install = new Button(this);
        install.setText("Install");
        install.setAllCaps(false);
        install.setOnClickListener(v -> installPackage(packageName, install, status));
        actionRow.addView(install, new LinearLayout.LayoutParams(dp(104), dp(40)));
        card.addView(actionRow);
    }

    private void installPackage(String packageName, Button button, TextView status) {
        if (!packageName.matches("[a-z0-9.+-]+")) {
            status.setText("Invalid package name");
            return;
        }
        button.setEnabled(false);
        button.setText("Wait…");
        status.setText("Preparing Ubuntu package manager");
        storeWorker.execute(() -> {
            StringBuilder log = new StringBuilder();
            int code = -1;
            String failure = null;
            try {
                code = runUbuntuCommand("apt-get update -qq && apt-get install -y --no-install-recommends " + packageName, log);
            } catch (Exception e) {
                failure = e.getMessage();
            }
            final int result = code;
            final String error = failure;
            final String tail = log.length() > 450 ? log.substring(log.length() - 450) : log.toString();
            runOnUiThread(() -> {
                button.setEnabled(true);
                button.setText(result == 0 ? "Installed" : "Retry");
                if (result == 0) status.setText("Installed successfully");
                else status.setText("Install failed. " + (error != null ? error :
                        (tail.isEmpty() ? "Check internet and storage." : tail.replace('\n', ' '))));
            });
        });
    }

    private int runUbuntuCommand(String command, StringBuilder output) throws Exception {
        installRootfs();
        configureDns();
        File runtimeTmp = new File(getCacheDir(), "proot-tmp");
        if (!runtimeTmp.exists() && !runtimeTmp.mkdirs())
            throw new IllegalStateException("Cannot create PRoot temp directory");
        File libs = new File(getApplicationInfo().nativeLibraryDir);
        File proot = new File(libs, "libproot.so");
        File loader = new File(libs, "libproot-loader.so");
        if (!proot.canExecute() || !loader.isFile())
            throw new IllegalStateException("Bundled PRoot runtime missing");
        List<String> args = new ArrayList<>();
        args.add(proot.getAbsolutePath());
        java.util.Collections.addAll(args, "-r", rootfs.getAbsolutePath(), "-w", "/root", "-0",
                "-b", "/dev", "-b", "/proc", "-b", "/sys",
                "-b", runtimeTmp.getAbsolutePath() + ":/tmp");
        if (hasPhoneStorageAccess() && new File("/storage/emulated/0").isDirectory()) {
            new File(rootfs, "mnt/phone").mkdirs();
            args.add("-b");
            args.add("/storage/emulated/0:/mnt/phone");
        }
        java.util.Collections.addAll(args, "/usr/bin/env", "-i", "HOME=/root",
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                "TERM=xterm-256color", "LANG=C.UTF-8", "TMPDIR=/tmp",
                "DEBIAN_FRONTEND=noninteractive", "/bin/bash", "--noprofile", "--norc", "-c", command);
        ProcessBuilder pb = new ProcessBuilder(args).redirectErrorStream(true);
        pb.environment().put("PROOT_LOADER", loader.getAbsolutePath());
        pb.environment().put("PROOT_TMP_DIR", runtimeTmp.getAbsolutePath());
        pb.environment().put("TMPDIR", runtimeTmp.getAbsolutePath());
        pb.environment().put("LD_LIBRARY_PATH", libs.getAbsolutePath());
        pb.environment().put("PROOT_NO_SECCOMP", "1");
        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String outputLine;
            while ((outputLine = reader.readLine()) != null) {
                output.append(outputLine).append((char) 10);
                if (output.length() > 2200) output.delete(0, output.length() - 1600);
            }
        }
        return process.waitFor();
    }

    private void openDesktop() {
        hideQuickPanel();
        try {
            startActivity(new Intent(this, DesktopActivity.class));
        } catch (Exception e) {
            toast("Could not open Ubuntu desktop: " + e.getMessage());
        }
    }

    private void openPhoneStorage() {
        hideQuickPanel();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Allow phone storage")
                    .setMessage("To browse shared phone storage inside Ubuntu, allow file access here. Ubuntu exposes it at /mnt/phone. You can continue without granting access.")
                    .setPositiveButton("Open permission settings", (dialog, which) -> {
                        try {
                            Intent settings = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    android.net.Uri.parse("package:" + getPackageName()));
                            startActivity(settings);
                        } catch (Exception first) {
                            try { startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
                            catch (Exception second) { toast("Storage permission settings unavailable"); }
                        }
                    })
                    .setNegativeButton("Not now", (dialog, which) -> dialog.dismiss())
                    .show();
            return;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Phone storage is ready")
                .setMessage("Inside Ubuntu, open Files and browse to /mnt/phone. Downloads, Documents and Pictures will be available there.")
                .setPositiveButton("Open Ubuntu desktop", (dialog, which) -> openDesktop())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void openFiles() {
        openPhoneStorage();
    }

    private LinearLayout buildTerminal() {
        LinearLayout page = column(); page.setBackgroundColor(BG_DARK); page.setPadding(dp(11), dp(8), dp(11), dp(8));
        LinearLayout head = row();
        TextView back = pill("‹ Home"); back.setOnClickListener(v -> showHome()); head.addView(back);
        head.addView(text("  Terminal", 19, FG), new LinearLayout.LayoutParams(0, dp(43), 1));
        head.addView(pill("Ubuntu")); page.addView(head);
        terminalState = text("Ubuntu 22.04 · ARM64 · PRoot", 12, MUTED);
        terminalState.setPadding(dp(4), dp(3), 0, dp(5)); page.addView(terminalState);
        LinearLayout controls = row();
        terminalStart = new Button(this); terminalStart.setText("Start Ubuntu"); terminalStart.setAllCaps(false);
        controls.addView(terminalStart, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button stop = new Button(this); stop.setText("Stop"); stop.setAllCaps(false);
        controls.addView(stop, new LinearLayout.LayoutParams(0, dp(44), 1)); page.addView(controls);
        ScrollView sc = new ScrollView(this); sc.setBackground(shape(Color.rgb(7, 6, 10), 10));
        terminalOutput = text("Ubuntu Lite terminal\nTap Start Ubuntu, then run: whoami\n", 13, FG);
        terminalOutput.setTypeface(Typeface.MONOSPACE); terminalOutput.setGravity(Gravity.TOP);
        terminalOutput.setTextIsSelectable(true); terminalOutput.setPadding(dp(9), dp(9), dp(9), dp(9));
        sc.addView(terminalOutput); page.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout command = row(); command.setPadding(0, dp(6), 0, 0);
        commandInput = new EditText(this); commandInput.setSingleLine(true); commandInput.setTextColor(FG);
        commandInput.setHintTextColor(MUTED); commandInput.setHint("Ubuntu command…");
        commandInput.setTypeface(Typeface.MONOSPACE);
        commandInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        commandInput.setImeOptions(EditorInfo.IME_ACTION_SEND); commandInput.setBackground(shape(CARD, 10));
        commandInput.setPadding(dp(10), dp(4), dp(10), dp(4));
        command.addView(commandInput, new LinearLayout.LayoutParams(0, dp(48), 1));
        runButton = new Button(this); runButton.setText("Run"); runButton.setAllCaps(false);
        command.addView(runButton, new LinearLayout.LayoutParams(dp(72), dp(48))); page.addView(command);
        TextView note = text("Linux packages need an active internet connection.", 10, MUTED);
        note.setPadding(0, dp(5), 0, 0); page.addView(note);
        terminalStart.setOnClickListener(v -> startUbuntu()); stop.setOnClickListener(v -> stopUbuntu());
        runButton.setOnClickListener(v -> sendCommand());
        commandInput.setOnEditorActionListener((v, id, event) -> {
            if (id == EditorInfo.IME_ACTION_SEND || id == EditorInfo.IME_ACTION_DONE) { sendCommand(); return true; }
            return false;
        });
        return page;
    }

    private void setPage(View view) {
        if (view.getParent() != null) ((android.view.ViewGroup)view.getParent()).removeView(view);
        root.removeAllViews(); root.addView(view, match());
    }
    private void showHome() { hideQuickPanel(); setPage(homePage); }
    private void showDrawer() { hideQuickPanel(); setPage(drawerPage); }
    private void openTerminal() { hideQuickPanel(); setPage(terminalPage); }

    private void showQuickPanel() {
        if (quickShowing) return;
        quickShowing = true;
        quickPanel = column(); quickPanel.setPadding(dp(18), dp(18), dp(18), dp(20));
        quickPanel.setBackground(shape(Color.rgb(37, 28, 46), 27));
        TextView close = text("Quick settings     ⌄", 20, FG); close.setPadding(0, 0, 0, dp(9));
        quickPanel.addView(close);
        quickPanel.addView(text(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(new Date()), 13, MUTED));
        LinearLayout toggles = row(); toggles.setPadding(0, dp(13), 0, dp(7));
        addQuickButton(toggles, "⌁", "Wi-Fi", () -> openSettingsPage(Settings.ACTION_WIFI_SETTINGS));
        addQuickButton(toggles, "ᛒ", "Bluetooth", () -> openSettingsPage(Settings.ACTION_BLUETOOTH_SETTINGS));
        addQuickButton(toggles, "◉", "Display", () -> openSettingsPage(Settings.ACTION_DISPLAY_SETTINGS));
        addQuickButton(toggles, "⚙", "Settings", () -> launchSettings());
        quickPanel.addView(toggles);
        quickPanel.addView(text("Brightness", 13, FG));
        SeekBar seek = new SeekBar(this); seek.setMax(100); seek.setProgress(80); quickPanel.addView(seek);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (fromUser) { WindowManager.LayoutParams lp = getWindow().getAttributes(); lp.screenBrightness = Math.max(.05f, progress / 100f); getWindow().setAttributes(lp); }
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        Button done = new Button(this); done.setText("Done"); done.setAllCaps(false);
        done.setOnClickListener(v -> hideQuickPanel()); quickPanel.addView(done);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        p.setMargins(dp(8), dp(8), dp(8), 0); root.addView(quickPanel, p);
        close.setOnClickListener(v -> hideQuickPanel());
    }
    private void addQuickButton(LinearLayout parent, String icon, String label, Runnable action) {
        LinearLayout tile = column(); tile.setGravity(Gravity.CENTER); tile.setPadding(dp(3), dp(8), dp(3), dp(8));
        tile.setBackground(shape(CARD_LIGHT, 15));
        TextView glyph = text(icon, 20, FG); glyph.setGravity(Gravity.CENTER); tile.addView(glyph);
        TextView name = text(label, 10, FG); name.setGravity(Gravity.CENTER); tile.addView(name);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(65), 1);
        lp.setMargins(dp(3), 0, dp(3), 0); parent.addView(tile, lp);
        tile.setOnClickListener(v -> action.run());
    }
    private void hideQuickPanel() {
        if (!quickShowing) return;
        quickShowing = false;
        if (quickPanel != null && quickPanel.getParent() != null) ((android.view.ViewGroup)quickPanel.getParent()).removeView(quickPanel);
    }

    private void startUbuntu() {
        if (busy) return;
        if (shell != null && shell.isAlive()) { toast("Ubuntu is already running"); return; }
        busy = true; terminalStart.setEnabled(false); terminalState.setText("Preparing Ubuntu…");
        worker.execute(() -> {
            try {
                installRootfs(); configureDns();
                File runtimeTmp = new File(getCacheDir(), "proot-tmp");
                if (!runtimeTmp.exists() && !runtimeTmp.mkdirs()) throw new IllegalStateException("Cannot create PRoot temp directory");
                runtimeTmp.setWritable(true, true);
                File ubuntuTmp = new File(rootfs, "tmp");
                if (!ubuntuTmp.exists() && !ubuntuTmp.mkdirs()) throw new IllegalStateException("Cannot create Ubuntu /tmp");
                ubuntuTmp.setReadable(true, false); ubuntuTmp.setWritable(true, false); ubuntuTmp.setExecutable(true, false);
                File ubuntuVarTmp = new File(rootfs, "var/tmp");
                if (!ubuntuVarTmp.exists() && !ubuntuVarTmp.mkdirs()) throw new IllegalStateException("Cannot create Ubuntu /var/tmp");
                ubuntuVarTmp.setReadable(true, false); ubuntuVarTmp.setWritable(true, false); ubuntuVarTmp.setExecutable(true, false);
                File libs = new File(getApplicationInfo().nativeLibraryDir);
                File proot = new File(libs, "libproot.so");
                File loader = new File(libs, "libproot-loader.so");
                if (!proot.canExecute() || !loader.isFile()) throw new IllegalStateException("Bundled PRoot runtime missing");
                List<String> cmd = new ArrayList<>(); cmd.add(proot.getAbsolutePath());
                java.util.Collections.addAll(cmd, "-r", rootfs.getAbsolutePath(), "-w", "/root", "-0",
                        "-b", "/dev", "-b", "/proc", "-b", "/sys",
                        "-b", runtimeTmp.getAbsolutePath() + ":/tmp");
                if (hasPhoneStorageAccess() && new File("/storage/emulated/0").isDirectory()) {
                    new File(rootfs, "mnt/phone").mkdirs();
                    java.util.Collections.addAll(cmd, "-b", "/storage/emulated/0:/mnt/phone");
                }
                java.util.Collections.addAll(cmd, "/usr/bin/env", "-i", "HOME=/root",
                        "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                        "TMPDIR=/tmp", "TMP=/tmp", "TEMP=/tmp", "TERM=xterm-256color",
                        "LANG=C.UTF-8", "PS1=ubuntu-lite# ", "DEBIAN_FRONTEND=noninteractive",
                        "/usr/bin/script", "-qefc", "/bin/bash --noprofile --norc -i", "/dev/null");
                ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
                pb.environment().put("PROOT_LOADER", loader.getAbsolutePath());
                pb.environment().put("PROOT_TMP_DIR", runtimeTmp.getAbsolutePath());
                pb.environment().put("TMPDIR", runtimeTmp.getAbsolutePath());
                pb.environment().put("LD_LIBRARY_PATH", libs.getAbsolutePath());
                shell = pb.start(); writer = new BufferedWriter(new OutputStreamWriter(shell.getOutputStream()));
                BufferedReader reader = new BufferedReader(new InputStreamReader(shell.getInputStream()));
                runOnUiThread(() -> { busy = false; terminalStart.setEnabled(true); terminalState.setText("Ubuntu shell running · DNS configured"); });
                line("Ubuntu shell started. Try: whoami, uname -a, apt update");
                String s; while ((s = reader.readLine()) != null) line(s);
                line("Ubuntu shell ended.");
            } catch (Exception e) {
                line("START ERROR: " + e.getMessage());
                runOnUiThread(() -> terminalState.setText("Could not start Ubuntu"));
            } finally { busy = false; runOnUiThread(() -> terminalStart.setEnabled(true)); }
        });
    }

    private void configureDns() throws Exception {
        List<InetAddress> servers = new ArrayList<>();
        try {
            ConnectivityManager cm = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
            Network network = cm == null ? null : cm.getActiveNetwork();
            LinkProperties props = network == null ? null : cm.getLinkProperties(network);
            if (props != null) servers.addAll(props.getDnsServers());
        } catch (Exception ignored) {}
        if (servers.isEmpty()) { servers.add(InetAddress.getByName("1.1.1.1")); servers.add(InetAddress.getByName("8.8.8.8")); }
        File etc = new File(rootfs, "etc");
        if (!etc.exists() && !etc.mkdirs()) throw new IllegalStateException("Cannot create Ubuntu /etc");
        File resolv = new File(etc, "resolv.conf");
        if (Files.isSymbolicLink(resolv.toPath()) || resolv.exists()) Files.deleteIfExists(resolv.toPath());
        try (FileWriter fw = new FileWriter(resolv, false)) {
            fw.write("# DNS provided by Android active network\n");
            for (InetAddress server : servers) fw.write("nameserver " + server.getHostAddress() + "\n");
            fw.write("options timeout:2 attempts:3\n");
        }
        line("DNS configured from Android network (" + servers.size() + " server(s)).");
    }

    private void installRootfs() throws Exception {
        synchronized (rootfsLock) {
        if (rootfsReady()) return;
        delete(rootfs); if (!rootfs.mkdirs()) throw new IllegalStateException("Cannot create Ubuntu directory");
        Path base = rootfs.toPath().toAbsolutePath().normalize();
        List<TarArchiveEntry> links = new ArrayList<>(); List<Path> linkPaths = new ArrayList<>();
        try (InputStream raw = getAssets().open("ubuntu-rootfs.dat"); GZIPInputStream gz = new GZIPInputStream(raw);
             TarArchiveInputStream tar = new TarArchiveInputStream(gz)) {
            TarArchiveEntry e; byte[] buf = new byte[65536];
            while ((e = tar.getNextTarEntry()) != null) {
                String name = e.getName().replaceFirst("^\\\\./", "").replaceAll("/+$", "");
                if (name.isEmpty()) continue;
                Path target = base.resolve(name).normalize(); if (!target.startsWith(base)) continue;
                if (e.isSymbolicLink() || e.isLink()) { links.add(e); linkPaths.add(target); continue; }
                if (e.isDirectory()) { Files.createDirectories(target); target.toFile().setExecutable(true, false); }
                else if (e.isFile()) {
                    Files.createDirectories(target.getParent());
                    try (FileOutputStream out = new FileOutputStream(target.toFile())) {
                        int n; while ((n = tar.read(buf)) != -1) out.write(buf, 0, n);
                    }
                    target.toFile().setReadable(true, false);
                    if ((e.getMode() & 0111) != 0) target.toFile().setExecutable(true, false);
                }
            }
        }
        for (int i = 0; i < links.size(); i++) {
            TarArchiveEntry e = links.get(i); Path target = linkPaths.get(i);
            try {
                Files.createDirectories(target.getParent());
                if (e.isSymbolicLink()) Files.createSymbolicLink(target, java.nio.file.Paths.get(e.getLinkName()));
                else {
                    Path src = base.resolve(e.getLinkName()).normalize();
                    if (src.startsWith(base) && Files.exists(src)) Files.createLink(target, src);
                }
            } catch (Exception ignored) {}
        }
        new File(rootfs, ".installed").createNewFile();
        line("Ubuntu filesystem installed.");
        }
    }

    private boolean rootfsReady() {
        return new File(rootfs, "bin/bash").isFile()
                && (new File(rootfs, ".installed").isFile()
                    || new File(rootfs, "root/.ubuntu-preinstalled-desktop-v1").isFile());
    }

    private void sendCommand() {
        String command = commandInput.getText().toString();
        if (command.trim().isEmpty()) return;
        commandInput.setText("");
        if (writer == null || shell == null || !shell.isAlive()) { toast("Start Ubuntu first"); return; }
        try { writer.write(command); writer.newLine(); writer.flush(); }
        catch (Exception e) { line("Input error: " + e.getMessage()); }
    }
    private void stopUbuntu() {
        try { if (writer != null) { writer.write("exit"); writer.newLine(); writer.flush(); } } catch (Exception ignored) {}
        if (shell != null) shell.destroy();
        shell = null; writer = null;
        if (terminalState != null) terminalState.setText("Ubuntu stopped");
    }
    private void line(String s) {
        String plain = s == null ? "" : s.replaceAll(
                java.util.regex.Pattern.quote(String.valueOf((char) 27)) + "\\[[0-?]*[ -/]*[@-~]", "");
        runOnUiThread(() -> {
            if (terminalOutput == null) return;
            terminalOutput.append(plain + "\n");
            if (terminalOutput.length() > 80000) {
                int trim = terminalOutput.length() - 60000;
                terminalOutput.getEditableText().delete(0, trim);
            }
            if (terminalOutput.getParent() instanceof ScrollView)
                ((ScrollView)terminalOutput.getParent()).post(() -> ((ScrollView)terminalOutput.getParent()).fullScroll(View.FOCUS_DOWN));
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
    private void launchUrl(String url) { try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))); } catch (Exception e) { toast("No browser available"); } }
    private void launchSettings() { openSettingsPage(Settings.ACTION_SETTINGS); }
    private void openSettingsPage(String action) {
        try { startActivity(new Intent(action)); } catch (Exception e) { toast("This settings page is not available"); }
    }
    private void openFiles() {
        try { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("*/*"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivity(i); }
        catch (Exception e) { toast("File picker not available"); }
    }
    private void openGallery() {
        try { Intent i = new Intent(Intent.ACTION_GET_CONTENT); i.setType("image/*"); startActivity(i); }
        catch (Exception e) { toast("Gallery not available"); }
    }
    private void openCalculator() {
        Intent i = new Intent(Intent.ACTION_MAIN); i.addCategory("android.intent.category.APP_CALCULATOR");
        try { startActivity(i); } catch (Exception e) { toast("No calculator app found"); }
    }
    private void openClock() {
        try { startActivity(new Intent("android.intent.action.SHOW_ALARMS")); }
        catch (Exception e) { toast("Clock app not available"); }
    }
    private void showAbout() {
        new android.app.AlertDialog.Builder(this).setTitle("Ubuntu Touch-inspired shell")
            .setMessage("A mobile-style Linux-inspired interface running on Android 11 or newer. The Ubuntu terminal runs in a PRoot environment; this app does not replace Android or install a device OS.")
            .setPositiveButton("OK", (d,w) -> d.dismiss()).show();
    }
    private void toast(String s) { runOnUiThread(() -> Toast.makeText(this, s, Toast.LENGTH_SHORT).show()); }
    @Override protected void onDestroy() { stopUbuntu(); worker.shutdownNow(); super.onDestroy(); }
}
