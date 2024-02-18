package se.edinjakupovic.parsing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import se.edinjakupovic.commands.Action;
import se.edinjakupovic.commands.HeartBeat;
import se.edinjakupovic.commands.Message;
import se.edinjakupovic.commands.Register;
import se.edinjakupovic.commands.ServerError;
import se.edinjakupovic.commands.Unknown;

public record RequestParser() {
  private static final Logger log = LoggerFactory.getLogger(RequestParser.class);

  private static final ObjectMapper MAPPER = new ObjectMapper();

  public static Action parseMessage(Buffer buffer) {
    String type = null;
    try {
      JsonNode jsonNode = MAPPER.readTree(buffer.getBytes());
      type = jsonNode.get("type").asText();
      JsonNode data = jsonNode.get("data");
      return switch (type) {
        case "register" -> MAPPER.treeToValue(data, Register.class);
        case "message" -> MAPPER.treeToValue(data, Message.class);
        case "heartBeat" -> MAPPER.treeToValue(data, HeartBeat.class);
        default -> new Unknown();
      };
    } catch (Exception e) {
      log.info("Failed to parse message " + buffer.toString());
      log.info("Reason: " + e.getMessage());
      return new ServerError("Unable to parse message with type: " + type);
    }
  }
  public static <T> String toJSON(T t) {
    try {
      return MAPPER.writeValueAsString(t);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }

}
