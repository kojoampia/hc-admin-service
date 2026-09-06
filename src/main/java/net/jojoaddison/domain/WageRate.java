package net.jojoaddison.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import net.jojoaddison.domain.enumeration.ShiftType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * What one shift pays a professional of a given role, for a given shift type, from a given date.
 *
 * <p>Rates are <strong>effective-dated, never edited in place</strong>. Raising a rate means adding
 * a row with a later {@code validFrom}; the superseded row stays as history. A shift is valued at
 * the rate in force on its own {@code shiftDate}, so a rise never restates a total that has already
 * been reported or paid. See {@code WageRateService#rateOn}.
 *
 * <p><b>{@code shiftType} is the second dimension, added 2026-09-04, and the timing was the whole
 * argument for adding it then.</b> A night pays more than a day nearly everywhere, and before the
 * enum had five values a {@code shiftType} column mostly repeated what the role already implied —
 * {@code FLEXIBLE} is what makes a per-shift-type rate mean something. But the reason it could not
 * wait is the other half: <b>this collection held zero rows in production</b>, and that is a fact
 * with an expiry date. Once real rates exist and shifts have been valued against them, adding a
 * dimension means deciding what every already-valued shift was worth under the new key — which is a
 * restatement of a wage bill that has already been paid, and refusing exactly that is what
 * effective-dating is <em>for</em>. The model would have refused this change too.
 *
 * <p>The lookup is therefore {@code (role, shiftType, date)}, and it is <b>exact</b>: there is no
 * "any shift type" row and no fallback from a missing {@code (role, shiftType)} to a bare
 * {@code (role)}. A fallback would need a rule for whether a general row with a later
 * {@code validFrom} beats a specific one with an earlier date, and every answer to that is a
 * surprise to somebody. An unpriced combination stays unpriced, which the console already reports
 * distinctly from a rate of zero.
 *
 * <p><b>An {@code OFF} row is accepted and never read.</b> The pricing grid is five roles by five
 * shift types, so that whoever owns pricing is asked once for the whole thing rather than twice; but
 * {@code ShiftValuationService.payableShifts} excludes {@code OFF} before any rate is resolved, so a
 * rate priced against it contributes to nothing. Storing it is harmless and rejecting it would be a
 * rule to maintain; knowing it is inert is the part worth writing down.
 */
@Schema(description = "What one shift pays a professional of a given role and shift type, from a given date.")
@Document(collection = "wage_rate")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class WageRate extends AbstractAuditingEntity<String> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @NotNull
    @Field("role")
    private ProfessionalRole role;

    @NotNull
    @Field("shift_type")
    private ShiftType shiftType;

    @NotNull
    @DecimalMin(value = "0")
    @Field("amount")
    private BigDecimal amount;

    @NotNull
    @Size(min = 3, max = 3)
    @Field("currency")
    private String currency;

    @NotNull
    @Field("valid_from")
    private LocalDate validFrom;

    @Size(max = 200)
    @Field("note")
    private String note;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    @Override
    public String getId() {
        return this.id;
    }

    public WageRate id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ProfessionalRole getRole() {
        return this.role;
    }

    public WageRate role(ProfessionalRole role) {
        this.setRole(role);
        return this;
    }

    public void setRole(ProfessionalRole role) {
        this.role = role;
    }

    public ShiftType getShiftType() {
        return this.shiftType;
    }

    public WageRate shiftType(ShiftType shiftType) {
        this.setShiftType(shiftType);
        return this;
    }

    public void setShiftType(ShiftType shiftType) {
        this.shiftType = shiftType;
    }

    public BigDecimal getAmount() {
        return this.amount;
    }

    public WageRate amount(BigDecimal amount) {
        this.setAmount(amount);
        return this;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return this.currency;
    }

    public WageRate currency(String currency) {
        this.setCurrency(currency);
        return this;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDate getValidFrom() {
        return this.validFrom;
    }

    public WageRate validFrom(LocalDate validFrom) {
        this.setValidFrom(validFrom);
        return this;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public String getNote() {
        return this.note;
    }

    public WageRate note(String note) {
        this.setNote(note);
        return this;
    }

    public void setNote(String note) {
        this.note = note;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WageRate)) {
            return false;
        }
        return getId() != null && getId().equals(((WageRate) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "WageRate{" +
            "id=" + getId() +
            ", role='" + getRole() + "'" +
            ", shiftType='" + getShiftType() + "'" +
            ", amount=" + getAmount() +
            ", currency='" + getCurrency() + "'" +
            ", validFrom='" + getValidFrom() + "'" +
            ", note='" + getNote() + "'" +
            "}";
    }
}
