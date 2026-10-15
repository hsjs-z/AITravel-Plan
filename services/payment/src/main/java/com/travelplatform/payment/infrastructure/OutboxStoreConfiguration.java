package com.travelplatform.payment.infrastructure;

import com.travelplatform.common.outbox.JpaOutboxStore;
import com.travelplatform.common.outbox.OutboxStore;
import com.travelplatform.payment.domain.OutboxRecordEntity;
import com.travelplatform.payment.domain.OutboxRecordRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboxStoreConfiguration {

    @Bean
    public OutboxStore outboxStore(OutboxRecordRepository repository) {
        return new JpaOutboxStore<>(repository, OutboxRecordEntity::new);
    }
}
