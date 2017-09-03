package org.hammerlab.bam.spark

import cats.Show
import cats.Show.show
import cats.syntax.all._
import org.hammerlab.bgzf.{ EstimatedCompressionRatio, Pos }
import org.seqdoop.hadoop_bam.FileVirtualSplit
import org.hammerlab.bam.check.Checker.default

case class Split(start: Pos,
                 end: Pos) {
  def length(implicit estimatedCompressionRatio: EstimatedCompressionRatio = default[EstimatedCompressionRatio]): Double =
    end - start
}

object Split {
  implicit def apply(t: (Pos, Pos)): Split = Split(t._1, t._2)
  implicit def apply(fvs: FileVirtualSplit): Split =
    Split(
      Pos(fvs.getStartVirtualOffset),
      Pos(fvs.getEndVirtualOffset)
    )

  implicit def makeShow(implicit showPos: Show[Pos]): Show[Split] =
    show {
      case Split(start, end) ⇒
        show"$start-$end"
    }
}
