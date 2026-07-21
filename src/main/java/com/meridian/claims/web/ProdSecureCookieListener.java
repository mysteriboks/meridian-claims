package com.meridian.claims.web;

import com.meridian.claims.util.AppProfile;
import org.apache.log4j.Logger;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.SessionCookieConfig;

/**
 * Sets the session-cookie Secure flag programmatically under the prod profile.
 *
 * The static web.xml &lt;secure&gt; element is intentionally left commented so that
 * the default dev profile keeps plain-HTTP sessions working.  This listener
 * activates Secure only when claims.profile=prod, which means a TLS terminator
 * is in front of the app.
 *
 * Must be declared BEFORE the Spring ContextLoaderListener in web.xml so it
 * configures the cookie before any session is created.
 */
public class ProdSecureCookieListener implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(ProdSecureCookieListener.class);

    public void contextInitialized(ServletContextEvent sce) {
        AppProfile profile = new AppProfile();
        if (profile.isProd()) {
            ServletContext ctx = sce.getServletContext();
            SessionCookieConfig cookieConfig = ctx.getSessionCookieConfig();
            cookieConfig.setSecure(true);
            LOG.info("ProdSecureCookieListener: session cookie Secure flag set (prod profile).");
        }
    }

    public void contextDestroyed(ServletContextEvent sce) {
        // nothing to release
    }
}
