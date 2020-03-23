/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.vfs;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.rubygrapefruit.platform.Native;
import net.rubygrapefruit.platform.internal.jni.OsxFileEventFunctions;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.vfs.watch.FileWatcherRegistry;
import org.gradle.internal.vfs.watch.FileWatcherRegistryFactory;
import org.gradle.internal.vfs.watch.WatchRootUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class DarwinFileWatcherRegistry extends AbstractEventDrivenFileWatcherRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(DarwinFileWatcherRegistry.class);

    private final Set<Path> watchedRoots = new HashSet<>();

    public DarwinFileWatcherRegistry(Predicate<String> watchFilter, Collection<File> mustWatchDirectories, ChangeHandler handler) {
        super(
            callback -> Native.get(OsxFileEventFunctions.class)
                .startWatcher(
                    // TODO Figure out a good value for this
                    20, TimeUnit.MICROSECONDS,
                    callback
                ),
            watchFilter,
            mustWatchDirectories,
            handler
        );
    }

    @Override
    protected void snapshotAdded(CompleteFileSystemLocationSnapshot snapshot) {
        Set<String> mustWatchDirectoryPrefixes = ImmutableSet.copyOf(
            getMustWatchDirectories().stream()
                .map(path -> path.toString() + File.separator)
                ::iterator
        );
        List<Path> alreadyWatchedDirectories = ImmutableList
            .<Path>builder()
            .addAll(getMustWatchDirectories())
            .addAll(watchedRoots)
            .build();
        Set<String> directories = WatchRootUtil.resolveDirectoriesToWatch(
            snapshot,
            path -> getWatchFilter().test(path) || startsWithAnyPrefix(path, mustWatchDirectoryPrefixes),
            alreadyWatchedDirectories
        );
        Set<Path> newWatchRoots = WatchRootUtil.resolveRootsToWatch(directories);
        LOGGER.info("Watching {} directory hierarchies to track changes between builds in {} directories", newWatchRoots.size(), directories.size());
        Set<Path> watchRootsToRemove = new HashSet<>(watchedRoots);
        watchRootsToRemove.removeAll(newWatchRoots);
        newWatchRoots.removeAll(watchedRoots);
        newWatchRoots.stream()
            .map(Path::toFile)
            .forEach(getWatcher()::startWatching);
        watchRootsToRemove.stream()
            .map(Path::toFile)
            .forEach(getWatcher()::stopWatching);
        watchedRoots.addAll(newWatchRoots);
        watchedRoots.removeAll(watchRootsToRemove);
    }

    private static boolean startsWithAnyPrefix(String path, Set<String> prefixes) {
        for (String prefix : prefixes) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void snapshotRemoved(CompleteFileSystemLocationSnapshot snapshot) {
        // TODO
    }

    public static class Factory implements FileWatcherRegistryFactory {

        @Override
        public FileWatcherRegistry startWatcher(Predicate<String> watchFilter, Collection<File> mustWatchDirectories, ChangeHandler handler) {
            return new DarwinFileWatcherRegistry(watchFilter, mustWatchDirectories, handler);
        }

    }
}
