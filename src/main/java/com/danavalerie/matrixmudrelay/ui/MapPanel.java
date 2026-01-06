package com.danavalerie.matrixmudrelay.ui;

import com.danavalerie.matrixmudrelay.core.RoomMapService;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public final class MapPanel extends JPanel {
    private static final Color BACKGROUND = new Color(10, 10, 15);
    private final RoomMapService mapService = new RoomMapService("database.db");
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "map-render");
        t.setDaemon(true);
        return t;
    });
    private final JLabel mapLabel = new JLabel("Map will appear here", SwingConstants.CENTER);
    private final JScrollPane scrollPane;
    private final AtomicReference<String> lastRoomId = new AtomicReference<>();
    private final AtomicReference<BufferedImage> baseImageCache = new AtomicReference<>();
    private Timer animationTimer;

    public MapPanel() {
        setLayout(new BorderLayout());
        setBackground(BACKGROUND);
        mapLabel.setOpaque(true);
        mapLabel.setBackground(BACKGROUND);
        mapLabel.setForeground(new Color(220, 220, 220));
        mapLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        scrollPane = new JScrollPane(mapLabel);
        add(scrollPane, BorderLayout.CENTER);
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
                showImage(image, mapImage.body(), new Point(mapImage.currentX(), mapImage.currentY()),
                        new Dimension(mapImage.width(), mapImage.height()));
            } catch (RoomMapService.MapLookupException e) {
                showMessage("Map error: " + e.getMessage());
            } catch (Exception e) {
                showMessage("Map error: Unable to render map.");
            }
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }


    private void showImage(BufferedImage image, String title, Point focusPoint, Dimension imageSize) {
        SwingUtilities.invokeLater(() -> {
            AnimatedMapIcon icon = image == null ? null : new AnimatedMapIcon(image, focusPoint);
            mapLabel.setIcon(icon);
            mapLabel.setText(title == null ? "" : title);
            mapLabel.setPreferredSize(image == null ? null : imageSize);
            mapLabel.revalidate();
            configureAnimation(icon);
            if (image != null && focusPoint != null && imageSize != null) {
                SwingUtilities.invokeLater(() -> centerViewOnPoint(focusPoint, imageSize));
            }
        });
    }

    private void showMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            mapLabel.setIcon(null);
            mapLabel.setText(message);
            mapLabel.setPreferredSize(null);
            configureAnimation(null);
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
        private static final int MARKER_DIAMETER = 16;
        private static final int PINWHEEL_SEGMENTS = 20;
        private static final float PINWHEEL_ALPHA = 0.9f;
        private final BufferedImage image;
        private final Point focusPoint;
        private final long startTimeMs = System.currentTimeMillis();

        private AnimatedMapIcon(BufferedImage image, Point focusPoint) {
            this.image = image;
            this.focusPoint = focusPoint;
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
            int radius = MARKER_DIAMETER / 2;
            int diameter = MARKER_DIAMETER;
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
