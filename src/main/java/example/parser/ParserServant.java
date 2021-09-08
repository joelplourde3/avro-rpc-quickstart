package example.parser;

import example.definition.Property;

import javax.json.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ParserServant {

    public static final List<IParser> parsers = Collections.unmodifiableList(new ArrayList<IParser>() {{
        add(new ReferenceParser());
        add(new ArrayParser());
        add(new EnumParser());
        add(new TypeParser());
        add(new ConstantParser());
    }});

    private ParserServant() {}

    public static JsonObject parseField(String identifier, Property property) {
        JsonObject jsonObject = null;
        for (IParser fieldParser : parsers) {
            if (fieldParser.canParse(property)) {
                jsonObject = fieldParser.parseField(identifier, property);
                break;
            }
        }

        if (jsonObject == null) {
            throw new RuntimeException("We do not know how to parse this node yet.");
        }

        return jsonObject;
    }
}
