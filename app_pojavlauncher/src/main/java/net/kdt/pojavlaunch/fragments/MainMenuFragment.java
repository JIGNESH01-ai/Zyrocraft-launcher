package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.R;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.accounts.Account;
import net.kdt.pojavlaunch.authenticator.accounts.Accounts;
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.InstanceIconProvider;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ProjectType;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.VersionDescriptor;
import net.kdt.pojavlaunch.utils.jre.GameRunner;

import java.io.File;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;

    private final ActivityResultLauncher<Object> mModInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data)->{
                if(data != null) Tools.launchModInstaller(requireContext(), data);
            });

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button mNewsButton = view.findViewById(R.id.news_button);
        Button mDiscordButton = view.findViewById(R.id.social_media_button);
        Button mCustomControlButton = view.findViewById(R.id.custom_control_button);
        Button mInstallJarButton = view.findViewById(R.id.install_jar_button);
        Button mShareLogsButton = view.findViewById(R.id.share_logs_button);
        Button mOpenDirectoryButton = view.findViewById(R.id.open_files_button);

        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        Button mPlayButton = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);

        mNewsButton.setOnClickListener(v -> Tools.openURL(requireActivity(), Tools.URL_HOME));
        mDiscordButton.setOnClickListener(v -> Tools.openURL(requireActivity(), getString(R.string.social_media_invite)));
        mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        mInstallJarButton.setOnClickListener(v -> runInstallerWithConfirmation());
        mEditProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));

        view.findViewById(R.id.change_instance_button).setOnClickListener(v -> mVersionSpinner.performClick());

        mPlayButton.setOnClickListener(v -> {
        Instance instance = Instances.loadSelectedInstance();
        File gamedir = instance.getGameDirectory();

        if (GameRunner.hasVkMod(gamedir)) {
            new AlertDialog.Builder(requireContext())
            .setTitle(R.string.vk_mod_title)
            .setMessage(R.string.vk_mod_message)
            .setPositiveButton(R.string.continue_button, (d, w) -> {
                ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
            })
            .show();
        } else if (GameRunner.hasReplay(gamedir) && GameRunner.hasFfmpeg(requireContext())) {
          new AlertDialog.Builder(requireContext())
            .setTitle(R.string.no_ffmpeg_title)
            .setMessage(R.string.no_ffmpeg_message)
            .setPositiveButton(R.string.install_button, (d, w) -> {
             Tools.openURL(requireActivity(), "https://github.com/MojoLauncher/FFmpegPlugin/releases");
    })
    .setNegativeButton(R.string.continue_button, (d, w) -> {
        ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
    })
    .show();
        } else {
        ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
        }
        });

        mShareLogsButton.setOnClickListener((v) -> shareLog(requireContext()));

        mOpenDirectoryButton.setOnClickListener((v)-> openGameDirectory(v.getContext()));


        mNewsButton.setOnLongClickListener((v)->{
            Tools.swapFragment(requireActivity(), GamepadMapperFragment.class, GamepadMapperFragment.TAG, null);
            return true;
        });

        // Quick access cards: they only exist in the portrait home layout, the landscape
        // layout navigates from the left rail instead.
        bindQuickCard(view, R.id.card_instances, () ->
                Tools.swapFragment(requireActivity(), InstancesFragment.class, InstancesFragment.TAG, null));
        bindQuickCard(view, R.id.card_mods, () ->
                Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG,
                        SearchModFragment.newInstance(ProjectType.MOD).getArguments()));
        bindQuickCard(view, R.id.card_modpacks, () ->
                Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG,
                        SearchModFragment.newInstance(ProjectType.MODPACK).getArguments()));
        bindQuickCard(view, R.id.card_resource_packs, () ->
                Tools.swapFragment(requireActivity(), LocalPacksFragment.class, LocalPacksFragment.TAG,
                        LocalPacksFragment.newInstance(LocalPacksFragment.FOLDER_RESOURCE_PACKS,
                                getString(R.string.rc_resource_packs)).getArguments()));
        bindQuickCard(view, R.id.card_shaders, () ->
                Tools.swapFragment(requireActivity(), LocalPacksFragment.class, LocalPacksFragment.TAG,
                        LocalPacksFragment.newInstance(LocalPacksFragment.FOLDER_SHADER_PACKS,
                                getString(R.string.rc_shaders)).getArguments()));
        bindQuickCard(view, R.id.card_downloads, () ->
                Tools.swapFragment(requireActivity(), DownloadsFragment.class, DownloadsFragment.TAG, null));
    }

    /** Binds a quick access card when the current layout provides it */
    private void bindQuickCard(@NonNull View root, int viewId, Runnable action) {
        View card = root.findViewById(viewId);
        if (card != null) card.setOnClickListener(v -> action.run());
    }

    @Override
    public void onResume() {
        super.onResume();
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
        updateInstanceCard();
    }

    /** Fill the hero player name and the selected instance card */
    private void updateInstanceCard() {
        View view = getView();
        if (view == null) return;
        TextView playerName = view.findViewById(R.id.home_player_name);
        TextView instanceName = view.findViewById(R.id.home_instance_name);
        TextView instanceStatus = view.findViewById(R.id.home_instance_status);
        TextView versionText = view.findViewById(R.id.home_instance_version_text);
        TextView loaderText = view.findViewById(R.id.home_instance_loader_text);
        ImageView avatar = view.findViewById(R.id.home_instance_avatar);

        Account account = Accounts.getCurrent();
        playerName.setText(account != null && account.username != null ? account.username : "Player");

        Instance instance = Instances.loadSelectedInstance();
        if (instance == null) {
            instanceName.setText(R.string.no_instance);
            instanceStatus.setText(R.string.rc_instances_empty);
            versionText.setText("");
            loaderText.setText(R.string.rc_filter_vanilla);
            avatar.setImageResource(R.mipmap.ic_launcher);
            return;
        }
        instanceName.setText(instance.name != null ? instance.name : instance.versionId);
        instanceStatus.setText(R.string.rc_launch_hint);
        versionText.setText(VersionDescriptor.minecraftVersion(instance.versionId));
        loaderText.setText(VersionDescriptor.loaderName(instance.versionId));
        avatar.setImageDrawable(InstanceIconProvider.fetchIcon(getResources(), instance));
    }

    private void openGameDirectory(Context context) {
        Instance instance = Instances.loadSelectedInstance();
        if(instance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        File gameDirectory = instance.getGameDirectory();
        if(FileUtils.ensureDirectorySilently(gameDirectory)) {
            openPath(context, gameDirectory, false);
        }else {
            Toast.makeText(context, R.string.gamedir_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void runInstallerWithConfirmation() {
        if (ProgressKeeper.getTaskCount() == 0) {
            mModInstallerLauncher.launch(null);
        } else Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }
}
