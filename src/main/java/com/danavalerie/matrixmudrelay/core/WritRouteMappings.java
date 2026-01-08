package com.danavalerie.matrixmudrelay.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class WritRouteMappings {
    public record RouteTarget(int mapId, int x, int y) {}

    private record RouteKey(String npcName, String locationName) {}

    private static final Map<RouteKey, RouteTarget> ROUTES = buildRoutes();

    private WritRouteMappings() {
    }

    public static Optional<RouteTarget> findRoute(String npcName, String locationName) {
        if (npcName == null || locationName == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(ROUTES.get(new RouteKey(npcName, locationName)));
    }

    private static Map<RouteKey, RouteTarget> buildRoutes() {
        Map<RouteKey, RouteTarget> routes = new HashMap<>();
        routes.put(
                new RouteKey(
                        "Mardi",
                        "Masqueparade on Phedre Road"
                ),
                new RouteTarget(7, 328, 68)
        );
        routes.put(
                new RouteKey(
                        "the shopkeeper",
                        "the clothing shop on Ettercap Street"
                ),
                new RouteTarget(1, 588, 1016)
        );
        routes.put(
                new RouteKey(
                        "the young lady",
                        "the Souvenir Shop on Prouts"
                ),
                new RouteTarget(1, 603, 438)
        );
        routes.put(
                new RouteKey(
                        "Jenny Tawdry",
                        "Tawdry Things on the Maudlin Bridge"
                ),
                new RouteTarget(1, 805, 480)
        );
        return Collections.unmodifiableMap(routes);
    }
}
