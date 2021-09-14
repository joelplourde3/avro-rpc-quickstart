package bio.ferlab.fhir;

import ca.uhn.fhir.context.*;
import ca.uhn.fhir.model.api.*;
import ca.uhn.fhir.model.api.annotation.Child;
import ca.uhn.fhir.model.base.composite.BaseCodingDt;
import ca.uhn.fhir.model.base.composite.BaseContainedDt;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.model.primitive.InstantDt;
import ca.uhn.fhir.narrative.INarrativeGenerator;
import ca.uhn.fhir.parser.*;
import ca.uhn.fhir.parser.json.JsonLikeStructure;
import ca.uhn.fhir.parser.json.JsonLikeWriter;
import ca.uhn.fhir.parser.json.jackson.JacksonStructure;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.util.ElementUtil;
import ca.uhn.fhir.util.FhirTerser;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.hl7.fhir.instance.model.api.*;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DateType;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

import static ca.uhn.fhir.context.BaseRuntimeElementDefinition.ChildTypeEnum.ID_DATATYPE;
import static ca.uhn.fhir.context.BaseRuntimeElementDefinition.ChildTypeEnum.PRIMITIVE_DATATYPE;
import static org.apache.commons.lang3.StringUtils.*;

public class ExtendedJsonParser extends BaseParser {

    private boolean prettyPrint;
    private FhirTerser.ContainedResources myContainedResources;

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
            // if (theValue instanceof IBaseExtension) {
            // return null;
            // }

            /*
             * For RI structures Enumeration class, this replaces the child def
             * with the "code" one. This is messy, and presumably there is a better
             * way..
             */
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

                if (profiles != null && profiles.isEmpty() == false) {
                    beginArray(theEventWriter, "profile");
                    for (IIdType profile : profiles) {
                        if (profile != null && isNotBlank(profile.getValue())) {
                            theEventWriter.write(profile.getValue());
                        }
                    }
                    theEventWriter.endArray();
                }

                if (securityLabels.isEmpty() == false) {
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

                if (tags != null && tags.isEmpty() == false) {
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
    protected <T extends IBaseResource> T doParseResource(Class<T> aClass, Reader reader) throws DataFormatException {
        // TODO
        return null;
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
                } else if (value instanceof DateTimeType) {
                    System.out.println("Here!");
                    try {
                        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");
                        Date date = parser.parse(value.getValueAsString());

                        // Days days1 = Days.daysBetween(MutableDateTime(), new MutableDateTime(date1.getTime()));

                        long epochSecond = date.toInstant().getEpochSecond();

                        if (theChildName != null) {
                            write(theEventWriter, theChildName, String.valueOf(epochSecond));
                        } else {
                            theEventWriter.write(String.valueOf(epochSecond));
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                } else if (value instanceof DateType) {
                    try {
                        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd");
                        Date date = parser.parse(value.getValueAsString());

                        long epochDays = 0;

                        if (theChildName != null) {
                            write(theEventWriter, theChildName, String.valueOf(epochDays));
                        } else {
                            theEventWriter.write(String.valueOf(epochDays));
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
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

                // // Check for undeclared extensions on the declared extension
                // // (grrrrrr....)
                // if (myValue instanceof ISupportsUndeclaredExtensions) {
                // ISupportsUndeclaredExtensions value = (ISupportsUndeclaredExtensions)myValue;
                // List<ExtensionDt> exts = value.getUndeclaredExtensions();
                // if (exts.size() > 0) {
                // ArrayList<IBase> newValueList = new ArrayList<IBase>();
                // newValueList.addAll(preProcessedValue);
                // newValueList.addAll(exts);
                // preProcessedValue = newValueList;
                // }
                // }

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

            // theEventWriter.name(myUndeclaredExtension.get);

            theEventWriter.endObject();
        }
    }

    private static void write(JsonLikeWriter theWriter, String theName, String theValue) throws IOException {
        theWriter.write(theName, theValue);
    }

    class ChildNameAndDef {

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
        return myContainedResources;
    }

    void setContainedResources(FhirTerser.ContainedResources theContainedResources) {
        myContainedResources = theContainedResources;
    }

    public static class ParseLocation implements IParserErrorHandler.IParseLocation {

        private String myParentElementName;

        /**
         * Constructor
         */
        ParseLocation() {
            super();
        }

        /**
         * Constructor
         */
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

        /**
         * Factory method
         */
        static ParseLocation fromElementName(String theChildName) {
            return new ParseLocation(theChildName);
        }
    }
}
