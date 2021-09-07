package example.definition;

import com.fasterxml.jackson.databind.JsonNode;

import javax.json.Json;
import javax.json.JsonObject;
import java.util.Map;

public class PrimitiveDefinition extends BaseDefinition {

    private String type;
    private String pattern;

    public PrimitiveDefinition(String name, String identifier, JsonNode definition) {
        super(name, identifier, definition);
        setType("string");
    }

    public PrimitiveDefinition(Map.Entry<String, JsonNode> entry) {
        super(entry.getKey(), entry.getKey(), entry.getValue());

        setType(getDefinition().get("type").asText());  // TODO convert the format to an Avro primitive types.
        if (getDefinition().has("pattern")) {
            setPattern(getDefinition().get("pattern").asText());
        }
    }

    public JsonObject toJson(String name) {
        return Json.createObjectBuilder()
                .add("name", name)
                .add("type", getType())
                .build();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }
}
