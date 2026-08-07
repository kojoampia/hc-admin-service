package net.jojoaddison.domain;

import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Ghana digital address plus postal detail.
 */
@Document(collection = "address")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Address implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @NotNull
    @Size(max = 20)
    @Pattern(regexp = "^[A-Z]{2}-[0-9]{3}-[0-9]{4}$")
    @Field("digital_address")
    private String digitalAddress;

    @NotNull
    @Size(max = 120)
    @Field("street_address")
    private String streetAddress;

    @Size(max = 60)
    @Field("town_district")
    private String townDistrict;

    @NotNull
    @Size(max = 60)
    @Field("city_state")
    private String cityState;

    @NotNull
    @Size(max = 60)
    @Field("region")
    private String region;

    @NotNull
    @Size(max = 60)
    @Field("country")
    private String country;

    @DBRef
    private Profile profile;

    @DBRef
    private Hub hub;

    @DBRef
    private Organisation organisation;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public Address id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDigitalAddress() {
        return this.digitalAddress;
    }

    public Address digitalAddress(String digitalAddress) {
        this.setDigitalAddress(digitalAddress);
        return this;
    }

    public void setDigitalAddress(String digitalAddress) {
        this.digitalAddress = digitalAddress;
    }

    public String getStreetAddress() {
        return this.streetAddress;
    }

    public Address streetAddress(String streetAddress) {
        this.setStreetAddress(streetAddress);
        return this;
    }

    public void setStreetAddress(String streetAddress) {
        this.streetAddress = streetAddress;
    }

    public String getTownDistrict() {
        return this.townDistrict;
    }

    public Address townDistrict(String townDistrict) {
        this.setTownDistrict(townDistrict);
        return this;
    }

    public void setTownDistrict(String townDistrict) {
        this.townDistrict = townDistrict;
    }

    public String getCityState() {
        return this.cityState;
    }

    public Address cityState(String cityState) {
        this.setCityState(cityState);
        return this;
    }

    public void setCityState(String cityState) {
        this.cityState = cityState;
    }

    public String getRegion() {
        return this.region;
    }

    public Address region(String region) {
        this.setRegion(region);
        return this;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getCountry() {
        return this.country;
    }

    public Address country(String country) {
        this.setCountry(country);
        return this;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public Profile getProfile() {
        return this.profile;
    }

    public void setProfile(Profile profile) {
        if (this.profile != null) {
            this.profile.setAddress(null);
        }
        if (profile != null) {
            profile.setAddress(this);
        }
        this.profile = profile;
    }

    public Address profile(Profile profile) {
        this.setProfile(profile);
        return this;
    }

    public Hub getHub() {
        return this.hub;
    }

    public void setHub(Hub hub) {
        if (this.hub != null) {
            this.hub.setAddress(null);
        }
        if (hub != null) {
            hub.setAddress(this);
        }
        this.hub = hub;
    }

    public Address hub(Hub hub) {
        this.setHub(hub);
        return this;
    }

    public Organisation getOrganisation() {
        return this.organisation;
    }

    public void setOrganisation(Organisation organisation) {
        if (this.organisation != null) {
            this.organisation.setAddress(null);
        }
        if (organisation != null) {
            organisation.setAddress(this);
        }
        this.organisation = organisation;
    }

    public Address organisation(Organisation organisation) {
        this.setOrganisation(organisation);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Address)) {
            return false;
        }
        return getId() != null && getId().equals(((Address) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Address{" +
            "id=" + getId() +
            ", digitalAddress='" + getDigitalAddress() + "'" +
            ", streetAddress='" + getStreetAddress() + "'" +
            ", townDistrict='" + getTownDistrict() + "'" +
            ", cityState='" + getCityState() + "'" +
            ", region='" + getRegion() + "'" +
            ", country='" + getCountry() + "'" +
            "}";
    }
}
