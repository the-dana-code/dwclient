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
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class RoomNotePanel extends JPanel {
    private final RoomNoteService roomNoteService;
    private final JLabel roomNameLabel;
    private final JPanel northPanel;
    private final JTextArea notesArea;
    private final JScrollPane scrollPane;
    private String currentRoomId;
    private String currentRoomName;
    private Color currentBg;
    private Color currentFg;

    public RoomNotePanel(RoomNoteService roomNoteService) {
        super(new BorderLayout());
        this.roomNoteService = roomNoteService;

        roomNameLabel = new JLabel(" ");
        roomNameLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        roomNameLabel.setMinimumSize(new Dimension(0, 0));

        notesArea = new JTextArea();
        notesArea.setEditable(false);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);

        scrollPane = new JScrollPane(notesArea);
        scrollPane.setBorder(null);

        northPanel = new JPanel(new BorderLayout());
        northPanel.setOpaque(false);
        northPanel.add(roomNameLabel, BorderLayout.CENTER);

        add(northPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        notesArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showPopupMenu(e.getX(), e.getY());
                }
            }
        });
    }

    public void updateRoom(String roomId, String roomName) {
        this.currentRoomId = roomId;
        this.currentRoomName = roomName;
        roomNameLabel.setText(roomName != null ? roomName : " ");
        String notes = roomNoteService.getNotesForRoom(roomId);
        notesArea.setText(notes);
        notesArea.setCaretPosition(0);
    }

    private void showPopupMenu(int x, int y) {
        if (currentRoomId == null) return;
        JPopupMenu menu = new JPopupMenu();
        JMenuItem editItem = new JMenuItem("Edit Notes");
        editItem.addActionListener(e -> showEditDialog());
        menu.add(editItem);
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(menu, currentBg, currentFg);
        }
        menu.show(notesArea, x, y);
    }

    private void showEditDialog() {
        Window parentWindow = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(parentWindow, "Edit Room Notes", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout());

        JTextArea editArea = new JTextArea(notesArea.getText(), 10, 40);
        editArea.setLineWrap(true);
        editArea.setWrapStyleWord(true);
        JScrollPane editScroll = new JScrollPane(editArea);

        JButton saveBtn = new JButton("Save");
        saveBtn.addActionListener(e -> {
            String newNotes = editArea.getText();
            roomNoteService.updateNotesForRoom(currentRoomId, currentRoomName, newNotes);
            notesArea.setText(newNotes);
            dialog.dispose();
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dialog.dispose());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(saveBtn);
        btnPanel.add(cancelBtn);

        dialog.add(editScroll, BorderLayout.CENTER);
        dialog.add(btnPanel, BorderLayout.SOUTH);

        dialog.getRootPane().setDefaultButton(saveBtn);
        dialog.getRootPane().registerKeyboardAction(e -> cancelBtn.doClick(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        dialog.pack();
        dialog.setLocationRelativeTo(parentWindow);
        if (currentBg != null && currentFg != null) {
            updateDialogTheme(dialog, currentBg, currentFg);
        }
        dialog.setVisible(true);
    }

    public void updateTheme(Color bg, Color fg) {
        this.currentBg = bg;
        this.currentFg = fg;
        this.setBackground(bg);
        this.setForeground(fg);
        roomNameLabel.setOpaque(true);
        roomNameLabel.setBackground(bg);
        roomNameLabel.setForeground(fg);
        northPanel.setBackground(bg);
        notesArea.setBackground(bg);
        notesArea.setForeground(fg);
        notesArea.setCaretColor(fg);
        scrollPane.setBackground(bg);
        scrollPane.getViewport().setBackground(bg);
    }

    private void updateMenuTheme(JComponent menu, Color bg, Color fg) {
        menu.setBackground(bg);
        menu.setForeground(fg);
        for (Component c : menu.getComponents()) {
            if (c instanceof JMenuItem) {
                updateMenuItemTheme((JMenuItem) c, bg, fg);
            } else if (c instanceof JComponent) {
                c.setBackground(bg);
                c.setForeground(fg);
            }
        }
    }

    private void updateMenuItemTheme(JMenuItem item, Color bg, Color fg) {
        item.setBackground(bg);
        item.setForeground(fg);
        if (item instanceof JMenu menu) {
            for (int i = 0; i < menu.getItemCount(); i++) {
                JMenuItem subItem = menu.getItem(i);
                if (subItem != null) {
                    updateMenuItemTheme(subItem, bg, fg);
                }
            }
        }
    }

    private void updateDialogTheme(Component c, Color bg, Color fg) {
        if (c instanceof JPanel || c instanceof JDialog || c instanceof JScrollPane || c instanceof JViewport) {
            c.setBackground(bg);
        }
        if (c instanceof JLabel || c instanceof JTextArea || c instanceof JButton) {
            c.setBackground(bg);
            c.setForeground(fg);
            if (c instanceof JTextArea ta) {
                ta.setCaretColor(fg);
            }
        }

        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                updateDialogTheme(child, bg, fg);
            }
        }
    }
}
