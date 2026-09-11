package net.jojoaddison.config;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.testcontainers.containers.KafkaContainer;

public class KafkaTestContainersSpringContextCustomizerFactory implements ContextCustomizerFactory {

    private Logger log = LoggerFactory.getLogger(KafkaTestContainersSpringContextCustomizerFactory.class);

    private static KafkaTestContainer kafkaBean;

    /**
     * Whether this class has asked for a real broker.
     *
     * <p>Pulled out of the customizer so it can be asserted without starting anything —
     * {@code BrokerOptInArchTest} pins that the opt-in still resolves through a meta-annotation, which
     * is the property that made {@code @EmbeddedKafka} on {@code @IntegrationTest} start a container
     * for every test in the repository, and is the property a reader is least likely to expect.
     */
    static boolean wantsBroker(Class<?> testClass) {
        return AnnotatedElementUtils.findMergedAnnotation(testClass, EmbeddedKafka.class) != null;
    }

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
        return (context, mergedConfig) -> {
            ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
            TestPropertyValues testValues = TestPropertyValues.empty();
            if (wantsBroker(testClass)) {
                log.debug("detected the EmbeddedKafka annotation on class {}", testClass.getName());
                log.info("Warming up the kafka broker");
                if (null == kafkaBean) {
                    kafkaBean = beanFactory.createBean(KafkaTestContainer.class);
                    beanFactory.registerSingleton(KafkaTestContainer.class.getName(), kafkaBean);
                }
                testValues = testValues.and(
                    "spring.cloud.stream.kafka.binder.brokers=" +
                        kafkaBean.getKafkaContainer().getHost() +
                        ':' +
                        kafkaBean.getKafkaContainer().getMappedPort(KafkaContainer.KAFKA_PORT)
                );
            }
            testValues.applyTo(context);
        };
    }
}
