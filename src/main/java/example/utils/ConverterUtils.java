package example.utils;

import com.fasterxml.jackson.databind.JsonNode;

public class ConverterUtils {

    private ConverterUtils() {}

    public static String parseReference(JsonNode jsonNode) {
        return jsonNode.get("$ref").toString().replace("\"", "").replace("#/definitions/", "");
    }

    public static String parsePrimitiveType(String type) {
        type = type.replace("\"", "");
        switch (type) {
            case "number":
                return "integer";
            case "string":
                return "string";
            case "boolean":
                return "boolean";
            default:
                throw new RuntimeException("Please verify this type: " + type);
        }
    }

    public static String capitalizeWord(String word) {
        return word.substring(0, 1).toUpperCase() + word.substring(1);
    }
}
