package com.smartassign.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end workflow of the three portals against a real PostgreSQL (Testcontainers) with the full Flyway schema.
 * No claims, handlers or users are seeded - every record here is created through the API, as in production.
 * The suitability service is switched off, so this also proves the profile-only fallback.
 * Requires Docker:  mvn verify -Pintegration
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RoutingWorkflowIT {

    static final String STAFF_CODE = "it-staff-code-123";
    static final String PASSWORD = "Test-Password-123";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("smartassign.registration.staff-code", () -> STAFF_CODE);
        r.add("smartassign.ml.enabled", () -> "false");
        r.add("smartassign.jwt.secret", () -> "integration-test-secret-integration-test-secret");
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;

    // ------------------------------------------------------------------ helpers

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test";
    }

    private JsonNode json(ResultActions r) throws Exception {
        return om.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private ResultActions postJson(String url, String tok, String body) throws Exception {
        var req = post(url).contentType(MediaType.APPLICATION_JSON).content(body);
        if (tok != null) req.header("Authorization", "Bearer " + tok);
        return mvc.perform(req);
    }

    private ResultActions putJson(String url, String tok, String body) throws Exception {
        return mvc.perform(put(url).header("Authorization", "Bearer " + tok).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions getAuth(String url, String tok) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + tok));
    }

    private String login(String email) throws Exception {
        return json(postJson("/api/auth/login", null, "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")
                .andExpect(status().isOk())).get("token").asText();
    }

    private String customer() throws Exception {
        String email = unique("customer");
        postJson("/api/auth/register/customer", null, """
                {"fullName":"Asha Customer","email":"%s","phone":"+91 98765 43210","password":"%s"}""".formatted(email, PASSWORD))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String supervisor() throws Exception {
        String email = unique("supervisor");
        postJson("/api/auth/register/staff", null, """
                {"fullName":"Sunil Supervisor","email":"%s","password":"%s","role":"SUPERVISOR","staffCode":"%s"}"""
                .formatted(email, PASSWORD, STAFF_CODE)).andExpect(status().isCreated());
        return login(email);
    }

    private String handler(String expertise, int years, String city) throws Exception {
        String email = unique("handler");
        postJson("/api/auth/register/staff", null, """
                {"fullName":"Hari Handler","email":"%s","password":"%s","role":"HANDLER","staffCode":"%s",
                 "handlerProfile":{"primaryExpertise":"%s","experienceYears":%d,"location":"%s"}}"""
                .formatted(email, PASSWORD, STAFF_CODE, expertise, years, city)).andExpect(status().isCreated());
        return login(email);
    }

    private long submitAutoClaim(String customerToken, String city) throws Exception {
        JsonNode r = json(postJson("/api/customer/claims", customerToken, """
                {"claimType":"AUTO","policyType":"AUTO","policyNumber":"p1001","incidentType":"COLLISION",
                 "incidentDate":"%s","location":"%s","estimatedLoss":150000,"injuryInvolved":false,
                 "thirdPartyInvolved":true,"description":"Rear-ended at a traffic signal; bumper and tail lights damaged."}"""
                .formatted(LocalDate.now().minusDays(2), city)).andExpect(status().isCreated()));
        assertThat(r.at("/claim/status").asText()).isEqualTo("SUBMITTED");
        assertThat(r.at("/claim/priority").isMissingNode()).isTrue();   // priority is internal - not shown to customers
        assertThat(r.at("/claim/policyNumber").asText()).isEqualTo("P1001");   // stored with an upper-case prefix
        return r.at("/claim/id").asLong();
    }

    // ------------------------------------------------------------------ tests

    @Test
    void noSyntheticRecordsAreSeeded() {
        // only the city distance reference table carries data derived from the dataset
        assertThat(jdbc.queryForObject("SELECT count(*) FROM city_distances", Long.class)).isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM claims WHERE source NOT IN ('CUSTOMER_PORTAL','CLAIMCENTER')", Long.class)).isZero();
    }

    @Test
    void securitySeparatesThePortals() throws Exception {
        mvc.perform(get("/api/claims")).andExpect(status().isUnauthorized());
        String cust = customer();
        getAuth("/api/claims", cust).andExpect(status().isForbidden());              // supervisor queue
        getAuth("/api/handler-portal/me", cust).andExpect(status().isForbidden());   // handler portal
        String sup = supervisor();
        getAuth("/api/customer/claims", sup).andExpect(status().isForbidden());
    }

    @Test
    void staffSignupRequiresTheCode() throws Exception {
        postJson("/api/auth/register/staff", null, """
                {"fullName":"X","email":"%s","password":"%s","role":"SUPERVISOR","staffCode":"wrong-code"}"""
                .formatted(unique("x"), PASSWORD)).andExpect(status().isForbidden());
    }

    @Test
    void fullLifecycleCustomerToSupervisorToHandler() throws Exception {
        String cust = customer();
        String sup = supervisor();
        String hand = handler("AUTO", 6, "Mumbai");
        long claimId = submitAutoClaim(cust, "Mumbai");

        // routing is refused before triage
        postJson("/api/routing/" + claimId + "/recommendations", sup, "{}").andExpect(status().isConflict());

        // triage: suggestion, then the supervisor's decision (accepting the suggestion needs no note)
        JsonNode sug = json(getAuth("/api/claims/" + claimId + "/triage-suggestion", sup).andExpect(status().isOk()));
        assertThat(sug.get("severity").asText()).isEqualTo("MEDIUM");
        String decision = """
                {"priority":"%s","severity":"%s","complexity":"%s","fraudRiskScore":%s}"""
                .formatted(sug.get("priority").asText(), sug.get("severity").asText(), sug.get("complexity").asText(), sug.get("fraudRiskScore").asText());
        postJson("/api/claims/" + claimId + "/triage", sup, decision).andExpect(status().isOk());

        // departing from the suggestion without a note is refused
        postJson("/api/claims/" + claimId + "/triage", sup, """
                {"priority":"HIGH","severity":"CRITICAL","complexity":"HIGH","fraudRiskScore":0.9}""").andExpect(status().isUnprocessableEntity());

        // priority: the suggestion for this claim (third party, moderate vehicle loss) is MEDIUM; the supervisor can change it later
        assertThat(sug.get("priority").asText()).isEqualTo("MEDIUM");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/claims/" + claimId + "/priority")
                        .header("Authorization", "Bearer " + sup).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"priority\":\"HIGH\"}")).andExpect(status().isBadRequest());     // reason required
        JsonNode changed = json(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/claims/" + claimId + "/priority")
                .header("Authorization", "Bearer " + sup).contentType(MediaType.APPLICATION_JSON)
                .content("{\"priority\":\"HIGH\",\"reason\":\"Customer is stranded without a vehicle\"}")).andExpect(status().isOk()));
        assertThat(changed.at("/claim/priority/priority").asText()).isEqualTo("HIGH");
        assertThat(changed.at("/claim/priority/suggestedPriority").asText()).isEqualTo("MEDIUM");

        // eligibility + ranking, then approve
        JsonNode rec = json(postJson("/api/routing/" + claimId + "/recommendations", sup, "{}").andExpect(status().isOk()));
        assertThat(rec.get("outcome").asText()).isEqualTo("ELIGIBLE_FOUND");
        assertThat(rec.get("scoringBasis").asText()).isEqualTo("PROFILE_ONLY");
        long runId = rec.get("runId").asLong();
        JsonNode created = json(postJson("/api/assignments", sup, "{\"claimId\":" + claimId + ",\"runId\":" + runId + "}")
                .andExpect(status().isCreated()));
        postJson("/api/assignments/" + created.get("id").asLong() + "/approve", sup, "{}").andExpect(status().isOk());

        // handler sees it, with the supervisor who assigned it
        JsonNode mine = json(getAuth("/api/handler-portal/claims", hand).andExpect(status().isOk()));
        JsonNode first = null;
        for (JsonNode c : mine.get("content")) if (c.get("id").asLong() == claimId) first = c;
        assertThat(first).isNotNull();
        assertThat(first.at("/assignedBy/fullName").asText()).isEqualTo("Sunil Supervisor");

        // customer sees the handler
        JsonNode view = json(getAuth("/api/customer/claims/" + claimId, cust).andExpect(status().isOk()));
        assertThat(view.at("/claim/status").asText()).isEqualTo("ASSIGNED");
        assertThat(view.at("/claim/handler/fullName").asText()).isEqualTo("Hari Handler");
        assertThat(view.at("/claim/fraudRiskScore").isMissingNode()).isTrue();   // internal value never shown

        // handler goes unavailable: reason required
        putJson("/api/handler-portal/availability", hand, "{\"available\":false}").andExpect(status().isUnprocessableEntity());
        putJson("/api/handler-portal/availability", hand, "{\"available\":false,\"reason\":\"Annual leave this week\"}")
                .andExpect(status().isOk());

        // ...so a new claim finds no eligible handler and falls back to manual review
        long second = submitAutoClaim(cust, "Mumbai");
        JsonNode s2 = json(getAuth("/api/claims/" + second + "/triage-suggestion", sup));
        postJson("/api/claims/" + second + "/triage", sup, """
                {"priority":"%s","severity":"%s","complexity":"%s","fraudRiskScore":%s}"""
                .formatted(s2.get("priority").asText(), s2.get("severity").asText(), s2.get("complexity").asText(), s2.get("fraudRiskScore").asText()))
                .andExpect(status().isOk());
        JsonNode rec2 = json(postJson("/api/routing/" + second + "/recommendations", sup, "{}").andExpect(status().isOk()));
        assertThat(rec2.get("outcome").asText()).isEqualTo("NO_ELIGIBLE_HANDLER");
        assertThat(rec2.at("/fallback/claimStatus").asText()).isEqualTo("MANUAL_REVIEW");

        // handler resolves the first claim: workload freed, statistics recorded
        putJson("/api/handler-portal/availability", hand, "{\"available\":true}").andExpect(status().isOk());
        postJson("/api/handler-portal/claims/" + claimId + "/resolve", hand,
                "{\"note\":\"Repair estimate approved and paid to the garage.\"}").andExpect(status().isOk());
        JsonNode me = json(getAuth("/api/handler-portal/me", hand).andExpect(status().isOk()));
        assertThat(me.at("/handler/currentWorkload").asInt()).isZero();
        assertThat(me.at("/handler/resolvedCount").asInt()).isEqualTo(1);
        assertThat(me.at("/handler/successRate").asDouble()).isEqualTo(1.0);

        // everything is in PostgreSQL: timeline + availability log + audit
        assertThat(jdbc.queryForObject("SELECT count(*) FROM claim_status_history WHERE claim_id = ?", Long.class, claimId))
                .isGreaterThanOrEqualTo(4);   // SUBMITTED, UNASSIGNED, ASSIGNED, RESOLVED
        assertThat(jdbc.queryForObject("SELECT count(*) FROM handler_availability_log", Long.class)).isGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE event_type = 'CLAIM_RESOLVED'", Long.class)).isPositive();

        // the dashboard loads (regression: it used to fail inside a read-only transaction)
        getAuth("/api/dashboard/summary", sup).andExpect(status().isOk());
    }

    @Test
    void policyNumberMustStartWithP() throws Exception {
        String cust = customer();
        postJson("/api/customer/claims", cust, """
                {"claimType":"AUTO","policyType":"AUTO","policyNumber":"POL-1001","incidentType":"COLLISION",
                 "incidentDate":"%s","location":"Pune","estimatedLoss":150000,"injuryInvolved":false,
                 "thirdPartyInvolved":false,"description":"Rear-ended at a traffic signal; bumper damaged badly."}"""
                .formatted(LocalDate.now().minusDays(1))).andExpect(status().isBadRequest());
    }

    @Test
    void customersCannotSeeEachOthersClaims() throws Exception {
        String a = customer();
        String b = customer();
        long id = submitAutoClaim(a, "Pune");
        getAuth("/api/customer/claims/" + id, b).andExpect(status().isNotFound());
    }
}
