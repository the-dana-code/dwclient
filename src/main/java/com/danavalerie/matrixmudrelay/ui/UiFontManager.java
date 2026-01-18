/*
 * Lesa's Discworld MUD client.
 * Copyright (C) 2026 Dana Reese
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.danavalerie.matrixmudrelay.ui;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class UiFontManager {
    private final Component root;
    private final List<FontChangeListener> listeners = new ArrayList<>();
    private Font baseFont;

    public UiFontManager(Component root, Font baseFont) {
        this.root = Objects.requireNonNull(root, "root");
        this.baseFont = Objects.requireNonNull(baseFont, "baseFont");
    }

    public Font getBaseFont() {
        return baseFont;
    }

    public void registerListener(FontChangeListener listener) {
        Objects.requireNonNull(listener, "listener");
        listeners.add(listener);
        listener.onFontChange(baseFont);
    }

    public void setBaseFont(Font font) {
        this.baseFont = Objects.requireNonNull(font, "font");
        applyDefaults(font);
        SwingUtilities.updateComponentTreeUI(root);
        applyFontTree(root, font);
        for (FontChangeListener listener : listeners) {
            listener.onFontChange(font);
        }
    }

    private static void applyDefaults(Font font) {
        FontUIResource resource = new FontUIResource(font);
        UIManager.put("defaultFont", resource);
        var defaults = UIManager.getLookAndFeelDefaults();
        var keys = new ArrayList<>(defaults.keySet());
        for (Object key : keys) {
            Object value = defaults.get(key);
            if (value instanceof Font) {
                UIManager.put(key, resource);
            }
        }
    }

    private static void applyFontTree(Component component, Font font) {
        component.setFont(font);
        if (component instanceof JMenu menu) {
            for (Component item : menu.getMenuComponents()) {
                applyFontTree(item, font);
            }
        } else if (component instanceof JMenuItem item) {
            var popup = item.getComponentPopupMenu();
            if (popup != null) {
                applyFontTree(popup, font);
            }
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                applyFontTree(child, font);
            }
        }
    }
}

