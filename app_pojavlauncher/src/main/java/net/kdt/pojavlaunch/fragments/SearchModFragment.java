package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.runOnUiThread;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.ModFavorites;
import net.kdt.pojavlaunch.modloaders.modpacks.ModItemAdapter;
import net.kdt.pojavlaunch.modloaders.modpacks.api.CommonApi;
import net.kdt.pojavlaunch.modloaders.modpacks.api.CurseforgeApi;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackApi;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.CategoryEntry;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ProjectType;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.profiles.VersionSelectorDialog;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Browse screen for modpacks, mods, resource packs, shaders and local favorites.
 * Results come from the real Modrinth or CurseForge APIs, one page at a time.
 * Every project type uses the same layout, so the screens only differ by content.
 */
public class SearchModFragment extends Fragment implements ModItemAdapter.SearchResultCallback {

    public static final String TAG = "SearchModFragment";
    public static final String ARG_PROJECT_TYPE = "project_type";
    public static final String ARG_FAVORITES = "favorites";

    private static final int PAGE_SIZE = 50;
    private static final int SOURCE_MODRINTH = 0;
    private static final int SOURCE_CURSEFORGE = 1;

    /* Category slugs, one list per project type, in the order of Modrinth tags */
    private static final String[] MOD_CATEGORY_KEYS = {
            "technology", "adventure", "magic", "quests", "optimization",
            "decoration", "worldgen", "food", "storage", "equipment"};
    private static final int[] MOD_CATEGORY_LABELS = {
            R.string.rc_cat_tech, R.string.rc_cat_adventure, R.string.rc_cat_magic,
            R.string.rc_cat_rpg, R.string.rc_cat_optimization, R.string.rc_cat_decoration,
            R.string.rc_cat_worldgen, R.string.rc_cat_food, R.string.rc_cat_storage,
            R.string.rc_cat_equipment};

    private static final String[] PACK_CATEGORY_KEYS = {
            "adventure", "combat", "magic", "multiplayer", "optimization", "quests",
            "technology", "kitchen-sink", "light", "sci-fi"};
    private static final int[] PACK_CATEGORY_LABELS = {
            R.string.rc_cat_adventure, R.string.rc_pack_cat_combat, R.string.rc_cat_magic,
            R.string.rc_pack_cat_multiplayer, R.string.rc_cat_optimization,
            R.string.rc_cat_rpg, R.string.rc_cat_tech, R.string.rc_pack_cat_kitchensink,
            R.string.rc_pack_cat_light, R.string.rc_pack_cat_scifi};

    private static final String[] RESOURCE_PACK_CATEGORY_KEYS = {
            "textures", "models", "fonts", "audio", "gui", "locale",
            "environment", "blocks", "items", "entities"};
    private static final int[] RESOURCE_PACK_CATEGORY_LABELS = {
            R.string.rc_rp_cat_textures, R.string.rc_rp_cat_models, R.string.rc_rp_cat_fonts,
            R.string.rc_rp_cat_audio, R.string.rc_rp_cat_gui, R.string.rc_rp_cat_locale,
            R.string.rc_rp_cat_environment, R.string.rc_rp_cat_blocks,
            R.string.rc_rp_cat_items, R.string.rc_rp_cat_entities};

    private static final String[] SHADER_CATEGORY_KEYS = {
            "realistic", "semi-realistic", "fantasy", "cartoon",
            "vanilla-like", "atmosphere", "blooming", "foliage"};
    private static final int[] SHADER_CATEGORY_LABELS = {
            R.string.rc_sh_cat_realistic, R.string.rc_sh_cat_semirealistic,
            R.string.rc_sh_cat_fantasy, R.string.rc_sh_cat_cartoon,
            R.string.rc_sh_cat_vanilla, R.string.rc_sh_cat_atmosphere,
            R.string.rc_sh_cat_blooming, R.string.rc_sh_cat_foliage};

    private static final String[] LOADER_KEYS = {"fabric", "forge", "neoforge", "quilt"};
    private static final int[] LOADER_LABELS = {
            R.string.rc_loader_fabric, R.string.rc_loader_forge,
            R.string.rc_loader_neoforge, R.string.rc_loader_quilt};
    private static final String[] SORT_KEYS = {"relevance", "downloads", "newest", "updated"};
    private static final int[] SORT_LABELS = {
            R.string.rc_sort_relevance, R.string.rc_sort_downloads,
            R.string.rc_sort_newest, R.string.rc_sort_updated};

    private final SearchFilters mFilters = new SearchFilters();

    private EditText mSearchEditText;
    private RecyclerView mRecyclerView;
    private ModItemAdapter mAdapter;
    private ProgressBar mProgressBar;
    private TextView mStatusView;
    private View mSearchRow;
    private View mFiltersScroll;
    private View mPager;
    private Button mSourceButton;
    private Button mCategoriesButton;
    private Button mMcButton;
    private Button mLoaderButton;
    private Button mSortButton;
    private ImageButton mPrevPage;
    private ImageButton mNextPage;
    private TextView mPageIndicator;
    private Button mImportButton;

    private ModpackApi mSearchApi;
    private ModpackApi mImportApi;
    private int mSource = SOURCE_MODRINTH;
    private int mPage;
    private int mTotalResults;
    private int mProjectType = ProjectType.MODPACK;
    private boolean mFavoritesMode;
    private String mCategoryLabel;
    private final List<ModItem> mAllFavorites = new ArrayList<>();

    private TaskCountListener mTaskCountListener;

    private final ActivityResultLauncher<String> mImportLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                Context context = requireContext();
                ContentResolver contentResolver = context.getContentResolver();
                PojavApplication.sExecutorService.execute(() ->
                        performLocalInstall(uri, context, contentResolver));
            });

    public SearchModFragment() {
        super(R.layout.fragment_mod_search);
        mFilters.projectType = ProjectType.MODPACK;
    }

    public static SearchModFragment newInstance(int projectType) {
        SearchModFragment fragment = new SearchModFragment();
        Bundle bundle = new Bundle(1);
        bundle.putInt(ARG_PROJECT_TYPE, projectType);
        fragment.setArguments(bundle);
        return fragment;
    }

    /** Locally stored mods, packs and shaders */
    public static SearchModFragment newFavorites() {
        SearchModFragment fragment = new SearchModFragment();
        Bundle bundle = new Bundle(1);
        bundle.putBoolean(ARG_FAVORITES, true);
        fragment.setArguments(bundle);
        return fragment;
    }

    public static boolean isFavorites(Fragment fragment) {
        Bundle args = fragment.getArguments();
        return args != null && args.getBoolean(ARG_FAVORITES, false);
    }

    public static int projectTypeOf(Fragment fragment) {
        Bundle args = fragment.getArguments();
        return args == null ? ProjectType.MODPACK
                : args.getInt(ARG_PROJECT_TYPE, ProjectType.MODPACK);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Bundle args = getArguments();
        if (args != null) {
            mFavoritesMode = args.getBoolean(ARG_FAVORITES, false);
            mProjectType = args.getInt(ARG_PROJECT_TYPE, ProjectType.MODPACK);
        }
        mFilters.projectType = mProjectType;
        mImportApi = new CommonApi(context.getString(R.string.curseforge_api_key));
        // Favorites can hold items from both sources, so they need the routing api
        mSearchApi = mFavoritesMode
                ? new CommonApi(context.getString(R.string.curseforge_api_key))
                : new ModrinthApi();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mSearchRow = view.findViewById(R.id.browse_search_row);
        mFiltersScroll = view.findViewById(R.id.browse_filters_scroll);
        mPager = view.findViewById(R.id.browse_pager);
        mSearchEditText = view.findViewById(R.id.search_mod_edittext);
        mProgressBar = view.findViewById(R.id.search_mod_progressbar);
        mRecyclerView = view.findViewById(R.id.search_mod_list);
        mStatusView = view.findViewById(R.id.search_mod_status_text);
        mSourceButton = view.findViewById(R.id.search_mod_source);
        mCategoriesButton = view.findViewById(R.id.filter_categories);
        mMcButton = view.findViewById(R.id.filter_mc_version);
        mLoaderButton = view.findViewById(R.id.filter_loader);
        mSortButton = view.findViewById(R.id.filter_sort);
        mPrevPage = view.findViewById(R.id.page_prev);
        mNextPage = view.findViewById(R.id.page_next);
        mPageIndicator = view.findViewById(R.id.page_indicator);
        mImportButton = view.findViewById(R.id.mineButton_import_local_modpack);

        mAdapter = new ModItemAdapter(mSearchApi, this);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        mRecyclerView.post(() -> {
            float density = getResources().getDisplayMetrics().density;
            int width = mRecyclerView.getWidth();
            int columns = Math.max(1, (int) (width / (172f * density)));
            mRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), columns));
        });

        ProgressKeeper.addTaskCountListener(mAdapter);
        mTaskCountListener = taskCount -> {
            runOnUiThread(() -> mImportButton.setEnabled(taskCount == 0));
            return false;
        };
        ProgressKeeper.addTaskCountListener(mTaskCountListener);

        if (mFavoritesMode) setupFavorites(view);
        else setupBrowse();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ProgressKeeper.removeTaskCountListener(mAdapter);
        if (mTaskCountListener != null) ProgressKeeper.removeTaskCountListener(mTaskCountListener);
    }

    // ------------------------------------------------------------------ browse

    private void setupBrowse() {
        mStatusView.setVisibility(View.GONE);
        mPager.setVisibility(View.VISIBLE);
        // Loaders only apply to mods and modpacks
        mLoaderButton.setVisibility(ProjectType.usesLoader(mProjectType) ? View.VISIBLE : View.GONE);
        mImportButton.setVisibility(ProjectType.supportsLocalImport(mProjectType)
                ? View.VISIBLE : View.GONE);
        updateFilterLabels();
        updateSourceLabel();

        mSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
            mSearchEditText.clearFocus();
            search(0);
            return true;
        });
        // Clearing the field brings the unfiltered list back without another tap
        mSearchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                String previous = mFilters.name == null ? "" : mFilters.name;
                if (s.length() == 0 && !previous.isEmpty()) search(0);
            }
        });

        mSourceButton.setOnClickListener(v -> showSourceMenu());
        mCategoriesButton.setOnClickListener(v -> showCategoryMenu());
        mLoaderButton.setOnClickListener(v -> showLoaderMenu());
        mSortButton.setOnClickListener(v -> showSortMenu());
        mMcButton.setOnClickListener(v -> showVersionMenu());

        mPrevPage.setOnClickListener(v -> { if (mPage > 0) search(mPage - 1); });
        mNextPage.setOnClickListener(v -> search(mPage + 1));

        mImportButton.setText(R.string.import_local_modpack);
        mImportButton.setOnClickListener(v -> mImportLauncher.launch("*/*"));

        search(0);
    }

    private void search(int page) {
        if (mFavoritesMode) return;
        mPage = Math.max(0, page);
        mFilters.name = mSearchEditText.getText().toString();
        mFilters.offset = mPage * PAGE_SIZE;
        mProgressBar.setVisibility(View.VISIBLE);
        mStatusView.setVisibility(View.GONE);
        mAdapter.performSearchQuery(mFilters);
    }

    @Override
    public void onSearchFinished(int totalResults) {
        if (getView() == null) return;
        mProgressBar.setVisibility(View.GONE);
        mStatusView.setVisibility(View.GONE);
        mTotalResults = totalResults;
        int pages = Math.max(1, (totalResults + PAGE_SIZE - 1) / PAGE_SIZE);
        mPageIndicator.setText(getString(R.string.rc_page_indicator, mPage + 1, pages));
        mPrevPage.setEnabled(mPage > 0);
        mNextPage.setEnabled(mPage + 1 < pages);
        mPrevPage.setAlpha(mPage > 0 ? 1f : 0.35f);
        mNextPage.setAlpha(mPage + 1 < pages ? 1f : 0.35f);
    }

    @Override
    public void onSearchError(int error) {
        if (getView() == null) return;
        mProgressBar.setVisibility(View.GONE);
        mStatusView.setVisibility(View.VISIBLE);
        if (error == ERROR_NO_RESULTS) {
            mStatusView.setText(mFavoritesMode
                    ? R.string.rc_favorites_empty : R.string.search_modpack_no_result);
        } else {
            mStatusView.setText(mSource == SOURCE_CURSEFORGE
                    ? R.string.rc_source_error_curseforge : R.string.search_modpack_error);
        }
        if (mFavoritesMode) return;
        mPageIndicator.setText(getString(R.string.rc_page_indicator, mPage + 1, 1));
        mPrevPage.setEnabled(false);
        mNextPage.setEnabled(false);
        mPrevPage.setAlpha(0.35f);
        mNextPage.setAlpha(0.35f);
    }

    // ------------------------------------------------------------------ favorites

    private void setupFavorites(View view) {
        mSearchRow.setVisibility(View.VISIBLE);
        mFiltersScroll.setVisibility(View.GONE);
        mPager.setVisibility(View.GONE);
        mSearchEditText.setHint(R.string.rc_favorites_title);
        mProgressBar.setVisibility(View.GONE);

        mAllFavorites.clear();
        mAllFavorites.addAll(ModFavorites.load(requireContext()));
        applyFavoritesFilter("");

        mSearchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { applyFavoritesFilter(s.toString()); }
        });
    }

    private void applyFavoritesFilter(String query) {
        String needle = query == null ? "" : query.toLowerCase(Locale.US).trim();
        List<ModItem> filtered = new ArrayList<>();
        for (ModItem item : mAllFavorites) {
            if (needle.isEmpty() || (item.title != null && item.title.toLowerCase(Locale.US).contains(needle)))
                filtered.add(item);
        }
        mAdapter.setStaticItems(filtered);
        boolean empty = filtered.isEmpty();
        mStatusView.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) mStatusView.setText(needle.isEmpty()
                ? R.string.rc_favorites_empty : R.string.search_modpack_no_result);
    }

    // ------------------------------------------------------------------ menus

    private void showSourceMenu() {
        PopupMenu popup = new PopupMenu(requireContext(), mSourceButton);
        popup.getMenu().add(0, SOURCE_MODRINTH, 0, R.string.rc_source_modrinth);
        popup.getMenu().add(0, SOURCE_CURSEFORGE, 1, R.string.rc_source_curseforge);
        popup.setOnMenuItemClickListener(item -> {
            selectSource(item.getItemId());
            return true;
        });
        popup.show();
    }

    private void selectSource(int source) {
        if (mSource == source) return;
        mSource = source;
        mSearchApi = source == SOURCE_CURSEFORGE
                ? CurseforgeApi.create(getString(R.string.curseforge_api_key))
                : new ModrinthApi();
        // Category identifiers are not shared between the two sources
        mFilters.category = null;
        mFilters.categoryId = 0;
        mCategoryLabel = null;
        ModItemAdapter previousAdapter = mAdapter;
        mAdapter = new ModItemAdapter(mSearchApi, this);
        mRecyclerView.setAdapter(mAdapter);
        if (previousAdapter != null) ProgressKeeper.removeTaskCountListener(previousAdapter);
        ProgressKeeper.addTaskCountListener(mAdapter);
        updateSourceLabel();
        updateFilterLabels();
        search(0);
    }

    private void updateSourceLabel() {
        mSourceButton.setText(mSource == SOURCE_CURSEFORGE
                ? R.string.rc_source_curseforge : R.string.rc_source_modrinth);
    }

    private void showCategoryMenu() {
        if (mSource == SOURCE_CURSEFORGE) {
            showCurseforgeCategoryMenu();
            return;
        }
        String[] keys = modrinthCategoryKeys();
        int[] labels = modrinthCategoryLabels();
        PopupMenu popup = new PopupMenu(requireContext(), mCategoriesButton);
        popup.getMenu().add(0, 0, 0, getString(R.string.rc_filter_categories,
                getString(R.string.rc_filter_all)));
        for (int i = 0; i < keys.length; i++) {
            popup.getMenu().add(0, i + 1, i + 1, getString(labels[i]));
        }
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == 0) {
                mFilters.category = null;
                mCategoryLabel = null;
            } else {
                mFilters.category = keys[id - 1];
                mCategoryLabel = getString(labels[id - 1]);
            }
            updateFilterLabels();
            search(0);
            return true;
        });
        popup.show();
    }

    /** CurseForge categories are numeric ids, so they are read from the api */
    private void showCurseforgeCategoryMenu() {
        Toast.makeText(requireContext(), R.string.rc_categories_loading, Toast.LENGTH_SHORT).show();
        final CurseforgeApi api = CurseforgeApi.create(getString(R.string.curseforge_api_key));
        PojavApplication.sExecutorService.execute(() -> {
            final List<CategoryEntry> categories = api.fetchCategories(mProjectType);
            runOnUiThread(() -> {
                if (!isAdded() || getView() == null) return;
                if (categories == null || categories.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.rc_source_error_curseforge,
                            Toast.LENGTH_LONG).show();
                    return;
                }
                PopupMenu popup = new PopupMenu(requireContext(), mCategoriesButton);
                popup.getMenu().add(0, 0, 0, getString(R.string.rc_filter_categories,
                        getString(R.string.rc_filter_all)));
                for (int i = 0; i < categories.size(); i++) {
                    popup.getMenu().add(0, i + 1, i + 1, categories.get(i).name);
                }
                popup.setOnMenuItemClickListener(item -> {
                    int id = item.getItemId();
                    if (id == 0) {
                        mFilters.categoryId = 0;
                        mCategoryLabel = null;
                    } else {
                        CategoryEntry entry = categories.get(id - 1);
                        mFilters.categoryId = entry.id;
                        mCategoryLabel = entry.name;
                    }
                    updateFilterLabels();
                    search(0);
                    return true;
                });
                popup.show();
            });
        });
    }

    private String[] modrinthCategoryKeys() {
        switch (mProjectType) {
            case ProjectType.MOD: return MOD_CATEGORY_KEYS;
            case ProjectType.RESOURCE_PACK: return RESOURCE_PACK_CATEGORY_KEYS;
            case ProjectType.SHADER: return SHADER_CATEGORY_KEYS;
            case ProjectType.MODPACK:
            default: return PACK_CATEGORY_KEYS;
        }
    }

    private int[] modrinthCategoryLabels() {
        switch (mProjectType) {
            case ProjectType.MOD: return MOD_CATEGORY_LABELS;
            case ProjectType.RESOURCE_PACK: return RESOURCE_PACK_CATEGORY_LABELS;
            case ProjectType.SHADER: return SHADER_CATEGORY_LABELS;
            case ProjectType.MODPACK:
            default: return PACK_CATEGORY_LABELS;
        }
    }

    private void showLoaderMenu() {
        if (!ProjectType.usesLoader(mProjectType)) return;
        PopupMenu popup = new PopupMenu(requireContext(), mLoaderButton);
        popup.getMenu().add(0, 0, 0, getString(R.string.rc_filter_loader,
                getString(R.string.rc_filter_all)));
        for (int i = 0; i < LOADER_KEYS.length; i++) {
            popup.getMenu().add(0, i + 1, i + 1, getString(LOADER_LABELS[i]));
        }
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            mFilters.loader = id == 0 ? null : LOADER_KEYS[id - 1];
            updateFilterLabels();
            search(0);
            return true;
        });
        popup.show();
    }

    private void showSortMenu() {
        PopupMenu popup = new PopupMenu(requireContext(), mSortButton);
        for (int i = 0; i < SORT_KEYS.length; i++) {
            popup.getMenu().add(0, i, i, getString(SORT_LABELS[i]));
        }
        popup.setOnMenuItemClickListener(item -> {
            mFilters.sort = SORT_KEYS[item.getItemId()];
            updateFilterLabels();
            search(0);
            return true;
        });
        popup.show();
    }

    private void showVersionMenu() {
        PopupMenu popup = new PopupMenu(requireContext(), mMcButton);
        popup.getMenu().add(0, 0, 0, getString(R.string.rc_filter_mc,
                getString(R.string.rc_filter_all)));
        popup.getMenu().add(0, 1, 1, getString(R.string.rc_select_mc_version));
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 0) {
                mFilters.mcVersion = null;
                updateFilterLabels();
                search(0);
            } else {
                VersionSelectorDialog.open(requireContext(), true, (id, snapshot) -> {
                    mFilters.mcVersion = id;
                    updateFilterLabels();
                    search(0);
                });
            }
            return true;
        });
        popup.show();
    }

    private void updateFilterLabels() {
        mCategoriesButton.setText(getString(R.string.rc_filter_categories,
                mCategoryLabel == null ? getString(R.string.rc_filter_all) : mCategoryLabel));
        mMcButton.setText(getString(R.string.rc_filter_mc,
                mFilters.mcVersion == null ? getString(R.string.rc_filter_all)
                        : mFilters.mcVersion));
        if (ProjectType.usesLoader(mProjectType)) {
            mLoaderButton.setText(getString(R.string.rc_filter_loader,
                    mFilters.loader == null ? getString(R.string.rc_filter_all)
                            : loaderLabel(mFilters.loader)));
        }
        mSortButton.setText(getString(R.string.rc_filter_sort, sortLabel(mFilters.sort)));
    }

    private String loaderLabel(String key) {
        for (int i = 0; i < LOADER_KEYS.length; i++) {
            if (LOADER_KEYS[i].equals(key)) return getString(LOADER_LABELS[i]);
        }
        return key;
    }

    private String sortLabel(String key) {
        if (key == null) return getString(R.string.rc_sort_relevance);
        for (int i = 0; i < SORT_KEYS.length; i++) {
            if (SORT_KEYS[i].equals(key)) return getString(SORT_LABELS[i]);
        }
        return getString(R.string.rc_sort_relevance);
    }

    // ------------------------------------------------------------------ local import

    private void performLocalInstall(Uri uri, Context context, ContentResolver contentResolver) {
        String fileName = Tools.getFileName(context, uri);
        if (fileName == null) return;
        File outFile = new File(Tools.DIR_CACHE, fileName + ".cf");
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, R.string.multirt_progress_caching);
        try (InputStream inputStream = contentResolver.openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(outFile)) {
            if (inputStream == null) return;
            IOUtils.copy(inputStream, outputStream);
            outputStream.flush();
        } catch (IOException e) {
            Tools.showErrorRemote("Error", e);
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
            return;
        }
        try {
            if (mImportApi.installLocalModpack(fileName, outFile, null) == null) {
                runOnUiThread(() -> Toast.makeText(context, R.string.rc_versions_unavailable,
                        Toast.LENGTH_LONG).show());
            }
        } catch (IOException e) {
            Tools.showErrorRemote("Error", e);
        } finally {
            outFile.delete();
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }
    }
}
