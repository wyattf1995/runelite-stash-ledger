package com.stashledger.tracking;

import com.stashledger.model.BuildStatus;
import com.stashledger.model.StashDefinition;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;

/**
 * Reads the client-owned construction state for the supplied STASH catalog.
 *
 * <p>The caller must invoke this on RuneLite's client thread. This class follows
 * the public Watson script contract rather than inferring state from objects or
 * chat messages. A script failure deliberately escapes, so callers retain their
 * last verified snapshot instead of replacing it with guesses.</p>
 */
public final class BuildScanner
{
    private BuildScanner()
    {
    }

    public static Map<String, BuildStatus> scan(Client client, List<StashDefinition> definitions)
    {
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(definitions, "definitions");

        Map<String, BuildStatus> result = new LinkedHashMap<>();
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            for (StashDefinition definition : definitions)
            {
                result.put(definition.getId(), BuildStatus.UNKNOWN);
            }
            return Collections.unmodifiableMap(result);
        }

        for (StashDefinition definition : definitions)
        {
            client.runScript(ScriptID.WATSON_STASH_UNIT_CHECK, definition.getObjectId(), 0, 0, 0);
            int[] intStack = client.getIntStack();
            BuildStatus status = intStack != null && intStack.length > 0
                ? toBuildStatus(intStack[0])
                : BuildStatus.UNKNOWN;
            result.put(definition.getId(), status);
        }
        return Collections.unmodifiableMap(result);
    }

    private static BuildStatus toBuildStatus(int scriptValue)
    {
        if (scriptValue == 0)
        {
            return BuildStatus.NOT_BUILT;
        }
        if (scriptValue == 1)
        {
            return BuildStatus.BUILT;
        }
        return BuildStatus.UNKNOWN;
    }
}
