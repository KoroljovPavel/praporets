package io.praporets.controlplane.api;

import io.praporets.controlplane.api.dto.FlagResponse;
import io.praporets.controlplane.api.dto.VariantDto;
import io.praporets.controlplane.domain.ValueType;
import io.praporets.controlplane.service.FlagService;
import io.praporets.controlplane.service.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.node.BooleanNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Веб-слайс без БД: HTTP-контракт FlagController + переклад помилок у
 * RFC 9457. Сервіс — мок; його семантику ганяє RevisionAndAuditFlowTest.
 */
@WebMvcTest(controllers = FlagController.class)
class FlagApiValidationTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    FlagService flags;

    @Test
    void invalid_flag_key_is_rejected_as_problem_json() throws Exception {
        mvc.perform(post("/api/v1/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "Bad_Key",
                                  "name": "Bad",
                                  "valueType": "BOOLEAN",
                                  "variants": [{"key": "on", "value": true}]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void create_returns_201_with_location() throws Exception {
        when(flags.create(any(), anyString())).thenReturn(new FlagResponse(
                UUID.randomUUID(), "checkout.new-flow", "New checkout", null, ValueType.BOOLEAN,
                false, 0, Instant.parse("2026-07-27T00:00:00Z"),
                List.of(new VariantDto("on", BooleanNode.TRUE), new VariantDto("off", BooleanNode.FALSE))));

        mvc.perform(post("/api/v1/flags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "key": "checkout.new-flow",
                                  "name": "New checkout",
                                  "valueType": "BOOLEAN",
                                  "variants": [{"key": "on", "value": true}, {"key": "off", "value": false}]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/flags/checkout.new-flow")))
                .andExpect(jsonPath("$.key").value("checkout.new-flow"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void unknown_flag_is_404_problem_with_detail() throws Exception {
        when(flags.get("ghost")).thenThrow(new NotFoundException("flag 'ghost' not found"));

        mvc.perform(get("/api/v1/flags/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", containsString("ghost")));
    }

    @Test
    void patch_without_if_match_is_400() throws Exception {
        mvc.perform(patch("/api/v1/flags/checkout.new-flow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Renamed"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
