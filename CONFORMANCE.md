# Conformance

Scenario-by-scenario status of this SDK against the LogTide SDK contract.
Each scenario ID is stable across all official SDKs; "n/a" entries explain
why a scenario does not apply. TODO entries are tracked work.

| ID | Scenario | Status | Test reference |
|---|---|---|---|
| C01 | basic log: one POST to /api/v1/ingest with X-API-Key, {logs:[...]} body, RFC 3339 time, metadata.sdk | ✅ | `LogTideClientHttpTest` (path/headers/levels), `SpecTier1Test` (sdk stamp) |
| C02 | batch by size: batchSize entries flush automatically, order preserved | ✅ | `LogTideClientHttpTest` (batch) |
| C03 | batch by interval: entries delivered without explicit flush | partial | periodic flush job; no dedicated test |
| C04 | wire format strictness: SDK fields nested in metadata, only contract fields top-level | ✅ | `ScopeTest` (top-level span/session ids), models serialization tests |
| C05 | exception capture: structured metadata.exception with type/message/language/frames/cause | ✅ | `LogTideClientHttpTest` (canonical exception key, cause, language) |
| C06 | exception chain cap: cause depth ≤ 10, no infinite loop on cycles | ✅ | IdentityHashMap cycle guard (`serializeError`) |
| C07 | retry on 5xx with growing backoff | ✅ | `LogTideClientHttpTest` (retry on 500, backoff) |
| C08 | no retry on permanent 4xx (400/401/403/413) | ✅ | `LogTideClientHttpTest` (no retry on 400) |
| C09 | Retry-After overrides computed backoff | ✅ | `LogTideClientHttpTest` (retry-after overrides backoff) |
| C10 | circuit breaker opens after threshold failures | ✅ | `CircuitBreakerTest`, `LogTideClientHttpTest` |
| C11 | circuit breaker half-open probe and recovery | ✅ | `LogTideClientHttpTest` (recovery after reset) |
| C12 | buffer cap: drops beyond maxBufferSize, counted, never throws | ✅ | buffer drop policy + metrics (`LogTideClientTest`) |
| C13 | flush on close; capture after close is a silent no-op | ✅ | `LogTideClientHttpTest` (close flushes remaining logs) |
| C14 | DSN parsing incl. base path; invalid DSN fails at init | ✅ | `SpecTier1Test` (`Dsn.parse`, fromDsn, init errors) |
| C15 | inbound traceparent lands on entry trace_id | ✅ | `LogTideInterceptorTest`, `LogTidePluginTest`, `LogTideFilterTest` |
| C16 | no PII by default; API key never logged | ✅ | explicit-only user context (`ScopeTest`) |
| C17 | serialisation robustness: circular/unserialisable values never throw | partial | kotlinx serializer fallback; add cyclic-metadata test |
| C18 | timestamp fidelity: time reflects capture, not delivery | ✅ | time from `LogEntry` creation |
| C20 | scope isolation across concurrent requests | ✅ | `ScopeTest` (threads), `LogTidePluginTest` (per-request) |
| C21 | breadcrumb ring buffer eviction, oldest first | ✅ | `ScopeTest` (ring buffer eviction) |
| C22 | beforeSend can mutate or drop entries | TODO | beforeSend hook not implemented |
| C23 | sampling: rate 0 sends nothing (logs) / no-op spans (traces) | TODO | sampling not implemented |
| C24 | OTLP span export with service.name resource | TODO | span export: planned via opentelemetry-java exporter preset |
| C25 | outbound traceparent injection on instrumented HTTP clients | TODO | outbound traceparent interceptor not implemented |
| C26 | log/trace correlation: active span ids on entries | partial | scope trace context on entries; no span manager |
| C27 | middleware error capture rethrows after logging | ✅ | middleware rethrow after logging (interceptor/filter tests) |
| C28 | logging-bridge level mapping and scope context | ✅ | `logtide-logback` `LogTideAppenderTest` (level mapping, MDC) |
