package net.jojoaddison.repository;

import net.jojoaddison.domain.DocumentItem;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the DocumentItem entity.
 */
@Repository
public interface DocumentItemRepository extends MongoRepository<DocumentItem, String> {}
