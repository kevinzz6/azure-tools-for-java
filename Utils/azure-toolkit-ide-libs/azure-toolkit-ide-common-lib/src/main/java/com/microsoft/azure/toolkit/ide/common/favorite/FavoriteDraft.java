/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.ide.common.favorite;

import com.microsoft.azure.toolkit.lib.common.exception.AzureToolkitRuntimeException;
import com.microsoft.azure.toolkit.lib.common.model.AbstractAzResource;
import com.microsoft.azure.toolkit.lib.common.model.AzResource;
import com.microsoft.azure.toolkit.lib.common.operation.AzureOperation;
import lombok.Getter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;

public class FavoriteDraft extends Favorite implements AzResource.Draft<Favorite, AbstractAzResource<?, ?, ?>> {
    @Getter
    @Nullable
    private final Favorite origin;

    FavoriteDraft(@Nonnull String resourceId, @Nonnull Favorites module) {
        super(resourceId, module);
        this.origin = null;
    }

    FavoriteDraft(@Nonnull Favorite origin) {
        super(origin);
        this.origin = origin;
    }

    @Override
    public void reset() {
    }

    @Nonnull
    @Override
    @AzureOperation(
        name = "resource.create_resource.resource|type",
        params = {"this.getName()", "this.getResourceTypeName()"},
        type = AzureOperation.Type.SERVICE
    )
    public AbstractAzResource<?, ?, ?> createResourceInAzure() {
        Favorites.getInstance().favorites.add(0, this.getName());
        return Objects.requireNonNull(Favorites.getInstance().loadResourceFromAzure(this.getName(), null));
    }

    @Nonnull
    @Override
    public AbstractAzResource<?, ?, ?> updateResourceInAzure(@Nonnull AbstractAzResource<?, ?, ?> origin) {
        throw new AzureToolkitRuntimeException("not supported");
    }

    @Override
    public boolean isModified() {
        return false;
    }
}