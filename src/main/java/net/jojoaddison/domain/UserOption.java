package net.jojoaddison.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * PDF: Option (Category, User ID, Metadata). Renamed to avoid the
 * bare word \"Option\" colliding with TS/Angular vocabulary.
 */
@Schema(
    description = "PDF: Option (Category, User ID, Metadata). Renamed to avoid the\nbare word \"Option\" colliding with TS/Angular vocabulary."
)
@Document(collection = "user_option")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class UserOption implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @NotNull
    @Size(max = 60)
    @Field("category")
    private String category;

    @NotNull
    @Size(max = 60)
    @Field("user_ref")
    private String userRef;

    @Size(max = 500)
    @Field("metadata")
    private String metadata;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public UserOption id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCategory() {
        return this.category;
    }

    public UserOption category(String category) {
        this.setCategory(category);
        return this;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getUserRef() {
        return this.userRef;
    }

    public UserOption userRef(String userRef) {
        this.setUserRef(userRef);
        return this;
    }

    public void setUserRef(String userRef) {
        this.userRef = userRef;
    }

    public String getMetadata() {
        return this.metadata;
    }

    public UserOption metadata(String metadata) {
        this.setMetadata(metadata);
        return this;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserOption)) {
            return false;
        }
        return getId() != null && getId().equals(((UserOption) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "UserOption{" +
            "id=" + getId() +
            ", category='" + getCategory() + "'" +
            ", userRef='" + getUserRef() + "'" +
            ", metadata='" + getMetadata() + "'" +
            "}";
    }
}
