/*
 * Copyright 2008-2009 the original author or authors.
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
package net.hasor.dataql.ctx;
import net.hasor.dataql.Query;
import net.hasor.dataql.QueryResult;
import net.hasor.dataql.domain.BlockSet;
import net.hasor.dataql.domain.inst.CompilerStack;
import net.hasor.dataql.domain.inst.InstQueue;
import net.hasor.dataql.result.ObjectModel;

import java.util.HashMap;
import java.util.Map;
/**
 * 用于封装和引发 QL 查询执行。
 * @author 赵永春(zyc@hasor.net)
 * @version : 2017-03-23
 */
class QueryImpl implements Query {
    private final BlockSet queryModel;
    //
    public QueryImpl(DataQLFactory dataQL, BlockSet queryModel) {
        this.queryModel = queryModel;
    }
    //
    //
    @Override
    public <T> T doQuery(Map<String, Object> queryContext, Class<?> toType) {
        throw new UnsupportedOperationException();  // TODO
    }
    @Override
    public QueryResult doQuery(Map<String, Object> queryContext) {
        InstQueue queue = new InstQueue();
        this.queryModel.doCompiler(queue, new CompilerStack());
        //
        return new ObjectModel(new HashMap<String, Object>());
    }
}