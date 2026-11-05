# XaliRefer

**XaliRefer** is a referral system for Paper and Folia with a small Velocity plugin. A player shares an address made of their
name, such as `audizian.xalitoria.com`. A new player who joins with it is referred by them, and once the new player has
played long enough the referral is complete: the referrer gets a point, both are told, and the commands you configure run
(a reward, say).

"Played long enough" means **active** time. A player who stands in a farm or leaves the game open does not earn it, see
[How time is counted](#how-time-is-counted).

## Installation

### Requirements

| Plugin | Needed | Purpose |
|---|---|---|
| Velocity plugin (`xali-refer-<version>-velocity.jar`) | Behind Velocity | Tells the servers which address a player joined with. Without a proxy, set `proxy.enabled: false` and the server reads it itself. |
| PlaceholderAPI | No | The `%xalirefer_...%` placeholders. |

The domain has to reach the server like the main address does: a wildcard DNS record (`*.xalitoria.com`) pointing at the
proxy (or the server) makes every `<name>.xalitoria.com` work.

LuckPerms is not needed any more. The data is kept in a database of its own, see [Database](#database).

### Modules

| Module | Description |
|---|---|
| `refer/paper` | The Paper plugin, `xali-refer-<version>-paper.jar`: the referrals, the playtime, commands, storage (Exposed over SQLite or MariaDB/MySQL) and the placeholders. |
| `refer/velocity` | The Velocity plugin, `xali-refer-<version>-velocity.jar`. Sends the address a player used to the server they land on. |
| `refer/common` | What both share: the channel name and the signed message. No platform API; targets Java 17. |

### Building and installing

```console
user $ ./gradlew :refer:paper:shadowJar :refer:velocity:shadowJar
```

Copy `build/libs/refer/xali-refer-<version>-paper.jar` into each server's `plugins` folder, and the Velocity jar into the
proxy's. The descriptors come from the [Gradle plugin](../xali-gradle/README.md); the `paper { }` and `velocity { }` blocks
are in `refer/build.gradle.kts`.

> [!NOTE]
> Behind Velocity, turn on **modern forwarding** (`player-info-forwarding-mode = "modern"` in `velocity.toml`, and the secret
> in Paper's `config/paper-global.yml`). The proxy signs what it tells the server with that secret, so a client cannot
> claim a referral by sending the message itself.

## How it works

1. The new player connects with `audizian.xalitoria.com`. Velocity knows the address they typed (a server behind it sees only
   the proxy's), and sends it, signed, to the server they land on. Without a proxy the server takes it from the connection.
2. The server finds the player the first part of the address names (case does not matter) among the players it has seen,
   and records the referral. A player is referred **once, ever**, by whoever came first. It is refused when the referrer
   is the player themselves (they are told so, once per visit to the network), is unknown, or when the player first joined longer ago than `referral.window`.
   Alts are allowed: accounts that share an address are logged for a person to review (see [the address log](#the-address-log)).
3. While the referred player plays, their active time is counted and written to the database.
4. At `playtime.requirement` the referral is complete, exactly once even if several servers share the database.

The referrer is told when it happens, or the next time they join if they were away.

## How time is counted

Only players who still have a referral to finish are watched, and only while they are online. Everyone else costs nothing.

**AFK.** Any sign of play keeps a player active for `playtime.afk-threshold` (30 seconds by default). Past that with no
sign they are AFK and the time stops counting, until they do something. The signs are moving keys, turning the view, chat,
commands, building, using or hitting something, and clicking in an inventory or dropping or changing the held item.
Movement itself is not read from move events (they fire for every step of every player). It comes from the player's key
presses and from a look at their position and view every `playtime.check-interval`, which reads a few numbers. A listener
for an event does one lookup and returns for anyone who is not watched.

**AFK machines.** Something that moves the player or presses for them (a water stream, a minecart loop, a jump bot, an
auto-clicker, a bot pacing in a pen) shows as activity if only the position or one repeated action is looked at. So
`playtime.machines` judges time in windows (2 minutes by default) and throws away what a failing window earned. A window
passes if

- it shows at least `min-variety` **different kinds** of activity (keys, looking, chat, commands, building, clicking, inventory), and
- if the player stayed within `radius` blocks the whole window, one of them was a **deliberate** one: chat, a command,
  building, using something or an inventory click.

A water stream or a vehicle loop sends no key presses and does not turn the view, so it earns nothing. A bot that repeats
one key or one click has one kind. A bot that presses keys and turns in a pen has no deliberate activity. A player who
builds, fights, talks or walks about passes without noticing. This is best effort: a machine that varies what it does and walks
over a wide area looks like a player. Raise `min-variety` or `radius`, or lengthen the window, to be stricter; and keep what `completion.commands` gives out
small enough that a machine is not worth building.

Time earned in a window is credited when the window ends, so a referral completes up to a window after the last needed second.
Leaving judges the part of a window that is left. With `machines.enabled: false` time counts as it passes.

## Configuration

### Files

| File | Holds |
|---|---|
| `plugins/XaliRefer/config.yml` | Settings, see below. |
| `plugins/XaliRefer/language/<language>.yml` | Every message, written with the defaults on the first start. |
| `plugins/XaliRefer/xalirefer.db` | The data, with `database.type: sqlite`. |

Times are written like `30s`, `5m`, `2h`, `1d` or `1h 30m`. A wrong value stops the plugin from loading, or from reloading, and
names the setting.

### config.yml

| Key | Default | Meaning |
|---|---|---|
| `debug` | `false` | Log what the plugin does. `/refer admin debug` toggles it. |
| `language` | `en-us` | The file in `language/` that messages are read from. |
| `domain` | `xalitoria.com` | Addresses are `<player>.<domain>`. |
| `referral.window` | `1d` | How long after first joining a player can still be referred. `0s` for no limit. |
| `playtime.requirement` | `2h` | Active time a referred player needs. |
| `playtime.afk-threshold` | `30s` | Time without a sign of play after which a player is AFK. |
| `playtime.check-interval` | `5s` | How often watched players are looked at. Not longer than the AFK threshold. |
| `playtime.save-interval` | `1m` | How often the time earned is written to the database. |
| `playtime.machines.enabled` | `true` | Judge windows for AFK machines. |
| `playtime.machines.window` | `2m` | Length of a window. |
| `playtime.machines.radius` | `6` | Blocks a player may range over and still count as standing still. |
| `playtime.machines.min-variety` | `2` | Kinds of activity a window needs (1 to 7). |
| `completion.commands` | empty | Console commands run when a referral completes. `{player}`, `{player_uuid}`, `{referrer}` and `{referrer_uuid}`. |
| `leaderboard.refresh` | `5m` | How often `/refer top` and the placeholders are brought up to date. |
| `leaderboard.per-page` | `10` | Lines per page of `/refer top` and `/refer list`. |
| `log.enabled` | `true` | Keep the address each player joins from, for [`/refer admin log`](#the-address-log). |
| `proxy.enabled` | `true` | The address comes from the Velocity plugin. `false` reads it from the connection (no proxy). |
| `database` | | See [Database](#database). |

**Example.** Give the referrer a month of a rank and some money:

```yaml
completion:
  commands:
    - "lp user {referrer} parent addtemp supporter 30d"
    - "eco give {referrer} 500"
```

### Database

The `database` section is the one the other Xali plugins use (`DatabaseConfig` in [util](../util/README.md)):

| Key | Default | Meaning |
|---|---|---|
| `database.type` | `sqlite` | `sqlite`, `mariadb` or `mysql`. Use `mariadb` for MariaDB: it has its own driver. |
| `database.table-prefix` | `xalirefer_` | Prepended to every table name: letters, digits and underscores. A new prefix means new, empty tables. |
| `database.sqlite.filename` | `xalirefer.db` | The SQLite file. |
| `database.mysql` | | `host`, `port`, `database`, `username`, `password`, `use-ssl` and `pool-size`. |

Servers that point at one MariaDB/MySQL database share their players and referrals: a player can be referred on one server and
finish on another, and a referral completes once however many servers ask. There are four tables: `player` (names, to find
a referrer by name), `referral` (one row per referred player, with the active time), `bonus` and `ip` (the address log).

## Usage

### Commands

`/xalirefer`, also `/refer`, `/referral`, `/xrefer`, `/xref`, `/xreferral` and `/xalireferral`.

| Command | Effect |
|---|---|
| `/refer` or `/refer info [player]` | The address, referrals, place on the leaderboard and, for yourself, how far your own referral is. |
| `/refer ip` | The address to share. |
| `/refer top [page]` | Who has the most referrals. |
| `/refer list [page]` | Your referrals, and how far each one is. |
| `/refer admin give\|take <player> [amount]` | Change a player's bonus referrals. |
| `/refer admin redeem <player>` | Complete a player's referral now. |
| `/refer admin list <player> [page]` | A player's referrals. |
| `/refer admin raffle` | Draw a player, with a ticket for every referral they have. |
| `/refer admin save` | Write the time of players online to the database now. |
| `/refer admin reload`, `debug` | Reload the files. Toggle debug logging. |

### The address log and review

Alts are allowed. Every join writes one row for the pair of player and address (their name then, the first and the latest time),
so accounts that share an address can be found, and a person decides whether a referral between them is fair. The playtime and
the time a referral was completed are the referral's, so they are shown for referred accounts and read "not referred" for the rest.

| Command | Effect |
|---|---|
| `/refer admin log player <player> [page]` | The addresses a player joined from, and their referral. |
| `/refer admin log alts <player> [page]` | Other accounts that joined from any of those addresses, with the addresses they share. |
| `/refer admin log ip <address> [page]` | The accounts that joined from an address. Quote an IPv6 address: `"::1"`. |
| `/refer admin log remove <player>` | Take the referral of the referred player out of the count. |
| `/refer admin log restore <player>` | Put it back. |

A removed referral does not count for the referrer (`/refer info`, `top`, the placeholders and the raffle), earns no more time and
cannot complete, and a player is not told when it is removed other than by `/refer info` and `/refer list` saying it is not
counted. This works the same on one that was complete. The log shows who removed it and when. Restoring puts it back: one
that had not completed carries on from the time it had (and completes at once if that was enough), and a complete one counts again.
Commands that ran when it completed are not undone.

An address is personal data: reading the log needs `xalirefer.log`, and removing or restoring needs `xalirefer.review`.
`log.enabled: false` stops recording (what is logged stays; clear the `ip` table to remove it). A player who uses their
own address is told "You can't use your own referral." once when they join the network.

### Permissions

| Permission | Default | Allows |
|---|---|---|
| `xalirefer.use` | everyone | The commands that show a player their referrals. |
| `xalirefer.admin` | operators | `/refer admin ...`. |
| `xalirefer.log` | operators | `/refer admin log player\|alts\|ip`, the addresses players joined from. |
| `xalirefer.review` | operators | `/refer admin log remove\|restore`. It also needs `xalirefer.log`. |

### Placeholders

With PlaceholderAPI, `%xalirefer_...%`. They answer from memory: the leaderboard as of its last refresh and the time counted for
players online.

| Placeholder | Value |
|---|---|
| `address` | `<name>.<domain>`. |
| `count`, `count_referrals`, `count_bonus` | Total, completed referrals in it, and bonus in it. |
| `position` | Place on the leaderboard; empty when not on it. |
| `playtime`, `playtime_formatted` | Active seconds counted for the player's own referral, and in words. |
| `required`, `required_formatted` | Active seconds a referral needs. |
| `progress` | Percent of the requirement done; `100` when there is nothing to finish. |
| `top_<n>_name`, `top_<n>_count`, `top_<n>_position` | Place `n` of the leaderboard, from 1. |

## Upgrading from the first XaliRefer

The first version (Spigot and Velocity, LuckPerms) stored counts in LuckPerms meta and was never finished. Nothing is carried
over: this version has its own tables, and no longer needs LuckPerms, Plan or the CMI-style AFK code. The old `settings.yml`
and `messages.yml` are not read; `config.yml` and `language/en-us.yml` replace them.

## Development

```console
user $ ./gradlew :refer:paper:test :refer:common:test
```

| Test | Shows |
|---|---|
| `PlaytimeRules` | The AFK threshold and the machine window with a hand-driven clock: a player who plays, one who stops, a water stream, a one-key bot, an auto-clicker, a bot in a pen. |
| `ReferralStore` | The referral rules against SQLite: once per player, time added in the database, completed once, the leaderboard, removing and restoring. |
| `AddressLog` | The address log against SQLite: one row per player and address, alts across shared addresses, IPv6 spelling. |
| `ReferralAddress` | Reading a name out of an address, and the raffle. |
| `ConfigFile` | The defaults, wrong times and the written file. |
| `ProtocolSigning` (common) | A forged, unsigned or altered message is refused. |

The time rules are in `paper/kotlin/.../playtime/Session.kt` and know nothing of the server. `PlaytimeTracker` watches players on
their own scheduler (a region thread on Folia, where the position may be read) and `ActivityListener` feeds it.

## See also

- [util](../util/README.md) — `DatabaseConfig`, messages and YAML files
- [Gradle plugin](../xali-gradle/README.md) — descriptors and loaders
- [Root README](../README.md) — the workspace
