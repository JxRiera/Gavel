# Gavel

Punishment GUI for [LiteBans](https://www.spigotmc.org/resources/litebans.3715/) with automatic
escalation.

Staff run `/ban player`. Gavel ignores the reason and duration they typed, opens a menu of the
offences you configured, and picks the punishment from how many times that player already offended
in that category. LiteBans still applies it, so broadcasts, history, cross-server sync and appeals
keep working.

```
Hacked Client  #1  ->  15d ban
Hacked Client  #2  ->  25d ban
Hacked Client  #3  ->  permanent ban
```

## Requirements

Spigot, Paper or any fork on 1.16 or newer, Java 8 or newer, and LiteBans.

## Install

Drop the jar in `plugins/`, restart, edit `plugins/Gavel/categories.yml`, run `/gavel reload`.

## Configuration

| File | What is in it |
|---|---|
| `categories.yml` | The offences, their escalation tiers, and the menu layout |
| `config.yml` | Which commands are intercepted, how they run, storage, sounds |
| `messages.yml` | Every string shown to a player |

All three are commented. The part that matters is a category:

```yaml
categories:
  hacked_client:
    slot: 10
    permission: 'gavel.category.hacked_client'
    expire-after: 90d
    icon:
      material: NETHERITE_SWORD
      name: '&#ff5555&lHACKED CLIENT'
      lore:
        - '&7Previous offences: &f%offenses%'
        - '%tiers%'
    tiers:
      - { type: BAN, duration: 15d, reason: 'Hacked Client #1' }
      - { type: BAN, duration: 25d, reason: 'Hacked Client #2' }
      - { type: BAN, duration: perm, reason: 'Hacked Client #3' }
```

Types are `BAN`, `IPBAN`, `MUTE`, `WARN` and `KICK`, and can be mixed inside one category.
Durations use `s m h d w mo y` or `perm`.

## Commands

| Command | Permission |
|---|---|
| `/gavel <player>` | `gavel.use` |
| `/gavel history <player>` | `gavel.history` |
| `/gavel stats [player]` | `gavel.stats` |
| `/gavel clear <player> [category]` | `gavel.admin` |
| `/gavel reload` | `gavel.admin` |
| `/gavel version` | `gavel.admin` |

## Permissions

| Permission | Grants |
|---|---|
| `gavel.use` | The overlay opens on punishment commands, and `/gavel` can be run |
| `gavel.bypass` | The raw LiteBans commands, without the overlay |
| `gavel.category.<id>` | One category |
| `gavel.silent` | Silent punishments with shift-click |
| `gavel.history` | A player's history |
| `gavel.stats` | Counts by category and staff member |
| `gavel.admin` | `reload`, `clear` and `version` |

## Good to know

- Gavel keeps its own `gavel_offenses` table. With `database.mode: AUTO` it reuses the LiteBans
  database on MySQL, MariaDB or PostgreSQL, and falls back to a local SQLite file on H2, which
  locks its file exclusively.
- `/unban`, `/unmute` and friends roll the counter back, so the next punishment does not escalate
  from one that no longer exists.
- `execution.commands` are templates, so a LiteBans build with different command names is a config
  change rather than a recompile.
- Player names must match exactly. A partial name is rejected instead of guessed.
- `gavel.bypass` defaults to false on purpose. A blanket `*` permission hands it out and the
  overlay stops opening.
- `tracking.external-removals` decides whose unbans move the counter. `PLAYERS` ignores the
  console, so an anticheat or a chargeback bot cannot roll back a staff decision; `ALL` accepts
  every source and `GAVEL` accepts none. `tracking.ignored-executors` blocks specific names in
  any mode.
- With PlaceholderAPI installed, `%gavel_offenses%`, `%gavel_offenses_<category>%` and
  `%gavel_next_type_<category>%` / `%gavel_next_duration_<category>%` / `%gavel_next_number_<category>%`
  / `%gavel_next_reason_<category>%` are available. They read a cache refreshed on join and after
  every punishment, so a scoreboard never touches the database.

## Build

```bash
./gradlew build
```

Runs the tests and writes the jar to `build/libs/`. Needs JDK 11 or newer; the output targets
Java 8. CI blocks a release until the tests pass, and publishes one when the version in
`build.gradle.kts` changes.

## License

[Apache 2.0](LICENSE). Forks must keep the copyright notice, ship the contents of
[NOTICE](NOTICE), and state which files they changed.
