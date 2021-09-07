package example.definition;

import com.fasterxml.jackson.databind.JsonNode;
import example.Application;
import example.utils.ConverterUtils;

import javax.json.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ComplexDefinition extends BaseDefinition {

    private final Map<String, JsonNode> properties = new HashMap<>();

    public ComplexDefinition(Map.Entry<String, JsonNode> entry) {
        super(entry.getKey(), entry.getKey(), entry.getValue());
        for (Iterator<Map.Entry<String, JsonNode>> it = getDefinition().get("properties").fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> property = it.next();

            if (property.getKey().contains("_") || "id".equals(property.getKey()) || "extension".equals(property.getKey())) {
                continue;
            }

            properties.put(property.getKey(), property.getValue());
        }
    }

    public JsonObject toJson() {
        if (getJsonObject() == null) {
            JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();
            for (Map.Entry<String, JsonNode> node : properties.entrySet()) {
                if (node.getValue().has("$ref")) {
                    jsonArrayBuilder.add(getReferenceObject(node.getValue(), node.getKey()));
                } else if (node.getValue().has("items")) {
                    JsonNode root = node.getValue().get("items");
                    if (root.has("$ref")) {

                        // SKIP Extension has its optional.
                        if ("\"#/definitions/Extension\"".equals(node.getValue().get("items").get("$ref").toString())) {
                            continue;
                        }

                        jsonArrayBuilder.add(getObject(node.getKey(), getArrayObject(node.getValue())));
                    } else if (root.has("enum")) {
                        jsonArrayBuilder.add(getObject(node.getKey(), getEnumObject(node.getValue().get("items"))));
                    } else {
                        throw new RuntimeException("Not yet implemented!");
                    }
                } else if (node.getValue().has("type")) {
                    String reference = node.getValue().get("type").toString();
                    if (Application.primitiveDefinitions.containsKey(reference)) {
                        jsonArrayBuilder.add(getObject(node.getKey(), Application.primitiveDefinitions.get(reference).toJson(node.getKey())));
                    }
                } else if (node.getValue().has("enum")) {
                    jsonArrayBuilder.add(getEnumObject(node.getValue()));
                } else {
                    throw new RuntimeException("Not yet implemented!");
                }
            }

            setJsonObject(createRecord(jsonArrayBuilder.build()));
        }
        return getJsonObject();
    }

    private JsonObject createRecord(JsonArray fields) {
        return Json.createObjectBuilder()
                .add("type", "record")
                .add("name", getName())
                .add("namespace", "namespace")
                .add("fields", fields)
                .build();
    }

    private JsonObject getObject(String name, JsonObject jsonObject) {
        return Json.createObjectBuilder()
                .add("name", name)
                .add("type", jsonObject)
                .build();
    }

    private JsonObject getArrayObject(JsonNode root) {
        return Json.createObjectBuilder()
                .add("type", "array")
                .add("items", getReferenceObject(root.get("items"), "_"))
                .add("default", "[]") // TODO fill-in the defaault value if any.
                .build();
    }

    private JsonObject getEnumObject(JsonNode root) {
        return Json.createObjectBuilder()
                .add("type", "enum")
                .add("name", "???")
                .add("symbols", root.get("enum").toString())
                .build();
    }

    private JsonObject getReferenceObject(JsonNode root, String name) {
        String reference = ConverterUtils.parseReference(root);
        if (Application.primitiveDefinitions.containsKey(reference)) {
            return Application.primitiveDefinitions.get(reference).toJson(name);
        }  else if (Application.complexDefinitions.containsKey(reference)) {
            return Application.complexDefinitions.get(reference).toJson();
        } else {
            throw new RuntimeException("The reference object is not a primitive nor a complex, please implement this.");
        }
    }

    public Map<String, JsonNode> getProperties() {
        return properties;
    }
}
