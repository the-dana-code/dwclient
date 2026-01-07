package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.core.MudCommandProcessor;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;

public final class QuickLinksPanel extends JPanel {
    private final MudCommandProcessor commandProcessor;

    private record QuickLink(String name, int mapId, int x, int y) {}

    private static final QuickLink[] LINKS = {
            new QuickLink("Mended Drum", 1, 718, 802),
            new QuickLink("Bologna shop", 1, 648, 897),
            new QuickLink("Jobs Market", 1, 699, 905),
            new QuickLink("Wisdom Buff", 38, 838, 265),
            new QuickLink("Intelligence Buff", 25, 290, 200)
    };

    public QuickLinksPanel(MudCommandProcessor commandProcessor) {
        this.commandProcessor = commandProcessor;
        setLayout(new BorderLayout());

        JEditorPane editorPane = new JEditorPane();
        editorPane.setEditable(false);
        editorPane.setContentType("text/html");

        // Hide the caret
        editorPane.setCaret(new javax.swing.text.DefaultCaret() {
            @Override
            public void paint(Graphics g) {
                // do nothing
            }
        });

        StringBuilder html = new StringBuilder("<html><body style='font-family: sans-serif; padding: 5px;'>");
        html.append("<b>Quick Links</b><br><br>");
        for (int i = 0; i < LINKS.length; i++) {
            html.append("<a href='").append(i).append("'>").append(LINKS[i].name()).append("</a><br><br>");
        }
        html.append("</body></html>");

        editorPane.setText(html.toString());
        editorPane.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                try {
                    int index = Integer.parseInt(e.getDescription());
                    QuickLink link = LINKS[index];
                    commandProcessor.speedwalkTo(link.mapId(), link.x(), link.y());
                } catch (Exception ex) {
                    // Ignore or log
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(editorPane);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);
        setPreferredSize(new Dimension(150, 0));
    }
}
