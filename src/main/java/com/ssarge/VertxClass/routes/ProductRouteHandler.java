package com.ssarge.VertxClass.routes;

import com.ssarge.VertxClass.entity.Product;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.ssarge.VertxClass.resources.MongoManager.*;

public class ProductRouteHandler implements RouteHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductRouteHandler.class);
    private static final String JSON_TYPE = "appication/json";
    private static final String MONGO_SERVICE = "com.ssarge.mongoservice";

    private final Vertx vertx;

    public ProductRouteHandler(Vertx vertx) {
        this.vertx = vertx;
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

        vertx.eventBus().request(MONGO_SERVICE, new JsonObject().put("cmd", GET_ALL_PRODUCTS), reply -> {
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
                                            .map(obj -> obj.mapTo(Product.class))
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
        try {
            String id = routingContext.request().getParam("id");
            JsonObject message = new JsonObject().put("cmd", GET_PRODUCT).put("id", id);
            vertx.eventBus().request(MONGO_SERVICE, message, reply -> {
                if (reply.succeeded()) {
                    JsonObject msgJson = new JsonObject(reply.result().body().toString());
                    Optional.ofNullable(msgJson.getString("error"))
                            .ifPresentOrElse(error ->
                                            routingContext.response().setStatusCode(500)
                                                    .putHeader(HttpHeaders.CONTENT_TYPE, JSON_TYPE)
                                                    .end(Json.encodePrettily(new JsonObject().put("error", error))),
                                    () -> {
                                        Product product = msgJson.mapTo(Product.class);
                                        LOGGER.info("getProductById returning results");
                                        routingContext.response().setStatusCode(200)
                                                .putHeader(HttpHeaders.CONTENT_TYPE, JSON_TYPE)
                                                .end(Json.encodePrettily(JsonObject.mapFrom(product)));
                                    });
                } else {
                    routingContext.response().setStatusCode(401)
                            .putHeader(HttpHeaders.CONTENT_TYPE, JSON_TYPE)
                            .end();
                }
            });
        } catch (Exception exc) {
            routingContext.response().setStatusCode(400)
                    .putHeader(HttpHeaders.CONTENT_TYPE, JSON_TYPE)
                    .end(Json.encodePrettily(new JsonObject().put("error", exc.getMessage())));
        }
    }

    private void postProduct(RoutingContext routingContext) {
        JsonObject value = routingContext.getBodyAsJson();
        JsonObject message = new JsonObject().put("cmd", CREATE_PRODUCT).put("value", value);
        vertx.eventBus().request(MONGO_SERVICE, message, reply -> {
            if (reply.succeeded()) {
                JsonObject productJson = new JsonObject(reply.result().body().toString());
                Product product = productJson.mapTo(Product.class);
                routingContext.response().setStatusCode(201)
                        .putHeader(HttpHeaders.CONTENT_TYPE, JSON_TYPE)
                        .end(Json.encodePrettily(JsonObject.mapFrom(product)));
            } else {
                routingContext.response().setStatusCode(500)
                        .putHeader(HttpHeaders.CONTENT_TYPE, JSON_TYPE)
                        .end(Json.encodePrettily(new JsonObject().put("error", reply.cause().getMessage())));
            }
        });
    }

    private void updateProductById(RoutingContext routingContext) {
        String id = routingContext.request().getParam("id");
        JsonObject value = routingContext.getBodyAsJson();
        JsonObject message = new JsonObject().put("cmd", UPDATE_PRODUCT).put("id", id).put("value", value);
        vertx.eventBus().request(MONGO_SERVICE, message, reply -> {
            if (reply.succeeded()) {
                JsonObject productJson = new JsonObject(reply.result().body().toString());
                Product product = productJson.mapTo(Product.class);
                routingContext.response().setStatusCode(200)
                        .putHeader(HttpHeaders.CONTENT_TYPE, JSON_TYPE)
                        .end(Json.encodePrettily(JsonObject.mapFrom(product)));
            } else {
                routingContext.response().setStatusCode(500)
                        .putHeader(HttpHeaders.CONTENT_TYPE, JSON_TYPE)
                        .end(Json.encodePrettily(new JsonObject().put("error", reply.cause().getMessage())));
            }
        });
    }

    private void deleteProductById(RoutingContext routingContext) {
        String id = routingContext.request().getParam("id");
        JsonObject message = new JsonObject().put("cmd", DELETE_PRODUCT).put("id", id);
        vertx.eventBus().request(MONGO_SERVICE, message, reply -> {
            if (reply.succeeded()) {
                JsonObject productJson = new JsonObject(reply.result().body().toString());
                routingContext.response().setStatusCode(200)
                        .putHeader(HttpHeaders.CONTENT_TYPE, JSON_TYPE)
                        .end(Json.encodePrettily(productJson));
            } else {
                routingContext.response().setStatusCode(500)
                        .putHeader(HttpHeaders.CONTENT_TYPE, JSON_TYPE)
                        .end(Json.encodePrettily(new JsonObject().put("error", reply.cause().getMessage())));
            }
        });
    }

}
