package com.appsdeveloperblog.aws.lambda.service;

import com.google.gson.JsonObject;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CognitoUserService {

    private final CognitoIdentityProviderClient cognitoIdentityProviderClient;

    public CognitoUserService(String region) {
        this.cognitoIdentityProviderClient = CognitoIdentityProviderClient.builder()
                .region(Region.of(region))
                .build();
    }

    public CognitoUserService(CognitoIdentityProviderClient cognitoIdentityProviderClient) {
        this.cognitoIdentityProviderClient = cognitoIdentityProviderClient;
    }

    public JsonObject createUser(JsonObject user, String appClientId, String appClientSecret) {
        String email = user.get("email").getAsString();
        String password = user.get("password").getAsString();
        String firstName = user.get("firstName").getAsString();
        String lastName = user.get("lastName").getAsString();
        String userId = UUID.randomUUID().toString();

        AttributeType attributeEmail = AttributeType.builder()
                .name("email")
                .value(email)
                .build();

        AttributeType attributeName = AttributeType.builder()
                .name("name")
                .value(firstName + " " + lastName)
                .build();

        AttributeType attributeUserId = AttributeType.builder()
                .name("custom:userId")
                .value(userId)
                .build();

        List<AttributeType> attributes = new ArrayList<>();
        attributes.add(attributeEmail);
        attributes.add(attributeName);
        attributes.add(attributeUserId);

        String generatedSecrectHash = calculateSecretHash(appClientId, appClientSecret, email);

        SignUpRequest signUpRequest = SignUpRequest.builder()
                .username(email)
                .password(password)
                .userAttributes(attributes)
                .clientId(appClientId)
                .secretHash(generatedSecrectHash)
                .build();

        SignUpResponse signUpResponse = cognitoIdentityProviderClient.signUp(signUpRequest);

        JsonObject createUserResult = new JsonObject();
        createUserResult.addProperty("isSuccessful", signUpResponse.sdkHttpResponse().isSuccessful());
        createUserResult.addProperty("statusCode", signUpResponse.sdkHttpResponse().statusCode());
        createUserResult.addProperty("cognitoUserId", signUpResponse.userSub());
        createUserResult.addProperty("isConfirmed", signUpResponse.userConfirmed());

        return createUserResult;
    }

    public JsonObject confirmUserSignup(String appClientId, String appClientSecret, String email, String confirmationCode) {
        System.out.println("# BEGIN confirmUserSignup");
        String generatedSecretHash = calculateSecretHash(appClientId, appClientSecret, email);

        ConfirmSignUpRequest confirmSignUpRequest = ConfirmSignUpRequest.builder()
                .secretHash(generatedSecretHash)
                .username(email)
                .confirmationCode(confirmationCode)
                .clientId(appClientId)
                .build();

        ConfirmSignUpResponse confirmSignUpResponse = cognitoIdentityProviderClient.confirmSignUp(confirmSignUpRequest);

        JsonObject confirmUserResponse = new JsonObject();
        confirmUserResponse.addProperty("isSuccessful", confirmSignUpResponse.sdkHttpResponse().isSuccessful());
        confirmUserResponse.addProperty("statusCode", confirmSignUpResponse.sdkHttpResponse().statusCode());

        System.out.println("# END confirmUserSignup");

        return confirmUserResponse;
    }

    public JsonObject userLogin(JsonObject loginDetails, String appClientId, String appClientSecret) {
        String email = loginDetails.get("email").getAsString();
        String password = loginDetails.get("password").getAsString();
        String generatedSecretHash = calculateSecretHash(appClientId, appClientSecret, email);

        Map<String, String> authParams = new HashMap<String, String>() {
            {
                put("USERNAME", email);
                put("PASSWORD", password);
                put("SECRET_HASH", generatedSecretHash);
            }
        };

        InitiateAuthRequest initiateAuthRequest = InitiateAuthRequest.builder()
                .clientId(appClientId)
                .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                .authParameters(authParams)
                .build();

        InitiateAuthResponse initiateAuthResponse = cognitoIdentityProviderClient.initiateAuth(initiateAuthRequest);
        AuthenticationResultType authenticationResultType = initiateAuthResponse.authenticationResult();

        JsonObject loginUserResult = new JsonObject();
        loginUserResult.addProperty("isSuccessful", initiateAuthResponse.sdkHttpResponse().isSuccessful());
        loginUserResult.addProperty("statusCode", initiateAuthResponse.sdkHttpResponse().statusCode());
        loginUserResult.addProperty("idToken", authenticationResultType.idToken());
        loginUserResult.addProperty("accessToken", authenticationResultType.accessToken());
        loginUserResult.addProperty("refreshToken", authenticationResultType.refreshToken());

        return loginUserResult;
    }

    public JsonObject addUserToGroup(String groupName, String userName, String userPoolId) {
        System.out.println("AddUserToGroup - Begin");
        AdminAddUserToGroupRequest adminAddUserToGroupRequest = AdminAddUserToGroupRequest.builder()
                .groupName(groupName)
                .username(userName)
                .userPoolId(userPoolId)
                .build();

        AdminAddUserToGroupResponse adminAddUserToGroupResponse = cognitoIdentityProviderClient.adminAddUserToGroup(adminAddUserToGroupRequest);

        System.out.println("Response >> " + adminAddUserToGroupResponse.toString());
        System.out.println("Response >> " + adminAddUserToGroupResponse.sdkHttpResponse().isSuccessful());
        System.out.println("Response >> " + adminAddUserToGroupResponse.sdkHttpResponse().statusCode());

        JsonObject addUserToGroupResponse = new JsonObject();
        addUserToGroupResponse.addProperty("isSuccessful", adminAddUserToGroupResponse.sdkHttpResponse().isSuccessful());
        addUserToGroupResponse.addProperty("statusCode", adminAddUserToGroupResponse.sdkHttpResponse().statusCode());

        return addUserToGroupResponse;
    }

    public JsonObject getUser(String accessToken) {
        System.out.println("Service Get USER");
        GetUserRequest getUserRequest = GetUserRequest.builder()
                .accessToken(accessToken)
                .build();

        GetUserResponse getUserResponse = cognitoIdentityProviderClient.getUser(getUserRequest);

        JsonObject getUserResult = new JsonObject();
        getUserResult.addProperty("isSuccessful", getUserResponse.sdkHttpResponse().isSuccessful());
        getUserResult.addProperty("statusCode", getUserResponse.sdkHttpResponse().statusCode());

        JsonObject userDetails = new JsonObject();

        List<AttributeType> userAttributes = getUserResponse.userAttributes();
        userAttributes.stream().forEach(attributeType -> {
            userDetails.addProperty(attributeType.name(), attributeType.value());
        });

        getUserResult.add("user", userDetails);

        return getUserResult;
    }

    private String calculateSecretHash(String userPoolClientId, String userPoolClientSecret, String userName) {
        final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

        SecretKeySpec signingKey = new SecretKeySpec(
                userPoolClientSecret.getBytes(StandardCharsets.UTF_8),
                HMAC_SHA256_ALGORITHM);

        try {
            Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
            mac.init(signingKey);
            mac.update(userName.getBytes(StandardCharsets.UTF_8));
            byte[] rawHmac = mac.doFinal(userPoolClientId.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Error while calculating");
        }
    }
}
