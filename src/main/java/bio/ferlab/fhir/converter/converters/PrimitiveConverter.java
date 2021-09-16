package bio.ferlab.fhir.converter.converters;

import org.hl7.fhir.instance.model.api.IPrimitiveType;

public abstract class PrimitiveConverter<T> extends BaseConverter<T> {

    private final String elementType;

    public PrimitiveConverter(String elementType) {
        this.elementType = elementType;
    }

    public Object fromAvro(Object value) {
        return ((IPrimitiveType) value).getValue();
    }

    public String getElementType() {
        return this.elementType;
    }
}
