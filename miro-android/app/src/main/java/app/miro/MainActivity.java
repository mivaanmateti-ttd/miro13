package app.miro;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    static final String PREFS = "miro";
    static final int REQ_FILE = 1;

    WebView web;
    EditText urlBox;
    Spinner spinner;
    ProgressBar progress;
    JSONArray profiles = new JSONArray();
    String fillerSrc = "";
    ValueCallback<Uri[]> filePathCb;
    String flowSrc = "";
    boolean flowActive = false;
    int flowStep = 0;
    Handler handler = new Handler(Looper.getMainLooper());
    Button contBtn;
    LinearLayout addrRow;
    boolean autoHidden = false;
    long navTime = 0;
    String bannerSrc = "";
    boolean scrolledDown = false;

    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        fillerSrc = readAsset("filler.js");
        flowSrc = readAsset("flow.js");
        bannerSrc = readAsset("banner.js");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        // Top bar: Miro buttons on the first row, profile picker below
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(Color.parseColor("#f1f5f9"));
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        Button miro = new Button(this);
        miro.setText("Miro: Fill");
        miro.setAllCaps(false);
        miro.setTextColor(Color.WHITE);
        miro.setTextSize(14);
        miro.setBackgroundColor(Color.parseColor("#1e3a8a"));
        miro.setOnClickListener(v -> {
            if (flowActive) stopFlow(null);
            runFill();
        });
        row1.addView(miro, new LinearLayout.LayoutParams(0, dp(44), 1f));

        contBtn = new Button(this);
        contBtn.setText("Fill + Continue");
        contBtn.setAllCaps(false);
        contBtn.setTextColor(Color.WHITE);
        contBtn.setTextSize(14);
        contBtn.setBackgroundColor(Color.parseColor("#0f766e"));
        contBtn.setOnClickListener(v -> toggleFlow());
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, dp(44), 1f);
        cp.leftMargin = dp(8);
        row1.addView(contBtn, cp);
        bar.addView(row1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(android.view.Gravity.CENTER_VERTICAL);
        spinner = new Spinner(this);
        row2.addView(spinner, new LinearLayout.LayoutParams(0, dp(44), 1f));
        Button manage = new Button(this);
        manage.setText("Profiles");
        manage.setAllCaps(false);
        manage.setOnClickListener(v -> startActivity(new Intent(this, ProfilesActivity.class)));
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        mp.leftMargin = dp(8);
        row2.addView(manage, mp);
        bar.addView(row2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Address row
        addrRow = new LinearLayout(this);
        addrRow.setOrientation(LinearLayout.HORIZONTAL);
        addrRow.setPadding(dp(8), dp(2), dp(8), dp(2));
        urlBox = new EditText(this);
        urlBox.setSingleLine(true);
        urlBox.setHint("Search or type a web address");
        urlBox.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlBox.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        urlBox.setOnEditorActionListener((v, id, e) -> {
            if (id == EditorInfo.IME_ACTION_GO || (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                go(urlBox.getText().toString());
                return true;
            }
            return false;
        });
        addrRow.addView(urlBox, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button goBtn = new Button(this);
        goBtn.setText("Go");
        goBtn.setAllCaps(false);
        goBtn.setOnClickListener(v -> go(urlBox.getText().toString()));
        addrRow.addView(goBtn, new LinearLayout.LayoutParams(dp(64), dp(44)));
        root.addView(addrRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)));

        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setUserAgentString(s.getUserAgentString().replace("; wv", "").replace("Version/4.0 ", ""));
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);   // payment pages need their own cookies
        web.addJavascriptInterface(new Bridge(), "MiroBridge");
        web.setOnScrollChangeListener((v, sx, sy, ox, oy) -> {
            if (addrRow.getVisibility() != View.GONE) return;
            if (sy > dp(120)) scrolledDown = true;
            else if (sy <= 2 && scrolledDown && SystemClock.uptimeMillis() - navTime > 1500) {
                addrRow.setVisibility(View.VISIBLE);   // scrolled back to the very top: show the search bar again
                autoHidden = false;
                scrolledDown = false;
            }
        });

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, android.webkit.WebResourceRequest r) {
                String u = r.getUrl().toString();
                if (u.startsWith("http://") || u.startsWith("https://")) return false;
                openExternal(u);
                return true;
            }

            @Override
            public void onPageStarted(WebView v, String u, android.graphics.Bitmap f) {
                boolean start = u.startsWith("file:///android_asset");
                urlBox.setText(start ? "" : u);
                navTime = SystemClock.uptimeMillis();
                if (!start) {
                    addrRow.setVisibility(View.GONE);   // a site is open: hide the search bar
                    autoHidden = true;
                    scrolledDown = false;
                }
            }

            @Override
            public void onPageCommitVisible(WebView v, String u) {
                if (!u.startsWith("file:///android_asset")) v.evaluateJavascript(bannerSrc, null);   // close the site's app-install banner
            }

            @Override
            public void onPageFinished(WebView v, String u) {
                if (u.startsWith("file:///android_asset")) return;
                v.evaluateJavascript(bannerSrc, null);
                if (autoHidden) addrRow.setVisibility(View.GONE);   // keep the search bar hidden while a site is open
            }

            @Override
            public void onReceivedError(WebView v, android.webkit.WebResourceRequest r, android.webkit.WebResourceError e) {
                if (r.isForMainFrame()) {
                    addrRow.setVisibility(View.VISIBLE);
                    autoHidden = false;
                }
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                progress.setProgress(p);
                progress.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage m) {
                android.util.Log.d("MiroWeb", m.message() + " (" + m.sourceId() + ":" + m.lineNumber() + ")");
                return true;
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                WebView temp = new WebView(MainActivity.this);
                temp.getSettings().setJavaScriptEnabled(true);
                temp.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, android.webkit.WebResourceRequest r) {
                        routeLink(r.getUrl().toString());
                        return true;
                    }

                    @Override
                    public void onPageStarted(WebView v, String u, android.graphics.Bitmap f) {
                        if (u != null && !u.equals("about:blank")) {
                            v.stopLoading();
                            routeLink(u);
                        }
                    }
                });
                ((WebView.WebViewTransport) resultMsg.obj).setWebView(temp);
                resultMsg.sendToTarget();
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (filePathCb != null) filePathCb.onReceiveValue(null);
                filePathCb = cb;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    filePathCb = null;
                    return false;
                }
                return true;
            }
        });

        if (getIntent() != null && getIntent().getData() != null) go(getIntent().getData().toString());
        else web.loadUrl("file:///android_asset/start.html");   // one search bar only: ours
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProfiles();
    }

    void loadProfiles() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        try {
            profiles = new JSONArray(sp.getString("profiles", "[]"));
        } catch (Exception e) {
            profiles = new JSONArray();
        }
        List<String> names = new ArrayList<>();
        for (int i = 0; i < profiles.length(); i++) {
            JSONObject p = profiles.optJSONObject(i);
            String n = p == null ? "" : p.optString("name", "");
            names.add(n.isEmpty() ? "Profile " + (i + 1) : n);
        }
        if (names.isEmpty()) names.add("No profiles yet");
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        int sel = sp.getInt("selected", 0);
        if (sel >= 0 && sel < names.size()) spinner.setSelection(sel);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt("selected", Math.max(0, spinner.getSelectedItemPosition())).apply();
    }

    void go(String input) {
        String t = input == null ? "" : input.trim();
        if (t.isEmpty()) return;
        String u;
        if (t.startsWith("http://") || t.startsWith("https://")) u = t;
        else if (!t.contains(" ") && t.contains(".")) u = "https://" + t;
        else u = "https://duckduckgo.com/?q=" + Uri.encode(t);
        // Hide the keyboard and the search bar as soon as the address is chosen.
        urlBox.clearFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(urlBox.getWindowToken(), 0);
        addrRow.setVisibility(View.GONE);
        autoHidden = true;
        scrolledDown = false;
        web.loadUrl(u);
    }

    static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    JSONObject buildArgs(JSONObject p) throws Exception {
        JSONArray members = p.optJSONArray("members");
        JSONArray people = new JSONArray();
        if (members != null) {
            for (int i = 0; i < members.length() && people.length() < 6; i++) {
                JSONObject m = members.getJSONObject(i);
                String name = clean(m.optString("name")), age = clean(m.optString("age")), id = clean(m.optString("idNumber"));
                if (name.isEmpty() && age.isEmpty() && id.isEmpty()) continue;
                JSONObject o = new JSONObject();
                o.put("id", String.valueOf(people.length() + 1));
                o.put("name", name);
                Object ageVal = JSONObject.NULL;
                try {
                    ageVal = Integer.parseInt(age);
                } catch (Exception ignored) { }
                o.put("age", ageVal);
                o.put("gender", clean(m.optString("gender")));
                String type = clean(m.optString("idType"));
                if (type.toLowerCase().contains("passport")) type = "Passport";
                else if (type.toLowerCase().contains("aadhaar")) type = "Aadhaar";
                o.put("idType", type);
                o.put("idNumber", id);
                o.put("selected", true);
                people.put(o);
            }
        }
        JSONObject c = new JSONObject();
        c.put("email", clean(p.optString("email")));
        c.put("mobile", clean(p.optString("mobile")));
        c.put("city", clean(p.optString("city")));
        c.put("state", clean(p.optString("state")));
        c.put("country", clean(p.optString("country")));
        c.put("pincode", clean(p.optString("pin")));
        c.put("gothram", clean(p.optString("gothram")));
        c.put("doorNo", "");
        c.put("street", "");
        c.put("district", "");
        JSONObject args = new JSONObject();
        args.put("people", people);
        args.put("contact", c);
        args.put("previous", JSONObject.NULL);
        args.put("contactFirst", true);
        args.put("groupCard", false);
        args.put("focusCaptcha", true);
        args.put("budgetMs", 11000);
        return args;
    }

    void runFill() {
        if (profiles.length() == 0) {
            stopFlow(null);
            Toast.makeText(this, "Add a profile first", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, ProfilesActivity.class));
            return;
        }
        int idx = Math.max(0, Math.min(spinner.getSelectedItemPosition(), profiles.length() - 1));
        try {
            JSONObject args = buildArgs(profiles.getJSONObject(idx));
            String js = "(function(){" + fillerSrc + "\nvar a=" + args.toString() + ";"
                    + "fillPage(a).then(function(r){MiroBridge.onFillResult(JSON.stringify(r||{}));},"
                    + "function(e){MiroBridge.onFillResult(JSON.stringify({error:String(e)}));});})();";
            if (flowStep == 0) Toast.makeText(this, "Filling…", Toast.LENGTH_SHORT).show();
            web.evaluateJavascript(js, null);
        } catch (Exception e) {
            stopFlow(null);
            Toast.makeText(this, "Could not start: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ---------- Fill + Continue ----------
    void toggleFlow() {
        if (flowActive) {
            stopFlow("Stopped");
            return;
        }
        flowActive = true;
        flowStep = 0;
        contBtn.setText("Stop");
        runFill();
    }

    void stopFlow(String msg) {
        flowActive = false;
        flowStep = 0;
        handler.removeCallbacksAndMessages(null);
        if (contBtn != null) contBtn.setText("Fill + Continue");
        if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    String tapLog = "";
    static final String PROBE = "(function(){return location.href+'|'+((document.body&&document.body.innerText)||'').slice(0,300);})()";

    String runJs(String call) {
        return "(function(){" + flowSrc + "\nreturn JSON.stringify(" + call + ");})()";
    }

    JSONObject parse(String value) throws Exception {
        Object o = new JSONTokener(value).nextValue();
        return new JSONObject(String.valueOf(o));
    }

    // First page: fill is done, tap Continue / Submit.
    void runStep() {
        tapLog = "";
        if (!flowActive) return;
        web.evaluateJavascript(runJs("miroStep(false,true)"), value -> {
            if (!flowActive) return;
            try {
                JSONObject r = parse(value);
                String firstAct = r.optString("action");
                if ("clicked".equals(firstAct) || "tap".equals(firstAct)) {
                    tapLog = r.optString("reason");
                    final String sg = r.optString("sig");
                    tapTargets(r.optJSONArray("targets"), () -> waitNext(sg, System.currentTimeMillis() + 8000));
                } else {
                    stopFlow(r.optString("reason", "Done"));
                }
            } catch (Exception e) {
                stopFlow("Could not continue");
            }
        });
    }

    // Watch for the next page and act on it the moment it appears (no fixed delay).
    void waitNext(String sig, long deadline) {
        if (!flowActive) return;
        if (System.currentTimeMillis() > deadline) {
            stopFlow("Tapped \"" + tapLog + "\". The next page did not appear in time.");
            return;
        }
        web.evaluateJavascript(PROBE, value -> {
            if (!flowActive) return;
            String now = "";
            try {
                Object o = new JSONTokener(value).nextValue();
                now = String.valueOf(o);
            } catch (Exception ignored) { }
            if (!now.isEmpty() && !now.equals("null") && !now.equals(sig)) secondStep(deadline);
            else handler.postDelayed(() -> waitNext(sig, deadline), 150);
        });
    }

    // Next page: tap its Continue too.
    void secondStep(long deadline) {
        if (!flowActive) return;
        web.evaluateJavascript(runJs("miroStep(true,true)"), value -> {
            if (!flowActive) return;
            try {
                JSONObject r = parse(value);
                String act = r.optString("action");
                if ("clicked".equals(act) || "tap".equals(act)) {
                    final String done = "Tapped \"" + tapLog + " > " + r.optString("reason") + "\". Check the page.";
                    tapTargets(r.optJSONArray("targets"), () -> stopFlow(done));
                } else if (r.optString("reason").startsWith("No Continue") && System.currentTimeMillis() < deadline) {
                    handler.postDelayed(() -> secondStep(deadline), 150);
                } else {
                    stopFlow("Tapped \"" + tapLog + "\". Then: " + r.optString("reason", "Done"));
                }
            } catch (Exception e) {
                stopFlow("Tapped \"" + tapLog + "\". Check the page.");
            }
        });
    }

    class Bridge {
        @JavascriptInterface
        public void onFillResult(String json) {
            runOnUiThread(() -> {
                if (flowActive) {
                    try {
                        JSONObject fr = new JSONObject(json);
                        if (fr.has("error")) {
                            stopFlow("Fill failed");
                        } else if (fr.optBoolean("busy")) {
                            handler.postDelayed(MainActivity.this::runStep, 1000);
                        } else if (flowStep == 0 && fr.optInt("filled", 0) == 0) {
                            stopFlow("No matching fields found on this page");
                        } else {
                            handler.postDelayed(MainActivity.this::runStep, 700);
                        }
                    } catch (Exception e) {
                        stopFlow("Fill failed");
                    }
                    return;
                }
                String msg;
                try {
                    JSONObject r = new JSONObject(json);
                    if (r.has("error")) msg = "Fill failed";
                    else if (r.optBoolean("busy")) msg = "Already filling this page";
                    else {
                        int n = r.optInt("filled", 0);
                        msg = n > 0 ? "Filled " + n + " field(s). Please check them." : "No matching fields found on this page";
                    }
                } catch (Exception e) {
                    msg = "Done";
                }
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            });
        }
    }

    // A link opened in a new window: web pages load here, app links go to the matching app.
    void routeLink(String u) {
        if (u.startsWith("http://") || u.startsWith("https://")) {
            String host = Uri.parse(u).getHost();
            Toast.makeText(this, "New window: " + (host == null ? "" : host), Toast.LENGTH_SHORT).show();
        }
        if (u.startsWith("http://") || u.startsWith("https://")) web.loadUrl(u);
        else openExternal(u);
    }

    // UPI / payment-app links (upi:, intent:, phonepe:, paytmmp:, gpay: ...), phone and mail links.
    void openExternal(String u) {
        String scheme = u.contains(":") ? u.substring(0, u.indexOf(':')) : u;
        Toast.makeText(this, "Opening app link (" + scheme + ")", Toast.LENGTH_SHORT).show();
        try {
            Intent i;
            if (u.startsWith("intent:")) {
                i = Intent.parseUri(u, Intent.URI_INTENT_SCHEME);
                i.addCategory(Intent.CATEGORY_BROWSABLE);
                i.setComponent(null);
                i.setSelector(null);
            } else {
                i = new Intent(Intent.ACTION_VIEW, Uri.parse(u));
                i.addCategory(Intent.CATEGORY_BROWSABLE);
            }
            try {
                startActivity(i);
            } catch (ActivityNotFoundException e) {
                String fb = i.getStringExtra("browser_fallback_url");
                if (fb != null && (fb.startsWith("http://") || fb.startsWith("https://"))) {
                    web.loadUrl(fb);
                } else if (i.getPackage() != null) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + i.getPackage())));
                } else {
                    Toast.makeText(this, "No app found to open this link", Toast.LENGTH_LONG).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not open the payment app", Toast.LENGTH_LONG).show();
        }
    }

    // Real finger taps sent to the page (so payment pages treat them as genuine).
    void tapTargets(JSONArray t, Runnable after) {
        if (t == null || t.length() == 0) {
            after.run();
            return;
        }
        for (int i = 0; i < t.length(); i++) {
            JSONObject o = t.optJSONObject(i);
            if (o == null) continue;
            final float x = (float) o.optDouble("x"), y = (float) o.optDouble("y");
            handler.postDelayed(() -> nativeTap(x, y), i * 450L);
        }
        handler.postDelayed(after, t.length() * 450L + 100L);
    }

    void nativeTap(float x, float y) {
        long t = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        web.dispatchTouchEvent(down);
        down.recycle();
        handler.postDelayed(() -> {
            long t2 = SystemClock.uptimeMillis();
            MotionEvent up = MotionEvent.obtain(t, t2, MotionEvent.ACTION_UP, x, y, 0);
            up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            web.dispatchTouchEvent(up);
            up.recycle();
        }, 70);
    }

    String readAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_FILE && filePathCb != null) {
            filePathCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
            filePathCb = null;
            return;
        }
        super.onActivityResult(req, res, data);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else if (addrRow.getVisibility() == View.GONE) {
            addrRow.setVisibility(View.VISIBLE);   // nothing to go back to: bring the search bar back
            autoHidden = false;
        } else super.onBackPressed();
    }
}
