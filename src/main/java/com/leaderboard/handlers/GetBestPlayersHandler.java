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
 * Handles GET /stats/best-players
 * Returns the best player for each game from the auto-updated stats cache.
 */
public class GetBestPlayersHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final DynamoDbHelper db = new DynamoDbHelper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            List<Map<String, Object>> bestPlayers = db.getBestPlayers();
            return ApiResponse.success(Map.of("bestPlayers", bestPlayers, "count", bestPlayers.size()));
        } catch (Exception e) {
            context.getLogger().log("Error getting best players: " + e.getMessage());
            return ApiResponse.serverError("Failed to get best players: " + e.getMessage());
        }
    }
}
