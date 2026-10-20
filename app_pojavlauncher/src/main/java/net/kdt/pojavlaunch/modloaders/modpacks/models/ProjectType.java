package net.kdt.pojavlaunch.modloaders.modpacks.models;

/**
 * The four kinds of project the browse screens can show. The values are also
 * stored in favorites, so they must stay stable.
 */
public final class ProjectType {
    public static final int MODPACK = 0;
    public static final int MOD = 1;
    public static final int RESOURCE_PACK = 2;
    public static final int SHADER = 3;

    private ProjectType() {}

    /** Project type name used by the Modrinth search facets */
    public static String modrinthName(int projectType) {
        switch (projectType) {
            case RESOURCE_PACK: return "resourcepack";
            case SHADER: return "shader";
            case MOD: return "mod";
            case MODPACK:
            default: return "modpack";
        }
    }

    /** CurseForge class id for the project type */
    public static int curseforgeClassId(int projectType) {
        switch (projectType) {
            case RESOURCE_PACK: return 12;
            case SHADER: return 6552;
            case MOD: return 6;
            case MODPACK:
            default: return 4471;
        }
    }

    /** Folder inside the game directory that receives the downloaded file */
    public static String installFolder(int projectType) {
        switch (projectType) {
            case RESOURCE_PACK: return "resourcepacks";
            case SHADER: return "shaderpacks";
            case MOD:
            case MODPACK:
            default: return "mods";
        }
    }

    /** Only mods and modpacks are tied to a mod loader */
    public static boolean usesLoader(int projectType) {
        return projectType == MOD || projectType == MODPACK;
    }

    /** Only modpacks can be imported from a local archive */
    public static boolean supportsLocalImport(int projectType) {
        return projectType == MODPACK;
    }
}
