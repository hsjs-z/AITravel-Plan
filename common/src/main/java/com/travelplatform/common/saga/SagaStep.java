package com.travelplatform.common.saga;

/**
 * One step of a saga. {@code execute} performs the forward action and
 * {@code compensate} reverses it; compensation MUST be idempotent.
 */
public interface SagaStep<C> {

    String name();

    void execute(C context);

    void compensate(C context);
}
