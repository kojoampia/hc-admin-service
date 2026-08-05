package net.jojoaddison.domain;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import net.jojoaddison.domain.enumeration.PhotoType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A Photo.
 *
 * <p>Declared in {@code jdl/admin-db.jdl} and generated into the dashboard, but never into this
 * service — so the Photo screen, a live entry in the entity navigation, called {@code /api/photos}
 * and got a 404 on every list, create, edit and delete. The field set here is taken from
 * {@code web/.jhipster/Photo.json}, which is what the Angular model was generated from; the JDL
 * entity is looser (it has {@code photoData String} where the generated client expects a blob).
 */
@Document(collection = "photo")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Photo implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @NotNull
    @Field("description")
    private String description;

    @Field("alt_text")
    private String altText;

    @NotNull
    @Field("url")
    private String url;

    @NotNull
    @Field("profile_id")
    private String profileId;

    @NotNull
    @Field("photo_type")
    private PhotoType photoType;

    @NotNull
    @Field("data")
    private byte[] data;

    @Field("data_content_type")
    private String dataContentType;

    @Field("photo_metadata")
    private String photoMetadata;

    @Field("created_by")
    private String createdBy;

    @NotNull
    @Field("created_date")
    private Instant createdDate;

    @NotNull
    @Field("modified_by")
    private String modifiedBy;

    @NotNull
    @Field("modified_date")
    private Instant modifiedDate;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public Photo id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescription() {
        return this.description;
    }

    public Photo description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAltText() {
        return this.altText;
    }

    public Photo altText(String altText) {
        this.setAltText(altText);
        return this;
    }

    public void setAltText(String altText) {
        this.altText = altText;
    }

    public String getUrl() {
        return this.url;
    }

    public Photo url(String url) {
        this.setUrl(url);
        return this;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getProfileId() {
        return this.profileId;
    }

    public Photo profileId(String profileId) {
        this.setProfileId(profileId);
        return this;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public PhotoType getPhotoType() {
        return this.photoType;
    }

    public Photo photoType(PhotoType photoType) {
        this.setPhotoType(photoType);
        return this;
    }

    public void setPhotoType(PhotoType photoType) {
        this.photoType = photoType;
    }

    public byte[] getData() {
        return this.data;
    }

    public Photo data(byte[] data) {
        this.setData(data);
        return this;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public String getDataContentType() {
        return this.dataContentType;
    }

    public Photo dataContentType(String dataContentType) {
        this.dataContentType = dataContentType;
        return this;
    }

    public void setDataContentType(String dataContentType) {
        this.dataContentType = dataContentType;
    }

    public String getPhotoMetadata() {
        return this.photoMetadata;
    }

    public Photo photoMetadata(String photoMetadata) {
        this.setPhotoMetadata(photoMetadata);
        return this;
    }

    public void setPhotoMetadata(String photoMetadata) {
        this.photoMetadata = photoMetadata;
    }

    public String getCreatedBy() {
        return this.createdBy;
    }

    public Photo createdBy(String createdBy) {
        this.setCreatedBy(createdBy);
        return this;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedDate() {
        return this.createdDate;
    }

    public Photo createdDate(Instant createdDate) {
        this.setCreatedDate(createdDate);
        return this;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public String getModifiedBy() {
        return this.modifiedBy;
    }

    public Photo modifiedBy(String modifiedBy) {
        this.setModifiedBy(modifiedBy);
        return this;
    }

    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    public Instant getModifiedDate() {
        return this.modifiedDate;
    }

    public Photo modifiedDate(Instant modifiedDate) {
        this.setModifiedDate(modifiedDate);
        return this;
    }

    public void setModifiedDate(Instant modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Photo)) {
            return false;
        }
        return getId() != null && getId().equals(((Photo) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Photo{" +
            "id=" + getId() +
            ", description='" + getDescription() + "'" +
            ", altText='" + getAltText() + "'" +
            ", url='" + getUrl() + "'" +
            ", profileId='" + getProfileId() + "'" +
            ", photoType='" + getPhotoType() + "'" +
            ", data='" + getData() + "'" +
            ", dataContentType='" + getDataContentType() + "'" +
            ", photoMetadata='" + getPhotoMetadata() + "'" +
            ", createdBy='" + getCreatedBy() + "'" +
            ", createdDate='" + getCreatedDate() + "'" +
            ", modifiedBy='" + getModifiedBy() + "'" +
            ", modifiedDate='" + getModifiedDate() + "'" +
            "}";
    }
}
