package com.lumen.vertxapi.vertxapi;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2Options;
import io.vertx.ext.auth.oauth2.providers.OpenIDConnectAuth;
import io.vertx.ext.web.client.OAuth2WebClientOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.SslMode;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MainVerticle extends AbstractVerticle {

    private final static Logger LOG = Logger.getLogger(MainVerticle.class.getName());

    private String bindAddress;
    private int bindPort;

    private String logiBackendHost;
    private int logiBackendPort;
    private String keycloakBaseUrl;
    private String keycloakClientId;
    private String keycloakClientSecret;
    private String keycloakRealm;

    static {
        LOG.info("Customizing the built-in jackson ObjectMapper...");
        var objectMapper = DatabindCodec.mapper();
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);
        objectMapper.disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS);

        JavaTimeModule module = new JavaTimeModule();
        objectMapper.registerModule(module);
    }

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        LOG.log(Level.INFO, "Starting HTTP server");
        // setupLogging();

        readConfigProps();

        //Create a PgPool instance
        var pgPool = pgPool();

        //Creating PostRepository
        var postRepository = PostRepository.create(pgPool);

        //Creating PostHandler
        var postHandlers = PostsHandler.create(postRepository);

        // Initializing the sample data
        var initializer = DataInitializer.create(pgPool);
        initializer.run();

        // Configure routes
        var router = routes(postHandlers);

        // JksOptions keyOptions = new JksOptions();
        // keyOptions.setPath("/home/toor/certs2/server.jks");
        // keyOptions.setPath("/home/toor/checkout/devprojects/keycloak-nginx/app/nginx/certs/localhost.jks");
        // keyOptions.setPath("/home/toor/checkout/ctl-fed-security/dev-support/kubernetes/charts/dev-ingress/certs/dev-ingress-tls.jks");
        // keyOptions.setPath("/home/toor/certs/server.jks");
        // keyOptions.setPassword("changeit");
    
        HttpServerOptions options = new HttpServerOptions()
        .setIdleTimeout(0);
  
        // .setUseAlpn(true)
        // .setSsl(true)
        // .setKeyStoreOptions(keyOptions);
  
        // Create the HTTP server
        vertx.createHttpServer(options)
            // Handle every request using the router
            .requestHandler(router)
            // Start listening
            .listen(bindPort, bindAddress)
            // Print the port
            .onSuccess(server -> {
                startPromise.complete();
                System.out.println("HTTP server started on port " + server.actualPort());
            })
            .onFailure(event -> {
                startPromise.fail(event);
                System.out.println("Failed to start HTTP server:" + event.getMessage());
            })
        ;

    }

    // create routes
    private Router routes(PostsHandler handlers) 
    {
        LOG.log(Level.INFO, String.format("APP_BIND_ADDRESS: %s", bindAddress));
        LOG.log(Level.INFO, String.format("APP_BIND_PORT: %s", bindPort));
        LOG.log(Level.INFO, String.format("site: %s/realms/%s", keycloakBaseUrl, keycloakRealm));
        LOG.log(Level.INFO, String.format("keycloakBaseUrl: %s", keycloakBaseUrl));
        LOG.log(Level.INFO, String.format("keycloakRealm: %s", keycloakRealm));
        LOG.log(Level.INFO, String.format("keycloakClientId: %s", keycloakClientId));

        // set up keycloak authentication
        final Future<OAuth2Auth> oa2 = OpenIDConnectAuth.discover(
                vertx,
                new OAuth2Options()
                        .setClientId(keycloakClientId)
                        .setTenant(keycloakRealm)
                        .setClientSecret(keycloakClientSecret)
                        .setSite(String.format("%s/realms/%s", keycloakBaseUrl, keycloakRealm)))

        .onSuccess(oauth2 ->
                {
                    // the setup call succeeded.
                    // at this moment your auth is ready to use
                    LOG.info("success!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                })
                .onComplete(oauth2 ->
                {
                    LOG.info("complete!!!");
                })
                .onFailure(err -> {
                    // the setup failed.
                    LOG.warning(String.format("Initialization of OAuth2 failed: %s", err.getMessage()));
                });

        // LOG.info("Waiting on initialization of OAuth2");
        // while (!oa2.isComplete())
        // {
        //     LOG.info(String.format("is-complete: %b", oa2.isComplete()));
        //     LOG.info(String.format("failed: %b", oa2.failed()));
        //     LOG.info(String.format("test: %b", oa2.result() == null));
        //     try
        //     {
        //         Thread.sleep(1000);
        //     }
        //     catch (InterruptedException e)
        //     {
        //         break;
        //     }
        // }

        // if (oa2.failed())
        // {
        //     throw new IllegalStateException("Initialization of OAuth2 failed");
        // }
        // LOG.info("Initialization of OAuth2 succeeded");

        // Create a Router
        Router router = Router.router(vertx);
        // register BodyHandler globally.
        //router.route().handler(BodyHandler.create());
        router.get("/posts").produces("application/json").handler(handlers::all);
        router.post("/posts").consumes("application/json").handler(BodyHandler.create()).handler(handlers::save);
        router.get("/posts/:id").produces("application/json").handler(handlers::get)
            .failureHandler(frc -> {
                Throwable failure = frc.failure();
                if (failure instanceof PostNotFoundException) {
                    frc.response().setStatusCode(404).end();
                }
                frc.response().setStatusCode(500).setStatusMessage("Server internal error:" + failure.getMessage()).end();
            });
        router.put("/posts/:id").consumes("application/json").handler(BodyHandler.create()).handler(handlers::update);
        router.delete("/posts/:id").handler(handlers::delete);

        router.get("/hello").handler(rc -> rc.response().end("Hello from my route"));

        return router;
    }

    private Pool pgPool() 
    {
        PgConnectOptions connectOptions = new PgConnectOptions()
            .setPort(logiBackendPort)
            .setHost(logiBackendHost)
            .setDatabase("blogdb")
            .setUser("toor")
            .setPassword("Oicu812Oicu812")

            .setSsl(true)
            .setSslMode(SslMode.REQUIRE)
            .setKeyStoreOptions(new JksOptions().setPath("/home/toor/certs/server.jks").setPassword("changeit"));
            // .setKeyStoreOptions(new JksOptions().setPath("./server.jks").setPassword("changeit"));

        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);

        // Create the pool from the data object
        Pool pool = Pool.pool(vertx, connectOptions, poolOptions);

        return pool;
    }

    /**
     * Configure logging from logging.properties file.
     * When using custom JUL logging properties, named it to vertx-default-jul-logging.properties
     * or set java.util.logging.config.file system property to locate the properties file.
     */
    private static void setupLogging() throws IOException {
        try (InputStream is = MainVerticle.class.getResourceAsStream("/logging.properties")) {
            LogManager.getLogManager().readConfiguration(is);
        }
    }

    private void readConfigProps() throws IllegalArgumentException, InterruptedException
    {
        // look at environment variables and system properties
        // to get our required configuration parameters.

        String tmp = getConfigProp(ApplicationConstants.CONFIG_PROP__APP_BIND_ADDRESS);

        // verify that tmp looks like a real IP.
        IPAddressString bindAddressString = new IPAddressString(tmp);
        IPAddress bindAddressTmp = bindAddressString.getAddress();

        if (bindAddressTmp == null)
        {
            // the address supplied was not a valid IP address
            throw new IllegalArgumentException(
                    String.format("The value supplied for %s property was not a valid ip address",
                            ApplicationConstants.CONFIG_PROP__APP_BIND_ADDRESS));
        }
        bindAddress = tmp;

        tmp = getConfigProp(ApplicationConstants.CONFIG_PROP__APP_BIND_PORT);
        // check that tmp looks like a valid bind port
        Integer tmpPort;
        try
        {
            tmpPort = Integer.parseInt(tmp);

            if (tmpPort < ApplicationConstants.TCP_PORT_MIN || tmpPort > ApplicationConstants.TCP_PORT_MAX)
            {
                // not a valid port
                throw new IllegalArgumentException(String.format("The value for %s property must be a value between %d and %d",
                        ApplicationConstants.CONFIG_PROP__APP_BIND_PORT,
                        ApplicationConstants.TCP_PORT_MIN,
                        ApplicationConstants.TCP_PORT_MAX));
            }
            bindPort = tmpPort;
        }
        catch (NumberFormatException nfe)
        {
            // not a valid number
            throw new IllegalArgumentException(String.format("The value for %s property couldn't be converted to a number",
                    ApplicationConstants.CONFIG_PROP__APP_BIND_PORT), nfe);
        }

        tmp = getConfigProp(ApplicationConstants.CONFIG_PROP__LOGI_BACKEND_PORT);
        // check that tmp looks like a valid logi port
        try
        {
            tmpPort = Integer.parseInt(tmp);

            if (tmpPort < ApplicationConstants.TCP_PORT_MIN || tmpPort > ApplicationConstants.TCP_PORT_MAX)
            {
                // not a valid port
                throw new IllegalArgumentException(String.format("The value for %s property must be a value between %d and %d",
                        ApplicationConstants.CONFIG_PROP__LOGI_BACKEND_PORT,
                        ApplicationConstants.TCP_PORT_MIN,
                        ApplicationConstants.TCP_PORT_MAX));
            }
            logiBackendPort = tmpPort;
        }
        catch (NumberFormatException nfe)
        {
            // not a valid number
            throw new IllegalArgumentException(String.format("The value for %s property couldn't be converted to a number",
                    ApplicationConstants.CONFIG_PROP__LOGI_BACKEND_PORT), nfe);
        }

        tmp = getConfigProp(ApplicationConstants.CONFIG_PROP__LOGI_BACKEND_HOST);
        logiBackendHost = tmp;

        tmp = getConfigProp(ApplicationConstants.CONFIG_PROP__KEYCLOAK_BASE_URL);
        keycloakBaseUrl = tmp;

        tmp = getConfigProp(ApplicationConstants.CONFIG_PROP__KEYCLOAK_CLIENT_ID);
        keycloakClientId = tmp;

        tmp = getConfigProp(ApplicationConstants.CONFIG_PROP__KEYCLOAK_CLIENT_SECRET);
        keycloakClientSecret = tmp;
        tmp = getConfigProp(ApplicationConstants.CONFIG_PROP__KEYCLOAK_REALM);
        keycloakRealm = tmp;
    }

    /**
     * Attempt to get named property first from environment variables and if not
     * found then system props. If the property is not found in either location
     * throw an IllegalArgumentException.
     *
     * @param propName Name of environment variable or system prop we want the
     * value for.
     * @return The value of the system prop
     *
     * @throws IllegalArgumentException If the value isn't present.
     */
    private String getConfigProp(String propName) throws IllegalArgumentException
    {
        String ret;

        // first try as environment var
        ret = System.getenv(propName);

        if (ret == null)
        {
            // try as a system property
            ret = System.getProperty(propName);
        }

        if (ret != null)
        {
            // ret could still be blank, so let's trim
            ret = ret.trim();
        }

        if (ret == null || ret.isBlank())
        {
            // we have a null or blank value
            throw new IllegalArgumentException(String.format("No value supplied for config prop %s", propName));
        }

        // we have a non-null value
        return ret;
    }

}
