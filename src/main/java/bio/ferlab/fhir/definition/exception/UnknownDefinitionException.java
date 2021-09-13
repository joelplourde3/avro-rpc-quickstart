package bio.ferlab.fhir.definition.exception;

public class UnknownDefinitionException extends RuntimeException {

    public UnknownDefinitionException(String identifier) {
        super("Unknown definition, please verify: " + identifier);
    }
}
