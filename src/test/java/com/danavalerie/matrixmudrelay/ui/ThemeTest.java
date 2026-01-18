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

package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.core.RoomNoteService;
import com.danavalerie.matrixmudrelay.core.data.RoomButton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.Color;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ThemeTest {

    @Test
    public void testMudOutputPaneTheme() {
        MudOutputPane pane = new MudOutputPane();
        
        // Dark mode (inverted = true)
        pane.updateTheme(true);
        assertEquals(MapPanel.BACKGROUND_DARK, pane.getBackground());
        assertEquals(MapPanel.FOREGROUND_LIGHT, pane.getForeground());
        
        // Light mode (inverted = false) - should STILL be dark
        pane.updateTheme(false);
        assertEquals(MapPanel.BACKGROUND_DARK, pane.getBackground());
        assertEquals(MapPanel.FOREGROUND_LIGHT, pane.getForeground());
    }

    @Test
    public void testChitchatPaneTheme() {
        ChitchatPane pane = new ChitchatPane();
        
        // Dark mode
        pane.updateTheme(true);
        assertEquals(MapPanel.BACKGROUND_DARK, pane.getBackground());
        assertEquals(MapPanel.FOREGROUND_LIGHT, pane.getForeground());
        
        // Light mode - should STILL be dark
        pane.updateTheme(false);
        assertEquals(MapPanel.BACKGROUND_DARK, pane.getBackground());
        assertEquals(MapPanel.FOREGROUND_LIGHT, pane.getForeground());
    }

    @Test
    public void testStatsPanelTheme() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            StatsPanel panel = new StatsPanel();

            // Dark mode
            panel.updateTheme(true);
            assertEquals(MapPanel.BACKGROUND_DARK, panel.getBackground());

            // Light mode
            panel.updateTheme(false);
            assertEquals(MapPanel.BACKGROUND_LIGHT, panel.getBackground());
        });
    }

    @Test
    public void testRoomButtonBarPanelTheme(@TempDir Path tempDir) throws Exception {
        Path buttonsPath = tempDir.resolve("room-notes.json");
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            RoomNoteService service = new RoomNoteService(buttonsPath);
            RoomButtonBarPanel panel = new RoomButtonBarPanel(service, cmd -> {});
            
            Color bg = Color.BLACK;
            Color fg = Color.WHITE;
            panel.updateTheme(bg, fg);
            
            assertEquals(bg, panel.getBackground());
            assertEquals(fg, panel.getForeground());
            
            // Add a button and see if it has the theme
            service.addButton("room1", "Room 1", new RoomButton("Btn 1", "cmd 1"));
            panel.updateRoom("room1", "Room 1");
            
            assertEquals(1, panel.getComponentCount());
            assertEquals(bg, panel.getComponent(0).getBackground());
            assertEquals(fg, panel.getComponent(0).getForeground());
        });
    }

    @Test
    public void testRoomNotePanelTheme(@TempDir Path tempDir) throws Exception {
        Path notesPath = tempDir.resolve("room-notes.json");
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            RoomNoteService service = new RoomNoteService(notesPath);
            RoomNotePanel panel = new RoomNotePanel(service);
            
            Color bg = Color.BLACK;
            Color fg = Color.WHITE;
            panel.updateTheme(bg, fg);
            
            assertEquals(bg, panel.getBackground());
            assertEquals(fg, panel.getForeground());
        });
    }
}

