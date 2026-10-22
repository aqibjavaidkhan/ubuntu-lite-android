package com.ubuntulite.launcher;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

public final class MainActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private TextView output, status;
    private EditText input;
    private Button start, run;
    private Process shell;
    private BufferedWriter writer;
    private File rootfs;
    private volatile boolean busy;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(13,17,23));
        getWindow().setNavigationBarColor(Color.rgb(13,17,23));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        rootfs = new File(getFilesDir(), "ubuntu-rootfs");
        buildUi();
        line("Ubuntu Lite — Ubuntu 22.04 ARM64");
        line("Standalone app • no Android root • no Termux dependency");
        line("First start extracts the Ubuntu filesystem included in this APK.");
        status.setText(new File(rootfs, ".installed").exists() ? "Ubuntu installed" : "Ready to install Ubuntu");
    }

    private void buildUi() {
        int bg = Color.rgb(13,17,23), panel = Color.rgb(22,27,34), fg = Color.rgb(230,237,243), green = Color.rgb(126,231,135);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(14,14,14,10);
        page.setBackgroundColor(bg);
        TextView title = new TextView(this);
        title.setText("Ubuntu Lite");
        title.setTextColor(fg); title.setTextSize(25); title.setTypeface(Typeface.DEFAULT_BOLD);
        page.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText("Ubuntu Base 22.04 LTS · ARM64 · PRoot");
        subtitle.setTextColor(Color.LTGRAY); subtitle.setTextSize(12);
        page.addView(subtitle);
        status = new TextView(this);
        status.setTextColor(green); status.setPadding(10,10,10,10);
        page.addView(status);
        LinearLayout actions = new LinearLayout(this);
        start = new Button(this); start.setText("Install & Start Ubuntu"); start.setAllCaps(false);
        actions.addView(start, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button stop = new Button(this); stop.setText("Stop"); stop.setAllCaps(false);
        actions.addView(stop, new LinearLayout.LayoutParams(0, dp(48), 1));
        page.addView(actions);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(1,4,9));
        output = new TextView(this); output.setTextColor(fg); output.setTextSize(13);
        output.setTypeface(Typeface.MONOSPACE); output.setTextIsSelectable(true);
        output.setPadding(10,10,10,10);
        scroll.addView(output);
        page.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout entry = new LinearLayout(this);
        input = new EditText(this); input.setSingleLine(true); input.setTextColor(fg);
        input.setHintTextColor(Color.LTGRAY); input.setHint("Ubuntu command…");
        input.setTypeface(Typeface.MONOSPACE); input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        entry.addView(input, new LinearLayout.LayoutParams(0,dp(50),1));
        run = new Button(this); run.setText("Run"); run.setAllCaps(false);
        entry.addView(run, new LinearLayout.LayoutParams(dp(76),dp(50)));
        page.addView(entry);
        TextView note = new TextView(this);
        note.setText("Use apt update to refresh package lists. A network connection is required for new packages.");
        note.setTextColor(Color.LTGRAY); note.setTextSize(11);
        page.addView(note);
        setContentView(page);
        start.setOnClickListener(v -> startUbuntu());
        stop.setOnClickListener(v -> stopUbuntu());
        run.setOnClickListener(v -> send());
        input.setOnEditorActionListener((v,id,event) -> { if (id == EditorInfo.IME_ACTION_SEND || id == EditorInfo.IME_ACTION_DONE) { send(); return true; } return false; });
    }

    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density); }
    private void line(String s) { runOnUiThread(() -> { output.append(s + "\n"); }); }
    private void startUbuntu() {
        if (busy) return;
        if (shell != null && shell.isAlive()) { toast("Ubuntu is already running"); return; }
        busy = true; start.setEnabled(false); status.setText("Preparing Ubuntu…");
        worker.execute(() -> {
            try {
                installRootfs();
                File libs = new File(getApplicationInfo().nativeLibraryDir);
                File proot = new File(libs, "libproot.so");
                File loader = new File(libs, "libproot-loader.so");
                if (!proot.canExecute() || !loader.isFile()) throw new IllegalStateException("Bundled PRoot runtime is missing from this APK");
                List<String> cmd = new ArrayList<>();
                cmd.add(proot.getAbsolutePath());
                String[] args = {"-r",rootfs.getAbsolutePath(),"-w","/root","-0","-b","/dev","-b","/proc","-b","/sys","/usr/bin/env","-i","HOME=/root","PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin","TERM=xterm-256color","/bin/bash","--noprofile","--norc","-i"};
                java.util.Collections.addAll(cmd,args);
                ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
                pb.environment().put("PROOT_LOADER",loader.getAbsolutePath());
                pb.environment().put("LD_LIBRARY_PATH",libs.getAbsolutePath());
                shell = pb.start();
                writer = new BufferedWriter(new OutputStreamWriter(shell.getOutputStream()));
                BufferedReader reader = new BufferedReader(new InputStreamReader(shell.getInputStream()));
                runOnUiThread(() -> { busy=false; start.setEnabled(true); status.setText("Ubuntu shell running"); run.setEnabled(true); });
                line("Ubuntu started. Try: whoami, uname -a, apt update");
                String s;
                while ((s=reader.readLine()) != null) line(s);
                line("Ubuntu shell ended.");
            } catch (Exception e) {
                line("START ERROR: " + e.getMessage());
                runOnUiThread(() -> status.setText("Could not start Ubuntu"));
            } finally {
                busy=false;
                runOnUiThread(() -> start.setEnabled(true));
            }
        });
    }

    private void installRootfs() throws Exception {
        if (new File(rootfs, ".installed").isFile() && new File(rootfs, "bin/bash").exists()) return;
        delete(rootfs);
        if (!rootfs.mkdirs()) throw new IllegalStateException("Cannot create Ubuntu directory");
        Path base = rootfs.toPath().toAbsolutePath().normalize();
        List<TarArchiveEntry> links = new ArrayList<>();
        List<Path> linkPaths = new ArrayList<>();
        try (InputStream raw=getAssets().open("ubuntu-rootfs.tar.gz");
             GZIPInputStream gz=new GZIPInputStream(raw);
             TarArchiveInputStream tar=new TarArchiveInputStream(gz)) {
            TarArchiveEntry e;
            byte[] buf=new byte[65536];
            while ((e=tar.getNextTarEntry()) != null) {
                String name=e.getName().replaceFirst("^\\./","").replaceAll("/+$","");
                if (name.isEmpty()) continue;
                Path target=base.resolve(name).normalize();
                if (!target.startsWith(base)) continue;
                if (e.isSymbolicLink() || e.isLink()) { links.add(e); linkPaths.add(target); continue; }
                if (e.isDirectory()) { Files.createDirectories(target); target.toFile().setExecutable(true,false); }
                else if (e.isFile()) {
                    Files.createDirectories(target.getParent());
                    try (FileOutputStream out=new FileOutputStream(target.toFile())) {
                        int n; while ((n=tar.read(buf))!=-1) out.write(buf,0,n);
                    }
                    target.toFile().setReadable(true,false);
                    if ((e.getMode() & 0111) != 0) target.toFile().setExecutable(true,false);
                }
            }
        }
        for (int i=0;i<links.size();i++) {
            TarArchiveEntry e=links.get(i); Path target=linkPaths.get(i);
            try {
                Files.createDirectories(target.getParent());
                if (e.isSymbolicLink()) Files.createSymbolicLink(target, java.nio.file.Paths.get(e.getLinkName()));
                else {
                    Path src=base.resolve(e.getLinkName()).normalize();
                    if (src.startsWith(base) && Files.exists(src)) Files.createLink(target,src);
                }
            } catch (Exception ignored) { }
        }
        new File(rootfs, ".installed").createNewFile();
        line("Ubuntu filesystem installed.");
    }

    private void send() {
        String command=input.getText().toString();
        if (command.trim().isEmpty()) return;
        input.setText("");
        if (writer==null || shell==null || !shell.isAlive()) { toast("Start Ubuntu first"); return; }
        try { writer.write(command); writer.newLine(); writer.flush(); }
        catch (Exception e) { line("Input error: " + e.getMessage()); }
    }

    private void stopUbuntu() {
        try { if (writer!=null) { writer.write("exit"); writer.newLine(); writer.flush(); } } catch(Exception ignored) {}
        if (shell!=null) shell.destroy();
        shell=null; writer=null; status.setText("Ubuntu stopped");
    }

    private void delete(File f) {
        if (!f.exists()) return;
        if (f.isDirectory() && !Files.isSymbolicLink(f.toPath())) {
            File[] children=f.listFiles();
            if (children!=null) for(File child:children) delete(child);
        }
        try { Files.deleteIfExists(f.toPath()); } catch(Exception ignored) {}
    }
    private void toast(String s) { runOnUiThread(() -> Toast.makeText(this,s,Toast.LENGTH_SHORT).show()); }
    @Override protected void onDestroy() { stopUbuntu(); worker.shutdownNow(); super.onDestroy(); }
}
