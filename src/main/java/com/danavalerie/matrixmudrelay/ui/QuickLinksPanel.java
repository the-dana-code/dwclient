package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.core.MudCommandProcessor;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.awt.*;

public final class QuickLinksPanel extends JPanel implements FontChangeListener {
    private final MudCommandProcessor commandProcessor;
    private final JEditorPane editorPane = new JEditorPane();
    private Font baseFont;

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

        editorPane.setEditable(false);
        editorPane.setContentType("text/html");
        baseFont = editorPane.getFont();

        // Hide the caret
        editorPane.setCaret(new javax.swing.text.DefaultCaret() {
            @Override
            public void paint(Graphics g) {
                // do nothing
            }
        });

        editorPane.setText(renderHtml());
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

    @Override
    public void onFontChange(Font font) {
        baseFont = font;
        editorPane.setFont(font);
        editorPane.setText(renderHtml());
    }

    private String renderHtml() {
        Font font = resolveBaseFont();
        StringBuilder html = new StringBuilder();
        html.append("<html><body style='font-family: \"")
                .append(cssFontFamily(font))
                .append("\"; font-size: ")
                .append(font.getSize())
                .append("px; padding: 5px;'>");
        html.append("<b>Quick Links</b><br><br>");
        for (int i = 0; i < LINKS.length; i++) {
            html.append("<a href='").append(i).append("'>").append(LINKS[i].name()).append("</a><br><br>");
        }
        html.append("</body></html>");
        return html.toString();
    }

    private Font resolveBaseFont() {
        return baseFont != null ? baseFont : getFont();
    }

    private static String cssFontFamily(Font font) {
        return font.getFamily().replace("\"", "\\\"");
    }
}
