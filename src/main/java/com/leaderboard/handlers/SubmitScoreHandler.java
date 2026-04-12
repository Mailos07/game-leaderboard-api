package com.leaderboard.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leaderboard.models.ScoreEntry;
import com.leaderboard.utils.ApiResponse;
import com.leaderboard.utils.DynamoDbHelper;

import java.time.Instant;
import java.util.Map;

/**
 * Handles POST /scores
 *
 * Expects JSON body:
 * {
 *   "playerId": "player123",
 *   "playerName": "JohnDoe",
 *   "gameId": "battle-royale",
 *   "score": 9500
 * }
 *
 * Validates the input, adds a timestamp, and writes to DynamoDB.
 * Returns 201 on success with the created entry.
 */
public class SubmitScoreHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DynamoDbHelper db = new DynamoDbHelper();

    // Score bounds for validation — prevents garbage data
    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 9999999;

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            // Parse the incoming JSON body
            String body = request.getBody();
            if (body == null || body.isBlank()) {
                return ApiResponse.badRequest("Request body is required");
            }

            ScoreEntry entry = mapper.readValue(body, ScoreEntry.class);

            // ── Validation ──
            if (entry.getPlayerId() == null || entry.getPlayerId().isBlank()) {
                return ApiResponse.badRequest("playerId is required");
            }
            if (entry.getPlayerName() == null || entry.getPlayerName().isBlank()) {
                return ApiResponse.badRequest("playerName is required");
            }
            if (entry.getGameId() == null || entry.getGameId().isBlank()) {
                return ApiResponse.badRequest("gameId is required");
            }
            if (entry.getScore() < MIN_SCORE || entry.getScore() > MAX_SCORE) {
                return ApiResponse.badRequest("score must be between " + MIN_SCORE + " and " + MAX_SCORE);
            }

            // Set the timestamp server-side so clients can't fake it
            entry.setTimestamp(Instant.now().toString());

            // Write to DynamoDB
            db.putScore(entry);

            context.getLogger().log("Score submitted: " + entry.getPlayerName()
                    + " scored " + entry.getScore() + " in " + entry.getGameId());

            return ApiResponse.created(Map.of(
                    "message", "Score submitted successfully",
                    "playerId", entry.getPlayerId(),
                    "playerName", entry.getPlayerName(),
                    "gameId", entry.getGameId(),
                    "score", entry.getScore(),
                    "timestamp", entry.getTimestamp()
            ));

        } catch (Exception e) {
            context.getLogger().log("Error submitting score: " + e.getMessage());
            return ApiResponse.serverError("Failed to submit score: " + e.getMessage());
        }
    }
}
