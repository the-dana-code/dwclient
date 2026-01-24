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

package com.danavalerie.matrixmudrelay.core;

import com.danavalerie.matrixmudrelay.util.GrammarUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StoreInventoryTracker {
    private static final String INVENTORY_START = "The following items are for sale:";
    private static final String MENU_START = "You read the menu:";
    private static final String OFFER_START = "You find on offer:";
    private static final Pattern ITEM_LINE = Pattern.compile("^\\s{0,3}([A-Za-z0-9]{1,4}):\\s+(.+)\\s+for\\s+.+$");
    private final List<StoreItem> items = new ArrayList<>();
    private boolean readingInventory;
    private boolean hasInventory;
    private ListingMode listingMode = ListingMode.NONE;

    public synchronized boolean ingest(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        boolean updated = false;
        String[] lines = text.split("\\n", -1);
        for (String line : lines) {
            updated |= ingestLine(line);
        }
        return updated;
    }

    public synchronized List<StoreItem> getItems() {
        return List.copyOf(items);
    }

    public synchronized Optional<StoreItem> findMatch(String itemName) {
        if (!hasInventory || itemName == null || itemName.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalizeItemName(itemName);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        List<String> variants = buildNormalizedVariants(normalized);
        for (StoreItem item : items) {
            String candidate = normalizeItemName(item.name());
            if (variants.contains(candidate)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    public synchronized boolean hasInventory() {
        return hasInventory;
    }

    public synchronized boolean isNameListed() {
        return listingMode == ListingMode.NAME_LISTED;
    }

    public synchronized void clearInventory() {
        items.clear();
        readingInventory = false;
        hasInventory = false;
        listingMode = ListingMode.NONE;
    }

    private boolean ingestLine(String line) {
        if (line == null) {
            return false;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            if (readingInventory) {
                readingInventory = false;
            }
            return false;
        }
        if (INVENTORY_START.equals(trimmed)) {
            items.clear();
            readingInventory = true;
            hasInventory = true;
            listingMode = ListingMode.LETTERED;
            return true;
        }
        if (OFFER_START.equals(trimmed) || MENU_START.equals(trimmed)) {
            items.clear();
            hasInventory = true;
            listingMode = ListingMode.NAME_LISTED;
            return true;
        }
        if (!readingInventory) {
            return false;
        }
        return switch (listingMode) {
            case LETTERED -> ingestLetteredItem(line);
            case NONE, NAME_LISTED -> false;
        };
    }

    private static String normalizeItemName(String name) {
        String trimmed = name.trim().toLowerCase(Locale.ROOT);
        trimmed = removePrefix(trimmed, "our very last ");
        trimmed = removePrefix(trimmed, "a ", "an ", "one ");
        trimmed = removePrefix(trimmed, "pair of ", "pairs of ");
        trimmed = removePrefix(trimmed, "set of ", "sets of ");
        trimmed = removePrefix(trimmed, "game of ", "games of ");
        trimmed = removePrefix(trimmed, "tube of ", "tubes of ");
        return trimmed.replaceAll("\\s+", " ");
    }

    private static String removePrefix(String text, String... prefixes) {
        for (String prefix : prefixes) {
            if (text.startsWith(prefix)) {
                return text.substring(prefix.length()).trim();
            }
        }
        return text;
    }

    private static List<String> buildNormalizedVariants(String normalized) {
        LinkedHashSet<String> variants = new LinkedHashSet<>();
        variants.add(normalized);
        for (String singular : GrammarUtils.singularizePhrase(normalized)) {
            variants.add(singular.toLowerCase(Locale.ROOT));
        }

        String[] parts = normalized.split(" ");
        if (parts.length > 0) {
            String last = parts[parts.length - 1];
            for (String singular : GrammarUtils.singularizeWord(last)) {
                if (singular.equalsIgnoreCase(last)) {
                    continue;
                }
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    if (i > 0) {
                        builder.append(' ');
                    }
                    builder.append(parts[i]);
                }
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(singular.toLowerCase(Locale.ROOT));
                variants.add(builder.toString());
            }
        }
        return List.copyOf(variants);
    }

    private boolean ingestLetteredItem(String line) {
        Matcher matcher = ITEM_LINE.matcher(line);
        if (!matcher.matches()) {
            readingInventory = false;
            listingMode = ListingMode.NONE;
            return false;
        }
        String id = matcher.group(1).trim();
        String name = matcher.group(2).trim();
        if (!id.isEmpty() && !name.isEmpty()) {
            items.add(new StoreItem(id, name));
            return true;
        }
        return false;
    }

    private enum ListingMode {
        NONE,
        LETTERED,
        NAME_LISTED
    }

    public record StoreItem(String id, String name) {
    }
}

