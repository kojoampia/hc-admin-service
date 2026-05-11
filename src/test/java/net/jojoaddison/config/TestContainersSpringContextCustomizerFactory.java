package net.jojoaddison.config;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;

public class TestContainersSpringContextCustomizerFactory implements ContextCustomizerFactory {

    private Logger log = LoggerFactory.getLogger(TestContainersSpringContextCustomizerFactory.class);

    private static MongoDbTestContainer mongoDbBean;

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
        return (context, mergedConfig) -> {
            ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
            log.info("Warming up the mongo database for test context {}", testClass.getName());
            if (null == mongoDbBean) {
                mongoDbBean = beanFactory.createBean(MongoDbTestContainer.class);
                beanFactory.registerSingleton(MongoDbTestContainer.class.getName(), mongoDbBean);
                // ((DefaultListableBeanFactory)beanFactory).registerDisposableBean(MongoDbTestContainer.class.getName(), mongoDbBean);
            }
            String replicaSetUrl = mongoDbBean.getMongoDBContainer().getReplicaSetUrl();
            TestPropertyValues testValues = TestPropertyValues
                .empty()
                .and("spring.data.mongodb.uri=" + replicaSetUrl)
                .and("spring.mongodb.uri=" + replicaSetUrl);
            testValues.applyTo(context);
        };
    }
}
