package com.leaderboard.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leaderboard.utils.ApiResponse;
import com.leaderboard.utils.DynamoDbHelper;
import java.util.Map;

/**
 * Handles POST /admin/delete
 * Accepts PK and SK in the request body for reliable deletion.
 * The frontend sends the exact primary key values from the leaderboard query.
 */
public class DeleteScoreHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final DynamoDbHelper db = new DynamoDbHelper();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String body = request.getBody();
            if (body == null || body.isBlank()) return ApiResponse.badRequest("Request body is required");

            Map<String, String> data = mapper.readValue(body, Map.class);
            String pk = data.get("pk");
            String sk = data.get("sk");

            if (pk == null || pk.isBlank()) return ApiResponse.badRequest("pk (partition key) is required");
            if (sk == null || sk.isBlank()) return ApiResponse.badRequest("sk (sort key) is required");

            boolean deleted = db.deleteScore(pk, sk);

            if (deleted) {
                context.getLogger().log("Score deleted: PK=" + pk + " SK=" + sk);
                return ApiResponse.success(Map.of("message", "Score deleted successfully", "pk", pk, "sk", sk));
            } else {
                return ApiResponse.notFound("Score not found");
            }
        } catch (Exception e) {
            context.getLogger().log("Error deleting score: " + e.getMessage());
            return ApiResponse.serverError("Failed to delete score: " + e.getMessage());
        }
    }
}
