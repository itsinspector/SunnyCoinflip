#!/usr/bin/env python3
"""Apply the requested SunnyCoinflip BedWars fixes.

Target base: itsinspector/SunnyCoinflip commit
07b94f1243e6eb410437b7c34f95e2db70bb7b80
"""

from __future__ import annotations

import argparse
import re
import shutil
import sys
from pathlib import Path

BASE_SHA = "07b94f1243e6eb410437b7c34f95e2db70bb7b80"
MANAGER_REL = Path("src/main/java/org/ItsInspector/sunnyCoinflip/managers/BedfightManager.java")
LISTENER_REL = Path("src/main/java/org/ItsInspector/sunnyCoinflip/listeners/BedfightListener.java")


class PatchError(RuntimeError):
    pass


def replace_once(text: str, old: str, new: str, label: str) -> tuple[str, bool]:
    if new in text:
        return text, False
    count = text.count(old)
    if count != 1:
        raise PatchError(f"{label}: atteso 1 blocco, trovati {count}")
    return text.replace(old, new, 1), True


def replace_regex_once(
    text: str,
    pattern: re.Pattern[str],
    replacement: str,
    label: str,
    already_marker: str,
) -> tuple[str, bool]:
    if already_marker in text:
        return text, False
    matches = list(pattern.finditer(text))
    if len(matches) != 1:
        raise PatchError(f"{label}: atteso 1 blocco, trovati {len(matches)}")
    match = matches[0]
    return text[: match.start()] + replacement + text[match.end() :], True


def patch_manager(text: str) -> tuple[str, list[str]]:
    changes: list[str] = []

    spectator_pattern = re.compile(
        r"    private void forceSpectatorMode\(Player player, ActiveRound expectedRound\) \{.*?"
        r"\n    \}(?=\n\n    /\*\*\n     \* Keeps an eliminated participant)",
        re.DOTALL,
    )
    spectator_replacement = """    private void forceSpectatorMode(Player player, ActiveRound expectedRound) {
        applySpectatorMode(player);
        final BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()
                    || activeRound != expectedRound
                    || expectedRound.finishing
                    || !expectedRound.spectators.containsKey(player.getUniqueId())) {
                task[0].cancel();
                return;
            }

            if (player.getGameMode() != GameMode.SPECTATOR
                    || !player.getAllowFlight()
                    || !player.isFlying()) {
                applySpectatorMode(player);
            }
        }, 1L, 1L);
    }"""
    text, changed = replace_regex_once(
        text,
        spectator_pattern,
        spectator_replacement,
        "modalità spettatore persistente",
        "|| !expectedRound.spectators.containsKey(player.getUniqueId())",
    )
    if changed:
        changes.append("spettatore persistente anche per non-OP")

    queue_old = """        if (waitingSpawn != null) {
            creator.teleport(waitingSpawn);
        }
        creator.sendMessage(PREFIX + "§aCoinflip creato per §f\\uE0D8 §e" + formatMoney(amount) + "§a.");"""
    queue_new = """        if (waitingSpawn != null) {
            creator.teleport(waitingSpawn);
        }
        restoreFullHealthAfterEntry(creator);
        creator.sendMessage(PREFIX + "§aCoinflip creato per §f\\uE0D8 §e" + formatMoney(amount) + "§a.");"""
    text, changed = replace_once(text, queue_old, queue_new, "vita del First entrando in queue")
    if changed:
        changes.append("vita piena immediata entrando in queue come First")

    fighter_old = """        if (!player.teleport(spawn)) {
            return false;
        }
        giveKit(player, team);"""
    fighter_new = """        if (!player.teleport(spawn)) {
            return false;
        }
        restoreFullHealthAfterEntry(player);
        giveKit(player, team);"""
    text, changed = replace_once(text, fighter_old, fighter_new, "vita dei fighter entrando in partita")
    if changed:
        changes.append("vita piena immediata entrando in partita come First o Opponent")

    health_marker = "    private void restoreFullHealthAfterEntry(Player player) {"
    if health_marker not in text:
        reset_marker = "    private void resetCombatState(Player player) {"
        if text.count(reset_marker) != 1:
            raise PatchError("helper vita: impossibile trovare resetCombatState in modo univoco")
        health_helper = """    private void restoreFullHealthAfterEntry(Player player) {
        restoreFullHealth(player);
        UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (isParticipant(playerId)) {
                restoreFullHealth(player);
            }
        });
    }

    private void restoreFullHealth(Player player) {
        if (!player.isOnline() || player.isDead()) {
            return;
        }
        player.setHealth(player.getMaxHealth());
    }

"""
        text = text.replace(reset_marker, health_helper + reset_marker, 1)
        changes.append("secondo ripristino vita al tick successivo contro override di altri plugin")

    movement_marker = "    public void handleSpawnProtectionMovement(Player player, Location from, Location to) {"
    if movement_marker not in text:
        leave_marker = "    public boolean canLeaveArena(Player player, Location destination) {"
        if text.count(leave_marker) != 1:
            raise PatchError("spawn protection: impossibile trovare canLeaveArena in modo univoco")
        movement_method = """    public void handleSpawnProtectionMovement(Player player, Location from, Location to) {
        ActiveRound round = activeRound;
        UUID playerId = player.getUniqueId();
        if (round == null || !round.playing || round.finishing
                || from == null || to == null
                || !isActiveParticipant(playerId)
                || round.respawning.contains(playerId)
                || !hasSpawnProtection(round, playerId)) {
            return;
        }

        boolean changedWorld = from.getWorld() == null
                || to.getWorld() == null
                || !from.getWorld().getUID().equals(to.getWorld().getUID());
        boolean changedPosition = Double.compare(from.getX(), to.getX()) != 0
                || Double.compare(from.getY(), to.getY()) != 0
                || Double.compare(from.getZ(), to.getZ()) != 0;
        if (!changedWorld && !changedPosition) {
            return;
        }

        if (round.spawnProtectedUntil.remove(playerId) != null) {
            player.sendActionBar("§cProtezione spawn disattivata: ti sei mosso");
            playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 1.0f);
        }
    }

"""
        text = text.replace(leave_marker, movement_method + leave_marker, 1)
        changes.append("rimozione immediata della spawn protection al movimento")

    return text, changes


def patch_listener(text: str) -> tuple[str, list[str]]:
    changes: list[str] = []
    move_pattern = re.compile(
        r"    @EventHandler\(priority = EventPriority\.HIGHEST, ignoreCancelled = true\)\n"
        r"    public void onMove\(PlayerMoveEvent event\) \{.*?\n    \}"
        r"(?=\n\n    @EventHandler\(priority = EventPriority\.HIGHEST, ignoreCancelled = true\)\n"
        r"    public void onTeleport)",
        re.DOTALL,
    )
    move_replacement = """    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!manager().canMoveDuringCountdown(event.getPlayer(), event.getFrom(), event.getTo())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to != null) {
                from.setYaw(to.getYaw());
                from.setPitch(to.getPitch());
            }
            event.setTo(from);
            return;
        }

        manager().handleSpawnProtectionMovement(event.getPlayer(), event.getFrom(), event.getTo());
        manager().handleVoidLevel(event.getPlayer(), event.getTo());
    }"""
    text, changed = replace_regex_once(
        text,
        move_pattern,
        move_replacement,
        "listener del movimento",
        "manager().handleSpawnProtectionMovement(event.getPlayer(), event.getFrom(), event.getTo());",
    )
    if changed:
        changes.append("listener movimento collegato alla spawn protection")
    return text, changes


def write_with_backup(path: Path, content: str) -> None:
    backup = path.with_suffix(path.suffix + ".bak")
    if not backup.exists():
        shutil.copy2(path, backup)
    path.write_text(content, encoding="utf-8", newline="")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Applica le tre correzioni BedWars richieste a SunnyCoinflip."
    )
    parser.add_argument(
        "repo",
        nargs="?",
        default=".",
        help="cartella root del repository SunnyCoinflip (default: directory corrente)",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="verifica che la patch sia applicabile senza scrivere file",
    )
    args = parser.parse_args()

    root = Path(args.repo).expanduser().resolve()
    manager_path = root / MANAGER_REL
    listener_path = root / LISTENER_REL
    missing = [str(path) for path in (manager_path, listener_path) if not path.is_file()]
    if missing:
        raise PatchError("file mancanti:\n- " + "\n- ".join(missing))

    manager_original = manager_path.read_text(encoding="utf-8")
    listener_original = listener_path.read_text(encoding="utf-8")
    manager_patched, manager_changes = patch_manager(manager_original)
    listener_patched, listener_changes = patch_listener(listener_original)
    changes = manager_changes + listener_changes

    if args.check:
        print("Controllo completato.")
        if changes:
            print("La patch è applicabile:")
            for change in changes:
                print(f"- {change}")
        else:
            print("Le correzioni risultano già applicate.")
        return 0

    if not changes:
        print("Nessuna modifica necessaria: le correzioni risultano già applicate.")
        return 0

    write_with_backup(manager_path, manager_patched)
    write_with_backup(listener_path, listener_patched)

    print(f"Patch applicata (base prevista: {BASE_SHA}).")
    for change in changes:
        print(f"- {change}")
    print("Backup creati con estensione .java.bak")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except PatchError as exc:
        print(f"ERRORE: {exc}", file=sys.stderr)
        raise SystemExit(1)
