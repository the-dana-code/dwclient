package com.danavalerie.matrixmudrelay.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BotConfig {
    public Matrix matrix = new Matrix();
    public Mud mud = new Mud();

    // trigger -> list of lines to send to MUD (may include secrets)
    public Map<String, List<String>> aliases = new LinkedHashMap<>();

    public Transcript transcript = new Transcript();
    public Retry retry = new Retry();

    public static final class Matrix {
        public String homeserverUrl;           // e.g. https://myserver.danavalerie.com
        public String accessToken;             // SENSITIVE
        public String userId;                  // bot userId, e.g. @mudbot:myserver.danavalerie.com
        public String room;                    // roomId (!...) or alias (#...)
        public String controllingUserId;       // e.g. @dana:myserver.danavalerie.com

        public int syncTimeoutMs = 30000;
        public boolean ignoreInitialTimeline = true;

        // If true, bot replies to unauthorized senders in-room (default false to avoid noise)
        public boolean respondToUnauthorized = false;
    }

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

    public static final class Retry {
        public long initialBackoffMs = 500;
        public long maxBackoffMs = 5000;

        // 0 = infinite attempts
        public int maxAttempts = 0;
    }
}
