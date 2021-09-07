package example.utils;

import com.fasterxml.jackson.databind.JsonNode;
import example.repository.DefinitionRepository;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;

public class JsonObjectUtils {

    private JsonObjectUtils() {}

    public static JsonObject createRecord(String name, JsonArray fields) {
        return Json.createObjectBuilder()
                .add("type", "record")
                .add("name", name)
                .add("namespace", "namespace")
                .add("fields", fields)
                .build();
    }

    public static JsonObject createEnum(JsonNode root) {
        return Json.createObjectBuilder()
                .add("type", "enum")
                .add("name", "???")
                .add("symbols", root.get("enum").toString())
                .build();
    }

    public static JsonObject createArray(JsonNode root) {
        return Json.createObjectBuilder()
                .add("type", "array")
                .add("items", DefinitionRepository.getReferenceObject(root.get("items"), "_"))
                .add("default", "[]") // TODO fill-in the defaault value if any.
                .build();
    }

    public static JsonObject createField(String name, JsonObject jsonObject) {
        return Json.createObjectBuilder()
                .add("name", name)
                .add("type", jsonObject)
                .build();
    }
}
