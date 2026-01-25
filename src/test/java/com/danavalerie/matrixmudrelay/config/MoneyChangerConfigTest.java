package com.danavalerie.matrixmudrelay.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class MoneyChangerConfigTest {
  private static final String MONEY_CHANGER_PREFIX = "Money Changers/";

  @Test
  void moneyChangerPairsHaveAllVariants() throws IOException {
    Path configPath = Path.of("config.json");
    assertTrue(Files.exists(configPath), "Expected config.json to exist at repository root");

    JsonObject root = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();
    JsonArray bookmarks = root.getAsJsonArray("bookmarks");
    assertNotNull(bookmarks, "Expected bookmarks array in config.json");

    Map<String, Map<String, Set<String>>> variantsByChanger = new HashMap<>();

    for (JsonElement element : bookmarks) {
      JsonObject bookmark = element.getAsJsonObject();
      String name = bookmark.get("name").getAsString();
      if (!name.startsWith(MONEY_CHANGER_PREFIX)) {
        continue;
      }

      String[] parts = name.split("/");
      assertTrue(parts.length >= 5, "Unexpected money changer bookmark format: " + name);

      String direction = parts[1];
      String currencyA = parts[2];
      String toFrom = parts[3];
      String changer = parts[parts.length - 1];

      String currencyB;
      if (toFrom.startsWith("To ")) {
        currencyB = toFrom.substring("To ".length());
      } else if (toFrom.startsWith("From ")) {
        currencyB = toFrom.substring("From ".length());
      } else {
        throw new AssertionError("Unexpected money changer bookmark format: " + name);
      }

      assertTrue(direction.equals("From") || direction.equals("To"),
          "Unexpected money changer direction in bookmark: " + name);

      String pairKey = pairKey(currencyA, currencyB);
      String variant = variantKey(direction, currencyA, currencyB);

      variantsByChanger
          .computeIfAbsent(changer, ignored -> new HashMap<>())
          .computeIfAbsent(pairKey, ignored -> new HashSet<>())
          .add(variant);
    }

    assertFalse(variantsByChanger.isEmpty(), "Expected at least one money changer entry");

    for (Map.Entry<String, Map<String, Set<String>>> changerEntry : variantsByChanger.entrySet()) {
      String changer = changerEntry.getKey();
      for (Map.Entry<String, Set<String>> pairEntry : changerEntry.getValue().entrySet()) {
        String[] currencies = pairEntry.getKey().split("\u0000", 2);
        String currencyOne = currencies[0];
        String currencyTwo = currencies[1];

        Set<String> expectedVariants = expectedVariants(currencyOne, currencyTwo);
        Set<String> actualVariants = pairEntry.getValue();

        assertTrue(actualVariants.containsAll(expectedVariants),
            "Missing money changer variants for " + changer + " pair " + currencyOne + " / "
                + currencyTwo + ": expected " + expectedVariants + " but saw " + actualVariants);
      }
    }
  }

  private static String pairKey(String currencyA, String currencyB) {
    Objects.requireNonNull(currencyA, "currencyA");
    Objects.requireNonNull(currencyB, "currencyB");
    TreeSet<String> sorted = new TreeSet<>();
    sorted.add(currencyA);
    sorted.add(currencyB);
    return String.join("\u0000", sorted);
  }

  private static String variantKey(String direction, String currencyA, String currencyB) {
    return direction + "|" + currencyA + "|" + currencyB;
  }

  private static Set<String> expectedVariants(String currencyA, String currencyB) {
    Set<String> expected = new HashSet<>();
    expected.add(variantKey("From", currencyA, currencyB));
    expected.add(variantKey("From", currencyB, currencyA));
    expected.add(variantKey("To", currencyA, currencyB));
    expected.add(variantKey("To", currencyB, currencyA));
    return expected;
  }
}
