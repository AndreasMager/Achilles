/*
 * Copyright (C) 2012-2018 DuyHai DOAN
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package info.archinnov.achilles.internals.entities;

import info.archinnov.achilles.annotations.Column;
import info.archinnov.achilles.annotations.EntityCreator;
import info.archinnov.achilles.annotations.UDT;

@UDT(name = "custom_constructor_udt")
public class UDTWithCustomConstructor {

    @Column
    private Long idx;

    @Column
    private String value;

    @EntityCreator
    public UDTWithCustomConstructor(long idx, String value) {
        this.idx = idx;
        this.value = value;
    }

    public Long getIdx() {
        return idx;
    }

    public String getValue() {
        return value;
    }
}
