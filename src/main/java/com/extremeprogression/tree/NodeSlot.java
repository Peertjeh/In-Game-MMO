package com.extremeprogression.tree;

import java.util.List;
import java.util.Locale;

/**
 * The places of every specialization's branch (design §6, the Skill Screen mockup of round 21), from the bottom up:
 * <pre>
 *   row 8   A5            ability rank V    (level 80)
 *   row 7   A4            ability rank IV   (level 60)
 *   row 6   A3            ability rank III
 *   row 5   C1 or C2      a choice node: one of the two
 *   row 4   P3   P4       passives
 *   row 3   A2            ability rank II
 *   row 2   A1            ability rank I
 *   row 1   P1   P2       passives
 * </pre>
 * Passives and choice nodes have five ranks. A whole branch costs 80 points, more than the 75 a skill earns from
 * level 25 to 100 (design §6), so nobody can take everything, and two branches compete for the same points.
 */
public enum NodeSlot {
	P1(Kind.PASSIVE, 1, 0, 5, 2, List.of(), 0),
	P2(Kind.PASSIVE, 1, 1, 5, 2, List.of(), 0),
	A1(Kind.ABILITY, 2, 0, 1, 3, List.of(), 0),
	A2(Kind.ABILITY, 3, 0, 1, 3, List.of("A1"), 0),
	P3(Kind.PASSIVE, 4, 0, 5, 3, List.of("A2"), 0),
	P4(Kind.PASSIVE, 4, 1, 5, 3, List.of("A2"), 0),
	C1(Kind.CHOICE, 5, 0, 5, 3, List.of("P1", "P2"), 0),
	C2(Kind.CHOICE, 5, 1, 5, 3, List.of("P1", "P2"), 0),
	A3(Kind.ABILITY, 6, 0, 1, 3, List.of("A2"), 0),
	A4(Kind.ABILITY, 7, 0, 1, 3, List.of("A3"), 60),
	A5(Kind.ABILITY, 8, 0, 1, 3, List.of("A4"), 80);

	public enum Kind { PASSIVE, ABILITY, CHOICE }

	public final Kind kind;
	/** 1 at the bottom, 8 at the top. */
	public final int row;
	/** 0 or 1 within its row. */
	public final int column;
	public final int maxRank;
	/** Points per rank. */
	public final int cost;
	/** At least one rank in one of these first (none: the specialization itself is enough). */
	private final List<String> requires;
	/** The skill level a rank needs, besides the points. */
	public final int level;
	public final String id;

	NodeSlot(Kind kind, int row, int column, int maxRank, int cost, List<String> requires, int level) {
		this.kind = kind;
		this.row = row;
		this.column = column;
		this.maxRank = maxRank;
		this.cost = cost;
		this.requires = requires;
		this.level = level;
		this.id = name().toLowerCase(Locale.ROOT);
	}

	public List<NodeSlot> requires() {
		return requires.stream().map(NodeSlot::valueOf).toList();
	}

	/** The other side of a choice node, or null. */
	public NodeSlot partner() {
		return this == C1 ? C2 : this == C2 ? C1 : null;
	}

	/** The ability rank this node gives (1–5), or 0 for a passive or choice node. */
	public int abilityRank() {
		return kind == Kind.ABILITY ? Integer.parseInt(name().substring(1)) : 0;
	}

	public static NodeSlot byId(String id) {
		for (NodeSlot n : values()) {
			if (n.id.equals(id)) {
				return n;
			}
		}
		return null;
	}

	/** The total cost of a full branch (one side of the choice). */
	public static int fullBranch() {
		int total = 0;
		for (NodeSlot n : values()) {
			if (n != C2) {
				total += n.cost * n.maxRank;
			}
		}
		return total;
	}
}
