package com.ali.carsystem;

/** Maps BRouter 1.7.10 VoiceHint command IDs to navigation semantics. */
final class BrouterManeuverPolicy {
    private BrouterManeuverPolicy() { }

    static boolean isNavigable(int command) {
        return command >= 1 && command <= 18 && command != 12; // 12 = OFFR, handled by off-route logic.
    }

    static String modifier(int command) {
        switch (command) {
            case 2: case 3: case 4: case 17: return "left";
            case 5: case 6: case 7: case 18: return "right";
            case 8: return "keep-left";
            case 9: return "keep-right";
            case 10: case 11: case 15: return "uturn";
            default: return "straight";
        }
    }

    static String type(int command) {
        if (command == 13 || command == 14) return "roundabout";
        if (command == 17 || command == 18) return "exit";
        if (command == 1 || command == 16) return "continue";
        return "turn";
    }
}
