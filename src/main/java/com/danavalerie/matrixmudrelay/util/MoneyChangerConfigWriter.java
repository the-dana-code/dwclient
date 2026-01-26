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

import com.danavalerie.matrixmudrelay.config.ClientConfig;
import com.danavalerie.matrixmudrelay.config.ConfigLoader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Scanner;

/**
 * Utility program to add a new money changer to config.json.
 */
public class MoneyChangerConfigWriter {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Enter the name of the exchange: ");
        String exchangeName = scanner.nextLine().trim();

        System.out.print("Enter the room id: ");
        String roomId = scanner.nextLine().trim();

        System.out.print("Enter the native currency of the exchange: ");
        String nativeCurrency = scanner.nextLine().trim();

        System.out.println("Enter additional currencies (one per line, blank line to finish):");
        java.util.List<String> additionalCurrenciesList = new java.util.ArrayList<>();
        while (true) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) {
                break;
            }
            additionalCurrenciesList.add(line);
        }
        String[] additionalCurrencies = additionalCurrenciesList.toArray(new String[0]);

        if (exchangeName.isEmpty() || nativeCurrency.isEmpty() || roomId.isEmpty()) {
            System.err.println("Exchange name, native currency, and room id are required.");
            return;
        }

        try {
            updateConfig(Path.of("config.json"), exchangeName, nativeCurrency, additionalCurrencies, roomId);
            System.out.println("Successfully updated config.json with new money changer entries.");
        } catch (Exception e) {
            System.err.println("Error updating config.json: " + e.getMessage());
            e.printStackTrace();
        } finally {
            com.danavalerie.matrixmudrelay.util.BackgroundSaver.shutdown();
        }
    }

    public static void updateConfig(Path configPath, String exchangeName, String nativeCurrency, String[] additionalCurrencies, String roomId) throws Exception {
        ClientConfig config = ConfigLoader.load(configPath).clientConfig();

        for (String otherCurrencyRaw : additionalCurrencies) {
            String otherCurrency = otherCurrencyRaw.trim();
            if (otherCurrency.isEmpty()) {
                continue;
            }

            addBookmark(config, "To", nativeCurrency, "From " + otherCurrency, exchangeName, roomId);
            addBookmark(config, "To", otherCurrency, "From " + nativeCurrency, exchangeName, roomId);
            addBookmark(config, "From", nativeCurrency, "To " + otherCurrency, exchangeName, roomId);
            addBookmark(config, "From", otherCurrency, "To " + nativeCurrency, exchangeName, roomId);
        }

        ConfigLoader.save(configPath, config).get();
    }

    private static void addBookmark(ClientConfig config, String direction, String currencyA, String toFromB, String exchangeName, String roomId) {
        String name = String.format("Money Changers/%s/%s/%s/%s", direction, currencyA, toFromB, exchangeName);
        for (ClientConfig.Bookmark b : config.bookmarks) {
            if (name.equals(b.name)) {
                b.roomId = roomId;
                return;
            }
        }
        config.bookmarks.add(new ClientConfig.Bookmark(name, roomId));
    }
}
