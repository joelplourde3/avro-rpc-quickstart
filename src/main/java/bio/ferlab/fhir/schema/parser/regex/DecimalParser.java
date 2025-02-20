package bio.ferlab.fhir.schema.parser.regex;

import bio.ferlab.fhir.schema.definition.Property;
import bio.ferlab.fhir.schema.utils.Constant;
import bio.ferlab.fhir.schema.utils.JsonObjectUtils;

import javax.json.*;

public class DecimalParser extends RegexParser {

    @Override
    public JsonObject parseField(String root, String identifier, Property property) {
        JsonObject logicalType = JsonObjectUtils.createLogicalType(Constant.BYTES, Constant.DECIMAL)
                .add("precision", 18)
                .add("scale", 0)
                .build();
        return JsonObjectUtils.createField(identifier, logicalType, property.isRequired());
    }

    // Must supported minimally 18 decimal digits; https://www.w3.org/TR/xmlschema-2/#decimal
    @Override
    protected String getReferencePattern() {
        return "0.000000000000000000";
    }
}
