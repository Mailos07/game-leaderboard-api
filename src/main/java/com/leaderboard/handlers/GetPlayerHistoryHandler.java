package com.leaderboard.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.leaderboard.utils.ApiResponse;
import com.leaderboard.utils.DynamoDbHelper;

import java.util.List;
import java.util.Map;

/**
 * Handles GET /players/{playerId}/scores
 *
 * Returns all scores a player has submitted across all games.
 * Uses the GSI1 index (GSI1PK = "PLAYER#<playerId>") so this
 * is a single query, not a table scan.
 */
public class GetPlayerHistoryHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbHelper db = new DynamoDbHelper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String playerId = request.getPathParameters().get("playerId");

            if (playerId == null || playerId.isBlank()) {
                return ApiResponse.badRequest("playerId is required");
            }

            List<Map<String, Object>> history = db.getPlayerHistory(playerId);

            context.getLogger().log("Player history queried: " + playerId + " (" + history.size() + " scores)");

            return ApiResponse.success(Map.of(
                    "playerId", playerId,
                    "totalScores", history.size(),
                    "scores", history
            ));

        } catch (Exception e) {
            context.getLogger().log("Error getting player history: " + e.getMessage());
            return ApiResponse.serverError("Failed to get player history: " + e.getMessage());
        }
    }
}
