package com.ultron.assistant;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Two jobs:
 * 1) Global Volume-Up x3 privacy wake shortcut.
 * 2) Best-effort Chennai One form assistance up to, but never through, payment authorization.
 */
public class ChennaiOneAccessibilityService extends AccessibilityService {
    private static final String CHENNAI_ONE = "in.mobility.cumta";
    private static final long PENDING_TTL_MS = 180_000;
    private static final long TRIPLE_PRESS_WINDOW_MS = 1200;

    private static volatile Booking pending;
    private int volumeUpCount = 0;
    private long firstVolumeUpAt = 0L;
    private long lastAutomationActionAt = 0L;

    private static class Booking {
        final String source;
        final String destination;
        final String busNumber;
        final String busOtp;
        final long createdAt = System.currentTimeMillis();

        Booking(String source, String destination, String busNumber, String busOtp) {
            this.source = safe(source);
            this.destination = safe(destination);
            this.busNumber = safe(busNumber);
            this.busOtp = safe(busOtp);
        }
    }

    public static void setPendingBooking(String source, String destination, String busNumber, String busOtp) {
        pending = new Booking(source, destination, busNumber, busOtp);
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_VOLUME_UP ||
                event.getAction() != KeyEvent.ACTION_DOWN ||
                event.getRepeatCount() != 0) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (volumeUpCount == 0 || now - firstVolumeUpAt > TRIPLE_PRESS_WINDOW_MS) {
            volumeUpCount = 1;
            firstVolumeUpAt = now;
        } else {
            volumeUpCount++;
        }

        if (volumeUpCount >= 3) {
            volumeUpCount = 0;
            firstVolumeUpAt = 0L;
            wakeUltron();
        }
        // Do not consume the key: normal volume behavior remains available.
        return false;
    }

    private void wakeUltron() {
        Intent i = new Intent(this, UltronService.class);
        i.setAction(UltronService.ACTION_WAKE);
        try { startService(i); } catch (Exception ignored) {}
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        Booking b = pending;
        if (b == null) return;
        if (System.currentTimeMillis() - b.createdAt > PENDING_TTL_MS) {
            pending = null;
            return;
        }
        if (event.getPackageName() == null || !CHENNAI_ONE.contentEquals(event.getPackageName())) return;

        // Debounce noisy window-content events.
        long now = System.currentTimeMillis();
        if (now - lastAutomationActionAt < 450) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        // Never press anything when the visible screen looks like payment/UPI/bank authorization.
        if (screenLooksLikePayment(root)) {
            pending = null;
            return;
        }

        boolean changed = false;
        if (!b.busOtp.isEmpty()) changed |= fillMatchingEditable(root, b.busOtp, "bus otp", "otp", "code", "bus code", "ஓடிபி", "கோடு");
        if (!b.busNumber.isEmpty()) changed |= fillMatchingEditable(root, b.busNumber, "bus number", "bus no", "route number", "பஸ் நம்பர்");
        if (!b.source.isEmpty()) changed |= fillMatchingEditable(root, b.source, "source", "from", "boarding", "start", "pickup", "எங்கிருந்து");
        if (!b.destination.isEmpty()) changed |= fillMatchingEditable(root, b.destination, "destination", "to", "drop", "where to", "எங்கே", "இலக்கு");

        // If source/destination fields have no useful hints, cautiously use the first two empty text fields.
        if (!b.source.isEmpty() && !b.destination.isEmpty()) {
            List<AccessibilityNodeInfo> edits = new ArrayList<>();
            collectEditable(root, edits);
            if (edits.size() >= 2) {
                if (isEmpty(edits.get(0))) changed |= setText(edits.get(0), b.source);
                if (isEmpty(edits.get(1))) changed |= setText(edits.get(1), b.destination);
            }
        }

        // Tap an exact suggestion matching the requested destination/source when exposed.
        if (!b.source.isEmpty()) changed |= clickText(root, b.source, true);
        if (!b.destination.isEmpty()) changed |= clickText(root, b.destination, true);

        // Advance only through obviously non-payment navigation controls.
        // Payment/UPI/Pay/Bank/OTP verification buttons are excluded below.
        changed |= clickSafeAdvance(root);

        if (changed) lastAutomationActionAt = now;
    }

    private static boolean fillMatchingEditable(AccessibilityNodeInfo root, String value, String... labels) {
        List<AccessibilityNodeInfo> edits = new ArrayList<>();
        collectEditable(root, edits);
        for (AccessibilityNodeInfo n : edits) {
            String meta = nodeMeta(n);
            for (String label : labels) {
                if (meta.contains(label.toLowerCase(Locale.ROOT))) {
                    CharSequence current = n.getText();
                    if (current != null && value.equalsIgnoreCase(current.toString().trim())) return false;
                    return setText(n, value);
                }
            }
        }
        return false;
    }

    private static boolean clickSafeAdvance(AccessibilityNodeInfo root) {
        String[] allowed = {"search", "continue", "next", "proceed", "buy ticket", "book ticket", "get ticket", "confirm route", "select"};
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        collectAll(root, nodes);
        for (AccessibilityNodeInfo n : nodes) {
            if (!n.isClickable()) continue;
            String label = nodeLabel(n).toLowerCase(Locale.ROOT).trim();
            if (label.isEmpty()) continue;
            if (isSensitivePaymentLabel(label)) continue;
            for (String a : allowed) {
                if (label.equals(a) || label.contains(a)) {
                    return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
            }
        }
        return false;
    }

    private static boolean screenLooksLikePayment(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        collectAll(root, nodes);
        StringBuilder all = new StringBuilder();
        for (AccessibilityNodeInfo n : nodes) all.append(' ').append(nodeLabel(n));
        String s = all.toString().toLowerCase(Locale.ROOT);
        return s.contains("upi pin") || s.contains("payment method") || s.contains("choose payment") ||
                s.contains("pay ₹") || s.contains("pay rs") || s.contains("bank otp") ||
                s.contains("cvv") || s.contains("card number");
    }

    private static boolean isSensitivePaymentLabel(String s) {
        return s.contains("upi") || s.contains("payment") || s.startsWith("pay") ||
                s.contains("bank") || s.contains("cvv") || s.contains("card") ||
                s.contains("pin") || s.contains("verify otp");
    }

    private static boolean clickText(AccessibilityNodeInfo root, String wanted, boolean exactish) {
        if (wanted == null || wanted.trim().length() < 2) return false;
        String w = wanted.trim().toLowerCase(Locale.ROOT);
        List<AccessibilityNodeInfo> nodes = new ArrayList<>();
        collectAll(root, nodes);
        for (AccessibilityNodeInfo n : nodes) {
            String label = nodeLabel(n).toLowerCase(Locale.ROOT).trim();
            boolean match = exactish ? (label.equals(w) || label.startsWith(w + " ")) : label.contains(w);
            if (match && n.isClickable()) return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        return false;
    }

    private static void collectEditable(AccessibilityNodeInfo n, List<AccessibilityNodeInfo> out) {
        if (n == null) return;
        if (n.isEditable()) out.add(n);
        for (int i = 0; i < n.getChildCount(); i++) collectEditable(n.getChild(i), out);
    }

    private static void collectAll(AccessibilityNodeInfo n, List<AccessibilityNodeInfo> out) {
        if (n == null) return;
        out.add(n);
        for (int i = 0; i < n.getChildCount(); i++) collectAll(n.getChild(i), out);
    }

    private static boolean setText(AccessibilityNodeInfo n, String text) {
        Bundle b = new Bundle();
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
    }

    private static String nodeMeta(AccessibilityNodeInfo n) {
        StringBuilder s = new StringBuilder(nodeLabel(n));
        if (n.getHintText() != null) s.append(' ').append(n.getHintText());
        if (n.getViewIdResourceName() != null) s.append(' ').append(n.getViewIdResourceName());
        return s.toString().toLowerCase(Locale.ROOT);
    }

    private static String nodeLabel(AccessibilityNodeInfo n) {
        StringBuilder s = new StringBuilder();
        if (n.getText() != null) s.append(n.getText()).append(' ');
        if (n.getContentDescription() != null) s.append(n.getContentDescription()).append(' ');
        return s.toString().trim();
    }

    private static boolean isEmpty(AccessibilityNodeInfo n) {
        return n.getText() == null || n.getText().toString().trim().isEmpty();
    }

    private static String safe(String s) { return s == null ? "" : s.trim(); }

    @Override public void onInterrupt() {}
}
