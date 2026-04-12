package com.leaderboard.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.leaderboard.utils.ApiResponse;
import com.leaderboard.utils.DynamoDbHelper;

import java.util.Map;

/**
 * Handles DELETE /scores/{gameId}/{scoreId}
 *
 * Removes a specific score entry from the leaderboard.
 * In a real system this would be admin-only, but for our demo
 * it's useful for cleaning up test data.
 */
public class DeleteScoreHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final DynamoDbHelper db = new DynamoDbHelper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String gameId = request.getPathParameters().get("gameId");
            String scoreId = request.getPathParameters().get("scoreId");

            if (gameId == null || gameId.isBlank()) {
                return ApiResponse.badRequest("gameId is required");
            }
            if (scoreId == null || scoreId.isBlank()) {
                return ApiResponse.badRequest("scoreId is required");
            }

            boolean deleted = db.deleteScore(gameId, scoreId);

            if (deleted) {
                context.getLogger().log("Score deleted: " + scoreId + " from game " + gameId);
                return ApiResponse.success(Map.of(
                        "message", "Score deleted successfully",
                        "gameId", gameId
                ));
            } else {
                return ApiResponse.notFound("Score not found");
            }

        } catch (Exception e) {
            context.getLogger().log("Error deleting score: " + e.getMessage());
            return ApiResponse.serverError("Failed to delete score: " + e.getMessage());
        }
    }
}
