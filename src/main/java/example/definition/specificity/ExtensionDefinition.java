package example.definition.specificity;

import com.fasterxml.jackson.databind.JsonNode;
import example.definition.BaseDefinition;
import example.utils.JsonObjectUtils;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;

public class ExtensionDefinition extends BaseDefinition {

    public ExtensionDefinition(String name, String identifier, JsonNode definition) {
        super(name, identifier, definition);
    }

    // TODO Find a way to support variable value type.
    @Override
    public JsonObject convertToJson(String name, boolean required) {
        if (getJsonObject() != null) {
            return getJsonObject();
        }

        JsonArray fields = Json.createArrayBuilder()
                .add(JsonObjectUtils.createConst("url", "string", false))
                .add(JsonObjectUtils.createConst("value[x]", "string", false))
                .build();

        setJsonObject(Json.createObjectBuilder()
                .add("type", "record")
                .add("name", "extension")
                .add("namespace", "namespace")
                .add("fields", fields)
                .add("default", JsonObject.NULL) // TODO add the required if any.
                .build());

        return getJsonObject();

        // TODO support all these potential values of all these potential types.
        /*
          "url" : "<uri>", // R!  identifies the meaning of the extension
          // value[x]: Value of extension. One of these 50:
          "valueBase64Binary" : "<base64Binary>"
          "valueBoolean" : <boolean>
          "valueCanonical" : "<canonical>"
          "valueCode" : "<code>"
          "valueDate" : "<date>"
          "valueDateTime" : "<dateTime>"
          "valueDecimal" : <decimal>
          "valueId" : "<id>"
          "valueInstant" : "<instant>"
          "valueInteger" : <integer>
          "valueMarkdown" : "<markdown>"
          "valueOid" : "<oid>"
          "valuePositiveInt" : "<positiveInt>"
          "valueString" : "<string>"
          "valueTime" : "<time>"
          "valueUnsignedInt" : "<unsignedInt>"
          "valueUri" : "<uri>"
          "valueUrl" : "<url>"
          "valueUuid" : "<uuid>"
          "valueAddress" : { Address }
          "valueAge" : { Age }
          "valueAnnotation" : { Annotation }
          "valueAttachment" : { Attachment }
          "valueCodeableConcept" : { CodeableConcept }
          "valueCoding" : { Coding }
          "valueContactPoint" : { ContactPoint }
          "valueCount" : { Count }
          "valueDistance" : { Distance }
          "valueDuration" : { Duration }
          "valueHumanName" : { HumanName }
          "valueIdentifier" : { Identifier }
          "valueMoney" : { Money }
          "valuePeriod" : { Period }
          "valueQuantity" : { Quantity }
          "valueRange" : { Range }
          "valueRatio" : { Ratio }
          "valueReference" : { Reference }
          "valueSampledData" : { SampledData }
          "valueSignature" : { Signature }
          "valueTiming" : { Timing }
          "valueContactDetail" : { ContactDetail }
          "valueContributor" : { Contributor }
          "valueDataRequirement" : { DataRequirement }
          "valueExpression" : { Expression }
          "valueParameterDefinition" : { ParameterDefinition }
          "valueRelatedArtifact" : { RelatedArtifact }
          "valueTriggerDefinition" : { TriggerDefinition }
          "valueUsageContext" : { UsageContext }
          "valueDosage" : { Dosage }
          "valueMeta" : { Meta }
         */
    }
}
