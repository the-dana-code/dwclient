package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.core.MudCommandProcessor;

import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.event.HyperlinkEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Dimension;

public final class QuickLinksPanel extends JPanel implements FontChangeListener {
    private static final Color BACKGROUND = new Color(10, 10, 15);
    private static final Color TEXT_COLOR = new Color(220, 220, 220);
    private static final Color LINK_COLOR = new Color(110, 160, 255);
    private static final Color SUBTEXT_COLOR = new Color(170, 170, 190);

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
        setLayout(new BorderLayout(0, 8));
        setBackground(BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        editorPane.setEditable(false);
        editorPane.setContentType("text/html");
        editorPane.setBackground(BACKGROUND);
        editorPane.setForeground(TEXT_COLOR);
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
        scrollPane.getViewport().setBackground(BACKGROUND);
        add(scrollPane, BorderLayout.CENTER);
        setPreferredSize(new Dimension(300, 0));
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
        html.append("<html><head><style>")
                .append("body{font-family:'").append(cssFontFamily(font))
                .append("';font-size:").append(font.getSize2D()).append("pt;color:")
                .append(toHex(TEXT_COLOR)).append(";background-color:")
                .append(toHex(BACKGROUND)).append(";}")
                .append("a{color:").append(toHex(LINK_COLOR)).append(";text-decoration:none;}")
                .append(".muted{color:").append(toHex(SUBTEXT_COLOR)).append(";}")
                .append(".row{margin-top:6px;}")
                .append("</style></head><body>");
        html.append("<div class=\"muted\"><strong>Quick Links</strong></div>");
        for (int i = 0; i < LINKS.length; i++) {
            html.append("<div class=\"row\"><a href=\"")
                    .append(i)
                    .append("\">")
                    .append(LINKS[i].name())
                    .append("</a></div>");
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

    private static String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
