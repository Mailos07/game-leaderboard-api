package com.leaderboard.handlers;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leaderboard.utils.ApiResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.Map;

public class VerifyTokenHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final CognitoIdentityProviderClient cognito;
    private final ObjectMapper mapper = new ObjectMapper();

    public VerifyTokenHandler() {
        this.cognito = CognitoIdentityProviderClient.builder().region(Region.US_EAST_1).build();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String body = request.getBody();
            if (body == null) return ApiResponse.unauthorized("No token provided");

            Map<String, String> data = mapper.readValue(body, Map.class);
            String accessToken = data.get("accessToken");

            if (accessToken == null || accessToken.isBlank()) return ApiResponse.unauthorized("accessToken is required");

            GetUserRequest getUserRequest = GetUserRequest.builder().accessToken(accessToken).build();
            GetUserResponse userResponse = cognito.getUser(getUserRequest);

            String email = userResponse.username();
            String name = userResponse.userAttributes().stream()
                    .filter(a -> a.name().equals("name"))
                    .map(AttributeType::value)
                    .findFirst().orElse(email);

            return ApiResponse.success(Map.of("valid", true, "email", email, "name", name));

        } catch (NotAuthorizedException e) {
            return ApiResponse.unauthorized("Token is invalid or expired");
        } catch (Exception e) {
            return ApiResponse.unauthorized("Token verification failed: " + e.getMessage());
        }
    }
}
