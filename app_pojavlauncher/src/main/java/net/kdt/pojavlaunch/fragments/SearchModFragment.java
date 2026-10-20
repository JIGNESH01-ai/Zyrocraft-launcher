package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.runOnUiThread;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.math.MathUtils;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.ModItemAdapter;
import net.kdt.pojavlaunch.modloaders.modpacks.api.CommonApi;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthApi;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackApi;
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


public class SearchModFragment extends Fragment implements ModItemAdapter.SearchResultCallback {

    public static final String TAG = "SearchModFragment";
    public static final String ARG_IS_MODPACK = "is_modpack";
    private View mOverlay;
    private View mChipsRow;
    private String mActiveChip = "all";
    private float mOverlayTopCache; // Padding cache reduce resource lookup

    private final RecyclerView.OnScrollListener mOverlayPositionListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            mOverlay.setY(MathUtils.clamp(mOverlay.getY() - dy, -mOverlay.getHeight(), mOverlayTopCache));
        }
    };

    private EditText mSearchEditText;
    private ImageButton mFilterButton;
    private RecyclerView mRecyclerview;
    private ModItemAdapter mModItemAdapter;
    private ProgressBar mSearchProgressBar;
    private TextView mStatusTextView;
    private ColorStateList mDefaultTextColor;
    private ModpackApi modpackApi;

    private final SearchFilters mSearchFilters;

    private Button mImportButton;
    private TaskCountListener mTaskCountListener;

    ActivityResultLauncher<String> mImportLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri == null) return;
                Context context = getContext();
                ContentResolver contentResolver = getContext().getContentResolver();
                PojavApplication.sExecutorService.execute(() -> {
                    performLocalInstall(uri, context, contentResolver);
                });
            });

    public void performLocalInstall(Uri uri, Context context, ContentResolver contentResolver) {
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
                modpackApi.installLocalModpack(fileName, outFile, null);
            } catch (IOException e) {
                Tools.showErrorRemote("Error", e);
            } finally {
                outFile.delete();
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
            }
    }

    public SearchModFragment(){
        super(R.layout.fragment_mod_search);
        mSearchFilters = new SearchFilters();
        mSearchFilters.isModpack = true;
    }

    /** Modpacks by default; pass false to browse single mods */
    public static SearchModFragment newInstance(boolean isModpack) {
        SearchModFragment fragment = new SearchModFragment();
        Bundle bundle = new Bundle(1);
        bundle.putBoolean(ARG_IS_MODPACK, isModpack);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Bundle args = getArguments();
        if (args != null) mSearchFilters.isModpack = args.getBoolean(ARG_IS_MODPACK, true);
        modpackApi = pickApi(context);
    }

    /**
     * The combined API is used for unfiltered browsing. Modrinth-only is used when
     * sort or category filters are active so that the chips always mean what they say.
     */
    private ModpackApi pickApi(Context context) {
        boolean filtered = mSearchFilters.sort != null || mSearchFilters.category != null;
        if (!mSearchFilters.isModpack || filtered) return new ModrinthApi();
        return new CommonApi(context.getString(R.string.curseforge_api_key));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // You can only access resources after attaching to current context
        recreateAdapter();
        mOverlayTopCache = getResources().getDimension(R.dimen.fragment_padding_medium);

        mOverlay = view.findViewById(R.id.search_mod_overlay);
        mSearchEditText = view.findViewById(R.id.search_mod_edittext);
        mSearchProgressBar = view.findViewById(R.id.search_mod_progressbar);
        mRecyclerview = view.findViewById(R.id.search_mod_list);
        mStatusTextView = view.findViewById(R.id.search_mod_status_text);
        mFilterButton = view.findViewById(R.id.search_mod_filter);

        mDefaultTextColor = mStatusTextView.getTextColors();

        mRecyclerview.setLayoutManager(new LinearLayoutManager(getContext()));
        mRecyclerview.setAdapter(mModItemAdapter);

        mRecyclerview.addOnScrollListener(mOverlayPositionListener);

        mSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
            searchMods(mSearchEditText.getText().toString());
            mSearchEditText.clearFocus();
            return false;
        });

        mOverlay.post(()->{
           int overlayHeight = mOverlay.getHeight();
           mRecyclerview.setPadding(mRecyclerview.getPaddingLeft(),
                   mRecyclerview.getPaddingTop() + overlayHeight,
                   mRecyclerview.getPaddingRight(),
                   mRecyclerview.getPaddingBottom());
        });
        mFilterButton.setOnClickListener(v -> displayFilterDialog());
        setupChips(view);
        if (!mSearchFilters.isModpack) {
            mSearchEditText.setHint(R.string.zyro_search_mods);
        }
        mImportButton = view.findViewById(R.id.mineButton_import_local_modpack);
        if (!mSearchFilters.isModpack) {
            mImportButton.setVisibility(View.GONE);
        }
        mImportButton.setOnClickListener(v -> {
            mImportLauncher.launch("*/*");
        });
        mTaskCountListener = taskCount -> {
            runOnUiThread(() -> mImportButton.setEnabled(taskCount == 0));
            return false;
        };
        ProgressKeeper.addTaskCountListener(mTaskCountListener);

        searchMods(null);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mModItemAdapter != null) ProgressKeeper.removeTaskCountListener(mModItemAdapter);
        mRecyclerview.removeOnScrollListener(mOverlayPositionListener);
        if (mTaskCountListener != null) { ProgressKeeper.removeTaskCountListener(mTaskCountListener); }
    }

    @Override
    public void onSearchFinished() {
        mSearchProgressBar.setVisibility(View.GONE);
        mStatusTextView.setVisibility(View.GONE);
    }

    @Override
    public void onSearchError(int error) {
        mSearchProgressBar.setVisibility(View.GONE);
        mStatusTextView.setVisibility(View.VISIBLE);
        switch (error) {
            case ERROR_INTERNAL:
                mStatusTextView.setTextColor(Color.RED);
                mStatusTextView.setText(R.string.search_modpack_error);
                break;
            case ERROR_NO_RESULTS:
                mStatusTextView.setTextColor(mDefaultTextColor);
                mStatusTextView.setText(R.string.search_modpack_no_result);
                break;
        }
    }

    private void searchMods(String name) {
        mSearchProgressBar.setVisibility(View.VISIBLE);
        mSearchFilters.name = name == null ? "" : name;
        mModItemAdapter.performSearchQuery(mSearchFilters);
    }

    private void recreateAdapter() {
        Context context = requireContext();
        modpackApi = pickApi(context.getApplicationContext());
        if (mModItemAdapter != null) ProgressKeeper.removeTaskCountListener(mModItemAdapter);
        mModItemAdapter = new ModItemAdapter(getResources(), modpackApi, this);
        ProgressKeeper.addTaskCountListener(mModItemAdapter);
        RecyclerView list = getView() != null ? getView().findViewById(R.id.search_mod_list) : null;
        if (list != null) list.setAdapter(mModItemAdapter);
    }

    private void setupChips(View view) {
        mChipsRow = view.findViewById(R.id.search_mod_chips);
        int[] ids = {R.id.chip_all, R.id.chip_popular, R.id.chip_new, R.id.chip_updated,
                R.id.chip_tech, R.id.chip_adventure, R.id.chip_magic, R.id.chip_quests};
        String[] chips = {"all", "popular", "new", "updated", "tech", "adventure", "magic", "quests"};
        for (int i = 0; i < ids.length; i++) {
            final String chip = chips[i];
            Button button = view.findViewById(ids[i]);
            button.setOnClickListener(v -> {
                mActiveChip = chip;
                switch (chip) {
                    case "popular": mSearchFilters.sort = "downloads"; mSearchFilters.category = null; break;
                    case "new": mSearchFilters.sort = "date"; mSearchFilters.category = null; break;
                    case "updated": mSearchFilters.sort = "updated"; mSearchFilters.category = null; break;
                    case "tech": mSearchFilters.sort = null; mSearchFilters.category = "technology"; break;
                    case "adventure": mSearchFilters.sort = null; mSearchFilters.category = "adventure"; break;
                    case "magic": mSearchFilters.sort = null; mSearchFilters.category = "magic"; break;
                    case "quests": mSearchFilters.sort = null; mSearchFilters.category = "quests"; break;
                    default: mSearchFilters.sort = null; mSearchFilters.category = null; break;
                }
                updateChipStyles();
                recreateAdapter();
                searchMods(mSearchEditText.getText().toString());
            });
        }
        updateChipStyles();
    }

    private void updateChipStyles() {
        if (mChipsRow == null) return;
        String[] chips = {"all", "popular", "new", "updated", "tech", "adventure", "magic", "quests"};
        int[] ids = {R.id.chip_all, R.id.chip_popular, R.id.chip_new, R.id.chip_updated,
                R.id.chip_tech, R.id.chip_adventure, R.id.chip_magic, R.id.chip_quests};
        for (int i = 0; i < ids.length; i++) {
            Button b = mChipsRow.findViewById(ids[i]);
            if (b == null) continue;
            boolean selected = chips[i].equals(mActiveChip);
            b.setBackgroundResource(selected ? R.drawable.zyro_chip_selected : R.drawable.zyro_chip_bg);
            b.setTextColor(getResources().getColor(selected ? R.color.zyro_background : R.color.zyro_chip_text));
        }
    }

    private void displayFilterDialog() {
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(R.layout.dialog_mod_filters)
                .create();

        // setup the view behavior
        dialog.setOnShowListener(dialogInterface -> {
            TextView mSelectedVersion = dialog.findViewById(R.id.search_mod_selected_mc_version_textview);
            Button mSelectVersionButton = dialog.findViewById(R.id.search_mod_mc_version_button);
            Button mApplyButton = dialog.findViewById(R.id.search_mod_apply_filters);

            assert mSelectVersionButton != null;
            assert mSelectedVersion != null;
            assert mApplyButton != null;

            // Setup the expendable list behavior
            mSelectVersionButton.setOnClickListener(v -> VersionSelectorDialog.open(v.getContext(), true, (id, snapshot)-> mSelectedVersion.setText(id)));

            // Apply visually all the current settings
            mSelectedVersion.setText(mSearchFilters.mcVersion);

            // Apply the new settings
            mApplyButton.setOnClickListener(v -> {
                mSearchFilters.mcVersion = mSelectedVersion.getText().toString();
                searchMods(mSearchEditText.getText().toString());
                dialogInterface.dismiss();
            });
        });

        dialog.show();
    }
}
