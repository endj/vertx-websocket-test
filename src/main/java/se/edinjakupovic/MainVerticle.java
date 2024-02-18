package se.edinjakupovic;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import se.edinjakupovic.commands.Action;
import se.edinjakupovic.commands.HeartBeat;
import se.edinjakupovic.commands.Message;
import se.edinjakupovic.commands.Register;
import se.edinjakupovic.commands.ServerError;
import se.edinjakupovic.commands.Unknown;
import se.edinjakupovic.model.User;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static se.edinjakupovic.parsing.RequestParser.parseMessage;
import static se.edinjakupovic.parsing.RequestParser.toJSON;


public class MainVerticle extends AbstractVerticle {

  private static final int EVICTING_PERIOD_SECONDS = intConfig("EVICTING_PERIOD_SECONDS", "10");
  private static final int SOCKET_TTL_MS = intConfig("SOCKET_TTL", "120000");
  private static final Logger log = LoggerFactory.getLogger(MainVerticle.class);

  private static final ConcurrentMap<String, User> SOCKET_MAP = new ConcurrentHashMap<>();


  @Override
  public void start(Promise<Void> startPromise) {
    Router router = Router.router(vertx);
    vertx.createHttpServer()
      .webSocketHandler(socket -> {
        socket.handler(buffer -> {
          Action action = parseMessage(buffer);
          switch (action) {
            case Register register -> onRegister(socket, register);
            case Message message -> onMessage(message);
            case Unknown unknown -> onUnknown(unknown);
            case ServerError error -> onError(socket, error);
            case HeartBeat heartBeat -> onHeartBeat(heartBeat);
          }
        });
        socket.exceptionHandler(Throwable::printStackTrace);
        socket.closeHandler(unused -> log.info("Socket closed " + socket.textHandlerID()));
      })
      .requestHandler(router)
      .listen(8080);
  }

  private static void onRegister(ServerWebSocket socket, Register register) {
    SOCKET_MAP.put(register.id(), new User(register.id(), socket, System.currentTimeMillis()));
    log.info("Registered user: " + register);
  }

  private static void onHeartBeat(HeartBeat heartBeat) {
    String id = heartBeat.id();
    User user = SOCKET_MAP.get(id);
    if (user == null) return;
    User newUser = new User(user.id(), user.socket(), System.currentTimeMillis());
    SOCKET_MAP.put(id, newUser);
  }

  private static void onError(ServerWebSocket socket, ServerError error) {
    log.error("Got error: " + error);
    socket.writeTextMessage(toJSON(error));
  }

  private static void onUnknown(Unknown unknown) {
    log.info("Unknown command: " + unknown);
  }

  private static void onMessage(Message message) {
    log.info("Message " + message);
    log.info("Sending message to users " + SOCKET_MAP.keySet());
    SOCKET_MAP.values().forEach(user -> user.socket().writeTextMessage(toJSON(message))
      .onFailure(failure -> {
        if (user.socket().isClosed()) {
          SOCKET_MAP.remove(user.id());
        }
      }));
  }


  public static void main(String[] args) {
    Vertx.vertx().deployVerticle(new MainVerticle()).onSuccess(success -> {
      @SuppressWarnings("resource")
      var executor = Executors.newSingleThreadScheduledExecutor();
      executor.scheduleWithFixedDelay(() -> {
        var deadLine = System.currentTimeMillis() - SOCKET_TTL_MS;
        SOCKET_MAP.values().removeIf(user -> {
          var remove = user.lastHeartBeat() < deadLine;
          if (remove) {
            log.info("Removed user " + user.id());
            user.socket().close();
          }
          return remove;
        });
      }, 0, EVICTING_PERIOD_SECONDS, TimeUnit.SECONDS);
    });
  }

  static int intConfig(String key, String defaultVal) {
    return Integer.parseInt(Objects.requireNonNullElse(System.getenv(key), defaultVal));
  }
}
