package net.kdt.pojavlaunch;

import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.system.Os;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentManager;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.extra.ExtraListener;
import net.kdt.pojavlaunch.fragments.DownloadsFragment;
import net.kdt.pojavlaunch.fragments.InstancesFragment;
import net.kdt.pojavlaunch.fragments.LocalPacksFragment;
import net.kdt.pojavlaunch.fragments.MainMenuFragment;
import net.kdt.pojavlaunch.fragments.MicrosoftLoginFragment;
import net.kdt.pojavlaunch.fragments.SearchModFragment;
import net.kdt.pojavlaunch.fragments.SelectAuthFragment;
import net.kdt.pojavlaunch.fragments.VersionsFragment;
import net.kdt.pojavlaunch.fragments.WorldsFragment;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.InstanceInstaller;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.lifecycle.ContextAwareDoneListener;
import net.kdt.pojavlaunch.lifecycle.ContextExecutor;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.IconCacheJanitor;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ProjectType;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;
import net.kdt.pojavlaunch.services.ProgressServiceKeeper;
import net.kdt.pojavlaunch.tasks.MoJsonExtras;
import net.kdt.pojavlaunch.tasks.AsyncVersionList;
import net.kdt.pojavlaunch.tasks.MoJsonDownloader;
import net.kdt.pojavlaunch.utils.NotificationUtils;

import net.kdt.pojavlaunch.R;

public class LauncherActivity extends BaseActivity {
    public static final String SETTING_FRAGMENT_TAG = "SETTINGS_FRAGMENT";

    private ConstraintLayout mRootLayout;
    private View mTopBar;
    private HorizontalScrollView mTabsScroll;
    private FragmentContainerView mFragmentView;
    private boolean mBrowseHeaderVisible;
    private ImageButton mSettingsButton;
    private ProgressLayout mProgressLayout;
    private ProgressServiceKeeper mProgressServiceKeeper;
    private NotificationManager mNotificationManager;
    private static ActivityResultLauncher<String> mRequestPermissionLauncher;

    /* Left navigation rail entries, in the order they are declared in view_zyro_nav_rail.xml */
    private static final int NAV_NONE = -1;
    private static final int NAV_HOME = 0;
    private static final int NAV_INSTANCES = 1;
    private static final int NAV_VERSIONS = 2;
    private static final int NAV_MODS = 3;
    private static final int NAV_MODPACKS = 4;
    private static final int NAV_RESOURCE_PACKS = 5;
    private static final int NAV_SHADERS = 6;
    private static final int NAV_DOWNLOADS = 7;
    private static final int NAV_SETTINGS = 8;
    private ImageView[] mNavItems;
    private TextView[] mTabItems;
    private int mSelectedTab = TAB_NONE;

    /* Content tabs declared in view_zyro_browse_tabs.xml */
    private static final int TAB_MODPACKS = 0;
    private static final int TAB_MODS = 1;
    private static final int TAB_RESOURCE_PACKS = 2;
    private static final int TAB_SHADERS = 3;
    private static final int TAB_WORLDS = 4;
    private static final int TAB_VANILLA = 5;
    private static final int TAB_FAVORITES = 6;
    private static final int TAB_NONE = -1;
    private static final int HEADER_ANIM_DURATION = 200;

    /* Allows to switch from one button "type" to another */
    private final FragmentManager.FragmentLifecycleCallbacks mFragmentCallbackListener = new FragmentManager.FragmentLifecycleCallbacks() {
        @Override
        public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
            // The settings button only exists in the portrait layout, the landscape
            // layout reaches settings from the navigation rail.
            if (mSettingsButton != null) {
                mSettingsButton.setImageDrawable(ContextCompat.getDrawable(getBaseContext(), f instanceof MainMenuFragment
                        ? R.drawable.ic_px_sliders : R.drawable.ic_px_home));
            }
            updateNavSelection(f);
        }
    };

    /* Listener for the back button in settings */
    private final ExtraListener<String> mBackPreferenceListener = (key, value) -> {
        if(value.equals("true")) onBackPressed();
        return false;
    };

    /* Listener for the auth method selection screen */
    private final ExtraListener<Boolean> mSelectAuthMethod = (key, value) -> {
        // The "false" value is used to stop auth method selection
        FragmentManager manager = getSupportFragmentManager();
        if(!value || manager.isStateSaved()) return false;
        Fragment fragment = manager.findFragmentById(mFragmentView.getId());
        // Allow starting the add account only from the main menu, should it be moved to fragment itself ?
        if(!(fragment instanceof MainMenuFragment)) return false;

        Tools.swapFragment(this, SelectAuthFragment.class, SelectAuthFragment.TAG, null);
        return false;
    };

    /* Listener for the settings fragment */
    private final View.OnClickListener mSettingButtonListener = v -> {
        FragmentManager manager = getSupportFragmentManager();
        if(manager.isStateSaved()) return;
        Fragment fragment = manager.findFragmentById(mFragmentView.getId());
        if(fragment instanceof MainMenuFragment){
            Tools.swapFragment(this, LauncherPreferenceFragment.class, SETTING_FRAGMENT_TAG, null);
        } else{
            // The setting button doubles as a home button now
            Tools.backToMainMenu(this);
        }
    };

    private final ExtraListener<Boolean> mLaunchGameListener = (key, value) -> {
        if(mProgressLayout.hasProcesses()){
            Toast.makeText(this, R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return false;
        }

        Instance selectedInstance = Instances.loadSelectedInstance();

        if(selectedInstance == null) {
            Toast.makeText(this, R.string.no_instance, Toast.LENGTH_LONG).show();
            return false;
        }

        if(selectedInstance.installer != null) {
            selectedInstance.installer.start();
            return false;
        }

        if (!Tools.isValidString(selectedInstance.versionId)){
            Toast.makeText(this, R.string.error_no_version, Toast.LENGTH_LONG).show();
            return false;
        }

        if(Accounts.getCurrent() == null){
            Toast.makeText(this, R.string.no_saved_accounts, Toast.LENGTH_LONG).show();
            ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true);
            return false;
        }
        String normalizedVersionId = MoJsonExtras.normalizeVersionId(selectedInstance.versionId);
        JVersionList.Version mcVersion = MoJsonExtras.getListedVersion(normalizedVersionId);
        new MoJsonDownloader().start(
                this.getAssets(),
                mcVersion,
                normalizedVersionId,
                new ContextAwareDoneListener(this, normalizedVersionId)
        );
        return false;
    };

    private final TaskCountListener mDoubleLaunchPreventionListener = taskCount -> {
        // Hide the notification that starts the game if there are tasks executing.
        // Prevents the user from trying to launch the game with tasks ongoing.
        if(taskCount > 0) {
            Tools.runOnUiThread(() ->
                    mNotificationManager.cancel(NotificationUtils.NOTIFICATION_ID_GAME_START)
            );
        }
        return false;
    };
    @Override
    protected boolean shouldIgnoreNotch() {
        return getResources().getConfiguration().orientation == ORIENTATION_PORTRAIT;
    }

    @Override
    public boolean setFullscreen() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pojav_launcher);
        MoJsonDownloader.prepareSubstitutionMap(getAssets());

        try {
            Os.setenv("TMPDIR", Tools.DIR_CACHE.getAbsolutePath(), true);
         }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        IconCacheJanitor.runJanitor();

        getWindow().setBackgroundDrawable(null);
        bindViews();
        mRequestPermissionLauncher = this.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isAllowed -> {
                    if(!isAllowed) Tools.runOnUiThread(() -> Toast.makeText(this, R.string.notification_permission_toast, Toast.LENGTH_LONG).show());
                }
        );
        checkNotificationPermission();
        if(LauncherPreferences.PREF_MIGRATION_NOTICE)
            PojavApplication.sExecutorService.submit(this::checkPreviousInstalls);

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        ProgressKeeper.addTaskCountListener(mDoubleLaunchPreventionListener);
        ProgressKeeper.addTaskCountListener((mProgressServiceKeeper = new ProgressServiceKeeper(this)));

        if (mSettingsButton != null) mSettingsButton.setOnClickListener(mSettingButtonListener);
        ProgressKeeper.addTaskCountListener(mProgressLayout);
        ExtraCore.addExtraListener(ExtraConstants.BACK_PREFERENCE, mBackPreferenceListener);
        ExtraCore.addExtraListener(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);

        ExtraCore.addExtraListener(ExtraConstants.LAUNCH_GAME, mLaunchGameListener);

        new AsyncVersionList().getVersionList(versions -> ExtraCore.setValue(ExtraConstants.RELEASE_TABLE, versions));

        mProgressLayout.observe(ProgressLayout.DOWNLOAD_GAME);
        mProgressLayout.observe(ProgressLayout.UNPACK_RUNTIME);
        mProgressLayout.observe(ProgressLayout.INSTALL_MODPACK);
        mProgressLayout.observe(ProgressLayout.AUTHENTICATE);
        mProgressLayout.observe(ProgressLayout.DOWNLOAD_VERSION_LIST);
        mProgressLayout.observe(ProgressLayout.INSTANCE_INSTALL);
        mProgressLayout.observe(ProgressLayout.DATA_MIGRATION);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ContextExecutor.setActivity(this);
        InstanceInstaller.postInstallCheck(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ContextExecutor.clearActivity();
    }

    @Override
    protected void onStart() {
        super.onStart();
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(mFragmentCallbackListener, true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mProgressLayout.cleanUpObservers();
        ProgressKeeper.removeTaskCountListener(mProgressLayout);
        ProgressKeeper.removeTaskCountListener(mProgressServiceKeeper);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.BACK_PREFERENCE, mBackPreferenceListener);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.SELECT_AUTH_METHOD, mSelectAuthMethod);
        ExtraCore.removeExtraListenerFromValue(ExtraConstants.LAUNCH_GAME, mLaunchGameListener);

        getSupportFragmentManager().unregisterFragmentLifecycleCallbacks(mFragmentCallbackListener);
    }

    /** Custom implementation to feel more natural when a backstack isn't present */
    @Override
    public void onBackPressed() {
        MicrosoftLoginFragment fragment = (MicrosoftLoginFragment) getVisibleFragment(MicrosoftLoginFragment.TAG);
        if(fragment != null){
            if(fragment.canGoBack()){
                fragment.goBack();
                return;
            }
        }

        super.onBackPressed();
    }

    @SuppressWarnings("SameParameterValue")
    private Fragment getVisibleFragment(String tag){
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        if(fragment != null && fragment.isVisible()) {
            return fragment;
        }
        return null;
    }

    @SuppressWarnings("unused")
    private Fragment getVisibleFragment(int id){
        Fragment fragment = getSupportFragmentManager().findFragmentById(id);
        if(fragment != null && fragment.isVisible()) {
            return fragment;
        }
        return null;
    }

    public void askForPermission(int minApi, final String permission) {
        if(Build.VERSION.SDK_INT < minApi) return;
        mRequestPermissionLauncher.launch(permission);
    }
    public boolean checkForPermission(int minApi, final String permission) {
        return Build.VERSION.SDK_INT < minApi ||
                ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_DENIED;
    }
    public boolean checkForPermissionRationale(int minApi, final String permission) {
        return checkForPermission(minApi, permission) || ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
    }

    private void checkNotificationPermission() {
        if(LauncherPreferences.PREF_SKIP_NOTIFICATION_PERMISSION_CHECK ||
            this.checkForPermission(33, Manifest.permission.POST_NOTIFICATIONS)) {
            return;
        }
        showNotificationPermissionReasoning();
    }

    // Call async
    private void checkPreviousInstalls(){
        final String[] packages = {"net.kdt.pojavlaunch", "net.kdt.pojavlaunch.debug", "net.kdt.pojavlaunch.pub"};
        for(String s : packages){
            Intent i = getPackageManager().getLaunchIntentForPackage(s);
            if(i == null) continue;
            Tools.runOnUiThread(() ->
                    new AlertDialog.Builder(this)
                        .setTitle(R.string.migration_progress_warning_title)
                        .setMessage(R.string.migration_notice)
                        .setPositiveButton(android.R.string.ok, (d, button) -> LauncherPreferences.DEFAULT_PREF.edit().putBoolean("migrationNotice", false).apply())
                        .setOnDismissListener(d -> LauncherPreferences.PREF_MIGRATION_NOTICE = false)
                        .show());
            break;
        }
    }

    private void showNotificationPermissionReasoning() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.notification_permission_dialog_title)
                .setMessage(R.string.notification_permission_dialog_text)
                .setPositiveButton(android.R.string.ok, (d, w) ->
                        askForPermission(33, Manifest.permission.POST_NOTIFICATIONS))
                .setNegativeButton(android.R.string.cancel, (d, w)-> handleNoNotificationPermission())
                .show();
    }

    private void handleNoNotificationPermission() {
        LauncherPreferences.PREF_SKIP_NOTIFICATION_PERMISSION_CHECK = true;
        LauncherPreferences.DEFAULT_PREF.edit()
                .putBoolean(LauncherPreferences.PREF_KEY_SKIP_NOTIFICATION_CHECK, true)
                .apply();
    }

    /** Stuff all the view boilerplate here */
    private void bindViews(){
        mRootLayout = findViewById(R.id.zyro_root);
        mTopBar = findViewById(R.id.zyro_top_bar);
        mTabsScroll = findViewById(R.id.zyro_tabs_scroll);
        mFragmentView = findViewById(R.id.container_fragment);
        mSettingsButton = findViewById(R.id.setting_button);
        mProgressLayout = findViewById(R.id.progress_layout);
        bindNavRail();
        bindContentTabs();
    }

    /** Wires the left navigation rail. All views are nullable: the rail only exists in landscape. */
    private void bindNavRail() {
        int[] navIds = {
                R.id.nav_home, R.id.nav_instances, R.id.nav_versions, R.id.nav_mods,
                R.id.nav_modpacks, R.id.nav_resource_packs, R.id.nav_shaders,
                R.id.nav_downloads, R.id.nav_settings
        };
        mNavItems = new ImageView[navIds.length];
        for (int i = 0; i < navIds.length; i++) {
            mNavItems[i] = findViewById(navIds[i]);
        }

        setNavAction(NAV_HOME, () -> {
            FragmentManager manager = getSupportFragmentManager();
            if (!manager.isStateSaved()) Tools.backToMainMenu(this);
        });
        setNavAction(NAV_INSTANCES, () ->
                navigateTo(InstancesFragment.class, InstancesFragment.TAG, null));
        setNavAction(NAV_VERSIONS, () ->
                navigateTo(VersionsFragment.class, VersionsFragment.TAG, null));
        setNavAction(NAV_MODS, () ->
                navigateTo(SearchModFragment.class, SearchModFragment.TAG,
                        SearchModFragment.newInstance(ProjectType.MOD).getArguments()));
        setNavAction(NAV_MODPACKS, () ->
                navigateTo(SearchModFragment.class, SearchModFragment.TAG,
                        SearchModFragment.newInstance(ProjectType.MODPACK).getArguments()));
        setNavAction(NAV_RESOURCE_PACKS, () ->
                navigateTo(LocalPacksFragment.class, LocalPacksFragment.TAG,
                        LocalPacksFragment.newInstance(LocalPacksFragment.FOLDER_RESOURCE_PACKS,
                                getString(R.string.zyro_resource_packs)).getArguments()));
        setNavAction(NAV_SHADERS, () ->
                navigateTo(LocalPacksFragment.class, LocalPacksFragment.TAG,
                        LocalPacksFragment.newInstance(LocalPacksFragment.FOLDER_SHADER_PACKS,
                                getString(R.string.zyro_shaders)).getArguments()));
        setNavAction(NAV_DOWNLOADS, () ->
                navigateTo(DownloadsFragment.class, DownloadsFragment.TAG, null));
        setNavAction(NAV_SETTINGS, this::openSettings);
    }

    /** Wires the content tabs shown above the browse screens */
    private void bindContentTabs() {
        int[] tabIds = {
                R.id.tab_modpacks, R.id.tab_mods, R.id.tab_resource_packs, R.id.tab_shaders,
                R.id.tab_worlds, R.id.tab_vanilla, R.id.tab_favorites
        };
        mTabItems = new TextView[tabIds.length];
        for (int i = 0; i < tabIds.length; i++) mTabItems[i] = findViewById(tabIds[i]);

        setTabAction(TAB_MODPACKS, () ->
                navigateTo(SearchModFragment.class, SearchModFragment.TAG,
                        SearchModFragment.newInstance(ProjectType.MODPACK).getArguments()));
        setTabAction(TAB_MODS, () ->
                navigateTo(SearchModFragment.class, SearchModFragment.TAG,
                        SearchModFragment.newInstance(ProjectType.MOD).getArguments()));
        setTabAction(TAB_RESOURCE_PACKS, () ->
                navigateTo(SearchModFragment.class, SearchModFragment.TAG,
                        SearchModFragment.newInstance(ProjectType.RESOURCE_PACK).getArguments()));
        setTabAction(TAB_SHADERS, () ->
                navigateTo(SearchModFragment.class, SearchModFragment.TAG,
                        SearchModFragment.newInstance(ProjectType.SHADER).getArguments()));
        setTabAction(TAB_WORLDS, () ->
                navigateTo(WorldsFragment.class, WorldsFragment.TAG, null));
        setTabAction(TAB_VANILLA, () ->
                navigateTo(VersionsFragment.class, VersionsFragment.TAG, null));
        setTabAction(TAB_FAVORITES, () ->
                navigateTo(SearchModFragment.class, SearchModFragment.TAG,
                        SearchModFragment.newFavorites().getArguments()));
    }

    private void setTabAction(int index, Runnable action) {
        if (mTabItems == null || mTabItems[index] == null) return;
        mTabItems[index].setOnClickListener(v -> action.run());
    }

    private void setNavAction(int index, Runnable action) {
        if (mNavItems == null || mNavItems[index] == null) return;
        mNavItems[index].setOnClickListener(v -> action.run());
    }

    /** Replaces the visible fragment, unless this screen is already showing */
    private void navigateTo(Class<? extends Fragment> fragmentClass, String tag, Bundle args) {
        FragmentManager manager = getSupportFragmentManager();
        if (manager.isStateSaved()) return;
        Fragment current = manager.findFragmentById(R.id.container_fragment);
        if (current != null && current.getClass() == fragmentClass
                && sameArguments(current.getArguments(), args)) {
            return;
        }
        Tools.swapFragment(this, fragmentClass, tag, args);
    }

    private static boolean sameArguments(Bundle first, Bundle second) {
        if (first == null || second == null) return first == second;
        if (!first.keySet().equals(second.keySet())) return false;
        for (String key : first.keySet()) {
            Object left = first.get(key);
            Object right = second.get(key);
            if (left == null ? right != null : !left.equals(right)) return false;
        }
        return true;
    }

    private void openSettings() {
        FragmentManager manager = getSupportFragmentManager();
        if (manager.isStateSaved()) return;
        Fragment current = manager.findFragmentById(R.id.container_fragment);
        if (current instanceof LauncherPreferenceFragment) return;
        Tools.swapFragment(this, LauncherPreferenceFragment.class, SETTING_FRAGMENT_TAG, null);
    }

    /** Highlights the rail entry and the content tab matching the visible fragment */
    private void updateNavSelection(@NonNull Fragment fragment) {
        int rail = NAV_NONE;
        int tab = TAB_NONE;
        if (fragment instanceof MainMenuFragment) {
            rail = NAV_HOME;
        } else if (fragment instanceof InstancesFragment) {
            rail = NAV_INSTANCES;
        } else if (fragment instanceof VersionsFragment) {
            rail = NAV_VERSIONS;
            tab = TAB_VANILLA;
        } else if (fragment instanceof SearchModFragment) {
            if (SearchModFragment.isFavorites(fragment)) {
                tab = TAB_FAVORITES;
            } else {
                int projectType = SearchModFragment.projectTypeOf(fragment);
                switch (projectType) {
                    case ProjectType.MOD:
                        rail = NAV_MODS;
                        tab = TAB_MODS;
                        break;
                    case ProjectType.RESOURCE_PACK:
                        rail = NAV_RESOURCE_PACKS;
                        tab = TAB_RESOURCE_PACKS;
                        break;
                    case ProjectType.SHADER:
                        rail = NAV_SHADERS;
                        tab = TAB_SHADERS;
                        break;
                    case ProjectType.MODPACK:
                    default:
                        rail = NAV_MODPACKS;
                        tab = TAB_MODPACKS;
                        break;
                }
            }
        } else if (fragment instanceof LocalPacksFragment) {
            Bundle args = fragment.getArguments();
            String folder = args == null ? null : args.getString(LocalPacksFragment.ARG_FOLDER);
            boolean shaders = LocalPacksFragment.FOLDER_SHADER_PACKS.equals(folder);
            rail = shaders ? NAV_SHADERS : NAV_RESOURCE_PACKS;
            tab = shaders ? TAB_SHADERS : TAB_RESOURCE_PACKS;
        } else if (fragment instanceof WorldsFragment) {
            tab = TAB_WORLDS;
        } else if (fragment instanceof DownloadsFragment) {
            rail = NAV_DOWNLOADS;
        } else if (fragment instanceof LauncherPreferenceFragment) {
            rail = NAV_SETTINGS;
        }
        applyNavSelection(rail);
        applyTabSelection(tab);
        setBrowseHeaderVisible(showsBrowseHeader(fragment), true);
    }

    /** Screens that share the browse header: search, local libraries, worlds and versions */
    private boolean showsBrowseHeader(@NonNull Fragment fragment) {
        return fragment instanceof SearchModFragment
                || fragment instanceof LocalPacksFragment
                || fragment instanceof WorldsFragment
                || fragment instanceof VersionsFragment;
    }

    /**
     * Shows or hides the browse header. The content sits under it, so collapsing
     * the header to zero height gives every other screen the full view; the height
     * and the alpha animate together, which reads as a clean fade out.
     */
    private void setBrowseHeaderVisible(boolean visible, boolean animate) {
        if (mTopBar == null || mTabsScroll == null) return;
        if (mBrowseHeaderVisible == visible) return;
        mBrowseHeaderVisible = visible;

        if (!animate) {
            ViewGroup.LayoutParams params = mTopBar.getLayoutParams();
            params.height = visible ? ViewGroup.LayoutParams.WRAP_CONTENT : 0;
            mTopBar.setLayoutParams(params);
            mTopBar.setVisibility(visible ? View.VISIBLE : View.GONE);
            mTopBar.setAlpha(visible ? 1f : 0f);
            return;
        }

        final int fullHeight = measureHeaderHeight();
        final int startHeight = visible ? 0 : mTopBar.getHeight();
        mTopBar.setVisibility(View.VISIBLE);
        mTopBar.setAlpha(visible ? 0f : 1f);

        ValueAnimator animator = ValueAnimator.ofInt(startHeight, visible ? fullHeight : 0);
        animator.setDuration(HEADER_ANIM_DURATION);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            int height = (int) animation.getAnimatedValue();
            ViewGroup.LayoutParams params = mTopBar.getLayoutParams();
            if (params.height != height) {
                params.height = height;
                mTopBar.setLayoutParams(params);
            }
            float fraction = fullHeight <= 0 ? 0f : (float) height / fullHeight;
            mTopBar.setAlpha(visible ? fraction : 1f - fraction);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                ViewGroup.LayoutParams params = mTopBar.getLayoutParams();
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                mTopBar.setLayoutParams(params);
                if (visible) {
                    mTopBar.setAlpha(1f);
                    if (mSelectedTab != TAB_NONE) scrollTabIntoView(mTabItems[mSelectedTab]);
                } else {
                    mTopBar.setAlpha(0f);
                    mTopBar.setVisibility(View.GONE);
                }
            }
        });
        animator.start();
    }

    /** Height the header needs when every tab fits on one row */
    private int measureHeaderHeight() {
        if (mTopBar.getHeight() > 0) return mTopBar.getHeight();
        int width = mRootLayout == null ? 0 : mRootLayout.getWidth()
                - (int) getResources().getDimension(R.dimen.zyro_nav_width);
        if (width <= 0) width = getResources().getDisplayMetrics().widthPixels;
        int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        mTopBar.measure(widthSpec, heightSpec);
        return Math.max(mTopBar.getMeasuredHeight(), 0);
    }

    private void applyNavSelection(int selected) {
        if (mNavItems == null) return;
        int activeColor = ContextCompat.getColor(this, R.color.zyro_neon);
        int idleColor = ContextCompat.getColor(this, R.color.zyro_text_secondary);
        for (int i = 0; i < mNavItems.length; i++) {
            ImageView item = mNavItems[i];
            if (item == null) continue;
            boolean isSelected = i == selected;
            int color = isSelected ? activeColor : idleColor;
            item.setImageTintList(ColorStateList.valueOf(color));
            item.setBackgroundResource(isSelected
                    ? R.drawable.zyro_nav_icon_selected
                    : R.drawable.zyro_nav_icon_bg);
        }
    }

    private void applyTabSelection(int selected) {
        if (mTabItems == null) return;
        mSelectedTab = selected;
        int activeColor = ContextCompat.getColor(this, R.color.zyro_neon);
        int idleColor = ContextCompat.getColor(this, R.color.zyro_text_secondary);
        for (int i = 0; i < mTabItems.length; i++) {
            TextView tab = mTabItems[i];
            if (tab == null) continue;
            boolean isSelected = i == selected;
            int color = isSelected ? activeColor : idleColor;
            tab.setTextColor(color);
            tab.setBackgroundResource(isSelected
                    ? R.drawable.zyro_tab_selected
                    : R.drawable.zyro_tab_bg);
            tab.setCompoundDrawableTintList(ColorStateList.valueOf(color));
        }
        if (selected != TAB_NONE) scrollTabIntoView(mTabItems[selected]);
    }

    /** Keeps the selected tab inside the visible part of the scrolling tab bar */
    private void scrollTabIntoView(TextView tab) {
        if (tab == null || mTabsScroll == null) return;
        mTabsScroll.post(() -> {
            int target = tab.getLeft() - (mTabsScroll.getWidth() - tab.getWidth()) / 2;
            int max = Math.max(0, mTabsScroll.getChildAt(0).getWidth() - mTabsScroll.getWidth());
            mTabsScroll.smoothScrollTo(Math.max(0, Math.min(target, max)), 0);
        });
    }
}
