package com.danavalerie.matrixmudrelay.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class GrammarUtils {
    private GrammarUtils() {}

    public static List<String> singularizePhrase(String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return List.of();
        }
        List<String> phrases = new ArrayList<>();
        String s;
        if ((s = replacePrefix(phrase, "pairs of ", "pair of ")) != null) {
            phrases.add(s);
        } else if ((s = replacePrefix(phrase, "sets of ", "set of ")) != null) {
            phrases.add(s);
        } else if ((s = replacePrefix(phrase, "packets of ", "packet of ")) != null) {
            phrases.add(s);
        } else if ((s = replacePrefix(phrase, "tubes of ", "tube of ")) != null) {
            phrases.add(s);
        } else if ((s = replacePrefix(phrase, "games of ", "game of ")) != null) {
            phrases.add(s);
        } else if ((s = replacePrefix(phrase, "petits fours", "petit fours")) != null) {
            phrases.add(s);
        } else {
            String[] parts = phrase.trim().split("\\s+");
            if (parts.length == 0) {
                return List.of();
            }
            // try singularizing the last word -- red beach towels -> red beach towel
            String last = parts[parts.length - 1];
            List<String> singulars = singularizeWord(last);
            if (!singulars.isEmpty()) {
                for (String singular : singulars) {
                    if (singular.equalsIgnoreCase(last)) {
                        continue;
                    }
                    String[] newParts = parts.clone();
                    newParts[newParts.length - 1] = singular;
                    phrases.add(String.join(" ", newParts));
                }
            }
        }
        return phrases;
    }

    private static String replacePrefix(String text, String prefix, String replacement) {
        if (text.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
            return applyCase(text.substring(0, Math.min(text.length(), 1)), replacement) + text.substring(prefix.length());
        }
        return null;
    }

    public static List<String> singularizeWord(String word) {
        if (word == null || word.isEmpty()) {
            return List.of();
        }
        String lower = word.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(word);
        
        tryReplaceSuffix(word, lower, "ies", new String[]{"ie", "y"}, candidates);
        tryReplaceSuffix(word, lower, "oes", new String[]{"oe", "o"}, candidates);
        tryReplaceSuffix(word, lower, "ves", new String[]{"f", "fe"}, candidates);
        tryReplaceSuffix(word, lower, "men", new String[]{"man"}, candidates);

        if (lower.equals("auloi")) {
            candidates.add(applyCase(word, "aulos"));
        }
        if (lower.equals("xiphoi")) {
            candidates.add(applyCase(word, "xiphos"));
        }
        
        if (!lower.endsWith("ies") && !lower.endsWith("oes")) {
            tryReplaceSuffix(word, lower, "es", new String[]{""}, candidates);
        }
        if (!lower.endsWith("ss")) {
            tryReplaceSuffix(word, lower, "s", new String[]{""}, candidates);
        }
        return new ArrayList<>(candidates);
    }

    private static void tryReplaceSuffix(String word, String lower, String suffix, String[] replacements, Collection<String> candidates) {
        if (lower.endsWith(suffix) && lower.length() > suffix.length()) {
            String base = word.substring(0, word.length() - suffix.length());
            for (String r : replacements) {
                candidates.add(base + applyCase(word.substring(word.length() - suffix.length()), r));
            }
        }
    }
    
    private static String applyCase(String reference, String target) {
        if (reference == null || reference.isEmpty() || target == null || target.isEmpty()) {
            return target;
        }
        if (Character.isUpperCase(reference.charAt(0))) {
            return Character.toUpperCase(target.charAt(0)) + (target.length() > 1 ? target.substring(1) : "");
        }
        return target;
    }
}
