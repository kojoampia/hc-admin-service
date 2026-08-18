package net.jojoaddison.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configure the converters to use the ISO format for dates by default.
 */
@Configuration
public class DateTimeFormatConfiguration implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        DateTimeFormatterRegistrar registrar = new DateTimeFormatterRegistrar();
        registrar.setUseIsoFormat(true);
        registrar.registerFormatters(registry);
    }

    /**
     * "Now" as an injectable dependency.
     *
     * <p>Whether a shift has been worked is a comparison against today, so any test of the
     * remuneration figures has to be able to say when today is. The alternative — {@code
     * LocalDate.now()} inline — makes those tests depend on the date they are run on: seed a shift
     * for "yesterday" relative to a fixed date and the assertion passes this week and fails next.
     *
     * <p>A test pins the date by declaring its own {@code @Primary} {@code Clock} — see {@code
     * ProfessionalEarningsIT}. Not {@code @ConditionalOnMissingBean}: this is an ordinary
     * {@code @Configuration}, not an auto-configuration, so that condition is evaluated against
     * whatever has been registered by then and would silently depend on definition order.
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
