/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.cleanup;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.file.delete.Deleter;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.FileUtils;

import java.io.File;
import java.util.Collection;

public class DefaultBuildOutputDeleter implements BuildOutputDeleter {
    private final Logger logger = Logging.getLogger(DefaultBuildOutputDeleter.class);

    private final DocumentationRegistry documentationRegistry;
    private final Deleter deleter;

    public DefaultBuildOutputDeleter(DocumentationRegistry documentationRegistry, Deleter deleter) {
        this.documentationRegistry = documentationRegistry;
        this.deleter = deleter;
    }

    @Override
    public void delete(final Iterable<File> outputs) {
        Collection<? extends File> roots = Collections2.filter(FileUtils.calculateRoots(outputs), new Predicate<File>() {
            @Override
            public boolean apply(File file) {
                return file.exists();
            }
        });

        if (!roots.isEmpty()) {
            logger.warn("Gradle is removing stale outputs from a previous version of Gradle, for more information about stale outputs see {}.", documentationRegistry.getDocumentationFor("more_about_tasks", "sec:stale_task_outputs"));
            for (File output : roots) {
                deleteOutput(output);
            }
        }
    }

    private void deleteOutput(final File output) {
        try {
            if (output.isDirectory()) {
                deleter.delete(output);
                logger.quiet("Deleting directory '{}'", output);
            } else if (output.isFile()) {
                deleter.delete(output);
                logger.quiet("Deleting file '{}'", output);
            }
        } catch (UncheckedIOException e) {
            logger.warn("Unable to delete '{}'", output);
        }
    }
}
