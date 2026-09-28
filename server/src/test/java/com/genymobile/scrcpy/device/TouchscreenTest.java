package com.genymobile.scrcpy.device;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TouchscreenTest {

    private static final String DUMPSYS_INPUT = "INPUT MANAGER (dumpsys input)\n"
            + "\n"
            + "Event Hub State:\n"
            + "  BuiltInKeyboardId: -2\n"
            + "  Devices:\n"
            + "    -1: Virtual\n"
            + "      Classes: KEYBOARD | ALPHAKEY\n"
            + "      Path: <virtual>\n"
            + "      Enabled: true\n"
            + "      Descriptor: a718a782d34bc767f4689c232d64d527998ea7fd\n"
            + "      Location: \n"
            + "      ControllerNumber: 0\n"
            + "      UniqueId: <virtual>\n"
            + "    2: gpio-keys\n"
            + "      Classes: KEYBOARD\n"
            + "      Path: /dev/input/event1\n"
            + "      Enabled: true\n"
            + "      Descriptor: 485d69228e24f5e46da1598745890b214130dbc4\n"
            + "      Location: gpio-keys/input0\n"
            + "      ControllerNumber: 0\n"
            + "      UniqueId: \n"
            + "    4: fts_ts\n"
            + "      Classes: KEYBOARD | TOUCH | TOUCH_MT\n"
            + "      Path: /dev/input/event3\n"
            + "      Enabled: true\n"
            + "      Descriptor: 8c1ea4c0e7fb1d1d4d0f7d9e0a4b0b2f5e6c3a91\n"
            + "      Location: input/ts\n"
            + "      ControllerNumber: 0\n"
            + "      UniqueId: \n"
            + "    5: fts_ts_pen\n"
            + "      Classes: TOUCH | TOUCH_MT\n"
            + "      Path: /dev/input/event4\n"
            + "      Enabled: true\n"
            + "      Descriptor: 2b1f0d3c1c8a3e5f9e0d6b7a4c2e1f0a9b8c7d6e\n"
            + "      Location: input/ts\n"
            + "      ControllerNumber: 0\n"
            + "      UniqueId: \n"
            + "\n"
            + "Input Reader State (Nums of device: 3):\n"
            + "  Configuration:\n"
            + "      Viewports:\n"
            + "        Viewport INTERNAL: displayId=0, uniqueId=local:4619827259835644672, port=0, orientation=0, "
            + "logicalFrame=[0, 0, 1080, 2400], physicalFrame=[0, 0, 1080, 2400], deviceSize=[1080, 2400], isActive=[1]\n"
            + "        Viewport EXTERNAL: displayId=2, uniqueId=local:4619827551948147201, port=129, orientation=0, "
            + "logicalFrame=[0, 0, 1920, 1080], physicalFrame=[0, 0, 1920, 1080], deviceSize=[1920, 1080], isActive=[1]\n"
            + "        Viewport VIRTUAL: displayId=3, uniqueId=virtual:com.genymobile.scrcpy, port=<none>, orientation=0, "
            + "logicalFrame=[0, 0, 1080, 2400], physicalFrame=[0, 0, 1080, 2400], deviceSize=[1080, 2400], isActive=[1]\n";

    private static Set<String> setOf(String... values) {
        return new HashSet<>(Arrays.asList(values));
    }

    @Test
    public void testParseDescriptorsByLocation() {
        Map<String, Set<String>> result = Touchscreen.parseDescriptorsByLocation(DUMPSYS_INPUT);

        Assert.assertEquals(3, result.size());
        Assert.assertEquals(setOf("a718a782d34bc767f4689c232d64d527998ea7fd"), result.get(""));
        Assert.assertEquals(setOf("485d69228e24f5e46da1598745890b214130dbc4"), result.get("gpio-keys/input0"));
        Assert.assertEquals(setOf("8c1ea4c0e7fb1d1d4d0f7d9e0a4b0b2f5e6c3a91", "2b1f0d3c1c8a3e5f9e0d6b7a4c2e1f0a9b8c7d6e"),
                result.get("input/ts"));
    }

    @Test
    public void testParseDescriptorsByLocationEmpty() {
        Assert.assertEquals(Collections.emptyMap(), Touchscreen.parseDescriptorsByLocation(""));
    }

    @Test
    public void testFindUnusedDisplayPort() {
        Assert.assertEquals(255, Touchscreen.findUnusedDisplayPort(DUMPSYS_INPUT));
    }

    @Test
    public void testFindUnusedDisplayPortSkipsUsedPorts() {
        String dumpsys = "      Viewports:\n"
                + "        Viewport INTERNAL: displayId=0, uniqueId=local:4619827259835644927, port=255, orientation=0, "
                + "logicalFrame=[0, 0, 1080, 2400], physicalFrame=[0, 0, 1080, 2400], deviceSize=[1080, 2400], isActive=[1]\n"
                + "        Viewport EXTERNAL: displayId=2, uniqueId=local:4619827551948147198, port=254, orientation=0, "
                + "logicalFrame=[0, 0, 1920, 1080], physicalFrame=[0, 0, 1920, 1080], deviceSize=[1920, 1080], isActive=[1]\n";
        Assert.assertEquals(253, Touchscreen.findUnusedDisplayPort(dumpsys));
    }

    @Test
    public void testFindUnusedDisplayPortWithoutViewports() {
        Assert.assertEquals(255, Touchscreen.findUnusedDisplayPort(""));
    }
}
