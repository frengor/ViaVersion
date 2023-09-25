/*
 * This file is part of ViaVersion - https://github.com/ViaVersion/ViaVersion
 * Copyright (C) 2023-2023 ViaVersion and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.viaversion.viaversion.data.shared;

import com.google.common.base.Preconditions;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.data.MappingData;
import com.viaversion.viaversion.api.data.shared.DataFillers;
import com.viaversion.viaversion.api.protocol.Protocol;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DataFillersImpl implements DataFillers {

    private final Map<Class<?>, Initializer> initializers = new HashMap<>();
    private final Set<Class<?>> intents = new HashSet<>();
    private boolean cleared;

    @Override
    public void register(final Class<?> type, final Protocol<?, ?, ?, ?> protocol, final Runnable initializer) {
        Preconditions.checkArgument(!cleared, "Cannot register initializer after the mapping data loader has shut down. "
                + "Consider setting ProtocolLoadingIntention to ALL in ProtocolManager instead");
        initializers.put(type, new Initializer(protocol, initializer));
    }

    @Override
    public void registerIntent(final Class<?> clazz) {
        Preconditions.checkArgument(!cleared, "Cannot register intention after the mapping data loader has shut down. "
                + "Consider setting ProtocolLoadingIntention to ALL in ProtocolManager instead");
        // Initializer might not have been added yet, so we can't null check
        intents.add(clazz);
    }

    @Override
    public void initialize(final Class<?> clazz) {
        final Initializer initializer = initializers.get(clazz);
        Preconditions.checkNotNull(initializer, "Initializer for " + clazz + " not found");
        initializer.loader.run();
    }

    @Override
    public void initializeRequired() {
        final List<String> loadedData = new ArrayList<>();
        final List<MappingData> loadedMappingData = new ArrayList<>();
        for (final Class<?> intent : intents) {
            final Initializer initializer = initializers.get(intent);
            if (initializer == null) {
                throw new IllegalStateException("Initializer for " + intent.getSimpleName() + " not found");
            }

            if (Via.getManager().getProtocolManager().getProtocol(initializer.protocol.getClass()) != null) {
                // Registered, so its data will be already be loaded
                continue;
            }

            final MappingData mappingData = initializer.protocol.getMappingData();
            if (!mappingData.isLoaded()) {
                mappingData.load();
                loadedMappingData.add(mappingData);
            }

            loadedData.add(intent.getSimpleName());
            initializer.loader.run();
        }

        if (!loadedData.isEmpty()) {
            Via.getPlatform().getLogger().info("Loaded additional data classes: " + String.join(", ", loadedData));

            // Unload data of unregistered protocols again
            for (final MappingData data : loadedMappingData) {
                data.unload();
            }
        }
    }

    @Override
    public void clear() {
        initializers.clear();
        intents.clear();
        cleared = true;
    }

    private static final class Initializer {
        private final Protocol<?, ?, ?, ?> protocol;
        private final Runnable loader;

        private Initializer(final Protocol<?, ?, ?, ?> protocol, final Runnable loader) {
            this.protocol = protocol;
            this.loader = loader;
        }
    }
}
