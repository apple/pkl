/**
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.pkl.lsp.type

// sealed class ConstraintValue : ConstraintExpr() {
//  override fun evaluate(thisValue: ConstraintValue): ConstraintValue = this
//
//  object Error : ConstraintValue() {
//    override fun render(builder: StringBuilder) {
//      builder.append("<Error>")
//    }
//
//    override fun computeType(base: PklBaseModule): Type = Type.Nothing
//  }
//
//  object BaseModule : ConstraintValue() {
//    override val isImplicitReceiver: Boolean
//      get() = true
//
//    override fun render(builder: StringBuilder) {
//      builder.append("base")
//    }
//
//    override fun computeType(base: PklBaseModule): Type = Type.Module.create(base.psi, "base")
//  }
//
//  abstract class Bool : ConstraintValue() {
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  object True : Bool() {
//    override fun render(builder: StringBuilder) {
//      builder.append("true")
//    }
//  }
//
//  object False : Bool() {
//    override fun render(builder: StringBuilder) {
//      builder.append("false")
//    }
//  }
//
//  object Null : ConstraintValue() {
//    override fun render(builder: StringBuilder) {
//      builder.append("null")
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.nullType
//  }
//
//  data class IntValue(val value: Long) : ConstraintValue() {
//    override fun render(builder: StringBuilder) {
//      builder.append(value)
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.intType
//  }
//
//  data class FloatValue(val value: Double) : ConstraintValue() {
//    override fun render(builder: StringBuilder) {
//      builder.append(value)
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.floatType
//  }
//
//  data class StringValue(val value: String) : ConstraintValue() {
//    override fun render(builder: StringBuilder) {
//      builder.append('"').append(escapeString(value, "\"")).append('"')
//    }
//
//    override fun computeType(base: PklBaseModule): Type = Type.StringLiteral(value)
//  }
//
//  data class RegexValue(val value: Regex) : ConstraintValue() {
//    override fun render(builder: StringBuilder) {
//      builder.append("Regex(#\"")
//      builder.append(escapeString(value.pattern, "#\""))
//      builder.append("\"#)")
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.regexType
//  }
//
//  // originally copied from Pkl codebase
//  class Duration(val value: Double, val unit: Unit) : ConstraintValue(), Comparable<Duration> {
//    override fun computeType(base: PklBaseModule): Type = base.durationType
//
//    override operator fun compareTo(other: Duration): Int {
//      return if (unit.ordinal <= other.unit.ordinal) {
//        convertValueTo(other.unit).compareTo(other.value)
//      } else {
//        value.compareTo(other.convertValueTo(unit))
//      }
//    }
//
//    override fun render(builder: StringBuilder) {
//      if (isMathematicalInteger(value)) {
//        builder.append(value.toLong())
//      } else {
//        builder.append(value)
//      }
//      builder.append('.')
//      unit.render(builder)
//    }
//
//    override fun equals(other: Any?): Boolean {
//      if (this === other) return true
//      return other is Duration && convertValueTo(Unit.NANOS) == other.convertValueTo(Unit.NANOS)
//    }
//
//    override fun hashCode(): Int {
//      return convertValueTo(Unit.NANOS).hashCode()
//    }
//
//    private fun convertValueTo(other: Unit): Double {
//      return value * unit.nanos / other.nanos
//    }
//
//    enum class Unit(val nanos: Long, private val symbol: String) {
//
//      NANOS(1, "ns"),
//      MICROS(1000, "us"),
//      MILLIS(1000L * 1000, "ms"),
//      SECONDS(1000L * 1000 * 1000, "s"),
//      MINUTES(1000L * 1000 * 1000 * 60, "min"),
//      HOURS(1000L * 1000 * 1000 * 60 * 60, "h"),
//      DAYS(1000L * 1000 * 1000 * 60 * 60 * 24, "d");
//
//      fun render(builder: StringBuilder) {
//        builder.append(symbol)
//      }
//
//      override fun toString(): String {
//        return symbol
//      }
//    }
//  }
//
//  // originally copied from Pkl codebase
//  class DataSize(val value: Double, val unit: Unit) : ConstraintValue(), Comparable<DataSize> {
//    override fun computeType(base: PklBaseModule): Type = base.dataSizeType
//
//    override operator fun compareTo(other: DataSize): Int {
//      return if (unit.ordinal <= other.unit.ordinal) {
//        convertValueTo(other.unit).compareTo(other.value)
//      } else {
//        value.compareTo(other.convertValueTo(unit))
//      }
//    }
//
//    override fun render(builder: StringBuilder) {
//      if (isMathematicalInteger(value)) {
//        builder.append(value.toLong())
//      } else {
//        builder.append(value)
//      }
//      builder.append('.')
//      unit.render(builder)
//    }
//
//    override fun equals(other: Any?): Boolean {
//      if (this === other) return true
//      return other is DataSize && convertValueTo(Unit.BYTES) == other.convertValueTo(Unit.BYTES)
//    }
//
//    override fun hashCode(): Int {
//      return convertValueTo(Unit.BYTES).hashCode()
//    }
//
//    private fun convertValueTo(other: Unit): Double {
//      return value * unit.bytes / other.bytes
//    }
//
//    enum class Unit(val bytes: Long, private val symbol: String) {
//
//      BYTES(1, "b"),
//      KILOBYTES(1000, "kb"),
//      KIBIBYTES(1024, "kib"),
//      MEGABYTES(1000L * 1000, "mb"),
//      MEBIBYTES(1024L * 1024, "mib"),
//      GIGABYTES(1000L * 1000 * 1000, "gb"),
//      GIBIBYTES(1024L * 1024 * 1024, "gib"),
//      TERABYTES(1000L * 1000 * 1000 * 1000, "tb"),
//      TEBIBYTES(1024L * 1024 * 1024 * 1024, "tib"),
//      PETABYTES(1000L * 1000 * 1000 * 1000 * 1000, "pb"),
//      PEBIBYTES(1024L * 1024 * 1024 * 1024 * 1024, "pib");
//
//      fun render(builder: StringBuilder) {
//        builder.append(symbol)
//      }
//
//      override fun toString(): String {
//        return symbol
//      }
//    }
//  }
//
//  data class ListValue(val elements: List<ConstraintValue>) : ConstraintValue() {
//    override fun computeType(base: PklBaseModule): Type = base.listType
//
//    override fun render(builder: StringBuilder) {
//      builder.append("List(")
//      var first = true
//      for (element in elements) {
//        if (first) first = false else builder.append(", ")
//        element.render(builder)
//      }
//      builder.append(")")
//    }
//  }
//
//  data class SetValue(val elements: Set<ConstraintValue>) : ConstraintValue() {
//    override fun computeType(base: PklBaseModule): Type = base.setType
//
//    override fun render(builder: StringBuilder) {
//      builder.append("Set(")
//      var first = true
//      for (element in elements) {
//        if (first) first = false else builder.append(", ")
//        element.render(builder)
//      }
//      builder.append(")")
//    }
//  }
//
//  data class MapValue(val entries: Map<ConstraintValue, ConstraintValue>) : ConstraintValue() {
//    override fun computeType(base: PklBaseModule): Type = base.mapType
//
//    override fun render(builder: StringBuilder) {
//      builder.append("Map(")
//      var first = true
//      for ((key, value) in entries) {
//        if (first) first = false else builder.append(", ")
//        key.render(builder)
//        builder.append(", ")
//        value.render(builder)
//      }
//      builder.append(")")
//    }
//  }
//
//  // object values are harder to support because they typically use amend syntax,
//  // which makes it difficult to determine their members.
//  // however, we could start with just the members defined in the amend expression.
// }

sealed class ConstraintExpr {}
// sealed class ConstraintExpr {
//  companion object {
//    fun or(leftExpr: ConstraintExpr, rightExpr: ConstraintExpr): ConstraintExpr {
//      return when (leftExpr) {
//        True -> True
//        False -> rightExpr
//        else -> Or(leftExpr, rightExpr)
//      }
//    }
//
//    fun and(leftExpr: ConstraintExpr, rightExpr: ConstraintExpr): ConstraintExpr {
//      return when (leftExpr) {
//        True -> rightExpr
//        False -> False
//        else -> And(leftExpr, rightExpr)
//      }
//    }
//
//    fun and(constraints: List<ConstraintExpr>): ConstraintExpr {
//      return when {
//        constraints.isEmpty() -> True
//        else -> constraints.reduce { left, right -> and(left, right) }
//      }
//    }
//  }
//
//  abstract fun evaluate(thisValue: ConstraintValue): ConstraintValue
//
//  abstract fun computeType(base: PklBaseModule): Type
//
//  abstract fun render(builder: StringBuilder)
//
//  fun render(): String = buildString { render(this) }
//
//  open val isImplicitReceiver: Boolean
//    get() = false
//
//  protected fun renderReceiverExprAndDot(expr: ConstraintExpr, builder: StringBuilder) {
//    if (expr.isImplicitReceiver) return
//
//    val needParens = expr is InfixExpr
//    if (needParens) builder.append('(')
//    expr.render(builder)
//    if (needParens) builder.append(')')
//    builder.append('.')
//  }
//
//  class TypeTest(
//    private val operandExpr: ConstraintExpr,
//    private val type: Type,
//    private val base: PklBaseModule
//  ) : ConstraintExpr() {
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue =
//      operandExpr.computeType(base).isSubtypeOf(type, base).toBool()
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//
//    override fun render(builder: StringBuilder) {
//      operandExpr.render(builder)
//      builder.append(" is ")
//      type.render(builder)
//    }
//  }
//
//  object ThisExpr : ConstraintExpr() {
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue = thisValue
//
//    override fun computeType(base: PklBaseModule): Type = Type.Unknown // TODO
//
//    override fun render(builder: StringBuilder) {
//      builder.append("this")
//    }
//  }
//
//  object ImplicitThisExpr : ConstraintExpr() {
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue = thisValue
//
//    override fun computeType(base: PklBaseModule): Type = Type.Unknown // TODO
//
//    override val isImplicitReceiver: Boolean
//      get() = true
//
//    override fun render(builder: StringBuilder) {}
//  }
//
//  abstract class PrefixExpr(val operandExpr: ConstraintExpr) : ConstraintExpr() {
//
//    abstract val operator: String
//
//    final override fun render(builder: StringBuilder) {
//      builder.append(operator)
//      val needsParens = operandExpr is InfixExpr
//      if (needsParens) builder.append('(')
//      operandExpr.render(builder)
//      if (needsParens) builder.append(')')
//    }
//  }
//
//  abstract class InfixExpr(val leftExpr: ConstraintExpr, val rightExpr: ConstraintExpr) :
//    ConstraintExpr() {
//
//    abstract val operator: String
//
//    final override fun render(builder: StringBuilder) {
//      val leftNeedsParens = leftExpr is InfixExpr && leftExpr::class != this::class
//      if (leftNeedsParens) builder.append('(')
//      leftExpr.render(builder)
//      if (leftNeedsParens) builder.append(')')
//
//      builder.append(' ').append(operator).append(' ')
//
//      val rightNeedsParens = rightExpr is InfixExpr && rightExpr::class != this::class
//      if (rightNeedsParens) builder.append('(')
//      rightExpr.render(builder)
//      if (rightNeedsParens) builder.append(')')
//    }
//  }
//
//  abstract class PropertyAccess(protected val receiverExpr: ConstraintExpr) : ConstraintExpr() {
//    abstract val propertyName: String
//
//    final override fun render(builder: StringBuilder) {
//      renderReceiverExprAndDot(receiverExpr, builder)
//      builder.append(propertyName)
//    }
//  }
//
//  abstract class OneArgMethodCall(
//    protected val receiverExpr: ConstraintExpr,
//    protected val argumentExpr: ConstraintExpr
//  ) : ConstraintExpr() {
//
//    abstract val methodName: String
//
//    final override fun render(builder: StringBuilder) {
//      renderReceiverExprAndDot(receiverExpr, builder)
//      builder.append(methodName).append('(')
//      argumentExpr.render(builder)
//      builder.append(')')
//    }
//  }
//
//  abstract class TwoArgMethodCall(
//    protected val receiverExpr: ConstraintExpr,
//    protected val argument1Expr: ConstraintExpr,
//    protected val argument2Expr: ConstraintExpr
//  ) : ConstraintExpr() {
//
//    abstract val methodName: String
//
//    final override fun render(builder: StringBuilder) {
//      renderReceiverExprAndDot(receiverExpr, builder)
//      builder.append(methodName).append('(')
//      argument1Expr.render(builder)
//      builder.append(", ")
//      argument2Expr.render(builder)
//      builder.append(')')
//    }
//  }
//
//  abstract class VarArgMethodCall(
//    private val receiverExpr: ConstraintExpr,
//    protected val argumentExprs: List<ConstraintExpr>
//  ) : ConstraintExpr() {
//
//    abstract val methodName: String
//
//    final override fun render(builder: StringBuilder) {
//      renderReceiverExprAndDot(receiverExpr, builder)
//      builder.append(methodName).append('(')
//      var first = true
//      for (argumentExpr in argumentExprs) {
//        if (first) first = false else builder.append(", ")
//        argumentExpr.render(builder)
//      }
//      builder.append(')')
//    }
//  }
//
//  class Equals(leftExpr: ConstraintExpr, rightExpr: ConstraintExpr) :
//    InfixExpr(leftExpr, rightExpr) {
//
//    override val operator: String
//      get() = "=="
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val left = leftExpr.evaluate(thisValue)
//      val right = rightExpr.evaluate(thisValue)
//
//      return when (left) {
//        is IntValue ->
//          when (right) {
//            is FloatValue -> (left.value.toDouble() == right.value).toBool()
//            Error -> Error
//            else -> (left == right).toBool()
//          }
//        is FloatValue ->
//          when (right) {
//            is IntValue -> (left.value == right.value.toDouble()).toBool()
//            Error -> Error
//            else -> (left == right).toBool()
//          }
//        Error -> Error
//        else ->
//          when (right) {
//            Error -> Error
//            else -> (left == right).toBool()
//          }
//      }
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class NotEquals(leftExpr: ConstraintExpr, rightExpr: ConstraintExpr) :
//    InfixExpr(leftExpr, rightExpr) {
//
//    override val operator: String
//      get() = "!="
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val left = leftExpr.evaluate(thisValue)
//      val right = rightExpr.evaluate(thisValue)
//
//      return when (left) {
//        is IntValue ->
//          when (right) {
//            is FloatValue -> (left.value.toDouble() != right.value).toBool()
//            Error -> Error
//            else -> (left != right).toBool()
//          }
//        is FloatValue ->
//          when (right) {
//            is IntValue -> (left.value != right.value.toDouble()).toBool()
//            Error -> Error
//            else -> (left != right).toBool()
//          }
//        Error -> Error
//        else ->
//          when (right) {
//            Error -> Error
//            else -> (left != right).toBool()
//          }
//      }
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class LessThan(leftExpr: ConstraintExpr, rightExpr: ConstraintExpr) :
//    InfixExpr(leftExpr, rightExpr) {
//
//    override val operator: String = "<"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val left = leftExpr.evaluate(thisValue)
//      val right = rightExpr.evaluate(thisValue)
//
//      return when (left) {
//        is IntValue ->
//          when (right) {
//            is FloatValue -> (left.value.toDouble() < right.value).toBool()
//            is IntValue -> (left.value < right.value).toBool()
//            else -> Error
//          }
//        is FloatValue ->
//          when (right) {
//            is IntValue -> (left.value < right.value.toDouble()).toBool()
//            is FloatValue -> (left.value < right.value).toBool()
//            else -> Error
//          }
//        is Duration ->
//          when (right) {
//            is Duration -> (left < right).toBool()
//            else -> Error
//          }
//        is DataSize ->
//          when (right) {
//            is DataSize -> (left < right).toBool()
//            else -> Error
//          }
//        else -> Error
//      }
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class LessThanOrEqualTo(leftExpr: ConstraintExpr, rightExpr: ConstraintExpr) :
//    InfixExpr(leftExpr, rightExpr) {
//
//    override val operator: String = "<="
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val left = leftExpr.evaluate(thisValue)
//      val right = rightExpr.evaluate(thisValue)
//
//      return when (left) {
//        is IntValue ->
//          when (right) {
//            is FloatValue -> (left.value.toDouble() <= right.value).toBool()
//            is IntValue -> (left.value <= right.value).toBool()
//            else -> Error
//          }
//        is FloatValue ->
//          when (right) {
//            is IntValue -> (left.value <= right.value.toDouble()).toBool()
//            is FloatValue -> (left.value <= right.value).toBool()
//            else -> Error
//          }
//        is Duration ->
//          when (right) {
//            is Duration -> (left <= right).toBool()
//            else -> Error
//          }
//        is DataSize ->
//          when (right) {
//            is DataSize -> (left <= right).toBool()
//            else -> Error
//          }
//        else -> Error
//      }
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class GreaterThan(leftExpr: ConstraintExpr, rightExpr: ConstraintExpr) :
//    InfixExpr(leftExpr, rightExpr) {
//
//    override val operator: String = ">"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val left = leftExpr.evaluate(thisValue)
//      val right = rightExpr.evaluate(thisValue)
//
//      return when (left) {
//        is IntValue ->
//          when (right) {
//            is FloatValue -> (left.value.toDouble() > right.value).toBool()
//            is IntValue -> (left.value > right.value).toBool()
//            else -> Error
//          }
//        is FloatValue ->
//          when (right) {
//            is IntValue -> (left.value > right.value.toDouble()).toBool()
//            is FloatValue -> (left.value > right.value).toBool()
//            else -> Error
//          }
//        is Duration ->
//          when (right) {
//            is Duration -> (left > right).toBool()
//            else -> Error
//          }
//        is DataSize ->
//          when (right) {
//            is DataSize -> (left > right).toBool()
//            else -> Error
//          }
//        else -> Error
//      }
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class GreaterThanOrEqualTo(leftExpr: ConstraintExpr, rightExpr: ConstraintExpr) :
//    InfixExpr(leftExpr, rightExpr) {
//
//    override val operator: String = ">="
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val left = leftExpr.evaluate(thisValue)
//      val right = rightExpr.evaluate(thisValue)
//
//      return when (left) {
//        is IntValue ->
//          when (right) {
//            is FloatValue -> (left.value.toDouble() >= right.value).toBool()
//            is IntValue -> (left.value >= right.value).toBool()
//            else -> Error
//          }
//        is FloatValue ->
//          when (right) {
//            is IntValue -> (left.value >= right.value.toDouble()).toBool()
//            is FloatValue -> (left.value >= right.value).toBool()
//            else -> Error
//          }
//        is Duration ->
//          when (right) {
//            is Duration -> (left >= right).toBool()
//            else -> Error
//          }
//        is DataSize ->
//          when (right) {
//            is DataSize -> (left >= right).toBool()
//            else -> Error
//          }
//        else -> Error
//      }
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class NullCoalesce(leftExpr: ConstraintExpr, rightExpr: ConstraintExpr) :
//    InfixExpr(leftExpr, rightExpr) {
//    override val operator: String
//      get() = "??"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      return when (val leftValue = leftExpr.evaluate(thisValue)) {
//        Null -> rightExpr.evaluate(thisValue)
//        else -> leftValue
//      }
//    }
//
//    override fun computeType(base: PklBaseModule): Type =
//      Type.union(leftExpr.computeType(base), rightExpr.computeType(base), base)
//  }
//
//  class If(
//    private val conditionExpr: ConstraintExpr,
//    private val thenExpr: ConstraintExpr,
//    private val elseExpr: ConstraintExpr
//  ) : ConstraintExpr() {
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      return when (conditionExpr.evaluate(thisValue)) {
//        True -> thenExpr.evaluate(thisValue)
//        False -> elseExpr.evaluate(thisValue)
//        else -> Error
//      }
//    }
//
//    override fun computeType(base: PklBaseModule): Type =
//      Type.union(thenExpr.computeType(base), elseExpr.computeType(base), base)
//
//    override fun render(builder: StringBuilder) {
//      builder.append("if (")
//      conditionExpr.render(builder)
//      builder.append(") ")
//      thenExpr.render(builder)
//      builder.append(" else ")
//      elseExpr.render(builder)
//    }
//  }
//
//  class StringMatches(receiverExpr: ConstraintExpr, argumentExpr: ConstraintExpr) :
//    OneArgMethodCall(receiverExpr, argumentExpr) {
//
//    override val methodName: String = "matches"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? StringValue ?: return Error
//      val argument = argumentExpr.evaluate(thisValue) as? RegexValue ?: return Error
//      return argument.value.matches(receiver.value).toBool()
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class StringContains(receiverExpr: ConstraintExpr, argumentExpr: ConstraintExpr) :
//    OneArgMethodCall(receiverExpr, argumentExpr) {
//
//    override val methodName: String = "contains"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? StringValue ?: return Error
//      return when (val argument = argumentExpr.evaluate(thisValue)) {
//        is StringValue -> receiver.value.contains(argument.value).toBool()
//        is RegexValue -> receiver.value.contains(argument.value).toBool()
//        else -> Error
//      }
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class StringStartsWith(receiverExpr: ConstraintExpr, argumentExpr: ConstraintExpr) :
//    OneArgMethodCall(receiverExpr, argumentExpr) {
//
//    override val methodName: String = "startsWith"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? StringValue ?: return Error
//      return when (val argument = argumentExpr.evaluate(thisValue)) {
//        is StringValue -> receiver.value.startsWith(argument.value).toBool()
//        is RegexValue -> (argument.value.find(receiver.value)?.range?.start == 0).toBool()
//        else -> Error
//      }
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class StringEndsWith(receiverExpr: ConstraintExpr, argumentExpr: ConstraintExpr) :
//    OneArgMethodCall(receiverExpr, argumentExpr) {
//
//    override val methodName: String = "endsWith"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? StringValue ?: return Error
//      return when (val argument = argumentExpr.evaluate(thisValue)) {
//        is StringValue -> receiver.value.endsWith(argument.value).toBool()
//        is RegexValue ->
//          (argument.value.findAll(receiver.value).lastOrNull()?.range?.endInclusive ==
//            receiver.value.lastIndex)
//            .toBool()
//        else -> Error
//      }
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class IntIsPositive(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
//    override val propertyName: String
//      get() = "isPositive"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? IntValue ?: return Error
//      return (receiver.value >= 0).toBool()
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class IntIsNonZero(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
//    override val propertyName: String
//      get() = "isNonZero"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? IntValue ?: return Error
//      return (receiver.value != 0L).toBool()
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class IntIsEven(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
//    override val propertyName: String
//      get() = "isEven"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? IntValue ?: return Error
//      return (receiver.value % 2 == 0L).toBool()
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class IntIsOdd(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
//    override val propertyName: String
//      get() = "isOdd"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? IntValue ?: return Error
//      return (receiver.value % 2 == 1L).toBool()
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class FloatIsPositive(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
//    override val propertyName: String
//      get() = "isPositive"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? FloatValue ?: return Error
//      return (receiver.value >= 0.0).toBool()
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class FloatIsNonZero(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
//    override val propertyName: String
//      get() = "isNonZero"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? FloatValue ?: return Error
//      return (receiver.value != 0.0).toBool()
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class FloatIsFinite(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
//    override val propertyName: String
//      get() = "isFinite"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? FloatValue ?: return Error
//      return (receiver.value.isFinite()).toBool()
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class FloatIsInfinite(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
//    override val propertyName: String
//      get() = "isInfinite"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? FloatValue ?: return Error
//      return (receiver.value.isInfinite()).toBool()
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class FloatIsNaN(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
//    override val propertyName: String
//      get() = "isNaN"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? FloatValue ?: return Error
//      return (receiver.value.isNaN()).toBool()
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class DurationIsPositive(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
//    override val propertyName: String
//      get() = "isPositive"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? Duration ?: return Error
//      return (receiver.value >= 0.0).toBool()
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class DataSizeIsPositive(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
//    override val propertyName: String
//      get() = "isPositive"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? DataSize ?: return Error
//      return (receiver.value >= 0.0).toBool()
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class DataSizeIsBinaryUnit(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
//    override val propertyName: String
//      get() = "isBinaryUnit"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? DataSize ?: return Error
//      val ordinal = receiver.unit.ordinal
//      return (ordinal % 2 == 0).toBool()
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class DataSizeIsDecimalUnit(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
//    override val propertyName: String
//      get() = "isDecimalUnit"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? DataSize ?: return Error
//      val ordinal = receiver.unit.ordinal
//      return (ordinal == 0 || ordinal % 2 == 1).toBool()
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class StringIsEmpty(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
//    override val propertyName: String
//      get() = "isEmpty"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? StringValue ?: return Error
//      return receiver.value.isEmpty().toBool()
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class StringIsRegex(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
//    override val propertyName: String
//      get() = "isRegex"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? StringValue ?: return Error
//      return try {
//        Regex(receiver.value)
//        True
//      } catch (e: PatternSyntaxException) {
//        False
//      }
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class ListIsEmpty(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
//    override val propertyName: String
//      get() = "isEmpty"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? ListValue ?: return Error
//      return receiver.elements.isEmpty().toBool()
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class SetIsEmpty(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
//    override val propertyName: String
//      get() = "isEmpty"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? SetValue ?: return Error
//      return receiver.elements.isEmpty().toBool()
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class MapIsEmpty(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
//    override val propertyName: String
//      get() = "isEmpty"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? MapValue ?: return Error
//      return receiver.entries.isEmpty().toBool()
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class StringLength(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
//    override val propertyName: String
//      get() = "length"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? StringValue ?: return Error
//      return IntValue(receiver.value.length.toLong())
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.intType
//  }
//
//  class ListLength(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
//    override val propertyName: String
//      get() = "length"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? ListValue ?: return Error
//      return IntValue(receiver.elements.size.toLong())
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.intType
//  }
//
//  class SetLength(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
//    override val propertyName: String
//      get() = "length"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? SetValue ?: return Error
//      return IntValue(receiver.elements.size.toLong())
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.intType
//  }
//
//  class MapLength(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
//    override val propertyName: String
//      get() = "length"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? MapValue ?: return Error
//      return IntValue(receiver.entries.size.toLong())
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.intType
//  }
//
//  class IntIsBetween(
//    receiverExpr: ConstraintExpr,
//    argument1Expr: ConstraintExpr,
//    argument2Expr: ConstraintExpr
//  ) : TwoArgMethodCall(receiverExpr, argument1Expr, argument2Expr) {
//
//    override val methodName: String
//      get() = "isBetween"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? IntValue ?: return Error
//      val left = argument1Expr.evaluate(thisValue)
//      val right = argument2Expr.evaluate(thisValue)
//
//      return when (left) {
//        is IntValue ->
//          when (right) {
//            is IntValue -> (receiver.value in left.value..right.value).toBool()
//            is FloatValue ->
//              (receiver.value.toDouble() in left.value.toDouble()..right.value).toBool()
//            else -> Error
//          }
//        is FloatValue ->
//          when (right) {
//            is IntValue ->
//              (receiver.value.toDouble() in left.value..right.value.toDouble()).toBool()
//            is FloatValue -> (receiver.value.toDouble() in left.value..right.value).toBool()
//            else -> Error
//          }
//        else -> Error
//      }
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class FloatIsBetween(
//    receiverExpr: ConstraintExpr,
//    argument1Expr: ConstraintExpr,
//    argument2Expr: ConstraintExpr
//  ) : TwoArgMethodCall(receiverExpr, argument1Expr, argument2Expr) {
//
//    override val methodName: String
//      get() = "isBetween"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? FloatValue ?: return Error
//      val left = argument1Expr.evaluate(thisValue)
//      val right = argument2Expr.evaluate(thisValue)
//
//      return when (left) {
//        is IntValue ->
//          when (right) {
//            is IntValue ->
//              (receiver.value in left.value.toDouble()..right.value.toDouble()).toBool()
//            is FloatValue -> (receiver.value in left.value.toDouble()..right.value).toBool()
//            else -> Error
//          }
//        is FloatValue ->
//          when (right) {
//            is IntValue -> (receiver.value in left.value..right.value.toDouble()).toBool()
//            is FloatValue -> (receiver.value in left.value..right.value).toBool()
//            else -> Error
//          }
//        else -> Error
//      }
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class DurationIsBetween(
//    receiverExpr: ConstraintExpr,
//    argument1Expr: ConstraintExpr,
//    argument2Expr: ConstraintExpr
//  ) : TwoArgMethodCall(receiverExpr, argument1Expr, argument2Expr) {
//
//    override val methodName: String
//      get() = "isBetween"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? Duration ?: return Error
//      val argument1 = argument1Expr.evaluate(thisValue) as? Duration ?: return Error
//      val argument2 = argument2Expr.evaluate(thisValue) as? Duration ?: return Error
//
//      return (receiver in argument1..argument2).toBool()
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class DataSizeIsBetween(
//    receiverExpr: ConstraintExpr,
//    argument1Expr: ConstraintExpr,
//    argument2Expr: ConstraintExpr
//  ) : TwoArgMethodCall(receiverExpr, argument1Expr, argument2Expr) {
//
//    override val methodName: String
//      get() = "isBetween"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue) as? DataSize ?: return Error
//      val argument1 = argument1Expr.evaluate(thisValue) as? DataSize ?: return Error
//      val argument2 = argument2Expr.evaluate(thisValue) as? DataSize ?: return Error
//
//      return (receiver in argument1..argument2).toBool()
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class UnaryMinus(operandExpr: ConstraintExpr) : PrefixExpr(operandExpr) {
//    override val operator: String = "-"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      return when (val operand = operandExpr.evaluate(thisValue)) {
//        is IntValue -> IntValue(-operand.value)
//        is FloatValue -> FloatValue(-operand.value)
//        is Duration -> Duration(-operand.value, operand.unit)
//        is DataSize -> DataSize(-operand.value, operand.unit)
//        else -> Error
//      }
//    }
//
//    override fun computeType(base: PklBaseModule): Type {
//      return when (val operandType = operandExpr.computeType(base)) {
//        base.intType,
//        base.floatType,
//        base.durationType,
//        base.dataSizeType -> operandType
//        else -> Type.Nothing
//      }
//    }
//  }
//
//  class Not(operandExpr: ConstraintExpr) : PrefixExpr(operandExpr) {
//    override val operator: String = "!"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      return when (operandExpr.evaluate(thisValue)) {
//        is True -> False
//        is False -> True
//        else -> Error
//      }
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class And(leftExpr: ConstraintExpr, rightExpr: ConstraintExpr) : InfixExpr(leftExpr, rightExpr)
// {
//
//    override val operator: String
//      get() = "&&"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val left = leftExpr.evaluate(thisValue)
//      val right = rightExpr.evaluate(thisValue)
//
//      return when (left) {
//        is True ->
//          when (right) {
//            is True -> True
//            is False -> False
//            else -> Error
//          }
//        else -> Error
//      }
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class Or(leftExpr: ConstraintExpr, rightExpr: ConstraintExpr) : InfixExpr(leftExpr, rightExpr) {
//
//    override val operator: String
//      get() = "||"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val left = leftExpr.evaluate(thisValue)
//      val right = rightExpr.evaluate(thisValue)
//
//      return when (left) {
//        is True -> True
//        is False ->
//          when (right) {
//            is True -> True
//            is False -> False
//            else -> Error
//          }
//        else -> Error
//      }
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class Xor(receiverExpr: ConstraintExpr, argumentExpr: ConstraintExpr) :
//    OneArgMethodCall(receiverExpr, argumentExpr) {
//
//    override val methodName: String
//      get() = "xor"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue)
//      val argument = argumentExpr.evaluate(thisValue)
//
//      return when (receiver) {
//        is True ->
//          when (argument) {
//            is True -> False
//            is False -> True
//            else -> Error
//          }
//        is False ->
//          when (argument) {
//            is True -> True
//            is False -> False
//            else -> Error
//          }
//        else -> Error
//      }
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class Implies(receiverExpr: ConstraintExpr, argumentExpr: ConstraintExpr) :
//    OneArgMethodCall(receiverExpr, argumentExpr) {
//
//    override val methodName: String
//      get() = "implies"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val receiver = receiverExpr.evaluate(thisValue)
//      val argument = argumentExpr.evaluate(thisValue)
//
//      return when (receiver) {
//        is True ->
//          when (argument) {
//            is True -> True
//            is False -> False
//            else -> Error
//          }
//        is False ->
//          when (argument) {
//            is True -> True
//            is False -> True
//            else -> Error
//          }
//        else -> Error
//      }
//    }
//
//    override fun computeType(base: PklBaseModule): Type = base.booleanType
//  }
//
//  class ListExpr(argumentExprs: List<ConstraintExpr>) :
//    VarArgMethodCall(BaseModule, argumentExprs) {
//    override val methodName: String
//      get() = "List"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      return ListValue(argumentExprs.map { it.evaluate(thisValue) })
//    }
//
//    override fun computeType(base: PklBaseModule): Type =
//      base.listType.withTypeArguments(Type.union(argumentExprs.map { it.computeType(base) },
// base))
//  }
//
//  class SetExpr(argumentExprs: List<ConstraintExpr>) : VarArgMethodCall(BaseModule, argumentExprs)
// {
//    override val methodName: String
//      get() = "Set"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      return SetValue(argumentExprs.mapTo(mutableSetOf()) { it.evaluate(thisValue) })
//    }
//
//    override fun computeType(base: PklBaseModule): Type =
//      base.listType.withTypeArguments(Type.union(argumentExprs.map { it.computeType(base) },
// base))
//  }
//
//  class MapExpr(argumentExprs: List<ConstraintExpr>) : VarArgMethodCall(BaseModule, argumentExprs)
// {
//    override val methodName: String
//      get() = "Map"
//
//    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
//      val evenSize = argumentExprs.size.let { i -> i - (i % 2) }
//      val entries = mutableMapOf<ConstraintValue, ConstraintValue>()
//      for (i in 0 until evenSize step 2) {
//        entries[argumentExprs[i].evaluate(thisValue)] = argumentExprs[i + 1].evaluate(thisValue)
//      }
//      return MapValue(entries)
//    }
//
//    override fun computeType(base: PklBaseModule): Type {
//      var keyType: Type = Type.Nothing
//      var valueType: Type = Type.Nothing
//
//      for ((index, expr) in argumentExprs.withIndex()) {
//        if (index % 2 == 0) {
//          keyType = Type.union(keyType, expr.computeType(base), base)
//        } else {
//          valueType = Type.union(valueType, expr.computeType(base), base)
//        }
//      }
//
//      return base.mapType.withTypeArguments(keyType, valueType)
//    }
//  }
// }
