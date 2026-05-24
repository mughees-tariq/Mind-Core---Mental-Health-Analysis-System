package com.mentalhealth.util;

// Centralised mood-related helpers for converting between mood labels, scores, colours, and emoji
public final class MoodUtils {

    private MoodUtils() { /* utility class */ }

    //  mood → numeric score (higher = more severe)

    // Maps prediction label to severity score (0-100); returns 50 for null/unknown
    public static double getMoodScore(String mood) {
        if (mood == null) return 50;
        return switch (mood.trim()) {
            case "Normal"               -> 15;
            case "Stress"               -> 30;
            case "Anxiety"              -> 45;
            case "Bipolar"              -> 60;
            case "Depression"           -> 75;
            case "Personality Disorder" -> 90;
            case "Suicidal"             -> 100;
            default                     -> 50;
        };
    }

    //  mood → hex colour

    // Returns hex colour for given mood label
    public static String getMoodColor(String mood) {
        if (mood == null) return "#64748b";
        return switch (mood.trim()) {
            case "Normal"               -> "#10b981";
            case "Stress"               -> "#f59e0b";
            case "Anxiety"              -> "#f97316";
            case "Bipolar"              -> "#e879f9";
            case "Depression"           -> "#ef4444";
            case "Personality Disorder" -> "#dc2626";
            case "Suicidal"             -> "#991b1b";
            default                     -> "#64748b";
        };
    }

    //  mood → emoji 

    // Returns emoji character for given mood label (case-insensitive)
    public static String getMoodEmoji(String mood) {
        if (mood == null) return "\uD83E\uDDE0";          // 🧠
        return switch (mood.trim().toLowerCase()) {
            case "normal"               -> "\uD83D\uDE0A"; // 😊
            case "stress"               -> "\uD83D\uDE23"; // 😣
            case "anxiety"              -> "\uD83D\uDE30"; // 😰
            case "depression"           -> "\uD83D\uDE1E"; // 😞
            case "bipolar"              -> "\uD83C\uDF00"; // 🌀
            case "personality disorder" -> "\uD83E\uDDE9"; // 🧩
            case "suicidal"             -> "\uD83C\uDD98"; // 🆘
            default                     -> "\uD83E\uDDE0"; // 🧠
        };
    }

    //  score → mood label 

    // Converts numeric score (0-105) back to closest mood label
    public static String getMoodLabel(double score) {
        if (score < 22)  return "Normal";
        if (score < 38)  return "Stress";
        if (score < 52)  return "Anxiety";
        if (score < 68)  return "Bipolar";
        if (score < 82)  return "Depression";
        if (score < 95)  return "Personality Disorder";
        return "Suicidal";
    }

    //  mood 

    // Returns softer border hex colour for card outlines
    public static String getMoodBorderColor(String mood) {
        if (mood == null) return "#cbd5e1";
        return switch (mood.trim()) {
            case "Normal"               -> "#6ee7b7";
            case "Stress"               -> "#fcd34d";
            case "Anxiety"              -> "#fdba74";
            case "Bipolar"              -> "#f0abfc";
            case "Depression"           -> "#fca5a5";
            case "Personality Disorder" -> "#fca5a5";
            case "Suicidal"             -> "#fca5a5";
            default                     -> "#cbd5e1";
        };
    }
}
