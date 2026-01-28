package com.danavalerie.matrixmudrelay.ui;

import java.awt.Container;
import java.awt.Point;
import java.awt.event.HierarchyEvent;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

public class SpeedwalkMenuItem extends KeepOpenMenuItem {
    private static final String SPEEDWALK_SUFFIX = " →";
    private static final String ESTIMATE_PREFIX = "(";
    private static final String ESTIMATE_SUFFIX = ")";

    private static final Set<SpeedwalkMenuItem> INSTANCES =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static volatile String currentRoomId;
    private static volatile SpeedwalkEstimateProvider estimateProvider;

    private final Supplier<String> targetRoomIdSupplier;
    private String cachedRoomId;
    private String cachedTargetRoomId;
    private String baseText;
    private String estimateSuffix;

    public SpeedwalkMenuItem(String text, boolean keepMenuOpen) {
        this(text, keepMenuOpen, (Supplier<String>) null);
    }

    public SpeedwalkMenuItem(String text, boolean keepMenuOpen, String targetRoomId) {
        this(text, keepMenuOpen, targetRoomId != null ? () -> targetRoomId : null);
    }

    public SpeedwalkMenuItem(String text, boolean keepMenuOpen, Supplier<String> targetRoomIdSupplier) {
        super(text, keepMenuOpen);
        this.targetRoomIdSupplier = targetRoomIdSupplier;
        registerInstance();
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                refreshEstimateIfNeeded();
            }
        });
    }

    public SpeedwalkMenuItem(String text, JComponent parentMenu, boolean keepMenuOpen) {
        this(text, parentMenu, keepMenuOpen, (Supplier<String>) null);
    }

    public SpeedwalkMenuItem(String text, JComponent parentMenu, boolean keepMenuOpen, String targetRoomId) {
        this(text, parentMenu, keepMenuOpen, targetRoomId != null ? () -> targetRoomId : null);
    }

    public SpeedwalkMenuItem(String text, JComponent parentMenu, boolean keepMenuOpen, Supplier<String> targetRoomIdSupplier) {
        super(text, parentMenu, keepMenuOpen);
        this.targetRoomIdSupplier = targetRoomIdSupplier;
        registerInstance();
        addHierarchyListener(event -> {
            if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
                refreshEstimateIfNeeded();
            }
        });
    }

    public static void setCurrentRoomId(String roomId) {
        currentRoomId = roomId;
        refreshShowingItems();
    }

    public static void setEstimateProvider(SpeedwalkEstimateProvider provider) {
        estimateProvider = provider;
    }

    private void registerInstance() {
        INSTANCES.add(this);
    }

    private static void refreshShowingItems() {
        Runnable refresh = () -> {
            synchronized (INSTANCES) {
                for (SpeedwalkMenuItem item : INSTANCES) {
                    if (item != null && item.isShowing()) {
                        item.refreshEstimateIfNeeded();
                    }
                }
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            refresh.run();
        } else {
            SwingUtilities.invokeLater(refresh);
        }
    }

    @Override
    public void setText(String text) {
        baseText = normalizeBaseText(text);
        applyDisplayText();
    }

    private void refreshEstimateIfNeeded() {
        String startRoomId = currentRoomId;
        String targetRoomId = targetRoomIdSupplier != null ? targetRoomIdSupplier.get() : null;
        SpeedwalkEstimateProvider provider = estimateProvider;

        if (startRoomId == null || startRoomId.isBlank()
                || targetRoomId == null || targetRoomId.isBlank()
                || provider == null) {
            cachedRoomId = startRoomId;
            cachedTargetRoomId = targetRoomId;
            if (estimateSuffix != null) {
                estimateSuffix = null;
                applyDisplayText();
            }
            return;
        }

        if (Objects.equals(startRoomId, cachedRoomId) && Objects.equals(targetRoomId, cachedTargetRoomId)) {
            return;
        }
        cachedRoomId = startRoomId;
        cachedTargetRoomId = targetRoomId;
        SpeedwalkEstimate estimate = provider.estimate(startRoomId, targetRoomId);
        String newSuffix = formatEstimate(estimate);
        if (!Objects.equals(newSuffix, estimateSuffix)) {
            estimateSuffix = newSuffix;
            applyDisplayText();
        }
    }

    private String normalizeBaseText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text;
        if (normalized.endsWith(SPEEDWALK_SUFFIX)) {
            normalized = normalized.substring(0, normalized.length() - SPEEDWALK_SUFFIX.length());
        }
        if (estimateSuffix != null && !estimateSuffix.isBlank()) {
            String trailing = " " + estimateSuffix;
            if (normalized.endsWith(trailing)) {
                normalized = normalized.substring(0, normalized.length() - trailing.length());
            }
        }
        return normalized;
    }

    private void applyDisplayText() {
        if (baseText == null) {
            super.setText(null);
            return;
        }
        StringBuilder display = new StringBuilder();
        display.append(baseText);
        if (estimateSuffix != null && !estimateSuffix.isBlank()) {
            if (display.length() > 0) {
                display.append(' ');
            }
            display.append(estimateSuffix);
        }
        display.append(SPEEDWALK_SUFFIX);
        super.setText(display.toString());
        updatePopupSizeIfNeeded();
    }

    private String formatEstimate(SpeedwalkEstimate estimate) {
        if (estimate == null) {
            return null;
        }
        String value = estimate.hasTeleport() ? "TP+" + estimate.steps() : Integer.toString(estimate.steps());
        return ESTIMATE_PREFIX + value + ESTIMATE_SUFFIX;
    }

    public interface SpeedwalkEstimateProvider {
        SpeedwalkEstimate estimate(String startRoomId, String targetRoomId);
    }

    static record SpeedwalkEstimate(int steps, boolean hasTeleport) {
    }

    private void updatePopupSizeIfNeeded() {
        if (!isShowing()) {
            return;
        }
        Object parentProp = getClientProperty(PARENT_MENU_KEY);
        Container parent = (parentProp instanceof Container) ? (Container) parentProp : getParent();
        if (parent instanceof JMenu) {
            parent = ((JMenu) parent).getPopupMenu();
        }
        if (!(parent instanceof JPopupMenu popup)) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (!popup.isShowing()) {
                return;
            }
            if (!popup.getSize().equals(popup.getPreferredSize())) {
                Point p = popup.getLocationOnScreen();
                popup.setVisible(false);
                SwingUtilities.invokeLater(() -> {
                    Point q = new Point(p);
                    SwingUtilities.convertPointFromScreen(q, popup.getInvoker());
                    popup.show(popup.getInvoker(), q.x, q.y);
                });
            }
        });
    }
}
