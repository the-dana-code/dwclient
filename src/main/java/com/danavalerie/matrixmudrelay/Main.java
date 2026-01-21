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

package com.danavalerie.matrixmudrelay;

import com.danavalerie.matrixmudrelay.config.BotConfig;
import com.danavalerie.matrixmudrelay.config.ConfigLoader;
import com.danavalerie.matrixmudrelay.config.DeliveryRouteMappings;
import com.danavalerie.matrixmudrelay.ui.DesktopClientFrame;
import com.danavalerie.matrixmudrelay.util.BackgroundSaver;
import java.nio.file.Path;

public final class Main {
    public static void main(String[] args) throws Exception {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down, waiting for background saves...");
            BackgroundSaver.shutdown();
        }, "Shutdown-Saver-Hook"));

        Path configPath = Path.of("config.json");
        BotConfig cfg = ConfigLoader.load(configPath);
        Path routesPath = configPath.resolveSibling("delivery-routes.json");
        DeliveryRouteMappings routes = ConfigLoader.loadRoutes(routesPath);

        com.danavalerie.matrixmudrelay.core.RoomMapService mapService =
                new com.danavalerie.matrixmudrelay.core.RoomMapService("database.db");

        boolean cfgChanged = ConfigLoader.convertCoordinatesToRoomIds(cfg, mapService);
        if (cfgChanged) {
            ConfigLoader.save(configPath, cfg);
        }

        DeliveryRouteMappings convertedRoutes = ConfigLoader.convertRoutesToRoomIds(routes, mapService);
        if (convertedRoutes != routes) {
            ConfigLoader.saveRoutes(routesPath, convertedRoutes);
            routes = convertedRoutes;
        }

        DesktopClientFrame.launch(cfg, configPath, routes, mapService);
    }
}

