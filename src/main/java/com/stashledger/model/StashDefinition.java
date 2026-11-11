package com.stashledger.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Immutable catalog data, safe to share between the client and Swing threads. */
public final class StashDefinition
{
    private final String id;
    private final int objectId;
    private final String name;
    private final StashTier tier;
    private final int x;
    private final int y;
    private final int plane;
    private final List<String> items;

    public StashDefinition(String id, int objectId, String name, StashTier tier,
                           int x, int y, int plane, List<String> items)
    {
        this.id = Objects.requireNonNull(id);
        this.objectId = objectId;
        this.name = Objects.requireNonNull(name);
        this.tier = Objects.requireNonNull(tier);
        this.x = x;
        this.y = y;
        this.plane = plane;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
    }

    public String getId() { return id; }
    public int getObjectId() { return objectId; }
    public String getName() { return name; }
    public StashTier getTier() { return tier; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getPlane() { return plane; }
    public List<String> getItems() { return items; }
}
