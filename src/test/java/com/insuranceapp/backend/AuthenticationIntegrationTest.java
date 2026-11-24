package com.insuranceapp.backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** End-to-end authentication flow against the configured PostgreSQL database and seeded customers. */
@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationIntegrationTest {
    private static final String DEMO_EMAIL = "demo@gmail.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.seed.customer-password}")
    private String seedPassword;

    @Test
    void seededCustomerCanLoginAndSeesOwnProfile() throws Exception {
        String token = login(DEMO_EMAIL);

        mockMvc.perform(get("/api/customers/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(DEMO_EMAIL))
                .andExpect(jsonPath("$.fullName").value("Demo"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void loginIsCaseInsensitiveOnEmail() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body("DEMO@Gmail.com", seedPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(DEMO_EMAIL));
    }

    @Test
    void wrongPasswordAndUnknownEmailGetSameGenericError() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body(DEMO_EMAIL, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body("nobody@example.com", seedPassword)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = login(DEMO_EMAIL);
        int signatureStart = token.lastIndexOf('.') + 1;
        char flipped = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, signatureStart) + flipped + token.substring(signatureStart + 1);

        mockMvc.perform(get("/api/customers/me").header("Authorization", "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    private String login(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(body(email, seedPassword)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.get("accessToken").asText();
    }

    private String body(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", password));
    }
}
