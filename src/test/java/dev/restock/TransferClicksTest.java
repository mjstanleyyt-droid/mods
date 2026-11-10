package dev.restock;

import static org.junit.jupiter.api.Assertions.*;

import dev.restock.plan.TransferClicks;
import java.util.*;
import org.junit.jupiter.api.Test;

class TransferClicksTest {
  private void verify(
      int source,
      int destination,
      int amount,
      int sourceLimit,
      int destinationLimit,
      int cursorLimit) {
    var steps =
        TransferClicks.plan(
            source, destination, amount, sourceLimit, destinationLimit, cursorLimit);
    int[] slots = {source, destination};
    int cursor = 0;
    for (var step : steps) {
      int slot = step.source() ? 0 : 1;
      int limit = slot == 0 ? sourceLimit : destinationLimit;
      if (cursor == 0) {
        int take = step.button() == 0 ? slots[slot] : (slots[slot] + 1) / 2;
        assertTrue(take > 0);
        slots[slot] -= take;
        cursor = take;
      } else {
        int put = Math.min(step.button() == 0 ? cursor : 1, limit - slots[slot]);
        assertTrue(put > 0);
        slots[slot] += put;
        cursor -= put;
      }
      assertEquals(source + destination, slots[0] + slots[1] + cursor);
      assertTrue(slots[0] >= 0 && slots[0] <= sourceLimit);
      assertTrue(slots[1] >= 0 && slots[1] <= destinationLimit);
      assertTrue(cursor >= 0 && cursor <= cursorLimit);
    }
    assertArrayEquals(new int[] {source - amount, destination + amount}, slots);
    assertEquals(0, cursor);
    assertTrue(steps.size() <= amount + 2, "Never slower than old unit-deposit sequence");
  }

  @Test
  void halfStackTakesTwoClicks() {
    assertEquals(2, TransferClicks.plan(64, 0, 32, 64, 64, 64).size());
    verify(64, 0, 32, 64, 64, 64);
  }

  @Test
  void quarterStackTakesFourClicks() {
    assertEquals(4, TransferClicks.plan(64, 0, 16, 64, 64, 64).size());
    verify(64, 0, 16, 64, 64, 64);
  }

  @Test
  void nearlyWholeStackReturnsOneRemainder() {
    assertEquals(3, TransferClicks.plan(64, 0, 63, 64, 64, 64).size());
    verify(64, 0, 63, 64, 64, 64);
  }

  @Test
  void existingStackNeedsOnlyDeficit() {
    verify(64, 12, 4, 64, 64, 64);
  }

  @Test
  void oddStackAndPartialDestination() {
    verify(33, 7, 13, 64, 64, 64);
  }

  @Test
  void unequalSlotLimits() {
    verify(64, 2, 14, 64, 16, 64);
  }

  @Test
  void allSmallInventoryTransfersConserveItems() {
    for (int source = 1; source <= 8; source++)
      for (int dest = 0; dest <= 8; dest++)
        for (int amount = 1; amount <= Math.min(source, 8 - dest); amount++)
          verify(source, dest, amount, 8, 8, 8);
  }

  @Test
  void randomizedVanillaStackTransfers() {
    Random random = new Random(813);
    for (int trial = 0; trial < 250; trial++) {
      int source = random.nextInt(64) + 1, dest = random.nextInt(64);
      int amount = random.nextInt(Math.min(source, 64 - dest)) + 1;
      verify(source, dest, amount, 64, 64, 64);
    }
  }

  @Test
  void invalidTransfersRefused() {
    assertThrows(IllegalArgumentException.class, () -> TransferClicks.plan(64, 63, 2, 64, 64, 64));
    assertThrows(IllegalArgumentException.class, () -> TransferClicks.plan(0, 0, 1, 64, 64, 64));
    assertThrows(IllegalArgumentException.class, () -> TransferClicks.plan(4, 0, 5, 64, 64, 64));
  }

  @Test
  void oversizedStacksUseBoundedFallback() {
    verify(20000, 0, 19999, 20000, 20000, 20000);
  }
}
