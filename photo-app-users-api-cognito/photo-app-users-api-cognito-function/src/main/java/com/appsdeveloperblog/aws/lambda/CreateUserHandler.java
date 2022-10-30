package com.appsdeveloperblog.aws.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.appsdeveloperblog.aws.lambda.model.ErrorResponse;
import com.appsdeveloperblog.aws.lambda.service.CognitoUserService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import software.amazon.awssdk.awscore.exception.AwsServiceException;

import java.util.HashMap;
import java.util.Map;

/**
 * Handler for requests to Lambda function.
 */
public class CreateUserHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final CognitoUserService cognitoUserService;
    private final String appClientId;
    private final String appClientSecret;

    public CreateUserHandler() {
        this.cognitoUserService = new CognitoUserService(System.getenv("AWS_REGION"));
        this.appClientId = System.getenv("MY_COGNITO_POOL_APP_CLIENT_ID");
        this.appClientSecret = System.getenv("MY_COGNITO_POOL_APP_CLIENT_SECRET");
    }

    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        LambdaLogger logger = context.getLogger();

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(headers);

        String requestBody = input.getBody();
        logger.log("Original JSON body " + requestBody);

        JsonObject userDetails = JsonParser.parseString(requestBody).getAsJsonObject();

        try {
            JsonObject createUserResult = cognitoUserService.createUser(userDetails, appClientId, appClientSecret);

            response
                    .withStatusCode(200)
                    .withBody(new Gson().toJson(createUserResult, JsonObject.class));
        } catch (AwsServiceException e) {
            logger.log(e.awsErrorDetails().errorMessage());
            ErrorResponse errorResponse = new ErrorResponse(e.awsErrorDetails().errorMessage());
            String errorResponseJsonString = new Gson().toJson(errorResponse, ErrorResponse.class);
            response.withBody(errorResponseJsonString);
            response.withStatusCode(e.awsErrorDetails().sdkHttpResponse().statusCode());
        } catch (Exception e) {
            logger.log(e.getMessage());
            ErrorResponse errorResponse = new ErrorResponse(e.getMessage());
            String errorResponseJsonString = new GsonBuilder().serializeNulls().create().toJson(errorResponse, ErrorResponse.class);
            response.withBody(errorResponseJsonString);
            response.withStatusCode(500);
        }

        return response;
    }
}
