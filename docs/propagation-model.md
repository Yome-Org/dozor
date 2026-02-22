# propagation-model.md (v0.1)

## 1. Dependency Graph Model

- Directed Acyclic Graph (DAG)
- No cycles allowed in v1
- Validation occurs on component registration

Each edge:

`Upstream -> Downstream`

Downstream depends on Upstream.

```text
Constraint: graph must be acyclic before activation.
```

## 2. Propagation Trigger

Propagation occurs:

- after component state evaluation
- and after any state change

Propagation engine runs separately from state engine.

## 3. Impacted Rule

A component becomes Impacted if:

- exists upstream component in Critical state
- AND
- component's own state != Critical

Impacted does NOT override local Critical.

Priority order:

`Critical > Degraded > Impacted > Healthy > Unknown`

```text
Pseudo-rule:
  if local_state == Critical:
    effective_state = Critical
  else if exists(upstream.state == Critical):
    effective_state = Impacted
  else:
    effective_state = local_state
```

## 4. Recovery from Impacted

When upstream leaves Critical:

Downstream does NOT immediately leave Impacted.

Instead:

Downstream enters recovery observation period.

Duration = recoveryWindow

If during recoveryWindow:

- no upstream returns to Critical
- and local state != Critical

Then downstream state is recalculated:

`-> remove Impacted`

## 5. Root Cause Selection

Root cause component is defined as:

- component.state == Critical
- AND
- no upstream component.state == Critical

Only root components can open incidents.

## 6. Multiple Failures

If two independent components fail:

- each becomes its own root cause
- each opens its own incident

No artificial grouping in v1.

## 7. Deterministic Propagation Order

Propagation follows topological ordering of DAG.

Evaluation order:

1. Evaluate all component states (isolated)
2. Apply propagation pass
3. Persist final states

No partial propagation.

```text
Propagation pass:
  1) build topological order
  2) evaluate impacted overlays in that order
  3) persist final effective state for all touched components
```
