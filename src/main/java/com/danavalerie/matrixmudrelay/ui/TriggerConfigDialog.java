package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.config.ClientConfig;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TriggerConfigDialog extends JDialog {
    private final List<ClientConfig.Trigger> triggers;
    private final DefaultListModel<ClientConfig.Trigger> listModel = new DefaultListModel<>();
    private final JList<ClientConfig.Trigger> triggerList = new JList<>(listModel);

    private JTextField patternField;
    private JButton foregroundButton;
    private JButton backgroundButton;
    private JCheckBox boldCheckBox;
    private JRadioButton noSoundRadio;
    private JRadioButton beepRadio;
    private JRadioButton wavRadio;
    private JTextField wavFileField;
    private JButton browseWavButton;
    private JCheckBox chitchatCheckBox;

    private String selectedForeground;
    private String selectedBackground;
    private boolean isUpdating = false;
    private boolean saved = false;

    public TriggerConfigDialog(Frame owner, List<ClientConfig.Trigger> triggers) {
        super(owner, "Triggers", true);
        this.triggers = new ArrayList<>();
        for (ClientConfig.Trigger t : triggers) {
            this.triggers.add(cloneTrigger(t));
        }

        initComponents();
        loadTriggers();

        if (!this.triggers.isEmpty()) {
            triggerList.setSelectedIndex(0);
        }

        setSize(700, 500);
        setLocationRelativeTo(owner);
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        // List on the left
        JPanel listPanel = new JPanel(new BorderLayout());
        triggerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        triggerList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ClientConfig.Trigger t) {
                    setText(t.pattern == null || t.pattern.isEmpty() ? "(new trigger)" : t.pattern);
                }
                return this;
            }
        });
        triggerList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateEditor();
            }
        });

        JScrollPane scrollPane = new JScrollPane(triggerList);
        scrollPane.setPreferredSize(new Dimension(200, 0));
        listPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel listButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton("Add");
        JButton removeButton = new JButton("Remove");
        listButtons.add(addButton);
        listButtons.add(removeButton);
        listPanel.add(listButtons, BorderLayout.SOUTH);

        add(listPanel, BorderLayout.WEST);

        // Editor on the right
        JPanel editorPanel = new JPanel(new GridBagLayout());
        editorPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;

        // Pattern
        gbc.gridx = 0; gbc.gridy = 0;
        editorPanel.add(new JLabel("Regex Pattern:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        patternField = new JTextField();
        editorPanel.add(patternField, gbc);

        // Foreground
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        editorPanel.add(new JLabel("Foreground:"), gbc);
        gbc.gridx = 1;
        foregroundButton = new JButton("Choose...");
        editorPanel.add(foregroundButton, gbc);

        // Background
        gbc.gridx = 0; gbc.gridy = 2;
        editorPanel.add(new JLabel("Background:"), gbc);
        gbc.gridx = 1;
        backgroundButton = new JButton("Choose...");
        editorPanel.add(backgroundButton, gbc);

        // Bold
        gbc.gridx = 1; gbc.gridy = 3;
        boldCheckBox = new JCheckBox("Bold");
        editorPanel.add(boldCheckBox, gbc);

        // Sound
        gbc.gridx = 0; gbc.gridy = 4;
        editorPanel.add(new JLabel("Sound:"), gbc);
        gbc.gridx = 1;
        JPanel soundPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        noSoundRadio = new JRadioButton("None");
        beepRadio = new JRadioButton("Beep");
        wavRadio = new JRadioButton("WAV");
        ButtonGroup soundGroup = new ButtonGroup();
        soundGroup.add(noSoundRadio);
        soundGroup.add(beepRadio);
        soundGroup.add(wavRadio);
        soundPanel.add(noSoundRadio);
        soundPanel.add(beepRadio);
        soundPanel.add(wavRadio);
        editorPanel.add(soundPanel, gbc);

        // WAV file
        gbc.gridx = 0; gbc.gridy = 5;
        editorPanel.add(new JLabel("WAV File:"), gbc);
        gbc.gridx = 1;
        JPanel wavFilePanel = new JPanel(new BorderLayout(5, 0));
        wavFileField = new JTextField();
        browseWavButton = new JButton("Browse...");
        wavFilePanel.add(wavFileField, BorderLayout.CENTER);
        wavFilePanel.add(browseWavButton, BorderLayout.EAST);
        editorPanel.add(wavFilePanel, gbc);

        // Chitchat
        gbc.gridx = 1; gbc.gridy = 6;
        chitchatCheckBox = new JCheckBox("Send to Chitchat");
        editorPanel.add(chitchatCheckBox, gbc);

        // Spacer
        gbc.gridy = 7; gbc.weighty = 1.0;
        editorPanel.add(new JPanel(), gbc);

        add(editorPanel, BorderLayout.CENTER);

        // Bottom buttons
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveButton = new JButton("Save");
        JButton cancelButton = new JButton("Cancel");
        bottomPanel.add(saveButton);
        bottomPanel.add(cancelButton);
        add(bottomPanel, BorderLayout.SOUTH);

        // Actions
        addButton.addActionListener(e -> {
            ClientConfig.Trigger t = new ClientConfig.Trigger();
            t.pattern = "New Trigger";
            triggers.add(t);
            listModel.addElement(t);
            triggerList.setSelectedValue(t, true);
        });

        removeButton.addActionListener(e -> {
            int idx = triggerList.getSelectedIndex();
            if (idx != -1) {
                triggers.remove(idx);
                listModel.remove(idx);
                if (idx < listModel.size()) {
                    triggerList.setSelectedIndex(idx);
                } else if (!listModel.isEmpty()) {
                    triggerList.setSelectedIndex(listModel.size() - 1);
                }
            }
        });

        foregroundButton.addActionListener(e -> {
            Color initial = selectedForeground != null ? Color.decode(selectedForeground) : Color.WHITE;
            Color c = JColorChooser.showDialog(this, "Choose Foreground Color", initial);
            if (c != null) {
                selectedForeground = String.format("#%06X", (0xFFFFFF & c.getRGB()));
                updateColorButtons();
                saveCurrentTrigger();
            }
        });

        backgroundButton.addActionListener(e -> {
            Color initial = selectedBackground != null ? Color.decode(selectedBackground) : Color.BLACK;
            Color c = JColorChooser.showDialog(this, "Choose Background Color", initial);
            if (c != null) {
                selectedBackground = String.format("#%06X", (0xFFFFFF & c.getRGB()));
                updateColorButtons();
                saveCurrentTrigger();
            }
        });
        
        // Remove color actions (right click or separate button? let's just use "Clear" buttons)
        foregroundButton.setComponentPopupMenu(createColorPopup(true));
        backgroundButton.setComponentPopupMenu(createColorPopup(false));

        DocumentListener dl = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { saveCurrentTrigger(); }
            public void removeUpdate(DocumentEvent e) { saveCurrentTrigger(); }
            public void changedUpdate(DocumentEvent e) { saveCurrentTrigger(); }
        };
        patternField.getDocument().addDocumentListener(dl);
        wavFileField.getDocument().addDocumentListener(dl);

        ActionListener al = e -> saveCurrentTrigger();
        boldCheckBox.addActionListener(al);
        noSoundRadio.addActionListener(al);
        beepRadio.addActionListener(al);
        wavRadio.addActionListener(al);
        chitchatCheckBox.addActionListener(al);

        browseWavButton.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (wavFileField.getText() != null && !wavFileField.getText().isEmpty()) {
                fc.setSelectedFile(new File(wavFileField.getText()));
            }
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                wavFileField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });

        saveButton.addActionListener(e -> {
            saved = true;
            dispose();
        });
        cancelButton.addActionListener(e -> dispose());
    }

    private JPopupMenu createColorPopup(boolean foreground) {
        JPopupMenu popup = new JPopupMenu();
        JMenuItem clearItem = new JMenuItem("Clear Color");
        clearItem.addActionListener(e -> {
            if (foreground) selectedForeground = null;
            else selectedBackground = null;
            updateColorButtons();
            saveCurrentTrigger();
        });
        popup.add(clearItem);
        return popup;
    }

    private void loadTriggers() {
        listModel.clear();
        for (ClientConfig.Trigger t : triggers) {
            listModel.addElement(t);
        }
    }

    private void updateEditor() {
        ClientConfig.Trigger t = triggerList.getSelectedValue();
        if (t == null) {
            setEnabledAll(false);
            return;
        }
        setEnabledAll(true);
        isUpdating = true;
        patternField.setText(t.pattern);
        selectedForeground = t.foreground;
        selectedBackground = t.background;
        updateColorButtons();
        boldCheckBox.setSelected(t.bold);
        if (t.systemBeep) {
            beepRadio.setSelected(true);
        } else if (t.soundFile != null && !t.soundFile.isEmpty()) {
            wavRadio.setSelected(true);
        } else {
            noSoundRadio.setSelected(true);
        }
        wavFileField.setText(t.soundFile);
        chitchatCheckBox.setSelected(t.sendToChitchat);
        isUpdating = false;
    }

    private void setEnabledAll(boolean enabled) {
        patternField.setEnabled(enabled);
        foregroundButton.setEnabled(enabled);
        backgroundButton.setEnabled(enabled);
        boldCheckBox.setEnabled(enabled);
        noSoundRadio.setEnabled(enabled);
        beepRadio.setEnabled(enabled);
        wavRadio.setEnabled(enabled);
        wavFileField.setEnabled(enabled);
        browseWavButton.setEnabled(enabled);
        chitchatCheckBox.setEnabled(enabled);
    }

    private void updateColorButtons() {
        if (selectedForeground != null) {
            foregroundButton.setBackground(Color.decode(selectedForeground));
            foregroundButton.setText(selectedForeground);
        } else {
            foregroundButton.setBackground(null);
            foregroundButton.setText("Default");
        }
        if (selectedBackground != null) {
            backgroundButton.setBackground(Color.decode(selectedBackground));
            backgroundButton.setText(selectedBackground);
        } else {
            backgroundButton.setBackground(null);
            backgroundButton.setText("Default");
        }
    }

    private void saveCurrentTrigger() {
        if (isUpdating) return;
        ClientConfig.Trigger t = triggerList.getSelectedValue();
        if (t == null) return;

        t.pattern = patternField.getText();
        t.foreground = selectedForeground;
        t.background = selectedBackground;
        t.bold = boldCheckBox.isSelected();
        t.systemBeep = beepRadio.isSelected();
        t.soundFile = wavRadio.isSelected() ? wavFileField.getText() : null;
        t.sendToChitchat = chitchatCheckBox.isSelected();
        
        triggerList.repaint();
    }

    private ClientConfig.Trigger cloneTrigger(ClientConfig.Trigger t) {
        ClientConfig.Trigger clone = new ClientConfig.Trigger();
        clone.pattern = t.pattern;
        clone.foreground = t.foreground;
        clone.background = t.background;
        clone.bold = t.bold;
        clone.soundFile = t.soundFile;
        clone.systemBeep = t.systemBeep;
        clone.sendToChitchat = t.sendToChitchat;
        return clone;
    }

    public List<ClientConfig.Trigger> getTriggers() {
        return triggers;
    }

    public boolean isSaved() {
        return saved;
    }
}
