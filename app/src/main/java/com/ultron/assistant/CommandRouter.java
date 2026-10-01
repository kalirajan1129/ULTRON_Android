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

/** Routes natural Tamil-English commands to modular workflows. */
public class CommandRouter {
    public static void handle(Context c, String original, Consumer<String> speak) {
        ParsedCommand p = NaturalLanguageParser.parse(original);

        switch (p.type) {
            case CALL:
                call(c, p.target, speak);
                return;
            case MAPS:
                openMaps(c, p.destination, speak);
                return;
            case NEWS:
                speak.accept(newsIntro(p));
                NewsFetcher.fetch(p, speak);
                return;
            case CHENNAI_ONE_TICKET:
                openChennaiOne(c, p, speak);
                return;
            case OPEN_APP:
                openAppByLabel(c, p.target, speak);
                return;
            case HELP:
                speak.accept("பாஸ், நான் call பண்ணலாம், Maps route போடலாம், Chennai One ticket booking payment page வரை உதவலாம், latest news சொல்லலாம், app open பண்ணலாம்.");
                return;
            case WEB_FALLBACK:
            default:
                openWebFallback(c, p.target, speak);
        }
    }

    private static String newsIntro(ParsedCommand p) {
        String cat = "general".equals(p.newsCategory) ? "" : p.newsCategory + " ";
        return p.newsLocation + " " + cat + "news top " + p.count + " எடுக்கிறேன் பாஸ்";
    }

    private static void openChennaiOne(Context c, ParsedCommand p, Consumer<String> speak) {
        ChennaiOneAccessibilityService.setPendingBooking(
                p.source, p.destination, p.busNumber, p.busOtp);

        Intent launch = c.getPackageManager().getLaunchIntentForPackage("in.mobility.cumta");
        if (launch == null) {
            speak.accept("Chennai One app install ஆகல பாஸ்");
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(launch);

        StringBuilder s = new StringBuilder("Chennai One open பண்ணிட்டேன் பாஸ்");
        if (!p.source.isEmpty() && !p.destination.isEmpty()) {
            s.append(". ").append(p.source).append(" இருந்து ").append(p.destination).append(" booking details fill பண்ண முயற்சி செய்கிறேன்");
        }
        if (!p.busOtp.isEmpty()) s.append(". Bus OTP ").append(p.busOtp).append(" set பண்ணுறேன்");
        s.append(". Payment page வந்ததும் நீங்க confirm பண்ணணும்");
        speak.accept(s.toString());
    }

    private static void openMaps(Context c, String dest, Consumer<String> speak) {
        if (dest == null || dest.trim().isEmpty()) {
            speak.accept("Destination சொல்லுங்க பாஸ்");
            return;
        }
        try {
            String uri = "google.navigation:q=" + URLEncoder.encode(dest.trim(), "UTF-8");
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
            i.setPackage("com.google.android.apps.maps");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            speak.accept(dest + " route open பண்ணிட்டேன் பாஸ்");
        } catch (Exception e) {
            try {
                Intent browserMaps = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(dest)));
                browserMaps.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(browserMaps);
                speak.accept(dest + " map open பண்ணிட்டேன் பாஸ்");
            } catch (Exception ignored) {
                speak.accept("Maps open ஆகல பாஸ்");
            }
        }
    }

    private static void call(Context c, String target, Consumer<String> speak) {
        if (target == null || target.trim().isEmpty()) {
            speak.accept("யாருக்கு call பண்ணணும் பாஸ்?");
            return;
        }
        target = target.trim();
        String number = c.getSharedPreferences("ultron_aliases", Context.MODE_PRIVATE)
                .getString(target.toLowerCase(Locale.ROOT), null);
        if (number == null) number = findContactNumber(c, target);
        if (number == null && target.matches("[+0-9 ]{6,}")) number = target.replace(" ", "");
        if (number == null) {
            speak.accept(target + " number கிடைக்கல பாஸ்");
            return;
        }
        if (c.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            speak.accept("Call permission enable பண்ணுங்க பாஸ்");
            return;
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
        if (wanted == null || wanted.trim().isEmpty()) {
            speak.accept("எந்த app open பண்ணணும் பாஸ்?");
            return;
        }
        wanted = wanted.trim().toLowerCase(Locale.ROOT);
        PackageManager pm = c.getPackageManager();
        Intent base = new Intent(Intent.ACTION_MAIN, null);
        base.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(base, 0);
        ResolveInfo best = null;
        for (ResolveInfo r : apps) {
            String label = r.loadLabel(pm).toString().toLowerCase(Locale.ROOT);
            if (label.equals(wanted) || label.contains(wanted) || wanted.contains(label)) {
                best = r;
                break;
            }
        }
        if (best == null) {
            speak.accept(wanted + " app கிடைக்கல பாஸ்");
            return;
        }
        Intent launch = pm.getLaunchIntentForPackage(best.activityInfo.packageName);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(launch);
            speak.accept(best.loadLabel(pm) + " open பண்ணிட்டேன் பாஸ்");
        }
    }

    /** Free fallback: open a web search rather than pretending a local rule knows the answer. */
    private static void openWebFallback(Context c, String query, Consumer<String> speak) {
        if (query == null || query.trim().isEmpty()) return;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            speak.accept("இந்த கேள்விக்கு web search open பண்ணிட்டேன் பாஸ்");
        } catch (Exception e) {
            speak.accept("இந்த command புரியல பாஸ்");
        }
    }
}
