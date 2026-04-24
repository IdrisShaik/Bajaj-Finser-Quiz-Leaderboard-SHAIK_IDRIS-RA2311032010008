import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

// Requires Java 11+ (uses java.net.http)
// Run: javac QuizLeaderboard.java && java QuizLeaderboard

public class QuizLeaderboard {

    // =============================================
    //  TODO: Replace with YOUR registration number
    // =============================================
    private static final String REG_NO = "RA2311032010008";

    private static final String BASE_URL = "https://devapigw.vidalhealthtpa.com/srm-quiz-task";
    private static final int TOTAL_POLLS = 10;
    private static final int DELAY_MS = 5000; // 5 seconds mandatory delay

    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        // (roundId + participant) -> score  — deduplication map
        Map<String, Integer> seen = new LinkedHashMap<>();

        System.out.println("=== Bajaj Finserv Health – Quiz Leaderboard System ===");
        System.out.println("Registration No: " + REG_NO);
        System.out.println();

        // ── Step 1 & 2: Poll API 10 times ──────────────────────────────────
        for (int poll = 0; poll < TOTAL_POLLS; poll++) {
            System.out.printf("[Poll %d/%d] Fetching data...%n", poll, TOTAL_POLLS - 1);

            String url = BASE_URL + "/quiz/messages?regNo=" + REG_NO + "&poll=" + poll;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            System.out.println("  Status: " + response.statusCode());
            System.out.println("  Body  : " + response.body());

            if (response.statusCode() == 200) {
                // ── Step 3: Parse & Deduplicate ─────────────────────────────
                parseAndDeduplicate(response.body(), seen);
            } else {
                System.out.println("  [WARN] Non-200 response, skipping poll " + poll);
            }

            // Mandatory 5-second delay (skip after last poll)
            if (poll < TOTAL_POLLS - 1) {
                System.out.println("  Waiting 5 seconds...");
                Thread.sleep(DELAY_MS);
            }
        }

        System.out.println();
        System.out.println("=== Deduplication complete ===");
        System.out.println("Unique (roundId + participant) entries: " + seen.size());
        seen.forEach((k, v) -> System.out.println("  " + k + " -> " + v));
        System.out.println();

        // ── Step 4: Aggregate scores per participant ────────────────────────
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : seen.entrySet()) {
            // Key format: "roundId::participant"
            String participant = entry.getKey().split("::")[1];
            scores.merge(participant, entry.getValue(), Integer::sum);
        }

        // ── Step 5: Sort leaderboard by totalScore descending ───────────────
        List<Map<String, Object>> leaderboard = scores.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("participant", e.getKey());
                    m.put("totalScore", e.getValue());
                    return m;
                })
                .collect(Collectors.toList());

        // ── Step 6: Compute total score across all users ────────────────────
        int grandTotal = scores.values().stream().mapToInt(Integer::intValue).sum();

        System.out.println("=== Leaderboard ===");
        leaderboard.forEach(entry ->
                System.out.printf("  %-20s -> %d%n", entry.get("participant"), entry.get("totalScore")));
        System.out.println("Grand Total: " + grandTotal);
        System.out.println();

        // ── Step 7: Submit leaderboard once ────────────────────────────────
        String submitBody = buildSubmitJson(leaderboard);
        System.out.println("=== Submitting leaderboard ===");
        System.out.println("Payload: " + submitBody);
        System.out.println();

        HttpRequest submitRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/quiz/submit"))
                .POST(HttpRequest.BodyPublishers.ofString(submitBody))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> submitResponse = client.send(submitRequest, HttpResponse.BodyHandlers.ofString());

        System.out.println("Submit Status: " + submitResponse.statusCode());
        System.out.println("Submit Response: " + submitResponse.body());
        System.out.println();
        System.out.println("=== Done ===");
    }

    /**
     * Minimal JSON parser for the quiz API response.
     * Extracts events and deduplicates by (roundId + participant).
     */
    private static void parseAndDeduplicate(String json, Map<String, Integer> seen) {
        // Extract "events" array content
        int eventsStart = json.indexOf("[", json.indexOf("\"events\""));
        int eventsEnd   = json.lastIndexOf("]");
        if (eventsStart == -1 || eventsEnd == -1) return;

        String eventsJson = json.substring(eventsStart + 1, eventsEnd);

        // Split events by "}" to get each event object
        String[] rawEvents = eventsJson.split("\\}");
        for (String raw : rawEvents) {
            raw = raw.trim();
            if (raw.isEmpty() || raw.equals(",")) continue;

            String roundId    = extractValue(raw, "roundId");
            String participant = extractValue(raw, "participant");
            String scoreStr   = extractValue(raw, "score");

            if (roundId == null || participant == null || scoreStr == null) continue;

            int score;
            try {
                score = Integer.parseInt(scoreStr.trim());
            } catch (NumberFormatException e) {
                System.out.println("  [WARN] Could not parse score: " + scoreStr);
                continue;
            }

            String key = roundId + "::" + participant;
            if (seen.containsKey(key)) {
                System.out.printf("  [DUP]  Ignored duplicate: roundId=%s participant=%s score=%d%n",
                        roundId, participant, score);
            } else {
                seen.put(key, score);
                System.out.printf("  [NEW]  Recorded: roundId=%s participant=%s score=%d%n",
                        roundId, participant, score);
            }
        }
    }

    /** Extracts the string or numeric value for a given key from a JSON fragment. */
    private static String extractValue(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx == -1) return null;

        int colon = json.indexOf(":", idx + search.length());
        if (colon == -1) return null;

        String rest = json.substring(colon + 1).trim();
        if (rest.startsWith("\"")) {
            int end = rest.indexOf("\"", 1);
            return end == -1 ? null : rest.substring(1, end);
        } else {
            // numeric value
            int end = 0;
            while (end < rest.length() && (Character.isDigit(rest.charAt(end)) || rest.charAt(end) == '-')) {
                end++;
            }
            return end == 0 ? null : rest.substring(0, end);
        }
    }

    /** Builds the JSON payload for POST /quiz/submit manually (no external deps). */
    private static String buildSubmitJson(List<Map<String, Object>> leaderboard) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"regNo\": \"").append(REG_NO).append("\",\n");
        sb.append("  \"leaderboard\": [\n");

        for (int i = 0; i < leaderboard.size(); i++) {
            Map<String, Object> entry = leaderboard.get(i);
            sb.append("    { \"participant\": \"").append(entry.get("participant"))
              .append("\", \"totalScore\": ").append(entry.get("totalScore")).append(" }");
            if (i < leaderboard.size() - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("  ]\n");
        sb.append("}");
        return sb.toString();
    }
}
