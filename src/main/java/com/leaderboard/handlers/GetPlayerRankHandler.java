package com.leaderboard.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.leaderboard.utils.ApiResponse;
import com.leaderboard.utils.DynamoDbHelper;

import java.util.Map;

/**
 * Handles GET /leaderboard/{gameId}/player/{playerId}
 *
 * Returns where a specific player sits in the leaderboard,
 * plus the 2 players above and below them (the "nearby" view).
 *
 * This is the kind of query players actually care about most —
 * "where do I stand?" not just "who's on top?"
 */
public class GetPlayerRankHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbHelper db = new DynamoDbHelper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String gameId = request.getPathParameters().get("gameId");
            String playerId = request.getPathParameters().get("playerId");

            if (gameId == null || gameId.isBlank()) {
                return ApiResponse.badRequest("gameId is required");
            }
            if (playerId == null || playerId.isBlank()) {
                return ApiResponse.badRequest("playerId is required");
            }

            Map<String, Object> result = db.getPlayerRank(gameId, playerId);

            if (!(boolean) result.get("found")) {
                return ApiResponse.notFound("Player " + playerId + " not found in game " + gameId);
            }

            context.getLogger().log("Player rank queried: " + playerId + " in " + gameId);
            return ApiResponse.success(result);

        } catch (Exception e) {
            context.getLogger().log("Error getting player rank: " + e.getMessage());
            return ApiResponse.serverError("Failed to get player rank: " + e.getMessage());
        }
    }
}
