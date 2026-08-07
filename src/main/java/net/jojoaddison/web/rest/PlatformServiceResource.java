package net.jojoaddison.web.rest;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.jojoaddison.domain.PlatformService;
import net.jojoaddison.repository.PlatformServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tech.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing {@link net.jojoaddison.domain.PlatformService}.
 */
@RestController
@RequestMapping("/api/platform-services")
public class PlatformServiceResource {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformServiceResource.class);

    private final PlatformServiceRepository platformServiceRepository;

    public PlatformServiceResource(PlatformServiceRepository platformServiceRepository) {
        this.platformServiceRepository = platformServiceRepository;
    }

    /**
     * {@code GET  /platform-services} : get all the Platform Services.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and the list of Platform Services in body.
     */
    @GetMapping("")
    public List<PlatformService> getAllPlatformServices() {
        LOG.debug("REST request to get all PlatformServices");
        return platformServiceRepository.findAll();
    }

    /**
     * {@code GET  /platform-services/:id} : get the "id" platformService.
     *
     * @param id the id of the platformService to retrieve.
     * @return the {@link ResponseEntity} with status {@code 200 (OK)} and with body the platformService, or with status {@code 404 (Not Found)}.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PlatformService> getPlatformService(@PathVariable("id") String id) {
        LOG.debug("REST request to get PlatformService : {}", id);
        Optional<PlatformService> platformService = platformServiceRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(platformService);
    }

    private <T> void updateIfPresent(Consumer<T> setter, T value) {
        if (value != null) {
            setter.accept(value);
        }
    }
}
