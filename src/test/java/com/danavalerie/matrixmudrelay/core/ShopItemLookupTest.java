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

import com.danavalerie.matrixmudrelay.core.data.RoomData;
import com.danavalerie.matrixmudrelay.core.data.ShopItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class ShopItemLookupTest {

    @Test
    void testGlobalShopItemLookupFallback() {
        // We'll use a subclass to provide the data without loading from files
        MapDataService dataService = new MapDataService() {
            private final Map<String, RoomData> testRooms = new TreeMap<>();
            
            {
                String roomId = "shop-room-id";
                RoomData room = new RoomData();
                room.setRoomId(roomId);
                ShopItem shopItem = new ShopItem("old holey sock", "endless entertainment for foreign children");
                room.setShopItems(List.of(shopItem));
                testRooms.put(roomId, room);
            }

            @Override
            public Map<String, RoomData> getRooms() {
                return testRooms;
            }

            @Override
            public RoomData getRoom(String roomId) {
                return testRooms.get(roomId);
            }
        };

        RoomMapService routeMapService = new RoomMapService(dataService);
        StoreInventoryTracker inventoryTracker = new StoreInventoryTracker();

        // Simulate inventory in the shop
        String inventory = "The following items are for sale:\n" +
                "   N: endless entertainment for foreign children for DjToon 3.68 (one left).\n";
        inventoryTracker.ingest(inventory);

        String itemName = "old holey sock";
        
        // 1. Test when currentRoomId is correct
        assertTrue(routeMapService.findShopItem("shop-room-id", itemName).isPresent());

        // 2. Test global lookup fallback logic
        List<ShopItem> globalItems = routeMapService.findShopItemsGlobally(itemName);
        assertFalse(globalItems.isEmpty());
        assertEquals(1, globalItems.size());
        assertEquals("endless entertainment for foreign children", globalItems.get(0).getShopName());
        
        String searchName = null;
        for (ShopItem si : globalItems) {
            if (inventoryTracker.findMatch(si.getShopName()).isPresent()) {
                searchName = si.getShopName();
                break;
            }
        }
        
        assertEquals("endless entertainment for foreign children", searchName);
    }
}
