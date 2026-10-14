package com.travelplatform.common.saga;

public enum SagaExecutionStatus {
    PENDING,
    SUCCEEDED,
    COMPENSATING,
    COMPENSATED,
    FAILED
}
