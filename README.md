# Equip Leveling

A comprehensive equipment leveling system for Minecraft Java Edition 1.21.1+. This Fabric mod adds a dynamic progression system where weapons, tools, and armor gain experience and can be enhanced through an enchanting interface.

## Features

### Core System
- **Equipment Component**: Every piece of equipment carries persistent level data including XP, enchantment slots, and bonus slots
- **XP Accrual**: Gain XP based on meaningful actions:
  - **Swords**: Entity kills (scales with max health)
  - **Axes**: Log breaks and entity kills
  - **Pickaxes**: Ore mining (rarity-scaled)
  - **Shovels**: Dirt/sand/gravel/snow breaks
  - **Hoes**: Crop harvests and tilling
  - **Fishing Rods**: Successful fish reels
  - **Armor**: Damage taken by the player

### Leveling System
- Equipment levels up when XP reaches threshold
- XP requirements scale exponentially (configurable base + multiplier)
- Items feature 4 standard enchantment slots + 0-2 bonus slots from loot
- **Mending Enchantment**: Auto-added as bonus slot when all standard slots filled

### Broken Item State
- Items break when durability reaches zero
- Broken items gain red `[BROKEN]` prefix and red tint
- Enchantment effects suppressed
- Repair at Anvil with materials to restore and remove broken state
- Repair cost scales with item level

### Enchanting Interface
- Custom enchanting table with 3 weighted offers:
  - New enchantments for empty slots
  - Upgrades to existing enchantments
  - Rare legendary tier promotions
- **Reroll Button**: Spend XP levels to generate new offers
- Cost scales with filled slot count

### Glint System
- Items only show enchantment glint when ready to level up
- Glint disappears immediately after leveling

### Loot Integration
- Enchanted loot spawns with 1-2 bonus slots (gold-prefixed)
- Bonus slots are upgradeable like standard slots
- Enchanted books are disabled globally

### Configuration
- Per-category base XP values
- Global XP scaling multiplier
- XP display threshold
- Durability restore percentage
- Reroll costs per slot count
- Material tier ladder
- Enchantment offer weights
- Anvil repair costs
- Keep equipment on death toggle
- Broken mechanic toggle

## Installation

1. Download the latest mod JAR
2. Place in your `.minecraft/mods` folder
3. Requires: Fabric Loader + Fabric API
4. Optional: Mod Menu for config UI

## Building

```bash
./gradlew build
```

Output JAR: `build/libs/equip_leveling-*.jar`

## License

MIT License - See LICENSE file for details
