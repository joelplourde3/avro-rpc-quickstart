package example.utils;

import com.fasterxml.jackson.databind.JsonNode;

import javax.json.*;

public class JsonObjectUtils {

    private JsonObjectUtils() {
    }

    public static JsonObject createRecord(String name, String description, JsonArray fields, boolean required) {
        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder()
                .add("type", "record")
                .add("name", name)
                .add("doc", description)
                .add("namespace", "namespace")
                .add("fields", fields);

        if (required) {
            jsonObjectBuilder.add("default", "{}");
        } else {
            jsonObjectBuilder.add("default", JsonObject.NULL);
        }
        return jsonObjectBuilder.build();
    }

    public static JsonObject createEnum(String parentIdentifier, JsonNode root) {
        JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();
        root.get("enum").forEach(symbol -> jsonArrayBuilder.add(symbol.asText()));
        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder()
                .add("type", "enum")
                .add("name", ConverterUtils.capitalizeWord(parentIdentifier));

        if (root.has("description")) {
            jsonObjectBuilder.add("doc", root.get("description").asText());
        }
        return jsonObjectBuilder.add("symbols", jsonArrayBuilder.build()).build();
    }

    public static JsonObject createArray(JsonObject jsonObject) {
        return Json.createObjectBuilder()
                .add("type", "array")
                .add("items", jsonObject)
                .add("default", "[]")
                .build();
    }

    public static JsonObject createField(String name, JsonObject jsonObject, boolean required) {
        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder()
                .add("name", name);

        if (required) {
            jsonObjectBuilder.add("type", jsonObject);
        } else {
            jsonObjectBuilder.add("type", Json.createArrayBuilder()
                    .add("null")
                    .add(jsonObject)
                    .build());
        }
        return jsonObjectBuilder.build();
    }

    public static JsonObject createConst(String name, String type, boolean required) {
        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder()
                .add("name", name);
        if (required) {
            jsonObjectBuilder.add("type", type);
        } else {
            jsonObjectBuilder.add("type", Json.createArrayBuilder()
                    .add("null")
                    .add(type)
                    .build());
        }
        return jsonObjectBuilder.build();
    }
}
