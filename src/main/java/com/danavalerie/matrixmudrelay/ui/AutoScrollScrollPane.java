package com.danavalerie.matrixmudrelay.ui;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.border.Border;
import java.awt.Color;

public class AutoScrollScrollPane extends JScrollPane {
    public static final Border BLACK_BORDER = javax.swing.BorderFactory.createLineBorder(Color.BLACK, 2);
    public static final Border RED_BORDER = javax.swing.BorderFactory.createLineBorder(Color.RED, 2);

    private final AutoScrollable view;
    private int lastScrollValue = 0;

    public <T extends JComponent & AutoScrollable> AutoScrollScrollPane(T view) {
        super(view);
        setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        this.view = view;
        setBorder(BLACK_BORDER);

        getVerticalScrollBar().addAdjustmentListener(e -> {
            int extent = getVerticalScrollBar().getModel().getExtent();
            int maximum = getVerticalScrollBar().getModel().getMaximum();
            int value = e.getValue();

            boolean atBottom = (value + extent) >= maximum;
            if (atBottom) {
                if (!this.view.isAutoScroll() || getBorder() != BLACK_BORDER) {
                    this.view.setAutoScroll(true);
                    setBorder(BLACK_BORDER);
                }
            } else {
                if (this.view.isAutoScroll() && value < lastScrollValue) {
                    this.view.setAutoScroll(false);
                    setBorder(RED_BORDER);
                }
            }
            lastScrollValue = value;
        });
    }
}
