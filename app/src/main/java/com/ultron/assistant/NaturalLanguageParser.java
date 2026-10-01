package com.ultron.assistant;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight, zero-cost Tamil-English intent/entity parser.
 * It is intentionally deterministic so ULTRON's core workflows do not need a paid LLM API.
 */
public class NaturalLanguageParser {
    private static final Pattern NUMBER = Pattern.compile("\\b(\\d{1,2})\\b");
    private static final Pattern OTP = Pattern.compile("(?i)(?:bus\\s*)?(?:otp|code|கோடு|ஓடிபி)\\s*[:=-]?\\s*([A-Za-z0-9-]{2,10})");
    private static final Pattern BUS = Pattern.compile("(?i)\\b(?:bus|route|பஸ்)\\s*(?:number|no\\.?|num|நம்பர்)?\\s*[:=-]?\\s*([0-9]{1,3}[A-Za-z]?)\\b");

    public static ParsedCommand parse(String raw) {
        ParsedCommand p = new ParsedCommand();
        p.raw = raw == null ? "" : raw.trim();
        String q = normalize(p.raw);

        if (has(q, "help", "what can you do", "commands", "என்ன செய்ய முடியும்", "என்னென்ன செய்ய", "உதவி")) {
            p.type = ParsedCommand.Type.HELP;
            return p;
        }

        if (isTicketIntent(q)) {
            p.type = ParsedCommand.Type.CHENNAI_ONE_TICKET;
            extractJourney(q, p);
            Matcher b = BUS.matcher(q);
            if (b.find()) p.busNumber = cleanEntity(b.group(1));
            Matcher o = OTP.matcher(q);
            if (o.find()) p.busOtp = cleanEntity(o.group(1));
            return p;
        }

        if (isCallIntent(q)) {
            p.type = ParsedCommand.Type.CALL;
            p.target = cleanup(q,
                    "call", "phone", "dial", "கால்", "கூப்பிடு", "கூப்பிட", "அழை", "அழைக்க",
                    "pannu", "panna", "பண்ணு", "பண்ண", "ku", "க்கு", "ஐ");
            return p;
        }

        if (isMapsIntent(q)) {
            p.type = ParsedCommand.Type.MAPS;
            extractJourney(q, p);
            if (p.destination.isEmpty()) {
                p.destination = cleanup(q,
                        "route", "navigate", "navigation", "maps", "map", "direction", "directions",
                        "ரூட்", "மேப்", "வழி", "பாதை", "podu", "போடு", "pannu", "பண்ணு", "to", "ku", "க்கு");
            }
            return p;
        }

        if (isNewsIntent(q)) {
            p.type = ParsedCommand.Type.NEWS;
            p.newsCategory = category(q);
            p.newsLocation = location(q);
            p.timeframe = timeframe(q);
            p.count = count(q);
            return p;
        }

        if (isOpenIntent(q)) {
            p.type = ParsedCommand.Type.OPEN_APP;
            p.target = cleanup(q, "open", "launch", "start", "ஓபன்", "திற", "திறந்து", "pannu", "பண்ணு");
            return p;
        }

        p.type = ParsedCommand.Type.WEB_FALLBACK;
        p.target = p.raw;
        return p;
    }

    private static boolean isTicketIntent(String q) {
        return has(q, "ticket", "டிக்கெட்", "chennai one", "சென்னை ஒன்") &&
                has(q, "book", "booking", "புக்", "எடு", "எடுக்க", "ticket");
    }

    private static boolean isCallIntent(String q) {
        return has(q, "call", "dial", "phone", "கால்", "கூப்பிடு", "அழை", "அழைக்க");
    }

    private static boolean isMapsIntent(String q) {
        return has(q, "route", "navigate", "navigation", "maps", "map", "direction", "directions", "ரூட்", "மேப்", "வழி", "பாதை");
    }

    private static boolean isNewsIntent(String q) {
        return has(q, "news", "headline", "headlines", "நியூஸ்", "செய்தி", "செய்திகள்", "நடந்துகிட்டு", "என்ன நடந்தது");
    }

    private static boolean isOpenIntent(String q) {
        return has(q, "open", "launch", "ஓபன்", "திற", "திறந்து");
    }

    /** Extracts common Tamil-English from/to constructions. */
    private static void extractJourney(String q, ParsedCommand p) {
        String s = q;
        String source = "";
        String destination = "";

        // English: from X to Y
        Matcher m = Pattern.compile("(?i)\\bfrom\\s+(.+?)\\s+to\\s+(.+?)(?=\\s+(?:bus|route|ticket|book|otp|code|பஸ்|டிக்கெட்|புக்|ஓடிபி|கோடு)\\b|$)").matcher(s);
        if (m.find()) {
            source = m.group(1);
            destination = m.group(2);
        }

        // Generic X to Y, useful for commands like "Velachery to T Nagar route podu".
        if (source.isEmpty()) {
            m = Pattern.compile("(?i)(.+?)\\s+to\\s+(.+?)(?=\\s+(?:bus|route|ticket|book|otp|code|ரூட்|பஸ்|டிக்கெட்|புக்|ஓடிபி|கோடு)\\b|$)").matcher(s);
            if (m.find()) {
                source = cleanup(m.group(1), "from", "route", "maps", "map", "navigate", "ticket", "book", "chennai one", "சென்னை ஒன்");
                destination = m.group(2);
            }
        }

        // Tamil/Tanglish: X la/ல இருந்து Y ku/க்கு
        if (source.isEmpty()) {
            m = Pattern.compile("(?i)(.+?)(?:\\s+la|ல|ல்)?\\s+(?:irundhu|இருந்து|லிருந்து|லேர்ந்து)\\s+(.+?)(?:\\s+ku|க்கு)(?=\\s|$)").matcher(s);
            if (m.find()) {
                source = m.group(1);
                destination = m.group(2);
            }
        }

        p.source = cleanJourneyEntity(source);
        p.destination = cleanJourneyEntity(destination);
    }

    private static String category(String q) {
        if (has(q, "technology", "technical", "tech", "ai ", "டெக்", "டெக்னாலஜி", "தொழில்நுட்ப")) return "technology";
        if (has(q, "sports", "sport", "cricket", "football", "ஸ்போர்ட்ஸ்", "கிரிக்கெட்")) return "sports";
        if (has(q, "business", "market", "finance", "stock", "பிசினஸ்", "மார்க்கெட்", "நிதி")) return "business";
        if (has(q, "cinema", "movie", "film", "entertainment", "சினிமா", "மூவி")) return "entertainment";
        if (has(q, "science", "space", "research", "சயின்ஸ்", "விண்வெளி", "அறிவியல்")) return "science";
        if (has(q, "politics", "political", "election", "அரசியல்", "தேர்தல்")) return "politics";
        return "general";
    }

    private static String location(String q) {
        if (has(q, "chennai", "சென்னை")) return "Chennai";
        if (has(q, "tamil nadu", "tamilnadu", "தமிழ்நாடு")) return "Tamil Nadu";
        if (has(q, "india", "இந்தியா", "இந்திய")) return "India";
        if (has(q, "world", "global", "international", "உலக", "உலகம்")) return "World";
        return "India";
    }

    private static String timeframe(String q) {
        if (has(q, "today", "today's", "இன்று", "இன்னைக்கு", "இன்றைய")) return "today";
        if (has(q, "latest", "recent", "இப்போ", "லேட்டஸ்ட்", "சமீப")) return "latest";
        if (has(q, "week", "weekly", "இந்த வார", "வாரம்")) return "week";
        return "today";
    }

    private static int count(String q) {
        if (has(q, "top five", "top 5", "ஐந்து", "5 news", "5 headlines")) return 5;
        if (has(q, "top ten", "top 10", "பத்து", "10 news", "10 headlines")) return 10;
        if (has(q, "top three", "top 3", "மூன்று", "3 news", "3 headlines")) return 3;
        Matcher m = NUMBER.matcher(q);
        while (m.find()) {
            try {
                int n = Integer.parseInt(m.group(1));
                if (n >= 1 && n <= 10) return n;
            } catch (Exception ignored) {}
        }
        return 5;
    }

    private static boolean has(String q, String... terms) {
        for (String t : terms) if (q.contains(t)) return true;
        return false;
    }

    private static String normalize(String raw) {
        return raw.toLowerCase(Locale.ROOT)
                .replace('–', '-')
                .replace('—', '-')
                // Common Tanglish case suffixes are often typed/spoken as akka-ku, chennai-la, etc.
                .replaceAll("(?i)-(kku|ku)\\b", " ku")
                .replaceAll("(?i)-la\\b", " la")
                .replaceAll("[?,.!]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String cleanup(String q, String... words) {
        String r = " " + q + " ";
        for (String w : words) {
            r = r.replace(" " + w + " ", " ");
        }
        return r.replaceAll("\\s+", " ").trim();
    }

    private static String cleanJourneyEntity(String s) {
        if (s == null) return "";
        return cleanup(s,
                "chennai one", "சென்னை ஒன்", "ticket", "டிக்கெட்", "book", "booking", "புக்",
                "route", "ரூட்", "maps", "map", "மேப்", "navigate", "podu", "போடு", "pannu", "பண்ணு",
                "from", "to");
    }

    private static String cleanEntity(String s) {
        return s == null ? "" : s.trim();
    }
}
