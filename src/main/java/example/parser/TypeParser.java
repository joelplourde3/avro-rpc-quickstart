package example.parser;

import example.definition.Property;
import example.parser.regex.DecimalParser;
import example.repository.DefinitionRepository;
import example.utils.Constant;
import example.utils.ConverterUtils;

import javax.json.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class TypeParser implements IParser {

    private static final List<IParser> innerParser = Collections.unmodifiableList(new ArrayList<IParser>() {{
        add(new DecimalParser());
    }});

    @Override
    public boolean canParse(Property property) {
        return property.getJsonNode().has(Constant.TYPE);
    }

    @Override
    public JsonObject parseField(String root, String identifier, Property property) {
        Optional<IParser> parser = innerParser.stream()
                .filter(x -> x.canParse(property))
                .findFirst();
        if (parser.isPresent()) {
            return parser.get().parseField(root, identifier, property);
        }

        String reference = ConverterUtils.parsePrimitiveType(property.getJsonNode().get(Constant.TYPE).toString());
        return DefinitionRepository.getPrimitiveDefinitionByIdentifier(reference).convertToJson(root, identifier, property.isRequired());
    }
}
