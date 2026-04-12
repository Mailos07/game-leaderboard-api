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
 * Handles GET /leaderboard/{gameId}?limit=10
 *
 * Returns the top N scores for a specific game, ranked highest first.
 * The ?limit query param controls how many results (default 10, max 100).
 *
 * Example response:
 * {
 *   "gameId": "battle-royale",
 *   "entries": [
 *     { "rank": 1, "playerId": "p1", "playerName": "Alice", "score": 9500, ... },
 *     { "rank": 2, "playerId": "p2", "playerName": "Bob", "score": 8200, ... }
 *   ]
 * }
 */
public class GetLeaderboardHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbHelper db = new DynamoDbHelper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            // Pull gameId from the URL path
            String gameId = request.getPathParameters().get("gameId");
            if (gameId == null || gameId.isBlank()) {
                return ApiResponse.badRequest("gameId path parameter is required");
            }

            // Parse the optional limit query parameter
            int limit = 10; // sensible default
            if (request.getQueryStringParameters() != null
                    && request.getQueryStringParameters().containsKey("limit")) {
                try {
                    limit = Integer.parseInt(request.getQueryStringParameters().get("limit"));
                    limit = Math.min(Math.max(limit, 1), 100); // clamp between 1 and 100
                } catch (NumberFormatException e) {
                    return ApiResponse.badRequest("limit must be a number between 1 and 100");
                }
            }

            List<Map<String, Object>> leaderboard = db.getLeaderboard(gameId, limit);

            context.getLogger().log("Leaderboard queried: " + gameId + " (top " + limit + ")");

            return ApiResponse.success(Map.of(
                    "gameId", gameId,
                    "count", leaderboard.size(),
                    "entries", leaderboard
            ));

        } catch (Exception e) {
            context.getLogger().log("Error getting leaderboard: " + e.getMessage());
            return ApiResponse.serverError("Failed to get leaderboard: " + e.getMessage());
        }
    }
}
