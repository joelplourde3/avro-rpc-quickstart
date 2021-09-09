package example.parser;

import example.definition.Property;
import example.definition.exception.UnknownParserException;
import example.utils.Constant;
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
        return property.getJsonNode().has(Constant.ITEMS);
    }

    @Override
    public JsonObject parseField(String identifier, Property property) {
        // TODO check if this property is required or not somehow
        Property rootProperty = new Property(property.getJsonNode().get(Constant.ITEMS), false);
        return JsonObjectUtils.createArray(innerParser.stream()
                .filter(parser -> parser.canParse(rootProperty))
                .findFirst()
                .orElseThrow(() -> new UnknownParserException(identifier))
                .parseField(identifier, rootProperty));
    }
}
