package bio.ferlab.fhir.parser.regex;

import bio.ferlab.fhir.definition.Property;
import bio.ferlab.fhir.parser.IParser;
import bio.ferlab.fhir.utils.Constant;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public abstract class RegexParser implements IParser {

    protected String getReferencePattern() {
        return "";
    }

    @Override
    public boolean canParse(Property property) {
        if (!property.getJsonNode().has(Constant.PATTERN)) {
            return false;
        }
        try {
            return Pattern.compile(property.getJsonNode().get(Constant.PATTERN).asText())
                    .matcher(getReferencePattern())
                    .matches();
        } catch(PatternSyntaxException patternSyntaxException) {
            return false;
        }
    }
}
