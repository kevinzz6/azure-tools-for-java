/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.hdinsight.serverexplore.hdinsightnode;

import com.microsoft.azure.hdinsight.common.*;
import com.microsoft.azure.hdinsight.common.logger.ILogger;
import com.microsoft.azure.hdinsight.sdk.cluster.*;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azuretools.azurecommons.helpers.NotNull;
import com.microsoft.azuretools.azurecommons.helpers.StringHelper;
import com.microsoft.azuretools.telemetry.AppInsightsConstants;
import com.microsoft.azuretools.telemetry.TelemetryConstants;
import com.microsoft.azuretools.telemetry.TelemetryProperties;
import com.microsoft.tooling.msservices.components.DefaultLoader;
import com.microsoft.tooling.msservices.serviceexplorer.*;
import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;

import java.util.HashMap;
import java.util.Map;

public class ClusterNode extends RefreshableNode implements TelemetryProperties, ILogger {
    private static final String CLUSTER_MODULE_ID = ClusterNode.class.getName();
    private static final String ICON_PATH = CommonConst.ClusterIConPath;
    public static final String ASE_DEEP_LINK = "storageexplorer:///";
    public static final String STORAGE_EXPLORER_REGISTRY_PATH = "storageexplorer\\shell\\open\\command";
    private static final String MAC_OS_STORAGE_EXPLORER_PATH = "/Contents/MacOS/Microsoft\\ Azure\\ Storage\\ Explorer";

    @NotNull
    private IClusterDetail clusterDetail;

    public ClusterNode(Node parent, @NotNull IClusterDetail clusterDetail) {
        super(CLUSTER_MODULE_ID, clusterDetail.getTitle(), parent, ICON_PATH, true);
        this.clusterDetail = clusterDetail;
        this.loadActions();
    }

    @Override
    protected void loadActions() {
        super.loadActions();

        if (ClusterManagerEx.getInstance().isHdiReaderCluster(clusterDetail)) {
            // We need to refresh the whole HDInsight root node when we successfully linked the cluster
            // So we have to pass "hdinsightRootModule" to the link cluster action
            HDInsightRootModule hdinsightRootModule = (HDInsightRootModule) this.getParent();
            NodeActionListener linkClusterActionListener =
                    HDInsightLoader.getHDInsightHelper().createAddNewHDInsightReaderClusterAction(hdinsightRootModule,
                            (ClusterDetail) clusterDetail);
            addAction("Link This Cluster", linkClusterActionListener);
        }

        if (clusterDetail instanceof ClusterDetail || clusterDetail instanceof HDInsightAdditionalClusterDetail ||
                clusterDetail instanceof EmulatorClusterDetail) {
            addAction("Open Spark History UI", new NodeActionListener() {
                @Override
                protected void actionPerformed(NodeActionEvent e) {
                    String sparkHistoryUrl = clusterDetail.isEmulator() ?
                            ((EmulatorClusterDetail)clusterDetail).getSparkHistoryEndpoint() :
                            ClusterManagerEx.getInstance().getClusterConnectionString(clusterDetail.getName()) + "/sparkhistory";
                    openUrlLink(sparkHistoryUrl);
                }
            });

            addAction("Open Azure Storage Explorer for storage", new NodeActionListener() {
                @Override
                protected void actionPerformed(NodeActionEvent e) {
                    if (StringUtils.isEmpty(getStorageExplorerExecutable())){
                        DefaultLoader.getUIHelper().showError("Azure Storage Explorer not found.", "Open Azure Storage Explorer");
                    } else {
                        openUrlLink(ASE_DEEP_LINK);
                    }
                }
            });

            addAction("Open Cluster Management Portal(Ambari)", new NodeActionListener() {
                @Override
                protected void actionPerformed(NodeActionEvent e) {
                    String ambariUrl = clusterDetail.isEmulator() ?
                            ((EmulatorClusterDetail)clusterDetail).getAmbariEndpoint() :
                            ClusterManagerEx.getInstance().getClusterConnectionString(clusterDetail.getName());
                    openUrlLink(ambariUrl);
                }
            });
        }

        if (clusterDetail instanceof ClusterDetail) {
            addAction("Open Jupyter Notebook", new NodeActionListener() {
                @Override
                protected void actionPerformed(NodeActionEvent e) {
                    final String jupyterUrl = ClusterManagerEx.getInstance().getClusterConnectionString(clusterDetail.getName()) + "/jupyter/tree";
                    openUrlLink(jupyterUrl);
                }
            });

            addAction("Open Azure Management Portal", new NodeActionListener() {
                @Override
                protected void actionPerformed(NodeActionEvent e) {
                    String resourceGroupName = clusterDetail.getResourceGroup();
                    if (resourceGroupName != null) {

                        String webPortHttpLink = String.format(HDIEnvironment.getHDIEnvironment().getPortal() + "#resource/subscriptions/%s/resourcegroups/%s/providers/Microsoft.HDInsight/clusters/%s",
                                clusterDetail.getSubscription().getId(),
                                resourceGroupName,
                                clusterDetail.getName());
                        openUrlLink(webPortHttpLink);
                    } else {
                        DefaultLoader.getUIHelper().showError("Failed to get resource group name.", "HDInsight Explorer");
                    }
                }
            });
        }

        if (clusterDetail instanceof HDInsightAdditionalClusterDetail || clusterDetail instanceof HDInsightLivyLinkClusterDetail) {
            NodeActionListener listener = new NodeActionListener() {
                @Override
                protected void actionPerformed(NodeActionEvent e) {
                    boolean choice = DefaultLoader.getUIHelper().showConfirmation("Do you really want to unlink the HDInsight cluster?",
                            "Unlink HDInsight Cluster", new String[]{"Yes", "No"}, null);
                    if (choice) {
                        ClusterManagerEx.getInstance().removeAdditionalCluster(clusterDetail);
                        ((RefreshableNode) getParent()).load(false);
                    }
                }
            };
            addAction("Unlink", new WrappedTelemetryNodeActionListener(
                    getServiceName(), TelemetryConstants.UNLINK_SPARK_CLUSTER, listener));
        } else if (clusterDetail instanceof EmulatorClusterDetail) {
            NodeActionListener listener = new NodeActionListener() {
                @Override
                protected void actionPerformed(NodeActionEvent e) {
                    boolean choice = DefaultLoader.getUIHelper().showConfirmation("Do you really want to unlink the Emulator cluster?",
                            "Unlink Emulator Cluster", new String[]{"Yes", "No"}, null);
                    if (choice) {
                        ClusterManagerEx.getInstance().removeEmulatorCluster((EmulatorClusterDetail) clusterDetail);
                        ((RefreshableNode) getParent()).load(false);
                    }
                }
            };
            addAction("Unlink", new WrappedTelemetryNodeActionListener(
                    getServiceName(), TelemetryConstants.UNLINK_SPARK_CLUSTER, listener));
        }
    }

    @Override
    protected void refreshItems() {
        if(!clusterDetail.isEmulator()) {
            JobViewManager.registerJovViewNode(clusterDetail.getName(), clusterDetail);
            JobViewNode jobViewNode = new JobViewNode(this, clusterDetail);
            boolean isIntelliJ = HDInsightLoader.getHDInsightHelper().isIntelliJPlugin();
            boolean isLinux = System.getProperty("os.name").toLowerCase().contains("linux");
            if(isIntelliJ || !isLinux) {
                addChildNode(jobViewNode);
            }

            RefreshableNode storageAccountNode = new StorageAccountFolderNode(this, clusterDetail);
            addChildNode(storageAccountNode);
        }
    }

    private void openUrlLink(@NotNull String linkUrl) {
        if (!StringHelper.isNullOrWhiteSpace(clusterDetail.getName())) {
            try {
                DefaultLoader.getIdeHelper().openLinkInBrowser(linkUrl);
            } catch (Exception exception) {
                DefaultLoader.getUIHelper().showError(exception.getMessage(), "HDInsight Explorer");
            }
        }
    }

    private String getStorageExplorerExecutable() {
        final String storageExplorerPath = Azure.az().config().getStorageExplorerPath();
        return StringUtils.isEmpty(storageExplorerPath) ? getStorageExplorerExecutableFromOS() : storageExplorerPath;
    }

    private String getStorageExplorerExecutableFromOS() {
        try {
            if (SystemUtils.IS_OS_WINDOWS) {
                final String storageExplorerPath = Advapi32Util.registryGetStringValue(WinReg.HKEY_CLASSES_ROOT, STORAGE_EXPLORER_REGISTRY_PATH, "");
                if (StringUtils.isEmpty(storageExplorerPath)) {
                    return null;
                }
                // Parse from e.g.: "C:\Program Files (x86)\Microsoft Azure Storage Explorer\StorageExplorer.exe" -- "%1"
                final String[] split = storageExplorerPath.split("\"");
                return split.length > 1 ? split[1] : null;
            } else if (SystemUtils.IS_OS_MAC) {
                return MAC_OS_STORAGE_EXPLORER_PATH;
            } else {
                return null;
            }
        } catch (RuntimeException runtimeException) {
            return null;
        }
    }

    @Override
    public Map<String, String> toProperties() {
        final Map<String, String> properties = new HashMap<>();
        properties.put(AppInsightsConstants.SubscriptionId, this.clusterDetail.getSubscription().getId());
        properties.put(AppInsightsConstants.Region, this.clusterDetail.getLocation());
        return properties;
    }

    @Override
    @NotNull
    public String getServiceName() {
        return TelemetryConstants.HDINSIGHT;
    }
}
