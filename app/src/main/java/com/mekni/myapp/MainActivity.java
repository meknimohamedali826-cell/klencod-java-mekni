 package com.mekni.myapp; // غيّر هذا السطر ليطابق مشروعك

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private final String[] NAMES = {"Home", "Likes", "Add", "Profile", "Alerts"};
    private final String[] ICONS = {"\u2302", "\u2665", "\uFF0B", "\u263A", "\uD83D\uDD14"};

    private TextView bigIcon, bigTitle;
    private View highlight;
    private TextView[] icons = new TextView[5];
    private TextView[] labels = new TextView[5];
    private int tabW;

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void addBlob(FrameLayout root, int color, int sizeDp, int gravity, int l, int t, int r, int b) {
        View v = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        v.setBackground(g);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp), gravity);
        lp.setMargins(dp(l), dp(t), dp(r), dp(b));
        root.addView(v, lp);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF1A120D);

        addBlob(root, 0xE6FF7A59, 170, Gravity.TOP | Gravity.START, -50, 90, 0, 0);
        addBlob(root, 0xE6FFC857, 170, Gravity.TOP | Gravity.END, 0, 280, -60, 0);
        addBlob(root, 0xE62EC4B6, 150, Gravity.BOTTOM | Gravity.START, 30, 0, 0, 150);

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        clp.bottomMargin = dp(110);

        bigIcon = new TextView(this);
        bigIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 64);
        bigIcon.setTextColor(Color.WHITE);
        bigIcon.setGravity(Gravity.CENTER);

        bigTitle = new TextView(this);
        bigTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        bigTitle.setTextColor(Color.WHITE);
        bigTitle.setGravity(Gravity.CENTER);

        center.addView(bigIcon);
        center.addView(bigTitle);
        root.addView(center, clp);

        int screenW = getResources().getDisplayMetrics().widthPixels;
        int barW = screenW - dp(32);
        int pad = dp(8);
        tabW = (barW - 2 * pad) / 5;

        FrameLayout bar = new FrameLayout(this);
        GradientDrawable barBg = new GradientDrawable();
        barBg.setColor(0x4DFFBE78);
        barBg.setCornerRadius(dp(36));
        barBg.setStroke(dp(1), 0x66FFFFFF);
        bar.setBackground(barBg);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                barW, dp(72), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        blp.bottomMargin = dp(28);
        root.addView(bar, blp);

        highlight = new View(this);
        GradientDrawable hBg = new GradientDrawable();
        hBg.setColor(0x38FFFFFF);
        hBg.setCornerRadius(dp(28));
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

            icons[i] = new TextView(this);
            icons[i].setText(ICONS[i]);
            icons[i].setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            icons[i].setGravity(Gravity.CENTER);

            labels[i] = new TextView(this);
            labels[i].setText(NAMES[i]);
            labels[i].setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
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

        setContentView(root);
        select(0);
        highlight.setTranslationX(0);
    }

    private void select(final int i) {
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
            labels[k].setTypeface(null, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }

        bigIcon.setText(ICONS[i]);
        bigTitle.setText(NAMES[i]);

        icons[i].animate().scaleX(0.8f).scaleY(0.8f).setDuration(90)
                .withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        icons[i].animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                    }
                }).start();
    }
}