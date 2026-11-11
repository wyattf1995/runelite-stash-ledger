package com.stashledger.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.runelite.api.coords.WorldPoint;

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
    private final List<WorldPoint> worldPoints;
    private final List<String> items;

    public StashDefinition(String id, int objectId, String name, StashTier tier,
                           int x, int y, int plane, List<String> items)
    {
        this(id, objectId, name, tier, List.of(new WorldPoint(x, y, plane)), items);
    }

    public StashDefinition(String id, int objectId, String name, StashTier tier,
                           List<WorldPoint> worldPoints, List<String> items)
    {
        this.id = Objects.requireNonNull(id);
        this.objectId = objectId;
        this.name = Objects.requireNonNull(name);
        this.tier = Objects.requireNonNull(tier);
        this.worldPoints = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(worldPoints)));
        if (this.worldPoints.isEmpty())
        {
            throw new IllegalArgumentException("A STASH definition must have at least one world point");
        }
        WorldPoint primary = this.worldPoints.get(0);
        this.x = primary.getX();
        this.y = primary.getY();
        this.plane = primary.getPlane();
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
    }

    public String getId() { return id; }
    public int getObjectId() { return objectId; }
    public String getName() { return name; }
    public StashTier getTier() { return tier; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getPlane() { return plane; }
    public List<WorldPoint> getWorldPoints() { return worldPoints; }
    public List<String> getItems() { return items; }
}
