package example.definition;

import com.fasterxml.jackson.databind.JsonNode;
import example.parser.ParserServant;
import example.utils.JsonObjectUtils;

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
            JsonArrayBuilder fields = Json.createArrayBuilder();
            for (Map.Entry<String, JsonNode> node : properties.entrySet()) {
                JsonObject field = ParserServant.parseField(node.getKey(), node.getValue());
                fields.add(field);
                setJsonObject(JsonObjectUtils.createRecord(getName(), fields.build()));
            }
        }
        return getJsonObject();
    }

    public Map<String, JsonNode> getProperties() {
        return properties;
    }
}
