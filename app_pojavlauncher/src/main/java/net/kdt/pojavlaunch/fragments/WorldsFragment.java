package net.kdt.pojavlaunch.fragments;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
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
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Lists the worlds stored in the selected instance (saves/level.dat) and lets
 * the player open their folder or delete them. Nothing is fetched from the
 * network here.
 */
public class WorldsFragment extends Fragment {
    public static final String TAG = "WorldsFragment";

    private final List<File> mWorlds = new ArrayList<>();
    private WorldAdapter mAdapter;
    private TextView mStatus;

    public WorldsFragment() {
        super(R.layout.fragment_zyro_worlds);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mStatus = view.findViewById(R.id.zyro_worlds_status);
        RecyclerView list = view.findViewById(R.id.zyro_worlds_list);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        mAdapter = new WorldAdapter();
        list.setAdapter(mAdapter);

        ImageButton refresh = view.findViewById(R.id.zyro_worlds_refresh);
        refresh.setOnClickListener(v -> reload());

        reload();
    }

    private void reload() {
        mWorlds.clear();
        File saves = savesDirectory();
        if (saves != null && saves.isDirectory()) {
            File[] children = saves.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.isDirectory() && new File(child, "level.dat").exists())
                        mWorlds.add(child);
                }
            }
        }
        Collections.sort(mWorlds, (first, second) ->
                Long.compare(second.lastModified(), first.lastModified()));
        if (mAdapter != null) mAdapter.notifyDataSetChanged();
        if (mStatus != null) {
            mStatus.setVisibility(mWorlds.isEmpty() ? View.VISIBLE : View.GONE);
            mStatus.setText(R.string.zyro_worlds_empty);
        }
    }

    private static File savesDirectory() {
        Instance instance = Instances.loadSelectedInstance();
        if (instance == null) return null;
        return new File(instance.getGameDirectory(), "saves");
    }

    private void confirmDelete(File world) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.zyro_world_delete_title)
                .setMessage(getString(R.string.zyro_world_delete_message, world.getName()))
                .setPositiveButton(R.string.zyro_world_delete, (dialog, which) ->
                        PojavApplication.sExecutorService.execute(() -> {
                            boolean deleted;
                            try {
                                FileUtils.deleteDirectory(world);
                                deleted = !world.exists();
                            } catch (IOException e) {
                                deleted = false;
                            }
                            boolean finalDeleted = deleted;
                            Tools.runOnUiThread(() -> {
                                if (!isAdded()) return;
                                Toast.makeText(requireContext(), finalDeleted
                                        ? R.string.zyro_world_deleted
                                        : R.string.zyro_world_delete_failed, Toast.LENGTH_SHORT).show();
                                reload();
                            });
                        }))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private class WorldAdapter extends RecyclerView.Adapter<WorldAdapter.WorldHolder> {

        @NonNull
        @Override
        public WorldHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_zyro_world, parent, false);
            return new WorldHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull WorldHolder holder, int position) {
            holder.bind(mWorlds.get(position));
        }

        @Override
        public int getItemCount() {
            return mWorlds.size();
        }

        private class WorldHolder extends RecyclerView.ViewHolder {
            private final ImageView mIcon;
            private final TextView mName;
            private final TextView mDetail;
            private final ImageButton mOpen;
            private final ImageButton mDelete;

            WorldHolder(View view) {
                super(view);
                mIcon = view.findViewById(R.id.world_icon);
                mName = view.findViewById(R.id.world_name);
                mDetail = view.findViewById(R.id.world_detail);
                mOpen = view.findViewById(R.id.world_open);
                mDelete = view.findViewById(R.id.world_delete);
            }

            void bind(File world) {
                mName.setText(world.getName());
                String date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(new Date(world.lastModified()));
                mDetail.setText(date);
                mIcon.setImageResource(R.mipmap.ic_launcher);

                mOpen.setOnClickListener(v ->
                        Tools.openPath(requireContext(), world, false));
                mDelete.setOnClickListener(v -> confirmDelete(world));

                File icon = new File(world, "icon.png");
                if (icon.isFile()) {
                    Bitmap bitmap = BitmapFactory.decodeFile(icon.getAbsolutePath());
                    if (bitmap != null) mIcon.setImageBitmap(bitmap);
                }
            }
        }
    }
}
