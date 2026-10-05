# Proactive Market Structure Automation Spec (Production Grade Mobile Edition)

Implementation blueprint for cancellation-first, pre-staged long and short crypto trade automation across balanced and imbalanced regimes.

Prepared as an implementation-ready specification for proactive market-structure automation natively executed on client mobile hardware (iOS/Android), including symmetric campaign libraries, proactive asymmetric overlays, and proactive monitoring and cancellation logic.

---

## Table of Contents
* **Part I: System Overview & Mobile Philosophy**
  * 1. System Purpose & Philosophy
  * 2. Zero-Cloud Mobile Architecture & Hardware Guardrails
  * 3. API Key Security, Trade Sessions & Orphaned Orders
* **Part II: Data Architecture & Universe Screening**
  * 4. Mandatory Data Sourcing & Cross-Platform Validation
  * 5. Data Throttling & Stale Data Expiration
  * 6. Universe Discovery & Mid-Cap Screener Layer
* **Part III: Market Analysis & Modeling**
  * 7. Core Objects & Timeframe Responsibilities
  * 8. Level Detection & Path Modeling
  * 9. Regime Classification & Multi-Horizon Bias
  * 10. Coin-Specific Calibration
  * 11. Generic Formulas & Scoring Examples
* **Part IV: The Campaign Library & Asymmetric Staging**
  * 12. Complete Campaign Taxonomy
  * 13. Core Campaign Templates
  * 14. Tactical Versus Strategic Bias Separation
  * 15. Bias Score Model
  * 16. Proactive Asymmetric Board Templates
  * 17. Primary, Secondary, and Defensive Board Policy
* **Part V: Execution, Sizing & Conflict Resolution (Entry)**
  * 18. Campaign Generation Algorithm
  * 19. Campaign State Machine
  * 20. Position Sizing & Exposure Model
  * 21. Entry Ladder Construction
  * 22. Proactive Order Architecture
  * 23. Conflict Resolution and Net Exposure
  * 24. Manual Overrides & Real-Time Guardrails
* **Part VI: Monitoring & Invalidation (Exit)**
  * 25. Monitoring Loop & Live Outputs
  * 26. Proactive Monitoring for Asymmetric Boards
  * 27. Invalidation & Cancellation Engine
  * 28. Side-Agnostic Invalidation Model
  * 29. Capital Routing Rules for Live Boards
* **Part VII: Engineering Architecture & Operations**
  * 30. Mobile Engineering Architecture & Schemas
  * 31. Event APIs & Pseudocode
  * 32. Unified Monitoring Dashboard Requirements
  * 33. Operational Guardrails
  * 34. Configuration Checklist
  * 35. Engineering Build Checklist
* **Part VIII: Production Deployment & Validation**
  * 36. User-Selectable Production Execution Modes
  * 37. Backtesting, Simulation & Failure Modes
  * 38. Pre-Launch Requirements & Final Design Summary
* **Appendices**
  * Appendix A. Default Field List for a Campaign Object
  * Appendix B. Separated Technology Stacks (Offline-Native)

---

## Part I: System Overview & Mobile Philosophy

### 1. System Purpose & Philosophy
This document specifies a cancellation-first trading automation. Instead of waiting for confirmation and then reacting, the system pre-stages trade campaigns in advance, then cancels or exits when invalidation evidence appears. The design is meant for reusable deployment on any coin, not just one ticker or one exchange.

| Purpose of this document | Translate the proactive trading method into a machine-executable system. | Define inputs, state, rules, order staging, monitoring, cancellation, and safety controls. | Make the output concrete enough for engineering, testing, simulation, and live deployment. |
| :--- | :--- | :--- | :--- |

**System Philosophy:**
* Markets often move to the nearest efficient pain point before revealing whether the larger reversal or continuation is real.
* The best trade is often available before classic confirmation, but only if size is lighter and cancellation rules are strict.
* A campaign is a staged hypothesis, not a single order.
* Each campaign has a map, an entry ladder, invalidation rules, monitoring rules, and a take-profit ladder.
* The system must know when to do nothing. Avoiding bad participation is part of the edge.

| Trading principle | Pre-stage around the most likely path. | Monitor evidence quality in real time. | Cancel aggressively when the market stops behaving as assumed. |
| :--- | :--- | :--- | :--- |

**Non-goals:**
* This is not a price prediction engine.
* This is not a discretionary charting replacement.
* This is not a guarantee of fills, liquidity, or profitability.
* This does not assume any single vendor, exchange, or signal provider beyond the mandated architecture.

### 2. Zero-Cloud Mobile Architecture & Hardware Guardrails
To ensure user privacy and eliminate recurring cloud infrastructure costs, the engine operates 100% locally on the user's mobile hardware (iOS/Android).

**Pre-Flight Hardware & Connectivity Diagnostics:**
To prevent liquidations caused by inadequate hardware, the app runs silent diagnostics before unlocking the "Stage Campaign" capability.
* **Ping / Latency Check:** The app pings the Binance and CoinGlass APIs. If latency exceeds the volatility-adjusted limit, the app disables passive staging and displays a network error.
* **Memory (RAM) Check:** The app verifies the device has sufficient available RAM to process live L2 order book arrays.
* **Thermal & Battery Throttling Check:** If the mobile OS reports that the device is in "Low Power Mode" or is thermally throttled, the app will suppress all new campaigns, warning that the cancellation engine cannot operate safely.

### 3. API Key Security, Trade Sessions & Orphaned Orders
To enforce strict security and operator control, the system utilizes a session-based execution model.
* **Manual Input & Zero Persistence:** On the automatic trading screen, the user must manually input their Binance API key and secret. The system is hard-coded to never save, cache, or store the API key locally, in the browser, or on any cloud database.
* **Session Lifecycle:** The API key is valid strictly for a single active foreground session. When a new session commences, the user must manually enter the key again.
* **Orphaned Order OS Intercept:** Because mobile operating systems suspend background apps, minimizing the app will instantly kill the monitoring loop. If the user attempts to swipe the app away or lock the phone while limit orders are resting, the app triggers a full-screen alert: *"WARNING: Minimizing or closing this app will instantly disconnect your API session and disable the cancellation engine. Your resting limit orders will NOT be automatically managed or cancelled. You must acknowledge that you are now manually responsible for these trades on the Binance platform."*
* **Suspension Wipe:** The app suspends only after the user acknowledges the warning, immediately wiping the API keys from RAM.

---

## Part II: Data Architecture & Universe Screening

### 4. Mandatory Data Sourcing & Cross-Platform Validation
The engine relies on a strictly defined set of mandatory data feeds. To eliminate single-point-of-failure risks and ensure the highest data integrity, the system mandates a cross-platform data architecture.

| Input | Granularity | Source | Priority | Use in automation |
| :--- | :--- | :--- | :--- | :--- |
| Candles | 1m, 5m, 15m, 1h, 4h, 1d | Binance Public Futures API | Mandatory | Structure, regime, support and resistance, ATR, acceptance and rejection behavior |
| Order book | L2 or better | Binance Public Futures API | Mandatory | Resting liquidity, queue movement, spread, depth shock, imbalance |
| Trades | Tick or aggregated | Binance Public Futures API | Mandatory | Taker buy and sell pressure, micro-impulse quality |
| Funding rate | Periodic or predicted | CoinGlass Public Futures API | Mandatory | Crowding and squeeze pressure |
| Open interest | Frequent snapshots | CoinGlass Public Futures API | Mandatory | Whether moves are supported by new positioning or position unwind |
| Basis / premium | Frequent snapshots | CoinGlass Public Futures API | Mandatory | Relative pressure in perps versus spot |
| Liquidation feed | Exchange or aggregator | CoinGlass Public Futures API | Mandatory | Detect forced move completion or continuation risk |
| Liquidity heatmap | Vendor or internal model | CoinGlass Public Futures API | Mandatory | Stop pool and magnet estimation |

**Cross-Platform Validation & Universe Inclusion:**
* **Strict Inclusion Rule:** A coin will absolutely not make it to the final screened list unless it actively has both futures and spot data available on Binance, AND futures data available on CoinGlass.
* **Pre-Processing Validation:** All required data streams must be validated to exist on both systems *before* the symbol is added to the active universe list and before any structural processing takes place.
* **Input Normalization:** All timestamps must be normalized to UTC. Exchange symbols must map to a canonical internal asset identifier.

### 5. Data Throttling & Stale Data Expiration
To prevent battery drain and thermal throttling on the mobile device, the app intelligently segregates and expires data based on its structural utility.
* **Heavy Data (HTF Context) & Staleness Check:** The app stores static Universe components locally (e.g., 1D and 4H historical candles, macro-level maps for major support/resistance bands). Upon app launch, the engine checks the timestamp of this existing heavy data. It calculates the time lapsed since the original download. If the data is determined to be stale or expired based on HTF refresh parameters (e.g., > 4 hours old), the system initiates a fresh background download. If the data is still fresh, the app skips the download to conserve bandwidth and processing power.
* **Dormant Polling:** When the user is browsing the screener, the app pulls lightweight REST API snapshots every 15-30 seconds.
* **Active Delta Mode (Live Trades):** When a user stages a campaign, the app opens a direct WebSocket connection to stream *only* the real-time deltas (tick trades, L2 order book queue movement, and 1m/5m candle updates) required for that active symbol. The socket is immediately closed upon campaign completion or cancellation.

### 6. Universe Discovery & Mid-Cap Screener Layer
The engine continuously screens the liquid mid-cap universe to identify symbols exhibiting tactical and strategic asymmetry. This layer feeds prioritized, directionally biased symbols into the Campaign Engine.

**1. Universe Filtering Boundaries**
The engine filters the perpetual universe using strict guardrails.
* **24h Quote Volume:** $50M to $750M. Isolates mid-caps; excludes mega-caps and illiquid pairs.
* **Order Book Spread:** < 0.10% typical. Ensures passive entry ladders and hard invalidations execute cleanly.
* **Funding History:** Available and active. Required for crowding elasticity calculations.

**2. Pain Score Execution Logic**
Calculates directional Pain Scores to find the path of least resistance based on current market profile and level maps.
* **Bullish Pain Score Components:** Funding rate is deeply negative (shorts paying longs); price is compressing near HTF resistance (trapped early shorts); recent tactical wicks show immediate downside rejection.
* **Bearish Pain Score Components:** Funding rate is highly positive (longs paying shorts); price is heavy near LTF support with rising OI (trapped late longs); tactical attempts to reclaim lost support have failed.

**3. Screener Output**
Outputs a ranked, directional priority list to the Campaign Engine containing `asset_id`, `tactical_bias_score`, `strategic_bias_score`, `primary_pain_path`, and `volatility_regime`.

---

## Part III: Market Analysis & Modeling

### 7. Core Objects & Timeframe Responsibilities

| Object | Description | Key fields |
| :--- | :--- | :--- |
| MarketProfile | Snapshot of current structure across timeframes | asset, timeframe map, ATR, trend score, damage score, crowding score |
| LevelMap | Structured support and resistance inventory | level id, zone type, price band, timeframe, confidence, source |
| PathModel | Most likely routes to next liquidity | candidate path, pain score, efficiency score, path probability |
| Campaign | Executable proactive trade hypothesis | campaign type, entry ladder, stop, targets, status, size budget |
| EvidenceFrame | Current evidence state used for cancellation | OI delta, funding delta, taker imbalance, reclaim quality, rejection quality |
| RiskEnvelope | Allowed exposure boundaries | account risk, symbol limit, total gross, daily stop, concurrency limit |

**Timeframe Responsibilities:**

| Layer | Typical frames | Primary job |
| :--- | :--- | :--- |
| HTF | 4h, 1d | Regime, damage, major support and resistance, major squeeze or unwind zones |
| MTF | 1h, 15m | Decision bands, reclaim zones, breakdown zones, campaign ranking |
| LTF | 5m, 1m | Entry ladder precision, acceptance and rejection quality, cancellation timing |

### 8. Level Detection & Path Modeling
Every asset receives a fresh level map automatically. The automation must never rely on a manually hard-coded level list for production use.
* Detect swing highs and lows over adaptive windows.
* Cluster repeated reaction points into zones rather than single price ticks.
* Mark high-volume nodes and low-volume rejection gaps if volume profile is available.
* Tag recent liquidation pivots and obvious wick extremes.
* Tag untested reclaim areas, failed breakout areas, and breakdown retest areas.
* Score each level by recency, number of touches, reaction magnitude, and timeframe importance.

| Required output from level detection | HTF support bands and resistance bands | LTF pivot, reclaim zone, breakdown zone, and likely stop pools | A confidence score for every zone | A freshness score so stale levels can decay automatically |
| :--- | :--- | :--- | :--- | :--- |

**Path Model:**
Estimates where price is most likely to travel next if it follows the most painful and efficient route.
* **Pain score:** How much trapped positioning or stops exist on that route.
* **Efficiency score:** How direct the route is from current price to the target zone.
* **Cleanliness score:** How clearly the path aligns with market structure.
* **Probability score:** Relative chance that the route occurs first.

### 9. Regime Classification & Multi-Horizon Bias
Before orders are staged, each symbol must be placed into one of four regimes. Regime classification gates which campaigns are allowed, how much size they receive, and how sensitive their cancel logic should be.
* **Bullish continuation:** Higher-timeframe structure is rising or recently reclaimed; pullbacks are being bought; upside continuation is favored.
* **Bearish continuation:** Higher-timeframe structure is declining or recently broken; rallies are being sold; downside continuation is favored.
* **Transition:** The market is damaged, stretched, or rebalancing after a liquidation event or failed impulse. Both sides may be viable but should be weighted asymmetrically.
* **Balance:** The market is rotating inside a range or value area. Mean reversion at edges is favored; continuation campaigns should be size-reduced or disabled until acceptance occurs.

**Multi-Horizon Bias Calculation:**
For every symbol, compute a tactical (MTF) and strategic (HTF) bias score from -100 (Strong Bearish Asymmetry) to +100 (Strong Bullish Asymmetry).
* **Tactical Bias (15m, 1h):** Evaluates Klines, OI delta, near-price liquidity pools, and recent 15m rejections to target feasibility and identify local trap risk.
* **Strategic Bias (4h):** Evaluates Klines, major support/resistance bands, and macro funding extremes to dictate HTF continuation state and constraints.

### 10. Coin-Specific Calibration
Different coins do not move symmetrically. The automation configures side-specific behavior per asset so proactive boards are placed with realistic expectations.
* **Impulse asymmetry:** compare average upward impulse velocity with average downward impulse velocity.
* **Wick asymmetry:** measure how often upside probes versus downside probes reject immediately.
* **Follow-through asymmetry:** estimate the probability that an upside breakout continues versus a downside breakdown continues.
* **Crowding elasticity:** measure how sensitive the coin is to funding or OI extremes on each side.
* **Mean-reversion half-life:** estimate how quickly the coin snaps back after one-sided stretches, separately for upside and downside extremes.
* **Execution profile:** estimate typical slippage, spread expansion, and partial-fill behavior by side and by volatility regime.

### 11. Generic Formulas & Scoring Examples

| Quantity | Illustrative formula |
| :--- | :--- |
| Zone center | (zone_low + zone_high)/2 |
| Zone width | max(config.min_zone_width, ATR_5m x config.atr_zone_multiplier) |
| Distance to level | abs(last_price - zone_center) / ATR_5m |
| Pain score | w1 x crowding + w2 x stop_pool + w3 x trapped_positioning |
| Long invalidation score | sum(weight_i x normalized_signal_i for structural failure, OI contradiction, flow failure, crowding reversal) |
| Campaign priority | path_probability x path_cleanliness x level_confidence x venue_quality |

---

## Part IV: The Campaign Library & Asymmetric Staging

### 12. Complete Campaign Taxonomy
Every symbol must be evaluated against six proactive campaign families. The engine must support mirrored long and short campaign families, regime-based campaign selection, conflict resolution, and consistent monitoring semantics regardless of direction.

**Mirror Rules:**
* **Support long & Resistance short** are structural mirrors. Both assume the market will rotate away from an edge unless the edge is accepted through.
* **Reclaim long & Reclaim fade short** are reclaim mirrors. Both assume that a broken level is being retested and that the outcome will be decided by acceptance versus rejection.
* **Breakout long & Breakdown short** are continuation mirrors. Both assume that once a threshold breaks and is not trapped, the next liquidity pocket becomes the efficient destination.

| Regime | Primary campaigns | Secondary campaigns | Usually disabled | Default size posture |
| :--- | :--- | :--- | :--- | :--- |
| Bullish continuation | Support long, breakout long | Reclaim long | Resistance short unless extreme exhaustion; breakdown short unless structure fails | Normal to aggressive on longs; small tactical shorts only |
| Bearish continuation | Resistance short, breakdown short | Reclaim fade short | Support long unless extreme exhaustion; breakout long unless clear acceptance | Normal to aggressive on shorts; small tactical longs only |
| Transition | Support long and resistance short, plus whichever reclaim campaign matches nearest major level | Breakout long or breakdown short only if threshold is very clear | None fully disabled, but all sizes reduced | Reduced gross exposure; asymmetry allowed by crowding and pain-path model |
| Balance | Support long at lower edge, resistance short at upper edge | Reclaim campaigns only if range edge is broken and retested | Breakout long and breakdown short until acceptance confirms range escape | Smallest size; highest cancel sensitivity |

### 13. Core Campaign Templates

**1. Proactive Support Long:**
* **Objective:** Catch an up-first pain move from support or balance.
* **Location:** Current support shelf or pivot zone.
* **Logic:** Resting bids across support shelf and pivot overlap.
* **Cancel if:** Acceptance below shelf or red candles with rising OI persist (i.e. price down with OI rising, no bounce quality).

**2. Proactive Resistance Short:**
* **Objective:** Sell into a mapped resistance or supply zone before confirmation, expecting rotation back toward pivot, balance midpoint, or lower support.
* **Typical entry ladder:** Lower edge of resistance, midpoint of resistance, upper edge or overshoot zone.
* **Core inputs:** HTF supply zone, LTF local resistance, upper liquidity cluster, stretched deviation from VWAP or value, crowding, and whether the recent path into resistance was impulsive or weak.
* **Hard invalidation:** Acceptance above the resistance zone, improving basis while price rises, persistent taker buying through the zone, or price-up with OI-up and no rejection behavior.
* **Default targets:** Pivot, opposite side of the local balance, next support shelf, and then continuation low if the move graduates into a breakdown.
* **Good signs:** Slower tape through resistance, upper wicks, reduced follow-through after each push, OI expansion into the move, fading taker-buy dominance, and repeated inability to hold above the zone.
* **Bad signs:** Shallow pullbacks only, pullbacks being bought instantly, acceptance above the upper edge, rising basis, or expanding OI with clean upward continuation.

**3. Proactive Reclaim Long:**
* **Objective:** Pre-stage longs around the reclaim threshold so the engine participates before a full acceptance candle sequence appears.
* **Typical entry ladder:** Just below the reclaim level, at the reclaim level, and just above the level on shallow retest or hold.
* **Core inputs:** Broken HTF or LTF level, probability of reclaim based on crowding and path efficiency, exhaustion of the opposing move, and whether dips back below the level are being absorbed.
* **Hard invalidation:** Inability to hold near the reclaim threshold, repeated rejection below the level, price-down with OI-up after reclaim attempts, or the level acting as resistance again.
* **Default targets:** Next intraday resistance, next HTF resistance, upper liquidity cluster, and breakout continuation if the reclaim evolves into a new trend leg.
* **Good signs:** Fast recovery after dips below the level, basis improving, taker-buy imbalance strengthening, OI stable to rising on green candles, and pullbacks holding above the threshold.
* **Bad signs:** Reclaim only by wick and not by hold, failure to retake the level after a dip, taker-buy weakness, and repeated closes back under the zone.

**4. Proactive Reclaim Fade Short:**
* **Objective:** Sell the likely weak bounce into damaged structure.
* **Location:** HTF or MTF reclaim zone above current price.
* **Logic:** Resting offers across damaged HTF reclaim zone.
* **Cancel if:** Price builds above top of reclaim zone with healthy buy support (i.e. price up with OI rising).

**5. Proactive Breakout Long:**
* **Objective:** Capture upside continuation through a resistance threshold before the move becomes extended and before late confirmation traders pile in.
* **Typical entry methods:** Stop entries just above resistance, staged add-on orders through successive breakout bands, or retest bids after a clean break.
* **Core inputs:** Resistance quality, nearby stop and liquidity map above, whether price is compressing under resistance, expanding OI on green candles, improving basis or funding conditions, and taker-buy persistence.
* **Hard invalidation:** Immediate breakout failure, swift reclaim back below the threshold, price-up without supportive OI, or large overhead supply appearing one band above.
* **Default targets:** First upper liquidity cluster, next HTF resistance, overshoot or squeeze extension, and trailing logic once breakout acceptance is established.
* **Good signs:** Compression into resistance, expanding OI as price lifts, aggressive offers being chewed, basis improving, and retests holding above the broken level.
* **Bad signs:** Breakout by wick only, no hold above resistance, immediate reversal on heavy sells, or price failing despite obvious stop pressure above.

**6. Proactive Breakdown Short:**
* **Objective:** Participate in continuation when support actually fails.
* **Location:** Stop entries below support plus optional retest offers.
* **Cancel if:** Break fails and broken support is reclaimed quickly (i.e. downside lacks follow-through).

### 14. Tactical Versus Strategic Bias Separation
The engine maintains two bias states instead of one to handle split conditions where the immediate path of pain points one way while the larger structure points the other.
* **Tactical bias:** driven by 5m, 15m, and 1h structure, near-price liquidity pools, current crowding, current OI behavior, and short-horizon path efficiency.
* **Strategic bias:** driven by 4h and 1D structure, major support and resistance bands, post-event damage or continuation state, and higher-timeframe reclaim or failure context.
* When tactical and strategic bias agree, continuation campaigns can receive the highest priority and size allowance.
* When tactical and strategic bias disagree, the engine must proactively arm both horizons with different size, target ambition, and cancellation sensitivity rather than collapsing them into a single directional opinion.

### 15. Bias Score Model
The automation computes directional asymmetry numerically so campaign weighting is reproducible. A pair of scores from -100 to +100 modifies proactive boards through priority, size multiplier, ladder aggressiveness, target depth, and cancel speed.
* **Candidate inputs for tactical bias:** local trend quality, distance to nearby stop clusters, crowding distortion, intraday OI delta, basis delta, funding extremity, taker imbalance, and short-horizon breakout versus rejection statistics.
* **Candidate inputs for strategic bias:** higher-timeframe swing structure, recent regime transitions, major reclaim state, distance to HTF support and resistance, post-event damage state, and higher-timeframe volume acceptance.
* **Candidate inputs for both:** venue health, index stability, spread quality, and data completeness penalties.

| Score band | Interpretation | Default operational effect |
| :--- | :--- | :--- |
| +60 to +100 | Strong bullish asymmetry | Promote long campaigns to primary, reduce short campaigns to defensive only, relax long cancel sensitivity slightly. |
| +20 to +59 | Moderate bullish asymmetry | Allow both sides but allocate larger size and wider patience to long boards. |
| -19 to +19 | Balanced or mixed | Keep both sides symmetrical, keep size moderate, and rely more on local zone quality. |
| -20 to -59 | Moderate bearish asymmetry | Allow both sides but allocate larger size and wider patience to short boards. |
| -60 to -100 | Strong bearish asymmetry | Promote short campaigns to primary, reduce long campaigns to defensive only, relax short cancel sensitivity slightly. |

### 16. Proactive Asymmetric Board Templates
The automation must never wait for classic confirmation before applying asymmetry. Instead, it must convert directional imbalance into pre-staged order boards, pre-staged cancellation logic, and pre-committed capital routing. The engine publishes ready-to-run board templates keyed to the current bias state so it can stage, cancel, and rotate exposure before the trigger prints.
* **A. Bull-dominant proactive asymmetry:** Primary boards are support long, reclaim long, breakout long. Default behavior is to preload long bids closer to current price, widen tolerance slightly for long-board survival, leave more runner on successful longs, and force shorter holding time for countertrend shorts.
* **B. Bear-dominant proactive asymmetry:** Primary boards are resistance short, reclaim fade short, breakdown short. Default behavior is to preload short offers closer to current price, widen tolerance slightly for short-board survival, leave more runner on successful shorts, and force shorter holding time for countertrend longs.
* **C. Tactical bullish, strategic bearish split:** Primary tactical board is support long or reclaim long near local demand or pivot. Primary strategic board is reclaim fade short or resistance short in the higher-timeframe supply or damaged reclaim zone. Long boards monetize faster and cancel faster; short boards above receive larger reserved capital.
* **D. Tactical bearish, strategic bullish split:** Primary tactical board is resistance short near local supply or local overextension. Primary strategic board is reclaim long or support long at higher-timeframe demand or major recovered level. Short boards monetize faster and cancel faster; long boards below or above receive larger reserved capital.

### 17. Primary, Secondary, and Defensive Board Policy
Asymmetry should route capital by board role. The role assignment is determined before fills occur and is updated only by explicit regime or bias transitions.

| Board role | Purpose | Typical size budget | Cancel sensitivity |
| :--- | :--- | :--- | :--- |
| Primary | Main expression of the current proactive edge | 40% to 60% of symbol risk budget | Slowest to cancel, but still rules-based |
| Secondary | Valid but lower-conviction path or alternate path | 20% to 35% of symbol risk budget | Moderate cancel sensitivity |
| Defensive | Countertrend hedge or exhaustion-only campaign | 5% to 20% of symbol risk budget | Fast cancel sensitivity |
| Disabled | Not allowed under the current state | 0% | No orders may be staged |

---

## Part V: Execution, Sizing & Conflict Resolution (Entry)

### 18. Campaign Generation Algorithm
1. Build the HTF and LTF level map.
2. Rank nearby paths by pain, efficiency, and cleanliness.
3. Select at most one primary near-price campaign and two secondary campaigns.
4. For each selected campaign, create an entry ladder, stop, target ladder, evidence checklist, cancellation conditions, and size budget.
5. Run exposure checks. If the new campaign conflicts with live exposure on the same asset, downgrade size or suppress it.
6. Publish the campaign to the execution engine and alerting layer.

### 19. Campaign State Machine

| State | Meaning | Transition triggers |
| :--- | :--- | :--- |
| Planned | Campaign exists but no orders live yet | Created after level map and path ranking complete |
| Staged | Orders are resting or stop entries are armed | Risk and concurrency checks pass |
| Partially filled | One or more ladder entries are active | Fill events begin |
| Active | Position exists and campaign monitoring is running | At least one fill remains open |
| Reduced | Partial profit or de-risking occurred | Target hit or quality deterioration without full invalidation |
| Cancelled | Orders removed before or after fill because assumption failed | Invalidation threshold reached |
| Completed | Final take-profit or stop reached | Position closed |
| Suppressed | Campaign intentionally disabled by risk layer | Account or market-level guardrail tripped |

### 20. Position Sizing & Exposure Model
Because the method enters early, initial size must be lighter than reactive confirmation trading. Size increases only when the market keeps behaving as assumed or when the campaign reaches a more favorable structural edge.

| Sizing concept | Default rule |
| :--- | :--- |
| Account risk per campaign | 0.25% to 0.75% of account equity, configurable by volatility bucket |
| Initial slice weight | 20% to 40% of full campaign size on first slice |
| Max campaign size | Hard cap by symbol liquidity, volatility bucket, and account tier |
| Concurrent campaigns per symbol | Normally 2, but only 1 primary directional bias may consume meaningful risk |
| Daily stop | Pause new campaigns after 1.5R to 2.0R daily realized loss unless operator override exists |

**Exposure safety rules:**
* Do not allow simultaneous full-size proactive long and full-size breakdown short on the same symbol.
* Fade short and breakdown short may coexist only if their combined worst-case risk remains within the symbol envelope.
* Correlated-asset caps should exist for highly linked coins so the engine does not duplicate the same macro bet across multiple tickers.

### 21. Entry Ladder Construction
Entries must be distributed across zones, not concentrated at a single exact print. The automation should work with market structure bands, volatility, and tick size.

| Rule | Implementation detail |
| :--- | :--- |
| Zone width | Default to max of 0.25 x ATR(5m) and exchange minimum practical tick span |
| Ladder count | Use 2 to 4 slices for passive campaigns and 2 to 3 slices for momentum breakdown campaigns |
| Price spacing | Equal distance or liquidity-aware spacing; allow denser concentration near the strongest structural edge |
| Fill cap | Do not exceed pre-approved campaign notional even if all slices fill immediately |
| Order type | Use passive limits for support/reclaim campaigns and stop-limit or stop-market for breakdown campaigns when needed |

### 22. Proactive Order Architecture
Each active board must publish a full order plan before the move. A board is not considered live unless its entry ladder, invalidation conditions, target ladder, time-to-live, and cancellation gates are all present in the execution state.
1. Map the regime, tactical bias, and strategic bias for the symbol.
2. Assign each campaign family a board role: primary, secondary, defensive, or disabled.
3. Build entry ladders with side-specific aggressiveness based on role. Primary boards can sit closer to price; defensive boards should sit deeper at the edge of exhaustion.
4. Publish stop or invalidation thresholds and target ladders before orders are placed.
5. Attach time-to-live and stale-board rules.
6. Attach cancellation predicates tied to acceptance, rejection failure, flow divergence, trap risk, venue health, and risk-envelope conflict.
7. Stage the orders only after all preconditions and account-level constraints pass.

### 23. Conflict Resolution and Net Exposure
A symmetric system staging long and short campaigns near adjacent zones requires an explicit conflict engine.
* Only one directional campaign family should own the same level from the same timeframe scope at full size.
* If opposite-side campaigns coexist, the engine must rank them by regime priority, level quality, distance to current price, and expected time-to-event. Lower-ranked campaigns should be size-reduced or left in watch-only mode.
* Net exposure caps must be enforced per symbol, sector, and account. The worst-case net stop-out must remain inside configured account-level risk.

### 24. Manual Overrides & Real-Time Guardrails
Any parameter generated by the engine at the time of entering a trade is subject to a manual override mode.
* **Override Authority:** The operator can adjust ladders, sizes, or cancellation thresholds before the order routes to the exchange.
* **Persistent Guardrail Warnings:** If the operator utilizes a manual override that contradicts the engine's calculated rules (e.g., placing a stop outside the structural invalidation zone or exceeding risk limits), the system will not block the trade, but it will persistently display real-time warning messages. These warnings will clearly contrast the user's overridden parameters against the engine's real-time, data-driven plan to ensure the operator is fully aware of the structural risks they are assuming.

---

## Part VI: Monitoring & Invalidation (Exit)

### 25. Monitoring Loop & Live Outputs

| Frequency | What is checked |
| :--- | :--- |
| Every tick or trade batch | Best bid and ask, spread, top-of-book imbalance, trade aggression, fill status |
| Every 1 to 5 seconds | Open interest, basis, funding estimate, liquidation bursts, campaign invalidation score |
| Every closed 1m candle | Micro structure, acceptance and rejection quality, impulse follow-through, ATR refresh |
| Every closed 5m and 15m candle | Zone integrity, path refresh, whether campaigns should be reprioritized or suppressed |

**Minimum Live Monitoring Outputs:**
* Current dominant campaign and probability rank
* All active orders with their campaign ids
* Current invalidation score and top evidence contributors
* Symbol-level risk used versus allowed
* Exchange and data-source health flags

### 26. Proactive Monitoring for Asymmetric Boards
Monitoring must also be proactive. The engine is not waiting for proof to enter; it is watching whether a staged board still deserves to remain alive.
* **Board survival checks:** is the level still fresh, has path efficiency deteriorated, and is the crowding distortion still present?
* **Board degradation checks:** has the opposing side started accepting through the zone, has OI behavior flipped against the thesis, or has the basis or funding picture normalized enough to remove the edge?
* **Board promotion checks:** can a secondary board be upgraded to primary because the originally favored path decayed before trigger?
* **Board cancellation checks:** should unfilled orders be canceled, partially filled positions reduced, or the board quarantined for a cooldown period?
* **Rearm checks:** after a cancellation, what exact conditions allow the same family to be re-staged at the same level or at a newly detected level?

### 27. Invalidation & Cancellation Engine
This is the heart of the proactive method. The system wins by cancelling bad hypotheses early. Invalidation must be explicit, machine-readable, and fast.

| Signal family | Examples of invalidation evidence | Typical reaction |
| :--- | :--- | :--- |
| Structure failure | Support becomes supply, reclaim becomes acceptance, breakdown reclaims immediately | Cancel remaining entries; flatten or reduce active position |
| Positioning contradiction | Price down with OI rising against long campaign, or price up with OI rising through fade short invalidation | Escalate evidence score; shrink holding window |
| Flow failure | No taker response at expected shelf, repeated weak bounces, impulse quality decays | Cancel passive orders and tighten active stop |
| Crowding reversal | Funding or basis changes enough to remove expected pain pressure | Lower path probability and suppress re-entry |
| Liquidity disorder | Spread blowout, abnormal slippage, exchange instability | Suppress all new staging and optionally flatten |

**Evidence scoring framework:**
* Each campaign maintains an invalidation score from 0 to 100.
* Every evidence family contributes weighted points.
* Soft invalidation threshold: default 45. Cancel unfilled orders and reduce any open position by a configured fraction.
* Hard invalidation threshold: default 70. Flatten the campaign immediately and mark the idea stale for a cooldown period.
* Weights are version-controlled and backtestable.

### 28. Side-Agnostic Invalidation Model
All six campaigns must share a common invalidation vocabulary so the engine can reason symmetrically.
* **Acceptance invalidation:** price holds beyond the far edge of a campaign zone in the wrong direction, converting the zone from edge to value.
* **Rejection invalidation:** the expected rejection never materializes and the tape instead prints repeated clean continuation through the zone.
* **Trap invalidation:** a threshold break or reclaim instantly reverses and fails to hold, invalidating continuation logic and often activating the mirrored campaign.
* **Flow invalidation:** OI, basis, funding, or taker-flow behavior contradicts the campaign thesis for a configured persistence window.
* **Time invalidation:** the market remains stagnant inside the zone longer than the campaign time budget, indicating that the pain-path hypothesis has decayed.
* **Conflict invalidation:** another campaign of higher priority is activated on the same symbol and exposure rules do not allow both to remain live.

### 29. Capital Routing Rules for Live Boards
* Reserve symbol-level risk by board role before fill. A primary board should not be starved because defensive boards consumed the envelope first.
* Never allow full-size primary boards in both directions on the same symbol.
* When tactical and strategic boards oppose each other, treat them as different horizons. Tactical boards should use tighter max hold time and faster profit-taking; strategic boards may have broader target ladders and longer persistence.
* As correlation rises across the universe, scale all secondary and defensive boards down first. Primary boards remain last in the queue for capital cuts.
* Apply venue-health penalties before route selection. If one venue is unstable, reduce aggressiveness for all boards that depend on fast fills or clean stop execution.

---

## Part VII: Engineering Architecture & Operations

### 30. Mobile Engineering Architecture & Schemas

| Component | Responsibilities |
| :--- | :--- |
| Market data adapter | Normalize exchange feeds, candle snapshots, and optional crowding inputs |
| Feature engine | Compute ATR, imbalance, taker pressure, OI deltas, regime and damage scores |
| Structure engine | Detect zones, classify reclaim and breakdown areas, update level freshness |
| Path engine | Rank efficient and painful routes to next liquidity |
| Campaign engine | Create, stage, cancel, and update campaigns |
| Execution engine | Place, amend, and cancel orders; reconcile fills; enforce venue limits |
| Risk engine | Account-level and symbol-level guardrails, kill switches, PnL tracking |

**Required Schema Additions for Full Symmetry:**
* `campaign_family`: one of support_long, resistance_short, reclaim_long, reclaim_fade_short, breakout_long, breakdown_short.
* `direction`: long or short, derived but explicitly stored for audit simplicity.
* `regime`: bullish_continuation, bearish_continuation, transition, or balance.
* `cancel_sensitivity`: normalized scalar controlling how much contradictory evidence is needed before the campaign is canceled.
* `zone_role`: support, resistance, reclaim, breakout, breakdown, pivot, or balance_edge.
* `mirror_campaign_id`: optional pointer to the opposite-side campaign that would naturally activate on invalidation.
* `priority_score` and `size_multiplier`: selector outputs so the execution engine can rank and weight campaigns consistently.

### 31. Event APIs & Pseudocode
Internal APIs should be event-driven.

| Event | Required fields |
| :--- | :--- |
| MarketSnapshot | ts, asset, bid, ask, spread, candles, oi, funding, basis, trades_summary |
| LevelMapUpdated | ts, asset, zones[], dominant_pivot, damage_score, confidence_summary |
| CampaignPlanned | campaign_id, asset, type, rank, entry_ladder[], stop, targets[], size_budget, invalidation_rules |
| OrderStateChanged | order_id, campaign_id, venue, side, type, price, qty, status, filled_qty |
| CampaignInvalidated | campaign_id, ts, invalidation_score, top_reasons[], action_taken |
| RiskAlert | ts, scope, severity, metric, threshold, actual, automatic_action |

**Pseudocode for the Automation Loop:**
```python
for each asset in universe:
    snapshot = market_data.get_snapshot(asset)
    profile = feature_engine.build_profile(snapshot)
    levels = structure_engine.update_levels(profile)
    paths = path_engine.rank_paths(profile, levels)
    campaigns = campaign_engine.plan_campaigns(profile, levels, paths)
    campaigns = risk_engine.filter_and_resize(campaigns)
    execution_engine.stage(campaigns)
    live_status = execution_engine.refresh(asset)
    evidence = feature_engine.build_evidence_frame(snapshot, live_status, levels)
    campaign_engine.apply_invalidation(evidence, live_status)
    risk_engine.apply_global_guards(live_status, snapshot)
    telemetry.publish(asset, profile, campaigns, live_status, evidence)
```

### 32. Unified Monitoring Dashboard Requirements
The operator dashboard must be symmetrical. A human should be able to inspect any symbol and understand both the active long-side and short-side campaigns instantly.
* Universe view with campaign ranks by asset.
* Per-asset view with level map, active ladders, invalidation score, and fill status.
* Show all six campaign families as cards with status: staged, partially filled, active, watch-only, canceled, invalidated, or completed.
* Render the current regime, priority score, size multiplier, and worst-case net risk per symbol.
* Display zone maps for support, resistance, reclaim, breakout, and breakdown levels on HTF and LTF simultaneously.
* Show side-agnostic evidence panels: OI delta, basis delta, funding delta, taker imbalance, crowding, acceptance versus rejection flags, and trap probability.
* Provide one-click audit trails listing every order placement, cancel, scale, flatten, and mirrored-campaign activation.
* Risk panel showing gross exposure, correlated exposure, daily PnL, and kill-switch state.
* Venue health panel showing stale feed detection, order reject rates, and latency spikes.
* Operator actions panel for suppress, flatten, widen cooldown, or disable an asset.

### 33. Operational Guardrails

| Guardrail | Automatic action |
| :--- | :--- |
| Data feed stale | Suppress new campaigns and cancel nonessential resting orders |
| Order rejects exceed threshold | Pause venue for that asset and alert operator |
| Spread exceeds volatility-adjusted limit | Disable passive staging and widen cancel aggressiveness |
| Exchange latency spike | Pause stop-entry arming and refresh risk assumptions |
| Daily loss limit hit | Suppress all new campaigns until next session or manual override |
| Unexpected position mismatch | Flatten symbol and lock until reconciliation succeeds |

### 34. Configuration Checklist
* Universe definition and liquidity thresholds
* Timeframes and lookback windows
* ATR and zone width multipliers
* Path scoring weights
* Campaign slice counts and default weight curves
* Soft and hard invalidation thresholds
* Cooldown durations after invalidation
* Per-asset and portfolio risk caps
* Venue-specific order type rules and retry policies
* Alert routing and operator override permissions

### 35. Engineering Build Checklist
1. Implement market data normalization and historical storage.
2. Implement feature engine and publish reproducible feature snapshots.
3. Implement level detection and zone freshness decay.
4. Implement path ranking and campaign generation.
5. Implement order staging and fill reconciliation.
6. Implement invalidation scoring and cancellation engine.
7. Implement risk guardrails and kill switches.
8. Implement dashboards, logging, and alerting.
9. Build historical replay and paper trading harness.
10. Run phased rollout with audit trail on every campaign action.

---

## Part VIII: Production Deployment & Validation

### 36. User-Selectable Production Execution Modes
The production system contains four manually activated operational modes to ensure safe deployment, calibration, and continuous testing natively on the mobile environment.
1. **Shadow mode:** generate campaigns and alerts only; do not place orders.
2. **Paper mode:** simulate order placement and fills against live feed.
3. **Capped live mode:** allow tiny notional with aggressive guardrails and operator supervision.
4. **Scaled live mode:** increase only after stable execution quality and acceptable invalidation behavior are verified.

### 37. Backtesting, Simulation & Failure Modes
* Historical replay must simulate passive fill logic, slippage, partial fills, and cancel latency.
* Evaluation should distinguish path prediction quality from execution quality.
* Every campaign template needs hit rate, average excursion, worst excursion, average time-to-invalidation, and target capture statistics.
* False-positive invalidations and slow invalidations must be measured separately.
* The simulator must support optional metrics being absent to test graceful degradation.
* Measure whether proactive asymmetry improves path capture versus a symmetric-only baseline.
* Measure whether primary-versus-defensive routing reduces drawdown without simply reducing participation.
* Replay split-bias scenarios where tactical and strategic direction disagree and verify that the engine monetizes the tactical leg faster while preserving the strategic board.
* Stress-test stale or missing crowding inputs to ensure the engine degrades gracefully toward a more symmetric posture instead of producing unstable bias swings.
* Test board-promotion logic, board-cancellation logic, and rearm logic independently before combining them.
* Require per-coin calibration reports so engineers can see whether one-sided parameter sets are justified by historical behavior.

**Key Backtest Outputs**

| Metric | Why it matters |
| :--- | :--- |
| Captured move before confirmation | Measures whether proactive staging actually adds edge |
| Invalidation speed | Shows whether bad ideas are being cut early enough |
| Order miss rate | Quantifies trades missed due to passive placement or cancel timing |
| Adverse excursion by campaign | Essential for sizing and stop design |
| PnL by regime bucket | Reveals where the method works or fails structurally |

**Failure Modes Specific to Proactive Asymmetry:**
* **False asymmetry:** the engine overweights a side because crowding or funding data was stale or incomplete.
* **Regime lag:** tactical or strategic bias updates too slowly, leaving the wrong primary board armed after state transition.
* **Board conflict:** a primary board and a defensive board interact badly because stale risk reservations were not released after partial fill.
* **Over-persistence:** a favored board remains active after its edge has normalized, causing unnecessary fills.
* **Under-persistence:** the engine cancels the primary board too quickly and repeatedly misses the intended pain path.
* **Rearm churn:** the same board is staged, canceled, and re-staged too often around a noisy level, increasing fees and execution noise.

### 38. Pre-Launch Requirements & Final Design Summary

**What must be true before scaled live launch:**
* The engine can explain every order in terms of campaign id, level source, and invalidation state.
* Every live cancel or flatten action is logged with reason codes.
* Worst-case concurrent stop-out is visible at all times.
* Operators can freeze the system immediately without ambiguity.

**Final Design Summary:**
A complete production-grade automation for any coin must be understood as three linked layers:
1. First, a symmetric proactive campaign engine defines the full library of support, resistance, reclaim, breakout, and breakdown campaigns.
2. Second, a proactive asymmetric overlay decides which boards should be armed right now, where to place them, how large to make them, and how quickly to cancel them.
3. Third, a proactive monitoring and cancellation engine continuously evaluates board survival, degradation, promotion, and rearm conditions.

Without symmetry the engine is incomplete; without asymmetry it is unrealistic; without proactivity in both layers it will keep arriving late. The difficult part is not placing ladders. The difficult part is evidence-driven cancellation, risk containment, and disciplined suppression when the market is noisy. A good implementation behaves like a careful operator with machine speed: pre-positioned, aware, skeptical, and quick to step aside.

---

## Appendices

### Appendix A. Default Field List for a Campaign Object

| Field | Type | Notes |
| :--- | :--- | :--- |
| `campaign_id` | string | Unique id, immutable |
| `asset` | string | Canonical asset id |
| `campaign_type` | enum | support_long, reclaim_fade_short, breakdown_short |
| `created_ts` | timestamp | UTC |
| `priority_rank` | float | Higher means stage sooner |
| `entry_ladder` | array | Each slice has price, qty, order type, expiry |
| `stop_logic` | object | Soft threshold, hard threshold, price stop if applicable |
| `targets` | array | Take-profit ladder |
| `size_budget` | object | Max notional, max risk, slice weights |
| `invalidation_rules` | array | Machine-readable rules with weights |
| `cooldown_policy` | object | Re-entry lockout after invalidation |
| `status` | enum | planned, staged, active, reduced, cancelled, completed, suppressed |

### Appendix B. Separated Technology Stacks (Offline-Native)
Following the "Zero-Cloud Mobile Architecture" mandate, the technology stack is divided into platform-specific native implementations to ensure 100% local, offline execution.

---

#### 1. Android Native Technology Stack
* **Orchestration & Language**: **Kotlin** is the primary language for system orchestration, utilizing **Kotlin Coroutines** for non-blocking asynchronous tasks.
* **Offline AI Inference**:
  * **TensorFlow Lite (TFLite)** with the **Flex Delegate** to support complex custom operators required for **Path Modeling**.
  * **Hardware Acceleration**: Utilizes the **Android Neural Networks API (NNAPI)** to run inference on the device's NPU or GPU for low-latency calculations.
* **Local Data & Persistence**:
  * **Storage**: **Room Persistence Library** (built on **SQLite**) for managing local state, HTF Market Profiles, and audit trails.
  * **Security**: **Android Keystore System** for session-based API key management, ensuring keys never leave the hardware's Trusted Execution Environment (TEE).
* **Event Handling**: **Kotlin Flow** and **Channels** to process real-time L2 order book deltas and execute the **Cancellation Engine** logic.

---

#### 2. Apple Native Technology Stack
* **Orchestration & Language**: **Swift** is used for orchestration, leveraging **Swift Concurrency (async/await)** for performance-critical execution.
* **Offline AI Inference**:
  * **TensorFlow Lite (TFLite)** integrated via the **Core ML Delegate** to offload **Evidence Scoring** to the **Apple Neural Engine (ANE)**.
  * **Flex Delegate**: Supports a broader range of TensorFlow operations natively on iOS hardware to match the **Market Analysis** spec.
* **Local Data & Persistence**:
  * **Storage**: **CoreData** (utilizing an **SQLite** backend) for high-performance local storage of levels and campaign objects.
  * **Security**: **iOS Keychain** for mandatory session-based API key isolation, with no persistence across app deletions or cloud backups.
* **Event Handling**: **Combine Framework** for reactive streams, managing the high-frequency monitoring loop for **Active Delta Mode** updates.

---

#### Common Hardware Guardrails (Both Platforms)
Both stacks utilize native OS hooks to monitor **Thermal State**, **Battery Level (Low Power Mode)**, and **Available RAM**. If thresholds are breached, the app triggers a full-screen **Orphaned Order OS Intercept** before wiping sensitive session data from memory.
