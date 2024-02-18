package se.edinjakupovic.model;

import io.vertx.core.http.ServerWebSocket;

public record User(String id, ServerWebSocket socket, long lastHeartBeat) {
}
