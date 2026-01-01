package com.danavalerie.matrixmudrelay.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WritTracker {
    private static final Pattern REQUIREMENT_PATTERN = Pattern.compile("^\\[[ xX]?\\] (.+?) to (.+?) at (.+)$");
    private static final Pattern NUMBER_PREFIX = Pattern.compile("^(\\d+)\\s+(.*)$");

    private final List<WritRequirement> requirements = new ArrayList<>();
    private boolean readingWrit = false;

    public void ingest(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        String[] lines = text.split("\\n", -1);
        for (String line : lines) {
            ingestLine(line);
        }
    }

    public List<WritRequirement> getRequirements() {
        return Collections.unmodifiableList(requirements);
    }

    private void ingestLine(String line) {
        if (line == null) {
            return;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        if (trimmed.startsWith("You read the official employment writ")) {
            readingWrit = true;
            requirements.clear();
            return;
        }
        if (trimmed.startsWith("You have until ")) {
            readingWrit = false;
            return;
        }
        if (!readingWrit) {
            return;
        }
        Matcher matcher = REQUIREMENT_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            return;
        }
        String itemText = matcher.group(1).trim();
        String npc = matcher.group(2).trim();
        String location = matcher.group(3).trim();
        QuantityResult qtyResult = parseQuantity(itemText);
        requirements.add(new WritRequirement(qtyResult.quantity, qtyResult.item, npc, location));
    }

    private static QuantityResult parseQuantity(String itemText) {
        if (itemText == null) {
            return new QuantityResult(1, "");
        }
        String trimmed = itemText.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("a ")) {
            return new QuantityResult(1, trimmed.substring(2).trim());
        }
        if (lower.startsWith("an ")) {
            return new QuantityResult(1, trimmed.substring(3).trim());
        }
        if (lower.startsWith("one ")) {
            return new QuantityResult(1, trimmed.substring(4).trim());
        }
        if (lower.startsWith("two ")) {
            return new QuantityResult(2, trimmed.substring(4).trim());
        }
        Matcher numberMatch = NUMBER_PREFIX.matcher(trimmed);
        if (numberMatch.matches()) {
            try {
                int quantity = Integer.parseInt(numberMatch.group(1));
                String item = numberMatch.group(2).trim();
                return new QuantityResult(quantity, item.isEmpty() ? trimmed : item);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return new QuantityResult(1, trimmed);
    }

    private record QuantityResult(int quantity, String item) {
    }

    public record WritRequirement(int quantity, String item, String npc, String location) {
    }
}
