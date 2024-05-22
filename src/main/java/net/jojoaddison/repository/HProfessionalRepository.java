package net.jojoaddison.repository;

import net.jojoaddison.domain.HProfessional;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the HProfessional entity.
 */
@SuppressWarnings("unused")
@Repository
public interface HProfessionalRepository extends MongoRepository<HProfessional, String> {}
