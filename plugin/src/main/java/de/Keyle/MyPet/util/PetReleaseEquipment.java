/*
 * This file is part of MyPet
 *
 * Copyright © 2011-2026 Keyle
 * MyPet is licensed under the GNU Lesser General Public License.
 *
 * MyPet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MyPet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package de.Keyle.MyPet.util;

import de.Keyle.MyPet.api.entity.Pet;
import de.Keyle.MyPet.api.entity.PetEquipment;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Rescues equipment that would otherwise be destroyed when a pet is released to the wild.
 *
 * <p>Releasing a pet rebuilds it as a vanilla mob from
 * {@code PetEntitySnapshot.capture(oldMob)} — a snapshot of the LIVE ENTITY. Both release
 * paths therefore skip {@code dropEquipment()} when the pet converted, on the assumption
 * that the wild mob carries the gear away with it.
 *
 * <p>That assumption only holds for gear the entity actually wears. MyPet also tracks
 * equipment in {@code PetImpl.equipment}, and the two can disagree — most visibly for a
 * Villager, whose hand reads empty however the item was equipped. Anything present in the
 * map but absent from the entity is in neither the snapshot nor the map once the pet is
 * discarded, so it is destroyed outright.
 *
 * <p>The rescue is deliberately narrow: only slots the entity is NOT carrying are dropped.
 * Dropping the rest would hand the player a copy of gear the wild mob also walked off with,
 * which is the duplication these fixes exist to remove.
 *
 * <p>Two phases, because the useful state disappears mid-release: the comparison must run
 * while the pet still has its Bukkit entity, and the drop must run afterwards so nothing is
 * dropped if the release is refused. Capture first, then drop only on success.
 */
public final class PetReleaseEquipment {

    private PetReleaseEquipment() {
    }

    /**
     * Returns the equipment MyPet is tracking that the pet's live entity is not wearing.
     *
     * <p>Call BEFORE {@code releaseToWild}. Afterwards the entity is detached and every slot
     * would read empty, which would make this return the pet's whole inventory.
     *
     * @param pet the pet about to be released
     * @return items that would be destroyed by the release; empty when there are none
     */
    public static List<ItemStack> captureUncarried(Pet pet) {
        List<ItemStack> uncarried = new ArrayList<>();
        if (!(pet instanceof PetEquipment equipmentPet)) {
            return uncarried;
        }
        Mob mob = pet.getBukkitEntity();
        EntityEquipment live = mob == null ? null : mob.getEquipment();

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack tracked = equipmentPet.getEquipment(slot);
            if (tracked == null || tracked.getType().isAir()) {
                continue;
            }
            // No live entity at all: nothing can be carried, so everything tracked is at risk.
            if (live == null) {
                uncarried.add(tracked);
                continue;
            }
            ItemStack worn;
            try {
                worn = live.getItem(slot);
            } catch (IllegalArgumentException | UnsupportedOperationException e) {
                // Slot unsupported on this entity or API version — treat as not carried.
                worn = null;
            }
            if (worn == null || worn.getType().isAir()) {
                uncarried.add(tracked);
            }
        }
        return uncarried;
    }

    /**
     * Drops what {@link #captureUncarried} found, at the location the pet occupied.
     *
     * <p>Call only once the release has actually succeeded — a refused release leaves the pet
     * intact, and dropping here would duplicate its gear.
     *
     * @param items items from {@link #captureUncarried}
     * @param dropAt where the pet was standing, captured before the release
     */
    public static void dropUncarried(List<ItemStack> items, Location dropAt) {
        if (items.isEmpty() || dropAt == null || dropAt.getWorld() == null) {
            return;
        }
        for (ItemStack item : items) {
            dropAt.getWorld().dropItem(dropAt, item);
        }
    }
}
