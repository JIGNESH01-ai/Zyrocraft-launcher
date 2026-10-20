package net.kdt.pojavlaunch.modloaders.modpacks.models;

import androidx.annotation.NonNull;

public class ModItem extends ModSource {

    public String id;
    public String title;
    public String description;
    public String imageUrl;
    /** Total download count as reported by the source API, 0 when unknown */
    public long downloads;

    public ModItem(int apiSource, boolean isModpack, String id, String title, String description, String imageUrl) {
        this.apiSource = apiSource;
        this.isModpack = isModpack;
        this.id = id;
        this.title = title;
        this.description = description;
        this.imageUrl = imageUrl;
    }

    public ModItem(int apiSource, boolean isModpack, String id, String title, String description,
                   String imageUrl, long downloads) {
        this(apiSource, isModpack, id, title, description, imageUrl);
        this.downloads = downloads;
    }

    @NonNull
    @Override
    public String toString() {
        return "ModItem{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", apiSource=" + apiSource +
                ", isModpack=" + isModpack +
                '}';
    }

    public String getIconCacheTag() {
        return apiSource+"_"+id;
    }
}
