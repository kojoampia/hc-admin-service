package net.jojoaddison.service.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import net.jojoaddison.domain.enumeration.PhotoType;

/**
 * A DTO for the {@link net.jojoaddison.domain.Photo} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class PhotoDTO implements Serializable {

    private String id;

    @NotNull
    private String description;

    private String altText;

    @NotNull
    private String url;

    @NotNull
    private String profileId;

    @NotNull
    private PhotoType photoType;

    @NotNull
    private byte[] data;

    private String dataContentType;

    private String photoMetadata;

    private String createdBy;

    @NotNull
    private Instant createdDate;

    @NotNull
    private String modifiedBy;

    @NotNull
    private Instant modifiedDate;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAltText() {
        return altText;
    }

    public void setAltText(String altText) {
        this.altText = altText;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getProfileId() {
        return profileId;
    }

    public void setProfileId(String profileId) {
        this.profileId = profileId;
    }

    public PhotoType getPhotoType() {
        return photoType;
    }

    public void setPhotoType(PhotoType photoType) {
        this.photoType = photoType;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public String getDataContentType() {
        return dataContentType;
    }

    public void setDataContentType(String dataContentType) {
        this.dataContentType = dataContentType;
    }

    public String getPhotoMetadata() {
        return photoMetadata;
    }

    public void setPhotoMetadata(String photoMetadata) {
        this.photoMetadata = photoMetadata;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public String getModifiedBy() {
        return modifiedBy;
    }

    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    public Instant getModifiedDate() {
        return modifiedDate;
    }

    public void setModifiedDate(Instant modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PhotoDTO)) {
            return false;
        }

        PhotoDTO photoDTO = (PhotoDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, photoDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "PhotoDTO{" +
            "id='" + getId() + "'" +
            ", description='" + getDescription() + "'" +
            ", altText='" + getAltText() + "'" +
            ", url='" + getUrl() + "'" +
            ", profileId='" + getProfileId() + "'" +
            ", photoType='" + getPhotoType() + "'" +
            // The payload itself is never rendered — a base64 image in a log line is megabytes of
            // noise, and it is user-supplied content that has no business in an operator's terminal.
            ", data='" + (getData() == null ? "null" : "[" + getData().length + " bytes]") + "'" +
            ", dataContentType='" + getDataContentType() + "'" +
            ", photoMetadata='" + getPhotoMetadata() + "'" +
            ", createdBy='" + getCreatedBy() + "'" +
            ", createdDate='" + getCreatedDate() + "'" +
            ", modifiedBy='" + getModifiedBy() + "'" +
            ", modifiedDate='" + getModifiedDate() + "'" +
            "}";
    }
}
