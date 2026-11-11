package dev.restock.plan;

import java.util.*;

/**
 * Models vanilla 26.2 shulker shift-click. An optimization is accepted only for an exact kit
 * result.
 */
public final class ShulkerQuickMove {
  public record Result(List<Planner.Stack> after, int consumedMoves) {}

  public static Optional<Result> certify(
      List<Planner.Stack> before,
      List<Planner.Move> moves,
      int start,
      int[] limits,
      boolean[] mayPlace,
      boolean stackable) {
    if (before.size() != 63
        || limits.length != 63
        || mayPlace.length != 63
        || start < 0
        || start >= moves.size()) return Optional.empty();
    var first = moves.get(start);
    int source = first.from();
    if (source < 0 || source >= 27 || first.swap() || before.get(source) == null)
      return Optional.empty();
    List<Planner.Stack> actual = simulate(before, source, limits, mayPlace, stackable);
    if (actual.equals(before)) return Optional.empty();
    List<Planner.Stack> goal = new ArrayList<>(before);
    for (int i = start; i < moves.size(); i++) {
      var move = moves.get(i);
      if (move.from() != source || move.swap() || move.to() < 27 || move.to() >= 63) break;
      Planner.Stack a = goal.get(source), b = goal.get(move.to());
      if (a == null
          || move.count() < 1
          || move.count() > a.count()
          || !mayPlace[move.to()]
          || (b != null && !a.key().equals(b.key()))) break;
      int existing = b == null ? 0 : b.count();
      if (move.count() > Math.min(a.max(), limits[move.to()]) - existing) break;
      goal.set(move.to(), new Planner.Stack(a.key(), existing + move.count(), a.max()));
      goal.set(
          source,
          move.count() == a.count()
              ? null
              : new Planner.Stack(a.key(), a.count() - move.count(), a.max()));
      if (goal.equals(actual))
        return Optional.of(new Result(Collections.unmodifiableList(actual), i - start + 1));
    }
    return Optional.empty();
  }

  // The vanilla QUICK_MOVE action repeats quickMoveStack until it cannot progress.
  private static List<Planner.Stack> simulate(
      List<Planner.Stack> before, int source, int[] limits, boolean[] mayPlace, boolean stackable) {
    List<Planner.Stack> result = new ArrayList<>(before);
    Planner.Stack original = before.get(source);
    int remaining = original.count();
    if (stackable) {
      for (int i = 62; i >= 27 && remaining > 0; i--) {
        Planner.Stack b = result.get(i);
        if (b == null || !b.key().equals(original.key())) continue;
        int room = Math.min(b.max(), limits[i]) - b.count();
        int n = Math.min(remaining, Math.max(0, room));
        if (n > 0) {
          result.set(i, new Planner.Stack(b.key(), b.count() + n, b.max()));
          remaining -= n;
        }
      }
    }
    for (int i = 62; i >= 27 && remaining > 0; i--) {
      if (result.get(i) != null || !mayPlace[i]) continue;
      int n = Math.min(remaining, Math.min(original.max(), limits[i]));
      if (n > 0) {
        result.set(i, new Planner.Stack(original.key(), n, original.max()));
        remaining -= n;
      }
    }
    result.set(
        source,
        remaining == 0 ? null : new Planner.Stack(original.key(), remaining, original.max()));
    return result;
  }
}
