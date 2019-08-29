package com.ssarge.VertxClass.routes;

import io.vertx.ext.web.Router;

public interface RouteHandler {
    Router initializeRouter();
}
