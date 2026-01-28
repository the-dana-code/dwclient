package com.danavalerie.matrixmudrelay.ui;

import javax.swing.JComponent;

public class SpeedwalkMenuItem extends KeepOpenMenuItem {
    public SpeedwalkMenuItem(String text, boolean keepMenuOpen) {
        super(text, keepMenuOpen);
    }

    public SpeedwalkMenuItem(String text, JComponent parentMenu, boolean keepMenuOpen) {
        super(text, parentMenu, keepMenuOpen);
    }
}
