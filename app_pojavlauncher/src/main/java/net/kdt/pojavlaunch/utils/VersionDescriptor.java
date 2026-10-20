package net.kdt.pojavlaunch.utils;

import java.util.Locale;

/**
 * Helpers to describe a Minecraft version/loader pair stored in an instance version id.
 */
public final class VersionDescriptor {
    private VersionDescriptor() {}

    /** Human readable loader name for a version id, e.g. "Forge 49.1.0" */
    public static String loaderName(String versionId) {
        if (versionId == null) return "Vanilla";
        String lower = versionId.toLowerCase(Locale.ROOT);
        if (lower.contains("neoforge")) return "NeoForge " + tailAfter(lower, "neoforge");
        if (lower.contains("forge")) return "Forge " + tailAfter(lower, "forge");
        if (lower.contains("fabric")) return "Fabric " + tailAfter(lower, "fabric");
        if (lower.contains("quilt")) return "Quilt " + tailAfter(lower, "quilt");
        if (lower.contains("optifine")) return "OptiFine " + tailAfter(lower, "optifine");
        return "Vanilla";
    }

    /** Loader keyword used for list filters: vanilla/forge/neoforge/fabric/quilt */
    public static String loaderFilterKey(String versionId) {
        if (versionId == null) return "vanilla";
        String lower = versionId.toLowerCase(Locale.ROOT);
        if (lower.contains("neoforge")) return "neoforge";
        if (lower.contains("forge")) return "forge";
        if (lower.contains("fabric")) return "fabric";
        if (lower.contains("quilt")) return "quilt";
        return "vanilla";
    }

    /** The Minecraft base version part of an instance version id */
    public static String minecraftVersion(String versionId) {
        if (versionId == null) return "";
        if (versionId.startsWith("latest_release")) return "Latest Release";
        if (versionId.startsWith("latest_snapshot")) return "Latest Snapshot";
        int cut = versionId.indexOf('-');
        if (cut > 0) return versionId.substring(0, cut);
        return versionId;
    }

    private static String tailAfter(String lower, String keyword) {
        int idx = lower.lastIndexOf(keyword + "-");
        if (idx < 0) return "";
        return lower.substring(idx + keyword.length() + 1).trim();
    }
}
