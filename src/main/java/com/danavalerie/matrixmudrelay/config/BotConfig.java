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
    public Map<String, CharacterTeleports> teleports = new LinkedHashMap<>();

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
    }

    public static final class Bookmark {
        public String name;
        public int mapId;
        public int x;
        public int y;

        public Bookmark() {}
        public Bookmark(String name, int mapId, int x, int y) {
            this.name = name;
            this.mapId = mapId;
            this.x = x;
            this.y = y;
        }
    }

    public static final class CharacterTeleports {
        public boolean reliable = true;
        public List<TeleportLocation> locations = new ArrayList<>();
    }

    public static final class TeleportLocation {
        public String command;
        public int mapId;
        public int x;
        public int y;

        public TeleportLocation() {}
        public TeleportLocation(String command, int mapId, int x, int y) {
            this.command = command;
            this.mapId = mapId;
            this.x = x;
            this.y = y;
        }
    }
}
