package com.extremeprogression.client.screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import com.extremeprogression.client.state.ClientAdmin;
import com.extremeprogression.client.ui.Ui;
import com.extremeprogression.config.Preset;
import com.extremeprogression.network.SettingsPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The settings screen for admins (design §9, screen 7, ✅ round 25, like Extreme Dimensions' settings screen): every
 * option of the config, skills.json and synergies.json, searchable, with the presets; Save sends them to the server,
 * which checks the player may, writes the files and tells every client. The XP tables stay in their files
 * ({@code /skills reload}).
 */
public class SettingsScreen extends Screen {
	static final int W = 404, H = 222;
	private static final int ROW = 16;

	private static int opened = 0;

	private final Screen parent;
	private int x0, y0;
	/** The settings as the server sent them, and every leaf value by its path ("config.death_base"). */
	private JsonObject original = new JsonObject();
	private final Map<String, JsonPrimitive> values = new LinkedHashMap<>();
	private final Map<String, String> edits = new LinkedHashMap<>();
	private String filter = "";
	private int page = 0;
	private final List<AbstractWidget> rowWidgets = new ArrayList<>();
	private EditBox search;
	private Button save;
	private String message = "";

	public SettingsScreen(Screen parent) {
		super(Component.translatable("gui.extremeprogression.settings.title"));
		this.parent = parent;
		opened++;
		load();
	}

	public static int opened() {
		return opened;
	}

	/** The settings from the server, every edit undone. */
	private void load() {
		values.clear();
		edits.clear();
		String json = ClientAdmin.settings();
		if (json == null) {
			return;
		}
		try {
			original = JsonParser.parseString(json).getAsJsonObject();
		} catch (RuntimeException e) {
			return;
		}
		flatten("", original);
	}

	private void flatten(String prefix, JsonObject o) {
		for (Map.Entry<String, JsonElement> e : o.entrySet()) {
			String path = prefix.isEmpty() ? e.getKey() : prefix + "." + e.getKey();
			if (e.getValue().isJsonObject()) {
				flatten(path, e.getValue().getAsJsonObject());
			} else if (e.getValue().isJsonPrimitive() && !path.endsWith(".strength_is")) {
				values.put(path, e.getValue().getAsJsonPrimitive());
			}
		}
	}

	private String value(String path) {
		String edited = edits.get(path);
		return edited != null ? edited : values.get(path).getAsString();
	}

	/** Whether a text is a valid value for this option. */
	private boolean valid(String path, String text) {
		JsonPrimitive was = values.get(path);
		if (was.isBoolean()) {
			return text.equals("true") || text.equals("false");
		}
		if (was.isNumber()) {
			try {
				double d = Double.parseDouble(text.trim());
				return Double.isFinite(d) && d >= 0;
			} catch (NumberFormatException e) {
				return false;
			}
		}
		return true;
	}

	private List<String> shown() {
		List<String> out = new ArrayList<>();
		String f = filter.toLowerCase(Locale.ROOT).trim();
		for (String path : values.keySet()) {
			if (!path.equals("config.preset") && (f.isEmpty() || path.toLowerCase(Locale.ROOT).contains(f))) {
				out.add(path);
			}
		}
		return out;
	}

	private int perPage() {
		return (H - 84) / ROW;
	}

	@Override
	protected void init() {
		x0 = (this.width - W) / 2;
		y0 = Math.max(2, (this.height - H) / 2);
		boolean editor = ClientAdmin.editor();
		int bx = x0 + 6;
		for (Preset p : Preset.values()) {
			this.addRenderableWidget(Button.builder(Component.translatable("gui.extremeprogression.settings.preset." + p.id), b -> preset(p))
					.pos(bx, y0 + 14).size(72, 14).build()).active = editor;
			bx += 74;
		}
		search = this.addRenderableWidget(new EditBox(this.font, x0 + W - 6 - 150, y0 + 14, 150, 14, Component.translatable("gui.extremeprogression.settings.search")));
		search.setValue(filter);
		search.setResponder(text -> {
			filter = text;
			page = 0;
			rows();
		});
		this.addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1)).pos(x0 + 6, y0 + H - 46).size(20, 14).build());
		this.addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1)).pos(x0 + W - 26, y0 + H - 46).size(20, 14).build());
		save = this.addRenderableWidget(Button.builder(Component.translatable("gui.extremeprogression.settings.save"), b -> save())
				.pos(this.width / 2 - 154, y0 + H - 20).size(100, 20).build());
		save.active = editor;
		this.addRenderableWidget(Button.builder(Component.translatable("gui.extremeprogression.settings.revert"), b -> {
			load();
			message = "";
			rows();
		}).pos(this.width / 2 - 50, y0 + H - 20).size(100, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable(parent != null ? "gui.back" : "gui.done"), b -> this.onClose())
				.pos(this.width / 2 + 54, y0 + H - 20).size(100, 20).build());
		rows();
	}

	/** The rows of the page shown: a box for a number or text, a button for a yes or no. */
	private void rows() {
		for (AbstractWidget w : rowWidgets) {
			this.removeWidget(w);
		}
		rowWidgets.clear();
		List<String> shown = shown();
		int pages = Math.max(1, (shown.size() + perPage() - 1) / perPage());
		page = Math.max(0, Math.min(page, pages - 1));
		boolean editor = ClientAdmin.editor();
		int y = y0 + 36;
		for (int i = page * perPage(); i < shown.size() && i < (page + 1) * perPage(); i++) {
			String path = shown.get(i);
			int vx = x0 + W - 6 - 90;
			if (values.get(path).isBoolean()) {
				Button b = Button.builder(Component.literal(value(path)), btn -> {
					edit(path, value(path).equals("true") ? "false" : "true");
					btn.setMessage(Component.literal(value(path)));
				}).pos(vx, y).size(90, 14).build();
				b.active = editor;
				rowWidgets.add(this.addRenderableWidget(b));
			} else {
				EditBox box = new EditBox(this.font, vx, y, 90, 14, Component.literal(path));
				box.setMaxLength(64);
				box.setValue(value(path));
				box.setEditable(editor);
				box.setResponder(text -> edit(path, text));
				rowWidgets.add(this.addRenderableWidget(box));
			}
			y += ROW;
		}
	}

	private void edit(String path, String text) {
		if (text.equals(values.get(path).getAsString())) {
			edits.remove(path);
		} else {
			edits.put(path, text);
		}
		// A value a preset sets, changed by hand: the preset is custom from now on
		String key = path.startsWith("config.") ? path.substring(7) : "";
		if (Preset.governed().contains(key) && !value("config.preset").equals("custom")) {
			Preset p = Preset.of(value("config.preset"));
			Object want = p == null ? null : p.value(key.toUpperCase(Locale.ROOT));
			if (want != null && !String.valueOf(want).equals(text)) {
				edits.put("config.preset", "custom");
			}
		}
	}

	/** A preset button: Hardcore and Normal set the options they govern; Custom keeps every value as it is. */
	private void preset(Preset p) {
		edit("config.preset", p.id);
		if (p != Preset.CUSTOM) {
			for (String key : Preset.governed()) {
				Object want = p.value(key.toUpperCase(Locale.ROOT));
				String path = "config." + key;
				if (want != null && values.containsKey(path)) {
					String text = String.valueOf(want);
					if (text.equals(values.get(path).getAsString())) {
						edits.remove(path);
					} else {
						edits.put(path, text);
					}
				}
			}
		}
		rows();
	}

	private void turn(int by) {
		page += by;
		rows();
	}

	/** Every edit valid: the settings go to the server, which answers with what it took. */
	private void save() {
		List<String> wrong = new ArrayList<>();
		for (Map.Entry<String, String> e : edits.entrySet()) {
			if (!valid(e.getKey(), e.getValue())) {
				wrong.add(e.getKey());
			}
		}
		if (!wrong.isEmpty()) {
			message = Ui.tr("gui.extremeprogression.settings.invalid", String.join(", ", wrong));
			return;
		}
		JsonObject out = original.deepCopy();
		for (Map.Entry<String, String> e : edits.entrySet()) {
			String[] parts = e.getKey().split("\\.");
			JsonObject o = out;
			for (int i = 0; i < parts.length - 1; i++) {
				o = o.getAsJsonObject(parts[i]);
			}
			JsonPrimitive was = values.get(e.getKey());
			String text = e.getValue().trim();
			o.add(parts[parts.length - 1], was.isBoolean() ? new JsonPrimitive(Boolean.parseBoolean(text))
					: was.isNumber() ? new JsonPrimitive(Double.parseDouble(text)) : new JsonPrimitive(text));
		}
		if (ClientPlayNetworking.canSend(SettingsPayload.TYPE)) {
			ClientPlayNetworking.send(new SettingsPayload(out.toString(), false, false));
			message = Ui.tr("gui.extremeprogression.settings.sent", edits.size());
		}
	}

	/** Changes an option as if typed (for tests). */
	public void set(String path, String text) {
		edit(path, text);
		rows();
	}

	public void saveForTests() {
		save();
	}

	@Override
	public void tick() {
		super.tick();
		// (new settings from the server, after a save: shown, with nothing left to save)
		String now = ClientAdmin.settings();
		if (now != null && !now.equals(original.toString()) && edits.isEmpty()) {
			load();
			rows();
		}
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractBackground(g, mouseX, mouseY, a);
		Ui.centered(g, this.font, this.title.getString(), this.width / 2, y0 + 2, Ui.GOLD);
		Ui.panel(g, x0, y0 + 32, W, H - 58);
		if (values.isEmpty()) {
			Ui.text(g, this.font, Ui.tr("gui.extremeprogression.settings.waiting"), x0 + 6, y0 + 38, Ui.MUTED);
			return;
		}
		String preset = value("config.preset");
		Ui.text(g, this.font, Ui.tr("gui.extremeprogression.settings.preset", preset), x0 + 6 + 3 * 74 + 4, y0 + 17, Ui.GOLD);
		List<String> shown = shown();
		int y = y0 + 36;
		for (int i = page * perPage(); i < shown.size() && i < (page + 1) * perPage(); i++) {
			String path = shown.get(i);
			boolean changed = edits.containsKey(path);
			boolean ok = valid(path, value(path));
			int color = !ok ? Ui.DANGER : changed ? Ui.WARN : Ui.TEXT;
			Ui.text(g, this.font, Ui.ellipsize(this.font, label(path), W - 12 - 96), x0 + 6, y + 3, color);
			y += ROW;
		}
		int pages = Math.max(1, (shown.size() + perPage() - 1) / perPage());
		Ui.centered(g, this.font, Ui.tr("gui.extremeprogression.settings.page", page + 1, pages, shown.size(), edits.size()), this.width / 2, y0 + H - 43, Ui.MUTED);
		if (!message.isEmpty()) {
			Ui.centered(g, this.font, Ui.ellipsize(this.font, message, W - 60), this.width / 2, y0 + H - 32, Ui.WARN);
		}
		if (!ClientAdmin.editor()) {
			Ui.right(g, this.font, Ui.tr("gui.extremeprogression.settings.read_only"), x0 + W - 6, y0 + 2, Ui.DIM);
		}
	}

	/** "config.death_base" reads "Death base"; "skills.skills.melee.xp_multiplier" reads "Melee · xp multiplier". */
	private static String label(String path) {
		String[] parts = path.split("\\.");
		StringBuilder out = new StringBuilder();
		int from = parts[0].equals("config") ? 1 : parts[0].equals("skills") && parts.length > 2 ? 2 : 1;
		for (int i = from; i < parts.length; i++) {
			if (out.length() > 0) {
				out.append(" · ");
			}
			out.append(parts[i].replace('_', ' '));
		}
		String s = out.toString();
		return s.isEmpty() ? path : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	@Override
	public void onClose() {
		if (parent != null) {
			Minecraft.getInstance().gui.setScreen(parent);
		} else {
			super.onClose();
		}
	}

	public boolean isPauseScreen() {
		return false;
	}
}
