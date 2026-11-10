package dev.restock.plan;

import java.util.*;

/**
 * Pure conservative planner. Keys represent item + ALL components; predicates represent kit
 * matching.
 */
public final class Planner {
  public record Stack(String key, int count, int max) {
    public Stack {
      if (count < 1 || count > max) throw new IllegalArgumentException();
    }
  }

  public record Target(java.util.function.Predicate<Stack> matches, int count) {}

  public record Move(int from, int to, int count, boolean swap) {}

  public record Plan(List<Move> moves, List<Integer> incomplete) {}

  public static Plan plan(
      List<Stack> snapshot,
      List<Integer> player,
      List<Integer> supply,
      Map<Integer, Target> targets,
      boolean rearrange) {
    List<Stack> s = new ArrayList<>(snapshot);
    List<Move> moves = new ArrayList<>();
    List<Integer> missing = new ArrayList<>();
    for (int dest : player) {
      Target t = targets.get(dest);
      if (t == null) continue;
      Stack d = s.get(dest);
      if (d != null && !t.matches.test(d)) {
        if (!rearrange) {
          missing.add(dest);
          continue;
        }
        int empty = -1;
        for (int slot : player)
          if (slot != dest && s.get(slot) == null && !targets.containsKey(slot)) {
            empty = slot;
            break;
          }
        if (empty >= 0) {
          move(s, moves, dest, empty, d.count, false);
          d = null;
        } else {
          int source = -1;
          for (int i : player)
            if (i != dest
                && s.get(i) != null
                && t.matches.test(s.get(i))
                && !targets.containsKey(i)) {
              source = i;
              break;
            }
          // Full-inventory cycles: swapping two entire stacks preserves every extra item.
          if (source < 0)
            for (int i : player)
              if (i != dest && s.get(i) != null && t.matches.test(s.get(i))) {
                Target st = targets.get(i);
                if (st == null || !st.matches.test(s.get(i))) {
                  source = i;
                  break;
                }
              }
          if (rearrange && source >= 0 && s.get(source).count <= t.count) {
            move(s, moves, source, dest, s.get(source).count, true);
            d = s.get(dest);
          } else {
            missing.add(dest);
            continue;
          }
        }
      }
      int need = t.count - (d == null ? 0 : d.count);
      if (need <= 0) continue;
      List<Integer> sources = new ArrayList<>();
      if (rearrange) sources.addAll(player);
      sources.addAll(supply);
      for (int src : sources) {
        if (src == dest || need <= 0) continue;
        Stack a = s.get(src);
        if (a == null || !t.matches.test(a)) continue;
        d = s.get(dest);
        if (d != null && !d.key.equals(a.key)) continue;
        int reserve = 0;
        Target st = targets.get(src);
        if (st != null && st.matches.test(a)) reserve = st.count;
        int n = Math.min(need, Math.max(0, a.count - reserve));
        n = Math.min(n, a.max - (d == null ? 0 : d.count));
        if (n > 0) {
          move(s, moves, src, dest, n, false);
          need -= n;
        }
      }
      if (need > 0) missing.add(dest);
    }
    return new Plan(List.copyOf(moves), List.copyOf(missing));
  }

  private static void move(List<Stack> s, List<Move> m, int from, int to, int n, boolean swap) {
    Stack a = s.get(from), b = s.get(to);
    m.add(new Move(from, to, n, swap));
    if (swap) {
      s.set(from, b);
      s.set(to, a);
      return;
    }
    if (b != null && !a.key.equals(b.key)) throw new IllegalStateException();
    s.set(to, new Stack(a.key, n + (b == null ? 0 : b.count), a.max));
    s.set(from, a.count == n ? null : new Stack(a.key, a.count - n, a.max));
  }
}
