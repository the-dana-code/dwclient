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

package com.danavalerie.matrixmudrelay.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.SwingUtilities;

public final class ThreadUtils {
    private static final Logger log = LoggerFactory.getLogger(ThreadUtils.class);

    private ThreadUtils() {}

    public static void checkEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            log.warn("UI access from non-EDT thread: {}", Thread.currentThread().getName(), new Throwable());
        }
    }

    public static void checkNotEdt() {
        if (SwingUtilities.isEventDispatchThread()) {
            log.warn("Blocking operation from EDT thread: {}", Thread.currentThread().getName(), new Throwable());
        }
    }
}

