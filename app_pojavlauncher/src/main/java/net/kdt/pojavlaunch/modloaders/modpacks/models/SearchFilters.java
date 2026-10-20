package net.kdt.pojavlaunch.modloaders.modpacks.models;

import org.jetbrains.annotations.Nullable;

/**
 * Search filters, passed to APIs
 */
public class SearchFilters {
    public boolean isModpack;
    public String name;
    @Nullable public String mcVersion;
    /** Modrinth sort index: relevance (default), downloads, date or updated */
    @Nullable public String sort;
    /** Modrinth category facet, for example technology, adventure, magic or quests */
    @Nullable public String category;
}
