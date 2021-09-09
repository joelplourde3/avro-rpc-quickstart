package example.definition.specificity;

import example.utils.Constant;

import javax.json.Json;
import javax.json.JsonObject;

public class ResourceListDefinition extends SpecificDefinition {

    @Override
    public JsonObject convertToJson(String name, boolean required) {
        return Json.createObjectBuilder()
                .add(Constant.NAME, getIdentifier().toLowerCase())
                .add(Constant.TYPE, Constant.STRING)
                .build();
    }
}
