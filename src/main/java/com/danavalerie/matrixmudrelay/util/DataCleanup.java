package com.danavalerie.matrixmudrelay.util;

import com.danavalerie.matrixmudrelay.config.ClientConfig;
import com.danavalerie.matrixmudrelay.config.ConfigLoader;
import com.danavalerie.matrixmudrelay.config.DeliveryRouteMappings;
import com.danavalerie.matrixmudrelay.core.MapDataService;
import com.danavalerie.matrixmudrelay.core.RoomMapService;
import com.danavalerie.matrixmudrelay.core.RoomNoteService;
import com.danavalerie.matrixmudrelay.core.UULibraryService;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Utility to load all JSON data and save it back out to ensure consistent formatting
 * and remove any unused fields.
 */
public class DataCleanup {
    public static void main(String[] args) {
        try {
            System.out.println("Starting data cleanup...");

            // Initialize services
            MapDataService mapDataService = new MapDataService();
            RoomMapService roomMapService = new RoomMapService(mapDataService);

            // 1. MapDataService (rooms.json, npcs.json, user_data.json)
            System.out.println("Cleaning MapData (rooms, items, npcs, etc.)...");
            mapDataService.saveAll();

            // 2. RoomNoteService (room-notes.json)
            System.out.println("Cleaning RoomNotes...");
            RoomNoteService roomNoteService = new RoomNoteService(Paths.get("room-notes.json"));
            roomNoteService.save();

            // 3. UULibraryService (uu_library.json)
            System.out.println("Cleaning UULibrary...");
            UULibraryService uuLibraryService = UULibraryService.getInstance();
            uuLibraryService.saveMap();

            // 4. DeliveryRouteMappings (delivery-routes.json)
            System.out.println("Cleaning DeliveryRoutes and converting coordinates...");
            Path routesPath = Paths.get("delivery-routes.json");
            DeliveryRouteMappings routes = ConfigLoader.loadRoutes(routesPath);
            DeliveryRouteMappings migratedRoutes = ConfigLoader.convertRoutesToRoomIds(routes, roomMapService);
            
            // Sort entries for consistency
            List<DeliveryRouteMappings.RouteEntry> sortedEntries = new ArrayList<>(migratedRoutes.routes());
            sortedEntries.sort(Comparator.comparing(DeliveryRouteMappings.RouteEntry::npc, Comparator.nullsFirst(String::compareTo))
                    .thenComparing(DeliveryRouteMappings.RouteEntry::location, Comparator.nullsFirst(String::compareTo)));
            ConfigLoader.saveRoutes(routesPath, new DeliveryRouteMappings(sortedEntries));

            // 5. BotConfig (config.json)
            System.out.println("Cleaning Config and converting coordinates...");
            Path configPath = Paths.get("config.json");
            ClientConfig config = ConfigLoader.load(configPath);
            ConfigLoader.convertCoordinatesToRoomIds(config, roomMapService);
            ConfigLoader.save(configPath, config);

            System.out.println("Waiting for background saves to complete...");
            BackgroundSaver.waitForIdle();
            BackgroundSaver.shutdown();

            System.out.println("Data cleanup finished successfully.");
        } catch (Exception e) {
            System.err.println("Data cleanup failed!");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
