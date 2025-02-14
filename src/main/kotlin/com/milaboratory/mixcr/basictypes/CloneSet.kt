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
package com.milaboratory.mixcr.basictypes

import cc.redberry.primitives.Filter
import com.milaboratory.mixcr.basictypes.VDJCSProperties.CloneOrdering
import com.milaboratory.mixcr.basictypes.tag.TagCountAggregator
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import io.repseq.core.VDJCGene
import java.util.*
import java.util.function.Function

/**
 * Created by poslavsky on 10/07/14.
 */
class CloneSet private constructor(
    val usedGenes: List<VDJCGene>,
    val clones: List<Clone>,
    val ordering: CloneOrdering,
    val cloneSetInfo: VirtualCloneSet
) : CloneSetInfo by cloneSetInfo, Iterable<Clone> by clones, HasFeatureToAlign {

    constructor(
        clones: List<Clone>,
        usedGenes: Collection<VDJCGene>,
        header: MiXCRHeader,
        footer: MiXCRFooter,
        ordering: CloneOrdering
    ) : this(
        Collections.unmodifiableList(ArrayList(usedGenes)),
        clones.sortedWith(ordering.comparator()),
        ordering,
        buildCloneSetInfo(clones, header, footer)
    )

    operator fun get(i: Int): Clone = clones[i]

    fun size(): Int = clones.size

    val alignmentParameters: VDJCAlignerParameters
        get() = header.alignerParameters

    val tagsInfo: TagsInfo
        get() = header.tagsInfo

    companion object {
        /**
         * WARNING: current object (in) will be destroyed
         */
        fun reorder(input: CloneSet, newOrdering: CloneOrdering): CloneSet {
            val newClones = input.clones.sortedWith(newOrdering.comparator())
            for (nc in newClones) nc.parent = null
            return CloneSet(newClones, input.usedGenes, input.header, input.footer, newOrdering)
        }

        /**
         * WARNING: current object (in) will be destroyed
         */
        fun transform(input: CloneSet, filter: Filter<Clone>): CloneSet {
            val newClones: MutableList<Clone> = ArrayList(input.size())
            for (i in 0 until input.size()) {
                val c = input[i]
                if (filter.accept(c)) {
                    c.parent = null
                    newClones.add(c)
                }
            }
            return CloneSet(newClones, input.usedGenes, input.header, input.footer, input.ordering)
        }

        /**
         * WARNING: current object (in) will be destroyed
         */
        fun <T> split(input: CloneSet, splitter: Function<Clone, T>): Map<T, CloneSet> {
            val clonesMap: MutableMap<T, MutableList<Clone>> = HashMap()
            for (i in 0 until input.size()) {
                val c = input[i]
                val key = splitter.apply(c)
                val cloneList = clonesMap.computeIfAbsent(key) { ArrayList() }
                c.parent = null
                cloneList.add(c)
            }
            return clonesMap
                .mapValuesTo(hashMapOf()) { (_, value) ->
                    CloneSet(
                        value, input.usedGenes, input.header, input.footer, input.ordering
                    )
                }
        }
    }
}

private fun buildCloneSetInfo(
    clones: List<Clone>,
    header: MiXCRHeader,
    footer: MiXCRFooter,
): VirtualCloneSet {
    var totalCount: Long = 0
    val tagDiversity = IntArray(header.tagsInfo.size + 1)
    val tagCountAggregator = TagCountAggregator()
    for (clone in clones) {
        totalCount += clone.count.toLong()
        require(clone.tagCount.depth() == header.tagsInfo.size) { "Conflict in tags info and clone tag counter." }
        tagCountAggregator.add(clone.tagCount)
        for (d in tagDiversity.indices) tagDiversity[d] += clone.getTagCount().getTagDiversity(d)
    }
    val result = VirtualCloneSet(
        totalCount.toDouble(),
        if (clones.isEmpty()) null else tagCountAggregator.createAndDestroy(),
        header,
        footer,
        tagDiversity
    )
    for (clone in clones) {
        totalCount += clone.count.toLong()
        clone.parentCloneSet = result
    }
    return result
}
