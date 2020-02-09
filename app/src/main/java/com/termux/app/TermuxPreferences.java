package com.termux.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.TypedValue;
import android.widget.Toast;
import com.termux.terminal.TerminalSession;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;

import androidx.annotation.IntDef;

final class TermuxPreferences {
    @IntDef({BELL_VIBRATE, BELL_BEEP, BELL_IGNORE})
    @Retention(RetentionPolicy.SOURCE)
    @interface AsciiBellBehaviour {
    }

    static final class TermuxProfile {
        String id;
        String displayName;

        @AsciiBellBehaviour
        int mBellBehaviour = BELL_VIBRATE;

        boolean mBackIsEscape;
        boolean mDisableVolumeVirtualKeys;
        String mUseDarkUI;

        boolean mUseCurrentSessionCwd;

        String[][] mExtraKeys;

        final List<KeyboardShortcut> shortcuts = new ArrayList<>();

        boolean isUsingBlackUI() {
            return "true".equalsIgnoreCase(mUseDarkUI);
        }
    }

    static final class KeyboardShortcut {

        KeyboardShortcut(int codePoint, int shortcutAction) {
            this.codePoint = codePoint;
            this.shortcutAction = shortcutAction;
        }

        final int codePoint;
        final int shortcutAction;
    }

    static final int SHORTCUT_ACTION_CREATE_SESSION = 1;
    static final int SHORTCUT_ACTION_NEXT_SESSION = 2;
    static final int SHORTCUT_ACTION_PREVIOUS_SESSION = 3;
    static final int SHORTCUT_ACTION_RENAME_SESSION = 4;

    static final int BELL_VIBRATE = 1;
    static final int BELL_BEEP = 2;
    static final int BELL_IGNORE = 3;

    private final int MIN_FONTSIZE;
    private static final int MAX_FONTSIZE = 256;

    private static final String SHOW_EXTRA_KEYS_KEY = "show_extra_keys";
    private static final String FONTSIZE_KEY = "fontsize";
    private static final String CURRENT_SESSION_KEY = "current_session";
    private static final String SCREEN_ALWAYS_ON_KEY = "screen_always_on";

    static final String DEFAULT_PROFILE_ID = "default";
    private static final String TERMUX_CONF_DIR = TermuxService.HOME_PATH + "/.termux";
    private static final String TERMUX_CONF_PROFILES_DIR = TERMUX_CONF_DIR + "/profiles";
    private static final String TERMUX_XDG_CONF_DIR = TermuxService.HOME_PATH + "/.config/termux";
    private static final String TERMUX_XDG_CONF_PROFILES_DIR = TERMUX_XDG_CONF_DIR + "/profiles";

    private static final FileFilter PROFILE_FILE_FILTER =
        (file) -> file.getName().endsWith(".properties") && file.isFile();

    final LinkedHashMap<String, TermuxProfile> termuxProfiles = new LinkedHashMap<>();
    private boolean mScreenAlwaysOn;
    private int mFontSize;
    boolean mShowExtraKeys;

    /**
     * If value is not in the range [min, max], set it to either min or max.
     */
    static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    TermuxPreferences(Context context) {
        reloadFromProperties(context);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        float dipInPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1, context.getResources().getDisplayMetrics());

        // This is a bit arbitrary and sub-optimal. We want to give a sensible default for minimum font size
        // to prevent invisible text due to zoom be mistake:
        MIN_FONTSIZE = (int) (4f * dipInPixels);

        mShowExtraKeys = prefs.getBoolean(SHOW_EXTRA_KEYS_KEY, true);
        mScreenAlwaysOn = prefs.getBoolean(SCREEN_ALWAYS_ON_KEY, false);

        // http://www.google.com/design/spec/style/typography.html#typography-line-height
        int defaultFontSize = Math.round(12 * dipInPixels);
        // Make it divisible by 2 since that is the minimal adjustment step:
        if (defaultFontSize % 2 == 1) defaultFontSize--;

        try {
            mFontSize = Integer.parseInt(prefs.getString(FONTSIZE_KEY, Integer.toString(defaultFontSize)));
        } catch (NumberFormatException | ClassCastException e) {
            mFontSize = defaultFontSize;
        }
        mFontSize = clamp(mFontSize, MIN_FONTSIZE, MAX_FONTSIZE); 
    }

    boolean toggleShowExtraKeys(Context context) {
        mShowExtraKeys = !mShowExtraKeys;
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(SHOW_EXTRA_KEYS_KEY, mShowExtraKeys).apply();
        return mShowExtraKeys;
    }

    int getFontSize() {
        return mFontSize;
    }

    void changeFontSize(Context context, boolean increase) {
        mFontSize += (increase ? 1 : -1) * 2;
        mFontSize = Math.max(MIN_FONTSIZE, Math.min(mFontSize, MAX_FONTSIZE));

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString(FONTSIZE_KEY, Integer.toString(mFontSize)).apply();
    }

    boolean isScreenAlwaysOn() {
        return mScreenAlwaysOn;
    }

    void setScreenAlwaysOn(Context context, boolean newValue) {
        mScreenAlwaysOn = newValue;
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(SCREEN_ALWAYS_ON_KEY, newValue).apply();
    }

    static void storeCurrentSession(Context context, TerminalSession session) {
        PreferenceManager.getDefaultSharedPreferences(context).edit().putString(TermuxPreferences.CURRENT_SESSION_KEY, session.mHandle).apply();
    }

    static int getCurrentSessionIndex(TermuxActivity context) {
        String sessionHandle = PreferenceManager.getDefaultSharedPreferences(context).getString(TermuxPreferences.CURRENT_SESSION_KEY, "");
        for (int i = 0, len = context.mTermService.getSessions().size(); i < len; i++) {
            TerminalSession session = context.mTermService.getSessions().get(i);
            if (session.mHandle.equals(sessionHandle)) return i;
        }
        return -1;
    }

    private void loadProfile(Context context, String profileId, Properties props) {
        TermuxProfile profile = termuxProfiles.computeIfAbsent(profileId, (key) ->
            new TermuxProfile());

        profile.id = profileId;
        profile.displayName = props.getProperty("profile-display-name", profileId);

        switch (props.getProperty("bell-character", "vibrate")) {
            case "beep":
                profile.mBellBehaviour = BELL_BEEP;
                break;
            case "ignore":
                profile.mBellBehaviour = BELL_IGNORE;
                break;
            default: // "vibrate".
                profile.mBellBehaviour = BELL_VIBRATE;
                break;
        }

        profile.mUseDarkUI = props.getProperty("use-black-ui", "false");

        try {
            JSONArray arr = new JSONArray(props.getProperty("extra-keys", "[['ESC', 'TAB', 'CTRL', 'ALT', '-', 'DOWN', 'UP']]"));

            profile.mExtraKeys = new String[arr.length()][];
            for (int i = 0; i < arr.length(); i++) {
                JSONArray line = arr.getJSONArray(i);
                profile.mExtraKeys[i] = new String[line.length()];
                for (int j = 0; j < line.length(); j++) {
                    profile.mExtraKeys[i][j] = line.getString(j);
                }
            }
        } catch (JSONException e) {
            Toast.makeText(context, "Could not load the extra-keys property from the config: " + e.toString(), Toast.LENGTH_LONG).show();
            Log.e("termux", "Error loading props", e);
            profile.mExtraKeys = new String[0][];
        }

        profile.mBackIsEscape = "escape".equals(props.getProperty("back-key", "back"));
        profile.mDisableVolumeVirtualKeys = "volume".equals(props.getProperty("volume-keys", "virtual"));

        profile.mUseCurrentSessionCwd = "current".equals(props.getProperty("session.cwd-on-create", "default"));

        profile.shortcuts.clear();
        parseAction("shortcut.create-session", SHORTCUT_ACTION_CREATE_SESSION, props, profile);
        parseAction("shortcut.next-session", SHORTCUT_ACTION_NEXT_SESSION, props, profile);
        parseAction("shortcut.previous-session", SHORTCUT_ACTION_PREVIOUS_SESSION, props, profile);
        parseAction("shortcut.rename-session", SHORTCUT_ACTION_RENAME_SESSION, props, profile);
    }

    private void loadProfile(Context context, String profileId, File propsFile) {
        Properties props = new Properties();
        try {
            if (propsFile.isFile() && propsFile.canRead()) {
                try (FileInputStream in = new FileInputStream(propsFile)) {
                    props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            Toast.makeText(context, "Could not open properties file " + propsFile.getName() + ".", Toast.LENGTH_LONG).show();
            Log.e("termux", "Error loading props", e);
        }
        loadProfile(context, profileId, props);
    }

    private static String getFileNameWithoutExtension(File profileFile) {
        final String fileName = profileFile.getName();
        final int lastDotIndex = fileName.lastIndexOf('.');

        if (lastDotIndex > 0) {
            return fileName.substring(0, lastDotIndex);
        }
        return null;
    }

    int getProfileCount() {
        return termuxProfiles.size();
    }

    TermuxProfile getProfileById(String id) {
        return termuxProfiles.get(id);
    }

    void reloadFromProperties(Context context) {
        File defaultPropsFile = new File(TERMUX_CONF_DIR, "termux.properties");
        if (!defaultPropsFile.exists())
            defaultPropsFile = new File(TERMUX_XDG_CONF_DIR, "termux.properties");

        termuxProfiles.clear();
        loadProfile(context, DEFAULT_PROFILE_ID, defaultPropsFile);
        try {
            File[] profileFiles = new File(TERMUX_CONF_PROFILES_DIR).listFiles(
                PROFILE_FILE_FILTER);

            if (profileFiles == null || profileFiles.length == 0) {
                profileFiles = new File(TERMUX_XDG_CONF_PROFILES_DIR).listFiles(
                    PROFILE_FILE_FILTER);
            }
            if (profileFiles != null) {
                for (File propsFile : profileFiles) {
                    final String profileId = getFileNameWithoutExtension(propsFile);

                    if (profileId != null)
                        loadProfile(context, profileId, propsFile);
                }
            }
        } catch (SecurityException e) {
            Toast.makeText(context, "Could not list profiles.", Toast.LENGTH_LONG).show();
            Log.e("termux", "Error listing profiles", e);
        }
    }

    private void parseAction(String name, int shortcutAction, Properties props, TermuxProfile profile) {
        String value = props.getProperty(name);
        if (value == null) return;
        String[] parts = value.toLowerCase().trim().split("\\+");
        String input = parts.length == 2 ? parts[1].trim() : null;
        if (!(parts.length == 2 && parts[0].trim().equals("ctrl")) || input.isEmpty() || input.length() > 2) {
            Log.e("termux", "Keyboard shortcut '" + name + "' is not Ctrl+<something>");
            return;
        }

        char c = input.charAt(0);
        int codePoint = c;
        if (Character.isLowSurrogate(c)) {
            if (input.length() != 2 || Character.isHighSurrogate(input.charAt(1))) {
                Log.e("termux", "Keyboard shortcut '" + name + "' is not Ctrl+<something>");
                return;
            } else {
                codePoint = Character.toCodePoint(input.charAt(1), c);
            }
        }
        profile.shortcuts.add(new KeyboardShortcut(codePoint, shortcutAction));
    }
}
