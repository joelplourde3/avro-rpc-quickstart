package example.definition.specificity;

import example.utils.Constant;
import example.utils.JsonObjectUtils;

import javax.json.Json;
import javax.json.JsonObject;

public class XHtmlDefinition extends SpecificDefinition {

    @Override
    public JsonObject convertToJson(String name, boolean required) {
        return Json.createObjectBuilder()
                .add(Constant.TYPE, Constant.RECORD)
                .add(Constant.NAME, name)
                .add(Constant.NAMESPACE, Constant.NAMESPACE_VALUE)
                .add(Constant.FIELDS, Json.createArrayBuilder()
                        .add(JsonObjectUtils.createConst("extension", Constant.STRING, false))
                        .add(JsonObjectUtils.createConst("url", Constant.STRING, false))
                        .add(JsonObjectUtils.createConst("valueString", Constant.STRING, false))
                        .build())
                .add(Constant.DEFAULT, JsonObject.NULL) // TODO add the required if any.
                .build();
    }
}
