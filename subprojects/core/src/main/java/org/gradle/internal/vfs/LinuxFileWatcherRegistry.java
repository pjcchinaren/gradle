/*
 * Copyright 2020 the original author or authors.
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

import net.rubygrapefruit.platform.Native;
import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.internal.jni.LinuxFileEventFunctions;
import org.gradle.internal.snapshot.CompleteDirectorySnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.gradle.internal.vfs.watch.FileWatcherRegistry;
import org.gradle.internal.vfs.watch.FileWatcherRegistryFactory;
import org.gradle.internal.vfs.watch.WatchRootUtil;
import org.gradle.internal.vfs.watch.WatchingNotSupportedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class LinuxFileWatcherRegistry extends AbstractEventDrivenFileWatcherRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(LinuxFileWatcherRegistry.class);
    private final Map<String, CompleteFileSystemLocationSnapshot> watchedSnapshots = new HashMap<>();
    private final Set<String> watchedRoots = new HashSet<>();
    private final Set<String> mustWatchDirectories = new HashSet<>();

    public LinuxFileWatcherRegistry(Predicate<String> watchFilter, ChangeHandler handler) {
        super(
            callback -> Native.get(LinuxFileEventFunctions.class).startWatcher(callback),
            watchFilter,
            handler
        );
    }

    @Override
    protected void snapshotAdded(CompleteFileSystemLocationSnapshot snapshot) {
        watchedSnapshots.put(snapshot.getAbsolutePath(), snapshot);
        updateWatchedDirectories();
    }

    @Override
    protected void snapshotRemoved(CompleteFileSystemLocationSnapshot snapshot) {
        watchedSnapshots.remove(snapshot.getAbsolutePath());
        updateWatchedDirectories();
    }

    @Override
    public void updateMustWatchDirectories(Collection<File> updatedWatchDirectories) {
        mustWatchDirectories.clear();
        updatedWatchDirectories.stream().map(File::getAbsolutePath).forEach(mustWatchDirectories::add);
        updateWatchedDirectories();

    }

    private void updateWatchedDirectories() {
        Set<String> directoriesToWatch = new HashSet<>();
        watchedSnapshots.values().stream()
            .filter(snapshot -> getWatchFilter().test(snapshot.getAbsolutePath()))
            .forEach(snapshot -> {
                WatchRootUtil.getDirectoriesToWatch(snapshot).map(Path::toString).forEach(directoriesToWatch::add);
                snapshot.accept(new FileSystemSnapshotVisitor() {
                    boolean root = true;

                    @Override
                    public boolean preVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
                        if (!root) {
                            directoriesToWatch.add(directorySnapshot.getAbsolutePath());
                        }
                        root = false;
                        return true;
                    }

                    @Override
                    public void visitFile(CompleteFileSystemLocationSnapshot fileSnapshot) {
                    }

                    @Override
                    public void postVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
                    }
                });
            });
        directoriesToWatch.addAll(mustWatchDirectories);

        updateWatchedDirectories(directoriesToWatch);
    }

    private void updateWatchedDirectories(Set<String> newWatchRoots) {
        Set<String> watchRootsToRemove = new HashSet<>(watchedRoots);
        if (newWatchRoots.isEmpty()) {
            LOGGER.warn("Not watching anything anymore");
        }
        watchRootsToRemove.removeAll(newWatchRoots);
        newWatchRoots.removeAll(watchedRoots);
        if (newWatchRoots.isEmpty() && watchRootsToRemove.isEmpty()) {
            return;
        }
        LOGGER.warn("Watching {} directory hierarchies to track changes", newWatchRoots.size());
        try {
            getWatcher().startWatching(newWatchRoots.stream()
                .map(File::new)
                .collect(Collectors.toList()));
            getWatcher().stopWatching(watchRootsToRemove.stream()
                .map(File::new)
                .collect(Collectors.toList()));
        } catch (NativeException e) {
            if (e.getMessage().contains("Already watching path: ")) {
                throw new WatchingNotSupportedException("Unable to watch same file twice via different paths: " + e.getMessage(), e);
            }
            throw e;
        }
        watchedRoots.addAll(newWatchRoots);
        watchedRoots.removeAll(watchRootsToRemove);
    }

    public static class Factory implements FileWatcherRegistryFactory {

        @Override
        public FileWatcherRegistry startWatcher(Predicate<String> watchFilter, ChangeHandler handler) {
            return new LinuxFileWatcherRegistry(watchFilter, handler);
        }
    }
}
