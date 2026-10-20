package net.kdt.pojavlaunch.fragments;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.utils.FileUtils;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Local pack management for an instance folder: resource packs, shader packs or mods.
 * Packs can be imported, enabled, disabled and removed.
 */
public class LocalPacksFragment extends Fragment {
    public static final String TAG = "LocalPacksFragment";
    public static final String ARG_FOLDER = "folder";
    public static final String ARG_TITLE = "title";

    public static final String FOLDER_RESOURCE_PACKS = "resourcepacks";
    public static final String FOLDER_SHADER_PACKS = "shaderpacks";
    public static final String FOLDER_MODS = "mods";

    private static final String TAB_ALL = "all";
    private static final String TAB_ENABLED = "enabled";
    private static final String TAB_DISABLED = "disabled";

    private String mFolder = FOLDER_RESOURCE_PACKS;
    private final List<File> mFiles = new ArrayList<>();
    private PackAdapter mAdapter;
    private TextView mStatus;
    private String mTab = TAB_ALL;

    private final ActivityResultLauncher<String> mImportLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.GetContent(),
                    this::onImportUri);

    public LocalPacksFragment() {
        super(R.layout.fragment_zyro_local_packs);
    }

    public static LocalPacksFragment newInstance(String folder, String title) {
        LocalPacksFragment fragment = new LocalPacksFragment();
        Bundle bundle = new Bundle(2);
        bundle.putString(ARG_FOLDER, folder);
        bundle.putString(ARG_TITLE, title);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        if (args != null) {
            String folder = args.getString(ARG_FOLDER);
            if (folder != null) mFolder = folder;
            String title = args.getString(ARG_TITLE);
            TextView titleView = view.findViewById(R.id.zyro_packs_title);
            if (title != null) titleView.setText(title);
        }

        mStatus = view.findViewById(R.id.zyro_packs_status);
        RecyclerView list = view.findViewById(R.id.zyro_packs_list);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        mAdapter = new PackAdapter();
        list.setAdapter(mAdapter);

        TextView warning = view.findViewById(R.id.zyro_packs_warning);
        if (FOLDER_SHADER_PACKS.equals(mFolder)) warning.setVisibility(View.VISIBLE);

        Button importButton = view.findViewById(R.id.zyro_packs_import);
        if (FOLDER_MODS.equals(mFolder)) {
            importButton.setText(R.string.zyro_import_local);
        }
        importButton.setOnClickListener(v -> mImportLauncher.launch("*/*"));

        setupTab(view.findViewById(R.id.tab_all), TAB_ALL);
        setupTab(view.findViewById(R.id.tab_enabled), TAB_ENABLED);
        setupTab(view.findViewById(R.id.tab_disabled), TAB_DISABLED);
        view.findViewById(R.id.tab_open_folder).setOnClickListener(v -> openFolder());

        reload();
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    private void setupTab(Button button, String tab) {
        button.setOnClickListener(v -> {
            mTab = tab;
            updateTabStyles();
            reload();
        });
        updateTabStyles();
    }

    private void updateTabStyles() {
        View root = getView();
        if (root == null) return;
        int[] ids = {R.id.tab_all, R.id.tab_enabled, R.id.tab_disabled};
        String[] tabs = {TAB_ALL, TAB_ENABLED, TAB_DISABLED};
        for (int i = 0; i < ids.length; i++) {
            Button b = root.findViewById(ids[i]);
            if (b == null) continue;
            boolean selected = tabs[i].equals(mTab);
            b.setBackgroundResource(selected ? R.drawable.zyro_chip_selected : R.drawable.zyro_chip_bg);
            b.setTextColor(getResources().getColor(selected ? R.color.zyro_background : R.color.zyro_chip_text));
        }
    }

    @Nullable
    private File getPackDirectory() {
        Instance instance = Instances.loadSelectedInstance();
        if (instance == null) return null;
        File gameDir = instance.getGameDirectory();
        if (gameDir == null) return null;
        File dir = new File(gameDir, mFolder);
        FileUtils.ensureDirectorySilently(dir);
        return dir;
    }

    private void reload() {
        if (getView() == null) return;
        mFiles.clear();
        File dir = getPackDirectory();
        if (dir == null) {
            showStatus(R.string.zyro_no_instance_for_mod);
            mAdapter.notifyDataSetChanged();
            return;
        }
        File[] listed = dir.listFiles();
        if (listed != null) {
            Arrays.sort(listed, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File file : listed) {
                if (!file.isFile()) continue;
                String lower = file.getName().toLowerCase(Locale.ROOT);
                boolean packLike = lower.endsWith(".zip") || lower.endsWith(".jar")
                        || lower.endsWith(".zip.disabled") || lower.endsWith(".jar.disabled");
                if (!packLike) continue;
                boolean enabled = !lower.endsWith(".disabled");
                if (TAB_ENABLED.equals(mTab) && !enabled) continue;
                if (TAB_DISABLED.equals(mTab) && enabled) continue;
                mFiles.add(file);
            }
        }
        mAdapter.notifyDataSetChanged();
        if (mFiles.isEmpty()) showStatus(R.string.zyro_packs_empty);
        else hideStatus();
    }

    private void showStatus(int res) {
        mStatus.setVisibility(View.VISIBLE);
        mStatus.setText(res);
    }

    private void hideStatus() {
        mStatus.setVisibility(View.GONE);
    }

    private void togglePack(File file) {
        String name = file.getName();
        File target;
        if (name.toLowerCase(Locale.ROOT).endsWith(".disabled")) {
            target = new File(file.getParentFile(), name.substring(0, name.length() - ".disabled".length()));
        } else {
            target = new File(file.getParentFile(), name + ".disabled");
        }
        if (!file.renameTo(target) && getContext() != null) {
            Toast.makeText(getContext(), R.string.global_error, Toast.LENGTH_SHORT).show();
        }
        reload();
    }

    private void confirmDelete(File file) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.global_delete)
                .setMessage(file.getName())
                .setPositiveButton(R.string.global_delete, (d, w) -> {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                    reload();
                })
                .setNegativeButton(R.string.global_no, null)
                .show();
    }

    private void openFolder() {
        File dir = getPackDirectory();
        if (dir == null) {
            Toast.makeText(requireContext(), R.string.zyro_error_no_game_dir, Toast.LENGTH_SHORT).show();
            return;
        }
        Tools.openPath(requireContext(), dir, false);
    }

    private void onImportUri(@Nullable Uri uri) {
        if (uri == null || getContext() == null) return;
        File dir = getPackDirectory();
        if (dir == null) {
            Toast.makeText(requireContext(), R.string.zyro_error_no_game_dir, Toast.LENGTH_SHORT).show();
            return;
        }
        PojavApplication.sExecutorService.execute(() -> {
            String fileName = Tools.getFileName(requireContext().getApplicationContext(), uri);
            if (fileName == null) fileName = "imported-pack.zip";
            File out = new File(dir, fileName);
            try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                 OutputStream os = new FileOutputStream(out)) {
                if (in == null) throw new IOException("Could not open " + uri);
                IOUtils.copy(in, os);
                Tools.runOnUiThread(() -> {
                    if (getContext() != null)
                        Toast.makeText(getContext(), R.string.zyro_pack_enabled, Toast.LENGTH_SHORT).show();
                    reload();
                });
            } catch (IOException e) {
                Tools.runOnUiThread(() -> {
                    //noinspection ResultOfMethodCallIgnored
                    out.delete();
                });
                Tools.showErrorRemote(e);
            }
        });
    }

    private class PackAdapter extends RecyclerView.Adapter<PackAdapter.Holder> {
        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_zyro_pack, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            File file = mFiles.get(position);
            String name = file.getName();
            boolean enabled = !name.toLowerCase(Locale.ROOT).endsWith(".disabled");
            holder.name.setText(name);
            holder.state.setText(getString(enabled ? R.string.zyro_pack_enabled : R.string.zyro_pack_disabled)
                    + " · " + android.text.format.Formatter.formatShortFileSize(getContext(), file.length()));
            holder.icon.setImageResource(FOLDER_MODS.equals(mFolder)
                    ? R.drawable.ic_zyro_puzzle : R.drawable.ic_zyro_image);
            holder.toggle.setOnCheckedChangeListener(null);
            holder.toggle.setChecked(enabled);
            holder.toggle.setOnCheckedChangeListener((v, checked) -> togglePack(file));
            holder.delete.setOnClickListener(v -> confirmDelete(file));
        }

        @Override
        public int getItemCount() {
            return mFiles.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView name, state;
            final android.widget.ImageView icon;
            final SwitchCompat toggle;
            final ImageButton delete;

            Holder(View v) {
                super(v);
                name = v.findViewById(R.id.zyro_pack_name);
                state = v.findViewById(R.id.zyro_pack_state);
                icon = v.findViewById(R.id.zyro_pack_icon);
                toggle = v.findViewById(R.id.zyro_pack_toggle);
                delete = v.findViewById(R.id.zyro_pack_delete);
            }
        }
    }
}
