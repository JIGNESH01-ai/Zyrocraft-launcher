package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.instances.DisplayInstance;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.InstanceIconProvider;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.utils.VersionDescriptor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lists the installed instances with play, edit, duplicate and delete actions. */
public class InstancesFragment extends Fragment {
    public static final String TAG = "InstancesFragment";

    private final List<DisplayInstance> mAllInstances = new ArrayList<>();
    private final List<DisplayInstance> mVisibleInstances = new ArrayList<>();
    private InstanceListAdapter mAdapter;
    private EditText mSearch;
    private TextView mStatus;
    private ProgressBar mProgress;
    private String mFilterKey = "all";

    public InstancesFragment() {
        super(R.layout.fragment_rc_instances);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mSearch = view.findViewById(R.id.rc_instances_search);
        mStatus = view.findViewById(R.id.rc_instances_status);
        mProgress = view.findViewById(R.id.rc_instances_progress);
        RecyclerView list = view.findViewById(R.id.rc_instances_list);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        mAdapter = new InstanceListAdapter();
        list.setAdapter(mAdapter);

        view.findViewById(R.id.rc_instances_create).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), ProfileTypeSelectFragment.class,
                        ProfileTypeSelectFragment.TAG, null));

        mSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { applyFilter(); }
        });

        setupFilterChip(view.findViewById(R.id.filter_all), "all");
        setupFilterChip(view.findViewById(R.id.filter_vanilla), "vanilla");
        setupFilterChip(view.findViewById(R.id.filter_forge), "forge");
        setupFilterChip(view.findViewById(R.id.filter_fabric), "fabric");
        setupFilterChip(view.findViewById(R.id.filter_neoforge), "neoforge");
        setupFilterChip(view.findViewById(R.id.filter_quilt), "quilt");

        reloadInstances();
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadInstances();
    }

    private void setupFilterChip(Button button, String key) {
        button.setOnClickListener(v -> {
            mFilterKey = key;
            updateChipStyles();
            applyFilter();
        });
        updateChipStyles();
    }

    private void updateChipStyles() {
        View root = getView();
        if (root == null) return;
        int[] ids = {R.id.filter_all, R.id.filter_vanilla, R.id.filter_forge,
                R.id.filter_fabric, R.id.filter_neoforge, R.id.filter_quilt};
        String[] keys = {"all", "vanilla", "forge", "fabric", "neoforge", "quilt"};
        for (int i = 0; i < ids.length; i++) {
            Button b = root.findViewById(ids[i]);
            if (b == null) continue;
            boolean selected = keys[i].equals(mFilterKey);
            b.setBackgroundResource(selected ? R.drawable.rc_chip_selected : R.drawable.rc_chip_bg);
            b.setTextColor(getResources().getColor(selected ? R.color.rc_background : R.color.rc_chip_text));
        }
    }

    private void reloadInstances() {
        if (getView() == null) return;
        mProgress.setVisibility(View.VISIBLE);
        mStatus.setVisibility(View.GONE);
        PojavApplication.sExecutorService.execute(() -> {
            List<DisplayInstance> loaded;
            try {
                loaded = Instances.loadDisplay().list;
            } catch (IOException e) {
                Tools.runOnUiThread(() -> {
                    if (getView() == null) return;
                    mProgress.setVisibility(View.GONE);
                    mStatus.setVisibility(View.VISIBLE);
                    mStatus.setText(R.string.global_error);
                });
                Tools.showErrorRemote(e);
                return;
            }
            Tools.runOnUiThread(() -> {
                if (getView() == null) return;
                mAllInstances.clear();
                mAllInstances.addAll(loaded);
                mProgress.setVisibility(View.GONE);
                applyFilter();
                ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, null);
            });
        });
    }

    private void applyFilter() {
        mVisibleInstances.clear();
        String query = mSearch.getText() == null ? "" :
                mSearch.getText().toString().toLowerCase(Locale.ROOT).trim();
        for (DisplayInstance instance : mAllInstances) {
            if (!"all".equals(mFilterKey)
                    && !VersionDescriptor.loaderFilterKey(instance.versionId).equals(mFilterKey)) continue;
            if (!query.isEmpty()) {
                String name = instance.name == null ? "" : instance.name.toLowerCase(Locale.ROOT);
                String version = instance.versionId == null ? "" : instance.versionId.toLowerCase(Locale.ROOT);
                if (!name.contains(query) && !version.contains(query)) continue;
            }
            mVisibleInstances.add(instance);
        }
        mAdapter.notifyDataSetChanged();
        boolean empty = mVisibleInstances.isEmpty();
        mStatus.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) mStatus.setText(R.string.rc_instances_empty);
    }

    private void play(DisplayInstance instance) {
        Instances.setSelectedInstance(instance);
        ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
    }

    private void edit(DisplayInstance instance) {
        Instances.setSelectedInstance(instance);
        Tools.swapFragment(requireActivity(), InstanceEditorFragment.class,
                InstanceEditorFragment.TAG, null);
    }

    private void duplicate(DisplayInstance instance) {
        PojavApplication.sExecutorService.execute(() -> {
            try {
                Instances.copyInstance(instance, null);
                Tools.runOnUiThread(() -> {
                    if (getContext() != null)
                        Toast.makeText(getContext(), R.string.rc_duplicate_success, Toast.LENGTH_SHORT).show();
                    reloadInstances();
                });
            } catch (IOException e) {
                Tools.runOnUiThread(() -> {
                    if (getContext() != null)
                        Toast.makeText(getContext(), R.string.rc_duplicate_failed, Toast.LENGTH_SHORT).show();
                });
                Tools.showErrorRemote(e);
            }
        });
    }

    private void confirmDelete(DisplayInstance instance) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.rc_delete_instance_title)
                .setMessage(R.string.rc_delete_instance_message)
                .setPositiveButton(R.string.global_delete, (d, w) ->
                        PojavApplication.sExecutorService.execute(() -> {
                            try {
                                Instance full = Instances.loadFullInstance(instance);
                                if (full != null) Instances.removeInstance(full);
                                Tools.runOnUiThread(this::reloadInstances);
                            } catch (IOException e) {
                                Tools.showErrorRemote(e);
                            }
                        }))
                .setNegativeButton(R.string.global_no, null)
                .show();
    }

    private class InstanceListAdapter extends RecyclerView.Adapter<InstanceListAdapter.Holder> {
        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_rc_instance, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            DisplayInstance instance = mVisibleInstances.get(position);
            holder.name.setText(instance.name == null ? instance.versionId : instance.name);
            holder.subtitle.setText(VersionDescriptor.minecraftVersion(instance.versionId));
            holder.loaderBadge.setText(VersionDescriptor.loaderName(instance.versionId));
            holder.icon.setImageDrawable(
                    InstanceIconProvider.fetchIcon(getResources(), instance));
            holder.play.setOnClickListener(v -> play(instance));
            holder.edit.setOnClickListener(v -> edit(instance));
            holder.duplicate.setOnClickListener(v -> duplicate(instance));
            holder.delete.setOnClickListener(v -> confirmDelete(instance));
        }

        @Override
        public int getItemCount() {
            return mVisibleInstances.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView name, subtitle, loaderBadge;
            final android.widget.ImageView icon;
            final Button play, edit;
            final ImageButton duplicate, delete;

            Holder(View v) {
                super(v);
                name = v.findViewById(R.id.rc_instance_name);
                subtitle = v.findViewById(R.id.rc_instance_subtitle);
                loaderBadge = v.findViewById(R.id.rc_instance_loader_badge);
                icon = v.findViewById(R.id.rc_instance_icon);
                play = v.findViewById(R.id.rc_instance_play);
                edit = v.findViewById(R.id.rc_instance_edit);
                duplicate = v.findViewById(R.id.rc_instance_duplicate);
                delete = v.findViewById(R.id.rc_instance_delete);
            }
        }
    }
}
