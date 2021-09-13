package example.utils;

import com.fasterxml.jackson.databind.JsonNode;

import javax.json.*;

import static javax.json.JsonValue.NULL;

public class JsonObjectUtils {

    private JsonObjectUtils() {
    }

    public static JsonObject createRecord(String name, String description, JsonArray fields, boolean required) {
        return Json.createObjectBuilder()
                .add(Constant.TYPE, Constant.RECORD)
                .add(Constant.NAME, name)
                .add(Constant.DOC, description)
                .add(Constant.NAMESPACE, Constant.NAMESPACE_VALUE)
                .add(Constant.FIELDS, fields)
                .add(Constant.DEFAULT, (required) ? Json.createObjectBuilder().build() : NULL)
                .build();
    }

    public static JsonObject createInnerRecord(String name, String description, JsonArray fields, boolean required) {
        JsonObject innerRecord = Json.createObjectBuilder()
                .add(Constant.TYPE, Constant.RECORD)
                .add(Constant.NAME, name)
                .add(Constant.DOC, description)
                .add(Constant.NAMESPACE, Constant.NAMESPACE_VALUE)
                .add(Constant.FIELDS, fields)
                .add(Constant.DEFAULT, Json.createObjectBuilder().build())
                .build();
        return Json.createObjectBuilder()
                .add(Constant.NAME, name)
                .add(Constant.TYPE, innerRecord)
                .add(Constant.DEFAULT, Json.createObjectBuilder().build())
                .build();
    }

    public static JsonObject createEnum(String parentIdentifier, JsonNode root) {
        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder()
                .add(Constant.TYPE, Constant.ENUM)
                .add(Constant.NAME, ConverterUtils.capitalizeWord(parentIdentifier));
        if (root.has(Constant.DESCRIPTION)) {
            jsonObjectBuilder.add(Constant.DOC, root.get(Constant.DESCRIPTION).asText());
        }
        return jsonObjectBuilder
                .add(Constant.SYMBOLS, formatSymbols(root))
                .build();
    }

    // TODO clean that up.
    public static JsonObject createArray(String name, JsonObject jsonObject) {
        if (jsonObject.containsKey("type")) {
            try {
                JsonObject innerRecord = jsonObject.getJsonObject("type");
                jsonObject = innerRecord;
            } catch (ClassCastException ignored) {
            }
        }

        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder()
                .add(Constant.TYPE, Constant.ARRAY);

        if (jsonObject.containsKey("type")) {
            String type = jsonObject.getJsonString("type").getString();
            if (type.contains(Constant.NAMESPACE_VALUE)) {
                jsonObjectBuilder.add(Constant.ITEMS, type);
            } else {
                jsonObjectBuilder.add(Constant.ITEMS, jsonObject);
            }
        } else {
            jsonObjectBuilder.add(Constant.ITEMS, jsonObject);
        }

        return Json.createObjectBuilder()
                .add(Constant.NAME, name)
                .add(Constant.TYPE, jsonObjectBuilder
                        .add(Constant.DEFAULT, Json.createArrayBuilder().build())
                        .build())
                .add(Constant.DEFAULT, Json.createArrayBuilder().build())
                .build();
    }

    public static JsonObject createField(String name, JsonObject jsonObject, boolean required) {
        return Json.createObjectBuilder()
                .add(Constant.NAME, name)
                .add(Constant.TYPE, (required) ? jsonObject : Json.createArrayBuilder()
                        .add(Constant.NULL)
                        .add(jsonObject)
                        .build())
                .add(Constant.DEFAULT, NULL)
                .build();
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
                            .build())
                    .add(Constant.DEFAULT, NULL);
        }
        return jsonObjectBuilder.build();
    }

    public static JsonObject createRedefinedRecord(String name, String type, JsonObject defaultObject) {
        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder()
                .add(Constant.TYPE, Constant.NAMESPACE_VALUE + "." + ConverterUtils.capitalizeWord(type))
                .add(Constant.NAME, name);
        if (defaultObject != null) {
            jsonObjectBuilder.add(Constant.DEFAULT, defaultObject);
        } else {
            jsonObjectBuilder.add(Constant.DEFAULT, NULL);
        }
        return jsonObjectBuilder.build();
    }

    // symbols: a JSON array, listing symbols, as JSON strings (required).
    // All symbols in an enum must be unique; duplicates are prohibited.
    // Every symbol must match the regular expression [A-Za-z_][A-Za-z0-9_]* (the same requirement as for names).
    private static JsonArray formatSymbols(JsonNode root) {
        JsonArrayBuilder formattedSymbols = Json.createArrayBuilder();
        root.get(Constant.ENUM).forEach(symbol -> {
            String txt = symbol.asText();
            txt = ConverterUtils.capitalizeWord(txt.replace("-", ""));
            txt = txt.substring(0, 1).toLowerCase() + txt.substring(1);
            formattedSymbols.add(txt);
        });
        return formattedSymbols.build();
    }
}
