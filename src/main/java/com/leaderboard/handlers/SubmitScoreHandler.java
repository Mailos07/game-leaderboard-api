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

public class SubmitScoreHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final ObjectMapper mapper = new ObjectMapper();
    private final DynamoDbHelper db = new DynamoDbHelper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String body = request.getBody();
            if (body == null || body.isBlank()) return ApiResponse.badRequest("Request body is required");

            ScoreEntry entry = mapper.readValue(body, ScoreEntry.class);
            if (entry.getPlayerId() == null || entry.getPlayerId().isBlank()) return ApiResponse.badRequest("playerId is required");
            if (entry.getPlayerName() == null || entry.getPlayerName().isBlank()) return ApiResponse.badRequest("playerName is required");
            if (entry.getGameId() == null || entry.getGameId().isBlank()) return ApiResponse.badRequest("gameId is required");
            if (entry.getScore() < 0 || entry.getScore() > 9999999) return ApiResponse.badRequest("score must be between 0 and 9999999");

            entry.setTimestamp(Instant.now().toString());
            db.putScore(entry);
            context.getLogger().log("Score submitted: " + entry.getPlayerName() + " scored " + entry.getScore() + " in " + entry.getGameId());

            return ApiResponse.created(Map.of("message", "Score submitted successfully",
                    "playerId", entry.getPlayerId(), "playerName", entry.getPlayerName(),
                    "gameId", entry.getGameId(), "score", entry.getScore(), "timestamp", entry.getTimestamp()));
        } catch (Exception e) {
            context.getLogger().log("Error: " + e.getMessage());
            return ApiResponse.serverError("Failed to submit score: " + e.getMessage());
        }
    }
}
