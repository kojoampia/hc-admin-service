package net.jojoaddison.web.rest;

import java.time.LocalDate;
import java.util.List;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.enumeration.EarningsGranularity;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.service.ShiftValuationService;
import net.jojoaddison.service.dto.ProfessionalEarningsDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.PaginationUtil;
import tech.jhipster.web.util.ResponseUtil;

/**
 * What professionals have earned: shifts completed, and what those shifts came to.
 *
 * <p>Separate from {@link ProfessionalResource} rather than bolted onto it. That resource is the
 * generated CRUD contract over the entity, and this is a derived read with its own window and
 * bucketing parameters — the two share a path prefix and nothing else. {@code /earnings} is a
 * literal segment, which outranks {@code /{id}} in path-pattern matching, so the two mappings do not
 * compete.
 */
@RestController
@RequestMapping("/api/professionals")
public class ProfessionalEarningsResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProfessionalEarningsResource.class);

    private final ProfessionalRepository professionalRepository;

    private final ShiftValuationService shiftValuationService;

    public ProfessionalEarningsResource(ProfessionalRepository professionalRepository, ShiftValuationService shiftValuationService) {
        this.professionalRepository = professionalRepository;
        this.shiftValuationService = shiftValuationService;
    }

    /**
     * {@code GET  /professionals/:id/earnings} : shifts completed and value accrued for one
     * professional, as a series.
     *
     * @param id the professional to value.
     * @param granularity {@code DAILY}, {@code WEEKLY} or {@code MONTHLY}; defaults to monthly.
     * @param from start of the window; defaults to a span that fills the series.
     * @param to end of the window; defaults to today, and is clipped to the last date that can have
     *     been worked.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the earnings in body, or
     *     {@code 404 (Not Found)} if there is no such professional.
     */
    @GetMapping("/{id}/earnings")
    public ResponseEntity<ProfessionalEarningsDTO> getProfessionalEarnings(
        @PathVariable("id") String id,
        @RequestParam(name = "granularity", required = false) EarningsGranularity granularity,
        @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LOG.debug("REST request to get earnings for Professional {} by {} from {} to {}", id, granularity, from, to);
        return ResponseUtil.wrapOrNotFound(
            professionalRepository.findById(id).map(professional -> shiftValuationService.earningsFor(professional, granularity, from, to))
        );
    }

    /**
     * {@code GET  /professionals/earnings} : the wage bill, one row per professional.
     *
     * <p>This is the "how much do we pay each professional this week" question. Paginated, because
     * it is one row per professional and that list grows — and because valuing a row costs a query,
     * so an unbounded version would issue one per professional in the network.
     *
     * @param pageable the pagination information.
     * @param granularity the bucket size of the series on each row; defaults to monthly.
     * @param from start of the window.
     * @param to end of the window.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and a page of earnings.
     */
    @GetMapping("/earnings")
    public ResponseEntity<List<ProfessionalEarningsDTO>> getAllProfessionalEarnings(
        @org.springdoc.core.annotations.ParameterObject Pageable pageable,
        @RequestParam(name = "granularity", required = false) EarningsGranularity granularity,
        @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LOG.debug("REST request to get a page of Professional earnings by {} from {} to {}", granularity, from, to);
        Page<Professional> professionals = professionalRepository.findNotArchived(pageable);
        Page<ProfessionalEarningsDTO> page = professionals.map(professional ->
            shiftValuationService.earningsFor(professional, granularity, from, to)
        );
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return ResponseEntity.ok().headers(headers).body(page.getContent());
    }
}
