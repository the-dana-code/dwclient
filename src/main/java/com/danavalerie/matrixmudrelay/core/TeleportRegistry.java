package com.danavalerie.matrixmudrelay.core;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TeleportRegistry {
    private static final List<TeleportLocation> LESA_TELEPORTS = List.of(
            new TeleportLocation("am-fiddleys-music", 1, 774, 323),
            new TeleportLocation("am-milords", 1, 1082, 298),
            new TeleportLocation("am-pishe", 1, 578, 480),
            new TeleportLocation("am-rimwards-gate", 1, 452, 200),
            new TeleportLocation("badass", 38, 1423, 224),
            new TeleportLocation("balance", 40, 269, 135),
            new TeleportLocation("banditcamp", 38, 309, 237),
            new TeleportLocation("barbariancamp-summer", 38, 2125, 103),
            new TeleportLocation("barbariancamp-winter", 46, 1737, 1063),
            new TeleportLocation("bi", 21, 105, 73),
            new TeleportLocation("blackglass", 46, 374, 257),
            new TeleportLocation("bleak", 45, 1213, 260),
            new TeleportLocation("bp", 17, 580, 414),
            new TeleportLocation("bp-dojo", 17, 272, 106),
            new TeleportLocation("bp-fish", 17, 875, 901),
            new TeleportLocation("bp-ninja", 17, 1168, 146),
            new TeleportLocation("bp-tunawalk", 17, 1070, 748),
            new TeleportLocation("brassneck", 38, 1327, 87),
            new TeleportLocation("chronides", 48, 473, 199),
            new TeleportLocation("death", 22, 42, 114),
            new TeleportLocation("dj", 23, 438, 285),
            new TeleportLocation("doctor", 3, 944, 316),
            new TeleportLocation("drum", 1, 718, 802),
            new TeleportLocation("ephebe", 25, 379, 349),
            new TeleportLocation("escrow", 46, 688, 669),
            new TeleportLocation("forest-pishe", 38, 1382, 395),
            new TeleportLocation("genua", 27, 400, 284),
            new TeleportLocation("goosegate", 1, 522, 998),
            new TeleportLocation("granny", 38, 1340, 182),
            new TeleportLocation("hillshire", 45, 1358, 467),
            new TeleportLocation("holywood", 45, 98, 111),
            new TeleportLocation("horse", 25, 290, 200),
            new TeleportLocation("jobs", 1, 699, 905),
            new TeleportLocation("klatch-foreign-legion", 31, 735, 515),
            new TeleportLocation("lancre", 38, 1351, 589),
            new TeleportLocation("lanfear", 1, 774, 578),
            new TeleportLocation("madstoat", 38, 1229, 357),
            new TeleportLocation("madwolf", 38, 1768, 667),
            new TeleportLocation("nowhere", 45, 243, 760),
            new TeleportLocation("oasis", 31, 621, 1011),
            new TeleportLocation("oc", 38, 827, 223),
            new TeleportLocation("pekanford", 45, 1416, 617),
            new TeleportLocation("pishe", 45, 1416, 617),
            new TeleportLocation("postoffice", 51, 99, 125),
            new TeleportLocation("razorback", 38, 1769, 393),
            new TeleportLocation("satorsquare", 1, 1040, 648),
            new TeleportLocation("scrogden", 45, 1078, 305),
            new TeleportLocation("scry", 1, 858, 1054),
            new TeleportLocation("sheepridge", 45, 618, 574),
            new TeleportLocation("slice", 38, 1979, 280),
            new TeleportLocation("slipperyhollow", 54, 159, 123),
            new TeleportLocation("stickenplace", 1, 172, 816),
            new TeleportLocation("stolat", 39, 367, 222),
            new TeleportLocation("tosg", 9, 221, 224)
    );
    private static final CharacterTeleports DEFAULT = new CharacterTeleports(true, List.of());
    private static final Map<String, CharacterTeleports> BY_CHARACTER = Map.of(
            "lesa", new CharacterTeleports(true, LESA_TELEPORTS),
            "maemot", new CharacterTeleports(false, List.of())
    );

    private TeleportRegistry() {
    }

    public static CharacterTeleports forCharacter(String characterName) {
        if (characterName == null || characterName.isBlank()) {
            return DEFAULT;
        }
        String key = characterName.trim().toLowerCase(Locale.ROOT);
        return BY_CHARACTER.getOrDefault(key, DEFAULT);
    }

    public record CharacterTeleports(boolean reliable, List<TeleportLocation> teleports) {
    }

    public record TeleportLocation(String name, int mapId, int x, int y) {
    }
}
