/*
 * Copyright 2014 http://Bither.net
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yjt.wallet.core.db.implement;

import com.google.common.base.Function;
import com.yjt.wallet.core.db.base.ICursor;
import com.yjt.wallet.core.db.base.IDb;
import com.yjt.wallet.core.db.base.IProvider;


public abstract class AbstractProvider implements IProvider {
    @Override
    public void execUpdate(String sql, String[] params) {
        this.getWriteDb().execUpdate(sql, params);
    }

    @Override
    public void execQueryOneRecord(String sql, String[] params, Function<ICursor, Void> func) {
        this.getReadDb().execQueryOneRecord(sql, params, func);
    }

    @Override
    public void execQueryLoop(String sql, String[] params, Function<ICursor, Void> func) {
        this.getReadDb().execQueryLoop(sql, params, func);
    }

    @Override
    public void execUpdate(IDb db, String sql, String[] params) {
        db.execUpdate(sql, params);
    }

    @Override
    public void execQueryOneRecord(IDb db, String sql, String[] params, Function<ICursor, Void> func) {
        db.execQueryOneRecord(sql, params, func);
    }

    @Override
    public void execQueryLoop(IDb db, String sql, String[] params, Function<ICursor, Void> func) {
        db.execQueryLoop(sql, params, func);
    }
}
