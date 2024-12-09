package fr.lordfinn.steveparty.client;

import fr.lordfinn.steveparty.service.TokenData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PartyService {
    public final static Map<UUID, TokenData> tokens = new HashMap<>();
}
