package example.definition;

import com.fasterxml.jackson.databind.JsonNode;
import example.utils.Constant;
import example.utils.JsonObjectUtils;

import javax.json.JsonObject;
import java.util.Map;

public class PrimitiveDefinition extends BaseDefinition {

    private String type;
    private String pattern;

    public PrimitiveDefinition(Map.Entry<String, JsonNode> entry) {
        super(entry.getKey(), entry.getKey(), entry.getValue());
        setType(getDefinition().get(Constant.TYPE).asText());
        if (getDefinition().has(Constant.PATTERN)) {
            setPattern(getDefinition().get(Constant.PATTERN).asText());
        }
    }

    public JsonObject convertToJson(String name, boolean required) {
        return JsonObjectUtils.createConst(name, getType(), required);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }
}
