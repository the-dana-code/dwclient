package com.danavalerie.matrixmudrelay.ui;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.plaf.ButtonUI;

public class KeepOpenCheckBoxMenuItem extends JMenuItem {
    private static final String CHECKED_PREFIX = "\u2611 ";
    private static final String UNCHECKED_PREFIX = "\u2610 ";

    private final String label;
    private ButtonUI defaultUI;
    private boolean keepMenuOpen;
    private boolean checked;

    public KeepOpenCheckBoxMenuItem(String label, boolean checked) {
        this(label, checked, false);
    }

    public KeepOpenCheckBoxMenuItem(String label, boolean checked, boolean keepMenuOpen) {
        this(label, checked, keepMenuOpen, null);
    }

    public KeepOpenCheckBoxMenuItem(String label, boolean checked, boolean keepMenuOpen, JComponent parentMenu) {
        this.label = label;
        this.defaultUI = getUI();
        setChecked(checked);
        setKeepMenuOpen(keepMenuOpen);
        if (parentMenu != null) {
            putClientProperty(KeepOpenMenuItem.PARENT_MENU_KEY, parentMenu);
        }
        addActionListener(event -> toggle());
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
        setText((checked ? CHECKED_PREFIX : UNCHECKED_PREFIX) + label);
    }

    public boolean isChecked() {
        return checked;
    }

    public void toggle() {
        setChecked(!checked);
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
            setUI(new KeepOpenMenuItem.KeepOpenMenuItemUI(true));
        }
    }
}
