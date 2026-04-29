package com.leaderboard.utils;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

public class ApiResponse {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Map<String, String> CORS_HEADERS = Map.of(
            "Content-Type", "application/json",
            "Access-Control-Allow-Origin", "*",
            "Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS",
            "Access-Control-Allow-Headers", "Content-Type,Authorization"
    );

    public static APIGatewayProxyResponseEvent success(Object body) { return buildResponse(200, body); }
    public static APIGatewayProxyResponseEvent created(Object body) { return buildResponse(201, body); }
    public static APIGatewayProxyResponseEvent badRequest(String message) { return buildResponse(400, Map.of("error", message)); }
    public static APIGatewayProxyResponseEvent unauthorized(String message) { return buildResponse(401, Map.of("error", message)); }
    public static APIGatewayProxyResponseEvent notFound(String message) { return buildResponse(404, Map.of("error", message)); }
    public static APIGatewayProxyResponseEvent serverError(String message) { return buildResponse(500, Map.of("error", message)); }

    private static APIGatewayProxyResponseEvent buildResponse(int statusCode, Object body) {
        try {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(statusCode).withHeaders(CORS_HEADERS)
                    .withBody(mapper.writeValueAsString(body));
        } catch (Exception e) {
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(500).withHeaders(CORS_HEADERS)
                    .withBody("{\"error\":\"Failed to serialize response\"}");
        }
    }
}
