# ProactiveMS — Specification Compliance Review
**Review date:** 2026-10-06
**Reviewed against:** `SPECIFICATION.md` (repo root, 38 sections + appendices A/B, commit `786ee70`)
**Codebase reviewed:** `app/src/main/java/com/example/**` — 30 Kotlin files, ~6,300 LOC (engines, models, Room persistence, Compose UI, tests)

---

## Verdict

The app is a faithful **demo/UI shell** of the specification. Models, enums, state machines, screen structure, and the invalidation scoring framework map closely to the spec. However, roughly **70% of production mandates are unimplemented**: there is no real market data, no real order execution, no real OS-level hardware/lifecycle hooks, and several spec-critical safety components exist but are never called (dead code).

---

## 1. Implemented (matches spec)

| Spec § | Requirement | Where implemented |
|---|---|---|
| §7, §30, App. A | Core objects: `MarketRegime`, `LevelZone`/`LevelMap`, `CandidatePath`, `Campaign`, `EvidenceFrame`, `RiskEnvelope`; campaign fields incl. `mirrorCampaignId`, `cooldownUntilTs`, priority/size multiplier | `data/model/AssetModels.kt`, `CampaignModels.kt`, `RiskAndVenueModels.kt` |
| §12 | Six campaign families, mirrored long/short | `CampaignFamily` enum |
| §12, §16, §17 | Regime-based board-role assignment incl. split-bias (Transition) handling; size budgets 50%/28%/12% | `CampaignEngine.determineBoardRoles`, `calculateSizeBudget` |
| §19 | 8-state campaign state machine (planned→staged→partially filled→active→reduced→cancelled/completed/suppressed) | `CampaignState` enum |
| §21 | Entry ladders: 3 passive slices across zone; 2-slice stop-limit ladders for breakout/breakdown | `CampaignEngine.buildEntryLadder`, `buildStopLadder` |
| §27 | Invalidation engine: soft(45)/hard(70) thresholds, weighted evidence families, cancel/flatten/cooldown actions | `InvalidationEngine.evaluateCampaign` |
| §24 | Manual override + persistent guardrail warnings contrasting engine plan vs override | `RiskEngine.checkManualOverrideGuardrails`, `ManualOverrideDialog`, `MainViewModel.applyManualOverride` |
| §3 (partial) | Session-scoped API keys held in RAM only; explicit wipe; orphaned-order dialog with the exact spec warning text | `SessionSecurityManager`, `OrphanedOrderWarningDialog`, `MainViewModel.acknowledgeOrphanedOrderWarning` |
| §32 (partial) | Dashboard, Screener, Telemetry, Risk/Guardrails, Simulation/Replay, Audit Trail screens | `ui/screens/*` |
| §36 | Four execution modes: Shadow / Paper / Capped Live / Scaled Live | `ExecutionMode` enum, TopBar mode selector |
| §38 | Worst-case concurrent stop-out always visible; kill switch; audit log with reason codes | `TopBar`, `RiskGuardrailsScreen`, `AuditTrailEntity` |

---

## 2. Critical gaps

### 2.1 Zero real market data — everything is `Random.nextDouble()` (§4, §5, §6)
- `MarketDataRepository` is a hardcoded 6-asset static list. `streamLiveTicks`, `streamOrderBook`, `streamTakerTrades`, and `FeatureEngine.buildEvidenceFrame` are random-number generators.
- **No networking code exists.** Retrofit/OkHttp are declared in `gradle/libs.versions.toml` and `app/build.gradle.kts` but never imported by any source file. Verified: zero `Retrofit`/`OkHttpClient`/`WebSocket`/endpoint usage in `app/src/main`.
- No candles anywhere (ATR values are hardcoded per asset), no funding/OI/basis/liquidation feeds, no UTC timestamp normalization, no canonical asset-id mapping.
- Cross-platform validation (Binance futures+spot ∧ CoinGlass futures before universe inclusion) is not implemented.
- Universe discovery is absent: the §6 mid-cap filter exists only as the unused `CryptoAsset.isMidCapQualified` property. The screener displays the static list; there is no ranked directional output feeding the Campaign Engine.

### 2.2 No execution engine (§22, §25, §29, §33)
- No order placement, amendment, cancellation, or fill reconciliation. `LadderSlice.isFilled` is set by `Math.random() > 0.65` inside `buildEntryLadder`.
- Binance signed endpoints are never called despite the app collecting API keys.
- `ExecutionMode` is a cosmetic label; Shadow/Paper/Capped/Scaled behave identically.
- `RiskEngine.validateCampaignStaging` (kill switch, daily loss stop, gross-exposure cap, Support-Long vs Breakdown-Short conflict) is **never called** — `planCampaigns` stages unconditionally.

### 2.3 No OS-level hardware or lifecycle hooks (§2, §3, Appendix B)
- Zero usage of `PowerManager`, `BatteryManager`, thermal APIs, or `LifecycleObserver`/`ON_PAUSE`/`ON_STOP` in the entire codebase.
- `SessionSecurityManager.updateHardwareDiagnostics()` exists but has **no caller**; the Pre-Flight Diagnostics card displays hardcoded `VenueHealth()` defaults (`binancePingMs=28`, `availableRamMb=3120`, …).
- The orphaned-order intercept is **manual-only** (a top-bar button). It never fires automatically on `ON_PAUSE`/`ON_STOP`, so minimizing the app silently kills the monitoring loop — precisely the disaster §3 is designed to prevent.
- No Android Keystore / TEE usage (Appendix B mandate).

---

## 3. Moderate gaps

| Spec § | Gap |
|---|---|
| §8 | `StructureEngine` synthesizes zones from ATR offsets around current price instead of detecting swing highs/lows, clustering reaction points, volume nodes, or liquidation pivots. `decayZoneFreshness` is real and unit-tested. |
| §9, §15 | Regime and tactical/strategic bias scores are static model fields; no computed multi-horizon bias model and no score-band operational table. |
| §10 | `CoinCalibration` data class is **dead code** — never instantiated. |
| §20, §23 | Conflict-resolution engine (full-size opposing boards, correlation caps, net-exposure enforcement) exists only as one narrow check inside the dead `validateCampaignStaging`. |
| §26 | Board survival/degradation/promotion/rearm: only `checkBoardPromotion` exists — also **dead code**. Rearm logic absent (`REARM_CAMPAIGN` enum value unused). |
| §28 | Time invalidation (`TIME_EXPIRATION`) never evaluated; `mirrorCampaignId` is never set anywhere, so mirror-activation cannot occur. |
| §25, §31 | No event-driven architecture (`MarketSnapshot`, `OrderStateChanged`, `RiskAlert` events); monitoring is a single 1.2 s tick loop with no fill status and no 1m/5m/15m candle-close cadence. |
| §33 | Operational guardrails (stale feed → suppress staging, reject-threshold → pause venue, position mismatch → flatten & lock) are not implemented. |
| §34 | No configuration layer — all thresholds hardcoded as constants. |
| §37 | `SimulationHarness` returns canned metrics per scenario. No historical replay, no slippage/partial-fill/cancel-latency simulation, no symmetric-baseline comparison; 4 of 11 required replay behaviors exist. |
| — | Dead code also includes `RiskEnvelope.isDailyLossExceeded` (only referenced by the dead validator) and `DatabaseRepository.saveCampaign`/`saveCampaigns` (campaigns are never persisted to Room; only assets and audit logs are). |
| — | Nondeterminism: `Math.random()` in production "fill state" (`buildEntryLadder`). |
| — | `metadata.json` declares `MAJOR_CAPABILITY_SERVER_SIDE_GEMINI_API`, but no Gemini/Firebase AI code exists — contrary to the zero-cloud mandate. |

---

## 4. Resolution plan

### Phase 0 — Determinism & wiring (1–2 days, highest leverage per line)
1. Remove all `Random`/`Math.random()` from engine paths; make fills and streams input-driven.
2. Call `validateCampaignStaging` inside `planCampaigns`/staging so kill switch, daily stop, and exposure caps become real.
3. Call `updateHardwareDiagnostics` from a real diagnostics provider; wire `decayZoneFreshness` into the UI loop.

### Phase 1 — Real OS integration (§2, §3; 2–3 days)
4. Add `DefaultLifecycleObserver` in `MainActivity`: on `ON_PAUSE`/`ON_STOP` trigger the orphaned-order dialog when resting orders exist, then wipe keys on acknowledgment.
5. Real diagnostics: `PowerManager.isPowerSaveMode`, `BatteryManager`, `PowerManager.addThermalStatusListener`, `ActivityManager.MemoryInfo` → feed `VenueHealth`; gate "Stage Campaign" behind a passing diagnostic check.
6. Android Keystore for any at-rest need (Appendix B) while keeping session keys RAM-only.

### Phase 2 — Data layer (§4–§6; 4–6 days)
7. Binance Futures REST (klines, depth, aggTrades) + WebSocket (bookTicker / depth-diff / kline) and CoinGlass (funding, OI, basis, liquidations) clients using the already-declared Retrofit/OkHttp dependencies.
8. UTC normalization + canonical `asset_id` mapping; Room persistence for candles/profiles with the >4 h staleness check (§5) — Room is already configured, add entities.
9. Universe discovery: fetch all perps, apply §6 filters via the existing `isMidCapQualified`, require cross-platform data before inclusion; compute Pain Scores into a ranked screener output.

### Phase 3 — Analytics on real data (§8–§11, §15; 3–5 days)
10. Replace `StructureEngine` synthesis with swing detection + zone clustering + freshness scoring; compute regime, tactical/strategic bias, and `CoinCalibration` from history.

### Phase 4 — Execution & invalidation for real (§22, §25–§29, §33; 5–8 days)
11. Binance signed order endpoints: place/amend/cancel ladders; user-data stream for fills → reconcile `LadderSlice` states.
12. Make `ExecutionMode` gate behavior (Shadow = no calls; Paper = internal fills vs live book; Capped = notional cap enforced in validator; Scaled = full).
13. Implement §26 board survival/degradation/promotion/rearm and §28 time & mirror invalidation; wire the §33 guardrail table into the risk engine.

### Phase 5 — Real replay harness (§37; 3–4 days)
14. Historical replay engine simulating fills/slippage/cancel latency; compute metrics instead of hardcoding; add missing scenario tests (stale-crowding degradation, board promotion/rearm isolation).

**Ordering rationale:** Phases 0–1 are small and remove the dangerous illusion of safety. Phases 2–4 convert each screen from mock to live. Phase 5 completes the validation mandate. Nothing in Phases 0–3 requires a backend, consistent with the zero-cloud architecture.

---

## 5. Verification limitations

- No JDK/Android SDK in the review sandbox; compile status is unverified locally. The GitHub Actions workflow (`build-apk.yml`) is the build authority.
- Conformance judgments on spec-mandated *runtime behavior* (fill reconciliation, suspension wipes, replay fidelity) rest on code inspection, not device execution.
