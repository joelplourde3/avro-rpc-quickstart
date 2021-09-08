package example.definition.specificity;

import com.fasterxml.jackson.databind.JsonNode;
import example.definition.BaseDefinition;

import javax.json.Json;
import javax.json.JsonObject;

public class ResourceListDefinition extends BaseDefinition {

    public ResourceListDefinition(String name, String identifier, JsonNode definition) {
        super(name, identifier, definition);
    }

    @Override
    public JsonObject convertToJson(String name, boolean required) {
        return Json.createObjectBuilder()
                .add("name", getIdentifier().toLowerCase())
                .add("type", "string")
                .build();
    }
}
