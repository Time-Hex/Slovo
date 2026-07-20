package com.timehex.slovo;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class LibraryStore {
    enum Kind { TEXT, PDF, AUDIO }

    static final class Item {
        String id = UUID.randomUUID().toString();
        String title = "Без названия";
        Kind kind = Kind.TEXT;
        final ArrayList<String> uris = new ArrayList<>();
        final LinkedHashSet<String> shelves = new LinkedHashSet<>();
        int progress = 0;
        long positionMs = 0;
        long durationMs = 0;

        JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id);
                o.put("title", title);
                o.put("kind", kind.name());
                o.put("uris", new JSONArray(uris));
                o.put("shelves", new JSONArray(shelves));
                o.put("progress", progress);
                o.put("positionMs", positionMs);
                o.put("durationMs", durationMs);
            } catch (Exception ignored) { }
            return o;
        }

        static Item fromJson(JSONObject o) {
            Item item = new Item();
            item.id = o.optString("id", item.id);
            item.title = o.optString("title", "Без названия");
            try { item.kind = Kind.valueOf(o.optString("kind", "TEXT")); }
            catch (Exception ignored) { }
            JSONArray u = o.optJSONArray("uris");
            if (u != null) for (int i = 0; i < u.length(); i++) item.uris.add(u.optString(i));
            JSONArray s = o.optJSONArray("shelves");
            if (s != null) for (int i = 0; i < s.length(); i++) item.shelves.add(s.optString(i));
            item.progress = o.optInt("progress", 0);
            item.positionMs = o.optLong("positionMs", 0);
            item.durationMs = o.optLong("durationMs", 0);
            return item;
        }
    }

    private final SharedPreferences prefs;
    private final ArrayList<Item> items = new ArrayList<>();
    private final LinkedHashSet<String> shelves = new LinkedHashSet<>();

    LibraryStore(Context context) {
        prefs = context.getSharedPreferences("slovo_library", Context.MODE_PRIVATE);
        shelves.addAll(Arrays.asList("Фэнтези", "Мистика", "Детективы", "Фантастика", "Классика", "Нон-фикшн"));
        load();
    }

    List<Item> items() { return items; }
    Set<String> shelves() { return shelves; }

    void add(Item item) {
        for (Item old : items) {
            if (!old.uris.isEmpty() && !item.uris.isEmpty() && old.uris.get(0).equals(item.uris.get(0))) return;
        }
        items.add(item);
        save();
    }

    void remove(Item item) { items.remove(item); save(); }

    void addShelf(String name) {
        String clean = name == null ? "" : name.trim();
        if (!clean.isEmpty()) { shelves.add(clean); save(); }
    }

    void removeShelf(String name) {
        shelves.remove(name);
        for (Item item : items) item.shelves.remove(name);
        save();
    }

    void save() {
        JSONArray data = new JSONArray();
        for (Item item : items) data.put(item.toJson());
        prefs.edit()
                .putString("items", data.toString())
                .putStringSet("shelves", new LinkedHashSet<>(shelves))
                .apply();
    }

    private void load() {
        Set<String> savedShelves = prefs.getStringSet("shelves", null);
        if (savedShelves != null) {
            shelves.clear();
            shelves.addAll(savedShelves);
        }
        try {
            JSONArray data = new JSONArray(prefs.getString("items", "[]"));
            for (int i = 0; i < data.length(); i++) items.add(Item.fromJson(data.getJSONObject(i)));
        } catch (Exception ignored) { }
    }

    static Kind kindFor(String name) {
        String n = name == null ? "" : name.toLowerCase();
        if (n.endsWith(".pdf")) return Kind.PDF;
        if (n.endsWith(".mp3") || n.endsWith(".m4b") || n.endsWith(".m4a") || n.endsWith(".aac") || n.endsWith(".ogg") || n.endsWith(".wav")) return Kind.AUDIO;
        return Kind.TEXT;
    }

    static String titleFor(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null) return "Без названия";
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf(':'));
        if (slash >= 0) name = name.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}

