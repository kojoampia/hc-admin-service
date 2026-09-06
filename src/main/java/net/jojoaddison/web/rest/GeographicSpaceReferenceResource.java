package net.jojoaddison.web.rest;

import java.util.List;
import net.jojoaddison.repository.GeographicSpaceRepository;
import net.jojoaddison.service.dto.GeographicSpaceReferenceDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * Geographic spaces, as reference data for callers who need to turn an id into a place name.
 *
 * <h2>Why this exists, and why it is not generated CRUD</h2>
 *
 * <p>hc-professional stores a {@code geographicSpaceId} on a roster round and has to render a name
 * beside it. The decision recorded in {@code docs/duty-roster-plan.md} § 9 was to give it a narrow
 * purpose-built read rather than to widen an entity surface — and the entity surface it names does
 * not in fact exist: {@code GeographicSpace} had no resource, no DTO and no mapper in this service
 * before this class, so there was nothing to widen. That document describes
 * {@code GET /api/geographic-spaces} as existing generated CRUD gated on {@code ROLE_ADMIN} /
 * {@code ROLE_OPERATOR}. It did not exist at all.
 *
 * <p><b>That citation is a permitted exception, not an oversight — do not delete it to satisfy the
 * decision that appears to forbid it.</b> Decision 11 in {@code docs/duty-roster-resolution.md} § 9.1
 * says the two roster documents are uncited from code, and it means <em>no further citations</em>:
 * this class is named there as the one that exists and stays (settled 2026-09-06, {@code
 * docs/backlog.md} item 13). What the reference above is worth is the divergence it records — the
 * plan's entity surface never existed — which is the half that would be tidied away. It names a path
 * rather than a bare filename, which is the safer of the two conventions in this workspace and is
 * likewise deliberate.
 *
 * <p>This is read-only and structurally so — there is no write mapping here. Should an administrator
 * ever need to edit the tree, the writes fall to {@code SecurityConfiguration}'s blanket
 * {@code /api/** -> ROLE_ADMIN} rule with nothing to relax, because the carve-out below is on
 * {@code GET} alone.
 *
 * <h2>What it discloses, and why that is acceptable</h2>
 *
 * <p>Unlike {@code ProfessionalSelfResource} — which is gated on authentication because it takes no
 * subject at all — this endpoint does take an id, so the question of what an id buys a caller has to
 * be answered rather than dissolved. It buys a place name, the kind of area it is, and the id of the
 * area around it. That is reference data: the same information a map or a postal directory carries,
 * describing an area rather than a person, and identical for every caller who asks. Nothing here is
 * anybody's record, nothing is derived from anybody's record, and there is no id whose existence is
 * a fact about a person — so the probe that {@code 404} would otherwise offer ("does this id exist")
 * answers a question about geography, and the entitlement idiom that {@code /api/duty-roster}'s
 * customer trail needs (403 never an empty list, unknown refused identically to unauthorised) buys
 * nothing here.
 *
 * <p>What made the narrow read the right call is the other direction: the authorities a clinical
 * caller holds are hc-professional's, and this service does not know them. Admitting them by name
 * would copy that list into a fourth repository to drift on its own, which is the argument
 * {@code SecurityConfiguration} already makes for {@code /api/professionals/me/**}. Authentication
 * is therefore the gate, and the projection is what keeps that gate honest as the entity grows.
 */
@RestController
@RequestMapping("/api/geographic-spaces")
public class GeographicSpaceReferenceResource {

    private static final Logger LOG = LoggerFactory.getLogger(GeographicSpaceReferenceResource.class);

    private final GeographicSpaceRepository geographicSpaceRepository;

    public GeographicSpaceReferenceResource(GeographicSpaceRepository geographicSpaceRepository) {
        this.geographicSpaceRepository = geographicSpaceRepository;
    }

    /**
     * {@code GET  /geographic-spaces} : a page of spaces, so a client can build and cache the tree.
     *
     * <p>Paginated like every other list in this service, and covered by {@code PaginationIT}'s
     * sweep the moment it exists — the sweep discovers single-segment {@code /api} paths from the
     * handler mapping, so this needed no entry anywhere. A caller assembling the whole tree pages
     * through it; the collection is small, but "small" is not a property a client should have to
     * assume, and an unbounded read is how nine endpoints in this service last went wrong.
     *
     * @param pageable the pagination information.
     * @return {@code 200 (OK)} with a page of spaces, {@code X-Total-Count} and {@code Link}.
     */
    @GetMapping("")
    public ResponseEntity<List<GeographicSpaceReferenceDTO>> getAllGeographicSpaces(
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get a page of geographic spaces");
        Page<GeographicSpaceReferenceDTO> page = geographicSpaceRepository.findAll(pageable).map(GeographicSpaceReferenceDTO::of);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /geographic-spaces/:id} : one space, named and placed.
     *
     * @param id the id carried on a roster round or a professional's home space.
     * @return {@code 200 (OK)} with the space, or {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<GeographicSpaceReferenceDTO> getGeographicSpace(@PathVariable("id") String id) {
        LOG.debug("REST request to get geographic space : {}", id);
        return ResponseUtil.wrapOrNotFound(geographicSpaceRepository.findById(id).map(GeographicSpaceReferenceDTO::of));
    }
}
