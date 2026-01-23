/*
 * Lesa's Discworld MUD client.
 * Copyright (C) 2026 Dana Reese
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
        String lower = phrase.toLowerCase(Locale.ROOT);

        String[][] containerPatterns = {
                {"pairs of ", "pair of "},
                {"sets of ", "set of "},
                {"packets of ", "packet of "},
                {"tubes of ", "tube of "},
                {"games of ", "game of "}
        };

        for (String[] pattern : containerPatterns) {
            if (lower.startsWith(pattern[0]) || lower.startsWith(pattern[1])) {
                String s = replacePrefix(phrase, pattern[0], pattern[1]);
                if (s != null) {
                    phrases.add(s);
                }
                return phrases;
            }
        }

        String s;
        if ((s = replacePrefix(phrase, "petits fours", "petit fours")) != null) {
            phrases.add(s);
        } else if ((s = replacePrefix(phrase, "mains gauches", "main gauche")) != null) {
            phrases.add(s);
        } else if ((s = replacePrefix(phrase, "blue fluffy blankets with fluffy bunnies on them", "blue fluffy blanket with fluffy bunnies on")) != null) {
            phrases.add(s);
        } else {
            for (String prep : new String[]{" with ", " of ", " in ", " for ", " to "}) {
                int idx = lower.indexOf(prep);
                if (idx > 0) {
                    String head = phrase.substring(0, idx);
                    String tail = phrase.substring(idx);
                    List<String> singularHeads = singularizePhrase(head);
                    if (!singularHeads.isEmpty()) {
                        for (String sHead : singularHeads) {
                            phrases.add(sHead + tail);
                        }
                        return phrases;
                    }
                }
            }

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
            if (lower.endsWith("ses") || lower.endsWith("xes") || lower.endsWith("zes") ||
                    lower.endsWith("ches") || lower.endsWith("shes")) {
                // In Discworld MUD (and British English), items ending in "axes" are almost always
                // singularized to "axe" (e.g., wooden axes -> wooden axe, pickaxes -> pickaxe).
                // Words like "taxes" and "faxes" are exceptions that singularize to "tax" and "fax".
                if (lower.endsWith("axes") && !lower.endsWith("taxes") && !lower.endsWith("faxes") && !lower.endsWith("maxes")) {
                    tryReplaceSuffix(word, lower, "s", new String[]{""}, candidates);
                } else {
                    tryReplaceSuffix(word, lower, "es", new String[]{""}, candidates);
                }
            }
        }
        if (!lower.endsWith("ss") && !lower.endsWith("is")) {
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

