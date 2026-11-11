package com.stashledger;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Development launcher; normal Plugin Hub installation does not use this class. */
public final class StashLedgerLauncher
{
    private StashLedgerLauncher() { }

    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(StashLedgerPlugin.class);
        RuneLite.main(args);
    }
}
