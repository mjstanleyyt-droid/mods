package dev.restock.plan;

import java.util.*;
import java.util.function.ToIntFunction;

/** Reorders independent moves; shared-slot dependencies always keep their original order. */
public final class MoveOrder {
  public static List<Planner.Move> prioritize(
      List<Planner.Move> moves, ToIntFunction<Planner.Move> priority) {
    int n = moves.size();
    List<List<Integer>> next = new ArrayList<>();
    for (int i = 0; i < n; i++) next.add(new ArrayList<>());
    int[] degree = new int[n];
    Map<Integer, Integer> lastUse = new HashMap<>();
    for (int i = 0; i < n; i++) {
      var move = moves.get(i);
      Set<Integer> parents = new HashSet<>();
      for (int slot : new int[] {move.from(), move.to()}) {
        Integer previous = lastUse.put(slot, i);
        if (previous != null && previous != i) parents.add(previous);
      }
      degree[i] = parents.size();
      for (int parent : parents) next.get(parent).add(i);
    }
    PriorityQueue<Integer> ready =
        new PriorityQueue<>(
            Comparator.comparingInt((Integer i) -> priority.applyAsInt(moves.get(i)))
                .thenComparingInt(i -> i));
    for (int i = 0; i < n; i++) if (degree[i] == 0) ready.add(i);
    List<Planner.Move> result = new ArrayList<>();
    while (!ready.isEmpty()) {
      int at = ready.remove();
      result.add(moves.get(at));
      for (int following : next.get(at)) if (--degree[following] == 0) ready.add(following);
    }
    if (result.size() != n) throw new IllegalStateException("Move dependency cycle");
    return List.copyOf(result);
  }
}
