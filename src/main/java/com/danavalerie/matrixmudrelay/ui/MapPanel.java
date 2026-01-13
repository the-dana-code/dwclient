package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.core.RoomMapService;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

public final class MapPanel extends JPanel {
    private static final Color BACKGROUND = new Color(10, 10, 15);
    private static final int ZOOM_MIN = 50;
    private static final int ZOOM_MAX = 200;
    private static final int ZOOM_DEFAULT = 100;
    private final RoomMapService mapService = new RoomMapService("database.db");
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "map-render");
        t.setDaemon(true);
        return t;
    });
    private final JLabel mapTitleLabel = new JLabel("", SwingConstants.CENTER);
    private final JLabel mapLabel = new JLabel("Map will appear here", SwingConstants.CENTER);
    private final JScrollPane scrollPane;
    private final AtomicReference<String> lastRoomId = new AtomicReference<>();
    private final AtomicReference<BufferedImage> baseImageCache = new AtomicReference<>();
    private final JSlider zoomSlider;
    private final JLabel zoomLabel = new JLabel();
    private final IntConsumer zoomChangeListener;
    private Consumer<RoomMapService.RoomLocation> speedwalkHandler;
    private final JButton speedWalkButton = new JButton("Speed Walk");
    private int zoomPercent;
    private BufferedImage lastBaseImage;
    private String lastTitle;
    private Point lastFocusPoint;
    private Dimension lastImageSize;
    private RoomMapService.MapImage lastMapImage;
    private RoomMapService.RoomLocation selectedRoom;
    private String currentRoomId;
    private Timer animationTimer;

    public MapPanel(int initialZoomPercent,
                    IntConsumer zoomChangeListener) {
        this.zoomPercent = sanitizeZoom(initialZoomPercent);
        this.zoomChangeListener = zoomChangeListener;
        setLayout(new BorderLayout());
        setBackground(BACKGROUND);
        mapTitleLabel.setForeground(new Color(220, 220, 220));
        mapTitleLabel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        mapLabel.setOpaque(true);
        mapLabel.setBackground(BACKGROUND);
        mapLabel.setForeground(new Color(220, 220, 220));
        mapLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        speedWalkButton.setEnabled(false);
        speedWalkButton.addActionListener(event -> handleSpeedWalk());
        JPanel titlePanel = new JPanel(new BorderLayout());
        titlePanel.setBackground(BACKGROUND);
        JPanel titleActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        titleActions.setOpaque(false);
        titleActions.add(speedWalkButton);
        titlePanel.add(mapTitleLabel, BorderLayout.CENTER);
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
        zoomPanel.add(new JLabel("Zoom:"));
        zoomPanel.add(zoomSlider);
        zoomPanel.add(zoomLabel);
        add(zoomPanel, BorderLayout.SOUTH);
        mapLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                handleMapClick(event.getPoint());
            }
        });
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
                RoomMapService.MapImage mapImage = mapService.renderMapImage(roomId);
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

    public void shutdown() {
        executor.shutdownNow();
    }


    private void showImage(RoomMapService.MapImage mapImage,
                           BufferedImage image,
                           RoomMapService.RoomLocation currentRoom) {
        lastMapImage = mapImage;
        lastBaseImage = image;
        lastTitle = mapImage.mapName();
        lastFocusPoint = new Point(mapImage.currentX(), mapImage.currentY());
        lastImageSize = new Dimension(mapImage.width(), mapImage.height());
        selectedRoom = currentRoom;
        updateDisplayedImage();
        updateSpeedWalkState();
    }

    private void updateDisplayedImage() {
        BufferedImage image = lastBaseImage;
        String title = lastTitle;
        Point focusPoint = lastFocusPoint;
        Dimension imageSize = lastImageSize;
        SwingUtilities.invokeLater(() -> {
            mapTitleLabel.setText(title == null ? "" : title);
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
            int markerDiameter = scaledMarkerDiameter(zoomPercent);
            AnimatedMapIcon icon = new AnimatedMapIcon(scaled, scaledFocus, markerDiameter);
            mapLabel.setIcon(icon);
            mapLabel.setText("");
            mapLabel.setPreferredSize(scaledSize);
            mapLabel.revalidate();
            configureAnimation(icon);
            if (scaledFocus != null && scaledSize != null) {
                SwingUtilities.invokeLater(() -> centerViewOnPoint(scaledFocus, scaledSize));
            }
        });
    }

    private void showMessage(String message) {
        lastBaseImage = null;
        lastTitle = null;
        lastFocusPoint = null;
        lastImageSize = null;
        lastMapImage = null;
        selectedRoom = null;
        SwingUtilities.invokeLater(() -> {
            mapTitleLabel.setText("");
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
                return cached;
            }
        }
        if (mapImage.data() == null) {
            return null;
        }
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(mapImage.data()));
        baseImageCache.set(image);
        return image;
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
        double scale = zoomPercent / 100.0;
        if (scale <= 0) {
            return;
        }
        int baseX = (int) Math.round(clickPoint.x / scale);
        int baseY = (int) Math.round(clickPoint.y / scale);
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
        Dimension effectiveSize = imageSize;
        Dimension labelSize = mapLabel.getSize();
        if (labelSize.width > 0 && labelSize.height > 0) {
            effectiveSize = labelSize;
        }
        int viewX = point.x - extent.width / 2;
        int viewY = point.y - extent.height / 2;
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

    private static final class AnimatedMapIcon implements javax.swing.Icon {
        private static final int ROTATION_PERIOD_MS = 1000;
        private static final int MARKER_DIAMETER_BASE = 16;
        private static final int PINWHEEL_SEGMENTS = 20;
        private static final float PINWHEEL_ALPHA = 0.9f;
        private final BufferedImage image;
        private final Point focusPoint;
        private final int markerDiameter;
        private final long startTimeMs = System.currentTimeMillis();

        private AnimatedMapIcon(BufferedImage image, Point focusPoint, int markerDiameter) {
            this.image = image;
            this.focusPoint = focusPoint;
            this.markerDiameter = markerDiameter;
        }

        @Override
        public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
            g.drawImage(image, x, y, null);
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
                Color segment = Color.getHSBColor(hue, 0.85f, 1.0f);
                Color segmentWithAlpha = new Color(segment.getRed(), segment.getGreen(), segment.getBlue(),
                        Math.round(255 * PINWHEEL_ALPHA));
                g2.setColor(segmentWithAlpha);
                int startAngle = Math.round(rotation + i * segmentSweep);
                g2.fillArc(topLeftX, topLeftY, diameter, diameter, startAngle, Math.round(segmentSweep));
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
