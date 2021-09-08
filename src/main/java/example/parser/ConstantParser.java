package example.parser;

import example.definition.Property;
import example.utils.JsonObjectUtils;

import javax.json.JsonObject;

public class ConstantParser implements IParser {

    @Override
    public boolean canParse(Property property) {
        return property.getJsonNode().has("const");
    }

    @Override
    public JsonObject parseField(String identifier, Property property) {
        return JsonObjectUtils.createConst(identifier, "string", property.isRequired());
    }
}
