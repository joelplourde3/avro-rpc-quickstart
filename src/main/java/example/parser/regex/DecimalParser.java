package example.parser.regex;

import example.definition.Property;
import example.utils.Constant;

import javax.json.Json;
import javax.json.JsonObject;

public class DecimalParser extends RegexParser {

    @Override
    public JsonObject parseField(String identifier, Property property) {
        return Json.createObjectBuilder()
                .add(Constant.TYPE, Constant.BYTES)
                .add(Constant.LOGICAL_TYPE, Constant.DECIMAL)
                .add("precision", 18)
                .add("scale", 0)
                .build();
    }

    // Must supported minimally 18 decimal digits; https://www.w3.org/TR/xmlschema-2/#decimal
    @Override
    protected String getReferencePattern() {
        return "0.000000000000000000";
    }
}
