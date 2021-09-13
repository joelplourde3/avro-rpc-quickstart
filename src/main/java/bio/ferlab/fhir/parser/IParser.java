package bio.ferlab.fhir.parser;

import bio.ferlab.fhir.definition.Property;

import javax.json.JsonObject;

public interface IParser {

    boolean canParse(Property property);

    JsonObject parseField(String root, String identifier, Property property);
}
