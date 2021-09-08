package example.definition;

import javax.json.JsonObject;

public interface IDefinition {

    JsonObject convertToJson(String name, boolean required);
}
