package play.api.libs.json.scalacheck

import play.api.libs.json._

import scala.reflect.ClassTag
import scala.testing.{GenericTestSuite, TestSuiteBridge}
import scala.util.control.NonFatal

/**
 * A common base class for providing a free serialization specification.
 *
 * This currently supports testing Lift and Play serialization, as well as cross-serialization
 * from a Lift serialized value read by Play and vice versa.
 *
 * Subclasses of this will test that for each example, the serialized result can be read back
 * into a value that is equal to the original example.
 */
trait SerializationTests[T] extends GenericTestSuite {
  self: TestSuiteBridge =>

  def examples: Seq[T]

  protected def clsTag: ClassTag[T]

  protected def className: String = clsTag.runtimeClass.getSimpleName

  protected def assertPostSerializationEquality(expected: T, actual: T): Unit = self.assertEqual(expected, actual)

  protected def assertSame(expected: T, actual: T, prettyJson: String): Unit = {
    try assertPostSerializationEquality(expected, actual)
    catch {
      case NonFatal(ex) =>
        fail(
          s"The expected and actual values are not equal.\n\n" +
          s"expected:\n$expected\n\n" +
          s"actual:\n$actual\n\n" +
          s"json:\n$prettyJson\n\n",
          Some(ex)
        )
    }
  }
}

/**
 * A mixin for adding Play serialization tests.
 */
trait PlaySerializationTests[T] extends SerializationTests[T] {
  self: TestSuiteBridge =>

  protected implicit def playFormat: Format[T]

  protected def assertSame(actual: T, expected: T, decomposed: JsValue): Unit = {
    assertSame(expected, actual, Json.prettyPrint(decomposed))
  }

  addTest(s"Format[$className] should read what it writes as JsValue in") {
    val decomposed = examples.par map playFormat.writes
    val reconstructed = decomposed map { written =>
      val pretty = Json.prettyPrint(written)
      val result = playFormat.reads(written)
      result match {
        case JsSuccess(extracted, _) => extracted
        case JsError(errors) =>
          val errorDetails = Json.prettyPrint(JsError.toFlatJson(errors))
          fail(
            s"Could not read an instance of $className from:\n$pretty\n\n" +
              s"Play detected errors:\n$errorDetails\n"
          )
      }
    }
    for (((expected, actual), asWritten) <- examples zip reconstructed zip decomposed) {
      assertSame(expected, actual, asWritten)
    }
  }
}

/**
 * Extend this to perform Play serialization tests.
 *
 * This provides a sample constructor that is easy to fulfill in the subclass.
 */
abstract class PlayJsonFormatTests[T](
  override val examples: Seq[T]
)(implicit
  override protected implicit val playFormat: Format[T],
  override protected val clsTag: ClassTag[T]
) extends PlaySerializationTests[T] {
   self: TestSuiteBridge =>

  /* sadly this does not work in Scala 2.10...
[error] /code/play-json-ops/src/main/scala/play/api/libs/json/scalacheck/PlayJsonFormatSpec.scala:93: too many arguments for constructor Predef: ()type
[error]     this(gen.toIterator.take(samples).toSeq)
[error]     ^
[error] /code/play-json-ops/src/main/scala/play/api/libs/json/scalacheck/PlayJsonFormatSpec.scala:96: too many arguments for constructor Predef: ()type
[error]     this(arb.arbitrary, samples)
   */

  // Extending TestSuiteBridge instead of using a self-type fixes it, but has a worse compiler error because it
  // doesn't tell you how to fix it (ie. extending some subclass of TestSuiteBridge).
  // I'm deferring these helpful constructors to the subclasses to avoid the issue

//  def this(gen: Gen[T], samples: Int = 100)(implicit playFormat: Format[T], clsTag: ClassTag[T]) =
//    this(gen.toIterator.take(samples).toSeq)
//
//  def this(samples: Int = 100)(implicit arb: Arbitrary[T], playFormat: Format[T], clsTag: ClassTag[T]) =
//    this(arb.arbitrary, samples)
}