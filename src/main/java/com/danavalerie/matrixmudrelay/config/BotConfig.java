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

import com.danavalerie.matrixmudrelay.util.CaseInsensitiveLinkedHashMap;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BotConfig {
    public Mud mud = new Mud();

    public boolean useTeleports = true;

    public Ui ui = new Ui();
    public List<Bookmark> bookmarks = new ArrayList<>();

    public Map<String, CharacterConfig> characters = new CaseInsensitiveLinkedHashMap<>();

    /** @deprecated Use characters.teleports instead. This field is for migration. */
    @Deprecated
    public Map<String, CharacterTeleports> teleports;

    public static final class Mud {
        public String host;
        public int port;
        public String charset = "UTF-8";
        public int connectTimeoutMs = 10000;
    }


    public static final class Ui {
        public String fontFamily;
        public Integer fontSize;
        public Integer mapZoomPercent;
        public Boolean invertMap;
        public Integer windowWidth;
        public Integer windowHeight;
        public Boolean windowMaximized;
        public Double mudMapSplitRatio;
        public Double mapNotesSplitRatio;
        public Double chitchatTimerSplitRatio;
        public Double outputSplitRatio;
        public List<Integer> timerColumnWidths;
    }

    public static final class Bookmark {
        public String name;
        public String roomId;
        public int[] target;
        /** @deprecated Use flattened name with slashes instead. This field is for migration. */
        @Deprecated
        public List<Bookmark> bookmarks;

        public Bookmark() {}
        public Bookmark(String name, String roomId) {
            this.name = name;
            this.roomId = roomId;
        }
    }

    public static final class CharacterConfig {
        public CharacterTeleports teleports = new CharacterTeleports();
        public Map<String, TimerData> timers = new LinkedHashMap<>();
        public List<Integer> gpRateSamples = new ArrayList<>();
        public List<Integer> hpRateSamples = new ArrayList<>();
        public UULibraryState uuLibrary;
    }

    public static final class UULibraryState {
        public int row;
        public int col;
        public String orientation;

        public UULibraryState() {}
        public UULibraryState(int row, int col, String orientation) {
            this.row = row;
            this.col = col;
            this.orientation = orientation;
        }
    }

    public static final class TimerData {
        public long expirationTime;
        public long durationMs;

        public TimerData() {}
        public TimerData(long expirationTime, long durationMs) {
            this.expirationTime = expirationTime;
            this.durationMs = durationMs;
        }
    }

    public static final class CharacterTeleports {
        public boolean reliable = true;
        public boolean outdoorOnly = false;
        public int speedwalkingPenalty = 8;
        public List<TeleportLocation> locations = new ArrayList<>();
    }

    public static final class TeleportLocation {
        public String name;
        public String command;
        public String roomId;
        public int[] target;

        public TeleportLocation() {}
        public TeleportLocation(String command, String roomId) {
            this.command = command;
            this.roomId = roomId;
        }

        public TeleportLocation(String name, String command, String roomId) {
            this.name = name;
            this.command = command;
            this.roomId = roomId;
        }
    }
}
