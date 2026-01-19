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

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BotConfig {
    public Mud mud = new Mud();

    // trigger -> list of lines to send to MUD (may include secrets)
    public Map<String, List<String>> aliases = new LinkedHashMap<>();

    public Transcript transcript = new Transcript();

    public Ui ui = new Ui();
    public List<Bookmark> bookmarks = new ArrayList<>();

    public Map<String, CharacterConfig> characters = new LinkedHashMap<>();

    /** @deprecated Use characters.teleports instead. This field is for migration. */
    @Deprecated
    public Map<String, CharacterTeleports> teleports;

    public static final class Mud {
        public String host;
        public int port;
        public String charset = "UTF-8";
        public int connectTimeoutMs = 10000;
    }

    public static final class Transcript {
        public boolean enabled = false;
        public String directory = "./transcripts";
        public long maxBytes = 10 * 1024 * 1024;
        public int maxFiles = 20;
    }

    public static final class Ui {
        public String fontFamily;
        public Integer fontSize;
        public Integer mapZoomPercent;
        public Boolean invertMap;
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

        public Bookmark() {}
        public Bookmark(String name, String roomId) {
            this.name = name;
            this.roomId = roomId;
        }
    }

    public static final class CharacterConfig {
        public CharacterTeleports teleports = new CharacterTeleports();
        public Map<String, TimerData> timers = new LinkedHashMap<>();
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
        public List<TeleportLocation> locations = new ArrayList<>();
    }

    public static final class TeleportLocation {
        public String command;
        public String roomId;
        public int[] target;

        public TeleportLocation() {}
        public TeleportLocation(String command, String roomId) {
            this.command = command;
            this.roomId = roomId;
        }
    }
}

