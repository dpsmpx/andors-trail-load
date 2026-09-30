package com.gpl.rpg.AndorsTrail.controller;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.gpl.rpg.AndorsTrail.model.actor.Monster;
import com.gpl.rpg.AndorsTrail.model.actor.MonsterType;
import com.gpl.rpg.AndorsTrail.util.ConstRange;
import com.gpl.rpg.AndorsTrail.util.Size;

/**
 * CombatController.getAverageDamagePerHit, which the monster difficulty shown to the player is
 * based on. CombatController asks to run this test after changing the calculation (audit finding
 * L13). The expected values are worked out by hand from the rules of CombatController.attack.
 */
public final class CombatControllerTest {
	private static final float DELTA = 0.0001f;

	@Test
	public void averageOfTheDamageRange() {
		// Attack chance - block chance = 50 gives a hit chance of exactly 50 %.
		// Damage 2..4 without resistance averages 3.
		Monster attacker = monster(MonsterType.MonsterClass.humanoid, 50, 2, 4, 0, 0, 0);
		Monster target = monster(MonsterType.MonsterClass.humanoid, 0, 1, 1, 0, 0, 0);
		assertEquals(0.5f * 3, CombatController.getAverageDamagePerHit(attacker, target), DELTA);
	}

	@Test
	public void resistanceIsSubtractedPerHitAndNeverNegative() {
		// Damage 2, 3, 4 against resistance 3: 0, 0, 1.
		Monster attacker = monster(MonsterType.MonsterClass.humanoid, 50, 2, 4, 0, 0, 0);
		Monster target = monster(MonsterType.MonsterClass.humanoid, 0, 1, 1, 0, 0, 3);
		assertEquals(0.5f * (1f / 3), CombatController.getAverageDamagePerHit(attacker, target), DELTA);
	}

	@Test
	public void criticalHitsMultiplyTheDamage() {
		// Critical skill 20 gives a 15 % critical chance (Actor.getEffectiveCriticalChance).
		// Normal hits average 3; critical hits with multiplier 2 do 4, 6, 8, which average 6.
		Monster attacker = monster(MonsterType.MonsterClass.humanoid, 50, 2, 4, 20, 2, 0);
		Monster target = monster(MonsterType.MonsterClass.humanoid, 0, 1, 1, 0, 0, 0);
		assertEquals(15, attacker.getEffectiveCriticalChance());
		assertEquals(0.5f * (0.85f * 3 + 0.15f * 6), CombatController.getAverageDamagePerHit(attacker, target), DELTA);
	}

	@Test
	public void targetsImmuneToCriticalHitsTakeNormalDamage() {
		Monster attacker = monster(MonsterType.MonsterClass.humanoid, 50, 2, 4, 20, 2, 0);
		Monster ghost = monster(MonsterType.MonsterClass.ghost, 0, 1, 1, 0, 0, 0);
		assertEquals(0.5f * 3, CombatController.getAverageDamagePerHit(attacker, ghost), DELTA);
	}

	private static Monster monster(MonsterType.MonsterClass monsterClass, int attackChance, int minDamage, int maxDamage,
			int criticalSkill, float criticalMultiplier, int damageResistance) {
		MonsterType type = new MonsterType("test", "test", "test", 0, null, null, false, null, monsterClass,
				MonsterType.AggressionType.none, new Size(1, 1), 0, 0, 10, 10, 10, 5, attackChance, criticalSkill, criticalMultiplier,
				new ConstRange(maxDamage, minDamage), 0, damageResistance, null, null, null);
		return new Monster(type, null);
	}
}
