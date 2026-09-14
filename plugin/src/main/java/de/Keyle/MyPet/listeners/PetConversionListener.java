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

package de.Keyle.MyPet.listeners;

import de.Keyle.MyPet.MyPetApi;
import de.Keyle.MyPet.api.entity.Pet;
import de.Keyle.MyPet.api.entity.PetType;
import de.Keyle.MyPet.api.exceptions.PetTypeNotFoundException;
import de.Keyle.MyPet.entity.spawn.PetEntityMarker;
import de.Keyle.MyPet.repository.PetManager;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTransformEvent;

import java.util.EnumSet;
import java.util.Set;

/**
 * Catch-all for vanilla species conversions that no other listener claims — a zombie
 * drowning, a husk drowning into a zombie, a skeleton freezing into a stray, a zombie
 * villager being cured.
 *
 * <p>Left to vanilla these DUPLICATE the pet's equipment. Vanilla copies the gear onto the
 * new mob, and that mob inherits the pet's scoreboard tag but NOT MyPet's PDC marker — so it
 * walks away as an ordinary wild mob wearing the pet's armor. MyPet meanwhile sees the pet's
 * entity vanish and the next {@code /petcall} respawns the pet re-equipped from
 * {@code PetImpl.equipment}. The gear now exists twice, and the cycle is farmable: park the
 * pet, recall it, kill the wild copy, repeat.
 *
 * <p>The fix is to re-type the Pet onto the entity vanilla just produced, exactly as
 * {@link PetZombificationListener} does for piglins — one entity, one set of gear, and the
 * pet keeps its name, skills and experience across the change. Cancelling instead would also
 * stop the duplication, but it would suppress a vanilla mechanic players expect (a zombie
 * left underwater does drown), so re-typing is preferred and cancelling is only the fallback
 * when the new species isn't a registered pet type.
 *
 * <p>Reasons owned by other listeners are skipped here so a conversion is never handled
 * twice: {@code METAMORPHOSIS} belongs to {@link PetMetamorphosisListener}, {@code LIGHTNING}
 * to {@link PetLightningStrikeListener} and {@code PetMooshroom}, and
 * {@code PIGLIN_ZOMBIFIED} to {@link PetZombificationListener} (which gates on the pet type
 * rather than the reason, so it is excluded by reason here). This listener is registered
 * after all of them.
 */
public class PetConversionListener implements Listener {

    /** Conversions another listener already owns. Anything outside this set lands here. */
    private static final Set<EntityTransformEvent.TransformReason> CLAIMED_ELSEWHERE = EnumSet.of(
            EntityTransformEvent.TransformReason.METAMORPHOSIS,
            EntityTransformEvent.TransformReason.LIGHTNING,
            EntityTransformEvent.TransformReason.PIGLIN_ZOMBIFIED);

    @EventHandler(priority = EventPriority.HIGH)
    public void onPetConversion(EntityTransformEvent event) {
        if (CLAIMED_ELSEWHERE.contains(event.getTransformReason())) return;
        if (!PetEntityMarker.isMarked(event.getEntity())) return;

        Pet pet = MyPetApi.getPetManager().getPetFromEntity(event.getEntity());
        if (pet == null) return;

        if (tryRetype(event, pet)) {
            return;
        }

        // The new species isn't a registered pet type, so there is nowhere to move the Pet
        // to. Cancelling keeps the pet as it is, which is the only remaining way to avoid
        // handing its gear to a wild mob.
        event.setCancelled(true);
    }

    /**
     * Moves the Pet onto the entity vanilla just created. Returns {@code true} when the
     * conversion was accepted (event left uncancelled, Pet swapped); {@code false} when the
     * new entity isn't a {@link Mob} or its species has no registered {@link PetType}, in
     * which case the caller cancels instead.
     */
    private boolean tryRetype(EntityTransformEvent event, Pet oldPet) {
        if (!(event.getTransformedEntity() instanceof Mob newEntity)) {
            return false;
        }
        PetType newType;
        try {
            newType = PetType.byEntityTypeName(newEntity.getType().name());
        } catch (PetTypeNotFoundException e) {
            return false;
        }
        if (newType == null) return false;

        // Cast is safe: the active concrete manager is always plugin-side.
        PetManager manager = (PetManager) MyPetApi.getPetManager();
        return manager.convertPetType(oldPet, newType, newEntity).isPresent();
    }
}
