package Shades

import calico.*
import calico.html.io.{*, given}
import cats.effect.*
import fs2.dom.*
import cats.data.NonEmptyList
import Shades.ParsedData.fileParser
import cats.Show
import Shades.ParsedData.errorToString
import scala.concurrent.duration.given
import fs2.concurrent.Channel
import cats.syntax.all.*
import fs2.concurrent.SignallingRef
import calico.frp.SignallingSortedMapRef
import calico.frp.given
import fs2.concurrent.Signal
import org.scalajs.dom.KeyValue
import cats.parse.Parser
import scala.scalajs.js.annotation.JSGlobalScope
import cats.data.Nested

enum CollationType:
    case regular
    case variable

case class CollationRule(
    codepoint: Int,
    kind: CollationType,
    x1: Int,
    x2: Int,
    x3: Int,
    name: String,
)

object ParsedData:
    import cats.parse.*

    val tab = Parser.char('\t')
    val dot = Parser.char('.').map(_ => CollationType.regular)
    val star = Parser.char('*').map(_ => CollationType.variable)

    extension (p: Parser[NonEmptyList[Char]])
        def toInt: Parser[Int] =
            p.map(chars => Integer.parseInt(chars.toList.mkString, 16))

    val fiveHexParser =
        Rfc5234.hexdig.rep(5, 5).toInt

    val fourHexParser =
        Rfc5234.hexdig.rep(4, 4).toInt

    val stringParser =
        (
            Parser.char(' ') | Rfc5234.alpha
        ).rep.map(chars => chars.toList.mkString)

    val typeParser =
        dot.orElse(star)

    val rowParser =
        fiveHexParser
            ~ (tab *> typeParser)
            ~ (tab *> fourHexParser)
            ~ (tab *> fourHexParser)
            ~ (tab *> fourHexParser)
            ~ (tab *> stringParser)
            <* Parser.char('\n')

    val fileParser =
        rowParser.map { case (((((id, kind), x1), x2), x3), name) =>
            CollationRule(id, kind, x1, x2, x3, null)
        }.rep.map { rules =>
            rules.map(x => (x.codepoint, x)).toList.toMap
        }

    def errorToString(error: Parser.Error): String =
        Show[Parser.Error].show(error)

def codepoint(int: Int): String =
    String(Array(int), 0, 1)

@scalajs.js.native
@JSGlobalScope
object Window extends scalajs.js.Object:
    def codepointsOf(str: String): scalajs.js.Array[Int] = scalajs.js.native

class StringStore(val entries: SignallingSortedMapRef[IO, Long, String]):
    private val nextID = IO.realTime.map(_.toMillis)

    def add(text: String): IO[Unit] =
        for
            newID <- nextID
            _ <- entries(newID).set(Some(text))
        yield ()
    def get(id: Long): SignallingRef[IO, Option[String]] =
        entries(id)
    def ids: Signal[IO, List[Long]] =
        entries.keys.map(_.toList)
    def idsSortedAccordingTo(rules: Map[Int, CollationRule]): Signal[IO, List[Long]] =
        entries.map { it =>
            it.toList.sortWith { case ((_, a), (_, b)) =>
                val aPoints = Window.codepointsOf(a)
                val bPoints = Window.codepointsOf(b)

                lazy val aLevel1 = aPoints.map(rules(_).x1)
                lazy val bLevel1 = bPoints.map(rules(_).x1)
                lazy val aLevel2 = aPoints.map(rules(_).x2)
                lazy val bLevel2 = bPoints.map(rules(_).x2)
                lazy val aLevel3 = aPoints.map(rules(_).x3)
                lazy val bLevel3 = bPoints.map(rules(_).x3)

                if aLevel1 != bLevel1 then
                    val (aDiff, bDiff) = aLevel1.zip(bLevel1).filterNot(_ == _).head
                    aDiff < bDiff
                else if aLevel2 != bLevel2 then
                    val (aDiff, bDiff) = aLevel2.zip(bLevel2).filterNot(_ == _).head
                    aDiff < bDiff
                else if aLevel3 != aLevel3 then
                    val (aDiff, bDiff) = aLevel3.zip(bLevel3).filterNot(_ == _).head
                    aDiff < bDiff
                else
                    false
            }.map(_._1)
        }

object Main extends IOWebApp:
    def itemInput(store: StringStore): Resource[IO, HtmlElement[IO]] =
        input.withSelf { self =>
            (
                placeholder := "add a thingy (UCSUR only, no latin)",
                autoFocus := true,
                cls := "w-100",
                onKeyDown --> {
                    _.filter(_.key == KeyValue.Enter)
                        .evalMap(_ => self.value.get)
                        .filterNot(_.isEmpty)
                        .foreach(store.add(_) *> self.value.set(""))
                }
            )
        }

    def editItem(
        item: SignallingRef[IO, Option[String]],
    ): Resource[IO, HtmlElement[IO]] =
        for
            field <- input.withSelf { self =>
                (
                    defaultValue <-- item.map(_.foldMap(identity)),
                    cls := "w-75",
                    onInput --> {
                        _.foreach { _ =>
                            self.value.get.flatMap { text =>
                                item.update(_.map(_ => text))
                            }
                        }
                    },
                )
            }
            btn <- button(
                "󱥄󱥶󱤉󱥁",
                cls := "w-25",
                onClick --> {
                    _.foreach { _ =>
                        item.set(None)
                    }
                }
            )
            ui <- div(field, btn)
        yield ui

    def sortedItem(
        item: SignallingRef[IO, Option[String]]
    ): Resource[IO, HtmlElement[IO]] =
        for ui <- div(item.map(_.foldMap(identity)))
        yield ui

    override def render: Resource[IO, HtmlElement[IO]] =
        for
            store <- SignallingSortedMapRef[IO, Long, String]
                .flatTap {
                    _.set(collection.immutable.SortedMap(
                        (1, "󱥢󱦐󱤺󱦜󱦑"),
                        (2, "󱥢󱤹"),
                        (3, "󱥢󱤸"),
                        (4, "󱥢󱦐󱤺󱥰󱤺󱥰󱦑"),
                        (5, "󱥢󱤺"),
                        (6, "󱥢󱦐󱤺󱦝󱦑"),
                        (7, "󱥢󱦆"),
                        (8, "󱥢󱤷"),
                    ))
                }
                .map(x => StringStore(x))
                .toResource

            textData <- Channel
                .unbounded[IO, String]
                .toResource
                .evalTap(_.send(Data.content))
            parsed <- textData.stream
                .debounce(1.second)
                .map(fileParser.parseAll)
                .holdOptionResource
            ui <-
                div(
                    cls := "mw9 center h-100",
                    div(
                        cls := "cf h-100",
                        div(
                            cls := "fl w-third",
                            span("collation rules!"),
                            textArea.withSelf { self =>
                                (
                                    onInput --> {
                                        _.evalMap(_ => self.value.get)
                                            .through(textData.sendAll)
                                    },
                                    value := Data.content,
                                    cls := "w-100 h-100",
                                )
                            },
                        ),
                        div(
                            cls := "fl w-third",
                            span("input your stuff! ", small("(UCSUR, bring your own input method)")),
                            itemInput(store),
                            children[Long](id =>
                                editItem(store.get(id))
                            ) <-- store.ids,
                            p("format:"),
                            p("the first number (before the period/star) is the codepoint. you probably don't want to touch this."),
                            p("the period/star indicates normal vs variable collation. glyphs should be normal and punctuation should be variable. you probably don't want to touch this."),
                            p("the second number is L1 collation."),
                            p("the third number is L2 collation."),
                            p("the fourth number is L3 collation."),
                            p("items are sorted in a sort of priority system."),
                        ),
                        div(
                            cls := "fl w-third",
                            span("it will show sorted here!"),
                            Nested(parsed).map {
                                case Right(rules) =>
                                    div(children[Long](id =>
                                        sortedItem(store.get(id))
                                    ) <-- store.idsSortedAccordingTo(rules))
                                case Left(error) =>
                                    pre(errorToString(error))
                            }.value,
                        ),
                    ),
                )
        yield ui
