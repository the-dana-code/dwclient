package com.danavalerie.matrixmudrelay.ui;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.plaf.ButtonUI;
import java.util.ArrayList;
import java.util.List;

public class KeepOpenRadioMenuItem extends JMenuItem {
    private static final String SELECTED_PREFIX = "\u25C9 ";
    private static final String UNSELECTED_PREFIX = "\u25CC ";

    private final String label;
    private final RadioMenuGroup group;
    private ButtonUI defaultUI;
    private boolean keepMenuOpen;
    private boolean selected;

    public KeepOpenRadioMenuItem(String label, boolean selected, RadioMenuGroup group, JComponent parentMenu) {
        this.label = label;
        this.group = group;
        if (group != null) {
            group.add(this);
        }
        this.defaultUI = getUI();
        setSelected(selected);
        setKeepMenuOpen(true);
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
        private final List<KeepOpenRadioMenuItem> items = new ArrayList<>();

        public void add(KeepOpenRadioMenuItem item) {
            if (!items.contains(item)) {
                items.add(item);
            }
        }

        public void select(KeepOpenRadioMenuItem selectedItem) {
            for (KeepOpenRadioMenuItem item : items) {
                item.setSelected(item == selectedItem);
            }
        }
    }
}
