package com.danavalerie.matrixmudrelay.ui;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import com.danavalerie.matrixmudrelay.core.UULibraryService;

public class UULibraryButtonPanel extends JPanel {
    private final Consumer<String> commandSubmitter;

    private final JLabel forwardBtn;
    private final JLabel backwardBtn;
    private final JLabel leftBtn;
    private final JLabel rightBtn;
    
    private Color themeBg = null;
    private Color themeFg = null;

    private static final Insets BUTTON_MARGIN = new Insets(2, 10, 2, 10);

    public UULibraryButtonPanel(Consumer<String> commandSubmitter) {
        super(new GridBagLayout());
        this.commandSubmitter = commandSubmitter;
        this.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        forwardBtn = createButton("forward");
        backwardBtn = createButton("backward");
        leftBtn = createButton("left");
        rightBtn = createButton("right");

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.fill = GridBagConstraints.BOTH;

        // Cross layout
        gbc.gridx = 1; gbc.gridy = 0;
        add(forwardBtn, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        add(leftBtn, gbc);

        gbc.gridx = 2; gbc.gridy = 1;
        add(rightBtn, gbc);

        gbc.gridx = 1; gbc.gridy = 2;
        add(backwardBtn, gbc);

        setVisible(false);
    }

    private JLabel createButton(String command) {
        JLabel btn = new JLabel(command, SwingConstants.CENTER);
        btn.setFocusable(false);
        btn.setOpaque(true);
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (btn.isEnabled()) {
                    setButtonsEnabled(false);
                    commandSubmitter.accept(command);
                    commandSubmitter.accept("look distortion");
                }
            }
        });
        return btn;
    }

    public void setButtonsEnabled(boolean enabled) {
        UULibraryService svc = UULibraryService.getInstance();
        UULibraryService.Orientation currentOri = svc.getOrientation();
        
        forwardBtn.setEnabled(enabled && svc.canMove(currentOri));
        backwardBtn.setEnabled(enabled && svc.canMove(currentOri.turn180()));
        leftBtn.setEnabled(enabled && svc.canMove(currentOri.turnLeft()));
        rightBtn.setEnabled(enabled && svc.canMove(currentOri.turnRight()));
        
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
        this.setBackground(bg);
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
