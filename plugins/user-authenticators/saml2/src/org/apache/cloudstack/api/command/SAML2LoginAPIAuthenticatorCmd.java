// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.cloudstack.api.command;

import com.cloud.api.response.ApiResponseSerializer;
import com.cloud.domain.Domain;
import com.cloud.exception.CloudAuthenticationException;
import com.cloud.user.Account;
import com.cloud.user.DomainManager;
import com.cloud.user.UserAccount;
import com.cloud.user.UserAccountVO;
import com.cloud.user.dao.UserAccountDao;
import com.cloud.utils.HttpUtils;
import com.cloud.utils.db.EntityManager;
import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.ApiServerService;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.auth.APIAuthenticationType;
import org.apache.cloudstack.api.auth.APIAuthenticator;
import org.apache.cloudstack.api.auth.PluggableAPIAuthenticator;
import org.apache.cloudstack.api.response.LoginCmdResponse;
import org.apache.cloudstack.saml.SAML2AuthManager;
import org.apache.cloudstack.saml.SAMLUtils;
import org.apache.log4j.Logger;
import org.opensaml.DefaultBootstrap;
import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.AuthnRequest;
import org.opensaml.saml2.core.EncryptedAssertion;
import org.opensaml.saml2.core.Response;
import org.opensaml.saml2.core.StatusCode;
import org.opensaml.saml2.encryption.Decrypter;
import org.opensaml.xml.ConfigurationException;
import org.opensaml.xml.encryption.DecryptionException;
import org.opensaml.xml.encryption.EncryptedKeyResolver;
import org.opensaml.xml.encryption.InlineEncryptedKeyResolver;
import org.opensaml.xml.io.MarshallingException;
import org.opensaml.xml.io.UnmarshallingException;
import org.opensaml.xml.security.SecurityHelper;
import org.opensaml.xml.security.credential.Credential;
import org.opensaml.xml.security.keyinfo.StaticKeyInfoCredentialResolver;
import org.opensaml.xml.security.x509.BasicX509Credential;
import org.opensaml.xml.signature.Signature;
import org.opensaml.xml.signature.SignatureValidator;
import org.opensaml.xml.validation.ValidationException;
import org.xml.sax.SAXException;

import javax.inject.Inject;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.FactoryConfigurationError;
import java.io.IOException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.List;
import java.util.Map;

@APICommand(name = "samlSso", description = "SP initiated SAML Single Sign On", requestHasSensitiveInfo = true, responseObject = LoginCmdResponse.class, entityType = {})
public class SAML2LoginAPIAuthenticatorCmd extends BaseCmd implements APIAuthenticator {
    public static final Logger s_logger = Logger.getLogger(SAML2LoginAPIAuthenticatorCmd.class.getName());
    private static final String s_name = "loginresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////
    @Parameter(name = ApiConstants.IDP_URL, type = CommandType.STRING, description = "Identity Provider SSO HTTP-Redirect binding URL", required = true)
    private String idpUrl;

    @Inject
    ApiServerService _apiServer;
    @Inject
    EntityManager _entityMgr;
    @Inject
    DomainManager _domainMgr;
    @Inject
    private UserAccountDao _userAccountDao;

    SAML2AuthManager _samlAuthManager;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public String getIdpUrl() {
        return idpUrl;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_TYPE_NORMAL;
    }

    @Override
    public void execute() throws ServerApiException {
        // We should never reach here
        throw new ServerApiException(ApiErrorCode.METHOD_NOT_ALLOWED, "This is an authentication api, cannot be used directly");
    }

    private String buildAuthnRequestUrl(String idpUrl) {
        String spId = _samlAuthManager.getServiceProviderId();
        String consumerUrl = _samlAuthManager.getSpSingleSignOnUrl();
        String identityProviderUrl = _samlAuthManager.getIdpSingleSignOnUrl();

        if (idpUrl != null) {
            identityProviderUrl = idpUrl;
        }

        String redirectUrl = "";
        try {
            DefaultBootstrap.bootstrap();
            AuthnRequest authnRequest = SAMLUtils.buildAuthnRequestObject(spId, identityProviderUrl, consumerUrl);
            PrivateKey privateKey = null;
            if (_samlAuthManager.getSpKeyPair() != null) {
                privateKey = _samlAuthManager.getSpKeyPair().getPrivate();
            }
            redirectUrl = identityProviderUrl + "?" + SAMLUtils.generateSAMLRequestSignature("SAMLRequest=" + SAMLUtils.encodeSAMLRequest(authnRequest), privateKey);
        } catch (ConfigurationException | FactoryConfigurationError | MarshallingException | IOException | NoSuchAlgorithmException | InvalidKeyException | java.security.SignatureException e) {
            s_logger.error("SAML AuthnRequest message building error: " + e.getMessage());
        }
        return redirectUrl;
    }

    public Response processSAMLResponse(String responseMessage) {
        Response responseObject = null;
        try {
            DefaultBootstrap.bootstrap();
            responseObject = SAMLUtils.decodeSAMLResponse(responseMessage);

        } catch (ConfigurationException | FactoryConfigurationError | ParserConfigurationException | SAXException | IOException | UnmarshallingException e) {
            s_logger.error("SAMLResponse processing error: " + e.getMessage());
        }
        return responseObject;
    }

    @Override
    public String authenticate(final String command, final Map<String, Object[]> params, final HttpSession session, final String remoteAddress, final String responseType, final StringBuilder auditTrailSb, final HttpServletRequest req, final HttpServletResponse resp) throws ServerApiException {
        try {
            if (!params.containsKey("SAMLResponse") && !params.containsKey("SAMLart")) {
                String idpUrl = null;
                final String[] idps = (String[])params.get(ApiConstants.IDP_URL);
                if (idps != null && idps.length > 0) {
                    idpUrl = idps[0];
                }
                String redirectUrl = this.buildAuthnRequestUrl(idpUrl);
                resp.sendRedirect(redirectUrl);
                return "";
            } if (params.containsKey("SAMLart")) {
                throw new ServerApiException(ApiErrorCode.UNSUPPORTED_ACTION_ERROR, _apiServer.getSerializedApiError(ApiErrorCode.UNSUPPORTED_ACTION_ERROR.getHttpCode(),
                        "SAML2 HTTP Artifact Binding is not supported",
                        params, responseType));
            } else {
                final String samlResponse = ((String[])params.get(SAMLUtils.SAML_RESPONSE))[0];
                Response processedSAMLResponse = this.processSAMLResponse(samlResponse);
                String statusCode = processedSAMLResponse.getStatus().getStatusCode().getValue();
                if (!statusCode.equals(StatusCode.SUCCESS_URI)) {
                    throw new ServerApiException(ApiErrorCode.ACCOUNT_ERROR, _apiServer.getSerializedApiError(ApiErrorCode.ACCOUNT_ERROR.getHttpCode(),
                            "Identity Provider send a non-successful authentication status code",
                            params, responseType));
                }

                String defaultDomainString = SAML2AuthManager.SAMLDefaultDomain.value();
                String username = null;
                Long domainId = null;
                Domain domain = _domainMgr.getDomain(defaultDomainString);
                if (domain != null) {
                    domainId = domain.getId();
                } else {
                    try {
                        domainId = Long.parseLong(defaultDomainString);
                    } catch (NumberFormatException ignore) {
                    }
                }
                if (domainId == null) {
                    s_logger.error("The default domain ID for SAML users is not set correct, it should be a UUID. ROOT domain will be used.");
                }

                Signature sig = processedSAMLResponse.getSignature();
                if (_samlAuthManager.getIdpSigningKey() != null && sig != null) {
                    BasicX509Credential credential = new BasicX509Credential();
                    credential.setEntityCertificate(_samlAuthManager.getIdpSigningKey());
                    SignatureValidator validator = new SignatureValidator(credential);
                    try {
                        validator.validate(sig);
                    } catch (ValidationException e) {
                        s_logger.error("SAML Response's signature failed to be validated by IDP signing key:" + e.getMessage());
                        throw new ServerApiException(ApiErrorCode.ACCOUNT_ERROR, _apiServer.getSerializedApiError(ApiErrorCode.ACCOUNT_ERROR.getHttpCode(),
                                "SAML Response's signature failed to be validated by IDP signing key",
                                params, responseType));
                    }
                }
                if (username == null) {
                    username = SAMLUtils.getValueFromAssertions(processedSAMLResponse.getAssertions(), SAML2AuthManager.SAMLUserAttributeName.value());
                }
                if (_samlAuthManager.getIdpEncryptionKey() != null && _samlAuthManager.getSpKeyPair() != null &&  _samlAuthManager.getSpKeyPair().getPrivate() != null) {
                    Credential credential = SecurityHelper.getSimpleCredential(_samlAuthManager.getIdpEncryptionKey().getPublicKey(),
                            _samlAuthManager.getSpKeyPair().getPrivate());
                    StaticKeyInfoCredentialResolver keyInfoResolver = new StaticKeyInfoCredentialResolver(credential);
                    EncryptedKeyResolver keyResolver = new InlineEncryptedKeyResolver();
                    Decrypter decrypter = new Decrypter(null, keyInfoResolver, keyResolver);
                    decrypter.setRootInNewDocument(true);
                    List<EncryptedAssertion> encryptedAssertions = processedSAMLResponse.getEncryptedAssertions();
                    if (encryptedAssertions != null) {
                        for (EncryptedAssertion encryptedAssertion : encryptedAssertions) {
                            Assertion assertion = null;
                            try {
                                assertion = decrypter.decrypt(encryptedAssertion);
                            } catch (DecryptionException e) {
                                s_logger.warn("SAML EncryptedAssertion error: " + e.toString());
                            }
                            if (assertion == null) {
                                continue;
                            }
                            Signature encSig = assertion.getSignature();
                            if (_samlAuthManager.getIdpSigningKey() != null && encSig != null) {
                                BasicX509Credential sigCredential = new BasicX509Credential();
                                sigCredential.setEntityCertificate(_samlAuthManager.getIdpSigningKey());
                                SignatureValidator validator = new SignatureValidator(sigCredential);
                                try {
                                    validator.validate(encSig);
                                } catch (ValidationException e) {
                                    s_logger.error("SAML Response's signature failed to be validated by IDP signing key:" + e.getMessage());
                                    throw new ServerApiException(ApiErrorCode.ACCOUNT_ERROR, _apiServer.getSerializedApiError(ApiErrorCode.ACCOUNT_ERROR.getHttpCode(),
                                            "SAML Response's signature failed to be validated by IDP signing key",
                                            params, responseType));
                                }
                            }
                            if (username == null) {
                                username = SAMLUtils.getValueFromAttributeStatements(assertion.getAttributeStatements(), SAML2AuthManager.SAMLUserAttributeName.value());
                            }
                        }
                    }
                }

                if (username == null) {
                    throw new ServerApiException(ApiErrorCode.ACCOUNT_ERROR, _apiServer.getSerializedApiError(ApiErrorCode.ACCOUNT_ERROR.getHttpCode(),
                            "Failed to find admin configured username attribute in the SAML Response. Please ask your administrator to check SAML user attribute name.", params, responseType));
                }
                List<UserAccountVO> userAccounts = _userAccountDao.getAllUsersByName(username);
                UserAccount userAccount = null;
                if (userAccounts != null && userAccounts.size() > 0) {
                    userAccount = userAccounts.get(0);
                }
                if (userAccount != null) {
                    try {
                        if (_apiServer.verifyUser(userAccount.getId())) {
                            LoginCmdResponse loginResponse = (LoginCmdResponse) _apiServer.loginUser(session, userAccount.getUsername(), userAccount.getPassword(), userAccount.getDomainId(), null, remoteAddress, params);
                            resp.addCookie(new Cookie("userid", URLEncoder.encode(loginResponse.getUserId(), HttpUtils.UTF_8)));
                            resp.addCookie(new Cookie("domainid", URLEncoder.encode(loginResponse.getDomainId(), HttpUtils.UTF_8)));
                            resp.addCookie(new Cookie("role", URLEncoder.encode(loginResponse.getType(), HttpUtils.UTF_8)));
                            resp.addCookie(new Cookie("username", URLEncoder.encode(loginResponse.getUsername(), HttpUtils.UTF_8)));
                            resp.addCookie(new Cookie("sessionkey", URLEncoder.encode(loginResponse.getSessionKey(), HttpUtils.UTF_8)));
                            resp.addCookie(new Cookie("account", URLEncoder.encode(loginResponse.getAccount(), HttpUtils.UTF_8)));
                            String timezone = loginResponse.getTimeZone();
                            if (timezone != null) {
                                resp.addCookie(new Cookie("timezone", URLEncoder.encode(timezone, HttpUtils.UTF_8)));
                            }
                            resp.addCookie(new Cookie("userfullname", URLEncoder.encode(loginResponse.getFirstName() + " " + loginResponse.getLastName(), HttpUtils.UTF_8).replace("+", "%20")));
                            resp.sendRedirect(SAML2AuthManager.SAMLCloudStackRedirectionUrl.value());
                            return ApiResponseSerializer.toSerializedString(loginResponse, responseType);
                        }
                    } catch (final CloudAuthenticationException ignored) {
                    }
                }
            }
        } catch (IOException e) {
            auditTrailSb.append("SP initiated SAML authentication using HTTP redirection failed:");
            auditTrailSb.append(e.getMessage());
        }
        throw new ServerApiException(ApiErrorCode.ACCOUNT_ERROR, _apiServer.getSerializedApiError(ApiErrorCode.ACCOUNT_ERROR.getHttpCode(),
                "Unable to authenticate user while performing SAML based SSO. Please make sure your user/account has been added, enable and authorized by the admin before you can authenticate. Please contact your administrator.",
                params, responseType));
    }

    @Override
    public APIAuthenticationType getAPIType() {
        return APIAuthenticationType.LOGIN_API;
    }

    @Override
    public void setAuthenticators(List<PluggableAPIAuthenticator> authenticators) {
        for (PluggableAPIAuthenticator authManager: authenticators) {
            if (authManager != null && authManager instanceof SAML2AuthManager) {
                _samlAuthManager = (SAML2AuthManager) authManager;
            }
        }
        if (_samlAuthManager == null) {
            s_logger.error("No suitable Pluggable Authentication Manager found for SAML2 Login Cmd");
        }
    }
}
