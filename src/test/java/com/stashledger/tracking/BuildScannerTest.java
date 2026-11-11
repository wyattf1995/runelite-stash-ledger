package com.stashledger.tracking;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stashledger.model.BuildStatus;
import com.stashledger.model.StashDefinition;
import com.stashledger.model.StashTier;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ScriptID;
import org.junit.Test;

public class BuildScannerTest
{
    @Test
    public void usesTheWatsonScriptAndTheStackTopForEveryDefinition()
    {
        Client client = mock(Client.class);
        int[] stack = {0, 99};
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getIntStack()).thenReturn(stack);
        doAnswer(invocation ->
        {
            stack[0] = ((int) invocation.getArguments()[1]) == 11 ? 1 : 0;
            return null;
        }).when(client).runScript(eq(ScriptID.WATSON_STASH_UNIT_CHECK), anyInt(), eq(0), eq(0), eq(0));

        List<StashDefinition> definitions = Arrays.asList(definition("one", 11), definition("two", 12));
        Map<String, BuildStatus> result = BuildScanner.scan(client, definitions);

        assertEquals(BuildStatus.BUILT, result.get("one"));
        assertEquals(BuildStatus.NOT_BUILT, result.get("two"));
        verify(client).runScript(ScriptID.WATSON_STASH_UNIT_CHECK, 11, 0, 0, 0);
        verify(client).runScript(ScriptID.WATSON_STASH_UNIT_CHECK, 12, 0, 0, 0);
    }

    @Test
    public void marksUnexpectedScriptValuesUnknown()
    {
        Client client = mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getIntStack()).thenReturn(new int[]{2});

        assertEquals(BuildStatus.UNKNOWN, BuildScanner.scan(client, Collections.singletonList(definition("one", 11))).get("one"));
    }

    @Test
    public void leavesEveryResultUnknownOutsideLoggedInState()
    {
        Client client = mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);

        Map<String, BuildStatus> result = BuildScanner.scan(client, Collections.singletonList(definition("one", 11)));

        assertEquals(BuildStatus.UNKNOWN, result.get("one"));
        verify(client, never()).runScript(eq(ScriptID.WATSON_STASH_UNIT_CHECK), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test
    public void letsScriptFailuresEscapeBeforeReturningAnySnapshot()
    {
        Client client = mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        doAnswer(invocation -> { throw new IllegalStateException("script failed"); })
            .when(client).runScript(eq(ScriptID.WATSON_STASH_UNIT_CHECK), anyInt(), eq(0), eq(0), eq(0));

        assertThrows(IllegalStateException.class,
            () -> BuildScanner.scan(client, Collections.singletonList(definition("one", 11))));
    }

    private static StashDefinition definition(String id, int objectId)
    {
        return new StashDefinition(id, objectId, id, StashTier.BEGINNER, 0, 0, 0, Collections.emptyList());
    }
}
