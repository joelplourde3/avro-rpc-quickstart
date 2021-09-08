package example.parser;

import example.definition.Property;
import example.utils.JsonObjectUtils;

import javax.json.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArrayParser implements IParser {

    private static final List<IParser> innerParser = Collections.unmodifiableList(new ArrayList<IParser>() {{
        add(new ReferenceParser());
        add(new EnumParser());
    }});

    @Override
    public boolean canParse(Property property) {
        return property.getJsonNode().has("items");
    }

    @Override
    public JsonObject parseField(String identifier, Property property) {
        // TODO check if this property is required or not somehow
        Property rootProperty = new Property(property.getJsonNode().get("items"), false);
        JsonObject jsonObject = null;
        for (IParser fieldParser : innerParser) {
            if (fieldParser.canParse(rootProperty)) {
                jsonObject = fieldParser.parseField(identifier, rootProperty);
                break;
            }
        }

        if (jsonObject == null) {
            throw new RuntimeException("We do not know how to parse this node yet.");
        }

        return JsonObjectUtils.createArray(jsonObject);
    }
}
