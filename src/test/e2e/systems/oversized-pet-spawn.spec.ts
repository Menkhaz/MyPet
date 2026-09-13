import { test, expect } from '@drownek/plugwright';
import { expectCondition } from '../lib/oracle.js';
import { createPet, removePet } from '../lib/pets.js';
import { ARENA, setupArena } from '../lib/world.js';
import { msgFragment } from '../lib/locale.js';

/**
 * Spawn-space checks for pets whose vanilla bounding box is much larger than the body
 * MyPet actually drives.
 *
 * VanillaMobSpawner#hasRoomFor tests a candidate spawn position against the pet's own
 * collision box so a pet can't materialise with its head inside a block (the suffocation
 * loop fixed in a7cbd31b). Vanilla's EnderDragon box is 16x8x16 — it spans the wingspan
 * and tail, not the body — so testing it verbatim demands a 16-wide, 8-tall clear volume
 * and a dragon pet is refused with "not enough space" almost anywhere terrain exists.
 * (Pre-4.0 the NMS pet dragon declared @EntitySize(1, 1), so the check this restores
 * never saw the vanilla box.)
 *
 * The pair below pins both halves: an oversized pet must still spawn next to terrain,
 * and a normally-sized pet must still be refused where it genuinely doesn't fit.
 */

/** Clears anything a test built inside the arena's air pocket. */
function clearArenaAir(server: any): void {
  server.execute(
    `fill ${ARENA.x - 20} ${ARENA.y} ${ARENA.z - 20} ` +
    `${ARENA.x + 20} ${ARENA.y + 4} ${ARENA.z + 20} minecraft:air`);
}

test('an EnderDragon pet is called next to terrain inside its vanilla bounding box', async ({ player, server }) => {
  await player.makeOp();
  await setupArena(server, player);

  // Created in the bare arena, where even the unclamped 16-wide box fits: this test is
  // about the *call*, so the pet has to exist first either way.
  const pet = await createPet(server, player, 'EnderDragon', { name: 'BigDrg' });
  try {
    player.chat('/petsendaway');
    await expectCondition(server, player, `unless entity @e[tag=${pet.tag}]`);

    // A single pillar 6 blocks away: outside anything the dragon's body needs, inside
    // the vanilla box's 8-block horizontal reach. This is the everyday case — a tree,
    // a wall, a hill within 8 blocks of the owner.
    server.execute(
      `fill ${ARENA.x + 6} ${ARENA.y} ${ARENA.z} ${ARENA.x + 6} ${ARENA.y + 3} ${ARENA.z} minecraft:stone`);
    // Awaited, not fire-and-forget: `fill` goes out on the server's stdin channel while
    // /petcall goes through the bot's chat channel, with no ordering guarantee between
    // the two — an un-awaited pillar can land after the spawn test has already run.
    await expectCondition(server, player,
      `if block ${ARENA.x + 6} ${ARENA.y + 3} ${ARENA.z} minecraft:stone`);

    const since = player.getMessageBufferIndex();
    player.chat('/petcall');
    await expect(player).toHaveReceivedMessage(
      msgFragment('Message.Command.Call.Success'), { since });
    await expectCondition(server, player, `if entity @e[tag=${pet.tag}]`);
  } finally {
    clearArenaAir(server);
    removePet(server, player);
  }
});

test('an IronGolem pet is refused under a ceiling too low for it', async ({ player, server }) => {
  await player.makeOp();
  await setupArena(server, player);

  const pet = await createPet(server, player, 'IronGolem', { name: 'LowGolem' });
  try {
    player.chat('/petsendaway');
    await expectCondition(server, player, `unless entity @e[tag=${pet.tag}]`);

    // 2 blocks of headroom: the 1.8-tall player still fits (so the call is issued from a
    // legal position), the 2.7-tall golem does not. Covers every candidate position
    // findValidSpawnLocation tries, which are all within 1 block of the owner.
    server.execute(
      `fill ${ARENA.x - 3} ${ARENA.y + 2} ${ARENA.z - 3} ` +
      `${ARENA.x + 3} ${ARENA.y + 2} ${ARENA.z + 3} minecraft:stone`);
    await expectCondition(server, player,
      `if block ${ARENA.x} ${ARENA.y + 2} ${ARENA.z} minecraft:stone`);

    const since = player.getMessageBufferIndex();
    player.chat('/petcall');
    await expect(player).toHaveReceivedMessage(
      msgFragment('Message.Spawn.NoSpace'), { since });
  } finally {
    clearArenaAir(server);
    removePet(server, player);
  }
});
