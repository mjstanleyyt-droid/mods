package dev.restock;

import static org.junit.jupiter.api.Assertions.*;

import dev.restock.plan.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ShulkerQuickMoveTest {
  private List<Planner.Stack> empty() {
    return new ArrayList<>(Collections.nCopies(63, null));
  }

  private Planner.Stack item(String key, int count) {
    return new Planner.Stack(key, count, 64);
  }

  private int[] limits() {
    int[] r = new int[63];
    Arrays.fill(r, 64);
    return r;
  }

  private boolean[] allowed() {
    boolean[] r = new boolean[63];
    Arrays.fill(r, true);
    return r;
  }

  private Optional<ShulkerQuickMove.Result> certify(
      List<Planner.Stack> s, List<Planner.Move> moves) {
    return ShulkerQuickMove.certify(s, moves, 0, limits(), allowed(), true);
  }

  @Test
  void fullStackIntoVanillaDestinationIsOneAction() {
    var s = empty();
    s.set(0, item("a", 64));
    var r = certify(s, List.of(new Planner.Move(0, 62, 64, false))).orElseThrow();
    assertEquals(1, r.consumedMoves());
    assertNull(r.after().get(0));
    assertEquals(item("a", 64), r.after().get(62));
  }

  @Test
  void wrongDestinationFallsBackWithoutMovingAnything() {
    var s = empty();
    s.set(0, item("a", 64));
    var copy = new ArrayList<>(s);
    assertTrue(certify(s, List.of(new Planner.Move(0, 27, 64, false))).isEmpty());
    assertEquals(copy, s);
  }

  @Test
  void partialKitCannotTakeWholeSource() {
    var s = empty();
    s.set(0, item("a", 64));
    assertTrue(certify(s, List.of(new Planner.Move(0, 62, 16, false))).isEmpty());
  }

  @Test
  void unconfiguredMatchingStackCannotBeFilled() {
    var s = empty();
    s.set(0, item("a", 64));
    s.set(62, item("a", 32));
    assertTrue(certify(s, List.of(new Planner.Move(0, 61, 64, false))).isEmpty());
  }

  @Test
  void distinctComponentsDoNotMerge() {
    var s = empty();
    s.set(0, item("enchanted", 32));
    s.set(62, item("plain", 32));
    var r = certify(s, List.of(new Planner.Move(0, 61, 32, false))).orElseThrow();
    assertEquals(item("plain", 32), r.after().get(62));
    assertEquals(item("enchanted", 32), r.after().get(61));
  }

  @Test
  void fullInventoryCanAcceptOnlyTheExactDeficit() {
    var s = empty();
    for (int i = 27; i < 63; i++) s.set(i, item("unrelated", 64));
    s.set(0, item("a", 64));
    s.set(62, item("a", 60));
    var r = certify(s, List.of(new Planner.Move(0, 62, 4, false))).orElseThrow();
    assertEquals(item("a", 60), r.after().get(0));
    assertEquals(item("a", 64), r.after().get(62));
  }

  @Test
  void twoKitRefillsCanShareOneShiftClick() {
    var s = empty();
    s.set(0, item("a", 64));
    s.set(62, item("a", 32));
    s.set(61, item("a", 32));
    var r =
        certify(s, List.of(new Planner.Move(0, 61, 32, false), new Planner.Move(0, 62, 32, false)))
            .orElseThrow();
    assertEquals(2, r.consumedMoves());
    assertEquals(item("a", 64), r.after().get(61));
    assertEquals(item("a", 64), r.after().get(62));
    assertNull(r.after().get(0));
  }

  @Test
  void swapsAndPlayerSourcesAreNotShiftClickOptimized() {
    var s = empty();
    s.set(0, item("a", 64));
    s.set(27, item("b", 64));
    assertTrue(certify(s, List.of(new Planner.Move(0, 27, 64, true))).isEmpty());
    assertTrue(certify(s, List.of(new Planner.Move(27, 62, 64, false))).isEmpty());
  }

  @Test
  void incompatibleLayoutAndNoOpAreRejected() {
    assertTrue(
        ShulkerQuickMove.certify(List.of(), List.of(), 0, new int[0], new boolean[0], true)
            .isEmpty());
    var s = empty();
    s.set(0, item("a", 64));
    for (int i = 27; i < 63; i++) s.set(i, item("b", 64));
    assertTrue(certify(s, List.of(new Planner.Move(0, 62, 1, false))).isEmpty());
  }

  @Test
  void nonStackableItemsSkipMerging() {
    var s = empty();
    s.set(0, item("damaged", 1));
    s.set(62, item("damaged", 1));
    var r =
        ShulkerQuickMove.certify(
                s, List.of(new Planner.Move(0, 61, 1, false)), 0, limits(), allowed(), false)
            .orElseThrow();
    assertEquals(item("damaged", 1), r.after().get(62));
  }

  @Test
  void reversedIndependentMovesReduceAnEmptyInventoryFixtureFrom45ActionsTo27() {
    var s = empty();
    List<Planner.Move> moves = new ArrayList<>();
    for (int i = 0; i < 27; i++) {
      s.set(i, item("item" + i, 64));
      moves.add(new Planner.Move(i, 36 + i, 64, false));
    }
    var ordered = MoveOrder.prioritize(moves, m -> -m.to());
    int actions = 0;
    for (int at = 0; at < ordered.size(); ) {
      var r = ShulkerQuickMove.certify(s, ordered, at, limits(), allowed(), true).orElseThrow();
      s = new ArrayList<>(r.after());
      at += r.consumedMoves();
      actions++;
    }
    assertEquals(27, actions);
    for (int i = 0; i < 27; i++) assertNull(s.get(i));
  }

  @Test
  void randomizedCertificatesAgreeWithVanillaReference() {
    Random random = new Random(815);
    int accepted = 0;
    for (int trial = 0; trial < 2000; trial++) {
      var before = empty();
      before.set(0, item("moving", 1 + random.nextInt(64)));
      for (int i = 27; i < 63; i++)
        if (random.nextInt(4) != 0)
          before.set(
              i, item(random.nextBoolean() ? "moving" : "other" + i, 1 + random.nextInt(64)));
      boolean stackable = random.nextBoolean();
      var expected = reference(before, stackable);
      List<Planner.Move> goals = new ArrayList<>();
      for (int i = 27; i < 63; i++) {
        var old = before.get(i);
        var now = expected.get(i);
        int delta = (now == null ? 0 : now.count()) - (old == null ? 0 : old.count());
        if (delta > 0) goals.add(new Planner.Move(0, i, delta, false));
      }
      Collections.shuffle(goals, random);
      var result = ShulkerQuickMove.certify(before, goals, 0, limits(), allowed(), stackable);
      if (goals.isEmpty()) {
        assertTrue(result.isEmpty());
        continue;
      }
      assertEquals(expected, result.orElseThrow().after());
      assertEquals(goals.size(), result.get().consumedMoves());
      accepted++;
      var reduced = new ArrayList<>(goals);
      var last = reduced.getLast();
      if (last.count() > 1) {
        reduced.set(reduced.size() - 1, new Planner.Move(0, last.to(), last.count() - 1, false));
        assertTrue(
            ShulkerQuickMove.certify(before, reduced, 0, limits(), allowed(), stackable).isEmpty());
      }
    }
    assertTrue(accepted > 1000);
  }

  // Independent count-array interpreter of repeated vanilla quickMoveStack calls.
  private List<Planner.Stack> reference(List<Planner.Stack> before, boolean stackable) {
    int[] counts = new int[63];
    String[] keys = new String[63];
    for (int i = 0; i < 63; i++)
      if (before.get(i) != null) {
        counts[i] = before.get(i).count();
        keys[i] = before.get(i).key();
      }
    do {
      int initial = counts[0];
      if (stackable)
        for (int i = 62; i >= 27; i--)
          if ("moving".equals(keys[i])) {
            int n = Math.min(counts[0], 64 - counts[i]);
            counts[i] += n;
            counts[0] -= n;
          }
      if (counts[0] > 0)
        for (int i = 62; i >= 27; i--)
          if (counts[i] == 0) {
            counts[i] = counts[0];
            counts[0] = 0;
            keys[i] = "moving";
            break;
          }
      if (counts[0] == 0 || counts[0] == initial) break;
    } while (true);
    var result = empty();
    for (int i = 0; i < 63; i++) if (counts[i] > 0) result.set(i, item(keys[i], counts[i]));
    assertEquals(
        before.stream().filter(Objects::nonNull).mapToInt(Planner.Stack::count).sum(),
        result.stream().filter(Objects::nonNull).mapToInt(Planner.Stack::count).sum());
    return result;
  }
}
