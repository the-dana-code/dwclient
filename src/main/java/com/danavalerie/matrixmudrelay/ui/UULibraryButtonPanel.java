package com.danavalerie.matrixmudrelay.ui;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import com.danavalerie.matrixmudrelay.core.UULibraryService;

public class UULibraryButtonPanel extends JPanel implements FontChangeListener {
    private final Consumer<String> commandSubmitter;

    private final JLabel[] slots = new JLabel[4];
    
    private Color themeBg = null;
    private Color themeFg = null;
    private Border btnBorder = null;
    private boolean distortion = false;
    private boolean buttonsEnabled = true;

    private static final Insets BUTTON_MARGIN = new Insets(4, 12, 4, 12);
    private static final int PANEL_PADDING = 8;
    private static final int GAP = 6;

    public UULibraryButtonPanel(Consumer<String> commandSubmitter) {
        super(new WrapLayout(FlowLayout.CENTER, GAP, GAP));
        this.commandSubmitter = commandSubmitter;
        this.setBorder(BorderFactory.createEmptyBorder(PANEL_PADDING, PANEL_PADDING, PANEL_PADDING, PANEL_PADDING));

        for (int i = 0; i < 4; i++) {
            slots[i] = createButton();
            add(slots[i]);
        }

        rebuildLayout();

        setVisible(false);
    }

    public void rebuildLayout() {
        UULibraryService service = UULibraryService.getInstance();
        UULibraryService.Orientation currentOri = service.getOrientation();

        // Map relative directions to cardinal orientations
        Map<UULibraryService.Orientation, String> available = new LinkedHashMap<>();
        if (service.canMove(currentOri)) {
            available.put(currentOri, "forward");
        }
        if (service.canMove(currentOri.turn180())) {
            available.put(currentOri.turn180(), "backward");
        }
        if (service.canMove(currentOri.turnLeft())) {
            available.put(currentOri.turnLeft(), "left");
        }
        if (service.canMove(currentOri.turnRight())) {
            available.put(currentOri.turnRight(), "right");
        }

        // Fixed cardinal order: north, south, east, west: 0, 2, 1, 3
        int[] cardinalOrder = {0, 2, 1, 3};
        List<String> sortedLabels = new ArrayList<>();
        for (int ord : cardinalOrder) {
            UULibraryService.Orientation ori = UULibraryService.Orientation.values()[ord];
            if (available.containsKey(ori)) {
                sortedLabels.add(available.get(ori));
            }
        }

        for (int i = 0; i < 4; i++) {
            JLabel btn = slots[i];
            btn.setVisible(true); // Always visible to take space
            btn.setEnabled(buttonsEnabled);
            if (buttonsEnabled && i < sortedLabels.size()) {
                btn.setText(sortedLabels.get(i));
                btn.setOpaque(true);
                btn.setBorder(btnBorder);
            } else {
                btn.setText("");
                btn.setOpaque(false);
                btn.setBorder(null);
            }
        }

        revalidate();
        repaint();
    }

    @Override
    public void onFontChange(Font font) {
        for (JLabel btn : slots) {
            btn.setFont(font);
        }
        updateButtonSizes();
        revalidate();
        repaint();
    }

    private void updateButtonSizes() {
        if (slots[0] == null) return;
        
        Dimension maxDim = new Dimension(0, 0);
        JLabel temp = new JLabel("", SwingConstants.CENTER);
        temp.setFont(slots[0].getFont());
        
        // Use a border for calculation even if btnBorder isn't set yet
        Border calcBorder = btnBorder;
        if (calcBorder == null) {
            calcBorder = BorderFactory.createEmptyBorder(BUTTON_MARGIN.top, BUTTON_MARGIN.left, BUTTON_MARGIN.bottom, BUTTON_MARGIN.right);
        }
        temp.setBorder(calcBorder);
        
        String[] possible = {"forward", "backward", "left", "right"};
        for (String s : possible) {
            temp.setText(s);
            Dimension d = temp.getPreferredSize();
            maxDim.width = Math.max(maxDim.width, d.width);
            maxDim.height = Math.max(maxDim.height, d.height);
        }
        
        for (JLabel btn : slots) {
            btn.setPreferredSize(maxDim);
        }
    }

    private JLabel createButton() {
        JLabel btn = new JLabel("", SwingConstants.CENTER);
        btn.setFocusable(false);
        btn.setOpaque(true);
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                String command = btn.getText();
                if (btn.isEnabled() && btn.isVisible() && !command.isEmpty()) {
                    setDistortion(false);
                    setButtonsEnabled(false);
                    commandSubmitter.accept(command);
                    commandSubmitter.accept("look distortion");
                }
            }
        });
        return btn;
    }

    public void setButtonsEnabled(boolean enabled) {
        this.buttonsEnabled = enabled;
        rebuildLayout();
    }

    public void setDistortion(boolean distortion) {
        this.distortion = distortion;
        this.setBackground(distortion ? Color.RED : themeBg);
    }

    public void updateTheme(Color bg, Color fg) {
        this.themeBg = bg;
        this.themeFg = fg;
        this.setBackground(distortion ? Color.RED : bg);
        this.setForeground(fg);
        
        btnBorder = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(fg),
                BorderFactory.createEmptyBorder(BUTTON_MARGIN.top, BUTTON_MARGIN.left, BUTTON_MARGIN.bottom, BUTTON_MARGIN.right)
        );
        
        for (JLabel btn : slots) {
            btn.setBackground(bg);
            btn.setForeground(fg);
        }
        
        updateButtonSizes();
        rebuildLayout(); // To apply new border and visibility
    }
}
