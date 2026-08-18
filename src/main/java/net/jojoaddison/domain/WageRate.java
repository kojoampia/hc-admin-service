package net.jojoaddison.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * What one shift pays a professional of a given role, from a given date.
 *
 * <p>Rates are <strong>effective-dated, never edited in place</strong>. Raising a rate means adding
 * a row with a later {@code validFrom}; the superseded row stays as history. A shift is valued at
 * the rate in force on its own {@code shiftDate}, so a rise never restates a total that has already
 * been reported or paid. See {@code WageRateService#rateOn}.
 */
@Schema(description = "What one shift pays a professional of a given role, from a given date.")
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
            ", amount=" + getAmount() +
            ", currency='" + getCurrency() + "'" +
            ", validFrom='" + getValidFrom() + "'" +
            ", note='" + getNote() + "'" +
            "}";
    }
}
