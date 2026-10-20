package net.kdt.pojavlaunch.modloaders.modpacks.models;

public abstract class ModSource {
    public int apiSource;
    /** One of the ProjectType values, MODPACK when the source only knows about packs */
    public int projectType = ProjectType.MODPACK;

    public boolean isModpack() {
        return projectType == ProjectType.MODPACK;
    }
}
