# state-machine.md (v0.1)

## 1. State Enum

- Unknown
- Healthy
- Degraded
- Critical
- Impacted

### Semantics

- Unknown — insufficient data
- Healthy — no signals above Info
- Degraded — warning threshold exceeded
- Critical — critical threshold exceeded
- Impacted — state caused by upstream Critical

```text
State priority: Critical > Degraded > Impacted > Healthy > Unknown
```

## 2. Evaluation Model

- State is fully recomputed on each evaluation cycle.
- State = f(signals within sliding window)
- No internal hidden flags.

## 3. Sliding Window Parameters

For each component:

- evaluationWindow = 5 minutes (default)
- recoveryWindow = 2 minutes (default)
- criticalThreshold = 3 signals
- degradedThreshold = 3 signals

All configurable per component (future-ready).

## 4. Transition Rules (Count-Based)

```text
Inputs per component (evaluation window):
  c = count(critical signals)
  w = count(warning signals)
```

### Enter Critical

If in evaluation window:

- count(critical signals) >= criticalThreshold

-> state = Critical

### Enter Degraded

If:

- count(warning signals) >= degradedThreshold
- AND
- count(critical signals) < criticalThreshold

-> state = Degraded

### Recover to Healthy

If:

- count(critical signals) == 0
- AND
- count(warning signals) < degradedThreshold
- AND
- no new signals during recoveryWindow

-> state = Healthy

### Unknown

If:

- no signals ever received
- OR
- evaluationWindow empty

```text
Pseudo-evaluation:
  if no_signals_ever || window_empty: state = Unknown
  else if c >= criticalThreshold: state = Critical
  else if w >= degradedThreshold: state = Degraded
  else if c == 0 && w < degradedThreshold && quiet_for(recoveryWindow): state = Healthy
```

## 5. Impacted (Handled Outside State Engine)

Impacted is not derived from signals.

Component becomes Impacted if:

- upstream component is Critical
- component itself is not Critical

Impacted does NOT override local Critical.

Priority order:

`Critical > Degraded > Impacted > Healthy > Unknown`

## 6. Determinism Principle

Given the same set of signals and configuration:

State must always resolve to the same value.

- No randomness.
- No time-of-day logic.
- No LLM.
- No hidden memory.

## 7. Hysteresis Principle

Entry thresholds and recovery rules are asymmetric.

This prevents flapping.
