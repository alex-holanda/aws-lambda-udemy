package com.appsdeveloperblog.aws.lambda.authorizer;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;

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

        if(userName.equalsIgnoreCase("123")) {
            effect = "Deny";
        }

        System.out.println("Effect: " + effect);

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
