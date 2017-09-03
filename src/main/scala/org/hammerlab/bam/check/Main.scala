package org.hammerlab.bam.check

import caseapp.core.ArgParser
import caseapp.{ AppName, Parser, ProgName, Recurse, ExtraName ⇒ O, HelpMessage ⇒ M }
import org.apache.spark.rdd.RDD
import org.apache.spark.util.LongAccumulator
import org.hammerlab.app.{ SparkPathApp, SparkPathAppArgs }
import org.hammerlab.args.ByteRanges
import org.hammerlab.args.{ FindBlockArgs, FindReadArgs, LogArgs, OutputArgs, PostPartitionArgs, Ranges }
import org.hammerlab.bam.check.Checker.MakeChecker
import org.hammerlab.bam.check.indexed.IndexedRecordPositions
import org.hammerlab.bam.kryo.Registrar
import org.hammerlab.bgzf.Pos
import org.hammerlab.bgzf.block.PosIterator
import org.hammerlab.bytes.Bytes
import org.hammerlab.channel.CachingChannel._
import org.hammerlab.channel.SeekableByteChannel
import org.hammerlab.iterator.FinishingIterator._
import org.hammerlab.paths.Path

/**
 * CLI for [[Main]]: check every (bgzf-decompressed) byte-position in a BAM file for a record-start with and compare the results to the true
 * read-start positions.
 *
 * - Takes one argument: the path to a BAM file.
 * - Requires that BAM to have been indexed prior to running by [[org.hammerlab.bgzf.index.IndexBlocks]] and
 *   [[org.hammerlab.bam.index.IndexRecords]].
 *
 * @param sparkBam  if set, run the [[org.hammerlab.bam.check.eager.Checker]] on the input BAM file. If both [[sparkBam]] and
 *                  [[hadoopBam]] are set, they are compared to each other; if only one is set, then an
 *                  [[IndexedRecordPositions.Args.recordsPath indexed-records]] file is assumed to exist for the BAM, and is
 *                  used as the source of truth against which to compare.
 * @param hadoopBam if set, run the [[org.hammerlab.bam.check.seqdoop.Checker]] on the input BAM file. If both [[sparkBam]]
 *                  and [[hadoopBam]] are set, they are compared to each other; if only one is set, then an
 *                  [[IndexedRecordPositions.Args.recordsPath indexed-records]] file is assumed to exist for the BAM, and is
 *                  used as the source of truth against which to compare.
 */
@AppName("Check all uncompressed positions in a BAM file for record-boundary-identification errors")
@ProgName("… org.hammerlab.bam.check.Main")
case class Args(
    @Recurse blocks: Blocks.Args,
    @Recurse records: IndexedRecordPositions.Args,
    @Recurse logging: LogArgs,
    @Recurse output: OutputArgs,
    @Recurse partitioning: PostPartitionArgs,
    @Recurse findReadArgs: FindReadArgs,

    @O("s")
    @M("Run the spark-bam checker; if both or neither of -s and -u are set, then they are both run, and the results compared. If only one is set, its results are compared against a \"ground truth\" file generated by the index-records command")
    sparkBam: Boolean = false,

    @O("upstream") @O("u")
    @M("Run the hadoop-bam checker; if both or neither of -s and -u are set, then they are both run, and the results compared. If only one is set, its results are compared against a \"ground truth\" file generated by the index-records command")
    hadoopBam: Boolean = false
)
  extends SparkPathAppArgs

object Foo {
//  val i = implicitly[ArgParser[ByteRanges]]
//  val g = implicitly[ArgParser[Path]]
//
//  import java.lang.{ Long ⇒ JLong }
//
//  implicit def bytesToJLong(bytes: Bytes): JLong = bytes.bytes
//  implicit val byteRangesParser: ArgParser[ByteRanges] = Ranges.parser[Bytes, JLong]

//  implicitly[Parser[FindBlockArgs]]
  implicitly[ArgParser[ByteRanges]]
  implicitly[ArgParser[Path]]

//  implicitly[Parser[Blocks.Args]]
//  implicitly[Parser[IndexedRecordPositions.Args]]
//  implicitly[Parser[LogArgs]]
//  implicitly[Parser[OutputArgs]]

}

//import Foo.byteRangesParser

object Main
  extends SparkPathApp[Args](classOf[Registrar])
    with AnalyzeCalls {

  override def run(args: Args): Unit = {

    new CheckerMain(args) {
      override def run(): Unit = {

        val (compressedSizeAccumulator, calls) =
          (args.sparkBam, args.hadoopBam) match {
            case (true, false) ⇒
              vsIndexed[Boolean, eager.Checker]
            case (false, true) ⇒
              vsIndexed[Boolean, seqdoop.Checker]
            case _ ⇒
              compare[
                eager.Checker,
                seqdoop.Checker
              ]
          }

        analyzeCalls(
          calls,
          args.partitioning.resultsPerPartition,
          compressedSizeAccumulator
        )
      }
    }
  }

  def compare[C1 <: Checker[Boolean], C2 <: Checker[Boolean]](
      implicit
      path: Path,
      args: Blocks.Args,
      makeChecker1: MakeChecker[Boolean, C1],
      makeChecker2: MakeChecker[Boolean, C2]
  ): (LongAccumulator, RDD[(Pos, (Boolean, Boolean))]) = {

    val (blocks, _) = Blocks()

    val compressedSizeAccumulator = sc.longAccumulator("compressedSizeAccumulator")

    val calls =
      blocks
        .mapPartitions {
          blocks ⇒
            val ch = SeekableByteChannel(path).cache
            val checker1 = makeChecker1(ch)
            val checker2 = makeChecker2(ch)

            blocks
              .flatMap {
                block ⇒
                  compressedSizeAccumulator.add(block.compressedSize)
                  PosIterator(block)
              }
              .map {
                pos ⇒
                  pos →
                    (
                      checker1(pos),
                      checker2(pos)
                    )
              }
              .finish(ch.close())
        }

    (
      compressedSizeAccumulator,
      calls
    )
  }
}
