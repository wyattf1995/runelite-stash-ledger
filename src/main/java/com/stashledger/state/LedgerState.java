package com.stashledger.state;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stashledger.model.BuildStatus;
import com.stashledger.model.ContentsStatus;
import com.stashledger.model.StashDefinition;
import com.stashledger.model.StashEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One profile's state, confined to the client thread. Only confirmed contents and pins persist. */
public final class LedgerState
{
    private final List<StashDefinition> definitions;
    private final Map<String, BuildStatus> builds = new HashMap<>();
    private final Map<String, ContentsStatus> contents = new HashMap<>();
    private final Map<String, Boolean> pins = new HashMap<>();

    public LedgerState(List<StashDefinition> definitions)
    {
        this.definitions = Collections.unmodifiableList(new ArrayList<>(definitions));
        definitions.forEach(d -> builds.put(d.getId(), BuildStatus.UNKNOWN));
    }

    /** Strict transactional parsing prevents a malformed save from partially replacing state. */
    public void load(String json)
    {
        Map<String, ContentsStatus> loadedContents = new HashMap<>();
        Map<String, Boolean> loadedPins = new HashMap<>();
        if (json != null && !json.trim().isEmpty())
        {
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            if (!root.has("version") || root.get("version").getAsInt() != 1 || !root.has("units"))
            {
                throw new IllegalArgumentException("Unsupported STASH Ledger save format");
            }
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("units").entrySet())
            {
                if (!builds.containsKey(entry.getKey())) { continue; }
                JsonObject value = entry.getValue().getAsJsonObject();
                if (value.has("contents"))
                {
                    loadedContents.put(entry.getKey(), ContentsStatus.valueOf(value.get("contents").getAsString()));
                }
                if (value.has("pinned"))
                {
                    if (!value.get("pinned").isJsonPrimitive() || !value.getAsJsonPrimitive("pinned").isBoolean())
                    {
                        throw new IllegalArgumentException("Invalid shortlist state");
                    }
                    loadedPins.put(entry.getKey(), value.get("pinned").getAsBoolean());
                }
            }
        }
        contents.clear(); contents.putAll(loadedContents);
        pins.clear(); pins.putAll(loadedPins);
        builds.replaceAll((id, status) -> BuildStatus.UNKNOWN);
    }

    public String serialize()
    {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject units = new JsonObject();
        for (StashDefinition definition : definitions)
        {
            String id = definition.getId();
            ContentsStatus content = contents.getOrDefault(id, ContentsStatus.UNKNOWN);
            boolean pinned = pins.getOrDefault(id, false);
            if (content == ContentsStatus.UNKNOWN && !pinned) { continue; }
            JsonObject value = new JsonObject();
            value.addProperty("contents", content.name());
            value.addProperty("pinned", pinned);
            units.add(id, value);
        }
        root.add("units", units);
        return root.toString();
    }

    public void applyBuildScan(Map<String, BuildStatus> scan)
    {
        for (StashDefinition d : definitions)
        {
            BuildStatus status = scan.getOrDefault(d.getId(), BuildStatus.UNKNOWN);
            builds.put(d.getId(), status);
            if (status == BuildStatus.NOT_BUILT) { contents.remove(d.getId()); }
        }
    }

    public boolean markContents(String id, ContentsStatus status)
    {
        if (status == null || builds.get(id) != BuildStatus.BUILT) { return false; }
        contents.put(id, status);
        return true;
    }

    public boolean togglePinned(String id)
    {
        if (!builds.containsKey(id)) { return false; }
        pins.put(id, !pins.getOrDefault(id, false));
        return true;
    }

    public List<StashEntry> snapshot()
    {
        List<StashEntry> result = new ArrayList<>();
        for (StashDefinition d : definitions)
        {
            result.add(new StashEntry(d, builds.get(d.getId()),
                contents.getOrDefault(d.getId(), ContentsStatus.UNKNOWN), pins.getOrDefault(d.getId(), false)));
        }
        return Collections.unmodifiableList(result);
    }

    public Map<String, BuildStatus> buildSnapshot()
    {
        return Collections.unmodifiableMap(new LinkedHashMap<>(builds));
    }
}
