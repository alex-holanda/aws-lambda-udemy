package com.appsdeveloperblog.aws.lambda.authorizer;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.appsdeveloperblog.aws.lambda.authorizer.utils.JwtUtils;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.util.Arrays;

/**
 * Handler for requests to Lambda function.
 */
public class LambdaAuthorizer implements RequestHandler<APIGatewayProxyRequestEvent, AuthorizerOutput> {

    @Override
    public AuthorizerOutput handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        System.out.println(">>> Lambda start");
        String userName = input.getPathParameters().get("userName");
        String effect = "Allow";

        String jwt = input.getHeaders().get("Authorization");
        String region = System.getenv("AWS_REGION");
        String userPoolId = System.getenv("PHOTO_APP_USERS_POOL_ID");
        String audience = System.getenv("PHOTO_APP_USERS_CLIENT_ID");

        JwtUtils jwtUtils = new JwtUtils();
        DecodedJWT decodedJWT = null;

        try {
            decodedJWT = jwtUtils.validateJwtForUser(jwt, region, userPoolId, userName, audience);
            userName = decodedJWT.getSubject();
        } catch (RuntimeException e) {
            effect = "Deny";
            e.printStackTrace();
        }

        APIGatewayProxyRequestEvent.ProxyRequestContext proxyRequestContext = input.getRequestContext();

        String arn = String.format("arn:aws:execute-api:%s:%s:%s/%s/%s/%s",
                System.getenv("AWS_REGION"),
                proxyRequestContext.getAccountId(),
                proxyRequestContext.getApiId(),
                proxyRequestContext.getStage(),
                proxyRequestContext.getHttpMethod(),
                "*");

        Statement statement = Statement.builder()
                .action("execute-api:Invoke")
                .effect(effect)
                .resource(arn)
                .build();

        PolicyDocument policyDocument = PolicyDocument.builder()
                .version("2012-10-17")
                .statements(Arrays.asList(statement))
                .build();


        AuthorizerOutput authorizerOutput = AuthorizerOutput.builder()
                .principalId(userName)
                .policyDocument(policyDocument)
                .build();

        return authorizerOutput;
    }

}
