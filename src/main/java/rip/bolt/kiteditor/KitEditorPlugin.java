package rip.bolt.kiteditor;

import static tc.oc.pgm.lib.net.kyori.adventure.text.Component.text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import net.md_5.bungee.api.ChatColor;
import tc.oc.pgm.api.PGM;
import tc.oc.pgm.api.match.event.MatchFinishEvent;
import tc.oc.pgm.api.party.Competitor;
import tc.oc.pgm.api.player.MatchPlayer;
import tc.oc.pgm.api.player.event.MatchPlayerAddEvent;
import tc.oc.pgm.api.player.event.ObserverInteractEvent;
import tc.oc.pgm.kits.ApplyItemKitEvent;
import tc.oc.pgm.kits.Slot;
import tc.oc.pgm.lib.net.kyori.adventure.text.Component;
import tc.oc.pgm.lib.net.kyori.adventure.text.format.NamedTextColor;
import tc.oc.pgm.spawns.Spawn;
import tc.oc.pgm.spawns.SpawnMatchModule;
import tc.oc.pgm.spawns.events.ObserverKitApplyEvent;

public class KitEditorPlugin extends JavaPlugin implements Listener {

    private ItemStack tool;
    private Map<UUID, CustomKit> kits;

    private Component[] help = new Component[] { text("Click on two items to swap them.", NamedTextColor.YELLOW), text("Close your inventory to save your changes.", NamedTextColor.YELLOW) };

    @Override
    public void onEnable() {
        this.tool = new ItemStack(Material.CHEST);
        ItemMeta meta = tool.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD.toString() + ChatColor.BOLD + "Loadout Editor");
        tool.setItemMeta(meta);

        this.kits = new HashMap<UUID, CustomKit>();
        Bukkit.getPluginManager().registerEvents(this, this);
        System.out.println("[KitEditor] KitEditor is enabled!");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onObserverKit(MatchPlayerAddEvent event) {
        if (!(event.getInitialParty() instanceof Competitor) || event.getPlayer().getMatch().isFinished())
            return;

        Inventory inventory = event.getPlayer().getInventory();
        ItemStack freezer = inventory.getItem(6);
        if (freezer != null)
            inventory.setItem(7, freezer);
        inventory.setItem(6, tool.clone());

        inventory.setItem(0, new ItemStack(Material.COMPASS));
    }

    @EventHandler
    public void onStartEditing(ObserverInteractEvent event) {
        if (event.getClickType() != ClickType.RIGHT)
            return;

        if (!tool.equals(event.getClickedItem()))
            return;

        MatchPlayer player = event.getPlayer();

        player.getInventory().clear();
        player.getBukkit().setGameMode(GameMode.ADVENTURE);
        player.getBukkit().setAllowFlight(true);
        player.getBukkit().setFlying(true);

        for (Spawn spawn : event.getMatch().needModule(SpawnMatchModule.class).getSpawns()) {
            if (!spawn.allows(player) || !spawn.getKit().isPresent())
                continue;

            spawn.getKit().get().apply(player, true, new ArrayList<ItemStack>());
        }

        player.getInventory().setArmorContents(new ItemStack[] { null, null, null, null });

        this.kits.computeIfAbsent(player.getBukkit().getUniqueId(), uuid -> new CustomKit()).startEditing();
        player.getBukkit().openInventory(player.getInventory());

        for (Component message : help)
            player.sendMessage(message);
    }

    @EventHandler
    public void onKitEdit(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        MatchPlayer matchPlayer = PGM.get().getMatchManager().getPlayer(player);
        if (!matchPlayer.isObserving())
            return;

        CustomKit kit = this.kits.get(player.getUniqueId());
        if (kit == null || !kit.isEditing())
            return;

        event.setCancelled(true);
        if (event.getClick() != ClickType.LEFT || event.getSlotType() == SlotType.OUTSIDE || event.getSlotType() == SlotType.ARMOR)
            return;

        if (kit.getLastSlot() == -1) {
            kit.setLastSlot(event.getSlot());
            return;
        }

        int tmp = kit.getSlots()[kit.getLastSlot()];
        kit.getSlots()[kit.getLastSlot()] = event.getSlot();
        kit.getSlots()[event.getSlot()] = tmp;

        ItemStack last = player.getInventory().getItem(kit.getLastSlot());
        player.getInventory().setItem(kit.getLastSlot(), event.getCurrentItem());
        player.getInventory().setItem(event.getSlot(), last);

        kit.setLastSlot(-1);
    }

    @EventHandler
    public void onFinishEditing(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        MatchPlayer matchPlayer = PGM.get().getMatchManager().getPlayer(player);
        if (!matchPlayer.isObserving())
            return;

        CustomKit kit = this.kits.get(player.getUniqueId());
        if (kit == null || !kit.isEditing())
            return;
        kit.stopEditing();

        player.getInventory().clear();
        player.setGameMode(GameMode.CREATIVE);
        Bukkit.getPluginManager().callEvent(new ObserverKitApplyEvent(matchPlayer));
    }

    @EventHandler
    public void onKitApply(ApplyItemKitEvent event) {
        CustomKit kit = kits.get(event.getPlayer().getId());
        if (kit == null)
            return;

        Map<Slot, ItemStack> items = new HashMap<Slot, ItemStack>();
        for (Entry<Slot, ItemStack> entry : event.getSlotItems().entrySet()) {
            int targetSlot = entry.getKey().getIndex();
            inner: for (int i = 0; i < 36; i++) {
                if (kit.getSlots()[i] == entry.getKey().getIndex()) {
                    targetSlot = i;

                    break inner;
                }
            }

            items.put(Slot.Player.forIndex(targetSlot), entry.getValue());
        }

        event.getSlotItems().clear();
        items.forEach(event.getSlotItems()::put);
    }

    @EventHandler
    public void onMatchEnd(MatchFinishEvent event) {
        kits.clear();
    }

}
