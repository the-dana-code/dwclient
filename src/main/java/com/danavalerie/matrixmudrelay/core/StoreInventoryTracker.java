package com.danavalerie.matrixmudrelay.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StoreInventoryTracker {
    private static final String INVENTORY_START = "The following items are for sale:";
    private static final String OFFER_START = "You find on offer:";
    private static final Pattern ITEM_LINE = Pattern.compile("^\\s{0,3}([A-Za-z0-9]{1,4}):\\s+(.+?)\\s+for\\s+.+$");
    private final List<StoreItem> items = new ArrayList<>();
    private boolean readingInventory;
    private boolean hasInventory;
    private ListingMode listingMode = ListingMode.NONE;

    public boolean ingest(String text) {
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

    public List<StoreItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public Optional<StoreItem> findMatch(String itemName) {
        if (!hasInventory || itemName == null || itemName.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalizeItemName(itemName);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        for (StoreItem item : items) {
            if (normalizeItemName(item.name()).equals(normalized)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    public boolean hasInventory() {
        return hasInventory;
    }

    public boolean isNameListed() {
        return listingMode == ListingMode.NAME_LISTED;
    }

    public void clearInventory() {
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
        if (OFFER_START.equals(trimmed)) {
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
        if (trimmed.startsWith("our very last ")) {
            trimmed = trimmed.substring("our very last ".length()).trim();
        }
        if (trimmed.startsWith("a ")) {
            trimmed = trimmed.substring(2).trim();
        } else if (trimmed.startsWith("an ")) {
            trimmed = trimmed.substring(3).trim();
        } else if (trimmed.startsWith("one ")) {
            trimmed = trimmed.substring(4).trim();
        }
        if (trimmed.startsWith("pair of ")) {
            trimmed = trimmed.substring("pair of ".length()).trim();
        } else if (trimmed.startsWith("pairs of ")) {
            trimmed = trimmed.substring("pairs of ".length()).trim();
        }
        return trimmed.replaceAll("\\s+", " ");
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
