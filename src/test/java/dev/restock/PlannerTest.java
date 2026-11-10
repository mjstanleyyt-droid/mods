package dev.restock;

import static org.junit.jupiter.api.Assertions.*;

import dev.restock.plan.Planner;
import java.util.*;
import org.junit.jupiter.api.Test;

class PlannerTest {
  Planner.Stack stack(String key, int n) {
    return new Planner.Stack(key, n, 64);
  }

  Planner.Target target(String key, int n) {
    return new Planner.Target(s -> s.key().equals(key), n);
  }

  Map<String, Integer> totals(List<Planner.Stack> s) {
    Map<String, Integer> n = new HashMap<>();
    for (var a : s) if (a != null) n.merge(a.key(), a.count(), Integer::sum);
    return n;
  }

  void verify(List<Planner.Stack> before, Planner.Plan p) {
    var s = new ArrayList<>(before);
    for (var m : p.moves()) {
      var a = s.get(m.from());
      var b = s.get(m.to());
      assertNotNull(a);
      assertTrue(m.count() > 0 && m.count() <= a.count());
      if (m.swap()) {
        s.set(m.from(), b);
        s.set(m.to(), a);
      } else {
        assertTrue(b == null || b.key().equals(a.key()));
        int n = m.count() + (b == null ? 0 : b.count());
        assertTrue(n <= a.max());
        s.set(m.to(), stack(a.key(), n));
        s.set(m.from(), a.count() == m.count() ? null : stack(a.key(), a.count() - m.count()));
      }
    }
    assertEquals(totals(before), totals(s));
  }

  @Test
  void partialSupplyOnlyTakesDeficit() {
    var s = Arrays.asList(stack("pearl", 5), null, stack("pearl", 64));
    var p = Planner.plan(s, List.of(0, 1), List.of(2), Map.of(0, target("pearl", 16)), true);
    assertEquals(11, p.moves().getFirst().count());
    verify(s, p);
  }

  @Test
  void missingSkipsAndContinues() {
    var s = Arrays.asList(null, null, stack("apple", 2));
    var p =
        Planner.plan(
            s,
            List.of(0, 1),
            List.of(2),
            Map.of(0, target("pearl", 16), 1, target("apple", 4)),
            true);
    assertEquals(List.of(0, 1), p.incomplete());
    assertEquals(1, p.moves().size());
    verify(s, p);
  }

  @Test
  void fullInventoryCycle() {
    var s = List.of(stack("a", 5), stack("b", 3));
    var p =
        Planner.plan(
            s, List.of(0, 1), List.of(), Map.of(0, target("b", 3), 1, target("a", 5)), true);
    assertTrue(p.incomplete().isEmpty());
    assertTrue(p.moves().getFirst().swap());
    verify(s, p);
  }

  @Test
  void fullInventoryPartialCannotDisplace() {
    var s = List.of(stack("a", 64), stack("b", 64), stack("c", 64));
    var p = Planner.plan(s, List.of(0, 1), List.of(2), Map.of(0, target("c", 1)), true);
    assertTrue(p.moves().isEmpty());
    assertEquals(List.of(0), p.incomplete());
    verify(s, p);
  }

  @Test
  void reservedStacksNotStolen() {
    var s = List.of(stack("p", 16), stack("p", 10), stack("p", 2));
    var p =
        Planner.plan(
            s, List.of(0, 1), List.of(2), Map.of(0, target("p", 16), 1, target("p", 16)), true);
    assertEquals(2, p.moves().getFirst().count());
    verify(s, p);
  }

  @Test
  void unrelatedMovedOnlyWhenRequired() {
    var s = Arrays.asList(stack("a", 5), null, stack("p", 16));
    var p = Planner.plan(s, List.of(0, 1), List.of(2), Map.of(0, target("p", 16)), true);
    assertEquals(2, p.moves().size());
    verify(s, p);
  }

  @Test
  void disabledRearrangementPreservesOccupiedSlots() {
    var s = Arrays.asList(stack("a", 5), null, stack("p", 16));
    var p = Planner.plan(s, List.of(0, 1), List.of(2), Map.of(0, target("p", 16)), false);
    assertTrue(p.moves().isEmpty());
  }

  @Test
  void differentComponentsCannotMerge() {
    var s = List.of(stack("enchanted", 1), stack("plain", 16));
    var p =
        Planner.plan(s, List.of(0), List.of(1), Map.of(0, new Planner.Target(x -> true, 16)), true);
    assertTrue(p.moves().isEmpty());
    assertEquals(List.of(0), p.incomplete());
  }

  @Test
  void randomizedConservation() {
    Random r = new Random(7);
    for (int round = 0; round < 2000; round++) {
      List<Planner.Stack> s = new ArrayList<>();
      Map<Integer, Planner.Target> t = new HashMap<>();
      for (int i = 0; i < 63; i++) {
        s.add(r.nextInt(5) == 0 ? null : stack("item" + r.nextInt(8), 1 + r.nextInt(64)));
        if (i < 36 && r.nextBoolean()) t.put(i, target("item" + r.nextInt(8), 1 + r.nextInt(64)));
      }
      verify(
          s,
          Planner.plan(
              s,
              java.util.stream.IntStream.range(0, 36).boxed().toList(),
              java.util.stream.IntStream.range(36, 63).boxed().toList(),
              t,
              true));
    }
  }
}
