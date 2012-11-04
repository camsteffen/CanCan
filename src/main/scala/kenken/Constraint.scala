package kenken

/**
 * A constraint on the possible values of a set of cells in a grid.
 * @param cs the cells to which the constraint applies
 */
abstract class Constraint(cs: List[(Int, Int)]) {
  /**
   * The cells to which this constraint applies
   */
  val cells = cs.sorted

  /**
   * Apply the constraint to the values in a set of cells
   * @param xs sets of values in the cells
   * @return sets of values in the cells with the constraint applied or
   *         _None_ if the constraint cannot be satisfied
   */
  def apply(xs: List[Set[Int]]): Option[List[Set[Int]]]

  override def toString = cells.mkString(" ")
}

/**
 * The value of a single cell is specified.
 * @param m the value
 * @param cell the cell to which the constraint applies
 */
case class SpecifiedConstraint(m: Int, cell: (Int, Int)) extends Constraint(cell :: Nil) {
  def apply(xs: List[Set[Int]]) = if (xs.head.contains(m)) Some(List(Set(m))) else None

  override def toString = m + ": " + super.toString
}

/**
 * All solved cells contain distinct values.
 *
 * The constraint is violated if the solved values are not all distinct.
 */
case class DefinitenessConstraint(cs: List[(Int, Int)]) extends Constraint(cs) {
  def apply(xs: List[Set[Int]]) = {
    // Partition input into solved and non-solved and subtract the union of
    // the non-solved values from the solved.
    val d = xs.filter(_.size == 1).foldLeft(List[Int]())((memo, x) => x.head :: memo)
    if (d.distinct != d)
      None
    else {
      //
      val s = Set(d: _*)
      Some(xs.map {
        x =>
          x.size match {
            case 1 => x
            case _ => x -- s
          }
      })
    }
  }

  override def toString = "Definite: " + super.toString
}

/**
 * If a value only appears in one cell, that cell is solved.
 */
case class UniquenessConstraint(cs: List[(Int, Int)]) extends Constraint(cs) {
  def apply(xs: List[Set[Int]]) = {
    Some(xs.map {
      x =>
        val u = x -- (xs.filter(y => !(y eq x)).reduceLeft(_ | _))
        u.size match {
          case 1 => u
          case _ => x
        }
    })
  }

  override def toString = "Unique: " + super.toString
}

/**
 * A set of cells whose values must combine arithmetically to a specified value.
 */
abstract class ArithmeticConstraint(m: Int, cs: List[(Int, Int)]) extends Constraint(cs) {
  def apply(xs: List[Set[Int]]) = {
    val f = fills(xs)
    if (f.isEmpty) None else Some(f.transpose.map(Set(_: _*)))
  }

  /**
   * Values that can fill the cells.
   *
   * For example, a 2-cell +5 constraint might return List(List(2, 3), List(3, 2), List(4, 1)).
   *
   * @param xs cell possible values
   * @return set of lists of possible values to fill the cells
   */
  def fills(xs: List[Set[Int]]): List[List[Int]]
}

/**
 * A pair of cells whose values combine with a non-associative operator.
 *
 * The constraint is satisfied if either ordering of the cells produces the specified value.
 */
abstract class NonAssociativeConstraint(m: Int, c1: (Int, Int), c2: (Int, Int))
  extends ArithmeticConstraint(m, List(c1, c2)) {

  def fills(xs: List[Set[Int]]) =
    (for (a <- xs.head; b <- xs.tail.head; if satisfied(a, b) || satisfied(b, a)) yield List(a, b)).toList

  /**
   * Does this pair of numbers satisfy the constraint?
   * @param x a number in a cell
   * @param y a number in a cell
   * @return _true_ if the combination satisfies the constraint
   */
  def satisfied(x: Int, y: Int): Boolean
}

/**
 * The difference of a pair of cells must be a specified value.
 */
case class MinusConstraint(m: Int, c1: (Int, Int), c2: (Int, Int)) extends NonAssociativeConstraint(m, c1, c2) {
  def satisfied(x: Int, y: Int) = x - y == m

  override def toString = m + "-: " + super.toString
}

/**
 * The quotient of a pair of cells must be a specified value.
 */
case class DivideConstraint(m: Int, c1: (Int, Int), c2: (Int, Int)) extends NonAssociativeConstraint(m, c1, c2) {
  def satisfied(x: Int, y: Int) = x % y == 0 && x / y == m

  override def toString = m + "/: " + super.toString
}

/**
 * A set of cells whose values combine with an associative operator
 */
abstract class AssociativeConstraint(m: Int, cs: List[(Int, Int)]) extends ArithmeticConstraint(m, cs) {
  def fills(xs: List[Set[Int]]) = {
    def cartesianProduct[A](zs: Traversable[Traversable[A]]): Seq[Seq[A]] =
      zs.foldLeft(Seq(Seq.empty[A])) {
        (x, y) => for (a <- x.view; b <- y) yield a :+ b
      }
    cartesianProduct(xs).filter(_.reduceLeft(combine) == m).map(_.toList).toList
  }

  def combine(x: Int, y: Int): Int
}

/**
 * The sum of the values in a set of cells must equal a specified value.
 */
case class PlusConstraint(m: Int, cs: List[(Int, Int)]) extends AssociativeConstraint(m, cs) {
  def combine(x: Int, y: Int) = x + y

  override def toString = m + "+: " + super.toString
}

/**
 * The sum of the values in a set of cells must equal a specified value.
 */
case class TimesConstraint(m: Int, cs: List[(Int, Int)]) extends AssociativeConstraint(m, cs) {
  def combine(x: Int, y: Int) = x * y

  override def toString = m + "x: " + super.toString
}
