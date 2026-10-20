package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.downloader.AcquireableTaskMetadata;
import net.kdt.pojavlaunch.downloader.Downloader;
import net.kdt.pojavlaunch.mirrors.DownloadMirror;
import net.kdt.pojavlaunch.modloaders.FabriclikeUtils;
import net.kdt.pojavlaunch.modloaders.ForgelikeUtils;
import net.kdt.pojavlaunch.modloaders.modpacks.api.modloader.FabriclikeLoaderInstaller;
import net.kdt.pojavlaunch.modloaders.modpacks.api.modloader.ForgelikeLoaderInstaller;
import net.kdt.pojavlaunch.modloaders.modpacks.api.modloader.LoaderInstaller;
import net.kdt.pojavlaunch.modloaders.modpacks.models.CategoryEntry;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.CurseManifest;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ProjectType;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.GsonJsonUtils;
import net.kdt.pojavlaunch.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

public class CurseforgeApi implements ModpackApi{
    private static final Pattern sMcVersionPattern = Pattern.compile("([0-9]+)\\.([0-9]+)\\.?([0-9]+)?");
    private static final int ALGO_SHA_1 = 1;
    // Stolen from
    // https://github.com/AnzhiZhang/CurseForgeModpackDownloader/blob/6cb3f428459f0cc8f444d16e54aea4cd1186fd7b/utils/requester.py#L93
    private static final int CURSEFORGE_MC_GAME_ID = 432;
    /** Official endpoint, needs an api key */
    public static final String API_URL = "https://api.curseforge.com/v1";
    /** Public keyless mirror of the same API, used when the build carries no key */
    public static final String MIRROR_URL = "https://api.curse.tools/v1/cf";
    private static final int CURSEFORGE_PAGINATION_SIZE = 50;
    private static final int CURSEFORGE_PAGINATION_END_REACHED = -1;
    private static final int CURSEFORGE_PAGINATION_ERROR = -2;

    private final ApiHandler mApiHandler;
    private final String mApiKey;

    public CurseforgeApi(String apiKey) {
        this(API_URL, apiKey);
    }

    public CurseforgeApi(String baseUrl, String apiKey) {
        mApiKey = apiKey;
        mApiHandler = apiKey == null ? new ApiHandler(baseUrl) : new ApiHandler(baseUrl, apiKey);
    }

    /**
     * Builds an api for the key embedded in the build. Without a key the public
     * keyless mirror is used, so browsing still works out of the box.
     */
    public static CurseforgeApi create(String apiKey) {
        if (apiKey == null || apiKey.isEmpty() || "DUMMY".equals(apiKey)) {
            return new CurseforgeApi(MIRROR_URL, null);
        }
        return new CurseforgeApi(API_URL, apiKey);
    }

    public boolean isUsingMirror() {
        return mApiKey == null;
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        CurseforgeSearchResult curseforgeSearchResult = (CurseforgeSearchResult) previousPageResult;

        HashMap<String, Object> params = new HashMap<>();
        params.put("gameId", CURSEFORGE_MC_GAME_ID);
        params.put("classId", ProjectType.curseforgeClassId(searchFilters.projectType));
        if(searchFilters.name != null && !searchFilters.name.isEmpty())
            params.put("searchFilter", searchFilters.name);
        params.put("sortField", curseforgeSortField(searchFilters.sort));
        params.put("sortOrder", "desc");
        params.put("pageSize", CURSEFORGE_PAGINATION_SIZE);
        if(searchFilters.mcVersion != null && !searchFilters.mcVersion.isEmpty())
            params.put("gameVersion", searchFilters.mcVersion);
        if(searchFilters.categoryId > 0)
            params.put("categoryId", searchFilters.categoryId);
        if(ProjectType.usesLoader(searchFilters.projectType) && searchFilters.loader != null)
            params.put("modLoaderType", curseforgeLoaderType(searchFilters.loader));
        if(previousPageResult != null)
            params.put("index", curseforgeSearchResult.previousOffset);
        else if(searchFilters.offset > 0)
            params.put("index", searchFilters.offset);

        JsonObject response = mApiHandler.get("mods/search", params, JsonObject.class);
        if(response == null) return null;
        JsonArray dataArray = response.getAsJsonArray("data");
        if(dataArray == null) return null;
        JsonObject paginationInfo = response.getAsJsonObject("pagination");
        ArrayList<ModItem> modItemList = new ArrayList<>(dataArray.size());
        for(int i = 0; i < dataArray.size(); i++) {
            JsonObject dataElement = dataArray.get(i).getAsJsonObject();
            JsonElement allowModDistribution = dataElement.get("allowModDistribution");
            // Gson automatically casts null to false, which leans to issues
            // So, only check the distribution flag if it is non-null
            if(allowModDistribution != null && !allowModDistribution.isJsonNull()
                    && !allowModDistribution.getAsBoolean()) {
                Log.i("CurseforgeApi", "Skipping " + dataElement.get("name").getAsString()
                        + " because the author disabled third party downloads");
                continue;
            }
            long downloads = 0;
            if(dataElement.get("downloadCount") != null && !dataElement.get("downloadCount").isJsonNull())
                downloads = dataElement.get("downloadCount").getAsLong();
            ModItem modItem = new ModItem(Constants.SOURCE_CURSEFORGE,
                    searchFilters.projectType,
                    dataElement.get("id").getAsString(),
                    dataElement.get("name").getAsString(),
                    readSummary(dataElement),
                    readLogoUrl(dataElement),
                    downloads);
            modItemList.add(modItem);
        }
        if(curseforgeSearchResult == null) curseforgeSearchResult = new CurseforgeSearchResult();
        curseforgeSearchResult.results = modItemList.toArray(new ModItem[0]);
        curseforgeSearchResult.totalResultCount = paginationInfo == null ? modItemList.size()
                : paginationInfo.get("totalCount").getAsInt();
        curseforgeSearchResult.previousOffset += dataArray.size();
        return curseforgeSearchResult;

    }

    /** CurseForge sort fields: 1 featured, 2 popularity, 3 last update, 6 downloads, 11 release date */
    private static int curseforgeSortField(String sortKey) {
        if (sortKey == null) return 1;
        switch (sortKey) {
            case "downloads": return 6;
            case "newest": return 11;
            case "updated": return 3;
            case "relevance":
            default: return 1;
        }
    }

    /** CurseForge mod loader ids: 1 forge, 4 fabric, 5 quilt, 6 neoforge */
    private static int curseforgeLoaderType(String loaderKey) {
        switch (loaderKey) {
            case "forge": return 1;
            case "fabric": return 4;
            case "quilt": return 5;
            case "neoforge": return 6;
            default: return 0;
        }
    }

    private static String readSummary(JsonObject element) {
        JsonElement summary = element.get("summary");
        if (summary == null || summary.isJsonNull()) return "";
        return summary.getAsString();
    }

    private static String readLogoUrl(JsonObject element) {
        JsonElement logo = element.get("logo");
        if (logo == null || logo.isJsonNull() || !logo.isJsonObject()) return "";
        JsonElement thumbnail = logo.getAsJsonObject().get("thumbnailUrl");
        if (thumbnail == null || thumbnail.isJsonNull()) return "";
        return thumbnail.getAsString();
    }

    /** Category list of one project type, for the category filter menu */
    public List<CategoryEntry> fetchCategories(int projectType) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("gameId", CURSEFORGE_MC_GAME_ID);
        params.put("classId", ProjectType.curseforgeClassId(projectType));
        JsonObject response = mApiHandler.get("categories", params, JsonObject.class);
        if (response == null) return null;
        JsonArray data = response.getAsJsonArray("data");
        if (data == null) return null;
        ArrayList<CategoryEntry> categories = new ArrayList<>(data.size());
        for (int i = 0; i < data.size(); i++) {
            JsonObject element = data.get(i).getAsJsonObject();
            if (element.get("id") == null || element.get("name") == null) continue;
            categories.add(new CategoryEntry(element.get("id").getAsInt(),
                    element.get("name").getAsString()));
        }
        return categories;
    }

    @Override
    public ModDetail getModDetails(ModItem item) {
        ArrayList<JsonObject> allModDetails = new ArrayList<>();
        int index = 0;
        while(index != CURSEFORGE_PAGINATION_END_REACHED &&
                index != CURSEFORGE_PAGINATION_ERROR) {
            index = getPaginatedDetails(allModDetails, index, item.id);
        }
        if(index == CURSEFORGE_PAGINATION_ERROR) return null;
        // Files whose download is disabled have no url, they cannot be installed
        ArrayList<String> names = new ArrayList<>(allModDetails.size());
        ArrayList<String> mcNames = new ArrayList<>(allModDetails.size());
        ArrayList<String> urls = new ArrayList<>(allModDetails.size());
        ArrayList<String> hashes = new ArrayList<>(allModDetails.size());
        for(int i = 0; i < allModDetails.size(); i++) {
            JsonObject modDetail = allModDetails.get(i);
            JsonElement downloadUrl = modDetail.get("downloadUrl");
            if(downloadUrl == null || downloadUrl.isJsonNull()) continue;
            names.add(modDetail.get("displayName").getAsString());
            urls.add(downloadUrl.getAsString());
            hashes.add(getSha1FromModData(modDetail));

            String mcVersion = "";
            JsonArray gameVersions = modDetail.getAsJsonArray("gameVersions");
            if(gameVersions != null) {
                for(JsonElement jsonElement : gameVersions) {
                    String gameVersion = jsonElement.getAsString();
                    if(!sMcVersionPattern.matcher(gameVersion).matches()) {
                        continue;
                    }
                    mcVersion = gameVersion;
                    break;
                }
            }
            mcNames.add(mcVersion);
        }
        if (names.isEmpty()) return null;
        return new ModDetail(item,
                names.toArray(new String[0]),
                mcNames.toArray(new String[0]),
                urls.toArray(new String[0]),
                hashes.toArray(new String[0]));
    }

    @Override
    public LoaderInstaller installModpack(ModDetail modDetail, int selectedVersion) throws IOException{
        //TODO considering only modpacks for now
        return ModpackInstaller.downloadModpack(modDetail, selectedVersion, this::installCurseforgeZip);
    }

    public LoaderInstaller installLocalModpack(String modpackName, File modpackFile, String icon) throws IOException {
        return ModpackInstaller.installModpack(modpackName, modpackName, modpackFile, icon, this::installCurseforgeZip);
    }

    private int getPaginatedDetails(ArrayList<JsonObject> objectList, int index, String modId) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("index", index);
        params.put("pageSize", CURSEFORGE_PAGINATION_SIZE);

        JsonObject response = mApiHandler.get("mods/"+modId+"/files", params, JsonObject.class);
        JsonArray data = GsonJsonUtils.getJsonArraySafe(response, "data");
        Log.i("CurseforgeApi", "data...");
        if(data == null) return CURSEFORGE_PAGINATION_ERROR;
        Log.i("CurseforgeApi", "filtering...");
        for(int i = 0; i < data.size(); i++) {
            JsonObject fileInfo = data.get(i).getAsJsonObject();
            JsonElement serverPack = fileInfo.get("isServerPack");
            if(serverPack != null && !serverPack.isJsonNull() && serverPack.getAsBoolean()) continue;
            objectList.add(fileInfo);
        }
        Log.i("CurseforgeApi", "pag_end");
        if(data.size() < CURSEFORGE_PAGINATION_SIZE) {
            return CURSEFORGE_PAGINATION_END_REACHED; // we read the remainder! yay!
        }
        return index + data.size();
    }

    private LoaderInstaller installCurseforgeZip(File zipFile, File instanceDestination) throws IOException {
        try (ZipFile modpackZipFile = new ZipFile(zipFile)){
            CurseManifest curseManifest = Tools.GLOBAL_GSON.fromJson(
                    Tools.read(ZipUtils.getEntryStream(modpackZipFile, "manifest.json")),
                    CurseManifest.class);
            if(!verifyManifest(curseManifest)) {
                Log.i("CurseforgeApi","manifest verification failed");
                return null;
            }
            try {
                new CurseDownloader().start(curseManifest, instanceDestination);
            }catch (InterruptedException e) {
                throw new IOException("NIY: InterruptedException", e);
            }
            String overridesDir = "overrides";
            if(curseManifest.overrides != null) overridesDir = curseManifest.overrides;
            ZipUtils.zipExtract(modpackZipFile, overridesDir, instanceDestination);
            return createInfo(curseManifest.minecraft);
        }
    }

    private LoaderInstaller createInfo(CurseManifest.CurseMinecraft minecraft) {
        CurseManifest.CurseModLoader primaryModLoader = null;
        for(CurseManifest.CurseModLoader modLoader : minecraft.modLoaders) {
            if(modLoader.primary) {
                primaryModLoader = modLoader;
                break;
            }
        }
        if(primaryModLoader == null) primaryModLoader = minecraft.modLoaders[0];
        String modLoaderId = primaryModLoader.id;
        int dashIndex = modLoaderId.indexOf('-');
        String modLoaderName = modLoaderId.substring(0, dashIndex);
        String modLoaderVersion = modLoaderId.substring(dashIndex+1);
        Log.i("CurseforgeApi", modLoaderId + " " + modLoaderName + " "+modLoaderVersion);
        LoaderInstaller loaderInstaller;
        switch (modLoaderName) {
            case "forge":
                return new ForgelikeLoaderInstaller(ForgelikeUtils.FORGE_UTILS, minecraft.version, modLoaderVersion);
            case "fabric":
                return new FabriclikeLoaderInstaller(FabriclikeUtils.FABRIC_UTILS, minecraft.version, modLoaderVersion);
            case "neoforge":
                return new ForgelikeLoaderInstaller(ForgelikeUtils.NEOFORGE_UTILS, minecraft.version, modLoaderVersion);
            default:
                return null;
            //TODO: Quilt is also Forge? How does that work?
        }
    }

    private String getDownloadUrl(JsonObject fileMetadata) throws IOException {
        if(fileMetadata.get("modId").isJsonNull() || fileMetadata.get("id").isJsonNull()) throw new IOException("Bad metadata schema!");
        long projectID = fileMetadata.get("modId").getAsLong();
        long fileID = fileMetadata.get("id").getAsLong();

        // First try the official api endpoint
        JsonObject response = mApiHandler.get("mods/"+projectID+"/files/"+fileID+"/download-url", JsonObject.class);
        if (response != null && !response.get("data").isJsonNull())
            return response.get("data").getAsString();

        // Otherwise, fallback to building an edge link
        return String.format("https://edge.forgecdn.net/files/%s/%s/%s", fileID/1000, fileID % 1000, fileMetadata.get("fileName").getAsString());
    }

    private void checkRequiredFileFields(JsonObject fileMetadata) throws IOException {
        if(fileMetadata == null || fileMetadata.isJsonNull()) throw new IOException("File metadata is null!");
        boolean hasProjectId = fileMetadata.has("modId");
        boolean hasFileId = fileMetadata.has("id");
        boolean hasLength = fileMetadata.has("fileLength");
        if(!hasProjectId || !hasFileId || !hasLength) {
            StringBuilder builder = new StringBuilder().append("File metadata is mising the following fields:");
            if(!hasProjectId) builder.append(" modId");
            if(!hasFileId) builder.append(" id");
            if(!hasLength) builder.append(" fileLength");
            throw new IOException(builder.toString());
        }
    }

    private @Nullable JsonObject getFile(long projectID, long fileID) {
        JsonObject response = mApiHandler.get("mods/"+projectID+"/files/"+fileID, JsonObject.class);
        return GsonJsonUtils.getJsonObjectSafe(response, "data");
    }

    private String getSha1FromModData(@NonNull JsonObject object) {
        JsonArray hashes = GsonJsonUtils.getJsonArraySafe(object, "hashes");
        if(hashes == null) return null;
        for (JsonElement jsonElement : hashes) {
            // The sha1 = 1; md5 = 2;
            JsonObject jsonObject = GsonJsonUtils.getJsonObjectSafe(jsonElement);
            if(GsonJsonUtils.getIntSafe(
                    jsonObject,
                    "algo",
                    -1) == ALGO_SHA_1) {
                return GsonJsonUtils.getStringSafe(jsonObject, "value");
            }
        }
        return null;
    }

    private boolean verifyManifest(CurseManifest manifest) {
        if(!"minecraftModpack".equals(manifest.manifestType)) return false;
        if(manifest.manifestVersion != 1) return false;
        if(manifest.minecraft == null) return false;
        if(manifest.minecraft.version == null) return false;
        if(manifest.minecraft.modLoaders == null) return false;
        return manifest.minecraft.modLoaders.length >= 1;
    }

    static class CurseforgeSearchResult extends SearchResult {
        int previousOffset;
    }

    class CurseDownloader extends Downloader {

        public CurseDownloader() {
            super(ProgressLayout.INSTALL_MODPACK);
        }

        public void start(CurseManifest curseManifest, File instanceDestination) throws IOException, InterruptedException {
            ArrayList<AcquireableTaskMetadata> taskMetadatas = new ArrayList<>(curseManifest.files.length);
            for(final CurseManifest.CurseFile file : curseManifest.files) {
                taskMetadatas.add(new CurseTaskMetadata(file, instanceDestination));
            }
            runDownloads(taskMetadatas);
        }
    }

    class CurseTaskMetadata extends AcquireableTaskMetadata {
        private final CurseManifest.CurseFile mFile;
        private final File mInstanceDestination;

        public CurseTaskMetadata(CurseManifest.CurseFile mFile, File mInstanceDestination) {
            super(DownloadMirror.DOWNLOAD_CLASS_METADATA);
            this.mFile = mFile;
            this.mInstanceDestination = mInstanceDestination;
        }

        @Override
        public void acquireMetadata() throws IOException {
            JsonObject fileMetadata = getFile(mFile.projectID, mFile.fileID);
            checkRequiredFileFields(fileMetadata);
            String url = getDownloadUrl(fileMetadata);
            this.url = new URL(url);
            this.path = new File(mInstanceDestination, "mods/"+ URLDecoder.decode(FileUtils.getFileName(url),"UTF-8"));
            FileUtils.ensureParentDirectorySilently(this.path);
            this.sha1Hash = getSha1FromModData(fileMetadata);
            this.size = fileMetadata.get("fileLength").getAsLong();
        }
    }
}
