package net.kdt.pojavlaunch.modloaders.modpacks.models;

import org.jetbrains.annotations.Nullable;

/**
 * Search filters, passed to APIs
 */
public class SearchFilters {
    /** One of the ProjectType values */
    public int projectType = ProjectType.MODPACK;
    public String name;
    @Nullable public String mcVersion;
    /** Sort key: relevance (default), downloads, newest or updated */
    @Nullable public String sort;
    /** Modrinth category slug, for example technology, adventure or textures */
    @Nullable public String category;
    /** CurseForge category id, 0 when no category is selected */
    public int categoryId;
    /** Mod loader key, for example fabric, forge, neoforge or quilt */
    @Nullable public String loader;
    /** Result offset for the first request of a page, in number of entries */
    public int offset;
}
