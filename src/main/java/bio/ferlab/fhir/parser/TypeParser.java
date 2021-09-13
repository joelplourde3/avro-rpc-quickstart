package bio.ferlab.fhir.parser;

import bio.ferlab.fhir.definition.Property;
import bio.ferlab.fhir.parser.regex.DecimalParser;
import bio.ferlab.fhir.repository.DefinitionRepository;
import bio.ferlab.fhir.utils.Constant;
import bio.ferlab.fhir.utils.ConverterUtils;

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
