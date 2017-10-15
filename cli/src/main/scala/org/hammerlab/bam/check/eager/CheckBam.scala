package org.hammerlab.bam.check.eager

import caseapp.{ AppName, ProgName, Recurse, ExtraName ⇒ O, HelpMessage ⇒ M }
import org.apache.spark.rdd.RDD
import org.apache.spark.util.LongAccumulator
import org.hammerlab.args.{ FindReadArgs, LogArgs, PostPartitionArgs }
import org.hammerlab.bam.check
import org.hammerlab.bam.check.Checker.MakeChecker
import org.hammerlab.bam.check.indexed.IndexedRecordPositions
import org.hammerlab.bam.check.{ AnalyzeCalls, Blocks, CheckerMain, eager, seqdoop }
import org.hammerlab.bgzf.Pos
import org.hammerlab.cli.app
import org.hammerlab.cli.app.Args
import org.hammerlab.cli.app.spark.PathApp
import org.hammerlab.cli.args.PrintLimitArgs
import org.hammerlab.kryo._
import org.hammerlab.paths.Path

object CheckBam {

  /**
   * CLI for [[CheckBam.App]]: check every (bgzf-decompressed) byte-position in a BAM file for a record-start with and
   * compare the results to the true read-start positions.
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
  case class Opts(
      @Recurse blocks: Blocks.Args,
      @Recurse records: IndexedRecordPositions.Args,
      @Recurse logging: LogArgs,
      @Recurse printLimit: PrintLimitArgs,
      @Recurse partitioning: PostPartitionArgs,
      @Recurse findReadArgs: FindReadArgs,

      @O("s")
      @M("Run the spark-bam checker; if both or neither of -s and -u are set, then they are both run, and the results compared. If only one is set, its results are compared against a \"ground truth\" file generated by the index-records command")
      sparkBam: Boolean = false,

                     @O("upstream") @O("u")
      @M("Run the hadoop-bam checker; if both or neither of -s and -u are set, then they are both run, and the results compared. If only one is set, its results are compared against a \"ground truth\" file generated by the index-records command")
      hadoopBam: Boolean = false
  )

  import AnalyzeCalls._

  case class App(args: Args[Opts])
    extends PathApp(args, Registrar) {

    def compare[C1 <: check.Checker[Boolean], C2 <: check.Checker[Boolean]](
        implicit
        path: Path,
        args: Blocks.Args,
        compressedSizeAccumulator: LongAccumulator,
        makeChecker1: MakeChecker[Boolean, C1],
        makeChecker2: MakeChecker[Boolean, C2]
    ): RDD[(Pos, (Boolean, Boolean))] =
      Blocks()
      .mapPartitions(
        callPartition[C1, Boolean, C2]
      )

    new CheckerMain(opts) {
      implicit val compressedSizeAccumulator = sc.longAccumulator("compressedSizeAccumulator")

      val calls =
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

      AnalyzeCalls(
        calls,
        args.partitioning.resultsPerPartition,
        compressedSizeAccumulator
      )
    }
  }

  case class Registrar() extends spark.Registrar(
    AnalyzeCalls,
    CheckerMain
  )

  object Main extends app.Main(App)
}
