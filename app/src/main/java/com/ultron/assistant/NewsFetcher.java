package com.ultron.assistant;

import android.os.Handler;
import android.os.Looper;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.net.*;
import java.util.*;
import java.util.function.Consumer;

/** Free RSS-based current-news workflow. No paid API key is required. */
public class NewsFetcher {
    public static void fetch(ParsedCommand request, Consumer<String> speak) {
        new Thread(() -> {
            String spoken;
            try {
                String query = buildQuery(request);
                String url = "https://news.google.com/rss/search?q=" + URLEncoder.encode(query, "UTF-8")
                        + "&hl=en-IN&gl=IN&ceid=IN:en";
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(9000);
                con.setReadTimeout(9000);
                con.setRequestProperty("User-Agent", "ULTRON/0.3");

                DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
                try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignored) {}
                Document doc = f.newDocumentBuilder().parse(con.getInputStream());
                NodeList items = doc.getElementsByTagName("item");

                int n = Math.min(Math.max(request.count, 1), Math.min(10, items.getLength()));
                if (n == 0) throw new IllegalStateException("No news items");

                String category = "general".equals(request.newsCategory) ? "" : request.newsCategory + " ";
                StringBuilder sb = new StringBuilder();
                sb.append(request.newsLocation).append(" ").append(category)
                        .append("news top ").append(n).append(". ");

                for (int i = 0; i < n; i++) {
                    Element e = (Element) items.item(i);
                    String title = cleanupTitle(text(e, "title"));
                    String source = text(e, "source").trim();
                    String desc = stripHtml(text(e, "description"));
                    desc = removeDuplicateTitle(desc, title);
                    if (desc.length() > 180) desc = desc.substring(0, 180).trim() + "...";

                    sb.append(i + 1).append(". ").append(title).append(". ");
                    if (!source.isEmpty()) sb.append("Source ").append(source).append(". ");
                    if (!desc.isEmpty()) sb.append(desc).append(". ");
                }
                spoken = sb.toString();
            } catch (Exception e) {
                spoken = "News fetch பண்ண முடியல பாஸ். Internet connection check பண்ணுங்க.";
            }
            String out = spoken;
            new Handler(Looper.getMainLooper()).post(() -> speak.accept(out));
        }).start();
    }

    private static String buildQuery(ParsedCommand r) {
        List<String> terms = new ArrayList<>();
        if (r.newsLocation != null && !r.newsLocation.isEmpty() && !"World".equalsIgnoreCase(r.newsLocation)) {
            terms.add('"' + r.newsLocation + '"');
        }
        if (r.newsCategory != null && !"general".equals(r.newsCategory)) terms.add(r.newsCategory);
        if ("today".equals(r.timeframe)) terms.add("when:1d");
        else if ("week".equals(r.timeframe)) terms.add("when:7d");
        return terms.isEmpty() ? "India when:1d" : String.join(" ", terms);
    }

    private static String cleanupTitle(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+-\\s+[^-]{2,80}$", "").trim();
    }

    private static String stripHtml(String s) {
        if (s == null) return "";
        return s.replaceAll("<[^>]*>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replaceAll("\\s+", " ").trim();
    }

    private static String removeDuplicateTitle(String desc, String title) {
        if (desc == null) return "";
        if (title != null && !title.isEmpty()) {
            String low = desc.toLowerCase(Locale.ROOT);
            String t = title.toLowerCase(Locale.ROOT);
            int i = low.indexOf(t);
            if (i >= 0) desc = (desc.substring(0, i) + " " + desc.substring(i + title.length())).trim();
        }
        return desc.replaceAll("\\s+", " ").trim();
    }

    private static String text(Element e, String tag) {
        NodeList n = e.getElementsByTagName(tag);
        return n.getLength() == 0 ? "" : n.item(0).getTextContent();
    }
}
