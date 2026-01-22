package com.danavalerie.matrixmudrelay.core.data;

import com.google.gson.*;
import java.lang.reflect.Type;

public class ShopItem {
    private String name;
    private String shopName;

    public ShopItem() {}

    public ShopItem(String name) {
        this.name = name;
    }

    public ShopItem(String name, String shopName) {
        this.name = name;
        this.shopName = shopName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShopName() {
        return (shopName == null || shopName.isEmpty()) ? name : shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public static class Adapter implements JsonSerializer<ShopItem>, JsonDeserializer<ShopItem> {
        @Override
        public ShopItem deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json.isJsonPrimitive()) {
                return new ShopItem(json.getAsString());
            } else if (json.isJsonObject()) {
                JsonObject obj = json.getAsJsonObject();
                String name = obj.has("name") ? obj.get("name").getAsString() : null;
                String shopName = obj.has("shopName") ? obj.get("shopName").getAsString() : null;
                return new ShopItem(name, shopName);
            }
            throw new JsonParseException("Unexpected JSON type for ShopItem");
        }

        @Override
        public JsonElement serialize(ShopItem src, Type typeOfSrc, JsonSerializationContext context) {
            if (src.shopName == null || src.shopName.isEmpty() || src.shopName.equals(src.name)) {
                return new JsonPrimitive(src.name);
            } else {
                JsonObject obj = new JsonObject();
                obj.addProperty("name", src.name);
                obj.addProperty("shopName", src.shopName);
                return obj;
            }
        }
    }
}
