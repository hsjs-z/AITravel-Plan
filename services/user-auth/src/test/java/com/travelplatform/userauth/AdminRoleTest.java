package com.travelplatform.userauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.userauth.domain.UserRepository;
import com.travelplatform.userauth.domain.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminRoleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminBootstrapIsIdempotentAndAdminCanLogin() throws Exception {
        AdminAccountBootstrap bootstrap = new AdminAccountBootstrap(
                userRepository,
                new com.travelplatform.userauth.domain.PasswordHasher(),
                "admin-test@travel.local",
                "AdminPassw0rd!");
        bootstrap.run(null);
        bootstrap.run(null);

        assertEquals(UserRole.ADMIN, userRepository.findByEmail("admin-test@travel.local")
                .orElseThrow().getRole());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin-test@travel.local","password":"AdminPassw0rd!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }
}
