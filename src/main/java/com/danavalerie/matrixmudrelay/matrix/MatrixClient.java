package com.danavalerie.matrixmudrelay.matrix;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

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
    private static final Gson GSON = new GsonBuilder().create();

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
        if (roomId == null || roomId.getAsString().isBlank()) throw new MatrixApiException(500, "No room_id in directory response");
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

    public String sendTextMessage(String roomId, String body)
            throws IOException, InterruptedException, MatrixApiException {
        // PUT /_matrix/client/v3/rooms/{roomId}/send/m.room.message/{txnId}
        String txnId = UUID.randomUUID().toString();
        String path = "/_matrix/client/v3/rooms/" + urlPath(roomId)
                + "/send/m.room.message/" + urlPath(txnId);

        JsonObject content = new JsonObject();
        content.addProperty("msgtype", "m.text");
        content.addProperty("body", body);

        JsonObject resp = putJson(path, content);
        JsonElement eventId = resp.get("event_id");
        return eventId == null ? null : eventId.getAsString();
    }

    public static final class SyncResponse {
        public final JsonObject root;
        public SyncResponse(JsonObject root) { this.root = root; }
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

        com.google.gson.JsonArray rooms = new com.google.gson.JsonArray();
        rooms.add(roomId);
        room.add("rooms", rooms);

        JsonObject timeline = new JsonObject();
        room.add("timeline", timeline);
        com.google.gson.JsonArray types = new com.google.gson.JsonArray();
        types.add("m.room.message");
        timeline.add("types", types);
        timeline.addProperty("limit", 20);

        // Reduce noise
        JsonObject state = new JsonObject();
        state.add("types", new com.google.gson.JsonArray());
        room.add("state", state);

        JsonObject ephemeral = new JsonObject();
        ephemeral.add("types", new com.google.gson.JsonArray());
        room.add("ephemeral", ephemeral);

        JsonObject accountData = new JsonObject();
        accountData.add("types", new com.google.gson.JsonArray());
        room.add("account_data", accountData);

        JsonObject presence = new JsonObject();
        presence.add("types", new com.google.gson.JsonArray());
        root.add("presence", presence);

        JsonObject rootAccountData = new JsonObject();
        rootAccountData.add("types", new com.google.gson.JsonArray());
        root.add("account_data", rootAccountData);

        return root;
    }

    private JsonObject get(String path) throws IOException, InterruptedException, MatrixApiException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(70))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();
        return send(req);
    }

    private JsonObject postJson(String path, JsonObject body) throws IOException, InterruptedException, MatrixApiException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();
        return send(req);
    }

    private JsonObject putJson(String path, JsonObject body) throws IOException, InterruptedException, MatrixApiException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();
        return send(req);
    }

    private JsonObject send(HttpRequest req) throws IOException, InterruptedException, MatrixApiException {
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int sc = resp.statusCode();
        String body = resp.body() == null ? "" : resp.body();
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
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String s) {
        if (s.endsWith("/")) return s.substring(0, s.length() - 1);
        return s;
    }
}
