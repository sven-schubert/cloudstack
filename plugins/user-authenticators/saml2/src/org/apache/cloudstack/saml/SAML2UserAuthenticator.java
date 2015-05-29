//  Licensed to the Apache Software Foundation (ASF) under one or more
//  contributor license agreements.  See the NOTICE file distributed with
//  this work for additional information regarding copyright ownership.
//  The ASF licenses this file to You under the Apache License, Version 2.0
//  (the "License"); you may not use this file except in compliance with
//  the License.  You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
package org.apache.cloudstack.saml;

import com.cloud.server.auth.DefaultUserAuthenticator;
import com.cloud.server.auth.UserAuthenticator;
import com.cloud.user.User;
import com.cloud.user.UserAccount;
import com.cloud.user.dao.UserAccountDao;
import com.cloud.user.dao.UserDao;
import com.cloud.utils.Pair;
import org.apache.cxf.common.util.StringUtils;
import org.apache.log4j.Logger;
import org.opensaml.DefaultBootstrap;
import org.opensaml.saml2.core.Response;
import org.opensaml.saml2.core.StatusCode;
import org.opensaml.xml.ConfigurationException;
import org.opensaml.xml.io.UnmarshallingException;
import org.xml.sax.SAXException;

import javax.ejb.Local;
import javax.inject.Inject;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.FactoryConfigurationError;
import java.io.IOException;
import java.util.Map;

@Local(value = {UserAuthenticator.class})
public class SAML2UserAuthenticator extends DefaultUserAuthenticator {
    public static final Logger s_logger = Logger.getLogger(SAML2UserAuthenticator.class);

    @Inject
    private UserAccountDao _userAccountDao;
    @Inject
    private UserDao _userDao;

    @Override
    public Pair<Boolean, ActionOnFailedAuthentication> authenticate(String username, String password, Long domainId, Map<String, Object[]> requestParameters) {
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Trying SAML2 auth for user: " + username);
        }

        if (StringUtils.isEmpty(username) || StringUtils.isEmpty(password)) {
            s_logger.debug("Username or Password cannot be empty");
            return new Pair<Boolean, ActionOnFailedAuthentication>(false, null);
        }

        final UserAccount userAccount = _userAccountDao.getUserAccount(username, domainId);
        if (userAccount == null) {
            s_logger.debug("Unable to find user with " + username + " in domain " + domainId);
            return new Pair<Boolean, ActionOnFailedAuthentication>(false, null);
        } else {
            User user = _userDao.getUser(userAccount.getId());
            if (user != null && requestParameters != null && requestParameters.containsKey(SAMLUtils.SAML_RESPONSE)) {
                final String samlResponse = ((String[])requestParameters.get(SAMLUtils.SAML_RESPONSE))[0];
                Response responseObject = null;
                try {
                    DefaultBootstrap.bootstrap();
                    responseObject = SAMLUtils.decodeSAMLResponse(samlResponse);
                } catch (ConfigurationException | FactoryConfigurationError | ParserConfigurationException | SAXException | IOException | UnmarshallingException e) {
                    return new Pair<Boolean, ActionOnFailedAuthentication>(false, null);
                }
                if (!responseObject.getStatus().getStatusCode().getValue().equals(StatusCode.SUCCESS_URI)) {
                    return new Pair<Boolean, ActionOnFailedAuthentication>(false, null);
                }
                return new Pair<Boolean, ActionOnFailedAuthentication>(true, null);
            }
        }
        // Deny all by default
        return new Pair<Boolean, ActionOnFailedAuthentication>(false, ActionOnFailedAuthentication.INCREMENT_INCORRECT_LOGIN_ATTEMPT_COUNT);
    }

    @Override
    public String encode(final String password) {
        return SAMLUtils.generateSecureRandomId();
    }
}
