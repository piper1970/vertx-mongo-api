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
import java.util.Optional;
import java.util.stream.Collectors;

import static com.ssarge.VertxClass.AppConstants.*;

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
                case CREATE_PRODUCT:
                    createProduct(input, message);
                    break;
                case GET_ALL_PRODUCTS:
                    getAllProducts(message);
                    break;
                case GET_PRODUCT:
                    getProduct(input.getString("id"), message);
                    break;
                case UPDATE_PRODUCT:
                    updateProduct(input.getString("id"), input, message);
                    break;
                case DELETE_PRODUCT:
                    deleteProduct(input.getString("id"), message);
                    break;
                default:
                    message.reply(new JsonObject().put("error", "Unrecognized command"));
            }
        });
    }

    private void getAllProducts(Message<Object> message) {
        FindOptions findOptions = new FindOptions();
        findOptions.setLimit(30);
        try {
            mongoClient.findWithOptions("products", new JsonObject(), findOptions, results -> {
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
                    message.reply(new JsonObject().put("error", Optional.ofNullable(results.cause())
                            .map(Throwable::getMessage).orElse("No results found")).toString());
                }
            });
        } catch (Exception exc) {
            LOGGER.error("MongoDB failed with exception " + exc.getMessage(), exc);
            message.reply(new JsonObject().put("error", "There were problems receiving the data from the backend"));
        }
    }

    private void getProduct(String id, Message<Object> message) {
        try {
            mongoClient.findOne("products", new JsonObject().put("_id", id), null, results -> {
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
                    message.reply(new JsonObject().put("message", Optional.ofNullable(results.cause())
                            .map(Throwable::getMessage).orElse("No results found")).toString());
                }
            });
        } catch (Exception exc) {
            LOGGER.error("MongoDB failed with exception " + exc.getMessage(), exc);
            message.reply(new JsonObject().put("error", "There were problems receiving the data from the backend").toString());
        }
    }

    private void createProduct(JsonObject input, Message<Object> message) {
        try {
            JsonObject body = input.getJsonObject("value");
            Product product = body.mapTo(Product.class);
            mongoClient.save("products", JsonObject.mapFrom(product), results -> {
                if (results.succeeded()) {
                    product.setId(results.result());
                    message.reply(JsonObject.mapFrom(product).toString());
                } else {
                    LOGGER.error("Failed to save product to database");
                    message.reply(new JsonObject().put("error", "Unable to create new product").toString());
                }
            });
        } catch (Exception exc) {
            LOGGER.error("MongoDB failed with exception " + exc.getMessage(), exc);
            message.reply(new JsonObject().put("error", "There were problems posting data to the backend database").toString());
        }
    }

    private void updateProduct(String id, JsonObject input, Message<Object> message) {
        JsonObject query = new JsonObject().put("_id", id);
        JsonObject update = input.getJsonObject("value");
        update.put("_id", id);
        try {
            mongoClient.replaceDocuments("products", query, update, response -> {
                if (response.succeeded()) {
                    LOGGER.info("Product with id " + id + " has been updated");
                    Product product = Product.builder()
                            .id(id)
                            .description(update.getString("description"))
                            .number(update.getString("number"))
                            .build();
                    message.reply(JsonObject.mapFrom(product).toString());
                } else {
                    LOGGER.error("Therew were problems updating record " + id + " in the database");
                    message.reply(new JsonObject()
                            .put("error", "There were problems updating the record on the backend")
                            .put("cause", Optional.ofNullable(response.cause()).map(Throwable::getMessage).orElse("Could not update record"))
                            .toString());
                }
            });
        } catch (Exception exc) {
            LOGGER.error("MongoDB failed with exception " + exc.getMessage(), exc);
            message.reply(new JsonObject().put("error", "There were problems updating data to the backend database").toString());
        }
    }

    private void deleteProduct(String id, Message<Object> message) {
        JsonObject query = new JsonObject().put("_id", id);
        try {
            mongoClient.removeDocument("products", query, response -> {
                if (response.succeeded()) {
                    LOGGER.info("Product with id " + id + " has been removed", id);
                    message.reply(new JsonObject().put("status", "removed").put("id", id).toString());
                } else {
                    message.reply(new JsonObject()
                            .put("error", "There were problems removing the record from the backend")
                            .put("cause", Optional.ofNullable(response.cause()).map(Throwable::getMessage).orElse("Could not delete record"))
                            .toString());
                }
            });
        } catch (Exception exc) {
            LOGGER.error("MongoDB failed with exception " + exc.getMessage(), exc);
            message.reply(new JsonObject().put("error", "There were problems deleting record " + id + " from the backend database").toString());
        }
    }
}
