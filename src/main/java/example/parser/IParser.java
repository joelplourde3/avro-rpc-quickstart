package example.parser;

import com.fasterxml.jackson.databind.JsonNode;
import example.definition.Property;

import javax.json.JsonObject;

public interface IParser {

    boolean canParse(Property property);

    JsonObject parseField(String identifier, Property property);
}
