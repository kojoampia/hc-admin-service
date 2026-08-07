package net.jojoaddison.repository;

import net.jojoaddison.domain.AuditEntry;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the AuditEntry entity.
 */
@Repository
public interface AuditEntryRepository extends MongoRepository<AuditEntry, String> {}
