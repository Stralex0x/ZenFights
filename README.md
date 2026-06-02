# ZenFights 2.0.7

**ZenFights** is a high-performance, competitive duel engine built exclusively for the every ecosystem including Paper, Spigot, Bukkit and Purpur. It transforms standard PvP into a highly isolated, professional matchmaking experience, supporting all modern server versions.

Version 2.0.7 introduces network-level combat isolation, dynamic Elo-style "Zen" ratings, a fully integrated AI Practice Bot, and an active Discord gateway bridge.


[![Discord](https://cdn.modrinth.com/data/cached_images/05fe664c676a12a0c6ee7a86b31d81be49b09a5c.png)](https://discord.gg/ReqqvMpuHh)


## 🌟 Key Features

* **Universal Version Support:** Fully optimized for seamless performance across modern PaperMC releases (1.20.x – 1.21.x).
* **Network-Level Arena Isolation:** When a duel begins, bystanders vanish. Fighters are network-isolated, preventing third-party interference, stray projectiles, and visual clutter.
* **Smart Rollback Sandbox:** Explosions from end crystals/beds and broken blocks are visually hidden from the rest of the server and instantly rolled back when the match concludes.
* **AI Practice Bot (Zen Master):** Train your pacing offline. The practice bot features an action-bar virtual health pool (2000 HP), automatic boundary containment, and mimics your selected kit.
* **Advanced Spectator Mode:** Server members can spectate live matches using a dedicated `/fight spec` routing engine that bypasses network invisibility while safely locking their commands.
* **Elo "Zen" Rating System:** A mathematical ranking system. Players start at `3000` Zen, gaining or losing points dynamically based on the skill gap of their opponent.
* **Discord Integration:** A built-in JDA Discord Bot that syncs your server's live match data, offering `/zfbot-top` and `/zfbot-stats` slash commands straight from your Discord server.
* **High-Precision Kit Serialization:** Admins can save their entire current state—**Inventory, Armor, and Offhand**—as custom, reusable kits directly from the game.

---

## 🚀 Getting Started

### Installation
1. Download the latest `ZenFights-2.0.x.jar` file.
2. Ensure you have **PlaceholderAPI** installed on your server.
3. Place ZenFights into your server's `plugins/` folder.
4. Restart your server to generate the default `config.yml`.

### Commands & Permissions

#### Player Commands
| Command | Description | Permission |
| :--- | :--- | :--- |
| `/fight <player>` | Opens the Kit Selection GUI to challenge a player. | `zenfights.fight` |
| `/fight accept <player>` | Accept an incoming duel request. | `zenfights.fight` |
| `/fight practice <kit>` | Start an isolated combat simulation against the AI. | `zenfights.fight` |
| `/fight practice exit` | Forcibly exit your active simulation early. | `zenfights.fight` |
| `/fight spec <player>` | Spectate an active duel in Gamemode 3. | `zenfights.command.spectate` |
| `/fight spec leave` | Safely disconnect from the spectator perspective. | `zenfights.command.spectate` |

#### Admin Commands
| Command | Description | Permission |
| :--- | :--- | :--- |
| `/zf savekit <name>` | Saves your current inventory layout as a global kit. | `zenfights.admin` |
| `/zf deletekit <name>` | Permanently deletes a kit profile. | `zenfights.admin` |
| `/zf listkits` | View all active kit templates loaded in memory. | `zenfights.admin` |
| `/zf reload` | Reloads the configuration and memory state safely. | `zenfights.admin` |
| `/zf loadkit <name>` | Manually equip a saved server kit profile. | `zenfights.base` |

*(Note: Give staff members the `zenfights.spectate.bypasscommands` permission to allow them to execute other server commands while locked in spectator mode).*

---

## 📊 PlaceholderAPI Integration

ZenFights comes with a built-in expansion. You do **not** need to download anything from the eCloud. Use these anywhere (Scoreboards, TAB, Holograms, Chat):

| Placeholder | Output / Description |
| :--- | :--- |
| `%zenfights_zen%` | Displays the player's current Elo/Zen score (Default: 3000). |
| `%zenfights_status%` | Returns `§bIn Fight` or `§7Idle` based on combat state. |
| `%zenfights_wins%` | Total number of recorded duel victories. |
| `%zenfights_losses%` | Total number of recorded duel defeats. |
| `%zenfights_deaths%` | Alias for losses. |
| `%zenfights_matches_played%` | The combined total of all historical matches logged. |

---

## ⚙️ Default Configuration (`config.yml`)

The configuration file handles your physical arena parameters, reward hooks, and database tracking.

```yaml
# ==============================================================================
#                         ZENFIGHTS CONFIGURATION
# ==============================================================================

arena:
  # The maximum blocks a practice bot can wander before bouncing off the border
  border-radius: 15.0

# sandbox-radius: How far the worldborder extends from the duel center
sandbox-radius: 32.0
# spawn-immunity-seconds: Invulnerability window in seconds upon match start
spawn-immunity-seconds: 3
# match-timeout-minutes: Max duration in minutes before a duel is auto-cancelled
match-timeout-minutes: 20

# zen-k-factor: Determines how much Zen is won/lost (higher = more volatile)
zen-k-factor: 32.0
# default-zen: The starting score for new players
default-zen: 3000

# win-commands: Commands executed by Console when a player wins.
# Use %player% to target the winner.
win-commands:
  - "eco give %player% 100"

discord-bot:
  enabled: false
  bot-token: "YOUR_BOT_TOKEN_HERE"
  embed-color-leaderboard: "#FFD700"
  embed-color-stats: "#00FF7F"
```
## Discord Bot Setup

```
🤖 Discord Bot Setup Guide
ZenFights includes a native Discord gateway hook to display leaderboards and live match stats directly in your Discord server.

Creating the Bot: Go to the Discord Developer Portal, create a new application, and generate your Bot Token under the "Bot" tab. Paste this into your config.yml.

Privileged Gateway Intents: Under the "Bot" tab, toggle ON Server Members Intent and Message Content Intent.

Inviting the Bot: Navigate to the OAuth2 > URL Generator tab, select bot and applications.commands scopes, and add the Send Messages and Embed Links permissions. Use the generated link to add it to your server.

Set enabled: true in your config.yml and run /zf reload to register commands.
```
