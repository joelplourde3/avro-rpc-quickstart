package bio.ferlab.fhir.converter.converters;

public abstract class BaseConverter<T> {

    public abstract Object fromAvro(Object value);

    public abstract T getDataType();

    public abstract String getElementType();
}
