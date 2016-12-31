/*
 *                                   SOFTWARE USE PERMISSION
 *
 *  By downloading and accessing this software and associated documentation files ("Software") you are granted the
 *  unrestricted right to deal in the Software, including, without limitation the right to use, copy, modify, publish,
 *  sublicense and grant such rights to third parties, subject to the following conditions:
 *
 *  The following copyright notice and this permission notice shall be included in all copies, modifications or
 *  substantial portions of this Software: Copyright © 2016 GSM Association.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS," WITHOUT WARRANTY OF ANY KIND, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 *  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 *  HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 *  ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE. YOU
 *  AGREE TO INDEMNIFY AND HOLD HARMLESS THE AUTHORS AND COPYRIGHT HOLDERS FROM AND AGAINST ANY SUCH LIABILITY.
 */

package com.gsma.mobileconnect.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.gsma.mobileconnect.discovery.DiscoveryException;
import com.gsma.mobileconnect.discovery.DiscoveryResponse;
import com.gsma.mobileconnect.discovery.IDiscovery;
import com.gsma.mobileconnect.impl.Factory;
import com.gsma.mobileconnect.oidc.*;
import com.gsma.mobileconnect.utils.KeyValuePair;
import com.gsma.mobileconnect.helpers.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.WebUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * Mobile Connect demonstration application.
 *
 * This is hosted as a spring boot application, but spring boot is not required to use the Mobile Connect SDK.
 *
 */
@Controller
@EnableAutoConfiguration
public class App
{
    private static final String ERROR_ATTRIBUTE_NAME = "error";
    private static final String ERROR_DESCRIPTION_ATTRIBUTE_NAME = "error_description";

    private static final String ERROR_PAGE = "error_page";
    private static final String AUTHORIZED_PAGE = "authorized_page";
    private static final String START_AUTHORIZATION_PAGE = "request_authorization_page";
    private static final String START_DISCOVERY_PAGE = "request_discovery_page";

    private static final String SESSION_KEY = "demo:key";

    @Autowired
    private IOIDC oidc;

    @Autowired
    private IDiscovery discovery;

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private MobileConnectConfig getMobileConnectConfig(HttpSession session)
    {
        // The Mobile Connect Interface methods expects a configuration object.
        // This can be unique per call or shared between calls as required.
        // Most of the values in the configuration object are optional.

        MobileConnectConfig mobileConnectConfig = null;
        synchronized (WebUtils.getSessionMutex(session))
        {
            mobileConnectConfig = (MobileConnectConfig) session.getAttribute(SESSION_KEY);

            if (null == mobileConnectConfig)
            {
                mobileConnectConfig = new MobileConnectConfig();

                // Registered application client id
                mobileConnectConfig.setClientId("xxxxx");

                // Registered application client secret
                mobileConnectConfig.setClientSecret("xxxxx");

                // Registered application url
                mobileConnectConfig.setApplicationURL("http://localhost:8080/mobile_connect");

                // URL of the Mobile Connect Discovery End Point
                mobileConnectConfig.setDiscoveryURL("xxxxx");

                // URL to inform the Discovery End Point to redirect to, this should route to the "/discovery_redirect" handler below
                mobileConnectConfig.setDiscoveryRedirectURL("http://localhost:8080/mobileconnect/discovery_redirect");

                // Authorization State would typically set to a unique value
                mobileConnectConfig.setAuthorizationState(MobileConnectInterface.generateUniqueString("state_"));

                // Authorization Nonce would typically set to a unique value
                mobileConnectConfig.setAuthorizationNonce(MobileConnectInterface.generateUniqueString("nonce_"));

                session.setAttribute(SESSION_KEY, mobileConnectConfig);
            }
        }
        return mobileConnectConfig;
    }

	/**
	 * This is the endpoint to initiate mobile connect.
     * <p>
     * The client javascript makes a JSON call to this method to initiate mobile connect authorization.
     * Authorization is carried out per operator, so the first step is to determine the operator.
	 *
     * @param servletRequest The request
     * @param servletResponse The response
     * @param servletSession The session
     * @return JSON object describing what to do next either error, display the operator selection page or the operator authorization page
     */
    @ResponseBody
	@RequestMapping("mobileconnect/start_discovery")
	MobileConnectStatus.ResponseJson startDiscovery(HttpServletRequest servletRequest, HttpServletResponse servletResponse, HttpSession servletSession)
	{
        MobileConnectConfig config = getMobileConnectConfig(servletSession);

        // This wraps the SDK to determine what the next step in the authorization process is: operator discovery or authorization with an operator.
        MobileConnectStatus mobileConnectStatus = MobileConnectInterface.callMobileConnectForStartDiscovery(discovery, config, servletRequest, servletResponse);

        if( mobileConnectStatus.isOperatorSelection())
        {
            // The operator cannot be identified, the operator selection url is set
            log.debug("The operator is unknown, redirect to the operator selection url: " + mobileConnectStatus.getUrl());
        }
        else if(mobileConnectStatus.isStartAuthorization())
        {
            // The operator has been identified. The discovery response is available.
            log.debug("The operator has been identified without requiring operator selection");

            DiscoveryResponse discoveryResponse = mobileConnectStatus.getDiscoveryResponse();
            logDiscoveryResponse(discoveryResponse);
        }
        else
        {
            // An error occurred, the error, description and nested exception (optional) are available.
            logError(mobileConnectStatus);
        }

        return mobileConnectStatus.getResponseJson();
	}

    /**
     * This is called by the redirect from the operator selection service.
     * <p>
     * The identified operator is encoded in the query string.
     * <p>
     * It responds with either:
     * <ul>
     * <li>an error
     * <li>a request to display the operator selection again
     * <li>a request to move onto authorization.
     * </ul>
     * <p>
     * It is assumed that this is redirected to from the operator selection popup so the return is a web page that contains
     * javascript that calls the parent page to continue the authorization process (either redisplay the operator selection
     * pop up or start authorization with the identified operator).
     *
     * @param servletRequest The servlet request
     * @param servletResponse The servlet response
     * @param servletSession The session
     * @param model The spring model to populate the response jsp
     * @return A Web page, the page contains javascript to call the parent page to continue the authorization process.
     */
	@RequestMapping("mobileconnect/discovery_redirect")
	String discoveryRedirect(HttpServletRequest servletRequest, HttpServletResponse servletResponse, HttpSession servletSession, Model model)
	{
        MobileConnectConfig config = getMobileConnectConfig(servletSession);

        MobileConnectStatus mobileConnectStatus = MobileConnectInterface.callMobileConnectOnDiscoveryRedirect(discovery, config, servletRequest, servletResponse);

        if(mobileConnectStatus.isError())
        {
            // An error occurred, the error, description and nested exception (optional) are available.
            logError(mobileConnectStatus);
        }
        else if(mobileConnectStatus.isStartDiscovery())
        {
            // The operator could not be identified, restart the discovery process.
            log.debug("The operator could not be identified, need to restart the discovery process.");
        }
        else if(mobileConnectStatus.isStartAuthorization())
        {
            // The operator has been identified. The discovery response is available.
            log.debug("The operator has been identified and the authorization process can begin");

            logDiscoveryResponse(mobileConnectStatus.getDiscoveryResponse());
        }

        return toPageDescription(mobileConnectStatus, model);
	}

    /**
     * This is called by the client javascript to initiate authorization with an operator.
     * <p>
     * This is typically called after the operator has been determined. The identified operator's discovery response is expected to
     * be stored in the session.
     * <p>
     * It will return a json object that contains an error, a request to initiate operator discovery or a url
     * to redirect to start authorization with the identified operator.
     *
     * @param servletRequest The request
     * @param servletSession The session
     * @return JSON object.
     */
    @ResponseBody
    @RequestMapping("mobileconnect/start_authorization")
    MobileConnectStatus.ResponseJson startAuthorization(HttpServletRequest servletRequest, HttpSession servletSession)
    {
        MobileConnectConfig config = getMobileConnectConfig(servletSession);

        MobileConnectStatus mobileConnectStatus = MobileConnectInterface.callMobileConnectForStartAuthorization(oidc, config, servletRequest);

        if(mobileConnectStatus.isError())
        {
            // An error occurred, the error, description optionally an exception is available.
            log.debug("Failed starting the authorization process");
            logError(mobileConnectStatus);
        }
        else if(mobileConnectStatus.isStartDiscovery())
        {
            // The operator could not be identified, start the discovery process.
            log.debug("The operator could not be identified, need to restart the discovery process.");
        }
        else if(mobileConnectStatus.isAuthorization())
        {
            // The operator has been identified, the discovery response and authentication url is available.
            log.debug("The operator has been identified and the authorization process can start");
            log.debug("URL is: " + mobileConnectStatus.getUrl());
            logDiscoveryResponse(mobileConnectStatus.getDiscoveryResponse());
        }

        return mobileConnectStatus.getResponseJson();
    }

    /**
     * This is called by the redirect from the operator authentication function.
     * <p>
     * This contains information that allows the SDK to obtain an authorization token (PCR) directly from the operator.
     * <p>
     * The response is either a successful authorization, an error or a request to identify the operator.
     *
     * @param servletRequest The request
     * @param servletSession The session
     * @param model The spring model to populate the jsp page
     * @return A web page
     */
    @RequestMapping("/mobile_connect")
    String authorizationRedirect(HttpServletRequest servletRequest, HttpSession servletSession, Model model)
    {
        MobileConnectConfig config = getMobileConnectConfig(servletSession);

        MobileConnectStatus mobileConnectStatus = MobileConnectInterface.callMobileConnectOnAuthorizationRedirect(oidc, config, servletRequest);

        if(mobileConnectStatus.isError())
        {
            // An error occurred, the error, description and (optionally) exception is available.
            log.debug("Authorization has failed");
            logError(mobileConnectStatus);
        }
        else if(mobileConnectStatus.isStartDiscovery())
        {
            // The operator could not be identified, start the discovery process.
            log.debug("The operator could not be identified, need to restart the discovery process.");
        }
        else if(mobileConnectStatus.isComplete())
        {
            // Successfully authenticated, ParsedAuthenticationResponse and RequestTokenResponse are available
            log.debug("Authorization has completed successfully");
            log.debug("PCR is " + mobileConnectStatus.getRequestTokenResponse().getResponseData().getParsedIdToken().get_pcr());
            logAuthorized(mobileConnectStatus);
        }

        return toPageDescription(mobileConnectStatus, model);
    }

    @RequestMapping("mobileconnect/mobile_connect_error")
    String mobileConnectError(HttpServletRequest request, Model model)
    {
        model.addAttribute(ERROR_ATTRIBUTE_NAME, request.getParameter(ERROR_ATTRIBUTE_NAME));
        model.addAttribute(ERROR_DESCRIPTION_ATTRIBUTE_NAME, request.getParameter(ERROR_DESCRIPTION_ATTRIBUTE_NAME));
        return ERROR_PAGE;
    }

    @ExceptionHandler(Exception.class)
    public String handleException(Exception ex, Model model)
    {
        log.error("Uncaught exception", ex);
        model.addAttribute(ERROR_ATTRIBUTE_NAME, "internal error");
        return ERROR_PAGE;
    }

    /**
     * Map the MobileConnectStatus to a jsp page.
     * @param status The MobileConnectStatus to map.
     * @param model The spring model to populate the page.
     * @return The name of the jsp page to return
     */
    private String toPageDescription(MobileConnectStatus status, Model model)
    {
        if(status.isComplete())
        {
            return AUTHORIZED_PAGE;
        }
        if(status.isStartDiscovery())
        {
            return START_DISCOVERY_PAGE;
        }
        if(status.isStartAuthorization())
        {
            return START_AUTHORIZATION_PAGE;
        }
        model.addAttribute(ERROR_ATTRIBUTE_NAME, status.getError());
        model.addAttribute(ERROR_DESCRIPTION_ATTRIBUTE_NAME, status.getDescription());
        return ERROR_PAGE;
    }

    private void logDiscoveryResponse(DiscoveryResponse response)
    {
        // The Discovery Response may be cached data as indicated by
        log.debug("Is the DiscoveryResponse from the cache: " + response.isCached());

        // The Discovery Response contains the response code (if not from cache)
        log.debug("Discovery response code: " + response.getResponseCode());

        // The Discovery Response contains the response headers (if not from cache).
        if(null != response.getHeaders())
        {
            for (KeyValuePair kvp : response.getHeaders())
            {
                log.debug(kvp.getKey() + ": " + kvp.getValue());
            }
        }

        // The Discovery Response contains the discovery JSON object
        JsonNode discoveryJson = response.getResponseData();
        log.debug("Serving operator is: " + discoveryJson.get("response").get("serving_operator").asText());
    }

    private void logAuthorized(MobileConnectStatus status)
    {
        ParsedAuthorizationResponse parsedAuthorizationResponse = status.getParsedAuthorizationResponse();
        if(null != parsedAuthorizationResponse)
        {
            log.debug("Code: " + parsedAuthorizationResponse.get_code());
            log.debug("State: " + parsedAuthorizationResponse.get_state());
        }
        RequestTokenResponse requestTokenResponse = status.getRequestTokenResponse();
        if(null != requestTokenResponse)
        {
            log.debug("Response Code: " + requestTokenResponse.getResponseCode());
            if(null != requestTokenResponse.getHeaders())
            {
                for (KeyValuePair kvp : requestTokenResponse.getHeaders())
                {
                    log.debug(kvp.getKey() + ": " + kvp.getValue());
                }
            }
            if(null != requestTokenResponse.getResponseData())
            {
                RequestTokenResponseData responseData = requestTokenResponse.getResponseData();
                log.debug("Time Received: " + responseData.getTimeReceived().getTime());
                if(null != responseData.getParsedIdToken())
                {
                    ParsedIdToken parsedIdToken = responseData.getParsedIdToken();
                    log.debug("Nonce: " + parsedIdToken.get_nonce());
                    log.debug("PCR: " + parsedIdToken.get_pcr());
                }
            }

        }
    }

    private void logError(MobileConnectStatus status)
    {
        log.debug("An error occurred: " + status.getError() + ": " + status.getDescription());
        Exception exception = status.getException();
        log.debug("Exception was: " + exception);

        // Depending on the exception more information may be available
        if(exception instanceof DiscoveryException)
        {
            DiscoveryException ex = (DiscoveryException)exception;
            log.debug(ex.getMessage());
            log.debug("URI: " + ex.getUri());
            log.debug("Response code: " + ex.getResponseCode());
            log.debug("Contents: " + ex.getContents());
            if(null != ex.getHeaders())
            {
                for (KeyValuePair kvp : ex.getHeaders())
                {
                    log.debug(kvp.getKey() + ": " + kvp.getValue());
                }
            }
        }
        else if( exception instanceof OIDCException)
        {
            OIDCException ex = (OIDCException)exception;
            log.debug(ex.getMessage());
            log.debug("URI: " + ex.getUri());
            log.debug("Response code: " + ex.getResponseCode());
            log.debug("Contents: " + ex.getContents());
            if(null != ex.getHeaders())
            {
                for (KeyValuePair kvp : ex.getHeaders())
                {
                    log.debug(kvp.getKey() + ": " + kvp.getValue());
                }
            }
        }
    }

    @Bean
    private IDiscovery getDiscovery()
    {
        log.debug("Created a discovery instance");
        return Factory.getDiscovery(Factory.getDefaultDiscoveryCache());
    }

    @Bean
    private IOIDC getOIDC()
    {
        log.debug("Created a oidc instance");
        return Factory.getOIDC();
    }

	public static void main(String[] args) throws Exception
    {
		SpringApplication.run(App.class, args);
	}

}
