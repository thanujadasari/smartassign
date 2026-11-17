package com.smartassign.ml;

import com.smartassign.common.JsonSupport;
import com.smartassign.config.AppProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Cached health of the suitability service and registration of model metadata in model_metadata.
 * The supervisor UI only ever sees ONLINE / OFFLINE / DISABLED.
 */
@Service
public class SuitabilityServiceStatus {

    public enum State { ONLINE, OFFLINE, DISABLED }

    private final MlClient client;
    private final JdbcTemplate jdbc;
    private final JsonSupport json;
    private final int cacheSeconds;
    private volatile State cached;
    private volatile String cachedVersion;
    private volatile Instant checkedAt = Instant.EPOCH;

    private final org.springframework.transaction.support.TransactionTemplate newTx;

    public SuitabilityServiceStatus(MlClient client, JdbcTemplate jdbc, JsonSupport json, AppProperties props,
                                    org.springframework.transaction.PlatformTransactionManager txManager) {
        this.client = client;
        // Model registration is a WRITE. state() is called from read-only transactions (e.g. the dashboard),
        // so the write always runs in its own transaction and can never break the caller.
        this.newTx = new org.springframework.transaction.support.TransactionTemplate(txManager);
        this.newTx.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.jdbc = jdbc;
        this.json = json;
        this.cacheSeconds = props.ml() == null ? 30 : Math.max(5, props.ml().healthCacheSeconds());
    }

    public State state() {
        if (!client.enabled()) return State.DISABLED;
        if (cached == null || checkedAt.plusSeconds(cacheSeconds).isBefore(Instant.now())) {
            refresh();
        }
        return cached;
    }

    public synchronized void refresh() {
        Optional<MlContract.HealthResponse> h = client.health();
        boolean ok = h.isPresent() && "ok".equalsIgnoreCase(h.get().status()) && Boolean.TRUE.equals(h.get().modelLoaded());
        String newVersion = ok ? h.get().modelVersion() : null;
        if (ok && newVersion != null && !newVersion.equals(cachedVersion)) {
            registerModel();
        }
        cached = ok ? State.ONLINE : State.OFFLINE;
        cachedVersion = newVersion;
        checkedAt = Instant.now();
    }

    /** Internal diagnostics (admin only). */
    public Map<String, Object> internalDetails() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("state", state());
        out.put("checkedAt", checkedAt);
        out.put("modelVersion", cachedVersion);
        client.modelInfo().ifPresent(info -> out.put("modelInfo", info));
        return out;
    }

    void registerModel() {
        try {
            newTx.executeWithoutResult(tx -> doRegisterModel());
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(SuitabilityServiceStatus.class)
                    .warn("Could not record model metadata: {}", e.getMessage());
        }
    }

    private void doRegisterModel() {
        client.modelInfo().ifPresent(info -> {
            Object version = info.get("model_version");
            if (version == null) return;
            jdbc.update("""
                            INSERT INTO model_metadata (model_version, model_family, task, target, feature_list, metrics_json, trained_at)
                            VALUES (?, ?, ?, ?, ?, ?, CAST(? AS timestamptz))
                            ON CONFLICT (model_version) DO UPDATE SET last_seen_at = now(),
                                metrics_json = EXCLUDED.metrics_json, feature_list = EXCLUDED.feature_list""",
                    String.valueOf(version), str(info.get("model_family")), String.valueOf(info.getOrDefault("task", "BINARY_CLASSIFICATION")),
                    str(info.get("target")), json.write(info.get("features")), json.write(info.get("metrics")),
                    str(info.get("trained_at")));
        });
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
