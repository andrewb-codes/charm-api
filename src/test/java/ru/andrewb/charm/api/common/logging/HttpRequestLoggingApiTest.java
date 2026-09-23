package ru.andrewb.charm.api.common.logging;

import org.junit.jupiter.api.Test;
import ru.andrewb.charm.api.ApiIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HttpRequestLoggingApiTest extends ApiIntegrationTest {

    @Test
    void includesRequestIdWhenSecurityRejectsRequest() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .header("X-Request-Id", "security-rejected-request"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Request-Id", "security-rejected-request"));
    }

    @Test
    void generatesRequestIdForPublicRequest() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"));
    }
}
