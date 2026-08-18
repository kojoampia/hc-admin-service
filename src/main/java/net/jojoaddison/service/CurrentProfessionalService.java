package net.jojoaddison.service;

import java.util.Optional;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Who is calling, as a {@link Professional}.
 *
 * <p>This service exists so that the self-service endpoints have exactly one way to answer that
 * question, and so that the answer can never come from the request. Every parameter a client can
 * set — path variable, query parameter, header, body — is capable of naming somebody else, and the
 * data behind these endpoints is what people are paid. The only input here is the authenticated
 * subject.
 *
 * <p><b>The link is {@code Profile.account_id}, which carries the gateway login.</b> This service
 * runs {@code skipUserManagement: true} and has no route to any gateway's user collection, so the
 * login in the token is the whole of the identity available to it — see {@link
 * ProfileRepository#findByAccount}. That the login is what {@code account_id} holds is a
 * cross-stack convention rather than a constraint this service can enforce: hc-professional's
 * {@code OnboardingService} sets it from the JWT subject for the same reason, because the token
 * exposes no user id. If a {@code uid} claim is ever added, both sides move together or the link
 * breaks in a way that presents as "this clinician has no roster" rather than as an error.
 *
 * <p>An unlinked caller is {@link Optional#empty()}, never an exception and never a fallback to
 * some other professional. A signed-in user with no profile, or a profile with no professional
 * record, is an ordinary state during onboarding — the applicant holds an account before anyone has
 * created the clinical record it will eventually point at.
 */
@Service
public class CurrentProfessionalService {

    private static final Logger LOG = LoggerFactory.getLogger(CurrentProfessionalService.class);

    private final ProfileRepository profileRepository;

    public CurrentProfessionalService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    /**
     * The professional record belonging to the authenticated caller, if there is one.
     *
     * @return the caller's {@link Professional}, or empty when nobody is authenticated, the login
     *     matches no profile, or the profile is not linked to a professional.
     */
    public Optional<Professional> currentProfessional() {
        return SecurityUtils.getCurrentUserLogin().flatMap(this::professionalFor);
    }

    private Optional<Professional> professionalFor(String login) {
        Optional<Professional> professional = profileRepository.findByAccount(login).map(Profile::getProfessional);
        if (professional.isEmpty()) {
            // Logged at debug and not at warn: this is the normal state for every account that is
            // not a clinician, and for a clinician still being onboarded.
            LOG.debug("Login {} resolves to no professional record", login);
        }
        return professional;
    }
}
