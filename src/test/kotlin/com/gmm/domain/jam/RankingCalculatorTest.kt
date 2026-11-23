// cache probe
package com.gmm.domain.jam

import com.gmm.domain.jam.service.RankingCalculator
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RankingCalculatorTest :
    FunSpec({
        test("ties share a rank and the next rank is skipped") {
            RankingCalculator.rank(listOf(3.0, 4.5, 3.0, 2.0)) shouldBe listOf(2, 1, 2, 4)
        }

        test("missing scores go last and share one rank") {
            RankingCalculator.rank(listOf(null, 4.0, null, 1.0)) shouldBe listOf(3, 1, 3, 2)
        }

        test("handles no scores and empty input") {
            RankingCalculator.rank(listOf(null, null)) shouldBe listOf(1, 1)
            RankingCalculator.rank(emptyList()) shouldBe emptyList()
        }
    })
