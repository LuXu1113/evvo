package com.evvo.island.population

import com.evvo.ObjectiveFunctionType

/**
  *
  * @param objective             The underlying function: takes a `Sol` and scores it.
  * @param name                  the name of this objective
  * @param optimizationDirection oneof `Minimize` or `Maximize`
  * @param precision             how many decimal points to round to. For example, if given 1,
  *                              .015 and .018 will be considered equal.
  */
abstract class Objective[Sol](
                          val name: String,
                          val optimizationDirection: OptimizationDirection,
                          precision: Int = 3)  // scalastyle:ignore magic.number
  extends Serializable {
  protected def objective(sol: Sol): Double

  def score: ObjectiveFunctionType[Sol] = sol => {
    val roundingMultiple = math.pow(10, precision) // scalastyle:ignore magic.number
    math.round(objective(sol) * roundingMultiple) / roundingMultiple
  }
}

sealed trait OptimizationDirection
case object Minimize extends OptimizationDirection
case object Maximize extends OptimizationDirection
