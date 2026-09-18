package com.skystream.ssmusic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class PreferencesLayoutTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";

    @Test
    public void settingsButtonClearsBottomPlaybackAndNavigationArea() throws Exception {
        Document layout = readResource("layout/activity_main.xml");
        Element button = findById(layout, "settings_button");
        assertEquals("bottom|end", button.getAttributeNS(ANDROID, "layout_gravity"));
        assertEquals("77dp", button.getAttributeNS(ANDROID, "layout_marginBottom"));
        assertEquals("16dp", button.getAttributeNS(ANDROID, "layout_marginEnd"));
        assertEquals("48dp", button.getAttributeNS(ANDROID, "layout_width"));
        assertEquals("48dp", button.getAttributeNS(ANDROID, "layout_height"));
        assertEquals("@string/settings", button.getAttributeNS(ANDROID, "contentDescription"));
    }

    @Test
    public void kidModeHomeOverlaysUpperLeftWithAccessibleTouchTarget() throws Exception {
        Document layout = readResource("layout/activity_main.xml");
        Element button = findById(layout, "kid_mode_home_button");
        assertSame(layout.getDocumentElement(), button.getParentNode());
        assertEquals("ImageButton", button.getTagName());
        assertEquals("top|left", button.getAttributeNS(ANDROID, "layout_gravity"));
        assertEquals("16dp", button.getAttributeNS(ANDROID, "layout_margin"));
        assertEquals("48dp", button.getAttributeNS(ANDROID, "layout_width"));
        assertEquals("48dp", button.getAttributeNS(ANDROID, "layout_height"));
        assertEquals("@string/home", button.getAttributeNS(ANDROID, "contentDescription"));
        assertEquals("@drawable/ic_home", button.getAttributeNS(ANDROID, "src"));
        assertEquals("#FFFFFF", button.getAttributeNS(ANDROID, "tint"));
        assertEquals("gone", button.getAttributeNS(ANDROID, "visibility"));
        assertEquals(Node.DOCUMENT_POSITION_FOLLOWING,
                findById(layout, "webview").compareDocumentPosition(button));
        assertEquals(Node.DOCUMENT_POSITION_FOLLOWING,
                findById(layout, "stats_overlay").compareDocumentPosition(button));
    }

    @Test
    public void appearanceAndSiteModeUseLabeledDropdowns() throws Exception {
        Document layout = readResource("layout/dialog_preferences.xml");
        assertDropdown(layout, "theme_spinner", "@array/theme_options");
        assertDropdown(layout, "site_mode_spinner", "@array/site_mode_options");
        assertEquals(0, layout.getElementsByTagName("RadioGroup").getLength());

        Document strings = readResource("values/strings.xml");
        assertOptions(strings, "theme_options",
                "@string/theme_system", "@string/theme_light", "@string/theme_dark");
        assertOptions(strings, "site_mode_options",
                "@string/site_mode_mobile", "@string/site_mode_desktop");
    }

    @Test
    public void musicServerControlsAreOutsideCollapsedSectionsAndStartLoggedOut() throws Exception {
        Document layout = readResource("layout/dialog_preferences.xml");
        for (String id : new String[]{"music_server_login", "music_server_cancel",
                "music_server_playlist", "music_server_song", "music_server_logout"}) {
            Element button = findById(layout, id);
            assertSame(layout.getDocumentElement(), button.getParentNode());
            assertEquals("Button", button.getTagName());
            assertEquals("@string/" + id, button.getAttributeNS(ANDROID, "text"));
            if (!"music_server_login".equals(id)) {
                assertEquals("gone", button.getAttributeNS(ANDROID, "visibility"));
            }
        }
    }

    @Test
    public void advancedStartsCollapsedAndContainsOnlyAdvancedSettings() throws Exception {
        Document layout = readResource("layout/dialog_preferences.xml");
        Element advanced = findById(layout, "advanced_settings");
        assertEquals("gone", advanced.getAttributeNS(ANDROID, "visibility"));
        assertSame(advanced.getParentNode(), findById(layout, "advanced_button").getParentNode());
        for (String id : new String[]{"open_supported_links_button",
                "stats_for_nerds_switch", "kid_mode_switch"}) {
            assertSame(advanced, findById(layout, id).getParentNode());
        }
        for (String id : new String[]{"video_thumbnail_switch", "navigation_bar"}) {
            assertSame(advanced.getParentNode(), findById(layout, id).getParentNode());
        }
        for (String id : new String[]{"theme_spinner", "site_mode_spinner"}) {
            assertSame(advanced.getParentNode(), findById(layout, id).getParentNode().getParentNode());
        }
    }

    @Test
    public void loggingIsIndependentlyCollapsibleWithAllLoggingControls() throws Exception {
        Document layout = readResource("layout/dialog_preferences.xml");
        Element logging = findById(layout, "logging_settings");
        Element button = findById(layout, "logging_button");
        assertEquals("gone", logging.getAttributeNS(ANDROID, "visibility"));
        assertSame(layout.getDocumentElement(), logging.getParentNode());
        assertSame(logging.getParentNode(), button.getParentNode());
        assertSame(logging.getParentNode(), findById(layout, "advanced_settings").getParentNode());
        assertEquals("Button", button.getTagName());
        assertEquals("@string/logging_collapsed", button.getAttributeNS(ANDROID, "text"));
        assertEquals("false", button.getAttributeNS(ANDROID, "textAllCaps"));
        for (String id : new String[]{"logging_switch", "logging_summary", "log_actions"}) {
            assertSame(logging, findById(layout, id).getParentNode());
        }
    }

    @Test
    public void headerKeepsTitleLeftAndCompactUpdateControlsRight() throws Exception {
        Document layout = readResource("layout/preferences_header.xml");
        Element title = findById(layout, "preferences_title");
        Element button = findById(layout, "check_updates_button");
        Element version = findById(layout, "app_version");
        Element controls = (Element) button.getParentNode();
        assertSame(controls, version.getParentNode());
        assertSame(layout.getDocumentElement(), title.getParentNode());
        assertSame(title.getParentNode(), controls.getParentNode());
        assertEquals("horizontal", layout.getDocumentElement().getAttributeNS(ANDROID, "orientation"));
        assertEquals("match_parent", layout.getDocumentElement().getAttributeNS(ANDROID, "layout_width"));
        assertEquals("0dp", title.getAttributeNS(ANDROID, "layout_width"));
        assertEquals("1", title.getAttributeNS(ANDROID, "layout_weight"));
        assertEquals("start", title.getAttributeNS(ANDROID, "gravity"));
        assertEquals("@string/preferences", title.getAttributeNS(ANDROID, "text"));
        assertEquals("wrap_content", controls.getAttributeNS(ANDROID, "layout_width"));
        assertEquals("end", controls.getAttributeNS(ANDROID, "gravity"));
        assertEquals("vertical", controls.getAttributeNS(ANDROID, "orientation"));
        assertEquals("wrap_content", button.getAttributeNS(ANDROID, "layout_width"));
        assertFalse(button.hasAttributeNS(ANDROID, "layout_weight"));
        assertEquals("0dp", button.getAttributeNS(ANDROID, "minWidth"));
        assertEquals("48dp", button.getAttributeNS(ANDROID, "minHeight"));
        assertEquals("false", button.getAttributeNS(ANDROID, "textAllCaps"));
        assertEquals("wrap_content", version.getAttributeNS(ANDROID, "layout_width"));
    }

    @Test
    public void navigationControlsRemainWithoutHeading() throws Exception {
        Document layout = readResource("layout/dialog_preferences.xml");
        NodeList labels = layout.getElementsByTagName("TextView");
        for (int i = 0; i < labels.getLength(); i++) {
            Element label = (Element) labels.item(i);
            assertFalse("@string/navigation".equals(label.getAttributeNS(ANDROID, "text")));
        }
        Element navigation = findById(layout, "navigation_bar");
        for (String id : new String[]{"back_button", "forward_button", "refresh_button", "home_button"}) {
            assertSame(navigation, findById(layout, id).getParentNode());
        }
    }

    @Test
    public void allLogActionsShareOneEqualWidthRow() throws Exception {
        Document layout = readResource("layout/dialog_preferences.xml");
        Element row = findById(layout, "log_actions");
        assertEquals("horizontal", row.getAttributeNS(ANDROID, "orientation"));
        NodeList buttons = row.getElementsByTagName("Button");
        assertEquals(3, buttons.getLength());
        String[] ids = {"view_log_button", "share_log_button", "clear_log_button"};
        for (int i = 0; i < ids.length; i++) {
            Element button = findById(layout, ids[i]);
            assertSame(row, button.getParentNode());
            assertSame(button, buttons.item(i));
            assertEquals("0dp", button.getAttributeNS(ANDROID, "layout_width"));
            assertEquals("1", button.getAttributeNS(ANDROID, "layout_weight"));
            assertEquals("1", button.getAttributeNS(ANDROID, "maxLines"));
        }
    }

    @Test
    public void loggingOffersBothModesAndDisplaysCurrentModeSummary() throws Exception {
        Document strings = readResource("values/strings.xml");
        assertOptions(strings, "logging_modes", "@string/logging_full", "@string/logging_reactive");
        Document layout = readResource("layout/dialog_preferences.xml");
        assertEquals("@string/enable_logging_summary",
                findById(layout, "logging_summary").getAttributeNS(ANDROID, "text"));
    }

    private static Document readResource(String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new File("src/main/res", path));
    }

    private static Element findById(Document document, String id) {
        NodeList elements = document.getElementsByTagName("*");
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (("@+id/" + id).equals(element.getAttributeNS(ANDROID, "id"))) {
                return element;
            }
        }
        throw new AssertionError("Missing view: " + id);
    }

    private static void assertDropdown(Document layout, String id, String entries) {
        Element spinner = findById(layout, id);
        assertEquals("Spinner", spinner.getTagName());
        assertEquals("dropdown", spinner.getAttributeNS(ANDROID, "spinnerMode"));
        assertEquals(entries, spinner.getAttributeNS(ANDROID, "entries"));
        NodeList labels = layout.getElementsByTagName("TextView");
        Element label = null;
        for (int i = 0; i < labels.getLength(); i++) {
            Element candidate = (Element) labels.item(i);
            if (("@+id/" + id).equals(candidate.getAttributeNS(ANDROID, "labelFor"))) {
                label = candidate;
            }
        }
        assertNotNull("Missing dropdown label: " + id, label);
        assertHorizontalRow(label, spinner);
        assertEquals("0dp", label.getAttributeNS(ANDROID, "layout_width"));
        assertEquals("1", label.getAttributeNS(ANDROID, "layout_weight"));
        assertEquals("wrap_content", spinner.getAttributeNS(ANDROID, "layout_width"));
        assertEquals("48dp", spinner.getAttributeNS(ANDROID, "minHeight"));
    }

    private static void assertHorizontalRow(Element first, Element second) {
        Element row = (Element) first.getParentNode();
        assertSame(row, second.getParentNode());
        assertEquals("LinearLayout", row.getTagName());
        assertEquals("horizontal", row.getAttributeNS(ANDROID, "orientation"));
        assertEquals("center_vertical", row.getAttributeNS(ANDROID, "gravity"));
        NodeList children = row.getElementsByTagName("*");
        assertEquals(2, children.getLength());
        assertSame(first, children.item(0));
        assertSame(second, children.item(1));
    }

    private static void assertOptions(Document strings, String name, String... expected) {
        NodeList arrays = strings.getElementsByTagName("string-array");
        for (int i = 0; i < arrays.getLength(); i++) {
            Element array = (Element) arrays.item(i);
            if (!name.equals(array.getAttribute("name"))) {
                continue;
            }
            NodeList items = array.getElementsByTagName("item");
            assertEquals(expected.length, items.getLength());
            for (int j = 0; j < expected.length; j++) {
                Node item = items.item(j);
                assertEquals(expected[j], item.getTextContent());
            }
            return;
        }
        throw new AssertionError("Missing options: " + name);
    }
}
