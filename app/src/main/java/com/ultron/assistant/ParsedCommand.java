package com.ultron.assistant;

public class ParsedCommand {
    public enum Type { CALL, MAPS, NEWS, CHENNAI_ONE_TICKET, OPEN_APP, HELP, WEB_FALLBACK }

    public Type type = Type.WEB_FALLBACK;
    public String raw = "";
    public String target = "";
    public String source = "";
    public String destination = "";
    public String busNumber = "";
    public String busOtp = "";
    public String newsLocation = "";
    public String newsCategory = "general";
    public String timeframe = "today";
    public int count = 5;
}
