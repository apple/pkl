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

import java.util.regex.PatternSyntaxException
import org.pkl.lsp.PklBaseModule
import org.pkl.lsp.ast.*
import org.pkl.lsp.ast.PklThisExpr
import org.pkl.lsp.isMathematicalInteger
import org.pkl.lsp.resolvers.ResolveVisitors
import org.pkl.lsp.type.ConstraintExpr.*
import org.pkl.lsp.type.ConstraintValue.*

fun List<PklExpr>.toConstraintExprs(base: PklBaseModule): List<ConstraintExpr> = map {
  it.toConstraintExpr(base)
}

fun PklExpr?.toConstraintExpr(base: PklBaseModule): ConstraintExpr {
  return when (this) {
    null -> Error
    is PklIntLiteralExpr -> {
      val strippedText = text.filterNot { it == '_' }
      when {
        strippedText.startsWith("0x") ->
          strippedText.substring(2).toLongOrNull(16)?.let { IntValue(it) } ?: Error
        strippedText.startsWith("0o") ->
          strippedText.substring(2).toLongOrNull(8)?.let { IntValue(it) } ?: Error
        strippedText.startsWith("0b") ->
          strippedText.substring(2).toLongOrNull(2)?.let { IntValue(it) } ?: Error
        else -> strippedText.toLongOrNull()?.let { IntValue(it) } ?: Error
      }
    }
    is PklFloatLiteralExpr -> {
      val strippedText = text.filterNot { it == '_' }
      strippedText.toDoubleOrNull()?.let { FloatValue(it) } ?: Error
    }
    is PklSingleLineStringLiteral -> escapedText()?.let { StringValue(it) } ?: Error
    is PklMultiLineStringLiteral -> escapedText()?.let { StringValue(it) } ?: Error
    is PklTrueLiteralExpr -> True
    is PklFalseLiteralExpr -> False
    is PklNullLiteralExpr -> Null
    is PklThisExpr -> ThisExpr
    is PklTypeTestExpr -> {
      if (operator == TypeTestOperator.IS)
        TypeTest(expr.toConstraintExpr(base), type.toType(base, mapOf()), base)
      else Error
    }
    is PklIfExpr ->
      If(
        conditionExpr.toConstraintExpr(base),
        thenExpr.toConstraintExpr(base),
        elseExpr.toConstraintExpr(base)
      )
    is PklAccessExpr -> {
      val receiverExpr by lazy {
        when (this) {
          is PklUnqualifiedAccessExpr -> ImplicitThisExpr
          is PklQualifiedAccessExpr -> this.receiverExpr.toConstraintExpr(base)
          else -> Error
        }
      }
      when (
        val resolved =
          resolve(base, null, mapOf(), ResolveVisitors.firstElementNamed(memberNameText, base))
      ) {
        is PklClassProperty -> {
          when (resolved.parentOfTypes(PklClass::class, PklModule::class, PklObjectMember::class)) {
            is PklObjectMember -> Error
            base.intType.ctx ->
              when (resolved) {
                base.intIsPositiveProperty -> IntIsPositive(receiverExpr)
                base.intIsNonZeroProperty -> IntIsNonZero(receiverExpr)
                base.intIsEvenProperty -> IntIsEven(receiverExpr)
                base.intIsOddProperty -> IntIsOdd(receiverExpr)
                else -> {
                  val quantity: Double =
                    when (val recvExpr = receiverExpr) {
                      is IntValue -> recvExpr.value.toDouble()
                      else -> return Error
                    }
                  @Suppress("DuplicatedCode") // false positive
                  when (resolved) {
                    base.intNsProperty -> Duration(quantity, Duration.Unit.NANOS)
                    base.intUsProperty -> Duration(quantity, Duration.Unit.MICROS)
                    base.intMsProperty -> Duration(quantity, Duration.Unit.MILLIS)
                    base.intSProperty -> Duration(quantity, Duration.Unit.SECONDS)
                    base.intMinProperty -> Duration(quantity, Duration.Unit.MINUTES)
                    base.intHProperty -> Duration(quantity, Duration.Unit.HOURS)
                    base.intDProperty -> Duration(quantity, Duration.Unit.DAYS)
                    base.intBProperty -> DataSize(quantity, DataSize.Unit.BYTES)
                    base.intKbProperty -> DataSize(quantity, DataSize.Unit.KILOBYTES)
                    base.intMbProperty -> DataSize(quantity, DataSize.Unit.MEGABYTES)
                    base.intGbProperty -> DataSize(quantity, DataSize.Unit.GIGABYTES)
                    base.intTbProperty -> DataSize(quantity, DataSize.Unit.TERABYTES)
                    base.intPbProperty -> DataSize(quantity, DataSize.Unit.PETABYTES)
                    base.intKibProperty -> DataSize(quantity, DataSize.Unit.KIBIBYTES)
                    base.intMibProperty -> DataSize(quantity, DataSize.Unit.MEBIBYTES)
                    base.intGibProperty -> DataSize(quantity, DataSize.Unit.GIBIBYTES)
                    base.intTibProperty -> DataSize(quantity, DataSize.Unit.TEBIBYTES)
                    base.intPibProperty -> DataSize(quantity, DataSize.Unit.PEBIBYTES)
                    else -> Error
                  }
                }
              }
            base.floatType.ctx ->
              when (resolved) {
                base.floatIsPositiveProperty -> FloatIsPositive(receiverExpr)
                base.floatIsNonZeroProperty -> FloatIsNonZero(receiverExpr)
                base.floatIsFiniteProperty -> FloatIsFinite(receiverExpr)
                base.floatIsInfiniteProperty -> FloatIsInfinite(receiverExpr)
                base.floatIsNaNProperty -> FloatIsNaN(receiverExpr)
                else -> {
                  val quantity: Double =
                    when (val recvExpr = receiverExpr) {
                      is FloatValue -> recvExpr.value
                      else -> return Error
                    }
                  @Suppress("DuplicatedCode") // false positive
                  when (resolved) {
                    base.floatNsProperty -> Duration(quantity, Duration.Unit.NANOS)
                    base.floatUsProperty -> Duration(quantity, Duration.Unit.MICROS)
                    base.floatMsProperty -> Duration(quantity, Duration.Unit.MILLIS)
                    base.floatSProperty -> Duration(quantity, Duration.Unit.SECONDS)
                    base.floatMinProperty -> Duration(quantity, Duration.Unit.MINUTES)
                    base.floatHProperty -> Duration(quantity, Duration.Unit.HOURS)
                    base.floatDProperty -> Duration(quantity, Duration.Unit.DAYS)
                    base.floatBProperty -> DataSize(quantity, DataSize.Unit.BYTES)
                    base.floatKbProperty -> DataSize(quantity, DataSize.Unit.KILOBYTES)
                    base.floatMbProperty -> DataSize(quantity, DataSize.Unit.MEGABYTES)
                    base.floatGbProperty -> DataSize(quantity, DataSize.Unit.GIGABYTES)
                    base.floatTbProperty -> DataSize(quantity, DataSize.Unit.TERABYTES)
                    base.floatPbProperty -> DataSize(quantity, DataSize.Unit.PETABYTES)
                    base.floatKibProperty -> DataSize(quantity, DataSize.Unit.KIBIBYTES)
                    base.floatMibProperty -> DataSize(quantity, DataSize.Unit.MEBIBYTES)
                    base.floatGibProperty -> DataSize(quantity, DataSize.Unit.GIBIBYTES)
                    base.floatTibProperty -> DataSize(quantity, DataSize.Unit.TEBIBYTES)
                    base.floatPibProperty -> DataSize(quantity, DataSize.Unit.PEBIBYTES)
                    else -> Error
                  }
                }
              }
            base.durationType.ctx ->
              when (resolved) {
                base.durationIsPositiveProperty -> DurationIsPositive(receiverExpr)
                else -> Error
              }
            base.dataSizeType.ctx ->
              when (resolved) {
                base.dataSizeIsPositiveProperty -> DataSizeIsPositive(receiverExpr)
                base.dataSizeIsBinaryUnitProperty -> DataSizeIsBinaryUnit(receiverExpr)
                base.dataSizeIsDecimalUnitProperty -> DataSizeIsDecimalUnit(receiverExpr)
                else -> Error
              }
            base.stringType.ctx ->
              when (resolved) {
                base.stringIsEmptyProperty -> StringIsEmpty(receiverExpr)
                base.stringIsRegexProperty -> StringIsRegex(receiverExpr)
                base.stringLengthProperty -> StringLength(receiverExpr)
                else -> Error
              }
            base.listType.ctx ->
              when (resolved) {
                base.listIsEmptyProperty -> ListIsEmpty(receiverExpr)
                base.listLengthProperty -> ListLength(receiverExpr)
                else -> Error
              }
            base.setType.ctx ->
              when (resolved) {
                base.setIsEmptyProperty -> SetIsEmpty(receiverExpr)
                base.setLengthProperty -> SetLength(receiverExpr)
                else -> Error
              }
            base.mapType.ctx ->
              when (resolved) {
                base.mapIsEmptyProperty -> MapIsEmpty(receiverExpr)
                base.mapLengthProperty -> MapLength(receiverExpr)
                else -> Error
              }
            else -> Error
          }
        }
        is PklClassMethod -> {
          val arguments = argumentList?.elements ?: return Error
          when (resolved.parentOfTypes(PklClass::class, PklModule::class, PklObjectMember::class)) {
            is PklObjectMember -> Error
            base.ctx ->
              when (resolved) {
                base.regexConstructor ->
                  expectOneArg(arguments, base) { arg ->
                    when (arg) {
                      is StringValue ->
                        try {
                          RegexValue(arg.value.toRegex())
                        } catch (e: PatternSyntaxException) {
                          Error
                        }
                      else -> Error
                    }
                  }
                base.listConstructor -> ListExpr(arguments.toConstraintExprs(base))
                base.setConstructor -> SetExpr(arguments.toConstraintExprs(base))
                base.mapConstructor -> MapExpr(arguments.toConstraintExprs(base))
                else -> Error
              }
            base.booleanType.ctx ->
              when (resolved) {
                base.booleanXorMethod -> expectOneArg(arguments, base) { Xor(receiverExpr, it) }
                base.booleanImpliesMethod ->
                  expectOneArg(arguments, base) { Implies(receiverExpr, it) }
                else -> Error
              }
            base.intType.ctx ->
              when (resolved) {
                base.intIsBetweenMethod ->
                  expectTwoArgs(arguments, base) { arg1, arg2 ->
                    IntIsBetween(receiverExpr, arg1, arg2)
                  }
                else -> Error
              }
            base.floatType.ctx ->
              when (resolved) {
                base.floatIsBetweenMethod ->
                  expectTwoArgs(arguments, base) { arg1, arg2 ->
                    FloatIsBetween(receiverExpr, arg1, arg2)
                  }
                else -> Error
              }
            base.stringType.ctx ->
              when (resolved) {
                base.stringMatchesMethod ->
                  expectOneArg(arguments, base) { StringMatches(receiverExpr, it) }
                base.stringContainsMethod ->
                  expectOneArg(arguments, base) { StringContains(receiverExpr, it) }
                base.stringStartsWithMethod ->
                  expectOneArg(arguments, base) { StringStartsWith(receiverExpr, it) }
                base.stringEndsWithMethod ->
                  expectOneArg(arguments, base) { StringEndsWith(receiverExpr, it) }
                else -> Error
              }
            base.durationType.ctx ->
              when (resolved) {
                base.durationIsBetweenMethod ->
                  expectTwoArgs(arguments, base) { arg1, arg2 ->
                    DurationIsBetween(receiverExpr, arg1, arg2)
                  }
                else -> Error
              }
            base.dataSizeType.ctx ->
              when (resolved) {
                base.dataSizeIsBetweenMethod ->
                  expectTwoArgs(arguments, base) { arg1, arg2 ->
                    DataSizeIsBetween(receiverExpr, arg1, arg2)
                  }
                else -> Error
              }
            else -> Error
          }
        }
        else -> Error
      }
    }
    is PklBinExpr -> {
      val leftExpr = leftExpr.toConstraintExpr(base)
      val rightExpr = rightExpr.toConstraintExpr(base)
      when (this) {
        is PklEqualityExpr -> {
          when (operator.type) {
            TokenType.EQUAL -> Equals(leftExpr, rightExpr)
            TokenType.NOT_EQUAL -> NotEquals(leftExpr, rightExpr)
            else -> Error
          }
        }
        is PklComparisonExpr -> {
          when (operator.type) {
            TokenType.LT -> LessThan(leftExpr, rightExpr)
            TokenType.LTE -> LessThanOrEqualTo(leftExpr, rightExpr)
            TokenType.GT -> GreaterThan(leftExpr, rightExpr)
            TokenType.GTE -> GreaterThanOrEqualTo(leftExpr, rightExpr)
            else -> Error
          }
        }
        is PklLogicalAndExpr -> And(leftExpr, rightExpr)
        is PklLogicalOrExpr -> Or(leftExpr, rightExpr)
        is PklNullCoalesceExpr -> NullCoalesce(leftExpr, rightExpr)
        else -> Error
      }
    }
    is PklUnaryMinusExpr -> {
      val operandExpr = expr.toConstraintExpr(base)
      UnaryMinus(operandExpr)
    }
    is PklLogicalNotExpr -> {
      val operandExpr = expr.toConstraintExpr(base)
      Not(operandExpr)
    }
    else -> Error
  }
}

sealed class ConstraintValue : ConstraintExpr() {
  override fun evaluate(thisValue: ConstraintValue): ConstraintValue = this

  object Error : ConstraintValue() {
    override fun render(builder: StringBuilder) {
      builder.append("<Error>")
    }

    override fun computeType(base: PklBaseModule): Type = Type.Nothing
  }

  object BaseModule : ConstraintValue() {
    override val isImplicitReceiver: Boolean
      get() = true

    override fun render(builder: StringBuilder) {
      builder.append("base")
    }

    override fun computeType(base: PklBaseModule): Type = Type.Module.create(base.ctx, "base")
  }

  abstract class Bool : ConstraintValue() {
    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  object True : Bool() {
    override fun render(builder: StringBuilder) {
      builder.append("true")
    }
  }

  object False : Bool() {
    override fun render(builder: StringBuilder) {
      builder.append("false")
    }
  }

  object Null : ConstraintValue() {
    override fun render(builder: StringBuilder) {
      builder.append("null")
    }

    override fun computeType(base: PklBaseModule): Type = base.nullType
  }

  data class IntValue(val value: Long) : ConstraintValue() {
    override fun render(builder: StringBuilder) {
      builder.append(value)
    }

    override fun computeType(base: PklBaseModule): Type = base.intType
  }

  data class FloatValue(val value: Double) : ConstraintValue() {
    override fun render(builder: StringBuilder) {
      builder.append(value)
    }

    override fun computeType(base: PklBaseModule): Type = base.floatType
  }

  data class StringValue(val value: String) : ConstraintValue() {
    override fun render(builder: StringBuilder) {
      builder.append('"').append(value).append('"')
    }

    override fun computeType(base: PklBaseModule): Type = Type.StringLiteral(value)
  }

  data class RegexValue(val value: Regex) : ConstraintValue() {
    override fun render(builder: StringBuilder) {
      builder.append("Regex(#\"")
      builder.append(value.pattern)
      builder.append("\"#)")
    }

    override fun computeType(base: PklBaseModule): Type = base.regexType
  }

  // originally copied from Pkl codebase
  class Duration(val value: Double, val unit: Unit) : ConstraintValue(), Comparable<Duration> {
    override fun computeType(base: PklBaseModule): Type = base.durationType

    override operator fun compareTo(other: Duration): Int {
      return if (unit.ordinal <= other.unit.ordinal) {
        convertValueTo(other.unit).compareTo(other.value)
      } else {
        value.compareTo(other.convertValueTo(unit))
      }
    }

    override fun render(builder: StringBuilder) {
      if (isMathematicalInteger(value)) {
        builder.append(value.toLong())
      } else {
        builder.append(value)
      }
      builder.append('.')
      unit.render(builder)
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      return other is Duration && convertValueTo(Unit.NANOS) == other.convertValueTo(Unit.NANOS)
    }

    override fun hashCode(): Int {
      return convertValueTo(Unit.NANOS).hashCode()
    }

    private fun convertValueTo(other: Unit): Double {
      return value * unit.nanos / other.nanos
    }

    enum class Unit(val nanos: Long, private val symbol: String) {

      NANOS(1, "ns"),
      MICROS(1000, "us"),
      MILLIS(1000L * 1000, "ms"),
      SECONDS(1000L * 1000 * 1000, "s"),
      MINUTES(1000L * 1000 * 1000 * 60, "min"),
      HOURS(1000L * 1000 * 1000 * 60 * 60, "h"),
      DAYS(1000L * 1000 * 1000 * 60 * 60 * 24, "d");

      fun render(builder: StringBuilder) {
        builder.append(symbol)
      }

      override fun toString(): String {
        return symbol
      }
    }
  }

  // originally copied from Pkl codebase
  class DataSize(val value: Double, val unit: Unit) : ConstraintValue(), Comparable<DataSize> {
    override fun computeType(base: PklBaseModule): Type = base.dataSizeType

    override operator fun compareTo(other: DataSize): Int {
      return if (unit.ordinal <= other.unit.ordinal) {
        convertValueTo(other.unit).compareTo(other.value)
      } else {
        value.compareTo(other.convertValueTo(unit))
      }
    }

    override fun render(builder: StringBuilder) {
      if (isMathematicalInteger(value)) {
        builder.append(value.toLong())
      } else {
        builder.append(value)
      }
      builder.append('.')
      unit.render(builder)
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      return other is DataSize && convertValueTo(Unit.BYTES) == other.convertValueTo(Unit.BYTES)
    }

    override fun hashCode(): Int {
      return convertValueTo(Unit.BYTES).hashCode()
    }

    private fun convertValueTo(other: Unit): Double {
      return value * unit.bytes / other.bytes
    }

    enum class Unit(val bytes: Long, private val symbol: String) {

      BYTES(1, "b"),
      KILOBYTES(1000, "kb"),
      KIBIBYTES(1024, "kib"),
      MEGABYTES(1000L * 1000, "mb"),
      MEBIBYTES(1024L * 1024, "mib"),
      GIGABYTES(1000L * 1000 * 1000, "gb"),
      GIBIBYTES(1024L * 1024 * 1024, "gib"),
      TERABYTES(1000L * 1000 * 1000 * 1000, "tb"),
      TEBIBYTES(1024L * 1024 * 1024 * 1024, "tib"),
      PETABYTES(1000L * 1000 * 1000 * 1000 * 1000, "pb"),
      PEBIBYTES(1024L * 1024 * 1024 * 1024 * 1024, "pib");

      fun render(builder: StringBuilder) {
        builder.append(symbol)
      }

      override fun toString(): String {
        return symbol
      }
    }
  }

  data class ListValue(val elements: List<ConstraintValue>) : ConstraintValue() {
    override fun computeType(base: PklBaseModule): Type = base.listType

    override fun render(builder: StringBuilder) {
      builder.append("List(")
      var first = true
      for (element in elements) {
        if (first) first = false else builder.append(", ")
        element.render(builder)
      }
      builder.append(")")
    }
  }

  data class SetValue(val elements: Set<ConstraintValue>) : ConstraintValue() {
    override fun computeType(base: PklBaseModule): Type = base.setType

    override fun render(builder: StringBuilder) {
      builder.append("Set(")
      var first = true
      for (element in elements) {
        if (first) first = false else builder.append(", ")
        element.render(builder)
      }
      builder.append(")")
    }
  }

  data class MapValue(val entries: Map<ConstraintValue, ConstraintValue>) : ConstraintValue() {
    override fun computeType(base: PklBaseModule): Type = base.mapType

    override fun render(builder: StringBuilder) {
      builder.append("Map(")
      var first = true
      for ((key, value) in entries) {
        if (first) first = false else builder.append(", ")
        key.render(builder)
        builder.append(", ")
        value.render(builder)
      }
      builder.append(")")
    }
  }

  // object values are harder to support because they typically use amend syntax,
  // which makes it difficult to determine their members.
  // however, we could start with just the members defined in the amend expression.
}

sealed class ConstraintExpr {
  companion object {
    fun or(leftExpr: ConstraintExpr, rightExpr: ConstraintExpr): ConstraintExpr {
      return when (leftExpr) {
        True -> True
        False -> rightExpr
        else -> Or(leftExpr, rightExpr)
      }
    }

    fun and(leftExpr: ConstraintExpr, rightExpr: ConstraintExpr): ConstraintExpr {
      return when (leftExpr) {
        True -> rightExpr
        False -> False
        else -> And(leftExpr, rightExpr)
      }
    }

    fun and(constraints: List<ConstraintExpr>): ConstraintExpr {
      return when {
        constraints.isEmpty() -> True
        else -> constraints.reduce { left, right -> and(left, right) }
      }
    }
  }

  abstract fun evaluate(thisValue: ConstraintValue): ConstraintValue

  abstract fun computeType(base: PklBaseModule): Type

  abstract fun render(builder: StringBuilder)

  fun render(): String = buildString { render(this) }

  open val isImplicitReceiver: Boolean
    get() = false

  protected fun renderReceiverExprAndDot(expr: ConstraintExpr, builder: StringBuilder) {
    if (expr.isImplicitReceiver) return

    val needParens = expr is InfixExpr
    if (needParens) builder.append('(')
    expr.render(builder)
    if (needParens) builder.append(')')
    builder.append('.')
  }

  class TypeTest(
    private val operandExpr: ConstraintExpr,
    private val type: Type,
    private val base: PklBaseModule
  ) : ConstraintExpr() {
    override fun evaluate(thisValue: ConstraintValue): ConstraintValue =
      operandExpr.computeType(base).isSubtypeOf(type, base).toBool()

    override fun computeType(base: PklBaseModule): Type = base.booleanType

    override fun render(builder: StringBuilder) {
      operandExpr.render(builder)
      builder.append(" is ")
      type.render(builder)
    }
  }

  object ThisExpr : ConstraintExpr() {
    override fun evaluate(thisValue: ConstraintValue): ConstraintValue = thisValue

    override fun computeType(base: PklBaseModule): Type = Type.Unknown // TODO

    override fun render(builder: StringBuilder) {
      builder.append("this")
    }
  }

  object ImplicitThisExpr : ConstraintExpr() {
    override fun evaluate(thisValue: ConstraintValue): ConstraintValue = thisValue

    override fun computeType(base: PklBaseModule): Type = Type.Unknown // TODO

    override val isImplicitReceiver: Boolean
      get() = true

    override fun render(builder: StringBuilder) {}
  }

  abstract class PrefixExpr(val operandExpr: ConstraintExpr) : ConstraintExpr() {

    abstract val operator: String

    final override fun render(builder: StringBuilder) {
      builder.append(operator)
      val needsParens = operandExpr is InfixExpr
      if (needsParens) builder.append('(')
      operandExpr.render(builder)
      if (needsParens) builder.append(')')
    }
  }

  abstract class InfixExpr(val leftExpr: ConstraintExpr, val rightExpr: ConstraintExpr) :
    ConstraintExpr() {

    abstract val operator: String

    final override fun render(builder: StringBuilder) {
      val leftNeedsParens = leftExpr is InfixExpr && leftExpr::class != this::class
      if (leftNeedsParens) builder.append('(')
      leftExpr.render(builder)
      if (leftNeedsParens) builder.append(')')

      builder.append(' ').append(operator).append(' ')

      val rightNeedsParens = rightExpr is InfixExpr && rightExpr::class != this::class
      if (rightNeedsParens) builder.append('(')
      rightExpr.render(builder)
      if (rightNeedsParens) builder.append(')')
    }
  }

  abstract class PropertyAccess(protected val receiverExpr: ConstraintExpr) : ConstraintExpr() {
    abstract val propertyName: String

    final override fun render(builder: StringBuilder) {
      renderReceiverExprAndDot(receiverExpr, builder)
      builder.append(propertyName)
    }
  }

  abstract class OneArgMethodCall(
    protected val receiverExpr: ConstraintExpr,
    protected val argumentExpr: ConstraintExpr
  ) : ConstraintExpr() {

    abstract val methodName: String

    final override fun render(builder: StringBuilder) {
      renderReceiverExprAndDot(receiverExpr, builder)
      builder.append(methodName).append('(')
      argumentExpr.render(builder)
      builder.append(')')
    }
  }

  abstract class TwoArgMethodCall(
    protected val receiverExpr: ConstraintExpr,
    protected val argument1Expr: ConstraintExpr,
    protected val argument2Expr: ConstraintExpr
  ) : ConstraintExpr() {

    abstract val methodName: String

    final override fun render(builder: StringBuilder) {
      renderReceiverExprAndDot(receiverExpr, builder)
      builder.append(methodName).append('(')
      argument1Expr.render(builder)
      builder.append(", ")
      argument2Expr.render(builder)
      builder.append(')')
    }
  }

  abstract class VarArgMethodCall(
    private val receiverExpr: ConstraintExpr,
    protected val argumentExprs: List<ConstraintExpr>
  ) : ConstraintExpr() {

    abstract val methodName: String

    final override fun render(builder: StringBuilder) {
      renderReceiverExprAndDot(receiverExpr, builder)
      builder.append(methodName).append('(')
      var first = true
      for (argumentExpr in argumentExprs) {
        if (first) first = false else builder.append(", ")
        argumentExpr.render(builder)
      }
      builder.append(')')
    }
  }

  class Equals(leftExpr: ConstraintExpr, rightExpr: ConstraintExpr) :
    InfixExpr(leftExpr, rightExpr) {

    override val operator: String
      get() = "=="

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val left = leftExpr.evaluate(thisValue)
      val right = rightExpr.evaluate(thisValue)

      return when (left) {
        is IntValue ->
          when (right) {
            is FloatValue -> (left.value.toDouble() == right.value).toBool()
            Error -> Error
            else -> (left == right).toBool()
          }
        is FloatValue ->
          when (right) {
            is IntValue -> (left.value == right.value.toDouble()).toBool()
            Error -> Error
            else -> (left == right).toBool()
          }
        Error -> Error
        else ->
          when (right) {
            Error -> Error
            else -> (left == right).toBool()
          }
      }
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class NotEquals(leftExpr: ConstraintExpr, rightExpr: ConstraintExpr) :
    InfixExpr(leftExpr, rightExpr) {

    override val operator: String
      get() = "!="

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val left = leftExpr.evaluate(thisValue)
      val right = rightExpr.evaluate(thisValue)

      return when (left) {
        is IntValue ->
          when (right) {
            is FloatValue -> (left.value.toDouble() != right.value).toBool()
            Error -> Error
            else -> (left != right).toBool()
          }
        is FloatValue ->
          when (right) {
            is IntValue -> (left.value != right.value.toDouble()).toBool()
            Error -> Error
            else -> (left != right).toBool()
          }
        Error -> Error
        else ->
          when (right) {
            Error -> Error
            else -> (left != right).toBool()
          }
      }
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class LessThan(leftExpr: ConstraintExpr, rightExpr: ConstraintExpr) :
    InfixExpr(leftExpr, rightExpr) {

    override val operator: String = "<"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val left = leftExpr.evaluate(thisValue)
      val right = rightExpr.evaluate(thisValue)

      return when (left) {
        is IntValue ->
          when (right) {
            is FloatValue -> (left.value.toDouble() < right.value).toBool()
            is IntValue -> (left.value < right.value).toBool()
            else -> Error
          }
        is FloatValue ->
          when (right) {
            is IntValue -> (left.value < right.value.toDouble()).toBool()
            is FloatValue -> (left.value < right.value).toBool()
            else -> Error
          }
        is Duration ->
          when (right) {
            is Duration -> (left < right).toBool()
            else -> Error
          }
        is DataSize ->
          when (right) {
            is DataSize -> (left < right).toBool()
            else -> Error
          }
        else -> Error
      }
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class LessThanOrEqualTo(leftExpr: ConstraintExpr, rightExpr: ConstraintExpr) :
    InfixExpr(leftExpr, rightExpr) {

    override val operator: String = "<="

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val left = leftExpr.evaluate(thisValue)
      val right = rightExpr.evaluate(thisValue)

      return when (left) {
        is IntValue ->
          when (right) {
            is FloatValue -> (left.value.toDouble() <= right.value).toBool()
            is IntValue -> (left.value <= right.value).toBool()
            else -> Error
          }
        is FloatValue ->
          when (right) {
            is IntValue -> (left.value <= right.value.toDouble()).toBool()
            is FloatValue -> (left.value <= right.value).toBool()
            else -> Error
          }
        is Duration ->
          when (right) {
            is Duration -> (left <= right).toBool()
            else -> Error
          }
        is DataSize ->
          when (right) {
            is DataSize -> (left <= right).toBool()
            else -> Error
          }
        else -> Error
      }
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class GreaterThan(leftExpr: ConstraintExpr, rightExpr: ConstraintExpr) :
    InfixExpr(leftExpr, rightExpr) {

    override val operator: String = ">"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val left = leftExpr.evaluate(thisValue)
      val right = rightExpr.evaluate(thisValue)

      return when (left) {
        is IntValue ->
          when (right) {
            is FloatValue -> (left.value.toDouble() > right.value).toBool()
            is IntValue -> (left.value > right.value).toBool()
            else -> Error
          }
        is FloatValue ->
          when (right) {
            is IntValue -> (left.value > right.value.toDouble()).toBool()
            is FloatValue -> (left.value > right.value).toBool()
            else -> Error
          }
        is Duration ->
          when (right) {
            is Duration -> (left > right).toBool()
            else -> Error
          }
        is DataSize ->
          when (right) {
            is DataSize -> (left > right).toBool()
            else -> Error
          }
        else -> Error
      }
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class GreaterThanOrEqualTo(leftExpr: ConstraintExpr, rightExpr: ConstraintExpr) :
    InfixExpr(leftExpr, rightExpr) {

    override val operator: String = ">="

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val left = leftExpr.evaluate(thisValue)
      val right = rightExpr.evaluate(thisValue)

      return when (left) {
        is IntValue ->
          when (right) {
            is FloatValue -> (left.value.toDouble() >= right.value).toBool()
            is IntValue -> (left.value >= right.value).toBool()
            else -> Error
          }
        is FloatValue ->
          when (right) {
            is IntValue -> (left.value >= right.value.toDouble()).toBool()
            is FloatValue -> (left.value >= right.value).toBool()
            else -> Error
          }
        is Duration ->
          when (right) {
            is Duration -> (left >= right).toBool()
            else -> Error
          }
        is DataSize ->
          when (right) {
            is DataSize -> (left >= right).toBool()
            else -> Error
          }
        else -> Error
      }
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class NullCoalesce(leftExpr: ConstraintExpr, rightExpr: ConstraintExpr) :
    InfixExpr(leftExpr, rightExpr) {
    override val operator: String
      get() = "??"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      return when (val leftValue = leftExpr.evaluate(thisValue)) {
        Null -> rightExpr.evaluate(thisValue)
        else -> leftValue
      }
    }

    override fun computeType(base: PklBaseModule): Type =
      Type.union(leftExpr.computeType(base), rightExpr.computeType(base), base)
  }

  class If(
    private val conditionExpr: ConstraintExpr,
    private val thenExpr: ConstraintExpr,
    private val elseExpr: ConstraintExpr
  ) : ConstraintExpr() {
    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      return when (conditionExpr.evaluate(thisValue)) {
        True -> thenExpr.evaluate(thisValue)
        False -> elseExpr.evaluate(thisValue)
        else -> Error
      }
    }

    override fun computeType(base: PklBaseModule): Type =
      Type.union(thenExpr.computeType(base), elseExpr.computeType(base), base)

    override fun render(builder: StringBuilder) {
      builder.append("if (")
      conditionExpr.render(builder)
      builder.append(") ")
      thenExpr.render(builder)
      builder.append(" else ")
      elseExpr.render(builder)
    }
  }

  class StringMatches(receiverExpr: ConstraintExpr, argumentExpr: ConstraintExpr) :
    OneArgMethodCall(receiverExpr, argumentExpr) {

    override val methodName: String = "matches"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? StringValue ?: return Error
      val argument = argumentExpr.evaluate(thisValue) as? RegexValue ?: return Error
      return argument.value.matches(receiver.value).toBool()
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class StringContains(receiverExpr: ConstraintExpr, argumentExpr: ConstraintExpr) :
    OneArgMethodCall(receiverExpr, argumentExpr) {

    override val methodName: String = "contains"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? StringValue ?: return Error
      return when (val argument = argumentExpr.evaluate(thisValue)) {
        is StringValue -> receiver.value.contains(argument.value).toBool()
        is RegexValue -> receiver.value.contains(argument.value).toBool()
        else -> Error
      }
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class StringStartsWith(receiverExpr: ConstraintExpr, argumentExpr: ConstraintExpr) :
    OneArgMethodCall(receiverExpr, argumentExpr) {

    override val methodName: String = "startsWith"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? StringValue ?: return Error
      return when (val argument = argumentExpr.evaluate(thisValue)) {
        is StringValue -> receiver.value.startsWith(argument.value).toBool()
        is RegexValue -> (argument.value.find(receiver.value)?.range?.start == 0).toBool()
        else -> Error
      }
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class StringEndsWith(receiverExpr: ConstraintExpr, argumentExpr: ConstraintExpr) :
    OneArgMethodCall(receiverExpr, argumentExpr) {

    override val methodName: String = "endsWith"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? StringValue ?: return Error
      return when (val argument = argumentExpr.evaluate(thisValue)) {
        is StringValue -> receiver.value.endsWith(argument.value).toBool()
        is RegexValue ->
          (argument.value.findAll(receiver.value).lastOrNull()?.range?.endInclusive ==
              receiver.value.lastIndex)
            .toBool()
        else -> Error
      }
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class IntIsPositive(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
    override val propertyName: String
      get() = "isPositive"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? IntValue ?: return Error
      return (receiver.value >= 0).toBool()
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class IntIsNonZero(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
    override val propertyName: String
      get() = "isNonZero"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? IntValue ?: return Error
      return (receiver.value != 0L).toBool()
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class IntIsEven(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
    override val propertyName: String
      get() = "isEven"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? IntValue ?: return Error
      return (receiver.value % 2 == 0L).toBool()
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class IntIsOdd(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
    override val propertyName: String
      get() = "isOdd"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? IntValue ?: return Error
      return (receiver.value % 2 == 1L).toBool()
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class FloatIsPositive(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
    override val propertyName: String
      get() = "isPositive"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? FloatValue ?: return Error
      return (receiver.value >= 0.0).toBool()
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class FloatIsNonZero(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
    override val propertyName: String
      get() = "isNonZero"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? FloatValue ?: return Error
      return (receiver.value != 0.0).toBool()
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class FloatIsFinite(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
    override val propertyName: String
      get() = "isFinite"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? FloatValue ?: return Error
      return (receiver.value.isFinite()).toBool()
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class FloatIsInfinite(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
    override val propertyName: String
      get() = "isInfinite"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? FloatValue ?: return Error
      return (receiver.value.isInfinite()).toBool()
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class FloatIsNaN(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
    override val propertyName: String
      get() = "isNaN"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? FloatValue ?: return Error
      return (receiver.value.isNaN()).toBool()
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class DurationIsPositive(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
    override val propertyName: String
      get() = "isPositive"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? Duration ?: return Error
      return (receiver.value >= 0.0).toBool()
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class DataSizeIsPositive(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
    override val propertyName: String
      get() = "isPositive"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? DataSize ?: return Error
      return (receiver.value >= 0.0).toBool()
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class DataSizeIsBinaryUnit(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
    override val propertyName: String
      get() = "isBinaryUnit"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? DataSize ?: return Error
      val ordinal = receiver.unit.ordinal
      return (ordinal % 2 == 0).toBool()
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class DataSizeIsDecimalUnit(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
    override val propertyName: String
      get() = "isDecimalUnit"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? DataSize ?: return Error
      val ordinal = receiver.unit.ordinal
      return (ordinal == 0 || ordinal % 2 == 1).toBool()
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class StringIsEmpty(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
    override val propertyName: String
      get() = "isEmpty"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? StringValue ?: return Error
      return receiver.value.isEmpty().toBool()
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class StringIsRegex(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
    override val propertyName: String
      get() = "isRegex"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? StringValue ?: return Error
      return try {
        Regex(receiver.value)
        True
      } catch (e: PatternSyntaxException) {
        False
      }
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class ListIsEmpty(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
    override val propertyName: String
      get() = "isEmpty"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? ListValue ?: return Error
      return receiver.elements.isEmpty().toBool()
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class SetIsEmpty(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
    override val propertyName: String
      get() = "isEmpty"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? SetValue ?: return Error
      return receiver.elements.isEmpty().toBool()
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class MapIsEmpty(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
    override val propertyName: String
      get() = "isEmpty"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? MapValue ?: return Error
      return receiver.entries.isEmpty().toBool()
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class StringLength(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
    override val propertyName: String
      get() = "length"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? StringValue ?: return Error
      return IntValue(receiver.value.length.toLong())
    }

    override fun computeType(base: PklBaseModule): Type = base.intType
  }

  class ListLength(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
    override val propertyName: String
      get() = "length"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? ListValue ?: return Error
      return IntValue(receiver.elements.size.toLong())
    }

    override fun computeType(base: PklBaseModule): Type = base.intType
  }

  class SetLength(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
    override val propertyName: String
      get() = "length"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? SetValue ?: return Error
      return IntValue(receiver.elements.size.toLong())
    }

    override fun computeType(base: PklBaseModule): Type = base.intType
  }

  class MapLength(receiverExpr: ConstraintExpr) : PropertyAccess(receiverExpr) {
    override val propertyName: String
      get() = "length"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? MapValue ?: return Error
      return IntValue(receiver.entries.size.toLong())
    }

    override fun computeType(base: PklBaseModule): Type = base.intType
  }

  class IntIsBetween(
    receiverExpr: ConstraintExpr,
    argument1Expr: ConstraintExpr,
    argument2Expr: ConstraintExpr
  ) : TwoArgMethodCall(receiverExpr, argument1Expr, argument2Expr) {

    override val methodName: String
      get() = "isBetween"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? IntValue ?: return Error
      val left = argument1Expr.evaluate(thisValue)
      val right = argument2Expr.evaluate(thisValue)

      return when (left) {
        is IntValue ->
          when (right) {
            is IntValue -> (receiver.value in left.value..right.value).toBool()
            is FloatValue ->
              (receiver.value.toDouble() in left.value.toDouble()..right.value).toBool()
            else -> Error
          }
        is FloatValue ->
          when (right) {
            is IntValue ->
              (receiver.value.toDouble() in left.value..right.value.toDouble()).toBool()
            is FloatValue -> (receiver.value.toDouble() in left.value..right.value).toBool()
            else -> Error
          }
        else -> Error
      }
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class FloatIsBetween(
    receiverExpr: ConstraintExpr,
    argument1Expr: ConstraintExpr,
    argument2Expr: ConstraintExpr
  ) : TwoArgMethodCall(receiverExpr, argument1Expr, argument2Expr) {

    override val methodName: String
      get() = "isBetween"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? FloatValue ?: return Error
      val left = argument1Expr.evaluate(thisValue)
      val right = argument2Expr.evaluate(thisValue)

      return when (left) {
        is IntValue ->
          when (right) {
            is IntValue ->
              (receiver.value in left.value.toDouble()..right.value.toDouble()).toBool()
            is FloatValue -> (receiver.value in left.value.toDouble()..right.value).toBool()
            else -> Error
          }
        is FloatValue ->
          when (right) {
            is IntValue -> (receiver.value in left.value..right.value.toDouble()).toBool()
            is FloatValue -> (receiver.value in left.value..right.value).toBool()
            else -> Error
          }
        else -> Error
      }
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class DurationIsBetween(
    receiverExpr: ConstraintExpr,
    argument1Expr: ConstraintExpr,
    argument2Expr: ConstraintExpr
  ) : TwoArgMethodCall(receiverExpr, argument1Expr, argument2Expr) {

    override val methodName: String
      get() = "isBetween"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? Duration ?: return Error
      val argument1 = argument1Expr.evaluate(thisValue) as? Duration ?: return Error
      val argument2 = argument2Expr.evaluate(thisValue) as? Duration ?: return Error

      return (receiver in argument1..argument2).toBool()
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class DataSizeIsBetween(
    receiverExpr: ConstraintExpr,
    argument1Expr: ConstraintExpr,
    argument2Expr: ConstraintExpr
  ) : TwoArgMethodCall(receiverExpr, argument1Expr, argument2Expr) {

    override val methodName: String
      get() = "isBetween"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue) as? DataSize ?: return Error
      val argument1 = argument1Expr.evaluate(thisValue) as? DataSize ?: return Error
      val argument2 = argument2Expr.evaluate(thisValue) as? DataSize ?: return Error

      return (receiver in argument1..argument2).toBool()
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class UnaryMinus(operandExpr: ConstraintExpr) : PrefixExpr(operandExpr) {
    override val operator: String = "-"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      return when (val operand = operandExpr.evaluate(thisValue)) {
        is IntValue -> IntValue(-operand.value)
        is FloatValue -> FloatValue(-operand.value)
        is Duration -> Duration(-operand.value, operand.unit)
        is DataSize -> DataSize(-operand.value, operand.unit)
        else -> Error
      }
    }

    override fun computeType(base: PklBaseModule): Type {
      return when (val operandType = operandExpr.computeType(base)) {
        base.intType,
        base.floatType,
        base.durationType,
        base.dataSizeType -> operandType
        else -> Type.Nothing
      }
    }
  }

  class Not(operandExpr: ConstraintExpr) : PrefixExpr(operandExpr) {
    override val operator: String = "!"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      return when (operandExpr.evaluate(thisValue)) {
        is True -> False
        is False -> True
        else -> Error
      }
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class And(leftExpr: ConstraintExpr, rightExpr: ConstraintExpr) : InfixExpr(leftExpr, rightExpr) {

    override val operator: String
      get() = "&&"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val left = leftExpr.evaluate(thisValue)
      val right = rightExpr.evaluate(thisValue)

      return when (left) {
        is True ->
          when (right) {
            is True -> True
            is False -> False
            else -> Error
          }
        else -> Error
      }
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class Or(leftExpr: ConstraintExpr, rightExpr: ConstraintExpr) : InfixExpr(leftExpr, rightExpr) {

    override val operator: String
      get() = "||"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val left = leftExpr.evaluate(thisValue)
      val right = rightExpr.evaluate(thisValue)

      return when (left) {
        is True -> True
        is False ->
          when (right) {
            is True -> True
            is False -> False
            else -> Error
          }
        else -> Error
      }
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class Xor(receiverExpr: ConstraintExpr, argumentExpr: ConstraintExpr) :
    OneArgMethodCall(receiverExpr, argumentExpr) {

    override val methodName: String
      get() = "xor"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue)
      val argument = argumentExpr.evaluate(thisValue)

      return when (receiver) {
        is True ->
          when (argument) {
            is True -> False
            is False -> True
            else -> Error
          }
        is False ->
          when (argument) {
            is True -> True
            is False -> False
            else -> Error
          }
        else -> Error
      }
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class Implies(receiverExpr: ConstraintExpr, argumentExpr: ConstraintExpr) :
    OneArgMethodCall(receiverExpr, argumentExpr) {

    override val methodName: String
      get() = "implies"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val receiver = receiverExpr.evaluate(thisValue)
      val argument = argumentExpr.evaluate(thisValue)

      return when (receiver) {
        is True ->
          when (argument) {
            is True -> True
            is False -> False
            else -> Error
          }
        is False ->
          when (argument) {
            is True -> True
            is False -> True
            else -> Error
          }
        else -> Error
      }
    }

    override fun computeType(base: PklBaseModule): Type = base.booleanType
  }

  class ListExpr(argumentExprs: List<ConstraintExpr>) :
    VarArgMethodCall(BaseModule, argumentExprs) {
    override val methodName: String
      get() = "List"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      return ListValue(argumentExprs.map { it.evaluate(thisValue) })
    }

    override fun computeType(base: PklBaseModule): Type =
      base.listType.withTypeArguments(Type.union(argumentExprs.map { it.computeType(base) }, base))
  }

  class SetExpr(argumentExprs: List<ConstraintExpr>) : VarArgMethodCall(BaseModule, argumentExprs) {
    override val methodName: String
      get() = "Set"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      return SetValue(argumentExprs.mapTo(mutableSetOf()) { it.evaluate(thisValue) })
    }

    override fun computeType(base: PklBaseModule): Type =
      base.listType.withTypeArguments(Type.union(argumentExprs.map { it.computeType(base) }, base))
  }

  class MapExpr(argumentExprs: List<ConstraintExpr>) : VarArgMethodCall(BaseModule, argumentExprs) {
    override val methodName: String
      get() = "Map"

    override fun evaluate(thisValue: ConstraintValue): ConstraintValue {
      val evenSize = argumentExprs.size.let { i -> i - (i % 2) }
      val entries = mutableMapOf<ConstraintValue, ConstraintValue>()
      for (i in 0 until evenSize step 2) {
        entries[argumentExprs[i].evaluate(thisValue)] = argumentExprs[i + 1].evaluate(thisValue)
      }
      return MapValue(entries)
    }

    override fun computeType(base: PklBaseModule): Type {
      var keyType: Type = Type.Nothing
      var valueType: Type = Type.Nothing

      for ((index, expr) in argumentExprs.withIndex()) {
        if (index % 2 == 0) {
          keyType = Type.union(keyType, expr.computeType(base), base)
        } else {
          valueType = Type.union(valueType, expr.computeType(base), base)
        }
      }

      return base.mapType.withTypeArguments(keyType, valueType)
    }
  }
}

private inline fun expectOneArg(
  arguments: List<PklExpr>,
  base: PklBaseModule,
  builder: (ConstraintExpr) -> ConstraintExpr
): ConstraintExpr =
  when (arguments.size) {
    1 -> builder(arguments[0].toConstraintExpr(base))
    else -> Error
  }

private inline fun expectTwoArgs(
  arguments: List<PklExpr>,
  base: PklBaseModule,
  builder: (ConstraintExpr, ConstraintExpr) -> ConstraintExpr
): ConstraintExpr =
  when (arguments.size) {
    2 -> builder(arguments[0].toConstraintExpr(base), arguments[1].toConstraintExpr(base))
    else -> Error
  }

private fun Boolean.toBool(): Bool = if (this) True else False
