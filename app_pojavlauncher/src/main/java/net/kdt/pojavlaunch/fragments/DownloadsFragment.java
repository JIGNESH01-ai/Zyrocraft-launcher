package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.TaskHistory;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Real download/task activity: live tasks plus session completed and failed history. */
public class DownloadsFragment extends Fragment {
    public static final String TAG = "DownloadsFragment";

    private static final String TAB_ACTIVE = "active";
    private static final String TAB_DONE = "done";
    private static final String TAB_FAILED = "failed";

    private final Handler mPollHandler = new Handler(Looper.getMainLooper());
    private boolean mPolling;
    private String mTab = TAB_ACTIVE;

    private RecyclerView mList;
    private TextView mStatus;
    private TaskAdapter mAdapter;

    /** Unified row model for the three tabs */
    private static final class Row {
        final String title;
        final String detail;
        final int progress;
        final boolean showProgress;
        final int iconRes;

        Row(String title, String detail, int progress, boolean showProgress, int iconRes) {
            this.title = title;
            this.detail = detail;
            this.progress = progress;
            this.showProgress = showProgress;
            this.iconRes = iconRes;
        }
    }

    private final List<Row> mRows = new ArrayList<>();

    private final Runnable mPollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mPolling) return;
            refreshRows();
            mPollHandler.postDelayed(this, 1000);
        }
    };

    public DownloadsFragment() {
        super(R.layout.fragment_zyro_downloads);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mList = view.findViewById(R.id.zyro_downloads_list);
        mStatus = view.findViewById(R.id.zyro_downloads_status);
        mList.setLayoutManager(new LinearLayoutManager(getContext()));
        mAdapter = new TaskAdapter();
        mList.setAdapter(mAdapter);

        setupTab(view.findViewById(R.id.tab_in_progress), TAB_ACTIVE);
        setupTab(view.findViewById(R.id.tab_completed), TAB_DONE);
        setupTab(view.findViewById(R.id.tab_failed), TAB_FAILED);

        refreshRows();
    }

    @Override
    public void onResume() {
        super.onResume();
        mPolling = true;
        mPollHandler.post(mPollRunnable);
    }

    @Override
    public void onPause() {
        mPolling = false;
        mPollHandler.removeCallbacks(mPollRunnable);
        super.onPause();
    }

    private void setupTab(Button button, String tab) {
        button.setOnClickListener(v -> {
            mTab = tab;
            updateTabStyles();
            refreshRows();
        });
        updateTabStyles();
    }

    private void updateTabStyles() {
        View root = getView();
        if (root == null) return;
        int[] ids = {R.id.tab_in_progress, R.id.tab_completed, R.id.tab_failed};
        String[] tabs = {TAB_ACTIVE, TAB_DONE, TAB_FAILED};
        for (int i = 0; i < ids.length; i++) {
            Button b = root.findViewById(ids[i]);
            if (b == null) continue;
            boolean selected = tabs[i].equals(mTab);
            b.setBackgroundResource(selected ? R.drawable.zyro_chip_selected : R.drawable.zyro_chip_bg);
            b.setTextColor(getResources().getColor(selected ? R.color.zyro_background : R.color.zyro_chip_text));
        }
    }

    private String resolveTitle(int titleRes, String fallback, Object[] args) {
        if (titleRes != 0) {
            try {
                return args != null ? getString(titleRes, args) : getString(titleRes);
            } catch (Exception ignored) {
                return fallback != null ? fallback : getString(R.string.zyro_downloads);
            }
        }
        return fallback != null ? fallback : getString(R.string.zyro_downloads);
    }

    private void refreshRows() {
        if (getView() == null) return;
        mRows.clear();
        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
        switch (mTab) {
            case TAB_ACTIVE:
                for (ProgressKeeper.RunningTask task : ProgressKeeper.getRunningTasks()) {
                    boolean determinate = task.progress >= 0 && task.progress <= 100;
                    mRows.add(new Row(
                            resolveTitle(task.titleRes, task.key, task.args),
                            determinate ? getString(R.string.zyro_status_downloading)
                                    : getString(R.string.zyro_status_installing),
                            Math.max(0, task.progress),
                            determinate,
                            R.drawable.ic_zyro_download));
                }
                break;
            case TAB_DONE:
                for (TaskHistory.Entry entry : TaskHistory.getCompleted()) {
                    mRows.add(new Row(
                            resolveTitle(entry.titleRes, entry.titleFallback, entry.args),
                            getString(R.string.zyro_status_done) + " · "
                                    + format.format(new Date(entry.timestamp)),
                            100, false, R.drawable.ic_zyro_check));
                }
                break;
            case TAB_FAILED:
                for (TaskHistory.Entry entry : TaskHistory.getFailed()) {
                    mRows.add(new Row(
                            getString(R.string.zyro_status_failed),
                            entry.titleFallback == null ? "" : entry.titleFallback
                                    + " · " + format.format(new Date(entry.timestamp)),
                            0, false, R.drawable.ic_zyro_warn));
                }
                break;
        }
        mAdapter.notifyDataSetChanged();
        boolean empty = mRows.isEmpty();
        mStatus.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            switch (mTab) {
                case TAB_ACTIVE: mStatus.setText(R.string.zyro_downloads_empty); break;
                case TAB_DONE: mStatus.setText(R.string.zyro_completed_empty); break;
                default: mStatus.setText(R.string.zyro_failed_empty); break;
            }
        }
    }

    private class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.Holder> {
        @NonNull @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_zyro_download, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Row row = mRows.get(position);
            holder.title.setText(row.title);
            holder.detail.setText(row.detail);
            holder.icon.setImageResource(row.iconRes);
            holder.progress.setVisibility(row.showProgress ? View.VISIBLE : View.GONE);
            holder.percent.setVisibility(row.showProgress ? View.VISIBLE : View.GONE);
            if (row.showProgress) {
                holder.progress.setProgress(row.progress);
                holder.percent.setText(String.format(Locale.ROOT, "%d%%", row.progress));
            }
        }

        @Override
        public int getItemCount() {
            return mRows.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView title, detail, percent;
            final ImageView icon;
            final android.widget.ProgressBar progress;

            Holder(View v) {
                super(v);
                title = v.findViewById(R.id.zyro_download_title);
                detail = v.findViewById(R.id.zyro_download_detail);
                percent = v.findViewById(R.id.zyro_download_percent);
                icon = v.findViewById(R.id.zyro_download_icon);
                progress = v.findViewById(R.id.zyro_download_progress);
            }
        }
    }
}
