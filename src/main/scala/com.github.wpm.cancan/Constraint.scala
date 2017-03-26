package com.github.wpm.cancan

import annotation.tailrec


/**
 * Constraint on a region of cells in a markup
 *
 * @param region region of cells in the markup
 */
abstract class Constraint(region: Seq[Cell]) extends ((Markup) => Option[Seq[(Cell, Set[Int])]]) {
  /**
   * The cells to which this constraint applies
   */
  val cells: Seq[Cell] = region.sorted

  lazy val size: Int = cells.size

  /**
   * Apply the constraint to the markup
   *
   * @return sequence of (cell, values) tuples for all changed cells or `None` if the constraint cannot be satisfied
   */
  override def apply(markup: Markup): Option[Seq[(Cell, Set[Int])]] = {
    val before = values(markup)
    constrainedValues(before) match {
      case None => None
      case Some(after) => Some(changedValues(cells, before, after))
    }
  }

  protected def changedValues(cells: Seq[Cell], before: Seq[Set[Int]], after: Seq[Set[Int]]): Seq[(Cell, Set[Int])] = {
    List(cells.zip(before), cells.zip(after)).transpose.filterNot(l => l.head._2 == l(1)._2).map(_(1))
  }

  /**
   * Values in the cells
   *
   * @param markup a markup
   * @return the values in the markup for this constraint's cells
   */
  protected def values(markup: Markup): Seq[Set[Int]] = cells.map(markup(_))

  protected def solvedValues(values: Seq[Set[Int]]): Seq[Set[Int]] = values.filter(_.size == 1)

  /**
   * Changes this constraint makes to a sequence of cell values
   *
   * @param values original values in the markup
   * @return constrained values or `None` if the constraint cannot be satisfied
   */
  protected def constrainedValues(values: Seq[Set[Int]]): Option[Seq[Set[Int]]] = None

  override def toString(): String = {
    val (r, c) = (cells.head.row, cells.head.col)
    if (cells.forall(_.row == r))
      "Row " + r
    else if (cells.forall(_.col == c)) {
      "Col " + c
    }
    else cells.mkString(" ")
  }
}

/**
 * The preemptive set constraint as described in J.F. Cook, "A Pencil-and-Paper Algorithm for Solving Sudoku Puzzles",
 * ''Notices of the American Mathematical Society'', April 2009, Volume 56, Number 4, pp. 460-468
 *
 * @param region region of cells in the markup
 */
case class PreemptiveSetConstraint(region: Seq[Cell]) extends Constraint(region) {
  override protected def constrainedValues(values: Seq[Set[Int]]): Some[Seq[Set[Int]]] = {
    // e.g. values = List(Set(1, 2, 3), Set(1, 2, 3, 4), Set(1, 2), Set(2, 3), Set(1, 2, 3, 4, 5, 6), Set(1, 2, 3, 4, 5, 6))
    // preemptiveSets = List((Set(1, 2, 3), Set(0, 2, 3)), (Set(1, 2, 3, 4), Set(0, 1, 2, 3)))
    val preemptiveSets = values.filter(_.size < values.size).map {
      v => (v, values.zipWithIndex.filter(_._1.subsetOf(v)))
    }.filter(t => t._1.size == t._2.size).map(t => (t._1, Set() ++ t._2.map(_._2)))
    // removeSets = List((Set(1, 2, 3), Set(5, 1, 4)), (Set(1, 2, 3, 4), Set(5, 4)))
    val removeSets = preemptiveSets.map(p => (p._1, Set() ++ values.indices -- p._2))
    // remove = Map(5 -> Set(1, 2, 3, 4), 1 -> Set(1, 2, 3), 4 -> Set(1, 2, 3, 4))
    val remove = (Map[Int, Set[Int]]().withDefaultValue(Set()) /: removeSets) {
      case (m, p) => m ++ p._2.map(r => r -> (m(r) ++ p._1))
    }
    // List(Set(1, 2, 3), Set(4), Set(1, 2), Set(2, 3), Set(5, 6), Set(5, 6))
    Some(values.zipWithIndex.map(v => v._1 -- remove.getOrElse(v._2, Set())))
  }
}

/**
 * All solved cells in the region must be unique.
 *
 * This constraint does not change any values in the markup but can be violated.
 *
 *  - `[123 123 123] -> [123 123 123]`
 *  - `[1 23 123] -> [1 23 123]`
 *  - `[1 23 1]   -> None`
 */
case class AllDifferentConstraint(region: Seq[Cell]) extends Constraint(region) {
  private def isDistinct[T](s: Seq[T]) = s.size == s.distinct.size

  override protected def constrainedValues(values: Seq[Set[Int]]): Option[Seq[Set[Int]]] =
    if (isDistinct(solvedValues(values))) Some(values) else None

  override def toString(): String = "Latin Square: " + super.toString
}

/**
 * If a value only appears in a single cell in the region, that cell is solved.
 *
 *  - `[12 23 23] -> [1 23 23]`
 */
case class UniquenessConstraint(region: Seq[Cell]) extends Constraint(region) {

  override protected def constrainedValues(values: Seq[Set[Int]]): Some[Seq[Set[Int]]] = {
    Some(values.map {
      value =>
      // Values only appearing in this cell.
        val u = value -- values.filter(y => !(y eq value)).reduceLeft(_ | _)
        u.size match {
          case 1 => u
          case _ => value
        }
    })
  }

  override def toString(): String = "Uniqueness: " + super.toString
}


/**
 * A constraint parameterized by an integer value.
 */
abstract class CageConstraint(value: Int, region: Seq[Cell]) extends Constraint(region) {
  protected val symbol: String
  lazy protected val nekNekSymbol: String = symbol

  override def toString(): String = value + "" + symbol

  /**
   * String representation of the constraint in the format recognized by the
   * [[http://www.mlsite.net/neknek NekNek solver]].
   */
  def toNekNekString: String = {
    nekNekSymbol + "\t" + value + "\t" + cells.map(_.toNekNekString).mkString(" ")
  }
}

/**
 * A single cell that contains a specified value.
 *
 * @param value the value the cell must contain
 * @param cell the cell
 */
case class SpecifiedConstraint(value: Int, cell: Cell) extends CageConstraint(value, Seq(cell)) {
  override def apply(markup: Markup): Option[Seq[(Cell, Set[Int])]] =
    if (markup(cell).contains(value)) Some(Seq(cell -> Set(value))) else None

  override protected val symbol = ""
  lazy override protected val nekNekSymbol = "!"
}

/**
 * A cage constraint whose values must combine arithmetically to a specified value.
 */
abstract class ArithmeticConstraint(value: Int, region: Seq[Cell]) extends CageConstraint(value, region) {
  override protected def constrainedValues(values: Seq[Set[Int]]): Option[Seq[Set[Int]]] = {
    val f = fills(values)
    if (f.isEmpty) None else Some(f.transpose.map(Set() ++ _))
  }

  /**
   * Values that can fill the cells.
   *
   * For example, a 2-cell +5 constraint might return `List(List(2, 3), List(3, 2), List(4, 1))`.
   *
   * @param values current cell values
   * @return lists of possible values to fill the cells
   */
  protected def fills(values: Seq[Set[Int]]): Seq[Seq[Int]]
}

/**
 * A pair of cells whose values combine with a non-associative operator.
 *
 * A non-associative constraint must apply to exactly two cells.
 * The constraint is satisfied if either ordering of the cells produces the specified value.
 */
abstract class NonAssociativeConstraint(value: Int, cell1: Cell, cell2: Cell)
  extends ArithmeticConstraint(value, Seq(cell1, cell2)) {

  override protected def fills(values: Seq[Set[Int]]): Seq[Seq[Int]] =
    (for (a <- values.head; b <- values.last; if satisfied(a, b) || satisfied(b, a)) yield Seq(a, b)).toSeq

  /**
   * Does this pair of numbers satisfy the constraint?
   *
   * @param x a number in a cell
   * @param y a number in a cell
   * @return `true` if the combination satisfies the constraint
   */
  protected def satisfied(x: Int, y: Int): Boolean
}

/**
 * The difference of a pair of cells must equal a specified value.
 */
case class MinusConstraint(value: Int, cell1: Cell, cell2: Cell) extends NonAssociativeConstraint(value, cell1, cell2) {
  override protected def satisfied(x: Int, y: Int): Boolean = x - y == value

  override protected val symbol = "-"
}

/**
 * The quotient of a pair of cells must equal a specified value.
 */
case class DivideConstraint(value: Int, cell1: Cell, cell2: Cell) extends NonAssociativeConstraint(value, cell1, cell2) {
  override protected def satisfied(x: Int, y: Int): Boolean = x % y == 0 && x / y == value

  override protected val symbol = "/"
}

/**
 * A set of cells whose values combine with an associative operator
 */
abstract class AssociativeConstraint(value: Int, region: Seq[Cell]) extends ArithmeticConstraint(value, region) {
  override protected def fills(values: Seq[Set[Int]]): List[List[Int]] = cartesianMonoid(values)

  /**
   * Take the Cartesian product of a set of integers and select the elements whose combination on a monoid is equal
   * to a specified value.
   *
   * @param ys sets of integers to combine
   * @return list of lists of integers combining to the target value
   */
  private def cartesianMonoid(ys: Seq[Traversable[Int]]): List[List[Int]] = {
    @tailrec
    def cmRec(ys: Seq[Traversable[Int]], acc: List[(List[Int], Int)]): List[List[Int]] = ys match {
      case Nil => acc.filter(_._2 == value).map(_._1.reverse)
      case z :: zs => cmRec(zs, for (a <- acc; b <- z; c = combine(a._2, b); if c <= value) yield (b :: a._1, c))
    }
    cmRec(ys, List((Nil, identity)))
  }

  /**
   * Combine two values with this constraint's operator
   * @param x a value
   * @param y a value
   * @return either x+y or x*y
   */
  protected def combine(x: Int, y: Int): Int

  /**
   * The identity element of the constraint's operator
   */
  protected val identity: Int
}

/**
 * The sum of the values in a set of cells must equal a specified value.
 */
case class PlusConstraint(value: Int, region: Seq[Cell]) extends AssociativeConstraint(value, region) {
  override protected def combine(x: Int, y: Int): Int = x + y

  override protected val identity = 0

  override protected val symbol = "+"
}

/**
 * The product of the values in a set of cells must equal a specified value.
 */
case class TimesConstraint(value: Int, region: Seq[Cell]) extends AssociativeConstraint(value, region) {
  override protected def combine(x: Int, y: Int): Int = x * y

  override protected val identity = 1

  override protected val symbol = "x"
  lazy override protected val nekNekSymbol = "*"
}

object Constraint {
  /**
   * Map of cells in a puzzle markup to the constraints that contain them
   */
  def constraintMap(constraints: Set[_ <: Constraint]): Map[Cell, Set[Constraint]] = {
    (Map[Cell, Set[Constraint]]() /:
      (for (constraint <- constraints; cell <- constraint.cells)
      yield cell -> constraint)) {
      case (m, (cell, constraint)) => m + (cell -> (m.getOrElse(cell, Set()) + constraint))
    }
  }
}
