package com.travelplatform.common.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wires the outbox relay for every service that declares an {@link OutboxStore}.
 * Scheduling is enabled here because the relay is the only scheduled infrastructure
 * the platform installs everywhere; services with their own schedulers are unaffected.
 */
@AutoConfiguration
@EnableScheduling
@ConditionalOnClass(name = {
        "org.springframework.data.jpa.repository.JpaRepository",
        "org.springframework.kafka.core.KafkaTemplate"
})
public class CommonOutboxAutoConfiguration {

    @Bean
    @ConditionalOnBean(OutboxStore.class)
    @ConditionalOnMissingBean(OutboxRecorder.class)
    public OutboxRecorder outboxRecorder(OutboxStore store, ObjectProvider<ObjectMapper> objectMapperProvider) {
        ObjectMapper mapper = objectMapperProvider.getIfAvailable(
                () -> new ObjectMapper().findAndRegisterModules());
        return new OutboxRecorder(store, mapper);
    }

    @Bean
    @ConditionalOnBean(OutboxStore.class)
    @ConditionalOnClass(KafkaTemplate.class)
    @ConditionalOnMissingBean(OutboxPublisher.class)
    public OutboxPublisher kafkaOutboxPublisher(KafkaTemplate<Object, Object> kafkaTemplate) {
        return new KafkaOutboxPublisher(kafkaTemplate);
    }

    @Bean
    @ConditionalOnBean({OutboxStore.class, OutboxPublisher.class})
    @ConditionalOnMissingBean(OutboxRelay.class)
    public OutboxRelay outboxRelay(OutboxStore store,
                                   OutboxPublisher publisher,
                                   @Value("${outbox.relay.batch-size:100}") int batchSize) {
        return new OutboxRelay(store, publisher, batchSize);
    }
}
