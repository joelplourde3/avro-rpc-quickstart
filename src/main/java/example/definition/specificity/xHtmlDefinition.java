package example.definition.specificity;

import com.fasterxml.jackson.databind.JsonNode;
import example.definition.BaseDefinition;
import example.utils.JsonObjectUtils;

import javax.json.Json;
import javax.json.JsonObject;

public class xHtmlDefinition extends BaseDefinition {

    public xHtmlDefinition(String name, String identifier, JsonNode definition) {
        super(name, identifier, definition);
    }

    @Override
    public JsonObject convertToJson(String name, boolean required) {
        return Json.createObjectBuilder()
                .add("type", "record")
                .add("name", name)
                .add("namespace", "namespace")
                .add("fields", Json.createArrayBuilder()
                        .add(JsonObjectUtils.createConst("extension", "string", false))
                        .add(JsonObjectUtils.createConst("url", "string", false))
                        .add(JsonObjectUtils.createConst("valueString", "string", false))
                        .build())
                .add("default", JsonObject.NULL) // TODO add the required if any.
                .build();
    }
}
