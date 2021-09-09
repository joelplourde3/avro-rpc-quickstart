package example.utils;

import com.fasterxml.jackson.databind.JsonNode;

public class ConverterUtils {

    private ConverterUtils() {}

    public static String parseReference(JsonNode jsonNode) {
        return jsonNode.get(Constant.REF).toString()
                .replace("\"", "")
                .replace("#/definitions/", "");
    }

    public static String parsePrimitiveType(String type) {
        type = type.replace("\"", "");
        if ("number".equals(type)) {
            return "integer";
        }
        return type;
    }

    public static String capitalizeWord(String word) {
        return word.substring(0, 1).toUpperCase() + word.substring(1);
    }
}
