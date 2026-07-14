package hackerrouter.perimeterdigger.client.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Collection;
import java.util.List;

public final class ItemIdentifierArgument implements ArgumentType<Identifier> {
	private static final DynamicCommandExceptionType INVALID_IDENTIFIER = new DynamicCommandExceptionType(
			value -> Component.literal("Invalid item identifier: " + value)
	);
	private static final Collection<String> EXAMPLES = List.of("enchanted_golden_apple", "minecraft:enchanted_golden_apple");

	private ItemIdentifierArgument() {
	}

	public static ItemIdentifierArgument itemIdentifier() {
		return new ItemIdentifierArgument();
	}

	public static Identifier getIdentifier(CommandContext<?> context, String name) {
		return context.getArgument(name, Identifier.class);
	}

	@Override
	public Identifier parse(StringReader reader) throws CommandSyntaxException {
		int start = reader.getCursor();
		while (reader.canRead() && !Character.isWhitespace(reader.peek())) reader.skip();
		String value = reader.getString().substring(start, reader.getCursor());
		Identifier identifier = Identifier.tryParse(value.contains(":") ? value : "minecraft:" + value);
		if (identifier == null) {
			reader.setCursor(start);
			throw INVALID_IDENTIFIER.createWithContext(reader, value);
		}
		return identifier;
	}

	@Override
	public Collection<String> getExamples() {
		return EXAMPLES;
	}
}
