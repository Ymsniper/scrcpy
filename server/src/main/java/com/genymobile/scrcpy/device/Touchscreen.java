package com.genymobile.scrcpy.device;

import com.genymobile.scrcpy.AndroidVersions;
import com.genymobile.scrcpy.util.Command;
import com.genymobile.scrcpy.util.Ln;
import com.genymobile.scrcpy.wrappers.InputManager;
import com.genymobile.scrcpy.wrappers.ServiceManager;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.SystemClock;
import android.view.InputDevice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Disable the physical touchscreens, typically to ignore ghost touches from a broken screen.
 * <p>
 * Events injected by scrcpy do not come from a physical input device, so they are not affected.
 */
public final class Touchscreen {

    // Prefixes of the restore tokens, passed to the clean up process as arguments
    private static final String TOKEN_DEVICE_ID = "id:";
    private static final String TOKEN_INPUT_PORT = "port:";

    private static final int MAX_DISPLAY_PORT = 255; // display ports are 8-bit values

    private Touchscreen() {
        // not instantiable
    }

    /**
     * Disable all the physical touchscreens.
     *
     * @return the tokens to pass to {@link #restore(List)} to enable them back
     */
    public static List<String> disable() {
        List<String> tokens = new ArrayList<>();

        if (Build.VERSION.SDK_INT < AndroidVersions.API_27_ANDROID_8_1) {
            Ln.w("Disabling the touchscreen is not supported before Android 8.1");
            return tokens;
        }

        List<InputDevice> touchscreens = findTouchscreens();
        if (touchscreens.isEmpty()) {
            Ln.w("No touchscreen found");
            return tokens;
        }

        InputManager inputManager = ServiceManager.getInputManager();
        List<InputDevice> remaining = new ArrayList<>();
        for (InputDevice device : touchscreens) {
            // Requires the DISABLE_INPUT_DEVICE permission, which is not granted to the shell (but it works as root)
            if (inputManager.disableInputDevice(device.getId())) {
                tokens.add(TOKEN_DEVICE_ID + device.getId());
            } else {
                remaining.add(device);
            }
        }

        if (!remaining.isEmpty() && Build.VERSION.SDK_INT >= AndroidVersions.API_30_ANDROID_11) {
            associateToMissingDisplay(inputManager, remaining, tokens);
        }

        if (!waitDisabled(touchscreens)) {
            for (InputDevice device : touchscreens) {
                if (isEnabled(device.getId())) {
                    Ln.w("Could not disable touchscreen \"" + device.getName() + "\"");
                }
            }
            if (Build.VERSION.SDK_INT < AndroidVersions.API_34_ANDROID_14) {
                Ln.w("Disabling the touchscreen requires Android 14 (or adb running as root)");
            }
        }

        return tokens;
    }

    /**
     * Enable back the touchscreens disabled by {@link #disable()}.
     */
    public static void restore(List<String> tokens) {
        InputManager inputManager = ServiceManager.getInputManager();
        for (String token : tokens) {
            if (token.startsWith(TOKEN_DEVICE_ID)) {
                int id = Integer.parseInt(token.substring(TOKEN_DEVICE_ID.length()));
                inputManager.enableInputDevice(id);
            } else if (token.startsWith(TOKEN_INPUT_PORT)) {
                inputManager.removePortAssociation(token.substring(TOKEN_INPUT_PORT.length()));
            } else {
                Ln.e("Invalid touchscreen restore token: " + token);
            }
        }
    }

    private static List<InputDevice> findTouchscreens() {
        List<InputDevice> touchscreens = new ArrayList<>();
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device != null && !device.isVirtual() && device.supportsSource(InputDevice.SOURCE_TOUCHSCREEN)) {
                touchscreens.add(device);
            }
        }
        return touchscreens;
    }

    /**
     * Associate the input devices to a display port without any display, so that InputReader disables them.
     * <p>
     * An input port is either the input device descriptor (since Android 15) or its location (since Android 11). Contrary to
     * DISABLE_INPUT_DEVICE, the ASSOCIATE_INPUT_DEVICE_TO_DISPLAY permission is granted to the shell (since Android 14).
     */
    @TargetApi(AndroidVersions.API_30_ANDROID_11)
    private static void associateToMissingDisplay(InputManager inputManager, List<InputDevice> devices, List<String> tokens) {
        String dumpsysInput;
        try {
            dumpsysInput = Command.execReadOutput("dumpsys", "input");
        } catch (IOException | InterruptedException e) {
            Ln.e("Could not execute \"dumpsys input\"", e);
            // The descriptor alone is sufficient on Android 15
            dumpsysInput = "";
        }

        int displayPort = findUnusedDisplayPort(dumpsysInput);
        if (displayPort == -1) {
            Ln.e("No unused display port");
            return;
        }

        Set<String> descriptors = new HashSet<>();
        for (InputDevice device : devices) {
            descriptors.add(device.getDescriptor());
        }

        List<String> inputPorts = new ArrayList<>();
        for (String descriptor : descriptors) {
            if (!descriptor.isEmpty()) {
                inputPorts.add(descriptor);
            }
        }
        for (Map.Entry<String, Set<String>> entry : parseDescriptorsByLocation(dumpsysInput).entrySet()) {
            String location = entry.getKey();
            // Do not disable other input devices sharing the same location
            if (!location.isEmpty() && descriptors.containsAll(entry.getValue())) {
                inputPorts.add(location);
            }
        }

        for (String inputPort : inputPorts) {
            if (!inputManager.addPortAssociation(inputPort, displayPort)) {
                // The next calls would fail for the same reason
                return;
            }
            tokens.add(TOKEN_INPUT_PORT + inputPort);
        }
    }

    // public to call it from unit tests
    public static int findUnusedDisplayPort(String dumpsysInputOutput) {
        Set<Integer> usedPorts = new HashSet<>();
        Pattern regex = Pattern.compile("^\\s*Viewport \\S+: .*?, port=([0-9]+),", Pattern.MULTILINE);
        Matcher m = regex.matcher(dumpsysInputOutput);
        while (m.find()) {
            usedPorts.add(Integer.parseInt(m.group(1)));
        }

        for (int port = MAX_DISPLAY_PORT; port >= 0; --port) {
            if (!usedPorts.contains(port)) {
                return port;
            }
        }
        return -1;
    }

    // public to call it from unit tests
    public static Map<String, Set<String>> parseDescriptorsByLocation(String dumpsysInputOutput) {
        Map<String, Set<String>> result = new HashMap<>();
        Pattern regex = Pattern.compile("^\\s*Descriptor: (\\S*)\\s*\\n\\s*Location: (.*)$", Pattern.MULTILINE);
        Matcher m = regex.matcher(dumpsysInputOutput);
        while (m.find()) {
            String descriptor = m.group(1);
            String location = m.group(2).trim();
            Set<String> descriptors = result.get(location);
            if (descriptors == null) {
                descriptors = new HashSet<>();
                result.put(location, descriptors);
            }
            descriptors.add(descriptor);
        }
        return result;
    }

    private static boolean waitDisabled(List<InputDevice> devices) {
        // The devices are disabled asynchronously by InputReader
        for (int i = 0; i < 20; ++i) {
            boolean allDisabled = true;
            for (InputDevice device : devices) {
                if (isEnabled(device.getId())) {
                    allDisabled = false;
                    break;
                }
            }
            if (allDisabled) {
                return true;
            }
            SystemClock.sleep(50);
        }
        return false;
    }

    @TargetApi(AndroidVersions.API_27_ANDROID_8_1)
    private static boolean isEnabled(int id) {
        // Since Android 15, the enabled state is stored in the InputDevice instance, so it must be retrieved again
        InputDevice device = InputDevice.getDevice(id);
        return device != null && device.isEnabled();
    }
}
