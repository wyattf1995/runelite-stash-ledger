package com.stashledger.items;

import com.stashledger.model.BuildStatus;
import com.stashledger.model.ContentsStatus;
import com.stashledger.model.StashEntry;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import net.runelite.client.game.ItemManager;

/**
 * Creates a thread-safe snapshot of source-declared equipment candidates for
 * every STASH that is not already built and filled. This is intentionally a
 * planner marker, never an item-count or equipment-fulfillment evaluator.
 */
public final class NeededItemProvider implements NeededItemLookup
{
    private final IntUnaryOperator canonicalize;
    private final Map<String, Set<Integer>> requirementIdsByStash;
    /** One volatile reference prevents a render from mixing eligibility and hover detail epochs. */
    private volatile Map<Integer, NeededItemDetail> detailsByItemId = Map.of();

    public NeededItemProvider(ItemManager itemManager, Map<String, Set<Integer>> requirementIdsByStash)
    {
        this(itemManager::canonicalize, requirementIdsByStash);
    }

    NeededItemProvider(IntUnaryOperator canonicalize, Map<String, Set<Integer>> requirementIdsByStash)
    {
        this.canonicalize = canonicalize;
        Map<String, Set<Integer>> copy = new HashMap<>();
        for (Map.Entry<String, Set<Integer>> entry : requirementIdsByStash.entrySet())
        {
            copy.put(entry.getKey(), Set.copyOf(entry.getValue()));
        }
        this.requirementIdsByStash = Map.copyOf(copy);
    }

    /** Replaces the snapshot atomically; callers may supply any account's full ledger snapshot. */
    public void refresh(List<StashEntry> entries)
    {
        refresh(entries, null);
    }

    /**
     * Replaces the snapshot atomically. A focus ID intentionally narrows the
     * plan to exactly one built, confirmed-empty unit; an invalid focus means
     * no candidates rather than an ambiguous return to the global plan.
     */
    public void refresh(List<StashEntry> entries, String fillStashId)
    {
        Map<Integer, Map<String, StashEntry>> entriesByItemId = new HashMap<>();
        boolean focused = fillStashId != null;
        if (entries != null)
        {
            for (StashEntry entry : entries)
            {
                if (entry == null || (focused ? !isFillableFocus(entry, fillStashId) : isComplete(entry)))
                {
                    continue;
                }
                for (int requirementId : requirementIdsByStash.getOrDefault(entry.getDefinition().getId(), Set.of()))
                {
                    int canonicalId = canonicalize.applyAsInt(requirementId);
                    entriesByItemId.computeIfAbsent(canonicalId, unused -> new LinkedHashMap<>())
                        .put(entry.getDefinition().getId(), entry);
                }
            }
        }
        Map<Integer, NeededItemDetail> next = new HashMap<>();
        for (Map.Entry<Integer, Map<String, StashEntry>> entry : entriesByItemId.entrySet())
        {
            next.put(entry.getKey(), new NeededItemDetail(entry.getKey(), List.copyOf(entry.getValue().values()), focused));
        }
        detailsByItemId = Map.copyOf(next);
    }

    public void clear()
    {
        detailsByItemId = Map.of();
    }

    @Override
    public boolean isNeeded(int itemId)
    {
        Map<Integer, NeededItemDetail> snapshot = detailsByItemId;
        return !snapshot.isEmpty() && snapshot.containsKey(canonicalize.applyAsInt(itemId));
    }

    @Override
    public Optional<NeededItemDetail> detailFor(int itemId)
    {
        Map<Integer, NeededItemDetail> snapshot = detailsByItemId;
        if (snapshot.isEmpty())
        {
            return Optional.empty();
        }
        int canonicalId = canonicalize.applyAsInt(itemId);
        return Optional.ofNullable(snapshot.get(canonicalId));
    }

    private static boolean isComplete(StashEntry entry)
    {
        return entry.getBuildStatus() == BuildStatus.BUILT
            && entry.getContentsStatus() == ContentsStatus.FILLED;
    }

    private static boolean isFillableFocus(StashEntry entry, String fillStashId)
    {
        return fillStashId.equals(entry.getDefinition().getId())
            && entry.getBuildStatus() == BuildStatus.BUILT
            && entry.getContentsStatus() == ContentsStatus.EMPTY;
    }
}
