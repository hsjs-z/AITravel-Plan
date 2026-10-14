package com.travelplatform.payment.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CallbackRecordRepository extends JpaRepository<CallbackRecord, String> {
}
