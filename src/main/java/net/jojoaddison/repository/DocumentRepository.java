package net.jojoaddison.repository;

import net.jojoaddison.domain.Document;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Document entity.
 */
@Repository
public interface DocumentRepository extends MongoRepository<Document, String> {}
