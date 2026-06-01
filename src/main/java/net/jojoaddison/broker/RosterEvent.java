package net.jojoaddison.broker;

import java.io.Serializable;

/**
 * Kafka event emitted when a shift is assigned in the duty roster.
 */
public class RosterEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private String shiftId;
    private String eventType;
    private String patientId;
    private String professionalId;

    public RosterEvent() {}

    public RosterEvent(String shiftId, String eventType, String patientId, String professionalId) {
        this.shiftId = shiftId;
        this.eventType = eventType;
        this.patientId = patientId;
        this.professionalId = professionalId;
    }

    public String getShiftId() {
        return shiftId;
    }

    public void setShiftId(String shiftId) {
        this.shiftId = shiftId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getProfessionalId() {
        return professionalId;
    }

    public void setProfessionalId(String professionalId) {
        this.professionalId = professionalId;
    }

    @Override
    public String toString() {
        return (
            "RosterEvent{" +
            "shiftId='" +
            shiftId +
            "'" +
            ", eventType='" +
            eventType +
            "'" +
            ", patientId='" +
            patientId +
            "'" +
            ", professionalId='" +
            professionalId +
            "'" +
            "}"
        );
    }
}
