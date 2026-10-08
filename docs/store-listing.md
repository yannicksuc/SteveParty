![Steve Party Maker](https://raw.githubusercontent.com/yannicksuc/SteveParty/master/art/logo/steve_party_maker_banner.gif)

# 🎲 Steve Party Maker

**A party board game you build inside Minecraft.**
Lay out a board block by block, turn any mob into a pawn, roll the dice, play mini-games and race for stars.
Everything is crafted in survival, and redstone can drive almost all of it.

![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-62B47A?logo=minecraft&logoColor=white) ![Fabric](https://img.shields.io/badge/Loader-Fabric-DBD0B4) ![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)

---

## ✨ Features

### 🗺️ Build your own board

- **Board spaces** in several sizes and slopes, stamped with their colour, joined by waypoints and junctions.
- **Cartridges** give each space its behaviour: move forward or back, teleport, open a shop, replay a turn…
- A **wrench** to trace the path and a **redstone router** to wire spaces to your own contraptions.

![Board spaces, cartridges, waypoint, junction, wrench and redstone router](https://raw.githubusercontent.com/yannicksuc/SteveParty/master/docs/screenshots/board.png)
![The Wrench in hand traces the path between the board spaces](https://raw.githubusercontent.com/yannicksuc/SteveParty/master/docs/screenshots/board-wrench.png)
![A board space's interface: its Teleport cartridge and its settings](https://raw.githubusercontent.com/yannicksuc/SteveParty/master/docs/screenshots/tile-cartridge-ui.png)

### ♟️ Any mob can be a pawn

Point the **Tokenizer Wand** at a mob and it becomes a pawn. Store it in a token and set it down again later.

![Ten mobs turned into pawns on board spaces, the Tokenizer Wand and tokens](https://raw.githubusercontent.com/yannicksuc/SteveParty/master/docs/screenshots/pawns.png)

### 🎮 Run a full party

- The **Party Controller** holds the turn order and the program of each turn, with cards to plan it.
- Players roll **single, double or triple dice**, move their pawns, and earn **coins** and **stars**.
- A clean in-game HUD: the turn strip, the score table and the announcements, each placeable on screen.
- The **finish pole** and the **podiums** close the game: jump to the top of the flag pole, then rank everyone.

![A party running: the turn strip, the announcement and the score table](https://raw.githubusercontent.com/yannicksuc/SteveParty/master/docs/screenshots/hud.png)
![The Party Controller's dashboard during a party](https://raw.githubusercontent.com/yannicksuc/SteveParty/master/docs/screenshots/party-controller.png)
![Party Controller, Step Controller, Party Bell, Piggy Bank, dice, coins, stars and party cards](https://raw.githubusercontent.com/yannicksuc/SteveParty/master/docs/screenshots/party.png)
![Goal poles with flags, gold, silver and bronze podiums](https://raw.githubusercontent.com/yannicksuc/SteveParty/master/docs/screenshots/finish-pole.png)

### 🏆 Mini-games, safe in survival

- A **Mini-game Page** describes a mini-game: its zone, its formats (2 to 4 teams, free-for-all or everyone
  together) and the pipes that take the players in and out.
- The **Mini-game Controller** starts with practice rounds; the real round begins once every player is ready.
- During a round each player gets an inventory that exists only for the round, the zone is sealed, and every
  change made inside it is undone afterwards. No duplication, no griefing, no cleanup.

![A mini-game arena, copper, iron and golden mini-game pipes, the Mini-game Controller and pages](https://raw.githubusercontent.com/yannicksuc/SteveParty/master/docs/screenshots/minigames.png)
![The Mini-game Page editor: picture, title, formats, zone and description](https://raw.githubusercontent.com/yannicksuc/SteveParty/master/docs/screenshots/minigame-page.png)

### 🎲 Dice Forge

Carve your own dice faces and forge custom dice.

![The Dice Forge's screen: faces in a ring, star fragments, modules](https://raw.githubusercontent.com/yannicksuc/SteveParty/master/docs/screenshots/dice-forge-screen.png)
![The Dice Forge, dice, forged dice, dice faces and modules](https://raw.githubusercontent.com/yannicksuc/SteveParty/master/docs/screenshots/dice-forge.png)

### 🛒 Shops

A stall, a cash register and a shopkeeper key to run a shop, and the **Boxed Trader**, a villager hiding in a
cardboard box, to sell your shop's offers.

![Trading Stall, Cash Register, Shopkeeper Key and five Boxed Traders](https://raw.githubusercontent.com/yannicksuc/SteveParty/master/docs/screenshots/shops.png)
![Trading with a Boxed Trader: the offers of his stall](https://raw.githubusercontent.com/yannicksuc/SteveParty/master/docs/screenshots/boxed-trader-ui.png)

### ⭐ The Mula

A small flying star creature. Feed it, watch it grow and dance around the Dice Forge, and look for new ones with
the **Telescope** during shooting-star nights.

![Mulas dancing around the Dice Forge at night, two Telescopes](https://raw.githubusercontent.com/yannicksuc/SteveParty/master/docs/screenshots/mula.png)
![Mulas dancing around the Dice Forge in daylight](https://raw.githubusercontent.com/yannicksuc/SteveParty/master/docs/screenshots/mula-day.png)
![Mulas dancing their dances around the Dice Forge: Rose Window, Infinity, Planets and Moons, Moonlight Waltz](https://raw.githubusercontent.com/yannicksuc/SteveParty/master/docs/screenshots/mula-dances.gif)

### 🧱 Decoration

Toy-like plastic blocks in 16 colours, plastic and glass pipes you can travel through, stencils to paint or engrave
patterns, and polished, bevelled and checkered building blocks.

![Plastic blocks in 16 colours, pipes, road and easel signs, polished and checkered blocks](https://raw.githubusercontent.com/yannicksuc/SteveParty/master/docs/screenshots/decoration.png)
![Easel signs, wooden panels, rock signs, plastic road signs and stencil paint](https://raw.githubusercontent.com/yannicksuc/SteveParty/master/docs/screenshots/signs.png)
![The Stencil Hammer stamps a red mushroom on the floor](https://raw.githubusercontent.com/yannicksuc/SteveParty/master/docs/screenshots/stencil-hammer.gif)
![Plastic, windowed, stained glass, glass and mini-game pipes, and a small pipe network](https://raw.githubusercontent.com/yannicksuc/SteveParty/master/docs/screenshots/pipes.png)

---

## 📦 Requirements

- Minecraft **1.21.1**, Fabric Loader **0.16.9+**
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [GeckoLib](https://modrinth.com/mod/geckolib) **4.7.3+**
- Recommended: [REI](https://modrinth.com/mod/rei) to browse the recipes in game

Install it on **both the client and the server**.

## 📖 Documentation

The [wiki](https://github.com/yannicksuc/SteveParty/wiki) (in French) documents every block, item and recipe.
Bugs and ideas: [GitHub issues](https://github.com/yannicksuc/SteveParty/issues).

---

*Steve Party Maker is an independent project. It is not affiliated with Mojang, Microsoft or Nintendo.*
