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

/**
 * Handles POST /admin/login and POST /admin/signup
 * Uses Cognito User Pool for admin authentication.
 */
public class AdminAuthHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CognitoIdentityProviderClient cognito;
    private final String userPoolId;
    private final String clientId;

    public AdminAuthHandler() {
        this.cognito = CognitoIdentityProviderClient.builder().region(Region.US_EAST_1).build();
        this.userPoolId = System.getenv("USER_POOL_ID");
        this.clientId = System.getenv("CLIENT_ID");
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        try {
            String path = request.getPath();
            String body = request.getBody();
            if (body == null || body.isBlank()) return ApiResponse.badRequest("Request body is required");

            Map<String, String> data = mapper.readValue(body, Map.class);
            String email = data.get("email");
            String password = data.get("password");

            if (email == null || email.isBlank()) return ApiResponse.badRequest("email is required");
            if (password == null || password.isBlank()) return ApiResponse.badRequest("password is required");

            if (path.endsWith("/signup")) {
                return handleSignup(email, password, data.get("name"), context);
            } else {
                return handleLogin(email, password, context);
            }
        } catch (Exception e) {
            context.getLogger().log("Auth error: " + e.getMessage());
            return ApiResponse.serverError("Authentication failed: " + e.getMessage());
        }
    }

    private APIGatewayProxyResponseEvent handleSignup(String email, String password, String name, Context context) {
        try {
            // Create the user
            AdminCreateUserRequest createRequest = AdminCreateUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .temporaryPassword(password)
                    .userAttributes(
                            AttributeType.builder().name("email").value(email).build(),
                            AttributeType.builder().name("email_verified").value("true").build(),
                            AttributeType.builder().name("name").value(name != null ? name : email).build()
                    )
                    .messageAction("SUPPRESS")
                    .build();
            cognito.adminCreateUser(createRequest);

            // Set permanent password immediately
            AdminSetUserPasswordRequest setPasswordRequest = AdminSetUserPasswordRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .password(password)
                    .permanent(true)
                    .build();
            cognito.adminSetUserPassword(setPasswordRequest);

            context.getLogger().log("Admin user created: " + email);
            return ApiResponse.created(Map.of("message", "Admin account created successfully", "email", email));

        } catch (UsernameExistsException e) {
            return ApiResponse.badRequest("An account with this email already exists");
        } catch (InvalidPasswordException e) {
            return ApiResponse.badRequest("Password must be at least 8 characters with uppercase, lowercase, and numbers");
        } catch (Exception e) {
            context.getLogger().log("Signup error: " + e.getMessage());
            return ApiResponse.serverError("Signup failed: " + e.getMessage());
        }
    }

    private APIGatewayProxyResponseEvent handleLogin(String email, String password, Context context) {
        try {
            AdminInitiateAuthRequest authRequest = AdminInitiateAuthRequest.builder()
                    .userPoolId(userPoolId)
                    .clientId(clientId)
                    .authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
                    .authParameters(Map.of("USERNAME", email, "PASSWORD", password))
                    .build();

            AdminInitiateAuthResponse authResponse = cognito.adminInitiateAuth(authRequest);
            AuthenticationResultType authResult = authResponse.authenticationResult();

            if (authResult == null) {
                return ApiResponse.badRequest("Authentication requires additional steps. Please contact admin.");
            }

            context.getLogger().log("Admin login: " + email);
            return ApiResponse.success(Map.of(
                    "message", "Login successful",
                    "idToken", authResult.idToken(),
                    "accessToken", authResult.accessToken(),
                    "expiresIn", authResult.expiresIn()
            ));

        } catch (NotAuthorizedException e) {
            return ApiResponse.unauthorized("Invalid email or password");
        } catch (UserNotFoundException e) {
            return ApiResponse.unauthorized("No account found with this email");
        } catch (Exception e) {
            context.getLogger().log("Login error: " + e.getMessage());
            return ApiResponse.serverError("Login failed: " + e.getMessage());
        }
    }
}
