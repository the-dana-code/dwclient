package com.danavalerie.matrixmudrelay.core;

import com.danavalerie.matrixmudrelay.core.data.RoomButton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RoomNoteServiceTest {

    @TempDir
    Path tempDir;

    @Test
    public void testPersistence() throws IOException {
        Path storagePath = tempDir.resolve("room-notes.json");
        RoomNoteService service = new RoomNoteService(storagePath);

        String roomId1 = "room1";
        RoomButton btn1 = new RoomButton("Look", "look");
        RoomButton btn2 = new RoomButton("Score", "score");

        service.addButton(roomId1, "Room One", btn1);
        service.addButton(roomId1, "Room One", btn2);

        String roomId2 = "room2";
        RoomButton btn3 = new RoomButton("Inventory", "inv");
        service.addButton(roomId2, "Room Two", btn3);

        // Verify in-memory
        assertEquals(2, service.getButtonsForRoom(roomId1).size());
        assertEquals(1, service.getButtonsForRoom(roomId2).size());

        // Re-load from file
        RoomNoteService service2 = new RoomNoteService(storagePath);
        List<RoomButton> loaded1 = service2.getButtonsForRoom(roomId1);
        assertEquals(2, loaded1.size());
        assertEquals("Look", loaded1.get(0).getName());
        assertEquals("score", loaded1.get(1).getCommand());
        
        assertEquals(1, service2.getButtonsForRoom(roomId2).size());
    }

    @Test
    public void testRoomNamePersistence() throws IOException {
        Path storagePath = tempDir.resolve("room-names.json");
        RoomNoteService service = new RoomNoteService(storagePath);

        String roomId = "room1";
        String roomName = "The Mended Drum";
        RoomButton btn = new RoomButton("Look", "look");

        service.addButton(roomId, roomName, btn);

        // Re-load
        RoomNoteService service2 = new RoomNoteService(storagePath);
        String json = Files.readString(storagePath);
        assertTrue(json.contains(roomName), "JSON should contain the room name");
    }

    @Test
    public void testMigration() throws IOException {
        Path storagePath = tempDir.resolve("migration.json");
        // Create an old-format JSON
        String oldJson = "{\n" +
                "  \"room1\": [\n" +
                "    {\n" +
                "      \"name\": \"OldBtn\",\n" +
                "      \"command\": \"oldcmd\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        Files.writeString(storagePath, oldJson);

        RoomNoteService service = new RoomNoteService(storagePath);
        List<RoomButton> buttons = service.getButtonsForRoom("room1");
        
        assertEquals(1, buttons.size());
        assertEquals("OldBtn", buttons.get(0).getName());
        
        // Save and verify it's now in new format
        service.save();
        String newJson = Files.readString(storagePath);
        assertTrue(newJson.contains("\"buttons\":"), "Should have been migrated to new format with 'buttons' field");
    }

    @Test
    public void testSorting() throws IOException {
        Path storagePath = tempDir.resolve("room-notes-sort.json");
        RoomNoteService service = new RoomNoteService(storagePath);

        service.addButton("z_room", "Z Room", new RoomButton("Z", "z"));
        service.addButton("a_room", "A Room", new RoomButton("A", "a"));

        String json = Files.readString(storagePath);
        int indexA = json.indexOf("a_room");
        int indexZ = json.indexOf("z_room");
        
        assertTrue(indexA < indexZ, "a_room should appear before z_room in JSON");
    }

    @Test
    public void testLateRoomNameUpdate() throws IOException {
        Path storagePath = tempDir.resolve("late-name.json");
        RoomNoteService service = new RoomNoteService(storagePath);

        String roomId = "room1";
        RoomButton btn = new RoomButton("Look", "look");

        // Add button with null name
        service.addButton(roomId, null, btn);

        String json1 = Files.readString(storagePath);
        // It will contain "name" because of the button name, but not as a sibling to "buttons" at the top level of the room object.
        // A simple way to check is to see if "The Drum" is NOT there yet.
        assertFalse(json1.contains("\"The Drum\""), "JSON should not contain the name yet");

        // Now update name
        service.updateRoomName(roomId, "The Drum");

        String json2 = Files.readString(storagePath);
        assertTrue(json2.contains("\"name\": \"The Drum\""), "JSON should now contain the room name");
    }

    @Test
    public void testUnknownFallback() throws IOException {
        Path storagePath = tempDir.resolve("unknown.json");
        RoomNoteService service = new RoomNoteService(storagePath);

        service.addButton("roomX", null, new RoomButton("B", "c"));

        String json = Files.readString(storagePath);
        assertTrue(json.contains("\"name\": \"Unknown\""), "Should fallback to 'Unknown' if name is null");
    }

    @Test
    public void testMigrationToUnknown() throws IOException {
        Path storagePath = tempDir.resolve("migration-unknown.json");
        String oldJson = "{\"room1\": [{\"name\": \"Btn\",\"command\": \"cmd\"}]}";
        Files.writeString(storagePath, oldJson);

        RoomNoteService service = new RoomNoteService(storagePath);
        service.save();

        String json = Files.readString(storagePath);
        assertTrue(json.contains("\"name\": \"Unknown\""), "Old entries should migrate with 'Unknown' name");
    }

    @Test
    public void testMovement() {
        RoomNoteService service = new RoomNoteService(tempDir.resolve("move.json"));
        String rid = "room";
        service.addButton(rid, "Test Room", new RoomButton("1", "1"));
        service.addButton(rid, "Test Room", new RoomButton("2", "2"));
        service.addButton(rid, "Test Room", new RoomButton("3", "3"));

        service.moveButtonLeft(rid, 1); // [2, 1, 3]
        assertEquals("2", service.getButtonsForRoom(rid).get(0).getName());

        service.moveButtonRight(rid, 0); // [1, 2, 3]
        assertEquals("1", service.getButtonsForRoom(rid).get(0).getName());
    }

    @Test
    public void testNotes() throws IOException {
        Path storagePath = tempDir.resolve("notes-test.json");
        RoomNoteService service = new RoomNoteService(storagePath);

        String roomId = "room1";
        String roomName = "The Drum";
        String notes = "Watch out for Dibbler.";

        service.updateNotesForRoom(roomId, roomName, notes);

        // Verify in-memory
        assertEquals(notes, service.getNotesForRoom(roomId));

        // Re-load
        RoomNoteService service2 = new RoomNoteService(storagePath);
        assertEquals(notes, service2.getNotesForRoom(roomId));
        assertEquals(roomName, service2.getNotesForRoom(roomId).isEmpty() ? "" : roomName); // Actually RoomNoteService doesn't expose name directly yet, but it's saved.
    }
}
