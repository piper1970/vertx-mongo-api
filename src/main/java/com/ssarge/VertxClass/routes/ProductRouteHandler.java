package com.ssarge.VertxClass.routes;

import com.ssarge.VertxClass.entity.Product;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ProductRouteHandler implements RouteHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductRouteHandler.class);
    private static final String JSON_TYPE = "appication/json";
    private static final String MONGO_SERVICE = "com.ssarge.mongoservice";

    private final Vertx vertx;
    private final MongoClient mongoClient;

    public ProductRouteHandler(Vertx vertx) {
        this.vertx = vertx;

        JsonObject dbConfig = new JsonObject()
                .put("connection_string", "mongodb://localhost:27017/MongoTest")
                .put("useObjectId", true);

        mongoClient = MongoClient.createShared(vertx, dbConfig);
    }

    @Override
    public Router initializeRouter() {
        Router productRouter = Router.router(vertx);

        productRouter.route("/v1/products*").handler(BodyHandler.create());
        productRouter.get("/v1/products").handler(this::getAllProducts);
        productRouter.get("/v1/products/:id").handler(this::getProductById);
        productRouter.post("/v1/products").handler(this::postProduct);
        productRouter.put("/v1/products/:id").handler(this::updateProductById);
        productRouter.delete("/v1/products/:id").handler(this::deleteProductById);

        return productRouter;
    }

    private void getAllProducts(RoutingContext routingContext) {

        vertx.eventBus().request(MONGO_SERVICE, new JsonObject().put("cmd", "findAll"), reply -> {
            if (reply.succeeded()) {
                JsonObject resultJson = new JsonObject(reply.result().body().toString());

                Optional.ofNullable(resultJson.getString("error"))
                        .ifPresentOrElse(error ->
                                        routingContext.response().setStatusCode(500)
                                                .putHeader(HttpHeaders.CONTENT_TYPE, JSON_TYPE)
                                                .end(Json.encodePrettily(new JsonObject().put("error", error)))
                                , () -> {
                                    JsonArray resultList = resultJson.getJsonArray("products");
                                    LOGGER.info("getAllProducts returning " + resultList.size() + " results");
                                    List<Product> products = resultList.stream()
                                            .map(obj -> new JsonObject(obj.toString()))
                                            .map(obj -> Product.builder()
                                                    .id(obj.getString("_id"))
                                                    .number(obj.getString("number"))
                                                    .description(obj.getString("description"))
                                                    .build())
                                            .collect(Collectors.toList());
                                    routingContext.response().setStatusCode(200)
                                            .putHeader(HttpHeaders.CONTENT_TYPE, JSON_TYPE)
                                            .end(Json.encodePrettily(new JsonObject().put("products", new JsonArray(products))));
                                });
            } else {
                routingContext.response().setStatusCode(401)
                        .putHeader(HttpHeaders.CONTENT_TYPE, JSON_TYPE)
                        .end();
            }
        });
    }

    private void getProductById(RoutingContext routingContext) {
        String id = routingContext.request().getParam("id");
        JsonObject message = new JsonObject().put("cmd", "findOne").put("id", id);
        vertx.eventBus().request(MONGO_SERVICE, message, reply -> {
            if (reply.succeeded()) {
                JsonObject msgJson = new JsonObject(reply.result().toString());
                Product product = Product.builder()
                        .id(msgJson.getString("_id"))
                        .number(msgJson.getString("number"))
                        .description(msgJson.getString("description"))
                        .build();
                LOGGER.info("getProductById returning results");
                routingContext.response().setStatusCode(200)
                        .putHeader(HttpHeaders.CONTENT_TYPE, JSON_TYPE)
                        .end(Json.encodePrettily(JsonObject.mapFrom(product)));

            } else {
                routingContext.response().setStatusCode(401)
                        .putHeader(HttpHeaders.CONTENT_TYPE, JSON_TYPE)
                        .end();
            }
        });
    }

    private void postProduct(RoutingContext routingContext) {
        routingContext.response().setStatusCode(500)
                .putHeader(HttpHeaders.CONTENT_TYPE, JSON_TYPE)
                .end(Json.encodePrettily(new JsonObject().put("error", "Not yet implemented")));
    }

    private void updateProductById(RoutingContext routingContext) {
        routingContext.response().setStatusCode(500)
                .putHeader(HttpHeaders.CONTENT_TYPE, JSON_TYPE)
                .end(Json.encodePrettily(new JsonObject().put("error", "Not yet implemented")));
    }

    private void deleteProductById(RoutingContext routingContext) {
        routingContext.response().setStatusCode(500)
                .putHeader(HttpHeaders.CONTENT_TYPE, JSON_TYPE)
                .end(Json.encodePrettily(new JsonObject().put("error", "Not yet implemented")));
    }

}
