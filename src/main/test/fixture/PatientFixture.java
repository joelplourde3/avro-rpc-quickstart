package fixture;

import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Patient;

public class PatientFixture {

    public static Patient createPatient() {
        Patient patient = new Patient();
        patient.addIdentifier(IdentifierFixture.createIdentifier());
        patient.addName()
                .setFamily("Simpsion")
                .addGiven("H")
                .addGiven("Homer");
        patient.setActive(true);
        patient.setMultipleBirth(new BooleanType(true));
        patient.setGender(Enumerations.AdministrativeGender.MALE);
        patient.setId(IdType.newRandomUuid());
        patient.addAddress(AddressFixture.createAddress());
        return patient;
    }
}
