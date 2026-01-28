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

package com.danavalerie.matrixmudrelay.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class UiConfig {
    public Integer windowWidth;
    public Integer windowHeight;
    public Integer windowX;
    public Integer windowY;
    public Boolean windowMaximized;
    public Double mudMapSplitRatio;
    public Double mapNotesSplitRatio;
    public Double chitchatTimerSplitRatio;
    public Double outputSplitRatio;
    public List<Integer> timerColumnWidths;

    public Map<String, CharacterUiData> characters = new LinkedHashMap<>();

    public static final class CharacterUiData {
        public Map<String, ClientConfig.TimerData> timers = new LinkedHashMap<>();
        public List<Integer> gpRateSamples = new ArrayList<>();
        public List<Integer> hpRateSamples = new ArrayList<>();
        public UULibraryState uuLibrary;
    }

    public static final class UULibraryState {
        public int row;
        public int col;
        public String orientation;

        public UULibraryState(int row, int col, String orientation) {
            this.row = row;
            this.col = col;
            this.orientation = orientation;
        }
    }
}
