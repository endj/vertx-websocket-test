package se.edinjakupovic.commands;

sealed public interface Action permits Register, Message, ServerError, Unknown, HeartBeat {
}
