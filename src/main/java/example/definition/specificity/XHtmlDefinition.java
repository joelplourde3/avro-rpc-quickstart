package example.definition.specificity;

import example.utils.Constant;
import example.utils.JsonObjectUtils;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;

public class XHtmlDefinition extends SpecificDefinition {

    @Override
    public JsonObject convertToJson(String root, String name, boolean required) {
        JsonArray fields = Json.createArrayBuilder()
                .add(JsonObjectUtils.createConst("extension", Constant.STRING, false))
                .add(JsonObjectUtils.createConst("url", Constant.STRING, false))
                .add(JsonObjectUtils.createConst("valueString", Constant.STRING, false))
                .build();
        return JsonObjectUtils.createInnerRecord(name, "", fields, required);
    }
}
