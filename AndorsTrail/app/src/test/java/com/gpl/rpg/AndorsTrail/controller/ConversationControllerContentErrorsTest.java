package com.gpl.rpg.AndorsTrail.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.gpl.rpg.AndorsTrail.AndorsTrailApplication;
import com.gpl.rpg.AndorsTrail.context.WorldContext;
import com.gpl.rpg.AndorsTrail.model.ModelContainer;
import com.gpl.rpg.AndorsTrail.model.actor.Monster;
import com.gpl.rpg.AndorsTrail.model.conversation.ConversationCollection;
import com.gpl.rpg.AndorsTrail.model.conversation.Reply;
import com.gpl.rpg.AndorsTrail.model.script.Requirement;
import com.gpl.rpg.AndorsTrail.model.script.ScriptEffect;
import com.gpl.rpg.AndorsTrail.resource.ConversationLoader;

/**
 * Content with references to things that do not exist must not crash the game (audit finding M1).
 * The world here has no content at all, so every id is unknown.
 */
public final class ConversationControllerContentErrorsTest {
	private WorldContext world;

	@Before
	public void createWorld() {
		world = new WorldContext();
		world.model = new ModelContainer(1, true);
		world.model.player.setName("Tester");
	}

	@Test
	public void unknownPhraseIsNotLoaded() {
		assertNull(new ConversationLoader().loadPhrase("no_such_phrase", new ConversationCollection(), null));
	}

	@Test
	public void unknownPhraseDoesNotCrashTheConversation() {
		RecordingListener listener = new RecordingListener();
		ConversationController.ConversationStatemachine conversation = new ConversationController.ConversationStatemachine(world, null, listener);

		conversation.proceedToPhrase(null, "no_such_phrase", false, true);

		if (AndorsTrailApplication.DEVELOPMENT_DEBUGMESSAGES) {
			// Debug builds show a placeholder so that content authors notice the missing phrase.
			assertEquals(1, listener.messages.size());
			assertTrue(listener.messages.get(0).contains("no_such_phrase"));
			assertFalse(listener.ended);
		} else {
			assertTrue(listener.ended);
			assertTrue(listener.messages.isEmpty());
		}
	}

	@Test
	public void scriptEffectsWithUnknownIdsAreIgnored() {
		ConversationController controller = new ConversationController(null, world);
		ConversationController.ScriptEffectResult result = new ConversationController.ScriptEffectResult();
		ScriptEffect[] effects = {
				new ScriptEffect(ScriptEffect.ScriptEffectType.skillIncrease, "no_such_skill", 1, null, null),
				new ScriptEffect(ScriptEffect.ScriptEffectType.actorCondition, "no_such_condition", 5, null, null),
				new ScriptEffect(ScriptEffect.ScriptEffectType.actorConditionImmunity, "no_such_condition", 5, null, null),
				new ScriptEffect(ScriptEffect.ScriptEffectType.dropList, "no_such_droplist", 0, null, null),
				new ScriptEffect(ScriptEffect.ScriptEffectType.giveItem, "no_such_item", 1, null, null),
				new ScriptEffect(ScriptEffect.ScriptEffectType.giveItem, "#no_such_filter", 1, null, null),
				new ScriptEffect(ScriptEffect.ScriptEffectType.spawnAll, "area", 0, "no_such_map", null),
				new ScriptEffect(ScriptEffect.ScriptEffectType.removeSpawnArea, "area", 0, "no_such_map", null),
				new ScriptEffect(ScriptEffect.ScriptEffectType.deactivateSpawnArea, "area", 0, "no_such_map", null),
				new ScriptEffect(ScriptEffect.ScriptEffectType.activateMapObjectGroup, "group", 0, "no_such_map", null),
				new ScriptEffect(ScriptEffect.ScriptEffectType.deactivateMapObjectGroup, "group", 0, "no_such_map", null),
				new ScriptEffect(ScriptEffect.ScriptEffectType.changeMapFilter, "black20", 0, "no_such_map", null),
		};
		for (ScriptEffect effect : effects) {
			controller.applyScriptEffect(null, world.model.player, effect, result);
		}
		assertTrue(result.isEmpty());
	}

	@Test
	public void requirementsOnUnknownSkillsAreNotFulfilled() {
		assertFalse(ConversationController.canFulfillRequirement(world,
				new Requirement(Requirement.RequirementType.skillLevel, "no_such_skill", 0, false, null)));
		assertFalse(ConversationController.canFulfillRequirement(world,
				new Requirement(Requirement.RequirementType.skillIncrease, "no_such_skill", 1, false, null)));
	}

	private static final class RecordingListener implements ConversationController.ConversationStatemachine.ConversationStateListener {
		final List<String> messages = new ArrayList<String>();
		boolean ended = false;

		@Override public void onTextPhraseReached(String message, com.gpl.rpg.AndorsTrail.model.actor.Actor actor, String phraseID) { messages.add(message); }
		@Override public void onConversationEnded() { ended = true; }
		@Override public void onConversationEndedWithShop(Monster npc) { ended = true; }
		@Override public void onConversationEndedWithCombat(Monster npc) { ended = true; }
		@Override public void onConversationEndedWithRemoval(Monster npc) { ended = true; }
		@Override public void onScriptEffectsApplied(ConversationController.ScriptEffectResult scriptEffectResult) { }
		@Override public void onConversationCanProceedWithNext() { }
		@Override public void onConversationHasReply(Reply r, String message) { }
	}
}
