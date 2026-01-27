package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.config.ClientConfig;
import com.danavalerie.matrixmudrelay.core.TimerService;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class TimerPanel extends JPanel {
    private final TimerService timerService;
    private final Supplier<String> characterNameSupplier;
    private final JTable table;
    private final TimerTableModel tableModel;
    private final Timer refreshTimer;
    private final JScrollPane scrollPane;
    private final JPanel buttonBar;
    private final JButton addButton;
    private final JButton editButton;
    private final JButton deleteButton;
    private final JButton restartButton;

    private Color currentBg;
    private Color currentFg;

    public TimerPanel(TimerService timerService, Supplier<String> characterNameSupplier) {
        this.timerService = timerService;
        this.characterNameSupplier = characterNameSupplier;
        this.tableModel = new TimerTableModel();
        this.table = new JTable(tableModel);
        this.table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.table.setRowHeight(26);
        this.table.getTableHeader().setPreferredSize(new Dimension(0, 26));

        List<Integer> savedWidths = timerService.getTimerColumnWidths();
        if (savedWidths != null && savedWidths.size() == table.getColumnCount()) {
            for (int i = 0; i < savedWidths.size(); i++) {
                table.getColumnModel().getColumn(i).setPreferredWidth(savedWidths.get(i));
            }
        }

        this.table.getColumnModel().addColumnModelListener(new TableColumnModelListener() {
            @Override public void columnAdded(TableColumnModelEvent e) {}
            @Override public void columnRemoved(TableColumnModelEvent e) {}
            @Override public void columnMoved(TableColumnModelEvent e) {}
            @Override public void columnMarginChanged(javax.swing.event.ChangeEvent e) {
                saveColumnWidths();
            }
            @Override public void columnSelectionChanged(ListSelectionEvent e) {}
        });

        this.table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
                if ("EXPIRED".equals(value) && column == 2) {
                    setForeground(Color.RED);
                } else if (!isSelected) {
                    String currentChar = characterNameSupplier.get();
                    int modelRow = table.convertRowIndexToModel(row);
                    Object rowCharValue = table.getModel().getValueAt(modelRow, 0);
                    String rowChar = rowCharValue != null ? rowCharValue.toString() : null;

                    if (currentChar != null && !currentChar.isBlank() && rowChar != null && currentChar.equalsIgnoreCase(rowChar)) {
                        setForeground(new Color(144, 238, 144)); // Light green
                    } else {
                        setForeground(table.getForeground());
                    }
                }
                return this;
            }
        });

        this.table.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                Component opposite = e.getOppositeComponent();
                if (opposite == null || !SwingUtilities.isDescendingFrom(opposite, TimerPanel.this)) {
                    table.clearSelection();
                }
            }
        });
        
        setLayout(new BorderLayout());

        scrollPane = new JScrollPane(table);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);

        buttonBar = new JPanel(new WrapLayout(FlowLayout.LEFT, 5, 5));
        addButton = new JButton("Add");
        editButton = new JButton("Edit");
        deleteButton = new JButton("Delete");
        restartButton = new JButton("Restart");

        buttonBar.add(addButton);
        buttonBar.add(editButton);
        buttonBar.add(deleteButton);
        buttonBar.add(restartButton);
        add(buttonBar, BorderLayout.SOUTH);

        addButton.addActionListener(e -> showAddDialog());
        editButton.addActionListener(e -> showEditDialog());
        deleteButton.addActionListener(e -> deleteSelectedTimer());
        restartButton.addActionListener(e -> restartSelectedTimer());

        // Periodically refresh the "Remain" column
        refreshTimer = new Timer(1000, e -> {
            tableModel.updateRemainingTimes();
        });
        refreshTimer.start();
        
        refreshData();
    }

    public void refreshData() {
        List<TimerEntry> entries = new ArrayList<>();
        Map<String, Map<String, ClientConfig.TimerData>> allTimers = timerService.getAllTimers();
        for (Map.Entry<String, Map<String, ClientConfig.TimerData>> charEntry : allTimers.entrySet()) {
            String characterName = charEntry.getKey();
            for (Map.Entry<String, ClientConfig.TimerData> timerEntry : charEntry.getValue().entrySet()) {
                ClientConfig.TimerData data = timerEntry.getValue();
                entries.add(new TimerEntry(characterName, timerEntry.getKey(), data.expirationTime, data.durationMs));
            }
        }
        // Sort by expiration date, oldest at the top
        entries.sort(Comparator.comparingLong(e -> e.expirationTime));
        tableModel.setEntries(entries);
    }

    private void showAddDialog() {
        String currentChar = characterNameSupplier.get();
        if (currentChar == null || currentChar.isBlank()) {
            JOptionPane.showMessageDialog(this, "You must be logged in with a character to add a timer.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        TimerDialog dialog = new TimerDialog((Frame) SwingUtilities.getWindowAncestor(this), "Add Timer", false, currentChar, null, 0, timerService, timerService.getKnownCharacters(), currentBg, currentFg);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            timerService.setTimer(dialog.getCharacterName(), dialog.getDescription(), dialog.getDurationMs());
            refreshData();
        }
    }

    private void showEditDialog() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a timer to edit.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        TimerEntry entry = tableModel.getEntry(selectedRow);
        TimerDialog dialog = new TimerDialog((Frame) SwingUtilities.getWindowAncestor(this), "Edit Timer", true, entry.characterName, entry.description, entry.expirationTime, timerService, timerService.getKnownCharacters(), currentBg, currentFg);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            timerService.updateTimerDescription(entry.characterName, entry.description, dialog.getDescription());
            refreshData();
        }
    }

    private void deleteSelectedTimer() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a timer to delete.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        TimerEntry entry = tableModel.getEntry(selectedRow);
        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete the timer '" + entry.description + "' for " + entry.characterName + "?", "Confirm Delete", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            timerService.removeTimer(entry.characterName, entry.description);
            refreshData();
        }
    }

    private void restartSelectedTimer() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a timer to restart.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }
        TimerEntry entry = tableModel.getEntry(selectedRow);
        if (entry.durationMs <= 0) {
            JOptionPane.showMessageDialog(this, "This timer does not have an initial duration saved and cannot be automatically restarted.", "Cannot Restart", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to restart the timer '" + entry.description + "' for " + entry.characterName + "?", "Confirm Restart", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            timerService.restartTimer(entry.characterName, entry.description);
            refreshData();
        }
    }

    void saveColumnWidths() {
        List<Integer> widths = new ArrayList<>();
        for (int i = 0; i < table.getColumnCount(); i++) {
            widths.add(table.getColumnModel().getColumn(i).getWidth());
        }
        timerService.setTimerColumnWidths(widths);
    }

    public void updateTheme(Color bg, Color fg) {
        this.currentBg = bg;
        this.currentFg = fg;
        setBackground(bg);
        table.setBackground(bg);
        table.setForeground(fg);
        table.setGridColor(fg.darker());
        table.getTableHeader().setBackground(bg);
        table.getTableHeader().setForeground(fg);
        scrollPane.setBackground(bg);
        scrollPane.getViewport().setBackground(bg);
        
        buttonBar.setBackground(bg);
        addButton.setBackground(bg);
        addButton.setForeground(fg);
        editButton.setBackground(bg);
        editButton.setForeground(fg);
        deleteButton.setBackground(bg);
        deleteButton.setForeground(fg);
        restartButton.setBackground(bg);
        restartButton.setForeground(fg);
    }

    private static class TimerEntry {
        String characterName;
        String description;
        long expirationTime;
        long durationMs;

        TimerEntry(String characterName, String description, long expirationTime, long durationMs) {
            this.characterName = characterName;
            this.description = description;
            this.expirationTime = expirationTime;
            this.durationMs = durationMs;
        }
    }

    private class TimerTableModel extends AbstractTableModel {
        private final String[] columnNames = {"Char", "Descr", "Remain"};
        private List<TimerEntry> entries = new ArrayList<>();

        void setEntries(List<TimerEntry> entries) {
            this.entries = entries;
            fireTableDataChanged();
        }

        TimerEntry getEntry(int row) {
            return entries.get(row);
        }

        void updateRemainingTimes() {
            if (entries.isEmpty()) return;
            fireTableRowsUpdated(0, entries.size() - 1);
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            TimerEntry entry = entries.get(rowIndex);
            switch (columnIndex) {
                case 0: return entry.characterName;
                case 1: return entry.description;
                case 2:
                    long remain = entry.expirationTime - System.currentTimeMillis();
                    return timerService.formatRemainingTime(remain);
                default: return null;
            }
        }
    }
}
