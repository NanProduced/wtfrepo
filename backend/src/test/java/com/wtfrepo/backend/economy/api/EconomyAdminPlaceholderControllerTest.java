package com.wtfrepo.backend.economy.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wtfrepo.backend.shared.json.JsonUtils;
import com.wtfrepo.backend.shared.security.UserBanPolicy;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = EconomyAdminPlaceholderController.class)
class EconomyAdminPlaceholderControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private UserBanPolicy userBanPolicy;

  @MockBean private JsonUtils jsonUtils;

  @BeforeEach
  void setUpUserBanPolicy() {
    when(userBanPolicy.findActiveBan(anyString())).thenReturn(Optional.empty());
  }

  @Test
  void adminPlaceholders_shouldReturnNotImplemented() throws Exception {
    mockMvc
        .perform(post("/api/v1/admin/economy/grant-bug").with(jwt().jwt(jwt -> jwt.subject("admin_1"))))
        .andExpect(status().isNotImplemented())
        .andExpect(jsonPath("$.code").value("ADMIN_CONTRACT_PENDING"));

    mockMvc
        .perform(
            post("/api/v1/admin/betting/force-settle")
                .with(jwt().jwt(jwt -> jwt.subject("admin_1"))))
        .andExpect(status().isNotImplemented())
        .andExpect(jsonPath("$.code").value("ADMIN_CONTRACT_PENDING"));

    mockMvc
        .perform(
            post("/api/v1/admin/betting/house-config")
                .with(jwt().jwt(jwt -> jwt.subject("admin_1"))))
        .andExpect(status().isNotImplemented())
        .andExpect(jsonPath("$.code").value("ADMIN_CONTRACT_PENDING"));
  }
}
