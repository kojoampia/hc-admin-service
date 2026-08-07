package net.jojoaddison.repository;

import net.jojoaddison.domain.Vendor;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data MongoDB repository for the Vendor entity.
 */
@Repository
public interface VendorRepository extends MongoRepository<Vendor, String> {}
