package com.danavalerie.matrixmudrelay.matrix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
        JsonNode resp = postJson(path, MAPPER.createObjectNode());
        JsonNode roomId = resp.get("room_id");
        if (roomId == null || roomId.asText().isBlank()) {
            if (roomOrAlias.startsWith("!")) return roomOrAlias;
            return resolveRoomAlias(roomOrAlias);
        }
        return roomId.asText();
    }

    public String resolveRoomAlias(String roomAlias) throws IOException, InterruptedException, MatrixApiException {
        // GET /_matrix/client/v3/directory/room/{roomAlias}
        String path = "/_matrix/client/v3/directory/room/" + urlPath(roomAlias);
        JsonNode resp = get(path);
        JsonNode roomId = resp.get("room_id");
        if (roomId == null || roomId.asText().isBlank()) throw new MatrixApiException(500, "No room_id in directory response");
        return roomId.asText();
    }

    /**
     * Create a server-side filter and return filter_id (spec-compliant usage for /sync filter parameter).
     */
    public String createFilter(ObjectNode filter) throws IOException, InterruptedException, MatrixApiException {
        // POST /_matrix/client/v3/user/{userId}/filter
        String path = "/_matrix/client/v3/user/" + urlPath(userId) + "/filter";
        JsonNode resp = postJson(path, filter);
        JsonNode fid = resp.get("filter_id");
        if (fid == null || fid.asText().isBlank()) throw new MatrixApiException(500, "No filter_id in response");
        return fid.asText();
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

        JsonNode resp = get(path);
        return new SyncResponse(resp);
    }

    public String sendTextMessage(String roomId, String body)
            throws IOException, InterruptedException, MatrixApiException {
        // PUT /_matrix/client/v3/rooms/{roomId}/send/m.room.message/{txnId}
        String txnId = UUID.randomUUID().toString();
        String path = "/_matrix/client/v3/rooms/" + urlPath(roomId)
                + "/send/m.room.message/" + urlPath(txnId);

        ObjectNode content = MAPPER.createObjectNode();
        content.put("msgtype", "m.text");
        content.put("body", body);

        JsonNode resp = putJson(path, content);
        JsonNode eventId = resp.get("event_id");
        return eventId == null ? null : eventId.asText();
    }

    public static final class SyncResponse {
        public final JsonNode root;
        public SyncResponse(JsonNode root) { this.root = root; }
        public String nextBatch() {
            JsonNode nb = root.get("next_batch");
            return nb == null ? null : nb.asText();
        }
    }

    // Build a filter object that restricts timeline events to m.room.message in the given room.
    public static ObjectNode buildRoomMessageFilter(String roomId) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode room = root.putObject("room");
        room.putArray("rooms").add(roomId);

        ObjectNode timeline = room.putObject("timeline");
        timeline.putArray("types").add("m.room.message");
        timeline.put("limit", 20);

        // Reduce noise
        room.putObject("state").putArray("types");
        room.putObject("ephemeral").putArray("types");
        room.putObject("account_data").putArray("types");
        root.putObject("presence").putArray("types");
        root.putObject("account_data").putArray("types");
        return root;
    }

    private JsonNode get(String path) throws IOException, InterruptedException, MatrixApiException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(70))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();
        return send(req);
    }

    private JsonNode postJson(String path, JsonNode body) throws IOException, InterruptedException, MatrixApiException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();
        return send(req);
    }

    private JsonNode putJson(String path, JsonNode body) throws IOException, InterruptedException, MatrixApiException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();
        return send(req);
    }

    private JsonNode send(HttpRequest req) throws IOException, InterruptedException, MatrixApiException {
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int sc = resp.statusCode();
        String body = resp.body() == null ? "" : resp.body();
        if (sc / 100 != 2) {
            throw new MatrixApiException(sc, body);
        }
        if (body.isBlank()) return MAPPER.createObjectNode();
        try {
            return MAPPER.readTree(body);
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
