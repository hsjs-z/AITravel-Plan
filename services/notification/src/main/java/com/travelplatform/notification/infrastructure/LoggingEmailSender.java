package com.travelplatform.notification.infrastructure;

import com.travelplatform.notification.application.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** v1 stand-in for the email channel: logs the outbound mail instead of sending it. */
@Component
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String userId, String title, String body) {
        log.info("email to user {}: [{}] {}", userId, title, body);
    }
}
