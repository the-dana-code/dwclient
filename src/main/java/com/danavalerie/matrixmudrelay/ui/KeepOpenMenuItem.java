package com.danavalerie.matrixmudrelay.ui;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.MenuItemUI;
import javax.swing.plaf.basic.BasicMenuItemUI;
import java.awt.Container;
import java.awt.Window;

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

    public static class KeepOpenMenuItemUI extends BasicMenuItemUI {
        @Override
        protected void doClick(MenuSelectionManager msm) {
            menuItem.doClick(0);
            updateMenuSize(menuItem);
        }
    }

    public static void updateMenuSize(JMenuItem menuItem) {
        SwingUtilities.invokeLater(() -> {
            Container parent = menuItem.getParent();
            if (parent instanceof JPopupMenu) {
                JPopupMenu popup = (JPopupMenu) parent;
                popup.revalidate();
                popup.repaint();

                Window window = SwingUtilities.getWindowAncestor(popup);
                if (window != null && window != SwingUtilities.getWindowAncestor(popup.getInvoker())) {
                    window.pack();
                } else {
                    popup.setSize(popup.getPreferredSize());
                }
            }
        });
    }
}
