package net.jojoaddison.service.dto;

import net.jojoaddison.domain.GeographicSpace;

/**
 * A geographic space as reference data: enough to name it and to place it in the tree, and nothing
 * else.
 *
 * <p>A record with a fixed component order, deliberately — the healthy cross-stack seam in this
 * estate ({@code /api/professionals/me/earnings} and its client) works because the DTO is a record
 * whose components are mirrored in the same order on the other side, and this is the second endpoint
 * hc-professional will read from this service.
 *
 * <p><b>The shape is the authorisation argument.</b> The whole entity is these four values today, so
 * the projection buys nothing in bytes; what it buys is that the surface cannot widen by accident.
 * A field added to {@link GeographicSpace} later — a contact, a catchment population, an internal
 * code — does not appear here, and therefore does not silently become readable by every
 * authenticated caller on three stacks the day it is added. Serialising the domain entity, which is
 * what {@code ProfessionalResource} and {@code OrganisationResource} do, would have exactly that
 * property.
 *
 * @param id the space's identifier, as stored on {@code DutyRoster.geographicSpaceId} and
 *     {@code Professional.homeSpaceId}
 * @param name what to call it on screen
 * @param type the kind of area it is — free text here, not an enumeration
 * @param parentId the space that contains this one, or null at the root
 */
public record GeographicSpaceReferenceDTO(String id, String name, String type, String parentId) {
    public static GeographicSpaceReferenceDTO of(GeographicSpace space) {
        return new GeographicSpaceReferenceDTO(space.getId(), space.getName(), space.getType(), space.getParentId());
    }
}
