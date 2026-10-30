package com.extremeprogression.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Brings player files of older schemas up to {@link PlayerProgress#SCHEMA}, one step per schema, in place. A step only
 * adds or renames; it never drops what it does not know, so no update can reset anyone's progression.
 */
public final class Migrations {
	private Migrations() {}

	/** The schema a file was written with; 0 for files from before the field existed. */
	public static int schemaOf(JsonObject json) {
		JsonElement v = json.get("schema");
		return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber() ? v.getAsInt() : 0;
	}

	public static JsonObject upgrade(JsonObject json) {
		int from = schemaOf(json);
		if (from < 1) {
			// 0 → 1: a file without a schema holds the first schema's fields; what it lacks takes the defaults
			json.addProperty("schema", 1);
		}
		if (from < 2) {
			// 1 → 2: exploration was added; a file without it has discovered nothing yet
			json.addProperty("schema", 2);
		}
		if (from < 3) {
			// 2 → 3: specializations, points, cooldowns and the respec time per skill; none were chosen before
			json.addProperty("schema", 3);
		}
		return json;
	}
}
