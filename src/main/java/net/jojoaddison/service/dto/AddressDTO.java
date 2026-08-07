package net.jojoaddison.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * A DTO for the {@link net.jojoaddison.domain.Address} entity.
 */
@Schema(description = "Ghana digital address plus postal detail.")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AddressDTO implements Serializable {

    private String id;

    @NotNull
    @Size(max = 20)
    @Pattern(regexp = "^[A-Z]{2}-[0-9]{3}-[0-9]{4}$")
    private String digitalAddress;

    @NotNull
    @Size(max = 120)
    private String streetAddress;

    @Size(max = 60)
    private String townDistrict;

    @NotNull
    @Size(max = 60)
    private String cityState;

    @NotNull
    @Size(max = 60)
    private String region;

    @NotNull
    @Size(max = 60)
    private String country;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDigitalAddress() {
        return digitalAddress;
    }

    public void setDigitalAddress(String digitalAddress) {
        this.digitalAddress = digitalAddress;
    }

    public String getStreetAddress() {
        return streetAddress;
    }

    public void setStreetAddress(String streetAddress) {
        this.streetAddress = streetAddress;
    }

    public String getTownDistrict() {
        return townDistrict;
    }

    public void setTownDistrict(String townDistrict) {
        this.townDistrict = townDistrict;
    }

    public String getCityState() {
        return cityState;
    }

    public void setCityState(String cityState) {
        this.cityState = cityState;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AddressDTO)) {
            return false;
        }

        AddressDTO addressDTO = (AddressDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, addressDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "AddressDTO{" +
            "id='" + getId() + "'" +
            ", digitalAddress='" + getDigitalAddress() + "'" +
            ", streetAddress='" + getStreetAddress() + "'" +
            ", townDistrict='" + getTownDistrict() + "'" +
            ", cityState='" + getCityState() + "'" +
            ", region='" + getRegion() + "'" +
            ", country='" + getCountry() + "'" +
            "}";
    }
}
