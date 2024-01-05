package Shades

import calico.*
import calico.html.io.{*, given}
import cats.effect.*
import fs2.dom.*
import Shades.ParsedData.fileParser
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
import cats.data.Nested

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
    def idsSortedAccordingTo(
        rules: CollationTable
    ): Signal[IO, List[Long]] =
        entries.map { it =>
            it.toList
                .sortWith { case ((_, a), (_, b)) =>
                    val keyA = rules.sortKeyOf(a)
                    val keyB = rules.sortKeyOf(b)
                    keyA.preceedes(keyB)
                }
                .map(_._1)
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
                },
            )
        }

    def editItem(
        item: SignallingRef[IO, Option[String]]
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
                },
            )
            ui <- div(field, btn)
        yield ui

    def sortedItem(
        item: SignallingRef[IO, Option[String]],
        table: CollationTable,
    ): Resource[IO, HtmlElement[IO]] =
        val sortKey = item.map {
            case Some(str) => Some(table.sortKeyOf(str))
            case None => None
        }
        def stringIt(array: Array[Int]): String =
            array.map(it => f"$it%04X").mkString(" ")
        for
            ui <- div(
                cls := "pa2",
                div(
                    item.map(_.foldMap(identity)),
                    div(
                        code(
                            cls := "dark-red",
                            sortKey.map(_.foldMap(x => stringIt(x.L1)))
                        ),
                        code(" 0000 "),
                        code(
                            cls := "dark-green",
                            sortKey.map(_.foldMap(x => stringIt(x.L2)))
                        ),
                        code(" 0000 "),
                        code(
                            cls := "dark-blue",
                            sortKey.map(_.foldMap(x => stringIt(x.L3)))
                        ),
                    )
                )
            )
        yield ui

    override def render: Resource[IO, HtmlElement[IO]] =
        for
            store <- SignallingSortedMapRef[IO, Long, String]
                .flatTap {
                    _.set(
                        collection.immutable.SortedMap(
                            (1, "󱥢󱦐󱤺󱦜󱦑"),
                            (2, "󱥢󱤹"),
                            (3, "󱥢󱤸"),
                            (4, "󱥢󱦐󱤺󱥰󱤺󱥰󱦑"),
                            (5, "󱥢󱤺"),
                            (6, "󱥢󱦐󱤺󱦝󱦑"),
                            (7, "󱥢󱦆"),
                            (8, "󱥢󱤷"),
                        )
                    )
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
                            span(
                                "input your stuff! ",
                                small("(UCSUR, bring your own input method)"),
                            ),
                            itemInput(store),
                            children[Long](id =>
                                editItem(store.get(id))
                            ) <-- store.ids,
                            p("format:"),
                            p(
                                "the first number (before the period/star) is the codepoint. you probably don't want to touch this."
                            ),
                            p(
                                "the period/star indicates normal vs variable collation. glyphs should be normal and punctuation should be variable. you probably don't want to touch this."
                            ),
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
                                    div(
                                        children[Long](id =>
                                            sortedItem(store.get(id), rules)
                                        ) <-- store.idsSortedAccordingTo(rules)
                                    )
                                case Left(error) =>
                                    pre(errorToString(error))
                            }.value,
                        ),
                    ),
                )
        yield ui
