/*
 * Copyright (C) 2012-2014 DuyHai DOAN
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package info.archinnov.achilles.type;

import java.util.Objects;

/**
 * <p>
 * Simple index condition for indexed query
 *
 * <pre class="code"><code class="java">
 *
 *   List<UserEntity> = manager.indexedQuery(UserEntity.class, new IndexCondition("name","John")).get();
 *
 * </code></pre>
 * @see <a href="https://github.com/doanduyhai/Achilles/wiki/Queries#indexed-query" target="_blank">Indexed queries</a>
 */
public class IndexCondition {

    private final String columnName;

    private final IndexRelation indexRelation;

    private Object columnValue;

    /**
     * Shortcut constructor to build an EQUAL index condition
     *
     * @param columnName
     *            name of indexed column
     * @param columnValue
     *            value of indexed column
     */
    public IndexCondition(String columnName, Object columnValue) {
        if (columnName == null || columnName.trim().equals("")) {
            throw new IllegalArgumentException("Column name for index condition '%s' should be provided");

        }
        if (columnValue == null) {
            throw new IllegalArgumentException("Column value for index condition '%s' should be provided");
        }
        this.columnName = columnName;
        this.indexRelation = IndexRelation.EQUAL;
        this.columnValue = columnValue;
    }

    public String getColumnName() {
        return columnName;
    }

    public Object getColumnValue() {
        return columnValue;
    }

    public void encodedValue(Object encodedValue) {
        this.columnValue = encodedValue;
    }

    @Override
    public String toString() {
        return "IndexCondition{" +
                "columnName='" + Objects.toString(columnName) + '\'' +
                ", indexRelation=" + Objects.toString(indexRelation) +
                ", columnValue=" + Objects.toString(columnValue) +
                '}';
    }
}
