package com.ultron.assistant;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;

public class NewsFetcher {
    public static void fetch(Context c, Consumer<String> speak) {
        new Thread(() -> {
            String spoken;
            try {
                URL u = new URL("https://news.google.com/rss/search?q=technology&hl=en-IN&gl=IN&ceid=IN:en");
                HttpURLConnection con = (HttpURLConnection) u.openConnection();
                con.setConnectTimeout(8000); con.setReadTimeout(8000);
                con.setRequestProperty("User-Agent", "ULTRON/0.1");
                Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(con.getInputStream());
                NodeList items = doc.getElementsByTagName("item");
                StringBuilder sb = new StringBuilder("Top five technology news. ");
                int n = Math.min(5, items.getLength());
                for (int i=0; i<n; i++) {
                    Element e = (Element) items.item(i);
                    String title = text(e, "title").replaceAll(" - [^-]+$", "");
                    String desc = text(e, "description").replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
                    if (desc.length() > 220) desc = desc.substring(0, 220) + "...";
                    sb.append((i+1)).append(". ").append(title).append(". ");
                    if (!desc.isEmpty()) sb.append(desc).append(". ");
                }
                spoken = sb.toString();
            } catch (Exception e) {
                spoken = "News fetch பண்ண முடியல பாஸ். Internet connection check பண்ணுங்க.";
            }
            String finalSpoken = spoken;
            new Handler(Looper.getMainLooper()).post(() -> speak.accept(finalSpoken));
        }).start();
    }
    private static String text(Element e, String tag) {
        NodeList n = e.getElementsByTagName(tag);
        return n.getLength() == 0 ? "" : n.item(0).getTextContent();
    }
}
