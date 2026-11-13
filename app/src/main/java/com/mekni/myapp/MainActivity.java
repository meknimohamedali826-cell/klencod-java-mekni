package com.example.myapp; // غيّر هذا السطر ليطابق مشروعك

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

public class MainActivity extends Activity {

    private static final String DB_URL = "https://mekni-43c5d-default-rtdb.firebaseio.com";

    private final String[] NAMES = {"Home", "Likes", "Add", "Profile", "Alerts"};
    private final String[] ICONS = {"\u2302", "\u2665", "\uFF0B", "\u263A", "\uD83D\uDD14"};

    private FrameLayout root, contentHost, bar;
    private FrameLayout.LayoutParams hostLp;
    private LinearLayout plainScreen, chatScreen, profileScreen, msgList;
    private ScrollView scroll;
    private TextView bigIcon, bigTitle, statusView;
    private EditText input, nameInput;
    private View highlight;
    private TextView[] icons = new TextView[5];
    private TextView[] labels = new TextView[5];
    private int tabW, current = 0;
    private boolean kbOpen = false;
    private String myName, lastSig = "";
    private SharedPreferences sp;
    private final Handler handler = new Handler();

    private final Runnable poller = new Runnable() {
        @Override
        public void run() {
            if (current == 0) refresh();
            handler.postDelayed(this, 2500);
        }
    };

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private void addBlob(int color, int sizeDp, int gravity, int l, int t, int r, int b) {
        View v = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        v.setBackground(g);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp), gravity);
        lp.setMargins(dp(l), dp(t), dp(r), dp(b));
        root.addView(v, lp);
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        return t;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        sp = getSharedPreferences("chat", MODE_PRIVATE);
        myName = sp.getString("name", "User" + (100 + (int) (Math.random() * 900)));

        root = new FrameLayout(this);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        root.setBackgroundColor(0xFF1A120D);

        addBlob(0xE6FF7A59, 170, Gravity.TOP | Gravity.LEFT, -50, 90, 0, 0);
        addBlob(0xE6FFC857, 170, Gravity.TOP | Gravity.RIGHT, 0, 280, -60, 0);
        addBlob(0xE62EC4B6, 150, Gravity.BOTTOM | Gravity.LEFT, 30, 0, 0, 150);

        contentHost = new FrameLayout(this);
        hostLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        hostLp.bottomMargin = dp(110);
        root.addView(contentHost, hostLp);

        buildPlainScreen();
        buildChatScreen();
        buildProfileScreen();
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
                    bar.setVisibility(open ? View.GONE : View.VISIBLE);
                    hostLp.bottomMargin = open ? 0 : dp(110);
                    contentHost.setLayoutParams(hostLp);
                }
            }
        });

        setContentView(root);
        select(0);
    }

    private void buildPlainScreen() {
        plainScreen = new LinearLayout(this);
        plainScreen.setOrientation(LinearLayout.VERTICAL);
        plainScreen.setGravity(Gravity.CENTER);
        bigIcon = text("", 64, Color.WHITE);
        bigIcon.setGravity(Gravity.CENTER);
        bigTitle = text("", 28, Color.WHITE);
        bigTitle.setGravity(Gravity.CENTER);
        plainScreen.addView(bigIcon);
        plainScreen.addView(bigTitle);
        contentHost.addView(plainScreen, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildChatScreen() {
        chatScreen = new LinearLayout(this);
        chatScreen.setOrientation(LinearLayout.VERTICAL);
        chatScreen.setPadding(dp(12), dp(40), dp(12), dp(8));

        TextView title = text("Friends chat", 22, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        chatScreen.addView(title);

        statusView = text("", 12, 0xFFFFDDAA);
        chatScreen.addView(statusView);

        scroll = new ScrollView(this);
        msgList = new LinearLayout(this);
        msgList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(msgList, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        chatScreen.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, 0);

        input = new EditText(this);
        input.setHint("Message");
        input.setHintTextColor(0x99FFFFFF);
        input.setTextColor(Color.WHITE);
        input.setSingleLine(true);
        input.setBackground(round(0x66000000, 24));
        input.setPadding(dp(16), dp(10), dp(16), dp(10));
        row.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView send = text("Send", 15, 0xFF0B141A);
        send.setTypeface(null, Typeface.BOLD);
        send.setBackground(round(0xFF25D366, 24));
        send.setPadding(dp(18), dp(12), dp(18), dp(12));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = dp(8);
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendMessage();
            }
        });
        row.addView(send, slp);

        chatScreen.addView(row);
        contentHost.addView(chatScreen, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildProfileScreen() {
        profileScreen = new LinearLayout(this);
        profileScreen.setOrientation(LinearLayout.VERTICAL);
        profileScreen.setGravity(Gravity.CENTER);
        profileScreen.setPadding(dp(32), 0, dp(32), 0);

        TextView t = text("Profile", 28, Color.WHITE);
        t.setGravity(Gravity.CENTER);
        profileScreen.addView(t);

        nameInput = new EditText(this);
        nameInput.setText(myName);
        nameInput.setTextColor(Color.WHITE);
        nameInput.setSingleLine(true);
        nameInput.setGravity(Gravity.CENTER);
        nameInput.setBackground(round(0x66000000, 16));
        nameInput.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = dp(16);
        profileScreen.addView(nameInput, nlp);

        TextView save = text("Save name", 15, 0xFF0B141A);
        save.setTypeface(null, Typeface.BOLD);
        save.setGravity(Gravity.CENTER);
        save.setBackground(round(0xFF25D366, 16));
        save.setPadding(dp(18), dp(12), dp(18), dp(12));
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.topMargin = dp(12);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String n = nameInput.getText().toString().trim();
                if (n.length() > 0) {
                    myName = n;
                    sp.edit().putString("name", n).apply();
                    Toast.makeText(MainActivity.this, "Saved", Toast.LENGTH_SHORT).show();
                }
            }
        });
        profileScreen.addView(save, vlp);

        contentHost.addView(profileScreen, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void buildBar() {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int barW = screenW - dp(32);
        int pad = dp(8);
        tabW = (barW - 2 * pad) / 5;

        bar = new FrameLayout(this);
        GradientDrawable barBg = round(0x4DFFBE78, 36);
        barBg.setStroke(dp(1), 0x66FFFFFF);
        bar.setBackground(barBg);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                barW, dp(72), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        blp.bottomMargin = dp(28);
        root.addView(bar, blp);

        highlight = new View(this);
        GradientDrawable hBg = round(0x38FFFFFF, 28);
        hBg.setStroke(dp(1), 0x80FFFFFF);
        highlight.setBackground(hBg);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(tabW, dp(56), Gravity.CENTER_VERTICAL);
        hlp.leftMargin = pad;
        bar.addView(highlight, hlp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(pad, 0, pad, 0);
        bar.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        for (int i = 0; i < 5; i++) {
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
                    select(index);
                }
            });
            row.addView(tab, new LinearLayout.LayoutParams(tabW, ViewGroup.LayoutParams.MATCH_PARENT));
        }
    }

    private void select(final int i) {
        current = i;
        highlight.animate()
                .translationX(i * tabW)
                .setDuration(350)
                .setInterpolator(new OvershootInterpolator(1.2f))
                .start();

        for (int k = 0; k < 5; k++) {
            boolean on = (k == i);
            int c = on ? Color.WHITE : 0xB3FFFFFF;
            icons[k].setTextColor(c);
            labels[k].setTextColor(c);
            labels[k].setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
        }

        chatScreen.setVisibility(i == 0 ? View.VISIBLE : View.GONE);
        profileScreen.setVisibility(i == 3 ? View.VISIBLE : View.GONE);
        plainScreen.setVisibility((i == 1 || i == 2 || i == 4) ? View.VISIBLE : View.GONE);
        bigIcon.setText(ICONS[i]);
        bigTitle.setText(NAMES[i]);

        if (i == 0) refresh();

        icons[i].animate().scaleX(0.8f).scaleY(0.8f).setDuration(90)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        icons[i].animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                    }
                }).start();
    }

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

    private void refresh() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final String r = http("GET",
                            DB_URL + "/messages.json?orderBy=%22$key%22&limitToLast=50", null);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("");
                            showMessages(r);
                        }
                    });
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("Offline: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void showMessages(String r) {
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
            if (sig.equals(lastSig)) return;
            lastSig = sig;

            msgList.removeAllViews();
            SimpleDateFormat fmt = new SimpleDateFormat("HH:mm");
            for (Map.Entry<String, JSONObject> e : map.entrySet()) {
                JSONObject m = e.getValue();
                String user = m.optString("user", "?");
                boolean mine = user.equals(myName);

                LinearLayout bubble = new LinearLayout(this);
                bubble.setOrientation(LinearLayout.VERTICAL);
                bubble.setPadding(dp(12), dp(8), dp(12), dp(6));
                bubble.setBackground(round(mine ? 0xE6005C4B : 0xE6202C33, 14));

                if (!mine) {
                    TextView u = text(user, 12, 0xFF25D366);
                    u.setTypeface(null, Typeface.BOLD);
                    bubble.addView(u);
                }
                TextView body = text(m.optString("text", ""), 16, 0xFFE9EDEF);
                body.setMaxWidth(dp(260));
                bubble.addView(body);
                TextView time = text(fmt.format(new Date(m.optLong("ts", 0))), 11, 0xFF8696A0);
                time.setGravity(Gravity.RIGHT);
                bubble.addView(time);

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.gravity = mine ? Gravity.RIGHT : Gravity.LEFT;
                lp.setMargins(0, dp(4), 0, dp(4));
                msgList.addView(bubble, lp);
            }
            scroll.post(new Runnable() {
                @Override
                public void run() {
                    scroll.fullScroll(View.FOCUS_DOWN);
                }
            });
        } catch (Exception ex) {
            statusView.setText("Data error: " + ex.getMessage());
        }
    }

    private void sendMessage() {
        final String t = input.getText().toString().trim();
        if (t.length() == 0) return;
        input.setText("");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject j = new JSONObject();
                    j.put("user", myName);
                    j.put("text", t);
                    j.put("ts", System.currentTimeMillis());
                    http("POST", DB_URL + "/messages.json", j.toString());
                    refresh();
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusView.setText("Send failed: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

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