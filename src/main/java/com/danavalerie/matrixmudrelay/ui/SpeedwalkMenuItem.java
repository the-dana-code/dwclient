package com.danavalerie.matrixmudrelay.ui;

import javax.swing.JComponent;

public class SpeedwalkMenuItem extends KeepOpenMenuItem {
    private static final String SPEEDWALK_SUFFIX = " →";

    public SpeedwalkMenuItem(String text, boolean keepMenuOpen) {
        super(text, keepMenuOpen);
    }

    public SpeedwalkMenuItem(String text, JComponent parentMenu, boolean keepMenuOpen) {
        super(text, parentMenu, keepMenuOpen);
    }

    @Override
    public void setText(String text) {
        if (text == null || text.endsWith(SPEEDWALK_SUFFIX)) {
            super.setText(text);
            return;
        }
        super.setText(text + SPEEDWALK_SUFFIX);
    }
}
