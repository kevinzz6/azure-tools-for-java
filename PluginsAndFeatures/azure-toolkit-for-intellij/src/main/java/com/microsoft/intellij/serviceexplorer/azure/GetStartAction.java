/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.intellij.serviceexplorer.azure;

import com.intellij.openapi.project.Project;
import com.microsoft.azure.toolkit.ide.common.icon.AzureIcon;
import com.microsoft.azure.toolkit.ide.common.icon.AzureIcons;
import com.microsoft.azure.toolkit.ide.guidance.GuidanceViewManager;
import com.microsoft.tooling.msservices.serviceexplorer.NodeAction;
import com.microsoft.tooling.msservices.serviceexplorer.NodeActionEvent;
import com.microsoft.tooling.msservices.serviceexplorer.NodeActionListener;
import com.microsoft.tooling.msservices.serviceexplorer.azure.AzureModule;

import javax.annotation.Nonnull;
import java.util.Objects;

public class GetStartAction extends NodeAction {

    public GetStartAction(@Nonnull AzureModule azureModule) {
        super(azureModule, "Getting Started");
        addListener(new NodeActionListener() {
            @Override
            protected void actionPerformed(NodeActionEvent e) {
                GuidanceViewManager.getInstance().showCoursesView((Project) Objects.requireNonNull(azureModule.getProject()));
            }
        });
    }

    @Override
    public AzureIcon getIconSymbol() {
        return AzureIcons.Common.GET_START;
    }
}
