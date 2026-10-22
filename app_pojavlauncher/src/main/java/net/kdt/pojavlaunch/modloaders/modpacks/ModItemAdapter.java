package net.kdt.pojavlaunch.modloaders.modpacks;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.recyclerview.widget.RecyclerView;

import com.kdt.SimpleArrayAdapter;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackApi;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.ImageReceiver;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.ModIconCache;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Future;

/**
 * Grid adapter for the browse screens. Each card carries the project icon, its
 * download count, a favorite toggle and the install action. The install button
 * opens a version picker built from the real version list of the project.
 */
public class ModItemAdapter extends RecyclerView.Adapter<ModItemAdapter.ViewHolder> implements TaskCountListener {
    private final Set<ViewHolder> mViewHolderSet = Collections.newSetFromMap(new WeakHashMap<>());
    private final ModIconCache mIconCache = new ModIconCache();
    private final SearchResultCallback mSearchResultCallback;
    private final ModpackApi mModpackApi;

    private final List<ModItem> mItems = new ArrayList<>();
    private Future<?> mTaskInProgress;
    private SearchFilters mSearchFilters;
    private boolean mTasksRunning;
    private boolean mStaticContent;

    public ModItemAdapter(ModpackApi api, SearchResultCallback callback) {
        mModpackApi = api;
        mSearchResultCallback = callback;
    }

    /** Replaces the list with the first page of a new query */
    public void performSearchQuery(SearchFilters searchFilters) {
        cancelRunningTask();
        mStaticContent = false;
        this.mSearchFilters = searchFilters;
        mItems.clear();
        notifyDataSetChanged();
        mTaskInProgress = new SelfReferencingFuture(new SearchApiTask(mSearchFilters))
                .startOnExecutor(PojavApplication.sExecutorService);
    }

    /** Shows a locally provided list (favorites), no network involved */
    public void setStaticItems(List<ModItem> items) {
        cancelRunningTask();
        mStaticContent = true;
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
        mSearchResultCallback.onSearchFinished(mItems.size());
    }

    private void cancelRunningTask() {
        if (mTaskInProgress != null) {
            mTaskInProgress.cancel(true);
            mTaskInProgress = null;
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        View view = LayoutInflater.from(viewGroup.getContext())
                .inflate(R.layout.view_mod_card, viewGroup, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(mItems.get(position));
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @Override
    public boolean onUpdateTaskCount(int taskCount) {
        Tools.runOnUiThread(() -> {
            mTasksRunning = taskCount != 0;
            for (ViewHolder viewHolder : mViewHolderSet) {
                viewHolder.updateInstallButtonState();
            }
        });
        return false;
    }

    /** One card */
    public class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView mIconView;
        private final TextView mTitleView;
        private final TextView mDescriptionView;
        private final TextView mDownloadsView;
        private final TextView mSourceView;
        private final ImageView mFavoriteView;
        private final Button mInstallButton;

        private ModItem mItem;
        private Bitmap mThumbnailBitmap;
        private ImageReceiver mImageReceiver;

        public ViewHolder(View view) {
            super(view);
            mViewHolderSet.add(this);
            mIconView = view.findViewById(R.id.mod_thumbnail_imageview);
            mTitleView = view.findViewById(R.id.mod_title_textview);
            mDescriptionView = view.findViewById(R.id.mod_body_textview);
            mDownloadsView = view.findViewById(R.id.mod_downloads_textview);
            mSourceView = view.findViewById(R.id.mod_source_textview);
            mFavoriteView = view.findViewById(R.id.mod_favorite_button);
            mInstallButton = view.findViewById(R.id.mod_install_button);

            mInstallButton.setOnClickListener(v -> showInstallDialog());
            mFavoriteView.setOnClickListener(v -> toggleFavorite());
            view.setOnClickListener(v -> showInstallDialog());
        }

        void bind(ModItem item) {
            mItem = item;

            if (mThumbnailBitmap != null) {
                mIconView.setImageBitmap(null);
                mThumbnailBitmap.recycle();
                mThumbnailBitmap = null;
            }
            if (mImageReceiver != null) {
                mIconCache.cancelImage(mImageReceiver);
                mImageReceiver = null;
            }
            if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
                mImageReceiver = bitmap -> {
                    mImageReceiver = null;
                    mThumbnailBitmap = bitmap;
                    RoundedBitmapDrawable drawable =
                            RoundedBitmapDrawableFactory.create(mIconView.getResources(), bitmap);
                    drawable.setCornerRadius(
                            mIconView.getResources().getDimension(R.dimen._6sdp));
                    mIconView.setImageDrawable(drawable);
                };
                mIconCache.getImage(mImageReceiver, item.getIconCacheTag(), item.imageUrl);
            } else {
                mIconView.setImageResource(R.mipmap.ic_launcher);
            }

            mTitleView.setText(item.title);
            mDescriptionView.setText(item.description);
            mSourceView.setText(item.apiSource == Constants.SOURCE_CURSEFORGE
                    ? R.string.rc_source_curseforge : R.string.rc_source_modrinth);

            if (item.downloads > 0) {
                mDownloadsView.setVisibility(View.VISIBLE);
                mDownloadsView.setText(formatCount(item.downloads));
            } else {
                mDownloadsView.setVisibility(View.GONE);
            }

            updateFavoriteState();
            updateInstallButtonState();
        }

        private void updateFavoriteState() {
            if (mItem == null) return;
            Context context = mFavoriteView.getContext();
            boolean favorite = ModFavorites.contains(context, mItem);
            mFavoriteView.setImageResource(favorite
                    ? R.drawable.ic_rc_heart : R.drawable.ic_rc_heart_outline);
            mFavoriteView.setImageTintList(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(context, favorite
                            ? R.color.rc_purple : R.color.rc_text_secondary)));
        }

        private void toggleFavorite() {
            if (mItem == null) return;
            Context context = mFavoriteView.getContext();
            boolean favorite = ModFavorites.toggle(context, mItem);
            updateFavoriteState();
            if (mStaticContent && !favorite) {
                int index = mItems.indexOf(mItem);
                if (index >= 0) {
                    mItems.remove(index);
                    notifyItemRemoved(index);
                    mSearchResultCallback.onSearchFinished(mItems.size());
                }
            }
        }

        void updateInstallButtonState() {
            if (mInstallButton == null) return;
            mInstallButton.setEnabled(!mTasksRunning);
        }

        private void showInstallDialog() {
            if (mItem == null || mTasksRunning) return;
            final Context context = mInstallButton.getContext();
            View content = LayoutInflater.from(context).inflate(R.layout.dialog_mod_versions, null);
            TextView status = content.findViewById(R.id.mod_dialog_status);
            Spinner spinner = content.findViewById(R.id.mod_dialog_version_spinner);

            final ModDetail[] detailHolder = new ModDetail[1];
            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setTitle(mItem.title)
                    .setView(content)
                    .setPositiveButton(R.string.rc_install, null)
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();

            dialog.setOnShowListener(d -> {
                Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                positive.setEnabled(false);
                positive.setOnClickListener(v -> {
                    ModDetail detail = detailHolder[0];
                    int position = spinner.getSelectedItemPosition();
                    if (detail == null || position < 0) return;
                    Context appContext = context.getApplicationContext();
                    if (mItem.isModpack()) {
                        mModpackApi.handleModpackInstallation(appContext, detail, position);
                    } else {
                        ModInstallTask.install(appContext, detail, position);
                    }
                    dialog.dismiss();
                });
            });

            Future<?> future = new SelfReferencingFuture(myFuture -> {
                ModDetail detail = mModpackApi.getModDetails(mItem);
                Tools.runOnUiThread(() -> {
                    if (myFuture.isCancelled() || !dialog.isShowing()) return;
                    detailHolder[0] = detail;
                    if (detail == null || detail.versionNames == null || detail.versionNames.length == 0) {
                        status.setText(R.string.rc_versions_unavailable);
                        spinner.setVisibility(View.GONE);
                        return;
                    }
                    status.setVisibility(View.GONE);
                    spinner.setVisibility(View.VISIBLE);
                    spinner.setAdapter(new SimpleArrayAdapter<>(Arrays.asList(detail.versionNames)));
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!mTasksRunning);
                });
            }).startOnExecutor(PojavApplication.sExecutorService);

            dialog.setOnDismissListener(d -> future.cancel(true));
            dialog.show();
        }
    }

    private static String formatCount(long count) {
        if (count >= 1_000_000L) return String.format(Locale.US, "%.1fM", count / 1_000_000.0);
        if (count >= 1_000L) return String.format(Locale.US, "%.1fK", count / 1_000.0);
        return String.valueOf(count);
    }

    /** Result of a page request */
    public interface SearchResultCallback {
        int ERROR_INTERNAL = 0;
        int ERROR_NO_RESULTS = 1;
        void onSearchFinished(int totalResults);
        void onSearchError(int error);
    }

    private class SearchApiTask implements SelfReferencingFuture.FutureInterface {
        private final SearchFilters mFilters;

        private SearchApiTask(SearchFilters filters) {
            this.mFilters = filters;
        }

        @Override
        public void run(Future<?> myFuture) {
            SearchResult result = mModpackApi.searchMod(mFilters, null);
            ModItem[] items = result != null ? result.results : null;
            int total = result != null ? result.totalResultCount : 0;
            Tools.runOnUiThread(() -> {
                if (myFuture.isCancelled()) return;
                mTaskInProgress = null;
                if (items == null) {
                    mSearchResultCallback.onSearchError(SearchResultCallback.ERROR_INTERNAL);
                    return;
                }
                mItems.clear();
                mItems.addAll(Arrays.asList(items));
                notifyDataSetChanged();
                if (items.length == 0) {
                    mSearchResultCallback.onSearchError(SearchResultCallback.ERROR_NO_RESULTS);
                } else {
                    mSearchResultCallback.onSearchFinished(total);
                }
            });
        }
    }
}
