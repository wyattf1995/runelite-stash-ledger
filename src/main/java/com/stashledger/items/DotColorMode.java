package com.stashledger.items;

/** Chooses whether planner dots communicate clue tiers or use one shared color. */
public enum DotColorMode
{
    CLUE_TIER("Clue tier colors"),
    SINGLE_COLOR("Single color");

    private final String label;

    DotColorMode(String label)
    {
        this.label = label;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
