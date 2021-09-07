package example.utils;

import com.fasterxml.jackson.databind.JsonNode;

public class ConverterUtils {

    public static String parseReference(JsonNode jsonNode) {
        return jsonNode.get("$ref").toString().replace("\"", "").replace("#/definitions/", "");
    }
}
