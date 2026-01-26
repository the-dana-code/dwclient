package com.danavalerie.matrixmudrelay.config;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public class TimerDataAdapter extends TypeAdapter<ClientConfig.TimerData> {
    @Override
    public void write(JsonWriter out, ClientConfig.TimerData value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        out.beginObject();
        out.name("expirationTime").value(value.expirationTime);
        out.name("durationMs").value(value.durationMs);
        out.endObject();
    }

    @Override
    public ClientConfig.TimerData read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        if (in.peek() == JsonToken.NUMBER) {
            long expirationTime = in.nextLong();
            return new ClientConfig.TimerData(expirationTime, 0);
        }
        in.beginObject();
        long expirationTime = 0;
        long durationMs = 0;
        while (in.hasNext()) {
            String name = in.nextName();
            switch (name) {
                case "expirationTime":
                    expirationTime = in.nextLong();
                    break;
                case "durationMs":
                    durationMs = in.nextLong();
                    break;
                default:
                    in.skipValue();
                    break;
            }
        }
        in.endObject();
        return new ClientConfig.TimerData(expirationTime, durationMs);
    }
}
