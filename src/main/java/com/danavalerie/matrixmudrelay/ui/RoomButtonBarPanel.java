package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.core.RoomNoteService;
import com.danavalerie.matrixmudrelay.core.data.RoomButton;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

public class RoomButtonBarPanel extends JPanel {
    private final RoomNoteService roomButtonService;
    private final Consumer<String> commandSubmitter;
    private String currentRoomId;
    private String currentRoomName;
    private Color currentBg;
    private Color currentFg;

    public RoomButtonBarPanel(RoomNoteService roomButtonService, Consumer<String> commandSubmitter) {
        super(new FlowLayout(FlowLayout.LEFT, 0, 0));
        this.roomButtonService = roomButtonService;
        this.commandSubmitter = commandSubmitter;
        this.setBorder(null);

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    showPanelPopupMenu(e.getX(), e.getY());
                }
            }
        });
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension ps = super.getPreferredSize();
        JButton dummy = new JButton("X");
        if (getFont() != null) {
            dummy.setFont(getFont());
        }
        int buttonHeight = dummy.getPreferredSize().height;
        return new Dimension(ps.width, Math.max(ps.height, buttonHeight));
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    public void updateRoom(String roomId, String roomName) {
        this.currentRoomId = roomId;
        this.currentRoomName = roomName;
        roomButtonService.updateRoomName(roomId, roomName);
        refreshButtons();
    }

    private void refreshButtons() {
        removeAll();
        if (currentRoomId != null) {
            List<RoomButton> buttons = roomButtonService.getButtonsForRoom(currentRoomId);
            for (int i = 0; i < buttons.size(); i++) {
                RoomButton rb = buttons.get(i);
                JButton btn = new JButton(rb.getName());
                btn.setToolTipText(rb.getCommand());
                btn.addActionListener(e -> commandSubmitter.accept(rb.getCommand()));
                if (currentBg != null) {
                    btn.setBackground(currentBg);
                }
                if (currentFg != null) {
                    btn.setForeground(currentFg);
                }
                
                int index = i;
                btn.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (SwingUtilities.isRightMouseButton(e)) {
                            showButtonPopupMenu(btn, index, e.getX(), e.getY());
                        }
                    }
                });
                add(btn);
            }
        }
        revalidate();
        repaint();
    }

    private void showPanelPopupMenu(int x, int y) {
        if (currentRoomId == null) return;
        JPopupMenu menu = new JPopupMenu();
        JMenuItem addBtn = new JMenuItem("Add Button");
        addBtn.addActionListener(e -> showEditDialog(null, -1));
        menu.add(addBtn);
        if (currentBg != null && currentFg != null) {
            updateMenuTheme(menu, currentBg, currentFg);
        }
        menu.show(this, x, y);
    }

    private void showButtonPopupMenu(JButton btn, int index, int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        
        JMenuItem editBtn = new JMenuItem("Edit");
        editBtn.addActionListener(e -> {
            RoomButton rb = roomButtonService.getButtonsForRoom(currentRoomId).get(index);
            showEditDialog(rb, index);
        });
        menu.add(editBtn);

        JMenuItem removeBtn = new JMenuItem("Remove");
        removeBtn.addActionListener(e -> {
            roomButtonService.removeButton(currentRoomId, index);
            refreshButtons();
        });
        menu.add(removeBtn);

        menu.addSeparator();

        JMenuItem moveLeft = new JMenuItem("Move Left");
        moveLeft.setEnabled(index > 0);
        moveLeft.addActionListener(e -> {
            roomButtonService.moveButtonLeft(currentRoomId, index);
            refreshButtons();
        });
        menu.add(moveLeft);

        JMenuItem moveRight = new JMenuItem("Move Right");
        int size = roomButtonService.getButtonsForRoom(currentRoomId).size();
        moveRight.setEnabled(index < size - 1);
        moveRight.addActionListener(e -> {
            roomButtonService.moveButtonRight(currentRoomId, index);
            refreshButtons();
        });
        menu.add(moveRight);

        if (currentBg != null && currentFg != null) {
            updateMenuTheme(menu, currentBg, currentFg);
        }
        menu.show(btn, x, y);
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

    private void showEditDialog(RoomButton existing, int index) {
        Window parentWindow = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(parentWindow, existing == null ? "Add Button" : "Edit Button", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField nameField = new JTextField(existing == null ? "" : existing.getName(), 20);
        JTextField commandField = new JTextField(existing == null ? "" : existing.getCommand(), 20);

        gbc.gridx = 0; gbc.gridy = 0;
        dialog.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1;
        dialog.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        dialog.add(new JLabel("Command:"), gbc);
        gbc.gridx = 1;
        dialog.add(commandField, gbc);

        JButton saveBtn = new JButton("Save");
        saveBtn.addActionListener(e -> {
            String name = nameField.getText().trim();
            String command = commandField.getText().trim();
            if (!name.isEmpty() && !command.isEmpty()) {
                RoomButton rb = new RoomButton(name, command);
                if (existing == null) {
                    roomButtonService.addButton(currentRoomId, currentRoomName, rb);
                } else {
                    roomButtonService.updateButton(currentRoomId, currentRoomName, index, rb);
                }
                refreshButtons();
                dialog.dispose();
            } else {
                JOptionPane.showMessageDialog(dialog, "Name and Command cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dialog.dispose());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.add(saveBtn);
        btnPanel.add(cancelBtn);

        gbc.gridx = 0; gbc.gridy = 2;
        gbc.gridwidth = 2;
        dialog.add(btnPanel, gbc);
        
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

    private void updateDialogTheme(Component c, Color bg, Color fg) {
        if (c instanceof JPanel || c instanceof JDialog) {
            c.setBackground(bg);
        }
        if (c instanceof JLabel || c instanceof JTextField || c instanceof JButton) {
            c.setBackground(bg);
            c.setForeground(fg);
        }

        if (c instanceof Container container) {
            for (Component child : container.getComponents()) {
                updateDialogTheme(child, bg, fg);
            }
        }
    }
    
    public void updateTheme(Color bg, Color fg) {
        this.currentBg = bg;
        this.currentFg = fg;
        this.setBackground(bg);
        this.setForeground(fg);
        for (Component c : getComponents()) {
            if (c instanceof JButton) {
                c.setBackground(bg);
                c.setForeground(fg);
            }
        }
    }
}
