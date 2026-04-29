package com.leaderboard.utils;

import com.leaderboard.models.ScoreEntry;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

public class DynamoDbHelper {

    private final DynamoDbClient client;
    private final String tableName;
    private final String statsTableName;
    private static final int MAX_SCORE = 9999999;

    public DynamoDbHelper() {
        this.client = DynamoDbClient.builder().region(Region.US_EAST_1).build();
        this.tableName = System.getenv("TABLE_NAME");
        this.statsTableName = System.getenv("STATS_TABLE_NAME");
    }

    public void putScore(ScoreEntry entry) {
        int invertedScore = MAX_SCORE - entry.getScore();
        String paddedInverted = String.format("%07d", invertedScore);

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", AttributeValue.fromS("GAME#" + entry.getGameId()));
        item.put("SK", AttributeValue.fromS("SCORE#" + paddedInverted + "#" + entry.getPlayerId() + "#" + entry.getTimestamp()));
        item.put("GSI1PK", AttributeValue.fromS("PLAYER#" + entry.getPlayerId()));
        item.put("GSI1SK", AttributeValue.fromS("GAME#" + entry.getGameId() + "#" + entry.getTimestamp()));
        item.put("playerId", AttributeValue.fromS(entry.getPlayerId()));
        item.put("playerName", AttributeValue.fromS(entry.getPlayerName()));
        item.put("gameId", AttributeValue.fromS(entry.getGameId()));
        item.put("score", AttributeValue.fromN(String.valueOf(entry.getScore())));
        item.put("timestamp", AttributeValue.fromS(entry.getTimestamp()));

        client.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .conditionExpression("attribute_not_exists(PK) AND attribute_not_exists(SK)")
                .build());
    }

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
            entry.put("pk", item.get("PK").s());
            entry.put("sk", item.get("SK").s());
            results.add(entry);
        }
        return results;
    }

    public Map<String, Object> getPlayerRank(String gameId, String playerId) {
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

        int start = Math.max(0, playerIndex - 2);
        int end = Math.min(allScores.size(), playerIndex + 3);
        result.put("nearby", allScores.subList(start, end));
        return result;
    }

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
     * Deletes a score using the exact PK and SK from the database.
     * This is the correct way — using the primary key directly.
     */
    public boolean deleteScore(String pk, String sk) {
        try {
            client.deleteItem(DeleteItemRequest.builder()
                    .tableName(tableName)
                    .key(Map.of(
                            "PK", AttributeValue.fromS(pk),
                            "SK", AttributeValue.fromS(sk)
                    ))
                    .conditionExpression("attribute_exists(PK)")
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Reads best players from the stats cache table.
     */
    public List<Map<String, Object>> getBestPlayers() {
        if (statsTableName == null) return new ArrayList<>();

        try {
            QueryRequest request = QueryRequest.builder()
                    .tableName(statsTableName)
                    .keyConditionExpression("PK = :pk")
                    .expressionAttributeValues(Map.of(
                            ":pk", AttributeValue.fromS("BEST_PLAYERS")
                    ))
                    .build();

            QueryResponse response = client.query(request);
            List<Map<String, Object>> results = new ArrayList<>();

            for (Map<String, AttributeValue> item : response.items()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("gameId", item.containsKey("gameId") ? item.get("gameId").s() : "unknown");
                entry.put("playerId", item.containsKey("playerId") ? item.get("playerId").s() : "unknown");
                entry.put("playerName", item.containsKey("playerName") ? item.get("playerName").s() : "unknown");
                entry.put("bestScore", item.containsKey("bestScore") ? Integer.parseInt(item.get("bestScore").n()) : 0);
                entry.put("lastUpdated", item.containsKey("lastUpdated") ? item.get("lastUpdated").s() : "");
                results.add(entry);
            }
            return results;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Updates the best player stats cache for a specific game.
     * Called by the Stream processor automatically.
     */
    public void updateBestPlayer(String gameId, String playerId, String playerName, int score, String timestamp) {
        if (statsTableName == null) return;

        try {
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("PK", AttributeValue.fromS("BEST_PLAYERS"));
            item.put("SK", AttributeValue.fromS("GAME#" + gameId));
            item.put("gameId", AttributeValue.fromS(gameId));
            item.put("playerId", AttributeValue.fromS(playerId));
            item.put("playerName", AttributeValue.fromS(playerName));
            item.put("bestScore", AttributeValue.fromN(String.valueOf(score)));
            item.put("lastUpdated", AttributeValue.fromS(timestamp));

            client.putItem(PutItemRequest.builder()
                    .tableName(statsTableName)
                    .item(item)
                    .build());
        } catch (Exception e) {
            // Log but don't fail
        }
    }
}
