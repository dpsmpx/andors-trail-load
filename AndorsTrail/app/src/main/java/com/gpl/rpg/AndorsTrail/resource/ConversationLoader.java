package com.gpl.rpg.AndorsTrail.resource;

import java.util.Collection;
import java.util.HashMap;

import android.content.res.Resources;

import com.gpl.rpg.AndorsTrail.model.conversation.ConversationCollection;
import com.gpl.rpg.AndorsTrail.model.conversation.Phrase;
import com.gpl.rpg.AndorsTrail.resource.parsers.ConversationListParser;

public final class ConversationLoader {
	private final HashMap<String, Integer> resourceIDsPerPhraseID = new HashMap<String, Integer>();

	public void addIDs(int resourceId, Collection<String> ids) {
		for(String s : ids) resourceIDsPerPhraseID.put(s, resourceId);
	}

	public Phrase loadPhrase(String phraseID, ConversationCollection conversationCollection, Resources r) {
		if (conversationCollection.hasPhrase(phraseID)) {
			return conversationCollection.getPhrase(phraseID);
		}

		Integer resourceID = resourceIDsPerPhraseID.get(phraseID);
		if (resourceID == null) return null; // The content refers to a phrase that does not exist.

		TranslationLoader translationLoader = new TranslationLoader(r.getAssets(), r);
		ConversationListParser conversationListParser = new ConversationListParser(translationLoader);
		conversationCollection.initialize(conversationListParser, ResourceLoader.readStringFromRaw(r, resourceID));
		translationLoader.close();

		return conversationCollection.getPhrase(phraseID);
	}
}
