package net.kdt.pojavlaunch.modloaders.modpacks;

import android.content.Context;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.File;

/**
 * Downloads a single mod file into the currently selected instance's mods folder.
 */
public final class ModInstallTask {
    public static final String PROGRESS_KEY = ProgressLayout.INSTALL_MODPACK;

    private ModInstallTask() {}

    public static void install(Context context, ModDetail detail, int selectedVersion) {
        Context appContext = context.getApplicationContext();
        ProgressLayout.setProgress(PROGRESS_KEY, 0, R.string.zyro_status_downloading);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                Instance instance = Instances.loadSelectedInstance();
                if (instance == null) throw new IllegalStateException(
                        appContext.getString(R.string.zyro_no_instance_for_mod));
                File modsFolder = new File(instance.getGameDirectory(), "mods");
                FileUtils.ensureDirectory(modsFolder);

                String url = detail.versionUrls[selectedVersion];
                String fileName = url.substring(url.lastIndexOf('/') + 1);
                int query = fileName.indexOf('?');
                if (query > 0) fileName = fileName.substring(0, query);
                if (fileName.isEmpty()) fileName = detail.id + ".jar";
                File outFile = new File(modsFolder, fileName);

                DownloadUtils.downloadFileMonitored(url, outFile, null, (curr, max) -> {
                    int percent = max > 0 ? (int) ((curr * 100L) / max) : 0;
                    ProgressKeeper.submitProgress(PROGRESS_KEY, percent, R.string.zyro_status_downloading);
                });
                ProgressKeeper.submitProgress(PROGRESS_KEY, -1, -1);
                Tools.runOnUiThread(() -> android.widget.Toast.makeText(appContext,
                        appContext.getString(R.string.zyro_mod_installed,
                                instance.name != null ? instance.name : "instance"),
                        android.widget.Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                ProgressKeeper.submitProgress(PROGRESS_KEY, -1, -1);
                Tools.showErrorRemote(appContext, R.string.zyro_mod_install_failed, e);
            }
        });
    }
}
