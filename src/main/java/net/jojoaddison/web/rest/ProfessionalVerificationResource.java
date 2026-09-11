package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.ProfessionalVerification;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.ProfessionalVerificationRepository;
import net.jojoaddison.service.ProfessionalVerificationService;
import net.jojoaddison.service.dto.ProfessionalVerificationRequest;
import net.jojoaddison.web.rest.errors.BadRequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for {@link net.jojoaddison.domain.ProfessionalVerification}.
 *
 * <p><strong>There is no {@code PUT}, {@code PATCH} or {@code DELETE}, and their absence is the
 * contract.</strong> This is an append-only history: correcting a verification means recording the
 * correcting decision, not editing the record of the wrong one. The generated CRUD shape is what an
 * entity gets by default and it is deliberately not what this has — a history that can be edited is
 * a history that proves nothing.
 *
 * <p>The JHipster contract is otherwise kept: {@code POST} rejects a body carrying an id, and
 * responses go through {@code HeaderUtil}/{@code ResponseUtil}/{@code PaginationUtil}.
 */
@RestController
@RequestMapping("/api/professional-verifications")
public class ProfessionalVerificationResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProfessionalVerificationResource.class);

    private static final String ENTITY_NAME = "directoryProfessionalVerification";

    @Value("${jhipster.clientApp.name:hcAdminService}")
    private String applicationName;

    private final ProfessionalVerificationService verificationService;

    private final ProfessionalVerificationRepository verificationRepository;

    private final ProfessionalRepository professionalRepository;

    public ProfessionalVerificationResource(
        ProfessionalVerificationService verificationService,
        ProfessionalVerificationRepository verificationRepository,
        ProfessionalRepository professionalRepository
    ) {
        this.verificationService = verificationService;
        this.verificationRepository = verificationRepository;
        this.professionalRepository = professionalRepository;
    }

    /**
     * {@code POST  /professional-verifications} : record a verification decision.
     *
     * <p>Takes {@link ProfessionalVerificationRequest} rather than the entity, which is the whole
     * point: {@code recordedAt} and {@code recordedBy} are not on that shape, so there is nothing to
     * send and nothing to overwrite. The response is the stored row, which does carry them.
     *
     * <p>No id is accepted either — it is not on the request, so the JHipster "POST rejects a body
     * with an ID" rule is structural here rather than a check.
     *
     * @param request the decision, naming the professional it is about
     * @return {@code 201 (Created)} and the stored row.
     */
    @PostMapping("")
    public ResponseEntity<ProfessionalVerification> createProfessionalVerification(
        @Valid @RequestBody ProfessionalVerificationRequest request
    ) throws URISyntaxException {
        LOG.debug("REST request to record ProfessionalVerification : {}", request);

        Professional professional =
            professionalRepository
                .findById(request.professionalId())
                // 400 rather than 404: the professional is a field of the thing being created, not the
                // resource being addressed, so this is a bad body and not a missing endpoint.
                .orElseThrow(() -> new BadRequestAlertException("No such professional", ENTITY_NAME, "professionalnotfound"));

        ProfessionalVerification verification = new ProfessionalVerification()
            .status(request.status())
            .method(request.method())
            .reference(request.reference())
            .note(request.note())
            .expiresOn(request.expiresOn());
        verification.setProfessional(professional);

        ProfessionalVerification saved = verificationService.record(verification);
        return ResponseEntity.created(new URI("/api/professional-verifications/" + saved.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, saved.getId()))
            .body(saved);
    }

    /**
     * {@code GET  /professional-verifications} : a page of the whole history.
     *
     * @param pageable the pagination information.
     */
    @GetMapping("")
    public ResponseEntity<List<ProfessionalVerification>> getAllProfessionalVerifications(
        @org.springdoc.core.annotations.ParameterObject Pageable pageable
    ) {
        LOG.debug("REST request to get a page of ProfessionalVerifications");
        Page<ProfessionalVerification> page = verificationRepository.findAll(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }

    /**
     * {@code GET  /professional-verifications/:id} : one recorded decision.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProfessionalVerification> getProfessionalVerification(@PathVariable("id") String id) {
        LOG.debug("REST request to get ProfessionalVerification : {}", id);
        return ResponseUtil.wrapOrNotFound(verificationRepository.findById(id));
    }
}
