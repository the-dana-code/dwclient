package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.core.TimerService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

public class TimerDialog extends JDialog {
    private final TimerService timerService;
    private final boolean isEdit;
    private final String initialChar;
    private final String initialDescr;
    private final long expirationTime;

    private JLabel charLabel;
    private JComboBox<String> charComboBox;
    private JTextField descrField;
    private JSpinner hourSpinner;
    private JSpinner minuteSpinner;
    private JLabel remainLabel;
    private boolean saved = false;
    private Timer countdownTimer;


    // Improved constructor
    private Color currentBg;
    private Color currentFg;

    public TimerDialog(Frame owner, String title, boolean isEdit, String characterName, String description, long expirationTime, TimerService timerService, List<String> knownCharacters, Color bg, Color fg) {
        super(owner, title, true);
        this.timerService = timerService;
        this.isEdit = isEdit;
        this.initialChar = characterName != null ? characterName : "";
        this.initialDescr = description != null ? description : "";
        this.expirationTime = expirationTime;
        this.currentBg = bg;
        this.currentFg = fg;

        initComponents(knownCharacters);
        if (bg != null && fg != null) {
            updateTheme(bg, fg);
        }
        pack();
        setLocationRelativeTo(owner);
    }

    private void updateTheme(Color bg, Color fg) {
        updateComponentTheme(getContentPane(), bg, fg);
        if (hourSpinner != null) {
            ((JSpinner.DefaultEditor) hourSpinner.getEditor()).getTextField().setBackground(bg);
            ((JSpinner.DefaultEditor) hourSpinner.getEditor()).getTextField().setForeground(fg);
        }
        if (minuteSpinner != null) {
            ((JSpinner.DefaultEditor) minuteSpinner.getEditor()).getTextField().setBackground(bg);
            ((JSpinner.DefaultEditor) minuteSpinner.getEditor()).getTextField().setForeground(fg);
        }
    }

    private void updateComponentTheme(Component c, Color bg, Color fg) {
        c.setBackground(bg);
        c.setForeground(fg);
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                updateComponentTheme(child, bg, fg);
            }
        }
        if (c instanceof JTextField) {
            ((JTextField) c).setCaretColor(fg);
        }
    }

    private void initComponents(List<String> knownCharacters) {
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Character Name
        gbc.gridx = 0; gbc.gridy = 0;
        add(new JLabel("Character:"), gbc);
        gbc.gridx = 1;
        if (isEdit) {
            charLabel = new JLabel(initialChar);
            add(charLabel, gbc);
        } else {
            charComboBox = new JComboBox<>(buildCharacterModel(knownCharacters));
            if (!initialChar.isBlank()) {
                charComboBox.setSelectedItem(initialChar);
            }
            add(charComboBox, gbc);
        }

        // Description
        gbc.gridx = 0; gbc.gridy = 1;
        add(new JLabel("Description:"), gbc);
        gbc.gridx = 1;
        descrField = new JTextField(initialDescr, 20);
        add(descrField, gbc);

        // Time Remaining / Duration
        gbc.gridx = 0; gbc.gridy = 2;
        add(new JLabel("Time:"), gbc);
        gbc.gridx = 1;
        
        if (isEdit) {
            remainLabel = new JLabel();
            updateRemainLabel();
            add(remainLabel, gbc);
            countdownTimer = new Timer(1000, e -> updateRemainLabel());
            countdownTimer.start();
        } else {
            JPanel timePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            hourSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 999, 1));
            minuteSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 59, 1));
            timePanel.add(hourSpinner);
            timePanel.add(new JLabel(" h "));
            timePanel.add(minuteSpinner);
            timePanel.add(new JLabel(" m"));
            add(timePanel, gbc);
        }

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        add(buttonPanel, gbc);

        saveButton.addActionListener(e -> {
            if (getCharacterName().isBlank()) {
                JOptionPane.showMessageDialog(this, "Character name is required.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (descrField.getText().isBlank()) {
                JOptionPane.showMessageDialog(this, "Description is required.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            saved = true;
            dispose();
        });

        cancelButton.addActionListener(e -> dispose());

        // Enter confirms
        getRootPane().setDefaultButton(saveButton);

        // Escape cancels
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private void updateRemainLabel() {
        long remain = expirationTime - System.currentTimeMillis();
        remainLabel.setText(timerService.formatRemainingTime(remain));
    }

    public boolean isSaved() {
        return saved;
    }

    public String getCharacterName() {
        if (charComboBox != null) {
            Object selected = charComboBox.getSelectedItem();
            return selected != null ? selected.toString().trim() : "";
        }
        return charLabel.getText().trim();
    }

    public String getDescription() {
        return descrField.getText().trim();
    }

    public long getDurationMs() {
        if (isEdit) return 0;
        int hours = (Integer) hourSpinner.getValue();
        int minutes = (Integer) minuteSpinner.getValue();
        return (hours * 3600L + minutes * 60L) * 1000L;
    }

    @Override
    public void dispose() {
        if (countdownTimer != null) {
            countdownTimer.stop();
        }
        super.dispose();
    }

    private DefaultComboBoxModel<String> buildCharacterModel(List<String> knownCharacters) {
        List<String> items = new ArrayList<>();
        if (knownCharacters != null) {
            items.addAll(knownCharacters);
        }
        if (!initialChar.isBlank() && !items.contains(initialChar)) {
            items.add(0, initialChar);
        }
        return new DefaultComboBoxModel<>(items.toArray(new String[0]));
    }
}
