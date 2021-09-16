package bio.ferlab.fhir.schema;

import ca.uhn.fhir.context.*;
import ca.uhn.fhir.model.api.*;
import ca.uhn.fhir.model.api.annotation.Child;
import ca.uhn.fhir.model.base.composite.BaseCodingDt;
import ca.uhn.fhir.model.base.composite.BaseContainedDt;
import ca.uhn.fhir.model.base.resource.ResourceMetadataMap;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.model.primitive.InstantDt;
import ca.uhn.fhir.model.primitive.XhtmlDt;
import ca.uhn.fhir.narrative.INarrativeGenerator;
import ca.uhn.fhir.parser.*;
import ca.uhn.fhir.parser.json.*;
import ca.uhn.fhir.parser.json.jackson.JacksonStructure;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.util.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.WordUtils;
import org.hl7.fhir.instance.model.api.*;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.TimeType;

import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static ca.uhn.fhir.context.BaseRuntimeElementDefinition.ChildTypeEnum.ID_DATATYPE;
import static ca.uhn.fhir.context.BaseRuntimeElementDefinition.ChildTypeEnum.PRIMITIVE_DATATYPE;
import static org.apache.commons.lang3.StringUtils.*;

public class ExtendedJsonParser extends BaseParser {

    private boolean prettyPrint;
    private FhirTerser.ContainedResources containedResources;

    public ExtendedJsonParser() {
        super(FhirContext.forR4(), new LenientErrorHandler());
    }

    @Override
    protected void doEncodeResourceToWriter(IBaseResource iBaseResource, Writer writer, EncodeContext encodeContext) throws IOException, DataFormatException {
        JsonLikeWriter eventWriter = createJsonWriter(writer);
        doEncodeResourceToJsonLikeWriter(iBaseResource, eventWriter, encodeContext);
        eventWriter.close();
    }

    private JsonLikeWriter createJsonWriter(Writer writer) throws IOException {
        JsonLikeStructure jsonStructure = new JacksonStructure();
        return jsonStructure.getJsonLikeWriter(writer);
    }

    public void doEncodeResourceToJsonLikeWriter(IBaseResource theResource, JsonLikeWriter eventWriter, EncodeContext theEncodeContext) throws IOException {
        if (prettyPrint) {
            eventWriter.setPrettyPrint(prettyPrint);
        }
        eventWriter.init();

        RuntimeResourceDefinition resDef = getContext().getResourceDefinition(theResource);
        encodeResourceToJsonStreamWriter(resDef, theResource, eventWriter, null, false, theEncodeContext);
        eventWriter.flush();
    }

    private void encodeResourceToJsonStreamWriter(RuntimeResourceDefinition theResDef, IBaseResource theResource, JsonLikeWriter theEventWriter, String theObjectNameOrNull,
                                                  boolean theContainedResource, EncodeContext theEncodeContext) throws IOException {
        IIdType resourceId = null;

        if (StringUtils.isNotBlank(theResource.getIdElement().getIdPart())) {
            resourceId = theResource.getIdElement();
            if (theResource.getIdElement().getValue().startsWith("urn:")) {
                resourceId = null;
            }
        }

        if (!theContainedResource) {
            if (!super.shouldEncodeResourceId(theResource, theEncodeContext)) {
                resourceId = null;
            } else if (theEncodeContext.getResourcePath().size() == 1 && getEncodeForceResourceId() != null) {
                resourceId = getEncodeForceResourceId();
            }
        }

        encodeResourceToJsonStreamWriter(theResDef, theResource, theEventWriter, theObjectNameOrNull, theContainedResource, resourceId, theEncodeContext);
    }

    private void writeOptionalTagWithTextNode(JsonLikeWriter theEventWriter, String theElementName, IPrimitiveDatatype<?> thePrimitive) throws IOException {
        if (thePrimitive == null) {
            return;
        }
        String str = thePrimitive.getValueAsString();
        writeOptionalTagWithTextNode(theEventWriter, theElementName, str);
    }

    private void writeOptionalTagWithTextNode(JsonLikeWriter theEventWriter, String theElementName, String theValue) throws IOException {
        if (StringUtils.isNotBlank(theValue)) {
            write(theEventWriter, theElementName, theValue);
        }
    }

    ChildNameAndDef getChildNameAndDef(BaseRuntimeChildDefinition theChild, IBase theValue) {
        Class<? extends IBase> type = theValue.getClass();
        String childName = theChild.getChildNameByDatatype(type);
        BaseRuntimeElementDefinition<?> childDef = theChild.getChildElementDefinitionByDatatype(type);
        if (childDef == null) {
            BaseRuntimeElementDefinition<?> elementDef = super.getContext().getElementDefinition(type);
            if (elementDef.getName().equals("code")) {
                Class<? extends IBase> type2 = super.getContext().getElementDefinition("code").getImplementingClass();
                childDef = theChild.getChildElementDefinitionByDatatype(type2);
                childName = theChild.getChildNameByDatatype(type2);
            }

            // See possibly the user has extended a built-in type without
            // declaring it anywhere, as in XmlParserDstu3Test#testEncodeUndeclaredBlock
            if (childDef == null) {
                Class<?> nextSuperType = theValue.getClass();
                while (IBase.class.isAssignableFrom(nextSuperType) && childDef == null) {
                    if (Modifier.isAbstract(nextSuperType.getModifiers()) == false) {
                        BaseRuntimeElementDefinition<?> def = super.getContext().getElementDefinition((Class<? extends IBase>) nextSuperType);
                        Class<?> nextChildType = def.getImplementingClass();
                        childDef = theChild.getChildElementDefinitionByDatatype((Class<? extends IBase>) nextChildType);
                        childName = theChild.getChildNameByDatatype((Class<? extends IBase>) nextChildType);
                    }
                    nextSuperType = nextSuperType.getSuperclass();
                }
            }

            if (childDef == null) {
                throwExceptionForUnknownChildType(theChild, type);
            }
        }

        return new ChildNameAndDef(childName, childDef);
    }

    private boolean addToHeldComments(int valueIdx, List<String> theCommentsToAdd, ArrayList<ArrayList<String>> theListToAddTo) {
        if (theCommentsToAdd.size() > 0) {
            theListToAddTo.ensureCapacity(valueIdx);
            while (theListToAddTo.size() <= valueIdx) {
                theListToAddTo.add(null);
            }
            if (theListToAddTo.get(valueIdx) == null) {
                theListToAddTo.set(valueIdx, new ArrayList<>());
            }
            theListToAddTo.get(valueIdx).addAll(theCommentsToAdd);
            return true;
        }
        return false;
    }

    private boolean addToHeldExtensions(int valueIdx, List<? extends IBaseExtension<?, ?>> ext, ArrayList<ArrayList<HeldExtension>> list, boolean theIsModifier, CompositeChildElement theChildElem,
                                        CompositeChildElement theParent, EncodeContext theEncodeContext, boolean theContainedResource, IBase theContainingElement) {
        boolean retVal = false;
        if (ext.size() > 0) {
            Boolean encodeExtension = null;
            for (IBaseExtension<?, ?> next : ext) {

                if (next.isEmpty()) {
                    continue;
                }

                // Make sure we respect _summary and _elements
                if (encodeExtension == null) {
                    encodeExtension = isEncodeExtension(theParent, theEncodeContext, theContainedResource, theContainingElement);
                }

                if (encodeExtension) {
                    HeldExtension extension = new HeldExtension(next, theIsModifier, theChildElem, theParent);
                    list.ensureCapacity(valueIdx);
                    while (list.size() <= valueIdx) {
                        list.add(null);
                    }
                    ArrayList<HeldExtension> extensionList = list.get(valueIdx);
                    if (extensionList == null) {
                        extensionList = new ArrayList<>();
                        list.set(valueIdx, extensionList);
                    }
                    extensionList.add(extension);
                    retVal = true;
                }
            }
        }
        return retVal;
    }

    private void addToHeldIds(int theValueIdx, ArrayList<String> theListToAddTo, String theId) {
        theListToAddTo.ensureCapacity(theValueIdx);
        while (theListToAddTo.size() <= theValueIdx) {
            theListToAddTo.add(null);
        }
        if (theListToAddTo.get(theValueIdx) == null) {
            theListToAddTo.set(theValueIdx, theId);
        }
    }

    private boolean isMultipleCardinality(int maxCardinality) {
        return maxCardinality > 1 || maxCardinality == Child.MAX_UNLIMITED;
    }

    private void encodeCompositeElementChildrenToStreamWriter(RuntimeResourceDefinition theResDef, IBaseResource theResource, IBase theElement, JsonLikeWriter theEventWriter,
                                                              boolean theContainedResource, CompositeChildElement theParent, EncodeContext theEncodeContext) throws IOException {

        {
            String elementId = getCompositeElementId(theElement);
            if (isNotBlank(elementId)) {
                write(theEventWriter, "id", elementId);
            }
        }

        boolean haveWrittenExtensions = false;
        Iterable<CompositeChildElement> compositeChildElements = super.compositeChildIterator(theElement, theContainedResource, theParent, theEncodeContext);
        for (CompositeChildElement nextChildElem : compositeChildElements) {

            BaseRuntimeChildDefinition nextChild = nextChildElem.getDef();

            if (nextChildElem.getDef().getElementName().equals("extension") || nextChildElem.getDef().getElementName().equals("modifierExtension")
                    || nextChild instanceof RuntimeChildDeclaredExtensionDefinition) {
                if (!haveWrittenExtensions) {
                    extractAndWriteExtensionsAsDirectChild(theElement, theEventWriter, getContext().getElementDefinition(theElement.getClass()), theResDef, theResource, nextChildElem, theParent, theEncodeContext, theContainedResource);
                    haveWrittenExtensions = true;
                }
                continue;
            }

            if (nextChild instanceof RuntimeChildNarrativeDefinition) {
                INarrativeGenerator gen = getContext().getNarrativeGenerator();
                if (gen != null) {
                    INarrative narr;
                    if (theResource instanceof IResource) {
                        narr = ((IResource) theResource).getText();
                    } else if (theResource instanceof IDomainResource) {
                        narr = ((IDomainResource) theResource).getText();
                    } else {
                        narr = null;
                    }
                    if (narr != null && narr.isEmpty()) {
                        gen.populateResourceNarrative(getContext(), theResource);
                        if (!narr.isEmpty()) {
                            RuntimeChildNarrativeDefinition child = (RuntimeChildNarrativeDefinition) nextChild;
                            String childName = nextChild.getChildNameByDatatype(child.getDatatype());
                            BaseRuntimeElementDefinition<?> type = child.getChildByName(childName);
                            encodeChildElementToStreamWriter(theResDef, theResource, theEventWriter, narr, type, childName, theContainedResource, nextChildElem, false, theEncodeContext);
                            continue;
                        }
                    }
                }
            } else if (nextChild instanceof RuntimeChildContainedResources) {
                String childName = nextChild.getValidChildNames().iterator().next();
                BaseRuntimeElementDefinition<?> child = nextChild.getChildByName(childName);
                encodeChildElementToStreamWriter(theResDef, theResource, theEventWriter, null, child, childName, theContainedResource, nextChildElem, false, theEncodeContext);
                continue;
            }

            List<? extends IBase> values = nextChild.getAccessor().getValues(theElement);
            values = preProcessValues(nextChild, theResource, values, nextChildElem, theEncodeContext);

            if (values == null || values.isEmpty()) {
                continue;
            }

            String currentChildName = null;
            boolean inArray = false;

            ArrayList<ArrayList<HeldExtension>> extensions = new ArrayList<>(0);
            ArrayList<ArrayList<HeldExtension>> modifierExtensions = new ArrayList<>(0);
            ArrayList<ArrayList<String>> comments = new ArrayList<>(0);
            ArrayList<String> ids = new ArrayList<>(0);

            int valueIdx = 0;
            for (IBase nextValue : values) {

                if (nextValue == null || nextValue.isEmpty()) {
                    if (nextValue instanceof BaseContainedDt) {
                        if (theContainedResource || getContainedResources().isEmpty()) {
                            continue;
                        }
                    } else {
                        continue;
                    }
                }

                ChildNameAndDef childNameAndDef = getChildNameAndDef(nextChild, nextValue);
                if (childNameAndDef == null) {
                    continue;
                }

                /*
                 * Often the two values below will be the same thing. There are cases though
                 * where they will not be. An example would be Observation.value, which is
                 * a choice type. If the value contains a Quantity, then:
                 * nextChildGenericName = "value"
                 * nextChildSpecificName = "valueQuantity"
                 */
                String nextChildSpecificName = childNameAndDef.getChildName();
                String nextChildGenericName = nextChild.getElementName();

                theEncodeContext.pushPath(nextChildGenericName, false);

                BaseRuntimeElementDefinition<?> childDef = childNameAndDef.getChildDef();
                boolean primitive = childDef.getChildType() == BaseRuntimeElementDefinition.ChildTypeEnum.PRIMITIVE_DATATYPE;

                if ((childDef.getChildType() == BaseRuntimeElementDefinition.ChildTypeEnum.CONTAINED_RESOURCES || childDef.getChildType() == BaseRuntimeElementDefinition.ChildTypeEnum.CONTAINED_RESOURCE_LIST) && theContainedResource) {
                    continue;
                }

                boolean force = false;
                if (primitive) {
                    if (nextValue instanceof ISupportsUndeclaredExtensions) {
                        List<ExtensionDt> ext = ((ISupportsUndeclaredExtensions) nextValue).getUndeclaredExtensions();
                        force |= addToHeldExtensions(valueIdx, ext, extensions, false, nextChildElem, theParent, theEncodeContext, theContainedResource, theElement);

                        ext = ((ISupportsUndeclaredExtensions) nextValue).getUndeclaredModifierExtensions();
                        force |= addToHeldExtensions(valueIdx, ext, modifierExtensions, true, nextChildElem, theParent, theEncodeContext, theContainedResource, theElement);
                    } else {
                        if (nextValue instanceof IBaseHasExtensions) {
                            IBaseHasExtensions element = (IBaseHasExtensions) nextValue;
                            List<? extends IBaseExtension<?, ?>> ext = element.getExtension();
                            force |= addToHeldExtensions(valueIdx, ext, extensions, false, nextChildElem, theParent, theEncodeContext, theContainedResource, theElement);
                        }
                        if (nextValue instanceof IBaseHasModifierExtensions) {
                            IBaseHasModifierExtensions element = (IBaseHasModifierExtensions) nextValue;
                            List<? extends IBaseExtension<?, ?>> ext = element.getModifierExtension();
                            force |= addToHeldExtensions(valueIdx, ext, modifierExtensions, true, nextChildElem, theParent, theEncodeContext, theContainedResource, theElement);
                        }
                    }
                    if (nextValue.hasFormatComment()) {
                        force |= addToHeldComments(valueIdx, nextValue.getFormatCommentsPre(), comments);
                        force |= addToHeldComments(valueIdx, nextValue.getFormatCommentsPost(), comments);
                    }
                    String elementId = getCompositeElementId(nextValue);
                    if (isNotBlank(elementId)) {
                        force = true;
                        addToHeldIds(valueIdx, ids, elementId);
                    }
                }

                if (currentChildName == null || !currentChildName.equals(nextChildSpecificName)) {
                    if (inArray) {
                        theEventWriter.endArray();
                    }
                    BaseRuntimeChildDefinition replacedParentDefinition = nextChild.getReplacedParentDefinition();
                    if (isMultipleCardinality(nextChild.getMax()) || (replacedParentDefinition != null && isMultipleCardinality(replacedParentDefinition.getMax()))) {
                        beginArray(theEventWriter, nextChildSpecificName);
                        inArray = true;
                        encodeChildElementToStreamWriter(theResDef, theResource, theEventWriter, nextValue, childDef, null, theContainedResource, nextChildElem, force, theEncodeContext);
                    } else if (nextChild instanceof RuntimeChildNarrativeDefinition && theContainedResource) {
                        // suppress narratives from contained resources
                    } else {
                        encodeChildElementToStreamWriter(theResDef, theResource, theEventWriter, nextValue, childDef, nextChildSpecificName, theContainedResource, nextChildElem, false, theEncodeContext);
                    }
                    currentChildName = nextChildSpecificName;
                } else {
                    encodeChildElementToStreamWriter(theResDef, theResource, theEventWriter, nextValue, childDef, null, theContainedResource, nextChildElem, force, theEncodeContext);
                }

                valueIdx++;
                theEncodeContext.popPath();
            }

            if (inArray) {
                theEventWriter.endArray();
            }


            if (!extensions.isEmpty() || !modifierExtensions.isEmpty() || !comments.isEmpty()) {
                if (inArray) {
                    // If this is a repeatable field, the extensions go in an array too
                    beginArray(theEventWriter, '_' + currentChildName);
                } else {
                    beginObject(theEventWriter, '_' + currentChildName);
                }

                for (int i = 0; i < valueIdx; i++) {
                    boolean haveContent = false;

                    List<HeldExtension> heldExts = Collections.emptyList();
                    List<HeldExtension> heldModExts = Collections.emptyList();
                    if (extensions.size() > i && extensions.get(i) != null && extensions.get(i).isEmpty() == false) {
                        haveContent = true;
                        heldExts = extensions.get(i);
                    }

                    if (modifierExtensions.size() > i && modifierExtensions.get(i) != null && modifierExtensions.get(i).isEmpty() == false) {
                        haveContent = true;
                        heldModExts = modifierExtensions.get(i);
                    }

                    ArrayList<String> nextComments;
                    if (comments.size() > i) {
                        nextComments = comments.get(i);
                    } else {
                        nextComments = null;
                    }
                    if (nextComments != null && nextComments.isEmpty() == false) {
                        haveContent = true;
                    }

                    String elementId = null;
                    if (ids.size() > i) {
                        elementId = ids.get(i);
                        haveContent |= isNotBlank(elementId);
                    }

                    if (!haveContent) {
                        theEventWriter.writeNull();
                    } else {
                        if (inArray) {
                            theEventWriter.beginObject();
                        }
                        if (isNotBlank(elementId)) {
                            write(theEventWriter, "id", elementId);
                        }
                        if (nextComments != null && !nextComments.isEmpty()) {
                            beginArray(theEventWriter, "fhir_comments");
                            for (String next : nextComments) {
                                theEventWriter.write(next);
                            }
                            theEventWriter.endArray();
                        }
                        writeExtensionsAsDirectChild(theResource, theEventWriter, theResDef, heldExts, heldModExts, theEncodeContext, theContainedResource);
                        if (inArray) {
                            theEventWriter.endObject();
                        }
                    }
                }

                if (inArray) {
                    theEventWriter.endArray();
                } else {
                    theEventWriter.endObject();
                }
            }
        }
    }

    private void encodeResourceToJsonStreamWriter(RuntimeResourceDefinition theResDef, IBaseResource theResource, JsonLikeWriter theEventWriter, String theObjectNameOrNull,
                                                  boolean theContainedResource, IIdType theResourceId, EncodeContext theEncodeContext) throws IOException {
        if (!super.shouldEncodeResource(theResDef.getName())) {
            return;
        }

        if (!theContainedResource) {
            setContainedResources(getContext().newTerser().containResources(theResource));
        }

        RuntimeResourceDefinition resDef = getContext().getResourceDefinition(theResource);

        if (theObjectNameOrNull == null) {
            theEventWriter.beginObject();
        } else {
            beginObject(theEventWriter, theObjectNameOrNull);
        }

        write(theEventWriter, "resourceType", resDef.getName());
        if (theResourceId != null && theResourceId.hasIdPart()) {
            write(theEventWriter, "id", theResourceId.getIdPart());
            final List<HeldExtension> extensions = new ArrayList<>(0);
            final List<HeldExtension> modifierExtensions = new ArrayList<>(0);
            // Undeclared extensions
            extractUndeclaredExtensions(theResourceId, extensions, modifierExtensions, null, null, theEncodeContext, theContainedResource);
            boolean haveExtension = false;
            if (!extensions.isEmpty()) {
                haveExtension = true;
            }

            if (theResourceId.hasFormatComment() || haveExtension) {
                beginObject(theEventWriter, "_id");
                if (theResourceId.hasFormatComment()) {
                    writeCommentsPreAndPost(theResourceId, theEventWriter);
                }
                if (haveExtension) {
                    writeExtensionsAsDirectChild(theResource, theEventWriter, theResDef, extensions, modifierExtensions, theEncodeContext, theContainedResource);
                }
                theEventWriter.endObject();
            }
        }

        if (theResource instanceof IResource) {
            IResource resource = (IResource) theResource;
            // Object securityLabelRawObj =

            List<BaseCodingDt> securityLabels = extractMetadataListNotNull(resource, ResourceMetadataKeyEnum.SECURITY_LABELS);
            List<? extends IIdType> profiles = extractMetadataListNotNull(resource, ResourceMetadataKeyEnum.PROFILES);
            profiles = super.getProfileTagsForEncoding(resource, profiles);

            TagList tags = getMetaTagsForEncoding(resource, theEncodeContext);
            InstantDt updated = (InstantDt) resource.getResourceMetadata().get(ResourceMetadataKeyEnum.UPDATED);
            IdDt resourceId = resource.getId();
            String versionIdPart = resourceId.getVersionIdPart();
            if (isBlank(versionIdPart)) {
                versionIdPart = ResourceMetadataKeyEnum.VERSION.get(resource);
            }
            List<Map.Entry<ResourceMetadataKeyEnum<?>, Object>> extensionMetadataKeys = getExtensionMetadataKeys(resource);

            if (super.shouldEncodeResourceMeta(resource) && (ElementUtil.isEmpty(versionIdPart, updated, securityLabels, tags, profiles) == false) || !extensionMetadataKeys.isEmpty()) {
                beginObject(theEventWriter, "meta");

                if (shouldEncodePath(resource, "meta.versionId")) {
                    writeOptionalTagWithTextNode(theEventWriter, "versionId", versionIdPart);
                }
                if (shouldEncodePath(resource, "meta.lastUpdated")) {
                    writeOptionalTagWithTextNode(theEventWriter, "lastUpdated", updated);
                }

                if (profiles != null && !profiles.isEmpty()) {
                    beginArray(theEventWriter, "profile");
                    for (IIdType profile : profiles) {
                        if (profile != null && isNotBlank(profile.getValue())) {
                            theEventWriter.write(profile.getValue());
                        }
                    }
                    theEventWriter.endArray();
                }

                if (!securityLabels.isEmpty()) {
                    beginArray(theEventWriter, "security");
                    for (BaseCodingDt securityLabel : securityLabels) {
                        theEventWriter.beginObject();
                        theEncodeContext.pushPath("security", false);
                        encodeCompositeElementChildrenToStreamWriter(resDef, resource, securityLabel, theEventWriter, theContainedResource, null, theEncodeContext);
                        theEncodeContext.popPath();
                        theEventWriter.endObject();
                    }
                    theEventWriter.endArray();
                }

                if (tags != null && !tags.isEmpty()) {
                    beginArray(theEventWriter, "tag");
                    for (Tag tag : tags) {
                        if (tag.isEmpty()) {
                            continue;
                        }
                        theEventWriter.beginObject();
                        writeOptionalTagWithTextNode(theEventWriter, "system", tag.getScheme());
                        writeOptionalTagWithTextNode(theEventWriter, "code", tag.getTerm());
                        writeOptionalTagWithTextNode(theEventWriter, "display", tag.getLabel());
                        theEventWriter.endObject();
                    }
                    theEventWriter.endArray();
                }

                addExtensionMetadata(theResDef, theResource, theContainedResource, extensionMetadataKeys, resDef, theEventWriter, theEncodeContext);

                theEventWriter.endObject(); // end meta
            }
        }

        encodeCompositeElementToStreamWriter(theResDef, theResource, theResource, theEventWriter, theContainedResource, new CompositeChildElement(resDef, theEncodeContext), theEncodeContext);

        theEventWriter.endObject();
    }

    private void encodeCompositeElementToStreamWriter(RuntimeResourceDefinition theResDef, IBaseResource theResource, IBase theNextValue, JsonLikeWriter theEventWriter, boolean theContainedResource, CompositeChildElement theParent, EncodeContext theEncodeContext) throws IOException, DataFormatException {

        writeCommentsPreAndPost(theNextValue, theEventWriter);
        encodeCompositeElementChildrenToStreamWriter(theResDef, theResource, theNextValue, theEventWriter, theContainedResource, theParent, theEncodeContext);
    }

    private void addExtensionMetadata(RuntimeResourceDefinition theResDef, IBaseResource theResource,
                                      boolean theContainedResource,
                                      List<Map.Entry<ResourceMetadataKeyEnum<?>, Object>> extensionMetadataKeys,
                                      RuntimeResourceDefinition resDef,
                                      JsonLikeWriter theEventWriter, EncodeContext theEncodeContext) throws IOException {
        if (extensionMetadataKeys.isEmpty()) {
            return;
        }

        ExtensionDt metaResource = new ExtensionDt();
        for (Map.Entry<ResourceMetadataKeyEnum<?>, Object> entry : extensionMetadataKeys) {
            metaResource.addUndeclaredExtension((ExtensionDt) entry.getValue());
        }
        encodeCompositeElementToStreamWriter(theResDef, theResource, metaResource, theEventWriter, theContainedResource, new CompositeChildElement(resDef, theEncodeContext), theEncodeContext);
    }


    private void beginArray(JsonLikeWriter theEventWriter, String arrayName) throws IOException {
        theEventWriter.beginArray(arrayName);
    }

    private void beginObject(JsonLikeWriter theEventWriter, String arrayName) throws IOException {
        theEventWriter.beginObject(arrayName);
    }

    @Override
    public <T extends IBaseResource> T doParseResource(Class<T> theResourceType, Reader theReader) {
        JsonLikeStructure jsonStructure = new JacksonStructure();
        jsonStructure.load(theReader);

        T retVal = doParseResource(theResourceType, jsonStructure);

        return retVal;
    }

    public <T extends IBaseResource> T doParseResource(Class<T> theResourceType, JsonLikeStructure theJsonStructure) {
        JsonLikeObject object = theJsonStructure.getRootObject();

        JsonLikeValue resourceTypeObj = object.get("resourceType");
        if (resourceTypeObj == null || !resourceTypeObj.isString() || isBlank(resourceTypeObj.getAsString())) {
            throw new DataFormatException("Invalid JSON content detected, missing required element: 'resourceType'");
        }

        String resourceType = resourceTypeObj.getAsString();

        ParserState<? extends IBaseResource> state = ParserState.getPreResourceInstance(this, theResourceType, getContext(), true, getErrorHandler());
        state.enteringNewElement(null, resourceType);

        parseChildren(object, state);

        state.endingElement();
        state.endingElement();

        @SuppressWarnings("unchecked")
        T retVal = (T) state.getObject();

        return retVal;
    }

    private JsonLikeArray grabJsonArray(JsonLikeObject theObject, String nextName, String thePosition) {
        JsonLikeValue object = theObject.get(nextName);
        if (object == null || object.isNull()) {
            return null;
        }
        if (!object.isArray()) {
            throw new DataFormatException("Syntax error parsing JSON FHIR structure: Expected ARRAY at element '" + thePosition + "', found '" + object.getJsonType() + "'");
        }
        return object.getAsArray();
    }

    private void parseAlternates(JsonLikeValue theAlternateVal, ParserState<?> theState, String theElementName, String theAlternateName) {
        if (theAlternateVal == null || theAlternateVal.isNull()) {
            return;
        }

        if (theAlternateVal.isArray()) {
            JsonLikeArray array = theAlternateVal.getAsArray();
            if (array.size() > 1) {
                throw new DataFormatException("Unexpected array of length " + array.size() + " (expected 0 or 1) for element: " + theElementName);
            }
            if (array.size() == 0) {
                return;
            }
            parseAlternates(array.get(0), theState, theElementName, theAlternateName);
            return;
        }

        JsonLikeValue alternateVal = theAlternateVal;
        if (alternateVal.isObject() == false) {
            getErrorHandler().incorrectJsonType(null, theAlternateName, JsonLikeValue.ValueType.OBJECT, null, alternateVal.getJsonType(), null);
            return;
        }

        JsonLikeObject alternate = alternateVal.getAsObject();
        for (String nextKey : alternate.keySet()) {
            JsonLikeValue nextVal = alternate.get(nextKey);
            if ("extension".equals(nextKey)) {
                boolean isModifier = false;
                JsonLikeArray array = nextVal.getAsArray();
                parseExtension(theState, array, isModifier);
            } else if ("modifierExtension".equals(nextKey)) {
                boolean isModifier = true;
                JsonLikeArray array = nextVal.getAsArray();
                parseExtension(theState, array, isModifier);
            } else if ("id".equals(nextKey)) {
                if (nextVal.isString()) {
                    theState.attributeValue("id", nextVal.getAsString());
                } else {
                    getErrorHandler().incorrectJsonType(null, "id", JsonLikeValue.ValueType.SCALAR, JsonLikeValue.ScalarType.STRING, nextVal.getJsonType(), nextVal.getDataType());
                }
            } else if ("fhir_comments".equals(nextKey)) {
                parseFhirComments(nextVal, theState);
            }
        }
    }

    private void parseChildren(JsonLikeObject theObject, ParserState<?> theState) {
        Set<String> keySet = theObject.keySet();

        int allUnderscoreNames = 0;
        int handledUnderscoreNames = 0;

        for (String nextName : keySet) {
            if ("resourceType".equals(nextName)) {
                continue;
            } else if ("extension".equals(nextName)) {
                JsonLikeArray array = grabJsonArray(theObject, nextName, "extension");
                parseExtension(theState, array, false);
                continue;
            } else if ("modifierExtension".equals(nextName)) {
                JsonLikeArray array = grabJsonArray(theObject, nextName, "modifierExtension");
                parseExtension(theState, array, true);
                continue;
            } else if (nextName.equals("fhir_comments")) {
                parseFhirComments(theObject.get(nextName), theState);
                continue;
            } else if (nextName.charAt(0) == '_') {
                allUnderscoreNames++;
                continue;
            }

            JsonLikeValue nextVal = theObject.get(nextName);
            String alternateName = '_' + nextName;
            JsonLikeValue alternateVal = theObject.get(alternateName);
            if (alternateVal != null) {
                handledUnderscoreNames++;
            }

            parseChildren(theState, nextName, nextVal, alternateVal, alternateName, false);

        }

        // if (elementId != null) {
        // IBase object = (IBase) theState.getObject();
        // if (object instanceof IIdentifiableElement) {
        // ((IIdentifiableElement) object).setElementSpecificId(elementId);
        // } else if (object instanceof IBaseResource) {
        // ((IBaseResource) object).getIdElement().setValue(elementId);
        // }
        // }

        /*
         * This happens if an element has an extension but no actual value. I.e.
         * if a resource has a "_status" element but no corresponding "status"
         * element. This could be used to handle a null value with an extension
         * for example.
         */
        if (allUnderscoreNames > handledUnderscoreNames) {
            for (String alternateName : keySet) {
                if (alternateName.startsWith("_") && alternateName.length() > 1) {
                    JsonLikeValue nextValue = theObject.get(alternateName);
                    if (nextValue != null) {
                        if (nextValue.isObject()) {
                            String nextName = alternateName.substring(1);
                            if (theObject.get(nextName) == null) {
                                theState.enteringNewElement(null, nextName);
                                parseAlternates(nextValue, theState, alternateName, alternateName);
                                theState.endingElement();
                            }
                        } else {
                            getErrorHandler().incorrectJsonType(null, alternateName, JsonLikeValue.ValueType.OBJECT, null, nextValue.getJsonType(), null);
                        }
                    }
                }
            }
        }

    }

    private void parseChildren(ParserState<?> theState, String theName, JsonLikeValue theJsonVal, JsonLikeValue theAlternateVal, String theAlternateName, boolean theInArray) {
        if (theName.equals("id")) {
            if (!theJsonVal.isString()) {
                getErrorHandler().incorrectJsonType(null, "id", JsonLikeValue.ValueType.SCALAR, JsonLikeValue.ScalarType.STRING, theJsonVal.getJsonType(), theJsonVal.getDataType());
            }
        }

        if (theJsonVal.isArray()) {
            JsonLikeArray nextArray = theJsonVal.getAsArray();

            JsonLikeValue alternateVal = theAlternateVal;
            if (alternateVal != null && alternateVal.isArray() == false) {
                getErrorHandler().incorrectJsonType(null, theAlternateName, JsonLikeValue.ValueType.ARRAY, null, alternateVal.getJsonType(), null);
                alternateVal = null;
            }

            JsonLikeArray nextAlternateArray = JsonLikeValue.asArray(alternateVal); // could be null
            for (int i = 0; i < nextArray.size(); i++) {
                JsonLikeValue nextObject = nextArray.get(i);
                JsonLikeValue nextAlternate = null;
                if (nextAlternateArray != null && nextAlternateArray.size() >= (i + 1)) {
                    nextAlternate = nextAlternateArray.get(i);
                }
                parseChildren(theState, theName, nextObject, nextAlternate, theAlternateName, true);
            }
        } else if (theJsonVal.isObject()) {
            if (!theInArray && theState.elementIsRepeating(theName)) {
                getErrorHandler().incorrectJsonType(null, theName, JsonLikeValue.ValueType.ARRAY, null, JsonLikeValue.ValueType.OBJECT, null);
            }

            theState.enteringNewElement(null, theName);
            parseAlternates(theAlternateVal, theState, theAlternateName, theAlternateName);
            JsonLikeObject nextObject = theJsonVal.getAsObject();
            boolean preResource = false;
            if (theState.isPreResource()) {
                JsonLikeValue resType = nextObject.get("resourceType");
                if (resType == null || !resType.isString()) {
                    throw new DataFormatException("Missing required element 'resourceType' from JSON resource object, unable to parse");
                }
                theState.enteringNewElement(null, resType.getAsString());
                preResource = true;
            }
            parseChildren(nextObject, theState);
            if (preResource) {
                theState.endingElement();
            }
            theState.endingElement();
        } else if (theJsonVal.isNull()) {
            theState.enteringNewElement(null, theName);
            parseAlternates(theAlternateVal, theState, theAlternateName, theAlternateName);
            theState.endingElement();
        } else {
            // must be a SCALAR
            theState.enteringNewElement(null, theName);
            String asString = theJsonVal.getAsString();
            theState.attributeValue("value", asString);
            parseAlternates(theAlternateVal, theState, theAlternateName, theAlternateName);
            theState.endingElement();
        }
    }

    private void parseExtension(ParserState<?> theState, JsonLikeArray theValues, boolean theIsModifier) {
        int allUnderscoreNames = 0;
        int handledUnderscoreNames = 0;

        for (int i = 0; i < theValues.size(); i++) {
            JsonLikeObject nextExtObj = JsonLikeValue.asObject(theValues.get(i));
            JsonLikeValue jsonElement = nextExtObj.get("url");
            String url;
            if (null == jsonElement || !(jsonElement.isScalar())) {
                String parentElementName;
                if (theIsModifier) {
                    parentElementName = "modifierExtension";
                } else {
                    parentElementName = "extension";
                }
                getErrorHandler().missingRequiredElement(new ParseLocation().setParentElementName(parentElementName), "url");
                url = null;
            } else {
                url = getExtensionUrl(jsonElement.getAsString());
            }
            theState.enteringNewElementExtension(null, url, theIsModifier, getServerBaseUrl());
            for (String next : nextExtObj.keySet()) {
                if ("url".equals(next)) {
                    continue;
                } else if ("extension".equals(next)) {
                    JsonLikeArray jsonVal = JsonLikeValue.asArray(nextExtObj.get(next));
                    parseExtension(theState, jsonVal, false);
                } else if ("modifierExtension".equals(next)) {
                    JsonLikeArray jsonVal = JsonLikeValue.asArray(nextExtObj.get(next));
                    parseExtension(theState, jsonVal, true);
                } else if (next.charAt(0) == '_') {
                    allUnderscoreNames++;
                    continue;
                } else {
                    JsonLikeValue jsonVal = nextExtObj.get(next);
                    String alternateName = '_' + next;
                    JsonLikeValue alternateVal = nextExtObj.get(alternateName);
                    if (alternateVal != null) {
                        handledUnderscoreNames++;
                    }
                    parseChildren(theState, next, jsonVal, alternateVal, alternateName, false);
                }
            }

            /*
             * This happens if an element has an extension but no actual value. I.e.
             * if a resource has a "_status" element but no corresponding "status"
             * element. This could be used to handle a null value with an extension
             * for example.
             */
            if (allUnderscoreNames > handledUnderscoreNames) {
                for (String alternateName : nextExtObj.keySet()) {
                    if (alternateName.startsWith("_") && alternateName.length() > 1) {
                        JsonLikeValue nextValue = nextExtObj.get(alternateName);
                        if (nextValue != null) {
                            if (nextValue.isObject()) {
                                String nextName = alternateName.substring(1);
                                if (nextExtObj.get(nextName) == null) {
                                    theState.enteringNewElement(null, nextName);
                                    parseAlternates(nextValue, theState, alternateName, alternateName);
                                    theState.endingElement();
                                }
                            } else {
                                getErrorHandler().incorrectJsonType(null, alternateName, JsonLikeValue.ValueType.OBJECT, null, nextValue.getJsonType(), null);
                            }
                        }
                    }
                }
            }
            theState.endingElement();
        }
    }

    private void parseFhirComments(JsonLikeValue theObject, ParserState<?> theState) {
        if (theObject.isArray()) {
            JsonLikeArray comments = theObject.getAsArray();
            for (int i = 0; i < comments.size(); i++) {
                JsonLikeValue nextComment = comments.get(i);
                if (nextComment.isString()) {
                    String commentText = nextComment.getAsString();
                    if (commentText != null) {
                        theState.commentPre(commentText);
                    }
                }
            }
        }
    }

    @Override
    public EncodingEnum getEncoding() {
        return EncodingEnum.JSON;
    }

    @Override
    public IParser setPrettyPrint(boolean b) {
        prettyPrint = b;
        return this;
    }

    private void writeCommentsPreAndPost(IBase theNextValue, JsonLikeWriter theEventWriter) throws IOException {
        if (theNextValue.hasFormatComment()) {
            beginArray(theEventWriter, "fhir_comments");
            List<String> pre = theNextValue.getFormatCommentsPre();
            if (pre.isEmpty() == false) {
                for (String next : pre) {
                    theEventWriter.write(next);
                }
            }
            List<String> post = theNextValue.getFormatCommentsPost();
            if (post.isEmpty() == false) {
                for (String next : post) {
                    theEventWriter.write(next);
                }
            }
            theEventWriter.endArray();
        }
    }

    private void extractUndeclaredExtensions(IBase theElement, List<HeldExtension> extensions, List<HeldExtension> modifierExtensions, CompositeChildElement theChildElem,
                                             CompositeChildElement theParent, EncodeContext theEncodeContext, boolean theContainedResource) {
        if (theElement instanceof ISupportsUndeclaredExtensions) {
            ISupportsUndeclaredExtensions element = (ISupportsUndeclaredExtensions) theElement;
            List<ExtensionDt> ext = element.getUndeclaredExtensions();
            for (ExtensionDt next : ext) {
                if (next == null || next.isEmpty()) {
                    continue;
                }
                extensions.add(new HeldExtension(next, false, theChildElem, theParent));
            }

            ext = element.getUndeclaredModifierExtensions();
            for (ExtensionDt next : ext) {
                if (next == null || next.isEmpty()) {
                    continue;
                }
                modifierExtensions.add(new HeldExtension(next, true, theChildElem, theParent));
            }
        } else {
            if (theElement instanceof IBaseHasExtensions) {
                IBaseHasExtensions element = (IBaseHasExtensions) theElement;
                List<? extends IBaseExtension<?, ?>> ext = element.getExtension();
                Boolean encodeExtension = null;
                for (IBaseExtension<?, ?> next : ext) {
                    if (next == null || (ElementUtil.isEmpty(next.getValue()) && next.getExtension().isEmpty())) {
                        continue;
                    }

                    // Make sure we respect _elements and _summary
                    if (encodeExtension == null) {
                        encodeExtension = isEncodeExtension(theParent, theEncodeContext, theContainedResource, element);
                    }
                    if (encodeExtension) {
                        HeldExtension extension = new HeldExtension(next, false, theChildElem, theParent);
                        extensions.add(extension);
                    }

                }
            }
            if (theElement instanceof IBaseHasModifierExtensions) {
                IBaseHasModifierExtensions element = (IBaseHasModifierExtensions) theElement;
                List<? extends IBaseExtension<?, ?>> ext = element.getModifierExtension();
                for (IBaseExtension<?, ?> next : ext) {
                    if (next == null || next.isEmpty()) {
                        continue;
                    }

                    HeldExtension extension = new HeldExtension(next, true, theChildElem, theParent);
                    modifierExtensions.add(extension);
                }
            }
        }
    }

    private void write(JsonLikeWriter theEventWriter, String theChildName, Boolean theValue) throws IOException {
        if (theValue != null) {
            theEventWriter.write(theChildName, theValue.booleanValue());
        }
    }

    private void write(JsonLikeWriter theEventWriter, String theChildName, BigDecimal theDecimalValue) throws IOException {
        theEventWriter.write(theChildName, theDecimalValue);
    }

    private void write(JsonLikeWriter theEventWriter, String theChildName, Integer theValue) throws IOException {
        theEventWriter.write(theChildName, theValue);
    }

    private void write(JsonLikeWriter eventWriter, String childName, Long value) throws IOException {
        eventWriter.write(childName, value);
    }

    private void encodeChildElementToStreamWriter(RuntimeResourceDefinition theResDef, IBaseResource theResource, JsonLikeWriter theEventWriter, IBase theNextValue,
                                                  BaseRuntimeElementDefinition<?> theChildDef, String theChildName, boolean theContainedResource, CompositeChildElement theChildElem,
                                                  boolean theForceEmpty, EncodeContext theEncodeContext) throws IOException {

        switch (theChildDef.getChildType()) {
            case ID_DATATYPE: {
                IIdType value = (IIdType) theNextValue;
                String encodedValue = "id".equals(theChildName) ? value.getIdPart() : value.getValue();
                if (isBlank(encodedValue)) {
                    break;
                }
                if (theChildName != null) {
                    write(theEventWriter, theChildName, encodedValue);
                } else {
                    theEventWriter.write(encodedValue);
                }
                break;
            }
            case PRIMITIVE_DATATYPE: {
                final IPrimitiveType<?> value = (IPrimitiveType<?>) theNextValue;
                final String valueStr = value.getValueAsString();
                if (isBlank(valueStr)) {
                    if (theForceEmpty) {
                        theEventWriter.writeNull();
                    }
                    break;
                }

                // check for the common case first - String value types
                Object valueObj = value.getValue();
                if (valueObj instanceof String) {
                    if (theChildName != null) {
                        theEventWriter.write(theChildName, valueStr);
                    } else {
                        theEventWriter.write(valueStr);
                    }
                    break;
                } else if (valueObj instanceof Long) {
                    if (theChildName != null) {
                        theEventWriter.write(theChildName, (long) valueObj);
                    } else {
                        theEventWriter.write((long) valueObj);
                    }
                    break;
                }

                if (value instanceof IBaseIntegerDatatype) {
                    if (theChildName != null) {
                        write(theEventWriter, theChildName, ((IBaseIntegerDatatype) value).getValue());
                    } else {
                        theEventWriter.write(((IBaseIntegerDatatype) value).getValue());
                    }
                } else if (value instanceof IBaseDecimalDatatype) {
                    BigDecimal decimalValue = ((IBaseDecimalDatatype) value).getValue();
                    decimalValue = new BigDecimal(decimalValue.toString()) {
                        private static final long serialVersionUID = 1L;

                        @Override
                        public String toString() {
                            return value.getValueAsString();
                        }
                    };
                    if (theChildName != null) {
                        write(theEventWriter, theChildName, decimalValue);
                    } else {
                        theEventWriter.write(decimalValue);
                    }
                } else if (value instanceof IBaseBooleanDatatype) {
                    if (theChildName != null) {
                        write(theEventWriter, theChildName, ((IBaseBooleanDatatype) value).getValue());
                    } else {
                        Boolean booleanValue = ((IBaseBooleanDatatype) value).getValue();
                        if (booleanValue != null) {
                            theEventWriter.write(booleanValue.booleanValue());
                        }
                    }
                }
                else if (value instanceof DateTimeType) {
                    // Input: a dateTime, The format is YYYY, YYYY-MM, YYYY-MM-DD or YYYY-MM-DDThh:mm:ss+zz:zz

                    // Timestamp (microsecond precision)
                    // Return: Avro long, where the long stores the number of microseconds from the unix epoch, 1 January 1970 00:00:00.000000 UTC.
                    try {
                        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
                        Date date = parser.parse(value.getValueAsString());
                        Long epochSecond = date.toInstant().getEpochSecond();
                        if (theChildName != null) {
                            write(theEventWriter, theChildName, epochSecond);
                        } else {
                            theEventWriter.write(epochSecond);
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
                else if (value instanceof DateType) {
                    // Input; a date, (e.g. just year or year + month)

                    // Date
                    // Return; Avro int, where the int stores the number of days from the unix epoch, 1 January 1970 (ISO calendar).
                    try {
                        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd");
                        LocalDate date = parser.parse(value.getValueAsString()).toInstant()
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate();
                        Long epochDays = date.toEpochDay();
                        if (theChildName != null) {
                            write(theEventWriter, theChildName, epochDays.intValue());
                        } else {
                            theEventWriter.write(String.valueOf(epochDays));
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                } else if (value instanceof TimeType) {
                    // Input; A time during the day.

                    // Time (millisecond precision)
                    // Return; Avro int, where the int stores the number of milliseconds after midnight, 00:00:00.000.
                } else {
                    if (theChildName != null) {
                        write(theEventWriter, theChildName, valueStr);
                    } else {
                        theEventWriter.write(valueStr);
                    }
                }
                break;
            }
            case RESOURCE_BLOCK:
            case COMPOSITE_DATATYPE: {
                if (theChildName != null) {
                    theEventWriter.beginObject(theChildName);
                } else {
                    theEventWriter.beginObject();
                }
                encodeCompositeElementToStreamWriter(theResDef, theResource, theNextValue, theEventWriter, theContainedResource, theChildElem, theEncodeContext);
                theEventWriter.endObject();
                break;
            }
            case CONTAINED_RESOURCE_LIST:
            case CONTAINED_RESOURCES: {
                List<IBaseResource> containedResources = getContainedResources().getContainedResources();
                if (containedResources.size() > 0) {
                    beginArray(theEventWriter, theChildName);

                    for (IBaseResource next : containedResources) {
                        IIdType resourceId = getContainedResources().getResourceId(next);
                        String value = resourceId.getValue();
                        encodeResourceToJsonStreamWriter(theResDef, next, theEventWriter, null, true, fixContainedResourceId(value), theEncodeContext);
                    }

                    theEventWriter.endArray();
                }
                break;
            }
            case PRIMITIVE_XHTML_HL7ORG:
            case PRIMITIVE_XHTML: {
                if (!isSuppressNarratives()) {
                    IPrimitiveType<?> dt = (IPrimitiveType<?>) theNextValue;
                    if (theChildName != null) {
                        write(theEventWriter, theChildName, dt.getValueAsString());
                    } else {
                        theEventWriter.write(dt.getValueAsString());
                    }
                } else {
                    if (theChildName != null) {
                        // do nothing
                    } else {
                        theEventWriter.writeNull();
                    }
                }
                break;
            }
            case RESOURCE:
                IBaseResource resource = (IBaseResource) theNextValue;
                RuntimeResourceDefinition def = getContext().getResourceDefinition(resource);

                theEncodeContext.pushPath(def.getName(), true);
                encodeResourceToJsonStreamWriter(def, resource, theEventWriter, theChildName, theContainedResource, theEncodeContext);
                theEncodeContext.popPath();

                break;
            case UNDECL_EXT:
            default:
                throw new IllegalStateException("Should not have this state here: " + theChildDef.getChildType().name());
        }
    }

    private boolean isEncodeExtension(CompositeChildElement theParent, EncodeContext theEncodeContext, boolean theContainedResource, IBase theElement) {
        BaseRuntimeElementDefinition<?> runtimeElementDefinition = getContext().getElementDefinition(theElement.getClass());
        boolean retVal = true;
        if (runtimeElementDefinition instanceof BaseRuntimeElementCompositeDefinition) {
            BaseRuntimeElementCompositeDefinition definition = (BaseRuntimeElementCompositeDefinition) runtimeElementDefinition;
            BaseRuntimeChildDefinition childDef = definition.getChildByName("extension");
            CompositeChildElement c = new CompositeChildElement(theParent, childDef, theEncodeContext);
            retVal = c.shouldBeEncoded(theContainedResource);
        }
        return retVal;
    }

    private void extractAndWriteExtensionsAsDirectChild(IBase theElement, JsonLikeWriter theEventWriter, BaseRuntimeElementDefinition<?> theElementDef, RuntimeResourceDefinition theResDef,
                                                        IBaseResource theResource, CompositeChildElement theChildElem, CompositeChildElement theParent, EncodeContext theEncodeContext, boolean theContainedResource) throws IOException {
        List<HeldExtension> extensions = new ArrayList<>(0);
        List<HeldExtension> modifierExtensions = new ArrayList<>(0);

        // Undeclared extensions
        extractUndeclaredExtensions(theElement, extensions, modifierExtensions, theChildElem, theParent, theEncodeContext, theContainedResource);

        // Declared extensions
        if (theElementDef != null) {
            extractDeclaredExtensions(theElement, theElementDef, extensions, modifierExtensions, theChildElem);
        }

        // Write the extensions
        writeExtensionsAsDirectChild(theResource, theEventWriter, theResDef, extensions, modifierExtensions, theEncodeContext, theContainedResource);
    }

    private void extractDeclaredExtensions(IBase theResource, BaseRuntimeElementDefinition<?> resDef, List<HeldExtension> extensions, List<HeldExtension> modifierExtensions,
                                           CompositeChildElement theChildElem) {
        for (RuntimeChildDeclaredExtensionDefinition nextDef : resDef.getExtensionsNonModifier()) {
            for (IBase nextValue : nextDef.getAccessor().getValues(theResource)) {
                if (nextValue != null) {
                    if (nextValue.isEmpty()) {
                        continue;
                    }
                    extensions.add(new HeldExtension(nextDef, nextValue, theChildElem));
                }
            }
        }
        for (RuntimeChildDeclaredExtensionDefinition nextDef : resDef.getExtensionsModifier()) {
            for (IBase nextValue : nextDef.getAccessor().getValues(theResource)) {
                if (nextValue != null) {
                    if (nextValue.isEmpty()) {
                        continue;
                    }
                    modifierExtensions.add(new HeldExtension(nextDef, nextValue, theChildElem));
                }
            }
        }
    }

    private void writeExtensionsAsDirectChild(IBaseResource theResource, JsonLikeWriter theEventWriter, RuntimeResourceDefinition resDef, List<HeldExtension> extensions,
                                              List<HeldExtension> modifierExtensions, EncodeContext theEncodeContext, boolean theContainedResource) throws IOException {
        // Write Extensions
        if (extensions.isEmpty() == false) {
            theEncodeContext.pushPath("extension", false);
            beginArray(theEventWriter, "extension");
            for (HeldExtension next : extensions) {
                next.write(resDef, theResource, theEventWriter, theEncodeContext, theContainedResource);
            }
            theEventWriter.endArray();
            theEncodeContext.popPath();
        }

        // Write ModifierExtensions
        if (modifierExtensions.isEmpty() == false) {
            theEncodeContext.pushPath("modifierExtension", false);
            beginArray(theEventWriter, "modifierExtension");
            for (HeldExtension next : modifierExtensions) {
                next.write(resDef, theResource, theEventWriter, theEncodeContext, theContainedResource);
            }
            theEventWriter.endArray();
            theEncodeContext.popPath();
        }
    }

    private class HeldExtension implements Comparable<HeldExtension> {

        private CompositeChildElement myChildElem;
        private RuntimeChildDeclaredExtensionDefinition myDef;
        private boolean myModifier;
        private IBaseExtension<?, ?> myUndeclaredExtension;
        private IBase myValue;
        private CompositeChildElement myParent;

        public HeldExtension(IBaseExtension<?, ?> theUndeclaredExtension, boolean theModifier, CompositeChildElement theChildElem, CompositeChildElement theParent) {
            assert theUndeclaredExtension != null;
            myUndeclaredExtension = theUndeclaredExtension;
            myModifier = theModifier;
            myChildElem = theChildElem;
            myParent = theParent;
        }

        public HeldExtension(RuntimeChildDeclaredExtensionDefinition theDef, IBase theValue, CompositeChildElement theChildElem) {
            assert theDef != null;
            assert theValue != null;
            myDef = theDef;
            myValue = theValue;
            myChildElem = theChildElem;
        }

        @Override
        public int compareTo(HeldExtension theArg0) {
            String url1 = myDef != null ? myDef.getExtensionUrl() : myUndeclaredExtension.getUrl();
            String url2 = theArg0.myDef != null ? theArg0.myDef.getExtensionUrl() : theArg0.myUndeclaredExtension.getUrl();
            url1 = defaultString(getExtensionUrl(url1));
            url2 = defaultString(getExtensionUrl(url2));
            return url1.compareTo(url2);
        }

        private void managePrimitiveExtension(final IBase theValue, final RuntimeResourceDefinition theResDef, final IBaseResource theResource, final JsonLikeWriter theEventWriter, final BaseRuntimeElementDefinition<?> def, final String childName, EncodeContext theEncodeContext, boolean theContainedResource) throws IOException {
            if (def.getChildType().equals(ID_DATATYPE) || def.getChildType().equals(PRIMITIVE_DATATYPE)) {
                final List<HeldExtension> extensions = new ArrayList<HeldExtension>(0);
                final List<HeldExtension> modifierExtensions = new ArrayList<HeldExtension>(0);
                // Undeclared extensions
                extractUndeclaredExtensions(theValue, extensions, modifierExtensions, myParent, null, theEncodeContext, theContainedResource);
                // Declared extensions
                if (def != null) {
                    extractDeclaredExtensions(theValue, def, extensions, modifierExtensions, myParent);
                }
                boolean haveContent = false;
                if (!extensions.isEmpty() || !modifierExtensions.isEmpty()) {
                    haveContent = true;
                }
                if (haveContent) {
                    beginObject(theEventWriter, '_' + childName);
                    writeExtensionsAsDirectChild(theResource, theEventWriter, theResDef, extensions, modifierExtensions, theEncodeContext, theContainedResource);
                    theEventWriter.endObject();
                }
            }
        }

        public void write(RuntimeResourceDefinition theResDef, IBaseResource theResource, JsonLikeWriter theEventWriter, EncodeContext theEncodeContext, boolean theContainedResource) throws IOException {
            if (myUndeclaredExtension != null) {
                writeUndeclaredExtension(theResDef, theResource, theEventWriter, myUndeclaredExtension, theEncodeContext, theContainedResource);
            } else {
                theEventWriter.beginObject();

                writeCommentsPreAndPost(myValue, theEventWriter);

                ExtendedJsonParser.write(theEventWriter, "url", getExtensionUrl(myDef.getExtensionUrl()));

                /*
                 * This makes sure that even if the extension contains a reference to a contained
                 * resource which has a HAPI-assigned ID we'll still encode that ID.
                 *
                 * See #327
                 */
                List<? extends IBase> preProcessedValue = preProcessValues(myDef, theResource, Collections.singletonList(myValue), myChildElem, theEncodeContext);

                myValue = preProcessedValue.get(0);

                BaseRuntimeElementDefinition<?> def = myDef.getChildElementDefinitionByDatatype(myValue.getClass());
                if (def.getChildType() == BaseRuntimeElementDefinition.ChildTypeEnum.RESOURCE_BLOCK) {
                    extractAndWriteExtensionsAsDirectChild(myValue, theEventWriter, def, theResDef, theResource, myChildElem, null, theEncodeContext, theContainedResource);
                } else {
                    String childName = myDef.getChildNameByDatatype(myValue.getClass());
                    encodeChildElementToStreamWriter(theResDef, theResource, theEventWriter, myValue, def, childName, false, myParent, false, theEncodeContext);
                    managePrimitiveExtension(myValue, theResDef, theResource, theEventWriter, def, childName, theEncodeContext, theContainedResource);
                }

                theEventWriter.endObject();
            }
        }

        private void writeUndeclaredExtension(RuntimeResourceDefinition theResDef, IBaseResource theResource, JsonLikeWriter theEventWriter, IBaseExtension<?, ?> ext, EncodeContext theEncodeContext, boolean theContainedResource) throws IOException {
            IBase value = ext.getValue();
            final String extensionUrl = getExtensionUrl(ext.getUrl());

            theEventWriter.beginObject();

            writeCommentsPreAndPost(myUndeclaredExtension, theEventWriter);

            String elementId = getCompositeElementId(ext);
            if (isNotBlank(elementId)) {
                ExtendedJsonParser.write(theEventWriter, "id", getCompositeElementId(ext));
            }

            if (isBlank(extensionUrl)) {
                ParseLocation loc = new ParseLocation(theEncodeContext.toString());
                getErrorHandler().missingRequiredElement(loc, "url");
            }

            ExtendedJsonParser.write(theEventWriter, "url", extensionUrl);

            boolean noValue = value == null || value.isEmpty();
            if (noValue && ext.getExtension().isEmpty()) {

                ParseLocation loc = new ParseLocation(theEncodeContext.toString());
                getErrorHandler().missingRequiredElement(loc, "value");
            } else {

                if (!noValue && !ext.getExtension().isEmpty()) {
                    ParseLocation loc = new ParseLocation(theEncodeContext.toString());
                    getErrorHandler().extensionContainsValueAndNestedExtensions(loc);
                }

                // Write child extensions
                if (!ext.getExtension().isEmpty()) {

                    if (myModifier) {
                        beginArray(theEventWriter, "modifierExtension");
                    } else {
                        beginArray(theEventWriter, "extension");
                    }

                    for (Object next : ext.getExtension()) {
                        writeUndeclaredExtension(theResDef, theResource, theEventWriter, (IBaseExtension<?, ?>) next, theEncodeContext, theContainedResource);
                    }
                    theEventWriter.endArray();

                }

                // Write value
                if (!noValue) {
                    theEncodeContext.pushPath("value", false);

                    /*
                     * Pre-process value - This is called in case the value is a reference
                     * since we might modify the text
                     */
                    value = preProcessValues(myDef, theResource, Collections.singletonList(value), myChildElem, theEncodeContext).get(0);

                    RuntimeChildUndeclaredExtensionDefinition extDef = getContext().getRuntimeChildUndeclaredExtensionDefinition();
                    String childName = extDef.getChildNameByDatatype(value.getClass());
                    if (childName == null) {
                        childName = "value" + WordUtils.capitalize(getContext().getElementDefinition(value.getClass()).getName());
                    }
                    BaseRuntimeElementDefinition<?> childDef = extDef.getChildElementDefinitionByDatatype(value.getClass());
                    if (childDef == null) {
                        throw new ConfigurationException("Unable to encode extension, unrecognized child element type: " + value.getClass().getCanonicalName());
                    }
                    encodeChildElementToStreamWriter(theResDef, theResource, theEventWriter, value, childDef, childName, false, myParent, false, theEncodeContext);
                    managePrimitiveExtension(value, theResDef, theResource, theEventWriter, childDef, childName, theEncodeContext, theContainedResource);

                    theEncodeContext.popPath();
                }
            }

            theEventWriter.endObject();
        }
    }

    private static void write(JsonLikeWriter theWriter, String theName, String theValue) throws IOException {
        theWriter.write(theName, theValue);
    }

    static class ChildNameAndDef {

        private final BaseRuntimeElementDefinition<?> myChildDef;
        private final String myChildName;

        public ChildNameAndDef(String theChildName, BaseRuntimeElementDefinition<?> theChildDef) {
            myChildName = theChildName;
            myChildDef = theChildDef;
        }

        public BaseRuntimeElementDefinition<?> getChildDef() {
            return myChildDef;
        }

        public String getChildName() {
            return myChildName;
        }

    }

    FhirTerser.ContainedResources getContainedResources() {
        return containedResources;
    }

    void setContainedResources(FhirTerser.ContainedResources theContainedResources) {
        containedResources = theContainedResources;
    }

    public static class ParseLocation implements IParserErrorHandler.IParseLocation {

        private String myParentElementName;

        ParseLocation() {
            super();
        }

        ParseLocation(String theParentElementName) {
            setParentElementName(theParentElementName);
        }

        @Override
        public String getParentElementName() {
            return myParentElementName;
        }

        ParseLocation setParentElementName(String theParentElementName) {
            myParentElementName = theParentElementName;
            return this;
        }

        @Override
        public String toString() {
            return "[element=\"" + defaultString(myParentElementName) + "\"]";
        }

        static ParseLocation fromElementName(String theChildName) {
            return new ParseLocation(theChildName);
        }
    }

    static class ParserState<T> {

        private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(ParserState.class);
        private final FhirContext myContext;
        private final IParserErrorHandler myErrorHandler;
        private final boolean myJsonMode;
        private final IParser myParser;
        private List<String> myComments = new ArrayList<String>(2);
        private T myObject;
        private IBase myPreviousElement;
        private BaseState myState;
        private List<IBaseResource> myGlobalResources = new ArrayList<>();
        private List<IBaseReference> myGlobalReferences = new ArrayList<>();

        private ParserState(IParser theParser, FhirContext theContext, boolean theJsonMode, IParserErrorHandler theErrorHandler) {
            myParser = theParser;
            myContext = theContext;
            myJsonMode = theJsonMode;
            myErrorHandler = theErrorHandler;
        }

        public void attributeValue(String theName, String theValue) throws DataFormatException {
            myState.attributeValue(theName, theValue);
        }

        public void commentPost(String theCommentText) {
            if (myPreviousElement != null) {
                myPreviousElement.getFormatCommentsPost().add(theCommentText);
            }
        }

        public void commentPre(String theCommentText) {
            if (myState.getCurrentElement() != null) {
                IBase element = myState.getCurrentElement();
                element.getFormatCommentsPre().add(theCommentText);
            }
        }

        boolean elementIsRepeating(String theChildName) {
            return myState.elementIsRepeating(theChildName);
        }

        void endingElement() throws DataFormatException {
            myState.endingElement();
        }

        void enteringNewElement(String theNamespaceUri, String theName) throws DataFormatException {
            myState.enteringNewElement(theNamespaceUri, theName);
        }

        void enteringNewElementExtension(StartElement theElem, String theUrlAttr, boolean theIsModifier, final String baseServerUrl) {
            myState.enteringNewElementExtension(theElem, theUrlAttr, theIsModifier, baseServerUrl);
        }

        public T getObject() {
            return myObject;
        }

        boolean isPreResource() {
            return myState.isPreResource();
        }

        private Object newContainedDt(IResource theTarget) {
            return ReflectionUtil.newInstance(theTarget.getStructureFhirVersionEnum().getVersionImplementation().getContainedType());
        }

        @SuppressWarnings("unchecked")
        private void pop() {
            myPreviousElement = myState.getCurrentElement();
            if (myState.myStack != null) {
                myState = myState.myStack;
                myState.wereBack();
            } else {
                myObject = (T) myState.getCurrentElement();
                myState = null;
            }
        }

        private void push(BaseState theState) {
            theState.setStack(myState);
            myState = theState;
            if (myComments.isEmpty() == false) {
                if (myState.getCurrentElement() != null) {
                    myState.getCurrentElement().getFormatCommentsPre().addAll(myComments);
                    myComments.clear();
                }
            }
        }

        public void string(String theData) {
            myState.string(theData);
        }

        /**
         * Invoked after any new XML event is individually processed, containing a copy of the XML event. This is basically
         * intended for embedded XHTML content
         */
        void xmlEvent(XMLEvent theNextEvent) {
            if (myState != null) {
                myState.xmlEvent(theNextEvent);
            }
        }

        public IBase newInstance(RuntimeChildDeclaredExtensionDefinition theDefinition) {
            return theDefinition.newInstance();
        }

        public ICompositeType newCompositeInstance(PreResourceState thePreResourceState, BaseRuntimeChildDefinition theChild, BaseRuntimeElementCompositeDefinition<?> theCompositeTarget) {
            ICompositeType retVal = (ICompositeType) theCompositeTarget.newInstance(theChild.getInstanceConstructorArguments());
            if (retVal instanceof IBaseReference) {
                IBaseReference ref = (IBaseReference) retVal;
                myGlobalReferences.add(ref);
                thePreResourceState.getLocalReferences().add(ref);
            }
            return retVal;
        }

        public ICompositeType newCompositeTypeInstance(PreResourceState thePreResourceState, BaseRuntimeElementCompositeDefinition<?> theCompositeTarget) {
            ICompositeType retVal = (ICompositeType) theCompositeTarget.newInstance();
            if (retVal instanceof IBaseReference) {
                IBaseReference ref = (IBaseReference) retVal;
                myGlobalReferences.add(ref);
                thePreResourceState.getLocalReferences().add(ref);
            }
            return retVal;
        }

        public IPrimitiveType<?> newPrimitiveInstance(RuntimeChildDeclaredExtensionDefinition theDefinition, RuntimePrimitiveDatatypeDefinition thePrimitiveTarget) {
            return thePrimitiveTarget.newInstance(theDefinition.getInstanceConstructorArguments());
        }

        public IPrimitiveType<?> getPrimitiveInstance(BaseRuntimeChildDefinition theChild, RuntimePrimitiveDatatypeDefinition thePrimitiveTarget, String theChildName) {
            return thePrimitiveTarget.newInstance(theChild.getInstanceConstructorArguments());
        }

        public IBaseXhtml newInstance(RuntimePrimitiveDatatypeXhtmlHl7OrgDefinition theXhtmlTarget) {
            return theXhtmlTarget.newInstance();
        }

        public XhtmlDt newInstance(RuntimePrimitiveDatatypeNarrativeDefinition theXhtmlTarget) {
            return theXhtmlTarget.newInstance();
        }

        public IPrimitiveType<?> newInstance(RuntimePrimitiveDatatypeDefinition thePrimitiveTarget) {
            return thePrimitiveTarget.newInstance();
        }

        public IBaseResource newInstance(RuntimeResourceDefinition theDef) {
            IBaseResource retVal = theDef.newInstance();
            myGlobalResources.add(retVal);
            return retVal;
        }

        public IBase newInstance(RuntimeResourceBlockDefinition theBlockTarget) {
            return theBlockTarget.newInstance();
        }

        private abstract class BaseState {

            private PreResourceState myPreResourceState;
            private BaseState myStack;

            BaseState(PreResourceState thePreResourceState) {
                super();
                myPreResourceState = thePreResourceState;
            }

            /**
             * @param theValue The attribute value
             */
            public void attributeValue(String theName, String theValue) throws DataFormatException {
                myErrorHandler.unknownAttribute(null, theName);
            }

            public boolean elementIsRepeating(String theChildName) {
                return false;
            }

            public void endingElement() throws DataFormatException {
                // ignore by default
            }

            /**
             * @param theNamespaceUri The XML namespace (if XML) or null
             */
            public void enteringNewElement(String theNamespaceUri, String theLocalPart) throws DataFormatException {
                myErrorHandler.unknownElement(null, theLocalPart);
            }

            /**
             * Default implementation just handles undeclared extensions
             */
            @SuppressWarnings("unused")
            public void enteringNewElementExtension(StartElement theElement, String theUrlAttr, boolean theIsModifier, final String baseServerUrl) {
                if (myPreResourceState != null && getCurrentElement() instanceof ISupportsUndeclaredExtensions) {
                    ExtensionDt newExtension = new ExtensionDt(theIsModifier);
                    newExtension.setUrl(theUrlAttr);
                    ISupportsUndeclaredExtensions elem = (ISupportsUndeclaredExtensions) getCurrentElement();
                    elem.addUndeclaredExtension(newExtension);
                    ExtensionState newState = new ExtensionState(myPreResourceState, newExtension);
                    push(newState);
                } else {
                    if (theIsModifier == false) {
                        if (getCurrentElement() instanceof IBaseHasExtensions) {
                            IBaseExtension<?, ?> ext = ((IBaseHasExtensions) getCurrentElement()).addExtension();
                            ext.setUrl(theUrlAttr);
                            ExtensionState newState = new ExtensionState(myPreResourceState, ext);
                            push(newState);
                        } else {
                            logAndSwallowUnexpectedElement("extension");
                        }
                    } else {
                        if (getCurrentElement() instanceof IBaseHasModifierExtensions) {
                            IBaseExtension<?, ?> ext = ((IBaseHasModifierExtensions) getCurrentElement()).addModifierExtension();
                            ext.setUrl(theUrlAttr);
                            ExtensionState newState = new ExtensionState(myPreResourceState, ext);
                            push(newState);
                        } else {
                            logAndSwallowUnexpectedElement("modifierExtension");
                        }
                    }
                }
            }

            protected IBase getCurrentElement() {
                return null;
            }

            PreResourceState getPreResourceState() {
                return myPreResourceState;
            }

            public boolean isPreResource() {
                return false;
            }

            void logAndSwallowUnexpectedElement(String theLocalPart) {
                myErrorHandler.unknownElement(null, theLocalPart);
                push(new SwallowChildrenWholeState(getPreResourceState()));
            }

            public void setStack(BaseState theState) {
                myStack = theState;
            }

            /**
             * @param theData The string value
             */
            public void string(String theData) {
                // ignore by default
            }

            public void wereBack() {
                // allow an implementor to override
            }

            /**
             * @param theNextEvent The XML event
             */
            public void xmlEvent(XMLEvent theNextEvent) {
                // ignore
            }

        }

        private class ContainedResourcesStateHapi extends PreResourceState {

            public ContainedResourcesStateHapi(PreResourceState thePreResourcesState) {
                super(thePreResourcesState, thePreResourcesState.myInstance.getStructureFhirVersionEnum());
            }

            @Override
            public void endingElement() throws DataFormatException {
                pop();
            }

            @Override
            protected void populateTarget() {
                // nothing
            }

            @Override
            public void wereBack() {
                super.wereBack();

                IResource res = (IResource) getCurrentElement();
                assert res != null;
                if (res.getId() == null || res.getId().isEmpty()) {
                    // If there is no ID, we don't keep the resource because it's useless (contained resources
                    // need an ID to be referred to)
                    myErrorHandler.containedResourceWithNoId(null);
                } else {
                    if (!res.getId().isLocal()) {
                        res.setId(new IdDt('#' + res.getId().getIdPart()));
                    }
                    getPreResourceState().getContainedResources().put(res.getId().getValueAsString(), res);
                }
                IResource preResCurrentElement = (IResource) getPreResourceState().getCurrentElement();

                @SuppressWarnings("unchecked")
                List<IResource> containedResources = (List<IResource>) preResCurrentElement.getContained().getContainedResources();
                containedResources.add(res);
            }

        }

        private class ContainedResourcesStateHl7Org extends PreResourceState {

            public ContainedResourcesStateHl7Org(PreResourceState thePreResourcesState) {
                super(thePreResourcesState, thePreResourcesState.myParentVersion);
            }

            @Override
            public void endingElement() throws DataFormatException {
                pop();
            }

            @Override
            protected void populateTarget() {
                // nothing
            }

            @Override
            public void wereBack() {
                super.wereBack();

                IBaseResource res = getCurrentElement();
                assert res != null;
                if (res.getIdElement() == null || res.getIdElement().isEmpty()) {
                    // If there is no ID, we don't keep the resource because it's useless (contained resources
                    // need an ID to be referred to)
                    myErrorHandler.containedResourceWithNoId(null);
                } else {
                    res.getIdElement().setValue('#' + res.getIdElement().getIdPart());
                    getPreResourceState().getContainedResources().put(res.getIdElement().getValue(), res);
                }

                IBaseResource preResCurrentElement = getPreResourceState().getCurrentElement();
                RuntimeResourceDefinition def = myContext.getResourceDefinition(preResCurrentElement);
                def.getChildByName("contained").getMutator().addValue(preResCurrentElement, res);
            }

        }

        @SuppressWarnings("EnumSwitchStatementWhichMissesCases")
        private class DeclaredExtensionState extends BaseState {

            private IBase myChildInstance;
            private RuntimeChildDeclaredExtensionDefinition myDefinition;
            private IBase myParentInstance;
            private PreResourceState myPreResourceState;

            public DeclaredExtensionState(PreResourceState thePreResourceState, RuntimeChildDeclaredExtensionDefinition theDefinition, IBase theParentInstance) {
                super(thePreResourceState);
                myPreResourceState = thePreResourceState;
                myDefinition = theDefinition;
                myParentInstance = theParentInstance;
            }

            @Override
            public void attributeValue(String theName, String theValue) throws DataFormatException {
                if (theName.equals("url")) {
                    // This can be ignored
                    return;
                }
                super.attributeValue(theName, theValue);
            }

            @Override
            public void endingElement() throws DataFormatException {
                pop();
            }

            @Override
            public void enteringNewElement(String theNamespaceUri, String theLocalPart) throws DataFormatException {
                BaseRuntimeElementDefinition<?> target = myDefinition.getChildByName(theLocalPart);
                if (target == null) {
                    myErrorHandler.unknownElement(null, theLocalPart);
                    push(new SwallowChildrenWholeState(getPreResourceState()));
                    return;
                }

                switch (target.getChildType()) {
                    case COMPOSITE_DATATYPE: {
                        BaseRuntimeElementCompositeDefinition<?> compositeTarget = (BaseRuntimeElementCompositeDefinition<?>) target;
                        ICompositeType newChildInstance = newCompositeInstance(getPreResourceState(), myDefinition, compositeTarget);
                        myDefinition.getMutator().addValue(myParentInstance, newChildInstance);
                        ElementCompositeState newState = new ElementCompositeState(myPreResourceState, theLocalPart, compositeTarget, newChildInstance);
                        push(newState);
                        return;
                    }
                    case ID_DATATYPE:
                    case PRIMITIVE_DATATYPE: {
                        RuntimePrimitiveDatatypeDefinition primitiveTarget = (RuntimePrimitiveDatatypeDefinition) target;
                        IPrimitiveType<?> newChildInstance = newPrimitiveInstance(myDefinition, primitiveTarget);
                        myDefinition.getMutator().addValue(myParentInstance, newChildInstance);
                        PrimitiveState newState = new PrimitiveState(getPreResourceState(), newChildInstance, theLocalPart, primitiveTarget.getName());
                        push(newState);
                        return;
                    }
                    case PRIMITIVE_XHTML:
                    case RESOURCE:
                    case RESOURCE_BLOCK:
                    case UNDECL_EXT:
                    case EXTENSION_DECLARED:
                    default:
                        break;
                }
            }

            @Override
            public void enteringNewElementExtension(StartElement theElement, String theUrlAttr, boolean theIsModifier, final String baseServerUrl) {
                RuntimeChildDeclaredExtensionDefinition declaredExtension = myDefinition.getChildExtensionForUrl(theUrlAttr);
                if (declaredExtension != null) {
                    if (myChildInstance == null) {
                        myChildInstance = newInstance(myDefinition);
                        myDefinition.getMutator().addValue(myParentInstance, myChildInstance);
                    }
                    BaseState newState = new DeclaredExtensionState(getPreResourceState(), declaredExtension, myChildInstance);
                    push(newState);
                } else {
                    super.enteringNewElementExtension(theElement, theUrlAttr, theIsModifier, baseServerUrl);
                }
            }


            @Override
            protected IBase getCurrentElement() {
                return myParentInstance;
            }

        }

        private class ElementCompositeState extends BaseState {

            private final BaseRuntimeElementCompositeDefinition<?> myDefinition;
            private final IBase myInstance;
            private final Set<String> myParsedNonRepeatableNames = new HashSet<>();
            private final String myElementName;

            ElementCompositeState(PreResourceState thePreResourceState, String theElementName, BaseRuntimeElementCompositeDefinition<?> theDef, IBase theInstance) {
                super(thePreResourceState);
                myDefinition = theDef;
                myInstance = theInstance;
                myElementName = theElementName;
            }

            @Override
            public void attributeValue(String theName, String theValue) throws DataFormatException {
                if ("id".equals(theName)) {
                    if (myInstance instanceof IIdentifiableElement) {
                        ((IIdentifiableElement) myInstance).setElementSpecificId((theValue));
                    } else if (myInstance instanceof IBaseElement) {
                        ((IBaseElement) myInstance).setId(theValue);
                    }
                } else {
                    if (myJsonMode) {
                        myErrorHandler.incorrectJsonType(null, myElementName, JsonLikeValue.ValueType.OBJECT, null, JsonLikeValue.ValueType.SCALAR, JsonLikeValue.ScalarType.STRING);
                    } else {
                        myErrorHandler.unknownAttribute(null, theName);
                    }
                }
            }

            @Override
            public boolean elementIsRepeating(String theChildName) {
                BaseRuntimeChildDefinition child = myDefinition.getChildByName(theChildName);
                if (child == null) {
                    return false;
                }
                return child.getMax() > 1 || child.getMax() == Child.MAX_UNLIMITED;
            }

            @Override
            public void endingElement() {
                pop();
            }

            @Override
            public void enteringNewElement(String theNamespace, String theChildName) throws DataFormatException {
                BaseRuntimeChildDefinition child = myDefinition.getChildByName(theChildName);
                if (child == null) {
                    if (theChildName.equals("id")) {
                        if (getCurrentElement() instanceof IIdentifiableElement) {
                            push(new IdentifiableElementIdState(getPreResourceState(), (IIdentifiableElement) getCurrentElement()));
                            return;
                        }
                    }

                    /*
                     * This means we've found an element that doesn't exist on the structure. If the error handler doesn't throw
                     * an exception, swallow the element silently along with any child elements
                     */
                    myErrorHandler.unknownElement(null, theChildName);
                    push(new SwallowChildrenWholeState(getPreResourceState()));
                    return;
                }

                if ((child.getMax() == 0 || child.getMax() == 1) && !myParsedNonRepeatableNames.add(theChildName)) {
                    myErrorHandler.unexpectedRepeatingElement(null, theChildName);
                    push(new SwallowChildrenWholeState(getPreResourceState()));
                    return;
                }

                BaseRuntimeElementDefinition<?> target = child.getChildByName(theChildName);
                if (target == null) {
                    // This is a bug with the structures and shouldn't happen..
                    throw new DataFormatException("Found unexpected element '" + theChildName + "' in parent element '" + myDefinition.getName() + "'. Valid names are: " + child.getValidChildNames());
                }

                switch (target.getChildType()) {
                    case COMPOSITE_DATATYPE: {
                        BaseRuntimeElementCompositeDefinition<?> compositeTarget = (BaseRuntimeElementCompositeDefinition<?>) target;
                        ICompositeType newChildInstance = newCompositeInstance(getPreResourceState(), child, compositeTarget);
                        child.getMutator().addValue(myInstance, newChildInstance);
                        ElementCompositeState newState = new ElementCompositeState(getPreResourceState(), theChildName, compositeTarget, newChildInstance);
                        push(newState);
                        return;
                    }
                    case ID_DATATYPE:
                    case PRIMITIVE_DATATYPE: {
                        RuntimePrimitiveDatatypeDefinition primitiveTarget = (RuntimePrimitiveDatatypeDefinition) target;
                        IPrimitiveType<?> newChildInstance;
                        newChildInstance = getPrimitiveInstance(child, primitiveTarget, theChildName);
                        child.getMutator().addValue(myInstance, newChildInstance);
                        PrimitiveState newState = new PrimitiveState(getPreResourceState(), newChildInstance, theChildName, primitiveTarget.getName());
                        push(newState);
                        return;
                    }
                    case RESOURCE_BLOCK: {
                        RuntimeResourceBlockDefinition blockTarget = (RuntimeResourceBlockDefinition) target;
                        IBase newBlockInstance = newInstance(blockTarget);
                        child.getMutator().addValue(myInstance, newBlockInstance);
                        ElementCompositeState newState = new ElementCompositeState(getPreResourceState(), theChildName, blockTarget, newBlockInstance);
                        push(newState);
                        return;
                    }
                    case PRIMITIVE_XHTML: {
                        RuntimePrimitiveDatatypeNarrativeDefinition xhtmlTarget = (RuntimePrimitiveDatatypeNarrativeDefinition) target;
                        XhtmlDt newDt = newInstance(xhtmlTarget);
                        child.getMutator().addValue(myInstance, newDt);
                        XhtmlState state = new XhtmlState(getPreResourceState(), newDt, true);
                        push(state);
                        return;
                    }
                    case PRIMITIVE_XHTML_HL7ORG: {
                        RuntimePrimitiveDatatypeXhtmlHl7OrgDefinition xhtmlTarget = (RuntimePrimitiveDatatypeXhtmlHl7OrgDefinition) target;
                        IBaseXhtml newDt = newInstance(xhtmlTarget);
                        child.getMutator().addValue(myInstance, newDt);
                        XhtmlStateHl7Org state = new XhtmlStateHl7Org(getPreResourceState(), newDt);
                        push(state);
                        return;
                    }
                    case CONTAINED_RESOURCES: {
                        List<? extends IBase> values = child.getAccessor().getValues(myInstance);
                        if (values == null || values.isEmpty() || values.get(0) == null) {
                            Object newDt = newContainedDt((IResource) getPreResourceState().myInstance);
                            child.getMutator().addValue(myInstance, (IBase) newDt);
                        }
                        ContainedResourcesStateHapi state = new ContainedResourcesStateHapi(getPreResourceState());
                        push(state);
                        return;
                    }
                    case CONTAINED_RESOURCE_LIST: {
                        ContainedResourcesStateHl7Org state = new ContainedResourcesStateHl7Org(getPreResourceState());
                        push(state);
                        return;
                    }
                    case RESOURCE: {
                        if (myInstance instanceof IAnyResource || myInstance instanceof IBaseBackboneElement || myInstance instanceof IBaseElement) {
                            PreResourceStateHl7Org state = new PreResourceStateHl7Org(myInstance, child.getMutator(), null);
                            push(state);
                        } else {
                            PreResourceStateHapi state = new PreResourceStateHapi(myInstance, child.getMutator(), null);
                            push(state);
                        }
                        return;
                    }
                    case UNDECL_EXT:
                    case EXTENSION_DECLARED: {
                        // Throw an exception because this shouldn't happen here
                        break;
                    }
                }

                throw new DataFormatException("Illegal resource position: " + target.getChildType());
            }

            @Override
            public void enteringNewElementExtension(StartElement theElement, String theUrlAttr, boolean theIsModifier, final String baseServerUrl) {
                RuntimeChildDeclaredExtensionDefinition declaredExtension = myDefinition.getDeclaredExtension(theUrlAttr, baseServerUrl);
                if (declaredExtension != null) {
                    BaseState newState = new DeclaredExtensionState(getPreResourceState(), declaredExtension, myInstance);
                    push(newState);
                } else {
                    super.enteringNewElementExtension(theElement, theUrlAttr, theIsModifier, baseServerUrl);
                }
            }

            @Override
            protected IBase getCurrentElement() {
                return myInstance;
            }

        }

        public class ElementIdState extends BaseState {

            private final IBaseElement myElement;

            ElementIdState(PreResourceState thePreResourceState, IBaseElement theElement) {
                super(thePreResourceState);
                myElement = theElement;
            }

            @Override
            public void attributeValue(String theName, String theValue) throws DataFormatException {
                myElement.setId(theValue);
            }

            @Override
            public void endingElement() {
                pop();
            }

        }

        private class ExtensionState extends BaseState {

            private final IBaseExtension<?, ?> myExtension;

            ExtensionState(PreResourceState thePreResourceState, IBaseExtension<?, ?> theExtension) {
                super(thePreResourceState);
                myExtension = theExtension;
            }

            @Override
            public void attributeValue(String theName, String theValue) throws DataFormatException {
                if ("url".equals(theName)) {
                    // The URL attribute is handles in the XML loop as a special case since it is "url" instead
                    // of "value" like every single other place
                    return;
                }
                if ("id".equals(theName)) {
                    if (getCurrentElement() instanceof IBaseElement) {
                        ((IBaseElement) getCurrentElement()).setId(theValue);
                        return;
                    } else if (getCurrentElement() instanceof IIdentifiableElement) {
                        ((IIdentifiableElement) getCurrentElement()).setElementSpecificId(theValue);
                        return;
                    }
                }
                super.attributeValue(theName, theValue);
            }

            @Override
            public void endingElement() throws DataFormatException {
                if (myExtension.getValue() != null && myExtension.getExtension().size() > 0) {
                    throw new DataFormatException("Extension (URL='" + myExtension.getUrl() + "') must not have both a value and other contained extensions");
                }
                pop();
            }

            @Override
            public void enteringNewElement(String theNamespaceUri, String theLocalPart) throws DataFormatException {
                if (theLocalPart.equals("id")) {
                    if (getCurrentElement() instanceof IBaseElement) {
                        push(new ElementIdState(getPreResourceState(), (IBaseElement) getCurrentElement()));
                        return;
                    } else if (getCurrentElement() instanceof IIdentifiableElement) {
                        push(new IdentifiableElementIdState(getPreResourceState(), (IIdentifiableElement) getCurrentElement()));
                        return;
                    }
                }

                BaseRuntimeElementDefinition<?> target = myContext.getRuntimeChildUndeclaredExtensionDefinition().getChildByName(theLocalPart);

                if (target != null) {
                    switch (target.getChildType()) {
                        case COMPOSITE_DATATYPE: {
                            BaseRuntimeElementCompositeDefinition<?> compositeTarget = (BaseRuntimeElementCompositeDefinition<?>) target;
                            ICompositeType newChildInstance = newCompositeTypeInstance(getPreResourceState(), compositeTarget);
                            myExtension.setValue(newChildInstance);
                            ElementCompositeState newState = new ElementCompositeState(getPreResourceState(), theLocalPart, compositeTarget, newChildInstance);
                            push(newState);
                            return;
                        }
                        case ID_DATATYPE:
                        case PRIMITIVE_DATATYPE: {
                            RuntimePrimitiveDatatypeDefinition primitiveTarget = (RuntimePrimitiveDatatypeDefinition) target;
                            IPrimitiveType<?> newChildInstance = newInstance(primitiveTarget);
                            myExtension.setValue(newChildInstance);
                            PrimitiveState newState = new PrimitiveState(getPreResourceState(), newChildInstance, theLocalPart, primitiveTarget.getName());
                            push(newState);
                            return;
                        }
                        case CONTAINED_RESOURCES:
                        case CONTAINED_RESOURCE_LIST:
                        case EXTENSION_DECLARED:
                        case PRIMITIVE_XHTML:
                        case PRIMITIVE_XHTML_HL7ORG:
                        case RESOURCE:
                        case RESOURCE_BLOCK:
                        case UNDECL_EXT:
                            break;
                    }
                }

                // We hit an invalid type for the extension
                myErrorHandler.unknownElement(null, theLocalPart);
                push(new SwallowChildrenWholeState(getPreResourceState()));
            }

            @Override
            protected IBaseExtension<?, ?> getCurrentElement() {
                return myExtension;
            }

        }

        public class IdentifiableElementIdState extends BaseState {

            private final IIdentifiableElement myElement;

            public IdentifiableElementIdState(PreResourceState thePreResourceState, IIdentifiableElement theElement) {
                super(thePreResourceState);
                myElement = theElement;
            }

            @Override
            public void attributeValue(String theName, String theValue) throws DataFormatException {
                myElement.setElementSpecificId(theValue);
            }

            @Override
            public void endingElement() {
                pop();
            }

        }

        private class MetaElementState extends BaseState {
            private final ResourceMetadataMap myMap;

            public MetaElementState(PreResourceState thePreResourceState, ResourceMetadataMap theMap) {
                super(thePreResourceState);
                myMap = theMap;
            }

            @Override
            public void endingElement() throws DataFormatException {
                pop();
            }

            @Override
            public void enteringNewElement(String theNamespaceUri, String theLocalPart) throws DataFormatException {
                switch (theLocalPart) {
                    case "versionId":
                        push(new MetaVersionElementState(getPreResourceState(), myMap));
                        // } else if (theLocalPart.equals("profile")) {
                        //
                        break;
                    case "lastUpdated":
                        InstantDt updated = new InstantDt();
                        push(new PrimitiveState(getPreResourceState(), updated, theLocalPart, "instant"));
                        myMap.put(ResourceMetadataKeyEnum.UPDATED, updated);
                        break;
                    case "security":
                        @SuppressWarnings("unchecked")
                        List<IBase> securityLabels = (List<IBase>) myMap.get(ResourceMetadataKeyEnum.SECURITY_LABELS);
                        if (securityLabels == null) {
                            securityLabels = new ArrayList<>();
                            myMap.put(ResourceMetadataKeyEnum.SECURITY_LABELS, securityLabels);
                        }
                        IBase securityLabel = myContext.getVersion().newCodingDt();
                        BaseRuntimeElementCompositeDefinition<?> codinfDef = (BaseRuntimeElementCompositeDefinition<?>) myContext.getElementDefinition(securityLabel.getClass());
                        push(new SecurityLabelElementStateHapi(getPreResourceState(), codinfDef, securityLabel));
                        securityLabels.add(securityLabel);
                        break;
                    case "profile":
                        @SuppressWarnings("unchecked")
                        List<IdDt> profiles = (List<IdDt>) myMap.get(ResourceMetadataKeyEnum.PROFILES);
                        List<IdDt> newProfiles;
                        if (profiles != null) {
                            newProfiles = new ArrayList<>(profiles.size() + 1);
                            newProfiles.addAll(profiles);
                        } else {
                            newProfiles = new ArrayList<>(1);
                        }
                        IdDt profile = new IdDt();
                        push(new PrimitiveState(getPreResourceState(), profile, theLocalPart, "id"));
                        newProfiles.add(profile);
                        myMap.put(ResourceMetadataKeyEnum.PROFILES, Collections.unmodifiableList(newProfiles));
                        break;
                    case "tag":
                        TagList tagList = (TagList) myMap.get(ResourceMetadataKeyEnum.TAG_LIST);
                        if (tagList == null) {
                            tagList = new TagList();
                            myMap.put(ResourceMetadataKeyEnum.TAG_LIST, tagList);
                        }
                        push(new TagState(tagList));
                        break;
                    default:
                        myErrorHandler.unknownElement(null, theLocalPart);
                        push(new SwallowChildrenWholeState(getPreResourceState()));
                }
            }

            @Override
            public void enteringNewElementExtension(StartElement theElem, String theUrlAttr, boolean theIsModifier, final String baseServerUrl) {
                ResourceMetadataKeyEnum.ExtensionResourceMetadataKey resourceMetadataKeyEnum = new ResourceMetadataKeyEnum.ExtensionResourceMetadataKey(theUrlAttr);
                Object metadataValue = myMap.get(resourceMetadataKeyEnum);
                ExtensionDt newExtension;
                if (metadataValue == null) {
                    newExtension = new ExtensionDt(theIsModifier);
                } else if (metadataValue instanceof ExtensionDt) {
                    newExtension = (ExtensionDt) metadataValue;
                } else {
                    throw new IllegalStateException("Expected ExtensionDt as custom resource metadata type, got: " + metadataValue.getClass().getSimpleName());
                }
                newExtension.setUrl(theUrlAttr);
                myMap.put(resourceMetadataKeyEnum, newExtension);

                ExtensionState newState = new ExtensionState(getPreResourceState(), newExtension);
                push(newState);
            }

        }

        private class MetaVersionElementState extends BaseState {

            private final ResourceMetadataMap myMap;

            MetaVersionElementState(PreResourceState thePreResourceState, ResourceMetadataMap theMap) {
                super(thePreResourceState);
                myMap = theMap;
            }

            @Override
            public void attributeValue(String theName, String theValue) throws DataFormatException {
                myMap.put(ResourceMetadataKeyEnum.VERSION, theValue);
            }

            @Override
            public void endingElement() throws DataFormatException {
                pop();
            }

            @Override
            public void enteringNewElement(String theNamespaceUri, String theLocalPart) throws DataFormatException {
                myErrorHandler.unknownElement(null, theLocalPart);
                push(new SwallowChildrenWholeState(getPreResourceState()));
            }

        }

        private abstract class PreResourceState extends BaseState {

            private Map<String, IBaseResource> myContainedResources;
            private List<IBaseReference> myLocalReferences = new ArrayList<>();
            private IBaseResource myInstance;
            private FhirVersionEnum myParentVersion;
            private Class<? extends IBaseResource> myResourceType;

            PreResourceState(Class<? extends IBaseResource> theResourceType) {
                super(null);
                myResourceType = theResourceType;
                myContainedResources = new HashMap<>();
                if (theResourceType != null) {
                    myParentVersion = myContext.getResourceDefinition(theResourceType).getStructureVersion();
                } else {
                    myParentVersion = myContext.getVersion().getVersion();
                }
            }

            PreResourceState(PreResourceState thePreResourcesState, FhirVersionEnum theParentVersion) {
                super(thePreResourcesState);
                Validate.notNull(theParentVersion);
                myParentVersion = theParentVersion;
                myContainedResources = thePreResourcesState.getContainedResources();
            }

            public List<IBaseReference> getLocalReferences() {
                return myLocalReferences;
            }

            @Override
            public void endingElement() throws DataFormatException {
                stitchBundleCrossReferences();
                pop();
            }

            @Override
            public void enteringNewElement(String theNamespaceUri, String theLocalPart) throws DataFormatException {
                RuntimeResourceDefinition definition;
                if (myResourceType == null) {
                    definition = null;
                    if (myParser.getPreferTypes() != null) {
                        for (Class<? extends IBaseResource> next : myParser.getPreferTypes()) {
                            RuntimeResourceDefinition nextDef = myContext.getResourceDefinition(next);
                            if (nextDef.getName().equals(theLocalPart)) {
                                definition = nextDef;
                            }
                        }
                    }
                    if (definition == null) {
                        definition = myContext.getResourceDefinition(myParentVersion, theLocalPart);
                    }
                    if ((definition == null)) {
                        throw new DataFormatException("Element '" + theLocalPart + "' is not a known resource type, expected a resource at this position");
                    }
                } else {
                    definition = myContext.getResourceDefinition(myResourceType);
                    if (!StringUtils.equals(theLocalPart, definition.getName())) {
                        throw new DataFormatException(myContext.getLocalizer().getMessage(ParserState.class, "wrongResourceTypeFound", definition.getName(), theLocalPart));
                    }
                }

                RuntimeResourceDefinition def = definition;
                if (!definition.getName().equals(theLocalPart) && definition.getName().equalsIgnoreCase(theLocalPart)) {
                    throw new DataFormatException("Unknown resource type '" + theLocalPart + "': Resource names are case sensitive, found similar name: '" + definition.getName() + "'");
                }
                myInstance = newInstance(def);

                if (myInstance instanceof IResource) {
                    push(new ResourceStateHapi(getRootPreResourceState(), def, (IResource) myInstance, myContainedResources));
                } else {
                    push(new ResourceStateHl7Org(getRootPreResourceState(), def, myInstance));
                }
            }

            public Map<String, IBaseResource> getContainedResources() {
                return myContainedResources;
            }

            @Override
            protected IBaseResource getCurrentElement() {
                return myInstance;
            }

            private PreResourceState getRootPreResourceState() {
                if (getPreResourceState() != null) {
                    return getPreResourceState();
                }
                return this;
            }

            @Override
            public boolean isPreResource() {
                return true;
            }

            protected abstract void populateTarget();

            private void postProcess() {
                if (myContext.hasDefaultTypeForProfile()) {
                    IBaseMetaType meta = myInstance.getMeta();
                    Class<? extends IBaseResource> wantedProfileType = null;
                    String usedProfile = null;
                    for (IPrimitiveType<String> next : meta.getProfile()) {
                        if (isNotBlank(next.getValue())) {
                            wantedProfileType = myContext.getDefaultTypeForProfile(next.getValue());
                            if (wantedProfileType != null) {
                                usedProfile = next.getValue();
                                break;
                            }
                        }
                    }

                    if (wantedProfileType != null && !wantedProfileType.equals(myInstance.getClass())) {
                        if (myResourceType == null || myResourceType.isAssignableFrom(wantedProfileType)) {
                            ourLog.debug("Converting resource of type {} to type defined for profile \"{}\": {}", myInstance.getClass().getName(), usedProfile, wantedProfileType);

                            /*
                             * This isn't the most efficient thing really.. If we want a specific
                             * type we just re-parse into that type. The problem is that we don't know
                             * until we've parsed the resource which type we want to use because the
                             * profile declarations are in the text of the resource itself.
                             *
                             * At some point it would be good to write code which can present a view
                             * of one type backed by another type and use that.
                             */
                            FhirTerser t = myContext.newTerser();

                            // Clean up the cached resources
                            myGlobalResources.remove(myInstance);
                            myGlobalReferences.removeAll(t.getAllPopulatedChildElementsOfType(myInstance, IBaseReference.class));

                            IParser parser = myContext.newJsonParser();
                            String asString = parser.encodeResourceToString(myInstance);
                            myInstance = parser.parseResource(wantedProfileType, asString);

                            // Add newly created instance
                            myGlobalResources.add(myInstance);
                            myGlobalReferences.addAll(t.getAllPopulatedChildElementsOfType(myInstance, IBaseReference.class));
                        }
                    }
                }

                myInstance.setUserData(RESOURCE_CREATED_BY_PARSER, Boolean.TRUE);

                populateTarget();
            }

            private void stitchBundleCrossReferences() {
                final boolean bundle = "Bundle".equals(myContext.getResourceType(myInstance));
                if (bundle) {

                    FhirTerser t = myContext.newTerser();

                    Map<String, IBaseResource> idToResource = new HashMap<>();
                    List<IBase> entries = t.getValues(myInstance, "Bundle.entry", IBase.class);
                    for (IBase nextEntry : entries) {
                        IPrimitiveType<?> fullUrl = t.getSingleValueOrNull(nextEntry, "fullUrl", IPrimitiveType.class);
                        if (fullUrl != null && isNotBlank(fullUrl.getValueAsString())) {
                            IBaseResource resource = t.getSingleValueOrNull(nextEntry, "resource", IBaseResource.class);
                            if (resource != null) {
                                idToResource.put(fullUrl.getValueAsString(), resource);
                            }
                        }
                    }

                    /*
                     * Stitch together resource references
                     */
                    for (IBaseResource next : myGlobalResources) {
                        IIdType id = next.getIdElement();
                        if (id != null && !id.isEmpty()) {
                            String resName = myContext.getResourceType(next);
                            IIdType idType = id.withResourceType(resName).toUnqualifiedVersionless();
                            idToResource.put(idType.getValueAsString(), next);
                        }
                    }

                    for (IBaseReference nextRef : myGlobalReferences) {
                        if (!nextRef.isEmpty() && nextRef.getReferenceElement() != null) {
                            IIdType unqualifiedVersionless = nextRef.getReferenceElement().toUnqualifiedVersionless();
                            IBaseResource target = idToResource.get(unqualifiedVersionless.getValueAsString());
                            // resource can already be filled with local contained resource by populateTarget()
                            if (target != null && nextRef.getResource() == null) {
                                nextRef.setResource(target);
                            }
                        }
                    }

                    /*
                     * Set resource IDs based on Bundle.entry.request.url
                     */
                    List<Pair<String, IBaseResource>> urlsAndResources = BundleUtil.getBundleEntryUrlsAndResources(myContext, (IBaseBundle) myInstance);
                    for (Pair<String, IBaseResource> pair : urlsAndResources) {
                        if (pair.getRight() != null && isNotBlank(pair.getLeft()) && pair.getRight().getIdElement().isEmpty()) {
                            if (pair.getLeft().startsWith("urn:")) {
                                pair.getRight().setId(pair.getLeft());
                            }
                        }
                    }

                }
            }

            void weaveContainedResources() {
                for (IBaseReference nextRef : myLocalReferences) {
                    String ref = nextRef.getReferenceElement().getValue();
                    if (isNotBlank(ref)) {
                        if (ref.startsWith("#") && ref.length() > 1) {
                            IBaseResource target = myContainedResources.get(ref);
                            if (target != null) {
                                ourLog.debug("Resource contains local ref {}", ref);
                                nextRef.setResource(target);
                            } else {
                                myErrorHandler.unknownReference(null, ref);
                            }
                        }
                    }
                }

            }

            @Override
            public void wereBack() {
                postProcess();
            }

        }

        private class PreResourceStateHapi extends PreResourceState {
            private BaseRuntimeChildDefinition.IMutator myMutator;
            private IBase myTarget;


            PreResourceStateHapi(Class<? extends IBaseResource> theResourceType) {
                super(theResourceType);
                assert theResourceType == null || IResource.class.isAssignableFrom(theResourceType);
            }

            PreResourceStateHapi(IBase theTarget, BaseRuntimeChildDefinition.IMutator theMutator, Class<? extends IBaseResource> theResourceType) {
                super(theResourceType);
                myTarget = theTarget;
                myMutator = theMutator;
                assert theResourceType == null || IResource.class.isAssignableFrom(theResourceType);
            }

            @Override
            protected void populateTarget() {
                weaveContainedResources();
                if (myMutator != null) {
                    myMutator.setValue(myTarget, getCurrentElement());
                }
            }

            @Override
            public void wereBack() {
                super.wereBack();

                IResource nextResource = (IResource) getCurrentElement();
                String version = ResourceMetadataKeyEnum.VERSION.get(nextResource);
                String resourceName = myContext.getResourceType(nextResource);
                String bundleIdPart = nextResource.getId().getIdPart();
                if (isNotBlank(bundleIdPart)) {
                    // if (isNotBlank(entryBaseUrl)) {
                    // nextResource.setId(new IdDt(entryBaseUrl, resourceName, bundleIdPart, version));
                    // } else {
                    IdDt previousId = nextResource.getId();
                    nextResource.setId(new IdDt(null, resourceName, bundleIdPart, version));
                    // Copy extensions
                    if (!previousId.getAllUndeclaredExtensions().isEmpty()) {
                        for (final ExtensionDt ext : previousId.getAllUndeclaredExtensions()) {
                            nextResource.getId().addUndeclaredExtension(ext);
                        }
                    }
                    // }
                }
            }

        }

        private class PreResourceStateHl7Org extends PreResourceState {

            private BaseRuntimeChildDefinition.IMutator myMutator;
            private IBase myTarget;

            PreResourceStateHl7Org(Class<? extends IBaseResource> theResourceType) {
                super(theResourceType);
            }

            PreResourceStateHl7Org(IBase theTarget, BaseRuntimeChildDefinition.IMutator theMutator, Class<? extends IBaseResource> theResourceType) {
                super(theResourceType);
                myMutator = theMutator;
                myTarget = theTarget;
            }

            @Override
            protected void populateTarget() {
                weaveContainedResources();
                if (myMutator != null) {
                    myMutator.setValue(myTarget, getCurrentElement());
                }
            }

            @Override
            public void wereBack() {
                super.wereBack();

                if (getCurrentElement() instanceof IDomainResource) {
                    IDomainResource elem = (IDomainResource) getCurrentElement();
                    String resourceName = myContext.getResourceType(elem);
                    String versionId = elem.getMeta().getVersionId();
                    if (StringUtils.isBlank(elem.getIdElement().getIdPart())) {
                        // Resource has no ID
                    } else if (!elem.getIdElement().getIdPart().startsWith("urn:")) {
                        if (StringUtils.isNotBlank(versionId)) {
                            elem.getIdElement().setValue(resourceName + "/" + elem.getIdElement().getIdPart() + "/_history/" + versionId);
                        } else {
                            elem.getIdElement().setValue(resourceName + "/" + elem.getIdElement().getIdPart());
                        }
                    }
                }
            }

        }

        private class PrimitiveState extends BaseState {
            private final String myChildName;
            private final String myTypeName;
            private IPrimitiveType<?> myInstance;

            PrimitiveState(PreResourceState thePreResourceState, IPrimitiveType<?> theInstance, String theChildName, String theTypeName) {
                super(thePreResourceState);
                myInstance = theInstance;
                myChildName = theChildName;
                myTypeName = theTypeName;
            }

            @Override
            public void attributeValue(String theName, String theValue) throws DataFormatException {
                String value = theValue;
                if ("value".equals(theName)) {
                    if ("".equals(value)) {
                        ParseLocation location = ParseLocation.fromElementName(myChildName);
                        myErrorHandler.invalidValue(location, value, "Attribute value must not be empty (\"\")");
                    } else {

                        /*
                         * It may be possible to clean this up somewhat once the following PR is hopefully merged:
                         * https://github.com/FasterXML/jackson-core/pull/611
                         *
                         * See TolerantJsonParser
                         */
                        if ("decimal".equals(myTypeName)) {
                            if (value != null)
                                if (value.startsWith(".") && NumberUtils.isDigits(value.substring(1))) {
                                    value = "0" + value;
                                } else {
                                    while (value.startsWith("00")) {
                                        value = value.substring(1);
                                    }
                                }
                        }

                        try {
                            myInstance.setValueAsString(value);
                        } catch (DataFormatException | IllegalArgumentException e) {
                            ParseLocation location = ParseLocation.fromElementName(myChildName);
                            myErrorHandler.invalidValue(location, value, e.getMessage());
                        }
                    }
                } else if ("id".equals(theName)) {
                    if (myInstance instanceof IIdentifiableElement) {
                        ((IIdentifiableElement) myInstance).setElementSpecificId(value);
                    } else if (myInstance instanceof IBaseElement) {
                        ((IBaseElement) myInstance).setId(value);
                    } else if (myInstance instanceof IBaseResource) {
                        new IdDt(value).applyTo((IBaseResource) myInstance);
                    } else {
                        ParseLocation location = ParseLocation.fromElementName(myChildName);
                        myErrorHandler.unknownAttribute(location, theName);
                    }
                } else {
                    super.attributeValue(theName, value);
                }
            }

            @Override
            public void endingElement() {
                pop();
            }

            @Override
            public void enteringNewElement(String theNamespaceUri, String theLocalPart) throws DataFormatException {
                super.enteringNewElement(theNamespaceUri, theLocalPart);
                push(new SwallowChildrenWholeState(getPreResourceState()));
            }

            @Override
            protected IBase getCurrentElement() {
                return myInstance;
            }

        }

        private class ResourceStateHapi extends ElementCompositeState {

            private IResource myInstance;

            public ResourceStateHapi(PreResourceState thePreResourceState, BaseRuntimeElementCompositeDefinition<?> theDef, IResource theInstance, Map<String, IBaseResource> theContainedResources) {
                super(thePreResourceState, theDef.getName(), theDef, theInstance);
                myInstance = theInstance;
            }

            @Override
            public void enteringNewElement(String theNamespace, String theChildName) throws DataFormatException {
                if ("id".equals(theChildName)) {
                    push(new PrimitiveState(getPreResourceState(), myInstance.getId(), theChildName, "id"));
                } else if ("meta".equals(theChildName)) {
                    push(new MetaElementState(getPreResourceState(), myInstance.getResourceMetadata()));
                } else {
                    super.enteringNewElement(theNamespace, theChildName);
                }
            }
        }

        private class ResourceStateHl7Org extends ElementCompositeState {

            ResourceStateHl7Org(PreResourceState thePreResourceState, BaseRuntimeElementCompositeDefinition<?> theDef, IBaseResource theInstance) {
                super(thePreResourceState, theDef.getName(), theDef, theInstance);
            }

        }

        private class SecurityLabelElementStateHapi extends ElementCompositeState {

            SecurityLabelElementStateHapi(PreResourceState thePreResourceState, BaseRuntimeElementCompositeDefinition<?> theDef, IBase codingDt) {
                super(thePreResourceState, theDef.getName(), theDef, codingDt);
            }

            @Override
            public void endingElement() throws DataFormatException {
                pop();
            }

        }

        private class SwallowChildrenWholeState extends BaseState {

            private int myDepth;

            SwallowChildrenWholeState(PreResourceState thePreResourceState) {
                super(thePreResourceState);
            }

            @Override
            public void attributeValue(String theName, String theValue) throws DataFormatException {
                // ignore
            }

            @Override
            public void endingElement() throws DataFormatException {
                myDepth--;
                if (myDepth < 0) {
                    pop();
                }
            }

            @Override
            public void enteringNewElement(String theNamespaceUri, String theLocalPart) throws DataFormatException {
                myDepth++;
            }

            @Override
            public void enteringNewElementExtension(StartElement theElement, String theUrlAttr, boolean theIsModifier, final String baseServerUrl) {
                myDepth++;
            }

        }

        private class TagListState extends BaseState {

            private TagList myTagList;

            public TagListState(TagList theTagList) {
                super(null);
                myTagList = theTagList;
            }

            @Override
            public void endingElement() throws DataFormatException {
                pop();
            }

            @Override
            public void enteringNewElement(String theNamespaceUri, String theLocalPart) throws DataFormatException {
                if (TagList.ATTR_CATEGORY.equals(theLocalPart)) {
                    push(new TagState(myTagList));
                } else {
                    throw new DataFormatException("Unexpected element: " + theLocalPart);
                }
            }

            @Override
            protected IBase getCurrentElement() {
                return myTagList;
            }

        }

        private class TagState extends BaseState {

            private static final int LABEL = 2;
            private static final int NONE = 0;

            private static final int SCHEME = 3;
            private static final int TERM = 1;
            private String myLabel;
            private String myScheme;
            private int mySubState = 0;
            private TagList myTagList;
            private String myTerm;

            public TagState(TagList theTagList) {
                super(null);
                myTagList = theTagList;
            }

            @Override
            public void attributeValue(String theName, String theValue) throws DataFormatException {
                String value = defaultIfBlank(theValue, null);

                switch (mySubState) {
                    case TERM:
                        myTerm = (value);
                        break;
                    case LABEL:
                        myLabel = (value);
                        break;
                    case SCHEME:
                        myScheme = (value);
                        break;
                    case NONE:
                        // This handles JSON encoding, which is a bit weird
                        enteringNewElement(null, theName);
                        attributeValue(null, value);
                        endingElement();
                        break;
                }
            }

            @Override
            public void endingElement() throws DataFormatException {
                if (mySubState != NONE) {
                    mySubState = NONE;
                } else {
                    if (isNotEmpty(myScheme) || isNotBlank(myTerm) || isNotBlank(myLabel)) {
                        myTagList.addTag(myScheme, myTerm, myLabel);
                    }
                    pop();
                }
            }

            @Override
            public void enteringNewElement(String theNamespaceUri, String theLocalPart) throws DataFormatException {
                /*
                 * We allow for both the DSTU1 and DSTU2 names here
                 */
                if (Tag.ATTR_TERM.equals(theLocalPart) || "code".equals(theLocalPart)) {
                    mySubState = TERM;
                } else if (Tag.ATTR_SCHEME.equals(theLocalPart) || "system".equals(theLocalPart)) {
                    mySubState = SCHEME;
                } else if (Tag.ATTR_LABEL.equals(theLocalPart) || "display".equals(theLocalPart)) {
                    mySubState = LABEL;
                } else {
                    throw new DataFormatException("Unexpected element: " + theLocalPart);
                }
            }

        }

        private class XhtmlState extends BaseState {
            private int myDepth;
            private XhtmlDt myDt;
            private List<XMLEvent> myEvents = new ArrayList<XMLEvent>();
            private boolean myIncludeOuterEvent;

            private XhtmlState(PreResourceState thePreResourceState, XhtmlDt theXhtmlDt, boolean theIncludeOuterEvent) throws DataFormatException {
                super(thePreResourceState);
                myDepth = 0;
                myDt = theXhtmlDt;
                myIncludeOuterEvent = theIncludeOuterEvent;
            }

            @Override
            public void attributeValue(String theName, String theValue) throws DataFormatException {
                if (myJsonMode) {
                    myDt.setValueAsString(theValue);
                } else {
                    // IGNORE - don't handle this as an error, we process these as XML events
                }
            }

            protected void doPop() {
                pop();
            }

            @Override
            public void endingElement() throws DataFormatException {
                if (myJsonMode) {
                    doPop();
                    return;
                }
                super.endingElement();
            }

            @Override
            public void enteringNewElement(String theNamespaceUri, String theLocalPart) throws DataFormatException {
                // IGNORE - don't handle this as an error, we process these as XML events
            }

            @Override
            protected IElement getCurrentElement() {
                return myDt;
            }

            public XhtmlDt getDt() {
                return myDt;
            }

            @Override
            public void xmlEvent(XMLEvent theEvent) {
                if (theEvent.isEndElement()) {
                    myDepth--;
                }

                if (myIncludeOuterEvent || myDepth > 0) {
                    myEvents.add(theEvent);
                }

                if (theEvent.isStartElement()) {
                    myDepth++;
                }

                if (theEvent.isEndElement()) {
                    if (myDepth == 0) {
                        String eventsAsString = XmlUtil.encode(myEvents);
                        myDt.setValue(eventsAsString);
                        doPop();
                    }
                }
            }

        }

        private class XhtmlStateHl7Org extends XhtmlState {
            private IBaseXhtml myHl7OrgDatatype;

            private XhtmlStateHl7Org(PreResourceState thePreResourceState, IBaseXhtml theHl7OrgDatatype) {
                super(thePreResourceState, new XhtmlDt(), true);
                myHl7OrgDatatype = theHl7OrgDatatype;
            }

            @Override
            public void doPop() {
                // TODO: this is not very efficient
                String value = getDt().getValueAsString();
                myHl7OrgDatatype.setValueAsString(value);

                super.doPop();
            }

        }

        /**
         * @param theResourceType May be null
         */
        static <T extends IBaseResource> ParserState<T> getPreResourceInstance(IParser theParser, Class<T> theResourceType, FhirContext theContext, boolean theJsonMode, IParserErrorHandler theErrorHandler)
                throws DataFormatException {
            ParserState<T> retVal = new ParserState<T>(theParser, theContext, theJsonMode, theErrorHandler);
            if (theResourceType == null) {
                if (theContext.getVersion().getVersion().isRi()) {
                    retVal.push(retVal.new PreResourceStateHl7Org(theResourceType));
                } else {
                    retVal.push(retVal.new PreResourceStateHapi(theResourceType));
                }
            } else {
                if (IResource.class.isAssignableFrom(theResourceType)) {
                    retVal.push(retVal.new PreResourceStateHapi(theResourceType));
                } else {
                    retVal.push(retVal.new PreResourceStateHl7Org(theResourceType));
                }
            }
            return retVal;
        }
    }
}