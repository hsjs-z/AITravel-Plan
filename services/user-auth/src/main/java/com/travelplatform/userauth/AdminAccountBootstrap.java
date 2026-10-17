package com.travelplatform.userauth;

import com.travelplatform.userauth.domain.PasswordHasher;
import com.travelplatform.userauth.domain.User;
import com.travelplatform.userauth.domain.UserRepository;
import com.travelplatform.userauth.domain.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Creates or promotes the administrator account from environment variables.
 * The password is hashed and never logged.
 */
@Component
public class AdminAccountBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final String adminEmail;
    private final String adminPassword;

    public AdminAccountBootstrap(UserRepository userRepository,
                                 PasswordHasher passwordHasher,
                                 @Value("${auth.admin.email:}") String adminEmail,
                                 @Value("${auth.admin.password:}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (isBlank(adminEmail) && isBlank(adminPassword)) {
            return;
        }
        if (isBlank(adminEmail) || isBlank(adminPassword)) {
            log.warn("administrator bootstrap skipped: both ADMIN_EMAIL and ADMIN_PASSWORD are required");
            return;
        }

        User existing = userRepository.findByEmail(adminEmail.trim()).orElse(null);
        if (existing != null) {
            if (existing.getRole() != UserRole.ADMIN) {
                existing.setRole(UserRole.ADMIN);
                userRepository.save(existing);
                log.info("promoted existing account to administrator: {}", adminEmail);
            }
            return;
        }

        User admin = new User();
        admin.setEmail(adminEmail.trim());
        admin.setPasswordHash(passwordHasher.hash(adminPassword));
        admin.setRole(UserRole.ADMIN);
        admin.setCreatedAt(Instant.now());
        userRepository.save(admin);
        log.info("created administrator account: {}", adminEmail);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
