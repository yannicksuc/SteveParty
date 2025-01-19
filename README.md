# 🎉 SteveParty Mod

Welcome to the **SteveParty Mod**! This mod enhances your Minecraft experience by introducing exciting party mechanics, custom entities, and unique gameplay features. Below you'll find detailed information about the various components of the mod.

## 📂 Table of Contents
- [Installation](#installation)
- [Features](#features)
- [File Overview](#file-overview)
    - [CustomFireworkRocketEntity](#customfireworkrocketentity)
    - [LivingEntityMixin](#livingentitymixin)
    - [MerchantEntityMixin](#merchantentitymixin)
    - [PlayerFallMixin](#playerfallmixin)
    - [StatusEffectMixin](#statuseffectmixin)
    - [TokenEntityMixin](#tokenentitymixin)
    - [TrackedDataHandlerRegistryMixin](#trackeddatahandlerregistrymixin)
    - [ModParticles](#modparticles)
    - [ParticleUtils](#particleutils)
    - [ArrowParticlesPayload](#arrowparticlespayload)
    - [EnchantedCircularParticlePayload](#enchantedcircularparticlepayload)
    - [ModPayloads](#modpayloads)
    - [PartyDataPayload](#partydatapayload)
    - [TokenPayload](#tokenpayload)
    - [UpdateColoredTilePayload](#updatecoloredtilepayload)
    - [UUIDCodec](#uuidcodec)
    - [CustomizableMerchantScreenHandler](#customizablemerchantscreenhandler)
    - [ModScreens](#modscreens)
    - [TileScreenHandler](#tilscreenhandler)
    - [MobOwnershipPersistentState](#mobownershippersistentstate)
    - [TokenData](#tokendata)
    - [BasicGameGeneratorStep](#basicgamegeneratorstep)
    - [BonusPartyStep](#bonuspartystep)
    - [EventPartyStep](#eventpartystep)
    - [MiniGamePartyStep](#minigamepartystep)
    - [StartRollsStep](#startrollsstep)
    - [TokenTurnPartyStep](#tokenturnpartystep)
    - [MoveTokenCommand](#movetokencmd)
    - [BoardSpaceBehaviorComponent](#boardspacebehaviorcomponent)
    - [EntityDataComponent](#entitydatacomponent)
    - [ModItems](#moditems)
    - [AbstractBoardSpaceSelectorItem](#abstractboardspaceselectoritem)
    - [WrenchItem](#wrenchitem)
    - [BoardSpaceBehaviorItem](#boardspacebehavioritem)
    - [StartTileBehaviorItem](#starttilebehavioritem)
    - [StopBoardSpaceBehaviorItem](#stopboardspacebehavioritem)
    - [TokenizedEntityInterface](#tokenizedentityinterface)
    - [TokenStatus](#tokenstatus)
    - [AttractionSimulation](#attractionsimulation)
    - [DiceEntity](#diceentity)
    - [DirectionDisplayEntity](#directiondisplayentity)
    - [HidingTraderEntity](#hidingtraderentity)
    - [HidingTraderScreen](#hidingtraderscreen)
    - [Tile](#tile)
    - [TriggerPoint](#triggerpoint)
    - [PartyController](#partycontroller)
    - [PartyControllerEntity](#partycontrollerentity)
    - [PartyData](#partydata)
    - [Task](#task)
    - [TaskScheduler](#taskscheduler)
    - [TickableBlockEntity](#tickableblockentity)
    - [VoxelShapeUtils](#voxelshapeutils)
    - [EntitiesUtils](#entitiesutils)
    - [MessageUtils](#messageutils)

## 🚀 Installation
To install the SteveParty mod, follow these steps:
1. Download the latest version of the mod from the releases page.
2. Place the mod file into the `mods` folder of your Minecraft installation.
3. Launch Minecraft with the Fabric or Forge profile.

## 🌟 Features
- Custom entities like `HidingTraderEntity` and `DiceEntity`.
- Unique gameplay mechanics involving tokens and party steps.
- Enhanced interactions with blocks and items.
- Custom sound effects and particle systems.

## 📁 File Overview

### CustomFireworkRocketEntity
- **Purpose**: Represents a custom firework rocket entity with adjustable lifetime.

### LivingEntityMixin
- **Purpose**: Extends the `LivingEntity` class to add custom behavior when status effects are removed.

### MerchantEntityMixin
- **Purpose**: Enhances the trading functionality of merchants by integrating with cash registers.

### PlayerFallMixin
- **Purpose**: Modifies player fall behavior to spawn a `HidingTraderEntity` when landing on a specific block.

### StatusEffectMixin
- **Purpose**: Extends the `StatusEffect` class to allow custom actions when effects are removed.

### TokenEntityMixin
- **Purpose**: Adds functionality for tokenized entities, allowing them to be owned and managed.

### TrackedDataHandlerRegistryMixin
- **Purpose**: Registers custom data handlers for entities.

### ModParticles
- **Purpose**: Defines and registers custom particle types for the mod.

### ParticleUtils
- **Purpose**: Provides utility methods for handling particle effects.

### ArrowParticlesPayload
- **Purpose**: Represents a custom payload for arrow particle effects.

### EnchantedCircularParticlePayload
- **Purpose**: Represents a custom payload for enchanted circular particle effects.

### ModPayloads
- **Purpose**: Defines and registers custom payload types for network communication.

### PartyDataPayload
- **Purpose**: Handles custom payloads related to party data.

### TokenPayload
- **Purpose**: Manages the serialization and deserialization of token data.

### UpdateColoredTilePayload
- **Purpose**: Represents a custom payload for updating tile colors.

### UUIDCodec
- **Purpose**: Provides a codec for serializing and deserializing UUIDs.

### CustomizableMerchantScreenHandler
- **Purpose**: Manages the screen interface for customizable merchants.

### ModScreens
- **Purpose**: Registers custom screen handlers for the mod.

### TileScreenHandler
- **Purpose**: Manages the screen interface for tile-based inventories.

### MobOwnershipPersistentState
- **Purpose**: Manages the persistent ownership state of mobs.

### TokenData
- **Purpose**: Manages data related to tokens, including ownership and attributes.

### BasicGameGeneratorStep
- **Purpose**: Manages the initial steps of a game.

### BonusPartyStep
- **Purpose**: Represents a bonus step in the party game.

### EventPartyStep
- **Purpose**: Represents an event step in the party game.

### MiniGamePartyStep
- **Purpose**: Manages mini-game steps in the party game.

### StartRollsStep
- **Purpose**: Manages the initial rolling phase of the party game.

### TokenTurnPartyStep
- **Purpose**: Manages the turn mechanics of a token in the party game.

### MoveTokenCommand
- **Purpose**: Registers a command for moving tokens based on dice rolls.

### BoardSpaceBehaviorComponent
- **Purpose**: Represents the behavior of a board space.

### EntityDataComponent
- **Purpose**: Manages entity data in a structured format.

### ModItems
- **Purpose**: Registers custom items for the mod.

### AbstractBoardSpaceSelectorItem
- **Purpose**: Represents a base item for selecting board spaces.

### WrenchItem
- **Purpose**: Represents a wrench item for managing board spaces.

### BoardSpaceBehaviorItem
- **Purpose**: Represents a specific behavior for board space items.

### StartTileBehaviorItem
- **Purpose**: Defines the behavior of a starting tile.

### StopBoardSpaceBehaviorItem
- **Purpose**: Defines the behavior of a stop board space.

### TokenizedEntityInterface
- **Purpose**: Defines a contract for tokenized entities.

### TokenStatus
- **Purpose**: Manages the status flags for tokens.

### AttractionSimulation
- **Purpose**: Simulates the movement of entities towards a target.

### DiceEntity
- **Purpose**: Represents a custom dice entity in the game.

### DirectionDisplayEntity
- **Purpose**: Represents a display entity that indicates direction.

### HidingTraderEntity
- **Purpose**: Represents a custom trader entity in the game.

### HidingTraderScreen
- **Purpose**: Represents the screen interface for trading with the hiding trader.

### Tile
- **Purpose**: Represents a custom tile block in the game.

### TriggerPoint
- **Purpose**: Represents a trigger point block in the game.

### PartyController
- **Purpose**: Manages the overall party mechanics and interactions.

### PartyControllerEntity
- **Purpose**: Represents a custom entity that controls party mechanics.

### PartyData
- **Purpose**: Manages the data associated with a party.

### Task
- **Purpose**: Represents a scheduled task in the game.

### TaskScheduler
- **Purpose**: Manages the scheduling of tasks in the game.

### TickableBlockEntity
- **Purpose**: Interface for block entities that require ticking behavior.

### VoxelShapeUtils
- **Purpose**: Provides utility methods for handling voxel shapes.

### EntitiesUtils
- **Purpose**: Provides utility methods for handling player entities.

### MessageUtils
- **Purpose**: Provides utility methods for sending messages to players.

## 🎉 Contributing
We welcome contributions to the SteveParty mod! If you have ideas for new features, bug fixes, or improvements, feel free to submit a pull request or open an issue.

## 📄 License
This project is licensed under the MIT License. See the LICENSE file for more details.

## 📞 Contact
For any inquiries or support, please reach out to the project maintainers via the project's GitHub page.

---

Thank you for checking out the SteveParty mod! We hope you enjoy the new features and enhancements it brings to your Minecraft experience! 🎮
```
