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

package de.Keyle.MyPet.entity.visual;

import de.Keyle.MyPet.MyPetApi;
import org.bukkit.EntityEffect;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Ravager;

import java.lang.reflect.Method;

/** Restores vanilla melee animations bypassed by MyPet's direct-damage attack path. */
public final class PetVanillaAttackAnimation {

    private static volatile boolean statusBridgeInitialized;
    private static volatile boolean statusBridgeAvailable;
    private static volatile Method getHandleMethod;
    private static volatile Method levelMethod;
    private static volatile Method broadcastEntityEventMethod;

    private PetVanillaAttackAnimation() {
    }

    /**
     * Plays the mob-specific vanilla animation associated with a successful
     * melee hit. The generic hand swing is handled by the attack goal itself.
     */
    public static void play(Mob mob) {
        switch (mob.getType()) {
            case HORSE, DONKEY, MULE, SKELETON_HORSE, ZOMBIE_HORSE -> {
                if (mob instanceof AbstractHorse horse) {
                    playRear(horse);
                }
                return;
            }
            default -> {
            }
        }

        if (mob instanceof CopperGolem) {
            broadcastCopperGolemAttack(mob);
            return;
        }

        // Ravager already has a dedicated setAttackTicks-based behavior. Keep
        // that handler authoritative rather than dispatching the same state twice.
        if (!(mob instanceof Ravager) && EntityEffect.ENTITY_ATTACK.isApplicableTo(mob)) {
            mob.playEffect(EntityEffect.ENTITY_ATTACK);
        }
    }

    /** Matches the ten-tick rearing attack visual used by MyPet 3. */
    private static void playRear(AbstractHorse horse) {
        horse.setRearing(true);
        horse.getScheduler().runDelayed(MyPetApi.getPlugin(),
                task -> horse.setRearing(false), null, 10L);
    }

    /**
     * Copper Golem handles entity status {@code 4} as its attack animation,
     * but Paper 1.21.11 does not include CopperGolem in
     * {@link EntityEffect#ENTITY_ATTACK}'s applicability set. Use the same
     * narrow, Mojang-mapped reflection pattern as BrainAccess until Paper
     * exposes this status through Bukkit.
     */
    private static void broadcastCopperGolemAttack(Mob mob) {
        if (!statusBridgeInitialized) {
            initializeStatusBridge();
        }
        if (!statusBridgeAvailable) return;

        try {
            Object handle = getHandleMethod.invoke(mob);
            Object level = levelMethod.invoke(handle);
            broadcastEntityEventMethod.invoke(level, handle, EntityEffect.ENTITY_ATTACK.getData());
        } catch (Throwable ignored) {
            // Fail soft after a successful lookup; a future runtime rename
            // should not break combat merely because its visual cannot play.
        }
    }

    private static synchronized void initializeStatusBridge() {
        if (statusBridgeInitialized) return;
        statusBridgeInitialized = true;

        try {
            Class<?> craftEntity = Class.forName("org.bukkit.craftbukkit.entity.CraftEntity");
            Class<?> nmsEntity = Class.forName("net.minecraft.world.entity.Entity");
            Class<?> nmsLevel = Class.forName("net.minecraft.world.level.Level");

            getHandleMethod = craftEntity.getMethod("getHandle");
            levelMethod = nmsEntity.getMethod("level");
            broadcastEntityEventMethod = nmsLevel.getMethod("broadcastEntityEvent", nmsEntity, byte.class);
            statusBridgeAvailable = true;
        } catch (Throwable t) {
            MyPetApi.getLogger().warning(
                    "Copper Golem attack animation unavailable: could not initialize entity-status bridge. Cause: " + t);
        }
    }
}
