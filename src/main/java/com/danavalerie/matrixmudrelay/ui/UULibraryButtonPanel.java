package com.danavalerie.matrixmudrelay.ui;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import com.danavalerie.matrixmudrelay.core.UULibraryService;

public class UULibraryButtonPanel extends JPanel implements FontChangeListener {
    private final Consumer<String> commandSubmitter;

    private final JLabel forwardBtn;
    private final JLabel backwardBtn;
    private final JLabel leftBtn;
    private final JLabel rightBtn;
    
    private Color themeBg = null;
    private Color themeFg = null;
    private boolean distortion = false;

    private static final Insets BUTTON_MARGIN = new Insets(2, 10, 2, 10);

    public UULibraryButtonPanel(Consumer<String> commandSubmitter) {
        super(new GridBagLayout());
        this.commandSubmitter = commandSubmitter;
        this.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        forwardBtn = createButton("forward");
        backwardBtn = createButton("backward");
        leftBtn = createButton("left");
        rightBtn = createButton("right");

        rebuildLayout();

        setVisible(false);
    }

    public void rebuildLayout() {
        removeAll();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 1.0;

        // Horizontal layout: order based on direction result
        // north, south, east, west
        UULibraryService service = UULibraryService.getInstance();
        UULibraryService.Orientation currentOri = service.getOrientation();

        JLabel[] cardinalButtons = new JLabel[4]; // 0:N, 1:E, 2:S, 3:W

        if (service.canMove(currentOri)) {
            cardinalButtons[currentOri.ordinal()] = forwardBtn;
        }
        if (service.canMove(currentOri.turnRight())) {
            cardinalButtons[currentOri.turnRight().ordinal()] = rightBtn;
        }
        if (service.canMove(currentOri.turn180())) {
            cardinalButtons[currentOri.turn180().ordinal()] = backwardBtn;
        }
        if (service.canMove(currentOri.turnLeft())) {
            cardinalButtons[currentOri.turnLeft().ordinal()] = leftBtn;
        }

        int col = 0;
        // Desired order: N (0), S (2), E (1), W (3)
        int[] order = {0, 2, 1, 3};
        for (int idx : order) {
            JLabel btn = cardinalButtons[idx];
            if (btn != null) {
                gbc.gridx = col++;
                gbc.gridy = 0;
                add(btn, gbc);
            }
        }

        revalidate();
        repaint();
    }

    @Override
    public void onFontChange(Font font) {
        forwardBtn.setFont(font);
        backwardBtn.setFont(font);
        leftBtn.setFont(font);
        rightBtn.setFont(font);
        revalidate();
        repaint();
    }

    private JLabel createButton(String command) {
        JLabel btn = new JLabel(command, SwingConstants.CENTER);
        btn.setFocusable(false);
        btn.setOpaque(true);
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (btn.isEnabled()) {
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
        forwardBtn.setEnabled(enabled);
        backwardBtn.setEnabled(enabled);
        leftBtn.setEnabled(enabled);
        rightBtn.setEnabled(enabled);
        
        updateButtonColors();
    }

    public void setDistortion(boolean distortion) {
        this.distortion = distortion;
        this.setBackground(distortion ? Color.RED : themeBg);
        updateButtonColors();
    }

    private void updateButtonColors() {
        updateButtonColor(forwardBtn);
        updateButtonColor(backwardBtn);
        updateButtonColor(leftBtn);
        updateButtonColor(rightBtn);
    }

    private void updateButtonColor(JLabel btn) {
        if (btn.isEnabled()) {
            btn.setBackground(Color.RED);
            btn.setForeground(Color.WHITE);
        } else {
            btn.setBackground(themeBg);
            btn.setForeground(themeFg);
        }
    }

    public void updateTheme(Color bg, Color fg) {
        this.themeBg = bg;
        this.themeFg = fg;
        this.setBackground(distortion ? Color.RED : bg);
        this.setForeground(fg);
        this.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(fg),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));
        
        Border btnBorder = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(fg),
                BorderFactory.createEmptyBorder(BUTTON_MARGIN.top, BUTTON_MARGIN.left, BUTTON_MARGIN.bottom, BUTTON_MARGIN.right)
        );
        
        forwardBtn.setBorder(btnBorder);
        backwardBtn.setBorder(btnBorder);
        leftBtn.setBorder(btnBorder);
        rightBtn.setBorder(btnBorder);
        
        updateButtonColors();
    }
}
