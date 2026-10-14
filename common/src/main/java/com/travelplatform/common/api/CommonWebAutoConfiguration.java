package com.travelplatform.common.api;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

/**
 * Loads the global exception handler into servlet web applications only, so
 * the reactive gateway (WebFlux) does not pull servlet MVC classes.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.web.bind.MethodArgumentNotValidException")
@Import(GlobalExceptionHandler.class)
public class CommonWebAutoConfiguration {
}
