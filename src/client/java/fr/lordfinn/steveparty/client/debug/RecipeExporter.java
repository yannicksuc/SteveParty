package fr.lordfinn.steveparty.client.debug;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;

import java.util.*;

import static net.minecraft.registry.Registries.ITEM;

public class RecipeExporter {

    private static final char[] fallbackSymbols = {'@', '#', '$', '%', '&', '*', '+', '-', '='};

    public static void exportRecipe(MinecraftClient client) {
        if (!(client.player.currentScreenHandler instanceof net.minecraft.screen.CraftingScreenHandler craftingHandler)) {
            client.player.sendMessage(Text.literal("Open a crafting table first."), true);
            return;
        }

        DefaultedList<Slot> slots = craftingHandler.slots;
        slots.removeFirst(); // Remove output slot

        String[] pattern = new String[3];
        Map<String, String> keyMap = new LinkedHashMap<>();
        Map<String, Character> usedSymbols = new HashMap<>();
        Set<Character> usedChars = new HashSet<>();
        int fallbackIndex = 0;

        for (int row = 0; row < 3; row++) {
            StringBuilder sb = new StringBuilder();
            for (int col = 0; col < 3; col++) {
                int slotIndex = row * 3 + col;
                ItemStack stack = slots.get(slotIndex).getStack();
                if (stack.isEmpty()) {
                    sb.append(" ");
                    continue;
                }

                String id = ITEM.getId(stack.getItem()).toString();
                if (!usedSymbols.containsKey(id)) {
                    // Try first letter uppercase
                    char symbol = Character.toUpperCase(stack.getName().getString().charAt(0));
                    if (usedChars.contains(symbol)) {
                        // Try lowercase
                        symbol = Character.toLowerCase(symbol);
                        if (usedChars.contains(symbol)) {
                            // Use fallback symbol
                            if (fallbackIndex >= fallbackSymbols.length) fallbackIndex = 0;
                            symbol = fallbackSymbols[fallbackIndex++];
                        }
                    }
                    usedSymbols.put(id, symbol);
                    usedChars.add(symbol);
                }

                sb.append(usedSymbols.get(id));
            }
            pattern[row] = sb.toString();
        }

        // Use crafting table output as result
        ItemStack resultStack = craftingHandler.getSlot(0).getStack();
        if (resultStack.isEmpty()) {
            client.player.sendMessage(Text.literal("Crafting table output is empty!"), true);
            return;
        }
        String resultId = ITEM.getId(resultStack.getItem()).toString();

        // Build JSON
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"type\": \"minecraft:crafting_shaped\",\n");
        json.append("  \"fabric:type\": \"minecraft:crafting_shaped\",\n");
        json.append("  \"pattern\": [\n");
        for (int i = 0; i < 3; i++) {
            json.append("    \"" + pattern[i] + "\"");
            if (i < 2) json.append(",");
            json.append("\n");
        }
        json.append("  ],\n  \"key\": {\n");

        int i = 0;
        for (Map.Entry<String, Character> entry : usedSymbols.entrySet()) {
            json.append("    \"" + entry.getValue() + "\": \"" + entry.getKey() + "\"");
            if (i < usedSymbols.size() - 1) json.append(",");
            json.append("\n");
            i++;
        }

        json.append("  },\n");
        json.append("  \"result\": {\n");
        json.append("    \"id\": \"" + resultId + "\"\n");
        json.append("  }\n");
        json.append("}");

        // Copy to clipboard
        copyToClipboard(json.toString());
        client.player.sendMessage(Text.literal("Recipe copied to clipboard!"), true);
    }

    public static void copyToClipboard(String text) {
        try {
            MinecraftClient.getInstance().keyboard.setClipboard(text);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
