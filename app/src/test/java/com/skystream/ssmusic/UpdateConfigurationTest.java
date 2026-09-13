package com.skystream.ssmusic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class UpdateConfigurationTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";

    @Test
    public void updateProviderIsPrivateAndSeparateFromLogs() throws Exception {
        Document manifest = read("AndroidManifest.xml");
        Element updates = find(manifest, "provider", "authorities", "${applicationId}.updates");
        Element logs = find(manifest, "provider", "authorities", "${applicationId}.logs");
        assertEquals(".UpdateFileProvider", updates.getAttributeNS(ANDROID, "name"));
        assertEquals("androidx.core.content.FileProvider", logs.getAttributeNS(ANDROID, "name"));
        assertEquals("false", updates.getAttributeNS(ANDROID, "exported"));
        assertEquals("true", updates.getAttributeNS(ANDROID, "grantUriPermissions"));
        NodeList metadata = updates.getElementsByTagName("meta-data");
        assertEquals(1, metadata.getLength());
        Element paths = (Element) metadata.item(0);
        assertEquals("android.support.FILE_PROVIDER_PATHS",
                paths.getAttributeNS(ANDROID, "name"));
        assertEquals("@xml/update_file_paths", paths.getAttributeNS(ANDROID, "resource"));
    }

    @Test
    public void installerSharingIsLimitedToPrivateUpdateDirectory() throws Exception {
        Document paths = read("res/xml/update_file_paths.xml");
        NodeList entries = paths.getDocumentElement().getElementsByTagName("*");
        assertEquals(1, entries.getLength());
        Element entry = (Element) entries.item(0);
        assertEquals("files-path", entry.getTagName());
        assertEquals("updates/", entry.getAttribute("path"));
    }

    @Test
    public void declaresInstallPermissionAndDisablesCleartextTraffic() throws Exception {
        Document manifest = read("AndroidManifest.xml");
        assertNotNull(find(manifest, "uses-permission", "name",
                "android.permission.REQUEST_INSTALL_PACKAGES"));
        Element application = (Element) manifest.getElementsByTagName("application").item(0);
        assertEquals("false", application.getAttributeNS(ANDROID, "usesCleartextTraffic"));
    }

    private static Document read(String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new File("src/main", path));
    }

    private static Element find(Document document, String tag, String attribute, String value) {
        NodeList elements = document.getElementsByTagName(tag);
        for (int i = 0; i < elements.getLength(); i++) {
            Element element = (Element) elements.item(i);
            if (value.equals(element.getAttributeNS(ANDROID, attribute))) return element;
        }
        throw new AssertionError("Missing " + tag + " with " + attribute + "=" + value);
    }
}
