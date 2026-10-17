package com.travelplatform.userauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.userauth.domain.User;
import com.travelplatform.userauth.domain.UserRepository;
import com.travelplatform.userauth.domain.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthPersistenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void registerLoginAndMeKeepUserRole() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String password = "Passw0rd!";

        String registerBody = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","phone":"13800000000"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andReturn().getResponse().getContentAsString();
        JsonNode registered = objectMapper.readTree(registerBody).path("data");
        String userId = registered.path("userId").asText();
        String token = registered.path("token").asText();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.role").value("USER"));

        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(userId))
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void duplicateEmailAndWrongPasswordKeepV1Statuses() throws Exception {
        String email = "duplicate-" + UUID.randomUUID() + "@example.com";
        String body = """
                {"email":"%s","password":"Passw0rd!"}
                """.formatted(email);

        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wrong-password"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void repositoryRoundTripSurvivesEntityManagerClear() {
        User user = new User();
        user.setEmail("roundtrip-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("hash");
        user.setRole(UserRole.USER);
        user.setCreatedAt(Instant.now());

        User saved = userRepository.save(user);
        assertNotNull(saved.getId());

        entityManager.flush();
        entityManager.clear();

        User reloaded = userRepository.findById(saved.getId()).orElseThrow();
        assertEquals(saved.getEmail(), reloaded.getEmail());
        assertEquals(UserRole.USER, reloaded.getRole());
    }

    @Test
    void administratorBootstrapCreatesAndKeepsAdminRole() {
        User admin = userRepository.findByEmail("admin-test@travel.local").orElseThrow();
        assertEquals(UserRole.ADMIN, admin.getRole());
    }
}
