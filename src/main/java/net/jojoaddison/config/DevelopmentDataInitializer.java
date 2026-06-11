package net.jojoaddison.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import net.jojoaddison.domain.Address;
import net.jojoaddison.domain.AuditLog;
import net.jojoaddison.domain.Contact;
import net.jojoaddison.domain.DutyRoster;
import net.jojoaddison.domain.Facility;
import net.jojoaddison.domain.Organisation;
import net.jojoaddison.domain.Person;
import net.jojoaddison.domain.PricingPlan;
import net.jojoaddison.domain.SystemCatalog;
import net.jojoaddison.domain.Team;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import tech.jhipster.config.JHipsterConstants;

@Component
@Profile({ JHipsterConstants.SPRING_PROFILE_DEVELOPMENT, JHipsterConstants.SPRING_PROFILE_TEST })
public class DevelopmentDataInitializer implements ApplicationRunner {

    private final Logger log = LoggerFactory.getLogger(DevelopmentDataInitializer.class);
    private final MongoTemplate template;
    private final ObjectMapper mapper;

    public DevelopmentDataInitializer(MongoTemplate template, ObjectMapper mapper) {
        this.template = template;
        this.mapper = mapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!args.containsOption("spring.profiles.active")) {
            return;
        }

        List<String> activeProfiles = List.of(args.getOptionValues("spring.profiles.active").get(0).split(","));

        String profile = JHipsterConstants.SPRING_PROFILE_DEVELOPMENT;

        if (activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_TEST)) {
            profile = JHipsterConstants.SPRING_PROFILE_TEST;
        }

        if (
            activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT) ||
            activeProfiles.contains(JHipsterConstants.SPRING_PROFILE_TEST)
        ) {
            log.info("Initializing {} data", profile);
            try (InputStream inputStream = new ClassPathResource("data/hc-admin-ms-data.json").getInputStream()) {
                TypeReference<Map<String, Map<String, List<Map<String, Object>>>>> typeReference = new TypeReference<>() {};
                Map<String, Map<String, List<Map<String, Object>>>> data = mapper.readValue(inputStream, typeReference);
                Map<String, List<Map<String, Object>>> profileData = data.get(profile);

                loadData(profileData, "addresses", Address.class);
                loadData(profileData, "contacts", Contact.class);
                loadData(profileData, "facilities", Facility.class);
                loadData(profileData, "audits", AuditLog.class);
                loadData(profileData, "organisations", Organisation.class);
                loadData(profileData, "persons", Person.class);
                loadData(profileData, "teams", Team.class);
                loadData(profileData, "profiles", net.jojoaddison.domain.HCProfile.class);
                loadData(profileData, "dutyRosters", DutyRoster.class);
                loadData(profileData, "pricingPlans", PricingPlan.class);
                loadData(profileData, "systemCatalogs", SystemCatalog.class);
            } catch (Exception e) {
                log.error("Failed to initialize {} data", profile, e);
            }
        }
    }

    private <T> void loadData(Map<String, List<Map<String, Object>>> data, String entityName, Class<T> clazz) {
        if (template.collectionExists(clazz)) {
            log.info("Collection for {} already exists, skipping data load.", clazz.getSimpleName());
            return;
        }
        List<Map<String, Object>> entityData = data.get(entityName);
        if (entityData != null) {
            entityData.forEach(item -> template.save(mapper.convertValue(item, clazz)));
            log.info("Loaded {} {} entities", entityData.size(), entityName);
        }
    }
}
