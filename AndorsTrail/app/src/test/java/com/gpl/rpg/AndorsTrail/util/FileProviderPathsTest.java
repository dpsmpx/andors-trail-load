package com.gpl.rpg.AndorsTrail.util;

import static org.junit.Assert.assertEquals;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.gpl.rpg.AndorsTrail.controller.Constants;

/**
 * The FileProvider serves the world map pages to the WebView and must share nothing else
 * (audit finding L1). Its path must match where WorldMapController writes the files.
 */
public final class FileProviderPathsTest {

	@Test
	public void onlyTheWorldMapFolderIsShared() throws Exception {
		// Gradle runs unit tests in the module directory, AndorsTrail/app.
		Element paths = DocumentBuilderFactory.newInstance().newDocumentBuilder()
				.parse(new File("../res/xml/fileprovider.xml")).getDocumentElement();
		NodeList children = paths.getChildNodes();
		int count = 0;
		for (int i = 0; i < children.getLength(); ++i) {
			if (children.item(i).getNodeType() != Node.ELEMENT_NODE) continue;
			Element path = (Element) children.item(i);
			++count;
			// AndroidStorage.getStorageDirectory uses getExternalFilesDir on Android 10+.
			assertEquals("external-files-path", path.getTagName());
			assertEquals(Constants.FILENAME_SAVEGAME_DIRECTORY + "/" + Constants.FILENAME_WORLDMAP_DIRECTORY + "/", path.getAttribute("path"));
		}
		assertEquals(1, count);
	}
}
