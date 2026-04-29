package com.leaderboard.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.leaderboard.utils.ApiResponse;
import com.leaderboard.utils.DynamoDbHelper;
import java.util.List;
import java.util.Map;

public class GetLeaderboardHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final DynamoDbHelper db = new DynamoDbHelper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String gameId = request.getPathParameters().get("gameId");
            if (gameId == null || gameId.isBlank()) return ApiResponse.badRequest("gameId is required");

            int limit = 10;
            if (request.getQueryStringParameters() != null && request.getQueryStringParameters().containsKey("limit")) {
                try { limit = Math.min(Math.max(Integer.parseInt(request.getQueryStringParameters().get("limit")), 1), 100); }
                catch (NumberFormatException e) { return ApiResponse.badRequest("limit must be a number"); }
            }

            List<Map<String, Object>> leaderboard = db.getLeaderboard(gameId, limit);
            return ApiResponse.success(Map.of("gameId", gameId, "count", leaderboard.size(), "entries", leaderboard));
        } catch (Exception e) {
            return ApiResponse.serverError("Failed to get leaderboard: " + e.getMessage());
        }
    }
}
