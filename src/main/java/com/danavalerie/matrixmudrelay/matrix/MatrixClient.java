package com.danavalerie.matrixmudrelay.matrix;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

public final class MatrixClient {
    private static final Logger log = LoggerFactory.getLogger(MatrixClient.class);
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final HttpClient http;
    private final String baseUrl;     // e.g. https://example.com
    private final String accessToken; // bearer
    private final String userId;      // bot userId

    public MatrixClient(String baseUrl, String accessToken, String userId) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.accessToken = accessToken;
        this.userId = userId;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public String getUserId() {
        return userId;
    }

    /**
     * Join by room ID or alias. Returns canonical room_id.
     */
    public String joinAndResolveRoom(String roomOrAlias) throws IOException, InterruptedException, MatrixApiException {
        // POST /_matrix/client/v3/join/{roomIdOrAlias}
        String path = "/_matrix/client/v3/join/" + urlPath(roomOrAlias);
        JsonObject resp = postJson(path, new JsonObject());
        JsonElement roomId = resp.get("room_id");
        if (roomId == null || roomId.getAsString().isBlank()) {
            if (roomOrAlias.startsWith("!")) return roomOrAlias;
            return resolveRoomAlias(roomOrAlias);
        }
        return roomId.getAsString();
    }

    public String resolveRoomAlias(String roomAlias) throws IOException, InterruptedException, MatrixApiException {
        // GET /_matrix/client/v3/directory/room/{roomAlias}
        String path = "/_matrix/client/v3/directory/room/" + urlPath(roomAlias);
        JsonObject resp = get(path);
        JsonElement roomId = resp.get("room_id");
        if (roomId == null || roomId.getAsString().isBlank())
            throw new MatrixApiException(500, "No room_id in directory response");
        return roomId.getAsString();
    }

    /**
     * Create a server-side filter and return filter_id (spec-compliant usage for /sync filter parameter).
     */
    public String createFilter(JsonObject filter) throws IOException, InterruptedException, MatrixApiException {
        // POST /_matrix/client/v3/user/{userId}/filter
        String path = "/_matrix/client/v3/user/" + urlPath(userId) + "/filter";
        JsonObject resp = postJson(path, filter);
        JsonElement fid = resp.get("filter_id");
        if (fid == null || fid.getAsString().isBlank()) throw new MatrixApiException(500, "No filter_id in response");
        return fid.getAsString();
    }

    public SyncResponse sync(String since, int timeoutMs, String filterId)
            throws IOException, InterruptedException, MatrixApiException {
        StringBuilder qs = new StringBuilder();
        if (since != null && !since.isBlank()) {
            qs.append("since=").append(urlQuery(since)).append("&");
        }
        if (timeoutMs >= 0) {
            qs.append("timeout=").append(timeoutMs).append("&");
        }
        if (filterId != null && !filterId.isBlank()) {
            qs.append("filter=").append(urlQuery(filterId)).append("&");
        }
        String query = qs.length() == 0 ? "" : ("?" + qs.substring(0, qs.length() - 1));
        String path = "/_matrix/client/v3/sync" + query;

        JsonObject resp = get(path);
        return new SyncResponse(resp);
    }

    public String sendTextMessage(String roomId, String body, boolean notify)
            throws IOException, InterruptedException, MatrixApiException {
        // PUT /_matrix/client/v3/rooms/{roomId}/send/m.room.message/{txnId}
        String txnId = UUID.randomUUID().toString();
        String path = "/_matrix/client/v3/rooms/" + urlPath(roomId) + "/send/m.room.message/" + urlPath(txnId);

        JsonObject content = new JsonObject();
        content.addProperty("msgtype", notify ? "m.text" : "m.notice");
        String toSend = body.isEmpty() ? " " : body; // Sending a zero-length string causes problems
//        content.addProperty("format", "org.matrix.custom.html");
        content.addProperty("body", toSend);
//        content.addProperty("formatted_body", toSend);

        JsonObject resp = putJson(path, content);
        JsonElement eventId = resp.get("event_id");
        return eventId == null ? null : eventId.getAsString();
    }

    public static final class SyncResponse {
        public final JsonObject root;

        public SyncResponse(JsonObject root) {
            this.root = root;
        }

        public String nextBatch() {
            JsonElement nb = root.get("next_batch");
            return nb == null ? null : nb.getAsString();
        }
    }

    // Build a filter object that restricts timeline events to m.room.message in the given room.
    public static JsonObject buildRoomMessageFilter(String roomId) {
        JsonObject root = new JsonObject();
        JsonObject room = new JsonObject();
        root.add("room", room);

        // We'll keep the room list for efficiency, but let's see if including encrypted events helps.
        com.google.gson.JsonArray rooms = new com.google.gson.JsonArray();
        rooms.add(roomId);
        room.add("rooms", rooms);

        JsonObject timeline = new JsonObject();
        room.add("timeline", timeline);
        com.google.gson.JsonArray types = new com.google.gson.JsonArray();
        types.add("m.room.message");
        types.add("m.room.encrypted"); // Often enabled by default in Synapse private rooms
        timeline.add("types", types);
        timeline.addProperty("limit", 50);

        return root;
    }

    private JsonObject get(String path) throws IOException, InterruptedException, MatrixApiException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(70))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .header("User-Agent", "MatrixMudRelay/1.0")
                .GET()
                .build();
        logCurl(req, null);
        return send(req);
    }

    private JsonObject postJson(String path, JsonObject body) throws IOException, InterruptedException, MatrixApiException {
        String json = GSON.toJson(body);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("User-Agent", "MatrixMudRelay/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        logCurl(req, json);
        return send(req);
    }

    private JsonObject putJson(String path, JsonObject body) throws IOException, InterruptedException, MatrixApiException {
        String json = GSON.toJson(body);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("User-Agent", "MatrixMudRelay/1.0")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();
        logCurl(req, json);
        return send(req);
    }

    private void logCurl(HttpRequest req, String body) {
        if (true) {
            return; // disable
        }
        StringBuilder sb = new StringBuilder();
        sb.append("curl -X ").append(req.method()).append(" '").append(req.uri()).append("'");
        req.headers().map().forEach((name, values) -> {
            for (String value : values) {
                sb.append(" -H '").append(name).append(": ").append(value).append("'");
            }
        });
        if (body != null && !body.isEmpty()) {
            sb.append(" --data '").append(body.replace("'", "'\\''")).append("'");
        }
        log.info("Matrix request: {}", sb.toString());
    }

    private JsonObject send(HttpRequest req) throws IOException, InterruptedException, MatrixApiException {
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int sc = resp.statusCode();
        String body = resp.body() == null ? "" : resp.body();

//        log.info("Matrix response: status={} body={}", sc, body);

        if (sc / 100 != 2) {
            throw new MatrixApiException(sc, body);
        }
        if (body.isBlank()) return new JsonObject();
        try {
            return GSON.fromJson(body, JsonObject.class);
        } catch (Exception e) {
            throw new IOException("Failed to parse Matrix JSON: " + e.getMessage() + " body=" + body, e);
        }
    }

    private static String urlQuery(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String urlPath(String s) {
        // For Matrix identifiers (no spaces), URLEncoder is acceptable.
        // But we keep :, @, ! literal for better server compatibility in paths.
        return URLEncoder.encode(s, StandardCharsets.UTF_8)
                .replace("%3A", ":")
                .replace("%40", "@")
                .replace("%21", "!");
    }

    private static String stripTrailingSlash(String s) {
        if (s.endsWith("/")) return s.substring(0, s.length() - 1);
        return s;
    }
}
