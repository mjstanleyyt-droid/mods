package dev.restock;

import static org.junit.jupiter.api.Assertions.*;

import dev.restock.plan.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class MoveOrderTest {
  @Test
  void sharedSlotDependenciesKeepTheirOrder() {
    var a = new Planner.Move(0, 30, 16, false);
    var b = new Planner.Move(0, 60, 16, false);
    var c = new Planner.Move(30, 50, 16, false);
    var independent = new Planner.Move(1, 62, 64, false);
    var r = MoveOrder.prioritize(List.of(a, b, c, independent), m -> -m.to());
    assertEquals(independent, r.getFirst());
    assertTrue(r.indexOf(a) < r.indexOf(b));
    assertTrue(r.indexOf(a) < r.indexOf(c));
  }

  @Test
  void tiesRemainStableAndEmptyPlansWork() {
    var s = List.of(new Planner.Move(0, 30, 1, false), new Planner.Move(1, 31, 1, false));
    assertEquals(s, MoveOrder.prioritize(s, m -> 0));
    assertTrue(MoveOrder.prioritize(List.of(), m -> 0).isEmpty());
  }

  @Test
  void randomizedValidPlansPreserveEveryFinalSlot() {
    Random random = new Random(816);
    int nontrivial = 0;
    for (int trial = 0; trial < 1000; trial++) {
      List<Planner.Stack> s = new ArrayList<>();
      Map<Integer, Planner.Target> targets = new HashMap<>();
      for (int i = 0; i < 63; i++) {
        s.add(
            random.nextInt(4) == 0
                ? null
                : new Planner.Stack("item" + random.nextInt(5), 1 + random.nextInt(64), 64));
        if (i >= 27 && random.nextBoolean()) {
          String key = "item" + random.nextInt(5);
          targets.put(
              i, new Planner.Target(stack -> stack.key().equals(key), 1 + random.nextInt(64)));
        }
      }
      var plan =
          Planner.plan(
              s,
              java.util.stream.IntStream.range(27, 63).boxed().toList(),
              java.util.stream.IntStream.range(0, 27).boxed().toList(),
              targets,
              true);
      var ordered = MoveOrder.prioritize(plan.moves(), m -> -m.to());
      assertEquals(apply(s, plan.moves()), apply(s, ordered));
      if (!ordered.equals(plan.moves())) nontrivial++;
    }
    assertTrue(nontrivial > 100);
  }

  private List<Planner.Stack> apply(List<Planner.Stack> before, List<Planner.Move> moves) {
    var s = new ArrayList<>(before);
    for (var m : moves) {
      var a = s.get(m.from());
      var b = s.get(m.to());
      assertNotNull(a);
      if (m.swap()) {
        s.set(m.from(), b);
        s.set(m.to(), a);
        continue;
      }
      assertTrue(m.count() <= a.count());
      assertTrue(b == null || b.key().equals(a.key()));
      s.set(m.to(), new Planner.Stack(a.key(), m.count() + (b == null ? 0 : b.count()), a.max()));
      s.set(
          m.from(),
          a.count() == m.count()
              ? null
              : new Planner.Stack(a.key(), a.count() - m.count(), a.max()));
    }
    return s;
  }
}
