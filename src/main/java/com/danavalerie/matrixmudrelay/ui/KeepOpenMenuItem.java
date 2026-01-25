package com.danavalerie.matrixmudrelay.ui;

import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JMenu;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import javax.swing.plaf.ButtonUI;
import javax.swing.plaf.MenuItemUI;
import javax.swing.plaf.basic.BasicMenuItemUI;
import java.awt.*;

public class KeepOpenMenuItem extends JMenuItem {
    public static final String PARENT_MENU_KEY = "KeepOpenMenuItem.parentMenu";
    private ButtonUI defaultUI;
    private boolean keepMenuOpen;

    public KeepOpenMenuItem(String text, boolean keepMenuOpen) {
        super(text);
        this.keepMenuOpen = keepMenuOpen;
        this.defaultUI = getUI();
        setKeepMenuOpen(true);
    }

    public KeepOpenMenuItem(String text, JComponent parentMenu, boolean keepMenuOpen) {
        this(text, keepMenuOpen);
        putClientProperty(PARENT_MENU_KEY, parentMenu);
    }

    public void setKeepMenuOpen(boolean keepMenuOpen) {
        if (keepMenuOpen) {
            setUI(new KeepOpenMenuItemUI(true));
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
        private final boolean keepMenuOpen;

        public KeepOpenMenuItemUI(boolean keepMenuOpen) {
            this.keepMenuOpen = keepMenuOpen;
        }

        @Override
        protected void doClick(MenuSelectionManager msm) {
            Container parent = menuItem.getParent();
            menuItem.doClick(0);
            updateMenuSize(menuItem, parent);
        }

        private void updateMenuSize(JMenuItem menuItem, Container capturedParent) {
            if (!keepMenuOpen) {
                return;
            }
            Object parentProp = menuItem.getClientProperty(PARENT_MENU_KEY);
            Container parent = (parentProp instanceof Container) ? (Container) parentProp : capturedParent;
            if (parent == null) {
                parent = menuItem.getParent();
            }

            if (parent instanceof JMenu) {
                parent = ((JMenu) parent).getPopupMenu();
            }

            if (parent instanceof JPopupMenu) {
                JPopupMenu popup = (JPopupMenu) parent;

                SwingUtilities.invokeLater(() -> {
                    if (! popup.getSize().equals(popup.getPreferredSize())) {
                        Point p = popup.getLocationOnScreen();
                        popup.setVisible(false);

                        SwingUtilities.invokeLater(() -> {
                            Point q = new Point(p);
                            SwingUtilities.convertPointFromScreen(q, popup.getInvoker());
                            popup.show(popup.getInvoker(), q.x, q.y);
                        });
                    }
                });
            }
        }

    }

}
