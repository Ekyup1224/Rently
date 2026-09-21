package mn.innex.stay.common.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import mn.innex.stay.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * How malformed input is reported.
 *
 * <p>The distinction these pin down is whose fault a failure was. A request the
 * caller could not have succeeded with is a 4xx; a 5xx tells clients, proxies and
 * CDNs to retry, and pages whoever watches the error log. Getting that backwards
 * is cheap to do — an unhandled exception type silently becomes a 500 — and the
 * cost only shows up once something is scanning URLs in production.
 */
class GlobalExceptionHandlerTest extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("a path variable that is not a UUID is the caller's error, not ours")
    void unparseableUuidIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/listings/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"));
    }

    @Test
    @DisplayName("the response names the offending parameter but never echoes its value")
    void namesTheParameterWithoutEchoingTheValue() throws Exception {
        // The value is caller-supplied, so reflecting it back out is a habit worth
        // not forming, whatever this particular endpoint would do with it. The
        // probe stays inside one path segment: a value containing a slash is a
        // different URL, matches no route, and would 404 before conversion runs.
        mockMvc.perform(get("/api/v1/listings/reflected-sentinel-9f3a"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("propertyId")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("reflected-sentinel-9f3a"))));
    }

    @Test
    @DisplayName("a query parameter of the wrong type is a 400 as well")
    void unparseableQueryParameterIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/listings/search").param("size", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("bad_request"));
    }

    @Test
    @DisplayName("a well-formed UUID for a listing that does not exist is still a 404")
    void wellFormedButUnknownIdIsNotFound() throws Exception {
        // Guards the fix from overreaching: "unparseable" and "no such row" are
        // different answers, and only the first one belongs to the new handler.
        mockMvc.perform(get("/api/v1/listings/01a081c9-0000-7000-8000-0000000c0ffe"))
                .andExpect(status().isNotFound());
    }
}
