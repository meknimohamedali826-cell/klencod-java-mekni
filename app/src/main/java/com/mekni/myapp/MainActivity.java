package com.mekni.myapp; // غيّر هذا السطر ليطابق مشروعك

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class MainActivity extends Activity {

    private static final String DB_URL = "https://mekni-43c5d-default-rtdb.firebaseio.com";

    // ===== الألوان: غيّرها كما تريد =====
    private static final int BG = 0xFF0F0B1A;
    private static final int BAR = 0xFF1A1330;
    private static final int ACCENT = 0xFF8B5CF6;
    private static final int BUBBLE_ME = 0xFF5B3FD0;
    private static final int BUBBLE_OTHER = 0xFF241C3D;
    private static final int TEXT = 0xFFEDE9FE;
    private static final int MUTED = 0xFF9A93B8;
    private static final int[] AV = {0xFF8B5CF6, 0xFFEC4899, 0xFF06B6D4, 0xFFF59E0B, 0xFF10B981};
    // =====================================

    private final String[] NAMES = {"Chats", "Status", "Profile"};
    private final String[] ICONS = {"\uD83D\uDCAC", "\u2605", "\u263A"};

    private FrameLayout root, contentHost, bar, chatsView, convoAvatarHolder, profileAvatarHolder;
    private FrameLayout.LayoutParams hostLp;
    private LinearLayout loginView, statusView, profileView, convoView, chatsList, convoList;
    private ScrollView chatsScroll, convoScroll;
    private TextView convoTitle, loginError, profileName;
    private EditText input, loginBox;
    private View highlight;
    private TextView[] icons = new TextView[3];
    private TextView[] labels = new TextView[3];
    private int tabW, screen = 4;
    private boolean kbOpen = false;
    private String me, peer, sigChats = "", sigConvo = "";
    private SharedPreferences prefs;
    private final Handler handler = new Handler();

    private interface Callback {
        void done(String res, Exception err);
    }

    private final Runnable poller = new Runnable() {
        @Override
        public void run() {
            if (screen == 0) refreshChats();
            else if (screen == 3) refreshConvo();
            handler.postDelayed(this, 2000);
        }
    };

    // ---------- أدوات ----------

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private GradientDrawable oval(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTextColor(color);
        return t;
    }

    private LinearLayout.LayoutParams matchW(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        return lp;
    }

    private FrameLayout.LayoutParams full() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private String clean(String s) {
        s = s.trim().toLowerCase().replaceAll("[^\\p{L}\\p{N}_]", "");
        if (s.length() > 20) s = s.substring(0, 20);
        return s;
    }

    private String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private String chatId() {
        return me.compareTo(peer) < 0 ? me + "__" + peer : peer + "__" + me;
    }

    private String fmtTime(long ts) {
        Calendar a = Calendar.getInstance();
        Calendar b = Calendar.getInstance();
        b.setTime(new Date(ts));
        boolean same = a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
        return new SimpleDateFormat(same ? "HH:mm" : "dd/MM").format(new Date(ts));
    }

    private TextView avatar(String name, int sizeDp) {
        String letter = name.length() > 0 ? name.substring(0, 1).toUpperCase() : "?";
        TextView a = text(letter, (int) (sizeDp * 0.4), Color.WHITE);
        a.setTypeface(null, Typeface.BOLD);
        a.setGravity(Gravity.CENTER);
        a.setBackground(oval(AV[(name.hashCode() & 0x7fffffff) % AV.length]));
        return a;
    }

    // ---------- الشبكة ----------

    private String http(String method, String u, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            OutputStream os = c.getOutputStream();
            os.write(body.getBytes("UTF-8"));
            os.close();
        }
        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        if (code >= 400) throw new Exception("HTTP " + code);
        return sb.toString();
    }

    private void net(final String method, final String path, final String body, final Callback cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String res = null;
                Exception err = null;
                try {
                    res = http(method, DB_URL + path, body);
                } catch (Exception e) {
                    err = e;
                }
                final String r = res;
                final Exception er = err;
                if (cb != null) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            cb.done(r, er);
                        }
                    });
                }
            }
        }).start();
    }

    // ---------- بدء التطبيق ----------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        prefs = getSharedPreferences("chat", MODE_PRIVATE);
        me = prefs.getString("me", null);

        root = new FrameLayout(this);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        root.setBackgroundColor(BG);

        contentHost = new FrameLayout(this);
        hostLp = full();
        root.addView(contentHost, hostLp);

        buildLogin();
        buildChats();
        buildStatus();
        buildProfile();
        buildConvo();
        buildBar();

        root.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                Rect r = new Rect();
                root.getWindowVisibleDisplayFrame(r);
                int diff = root.getRootView().getHeight() - r.bottom;
                boolean open = diff > dp(150);
                if (open != kbOpen) {
                    kbOpen = open;
                    applyChrome();
                }
            }
        });

        setContentView(root);
        showScreen(me == null ? 4 : 0);
    }

    private void applyChrome() {
        boolean nav = screen <= 2 && !kbOpen;
        bar.setVisibility(nav ? View.VISIBLE : View.GONE);
        hostLp.bottomMargin = (screen <= 2 && !kbOpen) ? dp(110) : 0;
        contentHost.setLayoutParams(hostLp);
    }

    private void showScreen(int s) {
        screen = s;
        loginView.setVisibility(s == 4 ? View.VISIBLE : View.GONE);
        chatsView.setVisibility(s == 0 ? View.VISIBLE : View.GONE);
        statusView.setVisibility(s == 1 ? View.VISIBLE : View.GONE);
        profileView.setVisibility(s == 2 ? View.VISIBLE : View.GONE);
        convoView.setVisibility(s == 3 ? View.VISIBLE : View.GONE);

        if (s <= 2) moveHighlight(s);
        if (s == 2 && me != null) {
            profileName.setText(me);
            profileAvatarHolder.removeAllViews();
            profileAvatarHolder.addView(avatar(me, 96), new FrameLayout.LayoutParams(dp(96), dp(96)));
        }
        applyChrome();
        if (s == 0) {
            sigChats = "";
            refreshChats();
        }
    }

    @Override
    public void onBackPressed() {
        if (screen == 3) showScreen(0);
        else super.onBackPressed();
    }

    // ---------- شاشة الدخول ----------

    private void buildLogin() {
        loginView = new LinearLayout(this);
        loginView.setOrientation(LinearLayout.VERTICAL);
        loginView.setGravity(Gravity.CENTER);
        loginView.setPadding(dp(32), 0, dp(32), 0);

        TextView logo = text("Mekni", 40, ACCENT);
        logo.setTypeface(null, Typeface.BOLD);
        logo.setGravity(Gravity.CENTER);
        loginView.addView(logo, matchW(0));

        TextView sub = text("Choose a username to start", 15, MUTED);
        sub.setGravity(Gravity.CENTER);
        loginView.addView(sub, matchW(8));

        loginBox = new EditText(this);
        loginBox.setHint("username");
        loginBox.setHintTextColor(MUTED);
        loginBox.setTextColor(TEXT);
        loginBox.setSingleLine(true);
        loginBox.setGravity(Gravity.CENTER);
        loginBox.setBackground(round(BAR, 16));
        loginBox.setPadding(dp(16), dp(12), dp(16), dp(12));
        loginView.addView(loginBox, matchW(28));

        loginError = text("", 13, 0xFFFF8A80);
        loginError.setGravity(Gravity.CENTER);
        loginView.addView(loginError, matchW(8));

        TextView go = text("Continue", 16, Color.WHITE);
        go.setTypeface(null, Typeface.BOLD);
        go.setGravity(Gravity.CENTER);
        go.setBackground(round(ACCENT, 16));
        go.setPadding(dp(18), dp(14), dp(18), dp(14));
        go.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String n = clean(loginBox.getText().toString());
                if (n.length() < 2) {
                    loginError.setText("At least 2 letters or digits");
                    return;
                }
                loginError.setText("");
                String body = "{\"name\":\"" + n + "\",\"ts\":" + System.currentTimeMillis() + "}";
                net("PUT", "/users/" + enc(n) + ".json", body, new Callback() {
                    @Override
                    public void done(String res, Exception err) {
                        if (err != null) {
                            loginError.setText("Connection error: " + err.getMessage());
                        } else {
                            me = n;
                            prefs.edit().putString("me", n).apply();
                            showScreen(0);
                        }
                    }
                });
            }
        });
        loginView.addView(go, matchW(12));

        contentHost.addView(loginView, full());
    }

    // ---------- قائمة المحادثات ----------

    private void buildChats() {
        chatsView = new FrameLayout(this);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView title = text("Mekni", 26, TEXT);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(dp(16), dp(40), dp(16), dp(12));
        col.addView(title);

        chatsScroll = new ScrollView(this);
        chatsList = new LinearLayout(this);
        chatsList.setOrientation(LinearLayout.VERTICAL);
        chatsScroll.addView(chatsList, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        col.addView(chatsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        chatsView.addView(col, full());

        TextView fab = text("+", 30, Color.WHITE);
        fab.setGravity(Gravity.CENTER);
        fab.setBackground(oval(ACCENT));
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                newChatDialog();
            }
        });
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(dp(58), dp(58),
                Gravity.BOTTOM | Gravity.RIGHT);
        flp.setMargins(0, 0, dp(20), dp(16));
        chatsView.addView(fab, flp);

        contentHost.addView(chatsView, full());
    }

    private void newChatDialog() {
        final EditText box = new EditText(this);
        box.setHint("username");
        box.setSingleLine(true);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(dp(20), dp(8), dp(20), 0);
        wrap.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("New chat")
                .setMessage("Friend's username")
                .setView(wrap)
                .setPositiveButton("Start", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        final String f = clean(box.getText().toString());
                        if (f.length() < 2 || f.equals(me)) {
                            Toast.makeText(MainActivity.this, "Invalid username", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        net("GET", "/users/" + enc(f) + ".json", null, new Callback() {
                            @Override
                            public void done(String res, Exception err) {
                                if (err != null) {
                                    Toast.makeText(MainActivity.this, "Connection error", Toast.LENGTH_SHORT).show();
                                } else if (res == null || res.trim().equals("null")) {
                                    Toast.makeText(MainActivity.this, "User not found", Toast.LENGTH_SHORT).show();
                                } else {
                                    openConvo(f);
                                }
                            }
                        });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void refreshChats() {
        if (me == null) return;
        net("GET", "/userChats/" + enc(me) + ".json", null, new Callback() {
            @Override
            public void done(String res, Exception err) {
                if (err == null && screen == 0) showChats(res);
            }
        });
    }

    private void showChats(String r) {
        try {
            ArrayList<JSONObject> list = new ArrayList<JSONObject>();
            if (r != null && r.length() > 4 && !r.equals("null")) {
                JSONObject o = new JSONObject(r);
                Iterator<String> it = o.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    JSONObject c = o.getJSONObject(k);
                    c.put("peer", k);
                    list.add(c);
                }
            }
            Collections.sort(list, new Comparator<JSONObject>() {
                @Override
                public int compare(JSONObject a, JSONObject b) {
                    long x = a.optLong("ts"), y = b.optLong("ts");
                    return x == y ? 0 : (x < y ? 1 : -1);
                }
            });

            StringBuilder sig = new StringBuilder();
            for (JSONObject c : list) sig.append(c.optString("peer")).append(c.optLong("ts"));
            if (sig.toString().equals(sigChats)) return;
            sigChats = sig.toString();

            chatsList.removeAllViews();
            if (list.isEmpty()) {
                TextView empty = text("No chats yet.\nTap + to start one.", 15, MUTED);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(dp(24), dp(80), dp(24), 0);
                chatsList.addView(empty, matchW(0));
                return;
            }

            for (JSONObject c : list) {
                final String p = c.optString("peer");
                String last = c.optString("last", "");
                if (c.optString("from", "").equals(me)) last = "You: " + last;

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), dp(10), dp(16), dp(10));

                row.addView(avatar(p, 50), new LinearLayout.LayoutParams(dp(50), dp(50)));

                LinearLayout mid = new LinearLayout(this);
                mid.setOrientation(LinearLayout.VERTICAL);
                TextView n = text(p, 16, TEXT);
                n.setTypeface(null, Typeface.BOLD);
                TextView l = text(last, 14, MUTED);
                l.setSingleLine(true);
                l.setEllipsize(TextUtils.TruncateAt.END);
                mid.addView(n);
                mid.addView(l);
                LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                mlp.leftMargin = dp(14);
                mlp.rightMargin = dp(8);
                row.addView(mid, mlp);

                row.addView(text(fmtTime(c.optLong("ts")), 12, MUTED));

                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        openConvo(p);
                    }
                });
                chatsList.addView(row);
            }
        } catch (Exception ex) {
            Toast.makeText(this, "Data error", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- شاشة المحادثة ----------

    private void buildConvo() {
        convoView = new LinearLayout(this);
        convoView.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(BAR);
        header.setPadding(dp(8), dp(36), dp(12), dp(10));

        TextView back = text("\u2190", 26, TEXT);
        back.setPadding(dp(10), 0, dp(14), 0);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showScreen(0);
            }
        });
        header.addView(back);

        convoAvatarHolder = new FrameLayout(this);
        header.addView(convoAvatarHolder, new LinearLayout.LayoutParams(dp(40), dp(40)));

        convoTitle = text("", 18, TEXT);
        convoTitle.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = dp(12);
        header.addView(convoTitle, tlp);

        convoView.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        convoScroll = new ScrollView(this);
        convoList = new LinearLayout(this);
        convoList.setOrientation(LinearLayout.VERTICAL);
        convoList.setPadding(dp(10), dp(8), dp(10), dp(8));
        convoScroll.addView(convoList, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        convoView.addView(convoScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(8), dp(8), dp(8));

        input = new EditText(this);
        input.setHint("Message");
        input.setHintTextColor(MUTED);
        input.setTextColor(TEXT);
        input.setSingleLine(true);
        input.setBackground(round(BAR, 24));
        input.setPadding(dp(18), dp(10), dp(18), dp(10));
        row.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView send = text("\u27A4", 20, Color.WHITE);
        send.setGravity(Gravity.CENTER);
        send.setBackground(oval(ACCENT));
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMsg();
            }
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(48), dp(48));
        slp.leftMargin = dp(8);
        row.addView(send, slp);

        convoView.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        contentHost.addView(convoView, full());
    }

    private void openConvo(String p) {
        peer = p;
        sigConvo = "";
        convoTitle.setText(p);
        convoAvatarHolder.removeAllViews();
        convoAvatarHolder.addView(avatar(p, 40), new FrameLayout.LayoutParams(dp(40), dp(40)));
        convoList.removeAllViews();
        showScreen(3);
        refreshConvo();
    }

    private void refreshConvo() {
        if (me == null || peer == null) return;
        final String p = peer;
        net("GET", "/chats/" + enc(chatId()) + "/messages.json?orderBy=%22$key%22&limitToLast=100",
                null, new Callback() {
                    @Override
                    public void done(String res, Exception err) {
                        if (err == null && screen == 3 && p.equals(peer)) showConvo(res);
                    }
                });
    }

    private void showConvo(String r) {
        try {
            TreeMap<String, JSONObject> map = new TreeMap<String, JSONObject>();
            if (r != null && r.length() > 4 && !r.equals("null")) {
                JSONObject o = new JSONObject(r);
                Iterator<String> it = o.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    map.put(k, o.getJSONObject(k));
                }
            }
            String sig = map.size() + (map.isEmpty() ? "" : map.lastKey());
            if (sig.equals(sigConvo)) return;
            sigConvo = sig;

            convoList.removeAllViews();
            for (Map.Entry<String, JSONObject> e : map.entrySet()) {
                JSONObject m = e.getValue();
                boolean mine = m.optString("from", "").equals(me);

                LinearLayout bubble = new LinearLayout(this);
                bubble.setOrientation(LinearLayout.VERTICAL);
                bubble.setPadding(dp(12), dp(8), dp(12), dp(6));
                bubble.setBackground(round(mine ? BUBBLE_ME : BUBBLE_OTHER, 14));

                TextView body = text(m.optString("text", ""), 16, TEXT);
                body.setMaxWidth(dp(270));
                bubble.addView(body);

                TextView time = text(fmtTime(m.optLong("ts", 0)), 11, MUTED);
                time.setGravity(Gravity.RIGHT);
                bubble.addView(time);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.gravity = mine ? Gravity.RIGHT : Gravity.LEFT;
                lp.setMargins(0, dp(3), 0, dp(3));
                convoList.addView(bubble, lp);
            }
            convoScroll.post(new Runnable() {
                @Override
                public void run() {
                    convoScroll.fullScroll(View.FOCUS_DOWN);
                }
            });
        } catch (Exception ex) {
            Toast.makeText(this, "Data error", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendMsg() {
        final String t = input.getText().toString().trim();
        if (t.length() == 0 || me == null || peer == null) return;
        input.setText("");
        try {
            final long now = System.currentTimeMillis();
            JSONObject j = new JSONObject();
            j.put("from", me);
            j.put("text", t);
            j.put("ts", now);

            JSONObject last = new JSONObject();
            last.put("last", t);
            last.put("ts", now);
            last.put("from", me);

            net("POST", "/chats/" + enc(chatId()) + "/messages.json", j.toString(), new Callback() {
                @Override
                public void done(String res, Exception err) {
                    if (err != null) {
                        Toast.makeText(MainActivity.this, "Send failed: " + err.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        refreshConvo();
                    }
                }
            });
            net("PUT", "/userChats/" + enc(me) + "/" + enc(peer) + ".json", last.toString(), null);
            net("PUT", "/userChats/" + enc(peer) + "/" + enc(me) + ".json", last.toString(), null);
        } catch (Exception e) {
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- Status و Profile ----------

    private void buildStatus() {
        statusView = new LinearLayout(this);
        statusView.setOrientation(LinearLayout.VERTICAL);
        statusView.setGravity(Gravity.CENTER);
        TextView i = text("\u2605", 56, ACCENT);
        i.setGravity(Gravity.CENTER);
        TextView t = text("Status\nComing soon", 18, MUTED);
        t.setGravity(Gravity.CENTER);
        statusView.addView(i);
        statusView.addView(t);
        contentHost.addView(statusView, full());
    }

    private void buildProfile() {
        profileView = new LinearLayout(this);
        profileView.setOrientation(LinearLayout.VERTICAL);
        profileView.setGravity(Gravity.CENTER);
        profileView.setPadding(dp(32), 0, dp(32), 0);

        profileAvatarHolder = new FrameLayout(this);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(96), dp(96));
        alp.gravity = Gravity.CENTER_HORIZONTAL;
        profileView.addView(profileAvatarHolder, alp);

        profileName = text("", 24, TEXT);
        profileName.setTypeface(null, Typeface.BOLD);
        profileName.setGravity(Gravity.CENTER);
        profileView.addView(profileName, matchW(16));

        TextView out = text("Log out", 15, Color.WHITE);
        out.setTypeface(null, Typeface.BOLD);
        out.setGravity(Gravity.CENTER);
        out.setBackground(round(BAR, 16));
        out.setPadding(dp(18), dp(12), dp(18), dp(12));
        out.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit().remove("me").apply();
                me = null;
                peer = null;
                showScreen(4);
            }
        });
        profileView.addView(out, matchW(24));

        contentHost.addView(profileView, full());
    }

    // ---------- الشريط الزجاجي ----------

    private void buildBar() {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int barW = screenW - dp(32);
        int pad = dp(8);
        tabW = (barW - 2 * pad) / 3;

        bar = new FrameLayout(this);
        GradientDrawable barBg = round(0xE61A1330, 36);
        barBg.setStroke(dp(1), 0x55FFFFFF);
        bar.setBackground(barBg);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                barW, dp(72), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        blp.bottomMargin = dp(28);
        root.addView(bar, blp);

        highlight = new View(this);
        GradientDrawable hBg = round(0x40FFFFFF, 28);
        hBg.setStroke(dp(1), 0x80FFFFFF);
        highlight.setBackground(hBg);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(tabW, dp(56), Gravity.CENTER_VERTICAL);
        hlp.leftMargin = pad;
        bar.addView(highlight, hlp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(pad, 0, pad, 0);
        bar.addView(row, full());

        for (int i = 0; i < 3; i++) {
            final int index = i;
            LinearLayout tab = new LinearLayout(this);
            tab.setOrientation(LinearLayout.VERTICAL);
            tab.setGravity(Gravity.CENTER);

            icons[i] = text(ICONS[i], 20, Color.WHITE);
            icons[i].setGravity(Gravity.CENTER);
            labels[i] = text(NAMES[i], 11, Color.WHITE);
            labels[i].setGravity(Gravity.CENTER);

            tab.addView(icons[i]);
            tab.addView(labels[i]);
            tab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showScreen(index);
                }
            });
            row.addView(tab, new LinearLayout.LayoutParams(tabW, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    private void moveHighlight(final int i) {
        highlight.animate()
                .translationX(i * tabW)
                .setDuration(350)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();
        for (int k = 0; k < 3; k++) {
            boolean on = (k == i);
            int c = on ? Color.WHITE : 0xB3FFFFFF;
            icons[k].setTextColor(c);
            labels[k].setTextColor(c);
            labels[k].setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    // ---------- دورة الحياة ----------

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(poller);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(poller);
    }
}