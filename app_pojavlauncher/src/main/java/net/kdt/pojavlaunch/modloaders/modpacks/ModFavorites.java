package net.kdt.pojavlaunch.modloaders.modpacks;

import android.content.Context;
import android.content.SharedPreferences;

import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ProjectType;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Small local store for favorited mods and modpacks. Everything is kept in a
 * private preferences file as a JSON array, so favorites survive restarts
 * without touching the network.
 */
public final class ModFavorites {
    private static final String PREFS_NAME = "rc_favorites";
    private static final String KEY_ITEMS = "items";

    private ModFavorites() {}

    public static String keyOf(ModItem item) {
        return item.apiSource + "_" + item.id;
    }

    public static List<ModItem> load(Context context) {
        ArrayList<ModItem> items = new ArrayList<>();
        String raw = prefs(context).getString(KEY_ITEMS, null);
        if (raw == null) return items;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                // Entries saved before project types existed only recorded a modpack flag
                int projectType = object.has("projectType")
                        ? object.optInt("projectType", ProjectType.MODPACK)
                        : (object.optBoolean("isModpack") ? ProjectType.MODPACK : ProjectType.MOD);
                items.add(new ModItem(
                        object.optInt("apiSource"),
                        projectType,
                        object.optString("id"),
                        object.optString("title"),
                        object.optString("description"),
                        object.optString("imageUrl"),
                        object.optLong("downloads")));
            }
        } catch (JSONException ignored) {
            // A corrupted entry simply produces an empty favorites list
        }
        return items;
    }

    public static boolean contains(Context context, ModItem item) {
        String key = keyOf(item);
        for (ModItem stored : load(context)) {
            if (key.equals(keyOf(stored))) return true;
        }
        return false;
    }

    /** Adds or removes the item and returns true when it is now a favorite */
    public static boolean toggle(Context context, ModItem item) {
        List<ModItem> items = load(context);
        String key = keyOf(item);
        boolean removed = false;
        for (int i = 0; i < items.size(); i++) {
            if (key.equals(keyOf(items.get(i)))) {
                items.remove(i);
                removed = true;
                break;
            }
        }
        if (!removed) items.add(item);
        save(context, items);
        return !removed;
    }

    private static void save(Context context, List<ModItem> items) {
        JSONArray array = new JSONArray();
        for (ModItem item : items) {
            JSONObject object = new JSONObject();
            try {
                object.put("apiSource", item.apiSource);
                object.put("projectType", item.projectType);
                object.put("isModpack", item.isModpack());
                object.put("id", item.id);
                object.put("title", item.title);
                object.put("description", item.description);
                object.put("imageUrl", item.imageUrl == null ? "" : item.imageUrl);
                object.put("downloads", item.downloads);
                array.put(object);
            } catch (JSONException ignored) {
                // Skip malformed entries instead of losing the whole list
            }
        }
        prefs(context).edit().putString(KEY_ITEMS, array.toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
