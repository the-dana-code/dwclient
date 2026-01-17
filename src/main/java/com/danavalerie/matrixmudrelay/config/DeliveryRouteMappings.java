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

