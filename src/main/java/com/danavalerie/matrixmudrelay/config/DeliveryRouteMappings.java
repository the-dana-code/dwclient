package com.danavalerie.matrixmudrelay.config;

import java.util.List;
import java.util.Optional;

public record DeliveryRouteMappings(List<RouteEntry> routes) {
    public record RouteEntry(String npc, String location, List<Integer> target) {}
    public record RouteTarget(int mapId, int x, int y) {}

    public Optional<RouteTarget> findRoute(String npc, String location) {
        if (npc == null || location == null) {
            return Optional.empty();
        }
        return routes.stream()
                .filter(r -> npc.equals(r.npc()) && location.equals(r.location()))
                .filter(r -> r.target() != null && r.target().size() >= 3)
                .map(r -> new RouteTarget(r.target().get(0), r.target().get(1), r.target().get(2)))
                .findFirst();
    }
}
