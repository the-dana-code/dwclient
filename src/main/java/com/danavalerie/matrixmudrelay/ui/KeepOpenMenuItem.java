package com.danavalerie.matrixmudrelay.ui;

import javax.swing.JMenuItem;
import javax.swing.MenuSelectionManager;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.MenuItemUI;
import javax.swing.plaf.basic.BasicMenuItemUI;

public class KeepOpenMenuItem extends JMenuItem {
    private ButtonUI defaultUI;

    public KeepOpenMenuItem(String text) {
        super(text);
        this.defaultUI = getUI();
        setKeepMenuOpen(true);
    }

    public void setKeepMenuOpen(boolean keepMenuOpen) {
        if (keepMenuOpen) {
            setUI(new KeepOpenMenuItemUI());
        } else if (defaultUI != null) {
            setUI(defaultUI);
        } else {
            updateUI();
        }
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (defaultUI == null) {
            defaultUI = getUI();
        }
        setKeepMenuOpen(true);
    }

    private static class KeepOpenMenuItemUI extends BasicMenuItemUI {
        @Override
        protected void doClick(MenuSelectionManager msm) {
            menuItem.doClick(0);
        }
    }
}
