package com.sksamuel.kotlintest.tests.matchers

import io.kotlintest.matchers.beGreaterThan
import io.kotlintest.matchers.beGreaterThanOrEqualTo
import io.kotlintest.matchers.beLessThan
import io.kotlintest.matchers.beLessThanOrEqualTo
import io.kotlintest.matchers.between
import io.kotlintest.matchers.should
import io.kotlintest.specs.StringSpec
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.tables.forAll
import io.kotlintest.tables.forNone
import io.kotlintest.tables.headers
import io.kotlintest.tables.row
import io.kotlintest.tables.table

class LongMatchersTest : StringSpec() {
  init {

    "Ge should be valid" {
      1L should beGreaterThan(0L)
    }

    "beGreaterThan" {
      1L should beGreaterThan(0L)

      shouldThrow<AssertionError> {
        2L should beGreaterThan(3L)
      }
    }

    "beLessThan" {
      1L should beLessThan(2L)

      shouldThrow<AssertionError> {
        2L should beLessThan(1L)
      }
    }

    "beLessThanOrEqualTo" {
      1L should beLessThanOrEqualTo(2L)

      shouldThrow<AssertionError> {
        2L should beLessThanOrEqualTo(1L)
      }
    }

    "greaterThan" {
      1L should beGreaterThanOrEqualTo(0L)

      shouldThrow<AssertionError> {
        2L should beGreaterThanOrEqualTo(3L)
      }
    }

    "between should test for valid interval" {

      val table = table(
          headers("a", "b"),
          row(0L, 2L),
          row(1L, 2L),
          row(0L, 1L),
          row(1L, 1L)
      )

      forAll(table) { a, b ->
        1 shouldBe between(a, b)
      }
    }

    "between should test for invalid interval" {

      val table = table(
          headers("a", "b"),
          row(0L, 2L),
          row(2L, 2L),
          row(4L, 5L),
          row(4L, 6L)
      )

      forNone(table) { a, b ->
        3 shouldBe between(a, b)
      }
    }
  }
}