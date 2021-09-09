package example.definition.specificity;

import example.utils.Constant;
import example.utils.JsonObjectUtils;

import javax.json.Json;
import javax.json.JsonObject;

public class ReferenceDefinition extends SpecificDefinition {

    @Override
    public JsonObject convertToJson(String name, boolean required) {
        // TODO add the required if any.
        return Json.createObjectBuilder()
                .add(Constant.TYPE, Constant.RECORD)
                .add(Constant.NAME, name)
                .add(Constant.NAMESPACE, Constant.NAMESPACE_VALUE)
                .add(Constant.FIELDS, Json.createArrayBuilder()
                        .add(JsonObjectUtils.createConst("reference", Constant.STRING, false))
                        .add(JsonObjectUtils.createConst("type", Constant.STRING, false))
                        .add(JsonObjectUtils.createConst("identifier", Constant.STRING, false))
                        .add(JsonObjectUtils.createConst("display", Constant.STRING, false))
                        .build())
                .add(Constant.DEFAULT, JsonObject.NULL)
                .build();
    }
}
