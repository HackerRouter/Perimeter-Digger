/*
 * This file is part of the Perimeter Digger project, licensed under the
 * GNU General Public License v3.0 or later.
 *
 * Copyright (C) 2026  HackerRouter
 *
 * Perimeter Digger is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Perimeter Digger is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Perimeter Digger. If not, see <https://www.gnu.org/licenses/>.
 */

package hackerrouter.perimeterdigger.client.translation;

import hackerrouter.perimeterdigger.client.state.AutomationState;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

public final class Translations {
	public static final Translator ROOT = new Translator("perimeterdigger");
	public static final Translator COMMAND = ROOT.derive("command");
	public static final Translator STATE = ROOT.derive("state");
	public static final Translator DETAIL = ROOT.derive("detail");
	public static final Translator VALUE = ROOT.derive("value");

	private Translations() {
	}

	public static MutableComponent state(AutomationState state) {
		return STATE.tr(state.name().toLowerCase(Locale.ROOT));
	}
}
