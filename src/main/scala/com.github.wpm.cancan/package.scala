package com.github.wpm

import collection.immutable.PagedSeq
import io.Source
import scala.util.matching.Regex
import util.parsing.combinator.RegexParsers
import util.parsing.input.PagedSeqReader

/**
 * CanCan is a solver and generator of [[http://www.kenken.com KenKen]] puzzles.
 */
package object cancan {
  /**
   * Read a set of puzzles from a file, treating # as a comment delimiter and skipping leading blank lines.
   *
   * See [[com.github.wpm.cancan.StringRepresentation.PuzzleParser]] for details of the puzzle representation format.
   * @param filename name of the file containing the puzzles
   * @return puzzles
   */
  def readPuzzlesFromFile(filename: String): List[Puzzle] =
    Puzzle.parsePuzzles(readFile(filename))

  /**
   * All solutions of a puzzle.
   *
   * If a puzzle has multiple solutions, the order in which they are returned is undefined.
   *
   * @param puzzle a puzzle
   * @param solver a puzzle solver, by default [[com.github.wpm.cancan.OrderByCellSize]]
   * @return the puzzle's solutions
   */
  def solutions(implicit puzzle: Puzzle, solver: Solver = OrderByCellSize()): Stream[Markup] =
    solver(puzzle).filter(_.isSolution).toStream

  /**
   * All the solutions of the puzzle up to `max` partial solution states.
   *
   * This is used to abandon difficult puzzles after a finite amount of time.
   *
   * @param max the maximum number of partial solutions to search
   * @param solver a search strategy, by default [[com.github.wpm.cancan.OrderByCellSize]]
   * @return tuple (solutions, `true` if all solutions have been searched)
   */
  def cappedSolutions(implicit puzzle: Puzzle,
                      max: Int,
                      solver: Solver = OrderByCellSize()): (Stream[Markup], Boolean) = {
    val partialSolutions = solver(puzzle)
    (partialSolutions.take(max).filter(_.isSolution).toStream, partialSolutions.drop(max).isEmpty)
  }

  implicit def stringToPuzzle(s: String): Puzzle = Puzzle(s)

  /**
   * Read in a file, treating # as a comment delimiter and skipping leading blank lines.
   *
   * @param filename name of the file
   * @return file reader
   */
  def readFile(filename: String): PagedSeqReader = {
    val lines = Source.fromFile(filename).getLines().map(_.replaceAll("#.*", "").trim).dropWhile(_.isEmpty)
    new PagedSeqReader(PagedSeq.fromLines(lines))
  }

  /**
   * Print a 2-dimensional array of objects as a grid, centering each element.
   *
   * @param matrix 2-dimensional array of objects
   * @tparam T object in the array
   * @return string representation
   */
  def matrixToString[T](matrix: Traversable[Traversable[T]]): String = {
    def centered(s: String, width: Int) = {
      val pad = (width - s.length) / 2
      ("%-" + width + "s").format(" " * pad + s)
    }
    def stripTrailing(s: String) = s.replaceAll( """\s+$""", "")

    val widest = (for (row <- matrix; col <- row) yield col.toString.length).max
    matrix.map {
      row => stripTrailing(row.map(col => centered(col.toString, widest)).mkString(" "))
    }.mkString("\n")
  }

  trait MultilineParser extends RegexParsers {
    override val skipWhitespace = false
    val inLineWhitespace: Regex = """[ \t]+""".r
    val eol: String = sys.props("line.separator")
    val eoi: Regex = """\z""".r
    val lineDelimiter: Parser[String] = eol | eoi
  }

}
