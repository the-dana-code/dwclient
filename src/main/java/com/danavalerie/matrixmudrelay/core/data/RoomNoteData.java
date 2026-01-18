package com.danavalerie.matrixmudrelay.core.data;

import java.util.ArrayList;
import java.util.List;

public class RoomNoteData {
    private String name;
    private String notes;
    private List<RoomButton> buttons = new ArrayList<>();

    public RoomNoteData() {}

    public RoomNoteData(String name, List<RoomButton> buttons) {
        this.name = name;
        this.buttons = buttons != null ? new ArrayList<>(buttons) : new ArrayList<>();
    }

    public RoomNoteData(String name, String notes, List<RoomButton> buttons) {
        this.name = name;
        this.notes = notes;
        this.buttons = buttons != null ? new ArrayList<>(buttons) : new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public List<RoomButton> getButtons() {
        return buttons;
    }

    public void setButtons(List<RoomButton> buttons) {
        this.buttons = buttons != null ? new ArrayList<>(buttons) : new ArrayList<>();
    }
}
