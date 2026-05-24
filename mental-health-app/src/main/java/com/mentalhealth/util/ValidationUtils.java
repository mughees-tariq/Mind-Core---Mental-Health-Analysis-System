package com.mentalhealth.util;

import java.util.regex.Pattern;

// Centralised input validation helpers; each method returns null on success or an error message on failure
public final class ValidationUtils {

    private ValidationUtils() { /* utility class */ }

    //  patterns 
    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9_]{3,30}$");

    private static final Pattern NAME_PATTERN =
            Pattern.compile("^[\\p{L} .'-]{1,100}$");

    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^\\+?[0-9\\- ]{7,20}$");

    //  public validators 

    // Validates username: 3-30 chars, letters/digits/underscores; returns null if valid
    public static String validateUsername(String username) {
        if (username == null || username.isBlank()) return "Username is required.";
        if (!USERNAME_PATTERN.matcher(username).matches())
            return "Username must be 3–30 characters (letters, digits, underscores).";
        return null;
    }

    // Validates display name; returns null if valid
    public static String validateName(String name) {
        if (name == null || name.isBlank()) return "Full name is required.";
        if (!NAME_PATTERN.matcher(name).matches())
            return "Name may only contain letters, spaces, hyphens, apostrophes, and periods.";
        return null;
    }

    // Validates password meets minimum complexity (8+ chars, letter, digit); returns null if valid
    public static String validatePassword(String password) {
        if (password == null || password.isEmpty()) return "Password is required.";
        if (password.length() < 8)
            return "Password must be at least 8 characters.";
        if (!password.matches(".*[a-zA-Z].*"))
            return "Password must contain at least one letter.";
        if (!password.matches(".*\\d.*"))
            return "Password must contain at least one digit.";
        return null;
    }

    // Validates age value (13-120); optional field, returns null if blank or valid
    public static String validateAge(String ageText) {
        if (ageText == null || ageText.isBlank()) return null; // optional field
        try {
            int age = Integer.parseInt(ageText.trim());
            if (age < 13 || age > 120) return "Age must be between 13 and 120.";
        } catch (NumberFormatException e) {
            return "Age must be a number.";
        }
        return null;
    }

    // Validates phone number (7-20 digits, optional + prefix); returns null if valid
    public static String validatePhone(String phone) {
        if (phone == null || phone.isBlank()) return "Phone number is required.";
        if (!PHONE_PATTERN.matcher(phone.trim()).matches())
            return "Phone number must be 7–20 digits (may start with + and contain dashes).";
        return null;
    }

    // Validates analysis text is non-empty and at most 5000 chars; returns null if valid
    public static String validateAnalysisText(String text) {
        if (text == null || text.isBlank())
            return "Please enter some text to analyse.";
        if (text.length() > 5000)
            return "Text must be no longer than 5,000 characters.";
        return null;
    }

    // Sanitises string by trimming and collapsing excessive whitespace
    public static String sanitise(String input) {
        if (input == null) return "";
        return input.trim().replaceAll("\\s{2,}", " ");
    }
}
