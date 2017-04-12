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

package org.gradle.api.internal.changedetection.state;

import com.google.common.base.Objects;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.resources.AbsolutePath;
import org.gradle.api.internal.changedetection.resources.DefaultRelativePath;
import org.gradle.api.internal.changedetection.resources.IgnoredPath;
import org.gradle.api.internal.changedetection.resources.IndexedRelativePath;
import org.gradle.api.internal.changedetection.resources.NormalizedPath;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.HashCodeSerializer;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class SnapshotMapSerializer extends AbstractSerializer<Map<String, NormalizedFileSnapshot>> {
    private static final byte DIR_SNAPSHOT = 1;
    private static final byte MISSING_FILE_SNAPSHOT = 2;
    private static final byte REGULAR_FILE_SNAPSHOT = 3;

    private static final byte NO_NORMALIZATION = 1;
    private static final byte DEFAULT_NORMALIZATION = 2;
    private static final byte INDEXED_NORMALIZATION = 3;
    private static final byte IGNORED_PATH_NORMALIZATION = 4;

    private final HashCodeSerializer hashCodeSerializer = new HashCodeSerializer();
    private final StringInterner stringInterner;

    public SnapshotMapSerializer(StringInterner stringInterner) {
        this.stringInterner = stringInterner;
    }

    @Override
    public Map<String, NormalizedFileSnapshot> read(Decoder decoder) throws Exception {
        int snapshotsCount = decoder.readSmallInt();
        Map<String, NormalizedFileSnapshot> snapshots = new LinkedHashMap<String, NormalizedFileSnapshot>(snapshotsCount);
        for (int i = 0; i < snapshotsCount; i++) {
            String absolutePath = stringInterner.intern(decoder.readString());
            NormalizedFileSnapshot snapshot = readSnapshot(absolutePath, decoder, stringInterner);
            snapshots.put(absolutePath, snapshot);
        }
        return snapshots;
    }

    private NormalizedFileSnapshot readSnapshot(String absolutePath, Decoder decoder, StringInterner stringInterner) throws IOException {
        byte fileSnapshotKind = decoder.readByte();
        FileContentSnapshot snapshot;
        switch (fileSnapshotKind) {
            case DIR_SNAPSHOT:
                snapshot = DirContentSnapshot.getInstance();
                break;
            case MISSING_FILE_SNAPSHOT:
                snapshot = MissingFileContentSnapshot.getInstance();
                break;
            case REGULAR_FILE_SNAPSHOT:
                snapshot = new FileHashSnapshot(hashCodeSerializer.read(decoder));
                break;
            default:
                throw new RuntimeException("Unable to read serialized file snapshot. Unrecognized value found in the data stream.");
        }

        int normalizedSnapshotKind = decoder.readByte();
        NormalizedPath normalizedPath;
        switch (normalizedSnapshotKind) {
            case NO_NORMALIZATION:
                normalizedPath = new AbsolutePath(absolutePath);
                break;
            case DEFAULT_NORMALIZATION:
                String normalizedPathString = stringInterner.intern(decoder.readString());
                normalizedPath = new DefaultRelativePath(normalizedPathString);
                break;
            case INDEXED_NORMALIZATION:
                int index = decoder.readSmallInt();
                normalizedPath = new IndexedRelativePath(absolutePath, index);
                break;
            case IGNORED_PATH_NORMALIZATION:
                normalizedPath = IgnoredPath.getInstance();
                break;
            default:
                throw new RuntimeException("Unable to read serialized file snapshot. Unrecognized value found in the data stream.");
        }
        return new DefaultNormalizedFileSnapshot(absolutePath, normalizedPath, snapshot);
    }

    @Override
    public void write(Encoder encoder, Map<String, NormalizedFileSnapshot> value) throws Exception {
        encoder.writeSmallInt(value.size());
        for (String key : value.keySet()) {
            encoder.writeString(key);
            NormalizedFileSnapshot snapshot = value.get(key);
            writeSnapshot(encoder, snapshot);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) {
            return false;
        }

        SnapshotMapSerializer rhs = (SnapshotMapSerializer) obj;
        return Objects.equal(hashCodeSerializer, rhs.hashCodeSerializer);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(super.hashCode(), hashCodeSerializer);
    }

    private void writeSnapshot(Encoder encoder, NormalizedFileSnapshot value) throws IOException {
        FileContentSnapshot snapshot = value.getSnapshot();
        if (snapshot instanceof DirContentSnapshot) {
            encoder.writeByte(DIR_SNAPSHOT);
        } else if (snapshot instanceof MissingFileContentSnapshot) {
            encoder.writeByte(MISSING_FILE_SNAPSHOT);
        } else if (snapshot instanceof FileHashSnapshot) {
            encoder.writeByte(REGULAR_FILE_SNAPSHOT);
            hashCodeSerializer.write(encoder, snapshot.getContentMd5());
        } else {
            throw new AssertionError();
        }

        NormalizedPath normalizedPath = value.getNormalizedPath();
        if (normalizedPath instanceof AbsolutePath) {
            encoder.writeByte(NO_NORMALIZATION);
        } else if (normalizedPath instanceof DefaultRelativePath) {
            encoder.writeByte(DEFAULT_NORMALIZATION);
            encoder.writeString(normalizedPath.getPath());
        } else if (normalizedPath instanceof IndexedRelativePath) {
            encoder.writeByte(INDEXED_NORMALIZATION);
            encoder.writeSmallInt(((IndexedRelativePath) normalizedPath).getIndex());
        } else if (normalizedPath instanceof IgnoredPath) {
            encoder.writeByte(IGNORED_PATH_NORMALIZATION);
        } else {
            throw new AssertionError();
        }
    }
}
