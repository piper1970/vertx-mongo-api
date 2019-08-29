package com.ssarge.VertxClass.resources;

import com.ssarge.VertxClass.entity.Product;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;

import java.util.List;
import java.util.stream.Collectors;

public class MongoManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(MongoManager.class);

    private final MongoClient mongoClient;

    public MongoManager(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    public void registerConsumer(Vertx vertx) {
        vertx.eventBus().consumer("com.ssarge.mongoservice", message -> {
            JsonObject input = new JsonObject(message.body().toString());
            switch (input.getString("cmd")) {
                case "findAll":
                    getAllProducts(message);
                    break;
                case "findOne":
                    getProduct(message, input.getString("id"));
                    break;
                default:
                    message.reply(new JsonObject().put("error", "Unrecognized command"));
            }
        });
    }

    private void getAllProducts(Message<Object> message) {
        FindOptions findOptions = new FindOptions();
        findOptions.setLimit(30);
        mongoClient.findWithOptions("products", new JsonObject(), findOptions, results -> {
            try {
                if (results.succeeded()) {
                    List<JsonObject> resultList = results.result();
                    LOGGER.info("getAllProducts returning " + resultList.size() + " results");
                    List<Product> products = resultList.stream().map(obj -> Product.builder()
                            .id(obj.getString("_id"))
                            .number(obj.getString("number"))
                            .description(obj.getString("description"))
                            .build())
                            .collect(Collectors.toList());
                    message.reply(new JsonObject().put("products", new JsonArray(products)).toString());
                } else {
                    message.reply(new JsonObject().put("error", results.cause().getMessage()).toString());
                }
            } catch (Exception exc) {
                LOGGER.error("MongoDB failed with exception {}", exc.getMessage(), exc);
                message.reply(new JsonObject().put("error", "There were problems receiving the data from the backend"));
            }
        });
    }

    private void getProduct(Message<Object> message, String id) {
        mongoClient.findOne("products", new JsonObject().put("_id", id), null, results -> {
            try {
                if (results.succeeded()) {
                    JsonObject obj = results.result();
                    Product product = Product.builder()
                            .id(obj.getString("_id"))
                            .number(obj.getString("number"))
                            .description(obj.getString("description"))
                            .build();
                    LOGGER.info("getProductById returning results");
                    message.reply(JsonObject.mapFrom(product).toString());
                } else {
                    message.reply(new JsonObject().put("message", "No results found").toString());
                }
            } catch (Exception exc) {
                LOGGER.error("MongoDB failed with exception {}", exc.getMessage(), exc);
                message.reply(new JsonObject().put("error", "There were problems receiving the data from the backend").toString());
            }
        });
    }
}
