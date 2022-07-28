/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.inlong.agent.plugin.sources.snapshot;

import org.apache.inlong.agent.utils.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;
import java.util.Objects;

/**
 * local file snapshot.
 */
public class LocalSnapshot implements SnapshotBase {

    public static final int BUFFER_SIZE = 1024;
    public static final int START_OFFSET = 0;
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalSnapshot.class);
    public final Encoder encoder = Base64.getEncoder();
    private final Decoder decoder = Base64.getDecoder();
    private final File file;
    private byte[] offset;

    public LocalSnapshot(String filePath) {
        file = new File(filePath);
    }

    @Override
    public String getSnapshot() {
        try {
            if (!file.exists()) {
                // if parentDir not exist, create first
                File parentDir = file.getParentFile();
                if (parentDir == null) {
                    throw new RuntimeException(String.format("no parent dir, file: %s", file.getAbsolutePath()));
                }
                if (!parentDir.exists()) {
                    boolean success = parentDir.mkdirs();
                    LOGGER.info("create dir {} result {}", parentDir, success);
                }
                file.createNewFile();
            }
            getOffset();
        } catch (Throwable ex) {
            LOGGER.error("load local offset error", ex);
            ThreadUtils.threadThrowableHandler(Thread.currentThread(), ex);
        }
        return encoder.encodeToString(offset);
    }

    private void getOffset() {
        try (FileInputStream fis = new FileInputStream(file);
                BufferedInputStream inputStream = new BufferedInputStream(fis);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            int len;
            byte[] buf = new byte[BUFFER_SIZE];
            while ((len = inputStream.read(buf)) != -1) {
                outputStream.write(buf, START_OFFSET, len);
            }
            offset = outputStream.toByteArray();
        } catch (Throwable ex) {
            ThreadUtils.threadThrowableHandler(Thread.currentThread(), ex);
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void close() {

    }

    /**
     * save offset to local file
     */
    public void save(String snapshot) {
        if (Objects.isNull(snapshot)) {
            return;
        }
        byte[] bytes = decoder.decode(snapshot);
        if (bytes.length != 0) {
            offset = bytes;
            try (OutputStream output = new FileOutputStream(file)) {
                output.write(bytes);
            } catch (Throwable e) {
                LOGGER.error("save offset to file error", e);
                ThreadUtils.threadThrowableHandler(Thread.currentThread(), e);
            }
        }
    }
}
