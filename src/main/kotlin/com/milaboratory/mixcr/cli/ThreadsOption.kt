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
package com.milaboratory.mixcr.cli

import picocli.CommandLine.Option

class ThreadsOption {
    @set:Option(
        description = ["Processing threads"],
        names = ["-t", "--threads"],
        paramLabel = "<n>",
        order = 1_000_000 - 4
    )
    var value = Runtime.getRuntime().availableProcessors()
        set(value) {
            if (value <= 0) throw ValidationException("-t / --threads must be positive")
            field = value
        }
}
