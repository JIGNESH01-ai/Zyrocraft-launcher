package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.JVersionList;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.tasks.AsyncVersionList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Minecraft version browser backed by the real Mojang version manifest. */
public class VersionsFragment extends Fragment {
    public static final String TAG = "VersionsFragment";

    private static final String TAB_ALL = "all";
    private static final String TAB_RELEASE = "release";
    private static final String TAB_SNAPSHOT = "snapshot";
    private static final String TAB_MODDED = "modded";

    private final List<JVersionList.Version> mAllVersions = new ArrayList<>();
    private final List<JVersionList.Version> mVisibleVersions = new ArrayList<>();
    private VersionListAdapter mAdapter;
    private EditText mSearch;
    private TextView mStatus;
    private ProgressBar mProgress;
    private RecyclerView mList;
    private LinearLayout mModdedPanel;
    private String mTab = TAB_ALL;
    private boolean mLoading;

    public VersionsFragment() {
        super(R.layout.fragment_rc_versions);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mSearch = view.findViewById(R.id.rc_versions_search);
        mStatus = view.findViewById(R.id.rc_versions_status);
        mProgress = view.findViewById(R.id.rc_versions_progress);
        mList = view.findViewById(R.id.rc_versions_list);
        mModdedPanel = view.findViewById(R.id.rc_modded_panel);

        mList.setLayoutManager(new LinearLayoutManager(getContext()));
        mAdapter = new VersionListAdapter();
        mList.setAdapter(mAdapter);

        mSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { applyFilter(); }
        });

        setupTab(view.findViewById(R.id.tab_all), TAB_ALL);
        setupTab(view.findViewById(R.id.tab_release), TAB_RELEASE);
        setupTab(view.findViewById(R.id.tab_snapshot), TAB_SNAPSHOT);
        setupTab(view.findViewById(R.id.tab_modded), TAB_MODDED);

        view.findViewById(R.id.rc_versions_refresh).setOnClickListener(v -> refreshList(true));
        view.findViewById(R.id.rc_modded_open).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), ProfileTypeSelectFragment.class,
                        ProfileTypeSelectFragment.TAG, null));

        JVersionList cached = (JVersionList) ExtraCore.getValue(ExtraConstants.RELEASE_TABLE);
        if (cached != null && cached.versions != null) {
            applyVersionList(cached);
        } else {
            refreshList(false);
        }
    }

    private void refreshList(boolean force) {
        if (mLoading) return;
        mLoading = true;
        mProgress.setVisibility(View.VISIBLE);
        mStatus.setVisibility(View.GONE);
        new AsyncVersionList().getVersionList(versions -> {
            mLoading = false;
            if (getView() == null) return;
            mProgress.setVisibility(View.GONE);
            if (versions == null || versions.versions == null) {
                mStatus.setVisibility(View.VISIBLE);
                mStatus.setText(R.string.rc_versions_error);
                return;
            }
            ExtraCore.setValue(ExtraConstants.RELEASE_TABLE, versions);
            applyVersionList(versions);
        });
    }

    private void applyVersionList(JVersionList list) {
        mAllVersions.clear();
        if (list.versions != null) {
            for (JVersionList.Version version : list.versions) {
                mAllVersions.add(version);
            }
        }
        applyFilter();
    }

    private void setupTab(Button button, String tab) {
        button.setOnClickListener(v -> {
            mTab = tab;
            updateTabStyles();
            applyFilter();
        });
        updateTabStyles();
    }

    private void updateTabStyles() {
        View root = getView();
        if (root == null) return;
        int[] ids = {R.id.tab_all, R.id.tab_release, R.id.tab_snapshot, R.id.tab_modded};
        String[] tabs = {TAB_ALL, TAB_RELEASE, TAB_SNAPSHOT, TAB_MODDED};
        for (int i = 0; i < ids.length; i++) {
            Button b = root.findViewById(ids[i]);
            if (b == null) continue;
            boolean selected = tabs[i].equals(mTab);
            b.setBackgroundResource(selected ? R.drawable.rc_chip_selected : R.drawable.rc_chip_bg);
            b.setTextColor(getResources().getColor(selected ? R.color.rc_background : R.color.rc_chip_text));
        }
    }

    private void applyFilter() {
        boolean modded = TAB_MODDED.equals(mTab);
        mModdedPanel.setVisibility(modded ? View.VISIBLE : View.GONE);
        mList.setVisibility(modded ? View.GONE : View.VISIBLE);
        if (modded) {
            mStatus.setVisibility(View.GONE);
            return;
        }
        mVisibleVersions.clear();
        String query = mSearch.getText() == null ? "" :
                mSearch.getText().toString().toLowerCase(Locale.ROOT).trim();
        for (JVersionList.Version version : mAllVersions) {
            if (TAB_RELEASE.equals(mTab) && !"release".equals(version.type)) continue;
            if (TAB_SNAPSHOT.equals(mTab) && !"snapshot".equals(version.type)) continue;
            if (!query.isEmpty() && (version.id == null
                    || !version.id.toLowerCase(Locale.ROOT).contains(query))) continue;
            mVisibleVersions.add(version);
        }
        mAdapter.notifyDataSetChanged();
        boolean empty = mVisibleVersions.isEmpty() && !mLoading;
        mStatus.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) mStatus.setText(R.string.rc_versions_empty);
    }

    private void installVersion(JVersionList.Version version) {
        net.kdt.pojavlaunch.PojavApplication.sExecutorService.execute(() -> {
            try {
                Instances.createInstance(inst -> {
                    inst.versionId = version.id;
                    inst.name = version.id;
                    inst.sharedData = true;
                }, "mc");
                Tools.runOnUiThread(() -> {
                    if (getContext() == null) return;
                    Toast.makeText(getContext(),
                            R.string.rc_install_created, Toast.LENGTH_LONG).show();
                    ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, null);
                });
            } catch (Exception e) {
                Tools.showErrorRemote(e);
            }
        });
    }

    private class VersionListAdapter extends RecyclerView.Adapter<VersionListAdapter.Holder> {
        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_rc_version, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            JVersionList.Version version = mVisibleVersions.get(position);
            holder.id.setText(version.id);
            holder.type.setText(version.type);
            JVersionList list = (JVersionList) ExtraCore.getValue(ExtraConstants.RELEASE_TABLE);
            boolean latest = list != null && list.latest != null && version.id != null
                    && (version.id.equals(list.latest.get("release"))
                        || version.id.equals(list.latest.get("snapshot")));
            holder.badge.setVisibility(latest ? View.VISIBLE : View.GONE);
            if (latest) holder.badge.setText(R.string.rc_version_type_latest);
            holder.install.setOnClickListener(v -> installVersion(version));
        }

        @Override
        public int getItemCount() {
            return mVisibleVersions.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView id, type, badge;
            final Button install;

            Holder(View v) {
                super(v);
                id = v.findViewById(R.id.rc_version_id);
                type = v.findViewById(R.id.rc_version_type);
                badge = v.findViewById(R.id.rc_version_badge);
                install = v.findViewById(R.id.rc_version_install);
            }
        }
    }
}
