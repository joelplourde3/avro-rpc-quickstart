package example.parser;

import example.definition.Property;

import javax.json.JsonObject;

public interface IParser {

    boolean canParse(Property property);

    JsonObject parseField(String root, String identifier, Property property);
}
