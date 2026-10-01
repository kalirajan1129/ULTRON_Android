package com.ultron.assistant;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.*;

public class ChennaiOneAccessibilityService extends AccessibilityService {
    private static volatile String pendingCommand;
    private static volatile long pendingAt;

    public static void setPendingCommand(String cmd) {
        pendingCommand = cmd;
        pendingAt = System.currentTimeMillis();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (pendingCommand == null || System.currentTimeMillis() - pendingAt > 120000) return;
        if (!"in.mobility.cumta".contentEquals(event.getPackageName())) return;
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        // Conservative automation only. It tries to enter source/destination if edit fields are exposed.
        // It deliberately never presses Pay/UPI/OTP/Confirm buttons.
        String cmd = pendingCommand;
        String[] parts = parseFromTo(cmd);
        if (parts != null) {
            List<AccessibilityNodeInfo> edits = new ArrayList<>();
            collectEditable(root, edits);
            if (edits.size() >= 2) {
                setText(edits.get(0), parts[0]);
                setText(edits.get(1), parts[1]);
                pendingCommand = null;
            }
        }
    }

    private static String[] parseFromTo(String s) {
        String lower = s.toLowerCase(Locale.ROOT);
        int ix = lower.indexOf(" to ");
        if (ix < 1) return null;
        String a = lower.substring(0, ix).replaceAll(".*?(ticket|book|from)", "").trim();
        String b = lower.substring(ix + 4).replaceAll("(ticket|book|pannu|பண்ணு|டிக்கெட்|புக்).*", "").trim();
        return (a.isEmpty() || b.isEmpty()) ? null : new String[]{a,b};
    }

    private static void collectEditable(AccessibilityNodeInfo n, List<AccessibilityNodeInfo> out) {
        if (n == null) return;
        if (n.isEditable()) out.add(n);
        for (int i=0; i<n.getChildCount(); i++) collectEditable(n.getChild(i), out);
    }

    private static void setText(AccessibilityNodeInfo n, String text) {
        Bundle b = new Bundle();
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
    }

    @Override public void onInterrupt() {}
}
