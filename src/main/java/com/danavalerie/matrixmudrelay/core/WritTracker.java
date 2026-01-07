package com.danavalerie.matrixmudrelay.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WritTracker {
    private static final Pattern REQUIREMENT_PATTERN = Pattern.compile("^\\[[ xX]?\\] (.+?) to (.+?) at (.+)$");
    private static final Pattern REQUIREMENT_SENTENCE_PATTERN = Pattern.compile(
            "^You are required to deliver (.+?) to (.+?) at (.+?)[.]?$");
    private static final Pattern NUMBER_PREFIX = Pattern.compile("^(\\d+)\\s+(.*)$");
    private static final String LOCATION_SEPARATOR = " on ";

    private final List<WritRequirement> requirements = new ArrayList<>();
    private boolean readingWrit = false;

    public boolean ingest(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String[] lines = text.split("\\n", -1);
        boolean updated = false;
        for (String line : lines) {
            updated |= ingestLine(line);
        }
        return updated;
    }

    public List<WritRequirement> getRequirements() {
        return Collections.unmodifiableList(requirements);
    }

    private boolean ingestLine(String line) {
        if (line == null) {
            return false;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (trimmed.startsWith("You read the official employment writ")) {
            readingWrit = true;
            if (!requirements.isEmpty()) {
                requirements.clear();
                return true;
            }
            return false;
        }
        if (trimmed.startsWith("You have until ")) {
            readingWrit = false;
            return false;
        }
        if (!readingWrit) {
            return false;
        }
        Matcher matcher = REQUIREMENT_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            matcher = REQUIREMENT_SENTENCE_PATTERN.matcher(trimmed);
            if (!matcher.matches()) {
                return false;
            }
        }
        String itemText = matcher.group(1).trim();
        String npc = matcher.group(2).trim();
        String location = matcher.group(3).trim();
        QuantityResult qtyResult = parseQuantity(itemText);
        LocationParts locationParts = parseLocationParts(location);
        requirements.add(new WritRequirement(qtyResult.quantity, qtyResult.item, npc,
                locationParts.locationName, locationParts.locationSuffix));
        return true;
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

    private record LocationParts(String locationName, String locationSuffix) {
    }

    private static LocationParts parseLocationParts(String location) {
        if (location == null || location.isBlank()) {
            return new LocationParts("", "");
        }
        String trimmed = location.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        int separatorIndex = lower.indexOf(LOCATION_SEPARATOR);
        if (separatorIndex <= 0 || separatorIndex >= trimmed.length() - LOCATION_SEPARATOR.length()) {
            return new LocationParts(trimmed, "");
        }
        String locationName = trimmed.substring(0, separatorIndex).trim();
        String locationSuffix = trimmed.substring(separatorIndex).trim();
        if (locationName.isEmpty()) {
            return new LocationParts(trimmed, "");
        }
        return new LocationParts(locationName, locationSuffix);
    }

    public record WritRequirement(int quantity, String item, String npc,
                                  String locationName, String locationSuffix) {
        public String locationDisplay() {
            if (locationSuffix == null || locationSuffix.isBlank()) {
                return locationName;
            }
            return locationName + " " + locationSuffix.trim();
        }
    }
}
