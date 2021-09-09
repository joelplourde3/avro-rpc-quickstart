package example.utils;

import com.fasterxml.jackson.databind.JsonNode;

import javax.json.*;

import static javax.json.JsonValue.NULL;

public class JsonObjectUtils {

    private JsonObjectUtils() {
    }

    public static JsonObject createRecord(String name, String description, JsonArray fields, boolean required) {
        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder()
                .add(Constant.TYPE, Constant.RECORD)
                .add(Constant.NAME, name)
                .add(Constant.DOC, description)
                .add(Constant.NAMESPACE, Constant.NAMESPACE_VALUE)
                .add(Constant.FIELDS, fields);

        if (required) {
            jsonObjectBuilder.add(Constant.DEFAULT, Constant.DEFAULT_RECORD);
        } else {
            jsonObjectBuilder.add(Constant.DEFAULT, NULL);
        }
        return jsonObjectBuilder.build();
    }

    public static JsonObject createEnum(String parentIdentifier, JsonNode root) {
        JsonArrayBuilder symbols = Json.createArrayBuilder();
        root.get(Constant.ENUM).forEach(symbol -> symbols.add(symbol.asText()));
        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder()
                .add(Constant.TYPE, Constant.ENUM)
                .add(Constant.NAME, ConverterUtils.capitalizeWord(parentIdentifier));
        if (root.has(Constant.DESCRIPTION)) {
            jsonObjectBuilder.add(Constant.DOC, root.get(Constant.DESCRIPTION).asText());
        }
        return jsonObjectBuilder.add(Constant.SYMBOLS, symbols.build()).build();
    }

    public static JsonObject createArray(JsonObject jsonObject) {
        return Json.createObjectBuilder()
                .add(Constant.TYPE, Constant.ARRAY)
                .add(Constant.ITEMS, jsonObject)
                .add(Constant.DEFAULT, Constant.DEFAULT_ARRAY)
                .build();
    }

    public static JsonObject createField(String name, JsonObject jsonObject, boolean required) {
        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder()
                .add(Constant.NAME, name);

        if (required) {
            jsonObjectBuilder.add(Constant.TYPE, jsonObject);
        } else {
            jsonObjectBuilder.add(Constant.TYPE, Json.createArrayBuilder()
                    .add(Constant.NULL)
                    .add(jsonObject)
                    .build());
        }
        return jsonObjectBuilder.build();
    }

    public static JsonObject createConst(String name, String type, boolean required) {
        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder()
                .add(Constant.NAME, name);

        if (required) {
            jsonObjectBuilder.add(Constant.TYPE, type);
        } else {
            jsonObjectBuilder.add(Constant.TYPE, Json.createArrayBuilder()
                    .add(Constant.NULL)
                    .add(type)
                    .build());
        }
        return jsonObjectBuilder.build();
    }
}
