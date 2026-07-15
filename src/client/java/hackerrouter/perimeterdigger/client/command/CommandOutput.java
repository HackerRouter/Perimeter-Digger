package hackerrouter.perimeterdigger.client.command;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

final class CommandOutput {
	private CommandOutput() {
	}

	static MutableComponent category(String value) {
		return Component.literal(value).withStyle(ChatFormatting.AQUA);
	}

	static MutableComponent field(String name, Object value) {
		return field(name, valueComponent(value));
	}

	static MutableComponent field(String name, MutableComponent value) {
		return Component.literal(name).withStyle(ChatFormatting.YELLOW)
				.append(Component.literal("=").withStyle(ChatFormatting.GRAY))
				.append(value);
	}

	static MutableComponent valueComponent(Object value) {
		if (value == null) return clickableValue("unset", ChatFormatting.RED);
		if (value instanceof Boolean flag) {
			return clickableValue(Boolean.toString(flag), flag ? ChatFormatting.GREEN : ChatFormatting.RED);
		}
		String text = value.toString();
		if (text.equalsIgnoreCase("unset") || text.equalsIgnoreCase("none") || text.equalsIgnoreCase("false")) {
			return clickableValue(text, ChatFormatting.RED);
		}
		return clickableValue(text, ChatFormatting.GREEN);
	}

	static MutableComponent separator() {
		return Component.literal(", ").withStyle(ChatFormatting.GRAY);
	}

	static void feedback(CommandContext<FabricClientCommandSource> context, Component message) {
		context.getSource().sendFeedback(message);
	}

	static int error(CommandContext<FabricClientCommandSource> context, String message) {
		context.getSource().sendFeedback(Component.literal("Error: " + (message == null ? "Unknown error." : message)));
		return 0;
	}

	private static MutableComponent clickableValue(String value, ChatFormatting color) {
		return Component.literal(value).withStyle(color)
				.withStyle(style -> style.withClickEvent(new ClickEvent.CopyToClipboard(value)));
	}
}
