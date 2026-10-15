package com.travelplatform.bookingorder.infrastructure;

import com.travelplatform.bookingorder.domain.OutboxRecordEntity;
import com.travelplatform.bookingorder.domain.OutboxRecordRepository;
import com.travelplatform.common.outbox.JpaOutboxStore;
import com.travelplatform.common.outbox.OutboxStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboxStoreConfiguration {

    @Bean
    public OutboxStore outboxStore(OutboxRecordRepository repository) {
        return new JpaOutboxStore<>(repository, OutboxRecordEntity::new);
    }
}
