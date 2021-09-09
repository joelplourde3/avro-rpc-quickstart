package example.parser.regex;

import example.definition.Property;
import example.parser.IParser;
import example.utils.Constant;

import java.util.regex.Pattern;

public abstract class RegexParser implements IParser {

    protected String getReferencePattern() {
        return "";
    }

    @Override
    public boolean canParse(Property property) {
        if (!property.getJsonNode().has(Constant.PATTERN)) {
            return false;
        }
        return Pattern.compile(property.getJsonNode().get(Constant.PATTERN).asText())
                .matcher(getReferencePattern())
                .matches();
    }
}
