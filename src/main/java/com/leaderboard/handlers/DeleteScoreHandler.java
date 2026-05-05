package com.leaderboard.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leaderboard.utils.ApiResponse;
import com.leaderboard.utils.DynamoDbHelper;

import java.time.Instant;
import java.util.Map;

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

            // Get source IP for audit logging
            String sourceIp = "unknown";
            if (request.getRequestContext() != null && request.getRequestContext().getIdentity() != null) {
                sourceIp = request.getRequestContext().getIdentity().getSourceIp();
            }

            // Extract game and player info from keys for readable logging
            String gameId = pk.replace("GAME#", "");
            String playerInfo = sk.contains("#") ? sk.split("#")[3] : "unknown";

            boolean deleted = db.deleteScore(pk, sk);

            if (deleted) {
                context.getLogger().log("[ADMIN_DELETE] game=" + gameId + " player=" + playerInfo + " ip=" + sourceIp + " status=SUCCESS pk=" + pk + " sk=" + sk + " timestamp=" + Instant.now());
                return ApiResponse.success(Map.of("message", "Score deleted successfully", "pk", pk, "sk", sk));
            } else {
                context.getLogger().log("[ADMIN_DELETE] game=" + gameId + " player=" + playerInfo + " ip=" + sourceIp + " status=FAILED reason=not_found pk=" + pk + " sk=" + sk + " timestamp=" + Instant.now());
                return ApiResponse.notFound("Score not found");
            }
        } catch (Exception e) {
            context.getLogger().log("[ADMIN_DELETE] status=ERROR reason=" + e.getMessage() + " timestamp=" + Instant.now());
            return ApiResponse.serverError("Failed to delete score: " + e.getMessage());
        }
    }
}
