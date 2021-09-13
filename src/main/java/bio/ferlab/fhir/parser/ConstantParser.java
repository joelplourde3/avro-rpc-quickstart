package bio.ferlab.fhir.parser;

import bio.ferlab.fhir.definition.Property;
import bio.ferlab.fhir.utils.Constant;
import bio.ferlab.fhir.utils.JsonObjectUtils;

import javax.json.JsonObject;

public class ConstantParser implements IParser {

    @Override
    public boolean canParse(Property property) {
        return property.getJsonNode().has("const");
    }

    @Override
    public JsonObject parseField(String root, String identifier, Property property) {
        return JsonObjectUtils.createConst(identifier, Constant.STRING, property.isRequired());
    }
}
