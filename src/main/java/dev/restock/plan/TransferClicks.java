package dev.restock.plan;

import java.util.*;

/** Shortest ordinary left/right click sequence between two component-identical stacks. */
public final class TransferClicks {
  public record Step(boolean source, int button) {}

  private record State(int source, int destination, int cursor) {}

  private record Previous(State state, Step step) {}

  private static final int SEARCH_LIMIT = 20000;

  public static List<Step> plan(
      int source,
      int destination,
      int amount,
      int sourceLimit,
      int destinationLimit,
      int cursorLimit) {
    if (source < 1
        || destination < 0
        || amount < 1
        || amount > source
        || source > sourceLimit
        || destination > destinationLimit
        || amount > destinationLimit - destination
        || sourceLimit > cursorLimit
        || destinationLimit > cursorLimit) throw new IllegalArgumentException("Invalid transfer");
    State start = new State(source, destination, 0);
    State goal = new State(source - amount, destination + amount, 0);
    Deque<State> todo = new ArrayDeque<>();
    Map<State, Previous> seen = new HashMap<>();
    todo.add(start);
    seen.put(start, null);
    while (!todo.isEmpty() && seen.size() <= SEARCH_LIMIT) {
      State s = todo.removeFirst();
      if (s.equals(goal)) {
        List<Step> result = new ArrayList<>();
        for (State at = goal; !at.equals(start); ) {
          Previous previous = seen.get(at);
          result.add(previous.step);
          at = previous.state;
        }
        Collections.reverse(result);
        return List.copyOf(result);
      }
      for (boolean from : new boolean[] {true, false})
        for (int button = 0; button <= 1; button++) {
          int count = from ? s.source : s.destination;
          int limit = from ? sourceLimit : destinationLimit;
          int cursor = s.cursor;
          if (cursor == 0) {
            int n = button == 0 ? count : count / 2 + count % 2;
            if (n == 0) continue;
            count -= n;
            cursor = n;
          } else {
            int n = Math.min(button == 0 ? cursor : 1, limit - count);
            if (n <= 0) continue;
            count += n;
            cursor -= n;
          }
          State next =
              from ? new State(count, s.destination, cursor) : new State(s.source, count, cursor);
          if (!seen.containsKey(next)) {
            seen.put(next, new Previous(s, new Step(from, button)));
            todo.addLast(next);
          }
        }
    }
    // Bounded planning for unusually large modded stacks: return the smaller remainder.
    List<Step> result = new ArrayList<>();
    result.add(new Step(true, 0));
    int remainder = source - amount;
    int units = Math.min(amount, remainder);
    if (units > 10000) throw new IllegalArgumentException("Transfer needs too many clicks");
    if (amount <= remainder) {
      for (int i = 0; i < amount; i++) result.add(new Step(false, 1));
      result.add(new Step(true, 0));
    } else {
      for (int i = 0; i < remainder; i++) result.add(new Step(true, 1));
      result.add(new Step(false, 0));
    }
    return List.copyOf(result);
  }
}
