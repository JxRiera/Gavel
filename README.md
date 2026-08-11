# Gavel

A punishment GUI for [LiteBans](https://www.spigotmc.org/resources/litebans.3715/) with automatic
escalation.

Staff run `/ban player`, `/mute player` or any other punishment command. Gavel ignores the reason
and duration they typed, opens a menu of the offences you configured, and picks the punishment
from how many times that player already offended in that category.

```
Hacked Client  #1  ->  15d ban
Hacked Client  #2  ->  25d ban
Hacked Client  #3  ->  permanent ban
```

The punishment itself is still applied by LiteBans, by running its own command, so broadcasts,
LiteBans history, cross-server sync and appeals keep working as before.

## Requirements

- Spigot, Paper or any fork, 1.16 or newer
- Java 8 or newer
- LiteBans

## Installation

1. Put the jar in `plugins/`.
2. Restart the server. `config.yml`, `categories.yml` and `messages.yml` are created in
   `plugins/Gavel/`.
3. Edit `categories.yml`.
4. Run `/gavel reload`.

## How it works

1. A staff member runs an intercepted command, for example `/ban Steve using killaura`.
2. The command is cancelled. The reason and duration are discarded.
3. Gavel reads that player's offence history and opens the menu, each icon showing how many
   previous offences the player has in that category and which tier comes next.
4. On click, the tier decides the type, the duration and the reason.
5. Gavel runs the matching LiteBans command and records the offence.

Lifting the punishment later reverses step 5, see [Lifting a punishment](#lifting-a-punishment).

## Configuration

### categories.yml

Each category is one slot in the menu and one ordered list of tiers:

```yaml
categories:
  hacked_client:
    slot: 10
    permission: 'gavel.category.hacked_client'
    expire-after: 90d
    icon:
      material: NETHERITE_SWORD
      name: '&#ff5555&lHACKED CLIENT'
      glow: true
      lore:
        - '&7Previous offences: &f%offenses%'
        - '%tiers%'
        - '&7Will apply: &f%next_type% &e%next_duration%'
    tiers:
      - { type: BAN, duration: 15d, reason: 'Hacked Client #1' }
      - { type: BAN, duration: 25d, reason: 'Hacked Client #2' }
      - { type: BAN, duration: perm, reason: 'Hacked Client #3' }
```

| Key | Meaning |
|---|---|
| `slot` | Position in the menu, `0` to `rows * 9 - 1`. Out of range skips the category |
| `permission` | Optional. Restricts who sees and can use the category |
| `expire-after` | Optional. Overrides `escalation.expire-after` for this category |
| `icon.material` | Resolved by name. An unknown name falls back instead of failing |
| `icon.glow` | Adds the enchantment shine |
| `tiers` | 0 previous offences uses the first entry, 1 uses the second, and so on |

Types are `BAN`, `IPBAN`, `MUTE`, `WARN` and `KICK`, and can be mixed inside one category, for
example warn, mute, mute, ban. Durations use `s m h d w mo y` (`30m`, `15d`, `2w`, `6mo`) or
`perm`. `KICK` ignores its duration.

A tier with an unknown type or a malformed duration is skipped with a warning on startup, and a
category left with no valid tiers is skipped entirely.

Icon placeholders: `%target%`, `%offenses%`, `%next_number%`, `%next_type%`, `%next_duration%`,
`%next_reason%`, and `%tiers%`, which expands to one line per tier and highlights the next one.

The menu layout lives at the top of the same file:

| Key | Meaning |
|---|---|
| `menu.title` | Supports `%target%` |
| `menu.rows` | 1 to 6 |
| `menu.filler` | Background item, `enabled`, `material` and `name` |
| `menu.tier-format` / `menu.tier-format-next` | Lines produced by `%tiers%` |
| `menu.history-button` / `menu.close-button` | `slot: -1` hides the button |

### config.yml

| Key | Default | Meaning |
|---|---|---|
| `server-name` | `main` | Stored with every record, to tell servers apart in one database |
| `intercept.enabled` | `true` | Master switch for the overlay |
| `intercept.commands` | ban, tempban, ipban, … | Commands that open the overlay. Add your aliases here |
| `intercept.allow-bypass-permission` | `true` | Lets `gavel.bypass` holders keep the raw LiteBans commands |
| `intercept.deny-self` | `true` | Blocks punishing yourself |
| `intercept.passthrough-without-target` | `true` | `/ban` with no player falls through to LiteBans' usage message |
| `intercept.require-known-player` | `true` | Refuses names that never joined the server |
| `execution.execute-as` | `PLAYER` | `PLAYER` runs the command as the staff member, `CONSOLE` from the console |
| `execution.commands.*` | see below | Command templates, one per type and duration kind |
| `execution.silent-flag` | `-s` | Flag appended for silent punishments |
| `execution.verify-permissions` | `true` | Checks the LiteBans permission before dispatching |
| `execution.permissions.*` | `litebans.ban`, … | Node required per punishment type |
| `execution.post-commands` | empty | Console commands run after a successful punishment |
| `revert.*` | see below | Rolls the escalation counter back when a punishment is lifted |
| `confirm.enabled` | `true` | Second screen before the punishment is applied |
| `confirm.only-permanent` | `false` | Only ask when the resulting punishment is permanent |
| `escalation.expire-after` | `perm` | How long a past offence keeps counting |
| `escalation.on-overflow` | `LAST` | Past the last tier, `LAST` repeats it and `CYCLE` wraps around |
| `database.mode` | `AUTO` | `AUTO`, `LITEBANS`, `MYSQL`, `POSTGRESQL` or `SQLITE`, see [Storage](#storage) |
| `database.table-prefix` | `gavel_` | Table name is `<prefix>offenses` |
| `database.host` `port` `database` `username` `password` `ssl` `properties` | | Used by `MYSQL` and `POSTGRESQL` |
| `database.file` | `gavel.db` | Used by `SQLITE`, relative to the plugin folder |
| `sounds.open` `apply` `deny` | | Sound names. Unknown names are ignored, empty disables |
| `debug` | `false` | Logs every command Gavel dispatches |

#### Command templates

`execution.commands` holds templates rather than hardcoded commands, so a LiteBans build with
different command names only needs a config change:

```yaml
execution:
  commands:
    ban-temp: 'tempban %flags%%target% %duration% %reason%'
    ban-perm: 'ban %flags%%target% %reason%'
```

Placeholders are `%target%`, `%duration%`, `%reason%` and `%flags%`, which already carries its
trailing space when it is not empty. The keys are `ban-temp`, `ban-perm`, `ipban-temp`,
`ipban-perm`, `mute-temp`, `mute-perm`, `warn-temp`, `warn-perm` and `kick`.

`post-commands` accepts `%target%`, `%staff%`, `%category%`, `%type%`, `%duration%`, `%reason%`
and `%offense%`.

#### Permission verification

Bukkit's `dispatchCommand` returns true as long as the command exists; it cannot report that the
command was refused. Without a check, a staff member lacking the LiteBans permission would see a
success message while LiteBans silently declined, and the escalation counter would move even
though nobody was punished.

`execution.verify-permissions` therefore checks the node **before** dispatching and names the
missing one if it fails. LiteBans can also cap the maximum duration a rank may hand out, so if
your tiers reach `perm` the staff member needs permission for permanent punishments, or use
`execution.execute-as: CONSOLE`.

### Lifting a punishment

When a punishment is reversed in LiteBans, the offence behind it should stop counting, otherwise
the next one escalates from a punishment that no longer exists.

```yaml
revert:
  enabled: true
  scope: LATEST
  commands:
    unban: [BAN]
    unbanip: [IPBAN]
    unmute: [MUTE]
    unmuteip: [MUTE]
    unwarn: [WARN]
  permissions:
    unban: 'litebans.unban'
```

These commands are **not** cancelled. LiteBans runs them normally and one tick later Gavel marks
the matching offence inactive.

| Key | Meaning |
|---|---|
| `revert.enabled` | Master switch |
| `revert.scope` | `LATEST` rolls back the most recent matching record, `ALL` every active one |
| `revert.commands` | Command to punishment types it reverts. Add your aliases here |
| `revert.permissions` | Node checked before rolling back, when `execution.verify-permissions` is on |

`LATEST` is the default because an unban reverses one punishment, not the whole record. Console
unbans are handled as well, since no menu is involved, which covers web panels and appeal
plugins. Records are never deleted, only marked inactive, so they stay visible in
`/gavel history`.

### messages.yml

Every string shown to a player, including the confirmation and history menus. Supports `&` colour
codes and `&#rrggbb` hex colours.

Do not rename the `words.boolean-true` and `words.boolean-false` keys to `yes` and `no`: YAML
resolves those to booleans and the lookup stops matching.

## Storage

Gavel keeps its own `gavel_offenses` table. It counts the category ID, not the reason text, so
editing a message does not break the counter.

With `database.mode: AUTO` the plugin reads `plugins/LiteBans/config.yml`:

| LiteBans runs on | Gavel writes to |
|---|---|
| MySQL, MariaDB, PostgreSQL | the same database, table `gavel_offenses` |
| H2 (LiteBans default) | `plugins/Gavel/gavel.db` |

Embedded H2 locks its file exclusively, so a second connection is not possible. Move LiteBans to
MySQL if you want everything in one database.

On a network, note that a local SQLite file is per server, so escalation counters do not follow a
player across servers. A shared MySQL or PostgreSQL database is required for that.

The SQLite, MySQL and PostgreSQL drivers are bundled in the jar. All database work runs off the
main thread.

## Commands

| Command | Permission |
|---|---|
| `/gavel <player>` | `gavel.use` |
| `/gavel history <player>` | `gavel.history` |
| `/gavel clear <player> [category]` | `gavel.admin` |
| `/gavel reload` | `gavel.admin` |
| `/gavel version` | |

`clear` marks the offences inactive instead of deleting them, so the counter resets but the
records stay.

## Permissions

| Permission | Grants |
|---|---|
| `gavel.use` | The overlay opens on punishment commands |
| `gavel.bypass` | Raw LiteBans commands, without the overlay |
| `gavel.category.<id>` | Access to one category |
| `gavel.silent` | Silent punishments with shift-click |
| `gavel.history` | View a player's history |
| `gavel.admin` | `reload` and `clear` |

Note that `gavel.bypass` defaults to false on purpose. A permission plugin granting a blanket `*`
hands it out and the overlay stops opening.

## Development

```bash
./gradlew build
```

Compiles, runs the test suite and produces the shaded jar in `build/libs/`. Requires JDK 11 or
newer; the output targets Java 8.

```bash
./gradlew test
```

The tests cover the pure logic: duration parsing, punishment type resolution and the escalation
engine, including the expiry window and the overflow behaviour. They need no server.

CI runs on every push and pull request. It builds, runs the tests, uploads the report, and fails
if the shaded jar ends up with duplicate zip entries, which Paper's plugin remapper refuses.
Pushing a `v*` tag publishes a release, and only after the build job has passed.

To stop a red build from being merged, enable branch protection on `main` in the repository
settings and require the **Build and test** status check. The workflow reports the failure, but
only branch protection blocks the merge button.

Contributions should stay free of NMS and of any API missing on 1.16.5, which is the
compatibility floor.

## License

[Apache License 2.0](LICENSE).