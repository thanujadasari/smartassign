package com.smartassign.ml;

import com.smartassign.config.AppProperties;
import com.smartassign.core.Fixtures;
import com.smartassign.core.HandlerSnapshot;
import com.smartassign.core.ml.SuitabilityScores;
import com.smartassign.domain.ClaimType;
import com.smartassign.domain.Expertise;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the real HTTP client against a tiny in-process server (no Spring context, no Docker). */
class MlClientTest {

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    private MlClient clientReturning(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ml/predict", ex -> {
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort();
        AppProperties props = new AppProperties(null, null, new AppProperties.Ml(true, url, 500, 1000, 30),
                null, null, null, null);
        return new MlClient(props, Fixtures.distances());
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private static List<HandlerSnapshot> two() {
        return List.of(Fixtures.handler(1, Expertise.AUTO, 0, 10), Fixtures.handler(2, Expertise.AUTO, 3, 10));
    }

    @Test
    void validResponseIsAcceptedAndOnlyEligibleCandidatesAreSent() throws IOException {
        MlClient c = clientReturning(200, """
                {"status":"ok","model_version":"v-test","predictions":[
                 {"candidate_id":"1","suitability":0.81},{"candidate_id":"2","suitability":0.12}]}""");
        SuitabilityScores s = c.score(Fixtures.claim(ClaimType.AUTO), two());
        assertThat(s.available()).isTrue();
        assertThat(s.scores()).containsEntry(1L, 0.81).containsEntry(2L, 0.12);
        assertThat(s.modelVersion()).isEqualTo("v-test");
        assertThat(lastBody.get()).contains("\"candidate_id\":\"1\"", "\"candidate_id\":\"2\"", "\"distance_km\"")
                .doesNotContain("claim_id", "adjuster_id", "assignment_delay_hours", "previous_reassignments");
    }

    @Test
    void outOfRangeScoreMakesPredictionsUnavailable() throws IOException {
        MlClient c = clientReturning(200, """
                {"status":"ok","model_version":"v","predictions":[
                 {"candidate_id":"1","suitability":1.7},{"candidate_id":"2","suitability":0.1}]}""");
        assertThat(c.score(Fixtures.claim(ClaimType.AUTO), two()).available()).isFalse();
    }

    @Test
    void missingCandidateMakesPredictionsUnavailable() throws IOException {
        MlClient c = clientReturning(200, """
                {"status":"ok","model_version":"v","predictions":[{"candidate_id":"1","suitability":0.5}]}""");
        assertThat(c.score(Fixtures.claim(ClaimType.AUTO), two()).available()).isFalse();
    }

    @Test
    void serverErrorFallsBackWithoutThrowing() throws IOException {
        MlClient c = clientReturning(500, "{\"detail\":\"boom\"}");
        assertThat(c.score(Fixtures.claim(ClaimType.AUTO), two()).available()).isFalse();
    }

    @Test
    void unreachableServiceFallsBackWithoutThrowing() {
        AppProperties props = new AppProperties(null, null, new AppProperties.Ml(true, "http://127.0.0.1:1", 200, 200, 30),
                null, null, null, null);
        MlClient c = new MlClient(props, Fixtures.distances());
        assertThat(c.score(Fixtures.claim(ClaimType.AUTO), two()).available()).isFalse();
    }
}
