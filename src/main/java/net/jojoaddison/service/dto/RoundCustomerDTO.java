package net.jojoaddison.service.dto;

/**
 * A patient the planner can put a visit against: the id hc-professional keys on, and a name for it.
 *
 * <h2>{@code customerId} is the sibling's id, and that is the whole point of this type</h2>
 *
 * <p>{@code RoundPlanDtos.VisitRequest.customerId} is a {@code patientservice}
 * {@code Profile.patientId}. This service's own {@code Patient} documents are keyed {@code a1},
 * {@code a2}, … and those ids mean nothing on the far stack — so the value here is
 * {@code DirectoryLink.externalId}, which <em>is</em> that {@code patientId}, arriving on
 * hc-patient's {@code OnboardingStarted}.
 *
 * <p><b>⚠ Read this before concluding that backlog item 22's warning was ignored.</b> That entry says
 * "do not close this by making the field a dropdown of hc-admin patients", and the failure it names
 * is sending {@code Patient.id} — an id that "means nobody", filed successfully, producing a day plan
 * the patient cannot see. <b>This sends the opposite value.</b> Item 22 was written on 2026-09-04, and
 * {@code directory_link} — the join it says this service does not have — arrived on 2026-09-07 with
 * items 45 to 47. The entry's premise stopped being true three days after it was written, which is why
 * a picker is buildable now and was not then.
 *
 * <p>So the invariant this type exists to carry is: <b>{@link #customerId()} is never a
 * {@code Patient.id}</b>. {@code RoundCustomerServiceTest.theCustomerIdIsTheSiblingsIdAndNeverTheLocalOne}
 * and {@code RoundCustomerResourceIT.theCustomerIdIsNeverTheLocalPatientId} both fail if it becomes
 * one, and they were watched failing on that mutation.
 *
 * <h2>{@code name} is nullable, and null is not "blank"</h2>
 *
 * <p>A patient learned from a domain event has no {@code Profile} here and can never be given one —
 * nothing on {@code patient-events} carries a name, their {@code PatientEventPublisher} refusing at
 * runtime to publish identifying content — so the name falls back to the address on the link, and
 * then to nothing at all. Null means <em>this service cannot name this person</em>, and the console
 * says so in words.
 *
 * <p><b>The words are the console's and not this service's</b>, which is the same call
 * {@code RoundPlanDtos.Reason} makes one type along: the wording lives in an i18n catalogue, and a
 * server-composed sentence would be the one string on that screen that never translates.
 * {@code PatientCsvExporter} returns "Identity not on file" itself for the opposite reason — a
 * downloaded file has no renderer to defer to.
 *
 * <p><b>Never the record's id.</b> Item 45's rule: an id where a name goes reads as a corrupted
 * record, and it has now been broken three times in this product (items 45, 53, 62). A row that
 * cannot be named carries a null here and nothing else.
 *
 * @param customerId hc-patient's own {@code patientId} for this person, from
 *     {@code DirectoryLink.externalId} — the value that goes on the wire to hc-professional
 * @param name what to call them: their {@code Profile} name, else the address on their link, else
 *     null when this service holds neither
 */
public record RoundCustomerDTO(String customerId, String name) {}
