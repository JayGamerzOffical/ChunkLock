## 🌐 ChunksLock - New Way to play Minecraft Unlocked

**ChunksLock** is a highly configurable Minecraft plugin designed to create a unique survival experience for every
player. When players join your server, they are assigned a **private world** (based on a template) where they can unlock
chunks using **customizable item requirements** or randomly chunks. Server admins have full control over chunk settings,
required items,
chunk border limits, protections, and more.

> Ideal for **progression-based**, **survival**, or **challenge**-oriented Minecraft servers!

--- 

## 📖 Overview

* 🔓 Players start in a **separate, personal world**.
* 🌍 Each world is cloned from a **template world**).
* 🧩 Players must **unlock nearby chunks** using specific items.
* 🛠️ Admins can define **custom required items** for unlocking chunks.
* ⚙️ Highly **configurable**, with full message customization, gradients, protections, and border dimensions.

---

## ✨ Features

* 🔄 **Per-player world generation** from a preconfigured template.
* 🧱 **Chunk unlocking** system using item-based requirements.
* 🛡️ **Protected locked chunks** (block break/place, interact, item drop).
* 🎨 **Hex and gradient color support** for prefixes and messages.
* 🔊 **Sound feedback** and **unlock animations**.
* 📦 Placeholder API support for `chunklock_totalChunkUnlocked` for more let me know.
* 🎯 Custom world teleport delay & fallback lobby.

---

## 💬 Commands

### Player Commands

| Command                 | Description                                      |
|-------------------------|--------------------------------------------------|
| `/chunklock join`       | Join or teleport to your personal chunk world.   |
| `/chunklock quit`       | Leave and return to lobby world.                 |
| `/chunklock resetworld` | Reset your chunk world (admin config dependent). |

### Admin Commands

| Command                                                | Description                                |
|--------------------------------------------------------|--------------------------------------------|
| `/chunklock admin setchunk`                            | Set the current chunk for unlocking logic. |
| `/chunklock admin setlobby`                            | Set the fallback lobby location.           |
| `/chunklock admin reload`                              | Reload the configuration.                  |
| `/chunklock admin additem`                             | Add required items for unlocking chunks.   |
| `/chunklock admin removeitem`                          | Remove required items.                     |
| `/chunklock admin border get`                          | Get current border dimensions.             |
| `/chunklock admin border set <height/width/linewidth>` | Set world border properties.               |

> 🧠 Includes intelligent tab completion for ease of use!

---

## 🔐 Permissions

| Permission        | Description                       |
|-------------------|-----------------------------------|
| `chunklock.admin` | Allows use of all admin commands. |

---

## 🔧 Configuration Highlights (`config.yml`)

```yaml
prefix: "&#b700fdC&#c000fah&#c900f8u&#d200f6n&#db00f3k&#e400f1L&#ed00efo&#f600ecc&#ff00eak &7>> "
max-distance-from-spawn: 100
restricted-worlds: [ "world", "hub" ]
template-world-name: "template_world"
auto-chunk-assignment: true
floating-item-movement: false
unlockAnimationEnabled: true
teleport-on-join: true
fallback-lobby-enabled: true
delay-seconds: 5
protection:
  prevent-block-break: true
  prevent-block-place: true
  prevent-interaction: true
  prevent-item-drop: true
sound:
  unlock: { name: "ENTITY_PLAYER_LEVELUP", volume: 1.0, pitch: 1.0 }
  teleport: { name: "ENTITY_ENDERMAN_TELEPORT", volume: 1.0, pitch: 1.0 }
  unlocking-tick: { name: "BLOCK_NOTE_BLOCK_PLING", volume: 1.0, pitch: 1.0 }
messages:
  chunk-locked: "&cThis chunk is locked! Required items: {items}"
  chunk-unlocked: "&aYou unlocked the chunk!"
  too-far: "&cYou can't unlock chunks more than {max} chunks from spawn!"
  teleporting: "&aTeleporting to your world..."
  starting: "&eTeleporting to the world in {seconds} seconds..."
  countdown: "&bTeleporting in {seconds} second(s)..."
  done: "&aTeleported!"
  error: "&cSomething went wrong!"
  missing-template: "&cNo template configured. Sending to lobby."
  no-location: "&cPlease first set a chunk!"
```

You can define:

* 🔑 **Minimum and maximum required items** per chunk unlock.
* 🏞️ Chunk-specific **biome settings**.
* 🎨 Full **message customization** with gradients and placeholders.
* 📏 Custom **world border** dimensions.

---

## 📹 Video Tutorial

> 🎥 *Coming Soon...*
> A full YouTube tutorial will be available here.
> So make sure subscribe to my YT Channel [JayGamerz ](https://www.youtube.com/jaygamerz)



---

## 🔌 Placeholders (PlaceholderAPI)

| Placeholder                      | Description                                    |
|----------------------------------|------------------------------------------------|
| `%chunklock_totalChunkUnlocked%` | Shows how many chunks the player has unlocked. |

---

## 🧰 Template World and Plugin Setup

To ensure proper world cloning:

0. use `/chunklock admin setchunk` - on your template world to set default chunk also this location will use to teleport
   players in this world then and `/chunklock admin setlobby` - to se lobby.
1. Upload your World data folder in ChunkLock/Here.
2. **MUST** Update that world's name in your `template-world-name` in config (e.g., `template_world`).
3. **Delete** the `uid.dat` file from your `template_world` folder.
4. Restart or reload the server to apply changes.

> 🔒 Each player's personal world is based on this **template**.

---

## ✅ Dependencies

* **Java 17+**
* **PaperMC (1.20+)**
* Optional: [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) for placeholder support

---

## 🛠️ Planned Features

* ⛏️ GUI-based item setup for unlocking
* 📊 Stats and leaderboards for unlocked chunks
* 🧱 Biome-based required item mapping

---

## 🗨️ Support

For bugs, suggestions, or support:

* Discord: https://discord.gg/72XVvJx5qv
* GitHub Issues: https://github.com/JayGamerzOffical/ChunkLock/issues

--- 

## 📝 License

This plugin is free to use and modify for private servers. Contact the author for commercial use or republishing
permissions.
