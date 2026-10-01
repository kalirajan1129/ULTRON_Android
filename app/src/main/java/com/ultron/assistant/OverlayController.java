package com.ultron.assistant;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class OverlayController {
    private final Context context;
    private final WindowManager wm;
    private View view;

    public OverlayController(Context c) {
        context = c.getApplicationContext();
        wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void showListening() {
        hide();
        TextView orb = new TextView(context);
        orb.setText("ULTRON\nListening...");
        orb.setTextColor(Color.WHITE);
        orb.setGravity(Gravity.CENTER);
        orb.setTextSize(14f);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.rgb(125, 0, 0));
        bg.setStroke(5, Color.rgb(255, 45, 45));
        orb.setBackground(bg);
        orb.setElevation(18f);
        int size = dp(132);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;
        wm.addView(orb, lp);
        view = orb;
        orb.animate().scaleX(1.10f).scaleY(1.10f).alpha(0.82f).setDuration(550)
                .withEndAction(() -> orb.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(550).start()).start();
    }

    public void hide() {
        if (view != null) {
            try { wm.removeView(view); } catch (Exception ignored) {}
            view = null;
        }
    }

    private int dp(int d) {
        return (int) (d * context.getResources().getDisplayMetrics().density);
    }
}
