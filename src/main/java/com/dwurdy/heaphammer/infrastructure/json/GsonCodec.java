package com.dwurdy.heaphammer.infrastructure.json;

import com.dwurdy.heaphammer.domain.ExperimentId;
import com.dwurdy.heaphammer.domain.ScenarioId;
import com.google.gson.*;

import java.lang.reflect.Type;

/**
 * Thread-safe JSON serialization provider for HeapHammer domain models.
 */
public final class GsonCodec {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeAdapter(ExperimentId.class, new ExperimentIdAdapter())
            .registerTypeAdapter(ScenarioId.class, new ScenarioIdAdapter())
            .create();

    private GsonCodec() {}

    public static Gson gson() {
        return GSON;
    }

    public static String toJson(Object object) {
        return GSON.toJson(object);
    }

    public static <T> T fromJson(String json, Class<T> classOfT) {
        return GSON.fromJson(json, classOfT);
    }

    public static <T> T fromJson(String json, Type typeOfT) {
        return GSON.fromJson(json, typeOfT);
    }

    private static class ExperimentIdAdapter implements JsonSerializer<ExperimentId>, JsonDeserializer<ExperimentId> {
        @Override
        public JsonElement serialize(ExperimentId src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.value());
        }

        @Override
        public ExperimentId deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return ExperimentId.of(json.getAsString());
        }
    }

    private static class ScenarioIdAdapter implements JsonSerializer<ScenarioId>, JsonDeserializer<ScenarioId> {
        @Override
        public JsonElement serialize(ScenarioId src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.value());
        }

        @Override
        public ScenarioId deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return new ScenarioId(json.getAsString());
        }
    }
}
