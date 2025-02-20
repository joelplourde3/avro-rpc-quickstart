package bio.ferlab.fhir.schema.definition.specificity;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class SpecificDefinitionFactory {

    private static final Map<String, SpecificDefinition> specificDefinitions = new HashMap<String, SpecificDefinition>() {{
        put("date", new DateDefinition());
        put("dateTime", new DateTimeDefinition());
        put("xhtml", new XHtmlDefinition());
        put("Extension", new ExtensionDefinition());
        put("Reference", new ReferenceDefinition());
        put("ResourceList", new ResourceListDefinition());
    }};

    private SpecificDefinitionFactory() {}

    public static SpecificDefinition getSpecificDefinition(String identifier) {
        return Optional.ofNullable(specificDefinitions.get(identifier))
                .orElseThrow(() -> new RuntimeException("Unknown SpecificDefinition, please verify: " + identifier));
    }

    public static boolean isSupported(String identifier) {
        return specificDefinitions.containsKey(identifier);
    }
}
