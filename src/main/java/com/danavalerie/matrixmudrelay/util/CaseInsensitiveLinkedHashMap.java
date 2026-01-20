package com.danavalerie.matrixmudrelay.util;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * A LinkedHashMap that is case-insensitive for String keys, but preserves the original casing
 * of the key as it was when first inserted.
 */
public class CaseInsensitiveLinkedHashMap<V> extends LinkedHashMap<String, V> {
    private final Map<String, String> lowerToActual = new HashMap<>();

    public CaseInsensitiveLinkedHashMap() {
        super();
    }

    public CaseInsensitiveLinkedHashMap(Map<? extends String, ? extends V> m) {
        this();
        if (m != null) {
            putAll(m);
        }
    }

    private String getActualKey(String key) {
        if (key == null) return null;
        return lowerToActual.get(key.toLowerCase());
    }

    @Override
    public V get(Object key) {
        if (key instanceof String) {
            String actualKey = getActualKey((String) key);
            if (actualKey != null) {
                return super.get(actualKey);
            }
        }
        return super.get(key);
    }

    @Override
    public boolean containsKey(Object key) {
        if (key instanceof String) {
            return lowerToActual.containsKey(((String) key).toLowerCase());
        }
        return super.containsKey(key);
    }

    @Override
    public V put(String key, V value) {
        String actualKey = getActualKey(key);
        if (actualKey != null) {
            return super.put(actualKey, value);
        } else {
            lowerToActual.put(key.toLowerCase(), key);
            return super.put(key, value);
        }
    }

    @Override
    public void putAll(Map<? extends String, ? extends V> m) {
        if (m == null) return;
        for (Map.Entry<? extends String, ? extends V> entry : m.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public V remove(Object key) {
        if (key instanceof String) {
            String actualKey = lowerToActual.remove(((String) key).toLowerCase());
            if (actualKey != null) {
                return super.remove(actualKey);
            }
        }
        return super.remove(key);
    }

    @Override
    public void clear() {
        lowerToActual.clear();
        super.clear();
    }

    @Override
    public V getOrDefault(Object key, V defaultValue) {
        if (key instanceof String) {
            String actualKey = getActualKey((String) key);
            if (actualKey != null) {
                return super.getOrDefault(actualKey, defaultValue);
            }
        }
        return super.getOrDefault(key, defaultValue);
    }

    @Override
    public V computeIfAbsent(String key, Function<? super String, ? extends V> mappingFunction) {
        Objects.requireNonNull(mappingFunction);
        String actualKey = getActualKey(key);
        if (actualKey != null) {
            return super.computeIfAbsent(actualKey, mappingFunction);
        } else {
            // It's a new key.
            V value = super.get(key); // Should be null since actualKey was null
            if (value != null) return value; // Defensive

            V newValue = mappingFunction.apply(key);
            if (newValue != null) {
                put(key, newValue);
            }
            return newValue;
        }
    }
}
