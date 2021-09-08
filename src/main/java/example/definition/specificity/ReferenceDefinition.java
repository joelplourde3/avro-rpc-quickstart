package example.definition.specificity;

import com.fasterxml.jackson.databind.JsonNode;
import example.definition.BaseDefinition;
import example.utils.JsonObjectUtils;

import javax.json.*;

public class ReferenceDefinition extends BaseDefinition {

    public ReferenceDefinition(String name, String identifier, JsonNode definition) {
        super(name, identifier, definition);
    }

    @Override
    public JsonObject convertToJson(String name, boolean required) {
        JsonArray fields = Json.createArrayBuilder()
                .add(JsonObjectUtils.createConst("reference", "string", false))
                .add(JsonObjectUtils.createConst("type", "string", false))
                .add(JsonObjectUtils.createConst("identifier", "string", false))
                .add(JsonObjectUtils.createConst("display", "string", false))
                .build();

        return Json.createObjectBuilder()
                .add("type", "record")
                .add("name", name)
                .add("namespace", "namespace")
                .add("fields", fields)
                .add("default", JsonObject.NULL) // TODO add the required if any.
                .build();
    }
}
