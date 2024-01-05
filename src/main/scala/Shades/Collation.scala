package Shades

import scala.scalajs.js.annotation.JSGlobalScope

case class CollationElement(
    x1: Int,
    x2: Int,
    x3: Int,
)

enum CollationType:
    case regular
    case variable

object CollationElement:
    def x1(of: CollationElement): Int =
        of.x1
    def x2(of: CollationElement): Int =
        of.x2
    def x3(of: CollationElement): Int =
        of.x3
    val levels = Array(x1, x2, x3)

case class SortKey(
    L1: Array[Int],
    L2: Array[Int],
    L3: Array[Int],
):
    // S3.2: if L is not L, append a level separator
    lazy val asArray: Array[Int] =
        // S3.3: if the collation element table is forwards at level L
        // S3.4: for each collation element in the array
        // S3.5: append CE to the array if CE is not zero
        Array(L1, L2, L3).intersperse(Array(0)).flatten

    // S4: compare sort keys
    def preceedes(other: SortKey) =
        val a = this.asArray
        val b = other.asArray

        val (aDiff, bDiff) = a.zip(b).filterNot(_ == _).head
        aDiff < bDiff

object SortKey:
    def apply(from: Array[Array[Int]]): SortKey =
        val Array(x1, x2, x3) = from
        new SortKey(x1, x2, x3)

case class CollationRule(
    codepoint: Int,
    kind: CollationType,
    element: CollationElement,
    name: String,
)

case class CollationTable(
    entries: List[CollationRule]
):
    def longestPrefix(of: Array[Int]): (Array[Int], CollationElement) =
        // TODO:
        // S2.1.1: process nonstarters
        // S2.1.2: process unblocked nonstarters
        // S2.1.3: process S + C

        // S2.1: find the longest initial substring S at each point that has a match in the collation element table
        // S2.2: get the collation elements from the table
        val triple = entries.find(_.codepoint == of.head).get.element
        val remaining = of.tail
        (remaining, triple)

        // TODO:
        // S2.3: process collation elements according to variable-weight setting

    def breakApart(arr: Array[Int], triples: Array[CollationElement]): Array[CollationElement] =
        if arr.isEmpty then
            triples
        else
            // S2.1 - S2.3
            val (remaining, triple) = longestPrefix(arr)
            // S2.4: append collation element to collation element array
            val next = triples.appended(triple)
            // S2.5: proceed to the next point in the string
            breakApart(remaining, next)

    def sortKeyOf(string: String): SortKey =
        // S1.1: we assume string is normalized
        val codepoints = Window.codepointsOf(string).toArray
        // S2: produce collation element arrays
        val elements = breakApart(codepoints, Array())
        // S3: form sort key
        SortKey(CollationElement.levels.map { level =>
            elements.map(level).filterNot(_ == 0)
        })

@scalajs.js.native
@JSGlobalScope
object Window extends scalajs.js.Object:
    def codepointsOf(str: String): scalajs.js.Array[Int] = scalajs.js.native

