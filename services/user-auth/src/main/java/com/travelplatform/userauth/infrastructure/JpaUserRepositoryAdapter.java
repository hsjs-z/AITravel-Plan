package com.travelplatform.userauth.infrastructure;

import com.travelplatform.userauth.domain.User;
import com.travelplatform.userauth.domain.UserRepository;
import com.travelplatform.userauth.domain.UserRole;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "auth.user-store", havingValue = "mysql", matchIfMissing = true)
public class JpaUserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository repository;

    public JpaUserRepositoryAdapter(UserJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return repository.findByEmail(email).map(this::toDomain);
    }

    @Override
    public Optional<User> findById(String id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public User save(User user) {
        UserJpaEntity entity = user.getId() == null
                ? new UserJpaEntity()
                : repository.findById(user.getId()).orElseGet(UserJpaEntity::new);
        entity.setId(user.getId() == null ? UUID.randomUUID().toString() : user.getId());
        entity.setEmail(user.getEmail());
        entity.setPasswordHash(user.getPasswordHash());
        entity.setPhone(user.getPhone());
        entity.setRole(user.getRole() == null ? UserRole.USER : user.getRole());
        entity.setCreatedAt(user.getCreatedAt());
        return toDomain(repository.save(entity));
    }

    private User toDomain(UserJpaEntity entity) {
        User user = new User();
        user.setId(entity.getId());
        user.setEmail(entity.getEmail());
        user.setPasswordHash(entity.getPasswordHash());
        user.setPhone(entity.getPhone());
        user.setRole(entity.getRole() == null ? UserRole.USER : entity.getRole());
        user.setCreatedAt(entity.getCreatedAt());
        return user;
    }
}
