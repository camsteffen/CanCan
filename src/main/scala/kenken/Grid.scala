package kenken

import collection.GenTraversableOnce

import util.parsing.combinator.RegexParsers


/**
 * A nxn grid of possible values
 *
 * @param n the dimension of the grid
 * @param g map of grid positions to possible values
 */
class Grid private(n: Int, g: Map[(Int, Int), Set[Int]]) extends Iterable[((Int, Int), Set[Int])] {
  val grid = g

  def iterator = g.iterator

  def isSolved = g.values.forall(_.size == 1)

  def apply(key: (Int, Int)) = g(key)

  def +(kv: ((Int, Int), Set[Int])) = new Grid(n, g + kv)

  def ++(xs: GenTraversableOnce[((Int, Int), Set[Int])]) = new Grid(n, g ++ xs)

  override def equals(other: Any): Boolean = other match {
    case that: Grid => g == that.grid
    case _ => false
  }

  override def hashCode() = g.hashCode()

  /**
   * Apply a constraint to the grid
   * @param constraint the constraint to apply
   * @return tuple of the new grid and a list of changed cells or _None_ if the constraint cannot be satisfied
   */
  def constrain(constraint: Constraint): Option[(Grid, List[(Int, Int)])] = {
    /**
     * scala> val xs = List(('a, 1), ('b, 2), ('c, 3)); val ys = List(('a, 1), ('b, 3), ('c, 4))
     * scala> tupleDiff(xs, ys) // List[(Symbol, Int)] = List(('b,3), ('c,4))
     */
    def tupleDiff[A, B](xs: List[(A, B)], ys: List[(A, B)]): List[(A, B)] =
      xs.zip(ys).filter(p => p._1._2 != p._2._2).map(_._2)

    val before = constraint.cells.map(cell => (cell, g(cell)))
    val values = before.map(_._2)
    constraint(values) match {
      case None => None
      case after => {
        val changed = tupleDiff(before, constraint.cells.zip(after.get))
        Option((this ++ changed, changed.map(_._1)))
      }
    }
  }

  override def toString() = {
    def centered(s: String, width: Int) = {
      val pad = (width - s.length) / 2
      ("%" + width + "s").format(" " * pad + s)
    }
    def widest = g.values.map(_.mkString("").length).max
    (1 to n).map(r => (1 to n).map {
      c => centered(g((r, c)).toList.sorted.mkString(""), widest)
    }.mkString(" ")).mkString("\n")
  }
}


object Grid {
  /**
   * Create an empty grid
   * @param n grid dimension
   * @return empty grid
   */
  def apply(n: Int) = {
    val init = for (r <- (1 to n); c <- (1 to n)) yield (r, c) -> Set((1 to n): _*)
    new Grid(n, Map(init: _*))
  }

  def apply(m: Array[Array[Set[Int]]]) = {
    val n = m.length
    // The array must be square.
    require(m.forall(_.length == n))
    val init = for ((row, r) <- m.zipWithIndex; (item, c) <- row.zipWithIndex) yield ((r + 1, c + 1), item)
    new Grid(n, Map(init: _*))
  }

  /**
   * Convert an string array of numbers to a grid
   * @param s array of numbers
   * @return corresponding grid
   */
  def apply(s: String): Grid = {
    // TODO Can GridParser be an object?
    val eol = sys.props("line.separator")

    class GridParser extends RegexParsers {
      override val whiteSpace = """[ \t]+""".r
      val cell: Parser[Set[Int]] = """\d+""".r ^^ (s => Set[Int](s.toList.map(_.toString.toInt): _*))

      def row: Parser[List[Set[Int]]] = rep(cell)

      def table: Parser[List[List[Set[Int]]]] = rep(row ~ eol ^^ (_._1))
    }


    val p = new GridParser()
    p.parseAll(p.table, if (s.endsWith(eol)) s else s + eol) match {
      case p.Success(a, _) => Grid(a.map(_.toArray).toArray)
      case e: p.Failure => throw new IllegalArgumentException(e.toString())
    }
  }
}