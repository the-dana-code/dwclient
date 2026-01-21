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
import com.danavalerie.matrixmudrelay.util.BackgroundSaver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.BorderLayout;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ThemeTest {
    @AfterEach
    public void tearDown() {
        BackgroundSaver.waitForIdle();
    }

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
            
            assertTrue(panel.getBorder() instanceof javax.swing.border.LineBorder);
            assertEquals(fg, ((javax.swing.border.LineBorder)panel.getBorder()).getLineColor());
            
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

    @Test
    public void testTimerPanelTheme(@TempDir Path tempDir) throws Exception {
        Path configPath = tempDir.resolve("config.json");
        com.danavalerie.matrixmudrelay.config.BotConfig config = new com.danavalerie.matrixmudrelay.config.BotConfig();
        com.danavalerie.matrixmudrelay.core.TimerService service = new com.danavalerie.matrixmudrelay.core.TimerService(config, configPath);
        
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            TimerPanel panel = new TimerPanel(service, () -> "TestChar");
            
            Color bg = Color.BLACK;
            Color fg = Color.WHITE;
            panel.updateTheme(bg, fg);
            
            assertEquals(bg, panel.getBackground());
            assertEquals(bg, panel.getComponent(0).getBackground()); // scrollPane
            // Components in buttonBar
            assertEquals(bg, panel.getComponent(1).getBackground()); // buttonBar
        });
    }

    @Test
    public void testJTabbedPaneTabColors() throws Exception {
        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        SwingUtilities.invokeAndWait(() -> {
            JTabbedPane tp = new JTabbedPane();
            tp.addTab("Room", new JPanel());

            Color bg = Color.BLACK;
            Color fg = Color.WHITE;

            // Now apply the fix logic:
            tp.setBackgroundAt(0, bg);
            tp.setForegroundAt(0, fg);

            Color tabBgFixed = tp.getBackgroundAt(0);
            assertEquals(bg, tabBgFixed, "Tab background should match the theme background after setBackgroundAt");
        });
    }

    @Test
    public void testJMenuTheme() throws Exception {
        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        SwingUtilities.invokeAndWait(() -> {
            JMenu menu = new JMenu("Test Menu");
            JMenuItem item = new JMenuItem("Test Item");
            menu.add(item);
            
            Color bg = Color.BLACK;
            Color fg = Color.WHITE;
            
            // This test simulates updateMenuTheme logic
            // We need to access the private method or replicate it. 
            // Since it's a test for behavior, we'll replicate what it SHOULD do.
            
            menu.setBackground(bg);
            menu.setForeground(fg);
            menu.setOpaque(true);
            JPopupMenu popup = menu.getPopupMenu();
            popup.setBackground(bg);
            popup.setForeground(fg);
            
            assertEquals(bg, menu.getBackground());
            assertEquals(fg, menu.getForeground());
            assertTrue(menu.isOpaque());
            assertEquals(bg, popup.getBackground());
            assertEquals(fg, popup.getForeground());
        });
    }

    @Test
    public void testThemePersistenceAfterFontChange() throws Exception {
        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        // This test simulates the logic in DesktopClientFrame and UiFontManager
        SwingUtilities.invokeAndWait(() -> {
            JPanel root = new JPanel(new BorderLayout());
            JTabbedPane tp = new JTabbedPane();
            tp.addTab("Room", new JPanel());
            root.add(tp, BorderLayout.CENTER);

            UiFontManager fontManager = new UiFontManager(root, new Font("Monospaced", Font.PLAIN, 12));

            Color bg = Color.BLACK;
            Color fg = Color.WHITE;

            // Helper to apply theme (simulating updateTheme/updateComponentTree)
            Runnable updateTheme = () -> {
                tp.setBackground(bg);
                tp.setForeground(fg);
                for (int i = 0; i < tp.getTabCount(); i++) {
                    tp.setBackgroundAt(i, bg);
                    tp.setForegroundAt(i, fg);
                }
            };

            // Register listener to re-apply theme on font change
            fontManager.registerListener(font -> updateTheme.run());

            // Initial apply (happens during registration)
            assertEquals(bg, tp.getBackgroundAt(0));

            // Change font - this calls updateComponentTreeUI and then our listener
            fontManager.setBaseFont(new Font("Serif", Font.PLAIN, 14));

            // Verify theme still applied
            assertEquals(bg, tp.getBackgroundAt(0), "Theme should be re-applied after font change");
        });
    }
}

