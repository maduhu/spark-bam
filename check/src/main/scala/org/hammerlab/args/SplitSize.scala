package org.hammerlab.args

import caseapp.{ ValueDescription, HelpMessage ⇒ M, Name ⇒ O }
import hammerlab.bytes._
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat.SPLIT_MAXSIZE
import org.hammerlab.hadoop.Configuration
import org.hammerlab.hadoop.splits.MaxSplitSize

object SplitSize {
  case class Args(
    @O("max-split-size") @O("m")
    @ValueDescription("bytes")
    @M("Maximum Hadoop split-size; if unset, default to underlying FileSystem's value. Integers as well as byte-size short-hands accepted, e.g. 64m, 32MB")
    splitSize: Option[Bytes]
  ) {
    def maxSplitSize(implicit conf: Configuration): MaxSplitSize =
      MaxSplitSize(splitSize)

    def maxSplitSize(default: Bytes): MaxSplitSize =
      MaxSplitSize(splitSize.getOrElse(default))

    def set(implicit conf: Configuration): Unit =
      splitSize
        .foreach(
          conf
            .setLong(
              SPLIT_MAXSIZE,
              _
            )
        )
  }
}
