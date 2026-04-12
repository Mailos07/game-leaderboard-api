package com.leaderboard.utils;

import com.leaderboard.models.ScoreEntry;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles all DynamoDB interactions.
 *
 * We use the low-level DynamoDB client (not the enhanced client) because
 * it gives us more control over key construction and query expressions,
 * which matters a lot for the composite sort key pattern we're using.
 */
public class DynamoDbHelper {

    private final DynamoDbClient client;
    private final String tableName;

    // Score is inverted so that DynamoDB's default ascending sort gives us
    // highest scores first. Max score of 9,999,999 is assumed.
    private static final int MAX_SCORE = 9999999;

    public DynamoDbHelper() {
        this.client = DynamoDbClient.builder()
                .region(Region.US_EAST_1)
                .build();
        this.tableName = System.getenv("TABLE_NAME");
    }

    /**
     * Writes a score to DynamoDB.
     *
     * Key trick: we INVERT the score in the sort key so that a default
     * ascending Query returns highest scores first.
     *   e.g., score=9500 → inverted=0000500 (9999999 - 9500 = 9999499, padded)
     *        score=100  → inverted=9999899
     * So when DynamoDB sorts ascending: 0000500 comes before 9999899 → higher score first.
     */
    public void putScore(ScoreEntry entry) {
        int invertedScore = MAX_SCORE - entry.getScore();
        String paddedInverted = String.format("%07d", invertedScore);

        Map<String, AttributeValue> item = new HashMap<>();
        // Main table keys
        item.put("PK", AttributeValue.fromS("GAME#" + entry.getGameId()));
        item.put("SK", AttributeValue.fromS("SCORE#" + paddedInverted + "#" + entry.getPlayerId() + "#" + entry.getTimestamp()));

        // GSI keys for player lookups
        item.put("GSI1PK", AttributeValue.fromS("PLAYER#" + entry.getPlayerId()));
        item.put("GSI1SK", AttributeValue.fromS("GAME#" + entry.getGameId() + "#" + entry.getTimestamp()));

        // Data attributes
        item.put("playerId", AttributeValue.fromS(entry.getPlayerId()));
        item.put("playerName", AttributeValue.fromS(entry.getPlayerName()));
        item.put("gameId", AttributeValue.fromS(entry.getGameId()));
        item.put("score", AttributeValue.fromN(String.valueOf(entry.getScore())));
        item.put("timestamp", AttributeValue.fromS(entry.getTimestamp()));

        // Conditional write: prevent exact duplicates (same player, game, timestamp)
        client.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .conditionExpression("attribute_not_exists(PK) AND attribute_not_exists(SK)")
                .build());
    }

    /**
     * Queries the top N scores for a given game.
     *
     * Because the sort key uses inverted scores, a simple ascending query
     * returns the highest scores first — no need for ScanIndexForward=false.
     */
    public List<Map<String, Object>> getLeaderboard(String gameId, int limit) {
        QueryRequest request = QueryRequest.builder()
                .tableName(tableName)
                .keyConditionExpression("PK = :pk AND begins_with(SK, :prefix)")
                .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.fromS("GAME#" + gameId),
                        ":prefix", AttributeValue.fromS("SCORE#")
                ))
                .limit(limit)
                .build();

        QueryResponse response = client.query(request);

        List<Map<String, Object>> results = new ArrayList<>();
        int rank = 1;
        for (Map<String, AttributeValue> item : response.items()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("rank", rank++);
            entry.put("playerId", item.get("playerId").s());
            entry.put("playerName", item.get("playerName").s());
            entry.put("score", Integer.parseInt(item.get("score").n()));
            entry.put("timestamp", item.get("timestamp").s());
            results.add(entry);
        }
        return results;
    }

    /**
     * Finds a specific player's position in a game's leaderboard.
     *
     * This is a two-step process:
     * 1. Query all scores for the game (up to a reasonable limit)
     * 2. Find the player's position and return nearby entries
     *
     * In production you'd use a more efficient approach (like maintaining
     * a rank counter), but this works fine for our demo scale.
     */
    public Map<String, Object> getPlayerRank(String gameId, String playerId) {
        // Get all scores for this game (capped at 1000 for demo purposes)
        List<Map<String, Object>> allScores = getLeaderboard(gameId, 1000);

        Map<String, Object> result = new LinkedHashMap<>();
        int playerIndex = -1;

        for (int i = 0; i < allScores.size(); i++) {
            if (allScores.get(i).get("playerId").equals(playerId)) {
                playerIndex = i;
                break;
            }
        }

        if (playerIndex == -1) {
            result.put("found", false);
            result.put("message", "Player not found in this game's leaderboard");
            return result;
        }

        result.put("found", true);
        result.put("playerRank", playerIndex + 1);
        result.put("totalPlayers", allScores.size());
        result.put("playerEntry", allScores.get(playerIndex));

        // Nearby players: 2 above and 2 below
        int start = Math.max(0, playerIndex - 2);
        int end = Math.min(allScores.size(), playerIndex + 3);
        result.put("nearby", allScores.subList(start, end));

        return result;
    }

    /**
     * Gets all scores for a specific player across all games (via GSI1).
     */
    public List<Map<String, Object>> getPlayerHistory(String playerId) {
        QueryRequest request = QueryRequest.builder()
                .tableName(tableName)
                .indexName("GSI1")
                .keyConditionExpression("GSI1PK = :pk")
                .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.fromS("PLAYER#" + playerId)
                ))
                .build();

        QueryResponse response = client.query(request);

        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, AttributeValue> item : response.items()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("gameId", item.get("gameId").s());
            entry.put("playerName", item.get("playerName").s());
            entry.put("score", Integer.parseInt(item.get("score").n()));
            entry.put("timestamp", item.get("timestamp").s());
            results.add(entry);
        }
        return results;
    }

    /**
     * Deletes a specific score by gameId and scoreId (the full sort key).
     */
    public boolean deleteScore(String gameId, String scoreId) {
        try {
            client.deleteItem(DeleteItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of(
                            "PK", AttributeValue.fromS("GAME#" + gameId),
                            "SK", AttributeValue.fromS(scoreId)
                    ))
                    .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
