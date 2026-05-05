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

import java.time.Instant;
import java.util.Map;

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

            // Get source IP for audit logging
            String sourceIp = "unknown";
            if (request.getRequestContext() != null && request.getRequestContext().getIdentity() != null) {
                sourceIp = request.getRequestContext().getIdentity().getSourceIp();
            }

            if (path.endsWith("/signup")) {
                return handleSignup(email, password, data.get("name"), sourceIp, context);
            } else {
                return handleLogin(email, password, sourceIp, context);
            }
        } catch (Exception e) {
            context.getLogger().log("[ADMIN_AUTH_ERROR] action=unknown error=" + e.getMessage() + " timestamp=" + Instant.now());
            return ApiResponse.serverError("Authentication failed: " + e.getMessage());
        }
    }

    private APIGatewayProxyResponseEvent handleSignup(String email, String password, String name, String sourceIp, Context context) {
        try {
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

            AdminSetUserPasswordRequest setPasswordRequest = AdminSetUserPasswordRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .password(password)
                    .permanent(true)
                    .build();
            cognito.adminSetUserPassword(setPasswordRequest);

            // Detailed audit log
            context.getLogger().log("[ADMIN_SIGNUP] email=" + email + " name=" + (name != null ? name : "none") + " ip=" + sourceIp + " status=SUCCESS timestamp=" + Instant.now());

            return ApiResponse.created(Map.of("message", "Admin account created successfully", "email", email));

        } catch (UsernameExistsException e) {
            context.getLogger().log("[ADMIN_SIGNUP] email=" + email + " ip=" + sourceIp + " status=FAILED reason=already_exists timestamp=" + Instant.now());
            return ApiResponse.badRequest("An account with this email already exists");
        } catch (InvalidPasswordException e) {
            context.getLogger().log("[ADMIN_SIGNUP] email=" + email + " ip=" + sourceIp + " status=FAILED reason=invalid_password timestamp=" + Instant.now());
            return ApiResponse.badRequest("Password must be at least 8 characters with uppercase, lowercase, and numbers");
        } catch (Exception e) {
            context.getLogger().log("[ADMIN_SIGNUP] email=" + email + " ip=" + sourceIp + " status=ERROR reason=" + e.getMessage() + " timestamp=" + Instant.now());
            return ApiResponse.serverError("Signup failed: " + e.getMessage());
        }
    }

    private APIGatewayProxyResponseEvent handleLogin(String email, String password, String sourceIp, Context context) {
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
                context.getLogger().log("[ADMIN_LOGIN] email=" + email + " ip=" + sourceIp + " status=FAILED reason=additional_auth_required timestamp=" + Instant.now());
                return ApiResponse.badRequest("Authentication requires additional steps. Please contact admin.");
            }

            // Detailed audit log — successful login
            context.getLogger().log("[ADMIN_LOGIN] email=" + email + " ip=" + sourceIp + " status=SUCCESS session_expires_in=" + authResult.expiresIn() + "s timestamp=" + Instant.now());

            return ApiResponse.success(Map.of(
                    "message", "Login successful",
                    "idToken", authResult.idToken(),
                    "accessToken", authResult.accessToken(),
                    "expiresIn", authResult.expiresIn()
            ));

        } catch (NotAuthorizedException e) {
            context.getLogger().log("[ADMIN_LOGIN] email=" + email + " ip=" + sourceIp + " status=FAILED reason=invalid_credentials timestamp=" + Instant.now());
            return ApiResponse.unauthorized("Invalid email or password");
        } catch (UserNotFoundException e) {
            context.getLogger().log("[ADMIN_LOGIN] email=" + email + " ip=" + sourceIp + " status=FAILED reason=user_not_found timestamp=" + Instant.now());
            return ApiResponse.unauthorized("No account found with this email");
        } catch (Exception e) {
            context.getLogger().log("[ADMIN_LOGIN] email=" + email + " ip=" + sourceIp + " status=ERROR reason=" + e.getMessage() + " timestamp=" + Instant.now());
            return ApiResponse.serverError("Login failed: " + e.getMessage());
        }
    }
}
