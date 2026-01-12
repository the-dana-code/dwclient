package com.danavalerie.matrixmudrelay.config;

import java.util.List;
import java.util.Optional;

public record DeliveryRouteMappings(List<RouteEntry> routes) {
    public record RouteEntry(String npc, String location, List<Integer> target, List<String> commands) {}
    public record RouteTarget(int mapId, int x, int y) {}
    public record RoutePlan(RouteTarget target, List<String> commands) {}

    public Optional<RoutePlan> findRoutePlan(String npc, String location) {
        if (npc == null || location == null) {
            return Optional.empty();
        }
        return routes.stream()
                .filter(r -> npc.equals(r.npc()) && location.equals(r.location()))
                .filter(r -> r.target() != null && r.target().size() >= 3)
                .map(r -> new RoutePlan(
                        new RouteTarget(r.target().get(0), r.target().get(1), r.target().get(2)),
                        r.commands() == null ? List.of() : List.copyOf(r.commands())
                ))
                .findFirst();
    }
}
