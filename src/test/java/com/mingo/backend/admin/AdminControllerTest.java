package com.mingo.backend.admin;

import com.mingo.backend.admin.dto.AdminStatsResponse;
import com.mingo.backend.common.security.CustomUserDetailsService;
import com.mingo.backend.common.security.JwtService;
import com.mingo.backend.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the real Spring Security filter chain (not a mocked/disabled one) actually
 * enforces {@code hasRole("ADMIN")} on {@code /admin/**}, since the unit-level AdminServiceTest
 * cannot exercise that layer at all.
 */
@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    // JwtAuthenticationFilter and SecurityConfig both need these as constructor dependencies.
    @MockBean
    private JwtService jwtService;
    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private AdminStatsResponse sampleStats() {
        return new AdminStatsResponse(1, 1, 1, 1, 1, 1, List.of(1L));
    }

    @Test
    void adminEndpoint_rejectsRequest_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/admin/stats"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(roles = "USER")
    void adminEndpoint_returns403_whenAuthenticatedAsRegularUser() throws Exception {
        mockMvc.perform(get("/admin/stats"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminEndpoint_returns200_whenAuthenticatedAsAdmin() throws Exception {
        when(adminService.getStats()).thenReturn(sampleStats());

        mockMvc.perform(get("/admin/stats"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void listUsers_returns403_whenAuthenticatedAsRegularUser() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void listUsers_returns200_whenAuthenticatedAsAdmin() throws Exception {
        when(adminService.listUsers(any(), any())).thenReturn(Page.empty());

        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void exportAuditLog_returns403_whenAuthenticatedAsRegularUser() throws Exception {
        mockMvc.perform(get("/admin/audit-log/export"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void exportAuditLog_returns200_whenAuthenticatedAsAdmin() throws Exception {
        when(adminService.exportAuditLog(any(), any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/admin/audit-log/export"))
                .andExpect(status().isOk());
    }
}
