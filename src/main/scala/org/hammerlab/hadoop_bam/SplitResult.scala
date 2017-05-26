package org.hammerlab.hadoop_bam

import org.apache.hadoop.fs.Path
import org.apache.hadoop.mapreduce.InputSplit
import org.hammerlab.hadoop_bam.bgzf.Pos
import org.seqdoop.hadoop_bam.FileVirtualSplit

sealed trait SplitResult

case class ExtendPrevious(path: Path, newEndPos: Pos)
  extends SplitResult

case class Split(path: Path,
                 start: Pos,
                 end: Pos,
                 locations: Array[String])
  extends InputSplit
    with SplitResult {

  override def getLength: Long =
    (end.blockPos - start.blockPos) match {
      case 0 ⇒ end.offset - start.offset
      case diff ⇒ diff << 16
    }

  override def getLocations: Array[String] = locations
}

object Split {
  implicit def apply(fvs: FileVirtualSplit): Split =
    Split(
      fvs.getPath,
      Pos(fvs.getStartVirtualOffset),
      Pos(fvs.getEndVirtualOffset),
      fvs.getLocations
    )
}