package com.ssarge.VertxClass.resources;

import com.ssarge.VertxClass.entity.Product;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.MongoClientDeleteResult;
import io.vertx.ext.mongo.MongoClientUpdateResult;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.ssarge.VertxClass.AppConstants.*;
import static com.ssarge.VertxClass.resources.MongoManager.*;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@RunWith(VertxUnitRunner.class)
public class MongoManagerTest {

    private static final String VERTX_ADDRESS = "com.ssarge.mongoservice";

    private MongoManager testClass;

    @Mock
    private MongoClient mongoClient;

    @Rule
    public RunTestOnContext rule = new RunTestOnContext();

    private Vertx vertx;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        vertx = rule.vertx();

        testClass = new MongoManager(mongoClient);
    }

    @Test
    public void registerConsumer_createProduct_success(TestContext context) {
        // GIVEN consumer is registered, and a CREATE_PRODUCT call is made
        // AND the data is good
        // WHEN the message comes down the pipe
        // THEN The mongo-client .save will be called
        // AND the message reply will have the new product in it;

        Async async = context.async();

        when(mongoClient.save(anyString(), any(JsonObject.class), any())).thenAnswer(invocationOnMock -> {
            // get the third value
            Handler<AsyncResult<String>> asyncResultHandler = invocationOnMock.getArgument(2);
            asyncResultHandler.handle(new AsyncResult<>() {
                @Override
                public String result() {
                    return "1";
                }

                @Override
                public Throwable cause() {
                    return null;
                }

                @Override
                public boolean succeeded() {
                    return true;
                }

                @Override
                public boolean failed() {
                    return false;
                }
            });
            return null;
        });

        Product expected = Product.builder()
                .number("123")
                .description("Some description")
                .id("1")
                .build();

        JsonObject value = new JsonObject().put("number", "123").put("description", "Some description");
        JsonObject message = new JsonObject().put("cmd", CREATE_PRODUCT).put("value", value);

        testClass.registerConsumer(vertx);

        vertx.eventBus().request(VERTX_ADDRESS, message, reply -> {
            context.assertTrue(reply.succeeded());
            Message<Object> msgResult = reply.result();
            JsonObject jsonResult = new JsonObject(msgResult.body().toString());
            Product resultProduct = jsonResult.mapTo(Product.class);
            try {
                verify(mongoClient, times(1)).save(anyString(), any(JsonObject.class), any());
                context.assertEquals(expected, resultProduct);
            } catch (Exception exc) {
                fail(exc.getMessage());
            } finally {
                async.complete();
            }
        });
    }

    @Test
    public void registerConsumer_createProduct_bad_data(TestContext context) {
        // GIVEN consumer is registered, and a CREATE_PRODUCT call is made
        // AND the data is malformed
        // WHEN the message comes down the pipe
        // THEN The mongo-client .save will not be called
        // AND the message reply will have an error in it

        Async async = context.async();

        JsonObject value = new JsonObject().put("number", "123").put("discriptin", "Some bad description");
        JsonObject message = new JsonObject().put("cmd", CREATE_PRODUCT).put("value", value);

        testClass.registerConsumer(vertx);

        vertx.eventBus().request(VERTX_ADDRESS, message, reply -> {
            context.assertTrue(reply.succeeded());
            Message<Object> msgResult = reply.result();
            JsonObject jsonResult = new JsonObject(msgResult.body().toString());
            try {
                verify(mongoClient, never()).save(anyString(), any(JsonObject.class), any());
                context.assertTrue(jsonResult.containsKey("error"));
            } catch (Exception exc) {
                fail(exc.getMessage());
            } finally {
                async.complete();
            }
        });
    }

    @Test
    public void registerConsumer_createProduct_database_error(TestContext context) {
        // GIVEN consumer is registered, and a CREATE_PRODUCT call is made
        // AND the data is good
        // AND the database call fails
        // WHEN the message comes down the pipe
        // THEN The mongo-client .save will be called
        // AND the message reply will have an error in it

        Async async = context.async();

        when(mongoClient.save(anyString(), any(JsonObject.class), any())).thenAnswer(invocationOnMock -> {
            // get the third value
            Handler<AsyncResult<String>> asyncResultHandler = invocationOnMock.getArgument(2);
            asyncResultHandler.handle(new AsyncResult<>() {
                @Override
                public String result() {
                    return null;
                }

                @Override
                public Throwable cause() {
                    return null;
                }

                @Override
                public boolean succeeded() {
                    return false;
                }

                @Override
                public boolean failed() {
                    return true;
                }
            });
            return null;
        });

        JsonObject value = new JsonObject().put("number", "123").put("description", "Some description");
        JsonObject message = new JsonObject().put("cmd", CREATE_PRODUCT).put("value", value);

        testClass.registerConsumer(vertx);

        vertx.eventBus().request(VERTX_ADDRESS, message, reply -> {
            context.assertTrue(reply.succeeded());
            Message<Object> msgResult = reply.result();
            JsonObject jsonResult = new JsonObject(msgResult.body().toString());
            try {
                verify(mongoClient, times(1)).save(anyString(), any(JsonObject.class), any());
                context.assertTrue(jsonResult.containsKey("error"));
            } catch (Exception exc) {
                fail(exc.getMessage());
            } finally {
                async.complete();
            }
        });

    }

    @Test
    public void registerConsumer_getALLProducts_success(TestContext context) {
        // GIVEN consumer is registered, and a GET_ALL_PRODUCTS call is made
        // WHEN the message comes down the pipe
        // THEN The mongo-client .findWithOptions will be called
        // AND the message reply will have a list of products in it
        Async async = context.async();

        List<Product> expected = Collections.singletonList(Product.builder()
                .id("1")
                .number("123")
                .description("Some id")
                .build());

        List<JsonObject> resultJson = Collections.singletonList(new JsonObject().put("_id", "1").put("number", "123").put("description", "Some id"));

        when(mongoClient.findWithOptions(anyString(), any(JsonObject.class), any(FindOptions.class), any())).thenAnswer(invocationOnMock -> {
            // get the third value
            Handler<AsyncResult<List<JsonObject>>> asyncResultHandler = invocationOnMock.getArgument(3);
            asyncResultHandler.handle(new AsyncResult<>() {
                @Override
                public List<JsonObject> result() {
                    return resultJson;
                }

                @Override
                public Throwable cause() {
                    return null;
                }

                @Override
                public boolean succeeded() {
                    return true;
                }

                @Override
                public boolean failed() {
                    return false;
                }
            });
            return null;
        });

        JsonObject message = new JsonObject().put("cmd", GET_ALL_PRODUCTS);

        testClass.registerConsumer(vertx);

        vertx.eventBus().request(VERTX_ADDRESS, message, reply -> {
            context.assertTrue(reply.succeeded());
            Message<Object> msgResult = reply.result();
            JsonObject jsonResult = new JsonObject(msgResult.body().toString());
            JsonArray jsonArray = jsonResult.getJsonArray("products");
            List<Product> resultProduct = jsonArray.stream()
                    .map(obj -> new JsonObject(obj.toString()))
                    .map(json -> json.mapTo(Product.class))
                    .collect(Collectors.toList());

            try {
                verify(mongoClient, times(1))
                        .findWithOptions(anyString(), any(JsonObject.class), any(FindOptions.class), any());
                context.assertEquals(expected, resultProduct);
            } catch (Exception exc) {
                fail(exc.getMessage());
            } finally {
                async.complete();
            }
        });
    }

    @Test
    public void registerConsumer_getALLProducts_failed_no_products(TestContext context) {
        // GIVEN consumer is registered, and a GET_ALL_PRODUCTS call is made
        // WHEN the message comes down the pipe
        // THEN The mongo-client .findWithOptions will be called
        // AND the message reply will a list of no products in it
        // AND the message reply will have an error message

        Async async = context.async();

        when(mongoClient.findWithOptions(anyString(), any(JsonObject.class), any(FindOptions.class), any())).thenAnswer(invocationOnMock -> {
            // get the third value
            Handler<AsyncResult<List<JsonObject>>> asyncResultHandler = invocationOnMock.getArgument(3);
            asyncResultHandler.handle(new AsyncResult<>() {
                @Override
                public List<JsonObject> result() {
                    return null;
                }

                @Override
                public Throwable cause() {
                    return null;
                }

                @Override
                public boolean succeeded() {
                    return false;
                }

                @Override
                public boolean failed() {
                    return true;
                }
            });
            return null;
        });

        JsonObject message = new JsonObject().put("cmd", GET_ALL_PRODUCTS);

        testClass.registerConsumer(vertx);

        vertx.eventBus().request(VERTX_ADDRESS, message, reply -> {
            context.assertTrue(reply.succeeded());
            Message<Object> msgResult = reply.result();
            JsonObject jsonResult = new JsonObject(msgResult.body().toString());
            try {
                verify(mongoClient, times(1))
                        .findWithOptions(anyString(), any(JsonObject.class), any(FindOptions.class), any());
                context.assertTrue(jsonResult.containsKey("error"));
            } catch (Exception exc) {
                fail(exc.getMessage());
            } finally {
                async.complete();
            }
        });
    }

    @Test
    public void registerConsumer_getALLProducts_failed_database_error(TestContext context) {
        // GIVEN consumer is registered, and a GET_ALL_PRODUCTS call is made
        // WHEN the message comes down the pipe
        // THEN The mongo-client .findWithOptions will be called
        // AND the mongo-client will throw an error
        // AND the message reply will have an error message

        Async async = context.async();

        when(mongoClient.findWithOptions(anyString(), any(JsonObject.class), any(FindOptions.class), any()))
                .thenThrow(new RuntimeException("Bad doggy!"));

        JsonObject message = new JsonObject().put("cmd", GET_ALL_PRODUCTS);

        testClass.registerConsumer(vertx);

        vertx.eventBus().request(VERTX_ADDRESS, message, reply -> {
            context.assertTrue(reply.succeeded());
            Message<Object> msgResult = reply.result();
            JsonObject jsonResult = new JsonObject(msgResult.body().toString());
            try {
                verify(mongoClient, times(1))
                        .findWithOptions(anyString(), any(JsonObject.class), any(FindOptions.class), any());
                context.assertTrue(jsonResult.containsKey("error"));
            } catch (Exception exc) {
                fail(exc.getMessage());
            } finally {
                async.complete();
            }
        });
    }

    @Test
    public void registerConsumer_getProduct_success(TestContext context) {
        // GIVEN consumer is registered, and a GET_PRODUCT call is made
        // WHEN the message comes down the pipe
        // THEN The mongo-client .findOne will be called
        // AND the message reply will have a product in it

        Async async = context.async();

        Product expected = Product.builder()
                .id("1")
                .number("123")
                .description("Some id")
                .build();

        JsonObject resultJson = JsonObject.mapFrom(expected);

        when(mongoClient.findOne(anyString(), any(JsonObject.class), eq(null), any())).thenAnswer(invocationOnMock -> {
            // get the third value
            Handler<AsyncResult<JsonObject>> asyncResultHandler = invocationOnMock.getArgument(3);
            asyncResultHandler.handle(new AsyncResult<>() {
                @Override
                public JsonObject result() {
                    return resultJson;
                }

                @Override
                public Throwable cause() {
                    return null;
                }

                @Override
                public boolean succeeded() {
                    return true;
                }

                @Override
                public boolean failed() {
                    return false;
                }
            });
            return null;
        });

        JsonObject message = new JsonObject().put("cmd", GET_PRODUCT).put("id", "1");

        testClass.registerConsumer(vertx);

        vertx.eventBus().request(VERTX_ADDRESS, message, reply -> {
            context.assertTrue(reply.succeeded());
            Message<Object> msgResult = reply.result();
            JsonObject jsonResult = new JsonObject(msgResult.body().toString());
            Product resultProduct = jsonResult.mapTo(Product.class);
            try {
                verify(mongoClient, times(1))
                        .findOne(anyString(), any(JsonObject.class), any(JsonObject.class), any());
                context.assertEquals(expected, resultProduct);
            } catch (Exception exc) {
                fail(exc.getMessage());
            } finally {
                async.complete();
            }
        });
    }

    @Test
    public void registerConsumer_getProduct_failed_no_product(TestContext context) {
        // GIVEN consumer is registered, and a GET_PRODUCT call is made
        // WHEN the message comes down the pipe
        // THEN The mongo-client .findOne will be called
        // AND the message reply will have no product in it
        // AND the message reply will have an error message
        Async async = context.async();

        when(mongoClient.findOne(anyString(), any(JsonObject.class), eq(null), any())).thenAnswer(invocationOnMock -> {
            // get the third value
            Handler<AsyncResult<JsonObject>> asyncResultHandler = invocationOnMock.getArgument(3);
            asyncResultHandler.handle(new AsyncResult<>() {
                @Override
                public JsonObject result() {
                    return null;
                }

                @Override
                public Throwable cause() {
                    return null;
                }

                @Override
                public boolean succeeded() {
                    return false;
                }

                @Override
                public boolean failed() {
                    return true;
                }
            });
            return null;
        });

        JsonObject message = new JsonObject().put("cmd", GET_PRODUCT).put("id", "1");

        testClass.registerConsumer(vertx);

        vertx.eventBus().request(VERTX_ADDRESS, message, reply -> {
            context.assertTrue(reply.succeeded());
            Message<Object> msgResult = reply.result();
            JsonObject jsonResult = new JsonObject(msgResult.body().toString());
            try {
                verify(mongoClient, times(1))
                        .findOne(anyString(), any(JsonObject.class), any(JsonObject.class), any());
                context.assertTrue(jsonResult.containsKey("error"));
            } catch (Exception exc) {
                fail(exc.getMessage());
            } finally {
                async.complete();
            }
        });
    }

    @Test
    public void registerConsumer_getProduct_failed_failed_database_error(TestContext context) {
        // GIVEN consumer is registered, and a GET_ALL_PRODUCTS call is made
        // WHEN the message comes down the pipe
        // THEN The mongo-client .findOne will be called
        // AND the mongo-client will throw an error
        // AND the message reply will have an error message

        Async async = context.async();

        when(mongoClient.findOne(anyString(), any(JsonObject.class), eq(null), any())).thenThrow(new RuntimeException("Nope!"));

        JsonObject message = new JsonObject().put("cmd", GET_PRODUCT).put("id", "1");

        testClass.registerConsumer(vertx);

        vertx.eventBus().request(VERTX_ADDRESS, message, reply -> {
            context.assertTrue(reply.succeeded());
            Message<Object> msgResult = reply.result();
            JsonObject jsonResult = new JsonObject(msgResult.body().toString());
            try {
                verify(mongoClient, times(1))
                        .findOne(anyString(), any(JsonObject.class), any(JsonObject.class), any());
                context.assertTrue(jsonResult.containsKey("error"));
            } catch (Exception exc) {
                fail(exc.getMessage());
            } finally {
                async.complete();
            }
        });
    }

    @Test
    public void registerConsumer_updateProduct_success(TestContext context) {
        // GIVEN consumer is registered, and a UPDATE_PRODUCT call is made
        // AND the data is good
        // AND the update id exists
        // WHEN the message comes down the pipe
        // THEN The mongo-client .replaceDocuments will be called
        // AND the message reply will have the updated product in it;

        Async async = context.async();

        Product expected = Product.builder()
                .number("1234")
                .description("Some altered description")
                .id("1")
                .build();

        when(mongoClient.replaceDocuments(anyString(), any(JsonObject.class), any(JsonObject.class), any())).thenAnswer(invocationOnMock -> {
            // get the third value
            Handler<AsyncResult<MongoClientUpdateResult>> asyncResultHandler = invocationOnMock.getArgument(3);
            asyncResultHandler.handle(new AsyncResult<>() {
                @Override
                public MongoClientUpdateResult result() {
                    return null;
                }

                @Override
                public Throwable cause() {
                    return null;
                }

                @Override
                public boolean succeeded() {
                    return true;
                }

                @Override
                public boolean failed() {
                    return false;
                }
            });
            return null;
        });

        JsonObject value = new JsonObject().put("number", "1234").put("description", "Some altered description");
        JsonObject message = new JsonObject().put("cmd", UPDATE_PRODUCT).put("value", value).put("id", "1");

        testClass.registerConsumer(vertx);

        vertx.eventBus().request(VERTX_ADDRESS, message, reply -> {
            context.assertTrue(reply.succeeded());
            Message<Object> msgResult = reply.result();
            JsonObject jsonResult = new JsonObject(msgResult.body().toString());
            Product resultProduct = jsonResult.mapTo(Product.class);
            try {
                verify(mongoClient, times(1)).replaceDocuments(anyString(), any(JsonObject.class), any(JsonObject.class), any());
                context.assertEquals(expected, resultProduct);
            } catch (Exception exc) {
                fail(exc.getMessage());
            } finally {
                async.complete();
            }
        });

    }

    @Test
    public void registerConsumer_updateProduct_failed_no_product_to_update(TestContext context) {
        // GIVEN consumer is registered, and a UPDATE_PRODUCT call is made
        // AND the data is good
        // AND the update id does not exist
        // WHEN the message comes down the pipe
        // THEN The mongo-client .replaceDocuments will be called
        // AND the message reply will have no product in it
        // AND the message reply will have an error message

        Async async = context.async();

        when(mongoClient.replaceDocuments(anyString(), any(JsonObject.class), any(JsonObject.class), any())).thenAnswer(invocationOnMock -> {
            // get the third value
            Handler<AsyncResult<MongoClientUpdateResult>> asyncResultHandler = invocationOnMock.getArgument(3);
            asyncResultHandler.handle(new AsyncResult<>() {
                @Override
                public MongoClientUpdateResult result() {
                    return null;
                }

                @Override
                public Throwable cause() {
                    return null;
                }

                @Override
                public boolean succeeded() {
                    return false;
                }

                @Override
                public boolean failed() {
                    return true;
                }
            });
            return null;
        });

        JsonObject value = new JsonObject().put("number", "1234").put("description", "Some altered description");
        JsonObject message = new JsonObject().put("cmd", UPDATE_PRODUCT).put("value", value).put("id", "1");

        testClass.registerConsumer(vertx);

        vertx.eventBus().request(VERTX_ADDRESS, message, reply -> {
            context.assertTrue(reply.succeeded());
            Message<Object> msgResult = reply.result();
            JsonObject jsonResult = new JsonObject(msgResult.body().toString());
            try {
                verify(mongoClient, times(1)).replaceDocuments(anyString(), any(JsonObject.class), any(JsonObject.class), any());
                context.assertTrue(jsonResult.containsKey("error"));
            } catch (Exception exc) {
                fail(exc.getMessage());
            } finally {
                async.complete();
            }
        });
    }

    @Test
    public void registerConsumer_updateProduct_failed_database_error(TestContext context) {
        // GIVEN consumer is registered, and a UPDATE_PRODUCT call is made
        // AND the data is good
        // AND the update id exists
        // WHEN the message comes down the pipe
        // THEN The mongo-client .replaceDocuments will be called
        // AND the mongo-client will throw an error
        // AND the message reply will have an error message

        Async async = context.async();

        when(mongoClient.replaceDocuments(anyString(), any(JsonObject.class), any(JsonObject.class), any()))
                .thenThrow(new RuntimeException("oops!"));

        JsonObject value = new JsonObject().put("number", "1234").put("description", "Some altered description");
        JsonObject message = new JsonObject().put("cmd", UPDATE_PRODUCT).put("value", value).put("id", "1");

        testClass.registerConsumer(vertx);

        vertx.eventBus().request(VERTX_ADDRESS, message, reply -> {
            context.assertTrue(reply.succeeded());
            Message<Object> msgResult = reply.result();
            JsonObject jsonResult = new JsonObject(msgResult.body().toString());
            try {
                verify(mongoClient, times(1)).replaceDocuments(anyString(), any(JsonObject.class), any(JsonObject.class), any());
                context.assertTrue(jsonResult.containsKey("error"));
            } catch (Exception exc) {
                fail(exc.getMessage());
            } finally {
                async.complete();
            }
        });
    }

    @Test
    public void registerConsumer_deleteProduct_success(TestContext context) {
        // GIVEN consumer is registered, and a DELETE_PRODUCT call is made
        // AND the product id exists
        // WHEN the message comes down the pipe
        // THEN The mongo-client .removeDocument will be called
        // AND the message reply will have an {status: 'removed', id: 'id'} body;

        Async async = context.async();

        JsonObject expectedResult = new JsonObject().put("status", "removed").put("id", "1");

        when(mongoClient.removeDocument(anyString(), any(JsonObject.class), any())).thenAnswer(invocationOnMock -> {
            // get the third value
            Handler<AsyncResult<MongoClientDeleteResult>> asyncResultHandler = invocationOnMock.getArgument(2);
            asyncResultHandler.handle(new AsyncResult<>() {
                @Override
                public MongoClientDeleteResult result() {
                    return null;
                }

                @Override
                public Throwable cause() {
                    return null;
                }

                @Override
                public boolean succeeded() {
                    return true;
                }

                @Override
                public boolean failed() {
                    return false;
                }
            });
            return null;
        });

        JsonObject message = new JsonObject().put("cmd", DELETE_PRODUCT).put("id", "1");

        testClass.registerConsumer(vertx);

        vertx.eventBus().request(VERTX_ADDRESS, message, reply -> {
            context.assertTrue(reply.succeeded());
            Message<Object> msgResult = reply.result();
            JsonObject jsonResult = new JsonObject(msgResult.body().toString());
            try {
                verify(mongoClient, times(1))
                        .removeDocument(anyString(), any(JsonObject.class), any());
                context.assertEquals(expectedResult, jsonResult);
            } catch (Exception exc) {
                fail(exc.getMessage());
            } finally {
                async.complete();
            }
        });
    }

    @Test
    public void registerConsumer_deleteProduct_failed_no_product_to_delete(TestContext context) {
        // GIVEN consumer is registered, and a DELETE_PRODUCT call is made
        // AND the product id does not exist
        // WHEN the message comes down the pipe
        // THEN The mongo-client .removeDocument will be called
        // AND the message reply will have an error message
        Async async = context.async();

        when(mongoClient.removeDocument(anyString(), any(JsonObject.class), any())).thenAnswer(invocationOnMock -> {
            // get the third value
            Handler<AsyncResult<MongoClientDeleteResult>> asyncResultHandler = invocationOnMock.getArgument(2);
            asyncResultHandler.handle(new AsyncResult<>() {
                @Override
                public MongoClientDeleteResult result() {
                    return null;
                }

                @Override
                public Throwable cause() {
                    return null;
                }

                @Override
                public boolean succeeded() {
                    return false;
                }

                @Override
                public boolean failed() {
                    return true;
                }
            });
            return null;
        });

        JsonObject message = new JsonObject().put("cmd", DELETE_PRODUCT).put("id", "1");

        testClass.registerConsumer(vertx);

        vertx.eventBus().request(VERTX_ADDRESS, message, reply -> {
            context.assertTrue(reply.succeeded());
            Message<Object> msgResult = reply.result();
            JsonObject jsonResult = new JsonObject(msgResult.body().toString());
            try {
                verify(mongoClient, times(1))
                        .removeDocument(anyString(), any(JsonObject.class), any());
                context.assertTrue(jsonResult.containsKey("error"));
            } catch (Exception exc) {
                fail(exc.getMessage());
            } finally {
                async.complete();
            }
        });
    }

    @Test
    public void registerConsumer_deleteProduct_failed_database_error(TestContext context) {
        // GIVEN consumer is registered, and a DELETE_PRODUCT call is made
        // AND the product id exists
        // WHEN the message comes down the pipe
        // THEN The mongo-client .removeDocument will be called
        // AND the mongo-client will throw an error
        // AND the message reply will have an error message

        Async async = context.async();

        when(mongoClient.removeDocument(anyString(), any(JsonObject.class), any())).thenThrow(new RuntimeException("not happenin!"));

        JsonObject message = new JsonObject().put("cmd", DELETE_PRODUCT).put("id", "1");

        testClass.registerConsumer(vertx);

        vertx.eventBus().request(VERTX_ADDRESS, message, reply -> {
            context.assertTrue(reply.succeeded());
            Message<Object> msgResult = reply.result();
            JsonObject jsonResult = new JsonObject(msgResult.body().toString());
            try {
                verify(mongoClient, times(1))
                        .removeDocument(anyString(), any(JsonObject.class), any());
                context.assertTrue(jsonResult.containsKey("error"));
            } catch (Exception exc) {
                fail(exc.getMessage());
            } finally {
                async.complete();
            }
        });
    }
}