/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.intellij.containerservice.actions;

import com.intellij.ide.actions.RevealFileAction;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.microsoft.azure.toolkit.intellij.common.FileChooser;
import com.microsoft.azure.toolkit.lib.common.action.Action;
import com.microsoft.azure.toolkit.lib.common.action.ActionView;
import com.microsoft.azure.toolkit.lib.common.bundle.AzureString;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.task.AzureTask;
import com.microsoft.azure.toolkit.lib.common.task.AzureTaskManager;
import com.microsoft.azure.toolkit.lib.containerservice.KubernetesCluster;
import org.apache.commons.io.FileUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

public class DownloadKubuConfigAction {
    public static void downloadKubuConfig(@Nonnull KubernetesCluster cluster, @Nonnull Project project, boolean isAdmin) {
        final File destFile = AzureTaskManager.getInstance().runLaterAsObservable(new AzureTask<>(() ->
                        FileChooser.showFileSaver("Download kubernetes configuration", String.format("%s-%s.yml", cluster.getName(), isAdmin ? "admin" : "user"))))
                .toBlocking().first();
        if (destFile == null) {
            return;
        }
        try {
            final byte[] content = isAdmin ? cluster.getAdminKubeConfig() : cluster.getUserKubeConfig();
            FileUtils.writeByteArrayToFile(destFile, content);
            AzureMessager.getMessager().info(AzureString.format("Save kubernetes configuration file for %s to %s successfully.", cluster.getName(), destFile.getAbsolutePath()),
                    null, getOpenInExplorerAction(project, destFile), getOpenInEditorAction(project, destFile), getOpenKubernetesAction(project, destFile));
        } catch (final IOException e) {
            AzureMessager.getMessager().error(e);
        }
    }

    @Nullable
    private static Action<?> getOpenKubernetesAction(@Nonnull Project project, @Nonnull File file) {
        if (!PluginManagerCore.isPluginInstalled(PluginId.getId(KubernetesUtils.KUBERNETES_PLUGIN_ID))) {
            return null;
        }
        final Consumer<Void> consumer = ignore -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(file.getAbsolutePath()), null);
            AzureTaskManager.getInstance().runLater(() -> ShowSettingsUtil.getInstance().showSettingsDialog(project, "Kubernetes"));
        };
        final ActionView.Builder view = new ActionView.Builder("Set kubeconfig for project")
                .title(ignore -> AzureString.fromString("Set kubeconfig")).enabled(ignore -> true);
        final Action.Id<Void> id = Action.Id.of("kubernetes.set_kube_config");
        return new Action<>(id, consumer, view);
    }

    // todo: remove duplicated with AppServiceFileAction
    private static Action<?> getOpenInExplorerAction(@Nonnull Project project, @Nonnull File file) {
        final ActionView.Builder view = new ActionView.Builder("Open in Explorer").enabled(ignore -> true);
        final Action.Id<Void> id = Action.Id.of("common.open_in_explorer");
        return new Action<>(id, ignore -> RevealFileAction.openFile(file), view);
    }

    private static Action<?> getOpenInEditorAction(@Nonnull Project project, @Nonnull File file) {
        final Consumer<Void> consumer = ignore -> {
            final FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
            final VirtualFile virtualFile = VfsUtil.findFileByIoFile(file, true);
            if (Objects.nonNull(virtualFile)) {
                AzureTaskManager.getInstance().runLater(() -> fileEditorManager.openFile(virtualFile, true, true));
            }
        };
        final ActionView.Builder view = new ActionView.Builder("Open in Editor").enabled(ignore -> true);
        final Action.Id<Void> id = Action.Id.of("common.open_in_editor");
        return new Action<>(id, consumer, view);
    }
}
