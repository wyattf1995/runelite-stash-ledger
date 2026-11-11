package com.stashledger.model;

import java.util.Objects;

/** A catalog unit plus one account's known state. Contents never imply construction. */
public final class StashEntry
{
    private final StashDefinition definition;
    private final BuildStatus buildStatus;
    private final ContentsStatus contentsStatus;
    private final boolean pinned;

    public StashEntry(StashDefinition definition, BuildStatus buildStatus,
                      ContentsStatus contentsStatus, boolean pinned)
    {
        this.definition = Objects.requireNonNull(definition);
        this.buildStatus = Objects.requireNonNull(buildStatus);
        this.contentsStatus = buildStatus == BuildStatus.BUILT
            ? Objects.requireNonNull(contentsStatus) : ContentsStatus.UNKNOWN;
        this.pinned = pinned;
    }

    public StashDefinition getDefinition() { return definition; }
    public BuildStatus getBuildStatus() { return buildStatus; }
    public ContentsStatus getContentsStatus() { return contentsStatus; }
    public boolean isPinned() { return pinned; }
}
