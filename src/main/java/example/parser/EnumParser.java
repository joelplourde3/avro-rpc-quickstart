package example.parser;

import example.definition.Property;
import example.utils.Constant;
import example.utils.JsonObjectUtils;

import javax.json.JsonObject;

public class EnumParser implements IParser {

    @Override
    public boolean canParse(Property property) {
        return property.getJsonNode().has(Constant.ENUM);
    }

    @Override
    public JsonObject parseField(String identifier, Property property) {
        return JsonObjectUtils.createField(identifier, JsonObjectUtils.createEnum(identifier, property.getJsonNode()), property.isRequired());
    }
}
