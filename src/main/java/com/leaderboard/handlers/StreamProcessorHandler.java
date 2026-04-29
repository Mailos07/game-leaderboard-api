package com.leaderboard.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import com.leaderboard.utils.DynamoDbHelper;

import java.util.Map;

/**
 * Processes DynamoDB Stream events from the LeaderboardTable.
 * Automatically updates the PlayerStats cache table whenever a score is inserted.
 * This means the "best player" data is always current without any polling.
 */
public class StreamProcessorHandler implements RequestHandler<DynamodbEvent, Void> {
    private final DynamoDbHelper db = new DynamoDbHelper();

    @Override
    public Void handleRequest(DynamodbEvent event, Context context) {
        for (DynamodbEvent.DynamodbStreamRecord record : event.getRecords()) {
            try {
                // Only process INSERT events (new scores)
                if (!"INSERT".equals(record.getEventName())) continue;

                Map<String, AttributeValue> newImage = record.getDynamodb().getNewImage();
                if (newImage == null) continue;

                // Only process score entries (SK starts with SCORE#)
                String sk = getStringValue(newImage, "SK");
                if (sk == null || !sk.startsWith("SCORE#")) continue;

                String gameId = getStringValue(newImage, "gameId");
                String playerId = getStringValue(newImage, "playerId");
                String playerName = getStringValue(newImage, "playerName");
                String scoreStr = getNumberValue(newImage, "score");
                String timestamp = getStringValue(newImage, "timestamp");

                if (gameId == null || playerId == null || scoreStr == null) continue;

                int score = Integer.parseInt(scoreStr);

                // Check if this score beats the current best for this game
                var bestPlayers = db.getBestPlayers();
                boolean isBest = true;

                for (var bp : bestPlayers) {
                    if (gameId.equals(bp.get("gameId"))) {
                        int currentBest = (int) bp.get("bestScore");
                        if (score <= currentBest) {
                            isBest = false;
                        }
                        break;
                    }
                }

                if (isBest) {
                    db.updateBestPlayer(gameId, playerId, playerName != null ? playerName : playerId, score, timestamp != null ? timestamp : "");
                    context.getLogger().log("New best player for " + gameId + ": " + playerName + " with " + score);
                }

            } catch (Exception e) {
                context.getLogger().log("Stream processing error: " + e.getMessage());
                // Don't throw — process remaining records
            }
        }
        return null;
    }

    private String getStringValue(Map<String, AttributeValue> item, String key) {
        AttributeValue val = item.get(key);
        return (val != null && val.getS() != null) ? val.getS() : null;
    }

    private String getNumberValue(Map<String, AttributeValue> item, String key) {
        AttributeValue val = item.get(key);
        return (val != null && val.getN() != null) ? val.getN() : null;
    }
}
