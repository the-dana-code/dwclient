package com.danavalerie.matrixmudrelay.core.data;

import java.util.ArrayList;
import java.util.List;

public class RoomButtons {
    private String name;
    private List<RoomButton> buttons = new ArrayList<>();

    public RoomButtons() {}

    public RoomButtons(String name, List<RoomButton> buttons) {
        this.name = name;
        this.buttons = buttons != null ? new ArrayList<>(buttons) : new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<RoomButton> getButtons() {
        return buttons;
    }

    public void setButtons(List<RoomButton> buttons) {
        this.buttons = buttons != null ? new ArrayList<>(buttons) : new ArrayList<>();
    }
}
