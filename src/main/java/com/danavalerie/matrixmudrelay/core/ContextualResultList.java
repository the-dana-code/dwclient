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

import java.util.List;

public record ContextualResultList(String title,
                                   List<ContextualResult> results,
                                   String emptyMessage,
                                   String footer) {
    public ContextualResultList {
        title = title == null ? "" : title;
        results = results == null ? List.of() : List.copyOf(results);
        emptyMessage = emptyMessage == null ? "" : emptyMessage;
        footer = footer == null ? "" : footer;
    }

    public record ContextualResult(String label, String command, String mapCommand, boolean isSeparator) {
        public ContextualResult {
            label = label == null ? "" : label;
            command = command == null ? "" : command;
            mapCommand = mapCommand == null ? "" : mapCommand;
        }

        public ContextualResult(String label, String command, String mapCommand) {
            this(label, command, mapCommand, false);
        }

        public static ContextualResult separator() {
            return new ContextualResult("", "", "", true);
        }
    }
}

