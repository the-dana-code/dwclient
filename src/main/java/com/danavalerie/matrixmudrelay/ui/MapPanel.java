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
    private final AtomicReference<String> lastRoomId = new AtomicReference<>();

    public MapPanel() {
        setLayout(new BorderLayout());
        setBackground(BACKGROUND);
        mapLabel.setOpaque(true);
        mapLabel.setBackground(BACKGROUND);
        mapLabel.setForeground(new Color(220, 220, 220));
        mapLabel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane scrollPane = new JScrollPane(mapLabel);
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
                showImage(image, mapImage.body());
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

    private void showImage(BufferedImage image, String title) {
        SwingUtilities.invokeLater(() -> {
            mapLabel.setIcon(image == null ? null : new ImageIcon(image));
            mapLabel.setText(title == null ? "" : title);
        });
    }

    private void showMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            mapLabel.setIcon(null);
            mapLabel.setText(message);
        });
    }
}
