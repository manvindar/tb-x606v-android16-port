package org.tablet.tuner;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

/** Home: a dial for the performance preset plus a short list of areas. Each area is a page with plain-language rows. */
public class MainActivity extends Activity {
    private static final String HOME = "home";
    private Map<String, String> st;
    private LinearLayout col;
    private boolean night;
    private float dp;
    private String page = HOME;
    private TextView status;
    private TextView liveCpu, liveGpu, liveMem, liveHeat, compileView;
    private final android.os.Handler ui = new android.os.Handler();
    private final Runnable tick = new Runnable() { @Override public void run() { refreshLive(); refreshCompile(); refreshApps(); ui.postDelayed(this, 1000); } };
    private final Runnable reload = () -> { st = Cfg.load(this); setStatus("Applied"); build(); };

    // preset id, name, one-line description
    private static final String[][] PRESETS = {
        {"battery", "Battery", "Low power mode and capped clocks. Quiet and cool, slower."},
        {"balanced", "Balanced", "Everyday default: a moderate clock floor, GPU at full speed."},
        {"smooth", "Smooth", "Stronger app boosts and more free memory. The best everyday feel."},
        {"performance", "Performance", "Maximum app boosts and a bigger write buffer. Lowest scroll jank in tests."},
        {"extreme", "Extreme", "Top clocks pinned, thermal limit off. Hot, short on battery, no UI gain over Performance."}};

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        dp = getResources().getDisplayMetrics().density;
        night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        setTitle("ROM Tuner");
        if (getActionBar() != null) getActionBar().hide();
        String p = getIntent().getStringExtra("page");
        if (p != null) page = p;
    }

    @Override protected void onResume() { super.onResume(); st = Cfg.load(this); build(); ui.post(tick); }

    @Override protected void onPause() { super.onPause(); ui.removeCallbacks(tick); }

    @Override public void onBackPressed() { if (!HOME.equals(page)) go(parentPage()); else super.onBackPressed(); }

    private String parentPage() { return "apps".equals(page) ? "system" : HOME; }

    // ---------- state ----------
    private String get(String k, String def) { String v = st.get(k); return v == null || v.isEmpty() ? def : v; }

    private void set(String k, String v) {
        st.put(k, v);
        Cfg.request(this, k, v);
        setStatus("Applying…");
        col.removeCallbacks(reload);
        col.postDelayed(reload, 2500);
    }

    private void setStatus(String s) { if (status != null) status.setText(s); }

    private void go(String p) { page = p; build(); ((ScrollView) col.getParent().getParent()).scrollTo(0, 0); }

    // ---------- look ----------
    private int px(float v) { return Math.round(v * dp); }
    private int sys(int night300, int day600) { return getColor(night ? night300 : day600); }
    private int accent() { return sys(android.R.color.system_accent1_300, android.R.color.system_accent1_600); }
    private int onAccent() { return sys(android.R.color.system_accent1_900, android.R.color.system_accent1_0); }
    private int surface() { return sys(android.R.color.system_neutral1_900, android.R.color.system_neutral1_10); }
    private int card() { return sys(android.R.color.system_neutral2_800, android.R.color.system_neutral2_100); }
    private int ink() { return sys(android.R.color.system_neutral1_50, android.R.color.system_neutral1_900); }
    private int dim() { return sys(android.R.color.system_neutral2_300, android.R.color.system_neutral2_600); }
    private int line() { return sys(android.R.color.system_neutral2_700, android.R.color.system_neutral2_200); }

    private TextView text(String s, float sp, boolean medium, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        t.setTextColor(color);
        return t;
    }

    private LinearLayout group(String title) {
        if (title != null) {
            TextView t = text(title, 14, true, accent());
            t.setPadding(px(10), px(22), 0, px(8));
            col.addView(t);
        } else {
            View gap = new View(this); col.addView(gap, new LinearLayout.LayoutParams(-1, px(14)));
        }
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(card()); g.setCornerRadius(px(26));
        c.setBackground(g);
        c.setPadding(0, px(4), 0, px(4));
        col.addView(c, new LinearLayout.LayoutParams(-1, -2));
        return c;
    }

    private void divider(LinearLayout c) {
        View v = new View(this); v.setBackgroundColor(line());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 1);
        lp.setMargins(px(20), 0, px(20), 0);
        c.addView(v, lp);
    }

    private LinearLayout row(LinearLayout c, String title, String sub, View control) {
        if (c.getChildCount() > 0) divider(c);
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setMinimumHeight(px(60));
        r.setPadding(px(20), px(12), px(16), px(12));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.addView(text(title, 16, false, ink()));
        if (sub != null) { TextView s = text(sub, 13, false, dim()); s.setPadding(0, px(2), px(12), 0); texts.addView(s); }
        r.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        if (control != null) r.addView(control);
        c.addView(r, new LinearLayout.LayoutParams(-1, -2));
        return r;
    }

    private void rowSwitch(LinearLayout c, String title, String sub, final String key, String def) {
        Switch s = new Switch(this);
        s.setChecked("1".equals(get(key, def)));
        s.setOnCheckedChangeListener((v, on) -> set(key, on ? "1" : "0"));
        row(c, title, sub, s).setOnClickListener(v -> s.toggle());
    }

    /** A row that shows the current value; tapping opens a list. */
    private void rowChoice(LinearLayout c, final String title, String sub, final String key, final String[] labels, final String[] vals, String def) {
        String cur = get(key, def); int pos = -1;
        for (int i = 0; i < vals.length; i++) if (vals[i].equals(cur)) pos = i;
        TextView v = text((pos >= 0 ? labels[pos] : cur) + "  ›", 15, true, accent());
        final int sel = pos;
        row(c, title, sub, v).setOnClickListener(x ->
            new AlertDialog.Builder(this).setTitle(title)
                .setSingleChoiceItems(labels, sel, (d, i) -> { d.dismiss(); if (i != sel) set(key, vals[i]); build(); })
                .setNegativeButton("Cancel", null).show());
    }

    private void rowSlider(LinearLayout c, String title, String sub, final String key, final int max, final int def, final String[] fmt) {
        int cur = def;
        try { cur = Math.round(Float.parseFloat(get(key, String.valueOf(def))) * (fmt == null ? 1 : 1)); } catch (Exception ignored) { }
        final TextView val = text("", 15, true, accent());
        SeekBar sb = new SeekBar(this); sb.setMax(max); sb.setProgress(cur);
        final int cc = cur;
        val.setText(label(fmt, cc));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { val.setText(label(fmt, p)); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { set(key, String.valueOf(s.getProgress())); }
        });
        row(c, title, sub, val);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(px(14), 0, px(14), px(10));
        c.addView(sb, lp);
    }
    private String label(String[] fmt, int p) { return fmt != null && p < fmt.length ? fmt[p] : String.valueOf(p); }

    private void rowGo(LinearLayout c, String title, String sub, final String target) {
        row(c, title, sub, text("›", 22, false, dim())).setOnClickListener(v -> go(target));
    }

    private void rowButton(LinearLayout c, String title, String sub, final Intent i) {
        row(c, title, sub, text("›", 22, false, dim())).setOnClickListener(v -> {
            try { startActivity(i); } catch (Exception e) { Toast.makeText(this, "Not available on this build", Toast.LENGTH_SHORT).show(); }
        });
    }

    private void rowAction(LinearLayout c, String title, String sub, final Runnable run) { row(c, title, sub, null).setOnClickListener(v -> run.run()); }

    private Intent comp(String pkg, String cls) { return new Intent().setComponent(new ComponentName(pkg, cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); }

    private String[] list(String key, String fallback) { String v = get(key, ""); return (v.isEmpty() ? fallback : v).split(","); }

    private String[] gpuValues() {
        String[] o = list("gpu_opps", "680000,500000,400000");
        String[] r = new String[o.length + 1]; r[0] = "0";
        System.arraycopy(o, 0, r, 1, o.length);
        return r;
    }
    private String[] gpuLabels() {
        String[] v = gpuValues(), r = new String[v.length]; r[0] = "Automatic";
        for (int i = 1; i < v.length; i++) r[i] = (Integer.parseInt(v[i]) / 1000) + " MHz";
        return r;
    }
    private static String fontLabel(String f) {
        switch (f) {
            case "lato": return "Lato";
            case "rubik": return "Rubik";
            case "notoserifsource": return "Noto Serif";
            case "googlesansflex": return "Google Sans Flex";
            default: return f.substring(0, 1).toUpperCase(Locale.US) + f.substring(1);
        }
    }

    private static String pick(String v, String[] vals, String[] names, String def) {
        for (int i = 0; i < vals.length; i++) if (vals[i].equals(v)) return names[i];
        return def;
    }

    // ---------- frame ----------
    private void build() {
        liveCpu = liveGpu = liveMem = liveHeat = compileView = null;
        appsBox = null; appsInfo = null;
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(surface());
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setGravity(Gravity.CENTER_HORIZONTAL);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(px(16), px(20), px(16), px(48));
        outer.addView(col, new LinearLayout.LayoutParams(Math.min(px(680), getResources().getDisplayMetrics().widthPixels), -2));
        sv.addView(outer);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(px(6), px(4), px(6), px(4));
        if (!HOME.equals(page)) {
            TextView back = text("‹", 34, false, accent());
            back.setPadding(0, 0, px(14), px(4));
            back.setOnClickListener(v -> go(parentPage()));
            head.addView(back);
        }
        head.addView(text(HOME.equals(page) ? "ROM Tuner" : pageTitle(), HOME.equals(page) ? 30 : 26, true, ink()), new LinearLayout.LayoutParams(0, -2, 1f));
        status = text(st.isEmpty() ? "Waiting for the tuner service…" : "", 13, false, dim());
        status.setMaxWidth(px(260)); status.setGravity(Gravity.END);
        head.addView(status);
        col.addView(head);

        switch (page) {
            case "perf": pagePerf(); break;
            case "memory": pageMemory(); break;
            case "picture": pagePicture(); break;
            case "look": pageLook(); break;
            case "display": pageDisplay(); break;
            case "connect": pageConnect(); break;
            case "system": pageSystem(); break;
            case "apps": pageApps(); break;
            default: pageHome();
        }
        setContentView(sv);
    }

    private String pageTitle() {
        switch (page) {
            case "perf": return "Performance details";
            case "memory": return "Memory and storage";
            case "picture": return "Picture";
            case "look": return "Look";
            case "display": return "Display and touch";
            case "connect": return "Connections";
            case "apps": return "App speed";
            default: return "System";
        }
    }

    // ---------- live readout (read straight from sysfs; the tuner's ROM runs permissive, elsewhere the cells show a dash) ----------
    private static String rd(String path) {
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(path))) { String l = r.readLine(); return l == null ? "" : l.trim(); }
        catch (Exception e) { return ""; }
    }
    private static String grep(String path, String key) {
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(path))) {
            String l; while ((l = r.readLine()) != null) if (l.contains(key)) return l;
        } catch (Exception ignored) { }
        return "";
    }
    private static long num(String s) { java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(s); return m.find() ? Long.parseLong(m.group(1)) : -1; }
    private static String ghz(long khz) { return khz < 0 ? "–" : String.format(Locale.US, "%.2f", khz / 1e6); }

    private void refreshLive() {
        if (liveCpu == null) return;
        String c = "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_cur_freq";
        long a = num(rd(String.format(c, 0))), b = num(rd(String.format(c, 4)));
        liveCpu.setText(ghz(a) + " · " + ghz(b));
        long g = num(grep("/proc/gpufreq/gpufreq_var_dump", "g_cur_opp_freq"));
        liveGpu.setText(g < 0 ? "–" : (g / 1000) + " MHz");
        long ddr = num(grep("/sys/devices/platform/10012000.dvfsrc/helio-dvfsrc/dvfsrc_dump", "DDR"));
        long avail = num(grep("/proc/meminfo", "MemAvailable")), total = num(grep("/proc/meminfo", "MemTotal"));
        liveMem.setText((ddr < 0 ? "–" : (ddr / 1000) + " MHz") + (avail < 0 || total < 0 ? "" : "\n" + String.format(Locale.US, "%.1f of %.1f GB free", avail / 1048576f, total / 1048576f)));
        long t = -1;
        for (int i = 0; i < 12 && t < 0; i++) if ("mtktscpu".equals(rd("/sys/class/thermal/thermal_zone" + i + "/type"))) t = num(rd("/sys/class/thermal/thermal_zone" + i + "/temp"));
        liveHeat.setText(t < 0 ? "–" : String.format(Locale.US, "%.0f °C", t / 1000f));
    }

    private static String compileLabel(String s) {
        switch (s) { case "running": return "Running"; case "done": return "Done"; case "missing": return "Not installed"; default: return "Not run"; }
    }
    /** The compile run ends on its own, so the System page re-reads the status key while it is open. */
    private void refreshCompile() {
        if (compileView != null) compileView.setText(compileLabel(Cfg.load(this).getOrDefault("compile_status", "idle")));
    }
    private void compile(String key) {
        Map<String, String> s = Cfg.load(this);
        if ("running".equals(s.get("compile_status"))) { Toast.makeText(this, "Already running", Toast.LENGTH_SHORT).show(); return; }
        if (!get0(s, "compile_queue").isEmpty() || get0(s, "compile_pkg_status").endsWith(":running")) { Toast.makeText(this, "An app is compiling. Wait for it to finish.", Toast.LENGTH_SHORT).show(); return; }
        Cfg.request(this, key, "1");
        setStatus("Starting…");
    }

    private TextView liveCell(LinearLayout c, String label, float weight) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(px(14), px(12), px(8), px(12));
        cell.addView(text(label, 12.5f, false, dim()));
        TextView v = text("–", 17, true, ink());
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        cell.addView(v);
        c.addView(cell, new LinearLayout.LayoutParams(0, -2, weight));
        return v;
    }

    private void liveCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(card()); g.setCornerRadius(px(26));
        c.setBackground(g);
        liveCpu = liveCell(c, "CPU, GHz (fast · slow cores)", 1.25f);
        liveGpu = liveCell(c, "GPU", 0.8f);
        liveMem = liveCell(c, "Memory clock and free RAM", 1.3f);
        liveHeat = liveCell(c, "Chip temperature", 0.8f);
        col.addView(c, new LinearLayout.LayoutParams(-1, -2));
        refreshLive();
    }

    // ---------- home ----------
    private void pageHome() {
        String cur = get("preset", "balanced");
        int idx = -1;
        String[] names = new String[PRESETS.length];
        for (int i = 0; i < PRESETS.length; i++) { names[i] = PRESETS[i][1]; if (PRESETS[i][0].equals(cur)) idx = i; }
        DialView d = new DialView(this);
        d.setColors(accent(), line(), ink(), dim(), onAccent(), 0xFFFF8A4C);
        d.setItems(names);
        d.setSelected(idx);
        d.setOnPick(i -> { set("preset", PRESETS[i][0]); build(); });
        col.addView(d, new LinearLayout.LayoutParams(-1, -2));
        String ap = get("profile_active", "");
        TextView name = text(idx >= 0 ? PRESETS[idx][1] : ap.isEmpty() ? "Custom" : ap.replace('_', ' '), 34, true, ink());
        name.setGravity(Gravity.CENTER); name.setPadding(0, px(8), 0, px(4));
        col.addView(name, new LinearLayout.LayoutParams(-1, -2));
        TextView desc = text(idx >= 0 ? PRESETS[idx][2] : ap.isEmpty() ? "You changed individual settings. Pick a preset on the dial to start over, or save these as a profile below." : "Your saved profile. Pick a preset on the dial to leave it.", 15, false, dim());
        desc.setGravity(Gravity.CENTER); desc.setPadding(px(24), 0, px(24), px(10));
        col.addView(desc, new LinearLayout.LayoutParams(-1, -2));

        liveCard();
        LinearLayout c = group(null);
        rowGo(c, "Performance details", perfSummary(), "perf");
        profileRows(c);
        c = group(null);
        rowGo(c, "Picture", pictureSummary(), "picture");
        rowGo(c, "Look", lookSummary(), "look");
        rowGo(c, "Display and touch", displaySummary(), "display");
        rowGo(c, "Memory and storage", "Read-ahead " + pick(get("readahead", "512"), new String[]{"128", "256", "512", "1024", "2048"}, new String[]{"128 KB", "256 KB", "512 KB", "1 MB", "2 MB"}, get("readahead", "512") + " KB") + ", swappiness " + get("swappiness", "100"), "memory");
        rowGo(c, "Connections", "Private DNS " + pick(get("dns", "auto"), new String[]{"off", "auto", "google", "cloudflare", "adguard", "quad9"}, new String[]{"off", "automatic", "Google", "Cloudflare", "AdGuard", "Quad9"}, "automatic"), "connect");
        rowGo(c, "System", get("status", "Shortcuts, logging, restart"), "system");
    }

    /** Saved profiles: tap to apply, Delete to remove, and a row that saves the current performance, memory and picture settings. */
    private void profileRows(LinearLayout c) {
        String active = get("profile_active", "");
        for (final String n : get("profiles", "").split(",")) {
            if (n.isEmpty()) continue;
            final String shown = n.replace('_', ' ');
            TextView del = text("Delete", 14, true, accent());
            del.setPadding(px(12), px(8), px(4), px(8));
            del.setOnClickListener(v -> new AlertDialog.Builder(this).setMessage("Delete the profile \"" + shown + "\"?")
                .setNegativeButton("Cancel", null).setPositiveButton("Delete", (d, w) -> set("delete_profile", n)).show());
            row(c, n.equals(active) ? shown + "  ✓" : shown, n.equals(active) ? "In use" : "Tap to apply", del)
                .setOnClickListener(v -> { set("apply_profile", n); });
        }
        row(c, "Save current settings as a profile", "Keeps performance, memory and picture settings under a name", text("Save", 15, true, accent()))
            .setOnClickListener(v -> {
                final EditText name = new EditText(this);
                name.setHint("Profile name"); name.setSingleLine(true); name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(24)});
                name.setPadding(px(24), px(16), px(24), px(8));
                new AlertDialog.Builder(this).setTitle("Save profile").setView(name).setNegativeButton("Cancel", null)
                    .setPositiveButton("Save", (d, w) -> {
                        String n = name.getText().toString().trim().replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
                        if (n.isEmpty()) { Toast.makeText(this, "Use letters or numbers in the name", Toast.LENGTH_SHORT).show(); return; }
                        set("save_profile", n);
                    }).show();
            });
    }

    private String perfSummary() {
        String g = get("gpu_freq", "680000");
        return ("1".equals(get("oc", "0")) ? "Overclocked, " : "") + "GPU " + ("0".equals(g) ? "automatic" : (Integer.parseInt(g) / 1000) + " MHz")
            + ", thermal " + ("off".equals(get("thermal", "stock")) ? "limit off" : "stock");
    }
    private String pictureSummary() {
        String shp = get("pq_shp", "2");
        return "Sharpness " + ("0".equals(shp) ? "off" : shp) + ", dynamic range " + pick(get("pq_dre", "2"), new String[]{"0", "1", "2"}, new String[]{"off", "on", "automatic"}, "automatic");
    }
    private String lookSummary() {
        String n = pick(get("night", "yes"), new String[]{"auto", "no", "yes"}, new String[]{"Automatic", "Light", "Dark"}, "Dark");
        return n + ("1".equals(get("black_theme", "1")) ? ", pure black" : "") + ", " + ("home_wallpaper".equals(get("palette_source", "preset")) ? "wallpaper colours" : "custom accent");
    }
    private String displaySummary() {
        return get("anim_scale", "1.0") + "x animations, " + ("gestural".equals(get("nav_mode", "gestural")) ? "gestures" : "three buttons");
    }

    // ---------- pages ----------
    private void pagePerf() {
        liveCard();
        TextView hint = text("Changing a setting here turns the preset into Custom. The dial on the home screen sets all of these together.", 13.5f, false, dim());
        hint.setPadding(px(10), px(6), px(10), 0); col.addView(hint);
        LinearLayout c = group("Clock speed");
        rowChoice(c, "GPU clock", "Locks the PowerVR GPU to one speed. Ignored in Battery.", "gpu_freq", gpuLabels(), gpuValues(), "680000");
        rowChoice(c, "Fast cores, top speed", "Limits the four fast cores", "cap_big",
            new String[]{"No limit", "2.0 GHz", "1.75 GHz", "1.5 GHz", "1.2 GHz", "0.9 GHz"}, new String[]{"-1", "2009000", "1750000", "1484000", "1218000", "900000"}, "-1");
        rowChoice(c, "Efficiency cores, top speed", "Limits the four slower cores", "cap_little",
            new String[]{"No limit", "1.5 GHz", "1.2 GHz", "1.05 GHz", "0.75 GHz"}, new String[]{"-1", "1491000", "1226000", "1050000", "745000"}, "-1");
        c = group("App boost");
        rowChoice(c, "Visible app", "CPU boost for the app on screen. Automatic follows the preset.", "fg_boost",
            new String[]{"Automatic", "0", "10", "20", "30", "40", "60", "80", "100"}, new String[]{"auto", "0", "10", "20", "30", "40", "60", "80", "100"}, "auto");
        rowChoice(c, "Touched app", "Extra boost for the app you are touching", "ta_boost",
            new String[]{"0", "10", "20", "30", "50", "70"}, new String[]{"0", "10", "20", "30", "50", "70"}, "0");
        rowSwitch(c, "Rotate heavy work between fast cores", "Spreads heat", "big_rotation", "0");
        c = group("Power and heat");
        rowChoice(c, "Power mode", "MediaTek power mode and clock floor. The presets choose it for you.", "perf_profile",
            new String[]{"Battery (low power)", "Balanced", "Performance (sports)"}, new String[]{"battery", "balanced", "performance"}, "balanced");
        rowSwitch(c, "Overclock", "Pins both CPU clusters at their top speed. Runs hot and drains the battery.", "oc", "0");
        rowChoice(c, "CPU governor", "Used when Overclock is off", "governor", list("govs", "schedplus,ondemand,interactive,performance,powersave"), list("govs", "schedplus,ondemand,interactive,performance,powersave"), "schedplus");
        rowChoice(c, "Thermal limit", "Off removes MediaTek's heat limit. Not recommended while charging.", "thermal", new String[]{"Stock", "Off"}, new String[]{"stock", "off"}, "stock");
        c = group("Touch and clocks");
        rowChoice(c, "Touch boost length", "How long the clocks stay raised after a touch. Stock is 100 ms.", "touch_duration_ms",
            new String[]{"100 ms (stock)", "200 ms", "400 ms", "800 ms"}, new String[]{"100", "200", "400", "800"}, "400");
        rowChoice(c, "Touch boost clock", "Clock step used during a touch boost. Top clock is the highest, Stock step is the vendor value.", "touch_opp",
            new String[]{"Top clock", "Middle step", "Stock step"}, new String[]{"0", "1", "2"}, "0");
        rowChoice(c, "Touch boost strength", "How hard a touch pushes the clocks up, 0 to 100. Stock is 80.", "touch_boost",
            new String[]{"Off", "20", "40", "60", "80 (stock)", "100"}, new String[]{"0", "20", "40", "60", "80", "100"}, "100");
        rowChoice(c, "Clock drop delay", "How long a clock is held before it is lowered. Longer keeps short bursts smooth. Stock is 4 ms. Needs the schedplus governor.", "down_throttle",
            new String[]{"4 ms (stock)", "10 ms", "20 ms", "40 ms"}, new String[]{"4", "10", "20", "40"}, "20");
        rowChoice(c, "Clock rise delay", "Minimum time between clock increases. Stock is none. Needs the schedplus governor.", "up_throttle",
            new String[]{"None (stock)", "2 ms", "5 ms"}, new String[]{"0", "2", "5"}, "0");
        rowChoice(c, "Clock headroom", "How far above the measured load the scheduler sets the clock. Stock is 1.25x. More is smoother and uses more power.", "cap_margin",
            new String[]{"1.0x", "1.25x (stock)", "1.5x", "1.75x"}, new String[]{"1024", "1280", "1536", "1792"}, "1536");
        c = group("Idle and statistics");
        rowSwitch(c, "Deep idle (MCDI)", "Turning it off cuts wake up delay and costs idle battery.", "mcdi", "1");
        rowSwitch(c, "Scheduler statistics", "Kernel counters used by profilers. Off saves a little CPU time.", "schedstats", "0");
    }

    private void pageMemory() {
        LinearLayout c = group("Storage");
        rowChoice(c, "Read-ahead", "How much storage is read in advance", "readahead",
            new String[]{"128 KB", "256 KB", "512 KB", "1 MB", "2 MB"}, new String[]{"128", "256", "512", "1024", "2048"}, "512");
        rowChoice(c, "Write buffer", "Share of memory that can hold unsaved writes. The vendor's 1% makes large writes slow.", "dirty",
            new String[]{"1%", "5%", "10%", "20%", "30%"}, new String[]{"1", "5", "10", "20", "30"}, "1");
        rowChoice(c, "I/O scheduler", "Storage request ordering", "io_sched", list("io_scheds", "noop,deadline,cfq"), list("io_scheds", "noop,deadline,cfq"), "deadline");
        rowChoice(c, "File cache pressure", "Lower keeps file caches longer", "vfs_cache", new String[]{"25", "50", "100", "150"}, new String[]{"25", "50", "100", "150"}, "50");
        c = group("Memory");
        rowChoice(c, "Compressed memory (swappiness)", "Higher keeps more apps in memory by compressing them", "swappiness",
            new String[]{"20", "40", "60", "80", "100"}, new String[]{"20", "40", "60", "80", "100"}, "100");
        rowChoice(c, "Free memory reserve", "Memory kept free so new apps start without waiting", "extra_free",
            new String[]{"None", "24 MB", "48 MB", "96 MB"}, new String[]{"0", "24576", "49152", "98304"}, "49152");
        rowChoice(c, "Swap read-ahead", "Pages read back from compressed memory at once", "page_cluster",
            new String[]{"1 (stock)", "2", "4", "8"}, new String[]{"0", "1", "2", "3"}, "0");
        rowChoice(c, "Memory watermark", "How early the kernel frees memory in the background. Higher keeps more free and caches less. Stock is 10.", "watermark",
            new String[]{"10 (stock)", "20", "30"}, new String[]{"10", "20", "30"}, "10");
        rowChoice(c, "Background apps kept", "How many background apps stay in memory, set through device config.", "bg_procs",
            new String[]{"System default", "8", "16", "32", "64"}, new String[]{"default", "8", "16", "32", "64"}, "default");
        rowChoice(c, "Cached app limit", "The same kind of limit, set through the Settings database. Use this or Background apps kept, not both. Android may ignore one of them.", "cached_apps",
            new String[]{"System default", "16", "24", "32", "48"}, new String[]{"default", "16", "24", "32", "48"}, "default");
    }

    private void pagePicture() {
        TextView hint = text("MediaTek's picture engine. Every change restarts it, which takes about two seconds. The sharpness and colour numbers are MediaTek indexes: judge them by eye on a video.", 13.5f, false, dim());
        hint.setPadding(px(10), px(6), px(10), 0); col.addView(hint);
        LinearLayout c = group("Quick looks");
        String[][] looks = {
            {"Clean", "No sharpening, factory contrast. Best for compressed video.", "pq_shp=0 pq_ultrares=0 pq_dre=2 pq_vp_dre=1 pq_hdr=1 pq_color=0"},
            {"Factory", "What the tablet shipped with.", "pq_shp=2 pq_ultrares=2 pq_dre=2 pq_vp_dre=1 pq_hdr=1 pq_color=0"},
            {"Punchy", "Stronger contrast and detail. Can look harsh.", "pq_shp=3 pq_ultrares=2 pq_dre=1 pq_vp_dre=1 pq_hdr=1 pq_color=0"}};
        for (final String[] l : looks) {
            row(c, l[0], l[1], text("Apply", 15, true, accent())).setOnClickListener(v -> {
                for (String kv : l[2].split(" ")) { String[] p = kv.split("="); st.put(p[0], p[1]); Cfg.request(this, p[0], p[1]); }
                setStatus("Applying…"); col.removeCallbacks(reload); col.postDelayed(reload, 3000);
            });
        }
        final String[] oao = {"Off", "On", "Automatic"}, oaov = {"0", "1", "2"};
        c = group("Fine control");
        rowChoice(c, "Video contrast boost", "Local contrast for video playback", "pq_vp_dre", oao, oaov, "1");
        rowChoice(c, "Screen contrast boost", "Contrast for the whole screen", "pq_dre", oao, oaov, "2");
        rowChoice(c, "Ultra resolution", "Upscaling and detail enhancement", "pq_ultrares", oao, oaov, "2");
        rowSwitch(c, "HDR video", "Tone-maps HDR clips for this screen", "pq_hdr", "1");
        rowSlider(c, "Sharpness", "0 is off, 2 is the factory value", "pq_shp", 9, 2, new String[]{"Off", "1", "2", "3", "4", "5", "6", "7", "8", "9"});
        rowChoice(c, "Colour profile", "MediaTek colour index, 0 is the factory value", "pq_color", new String[]{"0 (factory)", "1", "2", "3"}, new String[]{"0", "1", "2", "3"}, "0");
    }

    private void pageLook() {
        LinearLayout c = group("Theme");
        rowChoice(c, "Dark mode", null, "night", new String[]{"Automatic", "Light", "Dark"}, new String[]{"auto", "no", "yes"}, "yes");
        rowSwitch(c, "Pure black backgrounds", "True black behind everything", "black_theme", "1");
        c = group("Accent colour");
        boolean fromWall = "home_wallpaper".equals(get("palette_source", "preset"));
        String curAcc = get("accent", "3E91FF");
        LinearLayout chips = new LinearLayout(this);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        String[][] colors = {{"3E91FF", "Blue"}, {"7C5CFF", "Violet"}, {"00B8A9", "Teal"}, {"34C759", "Green"}, {"FF9500", "Orange"}, {"FF3B30", "Red"}, {"FF4F9A", "Pink"}};
        for (final String[] cc : colors) {
            View chip = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(0xFF000000 | Integer.parseInt(cc[0], 16));
            if (!fromWall && cc[0].equalsIgnoreCase(curAcc)) g.setStroke(px(3), ink());
            chip.setBackground(g);
            chip.setContentDescription(cc[1]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(38), px(38));
            lp.setMargins(px(5), px(6), px(5), px(6));
            chip.setOnClickListener(v -> { st.put("palette_source", "preset"); Cfg.request(this, "palette_source", "preset"); set("accent", cc[0]); build(); });
            chips.addView(chip, lp);
        }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.addView(chips);
        hs.setPadding(px(14), px(8), px(14), px(8));
        c.addView(hs);
        rowSwitchLike(c, "Use wallpaper colours", "Picks the accent from your home wallpaper", fromWall, on -> {
            if (on) set("palette_source", "home_wallpaper"); else set("palette_source", "preset"); build(); });
        rowChoice(c, "Palette style", null, "theme_style",
            new String[]{"Tonal spot", "Vibrant", "Expressive", "Spritz", "Rainbow", "Fruit salad", "Monochrome"},
            new String[]{"TONAL_SPOT", "VIBRANT", "EXPRESSIVE", "SPRITZ", "RAINBOW", "FRUIT_SALAD", "MONOCHROMATIC"}, "TONAL_SPOT");
        c = group("Fonts and icons");
        String[] fl = get("fonts", "").split(",");
        ArrayList<String> fLabels = new ArrayList<>(), fVals = new ArrayList<>();
        fLabels.add("System default"); fVals.add("default");
        for (String f : fl) if (!f.isEmpty()) { fVals.add(f); fLabels.add(fontLabel(f)); }
        if (fVals.size() > 1) rowChoice(c, "Font", null, "font", fLabels.toArray(new String[0]), fVals.toArray(new String[0]), "default");
        if ("1".equals(get("has_shapes", "0")))
            rowChoice(c, "Icon shape", null, "icon_shape",
                new String[]{"Default (circle)", "Squircle", "Rounded square", "Teardrop", "Pebble", "Vessel", "Tapered", "Square"},
                new String[]{"default", "squircle", "roundedrect", "teardrop", "pebble", "vessel", "taperedrect", "square"}, "default");
        if ("1".equals(get("has_packs", "0")))
            rowChoice(c, "System icon pack", null, "icon_pack",
                new String[]{"Default", "Rounded", "Filled", "Kai", "Sam", "Victor", "Circular"},
                new String[]{"default", "rounded", "filled", "kai", "sam", "victor", "circular"}, "default");
        rowButton(c, "Wallpaper and style", "Wallpapers and lock screen", new Intent(Intent.ACTION_SET_WALLPAPER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private interface OnBool { void on(boolean v); }
    private void rowSwitchLike(LinearLayout c, String title, String sub, boolean checked, final OnBool cb) {
        Switch s = new Switch(this); s.setChecked(checked);
        s.setOnCheckedChangeListener((v, on) -> cb.on(on));
        row(c, title, sub, s).setOnClickListener(v -> s.toggle());
    }

    private void pageDisplay() {
        LinearLayout c = group("Motion");
        rowChoice(c, "Navigation", null, "nav_mode", new String[]{"Swipe gestures", "Three buttons"}, new String[]{"gestural", "threebutton"}, "gestural");
        int a = 10; try { a = Math.round(Float.parseFloat(get("anim_scale", "1.0")) * 10); } catch (Exception ignored) { }
        final TextView av = text(String.format(Locale.US, "%.1fx", a / 10f), 15, true, accent());
        SeekBar sb = new SeekBar(this); sb.setMax(20); sb.setProgress(a);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) { av.setText(String.format(Locale.US, "%.1fx", p / 10f)); }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { set("anim_scale", String.format(Locale.US, "%.1f", s.getProgress() / 10f)); }
        });
        row(c, "Animation speed", "Lower is faster. 0.5x makes everything feel quicker.", av);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(px(14), 0, px(14), px(10));
        c.addView(sb, lp);
        rowSwitch(c, "Turn off window blur", "Cheaper to draw, which makes launcher and shade smoother", "window_blur", "0");
        c = group("Screen");
        rowChoice(c, "Display size", "Smaller shows more content", "density",
            new String[]{"Stock (240)", "Compact (192)", "Small (213)", "Large (260)", "Largest (280)"}, new String[]{"reset", "192", "213", "260", "280"}, "192");
        rowChoice(c, "Font size", null, "font_scale", new String[]{"Small", "Default", "Large", "Largest"}, new String[]{"0.85", "1.0", "1.15", "1.3"}, "1.0");
        rowChoice(c, "Screen timeout", null, "screen_timeout",
            new String[]{"15 seconds", "30 seconds", "1 minute", "2 minutes", "5 minutes", "10 minutes", "30 minutes"},
            new String[]{"15000", "30000", "60000", "120000", "300000", "600000", "1800000"}, "60000");
        rowChoice(c, "Stay awake while charging", null, "stay_awake", new String[]{"Never", "On AC and USB", "On AC, USB and wireless"}, new String[]{"0", "3", "7"}, "0");
        rowSwitch(c, "Auto-rotate", null, "auto_rotate", "1");
        rowSwitch(c, "Show taps", "Draws a dot where you touch", "show_taps", "0");
        c = group("Start-up");
        rowChoice(c, "Boot animation", null, "bootanim", new String[]{"Gemini (Pixel 10)", "ArrowOS", "Galaxy", "Aurora ring", "Stock LineageOS"}, new String[]{"gemini", "arrow", "galaxy", "aurora", "stock"}, "gemini");
    }

    private void pageConnect() {
        LinearLayout c = group("Wi-Fi");
        rowChoice(c, "Country code", "Sets the allowed Wi-Fi channels and power. Pick the country you are in. Applied again after every boot.", "wifi_country",
            new String[]{"Automatic", "Canada", "United States", "United Kingdom", "Germany", "France", "India", "Australia", "Japan", "Mexico", "Brazil"},
            new String[]{"auto", "CA", "US", "GB", "DE", "FR", "IN", "AU", "JP", "MX", "BR"}, "CA");
        c = group("Network");
        rowChoice(c, "Private DNS", null, "dns",
            new String[]{"Off", "Automatic", "Google", "Cloudflare", "AdGuard (blocks ads)", "Quad9"}, new String[]{"off", "auto", "google", "cloudflare", "adguard", "quad9"}, "auto");
        rowChoice(c, "TCP congestion control", "How fast downloads ramp up", "tcp_cc", list("tcps", "bic,cubic,reno"), list("tcps", "bic,cubic,reno"), "bic");
        c = group("Bluetooth and audio");
        rowSwitch(c, "Bluetooth MediaTek workaround", "Lets this controller connect. Restarts Bluetooth.", "bt_work", "0");
        rowSwitch(c, "Allow Treble audio effects", "Left on, app audio can cut out after a few milliseconds.", "phh_effects", "0");
        c = group("Developer");
        rowSwitch(c, "ADB over network", "Port 5555 on Wi-Fi. Anyone on the network can connect.", "adb_tcp", "0");
        rowSwitch(c, "Serial console shell", "Android shows a performance warning while it runs.", "serial_console", "0");
    }

    private String widevine() {
        try {
            android.media.MediaDrm d = new android.media.MediaDrm(new java.util.UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL));
            String r = d.getPropertyString("securityLevel") + ", HDCP " + d.getPropertyString("maxHdcpLevel");
            android.util.Log.i("RomTuner", "widevine " + r + " vendor=" + d.getPropertyString("vendor") + " version=" + d.getPropertyString("version")
                + " systemId=" + d.getPropertyString("systemId") + " hdcp=" + d.getPropertyString("hdcpLevel"));
            d.close();
            return r;
        } catch (Throwable t) { android.util.Log.i("RomTuner", "widevine failed " + t); return "unavailable"; }
    }

    // ---------- per app compile ----------
    private static class App { String pkg, label; boolean system, code; }
    private static final HashSet<String> NATIVE = new HashSet<>(Arrays.asList("speed", "speed-profile", "everything", "everything-profile", "space", "space-profile"));
    private volatile ArrayList<App> appsAll;               // from PackageManager, built off the UI thread
    private boolean appsLoading;
    private long appsLoadedAt, appsAsked;
    private int appsAsks;
    private Map<String, String> appStates;                 // package -> compiler filter; null while the daemon's file is missing or incomplete
    private Map<String, String> appsSt = new java.util.HashMap<>();
    private String appsStamp = "", appsSig = "", appsQuery = "";
    private int appsFilter, appsLimit = 60;                // filter: 0 all, 1 not compiled, 2 compiled
    private LinearLayout appsBox;
    private TextView appsInfo;

    private static String get0(Map<String, String> m, String k) { String v = m.get(k); return v == null ? "" : v; }

    private void pageApps() {
        TextView hint = text("Tap an app to compile it for faster launches or to put it back to the system default. Apps are compiled one at a time.", 13.5f, false, dim());
        hint.setPadding(px(10), px(6), px(10), 0); col.addView(hint);
        LinearLayout c = group(null);
        rowAction(c, "Compile all not compiled", "Compiles every app that is not at speed yet, apps at speed profile included. Runs in the background and takes 10 to 15 minutes.", () -> compile("compile_now"));

        appsInfo = text("", 13.5f, false, dim());
        appsInfo.setPadding(px(10), px(18), px(10), px(8));
        appsInfo.setOnClickListener(v -> { appsAsks = 0; askApps(); });
        col.addView(appsInfo);

        EditText q = new EditText(this);
        q.setHint("Search by name or package"); q.setSingleLine(true); q.setTextSize(15);
        q.setTextColor(ink()); q.setHintTextColor(dim());
        q.setImeOptions(EditorInfo.IME_ACTION_DONE);
        q.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        GradientDrawable qg = new GradientDrawable(); qg.setColor(card()); qg.setCornerRadius(px(26));
        q.setBackground(qg); q.setPadding(px(20), px(14), px(20), px(14));
        q.setText(appsQuery);
        q.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int d) { }
            @Override public void afterTextChanged(Editable e) { appsQuery = e.toString(); appsLimit = 60; fillApps(); }
        });
        col.addView(q, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(0, px(12), 0, px(2));
        String[] names = {"All", "Not compiled", "Compiled"};
        for (int i = 0; i < names.length; i++) {
            final int fi = i;
            boolean on = i == appsFilter;
            TextView t = text(names[i], 14, true, on ? onAccent() : ink());
            t.setGravity(Gravity.CENTER); t.setPadding(px(18), px(10), px(18), px(10));
            GradientDrawable g = new GradientDrawable(); g.setCornerRadius(px(20)); g.setColor(on ? accent() : card());
            t.setBackground(g);
            t.setOnClickListener(v -> { appsFilter = fi; appsLimit = 60; build(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2); lp.setMargins(0, 0, px(8), 0);
            chips.addView(t, lp);
        }
        col.addView(chips);

        appsBox = group(null);
        if (!appsLoading && System.currentTimeMillis() - appsLoadedAt > 30000) loadAppList();
        appsSig = "";
        refreshApps();
    }

    /** Reads the installed apps; labels take a moment, so this runs on its own thread. */
    private void loadAppList() {
        appsLoading = true;
        new Thread(() -> {
            ArrayList<App> out = new ArrayList<>();
            try {
                PackageManager pm = getPackageManager();
                for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                    if (!ai.enabled) continue;
                    App a = new App();
                    a.pkg = ai.packageName;
                    try { a.label = String.valueOf(ai.loadLabel(pm)); } catch (Exception e) { a.label = a.pkg; }
                    a.system = (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
                    a.code = (ai.flags & ApplicationInfo.FLAG_HAS_CODE) != 0;
                    out.add(a);
                }
            } catch (Throwable ignored) { }
            ui.post(() -> {
                appsLoading = false;
                if (!out.isEmpty() || appsAll == null) { appsAll = out; appsLoadedAt = System.currentTimeMillis(); }
                if (!isDestroyed() && "apps".equals(page)) fillApps();
            });
        }).start();
    }

    private void askApps() { Cfg.request(this, "refresh_apps", "1"); appsAsked = System.currentTimeMillis(); appsAsks++; }

    /** Runs every second while the page is open: re-reads the state copy and the list file and redraws only when something changed. */
    private void refreshApps() {
        if (appsInfo == null) return;
        Map<String, String> s = Cfg.load(this);
        appsSt = s;
        String stamp = get0(s, "apps_state_time"), rf = get0(s, "apps_refresh");
        if (appStates == null || (!stamp.isEmpty() && !stamp.equals(appsStamp))) {
            Map<String, String> m = Cfg.loadApps(this);
            if (m != null) { appStates = m; appsStamp = get0(m, "apps_state_time"); appsAsks = 0; }
        }
        long now = System.currentTimeMillis();
        String info, job = get0(s, "compile_pkg_status"), queue = get0(s, "compile_queue");
        int waiting = queue.isEmpty() ? 0 : queue.split(",").length;
        if (appStates == null) {
            if ("running".equals(rf)) info = "Loading compile states…";
            else if ("failed".equals(rf)) info = "Could not read the compile states. Tap here to try again.";
            else if (appsAsks >= 3) info = "The tuner service did not answer. Install the newest tablet_tunerd.sh on the tablet, then tap here.";
            else { info = "Loading compile states…"; if (now - appsAsked > 8000) askApps(); }
        } else if ("running".equals(s.get("compile_status"))) {
            info = "A system compile is running. Wait for it to finish before compiling one app.";
        } else if (job.endsWith(":running")) {
            info = "Compiling " + job.substring(0, job.length() - 8) + (waiting > 0 ? ", " + waiting + " waiting" : "") + ".";
        } else {
            long t = 0; try { t = Long.parseLong(appsStamp) * 1000; } catch (Exception ignored) { }
            info = "Compile states read at " + (t > 0 ? java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(new java.util.Date(t)) : "an unknown time") + ". Tap here to read them again.";
            if (t > 0 && now - t > 300000 && now - appsAsked > 60000) askApps();
        }
        appsInfo.setText(info);
        String sig = appsStamp + "|" + job + "|" + queue + "|" + s.get("compile_status") + "|" + (appStates == null);
        if (!sig.equals(appsSig)) { appsSig = sig; fillApps(); }
    }

    private static String stateLabel(String f) {
        if (f == null || f.isEmpty()) return "Not compiled";
        switch (f) {
            case "unknown": case "error": case "run-from-apk": case "run-from-apk-fallback": case "run-from-vdex-fallback": return "Not compiled";
            default: return f.substring(0, 1).toUpperCase(Locale.US) + f.substring(1).replace('-', ' ');
        }
    }
    /** 0 not compiled, 1 compiled to native code, 2 nothing to compile. */
    private int appGroup(App a) {
        if (!a.code) return 2;
        return appStates != null && NATIVE.contains(appStates.get(a.pkg)) ? 1 : 0;
    }
    private String baseState(App a) { return !a.code ? "No code" : appStates == null ? "Loading" : stateLabel(appStates.get(a.pkg)); }
    private String stateText(App a) {
        String job = get0(appsSt, "compile_pkg_status"), jp = "", js = "";
        int i = job.lastIndexOf(':');
        if (i > 0) { jp = job.substring(0, i); js = job.substring(i + 1); }
        if (a.pkg.equals(jp) && "running".equals(js)) return "Compiling…";
        if (("," + get0(appsSt, "compile_queue")).contains("," + a.pkg + ",")) return "Waiting";
        if (a.pkg.equals(jp) && "failed".equals(js)) return "Failed";
        if (a.pkg.equals(jp) && "done".equals(js)) return "Done, " + baseState(a).toLowerCase(Locale.US);
        return baseState(a);
    }

    private void fillApps() {
        if (appsBox == null) return;
        appsBox.removeAllViews();
        if (appsAll == null) { row(appsBox, "Loading apps…", null, null); return; }
        if (appStates == null && appsFilter != 0) { row(appsBox, "Waiting for the compile states", "Pick All to see the apps meanwhile.", null); return; }
        String q = appsQuery.trim().toLowerCase(Locale.ROOT);
        final ArrayList<App> m = new ArrayList<>();
        for (App a : appsAll) {
            int g = appGroup(a);
            if (appsFilter == 1 && g != 0 || appsFilter == 2 && g != 1) continue;
            if (!q.isEmpty() && !a.label.toLowerCase(Locale.ROOT).contains(q) && !a.pkg.toLowerCase(Locale.ROOT).contains(q)) continue;
            m.add(a);
        }
        Collections.sort(m, (x, y) -> { int gx = appGroup(x), gy = appGroup(y); return gx != gy ? gx - gy : x.label.compareToIgnoreCase(y.label); });
        if (m.isEmpty()) { row(appsBox, "No apps match", null, null); return; }
        for (int i = 0; i < Math.min(m.size(), appsLimit); i++) {
            final App a = m.get(i);
            String st1 = stateText(a);
            TextView sv = text(st1, 14, true, appGroup(a) == 1 ? accent() : ink());
            sv.setGravity(Gravity.END);
            row(appsBox, a.label, a.pkg + (a.system ? "\nSystem app" : ""), sv).setOnClickListener(v -> appDialog(a));
        }
        if (m.size() > appsLimit)
            row(appsBox, "Show more", appsLimit + " of " + m.size() + " shown", text("›", 22, false, dim())).setOnClickListener(v -> { appsLimit += 60; fillApps(); });
    }

    private int attrColor(int attr) {
        android.content.res.TypedArray t = obtainStyledAttributes(new int[]{attr});
        try { return t.getColor(0, ink()); } finally { t.recycle(); }
    }

    private void appDialog(final App a) {
        final int pri = attrColor(android.R.attr.textColorPrimary), sec = attrColor(android.R.attr.textColorSecondary);
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setPadding(px(24), px(8), px(24), 0);
        v.addView(text(a.pkg, 13, false, sec));
        TextView cur = text("Current state: " + stateText(a), 15, true, pri);
        cur.setPadding(0, px(6), 0, px(10));
        v.addView(cur);
        final AlertDialog[] dlg = new AlertDialog[1];
        String[][] acts = {
            {"Compile (speed)", "Compiles all of the app's code to native code. Fastest launches, uses more storage.", "compile_pkg"},
            {"Compile (speed profile)", "Compiles only the code this app has used before. Quicker to finish, smaller gain.", "compile_pkg_profile"},
            {"Reset", "Removes the compiled code and goes back to the system default.", "reset_pkg"}};
        for (final String[] x : acts) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.VERTICAL);
            r.setPadding(0, px(10), 0, px(10));
            r.addView(text(x[0], 16, true, accent()));
            TextView d = text(x[1], 13, false, sec); d.setPadding(0, px(2), 0, 0);
            r.addView(d);
            r.setOnClickListener(b -> { dlg[0].dismiss(); appAction(x[2], a); });
            v.addView(r);
        }
        dlg[0] = new AlertDialog.Builder(this).setTitle(a.label).setView(v).setNegativeButton("Cancel", null).show();
    }

    private void appAction(String key, App a) {
        Map<String, String> s = Cfg.load(this);
        if ("running".equals(s.get("compile_status"))) { Toast.makeText(this, "A system compile is running. Wait for it to finish.", Toast.LENGTH_SHORT).show(); return; }
        Cfg.request(this, key, a.pkg);
        setStatus("Sent");
    }

    private void pageSystem() {
        LinearLayout c = group("Shortcuts");
        rowButton(c, "Status bar", "Clock, battery, network speed, brightness slider", comp("org.lineageos.lineageparts", "org.lineageos.lineageparts.statusbar.StatusBarSettings"));
        rowButton(c, "Buttons", "Navigation and hardware key actions", comp("org.lineageos.lineageparts", "org.lineageos.lineageparts.input.ButtonSettings"));
        rowButton(c, "Display rotation", "Allowed rotation angles", comp("org.lineageos.lineageparts", "org.lineageos.lineageparts.hardware.DisplayRotation"));
        rowButton(c, "Charging control", "Limit the charge level", comp("org.lineageos.lineageparts", "org.lineageos.lineageparts.health.ChargingControlSettings"));
        rowButton(c, "Dolby Atmos", "Sound modes", comp("com.dolby.daxappui", "com.dolby.daxappui.MainActivity"));
        rowButton(c, "Developer options", null, new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        c = group("Streaming");
        row(c, "Widevine", "Decides the streaming quality: L1 allows HD, L3 is limited to about 480p", text(widevine(), 15, true, accent()));
        c = group("Diagnostics");
        rowSwitch(c, "System logging", "Off for speed. Turn on to debug.", "logging", "0");
        c = group("App speed");
        compileView = text(compileLabel(get("compile_status", "idle")), 15, true, accent());
        row(c, "Compile apps now", "Compiles installed apps for faster launches. Takes 10 to 15 minutes, runs in the background. SystemUI and the launcher restart when it finishes the first apps.", compileView)
            .setOnClickListener(v -> compile("compile_now"));
        rowAction(c, "Recompile all apps", "Redoes apps that are already compiled too. Use Restart system interface afterwards.", () -> compile("compile_all"));
        rowGo(c, "Per app compile", "See the compile state of every app and compile or reset one app", "apps");
        c = group("Restart");
        rowAction(c, "Restart system interface", "Re-applies theme changes without rebooting", () -> { Cfg.request(this, "restart_ui", "1"); setStatus("Restarting the interface…"); });
        rowAction(c, "Reboot the tablet", "Needed for a few changes to fully apply", () ->
            new AlertDialog.Builder(this).setMessage("Reboot the tablet now?").setNegativeButton("Cancel", null)
                .setPositiveButton("Reboot", (d, w) -> Cfg.request(this, "reboot", "1")).show());
    }
}
