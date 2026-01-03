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
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
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
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(mapImage.data()));
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
            mapLabel.setIcon(image == null ? null : new ImageIcon(image));
            mapLabel.setText(title == null ? "" : title);
            mapLabel.setPreferredSize(image == null ? null : imageSize);
            mapLabel.revalidate();
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
        });
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
}
