# Quiz Leaderboard System — Bajaj Finserv Health Internship

A Java solution for the **Quiz Leaderboard** backend integration problem.

## Problem Summary

Poll a validator API **10 times**, collect quiz event scores, **deduplicate** entries by `(roundId + participant)`, aggregate total scores per participant, and submit a sorted leaderboard once.

## Key Challenge

The same API response data may appear in multiple polls. **Duplicate events must be ignored** using the composite key `roundId + participant`.

## Solution Design

```
Poll 0..9 (5s delay between each)
  └─► GET /quiz/messages?regNo=<regNo>&poll=<n>
        └─► Extract events[]
              └─► Deduplicate by "roundId::participant" key
                    └─► Accumulate unique scores

Aggregate total score per participant
Sort leaderboard by totalScore (descending)
POST /quiz/submit → { regNo, leaderboard[] }
```

## How to Run

### Prerequisites
- Java 11 or higher (uses `java.net.http` — **no external dependencies**)

### Steps

```bash
# 1. Clone the repo
git clone https://github.com/YOUR_USERNAME/YOUR_REPO.git
cd YOUR_REPO

# 2. Set your registration number in QuizLeaderboard.java
#    → Change REG_NO = "YOUR_REG_NO"

# 3. Compile
javac QuizLeaderboard.java

# 4. Run (takes ~50 seconds due to 5s delay × 9 gaps)
java QuizLeaderboard
```

### Expected Output

```
=== Bajaj Finserv Health – Quiz Leaderboard System ===
Registration No: 2024CS101

[Poll 0/9] Fetching data...
  Status: 200
  Body  : { "regNo": "2024CS101", "setId": "SET_1", ...}
  [NEW]  Recorded: roundId=R1 participant=Alice score=10
  [NEW]  Recorded: roundId=R1 participant=Bob score=20
  Waiting 5 seconds...
...
[Poll 9/9] Fetching data...
  [DUP]  Ignored duplicate: roundId=R1 participant=Alice score=10

=== Leaderboard ===
  Bob                  -> 120
  Alice                -> 100
Grand Total: 220

=== Submitting leaderboard ===
Submit Status: 200
Submit Response: { "isCorrect": true, "isIdempotent": true, ... }
```

## Implementation Details

| Requirement | Implementation |
|---|---|
| 10 polls (0–9) | `for (int poll = 0; poll < 10; poll++)` |
| 5s mandatory delay | `Thread.sleep(5000)` between polls |
| Deduplication | `Map<String, Integer> seen` keyed by `"roundId::participant"` |
| Score aggregation | `scores.merge(participant, score, Integer::sum)` |
| Sorted leaderboard | `.sorted((a, b) -> b.getValue() - a.getValue())` |
| Submit once | Single `POST /quiz/submit` after all polls complete |
| No external libraries | Pure Java 11 `java.net.http.HttpClient` |

## Technology

- **Language**: Java 11+
- **HTTP Client**: `java.net.http.HttpClient` (built-in, zero external deps)
- **JSON Parsing**: Manual string parsing (no Jackson/Gson needed)

## API Reference

| Endpoint | Method | Description |
|---|---|---|
| `/quiz/messages?regNo=X&poll=N` | GET | Fetch quiz events for poll N |
| `/quiz/submit` | POST | Submit final leaderboard |

Base URL: `https://devapigw.vidalhealthtpa.com/srm-quiz-task`
