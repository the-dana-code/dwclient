package com.danavalerie.matrixmudrelay.core.data;

public class RoomButton {
    private String name;
    private String command;

    public RoomButton() {}

    public RoomButton(String name, String command) {
        this.name = name;
        this.command = command;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }
}
