/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.postanalysis.ui;

import com.milaboratory.mixcr.postanalysis.additive.KeyFunctions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 *
 */
public abstract class GroupSummary<K> implements CharacteristicGroupOutputExtractor<K> {
    public static final String key = "summary";

    public final Function<CharacteristicGroupResultCell<K>, Map<Object, Object>> columnsFunction;

    public GroupSummary(Function<CharacteristicGroupResultCell<K>, Map<Object, Object>> columnsFunction) {
        this.columnsFunction = columnsFunction;
    }

    @Override
    public Map<Object, OutputTable> getTables(CharacteristicGroupResult<K> result) {
        return Collections.singletonMap(key, columnsFunction == null
                ? OutputTableExtractor.<K>summary().getTable(result)
                : OutputTableExtractor.<K>summary(columnsFunction).getTable(result));
    }

    public static final class Simple<K> extends GroupSummary<K> {
        public Simple() {super(null);}

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Simple<?> simple = (Simple<?>) o;
            return true;
        }

        @Override
        public int hashCode() {
            return 12349911;
        }
    }

    public static final class VJUsage<K> extends GroupSummary<KeyFunctions.VJGenes<K>> {
        public VJUsage() {
            super(r -> new LinkedHashMap<Object, Object>() {{
                put("VGene", r.key.vGene);
                put("JGene", r.key.jGene);
                put("Value", r.value);
            }});
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            return o != null && getClass() == o.getClass();
        }

        @Override
        public int hashCode() {
            return 123499113;
        }
    }
}
