package com.ssarge.VertxClass;

import com.ssarge.VertxClass.resources.MongoManager;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;

import java.util.Optional;

public class MongoDBVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoDBVerticle.class);

    private MongoClient mongoClient;

    public static void main(String[] args) {
        VertxOptions vertxOptions = new VertxOptions();
        vertxOptions.getEventBusOptions().setClustered(true);
        Vertx.clusteredVertx(vertxOptions, result -> {
            if (result.succeeded()) {
                Vertx vertx = result.result();
                ConfigRetriever.create(vertx)
                        .getConfig(config -> {
                            if (config.succeeded()) {
                                LOGGER.info("Config options found");
                                DeploymentOptions options = new DeploymentOptions().setConfig(config.result());
                                vertx.deployVerticle(new MongoDBVerticle(), options);
                            } else {
                                LOGGER.error("No config options found. Deployment failed");
                            }
                        });
            }
        });

    }

    @Override
    public void start() {
        LOGGER.info("MongoDB Verticle Starting");

        JsonObject config = config();
        String connectionString = String.format("mongodb://%s:%d/%s",
                config.getString("mongodb.host"), config.getInteger("mongodb.port"),
                config.getString("mongodb.databasename"));

        JsonObject options = new JsonObject()
                .put("connection_string", connectionString)
                .put("useObjectId", true);

        // look for username, password and auth_source
        Optional.ofNullable(config.getString("mongodb.username", null))
                .ifPresent(user -> options.put("username", user));
        Optional.ofNullable(config.getString("mongodb.password", null))
                .ifPresent(pass -> options.put("password", pass));
        Optional.ofNullable(config.getString("mongodb.authSource", null))
                .ifPresent(auth -> options.put("authSource", auth));

        mongoClient = MongoClient.createShared(vertx, options);

        MongoManager mongoManager = new MongoManager(mongoClient);
        mongoManager.registerConsumer(vertx);
    }

    @Override
    public void stop() {
        LOGGER.info("MongoDB Verticle Stopping");
        Optional.ofNullable(mongoClient).ifPresent(MongoClient::close);
    }
}
