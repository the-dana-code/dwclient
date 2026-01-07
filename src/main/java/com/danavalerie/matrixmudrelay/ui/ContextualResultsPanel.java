package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.core.ContextualResultList;

import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class ContextualResultsPanel extends JPanel implements FontChangeListener {
    private static final Color BACKGROUND = new Color(10, 10, 15);
    private static final Color TEXT_COLOR = new Color(220, 220, 220);
    private static final Color LINK_COLOR = new Color(110, 160, 255);
    private static final Color VISITED_LINK_COLOR = new Color(170, 120, 255);
    private static final Color SUBTEXT_COLOR = new Color(170, 170, 190);

    private final JLabel titleLabel = new JLabel("Search Results");
    private final JEditorPane resultsPane = new JEditorPane();
    private final JScrollPane scrollPane;
    private final Consumer<String> commandSender;
    private final Set<String> visitedLinks = new HashSet<>();
    private Font baseFont;
    private ContextualResultList currentResults = new ContextualResultList(
            "Search Results",
            java.util.List.of(),
            "No results yet.",
            null
    );

    public ContextualResultsPanel(Consumer<String> commandSender) {
        this.commandSender = Objects.requireNonNull(commandSender, "commandSender");
        setLayout(new BorderLayout(0, 8));
        setBackground(BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        titleLabel.setForeground(TEXT_COLOR);
        add(titleLabel, BorderLayout.NORTH);

        resultsPane.setContentType("text/html");
        resultsPane.setEditable(false);
        resultsPane.setBackground(BACKGROUND);
        resultsPane.setForeground(TEXT_COLOR);
        baseFont = resultsPane.getFont();

        // Hide the caret
        resultsPane.setCaret(new javax.swing.text.DefaultCaret() {
            @Override
            public void paint(java.awt.Graphics g) {
                // do nothing
            }
        });

        resultsPane.setText(renderHtml());
        resultsPane.addHyperlinkListener(new ResultsLinkListener());

        scrollPane = new JScrollPane(resultsPane);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(BACKGROUND);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void updateResults(ContextualResultList results) {
        SwingUtilities.invokeLater(() -> {
            var viewPosition = scrollPane.getViewport().getViewPosition();
            if (results != null) {
                currentResults = results;
            }
            titleLabel.setText(currentResults.title() == null ? "Search Results" : currentResults.title());
            visitedLinks.clear();
            resultsPane.setText(renderHtml());
            SwingUtilities.invokeLater(() -> scrollPane.getViewport().setViewPosition(viewPosition));
        });
    }

    @Override
    public void onFontChange(Font font) {
        baseFont = font;
        titleLabel.setFont(font.deriveFont(Font.BOLD));
        resultsPane.setFont(font);
        updatePanePreservingScroll();
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
                .append("a.visited{color:").append(toHex(VISITED_LINK_COLOR)).append(";}")
                .append(".muted{color:").append(toHex(SUBTEXT_COLOR)).append(";}")
                .append("ol{margin:0;padding-left:18px;}")
                .append("li{margin-bottom:4px;}")
                .append("</style></head><body>");

        if (currentResults.results().isEmpty()) {
            String empty = currentResults.emptyMessage() == null ? "No results." : currentResults.emptyMessage();
            html.append("<div class=\"muted\">").append(escape(empty)).append("</div>");
        } else {
            html.append("<ol>");
            for (ContextualResultList.ContextualResult result : currentResults.results()) {
                String href = "cmd:" + encode(result.command());
                if (result.mapCommand() != null && !result.mapCommand().isBlank()) {
                    String mapHref = "cmd:" + encode(result.mapCommand());
                    html.append("<li><a href=\"").append(mapHref).append("\"")
                            .append(linkClass(mapHref)).append(">")
                            .append("[Map]")
                            .append("</a> ");
                } else {
                    html.append("<li>");
                }
                html.append("<a href=\"").append(href).append("\"")
                        .append(linkClass(href)).append(">")
                        .append(escape(result.label()))
                        .append("</a>");
                html.append("</li>");
            }
            html.append("</ol>");
        }

        if (currentResults.footer() != null && !currentResults.footer().isBlank()) {
            html.append("<div class=\"muted\" style=\"margin-top:6px;\">")
                    .append(escape(currentResults.footer()))
                    .append("</div>");
        }

        html.append("</body></html>");
        return html.toString();
    }

    private Font resolveBaseFont() {
        return baseFont != null ? baseFont : getFont();
    }

    private static String cssFontFamily(Font font) {
        return font.getFamily().replace("'", "\\'");
    }

    private void handleLink(String description) {
        if (description == null || !description.startsWith("cmd:")) {
            return;
        }
        String encoded = description.substring(4);
        String command = decode(encoded);
        if (command.isBlank()) {
            return;
        }
        visitedLinks.add(description);
        updatePanePreservingScroll();
        commandSender.accept(command);
    }

    private static String encode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String decode(String value) {
        if (value == null) {
            return "";
        }
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private static String escape(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String linkClass(String href) {
        return visitedLinks.contains(href) ? " class=\"visited\"" : "";
    }

    private void updatePanePreservingScroll() {
        var viewPosition = scrollPane.getViewport().getViewPosition();
        resultsPane.setText(renderHtml());
        SwingUtilities.invokeLater(() -> scrollPane.getViewport().setViewPosition(viewPosition));
    }

    private final class ResultsLinkListener implements HyperlinkListener {
        @Override
        public void hyperlinkUpdate(HyperlinkEvent e) {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                handleLink(e.getDescription());
            }
        }
    }
}
