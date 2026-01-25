package com.danavalerie.matrixmudrelay.ui;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.plaf.ButtonUI;
import java.util.ArrayList;
import java.util.List;

public class RadioMenuItem extends JMenuItem {
    private static final String SELECTED_PREFIX = "\u25C9 ";
    private static final String UNSELECTED_PREFIX = "\u25CC ";

    private final String label;
    private final RadioMenuGroup group;
    private ButtonUI defaultUI;
    private boolean keepMenuOpen;
    private boolean selected;

    public RadioMenuItem(String label, boolean selected, RadioMenuGroup group) {
        this(label, selected, group, false);
    }

    public RadioMenuItem(String label, boolean selected, RadioMenuGroup group, boolean keepMenuOpen) {
        this(label, selected, group, keepMenuOpen, null);
    }

    public RadioMenuItem(String label, boolean selected, RadioMenuGroup group, boolean keepMenuOpen, JComponent parentMenu) {
        this.label = label;
        this.group = group;
        if (group != null) {
            group.add(this);
        }
        this.defaultUI = getUI();
        setSelected(selected);
        setKeepMenuOpen(keepMenuOpen);
        if (parentMenu != null) {
            putClientProperty(KeepOpenMenuItem.PARENT_MENU_KEY, parentMenu);
        }
        addActionListener(event -> {
            if (group != null) {
                group.select(this);
            } else {
                setSelected(true);
            }
        });
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        setText((selected ? SELECTED_PREFIX : UNSELECTED_PREFIX) + label);
    }

    public boolean isSelected() {
        return selected;
    }

    public void setKeepMenuOpen(boolean keepMenuOpen) {
        this.keepMenuOpen = keepMenuOpen;
        updateUI();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (defaultUI == null) {
            defaultUI = getUI();
        }
        if (keepMenuOpen) {
            setUI(new KeepOpenMenuItem.KeepOpenMenuItemUI(keepMenuOpen));
        }
    }

    public static class RadioMenuGroup {
        private final List<RadioMenuItem> items = new ArrayList<>();

        public void add(RadioMenuItem item) {
            if (!items.contains(item)) {
                items.add(item);
            }
        }

        public void select(RadioMenuItem selectedItem) {
            for (RadioMenuItem item : items) {
                item.setSelected(item == selectedItem);
            }
        }
    }
}
