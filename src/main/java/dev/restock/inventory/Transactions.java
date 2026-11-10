package dev.restock.inventory;

import dev.restock.RestockClient;
import dev.restock.compat.Stacks;
import dev.restock.plan.Planner;
import dev.restock.plan.TransferClicks;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/**
 * Sends ordinary PICKUP interactions WITHOUT local prediction. Only incoming server state advances
 * the queue.
 */
public final class Transactions {
  private record Click(
      int slot,
      int button,
      ContainerInput input,
      List<ItemStack> before,
      ItemStack cursorBefore,
      List<ItemStack> after,
      ItemStack cursorAfter) {}

  private final Deque<Click> queue = new ArrayDeque<>();
  private AbstractContainerMenu menu;
  private boolean cancelRequested;
  private boolean atBoundary = true;
  private final Set<Click> boundaries = Collections.newSetFromMap(new IdentityHashMap<>());
  private Click pending;
  private long sent, next;
  private int delay;
  private Runnable completed;

  public boolean busy() {
    return menu != null;
  }

  public void start(
      AbstractContainerMenu menu, List<Planner.Move> moves, int delay, Runnable done) {
    if (busy()) throw new IllegalStateException("An inventory operation is already running");
    if (!menu.getCarried().isEmpty()) throw new IllegalStateException("Empty your cursor first");
    this.menu = menu;
    this.delay = delay;
    this.completed = done;
    List<ItemStack> sim = copy(menu);
    ItemStack cursor = ItemStack.EMPTY;
    try {
      for (var m : moves) {
        if (!directSwap(sim, m)) {
          if (m.swap()) {
            cursor = add(sim, cursor, m.from(), 0);
            cursor = add(sim, cursor, m.to(), 0);
            cursor = add(sim, cursor, m.from(), 0);
          } else {
            ItemStack source = sim.get(m.from());
            ItemStack destination = sim.get(m.to());
            if (!destination.isEmpty() && !ItemStack.isSameItemSameComponents(source, destination))
              throw new IllegalStateException("Cannot merge different components");
            for (var step :
                TransferClicks.plan(
                    source.getCount(),
                    destination.getCount(),
                    m.count(),
                    menu.getSlot(m.from()).getMaxStackSize(source),
                    menu.getSlot(m.to()).getMaxStackSize(source),
                    source.getMaxStackSize())) {
              cursor = add(sim, cursor, step.source() ? m.from() : m.to(), step.button());
            }
          }
        }
        if (!cursor.isEmpty()) throw new IllegalStateException("Planner left a cursor stack");
        boundaries.add(queue.getLast());
      }
    } catch (RuntimeException e) {
      clear();
      throw e;
    }
    next = System.currentTimeMillis();
  }

  private boolean directSwap(List<ItemStack> sim, Planner.Move move) {
    var mc = Minecraft.getInstance();
    ItemStack a = sim.get(move.from()), b = sim.get(move.to());
    if (move.count() != a.getCount() || (!move.swap() && !b.isEmpty())) return false;
    Slot from = menu.getSlot(move.from()), to = menu.getSlot(move.to());
    int hotbar = hotbarIndex(to);
    int clicked = move.from();
    if (hotbar < 0) {
      hotbar = hotbarIndex(from);
      clicked = move.to();
    }
    if (hotbar < 0) return false;
    if (!from.mayPickup(mc.player)
        || (!b.isEmpty() && !to.mayPickup(mc.player))
        || !to.mayPlace(a)
        || a.getCount() > to.getMaxStackSize(a)
        || (!b.isEmpty() && (!from.mayPlace(b) || b.getCount() > from.getMaxStackSize(b))))
      throw new IllegalStateException("Slot refuses swap");
    List<ItemStack> before = copies(sim);
    sim.set(move.from(), b.copy());
    sim.set(move.to(), a.copy());
    queue.add(
        new Click(
            clicked,
            hotbar,
            ContainerInput.SWAP,
            before,
            ItemStack.EMPTY,
            copies(sim),
            ItemStack.EMPTY));
    return true;
  }

  private int hotbarIndex(Slot slot) {
    var player = Minecraft.getInstance().player;
    int index = slot.getContainerSlot();
    return slot.container == player.getInventory() && index >= 0 && index < 9 ? index : -1;
  }

  private ItemStack add(List<ItemStack> s, ItemStack cursor, int slot, int button) {
    var mc = Minecraft.getInstance();
    Slot real = menu.getSlot(slot);
    ItemStack a = s.get(slot).copy(), c = cursor.copy();
    if (!a.isEmpty() && !real.mayPickup(mc.player))
      throw new IllegalStateException("Slot cannot be taken");
    if (!c.isEmpty() && (!real.mayPlace(c) || c.getCount() > c.getMaxStackSize()))
      throw new IllegalStateException("Slot refuses item");
    List<ItemStack> before = copies(s);
    ItemStack cb = c.copy();
    if (c.isEmpty()) {
      int n = button == 0 ? a.getCount() : a.getCount() / 2 + a.getCount() % 2;
      if (n <= 0) throw new IllegalStateException("Empty pickup");
      c = a.copyWithCount(n);
      a.shrink(n);
      if (a.isEmpty()) a = ItemStack.EMPTY;
    } else if (a.isEmpty() || ItemStack.isSameItemSameComponents(a, c)) {
      int n =
          Math.min(
              button == 1 ? 1 : c.getCount(),
              real.getMaxStackSize(c) - (a.isEmpty() ? 0 : a.getCount()));
      if (n <= 0) throw new IllegalStateException("No room");
      if (a.isEmpty()) a = c.copyWithCount(n);
      else a.grow(n);
      c.shrink(n);
      if (c.isEmpty()) c = ItemStack.EMPTY;
    } else {
      if (button != 0 || c.getCount() > real.getMaxStackSize(c)) throw new IllegalStateException();
      ItemStack temp = a;
      a = c;
      c = temp;
    }
    s.set(slot, a);
    queue.add(new Click(slot, button, ContainerInput.PICKUP, before, cb, copies(s), c.copy()));
    return c;
  }

  public void requestCancel() {
    cancelRequested = true;
    RestockClient.notice(
        "Cancelling after the current move returns the cursor safely. Press Escape again after it"
            + " stops.");
  }

  public void onServerUpdate() {
    if (busy()) tick();
  }

  public void tick() {
    if (!busy()) return;
    var mc = Minecraft.getInstance();
    long now = System.currentTimeMillis();
    if (mc.player == null
        || mc.getConnection() == null
        || mc.player.containerMenu != menu
        || !(mc.gui.screen()
            instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen)) {
      cancel("Inventory closed or disconnected; operation stopped.");
      return;
    }
    if (pending != null) {
      if (equal(menu, pending.after, pending.cursorAfter)) {
        atBoundary = boundaries.remove(pending);
        pending = null;
        next = now + delay;
      } else if (now - sent > 5000) {
        cancel("Server did not confirm the inventory action. Stopped; check your cursor.");
      }
      if (pending != null || !busy()) return;
    }
    if (cancelRequested && atBoundary && menu.getCarried().isEmpty()) {
      cancel("Inventory operation cancelled safely.");
      return;
    }
    if (now < next) return;
    if (queue.isEmpty()) {
      Runnable r = completed;
      clear();
      r.run();
      return;
    }
    Click c = queue.removeFirst();
    if (!equal(menu, c.before, c.cursorBefore)) {
      cancel("Inventory changed during restock. Stopped; check your cursor.");
      return;
    }
    pending = c;
    atBoundary = false;
    sent = now;
    // Report the genuine pre-click cursor and no predicted slot updates. Vanilla returns
    // authoritative changes.
    mc.getConnection()
        .send(
            new ServerboundContainerClickPacket(
                menu.containerId,
                menu.getStateId(),
                (short) c.slot,
                (byte) c.button,
                c.input,
                new Int2ObjectOpenHashMap<>(),
                HashedStack.create(
                    menu.getCarried(), mc.getConnection().decoratedHashOpsGenenerator())));
  }

  private boolean equal(AbstractContainerMenu m, List<ItemStack> s, ItemStack c) {
    if (m.slots.size() != s.size() || !Stacks.same(m.getCarried(), c)) return false;
    for (int i = 0; i < s.size(); i++)
      if (!Stacks.same(m.getSlot(i).getItem(), s.get(i))) return false;
    return true;
  }

  private static List<ItemStack> copy(AbstractContainerMenu m) {
    return copies(m.getItems());
  }

  private static List<ItemStack> copies(List<ItemStack> s) {
    return s.stream().map(ItemStack::copy).toList().stream()
        .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
  }

  public void cancel(String why) {
    clear();
    ArmorReplacement.abort();
    RestockClient.notice(why);
  }

  private void clear() {
    cancelRequested = false;
    atBoundary = true;
    boundaries.clear();
    queue.clear();
    pending = null;
    menu = null;
    completed = null;
  }
}
