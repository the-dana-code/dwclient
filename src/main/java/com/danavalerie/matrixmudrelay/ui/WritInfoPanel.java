package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.core.StoreInventoryTracker;
import com.danavalerie.matrixmudrelay.core.WritTracker;
import com.danavalerie.matrixmudrelay.core.WritRouteMappings;
import com.danavalerie.matrixmudrelay.util.ThreadUtils;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class WritInfoPanel extends JPanel implements FontChangeListener {
    private static final Color BACKGROUND = new Color(10, 10, 15);
    private static final Color TEXT_COLOR = new Color(220, 220, 220);
    private static final Color LINK_COLOR = new Color(110, 160, 255);
    private static final Color VISITED_LINK_COLOR = new Color(170, 120, 255);
    private static final Color SUBTEXT_COLOR = new Color(170, 170, 190);

    private final JLabel titleLabel = new JLabel("Writ Info");
    private final JEditorPane writPane = new JEditorPane();
    private final JScrollPane scrollPane;
    private final List<WritTracker.WritRequirement> requirements = new ArrayList<>();
    private final List<Boolean> finished = new ArrayList<>();
    private final Set<String> visitedLinks = new HashSet<>();
    private final Consumer<String> commandSender;
    private final StoreInventoryTracker storeInventoryTracker;
    private final Consumer<String> errorSender;
    private final RouteHandler routeHandler;
    private Font baseFont;

    public WritInfoPanel(Consumer<String> commandSender,
                         StoreInventoryTracker storeInventoryTracker,
                         Consumer<String> errorSender,
                         RouteHandler routeHandler) {
        this.commandSender = Objects.requireNonNull(commandSender, "commandSender");
        this.storeInventoryTracker = Objects.requireNonNull(storeInventoryTracker, "storeInventoryTracker");
        this.errorSender = Objects.requireNonNull(errorSender, "errorSender");
        this.routeHandler = Objects.requireNonNull(routeHandler, "routeHandler");
        setLayout(new BorderLayout(0, 8));
        setBackground(BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        titleLabel.setForeground(TEXT_COLOR);
        add(titleLabel, BorderLayout.NORTH);

        writPane.setContentType("text/html");
        writPane.setEditable(false);
        writPane.setBackground(BACKGROUND);
        writPane.setForeground(TEXT_COLOR);
        baseFont = writPane.getFont();

        // Hide the caret
        writPane.setCaret(new javax.swing.text.DefaultCaret() {
            @Override
            public void paint(java.awt.Graphics g) {
                // do nothing
            }
        });

        writPane.setText(renderHtml());
        writPane.addHyperlinkListener(new WritLinkListener());

        scrollPane = new JScrollPane(writPane);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(BACKGROUND);
        add(scrollPane, BorderLayout.CENTER);
    }

    public void updateWrit(List<WritTracker.WritRequirement> requirements) {
        SwingUtilities.invokeLater(() -> {
            ThreadUtils.checkEdt();
            var viewPosition = scrollPane.getViewport().getViewPosition();
            this.requirements.clear();
            this.finished.clear();
            this.visitedLinks.clear();
            if (requirements != null) {
                this.requirements.addAll(requirements);
                for (int i = 0; i < requirements.size(); i++) {
                    this.finished.add(false);
                }
            }
            writPane.setText(renderHtml());
            SwingUtilities.invokeLater(() -> scrollPane.getViewport().setViewPosition(viewPosition));
        });
    }

    @Override
    public void onFontChange(Font font) {
        baseFont = font;
        titleLabel.setFont(font.deriveFont(Font.BOLD));
        writPane.setFont(font);
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
                .append(".card{border:1px solid ").append(toHex(SUBTEXT_COLOR))
                .append(";padding:8px;margin-bottom:8px;border-radius:6px;}")
                .append(".row{margin-top:4px;}")
                .append(".row.actions{margin-bottom:14px;}")
                .append(".spacer{height:8px;}")
                .append("</style></head><body>");

        if (requirements.isEmpty()) {
            html.append("<div class=\"muted\">No writ requirements tracked yet.</div>");
        } else {
            for (int i = 0; i < requirements.size(); i++) {
                WritTracker.WritRequirement req = requirements.get(i);
                String toggleHref = "toggle:" + i;
                String itemHref = "item:" + i;
                String npcHref = "npc:" + i;
                String locHref = "loc:" + i;
                String buyHref = "buy:" + i;
                String deliverHref = "deliver:" + i;
                String routeHref = "route:" + i;
                String checkbox = finished.get(i) ? "&#x2611;" : "&#x2610;";
                boolean hasRoute = WritRouteMappings.findRoute(req.npc(), req.locationDisplay()).isPresent();
                html.append("<div class=\"card\">")
                        .append("<div>")
                        .append("<a href=\"").append(toggleHref).append("\"")
                        .append(linkClass(toggleHref)).append(">")
                        .append(checkbox).append("</a>")
                        .append(" <strong>Writ ").append(i + 1).append("</strong>")
                        .append("</div>")
                        .append("<div class=\"row\">")
                        .append("<a href=\"").append(itemHref).append("\"")
                        .append(linkClass(itemHref)).append(">")
                        .append(req.quantity()).append(" ").append(escape(req.item()))
                        .append("</a>")
                        .append("</div>")
                        .append("<div class=\"row actions\">")
                        .append("<a href=\"").append("list:").append(i).append("\"")
                        .append(linkClass("list:" + i)).append(">[List]</a>")
                        .append("  ")
                        .append("<a href=\"").append(buyHref).append("\"")
                        .append(linkClass(buyHref)).append(">[Buy]</a>")
                        .append("</div>")
                        .append("<div class=\"spacer\"></div>")
                        .append("<div class=\"row muted\">Deliver to ")
                        .append("<a href=\"").append(npcHref).append("\"")
                        .append(linkClass(npcHref)).append(">")
                        .append(escape(req.npc())).append("</a>")
                        .append(" ")
                        .append("</div>")
                        .append("<div class=\"row muted\">Location: ")
                        .append("<a href=\"").append(locHref).append("\"")
                        .append(linkClass(locHref)).append(">")
                        .append(escape(req.locationName())).append("</a>")
                        .append(req.locationSuffix().isBlank() ? "" : " " + escape(req.locationSuffix()))
                        .append("</div>")
                        .append("<div class=\"row\">")
                        .append(hasRoute ? "<a href=\"" + routeHref + "\"" + linkClass(routeHref) + ">[Route]</a> " : "")
                        .append("<a href=\"").append(deliverHref).append("\"")
                        .append(linkClass(deliverHref)).append(">[Deliver]</a>")
                        .append("</div>")
                        .append("</div>");
            }
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
        if (description == null || description.isEmpty()) {
            return;
        }
        String[] parts = description.split(":", 2);
        if (parts.length != 2) {
            return;
        }
        String action = parts[0];
        int index;
        try {
            index = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ignored) {
            return;
        }
        if (index < 0 || index >= requirements.size()) {
            return;
        }
        if ("toggle".equals(action)) {
            finished.set(index, !finished.get(index));
            visitedLinks.add(description);
            updatePanePreservingScroll();
            return;
        }
        if ("route".equals(action)) {
            handleRoute(index, description);
            return;
        }
        int writNumber = index + 1;
        String command = switch (action) {
            case "list" -> "list";
            case "deliver" -> "mm writ " + writNumber + " deliver";
            case "item" -> "mm item exact " + requirements.get(index).item();
            case "npc" -> "mm writ " + writNumber + " npc";
            case "loc" -> "mm writ " + writNumber + " loc";
            default -> null;
        };
        if ("buy".equals(action)) {
            handleStoreBuy(index, description);
            return;
        }
        if (command != null) {
            visitedLinks.add(description);
            commandSender.accept(command);
            updatePanePreservingScroll();
        }
    }

    private void handleStoreBuy(int index, String description) {
        if (!storeInventoryTracker.hasInventory()) {
            errorSender.accept("Store inventory not cached yet. Use [List] first.");
            return;
        }
        WritTracker.WritRequirement requirement = requirements.get(index);
        if (storeInventoryTracker.isNameListed()) {
            visitedLinks.add(description);
            commandSender.accept("buy " + requirement.quantity() + " " + requirement.item());
            updatePanePreservingScroll();
            return;
        }
        storeInventoryTracker.findMatch(requirement.item()).ifPresentOrElse(item -> {
            visitedLinks.add(description);
            commandSender.accept("buy " + item.id());
            updatePanePreservingScroll();
        }, () -> errorSender.accept("Store inventory does not list \"" + requirement.item() + "\"."));
    }

    private void handleRoute(int index, String description) {
        WritTracker.WritRequirement requirement = requirements.get(index);
        WritRouteMappings.findRoute(requirement.npc(), requirement.locationDisplay()).ifPresentOrElse(target -> {
            visitedLinks.add(description);
            routeHandler.routeTo(target.mapId(), target.x(), target.y());
            updatePanePreservingScroll();
        }, () -> errorSender.accept("No route mapping for \"" + requirement.npc()
                + "\" at \"" + requirement.locationDisplay() + "\"."));
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
        writPane.setText(renderHtml());
        SwingUtilities.invokeLater(() -> scrollPane.getViewport().setViewPosition(viewPosition));
    }

    @FunctionalInterface
    public interface RouteHandler {
        void routeTo(int mapId, int x, int y);
    }

    private final class WritLinkListener implements HyperlinkListener {
        @Override
        public void hyperlinkUpdate(HyperlinkEvent e) {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                handleLink(e.getDescription());
            }
        }
    }
}
