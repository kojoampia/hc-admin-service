package net.jojoaddison.service.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import net.jojoaddison.domain.enumeration.ProfessionalRole;

/**
 * A DTO for the {@link net.jojoaddison.domain.WageRate} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class WageRateDTO implements Serializable {

    private String id;

    @NotNull
    private ProfessionalRole role;

    @NotNull
    @DecimalMin(value = "0")
    private BigDecimal amount;

    @NotNull
    @Size(min = 3, max = 3)
    private String currency;

    @NotNull
    private LocalDate validFrom;

    @Size(max = 200)
    private String note;

    /**
     * Read-only, stamped server-side. The console shows these so an admin can see who last moved a
     * price and when — a rate change is the kind of thing somebody asks about later.
     */
    private String lastModifiedBy;

    private Instant lastModifiedDate;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ProfessionalRole getRole() {
        return role;
    }

    public void setRole(ProfessionalRole role) {
        this.role = role;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDate validFrom) {
        this.validFrom = validFrom;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public Instant getLastModifiedDate() {
        return lastModifiedDate;
    }

    public void setLastModifiedDate(Instant lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WageRateDTO wageRateDTO)) {
            return false;
        }

        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, wageRateDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "WageRateDTO{" +
            "id='" + getId() + "'" +
            ", role='" + getRole() + "'" +
            ", amount=" + getAmount() +
            ", currency='" + getCurrency() + "'" +
            ", validFrom='" + getValidFrom() + "'" +
            ", note='" + getNote() + "'" +
            "}";
    }
}
