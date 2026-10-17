/*
 * SiYuan - From thought to insight, with agents
 * Copyright (c) 2020-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.siyuan;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * 恢复首次写入中断后残留的闪念速记草稿。
 */
final class ShorthandDraftRecovery {

    private ShorthandDraftRecovery() {
    }

    // 调用方持有草稿锁，正式草稿和旧版备份优先交给 AtomicFile 读取。
    static void recover(final File baseFile) throws IOException {
        final File backupFile = new File(baseFile.getPath() + ".bak");
        final File newFile = new File(baseFile.getPath() + ".new");
        if (!baseFile.exists() && !backupFile.exists() && newFile.isFile()) {
            // 移动保留原始字节，恢复失败时不覆盖或删除临时草稿。
            Files.move(newFile.toPath(), baseFile.toPath());
        }
    }
}
