package se.edinjakupovic.commands;

import se.edinjakupovic.commands.Action;

public record Message(String sender, String text) implements Action {
}
