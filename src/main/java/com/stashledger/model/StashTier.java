package com.stashledger.model;

public enum StashTier
{
    BEGINNER("Beginner", 12, "2 planks, 10 nails"),
    EASY("Easy", 27, "2 planks, 10 nails"),
    MEDIUM("Medium", 42, "2 oak planks, 10 nails"),
    HARD("Hard", 55, "2 teak planks, 10 nails"),
    ELITE("Elite", 77, "2 mahogany planks, 10 nails"),
    MASTER("Master", 88, "2 mahogany planks, 1 gold leaf, 10 nails");

    private final String label;
    private final int constructionLevel;
    private final String materials;

    StashTier(String label, int constructionLevel, String materials)
    {
        this.label = label;
        this.constructionLevel = constructionLevel;
        this.materials = materials;
    }

    public String getLabel() { return label; }
    public int getConstructionLevel() { return constructionLevel; }
    public String getMaterials() { return materials; }
    @Override public String toString() { return label; }
}
