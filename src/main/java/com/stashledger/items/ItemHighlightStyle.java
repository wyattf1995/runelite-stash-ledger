package com.stashledger.items;

/** Visual treatment for planner candidates in item widgets. */
public enum ItemHighlightStyle
{
    OUTLINE("Clear outline"),
    DOT("Gold dot");

    private final String label;

    ItemHighlightStyle(String label)
    {
        this.label = label;
    }

    @Override
    public String toString()
    {
        return label;
    }
}
