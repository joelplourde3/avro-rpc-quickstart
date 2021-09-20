FHAVRO
=========

FHAVRO - Java library for serialization/deserialization of HL7 FHIR v4.0.1 resources in Apache Avro.

[![License][Badge-License]][Link-License]

## Documentation

FHIR is a standard for health care data exchange, published by HL7: https://hl7.org/FHIR/ 

HAPI FHIR is a complete implementation of the HL7 FHIR standard for healthcare interoperability in Java.: https://hapifhir.io/

Apache Avro is a data serialization system: https://avro.apache.org/docs/current/

## Getting Started

...

## Known issues

 - In Avro, Enum symbols need to conform to the same naming convention than Names. Therefore, it must starts with [A-Za-z_] and subsequent character must contain only [A-Za-z0-9_]. At the moment, some enum in FHIR contains illegal character (e.g. /, <, >, etc.).
 - Extensions are not included in the schema and therefore are not serialized.
 - Identifier property in the Reference type is saved as a String in order to avoid Cyclical definition.
 - Only 616 schemas are supported at the moment, some entities causes issues (only 43 out of 14348).
