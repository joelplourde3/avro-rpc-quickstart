package example.definition;

import com.fasterxml.jackson.databind.JsonNode;

import javax.json.JsonObject;

public abstract class BaseDefinition {

    private String name;
    private String identifier;
    private JsonNode definition;
    private JsonObject jsonObject;

    public BaseDefinition(String name, String identifier, JsonNode definition) {
        this.name = name;
        this.identifier = identifier;
        this.definition = definition;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public JsonNode getDefinition() {
        return definition;
    }

    public void setDefinition(JsonNode definition) {
        this.definition = definition;
    }

    public JsonObject getJsonObject() {
        return jsonObject;
    }

    public void setJsonObject(JsonObject jsonObject) {
        this.jsonObject = jsonObject;
    }
}
