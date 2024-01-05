package Shades

import scala.collection.{AbstractIterator, AbstractView, BuildFrom}
import scala.collection.generic.IsSeq

extension [Repr](coll: Repr)(using seq: IsSeq[Repr])
    def intersperse[B >: seq.A, That](sep: B)(using
        bf: BuildFrom[Repr, B, That]
    ): That =
        val seqOps = seq(coll)
        bf.fromSpecific(coll)(
            new AbstractView[B]:
                def iterator = new AbstractIterator[B]:
                    val it = seqOps.iterator
                    var intersperseNext = false
                    def hasNext = intersperseNext || it.hasNext
                    def next() =
                        val elem = if intersperseNext then sep else it.next()
                        intersperseNext = !intersperseNext && it.hasNext
                        elem
        )
