package com.ultron.assistant;

import android.Manifest;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import java.net.URLEncoder;
import java.util.*;
import java.util.function.Consumer;

public class CommandRouter {
    public static void handle(Context c, String original, Consumer<String> speak) {
        String q = original.toLowerCase(Locale.ROOT).trim();

        if (containsAny(q, "call", "கால்", "phone", "கூப்பிடு")) {
            String target = cleanup(q, "call", "கால்", "phone", "pannu", "பண்ணு", "ku", "க்கு", "கூப்பிடு");
            call(c, target, speak); return;
        }

        if (containsAny(q, "route", "navigate", "maps", "ரூட்", "வழி")) {
            String dest = cleanup(q, "route", "navigate", "maps", "podu", "போடு", "pannu", "பண்ணு", "ku", "க்கு", "to");
            openMaps(c, dest, speak); return;
        }

        if (containsAny(q, "technology news", "tech news", "technical news", "டெக்னாலஜி நியூஸ்")) {
            speak.accept("டாப் டெக்னாலஜி நியூஸ் எடுக்கிறேன் பாஸ்");
            NewsFetcher.fetch(c, speak); return;
        }

        if (containsAny(q, "ticket", "டிக்கெட்") && (q.contains("book") || q.contains("புக்"))) {
            ChennaiOneAccessibilityService.setPendingCommand(original);
            Intent launch = c.getPackageManager().getLaunchIntentForPackage("in.mobility.cumta");
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(launch);
                speak.accept("Chennai One open பண்ணிட்டேன் பாஸ். Payment confirmation உங்களிடமே இருக்கும்.");
            } else speak.accept("Chennai One app install ஆகல பாஸ்");
            return;
        }

        if (containsAny(q, "open", "திற", "ஓபன்")) {
            String app = cleanup(q, "open", "திற", "ஓபன்", "pannu", "பண்ணு");
            openAppByLabel(c, app, speak); return;
        }

        speak.accept("இந்த command இன்னும் add பண்ணல பாஸ்");
    }

    private static void openMaps(Context c, String dest, Consumer<String> speak) {
        if (dest.isEmpty()) { speak.accept("Destination சொல்லுங்க பாஸ்"); return; }
        try {
            String uri = "google.navigation:q=" + URLEncoder.encode(dest, "UTF-8");
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            i.setPackage("com.google.android.apps.maps");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            speak.accept(dest + " route open பண்ணிட்டேன் பாஸ்");
        } catch (Exception e) { speak.accept("Maps open ஆகல பாஸ்"); }
    }

    private static void call(Context c, String target, Consumer<String> speak) {
        String number = c.getSharedPreferences("ultron_aliases", Context.MODE_PRIVATE)
                .getString(target.toLowerCase(Locale.ROOT), null);
        if (number == null) number = findContactNumber(c, target);
        if (number == null && target.matches("[+0-9 ]{6,}")) number = target.replace(" ", "");
        if (number == null) { speak.accept(target + " number கிடைக்கல பாஸ்"); return; }
        if (c.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            speak.accept("Call permission enable பண்ணுங்க பாஸ்"); return;
        }
        Intent i = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + number));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
        speak.accept(target + "க்கு call பண்ணுறேன் பாஸ்");
    }

    private static String findContactNumber(Context c, String name) {
        if (c.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null;
        Cursor cur = c.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                new String[]{"%" + name + "%"}, null);
        if (cur != null) {
            try { if (cur.moveToFirst()) return cur.getString(0); }
            finally { cur.close(); }
        }
        return null;
    }

    private static void openAppByLabel(Context c, String wanted, Consumer<String> speak) {
        PackageManager pm = c.getPackageManager();
        Intent base = new Intent(Intent.ACTION_MAIN, null); base.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(base, 0);
        ResolveInfo best = null;
        for (ResolveInfo r : apps) {
            String label = r.loadLabel(pm).toString().toLowerCase(Locale.ROOT);
            if (label.equals(wanted) || label.contains(wanted) || wanted.contains(label)) { best = r; break; }
        }
        if (best == null) { speak.accept(wanted + " app கிடைக்கல பாஸ்"); return; }
        Intent launch = pm.getLaunchIntentForPackage(best.activityInfo.packageName);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(launch);
            speak.accept(best.loadLabel(pm) + " open பண்ணிட்டேன் பாஸ்");
        }
    }

    private static boolean containsAny(String q, String... terms) {
        for (String t : terms) if (q.contains(t)) return true; return false;
    }
    private static String cleanup(String q, String... words) {
        String r = q;
        for (String w : words) r = r.replace(w, " ");
        return r.replaceAll("\\s+", " ").trim();
    }
}
