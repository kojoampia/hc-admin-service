package net.jojoaddison.domain;

import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import net.jojoaddison.domain.enumeration.ServiceHealth;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A PlatformService.
 */
@Document(collection = "platform_service")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class PlatformService implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @NotNull
    @Size(max = 60)
    @Field("name")
    private String name;

    @NotNull
    @Size(max = 60)
    @Field("host")
    private String host;

    @NotNull
    @Min(value = 1)
    @Max(value = 65535)
    @Field("port")
    private Integer port;

    @NotNull
    @Size(max = 40)
    @Field("plane")
    private String plane;

    @NotNull
    @Field("health")
    private ServiceHealth health;

    @Min(value = 0)
    @Field("response_ms")
    private Integer responseMs;

    /**
     * When the probe last measured this service, or {@code null} if it never has.
     *
     * <p>Written only by {@code POST /api/platform-services/{id}/probe}. Its absence is the point:
     * {@code health} and {@code responseMs} arrive from the seed or from an operator's hand, and
     * without a timestamp beside them a fixture and a measurement look identical on the screen.
     */
    @Field("last_probed_at")
    private Instant lastProbedAt;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public PlatformService id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public PlatformService name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return this.host;
    }

    public PlatformService host(String host) {
        this.setHost(host);
        return this;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return this.port;
    }

    public PlatformService port(Integer port) {
        this.setPort(port);
        return this;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getPlane() {
        return this.plane;
    }

    public PlatformService plane(String plane) {
        this.setPlane(plane);
        return this;
    }

    public void setPlane(String plane) {
        this.plane = plane;
    }

    public ServiceHealth getHealth() {
        return this.health;
    }

    public PlatformService health(ServiceHealth health) {
        this.setHealth(health);
        return this;
    }

    public void setHealth(ServiceHealth health) {
        this.health = health;
    }

    public Integer getResponseMs() {
        return this.responseMs;
    }

    public PlatformService responseMs(Integer responseMs) {
        this.setResponseMs(responseMs);
        return this;
    }

    public void setResponseMs(Integer responseMs) {
        this.responseMs = responseMs;
    }

    public Instant getLastProbedAt() {
        return this.lastProbedAt;
    }

    public PlatformService lastProbedAt(Instant lastProbedAt) {
        this.setLastProbedAt(lastProbedAt);
        return this;
    }

    public void setLastProbedAt(Instant lastProbedAt) {
        this.lastProbedAt = lastProbedAt;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PlatformService)) {
            return false;
        }
        return getId() != null && getId().equals(((PlatformService) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "PlatformService{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", host='" + getHost() + "'" +
            ", port=" + getPort() +
            ", plane='" + getPlane() + "'" +
            ", health='" + getHealth() + "'" +
            ", responseMs=" + getResponseMs() +
            ", lastProbedAt='" + getLastProbedAt() + "'" +
            "}";
    }
}
