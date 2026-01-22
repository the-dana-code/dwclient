package com.danavalerie.matrixmudrelay.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

public class GsonUtils {

    private static final TypeAdapterFactory EMPTY_TO_NULL_FACTORY = new TypeAdapterFactory() {
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            Class<? super T> rawType = type.getRawType();
            if (Collection.class.isAssignableFrom(rawType)) {
                final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
                return new TypeAdapter<T>() {
                    @Override
                    public void write(JsonWriter out, T value) throws IOException {
                        if (value != null && ((Collection<?>) value).isEmpty()) {
                            out.nullValue();
                        } else {
                            delegate.write(out, value);
                        }
                    }

                    @Override
                    public T read(JsonReader in) throws IOException {
                        return delegate.read(in);
                    }
                };
            }
            if (Map.class.isAssignableFrom(rawType)) {
                final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
                return new TypeAdapter<T>() {
                    @Override
                    public void write(JsonWriter out, T value) throws IOException {
                        if (value != null && ((Map<?, ?>) value).isEmpty()) {
                            out.nullValue();
                        } else {
                            delegate.write(out, value);
                        }
                    }

                    @Override
                    public T read(JsonReader in) throws IOException {
                        return delegate.read(in);
                    }
                };
            }
            if (String.class.isAssignableFrom(rawType)) {
                final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
                return new TypeAdapter<T>() {
                    @Override
                    public void write(JsonWriter out, T value) throws IOException {
                        if (value != null && ((String) value).isEmpty()) {
                            out.nullValue();
                        } else {
                            delegate.write(out, value);
                        }
                    }

                    @Override
                    public T read(JsonReader in) throws IOException {
                        return delegate.read(in);
                    }
                };
            }
            return null;
        }
    };

    public static GsonBuilder getDefaultBuilder() {
        return new GsonBuilder()
                .registerTypeAdapterFactory(EMPTY_TO_NULL_FACTORY)
                .setPrettyPrinting();
    }

    public static Gson getGson() {
        return getDefaultBuilder().create();
    }
}
