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
import net.rubygrapefruit.platform.internal.jni.LinuxFileEventFunctions;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.vfs.watch.FileWatcherRegistry;
import org.gradle.internal.vfs.watch.FileWatcherRegistryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;
import java.util.function.Predicate;

public class LinuxFileWatcherRegistry extends AbstractEventDrivenFileWatcherRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(LinuxFileWatcherRegistry.class);

    public LinuxFileWatcherRegistry(Predicate<String> watchFilter, ChangeHandler handler) {
        super(
            callback -> Native.get(LinuxFileEventFunctions.class).startWatcher(callback),
            watchFilter,
            handler
        );
    }

    @Override
    protected void snapshotAdded(CompleteFileSystemLocationSnapshot snapshot) {
        // TODO
    }

    @Override
    protected void snapshotRemoved(CompleteFileSystemLocationSnapshot snapshot) {
        // TODO
    }

    @Override
    public void updateMustWatchDirectories(Collection<File> updatedWatchDirectories) {
        // TODO
    }

    public static class Factory implements FileWatcherRegistryFactory {

        @Override
        public FileWatcherRegistry startWatcher(Predicate<String> watchFilter, ChangeHandler handler) {
            return new LinuxFileWatcherRegistry(watchFilter, handler);
        }
    }
}
