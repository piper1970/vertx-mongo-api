package com.ssarge.VertxClass;

import com.ssarge.VertxClass.routes.ProductRouteHandler;
import com.ssarge.VertxClass.routes.RouteHandler;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.*;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;


public class ApiVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiVerticle.class);

    private final List<RouteHandler> routeHandlerList;

    private Integer port;
    private String passPhrase;

    public static void main(String[] args) {

        VertxOptions vertxOptions = new VertxOptions();
        vertxOptions.getEventBusOptions().setClustered(true);
        Vertx.clusteredVertx(vertxOptions, result -> {
            if (result.succeeded()) {
                Vertx vertx = result.result();
                ApiVerticle apiVerticle = new ApiVerticle(new ProductRouteHandler(vertx));
                ConfigRetriever.create(vertx)
                        .getConfig(config -> {
                            if (config.succeeded()) {
                                LOGGER.info("Config options found");
                                DeploymentOptions options = new DeploymentOptions().setConfig(config.result());
                                vertx.deployVerticle(apiVerticle, options);
                            } else {
                                LOGGER.error("No config options found. Deployment failed");
                            }
                        });
            }
        });

    }

    private ApiVerticle(RouteHandler... routes) {
        super();
        routeHandlerList = Arrays.asList(routes);
    }

    @Override
    public void start(){
        intitialize();

        LOGGER.info("starting AppVerticle");

        Router router = Router.router(vertx);
        router.route().handler(CookieHandler.create());
        router.route("/api/*").handler(this::defaultRouteProcessor);

        routeHandlerList.forEach(subRoute -> router.mountSubRouter("/api/", subRoute.initializeRouter()));

        router.get("/yo.html").handler(context -> {

            String name = Optional.ofNullable(context.getCookie("name"))
                    .map(Cookie::getValue)
                    .orElseGet(() -> {
                        // Not quite pure, but does the trick
                        String fakeName = "Bubba";
                        Cookie cookie = Cookie.cookie("name", fakeName);
                        cookie.setPath("/");
                        cookie.setMaxAge(Duration.of(1, ChronoUnit.DAYS).getSeconds() * 365);
                        context.addCookie(cookie);
                        return fakeName;
                    });

            ClassLoader cl = getClass().getClassLoader();
            File file = Optional.ofNullable(cl.getResource("webroot/yo.html"))
                    .map(URL::getFile)
                    .map(File::new)
                    .orElse(null);

            String mappedHTML = "";

            Scanner scanner = null;
            try {
                StringBuilder sb = new StringBuilder();

                if (file != null) {
                    scanner = new Scanner(file);

                    while (scanner.hasNextLine()) {
                        String line = scanner.nextLine();
                        sb.append(line).append("\n");
                    }

                    mappedHTML = sb.toString();

                    mappedHTML = replaceAllTokens(mappedHTML, "{name}", name);
                }


            } catch (IOException exc) {
                LOGGER.error(exc.getMessage(), exc);
            } finally {
                if (scanner != null) {
                    scanner.close();
                }
            }

            context.response().putHeader("content-type", "text/html").end(mappedHTML);

        });

        router.route().handler(StaticHandler.create().setCachingEnabled(false));

        vertx.createHttpServer().requestHandler(router).listen(port, result -> {
            if (result.succeeded()) {
                System.out.println("Server started on port " + port.toString());
            } else {
                System.err.println("Problems occurred trying to startup server on port " +
                        port.toString() + ". Cause: " + result.cause().getMessage());
            }
        });

//        vertx.eventBus().consumer("com.ssarge.firstconsumer", msg -> {
//            System.out.println("Received message: " + msg.body());
//            msg.reply(new JsonObject().put("responseCode", "OK").put("message", "Thank you"));
//        });
//
//        vertx.setTimer(10000, handler -> {
//            JsonObject msg = new JsonObject()
//                    .put("msg", "Hello from beyond!")
//                    .put("timestamp", Instant.now());
//            sendMessage(vertx, "com.ssarge.firstconsumer", msg);
//        });

    }

    @Override
    public void stop(){
        LOGGER.info("stopping AppVerticle");
    }

    private void defaultRouteProcessor(RoutingContext routingContext) {
        MultiMap headers = routingContext.request().headers();
        if (!passPhrase.equals(headers.get("AuthToken"))) {
            routingContext.response().setStatusCode(401)
                    .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                    .end(Json.encodePrettily(new JsonObject().put("error", "Authorization required")));
        } else {
            routingContext.response().putHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                    .putHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, DELETE");

            // move to next in line
            routingContext.next();
        }
    }

    private void intitialize() {
        port = (Integer) Optional.ofNullable(config().getValue("http.port"))
                .orElseThrow(() -> new IllegalStateException("http.port not set in config file properly"));
        passPhrase = (String) Optional.ofNullable(config().getValue("server.passphrase"))
                .orElseThrow(() -> new IllegalStateException("server.passphrase not set properly in config"));
    }

    @SuppressWarnings("SameParameterValue")
    private String replaceAllTokens(String source, String token, String replacement) {

        String output = source;

        while (output.contains(token)) {
            output = output.replace(token, replacement);
        }

        return output;
    }
}
