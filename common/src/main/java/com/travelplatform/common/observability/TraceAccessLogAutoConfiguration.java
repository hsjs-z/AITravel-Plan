package com.travelplatform.common.observability;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;

/** Servlet-only access log that attaches the request trace id (see TraceAccessLogFilter). */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(OncePerRequestFilter.class)
public class TraceAccessLogAutoConfiguration {

    @Bean
    public TraceAccessLogFilter traceAccessLogFilter() {
        return new TraceAccessLogFilter();
    }
}
