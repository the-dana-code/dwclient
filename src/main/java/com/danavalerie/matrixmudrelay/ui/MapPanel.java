package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.core.RoomMapService;
import com.danavalerie.matrixmudrelay.util.DarkThemeConverter;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class MapPanel extends JPanel {
    public static final Color BACKGROUND_LIGHT = new Color(245, 245, 240);
    public static final Color FOREGROUND_DARK = new Color(35, 35, 35);
    public static final Color BACKGROUND_DARK = new Color(12, 12, 18);
    public static final Color FOREGROUND_LIGHT = new Color(220, 220, 220);
    private static final int ZOOM_MIN = 50;
    private static final int ZOOM_MAX = 200;
    private static final int ZOOM_DEFAULT = 100;
    private static final RoomMapService.MapArea NONE_AREA = new RoomMapService.MapArea(-1, "<None>");
    private final RoomMapService mapService = new RoomMapService("database.db");
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "map-render");
        t.setDaemon(true);
        return t;
    });
    private final JComboBox<RoomMapService.MapArea> areaComboBox;
    private final DefaultComboBoxModel<RoomMapService.MapArea> areaComboBoxModel;
    private final Map<Integer, RoomMapService.MapArea> areaOptions = new HashMap<>();
    private final JLabel mapLabel = new JLabel("Map will appear here", SwingConstants.CENTER);
    private final JScrollPane scrollPane;
    private final AtomicReference<String> lastRoomId = new AtomicReference<>();
    private final AtomicReference<BufferedImage> baseImageCache = new AtomicReference<>();
    private final JSlider zoomSlider;
    private final JLabel zoomLabel = new JLabel();
    private final IntConsumer zoomChangeListener;
    private final Consumer<Boolean> invertChangeListener;
    private final JButton invertButton = new JButton("Invert");
    private volatile boolean invertMap;
    private Consumer<RoomMapService.RoomLocation> speedwalkHandler;
    private final JButton speedWalkButton = new JButton("Speed Walk");
    private List<RoomMapService.RoomLocation> speedwalkPath = List.of();
    private int zoomPercent;
    private volatile BufferedImage lastBaseImage;
    private volatile String lastTitle;
    private volatile Integer lastMapId;
    private volatile Point lastFocusPoint;
    private volatile Dimension lastImageSize;
    private volatile RoomMapService.MapImage lastMapImage;
    private volatile RoomMapService.RoomLocation selectedRoom;
    private String currentRoomId;
    private Timer animationTimer;
    private boolean updatingAreaSelection;

    public MapPanel(int initialZoomPercent,
                    IntConsumer zoomChangeListener,
                    boolean initialInvertMap,
                    Consumer<Boolean> invertChangeListener) {
        this.zoomPercent = sanitizeZoom(initialZoomPercent);
        this.zoomChangeListener = zoomChangeListener;
        this.invertMap = initialInvertMap;
        this.invertChangeListener = invertChangeListener;
        setLayout(new BorderLayout());
        setBackground(getMapBackground());
        areaComboBoxModel = new DefaultComboBoxModel<>();
        areaComboBoxModel.addElement(NONE_AREA);
        for (RoomMapService.MapArea area : mapService.listMapAreas()) {
            areaOptions.put(area.mapId(), area);
            areaComboBoxModel.addElement(area);
        }
        areaComboBox = new JComboBox<>(areaComboBoxModel);
        areaComboBox.setSelectedItem(NONE_AREA);
        areaComboBox.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        areaComboBox.addActionListener(event -> handleAreaSelection());
        mapLabel.setOpaque(true);
        mapLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        speedWalkButton.setEnabled(false);
        speedWalkButton.addActionListener(event -> handleSpeedWalk());
        invertButton.addActionListener(event -> toggleInvert());
        JPanel titlePanel = new JPanel(new BorderLayout());
        JPanel titleActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        titleActions.setOpaque(false);
        titleActions.add(speedWalkButton);
        titlePanel.add(areaComboBox, BorderLayout.CENTER);
        titlePanel.add(titleActions, BorderLayout.EAST);
        scrollPane = new JScrollPane(mapLabel);
        add(titlePanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        zoomSlider = new JSlider(ZOOM_MIN, ZOOM_MAX, this.zoomPercent);
        zoomSlider.setPaintTicks(true);
        zoomSlider.setMajorTickSpacing(25);
        zoomSlider.setMinorTickSpacing(5);
        zoomSlider.addChangeListener(event -> onZoomChanged());
        updateZoomLabel();
        JPanel zoomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JLabel zoomTitleLabel = new JLabel("Zoom:");
        zoomPanel.add(zoomTitleLabel);
        zoomPanel.add(zoomSlider);
        zoomPanel.add(zoomLabel);
        zoomPanel.add(invertButton);
        add(zoomPanel, BorderLayout.SOUTH);
        updateColors();
        mapLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                handleMapClick(event.getPoint());
            }
        });
    }

    private void toggleInvert() {
        invertMap = !invertMap;
        updateColors();

        String roomId = lastRoomId.get();
        if (roomId != null) {
            executor.submit(() -> {
                try {
                    RoomMapService.MapImage mapImage = mapService.renderMapImage(roomId, invertMap);
                    BufferedImage image = resolveBaseImage(mapImage);
                    RoomMapService.RoomLocation currentRoom = new RoomMapService.RoomLocation(
                            mapImage.roomId(),
                            mapImage.mapId(),
                            mapImage.roomX(),
                            mapImage.roomY(),
                            mapImage.roomShort()
                    );
                    showImage(mapImage, image, currentRoom);
                } catch (Exception e) {
                    BufferedImage cached = baseImageCache.get();
                    if (cached != null) {
                        lastBaseImage = (invertMap) ? DarkThemeConverter.toDarkTheme(cached) : cached;
                        updateDisplayedImage();
                    }
                }
            });
        } else {
            BufferedImage cached = baseImageCache.get();
            if (cached != null) {
                lastBaseImage = (invertMap) ? DarkThemeConverter.toDarkTheme(cached) : cached;
                updateDisplayedImage();
            }
        }

        if (invertChangeListener != null) {
            invertChangeListener.accept(invertMap);
        }
    }

    public boolean isInverted() {
        return invertMap;
    }

    private void updateColors() {
        Color bg = getMapBackground();
        Color fg = getMapForeground();
        updateComponentTree(this, bg, fg);
        
        areaComboBox.setBackground(invertMap ? new Color(30, 30, 35) : new Color(235, 235, 227));
        
        Component parent = getParent();
        if (parent instanceof JPanel) {
            parent.setBackground(bg);
        }
    }

    private void updateComponentTree(Component c, Color bg, Color fg) {
        if (c instanceof JPanel || c instanceof JScrollPane || c instanceof JViewport) {
            c.setBackground(bg);
            for (Component child : ((Container) c).getComponents()) {
                updateComponentTree(child, bg, fg);
            }
        } else if (c instanceof JLabel) {
            c.setForeground(fg);
            c.setBackground(bg);
        } else if (c instanceof JComboBox) {
            c.setForeground(fg);
        } else if (c instanceof JSlider) {
            c.setForeground(fg);
            c.setBackground(bg);
        } else if (c instanceof JButton) {
            c.setForeground(fg);
            c.setBackground(bg);
        }
    }

    private Color getMapBackground() {
        return invertMap ? BACKGROUND_DARK : BACKGROUND_LIGHT;
    }

    private Color getMapForeground() {
        return invertMap ? FOREGROUND_LIGHT : FOREGROUND_DARK;
    }

    public void updateMap(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            showMessage("No room info available yet.");
            return;
        }
        String previous = lastRoomId.getAndSet(roomId);
        if (Objects.equals(previous, roomId)) {
            return;
        }
        executor.submit(() -> {
            try {
                RoomMapService.MapImage mapImage = mapService.renderMapImage(roomId, invertMap);
                BufferedImage image = resolveBaseImage(mapImage);
                RoomMapService.RoomLocation currentRoom = new RoomMapService.RoomLocation(
                        mapImage.roomId(),
                        mapImage.mapId(),
                        mapImage.roomX(),
                        mapImage.roomY(),
                        mapImage.roomShort()
                );
                showImage(mapImage, image, currentRoom);
            } catch (RoomMapService.MapLookupException e) {
                showMessage("Map error: " + e.getMessage());
            } catch (Exception e) {
                showMessage("Map error: Unable to render map.");
            }
        });
    }

    public void updateCurrentRoom(String roomId) {
        currentRoomId = roomId;
        updateSpeedWalkState();
    }

    public void setSpeedwalkHandler(Consumer<RoomMapService.RoomLocation> speedwalkHandler) {
        this.speedwalkHandler = speedwalkHandler;
    }

    public void setSpeedwalkPath(List<RoomMapService.RoomLocation> path) {
        this.speedwalkPath = path == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(path));
        updateDisplayedImage();
    }

    public void shutdown() {
        executor.shutdownNow();
    }


    private void showImage(RoomMapService.MapImage mapImage,
                           BufferedImage image,
                           RoomMapService.RoomLocation currentRoom) {
        lastMapImage = mapImage;
        lastBaseImage = image;
        lastTitle = mapImage.mapName();
        lastMapId = mapImage.mapId();
        lastFocusPoint = new Point(mapImage.currentX(), mapImage.currentY());
        lastImageSize = new Dimension(mapImage.width(), mapImage.height());
        selectedRoom = currentRoom;
        updateDisplayedImage();
        updateSpeedWalkState();
    }

    private void updateDisplayedImage() {
        BufferedImage image = lastBaseImage;
        String title = lastTitle;
        Integer mapId = lastMapId;
        Point focusPoint = lastFocusPoint;
        Dimension imageSize = lastImageSize;
        RoomMapService.MapImage mapImage = lastMapImage;
        SwingUtilities.invokeLater(() -> {
            if (mapId != null) {
                updatingAreaSelection = true;
                RoomMapService.MapArea area = ensureAreaOption(mapId, title);
                areaComboBox.setSelectedItem(area);
                updatingAreaSelection = false;
            }
            if (image == null || imageSize == null) {
                mapLabel.setIcon(null);
                mapLabel.setText("");
                mapLabel.setPreferredSize(null);
                mapLabel.revalidate();
                configureAnimation(null);
                return;
            }
            BufferedImage scaled = scaleImage(image, zoomPercent);
            Dimension scaledSize = scaleDimension(imageSize, zoomPercent);
            Point scaledFocus = scalePoint(focusPoint, zoomPercent);
            List<Point> scaledPath = buildScaledSpeedwalkPath(mapImage, mapId, zoomPercent);
            int markerDiameter = scaledMarkerDiameter(zoomPercent);
            AnimatedMapIcon icon = new AnimatedMapIcon(scaled, scaledFocus, markerDiameter, scaledPath, invertMap);
            mapLabel.setIcon(icon);
            mapLabel.setText("");
            Insets insets = mapLabel.getInsets();
            Dimension preferredSize = new Dimension(
                    scaledSize.width + insets.left + insets.right,
                    scaledSize.height + insets.top + insets.bottom
            );
            mapLabel.setPreferredSize(preferredSize);
            mapLabel.revalidate();
            configureAnimation(icon);
            if (scaledFocus != null && scaledSize != null) {
                SwingUtilities.invokeLater(() -> centerViewOnPoint(scaledFocus, scaledSize));
            }
        });
    }

    private List<Point> buildScaledSpeedwalkPath(RoomMapService.MapImage mapImage, Integer mapId, int zoomPercent) {
        if (mapImage == null || mapId == null) {
            return List.of();
        }
        if (speedwalkPath.isEmpty()) {
            return List.of();
        }
        List<Point> points = new ArrayList<>();
        boolean previousOnMap = false;
        for (RoomMapService.RoomLocation location : speedwalkPath) {
            if (location == null || location.mapId() != mapId) {
                if (previousOnMap) {
                    points.add(null);
                }
                previousOnMap = false;
                continue;
            }
            Point basePoint = mapToImagePoint(location, mapImage);
            if (basePoint == null) {
                continue;
            }
            points.add(scalePoint(basePoint, zoomPercent));
            previousOnMap = true;
        }
        return Collections.unmodifiableList(points);
    }

    private void showMessage(String message) {
        lastBaseImage = null;
        lastTitle = null;
        lastMapId = null;
        lastFocusPoint = null;
        lastImageSize = null;
        lastMapImage = null;
        selectedRoom = null;
        lastRoomId.set(null);
        SwingUtilities.invokeLater(() -> {
            updatingAreaSelection = true;
            areaComboBox.setSelectedItem(NONE_AREA);
            updatingAreaSelection = false;
            mapLabel.setIcon(null);
            mapLabel.setText(message);
            mapLabel.setPreferredSize(null);
            configureAnimation(null);
            speedWalkButton.setEnabled(false);
        });
    }

    private BufferedImage resolveBaseImage(RoomMapService.MapImage mapImage) throws Exception {
        if (mapImage.baseImageReused()) {
            BufferedImage cached = baseImageCache.get();
            if (cached != null) {
                return (invertMap && !mapImage.isDark()) ? DarkThemeConverter.toDarkTheme(cached) : cached;
            }
        }
        if (mapImage.data() == null) {
            return null;
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(mapImage.data()));
        baseImageCache.set(image);
        return (invertMap && !mapImage.isDark()) ? DarkThemeConverter.toDarkTheme(image) : image;
    }

    private void onZoomChanged() {
        int newValue = sanitizeZoom(zoomSlider.getValue());
        if (newValue == zoomPercent) {
            return;
        }
        zoomPercent = newValue;
        updateZoomLabel();
        updateDisplayedImage();
        if (!zoomSlider.getValueIsAdjusting() && zoomChangeListener != null) {
            zoomChangeListener.accept(zoomPercent);
        }
    }

    private void updateZoomLabel() {
        zoomLabel.setText(zoomPercent + "%");
    }

    private static int sanitizeZoom(int zoomPercent) {
        if (zoomPercent <= 0) {
            return ZOOM_DEFAULT;
        }
        return Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoomPercent));
    }

    private static BufferedImage scaleImage(BufferedImage image, int zoomPercent) {
        if (image == null) {
            return null;
        }
        if (zoomPercent == 100) {
            return image;
        }
        double scale = zoomPercent / 100.0;
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        int type = image.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : image.getType();
        BufferedImage scaled = new BufferedImage(width, height, type);
        Graphics2D g2 = scaled.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(image, 0, 0, width, height, null);
        g2.dispose();
        return scaled;
    }

    private static Dimension scaleDimension(Dimension dimension, int zoomPercent) {
        if (dimension == null) {
            return null;
        }
        if (zoomPercent == 100) {
            return new Dimension(dimension);
        }
        double scale = zoomPercent / 100.0;
        int width = Math.max(1, (int) Math.round(dimension.width * scale));
        int height = Math.max(1, (int) Math.round(dimension.height * scale));
        return new Dimension(width, height);
    }

    private static Point scalePoint(Point point, int zoomPercent) {
        if (point == null) {
            return null;
        }
        if (zoomPercent == 100) {
            return new Point(point);
        }
        double scale = zoomPercent / 100.0;
        int x = (int) Math.round(point.x * scale);
        int y = (int) Math.round(point.y * scale);
        return new Point(x, y);
    }

    private void handleMapClick(Point clickPoint) {
        RoomMapService.MapImage mapImage = lastMapImage;
        if (mapImage == null || lastImageSize == null) {
            return;
        }

        javax.swing.Icon icon = mapLabel.getIcon();
        if (icon == null) {
            return;
        }

        Insets insets = mapLabel.getInsets();
        int iconWidth = icon.getIconWidth();
        int iconHeight = icon.getIconHeight();
        int labelWidth = mapLabel.getWidth();
        int labelHeight = mapLabel.getHeight();

        // Calculate icon position within the label (centered)
        int xOffset = insets.left + (labelWidth - insets.left - insets.right - iconWidth) / 2;
        int yOffset = insets.top + (labelHeight - insets.top - insets.bottom - iconHeight) / 2;

        double scale = zoomPercent / 100.0;
        if (scale <= 0) {
            return;
        }

        int relativeX = clickPoint.x - xOffset;
        int relativeY = clickPoint.y - yOffset;

        int baseX = (int) Math.round(relativeX / scale);
        int baseY = (int) Math.round(relativeY / scale);
        int mapX = mapImage.minX()
                + (int) Math.round((baseX - mapImage.roomPixelOffsetX()) / (double) mapImage.imageScale());
        int mapY = mapImage.minY()
                + (int) Math.round((baseY - mapImage.roomPixelOffsetY()) / (double) mapImage.imageScale());
        executor.submit(() -> {
            try {
                RoomMapService.RoomLocation nearest = mapService.findNearestRoom(mapImage.mapId(), mapX, mapY);
                if (nearest == null) {
                    return;
                }
                updateSelectedRoom(nearest, mapImage);
            } catch (RoomMapService.MapLookupException e) {
                showMessage("Map error: " + e.getMessage());
            } catch (Exception e) {
                showMessage("Map error: Unable to update selection.");
            }
        });
    }

    private void updateSelectedRoom(RoomMapService.RoomLocation room, RoomMapService.MapImage mapImage) {
        if (room == null || mapImage == null) {
            return;
        }
        selectedRoom = room;
        lastFocusPoint = mapToImagePoint(room, mapImage);
        updateDisplayedImage();
        updateSpeedWalkState();
    }

    private Point mapToImagePoint(RoomMapService.RoomLocation room, RoomMapService.MapImage mapImage) {
        int px = (room.xpos() - mapImage.minX()) * mapImage.imageScale() + mapImage.roomPixelOffsetX();
        int py = (room.ypos() - mapImage.minY()) * mapImage.imageScale() + mapImage.roomPixelOffsetY();
        return new Point(px, py);
    }

    private void handleSpeedWalk() {
        RoomMapService.RoomLocation room = selectedRoom;
        if (room == null || speedwalkHandler == null) {
            return;
        }
        speedwalkHandler.accept(room);
    }

    private void updateSpeedWalkState() {
        RoomMapService.RoomLocation room = selectedRoom;
        boolean enabled = room != null;
        if (enabled && currentRoomId != null && room.roomId() != null) {
            enabled = !currentRoomId.equals(room.roomId());
        }
        boolean finalEnabled = enabled;
        SwingUtilities.invokeLater(() -> speedWalkButton.setEnabled(finalEnabled));
    }

    private static int scaledMarkerDiameter(int zoomPercent) {
        double scale = zoomPercent / 100.0;
        int diameter = (int) Math.round(AnimatedMapIcon.MARKER_DIAMETER_BASE * scale);
        return Math.max(4, diameter);
    }

    private void centerViewOnPoint(Point point, Dimension imageSize) {
        Dimension extent = scrollPane.getViewport().getExtentSize();
        if (extent.width <= 0 || extent.height <= 0) {
            return;
        }

        javax.swing.Icon icon = mapLabel.getIcon();
        if (icon == null) {
            return;
        }

        Insets insets = mapLabel.getInsets();
        int iconWidth = icon.getIconWidth();
        int iconHeight = icon.getIconHeight();
        int labelWidth = mapLabel.getWidth();
        int labelHeight = mapLabel.getHeight();

        // Calculate icon position within the label (centered)
        int xOffset = insets.left + (labelWidth - insets.left - insets.right - iconWidth) / 2;
        int yOffset = insets.top + (labelHeight - insets.top - insets.bottom - iconHeight) / 2;

        int componentX = point.x + xOffset;
        int componentY = point.y + yOffset;

        Dimension effectiveSize = imageSize;
        Dimension labelSize = mapLabel.getSize();
        if (labelSize.width > 0 && labelSize.height > 0) {
            effectiveSize = labelSize;
        }
        int viewX = componentX - extent.width / 2;
        int viewY = componentY - extent.height / 2;
        if (effectiveSize.width > extent.width) {
            viewX = Math.max(0, Math.min(viewX, effectiveSize.width - extent.width));
        } else {
            viewX = 0;
        }
        if (effectiveSize.height > extent.height) {
            viewY = Math.max(0, Math.min(viewY, effectiveSize.height - extent.height));
        } else {
            viewY = 0;
        }
        scrollPane.getViewport().setViewPosition(new Point(viewX, viewY));
    }

    private void configureAnimation(AnimatedMapIcon icon) {
        if (animationTimer != null) {
            animationTimer.stop();
            animationTimer = null;
        }
        if (icon == null) {
            return;
        }
        animationTimer = new Timer(60, this::onAnimationTick);
        animationTimer.start();
    }

    private void onAnimationTick(ActionEvent event) {
        mapLabel.repaint();
    }

    private void handleAreaSelection() {
        if (updatingAreaSelection) {
            return;
        }
        RoomMapService.MapArea area = (RoomMapService.MapArea) areaComboBox.getSelectedItem();
        if (area == null) {
            return;
        }
        if (area.mapId() < 0) {
            showMessage("No map selected.");
            return;
        }
        Integer currentMapId = lastMapId;
        if (currentMapId != null && currentMapId == area.mapId()) {
            return;
        }
        executor.submit(() -> {
            try {
                String roomId = mapService.findRepresentativeRoomId(area.mapId());
                if (roomId == null || roomId.isBlank()) {
                    showMessage("Map error: No rooms found for " + area.displayName() + ".");
                    return;
                }
                updateMap(roomId);
            } catch (RoomMapService.MapLookupException e) {
                showMessage("Map error: " + e.getMessage());
            } catch (Exception e) {
                showMessage("Map error: Unable to load selected map.");
            }
        });
    }



    private RoomMapService.MapArea ensureAreaOption(int mapId, String title) {
        RoomMapService.MapArea area = areaOptions.get(mapId);
        if (area != null) {
            return area;
        }
        String displayName = title == null || title.isBlank()
                ? mapService.getMapDisplayName(mapId)
                : title;
        area = new RoomMapService.MapArea(mapId, displayName);
        areaOptions.put(mapId, area);
        areaComboBoxModel.addElement(area);
        return area;
    }

    private static final class AnimatedMapIcon implements javax.swing.Icon {
        private static final int ROTATION_PERIOD_MS = 1000;
        private static final int MARKER_DIAMETER_BASE = 16;
        private static final int PINWHEEL_SEGMENTS = 20;
        private static final float PINWHEEL_ALPHA = 0.9f;
        private static final Color SPEEDWALK_COLOR = new Color(230, 70, 70);
        private final BufferedImage image;
        private final Point focusPoint;
        private final int markerDiameter;
        private final List<Point> speedwalkPath;
        private final boolean invertMap;
        private final long startTimeMs = System.currentTimeMillis();

        private AnimatedMapIcon(BufferedImage image, Point focusPoint, int markerDiameter, List<Point> speedwalkPath, boolean invertMap) {
            this.image = image;
            this.focusPoint = focusPoint;
            this.markerDiameter = markerDiameter;
            this.speedwalkPath = speedwalkPath == null ? List.of() : speedwalkPath;
            this.invertMap = invertMap;
        }

        @Override
        public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
            g.drawImage(image, x, y, null);
            drawSpeedwalkPath(g, x, y);
            if (focusPoint == null) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float phase = (System.currentTimeMillis() - startTimeMs) % ROTATION_PERIOD_MS
                    / (float) ROTATION_PERIOD_MS;
            int centerX = x + focusPoint.x;
            int centerY = y + focusPoint.y;
            int diameter = markerDiameter;
            int radius = diameter / 2;
            int topLeftX = centerX - radius;
            int topLeftY = centerY - radius;
            float segmentSweep = 360f / PINWHEEL_SEGMENTS;
            float rotation = phase * 360f;
            for (int i = 0; i < PINWHEEL_SEGMENTS; i++) {
                float hue = i / (float) PINWHEEL_SEGMENTS;
                Color segment = Color.getHSBColor(hue, 0.85f, invertMap ? 1.0f : 0.7f);
                Color segmentWithAlpha = new Color(segment.getRed(), segment.getGreen(), segment.getBlue(),
                        Math.round(255 * PINWHEEL_ALPHA));
                g2.setColor(segmentWithAlpha);
                int startAngle = Math.round(rotation + i * segmentSweep);
                g2.fillArc(topLeftX, topLeftY, diameter, diameter, startAngle, Math.round(segmentSweep));
            }
            g2.dispose();
        }

        private void drawSpeedwalkPath(java.awt.Graphics g, int x, int y) {
            if (speedwalkPath.size() < 2) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(SPEEDWALK_COLOR);
            float strokeWidth = Math.max(2f, markerDiameter / 6f);
            g2.setStroke(new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Point previous = null;
            for (Point point : speedwalkPath) {
                if (point == null) {
                    previous = null;
                    continue;
                }
                if (previous != null) {
                    g2.drawLine(x + previous.x, y + previous.y, x + point.x, y + point.y);
                }
                previous = point;
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return image.getWidth();
        }

        @Override
        public int getIconHeight() {
            return image.getHeight();
        }
    }
}
